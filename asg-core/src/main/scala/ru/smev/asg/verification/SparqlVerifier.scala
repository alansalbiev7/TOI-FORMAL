// =============================================================================
// SparqlVerifier.scala — SPARQL-верификатор уровня 3 (семантические инварианты).
//
// Загружает запросы из ss1-verify.rq и ss2-verify.rq, выполняет их против
// объединённой модели O₁ ∪ O₂ ∪ m. Каждая строка результата — нарушение.
// =============================================================================
package ru.smev.asg.verification

import org.apache.jena.query.{Query, QueryExecutionFactory, QueryFactory, ResultSet}
import org.apache.jena.rdf.model.Model
import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}

// -----------------------------------------------------------------------------
// Доменные модели.
// -----------------------------------------------------------------------------
/** Результат проверки: passed=true если нарушений нет, иначе violations. */
final case class VerificationResult(
  passed:     Boolean,
  violations: List[String]
)

// -----------------------------------------------------------------------------
// API.
// -----------------------------------------------------------------------------
trait SparqlVerifierApi:
  /** SS-1: выполнимость запросов (no unsatisfiable patterns). */
  def verifySS1(model: Model): VerificationResult

  /** SS-2: двустороннее сохранение инконсистентности. */
  def verifySS2(model: Model): VerificationResult

// =============================================================================
// Реализация.
// =============================================================================
class SparqlVerifierImpl(sparqlDir: String) extends SparqlVerifierApi:

  private val log = LoggerFactory.getLogger(getClass)

  // Загрузка SPARQL-запросов из файлов при создании.
  private val ss1Query: Option[Query] = loadQuery(sparqlDir, "ss1-verify.rq")
  private val ss2Query: Option[Query] = loadQuery(sparqlDir, "ss2-verify.rq")

  log.info("SparqlVerifierImpl: SS-1={}, SS-2={} (каталог {})",
    ss1Query.isDefined, ss2Query.isDefined, sparqlDir)

  // ---------------------------------------------------------------------------
  // Загрузка .rq-файла в Query-объект.
  // ---------------------------------------------------------------------------
  private def loadQuery(dir: String, fileName: String): Option[Query] =
    val path = Paths.get(dir, fileName)
    if !Files.exists(path) then
      log.warn("SPARQL-файл {} не найден — проверка SS будет пропущена", path)
      return None
    val text = Files.readAllLines(path).asScala.mkString("\n")
    Try(org.apache.jena.query.QueryFactory.create(text)) match
      case Success(q) => Some(q)
      case Failure(e) =>
        log.error("Ошибка парсинга {}: {}", path, e.getMessage)
        None
  end loadQuery

  // ---------------------------------------------------------------------------
  // Выполнение SELECT-запроса; каждая строка ResultSet — нарушение.
  // ---------------------------------------------------------------------------
  private def execute(model: Model, query: Query): List[String] =
    val qe = QueryExecutionFactory.create(query, model)
    try
      val rs: ResultSet = qe.execSelect()
      val vars = rs.getResultVars.asScala.toList
      rs.asScala.toList.map { sol =>
        // Строковое представление строки результата.
        vars.flatMap(v => Option(sol.get(v)).map(_.toString)).mkString(" | ")
      }
    finally qe.close()

  // ---------------------------------------------------------------------------
  // Реализация интерфейса.
  // ---------------------------------------------------------------------------
  override def verifySS1(model: Model): VerificationResult =
    ss1Query match
      case None     => VerificationResult(passed = true, violations = Nil)
      case Some(q)  =>
        val violations = execute(model, q)
        if violations.nonEmpty then
          log.info("SS-1: {} нарушений (первые 3: {})",
            violations.size, violations.take(3).mkString("; "))
        VerificationResult(passed = violations.isEmpty, violations = violations)

  override def verifySS2(model: Model): VerificationResult =
    ss2Query match
      case None     => VerificationResult(passed = true, violations = Nil)
      case Some(q)  =>
        val violations = execute(model, q)
        if violations.nonEmpty then
          log.info("SS-2: {} нарушений (первые 3: {})",
            violations.size, violations.take(3).mkString("; "))
        VerificationResult(passed = violations.isEmpty, violations = violations)

end SparqlVerifierImpl

// -----------------------------------------------------------------------------
// Companion-object с фабрикой.
// -----------------------------------------------------------------------------
object SparqlVerifier:
  /** Фабрика: загружает .rq-файлы из каталога, возвращает готовый верификатор. */
  def apply(sparqlDir: String): SparqlVerifierApi = new SparqlVerifierImpl(sparqlDir)
end SparqlVerifier
