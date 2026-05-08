# Plan-Issue Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three Claude Code slash commands (`/plan-issue`, `/plan-issues`, `/plan-review`) that turn an open spec PR into a spec+plan PR — adding an implementation plan inline as a new commit on the existing spec branch and updating the PR title to `Spec + Plan: ...`.

**Architecture:** Each command is a Markdown file in `.claude/commands/` with YAML frontmatter (`description`, `argument-hint`) and a step-by-step body Claude follows when invoked. No bash helpers, no API calls — Claude uses native tools (Bash, Read, Write, Edit, Task) in the user's own session. Mirrors the proven shape of `/spec-issue`, `/spec-issues`, `/spec-review`.

**Tech Stack:** Claude Code slash commands (Markdown + YAML frontmatter), `gh` CLI, `git`. No new dependencies.

---

## Reference

Spec: `docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md`. Read the **Decisions** and **Slash command structure** sections before starting; they lock in non-obvious choices (inline branch model, slug extraction from head branch, PR body append-only update, refusal on dirty working tree, default-branch return discipline, sequential sweep with confirmation).

The spec-issue companion design (`docs/superpowers/specs/2026-05-08-spec-issue-commands-design.md`) and its plan (`docs/superpowers/plans/2026-05-08-spec-issue-commands.md`) are useful prior art — the plan-issue commands deliberately match their structure.

## File Map

| File | Change |
|---|---|
| `docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md` | **Created** (already on disk; commit in Task 1) |
| `docs/superpowers/plans/2026-05-08-plan-issue-commands.md` | **Created** (this file; commit in Task 1) |
| `.claude/commands/plan-issue.md` | **Create** — single-issue plan command |
| `.claude/commands/plan-issues.md` | **Create** — sweep |
| `.claude/commands/plan-review.md` | **Create** — review |

No app source code is touched. This is repo tooling only.

## Branch strategy

Create a new feature branch `feat/plan-issue-commands` off `main`. The work lands as separate commits per command + a setup commit for the design docs. PR is opened as draft at the end and the user marks it ready when satisfied.

---

### Task 1: Branch setup + commit design docs

**Files:**
- Add: `docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md`
- Add: `docs/superpowers/plans/2026-05-08-plan-issue-commands.md`

The spec was written during brainstorming and the plan was written by writing-plans. Both are on disk untracked. This task creates the branch and commits them together so the rest of the work has a stable base.

- [ ] **Step 1: Confirm a clean working tree on `main`**

Run:
```bash
git status --porcelain
git branch --show-current
```

Expected:
- Working tree shows only the two untracked files (the spec and plan docs).
- Current branch is `main`.

If the working tree has unrelated changes, commit/stash them first or stop and ask the user.

- [ ] **Step 2: Pull latest `main`**

Run:
```bash
git fetch origin --quiet
git pull --ff-only origin main
```

Expected: `Already up to date.` or fast-forward.

- [ ] **Step 3: Create and switch to the feature branch**

Run:
```bash
git checkout -b feat/plan-issue-commands
```

Expected: `Switched to a new branch 'feat/plan-issue-commands'`.

- [ ] **Step 4: Confirm both design docs exist on disk**

Run:
```bash
ls -l docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md
ls -l docs/superpowers/plans/2026-05-08-plan-issue-commands.md
```

Expected: both files exist with non-zero size. If either is missing, stop — the brainstorming/writing-plans steps were skipped or the files were lost.

- [ ] **Step 5: Stage and commit**

Run:
```bash
git add docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md \
        docs/superpowers/plans/2026-05-08-plan-issue-commands.md
git commit -m "docs: spec + plan for plan-issue commands"
```

- [ ] **Step 6: Verify the commit landed**

Run:
```bash
git log --oneline -1
git status --porcelain
```

Expected:
- Last commit is `docs: spec + plan for plan-issue commands`.
- Working tree is clean.

---

### Task 2: Write `/plan-issue` command

