#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Every user-visible string lives in strings.xml (UI shell decision 8). Self-tested by scripts/test_check_ui_strings.py.
# Usage: check-ui-strings.sh [repo-root]
exec python3 "$(dirname "$0")/check_ui_strings.py" "${1:-.}"
