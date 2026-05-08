# Spec-Issue Agent Implementation Plan

> **⚠️ Superseded.** The feature this plan implemented (a `workflow_dispatch` GitHub Action plus bash helpers) was dropped after the design pivot away from cloud-stored API keys. The replacement is in [`2026-05-08-spec-issue-commands.md`](2026-05-08-spec-issue-commands.md) (slash-commands-only). This plan is left in place for historical context; **do not execute it**.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `workflow_dispatch`-triggered GitHub Action that turns open issues in this repo into draft spec PRs (one branch + spec file + draft PR per issue), plus a thin `/spec-review N` Claude Code slash command for revising generated specs locally.

**Architecture:** Bash scripts + `gh` + `jq` + `curl`, orchestrated by a single workflow YAML. Pure-function units (slug, response parsing) get plain-bash unit tests; integration glue (API call, branching, PR creation) is verified by end-to-end runs against a real issue. The slash command is a thin shell over the existing `superpowers:brainstorming` skill.

**Tech Stack:** Bash 5, `gh` CLI, `jq`, `curl`, GitHub Actions (`ubuntu-latest`), Claude Messages API, Claude Code slash commands.

---

## Reference

Full design: `docs/superpowers/specs/2026-05-07-spec-issue-agent-design.md`. Read it before starting — it locks in non-obvious decisions (skip-with-notice, pinned bundle, two-section delimited output, `Refs #N` not `Closes #N`, manual cleanup).

## File Map

| File | Change |
|---|---|
| `.github/scripts/spec-issue/system_prompt.txt` | **Create** — role + output contract for the model |
| `.github/scripts/spec-issue/slug.sh` | **Create** — title → branch-safe slug (pure function, unit-tested) |
| `.github/scripts/spec-issue/call_claude.sh` | **Create** — `call_claude` (curl wrapper) + `parse_response` (delimiter splitter, unit-tested) |
| `.github/scripts/spec-issue/resolve_targets.sh` | **Create** — single vs sweep target enumeration; `remote_spec_branch_exists` |
| `.github/scripts/spec-issue/build_bundle.sh` | **Create** — assembles the pinned context bundle as a Claude Messages API payload |
| `.github/scripts/spec-issue/run.sh` | **Create** — entrypoint orchestrator; loops issues; pushes branch + opens PR + comments on issue |
| `.github/scripts/spec-issue/test/run_tests.sh` | **Create** — bash unit-test runner for `slug` and `parse_response` |
| `.github/scripts/spec-issue/README.md` | **Create** — script-level docs (entrypoint, test command, secrets) |
| `.github/workflows/spec-issue.yml` | **Create** — `workflow_dispatch` workflow with `issue_number` input |
| `.claude/commands/spec-review.md` | **Create** — `/spec-review N` slash command |

No app source files are modified. This is repo tooling only.

---

### Task 1: Create the directory skeleton + lockable test runner

**Files:**
- Create: `.github/scripts/spec-issue/test/run_tests.sh`

The test runner is the first thing built so all subsequent unit-tested tasks have a place to drop assertions. It uses zero external dependencies (no `bats`, no `shunit2`) — just bash with `set -euo pipefail` and a tiny `assert_eq` helper.

- [ ] **Step 1: Create the directory tree**

Run:
```bash
mkdir -p .github/scripts/spec-issue/test
```

- [ ] **Step 2: Create `.github/scripts/spec-issue/test/run_tests.sh`**

```bash
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
  source "${SCRIPT_DIR}/test/test_slug.sh"
fi

if [[ -f "${SCRIPT_DIR}/call_claude.sh" ]]; then
  source "${SCRIPT_DIR}/test/test_parse_response.sh"
fi

echo "--------------------------------"
echo "Passed: ${passed}  Failed: ${failed}"
[[ $failed -eq 0 ]]
```

- [ ] **Step 3: Make the runner executable**

Run:
```bash
chmod +x .github/scripts/spec-issue/test/run_tests.sh
```

- [ ] **Step 4: Verify it runs cleanly with no test files yet**

Run: `./.github/scripts/spec-issue/test/run_tests.sh`

Expected output:
```
Running spec-issue unit tests...
--------------------------------
--------------------------------
Passed: 0  Failed: 0
```

