// =============================================================================
// ShaclValidatorSpec.scala — Спецификация SHACL-валидатора (ShaclValidatorImpl).
//
// Тестирует:
//   1. Загрузку всех .ttl-файлов из каталога shapes.
//   2. Валидацию OM-1 (сохранение иерархии концептов).
//   3. Валидацию OM-2 (сохранение объединения).
//   4. Валидацию OM-3 (сохранение ограничений ролей).
//   5. conforms=true для корректного отображения.
//   6. Возврат списка нарушений для некорректного отображения.
//
// Создаёт временный каталог с .ttl-файлами SHACL-форм в BeforeAll.
// =============================================================================
package ru.smev.asg.verification

import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.vocabulary.RDF
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}

class ShaclValidatorSpec
  extends AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll:

  import ShaclValidatorSpec.*

  /** Временный каталог с .ttl-файлами SHACL-форм. */
  private var shapesDir: Path = _
  /** Экземпляр валидатора (создаётся после подготовки каталога). */
  private var validator: ShaclValidatorImpl = _
  /** Загруженная модель SHACL-форм (для 2-аргументного API validate(graph, shapes)). */
  private var shapesModel: Model = _

  override def beforeAll(): Unit =
    super.beforeAll()
    shapesDir = Files.createTempDirectory("asg-shacl-test")
    writeShapes(shapesDir.resolve("om1-hierarchy.ttl").toFile, Om1ShapesTtl)
    writeShapes(shapesDir.resolve("om2-union.ttl").toFile, Om2ShapesTtl)
    writeShapes(shapesDir.resolve("om3-role.ttl").toFile, Om3ShapesTtl)
    // Создаём экземпляр валидатора — он загружает все .ttl из каталога.
    validator = new ShaclValidatorImpl(shapesDir.toString)
    // Также загружаем формы в отдельную модель для тестов 2-аргументного API.
    shapesModel = loadShapesFromDir(shapesDir)

  override def afterAll(): Unit =
    if shapesDir != null then
      Files.walk(shapesDir).toArray.reverse.foreach { p =>
        p.asInstanceOf[Path].toFile.delete()
      }
    super.afterAll()

  "ShaclValidator" should {

    "load all .ttl shapes from directory" in {
      // Косвенно проверяем загрузку: валидатор не падает при создании (beforeAll),
      // и последующие тесты валидации работают (значит, формы загружены).
      // Дополнительно проверяем, что conforms возвращает true для пустой модели
      // (нет триплетов, нарушающих формы — targetClass не заданы).
      val emptyModel = ModelFactory.createDefaultModel()
      val report = validator.validate(emptyModel, shapesModel)
      report.conforms shouldBe true
    }

    "validate OM-1 hierarchy preservation" in {
      // Корректная модель: концепт имеет родителя (ex:parent).
      val validModel = buildModel { m =>
        val ex = "http://example.org/"
        m.createResource(ex + "Concept1")
          .addProperty(RDF.`type`, m.createResource(ex + "Concept"))
          .addProperty(m.createProperty(ex + "parent"), m.createResource(ex + "ParentConcept"))
      }
      val validReport = validator.validate(validModel, shapesModel)
      validReport.conforms shouldBe true

      // Некорректная модель: концепт без родителя (нарушение OM-1).
      val invalidModel = buildModel { m =>
        val ex = "http://example.org/"
        m.createResource(ex + "Concept2")
          .addProperty(RDF.`type`, m.createResource(ex + "Concept"))
        // ex:parent отсутствует
      }
      val invalidReport = validator.validate(invalidModel, shapesModel)
      invalidReport.conforms shouldBe false
      invalidReport.results should not be empty
      // Хотя бы одно нарушение должно содержать сообщение об OM-1.
      invalidReport.results.map(_.message) should contain("OM-1: иерархия нарушена — у концепта должен быть родитель")
    }

    "validate OM-2 union preservation" in {
      val ex = "http://example.org/"
      // Корректная модель: Mapping имеет targetClass.
      val validModel = buildModel { m =>
        m.createResource(ex + "Mapping1")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
          .addProperty(m.createProperty(ex + "targetClass"), m.createResource(ex + "TargetConcept"))
      }
      val validReport = validator.validate(validModel, shapesModel)
      validReport.conforms shouldBe true

      // Некорректная модель: Mapping без targetClass (нарушение OM-2).
      val invalidModel = buildModel { m =>
        m.createResource(ex + "Mapping2")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
        // ex:targetClass отсутствует
      }
      val invalidReport = validator.validate(invalidModel, shapesModel)
      invalidReport.conforms shouldBe false
      invalidReport.results.map(_.message) should contain("OM-2: объединение нарушено — mapping должен иметь targetClass")
    }

    "validate OM-3 role restriction preservation" in {
      val ex = "http://example.org/"
      // Корректная модель: Mapping.role указывает на ресурс типа ex:Role.
      val validModel = buildModel { m =>
        val role = m.createResource(ex + "Role1")
          .addProperty(RDF.`type`, m.createResource(ex + "Role"))
        m.createResource(ex + "Mapping1")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
          .addProperty(m.createProperty(ex + "targetClass"), m.createResource(ex + "TargetConcept"))
          .addProperty(m.createProperty(ex + "role"), role)
      }
      val validReport = validator.validate(validModel, shapesModel)
      validReport.conforms shouldBe true

      // Некорректная модель: role указывает на ресурс НЕ типа ex:Role.
      val invalidModel = buildModel { m =>
        val notRole = m.createResource(ex + "NotRole")
          .addProperty(RDF.`type`, m.createResource(ex + "OtherClass"))
        m.createResource(ex + "Mapping2")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
          .addProperty(m.createProperty(ex + "targetClass"), m.createResource(ex + "TargetConcept"))
          .addProperty(m.createProperty(ex + "role"), notRole)
      }
      val invalidReport = validator.validate(invalidModel, shapesModel)
      invalidReport.conforms shouldBe false
      invalidReport.results.map(_.message) should contain("OM-3: ограничение роли нарушено")
    }

    "return conforms=true for valid mapping" in {
      val ex = "http://example.org/"
      // Полностью корректное отображение: удовлетворяет OM-1, OM-2, OM-3.
      val validModel = buildModel { m =>
        val role = m.createResource(ex + "Role1")
          .addProperty(RDF.`type`, m.createResource(ex + "Role"))
        m.createResource(ex + "Concept1")
          .addProperty(RDF.`type`, m.createResource(ex + "Concept"))
          .addProperty(m.createProperty(ex + "parent"), m.createResource(ex + "ParentConcept"))
        m.createResource(ex + "Mapping1")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
          .addProperty(m.createProperty(ex + "targetClass"), m.createResource(ex + "TargetConcept"))
          .addProperty(m.createProperty(ex + "role"), role)
      }
      val report = validator.validate(validModel, shapesModel)
      report.conforms shouldBe true
      report.results shouldBe empty
    }

    "return violations list for invalid mapping" in {
      val ex = "http://example.org/"
      // Некорректное отображение: нарушает все три OM-ограничения.
      val invalidModel = buildModel { m =>
        // Концепт без родителя (OM-1).
        m.createResource(ex + "Concept1")
          .addProperty(RDF.`type`, m.createResource(ex + "Concept"))
        // Mapping без targetClass и с некорректной ролью (OM-2 + OM-3).
        val notRole = m.createResource(ex + "NotRole")
          .addProperty(RDF.`type`, m.createResource(ex + "OtherClass"))
        m.createResource(ex + "Mapping1")
          .addProperty(RDF.`type`, m.createResource(ex + "Mapping"))
          .addProperty(m.createProperty(ex + "role"), notRole)
      }
      val report = validator.validate(invalidModel, shapesModel)
      report.conforms shouldBe false
      report.results should not be empty
      // Должны быть нарушения всех трёх типов.
      val messages = report.results.map(_.message)
      messages should contain("OM-1: иерархия нарушена — у концепта должен быть родитель")
      messages should contain("OM-2: объединение нарушено — mapping должен иметь targetClass")
      messages should contain("OM-3: ограничение роли нарушено")
      // Все нарушения имеют severity Violation.
      report.results.foreach(_.severity shouldBe Severity.Violation)
    }
  }

