package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
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
        val PLAYER_COLORS = intArrayOf(
            Color.rgb(226, 67, 76),
            Color.rgb(54, 126, 218),
            Color.rgb(66, 167, 120),
            Color.rgb(232, 184, 74),
        )
    }

    enum class Board(
        val displayName: String,
        val assetName: String,
        val gridInset: Float,
        val gridSize: Float,
        val artworkAspectRatio: Float,
        val artworkScale: Float,
        val fullBleedBackground: Boolean,
        val ladders: Map<Int, Int>,
        val snakes: Map<Int, Int>,
        val accentColor: Int,
        val matchBackgroundAssetName: String? = null,
        val playerFrameAssetName: String? = null,
        val profileScale: Float = 1f,
        val dieScale: Float = 1f,
    ) {
        ONE(
            displayName = "Board One",
            assetName = "snakes_ladders_board.jpg",
            gridInset = 15f / 740f,
            gridSize = 710f / 740f,
            artworkAspectRatio = 1f,
            artworkScale = 0.97f,
            fullBleedBackground = false,
            ladders = mapOf(
                7 to 45,
                34 to 66,
                40 to 77,
                62 to 81,
                48 to 91,
                74 to 96,
            ),
            snakes = mapOf(
                33 to 10,
                37 to 5,
                57 to 19,
                70 to 31,
                92 to 55,
                97 to 56,
            ),
            accentColor = Color.parseColor("#E3B86A"),
        ),
        TWO(
            displayName = "Board Two",
            assetName = "snakes_ladders_board_two.jpg",
            gridInset = 0f,
            gridSize = 1f,
            artworkAspectRatio = 1f,
            artworkScale = 1f,
            fullBleedBackground = false,
            ladders = mapOf(
                7 to 30,
                16 to 33,
                20 to 38,
                36 to 83,
                50 to 68,
                63 to 81,
                71 to 89,
                86 to 97,
            ),
            snakes = mapOf(
                25 to 3,
                42 to 1,
                61 to 43,
                56 to 48,
                92 to 67,
                94 to 12,
                98 to 80,
            ),
            accentColor = Color.parseColor("#8EC7B9"),
            matchBackgroundAssetName = "snakes_ladders_board_two_background.webp",
            playerFrameAssetName = "snakes_ladders_board_two_profile_dice.webp",
            profileScale = 0.9f,
        ),
        THREE(
            displayName = "Board Three",
            assetName = "snakes_ladders_board_three.webp",
            gridInset = 0f,
            gridSize = 1f,
            artworkAspectRatio = 1f,
            artworkScale = 1f,
            fullBleedBackground = false,
            ladders = mapOf(
                9 to 27,
                18 to 37,
                25 to 54,
                28 to 51,
                56 to 64,
                68 to 88,
                76 to 97,
                79 to 100,
            ),
            snakes = mapOf(
                16 to 7,
                59 to 17,
                63 to 19,
                87 to 24,
                67 to 30,
                93 to 69,
                95 to 75,
                99 to 77,
            ),
            accentColor = Color.parseColor("#F39A32"),
            matchBackgroundAssetName = "snakes_ladders_board_three_background.webp",
            playerFrameAssetName = "snakes_ladders_board_three_profile_dice.webp",
            profileScale = 0.7f,
            dieScale = 0.7f,
        ),
        FOUR(
            displayName = "Board Four",
            assetName = "snakes_ladders_board_four.webp",
            gridInset = 0f,
            gridSize = 1f,
            artworkAspectRatio = 1f,
            artworkScale = 1f,
            fullBleedBackground = false,
            ladders = mapOf(
                13 to 28,
                32 to 49,
                42 to 61,
                54 to 85,
                72 to 90,
                85 to 95,
            ),
            snakes = mapOf(
                11 to 10,
                23 to 3,
                39 to 20,
                45 to 36,
                47 to 26,
                71 to 9,
                78 to 24,
                86 to 66,
                98 to 79,
            ),
            accentColor = Color.parseColor("#8DBB5A"),
            matchBackgroundAssetName = "snakes_ladders_board_four_background.webp",
            playerFrameAssetName = "snakes_ladders_board_four_profile_dice.webp",
            profileScale = 0.7f,
            dieScale = 0.7f,
        ),
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

    private var selectedBoard = Board.ONE
    private var boardBitmap: Bitmap? = loadBoardBitmap(selectedBoard)
    private val positions = IntArray(4)
    private val animatedPoints = arrayOfNulls<PointF>(4)
    private val startAnchors = arrayOfNulls<PointF>(4)
    private val startRadii = FloatArray(4)
    private var activePlayerCount = 2
    private var moveAnimator: ValueAnimator? = null
    private var animationGeneration = 0
    private var artworkWidth = 0f
    private var artworkHeight = 0f
    private var cell = 0f
    private var cellWidth = 0f
    private var cellHeight = 0f
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
    private val boardCellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(195, 20, 55, 61)
    }
    private val boardNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
        setShadowLayer(3f, 0f, 1f, Color.argb(220, 0, 0, 0))
    }
    private val ladderRailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(78, 49, 31)
    }
    private val ladderRungPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(231, 197, 132)
    }
    private val snakeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(225, 20, 42, 45)
    }
    private val snakeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val snakeEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val snakePupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 42, 44)
    }
    private val snakeMouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(75, 20, 29)
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = "Snakes and Ladders board"
    }

    fun setBoard(board: Board) {
        if (selectedBoard == board) return
        selectedBoard = board
        boardBitmap = loadBoardBitmap(board)
        updateGeometry()
        contentDescription = "Snakes and Ladders ${board.displayName}"
        invalidate()
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
        updateGeometry()
    }

    private fun updateGeometry() {
        if (width <= 0 || height <= 0) return
        val board = selectedBoard
        if (board.fullBleedBackground) {
            artworkWidth = width.toFloat()
            artworkHeight = height.toFloat()
            left = 0f
            top = 0f
        } else {
            val fittedWidth = min(width.toFloat(), height * board.artworkAspectRatio)
            val fittedHeight = fittedWidth / board.artworkAspectRatio
            artworkWidth = fittedWidth * board.artworkScale
            artworkHeight = fittedHeight * board.artworkScale
            left = (width - artworkWidth) / 2f
            top = (height - artworkHeight) / 2f
        }
        gridLeft = left + artworkWidth * board.gridInset
        gridTop = top + artworkHeight * board.gridInset
        cellWidth = artworkWidth * board.gridSize / BOARD_SIZE
        cellHeight = artworkHeight * board.gridSize / BOARD_SIZE
        cell = min(cellWidth, cellHeight)
        tokenTextPaint.textSize = cell * 0.27f
        tokenEdgePaint.strokeWidth = cell * 0.045f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boardRect = RectF(left, top, left + artworkWidth, top + artworkHeight)
        val bitmap = boardBitmap
        if (bitmap != null) {
            if (selectedBoard.fullBleedBackground) {
                drawBitmapCover(canvas, bitmap, boardRect)
            } else {
                canvas.drawBitmap(bitmap, null, boardRect, bitmapPaint)
            }
        } else {
            tokenPaint.color = Color.rgb(185, 217, 182)
            canvas.drawRect(boardRect, tokenPaint)
        }

        for (player in 0 until activePlayerCount) {
            drawToken(canvas, player, animatedPoints[player])
        }
    }

    private fun drawBoardOverlay(canvas: Canvas) {
        if (selectedBoard != Board.TWO || cellWidth == 0f || cellHeight == 0f) return

        for (row in 0 until BOARD_SIZE) {
            for (column in 0 until BOARD_SIZE) {
                boardCellPaint.color = if ((row + column) % 2 == 0) {
                    Color.argb(24, 255, 255, 255)
                } else {
                    Color.argb(18, 12, 49, 54)
                }
                canvas.drawRect(
                    gridLeft + column * cellWidth,
                    gridTop + row * cellHeight,
                    gridLeft + (column + 1) * cellWidth,
                    gridTop + (row + 1) * cellHeight,
                    boardCellPaint,
                )
            }
        }

        boardGridPaint.strokeWidth = maxOf(1f, cell * 0.035f)
        for (row in 0..BOARD_SIZE) {
            val y = gridTop + row * cellHeight
            canvas.drawLine(gridLeft, y, gridLeft + BOARD_SIZE * cellWidth, y, boardGridPaint)
        }
        for (column in 0..BOARD_SIZE) {
            val x = gridLeft + column * cellWidth
            canvas.drawLine(x, gridTop, x, gridTop + BOARD_SIZE * cellHeight, boardGridPaint)
        }

        selectedBoard.ladders.entries.forEach { entry ->
            drawLadder(canvas, entry.key, entry.value)
        }
        selectedBoard.snakes.entries.forEachIndexed { index, entry ->
            drawSnake(canvas, entry.key, entry.value, index)
        }

        boardNumberPaint.textSize = cell * 0.2f
        for (number in 1..100) {
            val point = pointForNumber(number.toFloat())
            canvas.drawText(
                number.toString(),
                point.x,
                point.y - (boardNumberPaint.ascent() + boardNumberPaint.descent()) / 2f,
                boardNumberPaint,
            )
        }
    }

    private fun drawLadder(canvas: Canvas, fromNumber: Int, toNumber: Int) {
        val from = pointForNumber(fromNumber.toFloat())
        val to = pointForNumber(toNumber.toFloat())
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val perpendicularX = -dy / length
        val perpendicularY = dx / length
        val railOffset = cell * 0.16f
        val railWidth = maxOf(2f, cell * 0.065f)
        ladderRailPaint.strokeWidth = railWidth
        ladderRungPaint.strokeWidth = maxOf(1.5f, cell * 0.035f)

        val firstRailStart = PointF(
            from.x + perpendicularX * railOffset,
            from.y + perpendicularY * railOffset,
        )
        val firstRailEnd = PointF(
            to.x + perpendicularX * railOffset,
            to.y + perpendicularY * railOffset,
        )
        val secondRailStart = PointF(
            from.x - perpendicularX * railOffset,
            from.y - perpendicularY * railOffset,
        )
        val secondRailEnd = PointF(
            to.x - perpendicularX * railOffset,
            to.y - perpendicularY * railOffset,
        )
        canvas.drawLine(
            firstRailStart.x,
            firstRailStart.y,
            firstRailEnd.x,
            firstRailEnd.y,
            ladderRailPaint,
        )
        canvas.drawLine(
            secondRailStart.x,
            secondRailStart.y,
            secondRailEnd.x,
            secondRailEnd.y,
            ladderRailPaint,
        )

        val rungCount = (length / (cell * 0.75f)).toInt().coerceIn(3, 14)
        for (rung in 1 until rungCount) {
            val progress = rung.toFloat() / rungCount
            val centerX = from.x + dx * progress
            val centerY = from.y + dy * progress
            canvas.drawLine(
                centerX + perpendicularX * railOffset,
                centerY + perpendicularY * railOffset,
                centerX - perpendicularX * railOffset,
                centerY - perpendicularY * railOffset,
                ladderRungPaint,
            )
        }
    }

    private fun drawSnake(canvas: Canvas, fromNumber: Int, toNumber: Int, colorIndex: Int) {
        val from = pointForNumber(fromNumber.toFloat())
        val to = pointForNumber(toNumber.toFloat())
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val perpendicularX = -dy / length
        val perpendicularY = dx / length
        val waveSize = cell * 0.23f
        val path = Path()
        val segmentCount = 16
        for (segment in 0..segmentCount) {
            val progress = segment.toFloat() / segmentCount
            val wave = sin(progress.toDouble() * PI * 2.5).toFloat() * waveSize
            val x = from.x + dx * progress + perpendicularX * wave
            val y = from.y + dy * progress + perpendicularY * wave
            if (segment == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val bodyColor = when (colorIndex % 4) {
            0 -> Color.rgb(212, 71, 91)
            1 -> Color.rgb(64, 126, 170)
            2 -> Color.rgb(69, 143, 104)
            else -> Color.rgb(153, 93, 169)
        }
        snakeOutlinePaint.strokeWidth = maxOf(3f, cell * 0.18f)
        snakeBodyPaint.strokeWidth = maxOf(2f, cell * 0.12f)
        snakeBodyPaint.color = bodyColor
        canvas.drawPath(path, snakeOutlinePaint)
        canvas.drawPath(path, snakeBodyPaint)

        val headRadius = cell * 0.24f
        canvas.drawCircle(from.x, from.y, headRadius, snakeOutlinePaint)
        canvas.drawCircle(from.x, from.y, headRadius * 0.82f, snakeBodyPaint)
        val eyeOffsetX = cell * 0.08f
        val eyeOffsetY = -cell * 0.07f
        for (direction in listOf(-1f, 1f)) {
            val eyeX = from.x + eyeOffsetX * direction
            val eyeY = from.y + eyeOffsetY
            canvas.drawCircle(eyeX, eyeY, cell * 0.045f, snakeEyePaint)
            canvas.drawCircle(eyeX, eyeY, cell * 0.018f, snakePupilPaint)
        }
        snakeMouthPaint.strokeWidth = maxOf(1f, cell * 0.018f)
        canvas.drawLine(
            from.x - cell * 0.09f,
            from.y + cell * 0.1f,
            from.x + cell * 0.09f,
            from.y + cell * 0.1f,
            snakeMouthPaint,
        )
    }

    private fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val destinationAspect = destination.width() / destination.height()
        val source = if (sourceAspect > destinationAspect) {
            val croppedWidth = (bitmap.height * destinationAspect).toInt()
            Rect(
                (bitmap.width - croppedWidth) / 2,
                0,
                (bitmap.width + croppedWidth) / 2,
                bitmap.height,
            )
        } else {
            val croppedHeight = (bitmap.width / destinationAspect).toInt()
            Rect(
                0,
                (bitmap.height - croppedHeight) / 2,
                bitmap.width,
                (bitmap.height + croppedHeight) / 2,
            )
        }
        canvas.drawBitmap(bitmap, source, destination, bitmapPaint)
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

    fun boardArtworkLeftPixels(): Int = left.roundToInt()

    fun boardArtworkWidthPixels(): Int = artworkWidth.roundToInt()

    fun boardArtworkTopPixels(): Int = top.roundToInt()

    fun boardArtworkBottomPixels(): Int = (top + artworkHeight).roundToInt()

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
            gridLeft + (column + 0.5f) * cellWidth,
            gridTop + (row + 0.5f) * cellHeight,
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
        val x = gridLeft + cellWidth * if (player % 2 == 0) 1.5f else 8.5f
        val radius = cell * 0.25f
        val y = if (player < 2) {
            gridTop + BOARD_SIZE * cellHeight + cellHeight * 0.38f
        } else {
            gridTop - cellHeight * 0.38f
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
                (gridTop + BOARD_SIZE * cellHeight - cellHeight * 0.08f)
                    .coerceAtMost(height - radius)
            } else {
                (gridTop + cellHeight * 0.08f).coerceAtLeast(radius)
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

    private fun loadBoardBitmap(board: Board): Bitmap? = runCatching {
        context.assets.open(board.assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}