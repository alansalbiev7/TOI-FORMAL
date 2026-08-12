// =============================================================================
// ShaclValidator.scala — SHACL-валидация уровня 1 (структурные ограничения).
//
// Загрузка ограничений OM-1, OM-2, OM-3 из .ttl-файлов каталога shapesDir,
// валидация произвольного RDF-графа через org.apache.jena.shacl.ShaclValidator.
// =============================================================================
package ru.smev.asg.verification

import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.{ShaclValidator => JenaShaclValidator, Shapes, ValidationReport => JenaValidationReport}
import org.slf4j.LoggerFactory

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// -----------------------------------------------------------------------------
// Доменные модели результата.
// -----------------------------------------------------------------------------
/** Severity нарушения (по SHACL-спецификации). */
enum Severity:
  case Info
  case Warning
  case Violation

import Severity._

/** Одно нарушение SHACL. */
final case class ShaclResult(
  focusNode:  String,
  resultPath: Option[String],
  message:    String,
  severity:   Severity
)

/** Полный отчёт валидации. */
final case class ValidationReport(
  conforms: Boolean,
  results:  List[ShaclResult]
)

// -----------------------------------------------------------------------------
// API.
// -----------------------------------------------------------------------------
trait ShaclValidatorApi:
  /** Валидировать `graph` против набора `shapes`. */
  def validate(graph: Model, shapes: Model): ValidationReport

// -----------------------------------------------------------------------------
// Backward-compat трейт для существующих агентов (ValidatorAgent из S2-1a),
// которые вызывают `shacl.validate(model)` (1-арг) и `shacl.conforms(model)`.
// -----------------------------------------------------------------------------
trait ShaclValidator:
  def validate(model: Model): ShaclReport
  def conforms(model: Model): Boolean

/** Backward-compat: отчет валидации (старая сигнатура с violations и rdfReport). */
final case class ShaclReport(
  conforms:    Boolean,
  violations:  List[Violation],
  rdfReport:   String
)

/** Backward-compat: одно нарушение SHACL. */
final case class Violation(
  focusNode: String,
  path:      Option[String],
  severity:  String,
  message:   String,
  value:     Option[String]
)

