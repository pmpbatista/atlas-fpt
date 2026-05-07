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
