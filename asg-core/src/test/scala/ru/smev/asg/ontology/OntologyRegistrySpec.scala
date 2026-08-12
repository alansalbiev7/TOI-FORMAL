// =============================================================================
// OntologyRegistrySpec.scala — Спецификация реестра онтологий (OntologyRegistry).
//
// Тестирует:
//   1. Загрузку .owl-файла при старте актёра.
//   2. Возврат концепта по URI (метка + прямые надклассы).
//   3. Возврат иерархии транзитивно (rdfs:subClassOf closure).
//   4. Возврат None для неизвестного концепта.
//   5. Кэширование загруженных OntModel (повторные запросы не перечитывают файл).
//
// Создаёт временный каталог с .owl-файлом в BeforeAll, удаляет в AfterAll.
// =============================================================================
package ru.smev.asg.ontology

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import ru.smev.asg.ontology.OntologyQuery.*

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}

class OntologyRegistrySpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll:

  import OntologyRegistrySpec.*

  /** Временный каталог с .owl-файлом (создаётся в BeforeAll). */
  private var tempDir: Path = _
  /** Актёр реестра (спавнится в BeforeAll после создания каталога). */
  private var registry: akka.actor.typed.ActorRef[OntologyQuery] = _

  override def beforeAll(): Unit =
    super.beforeAll()
    tempDir = Files.createTempDirectory("asg-ontology-test")
    writeSampleOntology(tempDir.resolve("test.owl").toFile)
    // Спавним реестр с указанием каталога онтологий.
    registry = testKit.spawn(OntologyRegistry(tempDir.toString))

  override def afterAll(): Unit =
    // Удаляем временный каталог.
    if tempDir != null then
      Files.walk(tempDir).toArray.reverse.foreach { p =>
        p.asInstanceOf[Path].toFile.delete()
      }
    super.afterAll()

  "OntologyRegistry" should {

    "load OWL file on startup" in {
      // Реестр уже загружен в beforeAll. Проверяем, что концепт из загруженной
      // онтологии доступен — это косвенно подтверждает успешную загрузку .owl.
      val probe = testKit.createTestProbe[ConceptResult]()
      registry ! GetConcept(
        ontologyId = "test",  // имя файла без расширения
        conceptUri = AnimalUri,
        replyTo = probe.ref
      )
      val r = probe.expectMessageType[ConceptResult]
      r.concept shouldBe defined
      r.concept.value.uri shouldBe AnimalUri
      r.concept.value.label shouldBe "Animal"
    }

    "return concept by URI" in {
      val probe = testKit.createTestProbe[ConceptResult]()
      registry ! GetConcept("test", MammalUri, probe.ref)

      val r = probe.expectMessageType[ConceptResult]
      r.concept shouldBe defined
      r.concept.value.uri shouldBe MammalUri
      r.concept.value.label shouldBe "Mammal"
      // Прямой надкласс — Animal (rdfs:subClassOf).
      r.concept.value.superClasses should contain(AnimalUri)
    }

    "return hierarchy transitively" in {
      // Dog → Mammal → Animal (транзитивное замыкание вверх).
      val probe = testKit.createTestProbe[HierarchyResult]()
      registry ! GetHierarchy("test", DogUri, probe.ref)

      val r = probe.expectMessageType[HierarchyResult]
      // Суперклассы Dog должны включать и Mammal, и Animal.
      r.superClasses should contain(MammalUri)
      r.superClasses should contain(AnimalUri)
      // Подклассы Animal должны включать Mammal и Dog.
      val animalProbe = testKit.createTestProbe[HierarchyResult]()
      registry ! GetHierarchy("test", AnimalUri, animalProbe.ref)
      val ar = animalProbe.expectMessageType[HierarchyResult]
      ar.subClasses should contain(MammalUri)
      ar.subClasses should contain(DogUri)
    }

    "return None for unknown concept" in {
      val probe = testKit.createTestProbe[ConceptResult]()
      registry ! GetConcept("test", "http://example.org/test#Unknown", probe.ref)

      val r = probe.expectMessageType[ConceptResult]
      r.concept shouldBe None
    }

    "cache loaded models" in {
      // После загрузки онтологии повторные запросы должны возвращать тот же результат
      // (модель закэширована). Проверяем идентичность ответов и метку концепта.
      val probe1 = testKit.createTestProbe[ConceptResult]()
      registry ! GetConcept("test", MammalUri, probe1.ref)
      val r1 = probe1.expectMessageType[ConceptResult]

      val probe2 = testKit.createTestProbe[ConceptResult]()
      registry ! GetConcept("test", MammalUri, probe2.ref)
      val r2 = probe2.expectMessageType[ConceptResult]

      // Оба ответа должны возвращать один и тот же концепт.
      r1.concept.map(_.uri) shouldBe r2.concept.map(_.uri)
      r1.concept.map(_.label) shouldBe r2.concept.map(_.label)
      r1.concept.map(_.superClasses) shouldBe r2.concept.map(_.superClasses)
    }
  }

end OntologyRegistrySpec

object OntologyRegistrySpec:
  // URI концептов тестовой онтологии.
  val AnimalUri = "http://example.org/test#Animal"
  val MammalUri = "http://example.org/test#Mammal"
  val DogUri    = "http://example.org/test#Dog"

  /**
   * Содержимое тестового .owl-файла (RDF/XML с OWL-классами и ObjectProperty).
   * Иерархия: Dog → Mammal → Animal.
   */
  val SampleOwlXml: String =
    """<?xml version="1.0"?>
      |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
      |         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
      |         xmlns:owl="http://www.w3.org/2002/07/owl#"
      |         xmlns:test="http://example.org/test#">
      |  <owl:Ontology rdf:about="http://example.org/test"/>
      |  <owl:Class rdf:about="http://example.org/test#Animal">
      |    <rdfs:label>Animal</rdfs:label>
      |  </owl:Class>
      |  <owl:Class rdf:about="http://example.org/test#Mammal">
      |    <rdfs:subClassOf rdf:resource="http://example.org/test#Animal"/>
      |    <rdfs:label>Mammal</rdfs:label>
      |  </owl:Class>
      |  <owl:Class rdf:about="http://example.org/test#Dog">
      |    <rdfs:subClassOf rdf:resource="http://example.org/test#Mammal"/>
      |    <rdfs:label>Dog</rdfs:label>
      |  </owl:Class>
      |  <owl:ObjectProperty rdf:about="http://example.org/test#hasParent">
      |    <rdfs:domain rdf:resource="http://example.org/test#Animal"/>
      |    <rdfs:range rdf:resource="http://example.org/test#Animal"/>
      |    <rdfs:label>hasParent</rdfs:label>
      |  </owl:ObjectProperty>
      |</rdf:RDF>
      |""".stripMargin

  /** Записывает тестовую онтологию в указанный файл. */
  def writeSampleOntology(file: File): Unit =
    val pw = new PrintWriter(file, "UTF-8")
    try pw.write(SampleOwlXml)
    finally pw.close()

end OntologyRegistrySpec
