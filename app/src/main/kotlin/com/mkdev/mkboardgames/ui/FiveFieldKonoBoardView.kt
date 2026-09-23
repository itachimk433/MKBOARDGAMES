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
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.fivefieldkono.FiveFieldKonoPiece
import com.mkdev.mkboardgames.games.fivefieldkono.FiveFieldKonoRuleEngine
import kotlin.math.min

class FiveFieldKonoBoardView(context: Context) : View(context) {
    var gameState: GameState = FiveFieldKonoRuleEngine().initialState()
        set(value) {
            field = value
            selected = null
            invalidate()
        }

    var boardStyle: FiveFieldKonoBoardStyle = FiveFieldKonoBoardStyle.WOOD
        set(value) {
            if (field == value) return
            field = value
            boardBitmap = loadBitmap(value.assetName)
            if (width > 0 && height > 0) updateGeometry(width, height)
            invalidate()
        }

    var playerColor: PieceColor = PieceColor.WHITE
    var vsAI: Boolean = false
    var isLocked: Boolean = false
    var onMoveMade: ((Move, Boolean) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private val engine = FiveFieldKonoRuleEngine()
    private var selected: Position? = null
    private var boardRect = RectF()
    private var movingFrom = PointF()
    private var movingTo = PointF()
    private var movingColor = PieceColor.WHITE
    private var pendingMove: Move? = null
    private var pendingMoveFromComputer = false
    private var moveProgress = 0f
    private var moveAnimator: ValueAnimator? = null

    private var boardBitmap: Bitmap? = loadBitmap(boardStyle.assetName)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundPaint = Paint().apply { color = Color.parseColor("#071522") }
    private val gridPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 229, 140)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFE29A")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 0, 0, 0)
    }
    private val whitePiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        // Point centres measured from the supplied 1105 × 1423 board artwork.
        private val COLUMN_POINTS = floatArrayOf(
            0.085f, 0.291f, 0.500f, 0.709f, 0.919f,
        )
        private val ROW_POINTS = floatArrayOf(
            0.168f, 0.326f, 0.484f, 0.642f, 0.800f,
        )
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDetachedFromWindow() {
        cancelMoveAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        updateGeometry(w, h)
    }

    private fun updateGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val sourceWidth = 1105f
        val sourceHeight = 1423f
        val scale = min(w / sourceWidth, h / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        boardRect.set(
            (w - drawWidth) / 2f,
            (h - drawHeight) / 2f,
            (w + drawWidth) / 2f,
            (h + drawHeight) / 2f,
        )
        val spacing = min(
            boardRect.width() * (COLUMN_POINTS[1] - COLUMN_POINTS[0]),
            boardRect.height() * (ROW_POINTS[1] - ROW_POINTS[0]),
        )
        selectionPaint.strokeWidth = maxOf(2f, spacing * 0.055f)
        rimPaint.strokeWidth = maxOf(1f, spacing * 0.035f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawBoard(canvas)
        drawTargets(canvas)
        drawPieces(canvas)
        drawMovingPiece(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        boardBitmap?.let {
            canvas.drawBitmap(it, null, boardRect, imagePaint)
            return
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B77A45")
            style = Paint.Style.STROKE
            strokeWidth = min(boardRect.width(), boardRect.height()) * 0.006f
        }
        for (row in 0..FiveFieldKonoRuleEngine.SIZE) {
            val y = boardRect.top + boardRect.height() * (0.10f + row * 0.16f)
            canvas.drawLine(boardRect.left + boardRect.width() * 0.08f, y,
                boardRect.right - boardRect.width() * 0.08f, y, gridPaint)
        }
        for (col in 0..FiveFieldKonoRuleEngine.SIZE) {
            val x = boardRect.left + boardRect.width() * (0.08f + col * 0.21f)
            canvas.drawLine(x, boardRect.top + boardRect.height() * 0.10f,
                x, boardRect.bottom - boardRect.height() * 0.20f, gridPaint)
        }
    }

    private fun drawTargets(canvas: Canvas) {
        val chosen = selected ?: return
        val center = centerOf(chosen)
        canvas.drawCircle(center.x, center.y, pieceRadius() * 1.22f, selectionPaint)
        engine.legalMovesFrom(gameState, chosen).forEach { move ->
            val target = centerOf(move.to)
            canvas.drawCircle(target.x, target.y, pieceRadius() * 0.20f, targetPaint)
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val hidden = pendingMove?.from
        for (index in 0 until FiveFieldKonoRuleEngine.BOARD_CELLS) {
            val position = FiveFieldKonoRuleEngine.positionAt(index)
            if (position == hidden) continue
            val piece = gameState.board[index] as? FiveFieldKonoPiece ?: continue
            val center = centerOf(position)
            drawPiece(canvas, center.x, center.y, piece.color)
        }
    }

    private fun drawMovingPiece(canvas: Canvas) {
        val move = pendingMove ?: return
        val x = movingFrom.x + (movingTo.x - movingFrom.x) * moveProgress
        val y = movingFrom.y + (movingTo.y - movingFrom.y) * moveProgress
        drawPiece(canvas, x, y, movingColor)
    }

    private fun drawPiece(canvas: Canvas, x: Float, y: Float, color: PieceColor) {
        val radius = pieceRadius()
        canvas.drawCircle(x + radius * 0.10f, y + radius * 0.14f, radius * 1.04f, shadowPaint)
        val paint = if (color == PieceColor.WHITE) whitePiecePaint else blackPiecePaint
        val colors = if (color == PieceColor.WHITE) {
            intArrayOf(Color.parseColor("#FFFDF7"), Color.parseColor("#D9D0C1"), Color.parseColor("#80766A"))
        } else {
            intArrayOf(Color.parseColor("#56666D"), Color.parseColor("#202C32"), Color.parseColor("#05090C"))
        }
        paint.shader = RadialGradient(
            x - radius * 0.30f, y - radius * 0.34f, radius * 1.35f,
            colors, floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
        rimPaint.color = if (color == PieceColor.WHITE) Color.BLACK else Color.WHITE
        canvas.drawCircle(x, y, radius, rimPaint)
        highlightPaint.color = Color.argb(if (color == PieceColor.WHITE) 105 else 75, 255, 255, 255)
        canvas.drawCircle(x - radius * 0.30f, y - radius * 0.34f, radius * 0.16f, highlightPaint)
    }

    private fun pieceRadius(): Float {
        val spacing = min(
            boardRect.width() * (COLUMN_POINTS[1] - COLUMN_POINTS[0]),
            boardRect.height() * (ROW_POINTS[1] - ROW_POINTS[0]),
        )
        return spacing * 0.31f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (gameState.status != GameStatus.IN_PROGRESS) {
                    onGameOverTapped?.invoke()
                    return true
                }
                if (isLocked || pendingMove != null) return true
                if (vsAI && gameState.currentTurn != playerColor) return true
                val tapped = positionAt(event.x, event.y) ?: return true
                handleTap(tapped)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                return true
            }
        }
        return true
    }

    private fun handleTap(position: Position) {
        val current = selected
        if (current != null) {
            engine.legalMovesFrom(gameState, current)
                .firstOrNull { it.to == position }
                ?.let {
                    selected = null
                    animateMove(it)
                    return
                }
        }
        selected = if (gameState.get(position)?.color == gameState.currentTurn &&
            engine.legalMovesFrom(gameState, position).isNotEmpty()
        ) {
            position
        } else {
            null
        }
        invalidate()
    }

    fun animateMove(move: Move, fromComputer: Boolean = false) {
        cancelMoveAnimation()
        movingColor = gameState.currentTurn
        movingFrom = centerOf(move.from)
        movingTo = centerOf(move.to)
        pendingMove = move
        pendingMoveFromComputer = fromComputer
        moveProgress = 0f
        isLocked = true
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                moveProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (moveAnimator !== animation) return
                    val completed = pendingMove ?: return
                    moveAnimator = null
                    pendingMove = null
                    val fromComputerMove = pendingMoveFromComputer
                    pendingMoveFromComputer = false
                    moveProgress = 0f
                    isLocked = false
                    onMoveMade?.invoke(completed, fromComputerMove)
                    invalidate()
                }
            })
        }
        moveAnimator = animator
        animator.start()
    }

    fun cancelMoveAnimation() {
        val animator = moveAnimator
        moveAnimator = null
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        pendingMove = null
        pendingMoveFromComputer = false
        moveProgress = 0f
        isLocked = false
        invalidate()
    }

    fun pauseMoveAnimation() {
        moveAnimator?.pause()
    }

    fun resumeMoveAnimation() {
        moveAnimator?.resume()
    }

    fun hasPendingMoveAnimation(): Boolean = moveAnimator != null || pendingMove != null

    private fun positionAt(x: Float, y: Float): Position? {
        if (!boardRect.contains(x, y)) return null
        val sourceX = (x - boardRect.left) / boardRect.width()
        val sourceY = (y - boardRect.top) / boardRect.height()
        val col = nearestIndex(sourceX, COLUMN_POINTS)
        val row = nearestIndex(sourceY, ROW_POINTS)
        val spacing = min(
            boardRect.width() * (COLUMN_POINTS[1] - COLUMN_POINTS[0]),
            boardRect.height() * (ROW_POINTS[1] - ROW_POINTS[0]),
        )
        val dx = kotlin.math.abs(sourceX - COLUMN_POINTS[col]) * boardRect.width()
        val dy = kotlin.math.abs(sourceY - ROW_POINTS[row]) * boardRect.height()
        return if (dx * dx + dy * dy <= (spacing * 0.60f) * (spacing * 0.60f)) {
            Position(row, col)
        } else {
            null
        }
    }

    private fun nearestIndex(value: Float, points: FloatArray): Int =
        points.indices.minByOrNull { kotlin.math.abs(value - points[it]) } ?: 0

    private fun centerOf(position: Position): PointF =
        PointF(
            boardRect.left + boardRect.width() * COLUMN_POINTS[position.col],
            boardRect.top + boardRect.height() * ROW_POINTS[position.row],
        )

    private fun loadBitmap(assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}