#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails unless every backup and device-transfer section excludes the private app data.

Usage: check_backup_rules.py <xml-res-dir>   (the directory holding backup_rules.xml and data_extraction_rules.xml)

Required exclusions, by (domain, path), in EVERY section:
  backup_rules.xml            -> <full-backup-content>
  data_extraction_rules.xml   -> <cloud-backup> and <device-transfer>
A section that is missing, an exclusion with the wrong domain or path, or any <include> element (which could
re-add excluded data) is a failure. Substring matches elsewhere in the file do not count.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED = [
    ("file", "secure/"),            # encrypted token store
    ("sharedpref", "secure.xml"),
    ("database", "aria.db"),        # Room database and its side files
    ("database", "aria.db-wal"),
    ("database", "aria.db-shm"),
    ("database", "aria.db-journal"),
    ("sharedpref", "resume.xml"),   # playback resume position
    ("file", "crash/"),             # crash reports
]


def sections(res_dir: Path):
    """Yield (label, element) for every section that must carry the exclusions; raise ValueError if one is absent."""
    full = ET.parse(res_dir / "backup_rules.xml").getroot()
    if full.tag != "full-backup-content":
        raise ValueError(f"backup_rules.xml root is <{full.tag}>, expected <full-backup-content>")
    yield "backup_rules.xml <full-backup-content>", full
    ext = ET.parse(res_dir / "data_extraction_rules.xml").getroot()
    if ext.tag != "data-extraction-rules":
        raise ValueError(f"data_extraction_rules.xml root is <{ext.tag}>, expected <data-extraction-rules>")
    for name in ("cloud-backup", "device-transfer"):
        found = ext.findall(name)
        if len(found) != 1:
            raise ValueError(f"data_extraction_rules.xml must contain exactly one <{name}> section, found {len(found)}")
        yield f"data_extraction_rules.xml <{name}>", found[0]


def problems(res_dir: Path) -> list[str]:
    out: list[str] = []
    try:
        for label, el in sections(res_dir):
            excluded = {(c.get("domain"), c.get("path")) for c in el if c.tag == "exclude"}
            for domain, path in REQUIRED:
                if (domain, path) not in excluded:
                    out.append(f'{label} is missing <exclude domain="{domain}" path="{path}" />')
            if any(c.tag == "include" for c in el):
                out.append(f"{label} contains an <include>; it could re-add excluded data")
    except (ValueError, ET.ParseError, OSError) as e:
        out.append(str(e))
    return out


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    errs = problems(Path(argv[1]))
    for e in errs:
        print(e)
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
