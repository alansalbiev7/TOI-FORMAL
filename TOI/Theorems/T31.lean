import Mathlib.Order.Closure
import Mathlib.Data.Set.Lattice

/-! §3.2: closed sets of requirements, not necessarily jointly realizable profiles. -/
namespace TOI
universe u
variable {U : Type u}

/-- Closure's extensivity, monotonicity and idempotence are explicit structure laws. -/
abbrev RequirementClosure (U : Type u) := ClosureOperator (Set U)

def ClosedProfile (cl : RequirementClosure U) (P : Set U) : Prop := cl P = P

theorem closed_intersection (cl : RequirementClosure U) (S : Set (Set U))
    (hs : ∀ P ∈ S, ClosedProfile cl P) : ClosedProfile cl (⋂₀ S) := by
  apply le_antisymm
  · intro x hx P hP
    have hm : cl (⋂₀ S) ⊆ cl P := cl.monotone (fun _ ha => ha P hP)
    have hh := hm hx
    rw [show cl P = P from hs P hP] at hh
    exact hh
  · exact cl.le_closure _

theorem closed_join (cl : RequirementClosure U) (S : Set (Set U)) :
    ClosedProfile cl (cl (⋃₀ S)) := cl.idempotent _

theorem join_is_least (cl : RequirementClosure U) (S : Set (Set U)) (P : Set U)
    (hp : ClosedProfile cl P) : cl (⋃₀ S) ⊆ P ↔ ∀ Q ∈ S, Q ⊆ P := by
  constructor
  · intro h Q hQ x hx
    exact h (cl.le_closure _ ⟨Q, hQ, hx⟩)
  · intro h
    calc
      cl (⋃₀ S) ⊆ cl P := cl.monotone (by rintro x ⟨Q, hQ, hx⟩; exact h Q hQ hx)
      _ = P := hp

/-- T3.1 via explicit greatest/least bounds for every family, including empty. -/
theorem theorem_3_1 (cl : RequirementClosure U) (S : Set (Set U))
    (hs : ∀ P ∈ S, ClosedProfile cl P) :
    ClosedProfile cl (⋂₀ S) ∧ ClosedProfile cl (cl (⋃₀ S)) ∧
    (∀ P, P ⊆ ⋂₀ S ↔ ∀ Q ∈ S, P ⊆ Q) ∧
    (∀ P, ClosedProfile cl P → (cl (⋃₀ S) ⊆ P ↔ ∀ Q ∈ S, Q ⊆ P)) := by
  refine ⟨closed_intersection cl S hs, closed_join cl S, ?_, ?_⟩
  · intro P
    exact Set.subset_sInter_iff
  · exact fun P hp => join_is_least cl S P hp
end TOI
