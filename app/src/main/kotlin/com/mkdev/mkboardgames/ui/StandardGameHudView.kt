package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.mkdev.mkboardgames.SoundPlayer

/**
 * Shared top HUD used by the board games.
 *
 * The button geometry intentionally matches the HUD in GameActivity and
 * ConnectFourActivity so every game presents Back, Undo, Redo, and Menu in
 * the same places.
 */
class StandardGameHudView(
    context: Context,
    private val showHistoryControls: Boolean = true,
    labelTextSizeSp: Float = 15f,
    private val labelOffsetDp: Float = 0f,
    private val backLabel: String = "← Back",
    sideLabel: String = "",
    private val menuLabel: String = "Menu",
    private val showHintControl: Boolean = false,
    private val showMenuControl: Boolean = true,
    private val stackInfoBelowControls: Boolean = false,
) : View(context) {
    var onBack: (() -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onHint: (() -> Unit)? = null
    var controlsEnabled: Boolean = true

    private var label = ""
    private var canUndo = false
    private var canRedo = false
    private var detail = ""
    private var labelColor = Color.WHITE
    private var sideLabelText = sideLabel
    private var hintActive = false
    private var hintRotation = 0f
    private var hintAnimator: ValueAnimator? = null

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val bgPaint = Paint().apply { color = Color.parseColor("#102C32") }
    private val divPaint = Paint().apply { color = Color.parseColor("#203E42") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = labelTextSizeSp * sp.coerceAtMost(3f)
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BFD0C6")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val sideLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BFD0C6")
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
        textSize = 9f * sp.coerceAtMost(3f)
    }
    private val disabledButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7D776C")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34261B")
    }
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp
        color = Color.parseColor("#D3A05F")
    }

    private val backRect = RectF()
    private val undoRect = RectF()
    private val redoRect = RectF()
    private val hintRect = RectF()
    private val menuRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = if (showHintControl) {
            "Chess controls. Back button. Hint button."
        } else {
            "Game controls. Back button."
        }
    }

    override fun onInitializeAccessibilityNodeInfo(
        info: android.view.accessibility.AccessibilityNodeInfo,
    ) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = StandardGameHudView::class.java.name
        info.isClickable = true
        info.text = contentDescription
    }

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

    fun setSideLabel(value: String) {
        sideLabelText = value
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldWidth: Int, oldHeight: Int) {
        val buttonWidth = 44f * dp
        val buttonHeight = 28f * dp
        val top = if (stackInfoBelowControls) 6f * dp else (h - buttonHeight) / 2f
        backRect.set(6f * dp, top, 6f * dp + buttonWidth, top + buttonHeight)
        if (showHistoryControls) {
            undoRect.set(w - buttonWidth * 3.3f, top, w - buttonWidth * 2.2f, top + buttonHeight)
            redoRect.set(w - buttonWidth * 2.15f, top, w - buttonWidth * 1.1f, top + buttonHeight)
        } else {
            undoRect.setEmpty()
            redoRect.setEmpty()
        }
        if (showHintControl) {
            if (showMenuControl) {
                hintRect.set(w - buttonWidth * 2.15f, top, w - buttonWidth * 1.1f, top + buttonHeight)
            } else {
                hintRect.set(w - buttonWidth * 1.05f, top, w - 4f * dp, top + buttonHeight)
            }
        } else {
            hintRect.setEmpty()
        }
        if (showMenuControl) {
            menuRect.set(w - buttonWidth * 1.05f, top, w - 4f * dp, top + buttonHeight)
        } else {
            menuRect.setEmpty()
        }
    }

    fun setHintActive(active: Boolean, animate: Boolean = true) {
        hintActive = active
        val target = if (active) 180f else 0f
        hintAnimator?.cancel()
        if (!animate) {
            hintRotation = target
            invalidate()
            return
        }
        hintAnimator = ValueAnimator.ofFloat(hintRotation, target).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                hintRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (!controlsEnabled) return true
            when {
                backRect.contains(event.x, event.y) -> {
                    SoundPlayer.play("ui_click")
                    onBack?.invoke()
                }
                showHistoryControls && undoRect.contains(event.x, event.y) && canUndo -> {
                    SoundPlayer.play("ui_click")
                    onUndo?.invoke()
                }
                showHistoryControls && redoRect.contains(event.x, event.y) && canRedo -> {
                    SoundPlayer.play("ui_click")
                    onRedo?.invoke()
                }
                showHintControl && hintRect.contains(event.x, event.y) -> {
                    SoundPlayer.play("ui_click")
                    setHintActive(!hintActive)
                    onHint?.invoke()
                }
                showMenuControl && menuRect.contains(event.x, event.y) -> {
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
        val controls = if (showHistoryControls) {
            listOf(backRect, undoRect, redoRect) +
                (if (showMenuControl) listOf(menuRect) else emptyList())
        } else {
            listOf(backRect) +
                (if (showHintControl) listOf(hintRect) else emptyList()) +
                (if (showMenuControl) listOf(menuRect) else emptyList())
        }
        controls.forEach {
            canvas.drawRoundRect(it, radius, radius, buttonBackgroundPaint)
            canvas.drawRoundRect(it, radius, radius, buttonBorderPaint)
        }
        canvas.drawText(backLabel, backRect.centerX(), backRect.centerY() + buttonPaint.textSize * 0.36f, buttonPaint)
        if (sideLabelText.isNotEmpty()) {
            canvas.drawText(
                sideLabelText,
                backRect.right + 9f * dp,
                backRect.centerY() + sideLabelPaint.textSize * 0.36f,
                sideLabelPaint,
            )
        }
        if (showHistoryControls) {
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
        }
        if (showHintControl) {
            canvas.save()
            canvas.rotate(hintRotation, hintRect.centerX(), hintRect.centerY())
            canvas.drawText("💡", hintRect.centerX(), hintRect.centerY() + buttonPaint.textSize * 0.36f, buttonPaint)
            canvas.restore()
        }
        if (showMenuControl) {
            canvas.drawText(menuLabel, menuRect.centerX(), menuRect.centerY() + buttonPaint.textSize * 0.36f, buttonPaint)
        }

        textPaint.color = labelColor
        val centerX = width / 2f
        val labelBaseline = if (stackInfoBelowControls) {
            backRect.bottom + textPaint.textSize * 1.15f
        } else {
            height / 2f - textPaint.textSize * 0.15f
        }
        canvas.drawText(
            label,
            centerX + labelOffsetDp * dp,
            labelBaseline,
            textPaint,
        )
        if (detail.isNotEmpty()) {
            val detailBaseline = if (stackInfoBelowControls) {
                labelBaseline + detailPaint.textSize * 1.15f
            } else {
                height / 2f + detailPaint.textSize * 1.1f
            }
            canvas.drawText(
                detail,
                centerX + labelOffsetDp * dp,
                detailBaseline,
                detailPaint,
            )
        }
    }
}