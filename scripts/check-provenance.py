#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails if a Gradle module has no provenance ledger row, or if a row is malformed."""
import csv, pathlib, re, sys

KINDS = {"copied", "adapted", "inspired", "original"}
SHA = re.compile(r"^[0-9a-f]{40}$")

def main(root: pathlib.Path) -> int:
    settings = (root / "settings.gradle.kts").read_text()
    modules = sorted({m.lstrip(":") for m in re.findall(r'"(:[A-Za-z0-9_-]+)"', settings)})
    with open(root / "docs" / "provenance.csv", newline="") as fh:
        rows = list(csv.DictReader(fh))
    errors: list[str] = []
    for r in rows:
        if r["kind"] not in KINDS:
            errors.append(f"{r['path']}: invalid kind '{r['kind']}' (use {sorted(KINDS)})")
        if r["kind"] != "original":
            if not SHA.match(r["origin_commit"] or ""):
                errors.append(f"{r['path']}: origin_commit must be a 40-hex SHA")
            if not (r["origin_license"] or "").strip():
                errors.append(f"{r['path']}: origin_license is required for non-original code")
    for m in modules:
        if not any(r["path"] == m or r["path"].startswith(m + "/") for r in rows):
            errors.append(f"module '{m}' has no row in docs/provenance.csv")
    for e in errors:
        print("provenance:", e, file=sys.stderr)
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")))
