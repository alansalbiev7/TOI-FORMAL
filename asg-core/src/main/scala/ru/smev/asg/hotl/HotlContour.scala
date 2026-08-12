// =============================================================================
// HotlContour.scala — Эскалация оператору СМЭВ (Human-in-the-loop).
//
// Очередь решений оператора: при Warning из ValidatorAgent или при тайм-ауте
// ArbiterAgent передаёт управление сюда. Запросы ставятся в очередь, оператор
// асинхронно выносит решение (OperatorDecisionQueue), которое возвращается
// в ArbiterAgent через replyTo.
//
// Тайм-аут: запросы в очереди более 1 часа авто-отклоняются.
// =============================================================================
package ru.smev.asg.hotl

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory

import ru.smev.asg.agents.MatcherAgent.MatchCandidate
import ru.smev.asg.verification.ValidationReport

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration, HOURS}

// -----------------------------------------------------------------------------
// Доменные модели.
// -----------------------------------------------------------------------------
/** Эскалированный запрос в очереди оператора. */
final case class HotLRequest(
  requestId:         String,
  sourceOntologyId:  String,
  targetOntologyId:  String,
  query:             String,
  candidates:        List[MatchCandidate],
  report:            ValidationReport,
  escalatedAt:       Instant,
  operator:          Option[String] = None
)

// -----------------------------------------------------------------------------
// Протокол актёра.
// -----------------------------------------------------------------------------
sealed trait EscalationCommand

object EscalationCommand:
  /** Запрос на эскалацию от ArbiterAgent. */
  final case class Escalate(
    request:    ru.smev.asg.agents.ArbiterAgent.TranslateRequest,
    candidates: List[MatchCandidate],
    report:     ValidationReport,
    replyTo:    ActorRef[EscalationResponse]
  ) extends EscalationCommand

  /** Ответ: одобрено/отклонено оператором (approved=false до решения). */
  final case class EscalationResponse(
    approved:    Boolean,
    operatorId:  String,
    comment:     String
  )

  /** Оператор вынес решение по конкретному requestId. */
  final case class OperatorDecisionQueue(
    requestId: String,
    approved:  Boolean,
    comment:    String
  ) extends EscalationCommand

  /** Внутренний сигнал: тайм-аут очереди (запрос ждёт > 1 часа). */
  private[hotl] final case class QueueTimeout(requestId: String) extends EscalationCommand

end EscalationCommand

import EscalationCommand._

// =============================================================================
// Companion-object EscalationManager.
// =============================================================================
object EscalationManager:

  private val log = LoggerFactory.getLogger(getClass)

  /** Тайм-аут нахождения в очереди (1 час по умолчанию). */
  private val DefaultTimeout: FiniteDuration = Duration(1, HOURS)

  /** @param operatorQueueSize максимальный размер очереди оператора. */
  def apply(operatorQueueSize: Int): Behavior[EscalationCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("EscalationManager: старт; queueCapacity={}", operatorQueueSize)

      // Очередь эскалированных запросов (LinkedHashMap — сохраняет порядок).
      val queue: mutable.LinkedHashMap[String, HotLRequest] = mutable.LinkedHashMap.empty
      // Каналы обратной связи к ArbiterAgent по requestId.
      val replyChannels: mutable.Map[String, ActorRef[EscalationResponse]] = mutable.Map.empty

      def scheduleTimeout(reqId: String): Unit =
        ctx.scheduleOnce(DefaultTimeout, ctx.self, QueueTimeout(reqId))

      // ---------------------------------------------------------------------
      // Главный обработчик.
      // ---------------------------------------------------------------------
      Behaviors.receiveMessage[EscalationCommand] {
        case Escalate(request, candidates, report, replyTo) =>
          if queue.size >= operatorQueueSize then
            log.warn("EscalationManager: очередь переполнена ({}); авто-отклонение requestId={}",
              queue.size, request.requestId)
            replyTo ! EscalationResponse(approved = false, operatorId = "system",
              comment = "Очередь эскалации переполнена")
          else
            val hotReq = HotLRequest(
              requestId        = request.requestId,
              sourceOntologyId = request.sourceOntology,
              targetOntologyId = request.targetOntology,
              query            = request.concept,
              candidates       = candidates,
              report           = report,
              escalatedAt      = Instant.now
            )
            queue += request.requestId -> hotReq
            replyChannels += request.requestId -> replyTo
            log.warn("Эскалация оператору СМЭВ: requestId={} violations={}",
              request.requestId, report.results.size)
            // Возвращаем approved=false (ожидаем решения оператора).
            replyTo ! EscalationResponse(approved = false, operatorId = "",
              comment = "Запрос поставлен в очередь оператора")
            scheduleTimeout(request.requestId)
          Behaviors.same

        case OperatorDecisionQueue(reqId, approved, comment) =>
          (queue.remove(reqId), replyChannels.remove(reqId)) match
            case (Some(_), Some(channel)) =>
              val operator = "operator-1"
              log.info("Решение оператора: requestId={} approved={} comment='{}'",
                reqId, approved, comment)
              channel ! EscalationResponse(approved = approved, operatorId = operator, comment = comment)
            case _ =>
              log.warn("OperatorDecisionQueue: requestId={} не найден в очереди", reqId)
          Behaviors.same

        case QueueTimeout(reqId) =>
          (queue.remove(reqId), replyChannels.remove(reqId)) match
            case (Some(_), Some(channel)) =>
              log.warn("QueueTimeout: requestId={} в очереди > {} — авто-отклонение",
                reqId, DefaultTimeout)
              channel ! EscalationResponse(approved = false, operatorId = "system",
                comment = s"Тайм-аут очереди (${DefaultTimeout})")
            case _ => () // уже разрешено — игнор.
          Behaviors.same
      }
    }

end EscalationManager

// -----------------------------------------------------------------------------
// Backward-compat type aliases для кода (ArbiterAgent), который ссылается на
// HotlContour.Command / HotlContour.Escalate / HotlContour.OperatorVerdict.
// Эти алиасы резолвят типы на уровне компилятора, не меняя поведение.
// -----------------------------------------------------------------------------
object HotlContour:
  type Command = EscalationCommand
  type Escalate = EscalationCommand.Escalate
  type OperatorVerdict = EscalationCommand.EscalationResponse
  val Escalate = EscalationCommand.Escalate
  val OperatorVerdict = EscalationCommand.EscalationResponse
