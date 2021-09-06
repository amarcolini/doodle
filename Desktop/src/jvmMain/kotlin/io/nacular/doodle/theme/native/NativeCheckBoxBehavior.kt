package io.nacular.doodle.theme.native

import io.nacular.doodle.controls.buttons.Button
import io.nacular.doodle.controls.buttons.ToggleButton
import io.nacular.doodle.drawing.TextMetrics
import io.nacular.doodle.focus.FocusManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skiko.SkiaWindow
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import kotlin.coroutines.CoroutineContext

internal class JCheckBoxPeer(focusManager: FocusManager?, button: ToggleButton): JCheckBox(), AbstractNativeButtonBehavior.Peer {
    private val button: Button? = button

    override var ignoreSelectionChange = false
    override var selected_ get() = isSelected; set(value) { isSelected = value }

    init {
        text       = button.text
        isSelected = button.selected

        addFocusListener(object: FocusListener {
            override fun focusGained(e: FocusEvent?) { focusManager?.requestFocus(button) }
            override fun focusLost  (e: FocusEvent?) {
                if (button == focusManager?.focusOwner) { focusManager.clearFocus() }
            }
        })

        addActionListener { if (!ignoreSelectionChange) button.click() }
    }

    override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) {
        button?.rerender()
    }

    override fun handleMouseEvent(e: MouseEvent?) {
        processMouseEvent(e)
    }
}

internal class NativeCheckBoxBehavior(
        window                   : SkiaWindow,
        appScope                 : CoroutineScope,
        uiDispatcher             : CoroutineContext,
        swingGraphicsFactory     : SwingGraphicsFactory,
        textMetrics              : TextMetrics,
        swingFocusManager        : javax.swing.FocusManager,
        focusManager             : FocusManager?,
        nativePointerPreprocessor: NativePointerPreprocessor?
): AbstractNativeButtonBehavior<ToggleButton, JCheckBoxPeer>(window, appScope, uiDispatcher, textMetrics, swingGraphicsFactory, swingFocusManager, focusManager, nativePointerPreprocessor) {
    override fun createPeer(button: ToggleButton) = JCheckBoxPeer(focusManager, button)
}