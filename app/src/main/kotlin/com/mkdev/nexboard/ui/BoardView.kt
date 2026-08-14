package com.mkdev.nexboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.mkdev.nexboard.SettingsManager
import com.mkdev.nexboard.engine.*
import com.mkdev.nexboard.games.checkers.CheckersPiece
import com.mkdev.nexboard.games.chess.ChessPiece
import com.mkdev.nexboard.games.foxandgeese.FoxAndGeesePiece
import com.mkdev.nexboard.games.foxandgeese.FoxAndGeesePieceType
import com.mkdev.nexboard.games.othello.OthelloPiece

class BoardView(context: Context) : View(context) {

    // ─── External state ───────────────────────────────────────────────────────
    var gameState: GameState = GameState(arrayOfNulls(64))
        set(value) {
            field = value
            updateBoardGeometry()
            selectedPos = null
            legalMoves  = emptyList()
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

    // ─── Selection ───────────────────────────────────────────────────────────
    private var selectedPos: Position? = null
    private var legalMoves: List<Move> = emptyList()

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
        val dp      = resources.displayMetrics.density
        val margin  = 4f * dp
        val boardSz = minOf(width.toFloat() - margin * 2, height.toFloat() - margin * 2)
        cellSize  = boardSz / gameState.boardSize.toFloat()
        boardLeft = (width - boardSz) / 2f
        boardTop  = (height - boardSz) / 2f
        piecePaint.textSize       = cellSize * 0.60f
        labelPaint.textSize       = cellSize * 0.22f
        labelPaint.color          = Color.argb(130, 120, 80, 40)
        mustCapturePaint.strokeWidth = cellSize * 0.055f
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
        if (directMoveMode) {
            val engine = ruleEngine ?: return
            if (legalMoves.isEmpty())
                legalMoves = engine.allLegalMoves(gameState, gameState.currentTurn)
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) startMoveAnimation(move)
            return
        }

        val engine = ruleEngine ?: return

        if (selectedPos != null) {
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) { startMoveAnimation(move); return }

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
        val piece = gameState.get(move.from) ?: run { isLocked = false; onMoveMade?.invoke(move); return }

