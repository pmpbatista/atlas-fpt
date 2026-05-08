# Spec-Issue Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the previously-built GitHub Action approach with three Claude Code slash commands (`/spec-issue`, `/spec-issues`, `/spec-review`) that produce identical artefacts (branch + spec file + draft PR + issue comment) without requiring an `ANTHROPIC_API_KEY` repo secret.

**Architecture:** Each slash command is a Markdown file in `.claude/commands/` with YAML frontmatter (`description`, `argument-hint`) and a step-by-step body that Claude follows when the command is invoked. No bash, no helper scripts, no API calls — Claude uses its native tools (Bash, Read, Grep, Edit, Write, Task) to do the work in the user's own session.

**Tech Stack:** Claude Code slash commands (Markdown + YAML frontmatter), `gh` CLI, `git`. The existing repo's tooling — no new dependencies.

---

## Reference

Spec: `docs/superpowers/specs/2026-05-08-spec-issue-commands-design.md`. Read the **Decisions** and **Slash command structure** sections before starting; they lock in non-obvious choices (free file exploration, sequential sweep with confirmation, default-branch-cleanup discipline, refusal on dirty working tree).

The *previous* spec/plan (`2026-05-07-spec-issue-agent-design.md` / `2026-05-07-spec-issue-agent.md`) are committed but marked superseded — do not implement them.

## File Map

| File | Change |
|---|---|
| `.github/workflows/spec-issue.yml` | **Delete** |
| `.github/scripts/spec-issue/` (whole tree, 10 files) | **Delete** |
| `.claude/commands/spec-issue.md` | **Create** — single-issue spec generation |
| `.claude/commands/spec-issues.md` | **Create** — sweep |
| `.claude/commands/spec-review.md` | **Modify** — two text edits (frontmatter description + opening sentence) |

No app source code is touched. This is repo tooling only.

## Branch strategy

