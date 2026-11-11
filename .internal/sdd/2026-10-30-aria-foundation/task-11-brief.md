### Task 11: CI, nightly smoke test, provenance and license gates

**Files:**
- Modify: `.github/workflows/ci.yml` (created in Task 1; this task adds jobs and steps to it)
- Create: `.github/workflows/smoke.yml`, `.github/dependabot.yml`, `scripts/check-actions-pinned.sh`, `gradle/verification-metadata.xml`, `scripts/check-provenance.py`, `scripts/test_check_provenance.py`, `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`
- Modify: `innertube/build.gradle.kts` (smoke test exclusion), `build.gradle.kts` (license report plugin)

**Interfaces:**
- Consumes: `docs/provenance.csv`, module list from `settings.gradle.kts`.
- Produces: `check-provenance.py` exits non-zero if any module directory lacks a valid ledger row.

**Acceptance Criteria:**
- `ci.yml` runs on every PR and push: builds `foss` and `gms` debug, unit tests for every module, `checkModuleGraph`, the backup-rules and destructive-migration scripts, the provenance check, and fails if `data/schemas` changed without a matching tracked file.
- `check-provenance.py` fails for: a module directory with no row; a row whose `kind` is not one of `copied|adapted|inspired|original`; a non-`original` row without a 40-hex `origin_commit` or without `origin_license`.
- `smoke.yml` runs nightly, resolves three fixed public tracks through `YouTubeStreamSource`, and opens one GitHub issue on failure (later failures comment on it), and fails if the smoke test was skipped rather than run.
- Every `uses:` in `.github/workflows` is pinned to a full commit SHA (enforced by `check-actions-pinned.sh`), the Gradle wrapper is validated, Dependabot covers Actions and Gradle, and `gradle/verification-metadata.xml` is committed and enforced.
- The license report job fails on a dependency whose license is not on the allowlist (Apache-2.0, MIT, BSD, GPL-3.0, LGPL, MPL-2.0, EPL, CC0, public domain, Unicode/ICU).

- [ ] **Step 1: Write the failing provenance tests**

`scripts/test_check_provenance.py`:
```python
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
```

- [ ] **Step 2: Run to verify failure**

Run: `python3 -m pytest scripts/test_check_provenance.py -q`
Expected: FAIL, because `check-provenance.py` does not exist.

- [ ] **Step 3: Implement the check**

`scripts/check-provenance.py`:
```python
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
```

Run: `python3 -m pytest scripts/test_check_provenance.py -q && python3 scripts/check-provenance.py .`
Expected: 5 passed; the real check fails until every module row exists. Add rows for `core`, `designsystem`, `playback`, `app` (kind `original`) to `docs/provenance.csv`, then re-run: exit 0.

- [ ] **Step 4: Smoke test (excluded from normal runs)**

`innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.smoke

import app.aria.domain.TrackId
import app.aria.innertube.adapter.YouTubeStreamSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Nightly early-warning for YouTube breakage. Runs only with -PrunSmoke (see innertube/build.gradle.kts). */
class PlaybackSmokeTest {
    private val ids = listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "9bZkp7q19f0")

    @Test fun fixedPublicTracksResolveToAnAudioUrl() = runBlocking {
        assumeTrue(System.getProperty("aria.smoke") == "true")
        val source = YouTubeStreamSource()
        val failures = ids.filter { id -> source.resolve(TrackId.youtube(id)).isFailure }
        assertTrue("Could not resolve: $failures", failures.isEmpty())
    }
}
```
Add to `innertube/build.gradle.kts`:
```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("aria.smoke", providers.gradleProperty("runSmoke").orNull ?: "false")
}
```
Run: `./gradlew :innertube:testDebugUnitTest`
Expected: PASS with the smoke test skipped (assumption not met).

- [ ] **Step 5: Write the workflows**

