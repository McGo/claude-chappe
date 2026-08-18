package de.mirkohaaser.claudechappe

/**
 * State of a single Claude Code session, as reported by the hook script.
 *
 * The hooks deliberately report raw states only. Which state deserves the
 * user's attention (and therefore which colour a window gets) is decided
 * here in the plugin, so it stays configurable without touching the hooks.
 */
enum class ClaudeState {
    /** No session known for this project. */
    NONE,

    /** Session exists, Claude finished its turn and awaits the next prompt. */
    IDLE,

    /** Claude is processing. */
    WORKING,

    /** Claude needs a decision — permission prompt or plain input. */
    WAITING,
    ;

    companion object {
        fun parse(raw: String?): ClaudeState = when (raw?.lowercase()) {
            "working" -> WORKING
            "waiting" -> WAITING
            "idle" -> IDLE
            else -> NONE
        }
    }
}

data class ClaudeSession(
    val id: String,
    /** Working directory of the session — matched against the project base path. */
    val cwd: String,
    val state: ClaudeState,
    /** Unix seconds of the last state change. */
    val timestamp: Long,
    /** PID of the owning `claude` process, 0 if it could not be resolved. */
    val pid: Long,
) {
    /** A session whose process is gone left a stale file behind. */
    fun isAlive(): Boolean = pid <= 0 || ProcessHandle.of(pid).isPresent
}
