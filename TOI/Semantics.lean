import Mathlib.Logic.Equiv.Defs
import Mathlib.Data.Set.Basic

/-! Explicit relational semantics. This is an ALC fragment, not OWL 2 DL.
The TBox is a set of concept inclusions. Satisfiability, subsumption and
pointwise query evaluation are distinct predicates. No finite-cardinality
function is applied to infinite sets. -/
namespace TOI
universe u v
inductive Concept (Atom Role : Type u) where
  | atom : Atom → Concept Atom Role
  | bot : Concept Atom Role
  | neg : Concept Atom Role → Concept Atom Role
  | and : Concept Atom Role → Concept Atom Role → Concept Atom Role
  | existsRole : Role → Concept Atom Role → Concept Atom Role
  | forallRole : Role → Concept Atom Role → Concept Atom Role

structure Interpretation (Atom Role : Type u) (D : Type v) where
  nonempty : Nonempty D
  atom : Atom → D → Prop
  role : Role → D → D → Prop

def Concept.holds {A R : Type u} {D : Type v} (I : Interpretation A R D) :
    Concept A R → D → Prop
  | .atom a, x => I.atom a x
  | .bot, _ => False
  | .neg q, x => ¬ q.holds I x
  | .and p q, x => p.holds I x ∧ q.holds I x
  | .existsRole r q, x => ∃ y, I.role r x y ∧ q.holds I y
  | .forallRole r q, x => ∀ y, I.role r x y → q.holds I y

abbrev TBox (A R : Type u) := Set (Concept A R × Concept A R)

def IsModel {A R : Type u} {D : Type v} (T : TBox A R) (I : Interpretation A R D) : Prop :=
  ∀ p ∈ T, ∀ x, p.1.holds I x → p.2.holds I x

def Satisfiable {A R : Type u} (T : TBox A R) (q : Concept A R) : Prop :=
  ∃ (D : Type u) (_ : Nonempty D) (I : Interpretation A R D), IsModel T I ∧ ∃ x, q.holds I x

def Entails {A R : Type u} (T : TBox A R) (p q : Concept A R) : Prop :=
  ∀ (D : Type u) [Nonempty D] (I : Interpretation A R D),
    IsModel T I → ∀ x, p.holds I x → q.holds I x

/-- Strong, explicit sufficient condition for ALC: a bijection of domains
preserving atoms and roles. We make no claim that Boolean T1.1 implies it. -/
structure InterpretationIso {A R : Type u} {D E : Type v}
    (I : Interpretation A R D) (J : Interpretation A R E) where
  domain : D ≃ E
  atoms : ∀ a x, J.atom a (domain x) ↔ I.atom a x
  roles : ∀ r x y, J.role r (domain x) (domain y) ↔ I.role r x y

theorem InterpretationIso.holds_iff {A R : Type u} {D E : Type v}
    {I : Interpretation A R D} {J : Interpretation A R E}
    (h : InterpretationIso I J) (q : Concept A R) (x : D) :
    q.holds J (h.domain x) ↔ q.holds I x := by
  induction q generalizing x with
  | atom a => exact h.atoms a x
  | bot => rfl
  | neg q ih => exact not_congr (ih x)
  | and p q hp hq => exact and_congr (hp x) (hq x)
  | existsRole r q ih =>
    constructor
    · rintro ⟨y, hy, hq⟩
      obtain ⟨z, rfl⟩ := h.domain.surjective y
      exact ⟨z, (h.roles r x z).mp hy, (ih z).mp hq⟩
    · rintro ⟨y, hy, hq⟩
      exact ⟨h.domain y, (h.roles r x y).mpr hy, (ih y).mpr hq⟩
  | forallRole r q ih =>
    constructor
    · intro hall y hy
      exact (ih y).mp (hall (h.domain y) ((h.roles r x y).mpr hy))
    · intro hall y hy
      obtain ⟨z, rfl⟩ := h.domain.surjective y
      exact (ih z).mpr (hall z ((h.roles r x z).mp hy))

theorem InterpretationIso.model_iff {A R : Type u} {D E : Type v}
    {I : Interpretation A R D} {J : Interpretation A R E}
    (h : InterpretationIso I J) (T : TBox A R) : IsModel T J ↔ IsModel T I := by
  constructor
  · intro hm p hp x hx
    exact (h.holds_iff p.2 x).mp (hm p hp (h.domain x) ((h.holds_iff p.1 x).mpr hx))
  · intro hm p hp y hy
    obtain ⟨x, rfl⟩ := h.domain.surjective y
    exact (h.holds_iff p.2 x).mpr (hm p hp x ((h.holds_iff p.1 x).mp hy))
end TOI
