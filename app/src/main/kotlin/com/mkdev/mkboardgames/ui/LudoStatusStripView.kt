package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.games.ludo.LudoSetup

class LudoStatusStripView(context: Context) : View(context) {
    var gameState: GameState = LudoSetup.initialState()
        set(value) {
            field = value
            invalidate()
        }

    var activePlayer: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var rolledValue: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val displayOrder = intArrayOf(2, 3, 1, 0)

    init {
        contentDescription = "Ludo player token status"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val columns = 4
        val padding = 5f * density
        val horizontalGap = 7f * density
        val cardWidth = (width - padding * 2f - horizontalGap * (columns - 1)) / columns
        val cardHeight = height - padding * 2f
        val scale = (cardWidth / (178f * density)).coerceIn(0.72f, 1f)
        val radius = 9f * density * scale
        val pieces = LudoSetup.allPieces(gameState)

        displayOrder.forEachIndexed { index, player ->
            val column = index % columns
            val left = padding + column * (cardWidth + horizontalGap)
            val top = padding
            val right = left + cardWidth
            val bottom = top + cardHeight
            val color = LudoSetup.PLAYER_COLORS[player]

            cardPaint.color = Color.argb(242, 17, 22, 28)
            cardPaint.setShadowLayer(5f * density, 0f, 2f * density, Color.argb(130, 0, 0, 0))
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, cardPaint)
            cardPaint.clearShadowLayer()

            accentPaint.color = color
            canvas.drawRoundRect(
                RectF(left, top, right, top + 10f * density * scale),
                radius,
                radius,
                accentPaint,
            )
            canvas.drawRect(
                left,
                top + 5f * density * scale,
                right,
                top + 10f * density * scale,
                accentPaint,
            )

            val avatarX = left + 28f * density * scale
            val avatarY = top + 32f * density * scale
            avatarPaint.color = Color.argb(255, 233, 240, 244)
            canvas.drawCircle(avatarX, avatarY, 21f * density * scale, avatarPaint)
            avatarPaint.color = Color.argb(
                255,
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
            canvas.drawCircle(avatarX, avatarY, 17f * density * scale, avatarPaint)
            avatarPaint.color = Color.argb(130, 255, 255, 255)
            canvas.drawCircle(
                avatarX,
                avatarY - 5f * density * scale,
                6f * density * scale,
                avatarPaint,
            )
            canvas.drawOval(
                RectF(
                    avatarX - 10f * density * scale,
                    avatarY + 1f * density * scale,
                    avatarX + 10f * density * scale,
                    avatarY + 13f * density * scale,
                ),
                avatarPaint,
            )

            textPaint.color = Color.rgb(185, 192, 201)
            textPaint.textSize = 17f * density * scale
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Tokens",
                left + 58f * density * scale,
                top + 39f * density * scale,
                textPaint,
            )

            textPaint.color = color
            textPaint.textSize = 15f * density * scale
            canvas.drawText(
                "[A]",
                left + 9f * density * scale,
                bottom - 15f * density * scale,
                textPaint,
            )

            val playerPieces = pieces.filter { it.player == player }
            val dotStart = left + 59f * density * scale
            val dotGap = 18f * density * scale
            repeat(LudoSetup.TOKENS_PER_PLAYER) { token ->
                val piece = playerPieces.firstOrNull { it.token == token }
                val dotColor = when {
                    piece == null || piece.progress < 0 ->
                        Color.rgb(73, 81, 91)
                    piece.progress >= LudoSetup.FINISH -> color
                    else -> Color.argb(210, Color.red(color), Color.green(color), Color.blue(color))
                }
                dotPaint.color = dotColor
                canvas.drawCircle(
                    dotStart + token * dotGap,
                    bottom - 20f * density * scale,
                    7f * density * scale,
                    dotPaint,
                )
                if (piece?.progress == LudoSetup.FINISH) {
                    dotPaint.color = Color.argb(190, 255, 255, 255)
                    dotPaint.style = Paint.Style.STROKE
                    dotPaint.strokeWidth = 1.5f * density * scale
                    canvas.drawCircle(
                        dotStart + token * dotGap,
                        bottom - 20f * density * scale,
                        7f * density * scale,
                        dotPaint,
                    )
                    dotPaint.style = Paint.Style.FILL
                }
            }

            if (player == activePlayer) {
                cardPaint.color = Color.argb(235, 255, 255, 255)
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = (if (rolledValue == 0) 1f else 2f) * density
                canvas.drawRoundRect(
                    RectF(left + density, top + density, right - density, bottom - density),
                    radius,
                    radius,
                    cardPaint,
                )
                cardPaint.style = Paint.Style.FILL
            }
        }
    }
}