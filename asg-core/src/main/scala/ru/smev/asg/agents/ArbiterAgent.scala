package ru.smev.asg.agents

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.slf4j.LoggerFactory
import ru.smev.asg.hotl.{EscalationCommand, EscalationManager}
import scala.concurrent.duration.*

// Протокол арбитра: конечный автомат S0 → S1 → S2 → S3
sealed trait ArbiterCommand

object ArbiterCommand:
  // Внешние команды
  final case class StartTranslation(
    request: TranslationRequest,
    replyTo: ActorRef[TranslationResponse]
  ) extends ArbiterCommand

  // Внутренние ответы от MatcherAgent
  final case class MatcherResponse(
    candidates: List[MatcherCommand.MatchCandidate],
    requestId: String
  ) extends ArbiterCommand

  // Внутренние ответы от ValidatorAgent
  final case class ValidatorResponse(
    result: ValidatorCommand.ValidationResult,
    report: ValidatorCommand.ValidationReport
  ) extends ArbiterCommand

  // Решение оператора при эскалации
  final case class OperatorDecision(
    approved: Boolean,
    comment: String,
    requestId: String
  ) extends ArbiterCommand

  // Тайм-аут перевода
  case object TranslationTimeout extends ArbiterCommand

  // Запрос на перевод
  final case class TranslationRequest(
    sourceOntologyId: String,
    targetOntologyId: String,
    query: String
  )

  // Статус результата перевода
  enum TranslationStatus:
    case Accepted, Rejected, Escalated, Failed, TimedOut

  // Ответ на запрос перевода
  final case class TranslationResponse(
    requestId: String,
    translatedQuery: String,
    confidence: Double,
    status: TranslationStatus,
    message: String
  )

/**
 * Агент-арбитр: конечный автомат с состояниями S0–S3.
 *
 * Состояния (ТЭМ-2021 + расширение LISI++):
 *   S0 (Idle)         — ожидание запроса
 *   S1 (Matching)     — делегирование MatcherAgent, ожидание кандидатов
 *   S2 (Validating)   — делегирование ValidatorAgent, проверка каждого кандидата
 *   S3 (Escalated)    — эскалация оператору при Warning или исчерпании кандидатов
 *
 * Переходы:
 *   S0 + StartTranslation → S1 (send MatchRequest to matcher)
 *   S1 + MatcherResponse → S2 (send ValidateRequest to validator with first candidate)
 *   S2 + ValidatorResponse(Valid)   → S0 (send TranslationResponse(Accepted))
 *   S2 + ValidatorResponse(Invalid) → S2 (try next candidate) OR S3 (no more candidates)
 *   S2 + ValidatorResponse(Warning) → S3 (escalate to operator)
 *   S3 + OperatorDecision(approved)  → S0 (send TranslationResponse(Accepted))
 *   S3 + OperatorDecision(rejected)  → S0 (send TranslationResponse(Rejected))
 *   Any + Timeout → S0 (send TranslationResponse(TimedOut))
 */
