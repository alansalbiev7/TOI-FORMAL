/-
  TOI/Lemmas/RoleRestrict.lean — Лемма 1.4: сохранение ролевых ограничений

  Утверждение: если m удовлетворяет условию (iii) OM-3, то для любой роли r,
  концепта C и кардинального ограничения n:
    m(∃ r.C) = ∃ m(r).m(C)
    m(∀ r.C) = ∀ m(r).m(C)
    m(≥ n r.C) = ≥ n m(r).m(C)
    m(≤ n r.C) = ≤ n m(r).m(C)

  Доказательство:
    Первые два равенства — непосредственно из OM-3.
    Кардинальные ограничения являются сокращениями для комбинаций ролевых
    ограничений и булевых операций; результат следует из OM-3 и Леммы 1.3.

  Ссылка: монография, §1.3.4, Лемма 1.4.
-/

import TOI.Axioms
import TOI.Lemmas.TopPreserved
import TOI.Lemmas.NegPreserved
import TOI.Lemmas.BoolExt

namespace TOI

/-- Лемма 1.4: m сохраняет ролевые ограничения. -/
theorem RoleRestrict.role_restrictions_preserved {O₁ O₂ : Ontology}
    [ConceptLattice O₁.Carrier] [ConceptLattice O₂.Carrier]
    [HasComplement O₁.Carrier] [HasComplement O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h_top : m.mapConcept (⊤ : O₁.Carrier) = (⊤ : O₂.Carrier)) :
    ∀ (r : RoleName) (C : O₁.Carrier) (n : ℕ),
      -- Сохранение ∃
      m.mapConcept (existsRoleInterp r C) =
        existsRoleInterp (m.mapRole r) (m.mapConcept C) ∧
      -- Сохранение ∀
      m.mapConcept (forallRoleInterp r C) =
        forallRoleInterp (m.mapRole r) (m.mapConcept C) ∧
      -- Сохранение ≥ n (через развёртку сокращения)
      m.mapConcept (atLeastCardRestriction n r C) =
        atLeastCardRestriction n (m.mapRole r) (m.mapConcept C) ∧
      -- Сохранение ≤ n (через развёртку сокращения)
      m.mapConcept (atMostCardRestriction n r C) =
        atMostCardRestriction n (m.mapRole r) (m.mapConcept C) := by
  intro r C n
  refine ⟨?_, ?_, ?_, ?_⟩
  · -- Сохранение ∃ r.C — непосредственно OM-3
    exact m.om3_exists r C
  · -- Сохранение ∀ r.C — непосредственно OM-3
    exact m.om3_forall r C
  · -- Сохранение ≥ n r.C — через развёртку сокращения
    -- Кардинальное ограничение ≥ n r.C разворачивается через последовательность
    -- ∃-ограничений. Результат следует из OM-3 и Леммы 1.3.
    -- Полная формализация развёртки — TODO (см. комментарий ниже).
    sorry
  · -- Сохранение ≤ n r.C — через развёртку сокращения и ¬
    -- Кардинальное ограничение ≤ n r.C = ¬ (≥ (n+1) r.C).
    -- Результат следует из OM-3, Леммы 1.2 и предыдущего пункта.
    sorry

/-- Кардинальное ограничение «не менее n» как элемент концептуальной решётки.

    В ALC кардинальные ограничения не являются примитивными; они вводятся
    через расширения (ALCN). В данной формализации рассматривается упрощённая
    модель, где кардинальное ограничение ≥ n r.C определяется как:
      ≥ 0 r.C = ⊤
      ≥ 1 r.C = ∃ r.C
      ≥ (n+1) r.C = ∃ r.(C ⊓ (≥ n r.C))
    Это рекурсивное определение требует отсутствия ∃ в ALC; в SROIQ это
    достигается через композицию ролей и инверсию. -/
noncomputable def atLeastCardRestriction {O : Ontology} (n : ℕ) (r : RoleName)
    (C : O.Carrier) : O.Carrier :=
  match n with
  | 0 => ⊤
  | Nat.succ k =>
      -- Рекурсивное определение через ∃ r.(C ⊓ (≥ k r.C))
      existsRoleInterp r (C ⊓ atLeastCardRestriction k r C)

/-- Кардинальное ограничение «не более n» как ¬(≥ (n+1) r.C). -/
noncomputable def atMostCardRestriction {O : Ontology} (n : ℕ) (r : RoleName)
    (C : O.Carrier) [HasComplement O.Carrier] : O.Carrier :=
  HasComplement.compl (atLeastCardRestriction (n + 1) r C)

/-- Следствие 1.4.1: сохранение ∃ и ∀ через OM-3. -/
theorem RoleRestrict.exists_forall_preserved {O₁ O₂ : Ontology}
    [ConceptLattice O₁.Carrier] [ConceptLattice O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (r : RoleName) (C : O₁.Carrier) :
    m.mapConcept (existsRoleInterp r C) =
      existsRoleInterp (m.mapRole r) (m.mapConcept C) ∧
    m.mapConcept (forallRoleInterp r C) =
      forallRoleInterp (m.mapRole r) (m.mapConcept C) :=
  ⟨m.om3_exists r C, m.om3_forall r C⟩

end TOI
