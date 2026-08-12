// =============================================================================
// OntologyRegistry.scala — Реестр онтологий СМЭВ.
//
// Обёртка над Apache Jena OntModel: загрузка .owl/.ttl файлов при старте,
// кэширование OntModel в памяти, транзакционный запрос концептов/ролей
// и обход иерархии rdfs:subClassOf (транзитивное замыкание).
//
// Актёр хранит mutable.Map[String, OntologyEntry] и отвечает на сообщения
// протокола OntologyQuery. Использует OntDocumentManager для управляемой
// загрузки ( owl:imports, кэширование по IRI ).
// =============================================================================
package ru.smev.asg.ontology

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.jena.ontology.{OntClass, OntModel, OntModelSpec, OntProperty}
import org.apache.jena.ontology.OntDocumentManager
import org.apache.jena.rdf.model.{ModelFactory, Resource}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDFS
import org.slf4j.LoggerFactory

import java.io.File
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// -----------------------------------------------------------------------------
// Протокол актёра (sealed для полноты pattern-match).
// -----------------------------------------------------------------------------
sealed trait OntologyQuery

object OntologyQuery:
  /** Запрос концепта по URI: вернуть метку и надклассы. */
  final case class GetConcept(
    ontologyId: String,
    conceptUri: String,
    replyTo:    ActorRef[ConceptResult]
  ) extends OntologyQuery

  /** Запрос роли (owl:ObjectProperty / owl:DatatypeProperty). */
  final case class GetRole(
    ontologyId: String,
    roleUri:    String,
    replyTo:    ActorRef[RoleResult]
  ) extends OntologyQuery

  /** Запрос транзитивной иерархии концепта. */
  final case class GetHierarchy(
    ontologyId: String,
    conceptUri: String,
    replyTo:    ActorRef[HierarchyResult]
  ) extends OntologyQuery

  // Ответы.
  final case class ConceptResult(concept: Option[OntConcept])
  final case class RoleResult(role: Option[OntRole])
  final case class HierarchyResult(superClasses: List[String], subClasses: List[String])

  // Доменные модели.
  final case class OntConcept(uri: String, label: String, superClasses: List[String])
  final case class OntRole(uri: String, label: String, domain: Option[String], range: Option[String])

end OntologyQuery

import OntologyQuery._

/** Метаданные загруженной онтологии. */
final case class OntologyEntry(
  id:       String,
  model:    OntModel,
  loadedAt: Long
)