Exit code 0.

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/spec-issue/test/run_tests.sh
git commit -m "chore: scaffold spec-issue test runner"
```

---

### Task 2: Write failing tests for `slug.sh`

**Files:**
- Create: `.github/scripts/spec-issue/test/test_slug.sh`

The slug function lowercases, strips non-alphanumerics to dashes, caps at 50 chars, and trims back to the last word boundary so we never end mid-word.

- [ ] **Step 1: Create the test file**

```bash
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
```

- [ ] **Step 2: Run the test runner — should still pass with 0 tests because slug.sh doesn't exist yet**

Run: `./.github/scripts/spec-issue/test/run_tests.sh`

Expected:
```
Running spec-issue unit tests...
--------------------------------
--------------------------------
Passed: 0  Failed: 0
```

The conditional `if [[ -f "${SCRIPT_DIR}/slug.sh" ]]` keeps the runner green until the next task creates `slug.sh`.

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/test/test_slug.sh
git commit -m "test: add slug() unit tests"
```

---

### Task 3: Implement `slug.sh` to make the tests pass

**Files:**
- Create: `.github/scripts/spec-issue/slug.sh`

- [ ] **Step 1: Create the file**

```bash
#!/usr/bin/env bash
# Library: defines `slug()` — title → branch-safe slug, capped at 50 chars,
# trimmed to a word boundary so we never end mid-word.

slug() {
  local title="$1"
  local s
  s=$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')
  if [[ ${#s} -gt 50 ]]; then
    s="${s:0:50}"
    # If the first 50 chars don't end on a `-`, peel back to the previous `-`.
    if [[ "$s" != *-* ]]; then
      :  # no dash at all — leave as-is (e.g. one giant word)
    elif [[ "${s: -1}" != "-" ]]; then
      s="${s%-*}"
    else
      s="${s%-}"
    fi
  fi
  printf '%s' "$s"
}
```

- [ ] **Step 2: Run the tests**

Run: `./.github/scripts/spec-issue/test/run_tests.sh`

Expected:
```
Running spec-issue unit tests...
--------------------------------
PASS  slug: simple word
PASS  slug: spaces become dashes
PASS  slug: punctuation stripped
PASS  slug: multiple spaces collapse
PASS  slug: edges trimmed
PASS  slug: real issue #9 title trimmed at word boundary
PASS  slug: real issue #5 title trimmed at word boundary
PASS  slug: real issue #4 title trimmed at word boundary
PASS  slug: short title not truncated
PASS  slug: exactly 50 chars passes through
PASS  slug: 51 chars cut to 50 when no word boundary
--------------------------------
Passed: 11  Failed: 0
```

If any FAIL — fix `slug.sh` and re-run. Don't proceed until all pass.

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/slug.sh
git commit -m "feat: add slug() helper for spec-issue branch names"
```

---

### Task 4: Write failing tests for `parse_response`

**Files:**
- Create: `.github/scripts/spec-issue/test/test_parse_response.sh`

`parse_response` extracts content between `<<<SPEC>>>...<<<END SPEC>>>` and `<<<DISCOVERY>>>...<<<END DISCOVERY>>>` from the model's response, writing each to a separate file. Returns 0 only if both files end up non-empty.

- [ ] **Step 1: Create the test file**

```bash
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
```

- [ ] **Step 2: Run the test runner — slug tests pass, parse_response tests inert until call_claude.sh exists**

Run: `./.github/scripts/spec-issue/test/run_tests.sh`

Expected: same output as Task 3 step 2 (slug section), no parse_response output yet because `call_claude.sh` doesn't exist. Exit 0.

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/test/test_parse_response.sh
git commit -m "test: add parse_response() unit tests"
```

---

### Task 5: Implement `call_claude.sh` (with `parse_response`) to make the tests pass

**Files:**
- Create: `.github/scripts/spec-issue/call_claude.sh`

This file contains two functions: `call_claude` (the network call — not unit-tested, verified end-to-end) and `parse_response` (pure delimiter splitter — unit-tested).

- [ ] **Step 1: Create the file**

