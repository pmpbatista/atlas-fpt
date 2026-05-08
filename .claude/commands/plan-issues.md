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
