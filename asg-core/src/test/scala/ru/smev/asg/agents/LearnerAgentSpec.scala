// =============================================================================
// LearnerAgentSpec.scala — Спецификация агента-обучателя (LearnerAgent).
//
// Тестирует:
//   1. Регистрация feedback и инкремент счётчиков.
//   2. Запуск PPO-обновления после накопления 100 записей feedback.
//   3. Возврат версии модели по GetModelVersion.
//   4. Возврат статистики по GetStats.
//
// LearnerAgent накапливает обратную связь (accepted/rejected, confidence) и
// каждые 100 записей инициирует обновление весов через PPO-bridge (заглушка).
// =============================================================================
package ru.smev.asg.agents

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import ru.smev.asg.agents.LearnerCommand.*

class LearnerAgentSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterEach:

  import LearnerAgentSpec.*

  /** PPO-bridge endpoint (заглушка — агент не делает реальных HTTP-вызовов в тестах). */
  private val TestPpoEndpoint = "http://localhost:9999/train"

  /** Создаёт LearnerAgent для теста. */
  private def spawnLearner(): akka.actor.typed.ActorRef[LearnerCommand] =
    testKit.spawn(LearnerAgent(TestPpoEndpoint))

  "LearnerAgent" should {

    "record feedback and increment counters" in {
      val learner = spawnLearner()
      val statsProbe = testKit.createTestProbe[LearnerStats]()

      // Отправляем три записи feedback: две accepted, одну rejected.
      learner ! RecordFeedback("t-1", accepted = true, confidence = 0.9)
      learner ! RecordFeedback("t-2", accepted = true, confidence = 0.8, operatorApproved = true)
      learner ! RecordFeedback("t-3", accepted = false, confidence = 0.3)

      // Запрашиваем статистику.
      learner ! GetStats(statsProbe.ref)

      val stats = statsProbe.expectMessageType[LearnerStats]
      stats.totalFeedback shouldBe 3
      // positiveRate = 2/3 (две accepted из трёх).
      stats.positiveRate shouldBe (2.0 / 3.0 +- 0.001)
      stats.updatesCount shouldBe 0  // порог 100 не достигнут
    }

    "trigger PPO update after 100 feedback records" in {
      val learner = spawnLearner()
      val versionProbe = testKit.createTestProbe[String]()

      // Отправляем 100 записей feedback — после 100-й должен сработать PPO-bridge.
      (1 to 100).foreach { i =>
        learner ! RecordFeedback(s"t-$i", accepted = i % 2 == 0, confidence = 0.5)
      }

      // После PPO-обновления версия модели должна измениться (не быть "v1.0.0-initial").
      learner ! GetModelVersion(versionProbe.ref)
      val version = versionProbe.expectMessageType[String]
      version should not be "v1.0.0-initial"
      // Новая версия имеет формат "v1.<updatesCount>.<timestamp>".
      version should startWith("v1.")

      // Проверяем, что updatesCount инкрементирован.
      val statsProbe = testKit.createTestProbe[LearnerStats]()
      learner ! GetStats(statsProbe.ref)
      val stats = statsProbe.expectMessageType[LearnerStats]
      stats.updatesCount shouldBe 1
      stats.lastUpdateTimestamp should be > 0L
    }

    "return model version on GetModelVersion" in {
      val learner = spawnLearner()
      val probe = testKit.createTestProbe[String]()

      // До任何 feedback версия должна быть начальной.
      learner ! GetModelVersion(probe.ref)
      val v1 = probe.expectMessageType[String]
      v1 shouldBe "v1.0.0-initial"

      // После явного UpdateWeights версия должна измениться.
      val resultProbe = testKit.createTestProbe[UpdateResult]()
      learner ! UpdateWeights(resultProbe.ref)
      val result = resultProbe.expectMessageType[UpdateResult]
      result.success shouldBe true
      result.version should not be "v1.0.0-initial"
      // Метрики PPO должны присутствовать в результате.
      result.metrics should contain key "loss"
      result.metrics should contain key "accuracy"
      result.metrics should contain key "kl_divergence"
    }

    "return stats on GetStats" in {
      val learner = spawnLearner()
      val probe = testKit.createTestProbe[LearnerStats]()

      // На свежем агенте статистика должна быть нулевой.
      learner ! GetStats(probe.ref)
      val s0 = probe.expectMessageType[LearnerStats]
      s0.totalFeedback shouldBe 0
      s0.positiveRate shouldBe 0.0
      s0.modelVersion shouldBe "v1.0.0-initial"
      s0.lastUpdateTimestamp shouldBe 0L
      s0.updatesCount shouldBe 0

      // После одной записи feedback статистика обновляется.
      learner ! RecordFeedback("t-1", accepted = true, confidence = 0.95)
      learner ! GetStats(probe.ref)
      val s1 = probe.expectMessageType[LearnerStats]
      s1.totalFeedback shouldBe 1
      s1.positiveRate shouldBe 1.0
    }
  }

end LearnerAgentSpec

object LearnerAgentSpec:
  /** Порог обновления весов в LearnerAgent (см. исходный файл). */
  val UpdateThreshold: Int = 100
end LearnerAgentSpec