```bash
#!/usr/bin/env bash
# Library: defines `call_claude()` and `parse_response()`.
# call_claude posts a Messages API payload and prints the model's
# textual response on stdout. parse_response splits a raw response
# into SPEC and DISCOVERY files, returning non-zero if either is empty
# or its delimiter pair is missing.

call_claude() {
  local payload="$1"
  curl -sSf https://api.anthropic.com/v1/messages \
    -H "x-api-key: ${ANTHROPIC_API_KEY}" \
    -H "anthropic-version: 2023-06-01" \
    -H "content-type: application/json" \
    -d "$payload" \
  | jq -r '.content[0].text'
}

parse_response() {
  local raw="$1"
  local spec_out="$2"
  local discovery_out="$3"

  # awk extracts the lines BETWEEN the delimiters (delimiters themselves excluded).
  printf '%s\n' "$raw" \
    | awk '/<<<SPEC>>>/{flag=1; next} /<<<END SPEC>>>/{flag=0} flag' \
    > "$spec_out"

  printf '%s\n' "$raw" \
    | awk '/<<<DISCOVERY>>>/{flag=1; next} /<<<END DISCOVERY>>>/{flag=0} flag' \
    > "$discovery_out"

  # Strip the trailing newline that awk's stream adds, so blank-only outputs
  # don't false-positive as "non-empty".
  if [[ ! -s "$spec_out" ]] || [[ -z "$(tr -d '[:space:]' < "$spec_out")" ]]; then
    return 1
  fi
  if [[ ! -s "$discovery_out" ]] || [[ -z "$(tr -d '[:space:]' < "$discovery_out")" ]]; then
    return 1
  fi
  return 0
}
```

- [ ] **Step 2: Run the test runner**

Run: `./.github/scripts/spec-issue/test/run_tests.sh`

Expected: all slug PASS lines plus all parse_response PASS lines, totaling 17 passing tests, 0 failed:

```
...
PASS  parse_response: returns 0 on happy path
PASS  parse_response: extracts SPEC body verbatim
PASS  parse_response: extracts DISCOVERY body verbatim
PASS  parse_response: returns non-zero when SPEC missing
PASS  parse_response: returns non-zero when DISCOVERY missing
PASS  parse_response: returns non-zero when SPEC body is empty
--------------------------------
Passed: 17  Failed: 0
```

If any FAIL — fix `call_claude.sh` and re-run.

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/call_claude.sh
git commit -m "feat: add call_claude + parse_response helpers"
```

---

### Task 6: Write `system_prompt.txt`

**Files:**
- Create: `.github/scripts/spec-issue/system_prompt.txt`

The model's role + output contract. Plain text, committed alongside the scripts so it's diffable and reviewable.

- [ ] **Step 1: Create the file**

```text
You are an experienced software designer writing an implementation design doc
for an existing Android personal-finance app called SpendTrack. The codebase
already has established conventions and prior specs you can use as guides.
You will be given:

- The full text of one open GitHub issue (title + body + labels)
- The repo's top-level SPEC.md and CLAUDE.md
- The three most recent design docs from docs/superpowers/specs/ as format examples
- A directory tree listing of the source tree (no file contents)

You CANNOT read additional files. If you would benefit from reading specific
files, list them in the "Files I'd have read" section of the discovery report.

Output a single response with EXACTLY two sections, delimited by sentinel
lines:

<<<SPEC>>>
... committed design doc, matching the format and tone of the example specs.
The first lines must include `**Date:**`, `**Issue:**`, and `**Source:**`
fields, in that order.
<<<END SPEC>>>

<<<DISCOVERY>>>
## Discovery & Decisions
... including the five required subsections, in this order:
  - Questions I considered
  - Options weighed
  - Recommended approach (and why)
  - Assumptions I made — verify these
  - Files I'd have read with deeper grounding
<<<END DISCOVERY>>>

Rules:
- Always produce both sections, even for thin or vague issues. Make
  assumptions visible in the assumptions list rather than refusing.
- Match the architecture, layering, and dispatcher conventions established
  in CLAUDE.md and the example specs. Don't invent new patterns where
  existing ones apply.
- Keep the spec implementation-ready: concrete enough that a developer
  could start work, but not so prescriptive that obvious code-level details
  are guessed beyond the bundle's grounding.
- Cite specific files only if they appear in the tree listing or example
  specs. Don't invent file paths.
- The committed spec should not contain the discovery report — keep them
  cleanly separated.
```

- [ ] **Step 2: Commit**

```bash
git add .github/scripts/spec-issue/system_prompt.txt
git commit -m "feat: add spec-issue system prompt"
```

---

### Task 7: Write `resolve_targets.sh`

**Files:**
- Create: `.github/scripts/spec-issue/resolve_targets.sh`

Single-issue mode validates the input is open. Sweep mode lists open issues and excludes those with an existing `spec/issue-N-*` branch on origin. Both paths emit issue numbers on stdout, one per line.

- [ ] **Step 1: Create the file**

```bash
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
```

No unit tests — both functions depend on `gh` and `git ls-remote`, which need network + auth. Verified by the end-to-end runs in Phase D.

- [ ] **Step 2: Lint the file with `bash -n`**

Run: `bash -n .github/scripts/spec-issue/resolve_targets.sh`

Expected: silent (exit 0). Any syntax error must be fixed before continuing.

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/resolve_targets.sh
git commit -m "feat: add resolve_targets + branch-existence check"
```