        selectedPos = null; legalMoves = emptyList()
        animPiece   = piece; animFromPos = move.from
        animFromPx  = cellCenter(move.from); animToPx = cellCenter(move.to)
        pendingMove = move; isLocked = true

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animPiece = null; animFromPos = null; animProgress = 0f
                    isLocked  = false; invalidate()
                    pendingMove?.let { onMoveMade?.invoke(it) }
                    pendingMove = null
                }
            })
            start()
        }
    }

    private fun cellCenter(pos: Position): PointF {
        val last = gameState.boardSize - 1
        val dr = if (isFlipped) last - pos.row else pos.row
        val dc = if (isFlipped) last - pos.col else pos.col
        return PointF(boardLeft + dc * cellSize + cellSize / 2f,
                      boardTop  + dr * cellSize + cellSize / 2f)
    }

    // ─── Drawing ─────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        drawBoard(canvas); drawLabels(canvas); drawHighlights(canvas); drawPieces(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            val l = boardLeft + col * cellSize; val t = boardTop + row * cellSize
            canvas.drawRect(l, t, l + cellSize, t + cellSize,
                if ((row + col) % 2 == 0) lightPaint else darkPaint)
        }
    }

    private fun drawLabels(canvas: Canvas) {
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
        canvas.drawRect(boardLeft + c * cellSize, boardTop + r * cellSize,
            boardLeft + (c+1) * cellSize, boardTop + (r+1) * cellSize, paint)
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

    private fun drawPieceAt(canvas: Canvas, piece: Piece, cx: Float, cy: Float, pos: Position? = null) {
        when (piece) {
            is ChessPiece    -> drawChessPiece(canvas, piece, cx, cy)
            is CheckersPiece -> drawCheckersPiece(canvas, piece, cx, cy)
            is FoxAndGeesePiece -> drawFoxAndGeesePiece(canvas, piece, cx, cy)
            is OthelloPiece  -> drawOthelloPiece(canvas, piece, cx, cy, pos)
        }
    }

    private fun drawChessPiece(canvas: Canvas, piece: ChessPiece, cx: Float, cy: Float) {
        val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
        if (shouldRotate) { canvas.save(); canvas.rotate(180f, cx, cy) }
        val glyphY = cy + piecePaint.textSize * 0.36f
        piecePaint.style = Paint.Style.STROKE; piecePaint.strokeWidth = cellSize * 0.04f
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#757575")
                           else Color.parseColor("#EEEEEE")
        canvas.drawText(piece.symbol(), cx, glyphY, piecePaint)
        piecePaint.style = Paint.Style.FILL
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDE7")
                           else Color.parseColor("#212121")
        canvas.drawText(piece.symbol(), cx, glyphY, piecePaint)
        if (shouldRotate) canvas.restore()
    }

    private fun drawCheckersPiece(canvas: Canvas, piece: CheckersPiece, cx: Float, cy: Float) {
        val r = cellSize * 0.38f
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        val isWhite = piece.color == PieceColor.WHITE
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = if (isWhite) Color.parseColor("#F5F5F5") else Color.parseColor("#1E1E1E")
            canvas.drawCircle(cx, cy, r, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.10f
            it.color = if (isWhite) Color.parseColor("#9E9E9E") else Color.parseColor("#616161")
            canvas.drawCircle(cx, cy, r * 0.92f, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.06f
            it.color = if (isWhite) Color.parseColor("#BDBDBD") else Color.parseColor("#424242")
            canvas.drawCircle(cx, cy, r * 0.72f, it)
        }
        if (piece.isKing) {
            val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = r * 1.0f
                color = Color.parseColor("#FFC107")
            }
            canvas.drawText("♛", cx, cy + r * 0.38f, kp)
        }
    }

    private fun drawFoxAndGeesePiece(
        canvas: Canvas,
        piece: FoxAndGeesePiece,
        cx: Float,
        cy: Float
    ) {
        val radius = cellSize * 0.36f
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, radius, shadowPaint)
        val fill = if (piece.type == FoxAndGeesePieceType.FOX)
            Color.parseColor("#F28C28")
        else
            Color.parseColor("#DDEBFF")
        val edge = if (piece.type == FoxAndGeesePieceType.FOX)
            Color.parseColor("#A94F12")
        else
            Color.parseColor("#6A8FB8")
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = fill
            canvas.drawCircle(cx, cy, radius, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = radius * 0.11f
            it.color = edge
            canvas.drawCircle(cx, cy, radius * 0.92f, it)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.type == FoxAndGeesePieceType.FOX)
                Color.WHITE
            else
                Color.parseColor("#29415F")
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.95f
            isFakeBoldText = true
        }
        canvas.drawText(piece.symbol(), cx, cy + labelPaint.textSize * 0.36f, labelPaint)
    }

    private fun drawOthelloPiece(canvas: Canvas, piece: OthelloPiece, cx: Float, cy: Float, pos: Position? = null) {
        val r       = cellSize * 0.40f
        val isWhite = piece.color == PieceColor.WHITE
        val popScale = if (pos != null && pos in recentOthelloPieces)
            othelloPopProgress.coerceAtLeast(0f) else 1f
        canvas.save()
        if (popScale != 1f) canvas.scale(popScale, popScale, cx, cy)
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = if (isWhite) Color.parseColor("#F0F0F0") else Color.parseColor("#181818")
            canvas.drawCircle(cx, cy, r, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.08f
            it.color = if (isWhite) Color.parseColor("#B0B0B0") else Color.parseColor("#505050")
            canvas.drawCircle(cx, cy, r, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.05f
            it.color = if (isWhite) Color.parseColor("#FFFFFF") else Color.parseColor("#383838")
            canvas.drawCircle(cx - r * 0.18f, cy - r * 0.18f, r * 0.45f, it)
        }
        canvas.restore()
    }

    // ─── Coordinate helpers ───────────────────────────────────────────────────

    private fun boardRow(logicRow: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicRow else logicRow
    private fun boardCol(logicCol: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicCol else logicCol

    private fun screenToBoard(x: Float, y: Float): Position? {
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
