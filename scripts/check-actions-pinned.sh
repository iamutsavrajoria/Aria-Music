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
