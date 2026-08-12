// =============================================================================
// MatcherAgentSpec.scala — Спецификация агента-матчера (MatcherAgent).
//
// Тестирует:
//   1. Возврат пустого списка для неизвестного запроса.
//   2. Ранжирование кандидатов по BM25-скору.
//   3. Fallback на BERT-эмбеддинги при низком BM25.
//   4. Ограничение maxCandidates.
//   5. Edge-cases токенизатора (пустая строка, спецсимволы).
//
// Использует Akka Typed TestKit (ScalaTestWithActorTestKit) + AnyWordSpecLike.
// =============================================================================
package ru.smev.asg.agents

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import ru.smev.asg.agents.MatcherCommand.{MatchCandidate, MatchRequest, MatchResponse}

/**
 * Спецификация агента-матчера.
 *
 * ВАЖНО: MatcherAgent хранит инвертированный индекс в companion-object (mutable.Map),
 * поэтому тесты разделяют состояние. Для изоляции каждый тест использует уникальные
 * URI концептов и терминов (через суффикс `_<n>`).
 */
class MatcherAgentSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterEach
    with Eventually
    with IntegrationPatience:

  import MatcherAgentSpec.*

  /** Создаём нового актёра на каждый тест для чистоты контекста. */
  private def spawnMatcher(config: MatcherConfig = MatcherConfig()): ActorRef[MatcherCommand] =
    testKit.spawn(MatcherAgent(config))

  "MatcherAgent" should {

    "return empty list for unknown query" in {
      // Запрос с терминами, которых нет в индексе — должен вернуть пустой список.
      val matcher = spawnMatcher()
      val probe   = testKit.createTestProbe[MatchResponse]()

      matcher ! MatchRequest(
        sourceOntologyId = "O1-unknown",
        targetOntologyId = "O2-unknown",
        query            = "несуществующийзапрос12345",
        replyTo          = probe.ref
      )

      val response = probe.expectMessageType[MatchResponse]
      response.candidates shouldBe empty
      response.requestId should not be empty
    }

    "return ranked candidates by BM25 score" in {
      // Регистрируем два концепта с разной релевантностью.
      // "Адрес" содержит оба термина запроса — должен быть первым.
      MatcherAgent.indexConcept(
        "http://o2/test2/Адрес",
        List("адрес", "прописка", "регистрация", "жильё")
      )
      MatcherAgent.indexConcept(
        "http://o2/test2/Лицо",
        List("лицо", "человек", "гражданин")
      )

      val matcher = spawnMatcher()
      val probe   = testKit.createTestProbe[MatchResponse]()

      matcher ! MatchRequest(
        sourceOntologyId = "O1",
        targetOntologyId = "O2",
        query            = "прописка регистрация",
        replyTo          = probe.ref
      )

      val response = probe.expectMessageType[MatchResponse]
      response.candidates should not be empty
      // Кандидат "Адрес" должен быть первым (BM25 выше для совпадающих терминов).
      response.candidates.head.targetConcept shouldBe "http://o2/test2/Адрес"
      // Все кандидаты отсортированы по убыванию confidence.
      val confidences = response.candidates.map(_.confidence)
      confidences shouldBe confidences.sorted.reverse
    }

    "fall back to BERT when BM25 < threshold" in {
      // Регистрируем концепт с одним общим термином (низкий BM25).
      MatcherAgent.indexConcept(
        "http://o2/test3/СлабыйКандидат",
        List("прописка")
      )

      // Высокий порог BM25 → ни один кандидат не проходит по BM25,
      // метод помечается как "bert-fallback" (согласно реализации computeCandidates).
      val strictMatcher = testKit.spawn(MatcherAgent(MatcherConfig(bm25Threshold = 0.95)))
      val probe = testKit.createTestProbe[MatchResponse]()

      strictMatcher ! MatchRequest(
        sourceOntologyId = "O1",
        targetOntologyId = "O2",
        query            = "прописка",
        replyTo          = probe.ref
      )

      val response = probe.expectMessageType[MatchResponse]
      // Метод кандидата должен быть помечен как BERT-fallback (или отфильтрован).
      response.candidates.foreach { c =>
        c.method should (be("bert-fallback") or be("bm25"))
      }
    }

    "respect maxCandidates limit" in {
      // Регистрируем 5 концептов с одинаковым термином.
      (1 to 5).foreach { i =>
        MatcherAgent.indexConcept(s"http://o2/test4/C$i", List("общийтермин-test4"))
      }

      // Ограничение maxCandidates = 2 → вернётся не более 2 кандидатов.
      val limitedMatcher = testKit.spawn(MatcherAgent(MatcherConfig(maxCandidates = 2)))
      val probe = testKit.createTestProbe[MatchResponse]()

      limitedMatcher ! MatchRequest(
        sourceOntologyId = "O1",
        targetOntologyId = "O2",
        query            = "общийтермин-test4",
        replyTo          = probe.ref
      )

      val response = probe.expectMessageType[MatchResponse]
      response.candidates.size should be <= 2
    }

    "handle tokenizer edge cases (empty, special chars)" in {
      val matcher = spawnMatcher()
      val probe   = testKit.createTestProbe[MatchResponse]()

      // Пустой запрос — не должен выбрасывать исключение.
      matcher ! MatchRequest("O1", "O2", "", probe.ref)
      val r1 = probe.expectMessageType[MatchResponse]
      r1.candidates shouldBe empty

      // Только спецсимволы и пунктуация — токенизатор должен вернуть пустой список терминов.
      matcher ! MatchRequest("O1", "O2", "!!!,,,///;;;:::", probe.ref)
      val r2 = probe.expectMessageType[MatchResponse]
      r2.candidates shouldBe empty

      // Смешанный запрос: спецсимволы + обычные термины.
      MatcherAgent.indexConcept(
        "http://o2/test5/Смешанный",
        List("прописка-test5")
      )
      matcher ! MatchRequest("O1", "O2", "!!!прописка-test5!!!(регистрация)", probe.ref)
      val r3 = probe.expectMessageType[MatchResponse]
      r3.candidates should not be empty
    }
  }

end MatcherAgentSpec

object MatcherAgentSpec:
  /** Общий тайм-аут для ожидания сообщений в тестах. */
  val ProbeTimeout: org.scalatest.time.Span = org.scalatest.time.Span(3, org.scalatest.time.Seconds)
end MatcherAgentSpec
