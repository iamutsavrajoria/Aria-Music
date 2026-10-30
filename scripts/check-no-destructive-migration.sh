#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Fails if production code calls fallbackToDestructiveMigration.
if grep -rn "fallbackToDestructiveMigration" --include=*.kt --exclude-dir=test --exclude-dir=androidTest --exclude-dir=build --exclude-dir=.internal .; then
  echo "ERROR: destructive migration fallback found in production code" >&2
  exit 1
fi
