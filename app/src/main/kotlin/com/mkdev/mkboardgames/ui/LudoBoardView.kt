package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.ludo.LudoEconomy
import com.mkdev.mkboardgames.games.ludo.LudoPiece
import com.mkdev.mkboardgames.games.ludo.LudoSetup
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

class LudoBoardView(context: Context) : View(context) {
    var gameState: GameState = LudoSetup.initialState()
        set(value) {
            field = value
            selectedFrom = null
            legalMoves = emptyList()
            updateGeometry()
            invalidate()
        }

    var legalMoves: List<Move> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var isLocked: Boolean = false
    var onMoveSelected: ((Move) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private var selectedFrom: Position? = null
    private var cell = 0f
    private var left = 0f
    private var top = 0f
    private var animatedMove: Move? = null
    private var animatedPiece: LudoPiece? = null
    private var animatedPath: List<Position> = emptyList()
    private var animatedProgress = 0f
    private var moveAnimator: ValueAnimator? = null
    private var protectionPulse = 0f
    private val protectionPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 950L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            protectionPulse = it.animatedValue as Float
            invalidate()
        }
    }

    private val boardBitmap: Bitmap? = runCatching {
        context.assets.open("ludo_board_reference.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val protectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        protectionPulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        protectionPulseAnimator.cancel()
        moveAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val boardSize = when {
            widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0 -> widthSize
            heightMode != MeasureSpec.UNSPECIFIED && heightSize > 0 -> heightSize
            else -> suggestedMinimumWidth
        }
        val measuredWidth = resolveSize(boardSize, widthMeasureSpec)
        val measuredHeight = resolveSize(boardSize, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateGeometry()

    private fun updateGeometry() {
        if (width == 0 || height == 0) return
        val boardSize = minOf(width, height) * 0.98f
        cell = boardSize / LudoSetup.BOARD_SIZE
        left = (width - boardSize) / 2f
        top = (height - boardSize) / 2f
        highlightPaint.strokeWidth = cell * 0.08f
        textPaint.textSize = cell * 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#10151A"))
        drawBoard(canvas)
        drawMoveHints(canvas)
        drawPieces(canvas)
        drawTurnMarker(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        val size = LudoSetup.BOARD_SIZE * cell
        val boardRect = RectF(left, top, left + size, top + size)
        val bitmap = boardBitmap
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, boardRect, boardPaint)
        } else {
            boardPaint.color = Color.WHITE
            canvas.drawRect(boardRect, boardPaint)
        }
    }

    private fun drawMoveHints(canvas: Canvas) {
        for (move in legalMoves) {
            val accent = accentColorFor(move)
            val path = pathFor(move)
            // The source cell is the starting point, not step one. Show only
            // the route markers between source and destination so intermediate
            // cells cannot be mistaken for separate legal destinations.
            drawRoutePreview(canvas, path.drop(1).dropLast(1), accent)
            val sourcePoint = centerOf(move.from)
            highlightPaint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(sourcePoint.x, sourcePoint.y, cell * 0.43f, highlightPaint)
            highlightPaint.color = Color.argb(230, 255, 255, 255)
            canvas.drawCircle(sourcePoint.x, sourcePoint.y, cell * 0.36f, highlightPaint)
            val point = centerOf(move.to)
            highlightPaint.color = Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(point.x, point.y, cell * 0.34f, highlightPaint)
            highlightPaint.color = Color.argb(240, 255, 255, 255)
            canvas.drawCircle(point.x, point.y, cell * 0.08f, highlightPaint)
        }
        animatedMove?.let { drawPathHighlight(canvas, animatedPath, accentColorFor(it)) }
        selectedFrom?.let { from ->
            val point = centerOf(from)
            highlightPaint.color = Color.argb(240, 255, 255, 255)
            canvas.drawCircle(point.x, point.y, cell * 0.42f, highlightPaint)
        }
    }

    private fun drawRoutePreview(canvas: Canvas, route: List<Position>, accent: Int) {
        if (route.isEmpty()) return
        val red = Color.red(accent)
        val green = Color.green(accent)
        val blue = Color.blue(accent)
        piecePaint.style = Paint.Style.FILL
        for (position in route) {
            piecePaint.color = Color.argb(150, red, green, blue)
            canvas.drawCircle(centerOf(position).x, centerOf(position).y, cell * 0.09f, piecePaint)
        }
    }

    private fun drawPathHighlight(canvas: Canvas, path: List<Position>, accent: Int) {
        val red = Color.red(accent)
        val green = Color.green(accent)
        val blue = Color.blue(accent)
        for ((index, position) in path.withIndex()) {
            val rect = cellRect(position).apply { inset(cell * 0.12f, cell * 0.12f) }
            piecePaint.color = if (index == path.lastIndex) {
                Color.argb(185, red, green, blue)
            } else {
                Color.argb(105, red, green, blue)
            }
            piecePaint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, piecePaint)
            piecePaint.color = Color.argb(235, red, green, blue)
            piecePaint.style = Paint.Style.STROKE
            piecePaint.strokeWidth = cell * 0.035f
            canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, piecePaint)
            piecePaint.style = Paint.Style.FILL
        }
    }

    private fun accentColorFor(move: Move): Int {
        val player = move.metadata["player"] as? Int
            ?: (gameState.get(move.from) as? LudoPiece)?.player
            ?: LudoSetup.playerFromState(gameState)
        return LudoSetup.PLAYER_COLORS[player.coerceIn(0, LudoSetup.PLAYER_COUNT - 1)]
    }

    private fun drawPieces(canvas: Canvas) {
        val moving = animatedPiece
        val piecesByPosition = LudoSetup.allPieces(gameState)
            .groupBy { LudoSetup.positionOf(it) }
        for ((position, stack) in piecesByPosition) {
            val center = centerOf(position)
            stack.forEachIndexed { index, piece ->
                if (moving != null && piece.player == moving.player && piece.token == moving.token) {
                    return@forEachIndexed
                }
                val offset = stackOffset(index, stack.size)
                drawPiece(
                    canvas,
                    piece,
                    PointF(center.x + offset.x, center.y + offset.y),
                )
            }
        }
        val move = animatedMove
        if (moving != null && move != null) {
            drawPiece(canvas, moving, animatedPiecePoint())
        }
    }

    private fun stackOffset(index: Int, count: Int): PointF {
        val offsets = when (count) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-0.14f to 0f, 0.14f to 0f)
            3 -> listOf(-0.15f to 0.10f, 0.15f to 0.10f, 0f to -0.15f)
            else -> listOf(
                -0.15f to -0.15f,
                0.15f to -0.15f,
                -0.15f to 0.15f,
                0.15f to 0.15f,
            )
        }
        val (x, y) = offsets[index.coerceIn(0, offsets.lastIndex)]
        return PointF(x * cell, y * cell)
    }

    private fun animatedPiecePoint(): PointF {
        val path = animatedPath
        if (path.isEmpty()) return centerOf(animatedMove?.to ?: Position(0, 0))

        val lastIndex = path.lastIndex
        val progress = animatedProgress.coerceIn(0f, lastIndex.toFloat())
        val lowerIndex = floor(progress).toInt().coerceIn(0, lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(lastIndex)
        val segmentProgress = progress - lowerIndex
        val from = centerOf(path[lowerIndex])
        val to = centerOf(path[upperIndex])
        val jumpHeight = sin(segmentProgress * PI).toFloat() * cell * 0.18f
        return PointF(
            from.x + (to.x - from.x) * segmentProgress,
            from.y + (to.y - from.y) * segmentProgress - jumpHeight
        )
    }

    private fun drawPiece(canvas: Canvas, piece: LudoPiece, point: PointF) {
        val radius = cell * 0.34f
        val isProtected = piece.progress in 0 until LudoSetup.FINISH &&
            LudoEconomy.player(gameState, piece.player).protectedToken == piece.token
        if (isProtected) {
            protectionPaint.style = Paint.Style.FILL
            protectionPaint.color = Color.argb(75, 255, 216, 91)
            canvas.drawCircle(point.x, point.y, radius + cell * 0.15f, protectionPaint)
            protectionPaint.style = Paint.Style.STROKE
            protectionPaint.strokeWidth = cell * 0.075f
            protectionPaint.color = Color.rgb(255, 216, 91)
            canvas.drawCircle(point.x, point.y, radius + cell * 0.10f, protectionPaint)
        }
        canvas.drawCircle(point.x + cell * 0.05f, point.y + cell * 0.08f, radius, shadowPaint)
        piecePaint.color = LudoSetup.PLAYER_COLORS[piece.player]
        canvas.drawCircle(point.x, point.y, radius, piecePaint)
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = cell * 0.055f
        piecePaint.color = Color.argb(230, 255, 255, 255)
        canvas.drawCircle(point.x, point.y, radius, piecePaint)
        piecePaint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        canvas.drawText(piece.symbol(), point.x, point.y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        if (isProtected) {
            drawProtectionShield(canvas, point.x, point.y + radius * 1.18f)
        }
    }

    private fun drawProtectionShield(canvas: Canvas, centerX: Float, centerY: Float) {
        val width = cell * 0.22f
        val height = cell * 0.27f
        val pulse = protectionPulse
        protectionPaint.setShadowLayer(
            cell * (0.08f + pulse * 0.08f),
            0f,
            cell * 0.03f,
            Color.argb(180, 255, 206, 67),
        )
        protectionPaint.style = Paint.Style.FILL
        protectionPaint.color = Color.argb(185 + (pulse * 35f).toInt(), 89, 67, 20)
        val shield = Path().apply {
            moveTo(centerX, centerY - height)
            lineTo(centerX + width, centerY - height * 0.62f)
            lineTo(centerX + width * 0.82f, centerY + height * 0.35f)
            quadTo(centerX, centerY + height, centerX - width * 0.82f, centerY + height * 0.35f)
            lineTo(centerX - width, centerY - height * 0.62f)
            close()
        }
        canvas.drawPath(shield, protectionPaint)
        protectionPaint.clearShadowLayer()
        protectionPaint.style = Paint.Style.STROKE
        protectionPaint.strokeWidth = cell * 0.045f
        protectionPaint.color = Color.rgb(255, 224, 112)
        canvas.drawPath(shield, protectionPaint)
        protectionPaint.style = Paint.Style.FILL
    }

    private fun drawTurnMarker(canvas: Canvas) {
        val player = LudoSetup.playerFromState(gameState)
        val point = centerOf(LudoSetup.PATH[LudoSetup.startOffset(player)])
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = cell * 0.04f
        piecePaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawCircle(point.x, point.y, cell * 0.47f, piecePaint)
        piecePaint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (gameState.status != com.mkdev.mkboardgames.engine.GameStatus.IN_PROGRESS) {
            onGameOverTapped?.invoke()
            return true
        }
        if (isLocked) return true
        val tapped = positionAt(event.x, event.y) ?: return true
        val source = legalMoves
            .filter { it.from == tapped }
            .minByOrNull { sourceDistance(it, event.x, event.y) }
        if (source != null) {
            selectedFrom = null
            legalMoves = emptyList()
            onMoveSelected?.invoke(source)
            return true
        }
        val destination = legalMoves.firstOrNull { it.to == tapped }
        if (destination != null) {
            selectedFrom = null
            legalMoves = emptyList()
            onMoveSelected?.invoke(destination)
            return true
        }
        selectedFrom = null
        invalidate()
        return true
    }

    private fun sourceDistance(move: Move, x: Float, y: Float): Float {
        val piece = LudoSetup.pieceForMove(gameState, move)
            ?: (gameState.get(move.from) as? LudoPiece)
        val stack = LudoSetup.piecesAt(gameState, move.from)
        val index = piece?.let { stack.indexOfFirst { candidate ->
            candidate.player == it.player && candidate.token == it.token
        } } ?: 0
        val center = centerOf(move.from)
        val offset = stackOffset(index.coerceAtLeast(0), stack.size.coerceAtLeast(1))
        val dx = x - center.x - offset.x
        val dy = y - center.y - offset.y
        return dx * dx + dy * dy
    }

    fun animateMove(move: Move, onEnd: () -> Unit) {
        val piece = LudoSetup.pieceForMove(gameState, move)
            ?: (gameState.get(move.from) as? LudoPiece)
            ?: run { onEnd(); return }
        isLocked = true
        animatedMove = move
        animatedPiece = piece
        animatedPath = pathFor(move)
        animatedProgress = 0f
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, animatedPath.lastIndex.toFloat()).apply {
            duration = (animatedPath.lastIndex.coerceAtLeast(1) * 145L) + 70L
            interpolator = LinearInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatedMove = null
                    animatedPiece = null
                    animatedPath = emptyList()
                    animatedProgress = 0f
                    isLocked = false
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    private fun pathFor(move: Move): List<Position> {
        val piece = LudoSetup.pieceForMove(gameState, move)
            ?: (gameState.get(move.from) as? LudoPiece)
            ?: return listOf(move.from, move.to)
        val targetProgress = move.metadata["targetProgress"] as? Int
            ?: return listOf(move.from, move.to)
        val path = mutableListOf(move.from)

        if (piece.progress < 0) {
            path += positionForProgress(piece, 0)
            for (progress in 1..targetProgress) {
                path += positionForProgress(piece, progress)
            }
        } else {
            for (progress in (piece.progress + 1)..targetProgress) {
                path += positionForProgress(piece, progress)
            }
        }
        if (path.lastOrNull() != move.to) path += move.to
        return path
    }

    private fun positionForProgress(piece: LudoPiece, progress: Int): Position = when {
        progress < 52 -> LudoSetup.trackPosition(piece.player, progress)
        progress < LudoSetup.FINISH -> LudoSetup.homeLanePosition(piece.player, progress)
        else -> LudoSetup.finishPosition(piece.player, piece.token)
    }

    private fun cellRect(position: Position): RectF =
        RectF(left + position.col * cell, top + position.row * cell,
            left + (position.col + 1) * cell, top + (position.row + 1) * cell)

    private fun centerOf(position: Position): PointF =
        PointF(left + (position.col + 0.5f) * cell, top + (position.row + 0.5f) * cell)

    private fun positionAt(x: Float, y: Float): Position? {
        val col = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()
        return if (row in 0 until LudoSetup.BOARD_SIZE && col in 0 until LudoSetup.BOARD_SIZE)
            Position(row, col) else null
    }
}