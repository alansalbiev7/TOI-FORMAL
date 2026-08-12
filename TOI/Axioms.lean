/-
  TOI/Axioms.lean — Формализация аксиом OE/OM/SS

  Содержание:
    1. Базовые типы: концепты, роли, индивиды, онтологии
    2. Структура концептуальной решётки (полная дистрибутивная решётка)
    3. Аксиомы OE-1 (онтологическая экстенсиональность)
    4. Аксиомы OM-1, OM-2, OM-2', OM-3 (онтологические морфизмы)
    5. Аксиомы SS-1, SS-2' (семантическая инвариантность)
    6. Запросы к онтологии как формулы DL

  Ссылки: монография, §1.2.3 (аксиомы), §1.3.1 (определения 1.5–1.8).
-/

import Mathlib.Order.CompleteLattice
import Mathlib.Order.Lattice
import Mathlib.Data.Set.Basic

universe u v w

namespace TOI

-- ============================================================================
-- 1. Базовые типы онтологии
-- ============================================================================

/-- Имена концептов онтологии. -/
abbrev ConceptName : Type := String

/-- Имена ролей онтологии. -/
abbrev RoleName : Type := String

/-- Имена индивидов онтологии. -/
abbrev IndividualName : Type := String

/-- Идентификатор онтологии (URI). -/
abbrev OntologyId : Type := String

-- ============================================================================
-- 2. Концептуальная решётка
-- ============================================================================

/-!
  Концептуальная решётка онтологии O — это полная дистрибутивная решётка
  L(O), элементами которой являются концептуальные описания (concept
  descriptions), упорядоченные отношением уточнения ⊑.

  Согласно аксиоме OE-1 монографии, L(O) является полной дистрибутивной
  решёткой, что обеспечивает существование всех соединений и пересечений.
-/

/-- Структура концептуальной решётки онтологии. -/
structure ConceptLattice (α : Type u) extends CompleteLattice α where
  -- OE-1: дистрибутивность
  distrib_sup : ∀ a b c : α, a ⊔ (b ⊓ c) = (a ⊔ b) ⊓ (a ⊔ c)
  distrib_inf : ∀ a b c : α, a ⊓ (b ⊔ c) = (a ⊓ b) ⊔ (a ⊓ c)

/-- Решётка концептов онтологии O. -/
abbrev L (α : Type u) [CL : ConceptLattice α] := α

-- ============================================================================
-- 3. Структура онтологии
-- ============================================================================

/-- Онтология как структура: концептуальная решётка + роли + индивиды. -/
structure Ontology where
  /-- Идентификатор онтологии (URI). -/
  id : OntologyId
  /-- Тип элементов концептуальной решётки. -/
  Carrier : Type u
  /-- Концептуальная решётка — полная дистрибутивная. -/
  lattice : ConceptLattice Carrier
  /-- Множество ролей. -/
  roles : Set RoleName
  /-- Множество индивидов. -/
  individuals : Set IndividualName

-- ============================================================================
-- 4. Модель интерпретации дескрипционной логики
-- ============================================================================

/-- Домен интерпретации — непустое множество. -/
@[ext]
structure InterpretationDomain where
  /-- Носитель домена. -/
  carrier : Type v
  /-- Домен непуст. -/
  nonempty : Nonempty carrier

/-- Модель интерпретации I = (Δ^I, ·^I). -/
@[ext]
structure Interpretation (O : Ontology) where
  /-- Домен интерпретации Δ^I — непустое множество. -/
  domain : InterpretationDomain
  /-- Функция интерпретации концептов: ·^I : L(O) → 𝒫(Δ^I). -/
  concept_interp : O.Carrier → Set domain.carrier
  /-- Функция интерпретации ролей: ·^I : Roles → 𝒫(Δ^I × Δ^I). -/
  role_interp : RoleName → Set (domain.carrier × domain.carrier)
  /-- Функция интерпретации индивидов: ·^I : Individuals → Δ^I. -/
  individual_interp : IndividualName → domain.carrier

-- ============================================================================
-- 5. Определение 1.5: Запрос к онтологии
-- ============================================================================

