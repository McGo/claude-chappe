# Claude Chappe

[![Build](https://github.com/McGo/claude-chappe/actions/workflows/build.yml/badge.svg)](https://github.com/McGo/claude-chappe/actions/workflows/build.yml)
[![Licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

Colours each JetBrains project window by the state of the Claude Code sessions running in its
terminals. With a stack of open windows you can see which one needs you without clicking through
them.

- **Green** — at least one session is processing.
- **Red** — a session is waiting for a decision, or has finished its turn.
- **Untouched** — nothing running.

The window gets a coloured border, and optionally a tinted status bar. That second part matters
more than it sounds: when windows overlap, the status bar is often the only strip of a background
window you can still see.

Works in every JetBrains IDE — the plugin depends on the platform only, not on any language
support.

## Requirements

| | |
|---|---|
| IDE | Any JetBrains IDE from 2025.1 on. |
| Claude Code | Any version with hook support. |
| `jq` | Used by the hook script. `brew install jq`, or your package manager. |
| Shell | The hook is a POSIX shell script. On Windows it needs Git Bash or WSL. |

## How it works

Claude Code hooks report the state of each session into a small JSON file:

```
~/.claude/ide-status/<session-id>.json
{ "session": "…", "cwd": "/path/to/project", "state": "working", "ts": 1787047737, "pid": 30037 }
```

The plugin polls that directory and matches each session's working directory against the base path
of every open project. Sessions in subdirectories count towards their project, so a session started
in `apps/api` colours the repository window.

No terminal output is parsed and no process list is guessed at. The one thing read from the process
table is the PID stored in the file: if that process is gone, the session crashed and its file is
removed, so a dead `working` state cannot leave a window green forever.

These are the events the installer registers, and the state each one reports:

| Event | State | |
|---|---|---|
| `SessionStart` | `idle` | A session exists, nothing running yet. |
| `UserPromptSubmit` | `working` | You sent a prompt. |
| `PostToolUse` | `working` | A tool call finished. Also what takes a window out of `waiting` after you approve a permission prompt — approving a dialog is not a prompt submission, so nothing else would. |
| `Notification` | `waiting` | Claude wants a decision, or has been waiting for input. |
| `Stop` | `idle` | Claude finished its turn. |
| `SessionEnd` | — | The status file is removed. |

Hooks report raw states only. Which state deserves attention — and therefore which colour a window
gets — is decided in the plugin, so you can change that in the settings without touching the hooks.

## Install

**The plugin.** Not on the JetBrains Marketplace yet. Build it and install the ZIP from disk:

```bash
./gradlew buildPlugin
# Settings → Plugins → ⚙ → Install Plugin from Disk…
# build/distributions/claude-chappe-*.zip
```

**The hooks.** Requires `jq`.

```bash
./hooks/install.sh
```

That copies `claude-chappe.sh` to `~/.claude/hooks/` and registers it for six events in
`~/.claude/settings.json`. Hooks belonging to other tools are left alone, running the installer
twice does not duplicate anything, and `./hooks/install.sh --remove` undoes it. A timestamped
backup of the settings file is written either way.

Sessions that are already running keep their old hook configuration — restart them, or just wait
until you open them again.

## Settings

Under **Settings → Tools → Claude Chappe**:

| | |
|---|---|
| Colours | One for working, one for needs-you. |
| A finished session asks for attention | On by default. Switch off to reserve red for actual permission prompts. |
| Focusing a window clears the finished-session signal | On by default. Without it, every finished session stays red until you prompt it again. The window you are currently working in never turns red. |
| Border thickness | Pixels. |
| Tint the status bar | On by default. |
| Cascade window size | Pixels each window is resized to. 0 uses whatever the screen has left once the offsets are spent. |
| Cascade offsets | Horizontal and vertical step of the cascade. |
| Status directory, poll interval | Change if you moved the hook output somewhere else. |

## Cascade

**Window → Cascade Project Windows** stacks every open project of that IDE onto one screen, offset
step by step, most urgent one in front and fully visible. The stack sits flush with the top of the
screen and is centred horizontally, which keeps it in view on a wide screen. Window size and offsets
come from the settings.

All project windows of a JetBrains IDE live in the same process, so the plugin places them itself.
No accessibility permissions, no window manager scripting, and nothing that breaks when macOS
tightens its rules again.

## Troubleshooting

**Nothing is coloured.** Check that the hooks are registered (`jq '.hooks' ~/.claude/settings.json`)
and that a status file shows up in `~/.claude/ide-status/` once a session runs. Sessions started
before the installer ran still use the old configuration.

**A window stays red although Claude is working.** The session reported `waiting` and nothing
reported it back. Make sure `PostToolUse` is among the registered events; older installs only had
five. One case is left over by design: if you approve a permission prompt and the tool then runs
for a long time, the window stays red until that tool returns. Claude Code has no event for the
moment a permission is granted.

**A window stays green although nothing runs.** The status file outlived its session and the PID
in it is still taken by another process. Delete the file in `~/.claude/ide-status/`.

## Uninstall

```bash
./hooks/install.sh --remove
# Settings → Plugins → Claude Chappe → Uninstall
```

The plugin resets every window it touched when it is unloaded. Status files left in
`~/.claude/ide-status/` can be deleted by hand.

## Why the name

Claude Chappe built the optical telegraph in 1792: a chain of towers whose arms could be read from
the next hilltop, carrying a message across France in minutes. Status over distance, by nothing but
what you can see from far away. Which is what a coloured window edge does at the other end of a
stack of IDEs.

## Building

```bash
./gradlew buildPlugin     # ZIP in build/distributions/
./gradlew runIde          # sandbox IDE with the plugin loaded
./gradlew verifyPlugin    # JetBrains plugin verifier
```

Gradle fetches its own JDK 21 toolchain, so no local JDK setup is needed beyond one that can run
Gradle itself. `verifyPlugin` checks against the lower bound of the supported range; point
`verifyAgainstLocalIde` at an installed IDE in `~/.gradle/gradle.properties` to add a second target.

## Releasing

1. Set `pluginVersion` in `gradle.properties` and move the entries under `## [Unreleased]` in
   [CHANGELOG.md](CHANGELOG.md) into a dated section for that version. The build reads that section
   and writes it into the plugin as the change notes, which is what the Marketplace shows under
   *What's new*.
2. `./gradlew buildPlugin verifyPlugin`
3. The first upload of a new plugin goes through the web form at
   [plugins.jetbrains.com/plugin/add](https://plugins.jetbrains.com/plugin/add), where JetBrains
   reviews it before it becomes visible.
4. Every release after that: `PUBLISH_TOKEN=… ./gradlew publishPlugin`, with a token from
   [your Marketplace profile](https://plugins.jetbrains.com/author/me/tokens).

## Contributing

Bug reports and pull requests are welcome at
[github.com/McGo/claude-chappe](https://github.com/McGo/claude-chappe). For anything larger than a
fix, an issue first saves us both the work of finding out we meant different things.

## Licence

MIT — see [LICENSE](LICENSE).
