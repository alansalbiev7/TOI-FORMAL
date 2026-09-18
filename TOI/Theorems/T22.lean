import TOI.Theorems.T11

namespace TOI
universe u v
variable {α : Type u} {β : Type v} [BooleanAlgebra α] [BooleanAlgebra β]

/-- A finite knowledge base represented by conjunction, including the empty base. -/
def joint (constraint : α) : List α → α
  | [] => constraint
  | x :: xs => x ⊓ joint constraint xs

theorem joint_map (h : BoundedLatticeHom α β) (constraint : α) (xs : List α) :
    h (joint constraint xs) = joint (h constraint) (xs.map h) := by
  induction xs with
  | nil => rfl
  | cons x xs ih => simp only [joint, List.map_cons, map_inf, ih]

/-- T2.2: one shared translation, with explicit zero reflection. -/
theorem theorem_2_2 (h : BoundedLatticeHom α β) (hz : ReflectsZero h)
    (constraint : α) (xs : List α) :
    joint (h constraint) (xs.map h) ≠ ⊥ ↔ joint constraint xs ≠ ⊥ := by
  rw [← joint_map]
  exact nonzero_iff h hz _
end TOI