Replace the minimal `ci.yml` from Task 1 with this fuller one (same triggers; pin per Step 5b):
```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:
permissions: { contents: read }
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
        with: { persist-credentials: false }
      - uses: gradle/actions/wrapper-validation@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Static checks
        run: |
          python3 -m pip install pytest
          python3 -m pytest scripts -q
          python3 scripts/check-provenance.py .
          scripts/check-backup-rules.sh
          scripts/check-no-destructive-migration.sh
          scripts/check-actions-pinned.sh
          test -f gradle/verification-metadata.xml
      - name: Build-logic tests (module-graph rules)
        run: ./gradlew -p buildSrc test
      - name: Module graph
        run: ./gradlew checkModuleGraph
      - name: Unit tests (all modules)
        run: ./gradlew test
      - name: Build both flavors
        run: ./gradlew :app:assembleFossDebug :app:assembleGmsDebug
      - name: Schema is committed and unchanged
        # git diff ignores untracked files, so a forgotten new version file (2.json) would pass; status sees both.
        run: |
          out="$(git status --porcelain -- data/schemas)"
          if [ -n "$out" ]; then echo "Room schema changed or uncommitted:"; echo "$out"; exit 1; fi
      - name: Dependency licenses
        run: ./gradlew checkLicense
  glass-ui:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    strategy:
      matrix: { api: [26, 34] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api }}
          arch: x86_64
          script: ./gradlew :designsystem:connectedDebugAndroidTest
```

`.github/workflows/smoke.yml`:
```yaml
name: nightly-playback-smoke
on:
  schedule: [{ cron: "17 3 * * *" }]
  workflow_dispatch:
permissions: { contents: read, issues: write }
jobs:
  smoke:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :innertube:testDebugUnitTest -PrunSmoke=true --tests '*PlaybackSmokeTest'
      - name: Fail if the smoke test was skipped instead of run
        # assumeTrue skips silently when the property does not reach the test JVM; a skipped nightly must not look green.
        run: |
          f=$(ls innertube/build/test-results/testDebugUnitTest/*PlaybackSmokeTest.xml)
          grep -q 'tests="1"' "$f" && grep -q 'skipped="0"' "$f" || { echo "Smoke test did not run:"; head -3 "$f"; exit 1; }
      - name: Report failure (one open issue, not one per night)
        if: failure()
        env: { GH_TOKEN: "${{ github.token }}" }
        run: |
          run_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
          gh label create smoke-failure --color B60205 --description "Nightly playback smoke test" 2>/dev/null || true
          n=$(gh issue list --label smoke-failure --state open --json number --jq '.[0].number // empty')
          if [ -n "$n" ]; then
            gh issue comment "$n" --body "Failed again ($(date -u +%F)): $run_url"
          else
            gh issue create --label smoke-failure --title "Nightly playback smoke test failed ($(date -u +%F))" \
              --body "See run: $run_url. Note: YouTube may block datacenter IPs; if this fails every night with no code change, run the same command from the maintainer's machine on a schedule instead."
          fi
```

- [ ] **Step 5b: Supply-chain hardening (stress-test branch 8)**

The YAML above shows tags for readability; after this step every `uses:` line is pinned.

1. Pin check. `scripts/check-actions-pinned.sh` (already run in the Static checks step):
```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Fails if any workflow step uses an Action that is not pinned to a full 40-hex commit SHA.
set -euo pipefail
root="${1:-.}"
bad=$(grep -rnE '^\s*-?\s*uses:\s' "$root/.github/workflows" 2>/dev/null \
  | grep -vE 'uses:\s*\./' \
  | grep -vE 'uses:\s*[^@[:space:]]+@[0-9a-f]{40}(\s|$)' || true)
if [ -n "$bad" ]; then
  echo "Actions must be pinned to a full commit SHA (add the version as a trailing comment):" >&2
  echo "$bad" >&2
  exit 1
fi
echo "all workflow actions are SHA-pinned"
```
A dry run on a scratch workflow gave exit 1 for `actions/checkout@v4` and exit 0 once it was replaced by a 40-hex SHA with a trailing `# v4` comment. Make it executable with `chmod +x`.

