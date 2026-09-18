#!/usr/bin/env python3
"""Structural guard; complements (does not replace) kernel/dependency checking."""
from pathlib import Path
import re
import sys
import json
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def code_only(source):
    """Strip nested Lean block comments, line comments and ordinary string literals."""
    out, i, depth, quoted = [], 0, 0, False
    while i < len(source):
        pair = source[i:i+2]
        if depth:
            if pair == "/-": depth += 1; i += 2
            elif pair == "-/": depth -= 1; i += 2
            else: out.append("\n" if source[i] == "\n" else " "); i += 1
        elif quoted:
            if source[i] == "\\": i += 2
            elif source[i] == '"': quoted = False; i += 1
            else: i += 1
        elif pair == "/-": depth = 1; i += 2; out.append(" ")
        elif pair == "--":
            end = source.find("\n", i)
            i = len(source) if end < 0 else end
        elif source[i] == '"': quoted = True; i += 1; out.append(" ")
        else: out.append(source[i]); i += 1
    return "".join(out)


def check():
    errors = []
    lean_files = sorted((ROOT / "TOI").rglob("*.lean"))
    expected = {str(p.relative_to(ROOT).with_suffix("")).replace("/", ".") for p in lean_files}
    imports = set(re.findall(r"^import\s+(TOI(?:\.\w+)+)\s*$", (ROOT / "TOI.lean").read_text(), re.M))
    if imports != expected:
        errors.append(f"Root import mismatch: missing={expected-imports}, foreign={imports-expected}")
    for path in [ROOT / "TOI.lean", *lean_files]:
        code = code_only(path.read_text())
        bad = re.findall(r"\b(?:sorry|admit|axiom|native_decide|unsafe|partial)\b", code)
        if bad: errors.append(f"{path.relative_to(ROOT)}: prohibited {bad}")
        if re.search(r"\bset_option\s+debug\.", code):
            errors.append(f"{path.relative_to(ROOT)}: debug option in proof source")
    manifest = json.loads((ROOT / "lake-manifest.json").read_text())
    for dep in manifest["packages"]:
        if dep["type"] != "git" or not re.fullmatch(r"[0-9a-f]{40}", dep["rev"]):
            errors.append(f"Unpinned dependency: {dep['name']}")
    cfg = tomllib.loads((ROOT / "lakefile.toml").read_text())
    if cfg["defaultTargets"] != ["TOI"]: errors.append("Unexpected default Lean target")
    for e in errors: print(e, file=sys.stderr)
    if errors: return 1
    print(f"Repository guard passed: {len(lean_files)} modules imported, dependencies pinned")
    return 0

if __name__ == "__main__":
    raise SystemExit(check())
