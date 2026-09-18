import TOI.Axioms

namespace TOI
universe u v w
variable {α : Type u} {β : Type v} [BooleanAlgebra α] [BooleanAlgebra β]

/-- Monograph Lemma 1.1; uses uniqueness of Boolean complement in mathlib. -/
theorem complement_preserved (h : BoundedLatticeHom α β) (x : α) :
    h xᶜ = (h x)ᶜ := map_compl' h x

/-- Monograph Lemma 1.2: zero reflection gives order reflection. -/
theorem order_iff (h : BoundedLatticeHom α β) (hz : ReflectsZero h) (x y : α) :
    h x ≤ h y ↔ x ≤ y := by
  constructor
  · intro hxy
    have hzero : h (x ⊓ yᶜ) = ⊥ := by
      rw [map_inf, map_compl', ← sdiff_eq]
      exact sdiff_eq_bot_iff.mpr hxy
    have hzxy := hz _ hzero
    rw [← sdiff_eq] at hzxy
    exact sdiff_eq_bot_iff.mp hzxy
  · exact fun hxy => OrderHomClass.mono h hxy

theorem injective_of_reflectsZero (h : BoundedLatticeHom α β) (hz : ReflectsZero h) :
    Function.Injective h := by
  intro x y heq
  exact le_antisymm ((order_iff h hz x y).mp heq.le)
    ((order_iff h hz y x).mp heq.ge)

theorem reflectsZero_iff_injective (h : BoundedLatticeHom α β) :
    ReflectsZero h ↔ Function.Injective h := by
  constructor
  · exact injective_of_reflectsZero h
  · intro hi x hx
    exact hi (hx.trans (map_bot h).symm)

theorem zero_iff (h : BoundedLatticeHom α β) (hz : ReflectsZero h) (x : α) :
    h x = ⊥ ↔ x = ⊥ := ⟨hz x, fun hx => hx ▸ map_bot h⟩

theorem nonzero_iff (h : BoundedLatticeHom α β) (hz : ReflectsZero h) (x : α) :
    h x ≠ ⊥ ↔ x ≠ ⊥ := not_congr (zero_iff h hz x)

theorem equality_iff (h : BoundedLatticeHom α β) (hz : ReflectsZero h) (x y : α) :
    h x = h y ↔ x = y := ⟨fun heq => injective_of_reflectsZero h hz heq, congrArg h⟩

/-- Structural induction covers every finite Boolean expression, not a sample. -/
theorem eval_commutes {Atom : Type w} (h : BoundedLatticeHom α β)
    (valuation : Atom → α) (q : BoolExpr Atom) :
    h (q.eval valuation) = q.eval (h ∘ valuation) := by
  induction q with
  | atom a => rfl
  | bot => exact map_bot h
  | top => exact map_top h
  | neg q ih => simp only [BoolExpr.eval, map_compl', ih]
  | and p q hp hq => simp only [BoolExpr.eval, map_inf, hp, hq]
  | or p q hp hq => simp only [BoolExpr.eval, map_sup, hp, hq]

/-- T1.1 does not require finite carriers, surjectivity, or arbitrary joins. -/
theorem theorem_1_1 (h : BoundedLatticeHom α β) (hz : ReflectsZero h) :
    (∀ x y, h x ≤ h y ↔ x ≤ y) ∧ Function.Injective h ∧
    (∀ x, h x ≠ ⊥ ↔ x ≠ ⊥) :=
  ⟨order_iff h hz, injective_of_reflectsZero h hz, nonzero_iff h hz⟩
end TOI
