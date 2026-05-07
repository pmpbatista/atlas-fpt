#!/usr/bin/env bash
# Library: defines `call_claude()` and `parse_response()`.
# call_claude posts a Messages API payload and prints the model's
# textual response on stdout. parse_response splits a raw response
# into SPEC and DISCOVERY files, returning non-zero if either is empty
# or its delimiter pair is missing.

call_claude() {
  local payload="$1"
  curl -sSf https://api.anthropic.com/v1/messages \
    -H "x-api-key: ${ANTHROPIC_API_KEY}" \
    -H "anthropic-version: 2023-06-01" \
    -H "content-type: application/json" \
    -d "$payload" \
  | jq -r '.content[0].text'
}

parse_response() {
  local raw="$1"
  local spec_out="$2"
  local discovery_out="$3"

  # awk extracts the lines BETWEEN the delimiters (delimiters themselves excluded).
  printf '%s\n' "$raw" \
    | awk '/<<<SPEC>>>/{flag=1; next} /<<<END SPEC>>>/{flag=0} flag' \
    > "$spec_out"

  printf '%s\n' "$raw" \
    | awk '/<<<DISCOVERY>>>/{flag=1; next} /<<<END DISCOVERY>>>/{flag=0} flag' \
    > "$discovery_out"

  # Strip the trailing newline that awk's stream adds, so blank-only outputs
  # don't false-positive as "non-empty".
  if [[ ! -s "$spec_out" ]] || [[ -z "$(tr -d '[:space:]' < "$spec_out")" ]]; then
    return 1
  fi
  if [[ ! -s "$discovery_out" ]] || [[ -z "$(tr -d '[:space:]' < "$discovery_out")" ]]; then
    return 1
  fi
  return 0
}
