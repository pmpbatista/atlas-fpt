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
