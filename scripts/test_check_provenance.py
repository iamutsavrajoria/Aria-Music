# SPDX-License-Identifier: GPL-3.0-only
import subprocess, sys, textwrap, pathlib, tempfile

SCRIPT = pathlib.Path(__file__).with_name("check-provenance.py")
SHA = "a" * 40

def run(settings: str, csv: str, dirs: list[str]):
    with tempfile.TemporaryDirectory() as d:
        root = pathlib.Path(d)
        (root / "settings.gradle.kts").write_text(settings)
        (root / "docs").mkdir()
        (root / "docs" / "provenance.csv").write_text(csv)
        for name in dirs:
            (root / name).mkdir()
        p = subprocess.run([sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True)
        return p.returncode, p.stdout + p.stderr

HEADER = "path,origin_repo,origin_commit,origin_license,kind,notes\n"
SETTINGS = 'include(":domain", ":data")'

def test_passes_when_every_module_has_a_row():
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata,o/r,{SHA},GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code == 0

def test_fails_when_a_module_has_no_row():
    code, out = run(SETTINGS, HEADER + "domain,,,,original,x\n", ["domain", "data"])
    assert code != 0 and "data" in out

def test_fails_on_invalid_kind():
    code, out = run(SETTINGS, HEADER + "domain,,,,original,x\ndata,o/r," + SHA + ",GPL-3.0,borrowed,x\n", ["domain", "data"])
    assert code != 0 and "kind" in out

def test_fails_when_ported_row_lacks_commit_or_license():
    code, _ = run(SETTINGS, HEADER + "domain,,,,original,x\ndata,o/r,,GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code != 0
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata,o/r,{SHA},,adapted,x\n", ["domain", "data"])
    assert code != 0

def test_subpath_rows_cover_their_module():
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata/src/entities,o/r,{SHA},GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code == 0
