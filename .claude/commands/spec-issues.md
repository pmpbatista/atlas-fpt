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
