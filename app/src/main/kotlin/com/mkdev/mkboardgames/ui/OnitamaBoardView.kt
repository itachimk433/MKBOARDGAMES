package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.onitama.OnitamaPiece
import com.mkdev.mkboardgames.games.onitama.OnitamaRuleEngine
import kotlin.math.min

class OnitamaBoardView(context: Context) : View(context) {
    var gameState: GameState = OnitamaRuleEngine().initialState()
        set(value) {
            field = value
            selected = null
            invalidate()
        }
    var playerColor: PieceColor = PieceColor.WHITE
    var vsAI: Boolean = false
    var isLocked: Boolean = false
    var selectedCardId: String? = null
        set(value) {
            field = value
            selected = null
            invalidate()
        }
    var onMoveMade: ((Move, Boolean) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private val engine = OnitamaRuleEngine()
    private var selected: Position? = null
    private var pendingMove: Move? = null
    private var pendingFromComputer = false
    private var moveProgress = 0f
    private var moveAnimator: ValueAnimator? = null
    private val boardBitmap: Bitmap? = runCatching {
        context.assets.open("onitama_board.webp").use(BitmapFactory::decodeStream)
    }.getOrNull()
    private val boardRect = RectF()
    private val gridRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(115, 35, 47, 43)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFE09C")
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        updateGeometry(w, h)
    }

