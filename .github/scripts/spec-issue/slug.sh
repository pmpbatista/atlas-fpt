#!/usr/bin/env bash
# Library: defines `slug()` — title → branch-safe slug, capped at 50 chars,
# trimmed to a word boundary so we never end mid-word.

slug() {
  local title="$1"
  local s
  s=$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')
  if [[ ${#s} -gt 50 ]]; then
    s="${s:0:50}"
    # If the first 50 chars don't end on a `-`, peel back to the previous `-`.
    if [[ "$s" != *-* ]]; then
      :  # no dash at all — leave as-is (e.g. one giant word)
    elif [[ "${s: -1}" != "-" ]]; then
      s="${s%-*}"
    else
      s="${s%-}"
    fi
  fi
  printf '%s' "$s"
}
