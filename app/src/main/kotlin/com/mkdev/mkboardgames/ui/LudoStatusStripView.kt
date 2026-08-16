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
        val padding = 5f * density
        val gap = 7f * density
        val cardWidth = (width - padding * 2f - gap * 3f) / 4f
        val cardHeight = height - padding * 2f
        val radius = 9f * density
        val pieces = LudoSetup.allPieces(gameState)

        displayOrder.forEachIndexed { index, player ->
            val left = padding + index * (cardWidth + gap)
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
                RectF(left, top, right, top + 10f * density),
                radius,
                radius,
                accentPaint,
            )
            canvas.drawRect(left, top + 5f * density, right, top + 10f * density, accentPaint)

            val avatarX = left + 28f * density
            val avatarY = top + 32f * density
            avatarPaint.color = Color.argb(255, 233, 240, 244)
            canvas.drawCircle(avatarX, avatarY, 21f * density, avatarPaint)
            avatarPaint.color = Color.argb(
                255,
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
            canvas.drawCircle(avatarX, avatarY, 17f * density, avatarPaint)
            avatarPaint.color = Color.argb(130, 255, 255, 255)
            canvas.drawCircle(avatarX, avatarY - 5f * density, 6f * density, avatarPaint)
            canvas.drawOval(
                RectF(
                    avatarX - 10f * density,
                    avatarY + 1f * density,
                    avatarX + 10f * density,
                    avatarY + 13f * density,
                ),
                avatarPaint,
            )

            textPaint.color = Color.rgb(185, 192, 201)
            textPaint.textSize = 17f * density
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Tokens",
                left + 58f * density,
                top + 39f * density,
                textPaint,
            )

            textPaint.color = color
            textPaint.textSize = 15f * density
            canvas.drawText("[A]", left + 9f * density, bottom - 15f * density, textPaint)

            val playerPieces = pieces.filter { it.player == player }
            val dotStart = left + 59f * density
            val dotGap = 18f * density
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
                    bottom - 20f * density,
                    7f * density,
                    dotPaint,
                )
                if (piece?.progress == LudoSetup.FINISH) {
                    dotPaint.color = Color.argb(190, 255, 255, 255)
                    dotPaint.style = Paint.Style.STROKE
                    dotPaint.strokeWidth = 1.5f * density
                    canvas.drawCircle(
                        dotStart + token * dotGap,
                        bottom - 20f * density,
                        7f * density,
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