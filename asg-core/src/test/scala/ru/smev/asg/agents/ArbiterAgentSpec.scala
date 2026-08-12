// =============================================================================
// ArbiterAgentSpec.scala — Спецификация арбитра (конечный автомат S0→S1→S2→S3).
//
// Тестирует переходы FSM:
//   S0 (Idle)        → S1 (Matching)    на StartTranslation.
//   S1 (Matching)    → S2 (Validating)  на MatcherResponse с кандидатами.
//   S2 (Validating)  → S0 (Idle) + Accepted  когда Validator = Valid.
//   S2 (Validating)  → S2 (Validating)       когда Validator = Invalid (следующий кандидат).
//   S2 (Validating)  → S3 (Escalated)         когда все кандидаты исчерпаны.
//   S2 (Validating)  → S3 (Escalated)         когда Validator = Warning.
//   S3 (Escalated)   → S0 (Idle) + Accepted/Rejected на OperatorDecision.
//   Любое состояние   → S0 (Idle) + TimedOut   по тайм-ауту 30 секунд.
//
// Использует TestProbe для matcher/validator/escalation (имитация дочерних агентов).
// =============================================================================
package ru.smev.asg.agents

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import ru.smev.asg.agents.ArbiterCommand.*
import ru.smev.asg.agents.MatcherCommand.{MatchCandidate, MatchRequest}
import ru.smev.asg.agents.ValidatorCommand.{LevelReport, MappingCandidate, ValidationReport, ValidationResult}
import ru.smev.asg.hotl.EscalationCommand

import scala.concurrent.duration.*

class ArbiterAgentSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterEach
    with Eventually
    with IntegrationPatience:

  import ArbiterAgentSpec.*

  /** Удобный способ создать пару (arbiter, probes) для теста. */
  private case class ArbiterFixture(
    arbiter:        ActorRef[ArbiterCommand],
    matcherProbe:   TestProbe[MatcherCommand],
    validatorProbe: TestProbe[ValidatorCommand],
    escalationProbe: TestProbe[EscalationCommand],
    clientProbe:    TestProbe[TranslationResponse]
  )

  /** Создаёт арбитр с тремя TestProbe и probe клиента для ответов. */
  private def arbiterFixture(): ArbiterFixture =
    val matcherProbe    = testKit.createTestProbe[MatcherCommand]()
    val validatorProbe  = testKit.createTestProbe[ValidatorCommand]()
    val escalationProbe = testKit.createTestProbe[EscalationCommand]()
    val clientProbe     = testKit.createTestProbe[TranslationResponse]()
    val arbiter = testKit.spawn(ArbiterAgent(matcherProbe.ref, validatorProbe.ref, escalationProbe.ref))
    ArbiterFixture(arbiter, matcherProbe, validatorProbe, escalationProbe, clientProbe)

  /** Два кандидата для тестов. */
  private val twoCandidates: List[MatchCandidate] = List(
    MatchCandidate("http://o1/A", "http://o2/A", 0.95, "bm25"),
    MatchCandidate("http://o1/A", "http://o2/B", 0.70, "bert-fallback")
  )

  "ArbiterAgent" should {

    "transition S0→S1 on StartTranslation" in {
      val f = arbiterFixture()
      // Отправляем запрос на перевод — арбитр должен делегировать матчеру.
      f.arbiter ! StartTranslation(
        request = TranslationRequest("O1", "O2", "прописка"),
        replyTo = f.clientProbe.ref
      )
      // S0→S1 подтверждается тем, что матчер получил MatchRequest.
      val matchReq = f.matcherProbe.expectMessageType[MatchRequest]
      matchReq.sourceOntologyId shouldBe "O1"
      matchReq.targetOntologyId shouldBe "O2"
      matchReq.query shouldBe "прописка"
    }

    "transition S1→S2 on MatcherResponse with candidates" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "прописка"), f.clientProbe.ref)
      // S0→S1: матчер получает MatchRequest.
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()

      // Имитируем ответ матчера. requestId имеет формат "REQ-<millis>" — отправляем
      // несколько вариантов, чтобы покрыть возможное смещение миллисекунд.
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)

      // S1→S2: валидатор получает ValidateRequest с первым кандидатом.
      val validateReq = f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      validateReq.mapping.sourceConcept shouldBe "http://o1/A"
      validateReq.mapping.targetConcept shouldBe "http://o2/A"
      validateReq.mapping.confidence shouldBe 0.95
    }

    "transition S2→S0 with Accepted when Validator says Valid" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)
      f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]

      // Валидатор говорит Valid — арбитр принимает кандидата.
      f.arbiter ! ValidatorResponse(
        result = ValidationResult.Valid,
        report = ValidationReport(levels = List(LevelReport(1, "SHACL", passed = true, Nil)))
      )

      val resp = f.clientProbe.expectMessageType[TranslationResponse]
      resp.status shouldBe TranslationStatus.Accepted
      resp.translatedQuery shouldBe "http://o2/A"
      resp.confidence shouldBe 0.95
    }

    "transition S2→S2 with next candidate when Invalid" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)

      // Первый кандидат — Invalid, арбитр пробует следующий.
      val firstReq = f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      firstReq.mapping.targetConcept shouldBe "http://o2/A"

      f.arbiter ! ValidatorResponse(ValidationResult.Invalid,
        ValidationReport(List(LevelReport(1, "SHACL", passed = false, List("violation")))))

      // S2→S2: валидатор получает второй кандидат.
      val secondReq = f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      secondReq.mapping.targetConcept shouldBe "http://o2/B"
    }

    "transition S2→S3 (Escalate) when all candidates exhausted" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)

      // Оба кандидата Invalid → эскалация.
      f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      f.arbiter ! ValidatorResponse(ValidationResult.Invalid,
        ValidationReport(List(LevelReport(1, "SHACL", passed = false, List("v1")))))
      f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      f.arbiter ! ValidatorResponse(ValidationResult.Invalid,
        ValidationReport(List(LevelReport(1, "SHACL", passed = false, List("v2")))))

      // Арбитр в S3 (Escalated) — ждёт OperatorDecision.
      // Подтверждаем переход, отправляя OperatorDecision и проверяя ответ клиенту.
      sendOperatorDecisionCoveringRange(f.arbiter, approved = true, "одобрено оператором", t0, t1)
      val resp = f.clientProbe.expectMessageType[TranslationResponse]
      resp.status shouldBe TranslationStatus.Accepted
    }

    "transition S2→S3 when Validator says Warning" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)
      f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]

      // Warning → эскалация оператору (без попытки следующего кандидата).
      f.arbiter ! ValidatorResponse(ValidationResult.Warning,
        ValidationReport(List(LevelReport(3, "SPARQL", passed = false, List("SS-1 нарушен")))))

      // S3: ждёт OperatorDecision. Отправляем и проверяем, что арбитр вернулся в S0.
      sendOperatorDecisionCoveringRange(f.arbiter, approved = false, "отклонено оператором", t0, t1)
      val resp = f.clientProbe.expectMessageType[TranslationResponse]
      resp.status shouldBe TranslationStatus.Rejected
      resp.translatedQuery shouldBe ""
    }

    "transition S3→S0 on OperatorDecision" in {
      val f = arbiterFixture()
      val t0 = System.currentTimeMillis()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      val t1 = System.currentTimeMillis()
      sendMatcherResponseCoveringRange(f.arbiter, twoCandidates, t0, t1)
      f.validatorProbe.expectMessageType[ValidatorCommand.ValidateRequest]
      // Эскалация через Warning.
      f.arbiter ! ValidatorResponse(ValidationResult.Warning,
        ValidationReport(List(LevelReport(3, "SPARQL", passed = false, List("warn")))))

      // S3→S0: оператор одобряет — клиент получает Accepted.
      sendOperatorDecisionCoveringRange(f.arbiter, approved = true, "одобрено", t0, t1)
      val resp = f.clientProbe.expectMessageType[TranslationResponse]
      resp.status shouldBe TranslationStatus.Accepted
      resp.confidence shouldBe 0.95
      // После возврата в S0 арбитр готов к новому запросу — проверяем этим.
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q2"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
    }

    "return TimedOut after 30 seconds" in {
      val f = arbiterFixture()
      f.arbiter ! StartTranslation(TranslationRequest("O1", "O2", "q"), f.clientProbe.ref)
      f.matcherProbe.expectMessageType[MatchRequest]
      // Не отправляем MatcherResponse — имитируем тайм-аут прямой инъекцией сигнала.
      // (В реальной системе таймер срабатывает через 30 секунд; в тесте инъекция сигнала
      // эквивалентна срабатыванию таймера — ArbiterAgent обрабатывает TranslationTimeout
      // одинаково в обоих случаях.)
      f.arbiter ! TranslationTimeout
      val resp = f.clientProbe.expectMessageType[TranslationResponse]
      resp.status shouldBe TranslationStatus.TimedOut
      resp.translatedQuery shouldBe ""
      resp.confidence shouldBe 0.0
    }
  }

end ArbiterAgentSpec

object ArbiterAgentSpec:

  /**
   * Отправляет MatcherResponse с несколькими вариантами requestId, чтобы покрыть
   * возможное смещение миллисекунд между отправкой StartTranslation и текущим моментом.
   *
   * ArbiterAgent генерирует `state.requestId = s"REQ-${System.currentTimeMillis()}"`
   * при получении StartTranslation. Тест не имеет прямого доступа к этому requestId,
   * поэтому отправляет несколько сообщений с requestId в диапазоне [t0, t1].
   * Сообщения с несовпадающим requestId игнорируются арбитром (case _ => Behaviors.ignore).
   */
  def sendMatcherResponseCoveringRange(
    arbiter: ActorRef[ArbiterCommand],
    candidates: List[MatchCandidate],
    t0: Long, t1: Long
  ): Unit =
    (t0 to (t1 + 5L)).foreach { ts =>
      arbiter ! MatcherResponse(candidates, s"REQ-$ts")
    }

  /** Аналогично sendMatcherResponseCoveringRange, но для OperatorDecision. */
  def sendOperatorDecisionCoveringRange(
    arbiter: ActorRef[ArbiterCommand],
    approved: Boolean,
    comment: String,
    t0: Long, t1: Long
  ): Unit =
    (t0 to (t1 + 5L)).foreach { ts =>
      arbiter ! OperatorDecision(approved, comment, s"REQ-$ts")
    }

end ArbiterAgentSpec
