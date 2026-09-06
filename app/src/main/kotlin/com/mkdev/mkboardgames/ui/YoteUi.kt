package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class YoteGameOverView(
    context: Context,
    message: String,
) : MancalaChoiceOverlayView(
    context,
    "GAME OVER",
    message,
    listOf("Play Again", "Main Menu"),
)

class YoteRulesView(context: Context) : View(context) {
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
            "CAPTURE" to "Jump over an adjacent opponent stone into an empty space to capture it. A jump capture also lets you remove one additional opponent stone anywhere on the board.",
            "WINNING" to "Capture all of the opponent’s stones, including the stones still in their reserve, or leave them with no legal move.",
            "BOARD VARIANTS" to "Choose either the carved reservoir board or the rustic pebble board before the match. The chosen board stays fixed for that game.",
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
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}