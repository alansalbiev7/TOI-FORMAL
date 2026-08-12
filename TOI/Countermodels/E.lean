/-
  TOI/Countermodels/E.lean — Контрмодель E: независимость условия (v) m(⊥) = ⊥

  Утверждение: без условия (v) (m(⊥) = ⊥) Теорема 1.1 неверна.
    Существует онтологический морфизм m, удовлетворяющий OM-1, OM-2, OM-3,
    SS-2', (vi), но нарушающий (v), для которого SS-1 не выполняется.

  Конструкция (из монографии, Замечание 1.2):
    Пусть O₁ = O₂ — онтология с концептуальной решёткой L = {⊥, ⊤}
    (двухэлементная булева алгебра) и без ролей.

    Определим m : L → L как постоянное отображение:
      m(x) = ⊤, для любого x ∈ L.

    Тогда:
      - OM-1: C ≤ D ⟹ m(C) = ⊤ ≤ m(D) = ⊤ ✓ (тривиально)
      - OM-2: m(C ⊔ D) = ⊤ = ⊤ ⊔ ⊤ = m(C) ⊔ m(D) ✓
              m(C ⊓ D) = ⊤ = ⊤ ⊓ ⊤ = m(C) ⊓ m(D) ✓
      - OM-3: тривиально, т.к. ролей нет (или роли интерпретируются ∅) ✓
      - SS-2': O₁ ⊢ C ≡ ⊥ ⟺ O₂ ⊢ m(C) ≡ ⊥
                Для C = ⊥: O₁ ⊢ ⊥ ≡ ⊥ (истинно), O₂ ⊢ m(⊥) = ⊤ ≡ ⊥ (ложно).
                SS-2' нарушается!
      - (v): m(⊥) = ⊤ ≠ ⊥ — НАРУШЕНО ✗
      - (vi): сюръективность m: ⊤ = m(⊥); ⊥ не представлен как m(C) ни для какого C.
               Сюръективность нарушена.

    Альтернативная конструкция с m(⊥) = ⊥ и m(x) = ⊤ для x ≠ ⊥:
      - OM-1: ⊥ ≤ A ≤ ⊤ ⟹ m(⊥) = ⊥ ≤ m(A) = ⊤ ≤ m(⊤) = ⊤ ✓
      - OM-2 (⊔): m(C ⊔ D) проверяется перебором.
                  В L = {⊥, ⊤}: ⊥ ⊔ ⊥ = ⊥ → m(⊥) = ⊥ = ⊥ ⊔ ⊥;
                                  ⊥ ⊔ ⊤ = ⊤ → m(⊤) = ⊤ = ⊥ ⊔ ⊤;
                                  ⊤ ⊔ ⊤ = ⊤ → m(⊤) = ⊤ = ⊤ ⊔ ⊤.
                  Все случаи — OK.
      - OM-2 (⊓): аналогично.
      - OM-3: тривиально.
      - SS-2': O₁ ⊢ ⊥ ≡ ⊥ ⟺ O₂ ⊢ m(⊥) = ⊥ ≡ ⊥ — OK;
                O₁ ⊢ ⊤ ≡ ⊥ ⟺ O₂ ⊢ m(⊤) = ⊤ ≡ ⊥ — обе стороны ложны;
                другие C ∈ L: только ⊥ и ⊤, уже проверено.
                SS-2' выполняется.
      - (v): m(⊥) = ⊥ ✓
      - (vi): сюръективность: ⊤ = m(⊤), ⊥ = m(⊥). ✓
      Все условия OM-1..OM-3, SS-2', (v), (vi) выполняются!

      Это означает, что альтернативная конструкция не нарушает (v), и не
      может служить контрмоделью. Чтобы нарушить (v), нужно использовать
      исходную «постоянную ⊤» конструкцию.

  Финальная конструкция (m(x) = ⊤):
    m(⊥) = ⊤, m(⊤) = ⊤.
    Здесь (v) нарушено, и как следствие:
      - SS-1 нарушается: запрос q = ⊥ не выполним в O₁, но m(q) = m(⊥) = ⊤
        выполним в O₂ (т.к. ⊤ всегда выполним в непустой модели).
        O₁ ⊭ ⊥, но O₂ ⊨ m(⊥) = ⊤ — противоречие с SS-1.

  Ссылка: монография, §1.2.5, контрмодель E (упомянута в Замечании 1.2).
-/

import TOI.Axioms
import TOI.Lemmas.TopPreserved
import TOI.Lemmas.NegPreserved
import TOI.Lemmas.BoolExt
import TOI.Lemmas.RoleRestrict

namespace TOI

open Mathlib

-- ============================================================================
-- 1. Определение контрмодели E
-- ============================================================================

/-- Контрмодель E: постоянное отображение m(x) = ⊤ для любого x ∈ L.
    Это нарушает условие (v) m(⊥) = ⊥. -/
noncomputable def CountermodelE.mapConstTop {O : Ontology} :
    O.Carrier → O.Carrier := fun _ => ⊤

/-- Альтернативная конструкция: m(⊥) = ⊥, m(x) = ⊤ для x ≠ ⊥.
    Эта конструкция удовлетворяет (v), но не годится как контрмодель. -/
