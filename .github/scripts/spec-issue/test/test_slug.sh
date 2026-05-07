#!/usr/bin/env bash
# Tests for slug() in slug.sh — sourced by run_tests.sh after slug.sh exists.

# Simple cases
assert_eq "$(slug 'Hello')" "hello" "slug: simple word"
assert_eq "$(slug 'Hello World')" "hello-world" "slug: spaces become dashes"
assert_eq "$(slug 'Hello, World!')" "hello-world" "slug: punctuation stripped"
assert_eq "$(slug 'Foo  Bar')" "foo-bar" "slug: multiple spaces collapse"
assert_eq "$(slug '  leading and trailing  ')" "leading-and-trailing" "slug: edges trimmed"

# Real issue titles
assert_eq \
  "$(slug 'Settings: local data backup (manual + scheduled) to user-chosen folder')" \
  "settings-local-data-backup-manual-scheduled-to" \
  "slug: real issue #9 title trimmed at word boundary"

assert_eq \
  "$(slug 'Ticker search-as-you-type dropdown (Yahoo Finance-style)')" \
  "ticker-search-as-you-type-dropdown-yahoo-finance" \
  "slug: real issue #5 title trimmed at word boundary"

assert_eq \
  "$(slug 'Use date picker for all date input/edit fields (incl. Assets)')" \
  "use-date-picker-for-all-date-input-edit-fields" \
  "slug: real issue #4 title trimmed at word boundary"

# Short titles untouched
assert_eq \
  "$(slug 'Fix bug')" \
  "fix-bug" \
  "slug: short title not truncated"

# Length cap exact-50 case (50-char input that's already at the cap)
assert_eq \
  "$(slug 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')" \
  "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  "slug: exactly 50 chars passes through"

# Length cap 51-char case (one over) — would be all "a"s; cut leaves 50 "a"s; no dash to trim back to, so passes through
assert_eq \
  "$(slug 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab')" \
  "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  "slug: 51 chars cut to 50 when no word boundary"