2. Pin every `uses:` in `ci.yml` and `smoke.yml`. For each action and tag, resolve the SHA (needs network and a working `gh`), then rewrite the line as `uses: owner/repo@<sha> # vX.Y.Z`:
```bash
gh api repos/actions/checkout/git/ref/tags/v4 --jq '.object | "\(.type) \(.sha)"'
# if type is "tag" (annotated), dereference once more:
gh api repos/actions/checkout/git/tags/<sha> --jq '.object.sha'
```
Use the full tag (for example `v4.2.2`), not a moving major tag, so the comment says exactly what was pinned. Run `scripts/check-actions-pinned.sh .` until it exits 0. Actions to pin: `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `gradle/actions/wrapper-validation`, `reactivecircus/android-emulator-runner`.

3. `.github/dependabot.yml`:
```yaml
version: 2
updates:
  - package-ecosystem: github-actions
    directory: /
    schedule: { interval: weekly }
    groups: { actions: { patterns: ["*"] } }
  - package-ecosystem: gradle
    directory: /
    schedule: { interval: weekly }
    groups: { gradle-deps: { patterns: ["*"] } }
```

4. Gradle dependency verification. With network access, generate and commit the checksums:
```bash
./gradlew --write-verification-metadata sha256 :app:assembleFossDebug :app:assembleGmsDebug test
git add gradle/verification-metadata.xml
```
When the file exists Gradle enforces it on every build, so CI fails on any dependency whose checksum is missing or different. After any dependency bump (including Dependabot PRs, which cannot regenerate the file) run `./gradlew --write-verification-metadata sha256 help`, review the diff, and commit it. Record this in `CONTRIBUTING.md` (Task 12). A reviewer rejects any wildcard `<trusted-artifacts>` entry.

Run: `scripts/check-actions-pinned.sh . && ./gradlew :app:assembleFossDebug`
Expected: `all workflow actions are SHA-pinned`, then BUILD SUCCESSFUL under verification. If the sandbox has no network, do the pinning and checksum generation on the first run that has it (the Prerequisite already requires the GitHub repo).

- [ ] **Step 6: License gate**

In root `build.gradle.kts` add to the `plugins` block: `id("com.github.jk1.dependency-license-report") version "2.9"`, then:
```kotlin
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer

licenseReport {
    allowedLicensesFile = file("config/allowed-licenses.json")
    renderers = arrayOf(JsonReportRenderer())
    filters = arrayOf(LicenseBundleNormalizer())
}
```
The plugin registers `checkLicense` itself when `allowedLicensesFile` is set; do not register a task of that name. Create `config/allowed-licenses.json`:
```json
{
  "allowedLicenses": [
    { "moduleLicense": "The Apache Software License, Version 2.0" },
    { "moduleLicense": "Apache-2.0" },
    { "moduleLicense": "MIT License" },
    { "moduleLicense": "MIT" },
    { "moduleLicense": "BSD-3-Clause" },
    { "moduleLicense": "BSD-2-Clause" },
    { "moduleLicense": "GNU General Public License, version 3" },
    { "moduleLicense": "GPL-3.0" },
    { "moduleLicense": "LGPL-2.1" },
    { "moduleLicense": "MPL-2.0" },
    { "moduleLicense": "EPL-1.0" },
    { "moduleLicense": "CC0-1.0" },
    { "moduleLicense": "Unicode/ICU License" }
  ]
}
```
The plugin's task wiring for `allowedLicensesFile` is `checkLicense`; if the plugin version above is incompatible with Gradle 9.7 or AGP 9.3, pick the newest release that supports Gradle 9 and record the version in the commit message. Run: `./gradlew checkLicense`. Expected: passes, or lists dependencies whose license needs a decision from the maintainer (do not widen the allowlist to silence it).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "ci: add build/test/provenance/license gates and nightly playback smoke test"
```

---

