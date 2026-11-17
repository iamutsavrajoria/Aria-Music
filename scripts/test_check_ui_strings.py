# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-ui-strings.sh"
KT = "app/src/main/kotlin/app/aria/music/ui/Screen.kt"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def run(root):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def flagged(tmp_path, body):
    put(tmp_path, KT, body)
    return run(tmp_path)


def test_literals_in_text_are_flagged(tmp_path):
    for body in ['Text("Hello")', 'Text(text = "Hello")', 'Text(\n    "Hello",\n    maxLines = 1)', 'Text("""Hello""")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1 and "Screen.kt" in out, (body, out)


def test_parameters_are_flagged(tmp_path):
    for body in ['Icon(x, contentDescription = "Play")', 'label = { }, title = "Queue"', 'placeholder = "Search"',
                 'stateDescription = "On"', 'scope.launch { host.showSnackbar("Saved") }', 'contentDescription = if (a) "A" else null']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_resources_nulls_and_templates_pass(tmp_path):
    body = 'Text(stringResource(R.string.x))\nIcon(x, contentDescription = null)\nText("$count")\nText("${a.b}")\nText("•")\nText(title)\n'
    code, out = flagged(tmp_path, body)
    assert code == 0, out


def test_letters_next_to_templates_are_flagged(tmp_path):
    code, out = flagged(tmp_path, 'Text("${a} songs")\n')
    assert code == 1, out


def test_escape_comment_allows_one_line(tmp_path):
    code, out = flagged(tmp_path, 'Text("Debug") // ui-string-ok: developer-only label\n')
    assert code == 0, out


def test_other_source_sets_are_ignored(tmp_path):
    put(tmp_path, "app/src/androidTest/kotlin/X.kt", 'Text("hello")\n')
    put(tmp_path, "app/src/test/kotlin/X.kt", 'Text("hello")\n')
    code, out = run(tmp_path)
    assert code == 0, out


def test_baseline_exempts_a_file_that_still_violates(tmp_path):
    put(tmp_path, "app/src/main/kotlin/Legacy.kt", 'Text("Old")\n')
    put(tmp_path, "scripts/ui-strings-baseline.txt", "app/src/main/kotlin/Legacy.kt\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_stale_baseline_entries_fail(tmp_path):
    put(tmp_path, "app/src/main/kotlin/Clean.kt", 'Text(stringResource(R.string.x))\n')
    put(tmp_path, "scripts/ui-strings-baseline.txt", "app/src/main/kotlin/Clean.kt\napp/src/main/kotlin/Missing.kt\n")
    code, out = run(tmp_path)
    assert code == 1 and "stale baseline" in out and "Clean.kt" in out and "Missing.kt" in out, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out


def test_new_violation_fails_even_with_baseline_present(tmp_path):
    put(tmp_path, "app/src/main/kotlin/Legacy.kt", 'Text("Old")\n')
    put(tmp_path, "app/src/main/kotlin/New.kt", 'Text("Fresh")\n')
    put(tmp_path, "scripts/ui-strings-baseline.txt", "app/src/main/kotlin/Legacy.kt\n")
    code, out = run(tmp_path)
    assert code == 1 and "New.kt" in out and "Legacy.kt" not in out, out


def test_real_baseline_contains_only_player_screen():
    entries = [l.strip() for l in (ROOT / "scripts" / "ui-strings-baseline.txt").read_text().splitlines()
               if l.strip() and not l.startswith("#")]
    assert entries == ["app/src/main/kotlin/app/aria/music/PlayerScreen.kt"], entries


def test_escape_requires_a_non_empty_reason(tmp_path):
    for tail in ["// ui-string-ok:", "// ui-string-ok:   ", "// ui-string-ok"]:
        code, out = flagged(tmp_path, f'Text("Debug") {tail}\n')
        assert code == 1, (tail, out)


def test_escape_token_inside_a_string_does_not_count(tmp_path):
    for body in ['Text("Debug // ui-string-ok: nope")', 'Text("Debug ui-string-ok: nope")',
                 'Text("a") ; val s = "// ui-string-ok: x" ; Text("Debug")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_escape_must_be_a_line_comment_after_the_code(tmp_path):
    for body in ['/* ui-string-ok: x */ Text("Debug")', '// ui-string-ok: x Text("Debug")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_escape_with_reason_still_works_after_code_with_urls_in_strings(tmp_path):
    code, out = flagged(tmp_path, 'Text("Debug") // ui-string-ok: dev label\n')
    assert code == 0, out
    code, out = flagged(tmp_path, 'Text("Debug", x = "http://a") // ui-string-ok: dev label\n')
    assert code == 0, out


def test_real_repo_has_no_escape_uses():
    hits = []
    for f in (ROOT / "app").rglob("*.kt"):
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if "ui-string-ok" in line:
                hits.append(f"{f.relative_to(ROOT).as_posix()}:{n}")
    assert hits == [], hits


def test_non_ascii_letters_count(tmp_path):
    for body in ['Text("é")', 'Text("日本語")', 'Text("Привет")', 'Text("ñ")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_non_letter_symbols_still_pass(tmp_path):
    code, out = flagged(tmp_path, 'Text("•")\nText("→")\nText("12")\nText("😀")\n')
    assert code == 0, out


def test_space_before_paren_is_flagged(tmp_path):
    for body in ['Text ("x")', 'Text  (\n "x")', 'Text (text = "x")', 'host.showSnackbar ("x")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_utf8_files_are_read_regardless_of_locale(tmp_path):
    put(tmp_path, KT, '// ünïcode comment\nText(stringResource(R.string.x))\n')
    p = subprocess.run(["bash", str(SCRIPT), str(tmp_path)], capture_output=True, text=True,
                       env={"PATH": "/usr/bin:/bin", "LC_ALL": "C", "PYTHONUTF8": "0", "PYTHONCOERCECLOCALE": "0"})
    assert p.returncode == 0, p.stdout + p.stderr
