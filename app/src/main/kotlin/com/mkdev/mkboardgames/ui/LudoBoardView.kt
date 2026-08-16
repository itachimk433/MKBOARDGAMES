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
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateGeometry()

    private fun updateGeometry() {
        if (width == 0 || height == 0) return
        val boardSize = minOf(width, height) * 0.94f
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
            drawPathHighlight(canvas, pathFor(move))
            val point = centerOf(move.to)
            canvas.drawCircle(point.x, point.y, cell * 0.34f, highlightPaint)
            canvas.drawCircle(point.x, point.y, cell * 0.08f, highlightPaint)
        }
        animatedMove?.let { drawPathHighlight(canvas, animatedPath) }
        selectedFrom?.let { from ->
            val point = centerOf(from)
            canvas.drawCircle(point.x, point.y, cell * 0.42f, highlightPaint)
        }
    }

    private fun drawPathHighlight(canvas: Canvas, path: List<Position>) {
        for ((index, position) in path.withIndex()) {
            val rect = cellRect(position).apply { inset(cell * 0.12f, cell * 0.12f) }
            piecePaint.color = if (index == path.lastIndex) {
                Color.argb(120, 255, 255, 255)
            } else {
                Color.argb(68, 255, 255, 255)
            }
            piecePaint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, piecePaint)
            piecePaint.color = Color.argb(165, 255, 255, 255)
            piecePaint.style = Paint.Style.STROKE
            piecePaint.strokeWidth = cell * 0.035f
            canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, piecePaint)
            piecePaint.style = Paint.Style.FILL
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val skipped = animatedMove?.from
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                val piece = gameState.get(position) as? LudoPiece ?: continue
                if (position == skipped) continue
                drawPiece(canvas, piece, centerOf(position))
            }
        }
        val moving = animatedPiece
        val move = animatedMove
        if (moving != null && move != null) {
            drawPiece(canvas, moving, animatedPiecePoint())
        }
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
        val source = legalMoves.firstOrNull { it.from == tapped }
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

    fun animateMove(move: Move, onEnd: () -> Unit) {
        val piece = gameState.get(move.from) as? LudoPiece ?: run { onEnd(); return }
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
        val piece = gameState.get(move.from) as? LudoPiece
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