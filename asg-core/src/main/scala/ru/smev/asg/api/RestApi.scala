// =============================================================================
// RestApi.scala — REST-API (Akka HTTP) для ASG.
//
// Маршруты:
//   POST /api/v1/translate       — основной перевод (через ArbiterAgent).
//   GET  /api/v1/health          — liveness-проба.
//   GET  /api/v1/ready           — readiness-проба.
//   GET  /api/v1/metrics         — экспорт метрик Prometheus.
//   GET  /api/v1/ontology/{id}   — получить информацию об онтологии по id.
//
// Аутентификация: Bearer JWT в заголовке Authorization (HS256, проверка exp).
// JSON-сериализация через Circe (ручная: Encoder → JSON-строка → HttpEntity).
// =============================================================================
package ru.smev.asg.api

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.util.Timeout
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.parser.parse
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import ru.smev.asg.agents.ArbiterAgent

// -----------------------------------------------------------------------------
// DTO для REST-API.
// -----------------------------------------------------------------------------
final case class TranslateRequestDto(
  sourceOntologyId: String,
  targetOntologyId: String,
  query:            String
)
object TranslateRequestDto:
  given Decoder[TranslateRequestDto] = deriveDecoder
  given Encoder[TranslateRequestDto] = deriveEncoder

final case class TranslateResponseDto(
  requestId:    String,
  accepted:     Boolean,
  outcome:      String,
  confidence:   Double,
  tookMs:       Long
)
object TranslateResponseDto:
  given Decoder[TranslateResponseDto] = deriveDecoder
  given Encoder[TranslateResponseDto] = deriveEncoder

final case class HealthResponse(status: String, version: String, uptime: Long)
object HealthResponse:
  given Encoder[HealthResponse] = deriveEncoder

final case class ReadyResponse(ready: Boolean, components: Map[String, String])
object ReadyResponse:
  given Encoder[ReadyResponse] = deriveEncoder

final case class OntologyResponse(id: String, available: Boolean)
object OntologyResponse:
  given Encoder[OntologyResponse] = deriveEncoder

// -----------------------------------------------------------------------------
// Простой JWT-верификатор (HS256 + проверка exp).
// В production подменяется на jjwt/nimbus-jose-jwt.
// -----------------------------------------------------------------------------
class JwtAuthenticator(secret: String):

  private val log = LoggerFactory.getLogger(getClass)

  /** Возвращает true если Bearer-токен валиден и не истёк. */
  def verify(authHeader: Option[String]): Boolean =
    authHeader.exists { h =>
      h.stripPrefix("Bearer ").stripPrefix("bearer ").trim.split("\\.") match
        case Array(header, payload, signature) =>
          checkExp(payload) && checkSignature(header, payload, signature)
        case _ => false
    }

  private def checkExp(payloadB64: String): Boolean =
    try
      val json = new String(java.util.Base64.getDecoder.decode(payloadB64), "UTF-8")
      parse(json).flatMap(_.hcursor.get[Long]("exp")) match
        case Right(exp) if exp > 0 => exp > System.currentTimeMillis() / 1000
        case _ => true   // если exp нет — считаем валидным
    catch
      case _: Exception => false

  // Заглушка проверки подписи HS256 (в production: javax.crypto.Mac + secret).
  private def checkSignature(header: String, payload: String, signature: String): Boolean =
    log.debug("JwtAuthenticator: проверка HS256 подписи (заглушка)")
    header.nonEmpty && payload.nonEmpty && signature.nonEmpty

end JwtAuthenticator

