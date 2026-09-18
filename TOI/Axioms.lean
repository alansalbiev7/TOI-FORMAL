import Mathlib.Order.Hom.BoundedLattice
import Mathlib.Data.Set.Lattice

/-! Definitions for the Boolean fragment of monograph §1.3.
No model predicate is replaced by a constant and no ontology is assumed complete.
A bounded lattice homomorphism between Boolean algebras already preserves complements.
-/
namespace TOI
universe u v

/-- The *one-way* zero-reflection condition (1.8). -/
def ReflectsZero {α : Type u} {β : Type v} [BooleanAlgebra α] [BooleanAlgebra β]
    (h : BoundedLatticeHom α β) : Prop := ∀ x, h x = ⊥ → x = ⊥

/-- Finite Boolean syntax. Roles, counting and nominal constructors are separate. -/
inductive BoolExpr (Atom : Type u) where
  | atom : Atom → BoolExpr Atom
  | bot : BoolExpr Atom
  | top : BoolExpr Atom
  | neg : BoolExpr Atom → BoolExpr Atom
  | and : BoolExpr Atom → BoolExpr Atom → BoolExpr Atom
  | or : BoolExpr Atom → BoolExpr Atom → BoolExpr Atom

def BoolExpr.eval {Atom : Type u} {α : Type v} [BooleanAlgebra α]
    (valuation : Atom → α) : BoolExpr Atom → α
  | .atom a => valuation a
  | .bot => ⊥
  | .top => ⊤
  | .neg q => (q.eval valuation)ᶜ
  | .and p q => p.eval valuation ⊓ q.eval valuation
  | .or p q => p.eval valuation ⊔ q.eval valuation

/-- Three reference-model levels. Operational concerns are cross-cutting. -/
inductive InteroperabilityLevel where
  | technical | semantic | organizational
  deriving DecidableEq, Repr
end TOI