---

### Task 8: Write `build_bundle.sh`

**Files:**
- Create: `.github/scripts/spec-issue/build_bundle.sh`

Assembles the pinned context bundle as a Messages API JSON payload. Uses `jq -n --arg ...` so every interpolated value is JSON-encoded safely (newlines, quotes, backslashes all preserved).

- [ ] **Step 1: Create the file**

```bash
#!/usr/bin/env bash
# Library: defines `build_bundle()`. Emits a Claude Messages API payload as
# a single JSON object on stdout.

build_bundle() {
  local issue_number="$1"
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

  local issue_json spec_md claude_md tree_listing system_prompt
  issue_json=$(gh issue view "$issue_number" --json number,title,body,labels)
  spec_md=$(cat SPEC.md 2>/dev/null || printf '%s' '(SPEC.md missing)')
  claude_md=$(cat CLAUDE.md 2>/dev/null || printf '%s' '(CLAUDE.md missing)')
  tree_listing=$(tree -L 3 -d app/src/main/java/ 2>/dev/null | head -n 200 || printf '%s' '(tree not available)')
  system_prompt=$(cat "${script_dir}/system_prompt.txt")

  # Pull the 3 most-recent example specs into separate strings so jq can
  # JSON-encode each one cleanly. If fewer than 3 exist, the missing slots
  # are empty strings.
  local recent_files
  recent_files=$(ls -t docs/superpowers/specs/*.md 2>/dev/null | head -n 3 || true)
  local recent_1 recent_2 recent_3
  recent_1=$(printf '%s\n' "$recent_files" | sed -n 1p | xargs -I{} cat {} 2>/dev/null || printf '')
  recent_2=$(printf '%s\n' "$recent_files" | sed -n 2p | xargs -I{} cat {} 2>/dev/null || printf '')
  recent_3=$(printf '%s\n' "$recent_files" | sed -n 3p | xargs -I{} cat {} 2>/dev/null || printf '')

  jq -n \
    --arg model "${CLAUDE_MODEL:-claude-opus-4-7}" \
    --arg system "$system_prompt" \
    --arg issue "$issue_json" \
    --arg spec "$spec_md" \
    --arg claude_md "$claude_md" \
    --arg tree "$tree_listing" \
    --arg recent_1 "$recent_1" \
    --arg recent_2 "$recent_2" \
    --arg recent_3 "$recent_3" \
    '{
      model: $model,
      max_tokens: 16000,
      system: $system,
      messages: [{
        role: "user",
        content: [
          {type: "text", text: ("## Issue\n" + $issue)},
          {type: "text", text: ("## SPEC.md\n" + $spec)},
          {type: "text", text: ("## CLAUDE.md\n" + $claude_md)},
          {type: "text", text: ("## Source tree\n" + $tree)},
          {type: "text", text: ("## Prior specs (most recent first)\n---\n" + $recent_1 + "\n---\n" + $recent_2 + "\n---\n" + $recent_3)}
        ]
      }]
    }'
}
```

- [ ] **Step 2: Lint the file**

Run: `bash -n .github/scripts/spec-issue/build_bundle.sh`

Expected: silent (exit 0).

- [ ] **Step 3: Smoke test against the live repo (requires `gh auth status` to be green)**

Run:
```bash
( source .github/scripts/spec-issue/build_bundle.sh && CLAUDE_MODEL=claude-opus-4-7 build_bundle 4 ) \
  | jq '.model, .max_tokens, (.messages[0].content | length)'
```

Expected output:
```
"claude-opus-4-7"
16000
5
```

Five content blocks: Issue, SPEC.md, CLAUDE.md, tree, prior specs. `model` matches the env var. `max_tokens` is 16000.

- [ ] **Step 4: Commit**

```bash
git add .github/scripts/spec-issue/build_bundle.sh
git commit -m "feat: add build_bundle for spec-issue context payload"
```

---

### Task 9: Write `run.sh`

**Files:**
- Create: `.github/scripts/spec-issue/run.sh`

