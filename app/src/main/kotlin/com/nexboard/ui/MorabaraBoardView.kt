package com.nexboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.nexboard.SettingsManager
import com.nexboard.engine.*
import com.nexboard.games.morabaraba.MorabarabaBoard
import com.nexboard.games.morabaraba.MorabarabaPiece
import com.nexboard.games.morabaraba.MorabarabaRuleEngine

class MorabaraBoardView(context: Context) : View(context) {

    // ─── External state ───────────────────────────────────────────────────────
    var gameState: GameState = MorabarabaRuleEngine().initialState()
        set(value) { field = value; refreshMoves(); invalidate() }

    var ruleEngine: MorabarabaRuleEngine? = null
        set(value) { field = value; if (value != null) updateVariant() }

    var onMoveMade: ((Move) -> Unit)? = null
    /** Called when the board is tapped and the game is already over. */
    var onGameOverTapped: (() -> Unit)? = null
    var isLocked: Boolean = false
    var playerColor: PieceColor = PieceColor.WHITE
    var vsAI: Boolean = false

    // ─── Internal ─────────────────────────────────────────────────────────────
    private var legalMoves:   List<Move> = emptyList()
    private var selectedNode: Int        = -1
    private var validTargets: Set<Int>   = emptySet()
    private var millTargets:  Set<Int>   = emptySet()

    // Variant-aware topology
    private var activePosCount: Int                    = 24
    private var activeAdj:      Array<IntArray>        = MorabarabaBoard.ADJACENCY
    private var activePosList:  List<Position>         = MorabarabaBoard.POSITIONS
    private val activePosToIdx: HashMap<Position, Int> = HashMap()

    private fun updateVariant() {
        val eng = ruleEngine ?: return
        activePosCount = eng.activePositions.size
        activeAdj      = eng.activeAdjacency
        activePosList  = eng.activePositions
        activePosToIdx.clear()
        activePosList.forEachIndexed { i, p -> activePosToIdx[p] = i }
    }

    private fun activeIndexOf(pos: Position): Int = activePosToIdx[pos] ?: -1

    // ─── Animation ────────────────────────────────────────────────────────────
    private var animGen:      Int         = 0
    private var animFrom:     PointF      = PointF()
    private var animTo:       PointF      = PointF()
    private var animNode:     Int         = -1
    private var animColor:    PieceColor? = null
    private var animProgress: Float       = 0f
    private var animator:     ValueAnimator? = null
    private var pendingMove:  Move?       = null

    // Phase B — capture flash
    private var captureNodes:     List<Int> = emptyList()
    private var captureAlpha:     Float     = 0f
    private var captureAnim:      ValueAnimator? = null

    // "MILL!" banner
    private var millBannerText:  String = ""
    private var millBannerAlpha: Float  = 0f
    private var millBannerAnim:  ValueAnimator? = null

    // ─── Geometry ────────────────────────────────────────────────────────────
    private var boardLeft = 0f
    private var boardTop  = 0f
    private var cellSize  = 0f

