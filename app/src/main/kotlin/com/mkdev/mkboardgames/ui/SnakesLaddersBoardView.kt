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
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class SnakesLaddersBoardView(context: Context) : View(context) {
    companion object {
        const val BOARD_SIZE = 10
        // The board artwork is 740x740, while its numbered grid is the
        // measured 710x710 area from (15, 15) through (725, 725).
        private const val ARTWORK_SIZE = 740f
        private const val GRID_INSET = 15f / ARTWORK_SIZE
        private const val GRID_SIZE = 710f / ARTWORK_SIZE
        val PLAYER_COLORS = intArrayOf(
            Color.rgb(226, 67, 76),
            Color.rgb(54, 126, 218),
            Color.rgb(66, 167, 120),
            Color.rgb(232, 184, 74),
        )
    }

    enum class MovePath {
        NUMBERED_SQUARES,
        ENTER_BOARD,
        DIRECT_TRANSITION,
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
    private val positions = IntArray(4)
    private val animatedPoints = arrayOfNulls<PointF>(4)
    private val startAnchors = arrayOfNulls<PointF>(4)
    private val startRadii = FloatArray(4)
    private var activePlayerCount = 2
    private var moveAnimator: ValueAnimator? = null
    private var animationGeneration = 0
    private var artworkSize = 0f
    private var cell = 0f
    private var left = 0f
    private var top = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var animatedPath: List<PointF> = emptyList()
    private var animatedProgress = 0f
    private var animatedSoundStep = -1

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
        artworkSize = min(width, height).toFloat() * 0.97f
        cell = artworkSize * GRID_SIZE / BOARD_SIZE
        left = (width - artworkSize) / 2f
        top = (height - artworkSize) / 2f
        gridLeft = left + artworkSize * GRID_INSET
        gridTop = top + artworkSize * GRID_INSET
        tokenTextPaint.textSize = cell * 0.27f
        tokenEdgePaint.strokeWidth = cell * 0.045f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boardRect = RectF(left, top, left + artworkSize, top + artworkSize)
        if (boardBitmap != null) {
            canvas.drawBitmap(boardBitmap, null, boardRect, bitmapPaint)
        } else {
            tokenPaint.color = Color.rgb(185, 217, 182)
            canvas.drawRect(boardRect, tokenPaint)
        }

        for (player in 0 until activePlayerCount) {
            drawToken(canvas, player, animatedPoints[player])
        }
    }

    private fun drawToken(canvas: Canvas, player: Int, animatedPoint: PointF?) {
        val point = animatedPoint ?: pointForPosition(player, positions[player])
        val offset = if (positions[player] == 0) {
            PointF()
        } else {
            when (player) {
                0 -> PointF(-cell * 0.2f, cell * 0.18f)
                1 -> PointF(cell * 0.2f, -cell * 0.18f)
                2 -> PointF(-cell * 0.2f, -cell * 0.18f)
                else -> PointF(cell * 0.2f, cell * 0.18f)
            }
        }
        val radius = if (positions[player] == 0) {
            startRadii[player].takeIf { it > 0f } ?: cell * 0.25f
        } else {
            cell * 0.25f
        }
        tokenTextPaint.textSize = if (positions[player] == 0) {
            radius * 1.08f
        } else {
            cell * 0.27f
        }
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
            (player + 1).toString(),
            point.x + offset.x,
            point.y + offset.y - (tokenTextPaint.ascent() + tokenTextPaint.descent()) / 2f,
            tokenTextPaint,
        )
    }

    fun setPlayerPosition(player: Int, number: Int) {
        positions[player.coerceIn(0, positions.lastIndex)] = number.coerceIn(0, 100)
        invalidate()
    }

    fun setActivePlayerCount(count: Int) {
        activePlayerCount = count.coerceIn(1, positions.size)
        invalidate()
    }

    fun setPlayerStartAnchor(player: Int, centerX: Float, centerY: Float, radius: Float) {
        val index = player.coerceIn(0, startAnchors.lastIndex)
        startAnchors[index] = PointF(centerX, centerY)
        startRadii[index] = radius.coerceAtLeast(0f)
        invalidate()
    }

    fun playerPosition(player: Int): Int =
        positions[player.coerceIn(0, positions.lastIndex)]

    fun boardArtworkTopPixels(): Int = top.roundToInt()

    fun boardArtworkBottomPixels(): Int = (top + artworkSize).roundToInt()

    fun animateMove(
        player: Int,
        from: Int,
        to: Int,
        path: MovePath = MovePath.NUMBERED_SQUARES,
        onStep: () -> Unit = {},
        onEnd: () -> Unit,
    ) {
        val index = player.coerceIn(0, positions.lastIndex)
        val generation = ++animationGeneration
        moveAnimator?.cancel()
        animatedPath = routeFor(index, from, to, path)
        animatedProgress = 0f
        animatedSoundStep = -1
        animatedPoints[index] = animatedPath.firstOrNull()
        val segmentCount = (animatedPath.size - 1).coerceAtLeast(1)
        moveAnimator = ValueAnimator.ofFloat(0f, segmentCount.toFloat()).apply {
            duration = when (path) {
                MovePath.NUMBERED_SQUARES -> {
                    // Multi-square moves should read as deliberate board
                    // movement rather than a fast teleport. A one-square
                    // move keeps the snappy timing used for entering play.
                    val stepDuration = if (segmentCount >= 2) 290L else 145L
                    (segmentCount * stepDuration) + 70L
                }
                MovePath.ENTER_BOARD -> 215L
                MovePath.DIRECT_TRANSITION -> 520L
            }
            interpolator = LinearInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                val step = animatedProgress.toInt().coerceAtMost(segmentCount)
                if (step < segmentCount && step != animatedSoundStep) {
                    animatedSoundStep = step
                    onStep()
                }
                animatedPoints[index] = pointAlongPath(animatedProgress)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != animationGeneration) return
                    positions[index] = to.coerceIn(1, 100)
                    animatedPoints[index] = null
                    animatedPath = emptyList()
                    animatedProgress = 0f
                    animatedSoundStep = -1
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
        animatedPoints.fill(null)
        animatedPath = emptyList()
        animatedProgress = 0f
        animatedSoundStep = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gameOver) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
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
            gridLeft + (column + 0.5f) * cell,
            gridTop + (row + 0.5f) * cell,
        )
    }

    private fun routeFor(player: Int, from: Int, to: Int, path: MovePath): List<PointF> {
        if (path == MovePath.ENTER_BOARD) {
            return listOf(startPointForPlayer(player), pointForNumber(1f))
        }
        if (path == MovePath.DIRECT_TRANSITION) {
            // A ladder or snake is a single visual transition between its two
            // measured square anchors. It must not traverse the numbered path.
            return listOf(pointForNumber(from.toFloat()), pointForNumber(to.toFloat()))
        }

        val start = from.coerceIn(1, 100)
        val end = to.coerceIn(1, 100)
        val numbers = if (from == 0) {
            listOf(1) + (2..end).toList()
        } else if (start <= end) {
            (start..end).toList()
        } else {
            (start downTo end).toList()
        }
        val route = numbers.map { pointForNumber(it.toFloat()) }.toMutableList()
        if (route.size == 1) route += route.first()
        return route
    }

    private fun pointForPosition(player: Int, number: Int): PointF =
        if (number == 0) startPointForPlayer(player) else pointForNumber(number.toFloat())

    private fun startPointForPlayer(player: Int): PointF {
        if (cell == 0f) return PointF()
        startAnchors[player]?.let { return PointF(it.x, it.y) }
        val x = gridLeft + cell * if (player % 2 == 0) 1.5f else 8.5f
        val radius = cell * 0.25f
        val y = if (player < 2) {
            gridTop + BOARD_SIZE * cell + cell * 0.38f
        } else {
            gridTop - cell * 0.38f
        }
        val outsideBoard = PointF(x, y)
        if (outsideBoard.y - radius >= 0f && outsideBoard.y + radius <= height) {
            return outsideBoard
        }

        // On a very short landscape board, keep the start tokens in the
        // artwork's outer margin rather than clipping them at the view edge.
        return PointF(
            x,
            if (player < 2) {
                (gridTop + BOARD_SIZE * cell - cell * 0.08f).coerceAtMost(height - radius)
            } else {
                (gridTop + cell * 0.08f).coerceAtLeast(radius)
            },
        )
    }

    private fun pointAlongPath(progress: Float): PointF {
        if (animatedPath.isEmpty()) return pointForNumber(1f)
        val lastIndex = animatedPath.lastIndex
        val bounded = progress.coerceIn(0f, lastIndex.toFloat())
        val lowerIndex = bounded.toInt().coerceIn(0, lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(lastIndex)
        val segmentProgress = bounded - lowerIndex
        val from = animatedPath[lowerIndex]
        val to = animatedPath[upperIndex]
        val jump = sin(segmentProgress * PI).toFloat() * cell * 0.18f
        return PointF(
            from.x + (to.x - from.x) * segmentProgress,
            from.y + (to.y - from.y) * segmentProgress - jump,
        )
    }
}