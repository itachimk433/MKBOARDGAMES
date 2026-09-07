package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
    /*
     * The carved artwork is a portrait board. Its five pocket columns and six
     * pocket rows are transposed relative to the engine's 5-row × 6-column
     * coordinates. These are the measured pocket centres in the source asset
     * (730 × 1024), expressed as fractions so they scale with the bitmap.
     */
    // Each entry is an artwork-space (x, y) centre of the visible circular
    // recess, measured from the rendered board artwork rather than from the
    // intervening square blocks.
    private val carvedPocketCenters = arrayOf(
        floatArrayOf(
            182f, 274f, 283f, 275f, 379f, 274f, 477f, 274f, 573f, 274f,
        ),
        floatArrayOf(
            184f, 369f, 282f, 370f, 381f, 371f, 479f, 370f, 574f, 369f,
        ),
        floatArrayOf(
            184f, 472f, 283f, 468f, 380f, 471f, 479f, 468f, 574f, 471f,
        ),
        floatArrayOf(
            183f, 567f, 282f, 570f, 378f, 570f, 477f, 570f, 574f, 570f,
        ),
        floatArrayOf(
            184f, 667f, 283f, 664f, 383f, 666f, 478f, 664f, 574f, 665f,
        ),
        floatArrayOf(
            183f, 764f, 283f, 764f, 380f, 764f, 478f, 764f, 574f, 765f,
        ),
    )
    // The carved bitmap includes transparent padding around the photographed
    // board. Render the board itself instead of scaling that padding.
    private val carvedArtworkRect = Rect(70, 76, 660, 950)
    // The carved asset has 100px pocket spacing and approximately 27px
    // pocket radius. Keep the rendered stone inside that measured opening,
    // including its rim and offset shadow.
    private val carvedPocketRadiusRatio = 27f / 100f
    private val carvedPieceRadiusRatio = 20f / 100f

    var gameState: GameState = YoteRuleEngine().initialState()
        set(value) {
            field = value
            selected = null
            capturablePositions = emptySet()
            invalidate()
        }

    var variant: YoteBoardVariant = YoteBoardVariant.CARVED
        set(value) {
            field = value
            boardBitmap = loadBitmap(value.assetName)
            boardSourceRect = boardBitmap?.let(::drawableSourceRect)
            updateGeometry(width, height)
            invalidate()
        }

    var playerColor: PieceColor = PieceColor.WHITE
    var vsAI: Boolean = false
    var isLocked: Boolean = false
    var onMoveMade: ((Move, Boolean) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private val engine = YoteRuleEngine()
    private var selected: Position? = null
    private var boardRect = RectF()
    private var gridRect = RectF()
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var boardBitmap: Bitmap? = loadBitmap(variant.assetName)
    private var boardSourceRect: Rect? = boardBitmap?.let(::drawableSourceRect)
    private var atmospherePhase = 0f
    private var atmosphereAnimator: ValueAnimator? = null
    private val atmospherePaints = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val atmosphereShaders = arrayOfNulls<RadialGradient>(3)
    private val atmosphereMatrices = Array(3) { Matrix() }
    private val starsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var atmosphereRadius = 0f
    private var pendingMove: Move? = null
    private var movingFrom = PointF()
    private var movingTo = PointF()
    private var movingColor: PieceColor = PieceColor.WHITE
    private var moveProgress = 0f
    private var moveAnimator: ValueAnimator? = null
    private var pendingMoveFromComputer = false
    private var pieceShaderRadius = 0f
    private var whitePieceShader: RadialGradient? = null
    private var blackPieceShader: RadialGradient? = null
    private val whitePieceShaderMatrix = Matrix()
    private val blackPieceShaderMatrix = Matrix()

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
        color = Color.WHITE
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 255, 255, 255)
    }
    private val captureTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val capturablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 214, 64)
    }
    private var capturablePositions: Set<Position> = emptySet()

    init {
        isClickable = true
        isFocusable = true
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
        cancelMoveAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        updateGeometry(w, h)
    }

    private fun updateGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val sourceRect = boardBitmap?.let(::drawableSourceRect)
        val sourceWidth = sourceRect?.width()?.toFloat() ?: if (variant == YoteBoardVariant.CARVED) 0.72f else 1.3f
        val sourceHeight = sourceRect?.height()?.toFloat() ?: 1f
        val sourceAspect = if (boardBitmap != null) sourceWidth / sourceHeight else sourceWidth
        // Match the full-bleed image treatment used by Shogi. The carved
        // artwork already contains its own transparent and wooden margins;
        // adding another inset makes the board unnecessarily small.
        val artworkInset = if (variant == YoteBoardVariant.CARVED) 0f else 14f * density
        val maxWidth = w - artworkInset
        val maxHeight = h - artworkInset
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
        // The rustic artwork contains a transparent border and a thick wooden
        // frame.  The playable cells occupy only the inset rectangle inside
        // that frame; using boardRect here shifts every piece and hitbox.
        gridRect = if (variant == YoteBoardVariant.RUSTIC) {
            RectF(
                boardRect.left + boardRect.width() * 0.055f,
                boardRect.top + boardRect.height() * 0.063f,
                boardRect.left + boardRect.width() * 0.963f,
                boardRect.top + boardRect.height() * 0.955f,
            )
        } else {
            RectF(boardRect)
        }
        if (variant == YoteBoardVariant.CARVED) {
            cellWidth = boardRect.width() * (100f / carvedArtworkRect.width())
            cellHeight = boardRect.height() * (100f / carvedArtworkRect.height())
        } else {
            cellWidth = gridRect.width() / YoteRuleEngine.COLUMNS
            cellHeight = gridRect.height() / YoteRuleEngine.ROWS
        }
        gridPaint.strokeWidth = maxOf(1f, cellWidth * 0.018f)
        selectionPaint.strokeWidth = maxOf(2f, min(cellWidth, cellHeight) * 0.055f)
        captureTargetPaint.strokeWidth = maxOf(2f, min(cellWidth, cellHeight) * 0.045f)
        capturablePaint.strokeWidth = maxOf(2.5f, min(cellWidth, cellHeight) * 0.065f)
        rimPaint.strokeWidth = maxOf(1f, min(cellWidth, cellHeight) * 0.025f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawAtmosphere(canvas, w, h)
        drawBoard(canvas)
        drawTargets(canvas)
        drawPieces(canvas)
        drawMovingPiece(canvas)
    }

    private fun drawAtmosphere(canvas: Canvas, w: Float, h: Float) {
        ensureAtmosphereShaders(w, h)
        canvas.drawColor(Color.parseColor("#071522"))
        val drift = atmospherePhase * w
        drawAtmosphereGlow(canvas, 0, w * 0.16f + drift * 0.08f, h * 0.18f)
        drawAtmosphereGlow(canvas, 1, w * 0.86f - drift * 0.06f, h * 0.72f)
        drawAtmosphereGlow(canvas, 2, w * 0.5f, h * 1.02f)
        repeat(34) { i ->
            val x = ((i * 83 + 41) % 1000) / 1000f * w
            val y = ((i * 47 + 19) % 960) / 1000f * h
            starsPaint.color = Color.argb(55 + (i % 4) * 22, 214, 241, 245)
            canvas.drawCircle(
                x,
                y,
                resources.displayMetrics.density * (0.5f + i % 3 * 0.35f),
                starsPaint,
            )
        }
    }

    private fun ensureAtmosphereShaders(w: Float, h: Float) {
        val radius = min(w, h) * 0.58f
        if (radius == atmosphereRadius && atmosphereShaders.all { it != null }) return

        atmosphereRadius = radius
        val colors = arrayOf(
            Color.rgb(45, 137, 171),
            Color.rgb(44, 152, 120),
            Color.rgb(71, 37, 134),
        )
        colors.forEachIndexed { index, color ->
            atmosphereShaders[index] = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(
                    Color.argb(65, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            atmospherePaints[index].shader = atmosphereShaders[index]
        }
    }

    private fun drawAtmosphereGlow(canvas: Canvas, index: Int, x: Float, y: Float) {
        atmosphereMatrices[index].setTranslate(x, y)
        atmosphereShaders[index]?.setLocalMatrix(atmosphereMatrices[index])
        canvas.drawCircle(x, y, atmosphereRadius, atmospherePaints[index])
    }

    private fun drawBoard(canvas: Canvas) {
        boardBitmap?.let {
            imagePaint.alpha = 242
            canvas.drawBitmap(it, boardSourceRect ?: drawableSourceRect(it), boardRect, imagePaint)
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
            val y = gridRect.top + row * cellHeight
            canvas.drawLine(gridRect.left, y, gridRect.right, y, gridPaint)
        }
        for (column in 0..YoteRuleEngine.COLUMNS) {
            val x = gridRect.left + column * cellWidth
            canvas.drawLine(x, gridRect.top, x, gridRect.bottom, gridPaint)
        }
    }

    private fun drawTargets(canvas: Canvas) {
        val selectedPosition = selected
        capturablePositions = if (
            selectedPosition != null &&
            pendingMove == null &&
            gameState.status == GameStatus.IN_PROGRESS
        ) {
            engine.legalMovesFrom(gameState, selectedPosition)
                .filter { it.isCapture }
                .asSequence()
                .flatMap { it.captures.asSequence() }
                .toSet()
        } else {
            emptySet()
        }

        selectedPosition ?: return
        val selectedCenter = centerOf(selectedPosition)
        selectionPaint.color = Color.WHITE
        canvas.drawCircle(
            selectedCenter.x,
            selectedCenter.y,
            highlightRadius(selectionPaint.strokeWidth, 0.39f),
            selectionPaint,
        )
        engine.legalMovesFrom(gameState, selectedPosition)
            .filter { it.from == selectedPosition }
            .forEach {
                val center = centerOf(it.to)
                if (it.isCapture) {
                    canvas.drawCircle(
                        center.x,
                        center.y,
                        highlightRadius(captureTargetPaint.strokeWidth, 0.39f),
                        captureTargetPaint,
                    )
                } else {
                    canvas.drawCircle(center.x, center.y, min(cellWidth, cellHeight) * 0.1f, targetPaint)
                }
            }
    }

    private fun drawPieces(canvas: Canvas) {
        val radius = pieceRadius()
        val hiddenPosition = pendingMove?.from?.takeUnless { it == YoteRuleEngine.RESERVE }
        for (index in 0 until YoteRuleEngine.BOARD_CELLS) {
            val piece = gameState.board[index] as? YotePiece ?: continue
            val position = YoteRuleEngine.positionAt(index)
            // Keep the captured stone visible until the activity commits the
            // new state, matching the capture presentation used by the other
            // board games. Only the moving stone's source is hidden.
            if (position == hiddenPosition) continue
            val center = centerOf(position)
            drawPiece(canvas, center.x, center.y, radius, piece.color)
            if (position in capturablePositions) {
                canvas.drawCircle(
                    center.x,
                    center.y,
                    capturableRadius(radius),
                    capturablePaint,
                )
            }
        }
    }

    private fun drawMovingPiece(canvas: Canvas) {
        val move = pendingMove ?: return
        val radius = pieceRadius()
        val x = movingFrom.x + (movingTo.x - movingFrom.x) * moveProgress
        val y = movingFrom.y + (movingTo.y - movingFrom.y) * moveProgress
        drawPiece(canvas, x, y, radius, movingColor)
    }

    private fun drawPiece(canvas: Canvas, x: Float, y: Float, radius: Float, color: PieceColor) {
        if (variant == YoteBoardVariant.CARVED) {
            // Keep the visual footprint inside the photographed pocket. The
            // old offset shadow made a correctly centred stone look shifted
            // down and right onto the intervening wooden block.
            canvas.drawCircle(
                x + radius * 0.04f,
                y + radius * 0.08f,
                radius * 0.98f,
                shadowPaint,
            )
        } else {
            canvas.drawCircle(x + radius * 0.12f, y + radius * 0.18f, radius * 1.03f, shadowPaint)
        }
        val paint = if (color == PieceColor.WHITE) whitePiecePaint else blackPiecePaint
        ensurePieceShaders(radius)
        val shader = if (color == PieceColor.WHITE) whitePieceShader else blackPieceShader
        val matrix = if (color == PieceColor.WHITE) whitePieceShaderMatrix else blackPieceShaderMatrix
        matrix.setTranslate(x - radius * 0.35f, y - radius * 0.38f)
        shader?.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
        rimPaint.color = if (color == PieceColor.WHITE) Color.argb(175, 255, 245, 208) else Color.argb(180, 117, 166, 174)
        canvas.drawCircle(x, y, radius, rimPaint)
        emptyPaint.color = if (color == PieceColor.WHITE) Color.argb(95, 255, 255, 255) else Color.argb(85, 210, 235, 238)
        canvas.drawCircle(x - radius * 0.31f, y - radius * 0.34f, radius * 0.18f, emptyPaint)
    }

    private fun ensurePieceShaders(radius: Float) {
        if (radius == pieceShaderRadius && whitePieceShader != null && blackPieceShader != null) return

        pieceShaderRadius = radius
        whitePieceShader = RadialGradient(
            0f,
            0f,
            radius * 1.35f,
            intArrayOf(Color.parseColor("#FFF8E8"), Color.parseColor("#D8B77A"), Color.parseColor("#8B542F")),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        blackPieceShader = RadialGradient(
            0f,
            0f,
            radius * 1.35f,
            intArrayOf(Color.parseColor("#657C83"), Color.parseColor("#1D3039"), Color.parseColor("#050A0E")),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun pocketRadius(): Float =
        min(cellWidth, cellHeight) * if (variant == YoteBoardVariant.CARVED) {
            carvedPocketRadiusRatio
        } else {
            0.39f
        }

    private fun pieceRadius(): Float =
        min(cellWidth, cellHeight) * if (variant == YoteBoardVariant.CARVED) {
            carvedPieceRadiusRatio
        } else {
            0.27f
        }

    private fun highlightRadius(strokeWidth: Float, rusticRatio: Float): Float =
        if (variant == YoteBoardVariant.CARVED) {
            (pocketRadius() - strokeWidth / 2f).coerceAtLeast(0f)
        } else {
            min(cellWidth, cellHeight) * rusticRatio
        }

    private fun capturableRadius(pieceRadius: Float): Float =
        if (variant == YoteBoardVariant.CARVED) {
            highlightRadius(capturablePaint.strokeWidth, 0f)
        } else {
            pieceRadius * 1.22f
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

    fun animateMove(move: Move, fromComputer: Boolean = false) {
        cancelMoveAnimation()
        atmosphereAnimator?.pause()
        val destination = centerOf(move.to)
        movingColor = gameState.currentTurn
        movingFrom = if (move.from == YoteRuleEngine.RESERVE) {
            PointF(
                boardRect.centerX(),
                if (movingColor == PieceColor.WHITE) {
                    boardRect.bottom + min(cellWidth, cellHeight) * 0.48f
                } else {
                    boardRect.top - min(cellWidth, cellHeight) * 0.48f
                },
            )
        } else {
            centerOf(move.from)
        }
        movingTo = destination
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
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (moveAnimator !== animation) return
                    val completed = pendingMove ?: return
                    moveAnimator = null
                    pendingMove = null
                    val completedFromComputer = pendingMoveFromComputer
                    pendingMoveFromComputer = false
                    moveProgress = 0f
                    isLocked = false
                    onMoveMade?.invoke(completed, completedFromComputer)
                    atmosphereAnimator?.resume()
                    invalidate()
                }
            })
        }
        moveAnimator = animator
        animator.start()
    }

    /**
     * Stops a visual move without reporting it to the activity.
     *
     * Android can call Animator listeners when an animator is cancelled. The
     * listener must be removed first or a move interrupted by recents,
     * rotation, or another animation can be committed after its state is
     * already stale.
     */
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
        atmosphereAnimator?.resume()
        invalidate()
    }

    private fun positionAt(x: Float, y: Float): Position? {
        if (variant == YoteBoardVariant.CARVED) {
            var closest: Position? = null
            var closestDistanceSquared = Float.MAX_VALUE
            for (row in 0 until YoteRuleEngine.ROWS) {
                for (column in 0 until YoteRuleEngine.COLUMNS) {
                    val center = centerOf(Position(row, column))
                    val dx = x - center.x
                    val dy = y - center.y
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared < closestDistanceSquared) {
                        closestDistanceSquared = distanceSquared
                        closest = Position(row, column)
                    }
                }
            }
            val hitRadius = if (variant == YoteBoardVariant.CARVED) {
                pocketRadius()
            } else {
                min(cellWidth, cellHeight) * 0.48f
            }
            return closest.takeIf { closestDistanceSquared <= hitRadius * hitRadius }
        }
        if (!gridRect.contains(x, y)) return null
        val column = ((x - gridRect.left) / cellWidth).toInt()
        val row = ((y - gridRect.top) / cellHeight).toInt()
        return if (row in 0 until YoteRuleEngine.ROWS && column in 0 until YoteRuleEngine.COLUMNS) {
            Position(row, column)
        } else {
            null
        }
    }

    private fun centerOf(position: Position): PointF =
        if (variant == YoteBoardVariant.CARVED) {
            // Engine row/column become artwork column/row respectively.
            val measured = carvedPocketCenters[position.col]
            val offset = position.row * 2
            PointF(
                boardRect.left + boardRect.width() *
                    ((measured[offset] - carvedArtworkRect.left) / carvedArtworkRect.width()),
                boardRect.top + boardRect.height() *
                    ((measured[offset + 1] - carvedArtworkRect.top) /
                        carvedArtworkRect.height()),
            )
        } else {
            PointF(
                gridRect.left + (position.col + 0.5f) * cellWidth,
                gridRect.top + (position.row + 0.5f) * cellHeight,
            )
        }

    private fun loadBitmap(assetName: String): Bitmap? =
        try {
            context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }

    private fun drawableSourceRect(bitmap: Bitmap): Rect =
        if (bitmap.width == 730 && bitmap.height == 1024) {
            Rect(carvedArtworkRect)
        } else {
            Rect(
                0,
                0,
                bitmap.width,
                bitmap.height,
            )
        }
}