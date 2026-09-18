import TOI
import Lean.Util.CollectAxioms

open Lean Elab Command in
run_elab do
  let env ← getEnv
  let allowed := #[`propext, `Classical.choice, `Quot.sound]
  let mut count := 0
  for (name, info) in env.constants.toList do
    let n := name.toString
    if (n.startsWith "TOI." || n.startsWith "_private.TOI.") && !info.isUnsafe && !info.isPartial then
      count := count + 1
      let deps ← collectAxioms name
      for ax in deps do
        unless allowed.contains ax do
          throwError "Unapproved axiom {ax} in declaration {name}"
  if count == 0 then
    throwError "No TOI declarations were audited"
  logInfo m!"Audited {count} declarations; only propext, Classical.choice, Quot.sound are permitted"
