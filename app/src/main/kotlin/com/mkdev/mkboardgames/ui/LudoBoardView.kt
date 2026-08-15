package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.ludo.LudoPiece
import com.mkdev.mkboardgames.games.ludo.LudoSetup

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
    private var animatedProgress = 0f
    private var moveAnimator: ValueAnimator? = null

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
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
        boardPaint.color = Color.parseColor("#182029")
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), cell, cell, boardPaint)

        drawYard(canvas, 0, RectF(left, top + cell * 9, left + cell * 5, top + cell * 14))
        drawYard(canvas, 1, RectF(left, top, left + cell * 5, top + cell * 5))
        drawYard(canvas, 2, RectF(left + cell * 9, top, left + cell * 14, top + cell * 5))
        drawYard(canvas, 3, RectF(left + cell * 9, top + cell * 9, left + cell * 14, top + cell * 14))

        for (position in LudoSetup.PATH) drawCell(canvas, position, Color.parseColor("#E5E1D8"))
        for (player in 0 until LudoSetup.PLAYER_COUNT) {
            for (progress in 52 until LudoSetup.FINISH) {
                drawCell(canvas, LudoSetup.homeLanePosition(player, progress), LudoSetup.PLAYER_SOFT_COLORS[player])
            }
        }

        val center = RectF(left + cell * 5, top + cell * 5, left + cell * 10, top + cell * 10)
        boardPaint.color = Color.parseColor("#27313A")
        canvas.drawRect(center, boardPaint)
        val triangle = Path()
        triangle.moveTo(center.centerX(), center.centerY())
        triangle.lineTo(center.left, center.top)
        triangle.lineTo(center.left, center.bottom)
        triangle.close()
        boardPaint.color = LudoSetup.PLAYER_COLORS[0]
        canvas.drawPath(triangle, boardPaint)
        triangle.reset()
        triangle.moveTo(center.centerX(), center.centerY())
        triangle.lineTo(center.left, center.top)
        triangle.lineTo(center.right, center.top)
        triangle.close()
        boardPaint.color = LudoSetup.PLAYER_COLORS[1]
        canvas.drawPath(triangle, boardPaint)
        triangle.reset()
        triangle.moveTo(center.centerX(), center.centerY())
        triangle.lineTo(center.right, center.top)
        triangle.lineTo(center.right, center.bottom)
        triangle.close()
        boardPaint.color = LudoSetup.PLAYER_COLORS[2]
        canvas.drawPath(triangle, boardPaint)
        triangle.reset()
        triangle.moveTo(center.centerX(), center.centerY())
        triangle.lineTo(center.right, center.bottom)
        triangle.lineTo(center.left, center.bottom)
        triangle.close()
        boardPaint.color = LudoSetup.PLAYER_COLORS[3]
        canvas.drawPath(triangle, boardPaint)
    }

    private fun drawYard(canvas: Canvas, player: Int, rect: RectF) {
        boardPaint.color = LudoSetup.PLAYER_SOFT_COLORS[player]
        canvas.drawRoundRect(rect, cell * 0.55f, cell * 0.55f, boardPaint)
        boardPaint.color = Color.argb(40, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(rect.left + cell * 0.45f, rect.top + cell * 0.45f,
                rect.right - cell * 0.45f, rect.bottom - cell * 0.45f),
            cell * 0.25f, cell * 0.25f, boardPaint
        )
    }

    private fun drawCell(canvas: Canvas, position: Position, color: Int) {
        val rect = cellRect(position)
        boardPaint.color = color
        canvas.drawRect(rect, boardPaint)
        canvas.drawRect(rect, gridPaint)
    }

    private fun drawMoveHints(canvas: Canvas) {
        for (move in legalMoves) {
            val point = centerOf(move.to)
            canvas.drawCircle(point.x, point.y, cell * 0.34f, highlightPaint)
            canvas.drawCircle(point.x, point.y, cell * 0.08f, highlightPaint)
        }
        selectedFrom?.let { from ->
            val point = centerOf(from)
            canvas.drawCircle(point.x, point.y, cell * 0.42f, highlightPaint)
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
            val from = centerOf(move.from)
            val to = centerOf(move.to)
            drawPiece(canvas, moving, PointF(
                from.x + (to.x - from.x) * animatedProgress,
                from.y + (to.y - from.y) * animatedProgress
            ))
        }
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
        val destination = legalMoves.firstOrNull { it.to == tapped }
        if (destination != null) {
            selectedFrom = null
            legalMoves = emptyList()
            onMoveSelected?.invoke(destination)
            return true
        }
        val source = legalMoves.firstOrNull { it.from == tapped }?.from
        if (source != null) selectedFrom = source else selectedFrom = null
        invalidate()
        return true
    }

    fun animateMove(move: Move, onEnd: () -> Unit) {
        val piece = gameState.get(move.from) as? LudoPiece ?: run { onEnd(); return }
        isLocked = true
        animatedMove = move
        animatedPiece = piece
        animatedProgress = 0f
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatedMove = null
                    animatedPiece = null
                    animatedProgress = 0f
                    isLocked = false
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
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