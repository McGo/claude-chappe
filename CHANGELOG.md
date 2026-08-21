# Changelog

## [Unreleased]

### Added
- Window border and status bar tint driven by the state of Claude Code sessions
  in the project.
- `Cascade Project Windows` action, sorted by urgency.
- Settings page under Tools with colours, border thickness, attention rules,
  cascade offsets and the status source.
- Hook script and installer for Claude Code.

### Changed
- Cascade takes the window size from the settings instead of filling the screen,
  and places the stack top aligned and centred horizontally. Defaults: 2150 x 740
  pixels, offsets 210 x 45.

### Fixed
- A window stayed red after a permission prompt was approved. Approving a
  dialog is not a prompt submission, so nothing reported the session back to
  `working`. The installer now registers `PostToolUse` as well.