**Files:**
- Create: `.claude/commands/plan-issue.md`

This is the largest task — the command's body is ~200 lines of step-by-step instructions Claude follows when invoked. Every step must be explicit; vague instructions produce inconsistent results across runs.

- [ ] **Step 1: Confirm the directory exists**

Run:
```bash
ls -la .claude/commands/
```

Expected: directory exists; `spec-issue.md`, `spec-issues.md`, `spec-review.md` are already inside. If the directory is missing, run `mkdir -p .claude/commands` first.

- [ ] **Step 2: Create the file**

Write the following content verbatim to `.claude/commands/plan-issue.md`:

````markdown
---
description: Add an implementation plan as a commit on the open spec branch for an issue (updates the spec PR title to "Spec + Plan: ...").
argument-hint: <issue-number>
---

You are adding an implementation plan to the open spec PR for issue #$ARGUMENTS in this repo. Follow these steps in order. Stop and report any failure clearly — do not silently skip steps.

## 1. Validate input

`$ARGUMENTS` must be a positive integer. If empty or non-numeric, stop and tell the user: "Usage: `/plan-issue <issue-number>`."

## 2. Pre-flight checks — issue and spec PR

- Run `gh issue view $ARGUMENTS --json state,title,body,labels,number`. Capture the JSON; extract `.title` as `issue_title` and `.state` as `issue_state`.
- If `issue_state` is not `OPEN`, stop: "Issue #$ARGUMENTS is not open (state=<state>). Refusing to add a plan."
- Run `gh pr list --state open --search "head:spec/issue-$ARGUMENTS-" --json number,headRefName,title,body,url --limit 10`. Capture the JSON.
- If the result is an empty array, stop: "No open spec PR found for issue #$ARGUMENTS — run `/spec-issue $ARGUMENTS` first."
- If the result has more than one entry, stop: "Found multiple open spec PRs for issue #$ARGUMENTS — resolve manually before running /plan-issue."
- Capture from the single match: `pr_number` (`.number`), `head_branch` (`.headRefName`), `pr_title` (`.title`), `pr_body` (`.body`), `pr_url` (`.url`).

## 3. Confirm a clean working tree

- Run `git status --porcelain`. If output is non-empty, stop: "Working tree has uncommitted changes; commit or stash before running /plan-issue."
- Resolve the default branch: `gh repo view --json defaultBranchRef -q .defaultBranchRef.name`. Capture as `default_branch`.

## 4. Check out the spec branch

- Run `git fetch origin --quiet`.
- Run `gh pr checkout $pr_number`. This places HEAD on `$head_branch` and pulls. If it fails, stop and report.

## 5. Refuse if a plan file already exists

- List plan files matching this issue: `ls docs/superpowers/plans/ 2>/dev/null | grep -E "issue-$ARGUMENTS-.*\.md" | head -1`.
- If output is non-empty, stop: "A plan file for issue #$ARGUMENTS already exists on `$head_branch`: `<file>`. Run `/plan-review $ARGUMENTS` to revise it."
- Then `git checkout "$default_branch"` and exit.

## 6. Extract the slug from the head branch

The head branch is `spec/issue-$ARGUMENTS-<slug>`. Strip the `spec/issue-$ARGUMENTS-` prefix to get `slug`. Capture as `slug`.

Example: `spec/issue-7-use-date-picker-for-all-date-input-edit-fields` → `slug = use-date-picker-for-all-date-input-edit-fields`.

## 7. Read context — priors

Read these files in full:

- `CLAUDE.md`
- The spec file on this branch: locate via `ls docs/superpowers/specs/*-issue-$ARGUMENTS-*-design.md`. There should be exactly one match. If there are zero, stop: "Spec file for issue #$ARGUMENTS not found on `$head_branch`."
- The 3 most recent files in `docs/superpowers/plans/*.md` excluding any whose first ~10 lines contain `Superseded`. Order by file mtime: `ls -t docs/superpowers/plans/*.md`.

