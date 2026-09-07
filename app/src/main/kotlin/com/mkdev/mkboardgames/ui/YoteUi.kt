package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

class YoteRulesView(context: Context) : View(context) {
    var onBack: (() -> Unit)? = null
    private val backRect = RectF()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Yote how to play"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(205, 0, 6, 14))
        val panelWidth = min(w * 0.9f, dp(610f))
        val panelHeight = min(h * 0.9f, dp(700f))
        val panel = RectF(
            (w - panelWidth) / 2f,
            (h - panelHeight) / 2f,
            (w + panelWidth) / 2f,
            (h + panelHeight) / 2f,
        )
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, panel.top, 0f, panel.bottom,
                Color.parseColor("#17677F"),
                Color.parseColor("#0C3344"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(18f), 0f, dp(8f), Color.BLACK)
        }
        canvas.drawRoundRect(panel, dp(26f), dp(26f), panelPaint)
        panelPaint.clearShadowLayer()

        bodyPaint.textAlign = Paint.Align.CENTER
        bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        bodyPaint.color = Color.WHITE
        bodyPaint.textSize = min(dp(28f), panelWidth * 0.08f)
        canvas.drawText("YOTÉ", panel.centerX(), panel.top + dp(52f), bodyPaint)
        bodyPaint.color = Color.parseColor("#FFE09C")
        bodyPaint.textSize = min(dp(22f), panelWidth * 0.065f)
        canvas.drawText("HOW TO PLAY", panel.centerX(), panel.top + dp(84f), bodyPaint)

        val contentLeft = panel.left + dp(28f)
        val contentWidth = panel.width() - dp(56f)
        var y = panel.top + dp(122f)
        val lineHeight = dp(18f)
        val sections = listOf(
            "SETUP" to "Yoté uses a 5 × 6 board. Each player has twelve stones in reserve. White moves first.",
            "ENTER OR MOVE" to "On a turn, either enter one reserve stone onto any empty space, or move one of your stones one square orthogonally.",
            "CAPTURE" to "Jump over an adjacent opponent stone into an empty space to capture it. A jump capture also requires you to remove one additional opponent stone anywhere on the board when one remains.",
            "WINNING" to "Capture all of the opponent’s stones, including the stones still in their reserve.",
            "THE BOARD" to "Rustic Rubble is a 5 × 6 wooden grid. Each player has twelve stones in reserve. White moves first.",
        )
        sections.forEach { (heading, body) ->
            headingPaint.color = Color.parseColor("#FFE09C")
            headingPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            headingPaint.textSize = min(dp(15f), contentWidth * 0.045f)
            canvas.drawText(heading, contentLeft, y, headingPaint)
            y += lineHeight
            bodyPaint.textAlign = Paint.Align.LEFT
            bodyPaint.typeface = Typeface.DEFAULT
            bodyPaint.color = Color.parseColor("#E4F1F0")
            bodyPaint.textSize = min(dp(14f), contentWidth * 0.042f)
            y = drawWrapped(canvas, body, contentLeft, contentWidth, y, lineHeight)
            y += dp(12f)
        }

        backRect.set(
            panel.centerX() - min(panel.width() * 0.42f, dp(210f)) / 2f,
            panel.bottom - dp(62f),
            panel.centerX() + min(panel.width() * 0.42f, dp(210f)) / 2f,
            panel.bottom - dp(14f),
        )
        val button = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, backRect.top, 0f, backRect.bottom,
                Color.parseColor("#F5D49A"),
                Color.parseColor("#A76438"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(5f), 0f, dp(3f), Color.BLACK)
        }
        canvas.drawRoundRect(backRect, dp(14f), dp(14f), button)
        button.clearShadowLayer()
        button.shader = null
        button.style = Paint.Style.STROKE
        button.strokeWidth = dp(1.5f)
        button.color = Color.parseColor("#733A25")
        canvas.drawRoundRect(backRect, dp(14f), dp(14f), button)
        bodyPaint.textAlign = Paint.Align.CENTER
        bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        bodyPaint.color = Color.parseColor("#4A1714")
        bodyPaint.textSize = min(dp(19f), backRect.height() * 0.4f)
        val metrics = bodyPaint.fontMetrics
        canvas.drawText("Back", backRect.centerX(), backRect.centerY() - (metrics.ascent + metrics.descent) / 2f, bodyPaint)
    }

    private fun drawWrapped(
        canvas: Canvas,
        value: String,
        left: Float,
        maxWidth: Float,
        startY: Float,
        lineHeight: Float,
    ): Float {
        var y = startY
        var line = ""
        value.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && bodyPaint.measureText(candidate) > maxWidth) {
                canvas.drawText(line, left, y, bodyPaint)
                y += lineHeight
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, left, y, bodyPaint)
            y += lineHeight
        }
        return y
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && backRect.contains(event.x, event.y)) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            SoundPlayer.play("ui_click")
            onBack?.invoke()
        }
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}