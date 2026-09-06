package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.yote.YotePiece
import com.mkdev.mkboardgames.games.yote.YoteRuleEngine
import kotlin.math.min

enum class YoteBoardVariant(
    val assetName: String,
    val label: String,
) {
    CARVED("yote_board_carved.webp", "Carved Reservoir"),
    RUSTIC("yote_board_rustic.webp", "Rustic Pebble"),
}

class YoteBoardView(context: Context) : View(context) {
    var gameState: GameState = YoteRuleEngine().initialState()
        set(value) {
            field = value
            selected = null
            invalidate()
        }

    var variant: YoteBoardVariant = YoteBoardVariant.CARVED
        set(value) {
            field = value
            boardBitmap = loadBitmap(value.assetName)
            updateGeometry(width, height)
            invalidate()
        }

    var playerColor: PieceColor = PieceColor.WHITE
    var vsAI: Boolean = false
    var isLocked: Boolean = false
    var onMoveMade: ((Move) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private val engine = YoteRuleEngine()
    private var selected: Position? = null
    private var boardRect = RectF()
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var boardBitmap: Bitmap? = loadBitmap(variant.assetName)
    private var atmospherePhase = 0f
    private var atmosphereAnimator: ValueAnimator? = null
    private var pendingMove: Move? = null
    private var movingFrom = PointF()
    private var movingTo = PointF()
    private var movingColor: PieceColor = PieceColor.WHITE
    private var moveProgress = 0f
    private var moveAnimator: ValueAnimator? = null

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitePiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 0, 0, 0) }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(175, 255, 246, 220)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFE09C")
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 227, 184, 106)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    init {
        isClickable = true
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        atmosphereAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 32_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                atmospherePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        atmosphereAnimator?.cancel()
        atmosphereAnimator = null
        moveAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        updateGeometry(w, h)
    }

    private fun updateGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val sourceWidth = boardBitmap?.width?.toFloat() ?: if (variant == YoteBoardVariant.CARVED) 0.72f else 1.3f
        val sourceHeight = boardBitmap?.height?.toFloat() ?: 1f
        val sourceAspect = if (boardBitmap != null) sourceWidth / sourceHeight else sourceWidth
        val maxWidth = w - 14f * density
        val maxHeight = h - 14f * density
        val scale = min(maxWidth / sourceWidth, maxHeight / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        boardRect.set(
            (w - drawWidth) / 2f,
            (h - drawHeight) / 2f,
            (w + drawWidth) / 2f,
            (h + drawHeight) / 2f,
        )
        if (boardBitmap == null) {
            val fallbackWidth = min(maxWidth, maxHeight * sourceAspect)
            val fallbackHeight = fallbackWidth / sourceAspect
            boardRect.set(
                (w - fallbackWidth) / 2f,
                (h - fallbackHeight) / 2f,
                (w + fallbackWidth) / 2f,
                (h + fallbackHeight) / 2f,
            )
        }
        cellWidth = boardRect.width() / YoteRuleEngine.COLUMNS
        cellHeight = boardRect.height() / YoteRuleEngine.ROWS
        gridPaint.strokeWidth = maxOf(1f, cellWidth * 0.018f)
        selectionPaint.strokeWidth = maxOf(2f, min(cellWidth, cellHeight) * 0.055f)
        rimPaint.strokeWidth = maxOf(1f, min(cellWidth, cellHeight) * 0.025f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawAtmosphere(canvas, w, h)
        drawBoard(canvas)
        drawTargets(canvas)
        drawPieces(canvas)
        drawLabels(canvas)
        drawMovingPiece(canvas)
    }

    private fun drawAtmosphere(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(Color.parseColor("#071522"))
        val drift = atmospherePhase * w
        listOf(
            Triple(w * 0.16f + drift * 0.08f, h * 0.18f, Color.rgb(45, 137, 171)),
            Triple(w * 0.86f - drift * 0.06f, h * 0.72f, Color.rgb(44, 152, 120)),
            Triple(w * 0.5f, h * 1.02f, Color.rgb(71, 37, 134)),
        ).forEach { (x, y, color) ->
            val radius = min(w, h) * 0.58f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    x,
                    y,
                    radius,
                    intArrayOf(
                        Color.argb(65, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(x, y, radius, glow)
        }
        val stars = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(34) { i ->
            val x = ((i * 83 + 41) % 1000) / 1000f * w
            val y = ((i * 47 + 19) % 960) / 1000f * h
            stars.color = Color.argb(55 + (i % 4) * 22, 214, 241, 245)
            canvas.drawCircle(x, y, resources.displayMetrics.density * (0.5f + i % 3 * 0.35f), stars)
        }
    }

    private fun drawBoard(canvas: Canvas) {
        boardBitmap?.let {
            imagePaint.alpha = 242
            imagePaint.setShadowLayer(
                resources.displayMetrics.density * 16f,
                0f,
                resources.displayMetrics.density * 10f,
                Color.argb(170, 0, 0, 0),
            )
            canvas.drawBitmap(it, null, boardRect, imagePaint)
            imagePaint.clearShadowLayer()
            return
        }

        boardPaint.shader = LinearGradient(
            0f,
            boardRect.top,
            0f,
            boardRect.bottom,
            Color.parseColor("#9A5B31"),
            Color.parseColor("#432316"),
            Shader.TileMode.CLAMP,
        )
        boardPaint.setShadowLayer(
            resources.displayMetrics.density * 12f,
            0f,
            resources.displayMetrics.density * 8f,
            Color.argb(190, 0, 0, 0),
        )
        canvas.drawRoundRect(boardRect, resources.displayMetrics.density * 18f, resources.displayMetrics.density * 18f, boardPaint)
        boardPaint.clearShadowLayer()
        boardPaint.shader = null
        gridPaint.color = Color.argb(130, 63, 28, 14)
        for (row in 0..YoteRuleEngine.ROWS) {
            val y = boardRect.top + row * cellHeight
            canvas.drawLine(boardRect.left, y, boardRect.right, y, gridPaint)
        }
        for (column in 0..YoteRuleEngine.COLUMNS) {
            val x = boardRect.left + column * cellWidth
            canvas.drawLine(x, boardRect.top, x, boardRect.bottom, gridPaint)
        }
    }

    private fun drawTargets(canvas: Canvas) {
        val selectedPosition = selected ?: return
        val selectedCenter = centerOf(selectedPosition)
        selectionPaint.color = Color.parseColor("#FFE09C")
        canvas.drawCircle(
            selectedCenter.x,
            selectedCenter.y,
            min(cellWidth, cellHeight) * 0.39f,
            selectionPaint,
        )
        engine.legalMovesFrom(gameState, selectedPosition)
            .filter { it.from == selectedPosition }
            .forEach {
                val center = centerOf(it.to)
                canvas.drawCircle(center.x, center.y, min(cellWidth, cellHeight) * 0.1f, targetPaint)
            }
    }

    private fun drawPieces(canvas: Canvas) {
        val radius = min(cellWidth, cellHeight) * 0.27f
        for (index in 0 until YoteRuleEngine.BOARD_CELLS) {
            val piece = gameState.board[index] as? YotePiece ?: continue
            drawPiece(canvas, centerOf(YoteRuleEngine.positionAt(index)), radius, piece.color)
        }
    }

    private fun drawMovingPiece(canvas: Canvas) {
        val move = pendingMove ?: return
        val radius = min(cellWidth, cellHeight) * 0.27f
        val x = movingFrom.x + (movingTo.x - movingFrom.x) * moveProgress
        val y = movingFrom.y + (movingTo.y - movingFrom.y) * moveProgress
        drawPiece(canvas, PointF(x, y), radius, movingColor)
        if (move.from == YoteRuleEngine.RESERVE) {
            labelPaint.color = Color.argb(160, 255, 246, 226)
            labelPaint.textSize = radius * 0.55f
            canvas.drawText("ENTER", x, y + radius * 1.9f, labelPaint)
        }
    }

    private fun drawPiece(canvas: Canvas, center: PointF, radius: Float, color: PieceColor) {
        canvas.drawCircle(center.x + radius * 0.12f, center.y + radius * 0.18f, radius * 1.03f, shadowPaint)
        val paint = if (color == PieceColor.WHITE) whitePiecePaint else blackPiecePaint
        paint.shader = RadialGradient(
            center.x - radius * 0.35f,
            center.y - radius * 0.38f,
            radius * 1.35f,
            if (color == PieceColor.WHITE) {
                intArrayOf(Color.parseColor("#FFF8E8"), Color.parseColor("#D8B77A"), Color.parseColor("#8B542F"))
            } else {
                intArrayOf(Color.parseColor("#657C83"), Color.parseColor("#1D3039"), Color.parseColor("#050A0E"))
            },
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(center.x, center.y, radius, paint)
        paint.shader = null
        rimPaint.color = if (color == PieceColor.WHITE) Color.argb(175, 255, 245, 208) else Color.argb(180, 117, 166, 174)
        canvas.drawCircle(center.x, center.y, radius, rimPaint)
        emptyPaint.color = if (color == PieceColor.WHITE) Color.argb(95, 255, 255, 255) else Color.argb(85, 210, 235, 238)
        canvas.drawCircle(center.x - radius * 0.31f, center.y - radius * 0.34f, radius * 0.18f, emptyPaint)
    }

    private fun drawLabels(canvas: Canvas) {
        labelPaint.color = Color.argb(170, 255, 246, 226)
        labelPaint.textSize = min(cellWidth, cellHeight) * 0.13f
        for (column in 0 until YoteRuleEngine.COLUMNS) {
            canvas.drawText(
                ('A'.code + column).toChar().toString(),
                boardRect.left + (column + 0.5f) * cellWidth,
                boardRect.bottom + labelPaint.textSize * 1.2f,
                labelPaint,
            )
        }
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
                val tapped = positionAt(event.x, event.y) ?: return true
                if (vsAI && gameState.currentTurn != playerColor) return true
                handleTap(tapped)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                return true
            }
        }
        return true
    }

    private fun handleTap(position: Position) {
        val currentSelection = selected
        if (currentSelection != null) {
            val move = engine.legalMovesFrom(gameState, currentSelection)
                .firstOrNull { it.to == position }
            if (move != null) {
                selected = null
                animateMove(move)
                return
            }
        }

        val piece = gameState.get(position)
        if (piece?.color == gameState.currentTurn) {
            val hasMove = engine.legalMovesFrom(gameState, position).isNotEmpty()
            selected = if (hasMove) position else null
        } else if (piece == null && engine.reserveCount(gameState, gameState.currentTurn) > 0) {
            selected = null
            animateMove(
                engine.legalMovesFrom(gameState, YoteRuleEngine.RESERVE)
                    .firstOrNull { it.to == position } ?: return,
            )
        } else {
            selected = null
        }
        invalidate()
    }

    fun animateMove(move: Move) {
        val destination = centerOf(move.to)
        movingColor = gameState.currentTurn
        movingFrom = if (move.from == YoteRuleEngine.RESERVE) {
            PointF(
                boardRect.centerX(),
                if (movingColor == PieceColor.WHITE) boardRect.bottom + cellHeight else boardRect.top - cellHeight,
            )
        } else {
            centerOf(move.from)
        }
        movingTo = destination
        pendingMove = move
        moveProgress = 0f
        isLocked = true
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            addUpdateListener {
                moveProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val completed = pendingMove ?: return
                    pendingMove = null
                    moveProgress = 0f
                    isLocked = false
                    onMoveMade?.invoke(completed)
                    invalidate()
                }
            })
            start()
        }
    }

    private fun positionAt(x: Float, y: Float): Position? {
        if (!boardRect.contains(x, y)) return null
        val column = ((x - boardRect.left) / cellWidth).toInt()
        val row = ((y - boardRect.top) / cellHeight).toInt()
        return if (row in 0 until YoteRuleEngine.ROWS && column in 0 until YoteRuleEngine.COLUMNS) {
            Position(row, column)
        } else {
            null
        }
    }

    private fun centerOf(position: Position): PointF =
        PointF(
            boardRect.left + (position.col + 0.5f) * cellWidth,
            boardRect.top + (position.row + 0.5f) * cellHeight,
        )

    private fun loadBitmap(assetName: String): Bitmap? =
        try {
            context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
}