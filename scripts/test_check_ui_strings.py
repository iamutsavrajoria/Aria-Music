# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-ui-strings.sh"
KT = "app/src/main/kotlin/app/aria/music/ui/Screen.kt"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


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
