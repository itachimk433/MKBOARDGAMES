package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer

/**
 * Shared top HUD used by the board games.
 *
 * The button geometry intentionally matches the HUD in GameActivity and
 * ConnectFourActivity so every game presents Back, Undo, Redo, and Menu in
 * the same places.
 */
class StandardGameHudView(context: Context) : View(context) {
    var onBack: (() -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    private var label = ""
    private var canUndo = false
    private var canRedo = false
    private var detail = ""
    private var labelColor = Color.WHITE

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A1A") }
    private val divPaint = Paint().apply { color = Color.parseColor("#2A2A2A") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 15f * sp.coerceAtMost(3f)
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val disabledButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#252525")
    }

    private val backRect = RectF()
    private val undoRect = RectF()
    private val redoRect = RectF()
    private val menuRect = RectF()

    fun setInfo(
        value: String,
        undo: Boolean,
        redo: Boolean = false,
        detail: String = "",
        accentColor: Int = Color.WHITE,
    ) {
        label = value
        canUndo = undo
        canRedo = redo
        this.detail = detail
        labelColor = accentColor
        invalidate()
    }

    fun setThinking(value: Boolean) {
        detail = if (value) "Thinking…" else ""
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldWidth: Int, oldHeight: Int) {
        val buttonWidth = 44f * dp
        val buttonHeight = 28f * dp
        val top = (h - buttonHeight) / 2f
        backRect.set(6f * dp, top, 6f * dp + buttonWidth, top + buttonHeight)
        undoRect.set(w - buttonWidth * 3.3f, top, w - buttonWidth * 2.2f, top + buttonHeight)
        redoRect.set(w - buttonWidth * 2.15f, top, w - buttonWidth * 1.1f, top + buttonHeight)
        menuRect.set(w - buttonWidth * 1.05f, top, w - 4f * dp, top + buttonHeight)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            when {
                backRect.contains(event.x, event.y) -> {
                    SoundPlayer.play("ui_click")
                    onBack?.invoke()
                }
                undoRect.contains(event.x, event.y) && canUndo -> {
                    SoundPlayer.play("ui_click")
                    onUndo?.invoke()
                }
                redoRect.contains(event.x, event.y) && canRedo -> {
                    SoundPlayer.play("ui_click")
                    onRedo?.invoke()
                }
                menuRect.contains(event.x, event.y) -> {
                    SoundPlayer.play("ui_click")
                    onMenu?.invoke()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawRect(0f, height - dp, width.toFloat(), height.toFloat(), divPaint)
        val radius = 5f * dp
        listOf(backRect, undoRect, redoRect, menuRect).forEach {
            canvas.drawRoundRect(it, radius, radius, buttonBackgroundPaint)
        }
        canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + buttonPaint.textSize * 0.36f, buttonPaint)
        canvas.drawText(
            "Undo",
            undoRect.centerX(),
            undoRect.centerY() + buttonPaint.textSize * 0.36f,
            if (canUndo) buttonPaint else disabledButtonPaint,
        )
        canvas.drawText(
            "Redo",
            redoRect.centerX(),
            redoRect.centerY() + buttonPaint.textSize * 0.36f,
            if (canRedo) buttonPaint else disabledButtonPaint,
        )
        canvas.drawText("Menu", menuRect.centerX(), menuRect.centerY() + buttonPaint.textSize * 0.36f, buttonPaint)

        textPaint.color = labelColor
        val centerX = width / 2f
        canvas.drawText(label, centerX, height / 2f - textPaint.textSize * 0.15f, textPaint)
        if (detail.isNotEmpty()) {
            canvas.drawText(detail, centerX, height / 2f + detailPaint.textSize * 1.1f, detailPaint)
        }
    }
}