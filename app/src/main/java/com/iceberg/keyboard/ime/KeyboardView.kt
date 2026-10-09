package com.iceberg.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-drawn keyboard. Drawing is allocation-free; touch handling supports:
 *  - multi-touch rollover (a new finger commits the previous finger's key immediately)
 *  - sliding between keys before release
 *  - long-press alternates popup with slide-to-select
 *  - backspace auto-repeat (on press, then accelerating)
 *  - horizontal drag on the space bar to move the cursor
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** Finger went down on a key (haptics/sound; backspace acts here for responsiveness). */
        fun onKeyDown(key: Key)
        /** Key released / rolled over. [text] is a chosen long-press alternate, or null for the key itself. */
        fun onKeyCommit(key: Key, text: String?)
        /** Long-press on a key without alternates. Return true if handled. */
        fun onKeyLongPress(key: Key): Boolean
        fun onBackspaceRepeat(count: Int)
        fun onCursorMove(delta: Int)
    }

    var listener: Listener? = null

    var keyboardLayout: KeyboardLayout? = null
        set(value) {
            if (field === value) return
            cancelAllPointers()
            field = value
            requestLayout()
            computeGeometry()
            invalidate()
        }

    var theme: KeyboardTheme = KeyboardTheme.resolve(context, "system")
        set(value) { field = value; invalidate() }

    var shifted = false
        set(value) { if (field != value) { field = value; invalidate() } }
    var capsLocked = false
        set(value) { if (field != value) { field = value; invalidate() } }
    var enterLabel = "↵"
        set(value) { if (field != value) { field = value; invalidate() } }
    var showHints = true
        set(value) { if (field != value) { field = value; invalidate() } }
    var spaceCursorEnabled = true
    var longPressMs = 300L

    var rowHeightPx: Int = dp(54f).toInt()
        set(value) { if (field != value) { field = value; requestLayout() } }

    private val hGap = dp(5f)
    private val vGap = dp(7f)
    private val padH = dp(3f)
    private val padV = dp(4f)
    private val radius = dp(7f)
    private val cursorStepPx = dp(12f)
    private val touchSlop = dp(14f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val popupRect = RectF()
    private val optionRect = RectF()

    private val handler = Handler(Looper.getMainLooper())

    private class Popup(val key: Key, val options: List<String>, val left: Float, val top: Float, val optionWidth: Float, val height: Float) {
        var selected = 0
    }

    private inner class Pointer(val id: Int) {
        var key: Key? = null
        var downX = 0f
        var lastCursorX = 0f
        var cursorMode = false
        var popup: Popup? = null
        var consumed = false
        var repeatCount = 0

        val longPress = Runnable { onLongPress(this) }
        val repeat = object : Runnable {
            override fun run() {
                if (key?.code != KeyCode.BACKSPACE) return
                repeatCount++
                listener?.onBackspaceRepeat(repeatCount)
                handler.postDelayed(this, if (repeatCount > 15) 90L else 45L)
            }
        }

        fun cancelTimers() {
            handler.removeCallbacks(longPress)
            handler.removeCallbacks(repeat)
        }
    }

    private val pointers = SparseArray<Pointer>()

    init {
        isHapticFeedbackEnabled = false // haptics are driven by the service so they can be tuned
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ---- Layout ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = keyboardLayout?.rows?.size ?: 4
        setMeasuredDimension(w, (rows * rowHeightPx + 2 * padV).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeGeometry()

    private fun computeGeometry() {
        val l = keyboardLayout ?: return
        if (width == 0) return
        val maxUnits = l.rows.maxOf { row -> row.sumOf { (it.width + it.leftGap).toDouble() } }.toFloat()
        val unit = (width - 2 * padH) / maxUnits
        val rowH = (height - 2 * padV) / l.rows.size
        l.rows.forEachIndexed { r, row ->
            var x = padH
            val top = padV + r * rowH
            row.forEachIndexed { i, key ->
                val cellLeft = x + key.leftGap * unit
                val cellRight = cellLeft + key.width * unit
                key.rect.set(cellLeft + hGap / 2, top + vGap / 2, cellRight - hGap / 2, top + rowH - vGap / 2)
                // Touch area: the full cell, with leading gaps and the row's trailing space given to the edge keys.
                key.touchLeft = if (i == 0) 0f else x
                key.touchRight = if (i == row.size - 1) width.toFloat() else cellRight
                x = cellRight
            }
        }
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val l = keyboardLayout ?: return null
        val rowH = (height - 2 * padV) / l.rows.size
        val r = ((y - padV) / rowH).toInt().coerceIn(0, l.rows.size - 1)
        val row = l.rows[r]
        return row.firstOrNull { x >= it.touchLeft && x < it.touchRight } ?: if (x < 0) row.first() else row.last()
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        val l = keyboardLayout ?: return
        canvas.drawColor(theme.background)
        val rowH = (height - 2 * padV) / l.rows.size
        val letterSize = min(rowH * 0.46f, dp(25f))
        val labelSize = min(rowH * 0.33f, dp(17f))
        for (row in l.rows) for (key in row) drawKey(canvas, key, letterSize, labelSize)
        for (i in 0 until pointers.size()) pointers.valueAt(i).popup?.let { drawPopup(canvas, it, letterSize) }
    }

    private fun isPressed(key: Key): Boolean {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            if (p.key === key && !p.consumed) return true
        }
        return false
    }

    private fun drawKey(canvas: Canvas, key: Key, letterSize: Float, labelSize: Float) {
        val pressed = isPressed(key)
        val special = key.code != KeyCode.CHAR && key.code != KeyCode.SPACE && key.code != KeyCode.SETTINGS_COMMA
        keyPaint.color = when {
            pressed -> theme.keyPressed
            key.code == KeyCode.ENTER -> theme.accent
            key.code == KeyCode.SHIFT && (shifted || capsLocked) -> theme.keyPressed
            special -> theme.specialKey
            else -> theme.key
        }
        canvas.drawRoundRect(key.rect, radius, radius, keyPaint)

        var label = key.label
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = if (key.code == KeyCode.ENTER) theme.onAccent else theme.text
        textPaint.textSize = labelSize
        when (key.code) {
            KeyCode.CHAR, KeyCode.SETTINGS_COMMA -> {
                if (key.isLetter && shifted) label = label.uppercase()
                textPaint.textSize = if (label.length == 1) letterSize else labelSize
            }
            KeyCode.SHIFT -> {
                label = if (capsLocked) "⇪" else "⇧"
                textPaint.textSize = letterSize
                if (shifted || capsLocked) textPaint.color = theme.accent
            }
            KeyCode.BACKSPACE -> textPaint.textSize = letterSize * 0.9f
            KeyCode.ENTER -> {
                label = enterLabel
                textPaint.textSize = if (label.length == 1) letterSize else labelSize * 0.9f
                textPaint.typeface = Typeface.DEFAULT_BOLD
            }
            KeyCode.SPACE -> {
                textPaint.textSize = labelSize * 0.8f
                textPaint.color = theme.hint
            }
            else -> textPaint.typeface = Typeface.DEFAULT_BOLD
        }
        val cy = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, key.rect.centerX(), cy, textPaint)

        if (showHints && key.hint != null && key.code != KeyCode.SPACE && key.code != KeyCode.ENTER) {
            hintPaint.color = theme.hint
            hintPaint.textSize = labelSize * 0.62f
            canvas.drawText(key.hint, key.rect.right - dp(4f), key.rect.top - hintPaint.ascent() + dp(2f), hintPaint)
        }
    }

    private fun drawPopup(canvas: Canvas, p: Popup, letterSize: Float) {
        popupRect.set(p.left, p.top, p.left + p.optionWidth * p.options.size, p.top + p.height)
        keyPaint.color = theme.popup
        keyPaint.setShadowLayer(dp(6f), 0f, dp(2f), 0x66000000)
        canvas.drawRoundRect(popupRect, radius, radius, keyPaint)
        keyPaint.clearShadowLayer()
        textPaint.typeface = Typeface.DEFAULT
        p.options.forEachIndexed { i, opt ->
            optionRect.set(p.left + i * p.optionWidth, p.top, p.left + (i + 1) * p.optionWidth, p.top + p.height)
            if (i == p.selected) {
                keyPaint.color = theme.popupSelected
                canvas.drawRoundRect(optionRect, radius, radius, keyPaint)
            }
            val text = if (shifted && p.key.isLetter) opt.uppercase() else opt
            textPaint.color = if (i == p.selected) theme.onAccent else theme.text
            textPaint.textSize = if (text.length == 1) letterSize else letterSize * 0.6f
            val cy = optionRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, optionRect.centerX(), cy, textPaint)
        }
    }

    // ---- Touch ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                rollOverActivePointers()
                pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                pointers.get(e.getPointerId(i))?.let { pointerMove(it, e.getX(i), e.getY(i)) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                pointers.get(e.getPointerId(idx))?.let { pointerUp(it) }
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
        }
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float) {
        val key = keyAt(x, y) ?: return
        val p = Pointer(id).apply { this.key = key; downX = x; lastCursorX = x }
        pointers.put(id, p)
        listener?.onKeyDown(key)
        if (key.code == KeyCode.BACKSPACE) {
            p.consumed = false
            handler.postDelayed(p.repeat, 380L)
        } else {
            handler.postDelayed(p.longPress, longPressMs)
        }
        invalidate()
    }

    private fun pointerMove(p: Pointer, x: Float, y: Float) {
        if (p.consumed && p.popup == null && !p.cursorMode) return
        p.popup?.let { popup ->
            val idx = ((x - popup.left) / popup.optionWidth).toInt().coerceIn(0, popup.options.size - 1)
            if (idx != popup.selected) { popup.selected = idx; invalidate() }
            return
        }
        val key = p.key
        if (key?.code == KeyCode.SPACE && spaceCursorEnabled) {
            if (!p.cursorMode && abs(x - p.downX) > touchSlop) {
                p.cursorMode = true
                p.lastCursorX = x
                handler.removeCallbacks(p.longPress)
            }
            if (p.cursorMode) {
                val steps = ((x - p.lastCursorX) / cursorStepPx).toInt()
                if (steps != 0) {
                    p.lastCursorX += steps * cursorStepPx
                    listener?.onCursorMove(steps)
                }
            }
            return
        }
        if (key?.code == KeyCode.BACKSPACE) return
        val now = keyAt(x, y)
        if (now !== key) {
            p.key = now
            handler.removeCallbacks(p.longPress)
            if (now != null && now.code != KeyCode.BACKSPACE) handler.postDelayed(p.longPress, longPressMs)
            invalidate()
        }
    }

    private fun pointerUp(p: Pointer) {
        p.cancelTimers()
        pointers.remove(p.id)
        val key = p.key
        if (key != null && !p.consumed && !p.cursorMode) {
            val popup = p.popup
            when {
                popup != null -> listener?.onKeyCommit(key, popup.options[popup.selected])
                key.code == KeyCode.BACKSPACE -> Unit // acted on down
                else -> listener?.onKeyCommit(key, null)
            }
        }
        invalidate()
    }

    /** Fast typists press the next key before releasing the previous one; commit the earlier key now. */
    private fun rollOverActivePointers() {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            val key = p.key ?: continue
            if (p.consumed || p.popup != null || p.cursorMode) continue
            if (key.code == KeyCode.CHAR || key.code == KeyCode.SPACE || key.code == KeyCode.SETTINGS_COMMA) {
                p.cancelTimers()
                listener?.onKeyCommit(key, null)
                p.consumed = true
            }
        }
    }

    private fun onLongPress(p: Pointer) {
        val key = p.key ?: return
        if (p.consumed || p.cursorMode) return
        if (key.alternates.isNotEmpty()) {
            p.popup = makePopup(key)
            listener?.onKeyDown(key) // haptic tick for the popup
            invalidate()
        } else if (listener?.onKeyLongPress(key) == true) {
            p.consumed = true
            invalidate()
        }
    }

    private fun makePopup(key: Key): Popup {
        val optionW = max(key.rect.width(), dp(34f))
        val h = key.rect.height() * 1.1f
        val total = optionW * key.alternates.size
        val left = (key.rect.left).coerceAtMost(width - padH - total).coerceAtLeast(padH)
        val top = max(0f, key.rect.top - h - dp(4f))
        return Popup(key, key.alternates, left, top, optionW, h)
    }

    private fun cancelAllPointers() {
        for (i in 0 until pointers.size()) pointers.valueAt(i).cancelTimers()
        pointers.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAllPointers()
    }
}
