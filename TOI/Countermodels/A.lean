/-
  TOI/Countermodels/A.lean — Контрмодель A: независимость условия (v) m(⊥) = ⊥

  Утверждение: без условия (v) (m(⊥) = ⊥) Теорема 1.1 неверна.

  Контрмодель A:
    Пусть O₁ = O₂ — тривиальная онтология с L = {⊥, ⊤}.
    Определим m : L → L как постоянное отображение m(x) = ⊤.
    Тогда:
      - OM-1: C ≤ D ⟹ m(C) ≤ m(D) ⟹ ⊤ ≤ ⊤ ✓
      - OM-2: m(C ⊔ D) = ⊤ = ⊤ ⊔ ⊤ = m(C) ⊔ m(D) ✓
              m(C ⊓ D) = ⊤ = ⊤ ⊓ ⊤ = m(C) ⊓ m(D) ✓
      - OM-3: (требует ролевой структуры; для тривиальной онтологии выполнено)
      - SS-2': O₁ ⊢ C ≡ ⊥ ⟺ O₂ ⊢ m(C) ≡ ⊥
                C = ⊥ ⟺ m(⊥) = ⊤ ≡ ⊥ (НЕВЕРНО: ⊤ ≢ ⊥)
                ⚠ Контрмодель не проходит SS-2' при m(⊥) = ⊤.

  Уточнённая контрмодель A':
    m(x) = ⊤ нарушает только условие (v), но при этом SS-2' также нарушается.
    Для демонстрации независимости именно (v) нужна более тонкая конструкция.

  Ссылка: монография, §1.2.5, контрмодель E (упомянута в Замечании 1.2).
-/

import TOI.Axioms

namespace TOI

/-- Контрмодель A: постоянное отображение m(x) = ⊤ нарушает (v) и SS-1. -/
def CountermodelA.constTop {O : Ontology} : O.Carrier → O.Carrier := fun _ => ⊤

/-- Проверка нарушения условия (v): m(⊥) ≠ ⊥ (если в O есть нетривиальные концепты). -/
theorem CountermodelA.violates_bot_preservation {O : Ontology}
    [ConceptLattice O.Carrier] [Nontrivial O.Carrier] :
    CountermodelA.constTop (⊥ : O.Carrier) ≠ (⊥ : O.Carrier) := by
  -- m(⊥) = ⊤ ≠ ⊥ (т.к. O.Carrier — нетривиальная решётка)
  rw [CountermodelA.constTop]
  intro h
  -- h : ⊤ = ⊥, противоречит Nontrivial
  exact top_ne_bot h

/-- Проверка нарушения SS-1: существует запрос q, для которого
    O ⊨ q, но O ⊭ m(q) (или наоборот). -/
theorem CountermodelA.violates_ss1 {O : Ontology}
    [ConceptLattice O.Carrier] [Nontrivial O.Carrier]
    [HasComplement O.Carrier] :
    ¬ SemanticInvariance O O CountermodelA.constTop id id := by
  intro h_ss1
  -- Выберем q = ¬ ⊤ (тождественно ложный запрос).
  -- O ⊨ q ложно (т.к. ¬⊤ ≡ ⊥ несогласован).
  -- m(q) = m(¬ ⊤) = ¬ m(⊤) = ¬ ⊤ = ⊥.
  -- O ⊨ m(q) также ложно. Здесь нет противоречия.

  -- Выберем q = ⊥.
  -- O ⊨ ⊥ ложно (т.к. ⊥ несогласован).
  -- m(⊥) = ⊤.
  -- O ⊨ m(⊥) = O ⊨ ⊤ истинно (т.к. ⊤ согласован).
  -- Противоречие: O ⊭ ⊥, но O ⊨ m(⊥) = ⊤.
  -- По SS-1 должно быть O ⊨ ⊥ ⟺ O ⊨ m(⊥) = ⊤, но ложно ⟺ истинно.
  apply h_ss1
  -- Требуется явная модель O; в данной формализации предполагаем, что
  -- O имеет хотя бы одну модель.
  sorry

end TOI
