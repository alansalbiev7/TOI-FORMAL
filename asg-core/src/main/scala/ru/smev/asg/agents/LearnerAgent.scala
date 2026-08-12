package ru.smev.asg.agents

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory
import scala.collection.mutable

// Протокол агента-обучателя (PPO reinforcement learning)
sealed trait LearnerCommand

object LearnerCommand:
  // Регистрация обратной связи по результату перевода
  final case class RecordFeedback(
    translationId: String,
    accepted: Boolean,
    confidence: Double,
    operatorApproved: Boolean = false
  ) extends LearnerCommand

  // Запрос на обновление весов модели
  final case class UpdateWeights(
    replyTo: ActorRef[UpdateResult]
  ) extends LearnerCommand

  // Результат обновления весов
  final case class UpdateResult(
    success: Boolean,
    version: String,
    metrics: Map[String, Double]
  )

  // Запрос текущей версии модели
  final case class GetModelVersion(
    replyTo: ActorRef[String]
  ) extends LearnerCommand

  // Получение статистики обучения
  final case class GetStats(
    replyTo: ActorRef[LearnerStats]
  ) extends LearnerCommand

  final case class LearnerStats(
    totalFeedback: Int,
    positiveRate: Double,
    modelVersion: String,
    lastUpdateTimestamp: Long,
    updatesCount: Int
  )

/**
 * Агент-обучатель: сбор обратной связи и обновление весов модели MatcherAgent.
 *
 * Архитектура:
 *   - Накапливает обратную связь (accepted/rejected, confidence, operatorOverride)
 *   - Каждые 100 записей инициирует обновление весов через PPO-мост
 *   - PPO-мост: HTTP/gRPC вызов Python Stable Baselines3 сервиса
 *   - Возвращает метрики обновления (loss, accuracy, KL-divergence)
 *
 * PPO (Proximal Policy Optimization):
 *   - Policy gradient с обрезкой по clip-ratio
 *   - Value function baseline
 *   - Trust region constraint
 *   - Реализован в Python через Stable Baselines3 (PPO2)
 */
object LearnerAgent:

  private val logger = LoggerFactory.getLogger(getClass)
  private val UpdateThreshold = 100  // обновлять веса после каждых 100 feedback-записей

  // Внутреннее состояние
  private case class LearnerState(
    totalFeedback: Int = 0,
    positiveFeedback: Int = 0,
    operatorOverrides: Int = 0,
    weights: Map[String, Double] = Map.empty,
    lastUpdate: Long = 0L,
    modelVersion: String = "v1.0.0-initial",
    updatesCount: Int = 0,
    pendingFeedback: Int = 0
  )

  def apply(ppoBridgeEndpoint: String): Behavior[LearnerCommand] = Behaviors.setup { context =>
    context.log.info("LearnerAgent инициализирован. PPO bridge: {}", ppoBridgeEndpoint)
    active(LearnerState(), ppoBridgeEndpoint)
  }

  private def active(state: LearnerState, ppoBridgeEndpoint: String): Behavior[LearnerCommand] =
    Behaviors.receiveMessage {
      case RecordFeedback(translationId, accepted, confidence, operatorApproved) =>
        val newState = state.copy(
          totalFeedback = state.totalFeedback + 1,
          positiveFeedback = state.positiveFeedback + (if (accepted) 1 else 0),
          operatorOverrides = state.operatorOverrides + (if (operatorApproved) 1 else 0),
          pendingFeedback = state.pendingFeedback + 1
        )
        logger.info(
          "Feedback #{}: translationId={}, accepted={}, confidence={}, operatorOverride={}",
          newState.totalFeedback, translationId, accepted, confidence, operatorApproved
        )

        // Если накопили достаточно feedback — инициируем обновление весов
        if (newState.pendingFeedback >= UpdateThreshold) {
          logger.info("Порог обновления ({} записей) достигнут — запуск PPO-bridge", UpdateThreshold)
          val updateResult = triggerPpoUpdate(newState, ppoBridgeEndpoint)
          active(newState.copy(
            pendingFeedback = 0,
            lastUpdate = System.currentTimeMillis(),
            modelVersion = updateResult.version,
            updatesCount = newState.updatesCount + 1,
            weights = updateResult.metrics.map { case (k, v) => k -> v.toDouble }
          ), ppoBridgeEndpoint)
        } else {
          active(newState, ppoBridgeEndpoint)
        }

      case UpdateWeights(replyTo) =>
        val updateResult = triggerPpoUpdate(state, ppoBridgeEndpoint)
        replyTo ! updateResult
        active(state.copy(
          pendingFeedback = 0,
          lastUpdate = System.currentTimeMillis(),
          modelVersion = updateResult.version,
          updatesCount = state.updatesCount + 1
        ), ppoBridgeEndpoint)

      case GetModelVersion(replyTo) =>
        replyTo ! state.modelVersion
        Behaviors.same

      case GetStats(replyTo) =>
        val positiveRate = if (state.totalFeedback > 0)
          state.positiveFeedback.toDouble / state.totalFeedback else 0.0
        replyTo ! LearnerStats(
          totalFeedback = state.totalFeedback,
          positiveRate = positiveRate,
          modelVersion = state.modelVersion,
          lastUpdateTimestamp = state.lastUpdate,
          updatesCount = state.updatesCount
        )
        Behaviors.same
    }

  /**
   * Запуск обновления весов через PPO-bridge.
   * В реальной реализации — HTTP/gRPC вызов к Python Stable Baselines3 сервису.
   * Возвращает UpdateResult с метриками обучения.
   */
  private def triggerPpoUpdate(state: LearnerState, ppoBridgeEndpoint: String): UpdateResult =
    val positiveRate = if (state.totalFeedback > 0)
      state.positiveFeedback.toDouble / state.totalFeedback else 0.0
    val timestamp = System.currentTimeMillis()
    val newVersion = s"v1.${state.updatesCount + 1}.${timestamp / 1000}"

    // Заглушка: реальный вызов Python-сервиса PPO через HTTP
    // POST {ppoBridgeEndpoint}/train
    // Body: { "feedback_count": N, "positive_rate": R, "weights": {...} }
    // Response: { "version": "v1.X.Y", "metrics": {...} }
    logger.info("Вызов PPO-bridge: endpoint={}, feedbackCount={}", ppoBridgeEndpoint, state.pendingFeedback)

    UpdateResult(
      success = true,
      version = newVersion,
      metrics = Map(
        "loss" -> 0.0,                // placeholder; реальные метрики от Python-сервиса
        "accuracy" -> positiveRate,    // доля принятых кандидатов
        "kl_divergence" -> 0.0,        // метрика расхождения новой и старой policy
        "entropy" -> 0.0               // метрика исследования (exploration)
      )
    )
