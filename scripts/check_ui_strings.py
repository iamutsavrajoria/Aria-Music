#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails on user-visible string literals in app/src/main Kotlin: Text("..."), contentDescription = "..." and similar.

A literal counts when, after removing $name and ${...} templates, it still contains a letter. A line ending in
`// ui-string-ok: <reason>` is allowed. Files listed in scripts/ui-strings-baseline.txt are exempt while they are
legacy; a baseline entry that is missing or no longer violates is itself a failure, so the list only shrinks.
"""
import pathlib
import re
import sys

START = re.compile(
    r'\bText\(\s*(?:text\s*=\s*)?(?=")'
    r'|\b(?:contentDescription|stateDescription|label|title|text|placeholder|hint|supportingText|message|description)\s*=\s*(?:if\s*\([^)]*\)\s*)?(?=")'
    r'|\bshowSnackbar\(\s*(?:message\s*=\s*)?(?=")'
)
TEMPLATE = re.compile(r'\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*')


def literal_at(text: str, i: int) -> str:
    """Body of the string literal starting at text[i] == '"' (raw or normal)."""
    if text.startswith('"""', i):
        j = text.find('"""', i + 3)
        return text[i + 3:j if j >= 0 else len(text)]
    j = i + 1
    while j < len(text) and text[j] != '"':
        j += 2 if text[j] == "\\" else 1
    return text[i + 1:j]


def violations(path: pathlib.Path) -> list[int]:
    text = path.read_text()
    lines = text.split("\n")
    out = []
    for m in START.finditer(text):
        body = TEMPLATE.sub("", literal_at(text, m.end()))
        if not re.search(r"[A-Za-z]", body):
            continue
        line_no = text.count("\n", 0, m.start()) + 1
        if "ui-string-ok:" in lines[line_no - 1]:
            continue
        out.append(line_no)
    return out


def main(root: pathlib.Path) -> int:
    baseline_file = root / "scripts" / "ui-strings-baseline.txt"
    baseline = {l.strip() for l in baseline_file.read_text().splitlines() if l.strip() and not l.startswith("#")} if baseline_file.exists() else set()
    fail = False
    seen_violating: set[str] = set()
    for f in sorted((root / "app" / "src" / "main").rglob("*.kt")):
        rel = f.relative_to(root).as_posix()
        v = violations(f)
        if v:
            seen_violating.add(rel)
            if rel not in baseline:
                fail = True
                for n in v:
                    print(f"FAIL: string literal in UI code, use strings.xml: {rel}:{n}")
    for entry in sorted(baseline - seen_violating):
        fail = True
        print(f"FAIL: stale baseline entry (file missing or clean, remove it from scripts/ui-strings-baseline.txt): {entry}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")))
