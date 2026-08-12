// =============================================================================
// Main.scala — Точка входа в систему АСШ (Adaptive Semantic Gateway).
//
// Инициализация акторной системы, спавн инфраструктуры (OntologyRegistry,
// MappingRegistry, CacheManager, ShaclValidator, Owl2RlReasoner, SparqlVerifier,
// EscalationManager, ProvORecorder) и агентов (Matcher, Validator, Arbiter,
// Learner), старт Akka HTTP и gRPC серверов.
//
// Graceful shutdown по SIGTERM/SIGINT через sys.addShutdownHook.
// =============================================================================
package ru.smev.asg

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import ru.smev.asg.agents.{ArbiterAgent, LearnerAgent, MatcherAgent, ValidatorAgent}
import ru.smev.asg.api.{GrpcServer, RestApi}
import ru.smev.asg.hotl.{EscalationCommand, EscalationManager}
import ru.smev.asg.ontology.{CacheManager, MappingCommand, MappingRegistry, OntologyQuery, OntologyRegistry}
import ru.smev.asg.provenance.ProvORecorder
import ru.smev.asg.verification.{Owl2RlReasoner, ShaclValidator, ShaclValidatorImpl, SparqlVerifier}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

// -----------------------------------------------------------------------------
// Guardian-актёр: создаёт все дочерние акторы и хранит их ссылки.
// -----------------------------------------------------------------------------
object Guardian:

  sealed trait GuardianCommand
  final case class GetArbiter(replyTo: ActorRef[ActorRef[ArbiterAgent.Command]]) extends GuardianCommand

  // Ссылки на дочерние акторы.
  final case class Context(
    ontology:  ActorRef[OntologyQuery],
    mapping:   ActorRef[MappingCommand],
    cache:     ActorRef[ru.smev.asg.ontology.CacheCommand],
    matcher:   ActorRef[MatcherAgent.Command],
    validator: ActorRef[ValidatorAgent.Command],
    arbiter:   ActorRef[ArbiterAgent.Command],
    learner:   ActorRef[LearnerAgent.Command],
    escalation: ActorRef[EscalationCommand]
  )

  def apply(cfg: Config): Behavior[GuardianCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("Guardian: Инициализация акторной системы ASG")

      // Инфраструктура.
      val ontology = ctx.spawn(OntologyRegistry(cfg.ontologyDir), "ontology-registry")
      val mapping  = ctx.spawn(MappingRegistry(cfg.mappingPath), "mapping-registry")
      val cache    = ctx.spawn(CacheManager(cfg.redisUrl), "cache-manager")
      val escalation = ctx.spawn(EscalationManager(cfg.operatorQueueSize), "escalation-manager")
      val provO    = ctx.spawn(ProvORecorder.behavior(cfg.provPath), "prov-o-recorder")

      // Загрузка валидаторов (один раз).
      // ShaclValidatorImpl реализует и новый ShaclValidatorApi, и старый трейт ShaclValidator,
      // который ожидает ValidatorAgent.apply(shacl = ...).
      val shaclValidator: ShaclValidatorImpl = new ShaclValidatorImpl(cfg.shaclDir)
      val owlReasoner    = Owl2RlReasoner()
      val sparqlVerifier = SparqlVerifier(cfg.sparqlDir)

      // Агенты.
      val matcher = ctx.spawn(
        MatcherAgent(
          embeddings    = stubEmbeddings(),
          bm25Threshold = cfg.bm25Threshold,
          targetModels  = _ => None
        ),
        "matcher-agent"
      )
      val validator = ctx.spawn(
        ValidatorAgent(
          shacl        = shaclValidator,
          reasoner     = new ValidatorAgentOwlAdapter(owlReasoner),
          sparql       = new ValidatorAgentSparqlAdapter(sparqlVerifier),
          enableSparql = cfg.enableSparql
        ),
        "validator-agent"
      )
      val arbiter = ctx.spawn(
        ArbiterAgent(
          deps = ArbiterAgent.Dependencies(
            matcher    = matcher,
            validator  = validator,
            escalation = escalation,
            provRec    = provO
          )
        ),
        "arbiter-agent"
      )
      val learner = ctx.spawn(
        LearnerAgent(bridge = new LearnerAgent.SubprocessSb3Bridge(), matcher = Some(matcher)),
        "learner-agent"
      )

      ctx.log.info("Guardian: все дочерние акторы запущены")
      Behaviors.receiveMessage[GuardianCommand] {
        case GetArbiter(replyTo) =>
          replyTo ! arbiter
          Behaviors.same
      }
    }

  // Хелперы-адаптеры для ValidatorAgent (трейты с разными сигнатурами).
  private def stubEmbeddings(): MatcherAgent.EmbeddingsClient =
    new MatcherAgent.EmbeddingsClient:
      override def cosineSimilarity(a: List[String], b: List[String]): scala.util.Try[Double] =
        scala.util.Success(0.0)

  final class ValidatorAgentOwlAdapter(impl: ru.smev.asg.verification.OwlReasonerApi)
    extends ValidatorAgent.OwlReasoner:
    override def isConsistent(merged: org.apache.jena.rdf.model.Model): scala.util.Try[Boolean] =
      scala.util.Success(impl.checkConsistency(merged))

  final class ValidatorAgentSparqlAdapter(impl: ru.smev.asg.verification.SparqlVerifierApi)
    extends ValidatorAgent.SparqlInvariantChecker:
    override def checkSs1(merged: org.apache.jena.rdf.model.Model): scala.util.Try[List[String]] =
      scala.util.Success(impl.verifySS1(merged).violations)
    override def checkSs2(merged: org.apache.jena.rdf.model.Model): scala.util.Try[List[String]] =
      scala.util.Success(impl.verifySS2(merged).violations)

