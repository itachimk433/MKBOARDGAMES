package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.games.ludo.LudoEconomy
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

    var profilesEnabled: Boolean = false
        set(value) {
            field = value
            contentDescription = if (value) {
                "Ludo player profiles. Tap a player to inspect"
            } else {
                "Ludo player token status"
            }
            invalidate()
        }

    var onPlayerProfileTapped: ((Int) -> Unit)? = null

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
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
        val scale = (cardWidth / (104f * density)).coerceIn(0.28f, 1f)
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

            val avatarX = left + 22f * density * scale
            val avatarY = top + 27f * density * scale
            avatarPaint.color = Color.argb(255, 233, 240, 244)
            canvas.drawCircle(avatarX, avatarY, 18f * density * scale, avatarPaint)
            avatarPaint.color = Color.argb(
                255,
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
            canvas.drawCircle(avatarX, avatarY, 14f * density * scale, avatarPaint)
            avatarPaint.color = Color.argb(130, 255, 255, 255)
            canvas.drawCircle(
                avatarX,
                avatarY - 4f * density * scale,
                5f * density * scale,
                avatarPaint,
            )
            canvas.drawOval(
                RectF(
                    avatarX - 8f * density * scale,
                    avatarY + 1f * density * scale,
                    avatarX + 8f * density * scale,
                    avatarY + 11f * density * scale,
                ),
                avatarPaint,
            )

            textPaint.color = Color.rgb(185, 192, 201)
            textPaint.textSize = 14f * density * scale
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Tokens",
                left + 44f * density * scale,
                top + 33f * density * scale,
                textPaint,
            )

            textPaint.color = color
            textPaint.textSize = 12f * density * scale
            canvas.drawText(
                "[A]",
                left + 7f * density * scale,
                bottom - 25f * density * scale,
                textPaint,
            )

            val playerPieces = pieces.filter { it.player == player }
            val economy = LudoEconomy.player(gameState, player)
            val homeCount = playerPieces.count { it.progress >= LudoSetup.FINISH }
            val dotGap = 12f * density * scale
            val dotStart = right - (LudoSetup.TOKENS_PER_PLAYER - 1) * dotGap - 7f * density * scale
            val homeLabel = "$homeCount/${LudoSetup.TOKENS_PER_PLAYER} HOME"
            textPaint.color = if (homeCount > 0) color else Color.rgb(166, 174, 184)
            textPaint.textSize = 10f * density * scale
            textPaint.textAlign = Paint.Align.LEFT
            val maxLabelWidth = (dotStart - left - 6f * density * scale).coerceAtLeast(1f)
            if (textPaint.measureText(homeLabel) > maxLabelWidth) {
                textPaint.textSize *= maxLabelWidth / textPaint.measureText(homeLabel)
            }
            canvas.drawText(
                homeLabel,
                left + 7f * density * scale,
                bottom - 12f * density * scale,
                textPaint,
            )
            if (profilesEnabled) {
                textPaint.color = Color.rgb(195, 202, 210)
                textPaint.textSize = 9f * density * scale
                canvas.drawText(
                    "COINS ${economy.coins}",
                    left + 7f * density * scale,
                    bottom - 35f * density * scale,
                    textPaint,
                )
            }

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
                    bottom - 16f * density * scale,
                    5f * density * scale,
                    dotPaint,
                )
                if (piece?.progress == LudoSetup.FINISH) {
                    val centerX = dotStart + token * dotGap
                    val centerY = bottom - 16f * density * scale
                    val radius = 5f * density * scale
                    checkPaint.color = Color.argb(235, 255, 255, 255)
                    checkPaint.strokeWidth = 1.6f * density * scale
                    canvas.drawCircle(centerX, centerY, radius, checkPaint)
                    val check = Path().apply {
                        moveTo(centerX - radius * 0.5f, centerY)
                        lineTo(centerX - radius * 0.1f, centerY + radius * 0.38f)
                        lineTo(centerX + radius * 0.58f, centerY - radius * 0.42f)
                    }
                    canvas.drawPath(check, checkPaint)
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

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!profilesEnabled) return true
        if (event.action != android.view.MotionEvent.ACTION_UP) return true
        val density = resources.displayMetrics.density
        val padding = 5f * density
        val horizontalGap = 7f * density
        val cardWidth = (width - padding * 2f - horizontalGap * 3f) / 4f
        val column = ((event.x - padding) / (cardWidth + horizontalGap)).toInt()
        if (column !in 0..3) return true
        val left = padding + column * (cardWidth + horizontalGap)
        if (event.x < left || event.x > left + cardWidth) return true
        onPlayerProfileTapped?.invoke(displayOrder[column])
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}