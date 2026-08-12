/-
  TOI/Lemmas/TopPreserved.lean — Лемма 1.1: сохранение верхней грани

  Утверждение: если m удовлетворяет OM-1 (монотонность) и (vi) (сюръективность),
  то m(⊤) = ⊤.

  Доказательство:
    По сюръективности, для любого D ∈ L(O₂) существует C ∈ L(O₁) такой, что m(C) = D.
    Поскольку C ≤ ⊤ в L(O₁), из OM-1 следует D = m(C) ≤ m(⊤).
    Так как это верно для произвольного D ∈ L(O₂), получаем
      m(⊤) ≥ ⊔_{D ∈ L(O₂)} D = ⊤.
    Но m(⊤) ≤ ⊤ по определению ⊤. Следовательно, m(⊤) = ⊤.

  Ссылка: монография, §1.3.4, Лемма 1.1.
-/

import TOI.Axioms
import Mathlib.Order.CompleteLattice

namespace TOI

open Mathlib

/-- Лемма 1.1: сохранение верхней грани.
    Если m удовлетворяет условиям (i) OM-1 и (vi) сюръективности, то m(⊤) = ⊤. -/
theorem TopPreserved.top_eq_top {O₁ O₂ : Ontology}
    [CL₁ : ConceptLattice O₁.Carrier] [CL₂ : ConceptLattice O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    -- Явно переформулируем гипотезы, чтобы избежать срабатывания proof-irrelevance
    (h_om1 : ∀ C D : O₁.Carrier, C ≤ D → m.mapConcept C ≤ m.mapConcept D)
    (h_surj : ∀ D : O₂.Carrier, ∃ C : O₁.Carrier, m.mapConcept C = D) :
    m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier) := by
  -- Шаг 1: m(⊤) ≤ ⊤ (т.к. ⊤ — наибольший элемент в L(O₂)).
  have h_le : m.mapConcept (⊤ : O₁.Carrier) ≤ (⊤ : O₂.Carrier) := le_top

  -- Шаг 2: ⊤ ≤ m(⊤) — нужно показать, что m(⊤) является верхней гранью всех D ∈ L(O₂).
  have h_ge : (⊤ : O₂.Carrier) ≤ m.mapConcept (⊤ : O₁.Carrier) := by
    -- Для произвольного D ∈ L(O₂) по сюръективности найдётся C с m(C) = D.
    -- Поскольку C ≤ ⊤, из OM-1 следует D = m(C) ≤ m(⊤).
    -- Значит, m(⊤) ≥ любого D, следовательно m(⊤) ≥ ⊔ D = ⊤.
    rw [eq_top_iff]
    -- Альтернативное рассуждение через iSup
    rw [← iSup_eq_top]
    intro D
    obtain ⟨C, hC⟩ := h_surj D
    rw [← hC]
    exact h_om1 C (⊤ : O₁.Carrier) le_top

  -- Шаг 3: антисимметрия m(⊤) ≤ ⊤ и ⊤ ≤ m(⊤) даёт m(⊤) = ⊤.
  exact le_antisymm h_le h_ge

/-- Следствие 1.1.1: если m(⊤) = ⊤, то m сохраняет верхнюю грань. -/
theorem TopPreserved.preserves_top {O₁ O₂ : Ontology}
    [ConceptLattice O₁.Carrier] [ConceptLattice O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier)) :
    PreservesTop (⟨m.mapConcept, m.om2_union, m.om2_intersection⟩ :
      LatticeHom O₁.Carrier O₂.Carrier) := by
  exact ⟨h⟩

end TOI
