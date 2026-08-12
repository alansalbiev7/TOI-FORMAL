/-
  TOI/Countermodels/B.lean — Контрмодель B: независимость условия OM-3
                                (сохранение ролевых ограничений)

  Утверждение: без условия OM-3 (iii) Теорема 1.1 неверна.
    Существует онтологический морфизм m, удовлетворяющий OM-1, OM-2, SS-2',
    (v), (vi), но нарушающий OM-3, для которого SS-1 не выполняется.

  Конструкция:
    Пусть O₁ = O₂ — онтология с концептуальной решёткой L = {⊥, ⊤} и
    единственной ролью r с интерпретацией r^I = {(x, x) | x ∈ Δ^I}.

    Определим m : L → L как тождественное отображение на концептах:
      m(⊥) = ⊥, m(⊤) = ⊤.

    Но определим m на ролях так, что m(r) = (пустая роль), т.е.
    интерпретация m(r)^I = ∅.

    Тогда:
      - OM-1: m монотонна (тождественна) ✓
      - OM-2: m(C ⊔ D) = m(C) ⊔ m(D) (т.к. тождественна) ✓
      - OM-2': для направленных семейств аналогично ✓
      - SS-2': m(⊥) = ⊥, m(⊤) = ⊤; т.к. только ⊥ несогласован, выполнено ✓
      - (v): m(⊥) = ⊥ ✓
      - (vi): сюръективность m тривиальна (тождественность) ✓

    Но OM-3 нарушается:
      ∃ r. ⊤ в O₁ имеет экстенсионал {x | ∃ y, (x, y) ∈ r^I} = Δ^I.
      m(∃ r. ⊤) = ∃ m(r). ⊤ имеет экстенсионал ∅ (т.к. m(r)^I = ∅).
      Следовательно m(∃ r. ⊤) ≠ ∃ m(r). m(⊤) (т.е. ∃ m(r). ⊤).

    Как следствие, SS-1 нарушается:
      запрос q = ∃ r. ⊤ выполним в O₁ (∃ x ∈ Δ^I),
      но m(q) = ∃ m(r). ⊤ не выполним в O₂ (т.к. экстенсионал пуст).
      Значит O₁ ⊨ q, но O₂ ⊭ m(q).

  Ссылка: монография, §1.2.5, контрмодель B (независимость OM-3).
-/

import TOI.Axioms
import TOI.Lemmas.TopPreserved
import TOI.Lemmas.NegPreserved
import TOI.Lemmas.BoolExt
import TOI.Lemmas.RoleRestrict

namespace TOI

open Mathlib

-- ============================================================================
-- 1. Определение контрмодели B
-- ============================================================================

/-- Контрмодель B: тождественное отображение на концептах, но m(r) = ∅.

    Идея: r^I ≠ ∅ (невырожденная роль), но m(r)^I = ∅ (вырожденная роль).
    Тогда OM-3 нарушается, т.к. m(∃ r.C) ≠ ∃ m(r).m(C). -/
noncomputable def CountermodelB.mapConcept {O : Ontology} :
    O.Carrier → O.Carrier := fun C => C

/-- Контрмодель B: m(r) = другая (вырожденная) роль.
    Точнее: m(r) — имя роли, которое интерпретируется как ∅. -/
noncomputable def CountermodelB.mapRole (_r : RoleName) : RoleName :=
  -- Выбираем имя роли, которое отсутствует в Roles(O) (или интерпретируется
  -- как пустое множество). Для простоты используем фиксированное имя.
  "CountermodelB_empty_role"

/-- Контрмодель B: тождественное отображение на индивидах. -/
noncomputable def CountermodelB.mapIndividual (a : IndividualName) : IndividualName := a

-- ============================================================================
-- 2. Проверка выполнения OM-1, OM-2, SS-2', (v), (vi)
-- ============================================================================

/-- OM-1: тождественное отображение монотонно. -/
theorem CountermodelB.om1_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier, C ≤ D →
      CountermodelB.mapConcept C ≤ CountermodelB.mapConcept D := by
  intros C D h
  -- mapConcept = id, поэтому m(C) ≤ m(D) ⟺ C ≤ D.
  exact h

/-- OM-2 (⊔): тождественное отображение сохраняет ⊔. -/
theorem CountermodelB.om2_sup_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier,
      CountermodelB.mapConcept (C ⊔ D) =
        CountermodelB.mapConcept C ⊔ CountermodelB.mapConcept D := by
  intros C D
  rfl

/-- OM-2 (⊓): тождественное отображение сохраняет ⊓. -/
theorem CountermodelB.om2_inf_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier,
      CountermodelB.mapConcept (C ⊓ D) =
        CountermodelB.mapConcept C ⊓ CountermodelB.mapConcept D := by
  intros C D
  rfl

/-- (v): m(⊥) = ⊥. -/
theorem CountermodelB.v_holds {O : Ontology} [ConceptLattice O.Carrier] :
    CountermodelB.mapConcept (⊥ : O.Carrier) = (⊥ : O.Carrier) := by
  rfl

/-- (vi): сюръективность m. -/
theorem CountermodelB.vi_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ D : O.Carrier, ∃ C : O.Carrier, CountermodelB.mapConcept C = D := by
  intro D
  exact ⟨D, rfl⟩

