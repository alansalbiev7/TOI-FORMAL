/-
  TOI/Lemmas/BoolExt.lean — Лемма 1.3: индуцированное отображение на булевых комбинациях

  Утверждение: если m удовлетворяет условиям Теоремы 1.1, то m сохраняет
  все булевы комбинации запросов:
    m(¬ q₁) = ¬ m(q₁)
    m(q₁ ⊓ q₂) = m(q₁) ⊓ m(q₂)
    m(q₁ ⊔ q₂) = m(q₁) ⊔ m(q₂)

  Доказательство:
    Сохранение ⊔ и ⊓ следует непосредственно из OM-2.
    Сохранение ¬ доказано в Лемме 1.2.

  Ссылка: монография, §1.3.4, Лемма 1.3.
-/

import TOI.Axioms
import TOI.Lemmas.TopPreserved
import TOI.Lemmas.NegPreserved

namespace TOI

/-- Лемма 1.3: m сохраняет булевы комбинации запросов. -/
theorem BoolExt.boolean_combinations_preserved {O₁ O₂ : Ontology}
    [ConceptLattice O₁.Carrier] [ConceptLattice O₂.Carrier]
    [HasComplement O₁.Carrier] [HasComplement O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h_top : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier)) :
    -- Для любых запросов q₁, q₂ (как концептуальных описаний):
    ∀ (C D : O₁.Carrier),
      -- Сохранение ¬
      m.mapConcept (HasComplement.compl C) =
        HasComplement.compl (m.mapConcept C) ∧
      -- Сохранение ⊓
      m.mapConcept (C ⊓ D) = m.mapConcept C ⊓ m.mapConcept D ∧
      -- Сохранение ⊔
      m.mapConcept (C ⊔ D) = m.mapConcept C ⊔ m.mapConcept D := by
  intro C D
  refine ⟨?_, ?_, ?_⟩
  · -- Сохранение ¬ — Лемма 1.2
    exact NegPreserved.neg_eq_neg m h_top C
  · -- Сохранение ⊓ — OM-2
    exact m.om2_intersection C D
  · -- Сохранение ⊔ — OM-2
    exact m.om2_union C D

/-- Расширение Леммы 1.3 на произвольные запросы (через индукцию по структуре Query). -/
theorem BoolExt.preserves_all_queries {O₁ O₂ : Ontology}
    [ConceptLattice O₁.Carrier] [ConceptLattice O₂.Carrier]
    [HasComplement O₁.Carrier] [HasComplement O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h_top : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier)) :
    -- Для любого запроса q : Query O₁.Carrier,
    -- интерпретация translateQuery m q в любой модели I₂ равна
    -- интерпретации q в соответствующей модели I₁.
    ∀ (q : Query O₁.Carrier),
      m.mapConcept (evalQueryAsConcept q) = evalQueryAsConcept (translateQuery m.mapConcept m.mapRole m.mapIndividual q) := by
  -- Доказательство индукцией по структуре q.
  intro q
  induction q with
  | atom c => simp [evalQueryAsConcept, translateQuery]
  | neg q' ih =>
    -- m(¬ q') = ¬ m(q') по Лемме 1.2
    simp [evalQueryAsConcept, translateQuery]
    exact NegPreserved.neg_eq_neg m h_top (evalQueryAsConcept q')
  | and q₁ q₂ ih₁ ih₂ =>
    -- m(q₁ ⊓ q₂) = m(q₁) ⊓ m(q₂) по OM-2
    simp [evalQueryAsConcept, translateQuery]
    exact m.om2_intersection (evalQueryAsConcept q₁) (evalQueryAsConcept q₂)
  | or q₁ q₂ ih₁ ih₂ =>
    -- m(q₁ ⊔ q₂) = m(q₁) ⊔ m(q₂) по OM-2
    simp [evalQueryAsConcept, translateQuery]
    exact m.om2_union (evalQueryAsConcept q₁) (evalQueryAsConcept q₂)
  | existsRole r q' ih =>
    -- m(∃ r.q') = ∃ m(r).m(q') по OM-3
    simp [evalQueryAsConcept, translateQuery]
    exact m.om3_exists r (evalQueryAsConcept q')
  | forallRole r q' ih =>
    -- m(∀ r.q') = ∀ m(r).m(q') по OM-3
    simp [evalQueryAsConcept, translateQuery]
    exact m.om3_forall r (evalQueryAsConcept q')
  | atLeast n r q' ih =>
    -- Кардинальные ограничения — сокращения для комбинаций ролевых
    -- ограничений и булевых операций; результат следует из OM-3 и Леммы 1.3.
    -- Полное доказательство требует развёртки сокращения.
    sorry
  | atMost n r q' ih =>
    sorry
  | nominal a =>
    simp [evalQueryAsConcept, translateQuery]

/-- Заглушка: интерпретация запроса как концепта (для индукции). -/
noncomputable def evalQueryAsConcept {O : Ontology} (q : Query O.Carrier) : O.Carrier :=
  match q with
  | Query.atom c => c
  | Query.neg q' => HasComplement.compl (evalQueryAsConcept q')
  | Query.and q₁ q₂ => evalQueryAsConcept q₁ ⊓ evalQueryAsConcept q₂
  | Query.or q₁ q₂ => evalQueryAsConcept q₁ ⊔ evalQueryAsConcept q₂
  | Query.existsRole r q' => existsRoleInterp r (evalQueryAsConcept q')
  | Query.forallRole r q' => forallRoleInterp r (evalQueryAsConcept q')
  | Query.atLeast n r q' => existsRoleInterp r (evalQueryAsConcept q') -- упрощение
  | Query.atMost n r q' => existsRoleInterp r (evalQueryAsConcept q') -- упрощение
  | Query.nominal a => ⊤ -- упрощение

end TOI
