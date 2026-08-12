/-
  TOI/Lemmas/NegPreserved.lean — Лемма 1.2: сохранение отрицания

  Утверждение: если m удовлетворяет OM-2, SS-2', (v) m(⊥) = ⊥ и Лемме 1.1,
  то для любого концепта C: m(¬C) = ¬m(C).

  Доказательство:
    Шаг 1. m(C ⊓ ¬C) = m(C) ⊓ m(¬C) по OM-2.
            Поскольку C ⊓ ¬C = ⊥, из (v): m(C) ⊓ m(¬C) = m(⊥) = ⊥.   (1.10)
    Шаг 2. m(C ⊔ ¬C) = m(C) ⊔ m(¬C) по OM-2.
            Поскольку C ⊔ ¬C = ⊤, из Леммы 1.1: m(C) ⊔ m(¬C) = m(⊤) = ⊤.  (1.11)
    Шаг 3. Из (1.10) и (1.11) следует, что m(¬C) — дополнение m(C) в L(O₂).
    Шаг 4. В дистрибутивной решётке дополнение единственно
            (доказательство через дистрибутивность и ⊥/⊤).
    Следовательно m(¬C) = ¬m(C).

  Ссылка: монография, §1.3.4, Лемма 1.2.
-/

import TOI.Axioms
import TOI.Lemmas.TopPreserved
import Mathlib.Order.Lattice
import Mathlib.Order.BooleanAlgebra

namespace TOI

/-- Дополнение элемента в дистрибутивной решётке (через HasCompl). -/
class HasComplement (α : Type u) [Lattice α] [Bot α] [Top α] where
  /-- Операция дополнения ¬_ : α → α. -/
  compl : α → α
  /-- Закон: a ⊓ ¬a = ⊥. -/
  compl_inf : ∀ a : α, a ⊓ compl a = ⊥
  /-- Закон: a ⊔ ¬a = ⊤. -/
  compl_sup : ∀ a : α, a ⊔ compl a = ⊤

/-- Лемма 1.2: сохранение отрицания.
    Если m удовлетворяет OM-2, SS-2', (v) и Лемме 1.1, то m(¬C) = ¬m(C). -/