inductive Query (α : Type u) where
  /-- Атомарный концепт. -/
  | atom (c : α) : Query α
  /-- Отрицание. -/
  | neg (q : Query α) : Query α
  /-- Конъюнкция (пересечение). -/
  | and (q₁ q₂ : Query α) : Query α
  /-- Дизъюнкция (объединение). -/
  | or (q₁ q₂ : Query α) : Query α
  /-- Экзистенциальный квантор по роли. -/
  | existsRole (r : RoleName) (q : Query α) : Query α
  /-- Универсальный квантор по роли. -/
  | forallRole (r : RoleName) (q : Query α) : Query α
  /-- Кардинальное ограничение «не менее n». -/
  | atLeast (n : ℕ) (r : RoleName) (q : Query α) : Query α
  /-- Кардинальное ограничение «не более n». -/
  | atMost (n : ℕ) (r : RoleName) (q : Query α) : Query α
  /-- Номинал {a}. -/
  | nominal (a : IndividualName) : Query α
  deriving DecidableEq, Repr

/-- Запрос к онтологии O — это Query над Carrier O. -/
abbrev OntologyQuery (O : Ontology) : Type := Query O.Carrier

-- ============================================================================
-- 6. Экстенсионал запроса и выполнимость
-- ============================================================================

/-- Экстенсионал запроса q в интерпретации I — подмножество домена. -/
def Query.interp {O : Ontology} (q : OntologyQuery O)
    (I : Interpretation O) : Set I.domain.carrier := by
  induction q with
  | atom c => exact I.concept_interp c
  | neg q' ih => exact Set.univ \ ih
  | and q₁ q₂ ih₁ ih₂ => exact ih₁ ∩ ih₂
  | or q₁ q₂ ih₁ ih₂ => exact ih₁ ∪ ih₂
  | existsRole r q' ih =>
      exact { x | ∃ y, (x, y) ∈ I.role_interp r ∧ y ∈ ih }
  | forallRole r q' ih =>
      exact { x | ∀ y, (x, y) ∈ I.role_interp r → y ∈ ih }
  | atLeast n r q' ih =>
      exact { x | n ≤ Set.ncard { y | (x, y) ∈ I.role_interp r ∧ y ∈ ih } }
  | atMost n r q' ih =>
      exact { x | Set.ncard { y | (x, y) ∈ I.role_interp r ∧ y ∈ ih } ≤ n }
  | nominal a => exact { I.individual_interp a }

/-- Определение 1.5: Запрос q выполним в онтологии O (O ⊨ q),
    если существует модель I онтологии O, в которой экстенсионал непуст. -/
def Satisfies {O : Ontology} (I : Interpretation O) (q : OntologyQuery O) : Prop :=
  ∃ x : I.domain.carrier, x ∈ q.interp I

-- ============================================================================
-- 7. Определение 1.6: Семантическая инвариантность (SS-1)
-- ============================================================================

/-- Трансляция запроса через отображение m. -/
def translateQuery {O₁ O₂ : Ontology} (m : O₁.Carrier → O₂.Carrier)
    (mr : RoleName → RoleName) (ma : IndividualName → IndividualName)
    : OntologyQuery O₁ → OntologyQuery O₂
  | Query.atom c => Query.atom (m c)
  | Query.neg q => Query.neg (translateQuery m mr ma q)
  | Query.and q₁ q₂ => Query.and (translateQuery m mr ma q₁) (translateQuery m mr ma q₂)
  | Query.or q₁ q₂ => Query.or (translateQuery m mr ma q₁) (translateQuery m mr ma q₂)
  | Query.existsRole r q => Query.existsRole (mr r) (translateQuery m mr ma q)
  | Query.forallRole r q => Query.forallRole (mr r) (translateQuery m mr ma q)
  | Query.atLeast n r q => Query.atLeast n (mr r) (translateQuery m mr ma q)
  | Query.atMost n r q => Query.atMost n (mr r) (translateQuery m mr ma q)
  | Query.nominal a => Query.nominal (ma a)

/-- SS-1: Отображение m обладает семантической инвариантностью, если
    для любого запроса q к O₁ выполняется O₁ ⊨ q ⟺ O₂ ⊨ m(q). -/
