package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseSetup
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePiece
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePieceType
import com.mkdev.mkboardgames.games.go.GoPiece
import com.mkdev.mkboardgames.games.go.GoRuleEngine
import com.mkdev.mkboardgames.games.othello.OthelloPiece
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import com.mkdev.mkboardgames.games.shogi.ShogiRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiSetup
import com.mkdev.mkboardgames.games.shogi.ShogiPieceType
import com.mkdev.mkboardgames.games.xiangqi.XiangqiPiece
import com.mkdev.mkboardgames.games.xiangqi.XiangqiRuleEngine
import kotlin.math.roundToInt

class BoardView(context: Context) : View(context) {

    // ─── External state ───────────────────────────────────────────────────────
    var gameState: GameState = GameState(arrayOfNulls(64))
        set(value) {
            field = value
            updateBoardGeometry()
            selectedPos = null
            legalMoves  = if (shogiDropPiece != null && ruleEngine is ShogiRuleEngine) {
                (ruleEngine as ShogiRuleEngine).legalDropsFrom(value, shogiDropPiece!!)
            } else {
                emptyList()
            }
            mustCapturePieces = if (showMustCaptureHints && ruleEngine != null)
                computeMustCapturePieces(value) else emptySet()
            if (directMoveMode && ruleEngine != null && !isLocked)
                legalMoves = ruleEngine!!.allLegalMoves(value, value.currentTurn)
            invalidate()
        }
    var ruleEngine: RuleEngine? = null
    var onMoveMade: ((Move) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null
    var isFlipped: Boolean = false
    var isLocked: Boolean = false
    var showMustCaptureHints: Boolean = false
    var rotateBlackPieces: Boolean = false
    var directMoveMode: Boolean = false
    var chessBoardStyle: ChessBoardStyle = ChessBoardStyle.CANVAS
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var onPromotionChoice: ((List<Move>) -> Unit)? = null

    // ─── Selection ───────────────────────────────────────────────────────────
    private var selectedPos: Position? = null
    private var legalMoves: List<Move> = emptyList()
    private var shogiDropPiece: ShogiPieceType? = null

    fun beginShogiDrop(type: ShogiPieceType) {
        val engine = ruleEngine as? ShogiRuleEngine ?: return
        if (isLocked || gameState.status != GameStatus.IN_PROGRESS) return
        shogiDropPiece = type
        selectedPos = null
        legalMoves = engine.legalDropsFrom(gameState, type)
        invalidate()
    }

    fun cancelShogiDrop() {
        shogiDropPiece = null
        legalMoves = emptyList()
        invalidate()
    }

    // ─── Must-capture highlights ──────────────────────────────────────────────
    private var mustCapturePieces: Set<Position> = emptySet()

    private fun computeMustCapturePieces(state: GameState): Set<Position> {
        val engine = ruleEngine ?: return emptySet()
        if (state.status != GameStatus.IN_PROGRESS) return emptySet()
        val result = mutableSetOf<Position>()
        for (row in 0 until state.boardSize) for (col in 0 until state.boardSize) {
            val pos = Position(row, col)
            if (state.get(pos)?.color != state.currentTurn) continue
            if (engine.legalMovesFrom(state, pos).any { it.isCapture }) result.add(pos)
        }
        return result
    }

    // ─── Animation ───────────────────────────────────────────────────────────
    private var animPiece: Piece? = null
    private var animFromPx  = PointF()
    private var animToPx    = PointF()
    private var animFromPos: Position? = null
    private var animToPos: Position? = null
    private var animProgress: Float = 0f
    private var animator: ValueAnimator? = null
    private var pendingMove: Move? = null

    // Othello disc pop/flip animation
    private var recentOthelloPieces: Set<Position> = emptySet()
    private var othelloPopProgress: Float = 1f
    private var popAnimator: ValueAnimator? = null

    // ─── Board geometry ───────────────────────────────────────────────────────
    private var cellSize  = 0f
    private var boardLeft = 0f
    private var boardTop  = 0f
    private var shogiImageRect = RectF()
    private var shogiGridLeft = 0f
    private var shogiGridRight = 0f
    private var shogiGridTop = 0f
    private var shogiGridBottom = 0f
    private var shogiCellWidth = 0f
    private var shogiCellHeight = 0f
    // The supplied board image is photographed with slight perspective, so
    // its nine columns and rows are not perfectly uniform. Keep the measured
    // grid lines in image-relative coordinates so pieces sit in the visual
    // centre of each box instead of drifting across the board.
    private val shogiGridX = floatArrayOf(
        35f / 1190f, 161f / 1190f, 290f / 1190f, 414f / 1190f, 540f / 1190f,
        668f / 1190f, 793f / 1190f, 917f / 1190f, 1041f / 1190f, 1162f / 1190f,
    )
    private val shogiGridY = floatArrayOf(
        35f / 1322f, 178f / 1322f, 317f / 1322f, 456f / 1322f, 596f / 1322f,
        735f / 1322f, 876f / 1322f, 1015f / 1322f, 1154f / 1322f, 1290f / 1322f,
    )
    private var xiangqiImageRect = RectF()
    private var xiangqiGridLeft = 0f
    private var xiangqiGridRight = 0f
    private var xiangqiGridTop = 0f
    private var xiangqiCellWidth = 0f
    private var xiangqiCellHeight = 0f
    private var goImageRect = RectF()
    private val goGridX = floatArrayOf(
        0.084f, 0.154f, 0.224f, 0.294f, 0.364f, 0.434f, 0.503f,
        0.572f, 0.641f, 0.711f, 0.781f, 0.851f, 0.919f,
    )
    private val goGridY = floatArrayOf(
        0.079f, 0.145f, 0.211f, 0.275f, 0.341f, 0.406f, 0.472f,
        0.537f, 0.603f, 0.667f, 0.733f, 0.798f, 0.866f,
    )
    private var chessImageRect = RectF()
    private var chessCellWidth = 0f
    private var chessCellHeight = 0f
    // The supplied chess board includes a wooden frame and a slight camera
    // perspective. These are the measured boundaries of its playable 8x8 area
    // in the 1024px asset. Keep every boundary instead of deriving cells from
    // one average size: the draw, highlight, animation, and touch paths all
    // use these same lines.
    private val suppliedChessGridX = floatArrayOf(
        42f / 1024f, 163f / 1024f, 278f / 1024f, 395f / 1024f,
        511f / 1024f, 628f / 1024f, 744f / 1024f, 860f / 1024f,
        983f / 1024f,
    )
    private val suppliedChessGridY = floatArrayOf(
        33f / 1024f, 151f / 1024f, 265f / 1024f, 381f / 1024f,
        498f / 1024f, 613f / 1024f, 729f / 1024f, 845f / 1024f,
        975f / 1024f,
    )
    // The original framed board is a 1024px image with a slightly different
    // inner border. Keeping its geometry separate prevents pieces and taps
    // from drifting when switching between the two photographs.
    private val classicChessGridX = floatArrayOf(
        48f / 1024f, 166f / 1024f, 284f / 1024f, 401f / 1024f,
        512f / 1024f, 630f / 1024f, 748f / 1024f, 864f / 1024f,
        978f / 1024f,
    )
    private val classicChessGridY = floatArrayOf(
        45f / 1024f, 158f / 1024f, 272f / 1024f, 385f / 1024f,
        500f / 1024f, 615f / 1024f, 730f / 1024f, 845f / 1024f,
        959f / 1024f,
    )
    private val realisticChessGridX = floatArrayOf(
        48f / 1024f, 164f / 1024f, 280f / 1024f, 396f / 1024f,
        512f / 1024f, 628f / 1024f, 744f / 1024f, 860f / 1024f,
        976f / 1024f,
    )
    private val realisticChessGridY = realisticChessGridX.copyOf()

    private val xiangqiBoardBitmap: Bitmap? = try {
        context.assets.open("xiangqi_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val shogiBoardBitmap: Bitmap? = try {
        context.assets.open("shogi_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val goBoardBitmap: Bitmap? = try {
        context.assets.open("go_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val classicChessBoardBitmap: Bitmap? = try {
        context.assets.open("chess_board.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val suppliedChessBoardBitmap: Bitmap? = try {
        context.assets.open("chess_board_wood.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val realisticChessBoardBitmap: Bitmap? = createRealisticChessBoardBitmap()

    private fun createRealisticChessBoardBitmap(): Bitmap {
        val size = 1024
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#171B20")
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)

        // Keep the outer edge close to the bitmap bounds so this presentation
        // has the same visible footprint as the first (canvas) board.
        val boardRect = RectF(14f, 14f, size - 14f, size - 14f)
        val boardRadius = 42f
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2F3540")
        }
        canvas.drawRoundRect(boardRect, boardRadius, boardRadius, framePaint)

        val boardInset = 48f
        val squareSize = (size - boardInset * 2f) / 8f
        val gridRect = RectF(boardInset, boardInset, size - boardInset, size - boardInset)
        val gridPath = Path().apply {
            addRoundRect(gridRect, 18f, 18f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(gridPath)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val isLight = (row + col) % 2 == 0
                val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLight) Color.parseColor("#EAE5DA") else Color.parseColor("#30363F")
                }
                val left = boardInset + col * squareSize
                val top = boardInset + row * squareSize
                canvas.drawRect(left, top, left + squareSize, top + squareSize, squarePaint)
            }
        }
        canvas.restore()

        val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val bevelRect = RectF(boardRect).apply { inset(5f, 5f) }
        canvas.drawRoundRect(bevelRect, boardRadius - 5f, boardRadius - 5f, bevelPaint)

        return bitmap
    }

    // ─── Paints ───────────────────────────────────────────────────────────────
    private var lightPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private var darkPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90,255,215,0) }
    private val highlightBlue = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val mustCapturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8F00"); style = Paint.Style.STROKE
    }
    private val shogiSelectionEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ─── Theme ───────────────────────────────────────────────────────────────
    private fun applyTheme() {
        val t = SettingsManager.currentTheme(context)
        lightPaint.color  = t.light
        darkPaint.color   = t.dark
        accentPaint.color = t.accent
        highlightBlue.color = Color.argb(160, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        shogiSelectionEdgePaint.color =
            Color.argb(225, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        dotPaint.color  = Color.argb(130, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        ringPaint.color = Color.parseColor("#EF5350")
    }

    init { applyTheme() }
    fun refreshTheme() { applyTheme(); invalidate() }

    // ─── Size ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        updateBoardGeometry()
    }

    private fun updateBoardGeometry() {
        if (width <= 0 || height <= 0) return
        if (isShogiBoard()) {
            val bitmap = shogiBoardBitmap
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                shogiImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                // The uploaded reference includes a narrow wooden frame around
                // the playable 9×9 grid. Use the measured outer lines for the
                // legacy bounds and the per-line arrays for each box.
                shogiGridLeft = shogiLineX(0)
                shogiGridRight = shogiLineX(9)
                shogiGridTop = shogiLineY(0)
                shogiGridBottom = shogiLineY(9)
                shogiCellWidth = (shogiGridRight - shogiGridLeft) / 9f
                shogiCellHeight = (shogiGridBottom - shogiGridTop) / 9f
                cellSize = minOf(shogiCellWidth, shogiCellHeight)
                piecePaint.textSize = cellSize * 0.58f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
                shogiSelectionEdgePaint.strokeWidth = cellSize * 0.035f
            }
            return
        }
        if (isXiangqiBoard()) {
            val bitmap = xiangqiBoardBitmap
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                xiangqiImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                // Coordinates of the nine-by-ten intersection grid in the
                // supplied reference image, including its printed labels.
                xiangqiGridLeft = xiangqiImageRect.left + imageWidth * 0.052f
                xiangqiGridRight = xiangqiImageRect.left + imageWidth * 0.951f
                xiangqiGridTop = xiangqiImageRect.top + imageHeight * 0.113f
                val gridBottom = xiangqiImageRect.top + imageHeight * 0.898f
                xiangqiCellWidth = (xiangqiGridRight - xiangqiGridLeft) / 8f
                xiangqiCellHeight = (gridBottom - xiangqiGridTop) / 9f
                cellSize = minOf(xiangqiCellWidth, xiangqiCellHeight)
                piecePaint.textSize = cellSize * 0.72f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isGoBoard()) {
            val bitmap = goBoardBitmap
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                goImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                val gridSize = imageWidth * 0.832f
                cellSize = gridSize / 12f
                piecePaint.textSize = cellSize * 0.60f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isChessImageBoard()) {
            val bitmap = chessBitmap()
            if (bitmap != null) {
                // The first canvas board leaves a 4dp edge on the smaller
                // dimension. Give the realistic board the same outer bounds;
                // the other photo boards retain their original full-bleed fit.
                val canvasBoardMargin = if (chessBoardStyle == ChessBoardStyle.REALISTIC_BLACK_WHITE) {
                    4f * resources.displayMetrics.density
                } else {
                    0f
                }
                val availableWidth = (width.toFloat() - canvasBoardMargin * 2f).coerceAtLeast(0f)
                val availableHeight = (height.toFloat() - canvasBoardMargin * 2f).coerceAtLeast(0f)
                val scale = minOf(
                    availableWidth / bitmap.width,
                    availableHeight / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                chessImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                chessCellWidth = (chessLineX(8) - chessLineX(0)) / 8f
                chessCellHeight = (chessLineY(8) - chessLineY(0)) / 8f
                cellSize = minOf(chessCellWidth, chessCellHeight)
                piecePaint.textSize = cellSize * 0.60f
                labelPaint.textSize = cellSize * 0.18f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        val dp      = resources.displayMetrics.density
        val margin  = 4f * dp
        val boardSz = minOf(width.toFloat() - margin * 2, height.toFloat() - margin * 2)
        cellSize  = boardSz / gameState.boardSize.toFloat()
        boardLeft = (width - boardSz) / 2f
        boardTop  = (height - boardSz) / 2f
        if (isChessBoard()) {
            chessCellWidth = cellSize
            chessCellHeight = cellSize
        }
        piecePaint.textSize       = cellSize * 0.60f
        labelPaint.textSize       = cellSize * 0.22f
        labelPaint.color          = Color.argb(130, 120, 80, 40)
        mustCapturePaint.strokeWidth = cellSize * 0.055f
    }

    private fun refreshBoardStyleGeometry() {
        updateBoardGeometry()
        // Keep an in-flight move aligned when the board style changes.
        animFromPos?.let { animFromPx = cellCenter(it) }
        animToPos?.let { animToPx = cellCenter(it) }
    }

    // ─── Touch ───────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Game over: re-show result dialog on any tap
            if (gameState.status != GameStatus.IN_PROGRESS) {
                onGameOverTapped?.invoke()
                return true
            }
            if (!isLocked && animPiece == null)
                handleTap(screenToBoard(event.x, event.y) ?: return true)
        }
        return true
    }

    private fun handleTap(pos: Position) {
        if (isGoBoard()) {
            val engine = ruleEngine ?: return
            val move = engine.allLegalMoves(gameState, gameState.currentTurn)
                .firstOrNull { it.to == pos && it.metadata["pass"] != true }
            if (move != null) startMoveAnimation(move)
            return
        }
        if (directMoveMode) {
            val engine = ruleEngine ?: return
            if (legalMoves.isEmpty())
                legalMoves = engine.allLegalMoves(gameState, gameState.currentTurn)
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) startMoveAnimation(move)
            return
        }

        val engine = ruleEngine ?: return

        if (shogiDropPiece != null) {
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) {
                startMoveAnimation(move)
            } else {
                cancelShogiDrop()
            }
            return
        }

        if (selectedPos != null) {
            val choices = legalMoves.filter { it.to == pos }
            if (choices.size == 1) { startMoveAnimation(choices.first()); return }
            if (choices.size > 1) {
                selectedPos = null
                legalMoves = emptyList()
                onPromotionChoice?.invoke(choices)
                invalidate()
                return
            }

            val piece = gameState.get(pos)
            if (piece != null && piece.color == gameState.currentTurn) {
                val moves = engine.legalMovesFrom(gameState, pos)
                if (moves.isNotEmpty()) { selectedPos = pos; legalMoves = moves; invalidate(); return }
            }
            selectedPos = null; legalMoves = emptyList(); invalidate(); return
        }

        val piece = gameState.get(pos) ?: run { invalidate(); return }
        if (piece.color != gameState.currentTurn) { invalidate(); return }
        val moves = engine.legalMovesFrom(gameState, pos)
        if (moves.isNotEmpty()) { selectedPos = pos; legalMoves = moves }
        invalidate()
    }