object ArbiterAgent:

  private val logger = LoggerFactory.getLogger(getClass)
  private val TranslationTimeoutDuration = 30.seconds
  private val MaxRetriesPerCandidate = 1

  // Внутреннее состояние арбитра
  private case class ArbiterState(
    phase: ArbiterPhase,
    requestId: String,
    request: TranslationRequest,
    replyTo: ActorRef[TranslationResponse],
    candidates: List[MatcherCommand.MatchCandidate] = Nil,
    currentIndex: Int = 0,
    attempts: Int = 0
  )

  private enum ArbiterPhase:
    case Idle, Matching, Validating, Escalated

  def apply(
    matcher: ActorRef[MatcherCommand],
    validator: ActorRef[ValidatorCommand],
    escalation: ActorRef[EscalationCommand]
  ): Behavior[ArbiterCommand] = Behaviors.withTimers[ArbiterCommand] { timers =>
    Behaviors.setup { context =>
      // Состояние S0: Idle
      idle(matcher, validator, escalation, timers)
    }
  }

  /** S0: Idle — ожидание запроса на перевод. */
  private def idle(
    matcher: ActorRef[MatcherCommand],
    validator: ActorRef[ValidatorCommand],
    escalation: ActorRef[EscalationCommand],
    timers: TimerScheduler[ArbiterCommand]
  ): Behavior[ArbiterCommand] = Behaviors.receiveMessage {
    case StartTranslation(request, replyTo) =>
      val requestId = s"REQ-${System.currentTimeMillis()}"
      logger.info("[S0→S1] Запрос {}: {} → {}", requestId, request.sourceOntologyId, request.targetOntologyId)

      // Делегирование MatcherAgent
      matcher ! MatcherCommand.MatchRequest(
        sourceOntologyId = request.sourceOntologyId,
        targetOntologyId = request.targetOntologyId,
        query = request.query,
        replyTo = context.self
      )

      // Запуск таймера на 30 секунд
      timers.startSingleTimer(TranslationTimeout, TranslationTimeoutDuration)

      val state = ArbiterState(
        phase = ArbiterPhase.Matching,
        requestId = requestId,
        request = request,
        replyTo = replyTo
      )
      matching(state, matcher, validator, escalation, timers)

    case _ =>
      Behaviors.ignore
  }

  /** S1: Matching — ожидание ответа от MatcherAgent. */
  private def matching(
    state: ArbiterState,
    matcher: ActorRef[MatcherCommand],
    validator: ActorRef[ValidatorCommand],
    escalation: ActorRef[EscalationCommand],
    timers: TimerScheduler[ArbiterCommand]
  ): Behavior[ArbiterCommand] = Behaviors.receiveMessage {
    case MatcherResponse(candidates, requestId) if requestId == state.requestId =>
      timers.cancel(TranslationTimeout)

      if (candidates.isEmpty) {
        logger.warn("[S1→S0] {}: кандидаты не найдены, отказ", requestId)
        state.replyTo ! TranslationResponse(
          requestId = requestId,
          translatedQuery = "",
          confidence = 0.0,
          status = TranslationStatus.Rejected,
          message = "Кандидаты сопоставления не найдены"
        )
        idle(matcher, validator, escalation, timers)
      } else {
        logger.info("[S1→S2] {}: получено {} кандидатов", requestId, candidates.size)
        // Переход к валидации первого кандидата
        val firstCandidate = candidates.head
        validator ! ValidatorCommand.ValidateRequest(
          mapping = ValidatorCommand.MappingCandidate(
            sourceConcept = firstCandidate.sourceConcept,
            targetConcept = firstCandidate.targetConcept,
            confidence = firstCandidate.confidence
          ),
          replyTo = context.self
        )
        timers.startSingleTimer(TranslationTimeout, TranslationTimeoutDuration)
        validating(state.copy(candidates = candidates, phase = ArbiterPhase.Validating),
                   matcher, validator, escalation, timers)
      }

    case TranslationTimeout =>
      logger.error("[S1→S0] {}: тайм-аут ожидания MatcherAgent", state.requestId)
      state.replyTo ! TranslationResponse(
        requestId = state.requestId,
        translatedQuery = "",
        confidence = 0.0,
        status = TranslationStatus.TimedOut,
        message = "Тайм-аут сопоставления"
      )
      idle(matcher, validator, escalation, timers)

    case _ =>
      Behaviors.ignore
  }

  /** S2: Validating — проверка текущего кандидата через ValidatorAgent. */
  private def validating(
    state: ArbiterState,
    matcher: ActorRef[MatcherCommand],
    validator: ActorRef[ValidatorCommand],
    escalation: ActorRef[EscalationCommand],
    timers: TimerScheduler[ArbiterCommand]
  ): Behavior[ArbiterCommand] = Behaviors.receiveMessage {
    case ValidatorResponse(result, report) =>
      timers.cancel(TranslationTimeout)

      result match {
        case ValidatorCommand.ValidationResult.Valid =>
          // Кандидат принят
          val candidate = state.candidates(state.currentIndex)
          logger.info("[S2→S0] {}: кандидат принят (confidence={})", state.requestId, candidate.confidence)
          state.replyTo ! TranslationResponse(
            requestId = state.requestId,
            translatedQuery = candidate.targetConcept,
            confidence = candidate.confidence,
            status = TranslationStatus.Accepted,
            message = s"Перевод выполнен через ${candidate.method}"
          )
          idle(matcher, validator, escalation, timers)

        case ValidatorCommand.ValidationResult.Invalid =>
          // Кандидат отклонён; попытка следующего
          val nextIndex = state.currentIndex + 1
          if (nextIndex < state.candidates.size) {
            logger.info("[S2→S2] {}: кандидат {} отклонён, пробуем следующий", state.requestId, state.currentIndex)
            val nextCandidate = state.candidates(nextIndex)
            validator ! ValidatorCommand.ValidateRequest(
              mapping = ValidatorCommand.MappingCandidate(
                sourceConcept = nextCandidate.sourceConcept,
                targetConcept = nextCandidate.targetConcept,
                confidence = nextCandidate.confidence
              ),
              replyTo = context.self
            )
            timers.startSingleTimer(TranslationTimeout, TranslationTimeoutDuration)
            validating(state.copy(currentIndex = nextIndex, phase = ArbiterPhase.Validating),
                       matcher, validator, escalation, timers)
          } else {
            // Все кандидаты исчерпаны → эскалация
            logger.warn("[S2→S3] {}: все кандидаты отклонены, эскалация оператору", state.requestId)
            escalate(state, matcher, validator, escalation, timers, "Все кандидаты отклонены валидатором")
          }

        case ValidatorCommand.ValidationResult.Warning =>
          // Предупреждение → эскалация
          logger.warn("[S2→S3] {}: предупреждение валидатора, эскалация оператору", state.requestId)
          val violations = report.levels.flatMap(_.violations).mkString("; ")
          escalate(state, matcher, validator, escalation, timers, s"Предупреждение: $violations")
      }

    case TranslationTimeout =>
      logger.error("[S2→S0] {}: тайм-аут валидации", state.requestId)
      state.replyTo ! TranslationResponse(
        requestId = state.requestId,
        translatedQuery = "",
        confidence = 0.0,
        status = TranslationStatus.TimedOut,
        message = "Тайм-аут валидации"
      )
      idle(matcher, validator, escalation, timers)

    case _ =>
      Behaviors.ignore
  }

  /** S3: Escalated — эскалация оператору. */
  private def escalate(
    state: ArbiterState,
    matcher: ActorRef[MatcherCommand],
    validator: ActorRef[ValidatorCommand],
    escalation: ActorRef[EscalationCommand],
    timers: TimerScheduler[ArbiterCommand],
    reason: String
  ): Behavior[ArbiterCommand] = Behaviors.receiveMessage {
    case OperatorDecision(approved, comment, requestId) if requestId == state.requestId =>
      val status = if (approved) TranslationStatus.Accepted else TranslationStatus.Rejected
      val candidate = state.candidates(state.currentIndex)
      logger.info("[S3→S0] {}: оператор {} запрос", requestId, if (approved) "одобрил" else "отклонил")
      state.replyTo ! TranslationResponse(
        requestId = requestId,
        translatedQuery = if (approved) candidate.targetConcept else "",
        confidence = if (approved) candidate.confidence else 0.0,
        status = status,
        message = s"Решение оператора: $comment"
      )
      idle(matcher, validator, escalation, timers)

    case TranslationTimeout =>
      logger.error("[S3→S0] {}: тайм-аут эскалации оператору", state.requestId)
      state.replyTo ! TranslationResponse(
        requestId = state.requestId,
        translatedQuery = "",
        confidence = 0.0,
        status = TranslationStatus.Failed,
        message = "Тайм-аут решения оператора"
      )
      idle(matcher, validator, escalation, timers)

    case _ =>
      Behaviors.ignore
  }