def SemanticInvariance (O₁ O₂ : Ontology)
    (m : O₁.Carrier → O₂.Carrier)
    (mr : RoleName → RoleName)
    (ma : IndividualName → IndividualName) : Prop :=
  ∀ (I₁ : Interpretation O₁) (I₂ : Interpretation O₂) (q : OntologyQuery O₁),
    IsModel O₁ I₁ → IsModel O₂ I₂ →
    (Satisfies I₁ q ↔ Satisfies I₂ (translateQuery m mr ma q))

-- ============================================================================
-- 8. Определение 1.7: Гомоморфизм решёток
-- ============================================================================

/-- Гомоморфизм дистрибутивных решёток, сохраняющий ⊔ и ⊓. -/
structure LatticeHom (α : Type u) (β : Type v)
    [Lattice α] [Lattice β] where
  /-- Носитель отображения. -/
  toFun : α → β
  /-- OM-2: сохранение точных соединений ⊔. -/
  sup_pres : ∀ a b : α, toFun (a ⊔ b) = toFun a ⊔ toFun b
  /-- OM-2: сохранение точных пересечений ⊓. -/
  inf_pres : ∀ a b : α, toFun (a ⊓ b) = toFun a ⊓ toFun b

instance {α β} [Lattice α] [Lattice β] :
    FunLike (LatticeHom α β) α (fun _ => β) where
  coe := LatticeHom.toFun
  coe_injective' := by
    intro f g h
    cases f; cases g
    congr

/-- Гомоморфизм, сохраняющий нижнюю грань ⊥. -/
class PreservesBot {α β} [Lattice α] [Lattice β] [Bot α] [Bot β]
    (h : LatticeHom α β) : Prop where
  bot_pres : h.toFun ⊥ = ⊥

/-- Гомоморфизм, сохраняющий верхнюю грань ⊤. -/
class PreservesTop {α β} [Lattice α] [Lattice β] [Top α] [Top β]
    (h : LatticeHom α β) : Prop where
  top_pres : h.toFun ⊤ = ⊤

-- ============================================================================
-- 9. Аксиомы OM-1, OM-2, OM-2', OM-3, SS-2'
-- ============================================================================

/-- Структура онтологического морфизма (отображения) O₁ → O₂. -/
structure OntologyMorphism (O₁ O₂ : Ontology) where
  /-- Отображение концептов m : L(O₁) → L(O₂). -/
  mapConcept : O₁.Carrier → O₂.Carrier
  /-- Отображение ролей. -/
  mapRole : RoleName → RoleName
  /-- Отображение индивидов. -/
  mapIndividual : IndividualName → IndividualName
  /-- OM-1: сохранение иерархии (монотонность).
      C ⊑ D ⟹ m(C) ⊑ m(D). -/
  om1_hierarchy : ∀ C D : O₁.Carrier,
    C ≤ D → mapConcept C ≤ mapConcept D
  /-- OM-2: сохранение точных соединений и пересечений. -/
  om2_union : ∀ C D : O₁.Carrier,
    mapConcept (C ⊔ D) = mapConcept C ⊔ mapConcept D
  om2_intersection : ∀ C D : O₁.Carrier,
    mapConcept (C ⊓ D) = mapConcept C ⊓ mapConcept D
  /-- OM-2': сохранение направленных соединений (только для бесконечных онтологий).
      m(⊔ᵢ Cᵢ) = ⊔ᵢ m(Cᵢ) для любого направленного множества. -/
  om2_directed : ∀ {ι : Type w} [Nonempty ι] (dir : ι → O₁.Carrier)
    (_ : Directed dir), mapConcept (iSup dir) = iSup (mapConcept ∘ dir)
  /-- OM-3: сохранение ролевых ограничений.
      m(∃ r.C) = ∃ m(r).m(C) и m(∀ r.C) = ∀ m(r).m(C). -/
  om3_exists : ∀ (r : RoleName) (C : O₁.Carrier),
    mapConcept (existsRoleInterp r C) = existsRoleInterp (mapRole r) (mapConcept C)
  om3_forall : ∀ (r : RoleName) (C : O₁.Carrier),
    mapConcept (forallRoleInterp r C) = forallRoleInterp (mapRole r) (mapConcept C)
  /-- SS-2': двустороннее сохранение инконсистентности.
      O₁ ⊢ C ≡ ⊥ ⟺ O₂ ⊢ m(C) ≡ ⊥. -/
  ss2_prime : ∀ C : O₁.Carrier,
    IsInconsistent O₁ C ↔ IsInconsistent O₂ (mapConcept C)
  /-- (v): сохранение нижней грани. m(⊥) = ⊥. -/
  preserves_bot : mapConcept ⊥ = ⊥
  /-- (vi): сюръективность.
      ∀ D ∈ L(O₂), ∃ C ∈ L(O₁), m(C) = D. -/
  surjective : ∀ D : O₂.Carrier, ∃ C : O₁.Carrier, mapConcept C = D