All work continues on `feat/spec-issue-agent` (already pushed; PR #10 already open). The deletion + new-command commits land on top of the existing history. The PR title and description get refreshed at the end (Task 5). This preserves the honest narrative: built the workflow approach, pivoted, replaced with slash commands.

---

### Task 1: Delete the obsolete GitHub Action and bash helpers

**Files:**
- Delete: `.github/workflows/spec-issue.yml`
- Delete: `.github/scripts/spec-issue/` (whole tree)

This is a clean removal — the entire previous implementation goes away in a single commit, leaving only the spec, plan, and review command behind.

- [ ] **Step 1: Confirm what's getting deleted**

Run:
```bash
ls -la .github/workflows/spec-issue.yml
ls -la .github/scripts/spec-issue/
```

Expected: both paths exist. If either is missing, stop and report — the prior implementation should still be in place.

- [ ] **Step 2: Delete the files**

Run:
```bash
git rm .github/workflows/spec-issue.yml
git rm -r .github/scripts/spec-issue/
```

- [ ] **Step 3: Verify the staging area**

Run: `git status --short`

Expected: only deletions of `.github/workflows/spec-issue.yml` and the entire `.github/scripts/spec-issue/` tree. No other files modified or staged.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: remove GitHub Action approach (superseded by slash commands)"
```

- [ ] **Step 5: Verify the working tree is clean**

Run: `git status`

Expected: `nothing to commit, working tree clean` on `feat/spec-issue-agent`.

---

### Task 2: Write `/spec-issue` (single-issue command)

**Files:**
- Create: `.claude/commands/spec-issue.md`

This is the largest task — the command's body is ~200 lines of step-by-step instructions Claude follows when invoked. Every step must be explicit; vague instructions like "do the right thing" produce inconsistent results across runs.

- [ ] **Step 1: Confirm the directory exists**

Run: `ls -la .claude/commands/`

Expected: directory exists; `spec-review.md` is already inside it. If the directory is missing, run `mkdir -p .claude/commands` first.

- [ ] **Step 2: Create the file**

Write the following content verbatim to `.claude/commands/spec-issue.md`:

````markdown
---
description: Generate a draft spec PR for a single open issue (creates branch + spec file + draft PR + issue comment).
argument-hint: <issue-number>
---

You are generating a draft spec PR for issue #$ARGUMENTS in this repo. Follow these steps in order. Stop and report any failure clearly — do not silently skip steps.

## 1. Validate input

`$ARGUMENTS` must be a positive integer. If empty or non-numeric, stop and tell the user: "Usage: `/spec-issue <issue-number>`."

## 2. Pre-flight checks

- Run `gh issue view $ARGUMENTS --json state,title,body,labels,number`. Capture the JSON for later steps.
- If `.state` is not `OPEN`, stop: "Issue #$ARGUMENTS is not open (state=<state>). Refusing to generate a spec for a closed issue."
- Run `git fetch origin --quiet`.
- Run `git ls-remote --heads origin "spec/issue-$ARGUMENTS-*"`. If the output is non-empty, stop: "A spec branch for issue #$ARGUMENTS already exists: <branch-name>. Delete it first if you want to regenerate."

## 3. Confirm a clean working tree

- Run `git status --porcelain`. If output is non-empty, stop: "Working tree has uncommitted changes; commit or stash before running /spec-issue."
- Resolve the default branch: `gh repo view --json defaultBranchRef -q .defaultBranchRef.name`. Capture as `default_branch`.
- Run `git checkout "$default_branch"`.
- Run `git pull --ff-only origin "$default_branch"`. If this fails (diverged history), stop and tell the user to reconcile manually.

## 4. Read context — priors (always)

Read these files in full:

- `SPEC.md`
- `CLAUDE.md`
- The 3 most recent files in `docs/superpowers/specs/*-design.md`, excluding any whose first ~10 lines contain `Superseded` (those are dead). Use the file mtime to order: `ls -t docs/superpowers/specs/*-design.md`.

These establish the spec format, repo conventions, and architectural priors.

## 5. Read context — relevant source files

Based on the issue title and body, identify source files clearly relevant to the design and read them. Examples:

- Issue mentions a screen ("Timeline", "Overview") → read the corresponding `app/src/main/java/com/spendtrack/ui/feature/<feature>/` files.
- Issue mentions data layer ("backup", "import") → read `app/src/main/java/com/spendtrack/data/` files.
- Issue is conceptual / cross-cutting ("Settings: ...") → may not need any source-file reads beyond the priors.

**Maintain a list of files you read in this step.** You'll cite it in the discovery report's "Files I read" subsection. Don't list the priors from step 4 — only the issue-specific exploration.

If a file path you'd like to read doesn't exist, note it in your assumptions list rather than fabricating its contents.

## 6. Generate the slug

From the issue title, produce a branch-safe slug:

1. Lowercase the title.
2. Replace any run of non-alphanumeric characters with a single `-`.
3. Strip leading/trailing dashes.
4. If the result is longer than 50 characters: take the first 50, then trim back to the last `-` (so the slug ends on a word boundary, never mid-word). If there's no `-` in the first 50 (one giant word), keep the 50 as-is.

Examples:
- `"Settings: local data backup (manual + scheduled) to user-chosen folder"` → `settings-local-data-backup-manual-scheduled-to`
- `"Ticker search-as-you-type dropdown (Yahoo Finance-style)"` → `ticker-search-as-you-type-dropdown-yahoo-finance`
- `"Use date picker for all date input/edit fields (incl. Assets)"` → `use-date-picker-for-all-date-input-edit-fields`
- `"Fix bug"` → `fix-bug`

Capture the slug as `slug`.

## 7. Create the spec branch

- Branch name: `spec/issue-$ARGUMENTS-$slug`. Capture as `branch`.
- Today's date (UTC): run `date -u +%Y-%m-%d`. Capture as `today`.
- Spec file path: `docs/superpowers/specs/$today-issue-$ARGUMENTS-$slug-design.md`. Capture as `spec_path`.
- Run `git checkout -b "$branch"`.

## 8. Design and write the spec file

Design the spec for this issue. The committed spec should NOT contain the discovery report — that goes in the PR body separately.

Match the format and tone of the prior specs you read in step 4. The spec must include, in this order:

- Title heading: `# <Issue Title> — Implementation Design` (or `Design Spec` — match the convention in the most recent prior spec)
- Frontmatter fields, each on its own line, in this order:
  - `**Date:** <today>`
  - `**Issue:** #$ARGUMENTS`
  - `**Source:** <issue title>`
- Body sections, scaled to the issue's complexity. Common sections (use what fits, follow the prior specs' structure):
  - `## Goal` or `## What We're Building`
  - `## Non-goals` (if applicable)
  - `## Decisions`
  - `## Architecture` or `## Affected Components`
  - `## Implementation Notes` or analogous
  - `## Out of Scope` (if applicable)

Use the Write tool to create the file at `$spec_path`.

## 9. Commit the spec file and push the branch

- `git add "$spec_path"`
- `git commit -m "spec: draft for issue #$ARGUMENTS"`
- `git push -u origin "$branch"`

If push fails (auth, network), stop and report:
> "Spec is committed locally on `$branch` but push failed. Fix the underlying issue and run `git push -u origin $branch` manually."
> Then `git checkout "$default_branch"` and exit.

## 10. Construct the discovery report

Build the PR body markdown in memory (or in a temp file):

```markdown
## Discovery & Decisions

### Questions I considered

- <question 1>
- <question 2>
- ...

### Options weighed

- **Option A:** ... (pros / cons)
- **Option B:** ... (pros / cons)
- ...

### Recommended approach (and why)

<short narrative — 2-5 sentences>

### Assumptions I made — verify these

- <assumption 1>
- <assumption 2>
- ...

### Files I read

- `<path>` — <one-line note on what it confirmed>
- ...

---

Refs #$ARGUMENTS

*Generated by /spec-issue. Review before merge.*
```

Every subsection must have at least one bullet, even on a thin issue. Thin issues should have a longer "Assumptions I made — verify these" list. The "Files I read" subsection lists ONLY the issue-specific files from step 5 (not priors).

## 11. Open the draft PR

- Write the discovery body to a temp file: `tmp_pr_body=$(mktemp)` then write the markdown to it.
- `gh pr create --draft --title "Spec: <issue-title> (#$ARGUMENTS)" --body-file "$tmp_pr_body" --base "$default_branch"`. Capture the printed URL as `pr_url` (use `tail -n 1` if needed).
- `rm -f "$tmp_pr_body"`.

If `gh pr create` fails:
> "Branch `$branch` is pushed but PR creation failed. Open it manually: `gh pr create --draft --title \"Spec: <title> (#$ARGUMENTS)\" --body-file <file>`. Then `git checkout $default_branch`."

## 12. Comment on the source issue

Run `gh issue comment $ARGUMENTS --body "Spec PR opened: $pr_url"`.

If this fails, **log a warning but do not fail the command** — the PR is the canonical artefact:
> "::warning::Could not comment on issue #$ARGUMENTS (the PR is still the source of truth)."

## 13. Return to the default branch

Run `git checkout "$default_branch"`. Run this even if any earlier step failed after step 7 — never leave the user on the spec branch unexpectedly.

## 14. Report success

Print to the user:

```
Spec PR opened: <pr_url>
  Issue:   #$ARGUMENTS — <issue-title>
  Branch:  <branch>
  File:    <spec_path>

Next: /spec-review $ARGUMENTS to walk through the design and revise.
```
````

- [ ] **Step 3: Sanity-check the file structure**

Run:
```bash
head -5 .claude/commands/spec-issue.md
grep -c '^## ' .claude/commands/spec-issue.md
test -f .claude/commands/spec-issue.md && wc -l .claude/commands/spec-issue.md
```

Expected:
- `head -5` shows the YAML frontmatter starting with `---` and the `description:` / `argument-hint:` fields.
- `grep -c '^## '` returns 14 (one per numbered step).
- `wc -l` reports a line count in the 200-220 range.

- [ ] **Step 4: Commit**

```bash
git add .claude/commands/spec-issue.md
git commit -m "feat: add /spec-issue slash command"
```

---

### Task 3: Write `/spec-issues` (sweep command)

**Files:**
- Create: `.claude/commands/spec-issues.md`

The sweep command is shorter — it doesn't repeat `/spec-issue`'s logic, it dispatches per-issue subagents.

- [ ] **Step 1: Create the file**

Write the following content verbatim to `.claude/commands/spec-issues.md`:

````markdown
---
description: Sweep open issues without a spec branch and generate a draft spec PR per eligible issue.
---

You are sweeping open GitHub issues and generating draft spec PRs for those without an existing `spec/issue-*` branch on origin. Each eligible issue runs as a fresh subagent (sequentially — `git checkout -b` collisions if parallel).

## 1. List open issues

Run `gh issue list --state open --limit 200 --json number,title`. Capture the JSON.

If the command fails (auth, network), stop and surface the error: "gh issue list failed — check `gh auth status` and your network."

## 2. Build the existing-branches set

- Run `git fetch origin --quiet`.
- Run `git ls-remote --heads origin "spec/issue-*"`. Parse each line; the ref name is `refs/heads/spec/issue-N-...`. Extract the integer `N` from each. Build a set of those numbers.

## 3. Compute the eligible list

For each open issue, exclude it if its `.number` is in the existing-branches set.

If the eligible list is empty, print:
```
No eligible issues — every open issue already has a spec branch.
```
and exit cleanly.

## 4. Display the eligible list and confirm

Print:

```
Eligible issues (X):
  #N1 <title 1>
  #N2 <title 2>
  ...

About to dispatch a subagent per issue, sequentially. Each will create a draft spec PR. Proceed? (yes/no)
```

Wait for the user's response. Only continue on an explicit affirmative answer (`yes`, `y`). Any other response: stop and exit cleanly without dispatching.

## 5. Dispatch sequentially

For each eligible issue, in numerical order:

- Use the Task tool to dispatch a `general-purpose` subagent.
- Description: `Generate spec PR for issue #N`.
- Prompt: tell the subagent to follow the `/spec-issue <N>` slash command's instructions (paste the body of `.claude/commands/spec-issue.md` into the subagent prompt, with `$ARGUMENTS` replaced by the actual issue number). The subagent must do all the same checks (issue open, branch doesn't exist, working tree clean), all the reads, the design, the branch + commit + push, the PR open, the issue comment, and the return-to-default-branch.
- Wait for the subagent to complete before dispatching the next.
- Capture each result: success (with PR URL) or failure (with one-line reason from the subagent's report).

**Do NOT parallelize.** Even though subagents run in isolated context, they all share the user's working tree, and `git checkout -b` collisions would corrupt the run.

## 6. Report rollup

After all subagents complete (or the user interrupts):

```
Sweep complete: processed=X failed=Y

Successes:
  #N1 → <pr-url>
  #N2 → <pr-url>
  ...

Failures:
  #N3 → <one-line reason>
  ...
```

Then run `git status` and confirm the working tree is on the default branch with no uncommitted changes — sanity check, not enforce. If it isn't (a subagent left it dirty), surface that prominently in the rollup so the user can recover.
````

- [ ] **Step 2: Sanity-check**

Run:
```bash
head -5 .claude/commands/spec-issues.md
grep -c '^## ' .claude/commands/spec-issues.md
```

Expected:
- Frontmatter present with `description:`.
- Six top-level sections (`## 1.` through `## 6.`).

- [ ] **Step 3: Commit**

```bash
git add .claude/commands/spec-issues.md
git commit -m "feat: add /spec-issues sweep command"
```

---

### Task 4: Update `/spec-review` to reflect the new generation source

**Files:**
- Modify: `.claude/commands/spec-review.md`

Two text edits only — the command's behaviour doesn't change.

- [ ] **Step 1: Update the frontmatter description**

The current line is:
```
description: Review and revise a draft spec PR generated by the spec-issue workflow.
```

Replace it with:
```
description: Review and revise a draft spec PR generated by /spec-issue or /spec-issues.
```

Use Edit with `old_string` set to the full current line and `new_string` set to the replacement.

- [ ] **Step 2: Update the body's opening sentence**

The current opening line of the body is:
```
You are reviewing a draft spec PR generated by `.github/workflows/spec-issue.yml`
for issue #$ARGUMENTS.
```

Replace it with:
```
You are reviewing a draft spec PR generated by `/spec-issue` or `/spec-issues`
for issue #$ARGUMENTS.
```

Use Edit with `old_string` set to the full two-line block (preserving the newline) and `new_string` set to the replacement two-line block.

- [ ] **Step 3: Verify the file**

Run:
```bash
grep -n "spec-issue" .claude/commands/spec-review.md
```

Expected: matches reference `/spec-issue` or `/spec-issues` only — no remaining mentions of `.github/workflows/spec-issue.yml` or `spec-issue workflow`.

- [ ] **Step 4: Commit**

```bash
git add .claude/commands/spec-review.md
git commit -m "docs: update /spec-review references for slash-command pivot"
```

---

### Task 5: Push branch and update PR description

**Files:** none (git + GitHub operations only)

- [ ] **Step 1: Verify the local branch state**

Run:
```bash
git log --oneline origin/main..HEAD
git status
```

Expected:
- Multiple commits on top of `origin/main`, ending with the four new commits from this plan (delete, spec-issue, spec-issues, spec-review-edit).
- Working tree clean.

- [ ] **Step 2: Push the branch**

Run: `git push origin feat/spec-issue-agent`

Expected: success (no scope error since `workflow` scope is no longer needed — we just deleted the only workflow file).

- [ ] **Step 3: Update the existing PR description**

PR #10 was opened with the previous (workflow-based) description. Update it:

```bash
gh pr edit 10 --title "Spec-issue commands: turn open issues into draft spec PRs (locally)" --body "$(cat <<'EOF'
## Summary

Three Claude Code slash commands that turn open GitHub issues into draft spec PRs locally — no GitHub Action, no `ANTHROPIC_API_KEY` repo secret. Replaces an earlier workflow-based design that was dropped over secret-management concerns.

- `/spec-issue <N>` — generate one draft spec PR for the given issue.
- `/spec-issues` — sweep all open issues without a spec branch; dispatches one subagent per eligible issue (sequential), with a confirmation prompt before kicking off.
- `/spec-review <N>` — already existed; updated to reference the new generation source.

Per-issue output (unchanged from the original design): branch \`spec/issue-N-<slug>\`, committed spec file under \`docs/superpowers/specs/\`, draft PR titled \`Spec: <title> (#N)\` with the discovery report in the body and footer \`Refs #N\`, comment on the source issue linking to the PR.

Specs:
- Current design: \`docs/superpowers/specs/2026-05-08-spec-issue-commands-design.md\`
- Original (superseded): \`docs/superpowers/specs/2026-05-07-spec-issue-agent-design.md\`

## What's in the diff

- Deleted: \`.github/workflows/spec-issue.yml\`, \`.github/scripts/spec-issue/\` (whole tree).
- Added: \`.claude/commands/spec-issue.md\`, \`.claude/commands/spec-issues.md\`.
- Modified: \`.claude/commands/spec-review.md\` (frontmatter description + opening sentence text edits).
- Added: spec doc + plan for the new design; superseded headers on the original spec + plan.

## Test plan

Local sanity check (no live API call needed):

- [ ] \`gh issue list --state open\` returns the current issue list.
- [ ] \`git ls-remote --heads origin "spec/issue-*"\` returns nothing yet.
- [ ] \`/spec-issue 4\` (the thinnest open issue) — should create a branch + spec file + draft PR + issue comment, then return to \`main\` cleanly. Verify discovery report has all five subsections and footer \`Refs #4\`.
- [ ] Re-run \`/spec-issue 4\` — should refuse with the existing branch name.
- [ ] \`/spec-issues\` — should list eligible issues, ask for confirmation, then process them sequentially. Spot-check one resulting PR.
- [ ] \`/spec-review N\` against any open spec PR — should check out the branch, load context, and invoke brainstorming.
EOF
)"
```

Confirm the new title and body landed:
```bash
gh pr view 10 --json title,body,state -q '.title, .state, (.body | length)'
```

Expected: title matches the new one; state is `OPEN`; body length is non-zero (~1000+ chars).

---

### Task 6: Smoke test (user-driven)

**Files:** none — manual.

After the previous tasks land, the user runs the commands locally and verifies behaviour. This task is manual because slash commands need a fresh Claude Code session to invoke.

- [ ] **Step 1: Single-issue mode against issue #4**

Open a fresh Claude Code session in this repo. Run: `/spec-issue 4`.

Verify:
- Pre-flight checks run (issue open, no existing branch, working tree clean).
- Priors are read: SPEC.md, CLAUDE.md, recent specs.
- A branch `spec/issue-4-use-date-picker-for-all-date-input-edit-fields` is created.
- Spec file at `docs/superpowers/specs/<today>-issue-4-use-date-picker-for-all-date-input-edit-fields-design.md`.
- Draft PR is opened with the discovery report.
- Issue #4 has a new comment linking to the PR.
- Final state: on `main` with a clean working tree.

- [ ] **Step 2: Re-run for the same issue**

Same fresh session, run `/spec-issue 4` again.

Verify: refuses with the existing branch name in the message. No state change.

- [ ] **Step 3: Sweep mode**

Fresh session, run `/spec-issues`.

Verify:
- Lists eligible issues (excluding #4 from step 1, plus any others already with branches).
- Asks for confirmation before dispatching.
- On `yes`, processes each issue sequentially via subagents.
- Final rollup reports each PR URL.
- Working tree on `main` and clean at the end.

- [ ] **Step 4: `/spec-review` on a generated PR**

Fresh session, run `/spec-review N` for any of the open spec PRs.

Verify:
- Locates the PR.
- Checks out the branch.
- Loads spec + discovery report + issue body into context.
- Invokes the brainstorming skill — Claude should ask probing questions about the design rather than passively summarising.

- [ ] **Step 5: Decide on the spec PRs created during smoke testing**

If the generated specs are good, leave them open for review. If they're test artefacts you don't want, close + delete:
```bash
gh pr close <pr-number> --delete-branch
```

---

## Phase summary

| Phase | Tasks | What you have at the end |
|---|---|---|
| Cleanup | 1 | Workflow + bash helper tree gone, working tree clean |
| New commands | 2–4 | Three slash commands in `.claude/commands/` |
| Operational | 5 | Branch pushed, PR #10 description updated |
| Verification | 6 | Smoke-tested end-to-end against live issues |

After Task 6, the feature is ready to merge. Mark PR #10 ready-for-review and merge when satisfied.
