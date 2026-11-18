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
  # The tail above often misses which test failed: add the first lines of every failure from the JUnit XML results.
  fails="$(find . -ipath '*test-results*' -name '*.xml' -not -path './.git/*' -print0 2>/dev/null | xargs -0 -r grep -h -E -o '<testcase name="[^"]*"|<failure message="[^"]{0,400}' 2>/dev/null | grep -B1 '<failure' | head -c 3500 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')"
  summary="$(grep -E 'Tests on |Execute .*FAILED|^[A-Za-z0-9_.]+ > .* FAILED' "$log" | head -c 2500 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')"
  [ -n "$summary" ] && echo "::error title=failed tests (log)::$summary"
  [ -n "$fails" ] && echo "::error title=test failures::$fails"
fi
exit $code
