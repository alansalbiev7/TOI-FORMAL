// =============================================================================
// ProvORecorder.scala — Регистратор провиденции PROV-O.
//
// Каждый перевод через ASG сохраняется как prov:Activity с привязкой
// prov:wasGeneratedBy (запрос), prov:used (онтологии), prov:wasAssociatedWith
// (агенты ASG), prov:wasDerivedFrom (маппинги), prov:startedAtTime/endedAtTime.
//
// Персистентность — в .ttl файл (Turtle, формат Jena).
// =============================================================================
package ru.smev.asg.provenance

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}
import org.apache.jena.vocabulary.{RDF, XSD}
import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path, Paths}
import java.time.Instant

// -----------------------------------------------------------------------------
// Доменные модели провиденции.
// -----------------------------------------------------------------------------
/** Решение одного агента (для audit trail внутри Activity). */
final case class AgentDecision(
  agentName:   String,
  decision:    String,
  confidence:  Double,
  duration:    Long
)

/** Полная запись провиденции для одного перевода. */
final case class ProvRecord(
  requestId:         String,
  sourceOntologyId:  String,
  targetOntologyId:  String,
  query:             String,
  translatedQuery:   String,
  mappingIds:        List[String],
  agentDecisions:    List[AgentDecision],
  timestamp:         Long
)

// =============================================================================
// Реализация recorder'а.
// =============================================================================
class ProvORecorderImpl(persistencePath: String):

  private val log = LoggerFactory.getLogger(getClass)

  // PROV-O неймспейсы.
  private val ProvNs = "http://www.w3.org/ns/prov#"
  private val AsgNs  = "https://smev.ru/asg/provenance#"

  /** Записать ProvRecord в Jena-модель и сбросить на диск. */
  def record(record: ProvRecord): Unit =
    val model = toRdf(record)
    persist(model, record.requestId)
    log.info("ProvORecorder: записана провиденция для requestId={} ({} триплетов)",
      record.requestId, model.size())

  // ---------------------------------------------------------------------------
  // Конвертация ProvRecord → Jena Model (PROV-O триплеты).
  // ---------------------------------------------------------------------------
  def toRdf(record: ProvRecord): Model =
    val model = ModelFactory.createDefaultModel()
    model.setNsPrefix("prov", ProvNs)
    model.setNsPrefix("asg",  AsgNs)
    model.setNsPrefix("xsd",  XSD.getURI)

    val activityUri = s"${AsgNs}Activity_${record.requestId}"
    val activity = model.createResource(activityUri)
    activity.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Activity"))
    // prov:startedAtTime
    val startTs = Instant.ofEpochMilli(record.timestamp).toString
    activity.addProperty(model.createProperty(s"${ProvNs}startedAtTime"),
      model.createTypedLiteral(startTs, XSD.dateTime.getURI))
    // prov:endedAtTime (текущее время)
    activity.addProperty(model.createProperty(s"${ProvNs}endedAtTime"),
      model.createTypedLiteral(Instant.now().toString, XSD.dateTime.getURI))

    // prov:used — исходная и целевая онтологии (как prov:Entity).
    val srcEntity = model.createResource(s"${AsgNs}Ontology_${record.sourceOntologyId}")
    srcEntity.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Entity"))
    activity.addProperty(model.createProperty(s"${ProvNs}used"), srcEntity)

    val tgtEntity = model.createResource(s"${AsgNs}Ontology_${record.targetOntologyId}")
    tgtEntity.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Entity"))
    activity.addProperty(model.createProperty(s"${ProvNs}used"), tgtEntity)

    // prov:wasGeneratedBy — исходный запрос как prov:Entity.
    val queryEntity = model.createResource(s"${AsgNs}Query_${record.requestId}")
    queryEntity.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Entity"))
    queryEntity.addProperty(model.createProperty(s"${AsgNs}queryText"), record.query)
    queryEntity.addProperty(model.createProperty(s"${ProvNs}wasGeneratedBy"), activity)

    // Переведённый запрос (translatedQuery) — ещё один prov:Entity, wasDerivedFrom.
    val translatedEntity = model.createResource(s"${AsgNs}TranslatedQuery_${record.requestId}")
    translatedEntity.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Entity"))
    translatedEntity.addProperty(model.createProperty(s"${AsgNs}queryText"), record.translatedQuery)
    translatedEntity.addProperty(model.createProperty(s"${ProvNs}wasDerivedFrom"), queryEntity)
    translatedEntity.addProperty(model.createProperty(s"${ProvNs}wasGeneratedBy"), activity)

    // prov:wasAssociatedWith — агенты ASG (по AgentDecision.agentName).
    record.agentDecisions.foreach { d =>
      val agentUri = s"${AsgNs}Agent_${d.agentName}"
      val agent = model.createResource(agentUri)
      agent.addProperty(RDF.`type`, model.createResource(s"${ProvNs}SoftwareAgent"))
      activity.addProperty(model.createProperty(s"${ProvNs}wasAssociatedWith"), agent)
      // Расширение ASG: детали решения агента.
      activity.addLiteral(model.createProperty(s"${AsgNs}decision_${d.agentName}"), d.decision)
      activity.addLiteral(model.createProperty(s"${AsgNs}confidence_${d.agentName}"), d.confidence: java.lang.Double)
    }

    // prov:wasDerivedFrom — маппинги (по mappingIds).
    record.mappingIds.foreach { mid =>
      val mapping = model.createResource(s"${AsgNs}Mapping_$mid")
      mapping.addProperty(RDF.`type`, model.createResource(s"${ProvNs}Entity"))
      activity.addProperty(model.createProperty(s"${ProvNs}wasDerivedFrom"), mapping)
    }

    model
  end toRdf

  // ---------------------------------------------------------------------------
  // Персистентность: запись .ttl файла (один файл на requestId).
  // ---------------------------------------------------------------------------
  private def persist(model: Model, requestId: String): Unit =
    val path: Path = Paths.get(persistencePath)
    Files.createDirectories(path)
    val file = path.resolve(s"prov-$requestId.ttl")
    val sw = new java.io.StringWriter()
    model.write(sw, "TTL")
    Files.write(file, sw.toString.getBytes("UTF-8"))
    log.debug("ProvORecorder: файл провиденции {} сохранён", file)

  /** Пакетная запись списка провиденций (для batch-импорта). */
  def recordAll(records: List[ProvRecord]): Unit =
    records.foreach(record)