    // ─── Animation ───────────────────────────────────────────────────────────

    fun animateExternalMove(move: Move) = startMoveAnimation(move)

    /**
     * Cancel an in-flight move animation without applying its callback.
     *
     * Replay controls can jump to another state while the previous animation
     * is still running. Clearing the callback before cancelling prevents the
     * old move from overwriting the newly selected replay state.
     */
    fun cancelMoveAnimation() {
        val activeAnimator = animator
        animator = null
        pendingMove = null
        activeAnimator?.cancel()
        animPiece = null
        animFromPos = null
        animToPos = null
        animProgress = 0f
        isLocked = false
        invalidate()
    }

    /** Pop-in animation for Othello: placed disc + all flipped discs grow in with overshoot. */
    fun playOthelloPopAnim(positions: Set<Position>) {
        recentOthelloPieces = positions
        othelloPopProgress  = 0f
        popAnimator?.cancel()
        popAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L; interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { othelloPopProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    recentOthelloPieces = emptySet()
                    othelloPopProgress  = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun startMoveAnimation(move: Move) {
        cancelMoveAnimation()
        if (isGoBoard()) {
            selectedPos = null
            legalMoves = emptyList()
            isLocked = true
            postDelayed({
                isLocked = false
                invalidate()
                onMoveMade?.invoke(move)
            }, 140L)
            return
        }
        if (move.metadata["drop"] != null) {
            selectedPos = null
            legalMoves = emptyList()
            shogiDropPiece = null
            isLocked = true
            // Drops originate in the hand rather than from a board square, so
            // use a short placement pause instead of a from-to animation.
            postDelayed({
                isLocked = false
                invalidate()
                onMoveMade?.invoke(move)
            }, 160L)
            return
        }
        val piece = gameState.get(move.from) ?: run { isLocked = false; onMoveMade?.invoke(move); return }

        selectedPos = null; legalMoves = emptyList()
        animPiece   = piece; animFromPos = move.from; animToPos = move.to
        animFromPx  = cellCenter(move.from); animToPx = cellCenter(move.to)
        pendingMove = move; isLocked = true

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animation !== animator) return
                    animator = null
                    animPiece = null; animFromPos = null; animToPos = null; animProgress = 0f
                    isLocked  = false; invalidate()
                    val completedMove = pendingMove
                    pendingMove = null
                    completedMove?.let { onMoveMade?.invoke(it) }
                }
            })
            start()
        }
    }

    private fun cellCenter(pos: Position): PointF {
        if (isShogiBoard()) return shogiPoint(pos)
        if (isXiangqiBoard()) return xiangqiPoint(pos)
        if (isGoBoard()) return goPoint(pos)
        if (isChessBoard()) return chessPoint(pos)
        val last = gameState.boardSize - 1
        val dr = if (isFlipped) last - pos.row else pos.row
        val dc = if (isFlipped) last - pos.col else pos.col
        return PointF(boardLeft + dc * cellSize + cellSize / 2f,
                      boardTop  + dr * cellSize + cellSize / 2f)
    }

    // ─── Drawing ─────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (isChessBoard()) {
            if (isChessImageBoard()) drawChessBoard(canvas) else drawBoard(canvas)
            drawChessHighlights(canvas)
            drawChessLabels(canvas)
            drawChessPieces(canvas)
            return
        }
        if (isShogiBoard()) {
            drawShogiBoard(canvas)
            drawShogiHighlights(canvas)
            drawShogiPieces(canvas)
            return
        }
        if (isXiangqiBoard()) {
            drawXiangqiBoard(canvas)
            drawXiangqiHighlights(canvas)
            drawXiangqiPieces(canvas)
            return
        }
        if (isGoBoard()) {
            canvas.drawColor(Color.rgb(20, 20, 20))
            goBoardBitmap?.let {
                canvas.drawBitmap(it, null, goImageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            drawGoHighlights(canvas)
            drawGoPieces(canvas)
            return
        }
        drawBoard(canvas); drawLabels(canvas); drawHighlights(canvas); drawPieces(canvas)
    }

    private fun drawChessBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        chessBitmap()?.let {
            canvas.drawBitmap(
                it,
                null,
                chessImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawChessLabels(canvas: Canvas) {
        val inset = cellSize * 0.105f
        labelPaint.textSize = (cellSize * 0.17f).coerceIn(10f, 24f)
        labelPaint.style = Paint.Style.FILL

        for (displayedRow in 0 until 8) {
            for (displayedCol in 0 until 8) {
                val left = chessLineX(displayedCol)
                val top = chessLineY(displayedRow)
                val right = chessLineX(displayedCol + 1)
                val bottom = chessLineY(displayedRow + 1)
                val file = ('a' + if (isFlipped) 7 - displayedCol else displayedCol).toString()
                val rank = (if (isFlipped) displayedRow + 1 else 8 - displayedRow).toString()

                // Keep labels in opposite corners from the piece centre and
                // change their colour per square for legibility on both photos.
                val lightSquare = (displayedRow + displayedCol) % 2 == 0
                labelPaint.color = if (lightSquare) {
                    Color.argb(205, 45, 28, 18)
                } else {
                    Color.argb(220, 255, 244, 220)
                }
                labelPaint.setShadowLayer(
                    cellSize * 0.018f,
                    0f,
                    cellSize * 0.012f,
                    Color.argb(145, 0, 0, 0),
                )
                val metrics = labelPaint.fontMetrics
                if (displayedRow == 7) {
                    canvas.drawText(
                        file,
                        right - inset,
                        bottom - inset - metrics.descent,
                        labelPaint,
                    )
                }
                if (displayedCol == 0) {
                    canvas.drawText(
                        rank,
                        left + inset,
                        top + inset - metrics.ascent,
                        labelPaint,
                    )
                }
            }
        }
        labelPaint.clearShadowLayer()
    }

    private fun drawChessHighlights(canvas: Canvas) {
        gameState.lastMove?.let {
            drawChessCell(canvas, it.from, highlightGold)
            drawChessCell(canvas, it.to, highlightGold)
        }
        selectedPos?.let { drawChessCell(canvas, it, highlightBlue) }

        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = chessPoint(move.to)
                if (!directMoveMode && move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.44f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
                }
            }
        }
    }

    private fun drawChessCell(canvas: Canvas, position: Position, paint: Paint) {
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        canvas.drawRect(
            chessLineX(displayedCol),
            chessLineY(displayedRow),
            chessLineX(displayedCol + 1),
            chessLineY(displayedRow + 1),
            paint,
        )
    }

    private fun drawChessPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = chessPoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        xiangqiBoardBitmap?.let { canvas.drawBitmap(it, null, xiangqiImageRect, Paint(Paint.ANTI_ALIAS_FLAG)) }
    }

    private fun drawShogiBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        shogiBoardBitmap?.let {
            canvas.drawBitmap(it, null, shogiImageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun drawShogiHighlights(canvas: Canvas) {
        val showHints = SettingsManager.getShowHints(context)
        gameState.lastMove?.let {
            drawShogiCell(canvas, it.from, highlightGold)
            drawShogiCell(canvas, it.to, highlightGold)
        }
        selectedPos?.let { drawShogiPieceHighlight(canvas, it) }
        if (showHints) {
            legalMoves.forEach { move ->
                val point = shogiPoint(move.to)
                if (move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.40f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.13f, dotPaint)
                }
            }
        }
    }

    private fun drawShogiCell(canvas: Canvas, position: Position, paint: Paint) {
        val col = if (isFlipped) 8 - position.col else position.col
        val row = if (isFlipped) 8 - position.row else position.row
        canvas.drawRect(
            shogiLineX(col),
            shogiLineY(row),
            shogiLineX(col + 1),
            shogiLineY(row + 1),
            paint,
        )
    }

    private fun drawShogiPieceHighlight(canvas: Canvas, position: Position) {
        val piece = gameState.get(position) ?: return
        val point = shogiPoint(position)
        val halfW = shogiCellWidth * 0.395f
        val halfH = shogiCellHeight * 0.415f
        val path = shogiPiecePath(point.x, point.y, halfW, halfH)

        canvas.save()
        if (piece.color == PieceColor.BLACK) canvas.rotate(180f, point.x, point.y)
        canvas.drawPath(path, highlightBlue)
        canvas.drawPath(path, shogiSelectionEdgePaint)
        canvas.restore()
    }

    private fun drawShogiPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = shogiPoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiHighlights(canvas: Canvas) {
        val radius = cellSize * 0.46f
        gameState.lastMove?.let {
            canvas.drawCircle(xiangqiPoint(it.from).x, xiangqiPoint(it.from).y, radius, highlightGold)
            canvas.drawCircle(xiangqiPoint(it.to).x, xiangqiPoint(it.to).y, radius, highlightGold)
        }
        selectedPos?.let {
            canvas.drawCircle(xiangqiPoint(it).x, xiangqiPoint(it).y, radius, highlightBlue)
        }
        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = xiangqiPoint(move.to)
                if (move.isCapture) canvas.drawCircle(point.x, point.y, cellSize * 0.42f, ringPaint)
                else canvas.drawCircle(point.x, point.y, cellSize * 0.15f, dotPaint)
            }
        }
    }

    private fun drawXiangqiPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until 10) for (col in 0 until 9) {
            val position = Position(row, col)
            if (position == skipPos) continue
            val piece = gameState.get(position) ?: continue
            val point = xiangqiPoint(position)
            drawXiangqiPiece(canvas, piece, point.x, point.y)
        }
        animPiece?.let {
            drawXiangqiPiece(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiPiece(canvas: Canvas, piece: Piece, cx: Float, cy: Float) {
        val radius = cellSize * 0.43f
        canvas.drawCircle(cx + radius * 0.08f, cy + radius * 0.11f, radius, shadowPaint)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7E8C8") }
        canvas.drawCircle(cx, cy, radius, fill)
        val edge = if (piece.color == PieceColor.WHITE) Color.parseColor("#C62828")
        else Color.parseColor("#171717")
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = radius * 0.09f
            it.color = edge
            canvas.drawCircle(cx, cy, radius * 0.91f, it)
            it.strokeWidth = radius * 0.035f
            canvas.drawCircle(cx, cy, radius * 0.79f, it)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.02f
            isFakeBoldText = true
            color = edge
        }
        val metrics = text.fontMetrics
        canvas.drawText(piece.symbol(), cx, cy - (metrics.ascent + metrics.descent) / 2f, text)
    }

    private fun drawBoard(canvas: Canvas) {
        if (isFoxAndGeeseBoard()) {
            drawFoxAndGeeseBoard(canvas)
            return
        }
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            val l = boardLeft + col * cellSize; val t = boardTop + row * cellSize
            canvas.drawRect(l, t, l + cellSize, t + cellSize,
                if ((row + col) % 2 == 0) lightPaint else darkPaint)
        }
    }

    private fun drawLabels(canvas: Canvas) {
        if (isFoxAndGeeseBoard()) return
        val size = gameState.boardSize
        for (i in 0 until size) {
            val file = ('a' + if (isFlipped) size - 1 - i else i).toString()
            val rank = ((if (isFlipped) i + 1 else size - i)).toString()
            canvas.drawText(file, boardLeft + i * cellSize + cellSize * 0.86f,
                boardTop + size * cellSize - cellSize * 0.06f, labelPaint)
            canvas.drawText(rank, boardLeft + cellSize * 0.10f,
                boardTop + i * cellSize + cellSize * 0.28f, labelPaint)
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        gameState.lastMove?.let { m ->
            highlightCell(canvas, m.from, highlightGold)
            highlightCell(canvas, m.to,   highlightGold)
        }
        selectedPos?.let { highlightCell(canvas, it, highlightBlue) }
        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            for (move in legalMoves) {
                val cx = boardLeft + boardCol(move.to.col) * cellSize + cellSize / 2f
                val cy = boardTop  + boardRow(move.to.row) * cellSize + cellSize / 2f
                if (!directMoveMode && move.isCapture)
                    canvas.drawCircle(cx, cy, cellSize * 0.44f, ringPaint)
                else
                    canvas.drawCircle(cx, cy, cellSize * 0.17f, dotPaint)
            }
        }
    }

    private fun highlightCell(canvas: Canvas, pos: Position, paint: Paint) {
        val c = boardCol(pos.col); val r = boardRow(pos.row)
        if (isFoxAndGeeseBoard()) {
            canvas.drawCircle(
                boardLeft + c * cellSize + cellSize / 2f,
                boardTop + r * cellSize + cellSize / 2f,
                cellSize * 0.38f,
                paint
            )
        } else {
            canvas.drawRect(boardLeft + c * cellSize, boardTop + r * cellSize,
                boardLeft + (c+1) * cellSize, boardTop + (r+1) * cellSize, paint)
        }
    }

    /**
     * Draw the traditional 33-point cross board rather than a checkerboard.
     * Every playable point is connected to its valid neighbouring points,
     * matching the movement graph used by FoxAndGeeseRuleEngine.
     */
    private fun drawFoxAndGeeseBoard(canvas: Canvas) {
        val size = FoxAndGeeseSetup.BOARD_SIZE
        val left = boardLeft
        val top = boardTop
        val right = left + size * cellSize
        val bottom = top + size * cellSize
        val theme = SettingsManager.currentTheme(context)

        fun mix(first: Int, second: Int, secondWeight: Float): Int {
            val weight = secondWeight.coerceIn(0f, 1f)
            val inverse = 1f - weight
            return Color.rgb(
                (Color.red(first) * inverse + Color.red(second) * weight).toInt(),
                (Color.green(first) * inverse + Color.green(second) * weight).toInt(),
                (Color.blue(first) * inverse + Color.blue(second) * weight).toInt()
            )
        }

        val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.light
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.035f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.dark, Color.BLACK, 0.18f)
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.045f
            strokeCap = Paint.Cap.ROUND
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.light, Color.WHITE, 0.55f)
            style = Paint.Style.FILL
        }
        val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.dark, theme.accent, 0.35f)
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.025f
        }

        val cross = Path().apply {
            moveTo(left + 2f * cellSize, top)
            lineTo(left + 5f * cellSize, top)
            lineTo(left + 5f * cellSize, top + 2f * cellSize)
            lineTo(right, top + 2f * cellSize)
            lineTo(right, top + 5f * cellSize)
            lineTo(left + 5f * cellSize, top + 5f * cellSize)
            lineTo(left + 5f * cellSize, bottom)
            lineTo(left + 2f * cellSize, bottom)
            lineTo(left + 2f * cellSize, top + 5f * cellSize)
            lineTo(left, top + 5f * cellSize)
            lineTo(left, top + 2f * cellSize)
            lineTo(left + 2f * cellSize, top + 2f * cellSize)
            close()
        }
        canvas.drawPath(cross, boardPaint)
        canvas.drawPath(cross, borderPaint)

        val dirs = listOf(
            Position(-1, -1), Position(-1, 0), Position(-1, 1),
            Position(0, -1),                  Position(0, 1),
            Position(1, -1),  Position(1, 0),  Position(1, 1)
        )
        for (row in 0 until size) for (col in 0 until size) {
            val from = Position(row, col)
            if (!FoxAndGeeseSetup.isPlayable(from)) continue
            for (dir in dirs) {
                val to = from + dir
                if (!FoxAndGeeseSetup.isConnected(from, to)) continue
                if (to.row < row || (to.row == row && to.col <= col)) continue
                canvas.drawLine(
                    boardLeft + col * cellSize + cellSize / 2f,
                    boardTop + row * cellSize + cellSize / 2f,
                    boardLeft + to.col * cellSize + cellSize / 2f,
                    boardTop + to.row * cellSize + cellSize / 2f,
                    linePaint
                )
            }
        }

        for (row in 0 until size) for (col in 0 until size) {
            if (!FoxAndGeeseSetup.isPlayable(Position(row, col))) continue
            val cx = boardLeft + col * cellSize + cellSize / 2f
            val cy = boardTop + row * cellSize + cellSize / 2f
            canvas.drawCircle(cx, cy, cellSize * 0.105f, pointPaint)
            canvas.drawCircle(cx, cy, cellSize * 0.105f, pointBorderPaint)
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            if (skipPos != null && skipPos.row == row && skipPos.col == col) continue
            val piece = gameState.get(row, col) ?: continue
            val cx = boardLeft + boardCol(col) * cellSize + cellSize / 2f
            val cy = boardTop  + boardRow(row) * cellSize + cellSize / 2f
            if (showMustCaptureHints && Position(row, col) in mustCapturePieces)
                canvas.drawCircle(cx, cy, cellSize * 0.42f, mustCapturePaint)
            drawPieceAt(canvas, piece, cx, cy, Position(row, col))
        }
        val ap = animPiece
        if (ap != null) {
            val cx = lerp(animFromPx.x, animToPx.x, animProgress)
            val cy = lerp(animFromPx.y, animToPx.y, animProgress)
            drawPieceAt(canvas, ap, cx, cy)
        }
    }

    private fun drawGoPieces(canvas: Canvas) {
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            val piece = gameState.get(row, col) as? GoPiece ?: continue
            val point = goPoint(Position(row, col))
            drawGoPiece(canvas, piece, point.x, point.y)
        }
    }

    private fun drawGoHighlights(canvas: Canvas) {
        val lastMove = gameState.lastMove
        if (lastMove != null && lastMove.metadata["pass"] != true) {
            val point = goPoint(lastMove.to)
            canvas.drawCircle(point.x, point.y, cellSize * 0.16f, highlightGold)
        }
    }

    private fun drawGoPiece(canvas: Canvas, piece: GoPiece, cx: Float, cy: Float) {
        val radius = cellSize * 0.43f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 0, 0, 0)
            maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + radius * 0.10f, cy + radius * 0.15f, radius, shadow)

        val colors = if (piece.color == PieceColor.BLACK) {
            intArrayOf(Color.rgb(92, 92, 92), Color.rgb(28, 28, 28), Color.rgb(3, 3, 3))
        } else {
            intArrayOf(Color.WHITE, Color.rgb(235, 235, 235), Color.rgb(164, 164, 164))
        }
        val stone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.33f,
                cy - radius * 0.38f,
                radius * 1.35f,
                colors,
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, stone)

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, radius * 0.045f)
            color = if (piece.color == PieceColor.BLACK)
                Color.argb(190, 0, 0, 0)
            else
                Color.argb(150, 118, 118, 118)
        }
        canvas.drawCircle(cx, cy, radius, edge)

        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.color == PieceColor.BLACK)
                Color.argb(110, 255, 255, 255)
            else
                Color.argb(145, 255, 255, 255)
        }
        canvas.drawOval(
            RectF(
                cx - radius * 0.52f,
                cy - radius * 0.62f,
                cx - radius * 0.03f,
                cy - radius * 0.34f,
            ),
            glint,
        )
    }

    private fun drawPieceAt(canvas: Canvas, piece: Piece, cx: Float, cy: Float, pos: Position? = null) {
        when (piece) {
            is ChessPiece    -> drawChessPiece(canvas, piece, cx, cy)
            is CheckersPiece -> drawCheckersPiece(canvas, piece, cx, cy)
            is FoxAndGeesePiece -> drawFoxAndGeesePiece(canvas, piece, cx, cy)
            is OthelloPiece  -> drawOthelloPiece(canvas, piece, cx, cy, pos)
            is ShogiPiece    -> drawShogiPiece(canvas, piece, cx, cy)
            is XiangqiPiece  -> drawXiangqiPiece(canvas, piece, cx, cy)
        }
    }

    private fun drawChessPiece(canvas: Canvas, piece: ChessPiece, cx: Float, cy: Float) {
        val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
        val symbol = piece.symbol()
        piecePaint.style = Paint.Style.FILL
        piecePaint.strokeWidth = 0f
        val glyphBounds = Rect()
        piecePaint.getTextBounds(symbol, 0, symbol.length, glyphBounds)
        val glyphDimension = maxOf(glyphBounds.width(), glyphBounds.height()).toFloat()
        val targetDimension = cellSize * 0.60f
        val glyphScale = if (glyphDimension > 0f) targetDimension / glyphDimension else 1f
        val glyphBaseline = cy - (glyphBounds.top + glyphBounds.bottom) / 2f

        // Unicode chess glyphs have different native bounds. Fit every glyph
        // to the same square footprint so no piece looks taller or wider than
        // the others, while keeping its silhouette proportions intact.
        canvas.save()
        if (shouldRotate) { canvas.save(); canvas.rotate(180f, cx, cy) }
        canvas.scale(glyphScale, glyphScale, cx, cy)
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = cellSize * 0.025f / glyphScale
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#757575")
                           else Color.parseColor("#EEEEEE")
        canvas.drawText(symbol, cx, glyphBaseline, piecePaint)
        piecePaint.style = Paint.Style.FILL
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDE7")
                           else Color.parseColor("#212121")
        canvas.drawText(symbol, cx, glyphBaseline, piecePaint)
        canvas.restore()
        if (shouldRotate) canvas.restore()
    }

    private fun drawCheckersPiece(canvas: Canvas, piece: CheckersPiece, cx: Float, cy: Float) {
        val r = cellSize * 0.38f
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        val isWhite = piece.color == PieceColor.WHITE
        val base = if (isWhite) Color.parseColor("#F2F2F2") else Color.parseColor("#432B3A")
        val edge = if (isWhite) Color.parseColor("#858585") else Color.parseColor("#1C1420")
        val highlight = if (isWhite) Color.WHITE else Color.parseColor("#765064")
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.32f, cy - r * 0.38f, r * 1.25f,
                intArrayOf(highlight, base, edge),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, face)
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.10f
            it.color = edge
            canvas.drawCircle(cx, cy, r * 0.92f, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.06f
            it.color = if (isWhite) Color.parseColor("#C7C7C7") else Color.parseColor("#765064")
            canvas.drawCircle(cx, cy, r * 0.76f, it)
            canvas.drawCircle(cx, cy, r * 0.61f, it)
        }
        if (piece.isKing) {
            val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = r * 1.0f
                color = if (isWhite) Color.parseColor("#555555") else Color.parseColor("#E7B45C")
                isFakeBoldText = true
            }
            val metrics = kp.fontMetrics
            canvas.drawText(
                CheckersPiece.KING_SYMBOL,
                cx,
                cy - (metrics.ascent + metrics.descent) / 2f + r * 0.04f,
                kp,
            )
        }
    }

    private fun drawFoxAndGeesePiece(
        canvas: Canvas,
        piece: FoxAndGeesePiece,
        cx: Float,
        cy: Float
    ) {
        val radius = cellSize * 0.36f
        val fox = piece.type == FoxAndGeesePieceType.FOX
        val base = if (fox) Color.parseColor("#35B7A1") else Color.parseColor("#F2F2F2")
        val highlight = if (fox) Color.parseColor("#A8F1D7") else Color.WHITE
        val edge = if (fox) Color.parseColor("#126E69") else Color.parseColor("#858585")
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * .30f, cy - radius * .38f, radius * 1.25f,
                intArrayOf(highlight, base, edge),
                floatArrayOf(0f, .58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, face)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = edge
            style = Paint.Style.STROKE
            strokeWidth = radius * .09f
        }
        canvas.drawCircle(cx, cy, radius * .91f, ring)
        ring.color = if (fox) Color.parseColor("#D7FFF0") else Color.parseColor("#C7C7C7")
        ring.strokeWidth = radius * .035f
        canvas.drawCircle(cx, cy, radius * .72f, ring)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (fox) Color.parseColor("#083F43") else Color.parseColor("#333333")
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.02f
            isFakeBoldText = true
        }
        val metrics = label.fontMetrics
        canvas.drawText(if (fox) "F" else "G", cx, cy - (metrics.ascent + metrics.descent) / 2f, label)
    }

    private fun drawOthelloPiece(canvas: Canvas, piece: OthelloPiece, cx: Float, cy: Float, pos: Position? = null) {
        val r       = cellSize * 0.40f
        val isWhite = piece.color == PieceColor.WHITE
        val popScale = if (pos != null && pos in recentOthelloPieces)
            othelloPopProgress.coerceAtLeast(0f) else 1f
        canvas.save()
        if (popScale != 1f) canvas.scale(popScale, popScale, cx, cy)
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.30f, cy - r * 0.36f, r * 1.25f,
                if (isWhite) {
                    intArrayOf(Color.WHITE, Color.parseColor("#E6E0D0"), Color.parseColor("#A79F90"))
                } else {
                    intArrayOf(Color.parseColor("#56636A"), Color.parseColor("#20292E"), Color.BLACK)
                },
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, disc)
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = r * 0.045f
            it.color = if (isWhite) Color.parseColor("#938A7B") else Color.parseColor("#0B0F11")
            canvas.drawCircle(cx, cy, r * 0.96f, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = if (isWhite) Color.argb(145, 255, 255, 255) else Color.argb(90, 255, 255, 255)
            canvas.drawOval(
                RectF(cx - r * 0.56f, cy - r * 0.68f, cx - r * 0.08f, cy - r * 0.40f),
                it,
            )
        }
        canvas.restore()
    }

    private fun drawShogiPiece(canvas: Canvas, piece: ShogiPiece, cx: Float, cy: Float) {
        val halfW = shogiCellWidth * 0.34f
        val halfH = shogiCellHeight * 0.36f
        val path = shogiPiecePath(cx, cy, halfW, halfH)
        canvas.save()
        if (piece.color == PieceColor.BLACK) canvas.rotate(180f, cx, cy)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(cellSize * 0.035f, cellSize * 0.055f)
        canvas.drawPath(path, shadow)
        canvas.restore()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDF2")
            else Color.parseColor("#E7E0D1")
        }
        canvas.drawPath(path, fill)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.promoted) Color.parseColor("#B23A32")
            else Color.parseColor("#2A211B")
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.035f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, edge)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.promoted) Color.parseColor("#B23A32")
            else Color.parseColor("#221B17")
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.43f
            isFakeBoldText = true
        }
        val metrics = text.fontMetrics
        canvas.drawText(piece.symbol(), cx, cy - (metrics.ascent + metrics.descent) / 2f + cellSize * 0.025f, text)
        canvas.restore()
    }

    private fun shogiPiecePath(cx: Float, cy: Float, halfW: Float, halfH: Float) =
        Path().apply {
            moveTo(cx, cy - halfH)
            lineTo(cx + halfW * 0.76f, cy - halfH * 0.60f)
            lineTo(cx + halfW, cy + halfH)
            lineTo(cx - halfW, cy + halfH)
            lineTo(cx - halfW * 0.76f, cy - halfH * 0.60f)
            close()
        }

    // ─── Coordinate helpers ───────────────────────────────────────────────────

    private fun boardRow(logicRow: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicRow else logicRow
    private fun boardCol(logicCol: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicCol else logicCol

    private fun isFoxAndGeeseBoard(): Boolean =
        gameState.boardSize == FoxAndGeeseSetup.BOARD_SIZE &&
            gameState.board.any { it is FoxAndGeesePiece }

    private fun isXiangqiBoard(): Boolean =
        ruleEngine is XiangqiRuleEngine || gameState.board.any { it is XiangqiPiece }

    private fun isShogiBoard(): Boolean =
        ruleEngine is ShogiRuleEngine || gameState.board.any { it is ShogiPiece }

    private fun isGoBoard(): Boolean = ruleEngine is GoRuleEngine

    private fun isChessBoard(): Boolean =
        ruleEngine is ChessRuleEngine || gameState.board.any { it is ChessPiece }

    private fun chessBitmap(): Bitmap? = when (chessBoardStyle) {
        ChessBoardStyle.CANVAS -> null
        ChessBoardStyle.CLASSIC_WOOD -> classicChessBoardBitmap
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessBoardBitmap
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessBoardBitmap
    }

    private fun isChessImageBoard(): Boolean =
        isChessBoard() && chessBitmap() != null

    private fun chessGridX(): FloatArray = when (chessBoardStyle) {
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessGridX
        ChessBoardStyle.CLASSIC_WOOD -> classicChessGridX
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridX
        ChessBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun chessGridY(): FloatArray = when (chessBoardStyle) {
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessGridY
        ChessBoardStyle.CLASSIC_WOOD -> classicChessGridY
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridY
        ChessBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun goPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 12 - position.row else position.row
        val displayedCol = if (isFlipped) 12 - position.col else position.col
        return PointF(
            goImageRect.left + goImageRect.width() * goGridX[displayedCol],
            goImageRect.top + goImageRect.height() * goGridY[displayedRow],
        )
    }

    private fun chessPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        return PointF(
            (chessLineX(displayedCol) + chessLineX(displayedCol + 1)) / 2f,
            (chessLineY(displayedRow) + chessLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun chessLineX(index: Int): Float =
        if (isChessImageBoard()) {
            chessImageRect.left + chessImageRect.width() * chessGridX()[index]
        } else {
            boardLeft + index * cellSize
        }

    private fun chessLineY(index: Int): Float =
        if (isChessImageBoard()) {
            chessImageRect.top + chessImageRect.height() * chessGridY()[index]
        } else {
            boardTop + index * cellSize
        }

    private fun shogiPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 8 - position.row else position.row
        val displayedCol = if (isFlipped) 8 - position.col else position.col
        return PointF(
            (shogiLineX(displayedCol) + shogiLineX(displayedCol + 1)) / 2f,
            (shogiLineY(displayedRow) + shogiLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun shogiLineX(index: Int): Float =
        shogiImageRect.left + shogiImageRect.width() * shogiGridX[index]

    private fun shogiLineY(index: Int): Float =
        shogiImageRect.top + shogiImageRect.height() * shogiGridY[index]

    private fun xiangqiPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 9 - position.row else position.row
        val displayedCol = if (isFlipped) 8 - position.col else position.col
        return PointF(
            xiangqiGridLeft + displayedCol * xiangqiCellWidth,
            xiangqiGridTop + displayedRow * xiangqiCellHeight,
        )
    }

    private fun screenToBoard(x: Float, y: Float): Position? {
        if (isChessBoard()) {
            if (chessCellWidth <= 0f || chessCellHeight <= 0f) return null
            val displayedCol = (0 until 8).firstOrNull {
                x >= chessLineX(it) && x < chessLineX(it + 1)
            } ?: return null
            val displayedRow = (0 until 8).firstOrNull {
                y >= chessLineY(it) && y < chessLineY(it + 1)
            } ?: return null
            return Position(
                if (isFlipped) 7 - displayedRow else displayedRow,
                if (isFlipped) 7 - displayedCol else displayedCol,
            )
        }
        if (isShogiBoard()) {
            if (shogiCellWidth <= 0f || shogiCellHeight <= 0f) return null
            val displayedCol = (0 until 9).firstOrNull { x >= shogiLineX(it) && x < shogiLineX(it + 1) }
                ?: return null
            val displayedRow = (0 until 9).firstOrNull { y >= shogiLineY(it) && y < shogiLineY(it + 1) }
                ?: return null
            return Position(
                if (isFlipped) 8 - displayedRow else displayedRow,
                if (isFlipped) 8 - displayedCol else displayedCol,
            )
        }
        if (isXiangqiBoard()) {
            if (xiangqiCellWidth <= 0f || xiangqiCellHeight <= 0f) return null
            val displayedCol = ((x - xiangqiGridLeft) / xiangqiCellWidth).roundToInt()
            val displayedRow = ((y - xiangqiGridTop) / xiangqiCellHeight).roundToInt()
            if (displayedCol !in 0..8 || displayedRow !in 0..9) return null
            val nearestX = xiangqiGridLeft + displayedCol * xiangqiCellWidth
            val nearestY = xiangqiGridTop + displayedRow * xiangqiCellHeight
            if (kotlin.math.abs(x - nearestX) > xiangqiCellWidth * 0.48f ||
                kotlin.math.abs(y - nearestY) > xiangqiCellHeight * 0.48f
            ) return null
            return Position(
                if (isFlipped) 9 - displayedRow else displayedRow,
                if (isFlipped) 8 - displayedCol else displayedCol,
            )
        }
        if (isGoBoard()) {
            if (goImageRect.width() <= 0f || goImageRect.height() <= 0f) return null
            val displayedCol = goGridX.indices.minByOrNull { index ->
                kotlin.math.abs(x - (goImageRect.left + goImageRect.width() * goGridX[index]))
            } ?: return null
            val displayedRow = goGridY.indices.minByOrNull { index ->
                kotlin.math.abs(y - (goImageRect.top + goImageRect.height() * goGridY[index]))
            } ?: return null
            val nearest = goPoint(
                Position(
                    if (isFlipped) 12 - displayedRow else displayedRow,
                    if (isFlipped) 12 - displayedCol else displayedCol,
                ),
            )
            if (kotlin.math.abs(x - nearest.x) > cellSize * 0.52f ||
                kotlin.math.abs(y - nearest.y) > cellSize * 0.52f
            ) return null
            return Position(
                if (isFlipped) 12 - displayedRow else displayedRow,
                if (isFlipped) 12 - displayedCol else displayedCol,
            )
        }
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        val size = gameState.boardSize
        if (col !in 0 until size || row !in 0 until size) return null
        val logicRow = if (isFlipped) size - 1 - row else row
        val logicCol = if (isFlipped) size - 1 - col else col
        return Position(logicRow, logicCol)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
