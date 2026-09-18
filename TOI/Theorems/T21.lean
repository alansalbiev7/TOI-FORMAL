import Mathlib.Logic.Function.Basic

/-! Observable contracts (§2.4). Composition is proved from a commuting square.
Injectivity concerns observations, not raw data or all target-side queries. -/
namespace TOI
universe u
structure ObservableContract where
  State : Type u
  Query : Type u
  Value : Type u
  observe : State → Query → Value

structure ContractMorphism (X Y : ObservableContract.{u}) where
  state : X.State → Y.State
  query : X.Query → Y.Query
  value : X.Value → Y.Value
  value_injective : Function.Injective value
  commutes : ∀ s q, Y.observe (state s) (query q) = value (X.observe s q)

namespace ContractMorphism
variable {X Y Z W : ObservableContract.{u}}

def identity (X : ObservableContract.{u}) : ContractMorphism X X where
  state := id
  query := id
  value := id
  value_injective := Function.injective_id
  commutes := fun _ _ => rfl

def comp (g : ContractMorphism Y Z) (f : ContractMorphism X Y) : ContractMorphism X Z where
  state := g.state ∘ f.state
  query := g.query ∘ f.query
  value := g.value ∘ f.value
  value_injective := g.value_injective.comp f.value_injective
  commutes := by
    intro s q
    change Z.observe (g.state (f.state s)) (g.query (f.query q)) = _
    rw [g.commutes, f.commutes]
    rfl

theorem comp_assoc (h : ContractMorphism Z W) (g : ContractMorphism Y Z)
    (f : ContractMorphism X Y) : (h.comp g).comp f = h.comp (g.comp f) := rfl

theorem id_comp (f : ContractMorphism X Y) : (identity Y).comp f = f := by cases f; rfl

theorem comp_id (f : ContractMorphism X Y) : f.comp (identity X) = f := by cases f; rfl

theorem observations_equal_iff (f : ContractMorphism X Y) (s t : X.State) (p q : X.Query) :
    Y.observe (f.state s) (f.query p) = Y.observe (f.state t) (f.query q) ↔
      X.observe s p = X.observe t q := by
  rw [f.commutes, f.commutes]
  exact f.value_injective.eq_iff
end ContractMorphism
end TOI