The orchestrator. Loops eligible issues, calls Claude, parses, branches, pushes, opens the draft PR, comments on the issue. Designed so a per-issue failure does not abort the whole sweep.

- [ ] **Step 1: Create the file**

```bash
#!/usr/bin/env bash
# Entrypoint for the spec-issue workflow. Sources the helper libraries and
# loops over the eligible issue numbers, producing a spec branch + draft PR
# + issue comment per issue. Per-issue failures are logged and do not abort
# the entire run.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/slug.sh"
source "${SCRIPT_DIR}/call_claude.sh"
source "${SCRIPT_DIR}/resolve_targets.sh"
source "${SCRIPT_DIR}/build_bundle.sh"

push_spec_and_pr() {
  local n="$1"
  local spec_src="$2"
  local discovery_src="$3"

  local title slug_str branch spec_path today_date default_branch
  title=$(gh issue view "$n" --json title -q .title)
  slug_str=$(slug "$title")
  branch="spec/issue-${n}-${slug_str}"
  today_date=$(date -u +%Y-%m-%d)
  spec_path="docs/superpowers/specs/${today_date}-issue-${n}-${slug_str}-design.md"
  default_branch=$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)

  git checkout -b "$branch"
  mkdir -p "$(dirname "$spec_path")"
  cp "$spec_src" "$spec_path"
  git add "$spec_path"
  git commit -m "spec: draft for issue #${n}"
  git push -u origin "$branch"

  local pr_body
  pr_body=$(printf '%s\n\nRefs #%s\n\n---\n*Generated by spec-issue workflow. Review before merge.*\n' \
              "$(cat "$discovery_src")" "$n")

  local pr_url
  pr_url=$(gh pr create \
    --draft \
    --title "Spec: ${title} (#${n})" \
    --body "$pr_body" \
    --base "$default_branch" \
    | tail -n 1)

  gh issue comment "$n" --body "Spec PR opened: ${pr_url}" \
    || echo "::warning::issue comment failed for #${n}; PR is the canonical artifact"

  # Return to the default branch so the next issue starts from a clean state.
  git checkout "$default_branch"
}

main() {
  local targets
  targets=$(resolve_targets "${ISSUE_NUMBER:-}")
  if [[ -z "$targets" ]]; then
    echo "::notice::no eligible issues — nothing to do"
    return 0
  fi

  local processed=0 failed=0 issue_number
  for issue_number in $targets; do
    echo "::group::issue #${issue_number}"

    if remote_spec_branch_exists "$issue_number"; then
      echo "::notice::skipped #${issue_number} (branch exists)"
      echo "::endgroup::"
      continue
    fi

    local bundle raw_response tmp_spec tmp_discovery
    tmp_spec=$(mktemp)
    tmp_discovery=$(mktemp)

    if ! bundle=$(build_bundle "$issue_number"); then
      echo "::error::build_bundle failed for #${issue_number}"
      failed=$((failed + 1))
      rm -f "$tmp_spec" "$tmp_discovery"
      echo "::endgroup::"
      continue
    fi

    if ! raw_response=$(call_claude "$bundle"); then
      echo "::error::call_claude failed for #${issue_number}"
      failed=$((failed + 1))
      rm -f "$tmp_spec" "$tmp_discovery"
      echo "::endgroup::"
      continue
    fi

    if ! parse_response "$raw_response" "$tmp_spec" "$tmp_discovery"; then
      echo "::error::delimiter parsing failed for issue #${issue_number}"
      printf '%s' "$raw_response" > "/tmp/raw-${issue_number}.txt"
      failed=$((failed + 1))
      rm -f "$tmp_spec" "$tmp_discovery"
      echo "::endgroup::"
      continue
    fi

    if ! push_spec_and_pr "$issue_number" "$tmp_spec" "$tmp_discovery"; then
      echo "::error::push/PR failed for #${issue_number}"
      failed=$((failed + 1))
      rm -f "$tmp_spec" "$tmp_discovery"
      echo "::endgroup::"
      continue
    fi

    rm -f "$tmp_spec" "$tmp_discovery"
    processed=$((processed + 1))
    echo "::endgroup::"
  done

  echo "processed=${processed} failed=${failed}"
  [[ $failed -eq 0 ]]
}

main "$@"
```

- [ ] **Step 2: Lint the file**

Run: `bash -n .github/scripts/spec-issue/run.sh`

