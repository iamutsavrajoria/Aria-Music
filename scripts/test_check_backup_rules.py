# SPDX-License-Identifier: GPL-3.0-only
import pathlib, shutil, subprocess, sys, tempfile
import xml.etree.ElementTree as ET

import pytest

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check_backup_rules.py"
RES = ROOT / "app" / "src" / "main" / "res" / "xml"
FILES = ["backup_rules.xml", "data_extraction_rules.xml"]


def run(res: pathlib.Path):
    p = subprocess.run([sys.executable, str(SCRIPT), str(res)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def copy_res(tmp: pathlib.Path) -> pathlib.Path:
    for f in FILES:
        shutil.copy(RES / f, tmp / f)
    return tmp


def excludes(root):
    return [(sec, c) for sec in ([root] if root.tag == "full-backup-content" else list(root)) for c in sec if c.tag == "exclude"]


def test_real_rules_pass():
    code, out = run(RES)
    assert code == 0, out


@pytest.mark.parametrize("fname", FILES)
def test_removing_any_single_exclusion_fails(fname, tmp_path):
    tree = ET.parse(RES / fname)
    for i in range(len(excludes(tree.getroot()))):
        res = copy_res(pathlib.Path(tempfile.mkdtemp(dir=tmp_path)))
        t = ET.parse(RES / fname)
        sec, c = excludes(t.getroot())[i]
        sec.remove(c)
        t.write(res / fname)
        code, out = run(res)
        assert code == 1, f"removing exclusion #{i} from {fname} was not detected"


@pytest.mark.parametrize("fname", FILES)
def test_wrong_domain_or_path_fails(fname, tmp_path):
    for attr, value in (("domain", "root"), ("path", "elsewhere/")):
        res = copy_res(pathlib.Path(tempfile.mkdtemp(dir=tmp_path)))
        t = ET.parse(RES / fname)
        sec, c = excludes(t.getroot())[0]
        c.set(attr, value)
        t.write(res / fname)
        assert run(res)[0] == 1


def test_missing_device_transfer_section_fails(tmp_path):
    res = copy_res(tmp_path)
    t = ET.parse(res / "data_extraction_rules.xml")
    root = t.getroot()
    root.remove(root.find("device-transfer"))
    t.write(res / "data_extraction_rules.xml")
    assert run(res)[0] == 1


def test_include_element_fails(tmp_path):
    res = copy_res(tmp_path)
    t = ET.parse(res / "backup_rules.xml")
    ET.SubElement(t.getroot(), "include", {"domain": "database", "path": "aria.db"})
    t.write(res / "backup_rules.xml")
    assert run(res)[0] == 1


def test_path_substring_in_a_comment_does_not_count(tmp_path):
    res = copy_res(tmp_path)
    (res / "backup_rules.xml").write_text(
        '<?xml version="1.0"?><full-backup-content><!-- secure/ aria.db resume.xml crash/ --></full-backup-content>'
    )
    assert run(res)[0] == 1
