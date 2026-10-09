package com.iceberg.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Top strip: shows up to three suggestions, or a toolbar when there is nothing to suggest.
 * Shortcut expansions are shown with a ✦ marker; the item that space will insert is drawn bold.
 */
class SuggestionStripView(context: Context) : View(context) {

    enum class ItemKind { SHORTCUT, WORD, TYPED }

    data class Item(val text: String, val kind: ItemKind, val primary: Boolean = false, val display: String = text)

    enum class ToolbarAction(val glyph: String) { EMOJI("☺"), PASTE("📋"), SETTINGS("⚙"), SWITCH_KEYBOARD("🌐"), HIDE("⌄") }

    interface Listener {
        fun onSuggestionPicked(item: Item)
        fun onSuggestionLongPressed(item: Item)
        fun onToolbarAction(action: ToolbarAction)
    }

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.resolve(context, "system")
        set(value) { field = value; invalidate() }

    private var items: List<Item> = emptyList()
    private val toolbar = ToolbarAction.entries
    private var pressedIndex = -1
    private var downTime = 0L

    private val heightPx = dp(44f).toInt()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = dp(17f) }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressRect = RectF()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    fun setItems(newItems: List<Item>) {
        if (newItems == items) return
        items = newItems
        pressedIndex = -1
        invalidate()
    }

    val showingToolbar get() = items.isEmpty()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx)
    }

    private fun slotCount() = if (items.isEmpty()) toolbar.size else 3

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.strip)
        val n = slotCount()
        val slotW = width.toFloat() / n
        if (pressedIndex in 0 until n) {
            bgPaint.color = theme.keyPressed
            pressRect.set(pressedIndex * slotW + dp(4f), dp(4f), (pressedIndex + 1) * slotW - dp(4f), height - dp(4f))
            canvas.drawRoundRect(pressRect, dp(8f), dp(8f), bgPaint)
        }
        val cy = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        if (items.isEmpty()) {
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = theme.hint
            toolbar.forEachIndexed { i, a -> canvas.drawText(a.glyph, slotW * (i + 0.5f), cy, textPaint) }
            return
        }
        for (i in 0 until 3) {
            val item = items.getOrNull(i) ?: continue
            textPaint.typeface = if (item.primary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textPaint.color = when (item.kind) {
                ItemKind.SHORTCUT -> theme.accent
                else -> theme.stripText
            }
            val label = if (item.kind == ItemKind.SHORTCUT) "✦ " + item.display.replace('\n', ' ') else item.display
            val fitted = TextUtils.ellipsize(label, textPaint, slotW - dp(12f), TextUtils.TruncateAt.END)
            canvas.drawText(fitted, 0, fitted.length, slotW * (i + 0.5f), cy, textPaint)
            if (i > 0) {
                bgPaint.color = theme.divider
                canvas.drawRect(slotW * i - dp(0.5f), height * 0.3f, slotW * i + dp(0.5f), height * 0.7f, bgPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = slotCount()
        val idx = (e.x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedIndex = idx; downTime = e.eventTime; invalidate() }
            MotionEvent.ACTION_MOVE -> if (idx != pressedIndex) { pressedIndex = -1; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (pressedIndex == idx) {
                    val longPress = e.eventTime - downTime > 500
                    if (items.isEmpty()) listener?.onToolbarAction(toolbar[idx])
                    else items.getOrNull(idx)?.let { if (longPress) listener?.onSuggestionLongPressed(it) else listener?.onSuggestionPicked(it) }
                }
                pressedIndex = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedIndex = -1; invalidate() }
        }
        return true
    }
}
