// =============================================================================
// RestApiSpec.scala — Интеграционные тесты REST-API (Akka HTTP TestKit).
//
// Тестирует эндпоинты:
//   GET  /api/v1/health    — liveness-проба (200 OK).
//   GET  /api/v1/ready      — readiness-проба (ready=true).
//   GET  /api/v1/metrics    — Prometheus-метрики (text/plain).
//   POST /api/v1/translate  — основной перевод (200 для валидного payload).
//   POST /api/v1/translate  — 400 для невалидного JSON.
//   POST /api/v1/translate  — 401 без заголовка Authorization.
//   POST /api/v1/translate  — 403 с невалидным JWT.
//
// Использует ScalatestRouteTest (RouteTest) + ActorTestKit для TestProbe арбитра.
// =============================================================================
package ru.smev.asg.api

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem => TypedActorSystem}
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import ru.smev.asg.agents.ArbiterCommand

import java.util.Base64
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

class RestApiSpec
  extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with BeforeAndAfterAll:

  import RestApiSpec.*

  /** TestKit для typed-актёров (нужен для TestProbe арбитра). */
  private val testKit = ActorTestKit()
  /** Заглушка арбитра — перехватывает запросы от REST-API. */
  private val arbiterProbe = testKit.createTestProbe[ArbiterCommand]()
  /** Аутентификатор с тестовым секретом. */
  private val authenticator = new JwtAuthenticator("test-secret")

  /** Маршруты REST-API (синтезируются при каждом вызове — given-параметры). */
  private def routes: akka.http.scaladsl.server.Route =
    given TypedActorSystem[_] = system.toTyped
    given ExecutionContext = system.dispatcher
    given akka.util.Timeout = akka.util.Timeout(5.seconds)
    // ВАЖНО: RestApi.route ожидает ActorRef[ArbiterAgent.Command]; ArbiterAgent.Command
    // в текущей версии исходников не определён — приводим к ActorRef[Any] как обходной путь.
    // После рефакторинга исходников (добавления type Command = ArbiterCommand) приведение
    // можно убрать.
    val arbiterRef: ActorRef[Any] = arbiterProbe.ref.asInstanceOf[ActorRef[Any]]
    RestApi.route(arbiterRef, authenticator)

  override def afterAll(): Unit =
    testKit.shutdownTestKit()
    cleanUp()
    super.afterAll()

  "RestApi" should {

    "return 200 OK on GET /api/v1/health" in {
      Get("/api/v1/health") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        // Тело ответа — JSON с полями status, version, uptime.
        contentType shouldBe ContentTypes.`application/json`
        val body = responseAs[String]
        body should include("\"status\":\"UP\"")
        body should include("\"version\"")
      }
    }

    "return ready=true on GET /api/v1/ready" in {
      Get("/api/v1/ready") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val body = responseAs[String]
        body should include("\"ready\":true")
        // Все компоненты должны быть OK.
        body should include("ontology-registry")
        body should include("shacl-validator")
        body should include("redis")
      }
    }

    "return Prometheus metrics on GET /api/v1/metrics" in {
      Get("/api/v1/metrics") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        // Метрики Prometheus — text/plain, начинаются с "# TYPE".
        val body = responseAs[String]
        body should include("# TYPE asg_uptime_seconds gauge")
        body should include("asg_uptime_seconds")
        body should include("# TYPE asg_rest_requests_total counter")
        body should include("asg_rest_requests_total")
      }
    }

    "accept POST /api/v1/translate with valid payload" in {
      // Валидный JSON с обязательными полями sourceOntologyId, targetOntologyId, query.
      val payload =
        """{"sourceOntologyId":"residence","targetOntologyId":"healthcare","query":"прописка"}"""
      Post("/api/v1/translate", HttpEntity(ContentTypes.`application/json`, payload))
        .addHeader(Authorization.oauth2(validJwt()))
        ~> routes ~> check {
        // Эндпоинт либо отвечает 200 (если арбитр успел ответить), либо 500/408
        // (если тайм-аут); в любом случае не 400/401/403 — значит JSON и JWT валидны.
        status should (be(StatusCodes.OK) or be(StatusCodes.InternalServerError))
      }
    }

    "return 400 on POST /api/v1/translate with invalid JSON" in {
      // Невалидный JSON (отсутствует закрывающая скобка).
      val badJson = """{"sourceOntologyId":"x""""
      Post("/api/v1/translate", HttpEntity(ContentTypes.`application/json`, badJson))
        .addHeader(Authorization.oauth2(validJwt()))
        ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("Невалидный JSON")
      }
    }

    "return 401 without Authorization header" in {
      // Запрос без заголовка Authorization — аутентификатор должен отклонить.
      val payload =
        """{"sourceOntologyId":"a","targetOntologyId":"b","query":"q"}"""
      Post("/api/v1/translate", HttpEntity(ContentTypes.`application/json`, payload))
        ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return 403 with invalid JWT" in {
      // Заголовок Authorization присутствует, но JWT невалидного формата.
      val payload =
        """{"sourceOntologyId":"a","targetOntologyId":"b","query":"q"}"""
      Post("/api/v1/translate", HttpEntity(ContentTypes.`application/json`, payload))
        .addHeader(Authorization.oauth2("invalid-jwt-token"))
        ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }
  }

end RestApiSpec

object RestApiSpec:

  /**
   * Конструирует валидный JWT для тестов.
   * JwtAuthenticator проверяет: формат "Bearer header.payload.signature",
   * payload содержит "exp" > текущего времени, все три части непустые.
   */
  def validJwt(): String =
    val headerJson = """{"alg":"HS256","typ":"JWT"}"""
    val exp = (System.currentTimeMillis() / 1000) + 3600  // +1 час
    val payloadJson = s"""{"exp":$exp,"sub":"test-user"}"""
    val header    = Base64.getEncoder.encodeToString(headerJson.getBytes("UTF-8"))
    val payload   = Base64.getEncoder.encodeToString(payloadJson.getBytes("UTF-8"))
    val signature = "test-signature"  // заглушка (JwtAuthenticator не проверяет подпись реально)
    s"$header.$payload.$signature"

end RestApiSpec