Expected: silent (exit 0).

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/spec-issue/run.sh
git commit -m "feat: add spec-issue orchestrator (run.sh)"
```

---

### Task 10: Write the GitHub Action workflow

**Files:**
- Create: `.github/workflows/spec-issue.yml`

- [ ] **Step 1: Create the file**

```yaml
name: Spec from Issue

on:
  workflow_dispatch:
    inputs:
      issue_number:
        description: "Issue number to process. Leave empty to sweep all open issues without a spec branch."
        required: false
        default: ""

permissions:
  contents: write
  pull-requests: write
  issues: write

jobs:
  generate:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Configure git identity
        run: |
          git config user.name  "spec-issue-bot"
          git config user.email "spec-issue-bot@users.noreply.github.com"

      - name: Install jq + tree
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y jq tree

      - name: Run unit tests for helpers
        run: bash .github/scripts/spec-issue/test/run_tests.sh

      - name: Run spec generator
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          CLAUDE_MODEL: claude-opus-4-7
          ISSUE_NUMBER: ${{ inputs.issue_number }}
        run: bash .github/scripts/spec-issue/run.sh

      - name: Upload raw response artifacts on parse failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: spec-issue-raw-responses
          path: /tmp/raw-*.txt
          if-no-files-found: ignore
```

The unit-test step runs *before* the generator so a regression in `slug` or `parse_response` aborts the run with no API spend.

- [ ] **Step 2: Validate the YAML syntax**

Run: `python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/spec-issue.yml'))" && echo OK`

Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/spec-issue.yml
git commit -m "feat: add Spec from Issue workflow"
```

---

### Task 11: Write the scripts README

**Files:**
- Create: `.github/scripts/spec-issue/README.md`

- [ ] **Step 1: Create the file**

```markdown
# spec-issue scripts

Helpers for `.github/workflows/spec-issue.yml`. The workflow turns open
GitHub issues into draft spec PRs by calling the Claude Messages API with
a pinned context bundle.

## Files

| File | Purpose |
|---|---|
| `run.sh` | Entrypoint. Sources the helpers and loops eligible issues. |
| `slug.sh` | `slug()` — title → branch-safe slug, capped at 50 chars. |
| `resolve_targets.sh` | `resolve_targets()` (single vs sweep) + `remote_spec_branch_exists()`. |
| `build_bundle.sh` | `build_bundle()` — builds the Messages API payload (pinned context). |
| `call_claude.sh` | `call_claude()` (curl wrapper) + `parse_response()` (delimiter splitter). |
| `system_prompt.txt` | Role + output contract for the model. Diff this when iterating on quality. |
| `test/run_tests.sh` | Bash unit tests for `slug` and `parse_response`. Run on CI before the generator. |

## Required secrets

The workflow needs one repo secret in addition to `GITHUB_TOKEN`:

- `ANTHROPIC_API_KEY` — Claude API key. Set under **Settings → Secrets and
  variables → Actions → New repository secret**.

## Run unit tests locally

```bash
bash .github/scripts/spec-issue/test/run_tests.sh
```

All tests should pass. The runner has zero external dependencies (no
`bats`, no `shunit2`).

## Run the workflow

From the **Actions** tab in GitHub, choose **Spec from Issue** → **Run
workflow**. Optional `issue_number` input:

- Empty string: sweep all open issues without a `spec/issue-N-*` branch.
- Non-empty: process that one issue.

If a spec branch already exists for an issue, the workflow skips it with
a notice. To regenerate, manually delete the remote branch first.

## Output per issue

- Branch `spec/issue-N-<slug>` pushed to origin
- Spec file at `docs/superpowers/specs/<date>-issue-N-<slug>-design.md`
- Draft PR titled `Spec: <issue title> (#N)` with the discovery report in
  the body, footer `Refs #N`
- Comment on issue #N linking to the PR

## Reviewing generated specs

Use the `/spec-review N` Claude Code slash command from
`.claude/commands/spec-review.md`. It checks out the spec branch and
hands the spec, discovery report, and original issue to the brainstorming
skill for interactive revision.
```

- [ ] **Step 2: Commit**

```bash
git add .github/scripts/spec-issue/README.md
git commit -m "docs: README for spec-issue scripts"
```

---

### Task 12: Write the `/spec-review` slash command

**Files:**
- Create: `.claude/commands/spec-review.md`

- [ ] **Step 1: Create the directory if it doesn't exist**

Run:
```bash
mkdir -p .claude/commands
```

- [ ] **Step 2: Create the file**

```markdown
---
description: Review and revise a draft spec PR generated by the spec-issue workflow.
argument-hint: <issue-number>
---