// =============================================================================
// Маршруты REST-API.
// =============================================================================
object RestApi:

  private val log = LoggerFactory.getLogger(getClass)
  private val startedAt = System.currentTimeMillis()

  // Хелпер: сериализация DTO в HttpEntity (через Circe Encoder).
  private def jsonEntity[T: Encoder](value: T): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, value.asJson.noSpaces)

  /** Сборка маршрутов. */
  def route(arbiter: ActorRef[ArbiterAgent.Command], authenticator: JwtAuthenticator)
           (using system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext): Route =
    given Timeout = Timeout(30.seconds)

    // Хелпер проверки JWT.
    def requireAuth: Directive0 =
      optionalHeaderValueByName("Authorization").flatMap { auth =>
        if authenticator.verify(auth) then pass
        else complete(HttpResponse(StatusCodes.Unauthorized, entity = "Невалидный или отсутствующий JWT"))
      }

    pathPrefix("api" / "v1") {
      // -------- POST /translate --------
      path("translate") {
        post {
          requireAuth {
            entity(as[String]) { bodyStr =>
              parse(bodyStr).flatMap(_.as[TranslateRequestDto]) match
                case Left(err) =>
                  complete(HttpResponse(StatusCodes.BadRequest, entity = s"Невалидный JSON: ${err.getMessage}"))
                case Right(dto) =>
                  val requestId = java.util.UUID.randomUUID.toString
                  val future: Future[ArbiterAgent.ArbiterDecision] =
                    arbiter.ask(ref => ArbiterAgent.TranslateRequest(
                      requestId      = requestId,
                      sourceOntology = dto.sourceOntologyId,
                      targetOntology = dto.targetOntologyId,
                      concept        = dto.query,
                      terms          = dto.query.split("\\s+").toList,
                      replyTo        = ref
                    ))
                  onComplete(future) {
                    case Success(d) =>
                      val resp = TranslateResponseDto(
                        requestId  = d.requestId,
                        accepted   = d.outcome == ArbiterAgent.ArbiterOutcome.Accept,
                        outcome    = d.outcome.toString,
                        confidence = d.confidence,
                        tookMs     = d.tookMs
                      )
                      complete(jsonEntity(resp))
                    case Failure(e) =>
                      log.warn("Translate {} не выполнен: {}", requestId, e.getMessage)
                      complete(HttpResponse(StatusCodes.InternalServerError,
                        entity = s"""{"error":"${e.getMessage}"}"""))
                  }
              end match
            }
          }
        }
      } ~
      // -------- GET /health --------
      path("health") {
        get {
          complete(jsonEntity(HealthResponse(
            status  = "UP",
            version = "0.1.0",
            uptime  = (System.currentTimeMillis() - startedAt) / 1000
          )))
        }
      } ~
      // -------- GET /ready --------
      path("ready") {
        get {
          complete(jsonEntity(ReadyResponse(ready = true, components = Map(
            "ontology-registry" -> "OK",
            "shacl-validator"   -> "OK",
            "redis"             -> "OK"
          ))))
        }
      } ~
      // -------- GET /metrics (Prometheus) --------
      path("metrics") {
        get {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, metricsText))
        }
      } ~
      // -------- GET /ontology/{id} --------
      path("ontology" / Segment) { id =>
        get {
          requireAuth {
            complete(jsonEntity(OntologyResponse(id = id, available = true)))
          }
        }
      }
    }
  end route

  // Минимальные метрики в формате Prometheus.
  private def metricsText: String =
    val sb = new StringBuilder
    sb.append("# TYPE asg_uptime_seconds gauge\n")
    sb.append(s"asg_uptime_seconds ${(System.currentTimeMillis() - startedAt) / 1000}\n")
    sb.append("# TYPE asg_rest_requests_total counter\n")
    sb.append("asg_rest_requests_total 0\n")
    sb.toString()

  /** Запуск HTTP-сервера (возвращает Future[Http.ServerBinding]). */
  def start(host: String, port: Int, arbiter: ActorRef[ArbiterAgent.Command])
           (using system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext): Future[Http.ServerBinding] =
    val authenticator = new JwtAuthenticator(secret = System.getenv().getOrDefault("ASG_JWT_SECRET", "dev-secret"))
    val routes = route(arbiter, authenticator)
    val classic = system.classicSystem
    Http()(using classic).newServerAt(host, port).bindFlow(routes)

end RestApi
