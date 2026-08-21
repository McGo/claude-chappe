# Changelog

## [Unreleased]

### Fixed
- A project opened through a symlink stayed uncoloured. The IDE reports the
  project path with symlinks resolved, while the hook writes the working
  directory as the shell saw it — on macOS, `/tmp` against `/private/tmp` is
  enough to break the match. Both sides are now resolved before comparing.

## [0.1.0] - 2026-08-21

First release.

### Added
- Window border and status bar tint driven by the state of the Claude Code
  sessions running in the project.
- `Cascade Project Windows` action: stacks every open project window on one
  screen, most urgent in front, size and offsets taken from the settings.
- Settings page under Tools with colours, border thickness, attention rules,
  cascade layout and the status source.
- Hook script and installer for Claude Code, covering six events.
