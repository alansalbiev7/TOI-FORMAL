#!/usr/bin/env python3
"""Rebuild and audit the actual Lean environment, including transitive axioms."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
if __name__ == "__main__":
    subprocess.run(["lake", "build"], cwd=ROOT, check=True)
    subprocess.run(["lake", "env", "lean", "scripts/Audit.lean"], cwd=ROOT, check=True)
