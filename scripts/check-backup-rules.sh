#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Fails if backup rules leak the secure/ area, cleartext traffic is allowed, or a Firebase/analytics reference appears.
set -euo pipefail
cd "$(dirname "$0")/.."
m=app/src/main/AndroidManifest.xml
grep -q 'android:dataExtractionRules="@xml/data_extraction_rules"' "$m" || { echo "manifest missing dataExtractionRules"; exit 1; }
grep -q 'android:fullBackupContent="@xml/backup_rules"' "$m" || { echo "manifest missing fullBackupContent"; exit 1; }
grep -q 'android:usesCleartextTraffic="false"' "$m" || { echo "cleartext traffic must be disabled"; exit 1; }
for f in app/src/main/res/xml/data_extraction_rules.xml app/src/main/res/xml/backup_rules.xml; do
  grep -q 'path="secure/"' "$f" || { echo "$f does not exclude secure/"; exit 1; }
done
if grep -n -i -E "firebase|crashlytics|google-services" app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml; then
  echo "Firebase or analytics reference found"; exit 1
fi
echo "ok"