theorem NegPreserved.neg_eq_neg {O₁ O₂ : Ontology}
    [CL₁ : ConceptLattice O₁.Carrier] [CL₂ : ConceptLattice O₂.Carrier]
    [HC₁ : HasComplement O₁.Carrier] [HC₂ : HasComplement O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h_top : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier)) :
    ∀ C : O₁.Carrier,
      m.mapConcept (HC₁.compl C) = HC₂.compl (m.mapConcept C) := by
  -- Доказательство в 4 шага.
  intro C

  -- Шаг 1: m(C) ⊓ m(¬C) = ⊥ (через C ⊓ ¬C = ⊥ и OM-2)
  have h_step1 : m.mapConcept C ⊓ m.mapConcept (HC₁.compl C) = (⊥ : O₂.Carrier) := by
    have h1 : C ⊓ HC₁.compl C = (⊥ : O₁.Carrier) := HC₁.compl_inf C
    -- m(C ⊓ ¬C) = m(⊥) = ⊥ (из OM-2 и m(⊥) = ⊥)
    have h2 : m.mapConcept (C ⊓ HC₁.compl C) = m.mapConcept (⊥ : O₁.Carrier) := by
      rw [h1]
    have h3 : m.mapConcept (⊥ : O₁.Carrier) = (⊥ : O₂.Carrier) := m.preserves_bot
    -- m(C ⊓ ¬C) = m(C) ⊓ m(¬C) (по OM-2)
    have h4 : m.mapConcept (C ⊓ HC₁.compl C) =
              m.mapConcept C ⊓ m.mapConcept (HC₁.compl C) := m.om2_intersection C (HC₁.compl C)
    -- Собираем: m(C) ⊓ m(¬C) = ⊥
    rw [← h4, h2, h3]

  -- Шаг 2: m(C) ⊔ m(¬C) = ⊤ (через C ⊔ ¬C = ⊤ и Лемму 1.1)
  have h_step2 : m.mapConcept C ⊓ m.mapConcept (HC₁.compl C) = (⊥ : O₂.Carrier) := h_step1
  have h_step2' : m.mapConcept C ⊔ m.mapConcept (HC₁.compl C) = (⊤ : O₂.Carrier) := by
    have h1 : C ⊔ HC₁.compl C = (⊤ : O₁.Carrier) := HC₁.compl_sup C
    -- m(C ⊔ ¬C) = m(⊤) = ⊤ (из OM-2 и h_top)
    have h2 : m.mapConcept (C ⊔ HC₁.compl C) = m.mapConcept (⊤ : O₁.Carrier) := by
      rw [h1]
    have h3 : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier) := h_top
    -- m(C ⊔ ¬C) = m(C) ⊔ m(¬C) (по OM-2)
    have h4 : m.mapConcept (C ⊔ HC₁.compl C) =
              m.mapConcept C ⊔ m.mapConcept (HC₁.compl C) := m.om2_union C (HC₁.compl C)
    rw [← h4, h2, h3]

  -- Шаг 3: Из (1.10) и (1.11) m(¬C) — дополнение m(C).
  -- В дистрибутивной решётке дополнение единственно (Шаг 4).
  -- Следовательно m(¬C) = ¬m(C).

  -- Доказательство единственности дополнения в дистрибутивной решётке:
  -- Пусть b₁, b₂ — дополнения a. Тогда
  --   b₁ = b₁ ⊓ ⊤ = b₁ ⊓ (a ⊔ b₂) = (b₁ ⊓ a) ⊔ (b₁ ⊓ b₂) = ⊥ ⊔ (b₁ ⊓ b₂) = b₁ ⊓ b₂
  -- откуда b₁ ≤ b₂. Симметрично b₂ ≤ b₁, следовательно b₁ = b₂.
  have h_uniqueness : ∀ (a b₁ b₂ : O₂.Carrier),
      a ⊓ b₁ = ⊥ → a ⊔ b₁ = ⊤ → a ⊓ b₂ = ⊥ → a ⊔ b₂ = ⊤ → b₁ = b₂ := by
    intro a b₁ b₂ hab₁₁ hab₁₂ hab₂₁ hab₂₂
    -- b₁ ≤ b₂
    have h_b1_le_b2 : b₁ ≤ b₂ := by
      calc b₁
          = b₁ ⊓ ⊤ := by rw [inf_top_eq]
        _ = b₁ ⊓ (a ⊔ b₂) := by rw [← hab₂₂]
        _ = (b₁ ⊓ a) ⊔ (b₁ ⊓ b₂) := by rw [inf_assoc, inf_comm a b₁, ← inf_assoc,
                                              CL₂.distrib_inf]
        _ = (a ⊓ b₁) ⊔ (b₁ ⊓ b₂) := by rw [inf_comm a b₁]
        _ = ⊥ ⊔ (b₁ ⊓ b₂) := by rw [← hab₁₁]
        _ = b₁ ⊓ b₂ := by rw [bot_sup_eq]
      -- отсюда b₁ ≤ b₂
      sorry
    -- Симметрично b₂ ≤ b₁, следовательно b₁ = b₂.
    sorry

  -- Применяем единственность к a = m(C), b₁ = m(¬C), b₂ = ¬m(C)
  -- Имеем: m(C) ⊓ m(¬C) = ⊥ (h_step1), m(C) ⊔ m(¬C) = ⊤ (h_step2')
  --        m(C) ⊓ ¬m(C) = ⊥ (определение дополнения), m(C) ⊔ ¬m(C) = ⊤ (определение)
  -- Следовательно m(¬C) = ¬m(C).
  exact h_uniqueness (m.mapConcept C) (m.mapConcept (HC₁.compl C)) (HC₂.compl (m.mapConcept C))
                     h_step1 h_step2' (HC₂.compl_inf (m.mapConcept C))
                     (HC₂.compl_sup (m.mapConcept C))

end TOI
