#!/usr/bin/env bash
#
# Installs the Claude Chappe status hooks into ~/.claude/settings.json.
#
# Copies claude-chappe.sh to ~/.claude/hooks/ and registers it for the five
# events the plugin needs. Hooks belonging to other tools are left alone;
# earlier Chappe entries are replaced rather than duplicated.
#
# Usage:  ./install.sh           install
#         ./install.sh --remove  remove the Chappe hooks again

set -eu

script_dir=$(cd "$(dirname "$0")" && pwd)
settings="$HOME/.claude/settings.json"
target_dir="$HOME/.claude/hooks"
target="$target_dir/claude-chappe.sh"
command_path='$HOME/.claude/hooks/claude-chappe.sh'

command -v jq >/dev/null 2>&1 || {
  echo "install.sh: jq is required (brew install jq)" >&2
  exit 1
}

mkdir -p "$HOME/.claude"
[ -f "$settings" ] || echo '{}' > "$settings"
backup="$settings.bak-$(date +%Y%m%d-%H%M%S)"
cp "$settings" "$backup"

# Drops every hook entry that calls claude-chappe.sh, then prunes the events
# and lists that are empty afterwards.
read -r -d '' PRUNE <<'JQ' || true
def prune_chappe:
  with_entries(
    .value |= map(.hooks |= map(select((.command // "") | test("claude-chappe\\.sh") | not)))
    | .value |= map(select((.hooks | length) > 0))
  )
  | with_entries(select((.value | length) > 0));
JQ

run_jq() {
  if ! jq "$1" "$settings" > "$settings.new"; then
    rm -f "$settings.new"
    cp "$backup" "$settings"
    echo "install.sh: jq failed, settings restored from $backup" >&2
    exit 1
  fi
  mv "$settings.new" "$settings"
}

if [ "${1:-}" = "--remove" ]; then
  run_jq "$PRUNE
    .hooks = ((.hooks // {}) | prune_chappe)
    | if (.hooks | length) == 0 then del(.hooks) else . end"
  rm -f "$target"
  echo "Removed. Backup: $backup"
  exit 0
fi

mkdir -p "$target_dir"
install -m 0755 "$script_dir/claude-chappe.sh" "$target"

run_jq "$PRUNE
def entry(\$state):
  { matcher: \"\", hooks: [{ type: \"command\", command: (\"$command_path \" + \$state) }] };
(((.hooks // {}) | prune_chappe)
  | .SessionStart     = ((.SessionStart // [])     + [entry(\"idle\")])
  | .UserPromptSubmit = ((.UserPromptSubmit // []) + [entry(\"working\")])
  | .Notification     = ((.Notification // [])     + [entry(\"waiting\")])
  | .Stop             = ((.Stop // [])             + [entry(\"idle\")])
  | .SessionEnd       = ((.SessionEnd // [])       + [entry(\"clear\")])
) as \$hooks
| .hooks = \$hooks"

echo "Installed $target"
echo "Registered hooks: SessionStart, UserPromptSubmit, Notification, Stop, SessionEnd"
echo "Backup: $backup"
echo
echo "Restart running Claude Code sessions to pick the hooks up."
