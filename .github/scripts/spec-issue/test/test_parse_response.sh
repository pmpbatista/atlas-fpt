#!/usr/bin/env bash
# Tests for parse_response() in call_claude.sh — sourced after call_claude.sh exists.

_tmp_spec=$(mktemp)
_tmp_discovery=$(mktemp)

# Happy path
_raw_ok='Some preamble.
<<<SPEC>>>
# Title — Implementation Design

**Date:** 2026-05-07
<<<END SPEC>>>
Some divider text.
<<<DISCOVERY>>>
## Discovery & Decisions

### Questions I considered
- Did I do the thing?
<<<END DISCOVERY>>>
Some footer.'

if parse_response "$_raw_ok" "$_tmp_spec" "$_tmp_discovery"; then
  assert_eq "$?" "0" "parse_response: returns 0 on happy path"
else
  assert_eq "fail" "happy" "parse_response: should have succeeded on happy input"
fi

assert_eq \
  "$(cat "$_tmp_spec")" \
  "# Title — Implementation Design

**Date:** 2026-05-07" \
  "parse_response: extracts SPEC body verbatim"

assert_eq \
  "$(cat "$_tmp_discovery")" \
  "## Discovery & Decisions

### Questions I considered
- Did I do the thing?" \
  "parse_response: extracts DISCOVERY body verbatim"

# Missing SPEC delimiter → fail
_raw_no_spec='<<<DISCOVERY>>>
something
<<<END DISCOVERY>>>'

if parse_response "$_raw_no_spec" "$_tmp_spec" "$_tmp_discovery"; then
  assert_eq "ok" "fail" "parse_response: should have failed when SPEC missing"
else
  assert_eq "fail" "fail" "parse_response: returns non-zero when SPEC missing"
fi

# Missing DISCOVERY delimiter → fail
_raw_no_discovery='<<<SPEC>>>
something
<<<END SPEC>>>'

if parse_response "$_raw_no_discovery" "$_tmp_spec" "$_tmp_discovery"; then
  assert_eq "ok" "fail" "parse_response: should have failed when DISCOVERY missing"
else
  assert_eq "fail" "fail" "parse_response: returns non-zero when DISCOVERY missing"
fi

# Empty SPEC body (delimiters present but nothing between) → fail
_raw_empty_spec='<<<SPEC>>>
<<<END SPEC>>>
<<<DISCOVERY>>>
something
<<<END DISCOVERY>>>'

if parse_response "$_raw_empty_spec" "$_tmp_spec" "$_tmp_discovery"; then
  assert_eq "ok" "fail" "parse_response: should have failed when SPEC body is empty"
else
  assert_eq "fail" "fail" "parse_response: returns non-zero when SPEC body is empty"
fi

rm -f "$_tmp_spec" "$_tmp_discovery"
