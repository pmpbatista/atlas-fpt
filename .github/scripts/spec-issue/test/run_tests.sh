#!/usr/bin/env bash
# Bash unit tests for spec-issue helpers.
# Each test sources the relevant helper, calls a function with controlled
# input, and asserts the output. No external test framework — keeps the
# workflow runner image free of extra deps.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

passed=0
failed=0

assert_eq() {
  local actual="$1"
  local expected="$2"
  local name="$3"
  if [[ "$actual" == "$expected" ]]; then
    printf 'PASS  %s\n' "$name"
    passed=$((passed + 1))
  else
    printf 'FAIL  %s\n' "$name"
    printf '      expected: %q\n' "$expected"
    printf '      actual:   %q\n' "$actual"
    failed=$((failed + 1))
  fi
}

echo "Running spec-issue unit tests..."
echo "--------------------------------"

# Tests are appended below by later tasks via `source` calls.
# Until any helpers exist, the runner has nothing to do — emit a banner.

if [[ -f "${SCRIPT_DIR}/slug.sh" ]]; then
  source "${SCRIPT_DIR}/slug.sh"
  source "${SCRIPT_DIR}/test/test_slug.sh"
fi

if [[ -f "${SCRIPT_DIR}/call_claude.sh" ]]; then
  source "${SCRIPT_DIR}/test/test_parse_response.sh"
fi

echo "--------------------------------"
echo "Passed: ${passed}  Failed: ${failed}"
[[ $failed -eq 0 ]]
