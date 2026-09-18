package ru.smev.asg.verification

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFParser}
import org.apache.jena.shacl.{Shapes, ShaclValidator}
import org.apache.jena.query.{Query, QueryFactory, QueryExecution}
import org.apache.jena.sparql.syntax.{ElementService, ElementSubQuery, ElementVisitorBase, ElementWalker}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import java.util.concurrent.TimeUnit

/** These are check outcomes, not interoperability maturity levels or formal proofs. */
enum CheckStatus:
  case Passed, Failed, Error

final case class CheckResult(id: String, status: CheckStatus, scope: String, details: Vector[String])
final case class Evidence(bindings: Map[String, String], checks: Vector[CheckResult]):
  /** A missing, duplicate or extra check must not silently satisfy a release policy. */
  def passes(required: Set[String]): Boolean =
    required.nonEmpty && checks.map(_.id).distinct.size == checks.size &&
      checks.map(_.id).toSet == required && checks.forall(_.status == CheckStatus.Passed)

object Artifacts:
  val MaxBytes: Int = 8 * 1024 * 1024
  def bytes(path: Path): Array[Byte] =
    require(Files.isRegularFile(path), s"Required artifact is absent: $path")
    val stream = Files.newInputStream(path)
    try
      val b = stream.readNBytes(MaxBytes + 1)
      require(b.length <= MaxBytes, s"Artifact exceeds $MaxBytes bytes: $path")
      require(b.nonEmpty, s"Empty artifact: $path")
      b
    finally stream.close()
  def sha256(b: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(b).map(x => f"${x & 0xff}%02x").mkString
  def utf8(b: Array[Byte]): String =
    UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString
  def turtle(b: Array[Byte]): Model =
    val m = ModelFactory.createDefaultModel()
    try
      RDFParser.fromString(utf8(b)).lang(Lang.TURTLE).parse(m)
      m
    catch
      case NonFatal(e) => m.close(); throw e

object Verification:
  val Required: Set[String] = Set("shacl", "query-regression")
  val Scope = "Explicit RDF graph; selected SHACL constraints and finite SELECT regression only"

  private def run(id: String)(f: => Vector[String]): CheckResult =
    try
      val issues = f
      CheckResult(id, if issues.isEmpty then CheckStatus.Passed else CheckStatus.Failed, Scope, issues)
    catch
      case NonFatal(e) => CheckResult(id, CheckStatus.Error, Scope, Vector(e.getClass.getSimpleName))

  private def requireLocal(q: Query): Unit =
    require(!"(?i)\\bSERVICE\\b".r.findFirstIn(q.toString).isDefined, "SERVICE is unsupported, including in expressions")
    require(!q.hasDatasetDescription, "External datasets are not permitted")
    ElementWalker.walk(q.getQueryPattern, new ElementVisitorBase {
      override def visit(e: ElementService): Unit =
        throw new IllegalArgumentException("Remote SERVICE is not permitted")
      override def visit(e: ElementSubQuery): Unit = requireLocal(e.getQuery)
    })

  private def hasActiveConstraints(s: org.apache.jena.shacl.parser.Shape,
                                   seen: Set[org.apache.jena.graph.Node] = Set.empty): Boolean =
    !s.deactivated() && !seen(s.getShapeNode) &&
      (!s.getConstraints.isEmpty || s.getPropertyShapes.asScala.exists(p =>
        hasActiveConstraints(p, seen + s.getShapeNode)))

  /** Files are read once and hashed. Configuration is local and trusted; no OWL import loading. */
  def verify(data: Path, shapes: Path, query: Path): Evidence =
    val artifacts = Vector("data" -> data, "shapes" -> shapes, "query" -> query)
      .map((key, path) => key -> Artifacts.bytes(path)).toMap
    val hashes = artifacts.map((key, b) => key -> Artifacts.sha256(b))
    val checks = Vector(
      run("shacl") {
        val graph = Artifacts.turtle(artifacts("data"))
        val shapeModel = try Artifacts.turtle(artifacts("shapes"))
          catch
            case NonFatal(e) => graph.close(); throw e
        try
          val sh = "http://www.w3.org/ns/shacl#"
          val unsupported = Set("sparql", "select", "ask", "construct", "js", "jsFunctionName", "jsLibrary")
          require(!unsupported.exists(k => shapeModel.contains(null, shapeModel.createProperty(sh + k))),
            "Only SHACL Core configurations are supported")
          val parsed = Shapes.parse(shapeModel)
          require(!parsed.isEmpty, "No shapes")
          // Scope is deliberately a mapping graph with a required root shape targeting this class.
          val rdfType = graph.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
          val mappingType = graph.createResource("https://example.org/toi#Mapping")
          require(graph.contains(null, rdfType, mappingType), "No mapping focus nodes")
          val targetClass = shapeModel.createProperty("http://www.w3.org/ns/shacl#targetClass")
          require(parsed.getTargetShapes.asScala.exists(s =>
            !s.deactivated() && shapeModel.getGraph.contains(s.getShapeNode, targetClass.asNode(), mappingType.asNode()) &&
              hasActiveConstraints(s)), "No active mapping constraints")
          val report = ShaclValidator.get().validate(parsed, graph.getGraph)
          if report.conforms() then Vector.empty
          else report.getEntries.asScala.toVector.map(_.message()).map(Option(_).getOrElse("SHACL violation"))
        finally
          shapeModel.close()
          graph.close()
      },
      run("query-regression") {
        val graph = Artifacts.turtle(artifacts("data"))
        try
          val q = QueryFactory.create(Artifacts.utf8(artifacts("query")))
          require(q.isSelectType, "Only local SELECT regression queries are supported")
          requireLocal(q)
          val execution = QueryExecution.model(graph).query(q).timeout(5L, TimeUnit.SECONDS).build()
          try
            val results = execution.execSelect()
            val rows = Vector.newBuilder[String]
            var count = 0
            while results.hasNext && count < 100 do
              results.next()
              rows += "Selected regression constraint violated"
              count += 1
            rows.result()
          finally execution.close()
        finally graph.close()
      }
    )
    Evidence(hashes, checks)
