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
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.ludo.LudoEconomy
import com.mkdev.mkboardgames.games.ludo.LudoPiece
import com.mkdev.mkboardgames.games.ludo.LudoSetup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class LudoBoardView(context: Context) : View(context) {
    companion object {
        // The pin bitmap extends above its sharp-tip anchor by roughly one
        // board cell. Reserve this much space above the square so edge tokens
        // are not clipped by this View's own canvas bounds.
        const val EDGE_OVERFLOW_DP = 30
        private const val BOARD_ARTWORK_SIZE = 1254f
    }

    enum class Board(
        val assetName: String,
        val tokenAssetNames: Array<String>,
        val tokenTipFractions: FloatArray,
    ) {
        ONE(
            assetName = "ludo_board_reference.webp",
            tokenAssetNames = arrayOf(
                "ludo_token_red.webp",
                "ludo_token_blue.webp",
                "ludo_token_green.webp",
                "ludo_token_yellow.webp",
            ),
            tokenTipFractions = floatArrayOf(0.961f, 0.977f, 0.977f, 0.953f),
        ),
        TWO(
            assetName = "ludo_board_snow.webp",
            tokenAssetNames = arrayOf(
                "ludo_token_snow_red.webp",
                "ludo_token_snow_blue.webp",
                "ludo_token_snow_green.webp",
                "ludo_token_snow_yellow.webp",
            ),
            tokenTipFractions = floatArrayOf(0.956f, 0.949f, 0.960f, 0.922f),
        ),
    }

    var gameState: GameState = LudoSetup.initialState()
        set(value) {
            field = value
            selectedFrom = null
            // State refreshes can happen while a player is choosing a token
            // (for example after an economy/profile update). Keep the current
            // legal destinations until the activity explicitly replaces them.
            updateGeometry()
            invalidate()
        }

    var legalMoves: List<Move> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var isLocked: Boolean = false
    var showTokenNumbers: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var rotateOppositeSideTokenNumbers: Boolean = false
    var onMoveSelected: ((Move) -> Unit)? = null
    var onMoveStep: (() -> Unit)? = null
    var onTokenLongPressed: ((LudoPiece) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null

    private var selectedFrom: Position? = null
    private val touchHandler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var pendingLongPressPiece: LudoPiece? = null
    private var longPressTriggered = false
    private val longPressAction = Runnable {
        val piece = pendingLongPressPiece ?: return@Runnable
        longPressTriggered = true
        onTokenLongPressed?.invoke(piece)
    }
    private var cell = 0f
    private var left = 0f
    private var top = 0f
    private var animatedMove: Move? = null
    private var animatedPiece: LudoPiece? = null
    private var animatedPath: List<Position> = emptyList()
    private var animatedProgress = 0f
    private var animatedSoundStep = -1
    private var moveAnimator: ValueAnimator? = null
    private var moveGeneration = 0
    private var protectionPulse = 0f
    private var tokenIdlePulse = 0f
    private val protectionPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 950L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            protectionPulse = it.animatedValue as Float
            invalidate()
        }
    }
    private val tokenIdleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            tokenIdlePulse = it.animatedValue as Float
            invalidate()
        }
    }

    private var selectedBoard = Board.ONE
    private var boardBitmap: Bitmap? = loadBitmap(selectedBoard.assetName)
    private var tokenBitmaps: Array<Bitmap?> = loadTokenBitmaps(selectedBoard)
    private data class RenderedPiece(
        val piece: LudoPiece,
        val point: PointF,
    )
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tokenBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
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
    private val protectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val tokenOrbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val tokenBaseHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.STROKE
    }

    fun setBoard(board: Board) {
        if (selectedBoard == board) return
        selectedBoard = board
        boardBitmap = loadBitmap(board.assetName)
        tokenBitmaps = loadTokenBitmaps(board)
        updateGeometry()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeAnimations()
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    fun cancelAnimations() {
        cancelPendingLongPress()
        moveGeneration++
        moveAnimator?.cancel()
        moveAnimator = null
        animatedMove = null
        animatedPiece = null
        animatedPath = emptyList()
        animatedProgress = 0f
        animatedSoundStep = -1
        isLocked = false
        protectionPulseAnimator.cancel()
        tokenIdleAnimator.cancel()
        invalidate()
    }

    fun pauseAnimations() {
        moveAnimator?.pause()
    }

    fun resumeAnimations() {
        moveAnimator?.resume()
        if (isAttachedToWindow && !protectionPulseAnimator.isStarted) {
            protectionPulseAnimator.start()
        }
        if (isAttachedToWindow && !tokenIdleAnimator.isStarted) {
            tokenIdleAnimator.start()
        }
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
        val edgeOverflow = edgeOverflowPixels().toFloat()
        val measuredWidth = resolveSize(boardSize, widthMeasureSpec)
        val measuredHeight = resolveSize((boardSize + edgeOverflow).toInt(), heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateGeometry()

    private fun updateGeometry() {
        if (width == 0 || height == 0) return
        val edgeOverflow = edgeOverflowPixels().toFloat()
        val availableBoardHeight = (height - edgeOverflow).coerceAtLeast(0f)
        val boardSize = minOf(width.toFloat(), availableBoardHeight) * 0.98f
        cell = boardSize / LudoSetup.BOARD_SIZE
        left = (width - boardSize) / 2f
        // Keep the board artwork where it was while adding all extra height
        // above it. The token can then extend upward without leaving the View.
        top = edgeOverflow + (availableBoardHeight - boardSize) / 2f
        highlightPaint.strokeWidth = cell * 0.08f
        textPaint.textSize = cell * 0.35f
    }

    fun edgeOverflowPixels(): Int =
        (EDGE_OVERFLOW_DP * resources.displayMetrics.density).toInt()

    fun boardArtworkTopPixels(): Int = top.toInt()

    fun boardArtworkBottomPixels(): Int =
        (top + LudoSetup.BOARD_SIZE * cell).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The view extends into the top rail so edge tokens have room to
        // render. Keep that overflow transparent; an opaque fill here would
        // cover the CPU profile cards below this view's z-layer.
        drawBoard(canvas)
        drawMoveHints(canvas)
        drawMoveSourceHighlights(canvas)
        drawTurnMarker(canvas)
        // Tokens are the top layer. Board highlights and turn markers must
        // stay behind them so no overlay can cover the token bitmap or tip.
        drawPieces(canvas)
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
            val movingPiece = LudoSetup.pieceForMove(gameState, move)
            val isYardLaunch = (movingPiece?.progress ?: 0) < 0
            // The source cell is the starting point, not step one. Show only
            // the route markers between source and destination so intermediate
            // cells cannot be mistaken for separate legal destinations.
            //
            // A yard launch has no user-selectable destination yet: the token
            // itself is the action target. Drawing the start-cell hint here
            // makes it look like an extra square appears before the token
            // leaves its yard.
            if (!isYardLaunch) {
                drawRoutePreview(canvas, path.drop(1).dropLast(1), accent)
                val point = centerOf(move.to)
                highlightPaint.color = Color.argb(
                    245,
                    Color.red(accent),
                    Color.green(accent),
                    Color.blue(accent),
                )
                canvas.drawCircle(point.x, point.y, cell * 0.34f, highlightPaint)
                highlightPaint.color = Color.argb(240, 255, 255, 255)
                canvas.drawCircle(point.x, point.y, cell * 0.08f, highlightPaint)
            }
        }
        animatedMove?.let { drawPathHighlight(canvas, animatedPath, accentColorFor(it)) }
    }

    private fun drawMoveSourceHighlights(canvas: Canvas) {
        // Keep the roll-six/source ring above the token bitmap. Drawing it
        // before the token made the white pin body hide most of the ring,
        // especially for a yard launch.
        val sourceMoves = legalMoves.distinctBy { move ->
            Triple(
                move.from,
                move.metadata["player"] as? Int,
                move.metadata["token"] as? Int,
            )
        }
        for (move in sourceMoves) {
            val accent = accentColorFor(move)
            val sourcePoint = sourcePointFor(move)
            highlightPaint.color = Color.argb(
                245,
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent),
            )
            canvas.drawCircle(sourcePoint.x, sourcePoint.y, cell * 0.43f, highlightPaint)
            highlightPaint.color = Color.argb(235, 255, 255, 255)
            canvas.drawCircle(sourcePoint.x, sourcePoint.y, cell * 0.36f, highlightPaint)
        }
        selectedFrom?.let { from ->
            val point = pointForPosition(from)
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

    private fun sourcePointFor(move: Move): PointF {
        val piece = LudoSetup.pieceForMove(gameState, move)
            ?: (gameState.get(move.from) as? LudoPiece)
        return piece?.let { pointFor(it, move.from) } ?: pointForPosition(move.from)
    }

    private fun drawPieces(canvas: Canvas) {
        val moving = animatedPiece
        val piecesByPosition = LudoSetup.allPieces(gameState)
            .groupBy { LudoSetup.positionOf(it) }
        val renderedPieces = mutableListOf<RenderedPiece>()
        for ((position, stack) in piecesByPosition) {
            stack.forEachIndexed { index, piece ->
                if (moving != null && piece.player == moving.player && piece.token == moving.token) {
                    return@forEachIndexed
                }
                val center = pointFor(piece, position)
                val offset = if (piece.progress < 0) {
                    PointF()
                } else {
                    stackOffset(index, stack.size)
                }
                renderedPieces += RenderedPiece(
                    piece = piece,
                    point = PointF(center.x + offset.x, center.y + offset.y),
                )
            }
        }
        val move = animatedMove
        if (moving != null && move != null) {
            // A token leaving the yard transitions to the track position, so
            // it must use its sharp tip as the anchor for the whole animation.
            renderedPieces += RenderedPiece(
                piece = moving,
                point = animatedPiecePoint(),
            )
        }

        // Draw every orbit and protection underlay before any token bitmap.
        // Drawing these inside the token loop lets a later token's orbit
        // appear on top of an earlier token when pieces overlap.
        renderedPieces.forEach { rendered ->
            drawPieceUnderlay(canvas, rendered.piece, rendered.point)
        }
        renderedPieces.forEach { rendered ->
            drawPiece(
                canvas,
                rendered.piece,
                rendered.point,
            )
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
        val piece = animatedPiece
        val from = piece?.let { pointFor(it, path[lowerIndex]) }
            ?: centerOf(path[lowerIndex])
        val to = piece?.let { pointFor(it, path[upperIndex]) }
            ?: centerOf(path[upperIndex])
        val jumpHeight = sin(segmentProgress * PI).toFloat() * cell * 0.18f
        return PointF(
            from.x + (to.x - from.x) * segmentProgress,
            from.y + (to.y - from.y) * segmentProgress - jumpHeight
        )
    }

    private fun drawPieceUnderlay(
        canvas: Canvas,
        piece: LudoPiece,
        point: PointF,
    ) {
        val isProtected = piece.progress in 0 until LudoSetup.FINISH &&
            LudoEconomy.player(gameState, piece.player).protectedToken == piece.token
        val baseIndicatorRadius = if (piece.progress < 0) {
            cell * 0.47f
        } else {
            cell * 0.47f / 1.5f
        }
        if (piece.progress < LudoSetup.FINISH) {
            drawTokenBaseIndicator(
                canvas,
                point,
                baseIndicatorRadius,
            )
        }
        if (isProtected) {
            val radius = cell * 0.34f
            protectionPaint.style = Paint.Style.FILL
            protectionPaint.color = Color.argb(75, 255, 216, 91)
            canvas.drawCircle(point.x, point.y, radius + cell * 0.15f, protectionPaint)
            protectionPaint.style = Paint.Style.STROKE
            protectionPaint.strokeWidth = cell * 0.075f
            protectionPaint.color = Color.rgb(255, 216, 91)
            canvas.drawCircle(point.x, point.y, radius + cell * 0.10f, protectionPaint)
        }
    }

    private fun drawPiece(
        canvas: Canvas,
        piece: LudoPiece,
        point: PointF,
    ) {
        val radius = cell * 0.34f
        val isProtected = piece.progress in 0 until LudoSetup.FINISH &&
            LudoEconomy.player(gameState, piece.player).protectedToken == piece.token
        val tokenBitmap = tokenBitmaps.getOrNull(piece.player)
        val breathScale = 1f + 0.045f * (
            0.5f - 0.5f * cos(tokenIdlePulse * 2f * PI).toFloat()
        )
        val tokenHeight = tokenBitmap?.let { cell * 0.92f * 1.6f * breathScale }
        var labelY = point.y
        if (tokenBitmap != null) {
            val tokenWidth = tokenHeight!! * tokenBitmap.width.toFloat() / tokenBitmap.height.toFloat()
            // The sharp bottom tip is the board-position anchor in both the
            // yard and on the track. This places the tip and dotted indicator
            // in the measured home-circle instead of aligning by the head.
            val anchorFraction = selectedBoard.tokenTipFractions[piece.player]
            val tokenTop = point.y - tokenHeight!! * anchorFraction
            val tokenRect = RectF(
                point.x - tokenWidth / 2f,
                tokenTop,
                point.x + tokenWidth / 2f,
                tokenTop + tokenHeight!!,
            )
            canvas.drawBitmap(tokenBitmap, null, tokenRect, tokenBitmapPaint)
            labelY = tokenTop + tokenHeight!! * 0.34f
        } else {
            // Keep the board usable if an asset is unavailable on an older
            // install or is removed during packaging.
            canvas.drawCircle(point.x + cell * 0.05f, point.y + cell * 0.08f, radius, shadowPaint)
            piecePaint.color = LudoSetup.PLAYER_COLORS[piece.player]
            canvas.drawCircle(point.x, point.y, radius, piecePaint)
            piecePaint.style = Paint.Style.STROKE
            piecePaint.strokeWidth = cell * 0.055f
            piecePaint.color = Color.argb(230, 255, 255, 255)
            canvas.drawCircle(point.x, point.y, radius, piecePaint)
            piecePaint.style = Paint.Style.FILL
        }
        if (showTokenNumbers) {
            textPaint.color = Color.WHITE
            canvas.save()
            if (rotateOppositeSideTokenNumbers && LudoSetup.facesOppositeSide(piece.player)) {
                canvas.rotate(180f, point.x, labelY)
            }
            canvas.drawText(
                piece.symbol(),
                point.x,
                labelY - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint,
            )
            canvas.restore()
        }
        if (isProtected) {
            drawProtectionShield(canvas, point.x, point.y + radius * 1.18f)
        }
    }

    private fun drawTokenBaseIndicator(canvas: Canvas, point: PointF, radius: Float) {
        // The indicator is a single shared geometry: its outline and dots
        // have the same center as the board position. In a yard, that is the
        // measured home-circle center; on the track, it is the token tip.
        // Draw it before the token so the pin remains in front of it.
        tokenBaseHighlightPaint.strokeWidth = cell * 0.04f
        tokenBaseHighlightPaint.color = Color.argb(95, 0, 0, 0)
        tokenBaseHighlightPaint.style = Paint.Style.STROKE
        canvas.drawCircle(point.x, point.y, radius, tokenBaseHighlightPaint)

        // Larger dots with fewer positions leave a little more breathing
        // room between each dot while keeping the ring easy to read.
        val dotCount = 13
        val orbitRadius = radius
        val dotRadius = cell * 0.052f
        val rotation = tokenIdlePulse * 360f
        for (index in 0 until dotCount) {
            val angle = Math.toRadians(
                (rotation + index * (360f / dotCount)).toDouble()
            )
            canvas.drawCircle(
                point.x + cos(angle).toFloat() * orbitRadius,
                point.y + sin(angle).toFloat() * orbitRadius,
                dotRadius,
                tokenOrbitPaint,
            )
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
        // A yard launch already has a visible token/source highlight. The
        // normal turn marker sits on the same start square and looks like an
        // extra destination while the token is about to leave the yard.
        val launchPreviewVisible = legalMoves.any(::isYardLaunch) ||
            animatedMove?.let(::isYardLaunch) == true
        if (launchPreviewVisible) return

        val player = LudoSetup.playerFromState(gameState)
        val point = centerOf(LudoSetup.PATH[LudoSetup.startOffset(player)])
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = cell * 0.04f
        piecePaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawCircle(point.x, point.y, cell * 0.47f, piecePaint)
        piecePaint.style = Paint.Style.FILL
    }

    private fun isYardLaunch(move: Move): Boolean =
        (LudoSetup.pieceForMove(gameState, move)?.progress ?: 0) < 0 &&
            (move.metadata["targetProgress"] as? Int) == 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                longPressTriggered = false
                pendingLongPressPiece = if (!isLocked &&
                    gameState.status == com.mkdev.mkboardgames.engine.GameStatus.IN_PROGRESS
                ) {
                    pieceAtTouch(event.x, event.y)
                        ?: positionAt(event.x, event.y)?.let { position ->
                            pieceAt(position, event.x, event.y)
                        }
                } else {
                    null
                }
                touchHandler.removeCallbacks(longPressAction)
                if (pendingLongPressPiece != null) {
                    touchHandler.postDelayed(longPressAction, longPressTimeout)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                if (kotlin.math.abs(event.x - touchDownX) > touchSlop ||
                    kotlin.math.abs(event.y - touchDownY) > touchSlop
                ) {
                    cancelPendingLongPress()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchHandler.removeCallbacks(longPressAction)
                pendingLongPressPiece = null
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true
                }
            }
            else -> return true
        }
        if (gameState.status != com.mkdev.mkboardgames.engine.GameStatus.IN_PROGRESS) {
            onGameOverTapped?.invoke()
            return true
        }
        if (isLocked) return true
        val touchedPiece = pieceAtTouch(event.x, event.y)
        val tapped = positionAt(event.x, event.y)
            ?: touchedPiece?.let { LudoSetup.positionOf(it) }
            ?: return true
        val source = touchedPiece?.let { piece ->
            legalMoves
                .filter { move ->
                    move.metadata["player"] == piece.player &&
                        move.metadata["token"] == piece.token
                }
                .minByOrNull { sourceDistance(it, event.x, event.y) }
        } ?: legalMoves
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

    private fun cancelPendingLongPress() {
        touchHandler.removeCallbacks(longPressAction)
        pendingLongPressPiece = null
        longPressTriggered = false
    }

    private fun pieceAt(position: Position, x: Float, y: Float): LudoPiece? {
        val stack = LudoSetup.piecesAt(gameState, position)
        val positionCenter = pointForPosition(position)
        return stack.minByOrNull { piece ->
            val index = stack.indexOf(piece)
            val offset = stackOffset(index, stack.size)
            val dx = x - positionCenter.x - offset.x
            val dy = y - positionCenter.y - offset.y
            dx * dx + dy * dy
        }
    }

    /**
     * Hit-test the visible token artwork, not only its board anchor. Pin
     * artwork extends well above the cell/base, so tapping the token head
     * previously resolved to an unrelated board cell instead of its move.
     */
    private fun pieceAtTouch(x: Float, y: Float): LudoPiece? {
        val hitPieces = LudoSetup.allPieces(gameState)
            .filter { it.progress < LudoSetup.FINISH }
            .mapNotNull { piece ->
                val position = LudoSetup.positionOf(piece)
                val stack = LudoSetup.piecesAt(gameState, position)
                val index = stack.indexOfFirst { candidate ->
                    candidate.player == piece.player && candidate.token == piece.token
                }
                val center = pointFor(piece, position)
                val offset = if (piece.progress < 0) {
                    PointF()
                } else {
                    stackOffset(index.coerceAtLeast(0), stack.size.coerceAtLeast(1))
                }
                val point = PointF(center.x + offset.x, center.y + offset.y)
                val distance = tokenHitDistance(piece, point, x, y)
                distance?.let { piece to it }
            }
        return hitPieces.minByOrNull { it.second }?.first
    }

    private fun tokenHitDistance(piece: LudoPiece, point: PointF, x: Float, y: Float): Float? {
        val bitmap = tokenBitmaps.getOrNull(piece.player)
        if (bitmap != null) {
            val tokenHeight = cell * 0.92f * 1.6f * 1.05f
            val tokenWidth = tokenHeight * bitmap.width.toFloat() / bitmap.height.toFloat()
            val anchorFraction = selectedBoard.tokenTipFractions[piece.player]
            val horizontalPadding = cell * 0.12f
            val verticalPadding = cell * 0.12f
            val left = point.x - tokenWidth / 2f - horizontalPadding
            val top = point.y - tokenHeight * anchorFraction - verticalPadding
            val right = point.x + tokenWidth / 2f + horizontalPadding
            val bottom = point.y + tokenHeight * (1f - anchorFraction) + verticalPadding
            if (x in left..right && y in top..bottom) {
                val dx = x - point.x
                val dy = y - point.y
                return dx * dx + dy * dy
            }
            return null
        }

        val radius = cell * 0.48f
        val dx = x - point.x
        val dy = y - point.y
        return if (dx * dx + dy * dy <= radius * radius) {
            dx * dx + dy * dy
        } else {
            null
        }
    }

    private fun sourceDistance(move: Move, x: Float, y: Float): Float {
        val piece = LudoSetup.pieceForMove(gameState, move)
            ?: (gameState.get(move.from) as? LudoPiece)
        val stack = LudoSetup.piecesAt(gameState, move.from)
        val index = piece?.let { stack.indexOfFirst { candidate ->
            candidate.player == it.player && candidate.token == it.token
        } } ?: 0
        val center = piece?.let { pointFor(it, move.from) } ?: centerOf(move.from)
        val offset = if ((piece?.progress ?: 0) < 0) {
            PointF()
        } else {
            stackOffset(index.coerceAtLeast(0), stack.size.coerceAtLeast(1))
        }
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
        animatedSoundStep = -1
        val generation = ++moveGeneration
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, animatedPath.lastIndex.toFloat()).apply {
            duration = (animatedPath.lastIndex.coerceAtLeast(1) * 145L) + 70L
            interpolator = LinearInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                val step = floor(animatedProgress).toInt()
                    .coerceAtMost(animatedPath.lastIndex - 1)
                while (animatedSoundStep < step) {
                    animatedSoundStep++
                    onMoveStep?.invoke()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != moveGeneration) return
                    moveAnimator = null
                    animatedMove = null
                    animatedPiece = null
                    animatedPath = emptyList()
                    animatedProgress = 0f
                    animatedSoundStep = -1
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

    /**
     * Circle centroids measured from the Ludo board artwork at its native
     * 1254x1254 resolution. The model positions remain unchanged for rules;
     * these artwork coordinates are only used for rendering and hit targets.
     *
     * Player order is red, blue, green, yellow. Token order is the same as
     * LudoSetup.yardPosition(): top-left, top-right, bottom-left, bottom-right.
     */
    private val yardArtworkCenters = arrayOf(
        // red
        arrayOf(
            PointF(169.6f, 922.5f),
            PointF(330.9f, 922.5f),
            PointF(169.3f, 1084.3f),
            PointF(330.7f, 1084.3f),
        ),
        // blue
        arrayOf(
            PointF(922.1f, 922.5f),
            PointF(1083.4f, 922.6f),
            PointF(921.9f, 1084.3f),
            PointF(1083.3f, 1084.4f),
        ),
        // green
        arrayOf(
            PointF(169.5f, 169.4f),
            PointF(330.9f, 169.4f),
            PointF(169.4f, 331.1f),
            PointF(330.8f, 331.1f),
        ),
        // yellow
        arrayOf(
            PointF(922.1f, 169.4f),
            PointF(1083.5f, 169.4f),
            PointF(922.1f, 331.0f),
            PointF(1083.4f, 331.0f),
        ),
    )

    private fun yardCenter(player: Int, token: Int): PointF {
        val artworkCenter = yardArtworkCenters[player][token]
        val boardPixelScale = LudoSetup.BOARD_SIZE * cell / BOARD_ARTWORK_SIZE
        return PointF(
            left + artworkCenter.x * boardPixelScale,
            top + artworkCenter.y * boardPixelScale,
        )
    }

    private fun pointFor(piece: LudoPiece, position: Position): PointF =
        if (piece.progress < 0 &&
            position == LudoSetup.yardPosition(piece.player, piece.token)
        ) {
            yardCenter(piece.player, piece.token)
        } else {
            centerOf(position)
        }

    private fun pointForPosition(position: Position): PointF {
        val yardPiece = LudoSetup.allPieces(gameState).firstOrNull {
            it.progress < 0 && LudoSetup.positionOf(it) == position
        }
        return yardPiece?.let { pointFor(it, position) } ?: centerOf(position)
    }

    private fun centerOf(position: Position): PointF =
        PointF(left + (position.col + 0.5f) * cell, top + (position.row + 0.5f) * cell)

    private fun loadBitmap(assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun loadTokenBitmaps(board: Board): Array<Bitmap?> =
        board.tokenAssetNames.map(::loadBitmap).toTypedArray()

    private fun positionAt(x: Float, y: Float): Position? {
        val yardPiece = LudoSetup.allPieces(gameState)
            .firstOrNull { piece ->
                if (piece.progress >= 0) return@firstOrNull false
                val center = yardCenter(piece.player, piece.token)
                val dx = x - center.x
                val dy = y - center.y
                dx * dx + dy * dy <= (cell * 0.62f) * (cell * 0.62f)
            }
        if (yardPiece != null) {
            return LudoSetup.yardPosition(yardPiece.player, yardPiece.token)
        }
        val col = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()
        return if (row in 0 until LudoSetup.BOARD_SIZE && col in 0 until LudoSetup.BOARD_SIZE)
            Position(row, col) else null
    }

}