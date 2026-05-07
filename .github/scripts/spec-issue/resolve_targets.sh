#!/usr/bin/env bash
# Library: defines `resolve_targets()` and `remote_spec_branch_exists()`.

# Returns 0 (true) if origin has any branch matching spec/issue-N-*
remote_spec_branch_exists() {
  local n="$1"
  git ls-remote --heads origin "spec/issue-${n}-*" 2>/dev/null | grep -q .
}

# Prints the list of issue numbers to process, one per line.
# Args: $1 = ISSUE_NUMBER (empty for sweep mode, non-empty for single-issue)
# Returns non-zero if single-issue mode but the issue isn't open.
resolve_targets() {
  local input="$1"

  if [[ -n "$input" ]]; then
    local state
    state=$(gh issue view "$input" --json state -q .state 2>/dev/null || true)
    if [[ "$state" != "OPEN" ]]; then
      echo "::error::issue #${input} is not open (state=${state:-not-found})" >&2
      return 1
    fi
    printf '%s\n' "$input"
    return 0
  fi

  # Sweep mode: list open issues with no spec branch yet.
  local n
  for n in $(gh issue list --state open --limit 200 --json number -q '.[].number'); do
    if ! remote_spec_branch_exists "$n"; then
      printf '%s\n' "$n"
    fi
  done
}
