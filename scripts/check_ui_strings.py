#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails on user-visible string literals in app/src/main Kotlin: Text("..."), contentDescription = "..." and similar.

A literal counts when, after removing $name and ${...} templates, it still contains a letter. A line ending in
`// ui-string-ok: <reason>` is allowed: it must be a real `//` comment after the code (not inside a string) with a
non-empty reason, and the real repo's uses are pinned by the tests. Files listed in scripts/ui-strings-baseline.txt are exempt while they are
legacy; a baseline entry that is missing or no longer violates is itself a failure, so the list only shrinks.
"""
import pathlib
import re
import sys

START = re.compile(
    r'\bText\s*\(\s*(?:text\s*=\s*)?(?=")'
    r'|\b(?:contentDescription|stateDescription|label|title|text|placeholder|hint|supportingText|message|description)\s*=\s*(?:if\s*\([^)]*\)\s*)?(?=")'
    r'|\bshowSnackbar\s*\(\s*(?:message\s*=\s*)?(?=")'
)
LETTER = re.compile(r"[^\W\d_]")  # any Unicode letter
ESCAPE = re.compile(r"//[ \t]*ui-string-ok:[ \t]*\S")
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


def comment_start(line: str) -> int:
    """Index of the first `//` outside a string or char literal on this line, or -1."""
    i, n = 0, len(line)
    while i < n:
        if line.startswith('"""', i):
            j = line.find('"""', i + 3)
            if j < 0:
                return -1
            i = j + 3
        elif line[i] == '"':
            i += 1
            while i < n and line[i] != '"':
                i += 2 if line[i] == "\\" else 1
            i += 1
        elif line[i] == "'":
            i += 4 if line.startswith("'\\", i) else 3
        elif line.startswith("//", i):
            return i
        else:
            i += 1
    return -1


def escaped(line: str, code_start: int) -> bool:
    """True when the line carries `// ui-string-ok: <reason>` as a comment that follows the flagged code."""
    c = comment_start(line)
    return c > code_start and ESCAPE.match(line, c) is not None


def violations(path: pathlib.Path) -> list[int]:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.split("\n")
    out = []
    for m in START.finditer(text):
        body = TEMPLATE.sub("", literal_at(text, m.end()))
        if not LETTER.search(body):
            continue
        line_no = text.count("\n", 0, m.start()) + 1
        if escaped(lines[line_no - 1], m.start() - (text.rfind("\n", 0, m.start()) + 1)):
            continue
        out.append(line_no)
    return out


def main(root: pathlib.Path) -> int:
    baseline_file = root / "scripts" / "ui-strings-baseline.txt"
    baseline = {l.strip() for l in baseline_file.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")} if baseline_file.exists() else set()
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
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")))
