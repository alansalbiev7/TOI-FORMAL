import TOI.Semantics
import Mathlib.Data.Set.Lattice
import Mathlib.Order.Fin.Basic

/-! Small kernel-checked boundaries. Each example states exactly what fails;
none is claimed to establish independence of every assumption. -/
namespace TOI.Countermodels

/-- Projection preserves Boolean operations but identifies a nonempty set with ∅. -/
def restrictFalse (s : Set Bool) : Set Unit := fun _ => false ∈ s

theorem projection_loses_information :
    restrictFalse {true} = ∅ ∧ ({true} : Set Bool) ≠ ∅ := by
  constructor
  · apply Set.eq_empty_iff_forall_not_mem.mpr
    intro x hx
    have : false = true := hx
    cases this
  · simp

/-- A monotone zero-reflecting map on a 3-element chain need not be injective. -/
def collapse (x : Fin 3) : Fin 3 := if x = 0 then 0 else 1

theorem monotone_not_injective :
    Monotone collapse ∧ (∀ x, collapse x = 0 → x = 0) ∧ ¬ Function.Injective collapse := by
  refine ⟨?_, ?_, ?_⟩
  · intro x y hxy
    by_cases hx : x = 0
    · simp [collapse, hx]
    · have hy : y ≠ 0 := by
        intro hy
        subst y
        exact hx (le_antisymm hxy (by exact Fin.zero_le _))
      simp [collapse, hx, hy]
  · intro x hx
    by_contra hn
    simp [collapse, hn] at hx
  · intro hi
    have h : (1 : Fin 3) = 2 := hi (show collapse 1 = collapse 2 by decide)
    cases h

/-- Same atoms and domain, different roles: Boolean agreement does not imply ALC agreement. -/
def noEdges : Interpretation Unit Unit Bool := ⟨inferInstance, fun _ _ => True, fun _ _ _ => False⟩
def selfEdges : Interpretation Unit Unit Bool := ⟨inferInstance, fun _ _ => True, fun _ x y => x = y⟩

theorem roles_matter :
    (∀ a x, noEdges.atom a x ↔ selfEdges.atom a x) ∧
    ¬ ((Concept.existsRole () (.atom ())).holds noEdges false ↔
       (Concept.existsRole () (.atom ())).holds selfEdges false) := by
  simp [noEdges, selfEdges, Concept.holds]

/-- One inhabited concept is not a model of an inconsistent TBox. -/
theorem nontrivial_model_predicate :
    ¬ IsModel {(.atom (), .bot)} selfEdges := by
  intro hm
  have := hm (.atom (), .bot) (by simp) false trivial
  exact this

/-- A finite closed-set lattice M₃: closure of two distinct singleton requirements
is the universe, so completeness does not imply distributivity. -/
noncomputable def diamondClosure (s : Set (Fin 3)) : Set (Fin 3) := by
  classical
  exact if ∃ x ∈ s, ∃ y ∈ s, x ≠ y then Set.univ else s

theorem diamond_extensive (s : Set (Fin 3)) : s ⊆ diamondClosure s := by
  classical
  unfold diamondClosure
  split_ifs
  · exact Set.subset_univ _
  · exact Set.Subset.rfl

theorem diamond_monotone : Monotone diamondClosure := by
  classical
  intro s t hst
  unfold diamondClosure
  split_ifs with hs ht ht
  · exact Set.Subset.rfl
  · obtain ⟨x, hx, y, hy, hne⟩ := hs
    exact False.elim (ht ⟨x, hst hx, y, hst hy, hne⟩)
  · exact Set.subset_univ _
  · exact hst

theorem diamond_idempotent (s : Set (Fin 3)) :
    diamondClosure (diamondClosure s) = diamondClosure s := by
  classical
  have huniv : diamondClosure (Set.univ : Set (Fin 3)) = Set.univ := by
    apply Set.Subset.antisymm (Set.subset_univ _)
    exact diamond_extensive _
  by_cases h : ∃ x ∈ s, ∃ y ∈ s, x ≠ y
  · have hs : diamondClosure s = Set.univ := by simp [diamondClosure, h]
    rw [hs, huniv]
  · have hs : diamondClosure s = s := by simp [diamondClosure, h]
    rw [hs]
    exact hs

theorem diamond_not_distributive :
    ({0} : Set (Fin 3)) ∩ diamondClosure ({1} ∪ {2}) ≠
      diamondClosure (({0} ∩ {1}) ∪ ({0} ∩ {2})) := by
  intro heq
  have hm : (0 : Fin 3) ∈ ({0} : Set (Fin 3)) ∩ diamondClosure ({1} ∪ {2}) := by
    simp [diamondClosure]
  have hn : (0 : Fin 3) ∉ diamondClosure (({0} ∩ {1}) ∪ ({0} ∩ {2})) := by
    have hempty : (({0} ∩ {1}) ∪ ({0} ∩ {2}) : Set (Fin 3)) = ∅ := by
      ext x
      simp only [Set.mem_union, Set.mem_inter_iff, Set.mem_singleton_iff,
        Set.mem_empty_iff_false, iff_false]
      rintro (⟨rfl, h⟩ | ⟨rfl, h⟩) <;> cases h
    rw [hempty]
    simp [diamondClosure]
  exact hn (heq ▸ hm)
end TOI.Countermodels
