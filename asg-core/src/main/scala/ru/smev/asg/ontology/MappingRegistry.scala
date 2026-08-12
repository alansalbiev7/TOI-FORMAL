// =============================================================================
// MappingRegistry.scala — Реестр отображений C → m(C), r → m(r).
//
// Хранит in-memory кэш Map[(sourceOnto, targetOnto), Map[sourceUri, Mapping]]
// и персистентно сбрасывает состояние в JSON-файл через Circe.
//
// Каждое AddMapping валидируется (confidence ∈ [0,1], непустые URI),
// сохраняется в кэш и немедленно сбрасывается на диск (write-through).
// =============================================================================
package ru.smev.asg.ontology

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import io.circe.syntax._
import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// -----------------------------------------------------------------------------
// Тип отображения.
// -----------------------------------------------------------------------------
enum MappingType:
  case ConceptMap
  case RoleMap
  case IndividualMap

import MappingType._

/** Одно отображение между исходным и целевым URI. */
final case class Mapping(
  sourceUri:    String,
  targetUri:    String,
  mappingType:  MappingType,
  confidence:   Double,
  createdAt:    Long
)
object Mapping:
  given Encoder[MappingType] = Encoder.encodeString.contramap(_.toString)
  given Decoder[MappingType] = Decoder.decodeString.map { s =>
    s match
      case "ConceptMap"   => ConceptMap
      case "RoleMap"      => RoleMap
      case "IndividualMap" => IndividualMap
      case other          => throw new IllegalArgumentException(s"Неизвестный MappingType: $other")
  }
  given Encoder[Mapping] = deriveEncoder
  given Decoder[Mapping] = deriveDecoder

  // JSON-объекты имеют только строковые ключи, поэтому для Map[(String,String), V]
  // кодируем tuple-ключ в строку формата "src\u001Ftgt" (разделитель — US, 0x1F).
  private val Sep = "\u001F"
  given KeyEncoder[(String, String)] with
    def apply(key: (String, String)): String = key match
      case (s, t) => s"$s$Sep$t"
  given KeyDecoder[(String, String)] with
    def apply(key: String): Option[(String, String)] =
      key.split(Sep, 2) match
        case Array(s, t) => Some((s, t))
        case _           => None
end Mapping

// -----------------------------------------------------------------------------
// Протокол актёра.
// -----------------------------------------------------------------------------
sealed trait MappingCommand

object MappingCommand:
  /** Добавить отображение (с валидацией). */
  final case class AddMapping(
    sourceOntologyId: String,
    targetOntologyId: String,
    mapping:          Mapping,
    replyTo:          ActorRef[MappingResult]
  ) extends MappingCommand

  /** Получить отображение по sourceUri. */
  final case class GetMapping(
    sourceOntologyId: String,
    targetOntologyId: String,
    sourceUri:        String,
    replyTo:          ActorRef[Option[Mapping]]
  ) extends MappingCommand

  /** Перечислить все отображения (для UI/аудита). */
  final case class ListMappings(replyTo: ActorRef[List[Mapping]]) extends MappingCommand

  /** Результат AddMapping. */
  final case class MappingResult(success: Boolean, error: Option[String] = None)

end MappingCommand

import MappingCommand._
import Mapping.{given, *}

// =============================================================================
// Companion-object с фабрикой поведения.
// =============================================================================
object MappingRegistry:

  private val log = LoggerFactory.getLogger(getClass)

  /** @param persistencePath путь к JSON-файлу персистентности. */
  def apply(persistencePath: String): Behavior[MappingCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("MappingRegistry: инициализация; persistence={}", persistencePath)

      // in-memory: (sourceOnto, targetOnto) -> sourceUri -> Mapping
      val store: mutable.Map[(String, String), Map[String, Mapping]] = mutable.Map.empty

      val path: Path = Paths.get(persistencePath)
      // Восстановление состояния из файла (если он существует).
      loadFromDisk(path).foreach { case ((src, tgt), uriMap) =>
        store += (src -> tgt) -> uriMap
      }
      ctx.log.info("MappingRegistry: восстановлено {} пар онтологий", store.size)

      // ---------------------------------------------------------------------
      // Валидация перед вставкой.
      // ---------------------------------------------------------------------
      def validate(m: Mapping): Option[String] =
        if m.sourceUri == null || m.sourceUri.isBlank then Some("Пустой sourceUri")
        else if m.targetUri == null || m.targetUri.isBlank then Some("Пустой targetUri")
        else if m.confidence < 0.0 || m.confidence > 1.0 then Some(s"confidence=${m.confidence} вне [0,1]")
        else None

      def persistToDisk(): Unit =
        val json: Json = store.toMap.asJson
        val bytes = json.noSpaces.getBytes("UTF-8")
        Try {
          if Option(path.getParent).isDefined then Files.createDirectories(path.getParent)
          Files.write(path, bytes,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        } match
          case Success(_) => log.debug("MappingRegistry: состояние сброшено в {}", path)
          case Failure(e) => log.error("MappingRegistry: сбой записи в {}: {}", path, e.getMessage)

      // ---------------------------------------------------------------------
      // Главный обработчик.
      // ---------------------------------------------------------------------
      Behaviors.receiveMessage[MappingCommand] {
        case AddMapping(srcOnto, tgtOnto, mapping, replyTo) =>
          validate(mapping) match
            case Some(err) =>
              log.warn("AddMapping отклонён: {}", err)
              replyTo ! MappingResult(success = false, error = Some(err))
            case None =>
              val key = (srcOnto, tgtOnto)
              val inner = store.getOrElse(key, Map.empty)
              // Замещаем существующее отображение по sourceUri.
              store += key -> (inner + (mapping.sourceUri -> mapping))
              persistToDisk()
              log.info("AddMapping: {} → {} (type={}, conf={})",
                mapping.sourceUri, mapping.targetUri, mapping.mappingType, mapping.confidence)
              replyTo ! MappingResult(success = true)
          Behaviors.same

        case GetMapping(srcOnto, tgtOnto, sourceUri, replyTo) =>
          val result = store.get((srcOnto, tgtOnto)).flatMap(_.get(sourceUri))
          replyTo ! result
          Behaviors.same

        case ListMappings(replyTo) =>
          val all = store.values.flatMap(_.values).toList
          replyTo ! all
          Behaviors.same
      }
    }

  // ---------------------------------------------------------------------------
  // Чтение из JSON-файла.
  // ---------------------------------------------------------------------------
  private def loadFromDisk(path: Path): Map[(String, String), Map[String, Mapping]] =
    if !Files.exists(path) then Map.empty
    else
      val raw = Files.readAllLines(path).asScala.mkString("\n")
      decode[Map[(String, String), Map[String, Mapping]]](raw) match
        case Right(m) => m
        case Left(err) =>
          log.warn("MappingRegistry: не удалось десериализовать {} — {}: начинаем с пустого хранилища", path, err.getMessage)
          Map.empty

end MappingRegistry
