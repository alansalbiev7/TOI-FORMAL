package ru.smev.asg.agents

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory
import ru.smev.asg.verification.*
import scala.util.{Try, Success, Failure}

// Протокол агента-валидатора (трёхуровневая верификация соответствий)
sealed trait ValidatorCommand

object ValidatorCommand:
  // Запрос на верификацию кандидата сопоставления
  final case class ValidateRequest(
    mapping: MappingCandidate,
    replyTo: ActorRef[ValidateResponse]
  ) extends ValidatorCommand

  // Ответ с результатом трёхуровневой верификации
  final case class ValidateResponse(
    result: ValidationResult,
    report: ValidationReport
  ) extends ValidatorCommand

  // Результат верификации
  enum ValidationResult:
    case Valid, Warning, Invalid

  // Кандидат сопоставления (для совместимости с MatcherAgent)
  final case class MappingCandidate(
    sourceConcept: String,
    targetConcept: String,
    confidence: Double
  )

  // Полный отчёт верификации по трём уровням
  final case class ValidationReport(
    levels: List[LevelReport]
  ):
    def isConformant: Boolean = levels.forall(_.passed)
    def totalViolations: Int = levels.map(_.violations.size).sum

  // Отчёт по одному уровню верификации
  final case class LevelReport(
    level: Int,         // 1, 2, 3
    name: String,       // "SHACL", "OWL2RL", "SPARQL"
    passed: Boolean,
    violations: List[String]
  )

/**
 * Агент-валидатор: трёхуровневая верификация онтологических соответствий.
 *
 * Уровень 1 (SHACL): проверка структурных ограничений OM-1, OM-2, OM-3, SS-2′.
 *   - Загружает SHACL-формы из /app/shapes/*.ttl
 *   - Выполняет валидацию RDF-графа отображения
 *   - Если найдены sh:Violation → Invalid (ранний возврат)
 *
 * Уровень 2 (OWL2RL): проверка логической непротиворечивости через reasoning.
 *   - Запускает OWL2RL-reasoner над объединённой онтологией O₁ ∪ O₂ ∪ mappings
 *   - Если выводится owl:Nothing → Invalid
 *   - Если найдены unsatisfiable classes → Warning
 *
 * Уровень 3 (SPARQL): проверка семантических инвариантов SS-1 и SS-2′.
 *   - Выполняет SPARQL-запросы из /app/sparql/ss1-verify.rq и ss2-verify.rq
 *   - Каждый результат запроса — нарушение
 *   - Накапливает список нарушений
 */
object ValidatorAgent:

  private val logger = LoggerFactory.getLogger(getClass)

  def apply(
    shaclValidator: ShaclValidatorApi,
    owlReasoner: OwlReasonerApi,
    sparqlVerifier: SparqlVerifierApi
  ): Behavior[ValidatorCommand] = Behaviors.setup { context =>
    context.log.info("ValidatorAgent инициализирован: SHACL + OWL2RL + SPARQL")
    Behaviors.receiveMessage { case ValidateRequest(mapping, replyTo) =>
      val report = performValidation(mapping, shaclValidator, owlReasoner, sparqlVerifier)
      val result = determineResult(report)
      replyTo ! ValidateResponse(result, report)
      Behaviors.same
    }
  }

  /** Выполнение трёхуровневой верификации. */
  private def performValidation(
    mapping: MappingCandidate,
    shaclValidator: ShaclValidatorApi,
    owlReasoner: OwlReasonerApi,
    sparqlVerifier: SparqlVerifierApi
  ): ValidationReport =
    val levels = List.newBuilder[LevelReport]

    // Уровень 1: SHACL-валидация структурных ограничений
    val level1 = Try {
      val shaclReport = shaclValidator.validateMapping(mapping.sourceConcept, mapping.targetConcept)
      LevelReport(
        level = 1,
        name = "SHACL",
        passed = shaclReport.conforms,
        violations = shaclReport.results.filter(_.severity == Severity.Violation).map(_.message)
      )
    } match {
      case Success(r) => r
      case Failure(ex) =>
        logger.error("Ошибка SHACL-валидации", ex)
        LevelReport(1, "SHACL", passed = false, List(s"SHACL engine error: ${ex.getMessage}"))
    }
    levels += level1

    // Ранний возврат при нарушениях SHACL
    if (!level1.passed) {
      return ValidationReport(levels.result())
    }

    // Уровень 2: OWL2RL-consistency checking
    val level2 = Try {
      // В реальной реализации здесь: объединение моделей O₁ ∪ O₂ ∪ mapping → reasoner
      // Заглушка: считаем, что reasoner возвращает true для согласованных отображений
      val isConsistent = owlReasoner.checkConsistency(mapping.sourceConcept, mapping.targetConcept)
      val unsatisfiable = owlReasoner.getUnsatisfiableClasses(mapping.sourceConcept)
      LevelReport(
        level = 2,
        name = "OWL2RL",
        passed = isConsistent && unsatisfiable.isEmpty,
        violations = if (unsatisfiable.nonEmpty) List(s"Несогласованные классы: ${unsatisfiable.mkString(", ")}") else Nil
      )
    } match {
      case Success(r) => r
      case Failure(ex) =>
        logger.error("Ошибка OWL2RL-reasoning", ex)
        LevelReport(2, "OWL2RL", passed = false, List(s"Reasoner error: ${ex.getMessage}"))
    }
    levels += level2

    if (!level2.passed) {
      return ValidationReport(levels.result())
    }

    // Уровень 3: SPARQL-проверка SS-1 и SS-2'
    val level3 = Try {
      val ss1Result = sparqlVerifier.verifySS1(mapping.sourceConcept, mapping.targetConcept)
      val ss2Result = sparqlVerifier.verifySS2(mapping.sourceConcept, mapping.targetConcept)
      val violations = List.newBuilder[String]
      if (!ss1Result.passed) violations += s"SS-1 нарушен: ${ss1Result.violations.mkString("; ")}"
      if (!ss2Result.passed) violations += s"SS-2' нарушен: ${ss2Result.violations.mkString("; ")}"
      LevelReport(
        level = 3,
        name = "SPARQL",
        passed = ss1Result.passed && ss2Result.passed,
        violations = violations.result()
      )
    } match {
      case Success(r) => r
      case Failure(ex) =>
        logger.error("Ошибка SPARQL-верификации", ex)
        LevelReport(3, "SPARQL", passed = false, List(s"SPARQL engine error: ${ex.getMessage}"))
    }
    levels += level3

    ValidationReport(levels.result())

  /** Определение финального результата по совокупности уровней. */
  private def determineResult(report: ValidationReport): ValidationResult =
    val anyFailed = report.levels.exists(!_.passed)
    val anyWarning = report.levels.exists(level => !level.passed && level.violations.nonEmpty)
    if (anyFailed) ValidationResult.Invalid
    else if (anyWarning) ValidationResult.Warning
    else ValidationResult.Valid
