// =============================================================================
// Owl2RlReasoner.scala — OWL2RL reasoner уровня 2 (логическая консистентность).
//
// Обёртка над Jena ReasonerRegistry.getOWLReasoner: проверка консистентности
// объединённой модели O₁ ∪ O₂ ∪ m и поиск неудовлетворимых классов
// (subclasses of owl:Nothing).
// =============================================================================
package ru.smev.asg.verification

import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.{InfModel, Model, ModelFactory}
import org.apache.jena.reasoner.{Reasoner, ReasonerRegistry}
import org.apache.jena.vocabulary.{OWL, RDFS}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

// -----------------------------------------------------------------------------
// API.
// -----------------------------------------------------------------------------
trait OwlReasonerApi:
  /** Проверка консистентности модели (после применения OWL2RL rules). */
  def checkConsistency(model: Model): Boolean

  /** Список неудовлетворимых классов (прямые подклассы owl:Nothing). */
  def getUnsatisfiableClasses(model: Model): List[String]

// =============================================================================
// Реализация.
// =============================================================================
class Owl2RlReasonerImpl extends OwlReasonerApi:

  private val log = LoggerFactory.getLogger(getClass)

  // Создание OWL reasoner'а через ReasonerRegistry.
  // ReasonerRegistry.getOWLReasoner() возвращает OWLRuleReasoner (OWL-RL подмножество).
  private val reasoner: Reasoner =
    val r = ReasonerRegistry.getOWLReasoner
    r.setDerivationLogging(false)
    r

  log.info("Owl2RlReasonerImpl: OWL reasoner создан ({})", reasoner.getClass.getSimpleName)

  // ---------------------------------------------------------------------------
  // Проверка консистентности: создаём InfModel и спрашиваем validate().
  // ---------------------------------------------------------------------------
  override def checkConsistency(model: Model): Boolean =
    val inf: InfModel = ModelFactory.createInfModel(reasoner, model)
    val report = inf.validate()
    val consistent = report == null || !report.iterator().hasNext
    if !consistent then
      val problems = if report == null then List.empty
                     else report.iterator().asScala.toList.map(_.toString)
      log.warn("OWL2RL: модель неконсистентна; {} нарушений: {}",
        problems.size, problems.take(3).mkString("; "))
    consistent

  // ---------------------------------------------------------------------------
  // Поиск неудовлетворимых классов: прямые подклассы owl:Nothing.
  // В выводе reasoner'а все owl:Nothing-подклассы эквивалентны ⊥ (⊥-классы).
  // ---------------------------------------------------------------------------
  override def getUnsatisfiableClasses(model: Model): List[String] =
    val inf: InfModel = ModelFactory.createInfModel(reasoner, model)
    val nothing = inf.createResource(OWL.Nothing.getURI)
    // Прямые subclasses of owl:Nothing (transitive=false) — это ⊥-классы.
    val unsat = inf.listStatements(null, RDFS.subClassOf, nothing).asScala.toList
      .map(_.getSubject.getURI)
      .filter(_ != null)
      .distinct
    if unsat.nonEmpty then
      log.warn("OWL2RL: найдено {} неудовлетворимых классов: {}",
        unsat.size, unsat.take(5).mkString("; "))
    unsat

end Owl2RlReasonerImpl

// -----------------------------------------------------------------------------
// Companion-object с фабрикой.
// -----------------------------------------------------------------------------
object Owl2RlReasoner:
  /** Фабрика: возвращает готовый OwlReasonerApi. */
  def apply(): OwlReasonerApi = new Owl2RlReasonerImpl()
end Owl2RlReasoner