These establish the plan format, repo conventions, and the architectural decisions made by the spec.

## 8. Read context — relevant source files

Based on the spec and issue body, identify source files that the plan will touch and read them. Examples:

- Spec mentions a screen ("Timeline", "Overview") → read the corresponding `app/src/main/java/com/spendtrack/ui/feature/<feature>/` files.
- Spec mentions data layer ("backup", "import") → read `app/src/main/java/com/spendtrack/data/` files.
- Spec is conceptual / cross-cutting → may not need source-file reads.

**Maintain a list of files you read in this step.** Cite them in the plan's `Reference` or `File Map` sections so the executor knows the plan is grounded.

If a file path you'd like to read doesn't exist, note it in the plan's `Open follow-ups` rather than fabricating its contents.

## 9. Compute today's date and plan file path

- Today's date (UTC): `date -u +%Y-%m-%d`. Capture as `today`.
- Plan file path: `docs/superpowers/plans/$today-issue-$ARGUMENTS-$slug.md`. Capture as `plan_path`.

## 10. Design and write the plan file

Design the implementation plan for this issue, grounded in the spec. Match the format and tone of the prior plans you read in step 7. The plan must include, in this order:

- Title heading: `# <Issue Title> — Implementation Plan`
- Required-skill banner: `> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.`
- **Goal:** one paragraph synthesising the spec.
- **Architecture:** one paragraph on layering / file-level structure.
- **Tech Stack:** the relevant subset of the project stack (from `CLAUDE.md`).
- **Reference:** path to the spec file (relative), with a note about which decisions to read first.
- **File Map:** table of files to create/modify/delete with one-line per row.
- **Branch strategy:** which branch the plan applies to (this `$head_branch`).
- **Tasks:** numbered tasks, each broken into `- [ ]` Step blocks. Each Step contains the exact commands or edits an engineer would run. Each task should produce a commit at its end.
- **Phase summary:** small table at the end mapping phases to tasks.

Use the Write tool to create the file at `$plan_path`.

## 11. Commit the plan file and push the branch

- `git add "$plan_path"`
- `git commit -m "plan: draft for issue #$ARGUMENTS"`
- `git push origin "$head_branch"`

If push fails (auth, network), stop and report:
> "Plan is committed locally on `$head_branch` but push failed. Fix the underlying issue and run `git push origin $head_branch` manually."
> Then `git checkout "$default_branch"` and exit.

## 12. Build the new PR body

Concatenate `$pr_body` (the original) with a new trailing section. Format the appended section as:

```markdown
---

## Plan

Plan file: `<plan_path>`

### Tasks

- Task 1: <one-line summary of task 1>
- Task 2: <one-line summary of task 2>
- ...

*Generated by /plan-issue. Review before merge.*
```

The task list mirrors the task headings in the plan file you just wrote — one bullet per task, one-line summary each. Don't elaborate beyond the title.

Write the combined body (original + appended section) to a temp file: `tmp_pr_body=$(mktemp)`.

## 13. Update the PR title and body

- New title: `Spec + Plan: <issue_title> (#$ARGUMENTS)`. Capture as `new_title`.
- Run: `gh pr edit $pr_number --title "$new_title" --body-file "$tmp_pr_body"`.
- Run: `rm -f "$tmp_pr_body"`.

If `gh pr edit` fails, stop and report:
> "Plan is pushed to `$head_branch` but PR title/body update failed. Update manually: `gh pr edit $pr_number --title '<new_title>' --body-file <file>`."
> Then `git checkout "$default_branch"` and exit.

## 14. Comment on the source issue

Run `gh issue comment $ARGUMENTS --body "Plan added to spec PR: $pr_url"`.

If this fails, **log a warning but do not fail the command** — the PR is the canonical artefact:
> "::warning::Could not comment on issue #$ARGUMENTS (the PR is still the source of truth)."

## 15. Return to the default branch

