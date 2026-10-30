#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Runs Gradle and, on failure, surfaces the tail of the output as a workflow annotation
# (readable without a token; full job logs are not). Usage: scripts/ci-gradle.sh <gradle args>
set -uo pipefail
log="$(mktemp)"
./gradlew "$@" --console=plain >"$log" 2>&1
code=$?
if [ $code -ne 0 ]; then
  msg="$(grep -v -E '^> Task .*(UP-TO-DATE|NO-SOURCE|SKIPPED)$|^$' "$log" | tail -c 3500 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')"
  echo "::error title=gradle $*::$msg"
fi
exit $code
