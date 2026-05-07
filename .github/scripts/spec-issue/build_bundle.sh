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
