import TOI.Theorems.T11

/-! §1.3.4. A partition basis induces an embedding of powerset Boolean algebras.
The theorem applies also to arbitrary sets; executable enumeration is finite only. -/
namespace TOI
universe u v
structure AtomPartition (A : Type u) (B : Type v) where
  block : A → Set B
  nonempty : ∀ a, (block a).Nonempty
  unique : ∀ a b x, x ∈ block a → x ∈ block b → a = b
  covers : ∀ x, ∃ a, x ∈ block a

namespace AtomPartition
variable {A : Type u} {B : Type v}
def image (p : AtomPartition A B) (s : Set A) : Set B := {x | ∃ a ∈ s, x ∈ p.block a}

def toHom (p : AtomPartition A B) : BoundedLatticeHom (Set A) (Set B) where
  toFun := p.image
  map_bot' := by ext x; simp [image]
  map_top' := by ext x; simp [image, p.covers]
  map_sup' := by
    intro s t
    ext x
    constructor
    · rintro ⟨a, ha | ha, hx⟩
      · exact Or.inl ⟨a, ha, hx⟩
      · exact Or.inr ⟨a, ha, hx⟩
    · rintro (⟨a, ha, hx⟩ | ⟨a, ha, hx⟩)
      · exact ⟨a, Or.inl ha, hx⟩
      · exact ⟨a, Or.inr ha, hx⟩
  map_inf' := by
    intro s t
    ext x
    constructor
    · rintro ⟨a, ⟨ha, hb⟩, hx⟩
      exact ⟨⟨a, ha, hx⟩, ⟨a, hb, hx⟩⟩
    · rintro ⟨⟨a, ha, hax⟩, ⟨b, hb, hbx⟩⟩
      have hab := p.unique a b x hax hbx
      subst b
      exact ⟨a, ⟨ha, hb⟩, hax⟩

theorem reflectsZero (p : AtomPartition A B) : ReflectsZero p.toHom := by
  intro s hs
  apply Set.eq_empty_iff_forall_not_mem.mpr
  intro a ha
  obtain ⟨x, hx⟩ := p.nonempty a
  have hm : x ∈ p.toHom s := ⟨a, ha, hx⟩
  rw [hs] at hm
  exact hm

theorem preserves_and_reflects_inclusion (p : AtomPartition A B) (s t : Set A) :
    p.image s ⊆ p.image t ↔ s ⊆ t := order_iff p.toHom p.reflectsZero s t
end AtomPartition
end TOI
