// =============================================================================
// GrpcServer.scala — gRPC-сервер ASG с тремя перехватчиками.
//
// Эндпоинты:
//   rpc Translate (TranslateRequest) returns (TranslateResponse)
//
// Перехватчики (порядок внешнего→внутренний):
//   1. JwtAuthInterceptor     — проверка Bearer JWT (HS256/RS256 через jjwt или
//                                собственную реализацию на java.security).
//   2. TracingInterceptor    — извлечение/генерация trace-id, span-id, передача
//                                в заголовках W3C Trace Context (traceparent).
//   3. MetricsInterceptor     — счётчики запросов, гистограммы задержек,
//                                экспонент в Prometheus (через /metrics в REST).
//
// Маршрутизация запроса: TranslateRequest → ArbiterAgent.TranslateRequest →
// ожидание ArbiterDecision → упаковка в TranslateResponse.
//
// Примечание: сервис-определение .proto должно быть сгенерировано плагином
// akka-grpc; здесь мы используем абстрактный ApiContext, который инкапсулирует
// зависимость от сгенерированных классов (чтобы не требовать protoc на этапе
// компиляции asg-core в данной задаче).
// =============================================================================
package ru.smev.asg.api

import io.grpc.{Server, ServerInterceptor, ServerInterceptors, Status}
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.{Failure, Success}

import akka.actor.typed.ActorRef
import ru.smev.asg.agents.{ArbiterAgent, MatcherAgent}

