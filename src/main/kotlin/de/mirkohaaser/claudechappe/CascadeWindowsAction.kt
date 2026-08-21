package de.mirkohaaser.claudechappe

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.JFrame

/**
 * Stacks every open project window into a cascade on one screen.
 *
 * The stack sits flush with the top of the screen and is centred horizontally,
 * so it stays where you expect it on a wide screen instead of clinging to the
 * left edge.
 *
 * All project windows of one IDE live in the same process, so the plugin can
 * reach and place them directly - no accessibility permissions, no window
 * manager scripting.
 */
class CascadeWindowsAction : AnAction() {

    /** Smallest window the cascade will produce, whatever the offsets say. */
    private val minimumSize = Rectangle(0, 0, 900, 600)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = openFrames().size > 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = ChappeSettings.getInstance()
        val frames = openFrames()
        if (frames.isEmpty()) return

        val ordered = if (settings.state.cascadeSortsByState) {
            frames.sortedWith(
                compareByDescending<Pair<Project, JFrame>> { urgency(it.first) }
                    .thenBy { it.first.name.lowercase() },
            )
        } else {
            frames
        }

        val screen = usableBounds(e.project?.let { WindowManager.getInstance().getFrame(it) } ?: ordered.first().second)
        val offsetX = settings.state.cascadeOffsetX.coerceIn(0, 400)
        val offsetY = settings.state.cascadeOffsetY.coerceIn(0, 400)
        val steps = ordered.size - 1

        // What the screen still has room for once the offsets are spent - the
        // ceiling for the configured size, and the size itself when that is 0.
        val fitWidth = (screen.width - steps * offsetX).coerceAtLeast(minimumSize.width)
        val fitHeight = (screen.height - steps * offsetY).coerceAtLeast(minimumSize.height)
        val width = settings.state.cascadeWidth
            .takeIf { it > 0 }?.coerceIn(minimumSize.width, fitWidth) ?: fitWidth
        val height = settings.state.cascadeHeight
            .takeIf { it > 0 }?.coerceIn(minimumSize.height, fitHeight) ?: fitHeight

        // Place the stack as a whole: centred horizontally, top aligned.
        val stackWidth = width + steps * offsetX
        val originX = screen.x + ((screen.width - stackWidth) / 2).coerceAtLeast(0)
        val originY = screen.y

        ordered.forEachIndexed { index, (_, frame) ->
            if (frame.extendedState and Frame.ICONIFIED != 0) frame.extendedState = Frame.NORMAL
            // MAXIMIZED windows ignore setBounds until the state is cleared.
            if (frame.extendedState and Frame.MAXIMIZED_BOTH != 0) frame.extendedState = Frame.NORMAL
            frame.setBounds(
                originX + index * offsetX,
                originY + index * offsetY,
                width,
                height,
            )
        }

        // Raise back to front: the least urgent window first, so the most
        // urgent one ends up on top and fully visible.
        ordered.asReversed().forEach { (_, frame) -> frame.toFront() }
        ordered.firstOrNull()?.second?.requestFocus()
    }

    private fun urgency(project: Project): Int =
        when (ProjectWindowStatus.getInstance(project).tint) {
            WindowTint.ATTENTION -> 2
            WindowTint.WORKING -> 1
            WindowTint.NONE -> 0
        }

    private fun openFrames(): List<Pair<Project, JFrame>> {
        val windowManager = WindowManager.getInstance()
        return ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .mapNotNull { project -> windowManager.getFrame(project)?.let { project to it } }
    }

    /** Screen area of [reference] minus dock, taskbar and menu bar. */
    private fun usableBounds(reference: JFrame?): Rectangle {
        val configuration: GraphicsConfiguration = reference?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = Rectangle(configuration.bounds)
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        bounds.x += insets.left
        bounds.y += insets.top
        bounds.width -= insets.left + insets.right
        bounds.height -= insets.top + insets.bottom
        return bounds
    }
}