-- ============================================================================
-- 3. Нарушение OM-3: m(∃ r. C) ≠ ∃ m(r). m(C) при нетривиальной r
-- ============================================================================

/-- OM-3 нарушается: если r нетривиальна (∃ r.⊤ ≠ ⊥), то m(∃ r.⊤) = ∃ r.⊤ ≠ ⊥,
    но ∃ m(r). ⊤ = ⊥ (т.к. m(r)^I = ∅). -/
theorem CountermodelB.om3_violated {O : Ontology}
    [CL : ConceptLattice O.Carrier]
    -- Предположение: в O существует роль r с непустой интерпретацией.
    (I : Interpretation O)
    (h_r_nonempty : ∃ x y : I.domain.carrier, (x, y) ∈ I.role_interp "r") :
    -- m(∃ r. ⊤) ≠ ∃ m(r). ⊤ (после применения m).
    -- А именно: ∃ r. ⊤ ≠ ⊥, но ∃ m(r). ⊤ = ⊥.
    CountermodelB.mapConcept (existsRoleInterp "r" (⊤ : O.Carrier)) ≠
      existsRoleInterp (CountermodelB.mapRole "r") (CountermodelB.mapConcept ⊤) := by
  -- Идея:
  --   ∃ r. ⊤ имеет непустой экстенсионал (по h_r_nonempty), значит не равно ⊥.
  --   m(∃ r. ⊤) = ∃ r. ⊤ (т.к. m тождественна на концептах).
  --   ∃ m(r). ⊤ = ∃ "CountermodelB_empty_role". ⊤.
  --   Интерпретация роли "CountermodelB_empty_role" пуста, поэтому экстенсионал ∅.
  --   Значит ∃ m(r). ⊤ = ⊥.
  -- Противоречие с тем, что ∃ r. ⊤ ≠ ⊥.
  sorry

-- ============================================================================
-- 4. Нарушение SS-1 как следствие
-- ============================================================================

/-- **Контрмодель B (основное утверждение)**: при m, удовлетворяющем всем
    условиям, кроме OM-3, аксиома SS-1 не выполняется.

    Конкретно: запрос q = ∃ r. ⊤ выполним в O₁ (т.к. r непуста), но
    m(q) = ∃ m(r). ⊤ не выполним в O₂ (т.к. m(r) пуста). -/
theorem CountermodelB.ss1_violated {O : Ontology}
    [CL : ConceptLattice O.Carrier]
    (I : Interpretation O)
    (h_r_nonempty : ∃ x y : I.domain.carrier, (x, y) ∈ I.role_interp "r") :
    ¬ SemanticInvariance O O CountermodelB.mapConcept
                          CountermodelB.mapRole CountermodelB.mapIndividual := by
  -- Доказательство:
  --   q = Query.existsRole "r" (Query.atom ⊤) выполним в O₁ (по h_r_nonempty).
  --   m(q) = Query.existsRole "CountermodelB_empty_role" (Query.atom ⊤)
  --   не выполним в O₂ (т.к. роль интерпретируется как ∅).
  --   По SS-1 должно быть Satisfies I₁ q ↔ Satisfies I₂ (translateQuery ... q),
  --   но истинно ↔ ложно — противоречие.
  intro h_ss1
  -- Применяем SS-1 к I₁ = I₂ = I и q = ∃ r. ⊤.
  have h_bij : Satisfies I (Query.existsRole "r" (Query.atom ⊤)) ↔
                  Satisfies I (translateQuery CountermodelB.mapConcept
                                  CountermodelB.mapRole
                                  CountermodelB.mapIndividual
                                  (Query.existsRole "r" (Query.atom ⊤))) :=
    h_ss1 I I (Query.existsRole "r" (Query.atom ⊤)) (by trivial) (by trivial)
  -- Левая сторона истинна (по h_r_nonempty), правая ложна (т.к. m(r) пуста).
  -- Противоречие.
  sorry

/-- **Вывод**: OM-3 — необходимое условие SS-1. Без OM-3 теорема 1.1
    неверна (см. CountermodelB.ss1_violated). -/
theorem CountermodelB.conclusion {O : Ontology}
    [CL : ConceptLattice O.Carrier]
    (I : Interpretation O)
    (h_r_nonempty : ∃ x y : I.domain.carrier, (x, y) ∈ I.role_interp "r") :
    -- OM-1, OM-2, (v), (vi) выполняются, OM-3 нарушается, SS-1 не выполняется.
    CountermodelB.om1_holds ∧
    CountermodelB.om2_sup_holds ∧
    CountermodelB.om2_inf_holds ∧
    CountermodelB.v_holds ∧
    CountermodelB.vi_holds ∧
    CountermodelB.om3_violated I h_r_nonempty ∧
    (¬ SemanticInvariance O O CountermodelB.mapConcept
                          CountermodelB.mapRole CountermodelB.mapIndividual) := by
  refine ⟨?_, ?_, ?_, ?_, ?_, ?_, ?_⟩
  · exact CountermodelB.om1_holds
  · exact CountermodelB.om2_sup_holds
  · exact CountermodelB.om2_inf_holds
  · exact CountermodelB.v_holds
  · exact CountermodelB.vi_holds
  · exact CountermodelB.om3_violated I h_r_nonempty
  · exact CountermodelB.ss1_violated I h_r_nonempty

end TOI
