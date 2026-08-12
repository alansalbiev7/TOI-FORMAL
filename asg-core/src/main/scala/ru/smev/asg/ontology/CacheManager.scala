// =============================================================================
// CacheManager.scala — LRU-кэш на базе Redis (Lettuce-клиент).
//
// Актёр-обёртка над Redis: GET / PUT (с TTL) / INvalidate / Stats.
// Считает hits/misses в in-memory mutable-состоянии (для метрик).
// Сам вызов Redis — асинхронный через Future, обработка результатов — в акторе.
// =============================================================================
package ru.smev.asg.ontology

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import io.lettuce.core.{RedisClient, SetArgs}
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.LoggerFactory

import java.time.Duration
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.util.{Failure, Success}

// -----------------------------------------------------------------------------
// Протокол актёра.
// -----------------------------------------------------------------------------
sealed trait CacheCommand

object CacheCommand:
  /** Получить значение по ключу. */
  final case class Get(key: String, replyTo: ActorRef[Option[String]]) extends CacheCommand

  /** Записать значение с TTL (в секундах). */
  final case class Put(key: String, value: String, ttlSeconds: Int) extends CacheCommand

  /** Инвалидировать ключ. */
  final case class Invalidate(key: String) extends CacheCommand

  /** Снять статистику кэша. */
  final case class CacheStats(replyTo: ActorRef[CacheStatsResponse]) extends CacheCommand

  /** Ответ статистики кэша. */
  final case class CacheStatsResponse(hits: Long, misses: Long, size: Long)

  /** Внутренний сигнал: результат асинхронного GET из Redis. */
  private[ontology] final case class GetResult(key: String, value: Option[String], replyTo: ActorRef[Option[String]]) extends CacheCommand

  /** Внутренний сигнал: результат PUT из Redis. */
  private[ontology] final case class PutResult(key: String, success: Boolean) extends CacheCommand

end CacheCommand

import CacheCommand._

// =============================================================================
// Companion-object с фабрикой поведения.
// =============================================================================
object CacheManager:

  private val log = LoggerFactory.getLogger(getClass)

  def apply(redisUrl: String): Behavior[CacheCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("CacheManager: инициализация; redisUrl={}", redisUrl)

      // Создаём Lettuce-клиента (лениво — падение при недоступности Redis
      // не должно убивать актёр, актор логирует и работает в режиме "miss").
      given ExecutionContext = ctx.executionContext

      val (clientOpt, connOpt): (Option[RedisClient], Option[StatefulRedisConnection[String, String]]) =
        try
          val client = RedisClient.create(redisUrl)
          val conn = client.connect()
          ctx.log.info("CacheManager: подключение к Redis установлено")
          (Some(client), Some(conn))
        catch
          case e: Exception =>
            ctx.log.warn("CacheManager: Redis недоступен ({}); кэш работает в режиме miss", e.getMessage)
            (None, None)

      val asyncCommands = connOpt.map(_.async())

      // In-memory статистика.
      var hits: Long = 0L
      var misses: Long = 0L
      // In-memory фоллбэк-кэш (для случая недоступности Redis).
      val fallback: mutable.Map[String, String] = mutable.Map.empty

      // ---------------------------------------------------------------------
      // Главный обработчик.
      // ---------------------------------------------------------------------
      Behaviors.receiveMessage[CacheCommand] {
        case Get(key, replyTo) =>
          asyncCommands match
            case None =>
              misses += 1
              replyTo ! fallback.get(key)
            case Some(cmd) =>
              val fut: Future[String] = cmd.get(key).asScala
              // Перехватываем результат через ctx.self — сохраняем referential transparency.
              fut.onComplete {
                case Success(v) if v != null =>
                  ctx.self ! GetResult(key, Some(v), replyTo)
                case _ =>
                  ctx.self ! GetResult(key, None, replyTo)
              }
          Behaviors.same

        case GetResult(key, value, replyTo) =>
          if value.isDefined then hits += 1 else misses += 1
          replyTo ! value
          Behaviors.same

        case Put(key, value, ttlSeconds) =>
          asyncCommands match
            case None =>
              fallback += key -> value
            case Some(cmd) =>
              val args = SetArgs.Builder.ex(Duration.ofSeconds(ttlSeconds.toLong))
              cmd.set(key, value, args)
                .thenAccept { _ => ctx.self ! PutResult(key, success = true) }
                .exceptionally { _ => ctx.self ! PutResult(key, success = false); null }
          Behaviors.same

        case PutResult(key, success) =>
          if !success then log.warn("CacheManager: сбой PUT для ключа {}", key)
          Behaviors.same

        case Invalidate(key) =>
          asyncCommands.foreach { cmd => cmd.del(key) }
          fallback.remove(key)
          Behaviors.same

        case CacheStats(replyTo) =>
          replyTo ! CacheStatsResponse(hits = hits, misses = misses, size = fallback.size.toLong + (if asyncCommands.isDefined then 1L else 0L))
          Behaviors.same
      }
    }

end CacheManager
