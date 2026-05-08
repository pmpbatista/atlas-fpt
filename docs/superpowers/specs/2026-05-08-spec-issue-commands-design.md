# Spec-Issue Commands — Design Spec

**Date:** 2026-05-08
**Status:** Draft
**Supersedes:** [`2026-05-07-spec-issue-agent-design.md`](2026-05-07-spec-issue-agent-design.md) (the GitHub-Action approach is dropped; this design replaces it)
**Scope:** Three Claude Code slash commands that turn open GitHub issues into draft spec PRs locally — no GitHub Action, no cloud-stored API key. Replaces the previously-designed workflow + bash helper tree with two new commands (`/spec-issue`, `/spec-issues`) and minor edits to the existing `/spec-review`.

## Goal

Same outcome as the superseded design — a draft spec PR per open issue, with the discovery report in the PR body and the spec file committed under `docs/superpowers/specs/` — but executed locally via Claude Code instead of a GitHub Action. The author runs the commands from their terminal, using their own Claude Code auth and Anthropic credentials. Nothing is stored in GitHub repo secrets.

## Why this re-scope

The previous design required `ANTHROPIC_API_KEY` as a GitHub repo secret. The author isn't comfortable with that — concrete risks include cost abuse if the key leaks, broader trust footprint as the repo grows, and the general principle of avoiding API keys in CI when local execution is viable.

Switching to Claude Code slash commands removes the secret entirely. The author already runs Claude Code locally with their own auth; no new credentials, no new attack surface. The spec output is unchanged.

## Non-goals