end ShaclValidatorSpec

object ShaclValidatorSpec:

  /** OM-1: иерархия концептов — каждый Concept должен иметь хотя бы одного родителя. */
  val Om1ShapesTtl: String =
    """@prefix sh: <http://www.w3.org/ns/shacl#> .
      |@prefix ex: <http://example.org/> .
      |
      |ex:OM1Shape a sh:NodeShape ;
      |  sh:targetClass ex:Concept ;
      |  sh:property [
      |    sh:path ex:parent ;
      |    sh:minCount 1 ;
      |    sh:message "OM-1: иерархия нарушена — у концепта должен быть родитель" ;
      |    sh:severity sh:Violation ;
      |  ] .
      |""".stripMargin

  /** OM-2: объединение — каждый Mapping должен иметь targetClass. */
  val Om2ShapesTtl: String =
    """@prefix sh: <http://www.w3.org/ns/shacl#> .
      |@prefix ex: <http://example.org/> .
      |
      |ex:OM2Shape a sh:NodeShape ;
      |  sh:targetClass ex:Mapping ;
      |  sh:property [
      |    sh:path ex:targetClass ;
      |    sh:minCount 1 ;
      |    sh:message "OM-2: объединение нарушено — mapping должен иметь targetClass" ;
      |    sh:severity sh:Violation ;
      |  ] .
      |""".stripMargin

  /** OM-3: роль — ex:role должна быть экземпляром класса ex:Role. */
  val Om3ShapesTtl: String =
    """@prefix sh: <http://www.w3.org/ns/shacl#> .
      |@prefix ex: <http://example.org/> .
      |
      |ex:OM3Shape a sh:NodeShape ;
      |  sh:targetClass ex:Mapping ;
      |  sh:property [
      |    sh:path ex:role ;
      |    sh:class ex:Role ;
      |    sh:message "OM-3: ограничение роли нарушено" ;
      |    sh:severity sh:Violation ;
      |  ] .
      |""".stripMargin

  /** Записывает строку в .ttl-файл. */
  def writeShapes(file: File, content: String): Unit =
    val pw = new PrintWriter(file, "UTF-8")
    try pw.write(content)
    finally pw.close()

  /** Хелпер: создаёт Jena Model из замыкания, добавляющего триплеты. */
  def buildModel(f: Model => Unit): Model =
    val m = ModelFactory.createDefaultModel()
    f(m)
    m

  /** Загружает все .ttl из указанного каталога в одну Jena Model. */
  def loadShapesFromDir(dir: Path): Model =
    val merged = ModelFactory.createDefaultModel()
    Files.walk(dir).toArray
      .map(_.asInstanceOf[Path].toFile)
      .filter(_.getName.endsWith(".ttl"))
      .foreach { f => org.apache.jena.riot.RDFDataMgr.read(merged, f.getAbsolutePath) }
    merged

end ShaclValidatorSpec