end ProvORecorderImpl

// -----------------------------------------------------------------------------
// Companion-object с фабрикой.
// -----------------------------------------------------------------------------
object ProvORecorder:

  // Протокол актёра (для совместимости с ArbiterAgent, который вызывает
  // deps.provRec ! ProvORecorder.RecordTranslation(...)).
  sealed trait Command

  /** Запрос на запись провиденции перевода (от ArbiterAgent на этапе S3). */
  final case class RecordTranslation(
    requestId:      String,
    sourceOntology: String,
    targetOntology: String,
    concept:        String,
    decision:       String,
    confidence:     Double,
    timestamp:      Instant
  ) extends Command

  /** Фабрика (по спеке): возвращает ProvORecorderImpl с заданным путём. */
  def apply(persistencePath: String): ProvORecorderImpl = new ProvORecorderImpl(persistencePath)

  /** Актор-обёртка: использует ProvORecorderImpl для персистентности. */
  def behavior(persistencePath: String): Behavior[Command] =
    Behaviors.setup { ctx =>
      val impl = new ProvORecorderImpl(persistencePath)
      ctx.log.info("ProvORecorder actor: старт; persistence={}", persistencePath)
      Behaviors.receiveMessage[Command] {
        case rt: RecordTranslation =>
          // Конвертация упрощённого RecordTranslation → полный ProvRecord.
          val record = ProvRecord(
            requestId        = rt.requestId,
            sourceOntologyId = rt.sourceOntology,
            targetOntologyId = rt.targetOntology,
            query            = rt.concept,
            translatedQuery  = rt.concept,
            mappingIds       = Nil,
            agentDecisions   = List(AgentDecision(
              agentName   = "ArbiterAgent",
              decision    = rt.decision,
              confidence  = rt.confidence,
              duration    = 0L
            )),
            timestamp        = rt.timestamp.toEpochMilli
          )
          impl.record(record)
          Behaviors.same
      }
    }

end ProvORecorder