You are reviewing a draft spec PR generated by `.github/workflows/spec-issue.yml`
for issue #$ARGUMENTS.

Steps:

1. **Locate the spec PR.** Run:
   ```bash
   gh pr list --state open --search "Spec: in:title #$ARGUMENTS in:body" \
     --json number,headRefName,title,url
   ```
   If multiple match, prefer the most recent. If none match, run
   `gh issue view $ARGUMENTS --comments` and look for the bot comment
   containing "Spec PR opened:" — follow that link.

2. **Check out the head branch.** Run:
   ```bash
   gh pr checkout <pr-number>
   ```

3. **Read context into the conversation.** Read these files/values:
   - The spec file at `docs/superpowers/specs/*-issue-$ARGUMENTS-*-design.md`
   - The PR body: `gh pr view <pr-number> --json body -q .body`
   - The original issue body: `gh issue view $ARGUMENTS --json body -q .body`

4. **Invoke `superpowers:brainstorming`** with this framing:
   > "Here's a draft spec generated by the spec-issue agent for issue
   > #$ARGUMENTS. The committed spec is the file in `docs/superpowers/specs/`.
   > The agent's discovery report (questions, options, recommendation,
   > assumptions, files-it-would-have-read) is in the PR body. Walk me
   > through the decisions and surface anything I want to revise."

5. **Apply user-approved revisions:**
   - Edit the spec file in place.
   - Stage + commit: `git add <spec-file> && git commit -m "spec: revise <area>"`
   - Push: `git push origin HEAD`
   - If the discovery report in the PR body needs updating, write the new
     body to a temp file and run:
     ```bash
     gh pr edit <pr-number> --body-file <tmpfile>
     ```

6. **Do not** mark the PR ready for review or merge it — that stays a
   manual step the user takes when they're happy.
```

- [ ] **Step 3: Commit**

```bash
git add .claude/commands/spec-review.md
git commit -m "feat: add /spec-review slash command"
```

---

### Task 13: Push the branch and configure the API secret

**Files:** none (manual repo configuration)

- [ ] **Step 1: Push the branch**

Run:
```bash
git push origin feat/spec-issue-agent
```

If the branch is already pushed, this is a no-op for new commits.

- [ ] **Step 2: Add the `ANTHROPIC_API_KEY` repo secret**

Manual, via the GitHub UI:

1. Go to https://github.com/pmpbatista/spendtrack/settings/secrets/actions
2. Click **New repository secret**
3. Name: `ANTHROPIC_API_KEY`
4. Value: your Claude API key (from https://console.anthropic.com/)
5. Click **Add secret**

- [ ] **Step 3: Verify the workflow appears in the Actions tab**

Manual:

1. Go to https://github.com/pmpbatista/spendtrack/actions
2. Confirm **Spec from Issue** appears in the left sidebar (after the branch is pushed).

The workflow only becomes runnable from `main` once the branch is merged,
but it can be invoked from a non-default branch by selecting the branch
explicitly in the **Run workflow** dialog.

---

### Task 14: End-to-end smoke test — single-issue mode against issue #4

**Files:** none (manual workflow run + verification)

Issue #4 ("Use date picker for all date input/edit fields (incl. Assets)")
is the thinnest open issue. Use it to validate the loud-assumption flow.

- [ ] **Step 1: Trigger the workflow**

1. Go to https://github.com/pmpbatista/spendtrack/actions/workflows/spec-issue.yml
2. Click **Run workflow**
3. Branch: `feat/spec-issue-agent`
4. `issue_number`: `4`
5. Click **Run workflow**

- [ ] **Step 2: Watch the run**

1. Open the running job
2. Confirm the unit-test step passes (17 passed, 0 failed)
3. Confirm the `Run spec generator` step succeeds and prints `processed=1 failed=0`

- [ ] **Step 3: Verify the artefacts**

1. **Branch exists:** `git fetch origin && git branch -r | grep spec/issue-4-`
   Expected: a single line ending in `spec/issue-4-use-date-picker-for-all-date-input-edit-fields`
2. **Spec file exists** on the new branch: `git show origin/spec/issue-4-...:docs/superpowers/specs/2026-05-07-issue-4-use-date-picker-for-all-date-input-edit-fields-design.md | head -20`
   Expected: starts with `# ... — Implementation Design`, includes `**Date:**`, `**Issue:** #4`, `**Source:**`.
