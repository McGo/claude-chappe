package de.mirkohaaser.claudechappe

import com.intellij.openapi.wm.StatusBar
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.border.Border

/**
 * Which colour a window should carry.
 */
enum class WindowTint {
    /** Nothing to show - the window keeps its normal look. */
    NONE,

    /** At least one session is processing. */
    WORKING,

    /** At least one session needs the user. */
    ATTENTION,
    ;

    fun color(settings: ChappeSettings): Color? = when (this) {
        NONE -> null
        WORKING -> settings.workingColor()
        ATTENTION -> settings.attentionColor()
    }
}

/**
 * Paints the tint onto an IDE frame.
 *
 * The border is installed once and only its colour changes afterwards. Swapping
 * the border itself would change the root pane insets on every state change and
 * force a full re-layout of the window, which is very visible with a stack of
 * open projects.
 */
object WindowTinter {

    /** Blend factor for the status bar so its text stays readable. */
    private const val STATUS_BAR_BLEND = 0.78f

    private const val BORDER_KEY = "claudeChappe.border"
    private const val STATUS_BAR_BASE_KEY = "claudeChappe.statusBarBaseBackground"
    private const val STATUS_BAR_TOUCHED_KEY = "claudeChappe.statusBarTouched"

    fun apply(frame: JFrame?, statusBar: StatusBar?, color: Color?, settings: ChappeSettings) {
        applyBorder(frame, color.takeIf { settings.state.paintBorder }, settings.borderThickness)
        applyStatusBar(statusBar, color.takeIf { settings.state.tintStatusBar })
    }

    /** Removes every trace of the plugin from the frame, e.g. on project close. */
    fun reset(frame: JFrame?, statusBar: StatusBar?) {
        borderOf(frame)?.let {
            it.color = null
            frame?.rootPane?.repaint()
        }
        val component = statusBar?.component ?: return
        if (component.getClientProperty(STATUS_BAR_TOUCHED_KEY) != true) return
        val base = component.getClientProperty(STATUS_BAR_BASE_KEY) as? Color
        component.background = base
        component.isOpaque = base != null
        component.putClientProperty(STATUS_BAR_TOUCHED_KEY, null)
        component.repaint()
    }

    private fun applyBorder(frame: JFrame?, color: Color?, thickness: Int) {
        val rootPane = frame?.rootPane ?: return
        var border = borderOf(frame)
        if (border == null) {
            // Nothing else sets a root pane border in the IDE, but if something
            // did we would rather leave it alone than fight over it.
            if (rootPane.border != null) return
            border = TintBorder(thickness)
            rootPane.border = border
            rootPane.putClientProperty(BORDER_KEY, border)
        }
        if (border.color == color && border.thickness == thickness) return
        val insetsChanged = border.thickness != thickness
        border.color = color
        border.thickness = thickness
        if (insetsChanged) rootPane.revalidate()
        rootPane.repaint()
    }

    private fun borderOf(frame: JFrame?): TintBorder? =
        frame?.rootPane?.getClientProperty(BORDER_KEY) as? TintBorder

    private fun applyStatusBar(statusBar: StatusBar?, color: Color?) {
        val component = statusBar?.component ?: return
        if (component.getClientProperty(STATUS_BAR_BASE_KEY) == null) {
            component.putClientProperty(STATUS_BAR_BASE_KEY, component.background)
        }
        val base = component.getClientProperty(STATUS_BAR_BASE_KEY) as? Color
        val target = if (color == null) base else blend(color, base, STATUS_BAR_BLEND)
        if (component.background == target && component.getClientProperty(STATUS_BAR_TOUCHED_KEY) == (color != null)) {
            return
        }
        component.background = target
        component.isOpaque = target != null
        component.putClientProperty(STATUS_BAR_TOUCHED_KEY, color != null)
        repaintTree(component)
    }

    /**
     * Status bar widgets are separate opaque components, so repainting the
     * container alone leaves them in the old colour.
     */
    private fun repaintTree(component: JComponent) {
        component.repaint()
        for (child in component.components) {
            if (child is JComponent) repaintTree(child) else child.repaint()
        }
    }

    private fun blend(color: Color, base: Color?, factor: Float): Color {
        if (base == null) return color
        val inverse = 1f - factor
        return Color(
            (color.red * factor + base.red * inverse).toInt().coerceIn(0, 255),
            (color.green * factor + base.green * inverse).toInt().coerceIn(0, 255),
            (color.blue * factor + base.blue * inverse).toInt().coerceIn(0, 255),
        )
    }

    private class TintBorder(var thickness: Int) : Border {
        var color: Color? = null

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val paint = color ?: return
            val t = thickness
            g.color = paint
            g.fillRect(x, y, width, t)
            g.fillRect(x, y + height - t, width, t)
            g.fillRect(x, y + t, t, height - 2 * t)
            g.fillRect(x + width - t, y + t, t, height - 2 * t)
        }

        override fun getBorderInsets(c: Component): Insets =
            Insets(thickness, thickness, thickness, thickness)

        override fun isBorderOpaque(): Boolean = false
    }
}