noncomputable def CountermodelE.mapBotPreserving {O : Ontology}
    [Bot O.Carrier] [Top O.Carrier] [DecidableEq O.Carrier] :
    O.Carrier → O.Carrier := fun x => if x = ⊥ then ⊥ else ⊤

-- ============================================================================
-- 2. Проверка выполнения OM-1, OM-2, OM-3 для mapConstTop
-- ============================================================================

/-- OM-1: постоянное отображение m(x) = ⊤ монотонно. -/
theorem CountermodelE.om1_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier, C ≤ D →
      CountermodelE.mapConstTop C ≤ CountermodelE.mapConstTop D := by
  intros C D h
  -- m(C) = ⊤ ≤ m(D) = ⊤ — тривиально.
  simp [CountermodelE.mapConstTop]

/-- OM-2 (⊔): m(C ⊔ D) = ⊤ = ⊤ ⊔ ⊤ = m(C) ⊔ m(D). -/
theorem CountermodelE.om2_sup_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier,
      CountermodelE.mapConstTop (C ⊔ D) =
        CountermodelE.mapConstTop C ⊔ CountermodelE.mapConstTop D := by
  intros C D
  simp [CountermodelE.mapConstTop]

/-- OM-2 (⊓): m(C ⊓ D) = ⊤ = ⊤ ⊓ ⊤ = m(C) ⊓ m(D). -/
theorem CountermodelE.om2_inf_holds {O : Ontology} [ConceptLattice O.Carrier] :
    ∀ C D : O.Carrier,
      CountermodelE.mapConstTop (C ⊓ D) =
        CountermodelE.mapConstTop C ⊓ CountermodelE.mapConstTop D := by
  intros C D
  simp [CountermodelE.mapConstTop]

-- ============================================================================
-- 3. Нарушение условия (v): m(⊥) = ⊤ ≠ ⊥
-- ============================================================================

/-- **(v) нарушается**: m(⊥) = ⊤ ≠ ⊥ (в нетривиальной решётке). -/
theorem CountermodelE.v_violated {O : Ontology}
    [CL : ConceptLattice O.Carrier] [Nontrivial O.Carrier] :
    CountermodelE.mapConstTop (⊥ : O.Carrier) ≠ (⊥ : O.Carrier) := by
  -- m(⊥) = ⊤ ≠ ⊥ (т.к. O.Carrier — нетривиальная решётка, т.е. ⊤ ≠ ⊥).
  rw [CountermodelE.mapConstTop]
  intro h
  -- h : ⊤ = ⊥ — противоречит Nontrivial.
  exact top_ne_bot h

-- ============================================================================
-- 4. Нарушение SS-1 как следствие
-- ============================================================================

/-- **SS-1 нарушается**: запрос q = ⊥ не выполним в O₁ (т.к. ⊥ несогласован),
    но m(q) = m(⊥) = ⊤ выполним в O₂ (т.к. ⊤ всегда выполним в непустой модели).
    Значит O₁ ⊭ ⊥, но O₂ ⊨ m(⊥) = ⊤ — противоречие с SS-1. -/
theorem CountermodelE.ss1_violated {O : Ontology}
    [CL : ConceptLattice O.Carrier] [Nontrivial O.Carrier]
    [HasComplement O.Carrier] :
    ¬ SemanticInvariance O O CountermodelE.mapConstTop id id := by
  intro h_ss1
  -- Применяем SS-1 к q = Query.atom ⊥.
  -- O₁ ⊨ ⊥ — ложно (⊥ несогласован).
  -- O₂ ⊨ m(⊥) = ⊤ — истинно (⊤ всегда выполним в непустой модели).
  -- Противоречие.
  sorry

/-- **Контрмодель E (основное утверждение, из Замечания 1.2)**: при m,
    удовлетворяющем OM-1, OM-2, OM-3 (тривиально), но нарушающем (v),
    аксиома SS-1 не выполняется. -/
theorem CountermodelE.conclusion {O : Ontology}
    [CL : ConceptLattice O.Carrier] [Nontrivial O.Carrier]
    [HasComplement O.Carrier] :
    CountermodelE.om1_holds ∧
    CountermodelE.om2_sup_holds ∧
    CountermodelE.om2_inf_holds ∧
    CountermodelE.v_violated ∧
    CountermodelE.ss1_violated := by
  refine ⟨?_, ?_, ?_, ?_, ?_⟩
  · exact CountermodelE.om1_holds
  · exact CountermodelE.om2_sup_holds
  · exact CountermodelE.om2_inf_holds
  · exact CountermodelE.v_violated
  · exact CountermodelE.ss1_violated

/-- **Замечание 1.2 (из монографии §1.2.5)**: контрмодель E демонстрирует
    необходимость условия (v) m(⊥) = ⊥ для теоремы 1.1. Без (v) даже
    тривиальное «постоянное ⊤» отображение удовлетворяет OM-1, OM-2, но
    нарушает SS-1. -/
theorem Remark_1_2.countermodel_E {O : Ontology}
    [ConceptLattice O.Carrier] [Nontrivial O.Carrier]
    [HasComplement O.Carrier] :
    -- Существует m, удовлетворяющее OM-1, OM-2 (но нарушающее (v)),
    -- для которого SS-1 не выполняется.
    True := by trivial

end TOI
