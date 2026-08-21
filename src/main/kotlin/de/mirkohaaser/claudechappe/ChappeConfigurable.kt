package de.mirkohaaser.claudechappe

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.Color

class ChappeConfigurable : BoundConfigurable("Claude Chappe") {

    private val settings = ChappeSettings.getInstance()
    private val state get() = settings.state

    private val workingColor = ColorPanel()
    private val attentionColor = ColorPanel()

    override fun createPanel(): DialogPanel = panel {
        group("Signal") {
            row("Working:") {
                cell(workingColor)
                    .onReset { workingColor.selectedColor = settings.workingColor() }
                    .onIsModified { hex(workingColor, settings.workingColor()) != state.workingColor }
                    .onApply { state.workingColor = hex(workingColor, settings.workingColor()) }
                    .comment("At least one Claude session in this project is processing.")
            }
            row("Needs you:") {
                cell(attentionColor)
                    .onReset { attentionColor.selectedColor = settings.attentionColor() }
                    .onIsModified { hex(attentionColor, settings.attentionColor()) != state.attentionColor }
                    .onApply { state.attentionColor = hex(attentionColor, settings.attentionColor()) }
                    .comment("A session is waiting for a decision, or has finished its turn.")
            }
            row {
                checkBox("A finished session asks for attention")
                    .bindSelected(state::idleCountsAsAttention)
                    .comment(
                        "On: a window turns red once Claude is done, not only on permission prompts. " +
                            "Off: red is reserved for questions.",
                    )
            }
            row {
                checkBox("Focusing a window clears the finished-session signal")
                    .bindSelected(state::clearAttentionOnFocus)
                    .comment("Without this, every finished session stays red until it is prompted again.")
            }
        }

        group("Appearance") {
            row {
                checkBox("Paint a border around the window").bindSelected(state::paintBorder)
            }
            row("Border thickness:") {
                intTextField(1..24).bindIntText(state::borderThickness).columns(4)
                    .comment("Pixels. The border is always installed, so the width only re-layouts once.")
            }
            row {
                checkBox("Tint the status bar").bindSelected(state::tintStatusBar)
                    .comment("Useful when windows overlap: the status bar often stays visible when the header does not.")
            }
        }

        group("Cascade") {
            row("Window width:") {
                intTextField(0..20_000).bindIntText(state::cascadeWidth).columns(6)
            }
            row("Window height:") {
                intTextField(0..20_000).bindIntText(state::cascadeHeight).columns(6)
                    .comment(
                        "Pixels each window is resized to. 0 uses everything the screen has left " +
                            "once the offsets are spent. A size larger than that is capped.",
                    )
            }
            row("Horizontal offset:") {
                intTextField(0..400).bindIntText(state::cascadeOffsetX).columns(4)
            }
            row("Vertical offset:") {
                intTextField(0..400).bindIntText(state::cascadeOffsetY).columns(4)
                    .comment("Step between two windows. The stack sits top aligned and centred horizontally.")
            }
            row {
                checkBox("Most urgent window to the front")
                    .bindSelected(state::cascadeSortsByState)
                    .comment("Otherwise the cascade keeps the order in which the projects were opened.")
            }
        }

        group("Source") {
            row("Status directory:") {
                textField().bindText(state::statusDirectory).columns(40)
                    .comment(
                        "Where the Claude Code hooks write their session files. " +
                            "Empty means ${ChappeSettings.defaultStatusDirectory()}",
                    )
            }
            row("Poll interval:") {
                intTextField(200..10_000).bindIntText(state::pollIntervalMillis).columns(6)
                    .comment("Milliseconds.")
            }
        }
    }

    override fun apply() {
        super.apply()
        ClaudeStatusStore.getInstance().restart()
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { ProjectWindowStatus.getInstance(it).refresh() }
    }

    private fun hex(panel: ColorPanel, fallback: Color): String =
        ChappeSettings.toHex(panel.selectedColor ?: fallback)
}
