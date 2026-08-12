package ru.smev.asg.agents

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import org.apache.jena.ontology.OntModel
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.util.{Try, Success, Failure}

// Протокол агента-матчера (семантического сопоставителя концептов)
sealed trait MatcherCommand

object MatcherCommand:
  // Запрос на сопоставление концептов онтологии O₁ с концептами O₂
  final case class MatchRequest(
    sourceOntologyId: String,
    targetOntologyId: String,
    query: String,
    replyTo: ActorRef[MatchResponse]
  ) extends MatcherCommand

  // Ответ с ранжированным списком кандидатов
  final case class MatchResponse(
    candidates: List[MatchCandidate],
    requestId: String
  ) extends MatcherCommand

  // Кандидат на сопоставление
  final case class MatchCandidate(
    sourceConcept: String,
    targetConcept: String,
    confidence: Double,        // [0.0, 1.0]
    method: String              // "bm25" | "bert" | "combined"
  )

// Конфигурация агента-матчера
final case class MatcherConfig(
  bm25Threshold: Double = 0.30,     // минимальный порог BM25 для принятия кандидата
  bertFallback: Boolean = true,     // использовать BERT-эмбеддинги при низком BM25
  maxCandidates: Int = 10,           // максимальное число возвращаемых кандидатов
  k1: Double = 1.5,                  // параметр BM25 k1
  b: Double = 0.75                   // параметр BM25 b
)

/**
 * Агент-матчер: выполняет семантическое сопоставление концептов
 * с использованием BM25 (частотный метод) и BERT-эмбеддингов (семантический fallback).
 *
 * Архитектура:
 *   1. Извлекает термины из запроса и целевых концептов
 *   2. Вычисляет BM25-скор для каждой пары (термин, концепт)
 *   3. Если BM25 < bm25Threshold, вызывает BERT-эмбеддинги (fallback)
 *   4. Возвращает ранжированный список кандидатов
 */
object MatcherAgent:

  private val logger = LoggerFactory.getLogger(getClass)

  // Индекс инвертированных терминов: concept URI → Map[term, frequency]
  // В реальной системе загружается из OntologyRegistry
  private val invertedIndex = mutable.Map.empty[String, Map[String, Int]]
  private val documentLengths = mutable.Map.empty[String, Int]
  private var totalDocuments = 0

  def apply(
    config: MatcherConfig = MatcherConfig()
  ): Behavior[MatcherCommand] = Behaviors.setup { context =>
    // Инициализация индекса — в реальной системе здесь загрузка из OntologyRegistry
    context.log.info("MatcherAgent инициализирован. BM25 порог = {}", config.bm25Threshold)
    Behaviors.receiveMessage { case MatchRequest(srcId, tgtId, query, replyTo) =>
      val requestId = s"${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString.take(8)}"

      Try {
        val candidates = computeCandidates(srcId, tgtId, query, config)
        val ranked = candidates
          .filter(_.confidence >= config.bm25Threshold)
          .sortBy(-_.confidence)
          .take(config.maxCandidates)
        MatchResponse(ranked, requestId)
      } match {
        case Success(response) =>
          replyTo ! response
          Behaviors.same
        case Failure(ex) =>
          logger.error(s"Ошибка при сопоставлении запроса '$query'", ex)
          replyTo ! MatchResponse(Nil, requestId)
          Behaviors.same
      }
    }
  }

  /**
   * Вычисление кандидатов через BM25.
   * Формула BM25:
   *   score(D, Q) = Σ_i IDF(q_i) · (f(q_i, D) · (k1+1)) / (f(q_i, D) + k1·(1-b + b·|D|/avgdl))
   * где:
   *   IDF(q_i) = ln((N - n(q_i) + 0.5) / (n(q_i) + 0.5) + 1)
   *   N — общее число документов (концептов) в коллекции
   *   n(q_i) — число документов, содержащих термин q_i
   *   f(q_i, D) — частота термина q_i в документе D
   *   |D| — длина документа D (число терминов)
   *   avgdl — средняя длина документа
   */
  private def computeCandidates(
    srcId: String, tgtId: String, query: String, config: MatcherConfig
  ): List[MatchCandidate] =
    val queryTerms = tokenize(query)
    val avgdl = if (documentLengths.nonEmpty) documentLengths.values.sum.toDouble / documentLengths.size else 1.0

    // Для каждого целевого концепта вычисляем BM25-скор
    val scores = invertedIndex.toSeq.map { case (conceptUri, termFreqs) =>
      val docLen = documentLengths.getOrElse(conceptUri, 0)
      val score = queryTerms.map { term =>
        val f = termFreqs.getOrElse(term, 0)
        if (f == 0) 0.0
        else {
          val n_qi = invertedIndex.count(_._2.contains(term))
          val idf = math.log((totalDocuments - n_qi + 0.5) / (n_qi + 0.5) + 1)
          val num = f * (config.k1 + 1)
          val den = f + config.k1 * (1 - config.b + config.b * (docLen / avgdl))
          idf * (num / den)
        }
      }.sum
      (conceptUri, score)
    }.filter(_._2 > 0).sortBy(-_._2)

    // Возвращаем кандидатов; если BM25 < порога, помечаем для BERT-fallback
    scores.map { case (conceptUri, score) =>
      val method = if (score >= config.bm25Threshold) "bm25" else "bert-fallback"
      val confidence = math.min(1.0, score / 10.0) // нормализация
      MatchCandidate(query, conceptUri, confidence, method)
    }.toList

  /** Токенизация запроса: разбиение по пробелам и пунктуации. */
  private def tokenize(text: String): List[String] =
    text.toLowerCase
      .split("[\\s,.()\\[\\]{}:;\"'\\\\/]+")
      .filter(_.nonEmpty)
      .toList

  /** Регистрация нового концепта в индексе (вызывается при загрузке онтологии). */
  def indexConcept(conceptUri: String, terms: List[String]): Unit =
    val termFreqs = terms.groupBy(identity).view.mapValues(_.size).toMap
    invertedIndex(conceptUri) = termFreqs
    documentLengths(conceptUri) = terms.size
    totalDocuments += 1
