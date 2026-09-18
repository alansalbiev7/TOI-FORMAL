package ru.smev.asg

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.{Files, Path}
import ru.smev.asg.verification.*

class VerificationTest:
  private val root = Path.of("..").toAbsolutePath.normalize()
  private val data = root.resolve("examples/valid-mapping.ttl")
  private val shapes = root.resolve("shapes/mapping.ttl")
  private val query = root.resolve("sparql/mapping-regression.rq")
  private def withText(text: String)(f: Path => Unit): Unit =
    val p = Files.createTempFile("toi-test-", ".ttl")
    try { Files.writeString(p, text); f(p) }
    finally Files.deleteIfExists(p)
  private def good = Verification.verify(data, shapes, query)

  @Test def validSelectedChecks(): Unit =
    assertTrue(good.passes(Verification.Required))
    assertEquals(Set("data", "shapes", "query"), good.bindings.keySet)

  @Test def missingArtifactFails(): Unit =
    assertThrows(classOf[IllegalArgumentException], () => Verification.verify(data, root.resolve("absent.ttl"), query))

  @Test def emptyArtifactFails(): Unit = withText("") { p =>
    assertThrows(classOf[IllegalArgumentException], () => Verification.verify(data, p, query))
  }

  @Test def malformedShapesAreError(): Unit = withText("not valid Turtle {{") { p =>
    val e = Verification.verify(data, p, query)
    assertEquals(CheckStatus.Error, e.checks.head.status)
    assertFalse(e.passes(Verification.Required))
  }

  @Test def noFocusDoesNotPass(): Unit = withText("@prefix ex: <https://example.org/> . ex:x ex:p ex:y .") { p =>
    assertEquals(CheckStatus.Error, Verification.verify(p, shapes, query).checks.head.status)
  }

  @Test def noShapesDoesNotPass(): Unit = withText("@prefix ex: <https://example.org/> . ex:x ex:p ex:y .") { p =>
    assertFalse(Verification.verify(data, p, query).passes(Verification.Required))
  }

  @Test def missingMeaningIsNotZeroOrSuccess(): Unit =
    val txt = Files.readString(data).replace("toi:targetMeaning \"accrued\" ;", "")
    withText(txt) { p => assertEquals(CheckStatus.Failed, Verification.verify(p, shapes, query).checks.head.status) }

  @Test def changedMeaningFailsRegression(): Unit =
    val txt = Files.readString(data).replace("toi:targetMeaning \"accrued\"", "toi:targetMeaning \"paid\"")
    withText(txt) { p =>
      val e = Verification.verify(p, shapes, query)
      assertEquals(CheckStatus.Passed, e.checks.head.status)
      assertEquals(CheckStatus.Failed, e.checks(1).status)
      assertNotEquals(good.bindings("data"), e.bindings("data"))
    }

  @Test def remoteServiceIsError(): Unit =
    withText("SELECT * WHERE { SERVICE <https://example.org/sparql> { ?s ?p ?o } }") { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
    }

  @Test def externalDatasetIsError(): Unit =
    withText("SELECT * FROM <https://example.org/data> WHERE { ?s ?p ?o }") { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
    }

  @Test def wrongQueryKindIsError(): Unit = withText("ASK { ?s ?p ?o }") { p =>
    assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
  }

  @Test def malformedQueryIsError(): Unit = withText("SELECT ???") { p =>
    assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
  }

  @Test def missingOrDuplicateChecksDoNotPass(): Unit =
    val e = good
    assertFalse(e.copy(checks = Vector.empty).passes(Verification.Required))
    assertFalse(e.copy(checks = e.checks ++ e.checks).passes(Verification.Required))
    assertFalse(e.passes(Set.empty))

  @Test def approvalIsBoundToExactContext(): Unit =
    val e = good
    val c = ReleaseContext("p1", "s1", "t1", "lookup", e.bindings)
    val approval = Approval(c, "reviewer")
    val release = ReleaseGate.admit(c, e, approval, Set("reviewer")).toOption.get
    assertTrue(ReleaseGate.usable(release, c, false))
    assertFalse(ReleaseGate.usable(release, c, true))
    assertFalse(ReleaseGate.usable(release, c.copy(targetVersion = "t2"), false))
    assertTrue(ReleaseGate.admit(c, e, approval, Set.empty).isLeft)
    assertTrue(ReleaseGate.admit(c.copy(operation = "other"), e, approval, Set("reviewer")).isLeft)
    assertTrue(ReleaseGate.admit(c.copy(bindings = Map.empty), e, approval, Set("reviewer")).isLeft)
    assertTrue(ReleaseGate.admit(c, e.copy(checks = e.checks.drop(1)), approval, Set("reviewer")).isLeft)

  @Test def forgedEmptyBindingsDoNotPass(): Unit =
    val c = ReleaseContext("p1", "s1", "t1", "lookup", Map.empty)
    assertTrue(ReleaseGate.admit(c, good.copy(bindings = Map.empty), Approval(c, "r"), Set("r")).isLeft)

  @Test def nestedRemoteServiceIsError(): Unit =
    withText("SELECT * WHERE { { SELECT * WHERE { SERVICE <https://example.org/sparql> { ?s ?p ?o } } } }") { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
    }

  @Test def disabledShapeDoesNotPass(): Unit =
    withText(Files.readString(shapes).replace("sh:targetClass toi:Mapping ;", "sh:targetClass toi:Mapping ; sh:deactivated true ;")) { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, p, query).checks.head.status)
    }

  @Test def serviceInsideExistsIsError(): Unit =
    withText("SELECT * WHERE { ?s ?p ?o FILTER EXISTS { SERVICE <https://example.org/sparql> { ?x ?y ?z } } }") { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, shapes, p).checks(1).status)
    }

  @Test def sparqlShapesAreNotSupported(): Unit =
    withText(Files.readString(shapes) + "\n<https://example.org/shape> sh:select \"SELECT * WHERE { ?s ?p ?o }\" .") { p =>
      assertEquals(CheckStatus.Error, Verification.verify(data, p, query).checks.head.status)
    }

  @Test def invalidUtf8IsNotRepairedSilently(): Unit =
    val p = Files.createTempFile("toi-invalid-utf8-", ".ttl")
    try
      Files.write(p, Array(0xc3.toByte, 0x28.toByte))
      assertEquals(CheckStatus.Error, Verification.verify(p, shapes, query).checks.head.status)
    finally Files.deleteIfExists(p)