-- ============================================================================
-- 10. Вспомогательные определения для ролевых ограничений
-- ============================================================================

/-- ∃ r.C как элемент концептуальной решётки (через индуктивный тип). -/
noncomputable def existsRoleInterp {O : Ontology} (r : RoleName) (C : O.Carrier) : O.Carrier :=
  -- В полной решётке ∃ r.C определяется как iSup { a | ∃ b, (a, b) ∈ r^I ∧ b ∈ C^I }
  -- Здесь — заглушка, требующая формальной модели ролей в решётке.
  Classical.choice ⟨C⟩

/-- ∀ r.C как элемент концептуальной решётки. -/
noncomputable def forallRoleInterp {O : Ontology} (r : RoleName) (C : O.Carrier) : O.Carrier :=
  Classical.choice ⟨C⟩

-- ============================================================================
-- 11. Согласованность / несогласованность концепта (Определение 1.8)
-- ============================================================================

/-- Концепт C несогласован в онтологии O, если C^I = ∅ для любой модели I. -/
def IsInconsistent (O : Ontology) (C : O.Carrier) : Prop :=
  ∀ I : Interpretation O, IsModel O I → I.concept_interp C = ∅

/-- Концепт C согласован в онтологии O, если существует модель I с C^I ≠ ∅. -/
def IsConsistent (O : Ontology) (C : O.Carrier) : Prop :=
  ∃ I : Interpretation O, IsModel O I ∧ I.concept_interp C ≠ ∅

/-- Модель I онтологии O — интерпретация, удовлетворяющая всем TBox-аксиомам O. -/
def IsModel (O : Ontology) (I : Interpretation O) : Prop :=
  -- Условие модели: интерпретация согласована с решёточной структурой.
  -- Подробности — в TBox-аксиоматике (выходит за рамки данной формализации).
  True

-- ============================================================================
-- 12. Лемма 1.1 (предварительная): m(⊤) = ⊤
-- ============================================================================

theorem lemma_top_preserved {O₁ O₂ : Ontology}
    [CL₁ : ConceptLattice O₁.Carrier] [CL₂ : ConceptLattice O₂.Carrier]
    (m : OntologyMorphism O₁ O₂)
    (h_om1 : ∀ C D : O₁.Carrier, C ≤ D → m.mapConcept C ≤ m.mapConcept D)
    (h_surj : ∀ D : O₂.Carrier, ∃ C : O₁.Carrier, m.mapConcept C = D) :
    m.mapConcept ⊤ = ⊤ := by
  -- Доказательство: для любого D ∈ L(O₂) по сюръективности существует C с m(C) = D.
  -- Поскольку C ≤ ⊤, из OM-1 следует D = m(C) ≤ m(⊤).
  -- Значит m(⊤) — верхняя грань всех D ∈ L(O₂), то есть m(⊤) ≥ ⊤.
  -- Обратное неравенство m(⊤) ≤ ⊤ тривиально. Следовательно m(⊤) = ⊤.
  apply le_antisymm
  · -- m(⊤) ≤ ⊤: тривиально, т.к. ⊤ — наибольший элемент.
    exact le_top
  · -- ⊤ ≤ m(⊤): для любого D ∈ L(O₂), D ≤ m(⊤).
    -- Доказывается через сюръективность и OM-1.
    rw [le_top_iff_eq_top.mpr rfl]
    -- Здесь требуется более тонкое рассуждение; см. Lemmas/TopPreserved.lean
    sorry

end TOI