- **No GitHub Action.** No workflow file, no scheduled runs, no `workflow_dispatch`. All execution is local.
- **No bash helper scripts.** Slash commands use Claude Code's native tools (Bash, Read, Grep) directly. The previous design's bash helpers (`slug.sh`, `call_claude.sh`, `resolve_targets.sh`, `build_bundle.sh`, `run.sh`, `system_prompt.txt`, the test runner) are all deleted.
- **No "pinned context bundle".** That constraint existed because the API call had no file tools. Claude Code does — slash commands let Claude explore the repo as needed. (See [Context gathering](#context-gathering) for details.)
- **No two-section delimiter contract.** The model writes the spec to disk directly via the Edit/Write tools and the discovery report into the PR body via `gh pr create`. No need for sentinel-string parsing.
- **No retroactive "stale spec" cleanup, no auto-trigger on issue events, no token-cost telemetry.** Same exclusions as the superseded design.
- **No parallel sweep.** Sequential per issue (same reason: `git checkout -b` collisions on the working tree).

## Decisions

### Surface

Three commands in `.claude/commands/`:

- **`/spec-issue <issue-number>`** (new) — generates one draft spec PR for the given issue.
- **`/spec-issues`** (new) — sweep over all open issues without an existing `spec/issue-N-*` branch; dispatches a subagent per eligible issue.
- **`/spec-review <issue-number>`** (already exists; minor text update) — pulls a spec PR locally and runs the brainstorming skill against it for revision.

### Output shape (unchanged from superseded design)

Per processed issue:

- Branch `spec/issue-N-<slug>` pushed to origin.
- Spec file at `docs/superpowers/specs/<run-date>-issue-N-<slug>-design.md`.
- Draft PR titled `Spec: <issue title> (#N)`, body = discovery report, footer `Refs #N` (NOT `Closes #N`).
- Comment on issue #N: `Spec PR opened: <url>`.

### Pre-flight checks (`/spec-issue`)

Refuses to start when:

- `gh issue view <N> --json state` returns anything other than `OPEN`.
- `git ls-remote --heads origin "spec/issue-N-*"` finds an existing branch — surfaces the branch name and tells the author to delete it first if they want to regenerate.

Both surface clear, actionable error messages.

### Context gathering

Free exploration via Claude Code's native tools:

- **Always read:** the issue body via `gh issue view --json title,body,labels`, `SPEC.md`, `CLAUDE.md`, and the 3 most recent files in `docs/superpowers/specs/*-design.md` as format/style priors.
- **Then explore as needed:** read app source files clearly relevant to the issue. For example, an issue titled "Timeline: align screen with intended design" causes Claude to read `app/src/main/java/com/spendtrack/ui/feature/timeline/TimelineScreen.kt` and its ViewModel. Issues that don't reference specific code (e.g. "Settings: local data backup") may require no source-file reading.
- **Track the reads.** The discovery report's "Files I read" subsection lists every non-prior file Claude touched while designing. The reviewer can audit this for grounding quality.

### Design step

Claude picks an approach, weighs alternatives internally (no real-time dialogue with the author during generation — that's `/spec-review`'s job), commits to a recommendation. Same discipline as the `superpowers:brainstorming` skill but autonomous. Thin issues surface as loud assumption-flagging in the discovery report (same as the superseded design — uniform behaviour beats inconsistent gating).

### Slug + filenames

- Slug rules: lowercase, non-alphanumeric → `-`, collapse repeats, trim leading/trailing dashes, cap at 50 chars trimmed back to the last `-` so we never end mid-word.
- Examples (issue title → slug):
  - `"Settings: local data backup (manual + scheduled) to user-chosen folder"` → `settings-local-data-backup-manual-scheduled-to`
  - `"Ticker search-as-you-type dropdown (Yahoo Finance-style)"` → `ticker-search-as-you-type-dropdown-yahoo-finance`
  - `"Use date picker for all date input/edit fields (incl. Assets)"` → `use-date-picker-for-all-date-input-edit-fields`
- Branch: `spec/issue-N-<slug>`.
- Spec file: `docs/superpowers/specs/<run-date>-issue-N-<slug>-design.md`.

### Discovery report (5 subsections)

Goes in the draft PR body. Same structure as the superseded design:

```markdown
## Discovery & Decisions

### Questions I considered
...

### Options weighed
- **Option A:** ...
- **Option B:** ...

### Recommended approach (and why)
...

### Assumptions I made — verify these
...

### Files I read
- `app/src/.../TimelineScreen.kt` — confirmed composable structure
- `SPEC.md`, `CLAUDE.md` — priors

---

Refs #N

*Generated by /spec-issue. Review before merge.*
```

The "Files I read" subsection is now a real artefact (not aspirational, as in the superseded design where the agent couldn't actually read files).

### Cleanup discipline

`/spec-issue` always returns to the default branch on exit, success or failure. The slash command's instructions explicitly say: "after pushing or after any failure, `git checkout <default-branch>`." This is the slash-command equivalent of the original `trap ... RETURN`.

### Sweep behaviour (`/spec-issues`)

1. Resolve eligible issues:
   - `gh issue list --state open --limit 200 --json number,title` (fail loudly on non-zero exit).
   - Filter out those with an existing `spec/issue-N-*` branch via `git ls-remote --heads origin "spec/issue-*"`.
2. Display the eligible list (numbers + titles) and ask for explicit confirmation before dispatching anything. This is the one deviation from the superseded design's fire-and-forget — sweep in Claude Code is interactive enough that confirmation costs nothing and prevents surprise.
3. **For each eligible issue, sequentially**, dispatch a fresh subagent with the `/spec-issue <N>` instructions. Sequential because `git checkout -b` on the same working tree can't run in parallel.
4. After all subagents complete (or the user interrupts), report a rollup: `processed=X failed=Y`, with PR URLs for each success and a one-line failure reason per miss.

### Failure handling (consolidated)

| Scenario | Behaviour |
|---|---|
| `gh` not authenticated / no network | Surface the underlying error and abort. |
| Issue not OPEN (single mode) | Refuse: `issue #N is not open (state=closed)`. No branch created. |
| `spec/issue-N-*` branch already exists | Refuse with the existing branch name; suggest manual delete if regeneration is wanted. |
| Discovery / spec generation hits a confusing case | Claude proceeds with explicit assumptions in the discovery report rather than refusing. |
| `git push` fails (auth, network) | Spec file is committed locally; report the local branch and that push + PR steps were skipped. User can finish manually. |
| `gh pr create` fails | Branch is pushed; report it; skip the issue-comment step. User can open the PR manually. |
| `gh issue comment` fails | Logs a warning; PR is still the canonical artefact. |
| Mid-sweep issue failure | Logged, sweep continues, rollup at end shows the breakdown. |
| User interrupts mid-sweep | Whatever's been processed stays; rollup shows partial results. |

### `/spec-review` text edit

Existing file `.claude/commands/spec-review.md` works as-is — its job is to locate a PR by issue number and hand it to the brainstorming skill, regardless of how the PR was generated. Only edits needed:

- Frontmatter `description`: replace "spec-issue workflow" with "spec-issue commands".
- Body opening line: replace "generated by `.github/workflows/spec-issue.yml`" with "generated by `/spec-issue` or `/spec-issues`".

No behaviour change.

## Architecture / affected components

```
.claude/
└── commands/
    ├── spec-issue.md                  (NEW — single-issue command)
    ├── spec-issues.md                 (NEW — sweep command)
    └── spec-review.md                 (MODIFIED — minor text edits)

.github/
├── workflows/
│   └── spec-issue.yml                 (DELETED)
└── scripts/
    └── spec-issue/                    (DELETED — entire tree)
        ├── run.sh
        ├── slug.sh
        ├── call_claude.sh
        ├── resolve_targets.sh
        ├── build_bundle.sh
        ├── system_prompt.txt
        ├── README.md
        └── test/
            ├── run_tests.sh
            ├── test_slug.sh
            └── test_parse_response.sh

docs/
└── superpowers/
    └── specs/
        ├── 2026-05-07-spec-issue-agent-design.md   (LEFT IN PLACE — adds "Superseded by" header pointing here)
        └── 2026-05-08-spec-issue-commands-design.md   (THIS FILE)
└── superpowers/
    └── plans/
        └── 2026-05-07-spec-issue-agent.md          (LEFT IN PLACE — adds "Superseded" header)
```

The historical spec and plan stay committed as-is for repo-history value, with a one-line "Superseded by ..." header added at the top of each so future readers don't follow a dead path.

## Slash command structure

Each slash command is a markdown file with YAML frontmatter (`description`, `argument-hint`) and a step-by-step body that Claude follows when the command is invoked. No bash, no separate executables — Claude reads the markdown and uses its tools.

### `spec-issue.md` body outline

The full content is enumerated in the implementation plan; here's the structural outline:

1. Validate `$ARGUMENTS` is a positive integer.
2. `gh issue view $ARGUMENTS --json state,title,body,labels` — check state is `OPEN`, capture title + body + labels.
3. `git fetch origin` then `git ls-remote --heads origin "spec/issue-$ARGUMENTS-*"` — refuse if any match.
4. Read priors: `SPEC.md`, `CLAUDE.md`, the 3 most recent `docs/superpowers/specs/*-design.md`.
5. Read source files relevant to the issue. Maintain a "files read" list.
6. Generate slug from issue title (the rules above, applied via Bash/awk in-line — not a shared helper).
7. Resolve default branch: `gh repo view --json defaultBranchRef -q .defaultBranchRef.name`.
8. `git checkout <default-branch> && git pull --ff-only origin <default-branch>` (be on a clean main).
9. `git checkout -b spec/issue-N-<slug>`.
10. Write the spec to `docs/superpowers/specs/<today>-issue-N-<slug>-design.md` (Write tool).
11. `git add <file> && git commit -m "spec: draft for issue #N"`.
12. `git push -u origin spec/issue-N-<slug>`.
13. Construct the discovery report markdown body.
14. `gh pr create --draft --title "Spec: <title> (#N)" --body "<discovery>" --base <default-branch>`.
15. `gh issue comment N --body "Spec PR opened: <pr-url>"` (warn-on-fail, don't abort).
16. `git checkout <default-branch>` (always — recovery is part of the command, not implicit).
17. Report PR URL to the user.

### `spec-issues.md` body outline

1. `gh issue list --state open --limit 200 --json number,title` (abort on failure).
2. `git fetch origin && git ls-remote --heads origin "spec/issue-*"` — build set of issue numbers with existing branches.
3. Compute eligible list = open ∖ has-branch.
4. Display the list (numbers + titles + count) and ask for confirmation.
5. For each eligible issue, sequentially: dispatch a Task subagent with `/spec-issue <N>` instructions, wait for completion, capture result.
6. After all done: print rollup `processed=X failed=Y` plus per-issue results (PR URL or failure reason).

### `spec-review.md` body

Already exists and works. Edits only touch the frontmatter description and the opening sentence as noted under [Decisions → /spec-review text edit](#-spec-review-text-edit).

## Validation & edge cases

- **Default branch dirty.** If `git checkout <default-branch>` fails because of uncommitted changes, refuse the operation early with a clear "stash or commit your work first" message. Don't risk losing user work.
- **Default branch behind origin.** `git pull --ff-only` will fail loudly if the local branch has diverged. Refuse and tell the user to reconcile manually.
- **Pushed branch lacks PR.** If `gh pr create` fails after a successful push, the user has a remote branch with the spec but no PR. Report this state explicitly so they know to either retry `gh pr create` or delete the branch.
- **Already on a non-default branch.** The command always switches to the default branch first. If the user has uncommitted work on a feature branch, we refuse rather than silently switching.
- **Slug collision.** Two issues that produce the same 50-char slug are still distinct branches because of the `issue-N-` prefix. No special handling needed.
- **Sweep midway interruption.** `Ctrl-C` mid-sweep aborts the in-flight subagent and stops the loop. Rollup reports what was completed.

## Out of scope (deferred — same as superseded design)

- **Auto-clean-up of stale spec branches.** Manual.
- **Linking specs back to implementation PRs.** Convention only.
- **Generic templating for non-spendtrack repos.** This design is repo-specific (CLAUDE.md path, Android conventions, spec-format priors).
- **Token-cost telemetry.** The user can read their Anthropic console.
- **Auto-merge or auto-ready-for-review on the spec PR.** Always draft; user marks ready manually after `/spec-review`.

## Open follow-ups

- After the first few real generations, check whether the discovery report's "Files I read" list is genuinely useful. If reviewers ignore it, consider dropping it.
- If the sweep confirmation prompt becomes annoying for a small backlog, consider an opt-out flag (`/spec-issues -y`). Don't add up front — wait for the friction to be real.