end Guardian

// -----------------------------------------------------------------------------
// HOCON-обёртка.
// -----------------------------------------------------------------------------
final case class Config(
  ontologyDir:        String,
  mappingPath:        String,
  redisUrl:           String,
  shaclDir:           String,
  sparqlDir:          String,
  provPath:           String,
  bm25Threshold:      Double,
  enableSparql:       Boolean,
  operatorQueueSize:  Int,
  httpPort:           Int,
  grpcPort:           Int
)

object Config:
  def load(): Config =
    val cfg = ConfigFactory.load()
    Config(
      ontologyDir       = cfg.getString("asg.shacl.ontologies-dir"),
      mappingPath       = cfg.getString("asg.mapping.file-path"),
      redisUrl          = cfg.getString("asg.cache.redis.host") + ":" + cfg.getInt("asg.cache.redis.port"),
      shaclDir          = cfg.getString("asg.shacl.shapes-dir"),
      sparqlDir         = "sparql",
      provPath          = cfg.getString("asg.provenance.dump-path"),
      bm25Threshold     = cfg.getDouble("asg.matcher.bm25-threshold"),
      enableSparql      = cfg.getBoolean("asg.validator.enable-sparql"),
      operatorQueueSize = 1000,
      httpPort          = cfg.getInt("asg.http.port"),
      grpcPort          = cfg.getInt("asg.grpc.port")
    )

// =============================================================================
// Main.
// =============================================================================
object Main:

  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val cfg = Config.load()
    log.info("ASG startup: конфигурация загружена; httpPort={}, grpcPort={}", cfg.httpPort, cfg.grpcPort)

    // Создание ActorSystem с Guardian-актором.
    val system = ActorSystem(Guardian(cfg), "asg-system")
    given ExecutionContext = system.executionContext

    // Запрос Arbiter-агента у Guardian для проброса в REST/gRPC.
    import akka.actor.typed.scaladsl.AskPattern._
    given akka.util.Timeout = 30.seconds
    val arbiterF: Future[ActorRef[ArbiterAgent.Command]] =
      system.ask(ref => Guardian.GetArbiter(ref))
    val arbiter = Await.result(arbiterF, 30.seconds)

    // Старт gRPC-сервера.
    val grpc = new GrpcServer.GrpcServerImpl(arbiter = arbiter, verifier = jwtStub, port = cfg.grpcPort)
    grpc.start()
    log.info("gRPC-сервер запущен на порту {}", cfg.grpcPort)

    // Старт Akka HTTP REST-сервера.
    val bindingF = RestApi.start("0.0.0.0", cfg.httpPort, arbiter)
    val binding = Await.result(bindingF, 30.seconds)
    log.info("Akka HTTP REST-сервер запущен на 0.0.0.0:{}", cfg.httpPort)

    // Graceful shutdown по SIGTERM/SIGINT.
    sys.addShutdownHook {
      log.info("ASG shutdown: получен сигнал — корректная остановка...")
      try
        Await.result(binding.unbind(), 10.seconds)
        grpc.shutdown()
        val term = system.terminate()
        Await.result(term, 30.seconds)
        log.info("ASG shutdown завершён.")
      catch
        case e: Exception => log.error("Ошибка при остановке: {}", e.getMessage, e)
    }

    // Блокируем главный поток до завершения ActorSystem.
    Await.ready(system.whenTerminated, scala.concurrent.duration.Duration.Inf)

  // Заглушка JWT-верификатора (в production подменяется jjwt).
  private def jwtStub: GrpcServer.JwtVerifier = new GrpcServer.JwtVerifier:
    override def verify(token: String): Option[GrpcServer.JwtClaims] = None

end Main
