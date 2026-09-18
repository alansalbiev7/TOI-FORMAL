package ru.smev.asg
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FiniteModelsTest:
  @Test def allBooleanCombinationsRespectPartition(): Unit =
    val source = Set("a", "b", "c")
    val target = Set("x", "y", "z", "w")
    val h = AtomPartition.checked(source, target,
      Map("a" -> Set("x", "y"), "b" -> Set("z"), "c" -> Set("w"))).toOption.get
    for x <- source.subsets(); y <- source.subsets() do
      assertEquals(h.translate(x) intersect h.translate(y), h.translate(x intersect y))
      assertEquals(h.translate(x) union h.translate(y), h.translate(x union y))
      assertEquals(target diff h.translate(x), h.translate(source diff x))
      assertEquals(x.subsetOf(y), h.translate(x).subsetOf(h.translate(y)))
      assertEquals(x.isEmpty, h.translate(x).isEmpty)

  @Test def invalidPartitionsRejected(): Unit =
    val s = Set("a", "b"); val t = Set("x", "y")
    assertTrue(AtomPartition.checked(s, t, Map("a" -> Set("x"))).isLeft)
    assertTrue(AtomPartition.checked(s, t, Map("a" -> Set("x"), "b" -> Set("x", "y"))).isLeft)
    assertTrue(AtomPartition.checked(s, t, Map("a" -> Set("x"), "b" -> Set.empty[String])).isLeft)
    assertTrue(AtomPartition.checked(s, t, Map("a" -> Set("x"), "b" -> Set("z"))).isLeft)
    assertTrue(AtomPartition.checked(Set.empty, t, Map.empty).isLeft)

  @Test def unknownAtomsRejected(): Unit =
    val h = AtomPartition.checked(Set("a"), Set("x"), Map("a" -> Set("x"))).toOption.get
    assertThrows(classOf[IllegalArgumentException], () => h.translate(Set("b")))

  @Test def closureLawsAndConflictDetection(): Unit =
    val u = Set("a", "b", "c", "d", "e")
    val rules = Vector(Dependency(Set("a"), "b"), Dependency(Set("b"), "a"),
      Dependency(Set("b"), "c"), Dependency(Set("a", "d"), "e"))
    val conflict = Set(Set("c", "d"))
    def cl(s: Set[String]) = RequirementClosure.compute(u, s, rules, conflict)
    for x <- u.subsets() do
      val c = cl(x)
      assertTrue(x.subsetOf(c.requirements))
      assertEquals(c.requirements, cl(c.requirements).requirements)
      for y <- u.subsets() if x.subsetOf(y) do
        assertTrue(c.requirements.subsetOf(cl(y).requirements))
    val c = cl(Set("a", "d"))
    assertEquals(u, c.requirements)
    assertEquals(conflict, c.conflicts)
    assertEquals(Set("b", "c", "e"), c.reasons.keySet)

  @Test def foreignDependenciesRejected(): Unit =
    assertThrows(classOf[IllegalArgumentException], () =>
      RequirementClosure.compute(Set("a"), Set("a"), Vector(Dependency(Set("a"), "b"))))