3. **Draft PR exists:** `gh pr list --state open --search "Spec: #4 in:title"`
   Expected: one row, draft, base = default branch, title starts with `Spec:`.
4. **PR body contains discovery report:** `gh pr view <pr-number> --json body -q .body | head -30`
   Expected: includes `## Discovery & Decisions`, `### Questions I considered`, `### Assumptions I made — verify these`. Footer: `Refs #4`.
5. **Issue comment posted:** `gh issue view 4 --comments | tail -10`
   Expected: a comment from the bot account containing `Spec PR opened: https://github.com/...`.

- [ ] **Step 4: Re-run the workflow with the same input — verify skip-with-notice**

1. Run again with `issue_number: 4`.
2. Inspect the log: expect `::notice::skipped #4 (branch exists)` and `processed=0 failed=0`.

- [ ] **Step 5: Clean up the test artefacts** (only if you don't want to keep this spec PR)

```bash
gh pr close <pr-number> --delete-branch
```

This deletes the remote branch. Local `spec/issue-4-...` (if any) can be deleted with `git branch -D`.

---

### Task 15: End-to-end smoke test — sweep mode

**Files:** none (manual workflow run + verification)

- [ ] **Step 1: Confirm the current state**

Run: `gh issue list --state open --json number,title -q '.[] | "#\(.number) \(.title)"'`

Expected: list of currently-open issues. Note the count.

Run: `git ls-remote --heads origin "spec/issue-*" | wc -l`

Expected: number of existing spec branches.

- [ ] **Step 2: Trigger the workflow with empty input**

1. Actions → Spec from Issue → Run workflow
2. Branch: `feat/spec-issue-agent`
3. `issue_number`: leave empty
4. Run.

- [ ] **Step 3: Watch the run and verify outcome**

1. The job should process every open issue that does not yet have a spec branch, sequentially.
2. Existing spec branches should be skipped with `::notice::skipped #N (branch exists)`.
3. Final log line: `processed=<X> failed=0` where X = `(open issues) − (already-existing spec branches)`.

- [ ] **Step 4: Spot-check one of the new spec PRs**

Open one of the resulting draft PRs in the browser. Verify:

- Title format: `Spec: <issue title> (#<n>)`
- Body contains the discovery report with all five required subsections
- Body footer: `Refs #<n>` (NOT `Closes #<n>`)
- The committed spec file matches the format of existing files in `docs/superpowers/specs/`
- The source issue has a bot comment linking back to the PR

- [ ] **Step 5: Decide what to do with the generated PRs**

If the run was real (not a test): leave the PRs open for review via `/spec-review`. If the run was just a smoke test: close + delete the unwanted ones with `gh pr close <n> --delete-branch`.

---

### Task 16: Smoke test the `/spec-review` slash command

**Files:** none (manual)

Pick any open spec PR from Task 14 or 15. Run:

```
/spec-review <issue-number>
```

- [ ] **Step 1: Verify the command checks out the right branch**

`git status` should show the spec branch as the active branch.

- [ ] **Step 2: Verify the command loaded context**

The conversation should reference the spec content, the discovery report, and the issue body — not just generic placeholders.

- [ ] **Step 3: Verify brainstorming kicks off**

The brainstorming skill should propose questions about the design. Answer one to confirm the loop works end-to-end.

- [ ] **Step 4: Make a trivial revision and confirm push**

Edit one line in the spec, ask the command to commit + push. Verify with `git log -1 --oneline` and `gh pr view <n> --json commits -q '.commits | length'`.

---

## Phase summary

| Phase | Tasks | What you have at the end |
|---|---|---|
| Test runner | 1 | Empty-but-runnable bash test harness |
| Pure-function libs (TDD) | 2–5 | `slug()` and `parse_response()` covered by 17 passing unit tests |
| Static assets | 6 | `system_prompt.txt` committed |
| Integration libs | 7–9 | `resolve_targets`, `build_bundle`, `run.sh` orchestrator |
| Workflow + docs | 10–12 | Workflow YAML, README, `/spec-review` command |
| Operational rollout | 13 | Branch pushed, secret configured |
| End-to-end verification | 14–16 | Single-issue, sweep, and `/spec-review` proven against the live repo |

After Task 16, the feature is production-ready. Open a PR from
`feat/spec-issue-agent` → `main` for review and merge.
