# Spec-Issue Agent — Design Spec

**Date:** 2026-05-07
**Status:** Draft
**Scope:** New tooling — a GitHub Action that turns open issues in this repo into draft spec PRs by generating a design doc per issue (matching the format of files under `docs/superpowers/specs/`). Plus a thin Claude Code slash command for reviewing and revising the generated specs locally.

## Goal

Reduce the friction of going from "open issue" to "implementable spec" without sacrificing the quality bar of the existing handcrafted specs in this repo. The agent does the bulk of the discovery work in the background; humans review the result before any code is written.

Concretely, on demand the workflow:

1. Picks one issue (or sweeps all open issues without a spec branch).
2. Generates a design doc + a discovery report.
3. Pushes a `spec/issue-N-<slug>` branch with the spec committed under `docs/superpowers/specs/`.
4. Opens a draft PR (discovery report in the body, references but does not close the issue).
5. Comments on the source issue linking to the PR so the conversation stays connected.

A separate Claude Code slash command — `/spec-review N` — pulls the branch locally and runs an interactive brainstorming pass over the draft, allowing decisions to be revised before the PR is merged.

## Non-goals

- **Auto-triggering on issue events.** No `issues.opened` trigger. The author chose `workflow_dispatch` so spec generation stays a deliberate act.
- **Auto-merging or closing issues.** The PR is always draft; the issue stays open. The implementation work — and the eventual close — happens in a separate PR.
- **Free repo exploration during generation.** The agent gets a fixed pinned context bundle. No `Read`/`Bash`-style file walking. The discovery report flags what the agent *didn't* read.
- **Stale-branch / closed-issue cleanup.** If an issue is closed while a spec PR is open, nothing is automated. Manual cleanup; revisit if it becomes a chore.
- **Linking specs back to implementation PRs.** That's a separate convention, not this workflow's job.
- **Refusing thin issues.** Every targeted issue gets a spec PR; thinness surfaces as loud assumption-flagging in the discovery report (see [Thin-issue handling](#thin-issue-handling)).
- **Parallel issue processing.** Even in sweep mode, issues are processed sequentially. With ~6 open issues at any time, parallelism isn't worth the workflow complexity or the rate-limit risk.

## Decisions

### Form factor

- **GitHub Action triggered manually.** `workflow_dispatch` only; no scheduled runs, no event triggers. The author runs it from the Actions tab when ready.
- **One workflow file:** `.github/workflows/spec-issue.yml`.
- **One optional input:** `issue_number` (string). Empty string = sweep mode; non-empty = single-issue mode.
- **Sequential per-issue processing** with a hard skip when a `spec/issue-<N>-*` branch already exists on `origin`.

### Output shape

For each processed issue:

- **Branch:** `spec/issue-N-<slug>` (off the default branch's HEAD at run time).
- **Spec file:** `docs/superpowers/specs/YYYY-MM-DD-issue-N-<slug>-design.md` (`YYYY-MM-DD` = run date).
- **Draft PR:** title `Spec: <issue title> (#N)`, body = the discovery report rendered as Markdown, footer line `Refs #<N>` (intentionally not `Closes #<N>` — the issue stays open until the implementation lands).
- **Issue comment:** `Spec PR opened: <pr_url>` on the source issue.

### Discovery report lives in the PR body, not the repo

The committed spec stays clean — same shape as existing specs in this directory. The "questions raised, options weighed, recommendation, assumptions, files I'd have read" report goes into the draft PR's body so it's visible to the reviewer without polluting `main`. This keeps the spec history grep-friendly post-merge and means the discovery artifact is naturally discarded when the PR is merged or closed.

### Re-run behaviour

- **Skip with notice.** If `spec/issue-N-*` already exists on `origin`, the workflow logs `skipped #N (branch exists)` and moves on — same in both single-issue and sweep modes.
- **No `force` input.** To regenerate, the author manually deletes the remote branch first. Keeps the workflow input surface tiny and prevents an accidental clobber of in-progress review work.

### Context bundle (pinned, no exploration)

The workflow assembles a single fixed payload before each Claude API call. The agent does not read additional files. What's in the bundle:

- **Issue:** title, body, labels — fetched via `gh issue view <N> --json number,title,body,labels`.
- **Repo grounding:** `SPEC.md`, `CLAUDE.md`.
- **Spec-format priors:** the **3 most recent** files in `docs/superpowers/specs/`, used as few-shot examples for tone, section structure, and the kinds of decisions previous specs have already locked in (e.g. clean architecture layers, MVVM ViewModel pattern, dispatcher discipline).
- **Code orientation:** the output of `tree -L 3 app/src/main/java/` (or `find ... -maxdepth 4` if `tree` isn't installed) — directories only, capped at ~200 lines.
- **System prompt:** establishes role and output contract (delimited two-section response).

The motivation for pinning is cost predictability and reproducibility. The trade-off is the agent will sometimes lack code-level detail; the discovery report makes that visible by listing files it would have wanted to read.

### Output contract: two delimited sections in one response

The model returns a single response with two sections separated by sentinel lines. The workflow splits on the sentinels and routes each part to its destination.

```
<<<SPEC>>>
# <Issue Title> — Implementation Design

**Date:** YYYY-MM-DD
**Issue:** #N
**Source:** <issue title>

## What We're Building
...

## Decisions
...

## Architecture / Affected Components
...

## Implementation Notes
...

## Out of Scope
...
<<<END SPEC>>>

<<<DISCOVERY>>>
## Discovery & Decisions

### Questions I considered
- ...

### Options weighed
- **Option A:** ... (pros / cons)
- **Option B:** ...
- ...

### Recommended approach (and why)
...

### Assumptions I made — verify these
- ...

### Files I'd have read with deeper grounding
- e.g. `app/src/.../TimelineScreen.kt` — would confirm exact composable structure
<<<END DISCOVERY>>>
```

Reasons for delimiters over JSON: (a) Markdown inside JSON strings is painful to author and read in raw API output; (b) on parsing failure the raw response is still human-readable in the workflow log / artifact.

### Thin-issue handling

Thin issues (e.g. `#4 Use date picker for all date input/edit fields (incl. Assets)` — single-line title, empty body) still get a full spec PR. The discovery report's **Assumptions I made — verify these** section becomes the safety valve: every undocumented design choice the agent committed to should appear there. The reviewer accepts, edits, or rejects in PR review.

The agent is explicitly instructed *not* to refuse generation, and *not* to comment-bounce a thin issue. Uniform behaviour beats inconsistent gating that depends on the agent's mood.

### Model

**Claude Opus 4.7** (`claude-opus-4-7`). The existing specs in this directory are detailed and decision-rich — Sonnet would save tokens but degrade quality below the bar these specs set. Estimated cost ~$0.50–$1 per issue with the bundle described above; total volume is low (~10–20 runs/month). The model ID is exposed as a workflow env var (`CLAUDE_MODEL`) so it can be swapped without editing the script.

### `/spec-review` slash command

Lives at `.claude/commands/spec-review.md` in this repo. Usage: `/spec-review 5`. Steps:

1. Resolve the spec PR for issue 5: `gh pr list --state open --search "in:title Spec: ... #5" --json number,headRefName,body,url` (or fall back to scanning issue comments for the bot-posted PR link).
2. Fetch and check out the head branch.
3. Load the spec file, the PR body (discovery report), and the original issue body into the conversation.
4. Invoke the `superpowers:brainstorming` skill, framed as: "Here's a draft spec generated by an agent and its discovery report. Walk me through the decisions, surface anything I want to revise."
5. On user-approved revisions: edit the spec file, commit, push to the same branch. If the discovery report needs an update, `gh pr edit <pr-number> --body @<file>`.

This is a thin command — the brainstorming skill does the real work. The slash command just wires up context loading and `gh` plumbing. It's deliberately separate from the Action: the Action is fire-and-forget, review is interactive.

## Architecture / affected components

### New files

```
.github/
└── workflows/
    └── spec-issue.yml                  (NEW — workflow definition)
.github/
└── scripts/
    ├── spec-issue/
    │   ├── run.sh                      (NEW — entrypoint; orchestrates the loop)
    │   ├── resolve_targets.sh          (NEW — sweep vs single-issue resolution)
    │   ├── build_bundle.sh             (NEW — assembles the context bundle)
    │   ├── call_claude.sh              (NEW — POSTs to messages API; parses delimiters)
    │   ├── slug.sh                     (NEW — title → slug)
    │   ├── system_prompt.txt           (NEW — role + output contract for the model)
    │   └── README.md                   (NEW — script documentation)
.claude/
└── commands/
    └── spec-review.md                  (NEW — /spec-review slash command)
docs/
└── superpowers/
    └── specs/
        └── 2026-05-07-spec-issue-agent-design.md   (THIS FILE)
```

### No changes to app code

The Android app source tree is untouched. This feature is repo tooling only.

## Workflow definition

```yaml
# .github/workflows/spec-issue.yml
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
          fetch-depth: 0          # need full history to detect existing spec branches
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Configure git identity
        run: |
          git config user.name  "spec-issue-bot"
          git config user.email "spec-issue-bot@users.noreply.github.com"

      - name: Install jq + tree
        run: sudo apt-get update -qq && sudo apt-get install -y jq tree

      - name: Run spec generator
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          CLAUDE_MODEL: claude-opus-4-7
          ISSUE_NUMBER: ${{ inputs.issue_number }}
        run: bash .github/scripts/spec-issue/run.sh
```

### Why bash + curl, not a TypeScript/Python action

- The orchestration is straightforward: list issues, check branches, build a payload, POST, parse, write file, push, open PR. Bash + `gh` + `jq` + `curl` covers all of it in <300 lines and has zero install step.
- A Python action would need a `requirements.txt` + `pip install` step; a TS action would need a build pipeline. Neither buys anything here.
- If complexity ever justifies a real language, the migration is mechanical — the bash scripts are short and well-bounded.

## Script structure

### `run.sh` — entrypoint

Pseudocode:

```bash
#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/resolve_targets.sh"
source "$(dirname "$0")/build_bundle.sh"
source "$(dirname "$0")/call_claude.sh"
source "$(dirname "$0")/slug.sh"

targets=$(resolve_targets "${ISSUE_NUMBER}")
if [[ -z "$targets" ]]; then
  echo "::notice::no eligible issues — nothing to do"
  exit 0
fi

failed=0
processed=0

for issue_number in $targets; do
  echo "::group::issue #${issue_number}"

  if remote_spec_branch_exists "$issue_number"; then
    echo "::notice::skipped #${issue_number} (branch exists)"
    echo "::endgroup::"
    continue
  fi

  bundle=$(build_bundle "$issue_number") || { failed=$((failed+1)); echo "::endgroup::"; continue; }

  raw_response=$(call_claude "$bundle") || { failed=$((failed+1)); echo "::endgroup::"; continue; }

  if ! parse_response "$raw_response" /tmp/spec.md /tmp/discovery.md; then
    echo "::error::delimiter parsing failed for issue #${issue_number}"
    echo "$raw_response" > "/tmp/raw-${issue_number}.txt"
    failed=$((failed+1))
    echo "::endgroup::"
    continue
  fi

  push_spec_and_pr "$issue_number" /tmp/spec.md /tmp/discovery.md || { failed=$((failed+1)); echo "::endgroup::"; continue; }

  processed=$((processed+1))
  echo "::endgroup::"
done

echo "processed=${processed} failed=${failed}"
[[ $failed -eq 0 ]]
```

The exit code surfaces partial failures: any issue failure marks the workflow run yellow/red even if other issues succeeded.

### `resolve_targets.sh` — single vs sweep

```bash
resolve_targets() {
  local input="$1"
  if [[ -n "$input" ]]; then
    # Single-issue mode: validate it's open
    state=$(gh issue view "$input" --json state -q .state 2>/dev/null || true)
    if [[ "$state" != "OPEN" ]]; then
      echo "::error::issue #${input} is not open (state=${state:-not-found})"
      return 1
    fi
    echo "$input"
    return 0
  fi

  # Sweep mode: every open issue with no spec/issue-<N>-* branch
  open_issues=$(gh issue list --state open --limit 200 --json number -q '.[].number')
  for n in $open_issues; do
    if ! remote_spec_branch_exists "$n"; then
      echo "$n"
    fi
  done
}

remote_spec_branch_exists() {
  local n="$1"
  git ls-remote --heads origin "spec/issue-${n}-*" | grep -q .
}
```

### `build_bundle.sh` — context assembly

Reads the four pinned ingredients (issue JSON, `SPEC.md`, `CLAUDE.md`, last 3 spec files, tree listing) and emits a single JSON object containing the system + user message strings. Uses `jq -Rs` to safely encode each file's content as a JSON string.

```bash
build_bundle() {
  local issue_number="$1"

  local issue_json
  issue_json=$(gh issue view "$issue_number" --json number,title,body,labels)

  local spec_md
  spec_md=$(cat SPEC.md 2>/dev/null || echo "(SPEC.md missing)")

  local claude_md
  claude_md=$(cat CLAUDE.md 2>/dev/null || echo "(CLAUDE.md missing)")

  local recent_specs
  recent_specs=$(ls -t docs/superpowers/specs/*.md 2>/dev/null | head -n 3 || true)

  local tree_listing
  tree_listing=$(tree -L 3 -d app/src/main/java/ 2>/dev/null | head -n 200 || echo "(tree not available)")

  # Assemble messages-API payload (system + user) as JSON.
  # Implementation detail: use jq to construct the JSON safely.
  jq -n \
    --arg system "$(cat .github/scripts/spec-issue/system_prompt.txt)" \
    --arg issue "$issue_json" \
    --arg spec "$spec_md" \
    --arg claude_md "$claude_md" \
    --arg tree "$tree_listing" \
    --arg recent_1 "$(sed -n 1p <<<"$recent_specs" | xargs -I{} cat {} 2>/dev/null || echo)" \
    --arg recent_2 "$(sed -n 2p <<<"$recent_specs" | xargs -I{} cat {} 2>/dev/null || echo)" \
    --arg recent_3 "$(sed -n 3p <<<"$recent_specs" | xargs -I{} cat {} 2>/dev/null || echo)" \
    '{
      model: env.CLAUDE_MODEL,
      max_tokens: 16000,
      system: $system,
      messages: [{
        role: "user",
        content: [
          {type: "text", text: "## Issue\n" + $issue},
          {type: "text", text: "## SPEC.md\n" + $spec},
          {type: "text", text: "## CLAUDE.md\n" + $claude_md},
          {type: "text", text: "## Tree\n" + $tree},
          {type: "text", text: "## Prior specs (most recent first)\n---\n" + $recent_1 + "\n---\n" + $recent_2 + "\n---\n" + $recent_3}
        ]
      }]
    }'
}
```

The `system_prompt.txt` file (committed) holds the role description, output contract (delimiter sentinels, sections required), and tone guidance. Keeping it in a file rather than inline in the script means it's diffable and reviewable on its own.

### `call_claude.sh` — API call + parsing

```bash
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

  # Extract content between <<<SPEC>>> and <<<END SPEC>>>
  echo "$raw" | awk '/<<<SPEC>>>/{flag=1; next} /<<<END SPEC>>>/{flag=0} flag' > "$spec_out"
  echo "$raw" | awk '/<<<DISCOVERY>>>/{flag=1; next} /<<<END DISCOVERY>>>/{flag=0} flag' > "$discovery_out"

  [[ -s "$spec_out" && -s "$discovery_out" ]]
}
```

### `slug.sh` — title → slug

```bash
slug() {
  local title="$1"
  local s
  s=$(echo "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')
  # Cap at 50 chars, trimming back to the last `-` so we don't end mid-word.
  if [[ ${#s} -gt 50 ]]; then
    s="${s:0:50}"
    s="${s%-*}"
  fi
  echo "$s"
}
```

Examples:
- `"Settings: local data backup (manual + scheduled) to user-chosen folder"` → `settings-local-data-backup-manual-scheduled-to` (46 chars; cuts at the `-use` partial word).
- `"Ticker search-as-you-type dropdown (Yahoo Finance-style)"` → `ticker-search-as-you-type-dropdown-yahoo-finance` (48 chars; cuts at the `-s` partial word).

The 50-char cap keeps branch names manageable; the word-boundary trim avoids ugly truncations like `-s`. The issue number prefix disambiguates collisions across issues.

### `push_spec_and_pr` (in `run.sh`)

```bash
push_spec_and_pr() {
  local n="$1"
  local spec_md="$2"
  local discovery_md="$3"

  local title slug_str branch spec_path date today_date
  title=$(gh issue view "$n" --json title -q .title)
  slug_str=$(slug "$title")
  branch="spec/issue-${n}-${slug_str}"
  today_date=$(date -u +%Y-%m-%d)
  spec_path="docs/superpowers/specs/${today_date}-issue-${n}-${slug_str}-design.md"

  git checkout -b "$branch"
  mkdir -p "$(dirname "$spec_path")"
  cp "$spec_md" "$spec_path"
  git add "$spec_path"
  git commit -m "spec: draft for issue #${n}"
  git push -u origin "$branch"

  local pr_body
  pr_body=$(printf '%s\n\nRefs #%s\n\n---\n*Generated by spec-issue workflow. Review before merge.*\n' \
                  "$(cat "$discovery_md")" "$n")

  pr_url=$(gh pr create \
    --draft \
    --title "Spec: ${title} (#${n})" \
    --body "$pr_body" \
    --base "$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)" \
    | tail -n 1)

  gh issue comment "$n" --body "Spec PR opened: ${pr_url}" \
    || echo "::warning::issue comment failed for #${n}; PR is the canonical artifact"
}
```

## System prompt (`system_prompt.txt`)

```
You are an experienced software designer writing an implementation design doc for an
existing Android personal-finance app called SpendTrack. The codebase already has
established conventions and prior specs you can use as guides. You will be given:

- The full text of one open GitHub issue (title + body + labels)
- The repo's top-level SPEC.md and CLAUDE.md
- The three most recent design docs from docs/superpowers/specs/ as format examples
- A directory tree listing of the source tree (no file contents)

You CANNOT read additional files. If you would benefit from reading specific files,
list them in the "Files I'd have read" section of the discovery report.

Output a single response with EXACTLY two sections, delimited by sentinel lines:

<<<SPEC>>>
... committed design doc, matching the format and tone of the example specs.
The first lines must include `**Date:**`, `**Issue:**`, and `**Source:**` fields.
<<<END SPEC>>>

<<<DISCOVERY>>>
## Discovery & Decisions
... including the five required subsections:
  - Questions I considered
  - Options weighed
  - Recommended approach (and why)
  - Assumptions I made — verify these
  - Files I'd have read with deeper grounding
<<<END DISCOVERY>>>

Rules:
- Always produce both sections, even for thin or vague issues. Make assumptions
  visible in the assumptions list rather than refusing.
- Match the architecture, layering, and dispatcher conventions established in
  CLAUDE.md and the example specs. Don't invent new patterns where existing
  ones apply.
- Keep the spec implementation-ready: concrete enough that a developer could
  start work, but not so prescriptive that obvious code-level details are
  guessed beyond the bundle's grounding.
- Cite specific files only if they appear in the tree listing or example specs.
  Don't invent file paths.
```

## Validation & error handling

| Scenario | Behaviour |
|---|---|
| Single-issue mode, issue not found / not open | Workflow fails fast with a clear `::error::` line; no branch / PR created. |
| Sweep mode, zero eligible issues | Workflow exits 0 with `::notice::no eligible issues — nothing to do`. |
| `spec/issue-N-*` already exists on origin | Skip with notice, continue (sweep) or exit cleanly (single). |
| Anthropic API non-2xx / network error | Log `::error::`, increment failure counter, continue to next issue. |
| Delimiter parsing failure | Log `::error::`, dump raw response to `/tmp/raw-<N>.txt` as a workflow artifact, continue. |
| Empty SPEC or DISCOVERY section after parse | Same as parsing failure (treated as malformed). |
| `gh pr create` fails (permissions, rate limit) | Log `::error::`, branch is still pushed; manual PR creation possible. |
| `gh issue comment` fails | Log `::warning::`, do not fail the run — the PR is the canonical artifact. |
| Issue closed mid-sweep | No special handling; spec is generated as if open. Cheap to delete the resulting branch / PR if unwanted. |
| Two issues sluggify to the same branch suffix | The issue number prefix (`spec/issue-5-foo` vs `spec/issue-7-foo`) keeps them distinct. |
| `tree` not installed on the runner | `apt-get install tree` is part of the workflow setup; if it ever drops out, fall back to `find -maxdepth`. |

The workflow's overall exit code is the count of failed issues (`exit ${failed}` truncated to 0/1). A run with one success and one failure surfaces as failed in the Actions UI, prompting investigation.

## Concrete artefact examples

### Branch naming

For issue `#5 Ticker search-as-you-type dropdown (Yahoo Finance-style)` run on 2026-05-07:

- Branch: `spec/issue-5-ticker-search-as-you-type-dropdown-yahoo-finance`
- File:   `docs/superpowers/specs/2026-05-07-issue-5-ticker-search-as-you-type-dropdown-yahoo-finance-design.md`
- PR title: `Spec: Ticker search-as-you-type dropdown (Yahoo Finance-style) (#5)`
- PR body footer: `Refs #5`
- Issue comment: `Spec PR opened: https://github.com/pmpbatista/spendtrack/pull/<n>`

### Workflow inputs

| Input | Value | Behaviour |
|---|---|---|
| `issue_number` | `5` | Process only issue #5; fail-fast if it's closed or missing. |
| `issue_number` | (empty) | Sweep all open issues without a `spec/issue-*` branch. |

## `/spec-review` slash command

### Location

`.claude/commands/spec-review.md`

### Behaviour

```
---
description: Review and revise a draft spec PR generated by the spec-issue workflow
---

You are reviewing a draft spec PR generated by .github/workflows/spec-issue.yml
for issue #$ARGUMENTS.

1. Locate the PR:
   gh pr list --search "in:title Spec: #$ARGUMENTS" --state open \
     --json number,headRefName,body,url

   If multiple match, prefer the most recent. If none match, scan
   `gh issue view $ARGUMENTS --comments` for the bot comment containing
   "Spec PR opened:" and follow the link.

2. Fetch and check out the head branch.

3. Read into context:
   - The spec file at docs/superpowers/specs/*-issue-$ARGUMENTS-*-design.md
   - The PR body (gh pr view <pr-number> --json body)
   - The original issue body (gh issue view $ARGUMENTS --json body)

4. Invoke the superpowers:brainstorming skill, framed as: "Here's a draft spec
   generated by the spec-issue agent. Walk me through the decisions, surface
   anything I want to revise. The committed spec is X; the agent's discovery
   report (questions, options, assumptions) is Y."

5. On user-approved revisions:
   - Edit the spec file
   - git add + git commit -m "spec: revise <area>"
   - git push origin HEAD
   - If the discovery report itself needs an update, write the revised
     content to a tmp file and run: gh pr edit <pr-number> --body-file <tmpfile>
```

### Why this is a separate command rather than another workflow

- The Action is fire-and-forget; review needs interactive dialogue.
- The brainstorming skill already does the right thing — the slash command is
  just glue (locate PR, check out, load context, hand to brainstorming).
- Running the brainstorming pass locally means the author can `/clear` and
  iterate without consuming Action minutes.

## Out of scope (deferred)

- **Auto-trigger on `issues.opened`.** Re-evaluate if the manual cadence ever
  becomes a chore. Would require adding a `gating` step (only certain labels
  trigger spec generation) to avoid noisy issues spawning bad specs.
- **Auto-clean-up of stale spec branches.** A nightly workflow that closes
  spec PRs whose source issue is closed, or whose branch has been merged into
  another. Cheap to add if it becomes annoying.
- **Linking specs back to implementation PRs.** Convention only: implementation
  PRs cite `Spec: docs/superpowers/specs/...` in their body. Tooling could
  enforce this with a PR template, separately from this spec.
- **Token-cost telemetry.** Log per-issue input/output tokens to the workflow
  summary; cheap to add later if cost ever matters.
- **Smarter context selection.** A future v2 could let the agent declare a
  short list of additional files to load (the "two-pass" approach we deferred
  in favour of pinned bundles). Defer until pinned-bundle quality proves
  insufficient in practice.
- **Generic skills for non-spendtrack repos.** This workflow is repo-specific
  (CLAUDE.md path, spec directory layout, system prompt tone). Generalising it
  is a separate effort.

## Open follow-ups (for later specs)

- A pre-flight `lint` step that validates the generated spec's structure
  (presence of required headings, frontmatter fields) before pushing the PR,
  to catch obviously-malformed agent output earlier than human review.
- A small `scripts/list-specs-without-impl.sh` helper that lists merged spec
  PRs whose linked issue is still open, so it's easy to see what's queued
  for implementation.