Run `git checkout "$default_branch"`. Run this even if any earlier step failed after step 4 — never leave the user on the spec branch unexpectedly.

## 16. Report success

Print to the user:

```
Plan added to spec PR: <pr_url>
  Issue:   #$ARGUMENTS — <issue_title>
  Branch:  <head_branch>
  File:    <plan_path>
  Title:   <new_title>

Next: /plan-review $ARGUMENTS to walk through the plan and revise.
```
````

- [ ] **Step 3: Sanity-check the file structure**

Run:
```bash
head -5 .claude/commands/plan-issue.md
grep -c '^## ' .claude/commands/plan-issue.md
test -f .claude/commands/plan-issue.md && wc -l .claude/commands/plan-issue.md
```

Expected:
- `head -5` shows the YAML frontmatter starting with `---` and the `description:` / `argument-hint:` fields.
- `grep -c '^## '` returns `16` (one per numbered step).
- `wc -l` reports a line count in the 180-220 range.

- [ ] **Step 4: Commit**

```bash
git add .claude/commands/plan-issue.md
git commit -m "feat: add /plan-issue slash command"
```

---

### Task 3: Write `/plan-issues` (sweep command)

**Files:**
- Create: `.claude/commands/plan-issues.md`

The sweep command is shorter — it doesn't repeat `/plan-issue`'s logic, it dispatches per-issue subagents.

- [ ] **Step 1: Create the file**

Write the following content verbatim to `.claude/commands/plan-issues.md`:

````markdown
---
description: Sweep open spec PRs without a plan file and generate a plan for each (updates each PR to "Spec + Plan").
---

You are sweeping open spec PRs and generating an implementation plan for those without an existing plan file on their branch. Each eligible issue runs as a fresh subagent (sequentially — `gh pr checkout` collisions on the working tree if parallel).

## 1. List open spec PRs

Run `gh pr list --state open --search "head:spec/issue-" --json number,headRefName,title,url --limit 200`. Capture the JSON.

If the command fails (auth, network), stop and surface the error: "gh pr list failed — check `gh auth status` and your network."

## 2. Extract candidate issues

