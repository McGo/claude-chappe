package de.mirkohaaser.claudechappe

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import java.awt.Color
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.APP)
@State(
    name = "ChappeSettings",
    storages = [Storage("claude-chappe.xml")],
)
class ChappeSettings : PersistentStateComponent<ChappeSettings.State> {

    data class State(
        /** Empty means the default: ~/.claude/ide-status */
        var statusDirectory: String = "",
        var pollIntervalMillis: Int = 750,

        var paintBorder: Boolean = true,
        var borderThickness: Int = 4,
        var tintStatusBar: Boolean = true,

        var workingColor: String = "#3FB950",
        var attentionColor: String = "#F85149",

        /**
         * A finished session (Stop hook) counts as "needs you" and turns the
         * window red. Switch off to reserve red for actual permission prompts.
         */
        var idleCountsAsAttention: Boolean = true,

        /**
         * Focusing a window marks its finished sessions as seen, so the red
         * fades once you have looked. Without this every finished session
         * stays red until it is prompted again.
         */
        var clearAttentionOnFocus: Boolean = true,

        var cascadeOffsetX: Int = 210,
        var cascadeOffsetY: Int = 45,

        /** Size every window is set to. 0 means as large as the screen allows. */
        var cascadeWidth: Int = 2150,
        var cascadeHeight: Int = 740,
        /** Cascade puts the most urgent window in front instead of keeping IDE order. */
        var cascadeSortsByState: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(loaded: State) {
        myState = loaded
    }

    val statusDirectory: Path
        get() = myState.statusDirectory
            .takeIf { it.isNotBlank() }
            ?.let { Paths.get(expandHome(it)) }
            ?: defaultStatusDirectory()

    val pollIntervalMillis: Long get() = myState.pollIntervalMillis.coerceIn(200, 10_000).toLong()
    val borderThickness: Int get() = myState.borderThickness.coerceIn(1, 24)

    fun workingColor(): Color = parseColor(myState.workingColor, DEFAULT_WORKING)
    fun attentionColor(): Color = parseColor(myState.attentionColor, DEFAULT_ATTENTION)

    companion object {
        private val DEFAULT_WORKING = Color(0x3F, 0xB9, 0x50)
        private val DEFAULT_ATTENTION = Color(0xF8, 0x51, 0x49)

        fun getInstance(): ChappeSettings = service()

        fun defaultStatusDirectory(): Path =
            Paths.get(System.getProperty("user.home"), ".claude", "ide-status")

        private fun expandHome(raw: String): String =
            if (raw.startsWith("~")) System.getProperty("user.home") + raw.substring(1) else raw

        fun parseColor(raw: String?, fallback: Color): Color = try {
            Color.decode(raw?.trim().takeUnless { it.isNullOrEmpty() } ?: throw IllegalArgumentException())
        } catch (_: Exception) {
            fallback
        }

        fun toHex(color: Color): String = String.format("#%02X%02X%02X", color.red, color.green, color.blue)

        /** File paths are case-insensitive on macOS and Windows. */
        val pathsAreCaseInsensitive: Boolean = SystemInfo.isMac || SystemInfo.isWindows
    }
}
