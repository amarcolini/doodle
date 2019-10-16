package com.nectar.doodle.controls.theme.basic.list

import com.nectar.doodle.controls.ToStringItemVisualizer
import com.nectar.doodle.controls.list.List
import com.nectar.doodle.controls.list.ListBehavior
import com.nectar.doodle.controls.list.ListBehavior.RowGenerator
import com.nectar.doodle.controls.list.ListBehavior.RowPositioner
import com.nectar.doodle.controls.passThrough
import com.nectar.doodle.controls.theme.basic.ListPositioner
import com.nectar.doodle.controls.theme.basic.ListRow
import com.nectar.doodle.controls.theme.basic.SelectableListKeyHandler
import com.nectar.doodle.core.View
import com.nectar.doodle.drawing.Canvas
import com.nectar.doodle.drawing.Color
import com.nectar.doodle.drawing.Color.Companion.green
import com.nectar.doodle.drawing.Color.Companion.lightgray
import com.nectar.doodle.drawing.Color.Companion.white
import com.nectar.doodle.drawing.TextMetrics
import com.nectar.doodle.drawing.stripedBrush
import com.nectar.doodle.event.KeyEvent
import com.nectar.doodle.event.KeyListener
import com.nectar.doodle.event.MouseEvent
import com.nectar.doodle.event.MouseListener
import com.nectar.doodle.focus.FocusManager

/**
 * Created by Nicholas Eddy on 3/20/18.
 */

open class BasicItemGenerator<T>(private val focusManager         : FocusManager?,
                                 private val textMetrics          : TextMetrics,
                                 private val selectionColor       : Color? = green.lighter(),
                                 private val selectionBlurredColor: Color? = lightgray): RowGenerator<T> {
    override fun invoke(list: List<T, *>, row: T, index: Int, current: View?): View = when (current) {
        is ListRow<*> -> (current as ListRow<T>).apply { update(list, row, index) }
        else          -> ListRow(list, row, index, list.itemVisualizer ?: passThrough(ToStringItemVisualizer(textMetrics)), selectionColor, selectionBlurredColor).apply {
            mouseChanged += object: MouseListener {
                override fun mouseReleased(event: MouseEvent) {
                    focusManager?.requestFocus(list)
                }
            }
        }
    }
}

private class BasicListPositioner<T>(height: Double): ListPositioner(height), RowPositioner<T> {
    override fun rowFor(list: List<T, *>, y: Double) = super.rowFor(list.insets, y)

    override fun invoke(list: List<T, *>, row: T, index: Int) = super.invoke(list, list.insets, index)
}

open class BasicListBehavior<T>(override val generator   : RowGenerator<T>,
                                             evenRowColor: Color? = white,
                                             oddRowColor : Color? = lightgray.lighter().lighter(),
                                             rowHeight   : Double = 20.0): ListBehavior<T>, KeyListener, SelectableListKeyHandler {
    constructor(focusManager         : FocusManager?,
                textMetrics          : TextMetrics,
                evenRowColor         : Color? = white,
                oddRowColor          : Color? = lightgray.lighter().lighter(),
                selectionColor       : Color? = Color.green.lighter(),
                selectionBlurredColor: Color? = lightgray): this(BasicItemGenerator(focusManager, textMetrics, selectionColor, selectionBlurredColor), evenRowColor, oddRowColor)

    private val patternBrush = if (evenRowColor != null || oddRowColor != null) stripedBrush(rowHeight, evenRowColor, oddRowColor) else null

    override val positioner: RowPositioner<T> = BasicListPositioner(rowHeight)

    override fun install(view: List<T, *>) {
        view.keyChanged += this

        view.rerender()
    }

    override fun uninstall(view: List<T, *>) {
        view.keyChanged -= this
    }

    override fun render(view: List<T, *>, canvas: Canvas) {
        patternBrush?.let { canvas.rect(view.bounds.atOrigin, it) }
    }

    override fun keyPressed(event: KeyEvent) {
        super<SelectableListKeyHandler>.keyPressed(event)
    }
}