For each PR in the result:
- Parse the issue number from `.headRefName`. The format is `spec/issue-N-...`. Capture as `issue_number`.
- If the parse fails (head branch doesn't match the pattern), skip this PR.

Build a list of `{ issue_number, pr_number, head_branch, pr_title, pr_url }`.

## 3. Filter to eligible issues (no plan file on branch)

Run `git fetch origin --quiet`.

For each candidate, check if a plan file for `issue_number` exists on the branch:

```bash
git ls-tree -r --name-only "origin/<head_branch>" -- docs/superpowers/plans/ 2>/dev/null \
  | grep -E "issue-<issue_number>-.*\.md"
```

If the grep matches, this PR already has a plan — skip it. If the grep is empty, the issue is eligible.

## 4. (Optional sanity) Confirm each candidate issue is still open

For each remaining candidate, run `gh issue view <issue_number> --json state -q .state`. If the state is not `OPEN`, drop the candidate from the eligible list — there's no point planning a closed issue. Note the drop in your eventual rollup.

## 5. Display the eligible list and confirm

If the eligible list is empty, print:

```
No eligible spec PRs — every open spec PR already has a plan file.
```

and exit cleanly.

Otherwise, print:

```
Eligible spec PRs (X):
  #N1 <title 1> → <pr-url-1>
  #N2 <title 2> → <pr-url-2>
  ...

About to dispatch a subagent per issue, sequentially. Each will add a plan file and update the spec PR. Proceed? (yes/no)
```

Wait for the user's response. Only continue on an explicit affirmative answer (`yes`, `y`). Any other response: stop and exit cleanly without dispatching.

## 6. Dispatch sequentially

For each eligible issue, in numerical order of `issue_number`:

- Use the Task tool to dispatch a `general-purpose` subagent.
- Description: `Add plan to spec PR for issue #N`.
- Prompt: tell the subagent to follow the `/plan-issue <N>` slash command's instructions (paste the body of `.claude/commands/plan-issue.md` into the subagent prompt, with `$ARGUMENTS` replaced by the actual issue number). The subagent must do all the same checks (issue open, single open spec PR, working tree clean, no plan file yet), all the reads, the design, the commit + push, the PR-edit, the issue comment, and the return-to-default-branch.
- Wait for the subagent to complete before dispatching the next.
- Capture each result: success (with PR URL) or failure (with one-line reason from the subagent's report).

**Do NOT parallelize.** Even though subagents run in isolated context, they all share the user's working tree, and `gh pr checkout` collisions would corrupt the run.

## 7. Report rollup

After all subagents complete (or the user interrupts):

```
Plan sweep complete: processed=X failed=Y

Successes:
  #N1 → <pr-url> (Spec + Plan)
  #N2 → <pr-url> (Spec + Plan)
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
head -5 .claude/commands/plan-issues.md
grep -c '^## ' .claude/commands/plan-issues.md
```

Expected:
- Frontmatter present with `description:`.
- Seven top-level sections (`## 1.` through `## 7.`).

- [ ] **Step 3: Commit**

```bash
git add .claude/commands/plan-issues.md
git commit -m "feat: add /plan-issues sweep command"
```

---

### Task 4: Write `/plan-review` (review command)

**Files:**
- Create: `.claude/commands/plan-review.md`

Mirrors `/spec-review` very closely. The behavioural differences: reads the plan file (and the spec for context), refuses if no plan exists, frames brainstorming around plan revision rather than spec revision.

- [ ] **Step 1: Create the file**

Write the following content verbatim to `.claude/commands/plan-review.md`:

````markdown
---
description: Review and revise the implementation plan on a spec+plan PR generated by /plan-issue or /plan-issues.
argument-hint: <issue-number>
---

You are reviewing the plan section of a spec+plan PR generated by `/plan-issue` or `/plan-issues`
for issue #$ARGUMENTS.

Steps:

1. **Locate the PR.** Run:
   ```bash
   gh pr list --state open --search "head:spec/issue-$ARGUMENTS-" \
     --json number,headRefName,title,body,url
   ```
   If multiple match, prefer the most recent. If none match, run
   `gh issue view $ARGUMENTS --comments` and look for the bot comment
   containing "Plan added to spec PR:" — follow that link.
   Capture the PR number as `pr_number`.

2. **Check out the head branch.** Run:
   ```bash
   gh pr checkout <pr_number>
   ```

3. **Refuse if no plan file exists.** Run:
   ```bash
   ls docs/superpowers/plans/ 2>/dev/null | grep -E "issue-$ARGUMENTS-.*\.md"
   ```
   If the result is empty, stop: "No plan file found on this branch for issue #$ARGUMENTS — run `/plan-issue $ARGUMENTS` first." Then `git checkout` back to the default branch (resolved via `gh repo view --json defaultBranchRef -q .defaultBranchRef.name`) and exit.

4. **Read context into the conversation.** Read these files/values:
   - The plan file at `docs/superpowers/plans/*-issue-$ARGUMENTS-*.md`
   - The spec file at `docs/superpowers/specs/*-issue-$ARGUMENTS-*-design.md`
   - The PR body: `gh pr view <pr_number> --json body -q .body`
   - The original issue body: `gh issue view $ARGUMENTS --json body -q .body`

5. **Invoke `superpowers:brainstorming`** with this framing:
   > "Here's a draft implementation plan generated by /plan-issue for
   > issue #$ARGUMENTS. The committed plan is the file in
   > `docs/superpowers/plans/`. The corresponding spec is in
   > `docs/superpowers/specs/`. Walk me through the plan's tasks and
   > surface anything I want to revise: task ordering, completeness of
   > step-by-step instructions, missing verification commands, risk
   > areas, scope of each task, alignment with the spec."

6. **Apply user-approved revisions:**
   - Edit the plan file in place.
   - Stage + commit: `git add <plan-file> && git commit -m "plan: revise <area>"`
   - Push: `git push origin HEAD`
   - If the PR body's `## Plan` section needs updating (e.g., the task list changed), write the new full body to a temp file and run:
     ```bash
     gh pr edit <pr_number> --body-file <tmpfile>
     ```

7. **Do not** mark the PR ready for review or merge it — that stays a manual step the user takes when they're happy.
````

- [ ] **Step 2: Sanity-check**

Run:
```bash
head -5 .claude/commands/plan-review.md
grep -c '^[0-9]\+\. ' .claude/commands/plan-review.md
test -f .claude/commands/plan-review.md && wc -l .claude/commands/plan-review.md
```

Expected:
- Frontmatter present with `description:` and `argument-hint:`.
- Seven numbered top-level steps.
- Line count in the 50-70 range.

- [ ] **Step 3: Commit**

```bash
git add .claude/commands/plan-review.md
git commit -m "feat: add /plan-review slash command"
```

---

### Task 5: Push branch and open the draft PR

**Files:** none (git + GitHub operations only)

- [ ] **Step 1: Verify the local branch state**

Run:
```bash
git log --oneline origin/main..HEAD
git status
```

Expected:
- Four commits on top of `origin/main`: docs (spec+plan), `/plan-issue`, `/plan-issues`, `/plan-review`.
- Working tree clean.

- [ ] **Step 2: Push the branch**

Run:
```bash
git push -u origin feat/plan-issue-commands
```

Expected: success. The branch tracks `origin/feat/plan-issue-commands`.

- [ ] **Step 3: Open the draft PR**

Write the PR body to a temp file. Then run:

```bash
tmp_pr_body=$(mktemp)
cat > "$tmp_pr_body" <<'EOF'
## Summary

Three Claude Code slash commands that turn an open spec PR into a spec+plan PR — adding an implementation plan inline as a new commit on the existing spec branch and updating the PR title to `Spec + Plan: ...`. Mirrors the proven shape of `/spec-issue`, `/spec-issues`, `/spec-review`.

- `/plan-issue <N>` — add a plan to the open spec PR for issue N. Refuses if no spec PR exists, multiple exist, or a plan file is already on the branch.
- `/plan-issues` — sweep open spec PRs without a plan file; dispatches one subagent per eligible issue (sequential), with a confirmation prompt.
- `/plan-review <N>` — locate the spec+plan PR for issue N, load context, walk the plan via the brainstorming skill, apply revisions.

## What's in the diff

- Added: `.claude/commands/plan-issue.md`, `.claude/commands/plan-issues.md`, `.claude/commands/plan-review.md`.
- Added: `docs/superpowers/specs/2026-05-08-plan-issue-commands-design.md` (design).
- Added: `docs/superpowers/plans/2026-05-08-plan-issue-commands.md` (this implementation plan).

## Test plan

Local sanity check (no live API call needed):

- [ ] Pick any open spec PR (`gh pr list --search "head:spec/issue-"`). Run `/plan-issue <N>` for that issue. Verify a plan file is committed to the branch, the PR title becomes `Spec + Plan: ...`, the PR body has a trailing `## Plan` section, the issue gets a comment with the PR URL, and the working tree returns to `main` cleanly.
- [ ] Re-run `/plan-issue <N>` — should refuse with the existing-plan-file message.
- [ ] Run `/plan-issue <N>` for an issue with no spec PR — should refuse with the no-spec-PR message.
- [ ] Run `/plan-issues` — should list eligible spec PRs, ask for confirmation, then process them sequentially. Spot-check one resulting PR.
- [ ] Run `/plan-review <N>` against any spec+plan PR — should check out the branch, load context (plan + spec + PR body + issue body), and invoke brainstorming framed around plan revision.
EOF

gh pr create --draft \
  --title "Plan-issue commands: add implementation plans to spec PRs" \
  --body-file "$tmp_pr_body" \
  --base main

rm -f "$tmp_pr_body"
```

Expected: the command prints a PR URL. Capture it.

- [ ] **Step 4: Confirm the PR**

Run:
```bash
gh pr view --json number,title,state,isDraft,url -q '"PR #" + (.number|tostring) + " — " + .title + " (" + .state + ", draft=" + (.isDraft|tostring) + ")"'
```

Expected: PR number, title `Plan-issue commands: ...`, state `OPEN`, `draft=true`.

---

### Task 6: Smoke test (user-driven)

**Files:** none — manual.

After the previous tasks land, the user runs the commands locally and verifies behaviour. This task is manual because slash commands need a fresh Claude Code session to invoke.

- [ ] **Step 1: Confirm there's an eligible spec PR to test against**

Run:
```bash
gh pr list --state open --search "head:spec/issue-" --json number,headRefName,title,url
```

If the result is empty, the user needs to run `/spec-issue <N>` first against any open issue to create a spec PR for testing.

Pick one PR for testing — the thinnest issue is best for a quick smoke. Capture the issue number as `N`.

- [ ] **Step 2: Single-issue mode against issue N**

Open a fresh Claude Code session in this repo. Run: `/plan-issue N`.

Verify:
- Pre-flight checks run (issue open, single open spec PR found, working tree clean).
- `gh pr checkout` brings the user onto the spec branch.
- Plan file does not yet exist on the branch (refused-on-existing logic skipped because none).
- Priors are read: `CLAUDE.md`, the spec file, recent prior plans, the issue body.
- Plan file is created at `docs/superpowers/plans/<today>-issue-N-<slug>.md`.
- Commit `plan: draft for issue #N` is made and pushed.
- PR title is updated to `Spec + Plan: <title> (#N)`.
- PR body has the original spec discovery report PLUS a trailing `## Plan` section.
- Issue #N has a new comment linking to the PR.
- Final state: on `main` with a clean working tree.

- [ ] **Step 3: Re-run for the same issue**

Same fresh session, run `/plan-issue N` again.

Verify: refuses with the existing-plan-file message. No state change. Final state: on `main`.

- [ ] **Step 4: Run against an issue with no spec PR**

Pick an open issue with no spec branch (verify with `gh pr list --search "head:spec/issue-<M>-"` returning empty). Run `/plan-issue M`.

Verify: refuses with the no-spec-PR message. No commit, no checkout, no PR change.

- [ ] **Step 5: Sweep mode**

Fresh session, run `/plan-issues`.

Verify:
- Lists eligible spec PRs (excluding the one from step 2 since it now has a plan).
- Asks for confirmation before dispatching.
- On `yes`, processes each issue sequentially via subagents.
- Final rollup reports each PR URL.
- Working tree on `main` and clean at the end.

- [ ] **Step 6: `/plan-review` on a generated PR**

Fresh session, run `/plan-review N` for any of the spec+plan PRs.

Verify:
- Locates the PR.
- Checks out the branch.
- Loads plan + spec + PR body + issue body into context.
- Invokes the brainstorming skill — Claude should ask probing questions about the tasks rather than passively summarising.

- [ ] **Step 7: Decide on the test artefacts**

If the generated plans are good, leave them on their PRs for review. If they're test artefacts you don't want, you can revert the plan commit on each branch and force-push, or close the PR entirely:

```bash
gh pr close <pr-number> --delete-branch
```

(Caution: deleting the branch deletes the spec too. If the spec is keepable, just revert the plan commit instead.)

---

## Phase summary

| Phase | Tasks | What you have at the end |
|---|---|---|
| Setup | 1 | Feature branch created, design docs committed |
| New commands | 2–4 | Three slash commands in `.claude/commands/` |
| Operational | 5 | Branch pushed, draft PR opened |
| Verification | 6 | Smoke-tested end-to-end against live spec PRs |

After Task 6, the feature is ready to merge. Mark the PR ready-for-review and merge when satisfied.
