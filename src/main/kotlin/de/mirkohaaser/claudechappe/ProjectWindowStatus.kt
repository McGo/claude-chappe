package de.mirkohaaser.claudechappe

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.JFrame

/**
 * Decides which colour this project's window carries and keeps it up to date.
 */
@Service(Service.Level.PROJECT)
class ProjectWindowStatus(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): ProjectWindowStatus = project.service()
    }

    /**
     * Wall clock millis of the last time the user looked at this window. A
     * finished session older than this counts as seen and stops asking for
     * attention.
     */
    @Volatile
    private var lastSeenAt: Long = System.currentTimeMillis()

    @Volatile
    var tint: WindowTint = WindowTint.NONE
        private set

    private var focusListener: WindowFocusListener? = null

    fun start() {
        installFocusListener()
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(ClaudeStatusStore.TOPIC, ClaudeStatusListener { refresh() })
        ClaudeStatusStore.getInstance().ensureRunning()
        refresh()
    }

    private fun installFocusListener() {
        val frame = frame() ?: return
        val listener = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) = markSeen()

            // Also on losing focus: the user was looking at this window right
            // up to the moment they left it.
            override fun windowLostFocus(e: WindowEvent?) = markSeen()
        }
        frame.addWindowFocusListener(listener)
        focusListener = listener
    }

    private fun markSeen() {
        lastSeenAt = System.currentTimeMillis()
        refresh()
    }

    fun refresh() {
        val settings = ChappeSettings.getInstance()
        val next = computeTint(settings)
        tint = next
        val color = next.color(settings)
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            val windowManager = WindowManager.getInstance()
            WindowTinter.apply(frame(), windowManager.getStatusBar(project), color, settings)
        }, project.disposed)
    }

    private fun computeTint(settings: ChappeSettings): WindowTint {
        // While the window is up front the user is looking at it, so a session
        // finishing right now must not paint it red.
        if (frame()?.isActive == true) lastSeenAt = System.currentTimeMillis()

        val sessions = ClaudeStatusStore.getInstance().sessionsUnder(project.basePath)
        if (sessions.isEmpty()) return WindowTint.NONE

        // Attention wins over work in progress: a window you have to visit is
        // more urgent than one that is busy on its own.
        if (sessions.any { it.state == ClaudeState.WAITING }) return WindowTint.ATTENTION

        if (settings.state.idleCountsAsAttention) {
            val unseenIdle = sessions.any { session ->
                session.state == ClaudeState.IDLE &&
                    (!settings.state.clearAttentionOnFocus || session.timestamp * 1000L > lastSeenAt)
            }
            if (unseenIdle) return WindowTint.ATTENTION
        }

        if (sessions.any { it.state == ClaudeState.WORKING }) return WindowTint.WORKING
        return WindowTint.NONE
    }

    private fun frame(): JFrame? = WindowManager.getInstance().getFrame(project)

    override fun dispose() {
        val frame = frame()
        focusListener?.let { frame?.removeWindowFocusListener(it) }
        focusListener = null
        WindowTinter.reset(frame, WindowManager.getInstance().getStatusBar(project))
    }
}