// =============================================================================
// Companion-object с фабрикой поведения.
// =============================================================================
object OntologyRegistry:

  private val log = LoggerFactory.getLogger(getClass)

  // Поддерживаемые расширения файлов онтологий.
  private val SupportedExt = Set(".owl", ".ttl", ".rdf", ".n3")

  /** Фабрика актёра: `ontologyDir` — каталог с .owl/.ttl файлами. */
  def apply(ontologyDir: String): Behavior[OntologyQuery] =
    Behaviors.setup { ctx =>
      ctx.log.info("OntologyRegistry: инициализация; каталог онтологий={}", ontologyDir)

      // Кэш OntModel по идентификатору онтологии (ключ — имя файла без расширения).
      val cache: mutable.Map[String, OntologyEntry] = mutable.Map.empty

      // OntDocumentManager — управляет загрузкой owl:imports и кэшированием.
      val docMgr = OntDocumentManager.getInstance
      docMgr.setProcessImports(true)
      docMgr.setCacheModels(true)

      // Загрузка OWL-модели: при старте проход по каталогу.
      loadAll(ontologyDir, cache, docMgr)
      ctx.log.info("OntologyRegistry: загружено {} онтологий", cache.size)

      // ---------------------------------------------------------------------
      // Обработчик сообщений.
      // ---------------------------------------------------------------------
      Behaviors.receiveMessage[OntologyQuery] {
        case GetConcept(ontologyId, conceptUri, replyTo) =>
          val concept = getConcept(cache, ontologyId, conceptUri)
          replyTo ! ConceptResult(concept)
          Behaviors.same

        case GetRole(ontologyId, roleUri, replyTo) =>
          val role = getRole(cache, ontologyId, roleUri)
          replyTo ! RoleResult(role)
          Behaviors.same

        case GetHierarchy(ontologyId, conceptUri, replyTo) =>
          val (sup, sub) = getHierarchy(cache, ontologyId, conceptUri)
          replyTo ! HierarchyResult(sup, sub)
          Behaviors.same
      }
    }

  // ---------------------------------------------------------------------------
  // Загрузка всех .owl/.ttl из каталога.
  // ---------------------------------------------------------------------------
  private def loadAll(dir: String, cache: mutable.Map[String, OntologyEntry], docMgr: OntDocumentManager): Unit =
    val d = new File(dir)
    if !d.exists() || !d.isDirectory then
      log.warn("Каталог онтологий {} недоступен — реестр будет пустым", dir)
      return
    val files = d.listFiles().filter(f => f.isFile && SupportedExt.exists(f.getName.toLowerCase.endsWith))
    files.foreach { f =>
      val id = f.getName.substring(0, f.getName.lastIndexOf('.'))
      loadOne(id, f.getAbsolutePath, cache, docMgr)
    }

  /** Загрузка одной OWL-модели в кэш. */
  private def loadOne(id: String, path: String, cache: mutable.Map[String, OntologyEntry], docMgr: OntDocumentManager): Unit =
    val spec = OntModelSpec.OWL_MEM
    spec.setDocumentManager(docMgr)
    val model = ModelFactory.createOntologyModel(spec)
    Try {
      val lang = if path.toLowerCase.endsWith(".ttl") then "TTL"
                  else if path.toLowerCase.endsWith(".n3") then "N3"
                  else "RDF/XML"
      RDFDataMgr.read(model, new File(path).toURI.toString)
    } match
      case Success(_) =>
        cache += id -> OntologyEntry(id, model, System.currentTimeMillis())
        log.info("Загружена онтология {}: {} классов", id,
          model.listClasses().asScala.size)
      case Failure(e) =>
        log.error("Ошибка загрузки онтологии {} из {}: {}", id, path, e.getMessage)

  // ---------------------------------------------------------------------------
  // Запрос концепта: метка (rdfs:label) + прямые надклассы.
  // ---------------------------------------------------------------------------
  private def getConcept(cache: mutable.Map[String, OntologyEntry], ontologyId: String, conceptUri: String): Option[OntConcept] =
    cache.get(ontologyId).flatMap { entry =>
      Option(entry.model.getOntClass(conceptUri)).map { cls =>
        val label = Option(cls.getLabel(null)).getOrElse(cls.getLocalName)
        val supers = cls.listSuperClasses(true).asScala.toList
          .filter(_ != null).map(_.getURI).filter(_ != null)
        OntConcept(uri = conceptUri, label = label, superClasses = supers)
      }
    }

  /** Запрос роли (ObjectProperty / DatatypeProperty). */
  private def getRole(cache: mutable.Map[String, OntologyEntry], ontologyId: String, roleUri: String): Option[OntRole] =
    cache.get(ontologyId).flatMap { entry =>
      Option(entry.model.getOntProperty(roleUri)).map { p: OntProperty =>
        val label = Option(p.getLabel(null)).getOrElse(p.getLocalName)
        val domain = Option(p.getDomain).map(_.getURI)
        val range  = Option(p.getRange).map(_.getURI)
        OntRole(uri = roleUri, label = label, domain = domain, range = range)
      }
    }

  /** Транзитивное замыкание rdfs:subClassOf (вверх и вниз). */
  private def getHierarchy(cache: mutable.Map[String, OntologyEntry], ontologyId: String, conceptUri: String): (List[String], List[String]) =
    cache.get(ontologyId).flatMap { entry =>
      Option(entry.model.getOntClass(conceptUri)).map { cls: OntClass =>
        // listSuperClasses(true) — транзитивное замыкание (без direct=false).
        val sup = cls.listSuperClasses(true).asScala.toList
          .filter(_ != null).map(_.getURI).filter(_ != null).distinct
        val sub = cls.listSubClasses(true).asScala.toList
          .filter(_ != null).map(_.getURI).filter(_ != null).distinct
        (sup, sub)
      }
    }.getOrElse((Nil, Nil))

end OntologyRegistry
