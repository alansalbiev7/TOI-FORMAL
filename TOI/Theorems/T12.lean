import Mathlib.Order.Ideal

/-! Ideal completion, monograph §1.4. Not a Cartesian closed category claim. -/
namespace TOI
open Order
universe u
variable {α : Type u} [Lattice α] [BoundedOrder α]

/-- Reuse mathlib's proved construction; completeness is not a premise on α. -/
noncomputable def idealCompletion : CompleteLattice (Ideal α) := inferInstance

omit [BoundedOrder α] in
theorem principal_order_iff (a b : α) : Ideal.principal a ≤ Ideal.principal b ↔ a ≤ b := by
  simp only [Ideal.principal_le_iff, Ideal.mem_principal]

omit [BoundedOrder α] in
theorem principal_injective : Function.Injective (Ideal.principal : α → Ideal α) := by
  intro a b h
  exact le_antisymm ((principal_order_iff a b).mp h.le)
    ((principal_order_iff b a).mp h.ge)

theorem principal_inf (a b : α) :
    Ideal.principal (a ⊓ b) = Ideal.principal a ⊓ Ideal.principal b := by
  apply le_antisymm
  · exact le_inf ((principal_order_iff _ _).mpr inf_le_left)
      ((principal_order_iff _ _).mpr inf_le_right)
  · intro x hx
    exact le_inf ((inf_le_left : Ideal.principal a ⊓ Ideal.principal b ≤ _) hx)
      ((inf_le_right : Ideal.principal a ⊓ Ideal.principal b ≤ _) hx)

theorem principal_sup (a b : α) :
    Ideal.principal (a ⊔ b) = Ideal.principal a ⊔ Ideal.principal b := by
  apply le_antisymm
  · intro x hx
    exact Ideal.mem_sup.mpr ⟨a, le_rfl, b, le_rfl, hx⟩
  · intro x hx
    rcases Ideal.mem_sup.mp hx with ⟨i, hi, j, hj, hij⟩
    exact hij.trans (sup_le_sup hi hj)

theorem principal_bot : Ideal.principal (⊥ : α) = ⊥ := by
  apply le_antisymm
  · exact Ideal.principal_le_iff.mpr (Ideal.bot_mem (⊥ : Ideal α))
  · exact (inferInstance : CompleteLattice (Ideal α)).bot_le _

theorem principal_top : Ideal.principal (⊤ : α) = ⊤ := by
  apply le_antisymm
  · exact (inferInstance : CompleteLattice (Ideal α)).le_top _
  · intro x _
    exact le_top

/-- Arbitrary meets, including the empty family, have the required membership. -/
theorem ideal_meet_membership (S : Set (Ideal α)) (x : α) :
    x ∈ sInf S ↔ ∀ I ∈ S, x ∈ I := Ideal.mem_sInf

/-- Universal property of the join (the generated ideal, not set union). -/
theorem ideal_join_le (S : Set (Ideal α)) (J : Ideal α) :
    sSup S ≤ J ↔ ∀ I ∈ S, I ≤ J := sSup_le_iff
end TOI

namespace TOI
open Order
universe u
variable {α : Type u} [DistribLattice α] [BoundedOrder α]

/-- Right adjoint to intersection with I, built explicitly from distributivity. -/
def idealResidual (I K : Ideal α) : Ideal α where
  carrier := {x | ∀ a ∈ I, a ⊓ x ∈ K}
  lower' := by
    intro x y hxy hy a ha
    exact K.lower (inf_le_inf_left a hxy) (hy a ha)
  nonempty' := ⟨⊥, by intro a _; simp⟩
  directed' := by
    intro x hx y hy
    refine ⟨x ⊔ y, ?_, le_sup_left, le_sup_right⟩
    intro a ha
    rw [inf_sup_left]
    exact Ideal.sup_mem (hx a ha) (hy a ha)

/-- The frame law in T1.2; no stronger complete-distributivity assertion. -/
theorem ideal_frame_law (I : Ideal α) (S : Set (Ideal α)) :
    I ⊓ sSup S = sSup ((fun J => I ⊓ J) '' S) := by
  apply le_antisymm
  · let K := sSup ((fun J => I ⊓ J) '' S)
    have hsup : sSup S ≤ idealResidual I K := by
      apply sSup_le
      intro J hJ x hx a ha
      have hm : a ⊓ x ∈ I ⊓ J :=
        ⟨I.lower inf_le_left ha, J.lower inf_le_right hx⟩
      exact (le_sSup (show I ⊓ J ∈ (fun J => I ⊓ J) '' S from ⟨J, hJ, rfl⟩)) hm
    intro x hx
    have h := hsup hx.2 x hx.1
    simpa using h
  · apply sSup_le
    rintro _ ⟨J, hJ, rfl⟩
    exact inf_le_inf_left I (le_sSup hJ)
end TOI
