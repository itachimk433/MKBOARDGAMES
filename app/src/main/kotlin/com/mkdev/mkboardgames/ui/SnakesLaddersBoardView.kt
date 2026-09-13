package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import kotlin.math.min

class SnakesLaddersBoardView(context: Context) : View(context) {
    companion object {
        const val BOARD_SIZE = 10
        val PLAYER_COLORS = intArrayOf(
            Color.rgb(226, 67, 76),
            Color.rgb(54, 126, 218),
        )
    }

    var onGameOverTapped: (() -> Unit)? = null
    var gameOver: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val boardBitmap: Bitmap? = runCatching {
        context.assets.open("snakes_ladders_board.jpg").use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()
    private val positions = intArrayOf(0, 0)
    private val animatedNumbers = arrayOfNulls<Float>(2)
    private var moveAnimator: ValueAnimator? = null
    private var animationGeneration = 0
    private var cell = 0f
    private var left = 0f
    private var top = 0f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tokenEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    private val tokenTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 0, 0, 0)
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = "Snakes and Ladders board"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val boardSize = min(width, height).toFloat() * 0.97f
        cell = boardSize / BOARD_SIZE
        left = (width - boardSize) / 2f
        top = (height - boardSize) / 2f
        tokenTextPaint.textSize = cell * 0.27f
        tokenEdgePaint.strokeWidth = cell * 0.045f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boardSize = cell * BOARD_SIZE
        val boardRect = RectF(left, top, left + boardSize, top + boardSize)
        if (boardBitmap != null) {
            canvas.drawBitmap(boardBitmap, null, boardRect, bitmapPaint)
        } else {
            tokenPaint.color = Color.rgb(185, 217, 182)
            canvas.drawRect(boardRect, tokenPaint)
        }

        positions.indices.forEach { player ->
            val number = animatedNumbers[player] ?: positions[player].toFloat()
            drawToken(canvas, player, number)
        }
    }

    private fun drawToken(canvas: Canvas, player: Int, number: Float) {
        val point = pointForNumber(number)
        val offset = if (player == 0) {
            PointF(-cell * 0.18f, cell * 0.12f)
        } else {
            PointF(cell * 0.18f, -cell * 0.12f)
        }
        val radius = cell * 0.25f
        canvas.drawCircle(
            point.x + offset.x + cell * 0.04f,
            point.y + offset.y + cell * 0.06f,
            radius,
            shadowPaint,
        )
        tokenPaint.color = PLAYER_COLORS[player]
        canvas.drawCircle(point.x + offset.x, point.y + offset.y, radius, tokenPaint)
        canvas.drawCircle(point.x + offset.x, point.y + offset.y, radius, tokenEdgePaint)
        canvas.drawText(
            if (player == 0) "1" else "2",
            point.x + offset.x,
            point.y + offset.y - (tokenTextPaint.ascent() + tokenTextPaint.descent()) / 2f,
            tokenTextPaint,
        )
    }

    fun setPlayerPosition(player: Int, number: Int) {
        positions[player.coerceIn(0, 1)] = number.coerceIn(0, 100)
        invalidate()
    }

    fun playerPosition(player: Int): Int = positions[player.coerceIn(0, 1)]

    fun animateMove(
        player: Int,
        from: Int,
        to: Int,
        onStep: () -> Unit = {},
        onEnd: () -> Unit,
    ) {
        val index = player.coerceIn(0, 1)
        val generation = ++animationGeneration
        moveAnimator?.cancel()
        animatedNumbers[index] = from.toFloat()
        val stepCount = kotlin.math.abs(to - from).coerceAtLeast(1)
        var lastCompletedStep = 0
        moveAnimator = ValueAnimator.ofFloat(0f, stepCount.toFloat()).apply {
            duration = (stepCount * 105L + 90L)
                .coerceIn(180L, 1250L)
            addUpdateListener {
                val progress = it.animatedValue as Float
                val completedStep = progress.toInt().coerceAtMost(stepCount)
                if (completedStep > lastCompletedStep) {
                    repeat(completedStep - lastCompletedStep) { onStep() }
                    lastCompletedStep = completedStep
                }
                animatedNumbers[index] = if (to >= from) {
                    from + progress
                } else {
                    from - progress
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != animationGeneration) return
                    positions[index] = to.coerceIn(1, 100)
                    animatedNumbers[index] = null
                    moveAnimator = null
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    fun cancelAnimations() {
        animationGeneration++
        moveAnimator?.cancel()
        moveAnimator = null
        animatedNumbers.fill(null)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && gameOver) {
            performClick()
            onGameOverTapped?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointForNumber(value: Float): PointF {
        val number = value.coerceIn(1f, 100f)
        val index = number - 1f
        val rowFromBottom = (index / BOARD_SIZE).toInt()
        val positionInRow = index - rowFromBottom * BOARD_SIZE
        val column = if (rowFromBottom % 2 == 0) positionInRow else BOARD_SIZE - 1 - positionInRow
        val row = BOARD_SIZE - 1 - rowFromBottom
        return PointF(
            left + (column + 0.5f) * cell,
            top + (row + 0.5f) * cell,
        )
    }
}