// =============================================================================
// Реализация.
// =============================================================================
class ShaclValidatorImpl(shapesDir: String) extends ShaclValidatorApi with ShaclValidator:

  private val log = LoggerFactory.getLogger(getClass)

  // Загрузка ограничений OM-1, OM-2, OM-3 из .ttl-файлов при создании.
  private val mergedShapesModel: Model = loadShapes(shapesDir)
  private val shapes: Shapes = Shapes.parse(mergedShapesModel)
  private val jenaValidator: JenaShaclValidator = JenaShaclValidator.getInstance

  log.info("ShaclValidatorImpl: загружено {} триплетов SHACL-форм из {}", mergedShapesModel.size(), shapesDir)

  // ---------------------------------------------------------------------------
  // Загрузка .ttl из каталога.
  // ---------------------------------------------------------------------------
  private def loadShapes(dir: String): Model =
    val merged = ModelFactory.createDefaultModel()
    val path = Paths.get(dir)
    if !Files.exists(path) || !Files.isDirectory(path) then
      log.warn("Каталог SHACL-форм {} недоступен — валидатор будет всегда conform", dir)
      return merged
    val files = Files.list(path).iterator().asScala.toList
      .filter(_.toString.toLowerCase.endsWith(".ttl"))
    files.foreach { p =>
      Try { RDFDataMgr.read(merged, p.toFile.getAbsolutePath) } match
        case Success(_)  => log.debug("SHACL-формы загружены из {}", p)
        case Failure(e) => log.error("Ошибка загрузки {}: {}", p, e.getMessage)
    }
    merged

  // ---------------------------------------------------------------------------
  // Главный метод валидации (по спеке: 2-аргументный API).
  // ---------------------------------------------------------------------------
  override def validate(graph: Model, shapes: Model): ValidationReport =
    val report: JenaValidationReport = jenaValidator.validate(graph, Shapes.parse(shapes))
    toValidationReport(report)

  // ---------------------------------------------------------------------------
  // Backward-compat: 1-аргументный validate(model) — использует предзагруженный shapes.
  // Возвращает старый ShaclReport с violations и rdfReport для совместимости с ValidatorAgent.
  // ---------------------------------------------------------------------------
  override def validate(model: Model): ShaclReport =
    val report: JenaValidationReport = jenaValidator.validate(model, shapes)
    toShaclReport(report)

  override def conforms(model: Model): Boolean =
    jenaValidator.validate(model, shapes).conforms()

  // ---------------------------------------------------------------------------
  // Конвертация отчёта Jena в доменный case class (новый API).
  // ---------------------------------------------------------------------------
  private def toValidationReport(r: JenaValidationReport): ValidationReport =
    val ShNs = "http://www.w3.org/ns/shacl#"
    val model = r.getModel
    val pFocus = model.createProperty(ShNs + "focusNode")
    val pPath  = model.createProperty(ShNs + "resultPath")
    val pSev   = model.createProperty(ShNs + "resultSeverity")
    val pMsg   = model.createProperty(ShNs + "resultMessage")

    val results = model.listSubjectsWithProperty(pFocus).asScala.toList.map { res =>
      val focus = readNode(res, pFocus)
      val path  = Option(readNode(res, pPath)).filter(_.nonEmpty)
      val msg   = readNode(res, pMsg)
      val sev   = readNode(res, pSev) match
        case s if s.endsWith("Violation") => Violation
        case s if s.endsWith("Warning")  => Warning
        case _                            => Info
      ShaclResult(focusNode = focus, resultPath = path, message = msg, severity = sev)
    }
    ValidationReport(conforms = r.conforms(), results = results)

  // ---------------------------------------------------------------------------
  // Конвертация отчёта Jena в старый ShaclReport (для backward-compat).
  // ---------------------------------------------------------------------------
  private def toShaclReport(r: JenaValidationReport): ShaclReport =
    val ShNs = "http://www.w3.org/ns/shacl#"
    val model = r.getModel
    val pFocus = model.createProperty(ShNs + "focusNode")
    val pPath  = model.createProperty(ShNs + "resultPath")
    val pSev   = model.createProperty(ShNs + "resultSeverity")
    val pMsg   = model.createProperty(ShNs + "resultMessage")
    val pVal   = model.createProperty(ShNs + "value")

    val violations = model.listSubjectsWithProperty(pFocus).asScala.toList.map { res =>
      Violation(
        focusNode = readNode(res, pFocus),
        path      = Some(readNode(res, pPath)).filter(_.nonEmpty),
        severity  = readNode(res, pSev),
        message   = readNode(res, pMsg),
        value     = Some(readNode(res, pVal)).filter(_.nonEmpty)
      )
    }
    val sw = new java.io.StringWriter()
    model.write(sw, "TTL")
    ShaclReport(conforms = r.conforms(), violations = violations, rdfReport = sw.toString)

  private def readNode(res: Resource, p: org.apache.jena.rdf.model.Property): String =
    res.listProperties(p).asScala.toList.headOption.flatMap { st =>
      Option(st.getObject).flatMap { o =>
        if o.isLiteral then Some(o.asLiteral().getString)
        else if o.isURIResource then Some(o.asResource().getURI)
        else Some(o.toString)
      }
    }.getOrElse("")

end ShaclValidatorImpl

// -----------------------------------------------------------------------------
// Companion-object с фабрикой.
// -----------------------------------------------------------------------------
object ShaclValidator:
  /** Фабрика: загружает формы из каталога, возвращает готовый валидатор. */
  def apply(shapesDir: String): ShaclValidatorApi = new ShaclValidatorImpl(shapesDir)
end ShaclValidator