    private fun updateGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val sourceWidth = boardBitmap?.width?.toFloat() ?: 1086f
        val sourceHeight = boardBitmap?.height?.toFloat() ?: 1448f
        val scale = min(w / sourceWidth, h / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        boardRect.set((w - drawWidth) / 2f, (h - drawHeight) / 2f, (w + drawWidth) / 2f, (h + drawHeight) / 2f)
        val left = boardRect.left + boardRect.width() * 0.166f
        val right = boardRect.left + boardRect.width() * 0.835f
        val top = boardRect.top + boardRect.height() * 0.242f
        val bottom = boardRect.top + boardRect.height() * 0.758f
        gridRect.set(left, top, right, bottom)
        gridPaint.strokeWidth = maxOf(1f, min(gridRect.width(), gridRect.height()) * 0.008f)
        selectedPaint.strokeWidth = maxOf(2f, min(gridRect.width(), gridRect.height()) * 0.03f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#071522"))
        boardBitmap?.let { canvas.drawBitmap(it, null, boardRect, imagePaint) }
        if (boardBitmap == null) {
            val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9E0C8") }
            canvas.drawRect(gridRect, boardPaint)
        }
        drawTargets(canvas)
        drawPieces(canvas)
        drawMovingPiece(canvas)
    }

    private fun drawTargets(canvas: Canvas) {
        val selectedPosition = selected ?: return
        val center = centerOf(selectedPosition)
        canvas.drawCircle(center.x, center.y, cellSize() * 0.39f, selectedPaint)
        val card = selectedCardId ?: return
        engine.legalMovesFrom(gameState, selectedPosition)
            .filter { it.metadata["card"] == card }
            .forEach { move ->
                val target = centerOf(move.to)
                targetPaint.color = if (gameState.get(move.to) == null) {
                    Color.argb(135, 49, 128, 111)
                } else {
                    Color.argb(190, 203, 83, 64)
                }
                canvas.drawCircle(target.x, target.y, cellSize() * 0.16f, targetPaint)
            }
    }

    private fun drawPieces(canvas: Canvas) {
        val hidden = pendingMove?.from
        for (index in 0 until OnitamaRuleEngine.BOARD_CELLS) {
            val position = Position(index / OnitamaRuleEngine.BOARD_SIZE, index % OnitamaRuleEngine.BOARD_SIZE)
            if (position == hidden) continue
            (gameState.get(position) as? OnitamaPiece)?.let { drawPiece(canvas, centerOf(position), cellSize() * if (it.isMaster) 0.31f else 0.27f, it) }
        }
    }

    private fun drawPiece(canvas: Canvas, center: PointF, radius: Float, piece: OnitamaPiece) {
        piecePaint.shader = RadialGradient(
            center.x - radius * 0.3f, center.y - radius * 0.35f, radius * 1.35f,
            if (piece.color == PieceColor.WHITE) {
                intArrayOf(Color.parseColor("#FFF8E8"), Color.parseColor("#D8B77A"), Color.parseColor("#8B542F"))
            } else {
                intArrayOf(Color.parseColor("#8CB8C5"), Color.parseColor("#275B6C"), Color.parseColor("#071A22"))
            },
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(center.x + radius * 0.08f, center.y + radius * 0.1f, radius, piecePaint)
        piecePaint.shader = null
        rimPaint.color = if (piece.isMaster) Color.parseColor("#FFE09C") else Color.argb(175, 255, 255, 255)
        rimPaint.strokeWidth = maxOf(1f, radius * 0.11f)
        canvas.drawCircle(center.x + radius * 0.08f, center.y + radius * 0.1f, radius, rimPaint)
        if (piece.isMaster) {
            textPaint.textSize = radius * 0.72f
            textPaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#5A2B17") else Color.parseColor("#E5F2F1")
            canvas.drawText("M", center.x + radius * 0.08f, center.y + radius * 0.25f, textPaint)
        }
    }

    private fun drawMovingPiece(canvas: Canvas) {
        val move = pendingMove ?: return
        val from = centerOf(move.from)
        val to = centerOf(move.to)
        val x = from.x + (to.x - from.x) * moveProgress
        val y = from.y + (to.y - from.y) * moveProgress
        val piece = gameState.get(move.from) as? OnitamaPiece ?: return
        drawPiece(canvas, PointF(x, y), cellSize() * if (piece.isMaster) 0.31f else 0.27f, piece)
    }

    private fun centerOf(position: Position): PointF =
        PointF(
            gridRect.left + (position.col + 0.5f) * gridRect.width() / OnitamaRuleEngine.BOARD_SIZE,
            gridRect.top + (position.row + 0.5f) * gridRect.height() / OnitamaRuleEngine.BOARD_SIZE,
        )

    private fun cellSize(): Float = min(gridRect.width(), gridRect.height()) / OnitamaRuleEngine.BOARD_SIZE

    private fun positionAt(x: Float, y: Float): Position? {
        if (!gridRect.contains(x, y)) return null
        val col = ((x - gridRect.left) / (gridRect.width() / OnitamaRuleEngine.BOARD_SIZE)).toInt()
        val row = ((y - gridRect.top) / (gridRect.height() / OnitamaRuleEngine.BOARD_SIZE)).toInt()
        return Position(row, col).takeIf { it.isValid(OnitamaRuleEngine.BOARD_SIZE) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (gameState.status != GameStatus.IN_PROGRESS) {
            onGameOverTapped?.invoke()
            return true
        }
        if (isLocked || pendingMove != null) return true
        if (vsAI && gameState.currentTurn != playerColor) return true
        val tapped = positionAt(event.x, event.y) ?: return true
        val card = selectedCardId
        val currentSelection = selected
        if (currentSelection != null && card != null) {
            val move = engine.legalMovesFrom(gameState, currentSelection)
                .firstOrNull { it.to == tapped && it.metadata["card"] == card }
            if (move != null) {
                selected = null
                animateMove(move, fromComputer = false)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                return true
            }
        }
        val piece = gameState.get(tapped) as? OnitamaPiece
        if (piece?.color == gameState.currentTurn) {
            selected = tapped
            invalidate()
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
            selected = null
            invalidate()
        }
        return true
    }

    fun animateMove(move: Move, fromComputer: Boolean) {
        if (moveAnimator != null) return
        pendingMove = move
        pendingFromComputer = fromComputer
        moveProgress = 0f
        isLocked = true
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                moveProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (moveAnimator !== animation) return
                    moveAnimator = null
                    val completed = pendingMove ?: return
                    val computer = pendingFromComputer
                    pendingMove = null
                    pendingFromComputer = false
                    isLocked = false
                    onMoveMade?.invoke(completed, computer)
                    invalidate()
                }
            })
            start()
        }
    }

    fun cancelMoveAnimation() {
        val animator = moveAnimator
        moveAnimator = null
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        pendingMove = null
        pendingFromComputer = false
        moveProgress = 0f
        isLocked = false
        invalidate()
    }
}