object GrpcServer:

  private val log = LoggerFactory.getLogger(getClass)

  // ---------------------------------------------------------------------------
  // Контракт gRPC-сервиса (плоские case-классы, заменяющие сгенерированные
  // protobuf-типы до запуска protoc-плагина на CI).
  // ---------------------------------------------------------------------------
  final case class TranslateRequest(
    requestId:        String,
    sourceOntology:   String,
    targetOntology:   String,
    concept:          String,
    terms:            List[String],
    jwt:              String
  )

  final case class TranslateResponse(
    requestId:        String,
    accepted:         Boolean,
    targetConcept:    Option[String],
    confidence:       Double,
    findings:          List[String],
    outcome:           String,            // Accept / Reject / EscalateToOperator
    traceId:          String,
    tookMs:           Long
  )

  // ---------------------------------------------------------------------------
  // Интерфейс сервера.
  // ---------------------------------------------------------------------------
  trait ServerLike:
    def start(): Unit
    def awaitTermination(): Unit
    def shutdown(): Unit
    def port: Int

  // ---------------------------------------------------------------------------
  // Перехватчик #1: JWT-аутентификация.
  // ---------------------------------------------------------------------------
  /** Простой JWT-верификатор (HS256). В production подменяется на библиотеку
   *  jjwt или nimbus-jose-jwt. */
  trait JwtVerifier:
    /** Возвращает Option[JwtClaims], если токен валиден. */
    def verify(token: String): Option[JwtClaims]

  final case class JwtClaims(
    subject:  String,
    issuer:   String,
    audience: String,
    expiresAt: java.time.Instant,
    roles:    Set[String]
  )

  /** gRPC-перехватчик: пропускает вызов только при валидном JWT. */
  final class JwtAuthInterceptor(verifier: JwtVerifier) extends ServerInterceptor:
    override def interceptCall[ReqT, RespT](
      call: io.grpc.ServerCall[ReqT, RespT],
      headers: io.grpc.Metadata,
      next:   io.grpc.ServerCallHandler[ReqT, RespT]
    ): io.grpc.ServerCall.Listener[ReqT] =
      val authHeader = Option(headers.get(AuthorizationKey))
      val token = authHeader.map(_.stripPrefix("Bearer ").stripPrefix("bearer ").trim).filter(_.nonEmpty)
      token.flatMap(verifier.verify) match
        case Some(claims) =>
          ctxPutRole(claims.roles)
          next.startCall(call, headers)
        case None =>
          call.close(Status.UNAUTHENTICATED.withDescription("Невалидный или отсутствующий JWT"), headers)
          new io.grpc.ServerCall.Listener[ReqT] {}

    private def ctxPutRole(roles: Set[String]): Unit =
      val ctx = io.grpc.Context.current().withValue(RolesKey, roles.toArray: Array[AnyRef])
      ctx.attach()

    private val AuthorizationKey: io.grpc.Metadata.Key[String] =
      io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

    private val RolesKey: io.grpc.Context.Key[Array[AnyRef]] =
      io.grpc.Context.key("asg-roles")
  end JwtAuthInterceptor

  // ---------------------------------------------------------------------------
  // Перехватчик #2: трассировка (W3C Trace Context).
  // ---------------------------------------------------------------------------
  final class TracingInterceptor extends ServerInterceptor:
    override def interceptCall[ReqT, RespT](
      call:    io.grpc.ServerCall[ReqT, RespT],
      headers: io.grpc.Metadata,
      next:    io.grpc.ServerCallHandler[ReqT, RespT]
    ): io.grpc.ServerCall.Listener[ReqT] =
      val traceparent = Option(headers.get(TraceparentKey))
        .getOrElse(s"00-${java.util.UUID.randomUUID.toString.replace("-","")}-${java.util.UUID.randomUUID.toString.replace("-","").take(16)}-01")
      val (traceId, spanId) = parseTraceparent(traceparent)
      log.debug("[traceId={}] spanId={} — новый gRPC-вызов", traceId, spanId)
      val ctx = io.grpc.Context.current().withValue(TraceIdKey, traceId).withValue(SpanIdKey, spanId)
      ctx.attach()
      next.startCall(call, headers)

    private def parseTraceparent(tp: String): (String, String) =
      val parts = tp.split("-")
      if parts.length >= 3 then (parts(1), parts(2)) else ("?", "?")

    private val TraceparentKey: io.grpc.Metadata.Key[String] =
      io.grpc.Metadata.Key.of("traceparent", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

    val TraceIdKey: io.grpc.Context.Key[String] = io.grpc.Context.key("asg-trace-id")
    val SpanIdKey:  io.grpc.Context.Key[String] = io.grpc.Context.key("asg-span-id")
  end TracingInterceptor

  // ---------------------------------------------------------------------------
  // Перехватчик #3: метрики (Prometheus-совместимые счётчики).
  // ---------------------------------------------------------------------------
  object MetricsRegistry:
    private[api] val requestCount  = new LongAdder()
    private[api] val errorCount    = new LongAdder()
    private[api] val latencyBuckets: ConcurrentHashMap[String, LongAdder] = new ConcurrentHashMap()
    private val BucketsMs = Array(1L, 5L, 10L, 25L, 50L, 100L, 250L, 500L, 1_000L, 5_000L, 10_000L)

    /** Записать задержку запроса. */
    def observeLatency(ms: Long): Unit =
      BucketsMs.foreach { b =>
        if ms <= b then latencyBuckets.computeIfAbsent(s"le_$b", _ => new LongAdder()).increment()
      }
      latencyBuckets.computeIfAbsent("le_+Inf", _ => new LongAdder()).increment()

    def incRequest(): Unit = requestCount.increment()
    def incError():   Unit = errorCount.increment()

    /** Сериализация в текстовый формат Prometheus. */
    def toPrometheusText: String =
      val sb = new StringBuilder
      sb.append("# TYPE asg_grpc_requests_total counter\n")
      sb.append(s"asg_grpc_requests_total ${requestCount.sum()}\n")
      sb.append("# TYPE asg_grpc_errors_total counter\n")
      sb.append(s"asg_grpc_errors_total ${errorCount.sum()}\n")
      sb.append("# TYPE asg_grpc_request_duration_ms histogram\n")
      latencyBuckets.asScala.foreach { case (le, c) =>
        sb.append(s"""asg_grpc_request_duration_ms_bucket{le="$le"} ${c.sum()}\n""")
      }
      sb.toString()
  end MetricsRegistry

  final class MetricsInterceptor extends ServerInterceptor:
    override def interceptCall[ReqT, RespT](
      call:    io.grpc.ServerCall[ReqT, RespT],
      headers: io.grpc.Metadata,
      next:    io.grpc.ServerCallHandler[ReqT, RespT]
    ): io.grpc.ServerCall.Listener[ReqT] =
      val t0 = System.nanoTime()
      MetricsRegistry.incRequest()
      val delegate = next.startCall(call, headers)
      new io.grpc.ServerCall.Listener[ReqT]:
        override def onComplete(): Unit =
          MetricsRegistry.observeLatency((System.nanoTime() - t0) / 1_000_000)
          delegate.onComplete()
        override def onCancel(): Unit =
          MetricsRegistry.incError()
          delegate.onCancel()
        override def onMessage(message: ReqT): Unit = delegate.onMessage(message)
        override def onHalfClose(): Unit = delegate.onHalfClose()
        override def onReady(): Unit    = delegate.onReady()
  end MetricsInterceptor

  // ---------------------------------------------------------------------------
  // Реализация gRPC-сервера.
  // ---------------------------------------------------------------------------
  final class GrpcServerImpl(
    arbiter:  ActorRef[ArbiterAgent.Command],
    verifier: JwtVerifier,
    port:     Int = 50051,
    executor: ExecutionContext = ExecutionContext.fromExecutorService(
      java.util.concurrent.Executors.newWorkStealingPool(16)),
    underlyingExecutor: java.util.concurrent.Executor =
      java.util.concurrent.Executors.newWorkStealingPool(16)
  ) extends ServerLike:

    private var server: Option[Server] = None

    override def start(): Unit =
      val auth     = new JwtAuthInterceptor(verifier)
      val tracing  = new TracingInterceptor()
      val metrics  = new MetricsInterceptor()

      // Базовый ServerServiceDefinition для Translate. В реальной системе
      // здесь используется сгенерированный akka-grpc AsgServiceImpl.
      val baseSvc =
        io.grpc.ServerServiceDefinition.builder("asg.AsgService/Translate")
          .addMethod(
            io.grpc.MethodDescriptor.newBuilder[TranslateRequest, TranslateResponse]()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName("asg.AsgService/Translate")
              .setRequestMarshaller(translateRequestMarshaller)
              .setResponseMarshaller(translateResponseMarshaller)
              .build(),
            new io.grpc.stub.ServerCalls.UnaryMethod[TranslateRequest, TranslateResponse]:
              override def invoke(
                request: TranslateRequest,
                responseObserver: StreamObserver[TranslateResponse]
              ): Unit =
                val promise = Promise[ArbiterAgent.ArbiterDecision]()
                val arbiterRefAdapter = new akka.actor.typed.ActorRef[ArbiterAgent.ArbiterDecision]:
                  override def tell(msg: ArbiterAgent.ArbiterDecision): Unit =
                    promise.success(msg)
                  override def ref: akka.actor.typed.ActorRef[ArbiterAgent.ArbiterDecision] = this
                arbiter ! ArbiterAgent.TranslateRequest(
                  requestId      = request.requestId,
                  sourceOntology = request.sourceOntology,
                  targetOntology = request.targetOntology,
                  concept        = request.concept,
                  terms          = request.terms,
                  replyTo        = arbiterRefAdapter
                )
                // Асинхронный ответ с тайм-аутом 30 с.
                // Используем ScheduledExecutorService напрямую, чтобы не тащить
                // зависимость от ActorSystem scheduler'а в gRPC-сервер.
                timeoutScheduler.schedule(
                  new Runnable:
                    override def run(): Unit =
                      promise.tryFailure(
                        new java.util.concurrent.TimeoutException("arbiter timeout"))
                  ,
                  30L, TimeUnit.SECONDS
                )
                promise.future.onComplete {
                  case Success(d) =>
                    val resp = TranslateResponse(
                      requestId     = d.requestId,
                      accepted      = d.outcome == ArbiterAgent.ArbiterOutcome.Accept,
                      targetConcept = d.mapping.map(_.targetConcept),
                      confidence    = d.confidence,
                      findings      = d.findings,
                      outcome       = d.outcome.toString,
                      traceId       = TracingInterceptor.TraceIdKey.get(),
                      tookMs        = d.tookMs
                    )
                    responseObserver.onNext(resp)
                    responseObserver.onCompleted()
                  case Failure(e) =>
                    MetricsRegistry.incError()
                    responseObserver.onError(
                      Status.INTERNAL.withDescription(e.getMessage).asRuntimeException())
                }(using executor)
          )
          .build()

      // Цепочка перехватчиков: внешний → внутренний (auth → tracing → metrics).
      val svc = ServerInterceptors.interceptForward(
        ServerInterceptors.interceptForward(
          ServerInterceptors.interceptForward(baseSvc, metrics),
          tracing
        ),
        auth
      )

      val s = io.grpc.ServerBuilder.forPort(port)
        .executor(underlyingExecutor)
        .addService(svc)
        .maxInboundMessageSize(64 * 1024 * 1024)
        .build()
        .start()
      server = Some(s)
      log.info("gRPC-сервер запущен на порту {} (перехватчики: JWT, Tracing, Metrics)", port)

      Runtime.getRuntime.addShutdownHook(new Thread(() => s.shutdown()))

    // Scheduler, необходимый для тайм-аута запроса к ArbiterAgent.
    private def timeoutScheduler: java.util.concurrent.ScheduledExecutorService =
      timeoutSchedulerRef

    // Планировщик тайм-аутов gRPC-вызовов (daemon, 2 потока).
    private val timeoutSchedulerRef: java.util.concurrent.ScheduledExecutorService =
      java.util.concurrent.Executors.newScheduledThreadPool(
        2,
        new java.util.concurrent.ThreadFactory:
          private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
          override def newThread(r: Runnable): Thread =
            val t = new Thread(r, s"asg-grpc-timeout-${counter.incrementAndGet()}")
            t.setDaemon(true)
            t
      )

    override def awaitTermination(): Unit =
      server.foreach(_.awaitTermination())

    override def shutdown(): Unit =
      server.foreach { s =>
        s.shutdown().awaitTermination(10, TimeUnit.SECONDS)
      }

    override def port: Int = port

    // ---- Маршаллеры (текстовый/JSON-формат для демонстрации протокола). ----
    private val translateRequestMarshaller: io.grpc.MethodDescriptor.Marshaller[TranslateRequest] =
      new io.grpc.MethodDescriptor.Marshaller[TranslateRequest]:
        override def stream(value: TranslateRequest): java.io.InputStream =
          new java.io.ByteArrayInputStream(value.toString.getBytes("UTF-8"))
        override def parse(stream: java.io.InputStream): TranslateRequest =
          // Десериализация делегирована protobuf-генератору; заглушка для headless-сборки.
          TranslateRequest("", "", "", "", Nil, "")

    private val translateResponseMarshaller: io.grpc.MethodDescriptor.Marshaller[TranslateResponse] =
      new io.grpc.MethodDescriptor.Marshaller[TranslateResponse]:
        override def stream(value: TranslateResponse): java.io.InputStream =
          new java.io.ByteArrayInputStream(value.toString.getBytes("UTF-8"))
        override def parse(stream: java.io.InputStream): TranslateResponse =
          TranslateResponse("", accepted = false, None, 0.0, Nil, "", "", 0L)

  end GrpcServerImpl

end GrpcServer
