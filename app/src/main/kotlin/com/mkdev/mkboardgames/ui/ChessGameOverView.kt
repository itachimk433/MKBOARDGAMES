package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer

/**
 * Board-visible Chess result screen.
 *
 * The host keeps the finished board mounted underneath this transparent view,
 * so the result feels like the next state of the match rather than a generic
 * platform dialog.
 */
class ChessGameOverView(context: Context) : View(context) {

    var onPlayAgain: (() -> Unit)? = null
    var onMainMenu: (() -> Unit)? = null
    var onWatchReplay: (() -> Unit)? = null
    var resultMessage: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE7B0")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 235, 245, 255)
        textAlign = Paint.Align.CENTER
    }
    private val buttonLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6A2D1B")
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = arrayOf(RectF(), RectF(), RectF())
    private var pressedButton: Int? = null

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
        isClickable = true
        contentDescription = "Chess game over. Choose what to do next."
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val horizontalMargin = dp(20f)
        val buttonWidth = (width - horizontalMargin * 2f).coerceAtMost(dp(520f))
        val left = (width - buttonWidth) / 2f
        val buttonHeight = dp(62f)
        val gap = dp(10f)
        val bottom = height - dp(20f)
        for (index in buttons.indices) {
            val top = bottom - buttonHeight - (buttons.lastIndex - index) * (buttonHeight + gap)
            buttons[index].set(left, top, left + buttonWidth, top + buttonHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE || width <= 0 || height <= 0) return

        scrimPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            Color.argb(180, 4, 13, 24),
            Color.argb(35, 4, 13, 24),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        scrimPaint.shader = null

        titlePaint.textSize = dp(28f)
        titlePaint.setShadowLayer(dp(7f), 0f, dp(2f), Color.argb(230, 0, 0, 0))
        canvas.drawText("GAME OVER", width / 2f, dp(48f), titlePaint)
        titlePaint.clearShadowLayer()

        resultPaint.textSize = dp(18f)
        resultPaint.setShadowLayer(dp(5f), 0f, dp(2f), Color.argb(230, 0, 0, 0))
        drawCenteredWrappedText(
            canvas,
            resultMessage.ifBlank { "The match has ended." },
            width / 2f,
            dp(80f),
            dp(22f),
            resultPaint,
        )
        resultPaint.clearShadowLayer()

        hintPaint.textSize = dp(11f)
        canvas.drawText(
            "The final position remains on the board.",
            width / 2f,
            dp(125f),
            hintPaint,
        )

        val labels = arrayOf("Play Again", "Main Menu", "Watch Replay")
        val details = arrayOf(
            "Start a fresh game",
            "Choose another match",
            "Review every move",
        )
        val icons = arrayOf("↻", "⌂", "▶")
        val colors = intArrayOf(
            Color.parseColor("#E9C274"),
            Color.parseColor("#E58A7A"),
            Color.parseColor("#A9B6E8"),
        )
        buttons.forEachIndexed { index, rect ->
            drawActionButton(
                canvas = canvas,
                rect = rect,
                label = labels[index],
                detail = details[index],
                icon = icons[index],
                accent = colors[index],
                pressed = pressedButton == index,
            )
        }
    }

    private fun drawActionButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        detail: String,
        icon: String,
        accent: Int,
        pressed: Boolean,
    ) {
        val drawn = RectF(rect)
        if (pressed) drawn.offset(0f, dp(2f))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                drawn.left,
                drawn.top,
                drawn.right,
                drawn.bottom,
                Color.parseColor(if (pressed) "#D9A96D" else "#EBCB92"),
                Color.parseColor(if (pressed) "#A8683E" else "#B8784B"),
                Shader.TileMode.CLAMP,
            )
        }
        fill.setShadowLayer(dp(if (pressed) 2f else 7f), 0f, dp(3f), Color.argb(190, 0, 0, 0))
        canvas.drawRoundRect(drawn, dp(14f), dp(14f), fill)
        fill.clearShadowLayer()

        buttonBorderPaint.color = Color.argb(230, 255, 239, 194)
        buttonBorderPaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(drawn, dp(14f), dp(14f), buttonBorderPaint)

        iconPaint.color = accent
        iconPaint.textSize = dp(23f)
        canvas.drawText(icon, drawn.left + dp(31f), drawn.centerY() + dp(8f), iconPaint)

        buttonLabelPaint.textSize = dp(16f)
        canvas.drawText(label, drawn.centerX() + dp(12f), drawn.centerY() - dp(3f), buttonLabelPaint)
        buttonDetailPaint.textSize = dp(10f)
        canvas.drawText(detail, drawn.centerX() + dp(12f), drawn.centerY() + dp(15f), buttonDetailPaint)
    }

    private fun drawCenteredWrappedText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        firstBaseline: Float,
        maxWidth: Float,
        paint: Paint,
    ) {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        val visible = lines.ifEmpty { listOf("") }.take(3)
        val lineHeight = dp(22f)
        val offset = (visible.size - 1) * lineHeight / 2f
        visible.forEachIndexed { index, value ->
            canvas.drawText(value, centerX, firstBaseline - offset + index * lineHeight, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = buttons.indexOfFirst { it.contains(event.x, event.y) }
                    .takeIf { it >= 0 }
                if (pressedButton != null) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedButton != null &&
                    !buttons[pressedButton!!].contains(event.x, event.y)
                ) {
                    pressedButton = null
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val action = pressedButton
                val selected = action != null && buttons[action].contains(event.x, event.y)
                pressedButton = null
                invalidate()
                if (selected) {
                    performClick()
                    SoundPlayer.play("ui_click")
                    when (action) {
                        0 -> onPlayAgain?.invoke()
                        1 -> onMainMenu?.invoke()
                        2 -> onWatchReplay?.invoke()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * density
}