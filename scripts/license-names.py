#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Prints the distinct license names of dependencies that failed the allowlist, as one line."""
import collections, json, sys
d = json.load(open(sys.argv[1]))
c = collections.defaultdict(list)
for dep in d.get("dependencies", []):
    names = set()
    for key in ("licenses", "moduleLicenses"):
        for l in dep.get(key) or []:
            names.add(l.get("license_name") or l.get("moduleLicense") or "?")
    c[tuple(sorted(names))].append(dep.get("moduleName") or dep.get("moduleGroup") or "?")
print(" || ".join("%s: %d e.g. %s" % (k, len(v), v[:2]) for k, v in c.items()))
