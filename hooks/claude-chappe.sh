#!/usr/bin/env bash
#
# claude-chappe.sh - reports the state of a Claude Code session into a status
# file that the Claude Chappe IDE plugin reads.
#
# Usage:  claude-chappe.sh <state>
# States: working | waiting | idle | clear
#
# Written to: $CLAUDE_CHAPPE_DIR (default ~/.claude/ide-status)
#   <session_id>.json
#   { "session": …, "cwd": …, "state": …, "ts": …, "pid": … }
#
# The hooks report raw states only. Which state deserves attention - and
# therefore which colour a window gets - is decided in the plugin, so that
# choice can change without touching this script.
#
# Requires: jq

set -eu

state="${1:-idle}"
dir="${CLAUDE_CHAPPE_DIR:-$HOME/.claude/ide-status}"
mkdir -p "$dir"

# Hook payload arrives on stdin; empty when called by hand.
payload=$(cat 2>/dev/null || true)

session=""
cwd=""
if [ -n "$payload" ]; then
  session=$(printf '%s' "$payload" | jq -r '.session_id // empty' 2>/dev/null || true)
  cwd=$(printf '%s' "$payload" | jq -r '.cwd // empty' 2>/dev/null || true)
fi
[ -n "$cwd" ] || cwd="${CLAUDE_PROJECT_DIR:-$PWD}"
[ -n "$session" ] || session="pid-$PPID"

file="$dir/$session.json"

if [ "$state" = "clear" ]; then
  rm -f "$file"
  exit 0
fi

# PID of the nearest claude ancestor. The plugin uses it to spot files left
# behind by a crashed session, so a dead "working" state cannot keep a window
# green forever.
claude_pid=""
pid=$PPID
while [ -n "$pid" ] && [ "$pid" != "0" ] && [ "$pid" != "1" ]; do
  comm=$(ps -o comm= -p "$pid" 2>/dev/null | tr -d ' ' || true)
  case "$(basename "${comm:-}")" in
    claude) claude_pid="$pid"; break ;;
  esac
  pid=$(ps -o ppid= -p "$pid" 2>/dev/null | tr -d ' ' || true)
done

# Write to a temp file and rename, so a reader never sees a half-written file.
tmp="$file.tmp.$$"
jq -n \
  --arg session "$session" \
  --arg cwd     "$cwd" \
  --arg state   "$state" \
  --arg pid     "${claude_pid:-0}" \
  --argjson ts  "$(date +%s)" \
  '{session:$session, cwd:$cwd, state:$state, ts:$ts, pid:($pid|tonumber)}' \
  > "$tmp" 2>/dev/null && mv -f "$tmp" "$file" || rm -f "$tmp"

# Never fail a hook - a broken status file must not break the session.
exit 0
