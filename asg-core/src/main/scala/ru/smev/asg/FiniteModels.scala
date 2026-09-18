package ru.smev.asg

/** Powerset Boolean algebras: nonempty disjoint blocks covering the target universe
are a sufficient finite basis (§1.3.4). This object is not an OWL reasoner. */
final class AtomPartition private (val source: Set[String], val target: Set[String],
                                   private val images: Map[String, Set[String]]):
  def translate(concept: Set[String]): Set[String] =
    require(concept.subsetOf(source), "Unknown source atoms")
    concept.flatMap(images)

object AtomPartition:
  def checked(source: Set[String], target: Set[String], images: Map[String, Set[String]]):
      Either[String, AtomPartition] =
    if images.keySet != source then Left("Every source atom needs exactly one block")
    else if images.values.exists(s => s.isEmpty || !s.subsetOf(target)) then Left("Empty or foreign block")
    else if images.values.flatten.toSet != target then Left("Target is not covered")
    else if images.values.toVector.combinations(2).exists(p => (p(0) intersect p(1)).nonEmpty) then
      Left("Blocks overlap")
    else Right(new AtomPartition(source, target, images))

final case class Dependency(premises: Set[String], conclusion: String)
final case class ClosureResult(requirements: Set[String], reasons: Map[String, Dependency],
                               conflicts: Set[Set[String]])
object RequirementClosure:
  /** Termination: every strict iteration adds an element of a finite, fixed universe. */
  def compute(universe: Set[String], seed: Set[String], rules: Vector[Dependency],
              incompatible: Set[Set[String]] = Set.empty): ClosureResult =
    require(seed.subsetOf(universe), "Unknown seed requirement")
    require(rules.forall(r => r.premises.subsetOf(universe) && universe(r.conclusion)), "Foreign dependency")
    require(incompatible.forall(p => p.size == 2 && p.subsetOf(universe)), "Invalid conflict pair")
    var current = seed
    var reasons = Map.empty[String, Dependency]
    var changed = true
    while changed do
      changed = false
      rules.foreach { rule =>
        if rule.premises.subsetOf(current) && !current(rule.conclusion) then
          current += rule.conclusion
          reasons += rule.conclusion -> rule
          changed = true
      }
    ClosureResult(current, reasons, incompatible.filter(_.subsetOf(current)))
