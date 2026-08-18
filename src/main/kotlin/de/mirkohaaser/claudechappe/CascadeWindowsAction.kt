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

        val width = (screen.width - steps * offsetX).coerceAtLeast(minimumSize.width)
        val height = (screen.height - steps * offsetY).coerceAtLeast(minimumSize.height)

        ordered.forEachIndexed { index, (_, frame) ->
            if (frame.extendedState and Frame.ICONIFIED != 0) frame.extendedState = Frame.NORMAL
            // MAXIMIZED windows ignore setBounds until the state is cleared.
            if (frame.extendedState and Frame.MAXIMIZED_BOTH != 0) frame.extendedState = Frame.NORMAL
            frame.setBounds(
                screen.x + index * offsetX,
                screen.y + index * offsetY,
                width,
                height,
            )
        }

        // Raise back to front: the least urgent window first, so the most
        // urgent one ends up on top and fully visible in the upper left.
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
