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
