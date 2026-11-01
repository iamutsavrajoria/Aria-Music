#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Fails if backup/device-transfer rules do not exclude secure/, the Room database, resume prefs and crash reports (parsed per section by check_backup_rules.py; self-tested in test_check_backup_rules.py), if cleartext traffic is allowed, or a Firebase/analytics reference appears.
set -euo pipefail
cd "$(dirname "$0")/.."
m=app/src/main/AndroidManifest.xml
grep -q 'android:dataExtractionRules="@xml/data_extraction_rules"' "$m" || { echo "manifest missing dataExtractionRules"; exit 1; }
grep -q 'android:fullBackupContent="@xml/backup_rules"' "$m" || { echo "manifest missing fullBackupContent"; exit 1; }
grep -q 'android:usesCleartextTraffic="false"' "$m" || { echo "cleartext traffic must be disabled"; exit 1; }
python3 scripts/check_backup_rules.py app/src/main/res/xml || { echo "backup rules do not exclude all private data"; exit 1; }
if grep -n -i -E "firebase|crashlytics|google-services" app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml; then
  echo "Firebase or analytics reference found"; exit 1
fi
echo "ok"