    // ─── Paints ───────────────────────────────────────────────────────────────
    private val bgPaint       = Paint().apply { color = Color.parseColor("#0E0E0E") }
    private val linePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3A3A"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val nodePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A"); style = Paint.Style.FILL
    }
    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444"); style = Paint.Style.STROKE
    }
    private val whitePiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
    private val blackPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
    private val pieceRingPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#9E9E9E")
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 127, 200, 248); style = Paint.Style.STROKE
    }
    private val dotPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 127, 200, 248) }
    private val millDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 193, 7) }
    private val lastMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 215, 0); style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private val captureFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val captureRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#FF1744")
    }
    private val bannerBgPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.WHITE
    }

    // ─── Theme ───────────────────────────────────────────────────────────────
    fun applyTheme() {
        val t = SettingsManager.currentTheme(context); val ac = t.accent; val dk = t.dark
        linePaint.color     = Color.argb(170, Color.red(dk), Color.green(dk), Color.blue(dk))
        nodePaint.color     = Color.argb(255, (Color.red(dk)*.35f).toInt(), (Color.green(dk)*.35f).toInt(), (Color.blue(dk)*.35f).toInt())
        nodeRingPaint.color = Color.argb(200, (Color.red(dk)*.65f).toInt(), (Color.green(dk)*.65f).toInt(), (Color.blue(dk)*.65f).toInt())
        selectPaint.color   = Color.argb(200, Color.red(ac), Color.green(ac), Color.blue(ac))
        dotPaint.color      = Color.argb(140, Color.red(ac), Color.green(ac), Color.blue(ac))
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); applyTheme() }

    // ─── Size ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val dp = resources.displayMetrics.density
        val pad = 24f * dp
        val size = minOf(w.toFloat() - pad * 2, h.toFloat() - pad * 2)
        cellSize  = size / 6f
        boardLeft = (w - size) / 2f
        boardTop  = (h - size) / 2f
        linePaint.strokeWidth      = cellSize * 0.04f
        nodeRingPaint.strokeWidth  = cellSize * 0.04f
        selectPaint.strokeWidth    = cellSize * 0.10f
        pieceRingPaint.strokeWidth = cellSize * 0.06f
        captureRingPaint.strokeWidth = cellSize * 0.10f
        bannerTextPaint.textSize   = cellSize * 0.38f
    }

    // ─── Coordinate helpers ───────────────────────────────────────────────────
    private fun nodeCenter(idx: Int): PointF {
        val pos = activePosList[idx]
        return PointF(boardLeft + pos.col * cellSize, boardTop + pos.row * cellSize)
    }

    private fun nearestNode(x: Float, y: Float): Int {
        var bestIdx = -1; var bestDist = Float.MAX_VALUE
        for (i in 0 until activePosCount) {
            val c = nodeCenter(i)
            val d = (x-c.x)*(x-c.x) + (y-c.y)*(y-c.y)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        val threshold = cellSize * 0.65f
        return if (bestDist < threshold * threshold) bestIdx else -1
    }

    // ─── Legal moves ─────────────────────────────────────────────────────────
    private fun refreshMoves() {
        val engine = ruleEngine ?: return
        legalMoves   = engine.allLegalMoves(gameState, gameState.currentTurn)
        selectedNode = -1; validTargets = emptySet(); millTargets = emptySet()
    }

    // ─── Touch ───────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Game over: fire callback to re-show result dialog
            if (gameState.status != GameStatus.IN_PROGRESS) {
                onGameOverTapped?.invoke()
                return true
            }
            if (!isLocked && animNode == -1 && captureNodes.isEmpty())
                handleTap(nearestNode(event.x, event.y))
        }
        return true
    }

    private fun handleTap(node: Int) {
        if (node < 0) { selectedNode=-1; validTargets=emptySet(); millTargets=emptySet(); invalidate(); return }
        val engine = ruleEngine ?: return
        val inPlacement = legalMoves.any { it.from == MorabarabaRuleEngine.PLACE }
        val pos = activePosList[node]

        if (inPlacement) {
            if (vsAI && gameState.currentTurn != playerColor) return
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) { startAnim(move); return }
        } else {
            if (selectedNode >= 0) {
                val from = activePosList[selectedNode]
                val move = legalMoves.firstOrNull { it.from == from && it.to == pos }
                if (move != null) { startAnim(move); return }
            }
            val piece = gameState.get(pos)
            if (piece?.color == gameState.currentTurn && (!vsAI || piece.color == playerColor)) {
                val fromPos = pos
                val targets = legalMoves.filter { it.from==fromPos }.map { activeIndexOf(it.to) }.filter { it>=0 }.toSet()
                if (targets.isNotEmpty()) {
                    val mills = legalMoves.filter { it.from==fromPos && it.captures.isNotEmpty() }
                        .map { activeIndexOf(it.to) }.filter { it>=0 }.toSet()
                    selectedNode=node; validTargets=targets; millTargets=mills; invalidate(); return
                }
            }
            selectedNode=-1; validTargets=emptySet(); millTargets=emptySet(); invalidate()
        }
    }

    // ─── Animation ───────────────────────────────────────────────────────────
    private fun startAnim(move: Move) {
        val color = gameState.currentTurn
        val myGen = ++animGen
        selectedNode=-1; validTargets=emptySet(); millTargets=emptySet()

        val toIdx   = activeIndexOf(move.to)
        val isPlace = move.from == MorabarabaRuleEngine.PLACE

        if (!isPlace) {
            val fromIdx = activeIndexOf(move.from)
            if (fromIdx < 0 || toIdx < 0) { isLocked=false; onMoveMade?.invoke(move); return }
            animFrom = nodeCenter(fromIdx); animTo = nodeCenter(toIdx); animNode = fromIdx
        } else {
            if (toIdx < 0) { isLocked=false; onMoveMade?.invoke(move); return }
            animFrom = PointF(boardLeft + 3*cellSize, boardTop + 3*cellSize)
            animTo   = nodeCenter(toIdx); animNode = -2
        }
        animColor=color; animProgress=0f; pendingMove=move; isLocked=true

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animProgress=it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (animGen != myGen) return
                    val completed = pendingMove ?: return
                    pendingMove = null
                    if (completed.captures.isNotEmpty()) {
                        animProgress = 1f
                        captureNodes = completed.captures.map { activeIndexOf(it) }.filter { it >= 0 }
                        startCaptureFlash(completed, myGen)
                    } else {
                        commitMove(completed)
                    }
                }
            })
            start()
        }
    }

    private fun startCaptureFlash(completed: Move, myGen: Int) {
        val capturingColor = animColor ?: gameState.currentTurn
        millBannerText = if (capturingColor == playerColor) "MILL! You captured a piece"
                         else "MILL! AI captured your piece"
        showMillBanner(millBannerText)

        captureAlpha = 1f
        captureAnim?.cancel()
        captureAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 700L
            addUpdateListener { captureAlpha=it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (animGen != myGen) return
                    captureNodes=emptyList(); captureAlpha=0f
                    commitMove(completed)
                }
            })
            start()
        }
    }

    private fun showMillBanner(text: String) {
        millBannerText=text; millBannerAlpha=1f
        millBannerAnim?.cancel()
        millBannerAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay=700L; duration=700L
            addUpdateListener { millBannerAlpha=it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun commitMove(move: Move) {
        animNode=-1; animColor=null; animProgress=0f; isLocked=false
        onMoveMade?.invoke(move)
        invalidate()
    }

    fun animateExternalMove(move: Move) = startAnim(move)

    // ─── Drawing ─────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawLines(canvas); drawHighlights(canvas); drawNodes(canvas)
        drawPieces(canvas); drawCaptureFlash(canvas); drawAnimPiece(canvas); drawMillBanner(canvas)
    }

    private fun drawLines(canvas: Canvas) {
        val drawn = mutableSetOf<Long>()
        for (i in activeAdj.indices) {
            for (j in activeAdj[i]) {
                val key = if (i<j) i.toLong()*100+j else j.toLong()*100+i
                if (!drawn.add(key)) continue
                val a=nodeCenter(i); val b=nodeCenter(j)
                canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
            }
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        gameState.lastMove?.let { m ->
            if (m.from != MorabarabaRuleEngine.PLACE) {
                val i=activeIndexOf(m.from); if (i>=0) { val c=nodeCenter(i); canvas.drawCircle(c.x,c.y,cellSize*.38f,lastMovePaint) }
            }
            val i=activeIndexOf(m.to); if (i>=0) { val c=nodeCenter(i); canvas.drawCircle(c.x,c.y,cellSize*.38f,lastMovePaint) }
        }
        if (selectedNode>=0) { val c=nodeCenter(selectedNode); canvas.drawCircle(c.x,c.y,cellSize*.38f,selectPaint) }
        for (idx in validTargets) {
            val c=nodeCenter(idx)
            canvas.drawCircle(c.x, c.y, cellSize*.14f, if (idx in millTargets) millDotPaint else dotPaint)
        }
    }

    private fun drawNodes(canvas: Canvas) {
        for (i in 0 until activePosCount) {
            if (gameState.get(activePosList[i])==null && i!=animNode) {
                val c=nodeCenter(i)
                canvas.drawCircle(c.x,c.y,cellSize*.13f,nodePaint)
                canvas.drawCircle(c.x,c.y,cellSize*.13f,nodeRingPaint)
            }
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val skipIdx = if (animNode>=0) animNode else -1
        for (i in 0 until activePosCount) {
            if (i==skipIdx) continue
            val piece=gameState.get(activePosList[i]) as? MorabarabaPiece ?: continue
            val c=nodeCenter(i); drawDisc(canvas,piece.color,c.x,c.y)
        }
    }

    private fun drawCaptureFlash(canvas: Canvas) {
        if (captureNodes.isEmpty() || captureAlpha<=0f) return
        val alpha = (captureAlpha*255).toInt().coerceIn(0,255)
        for (idx in captureNodes) {
            if (idx<0) continue
            val c=nodeCenter(idx)
            captureFillPaint.color=Color.argb((alpha*0.7f).toInt(),255,23,68)
            canvas.drawCircle(c.x, c.y, cellSize*.46f, captureFillPaint)
            captureRingPaint.alpha=alpha
            canvas.drawCircle(c.x, c.y, cellSize*.46f, captureRingPaint)
        }
    }

    private fun drawAnimPiece(canvas: Canvas) {
        val col=animColor ?: return; if (animNode==-1) return
        val x=lerp(animFrom.x,animTo.x,animProgress)
        val y=lerp(animFrom.y,animTo.y,animProgress)
        drawDisc(canvas,col,x,y)
    }

    private fun drawMillBanner(canvas: Canvas) {
        if (millBannerAlpha<=0f || millBannerText.isEmpty()) return
        val alpha=(millBannerAlpha*255).toInt().coerceIn(0,255)
        val cx=width/2f; val cy=boardTop+cellSize*1.5f
        val textW=bannerTextPaint.measureText(millBannerText)
        val pad=cellSize*.3f; val rr=cellSize*.25f
        val bg=RectF(cx-textW/2-pad, cy-cellSize*.55f, cx+textW/2+pad, cy+cellSize*.25f)
        bannerBgPaint.color=Color.argb((alpha*0.85f).toInt(),30,0,0)
        canvas.drawRoundRect(bg, rr, rr, bannerBgPaint)
        bannerTextPaint.alpha=alpha
        canvas.drawText(millBannerText, cx, cy, bannerTextPaint)
    }

    private fun drawDisc(canvas: Canvas, color: PieceColor, cx: Float, cy: Float) {
        val r=cellSize*.36f
        canvas.drawCircle(cx+1.5f, cy+2.5f, r, shadowPaint)
        val fill=if (color==PieceColor.WHITE) whitePiecePaint else blackPiecePaint
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, pieceRingPaint)
        canvas.drawCircle(cx, cy, r*.68f, pieceRingPaint)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a+(b-a)*t
}
