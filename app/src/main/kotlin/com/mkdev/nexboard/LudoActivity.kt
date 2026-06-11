package com.mkdev.nexboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════════════════
// LUDO GAME ENGINE  (adapted from LudoGameEngine.kt + LudoAI.kt references)
// ═══════════════════════════════════════════════════════════════════════════════

private const val RING_SIZE    = 52       // main track
private const val HOME_DEPTH   = 6       // cells per home column (incl. finish)
private const val FINISH_POS   = 57      // logical position when piece is done
private const val IN_YARD      = -1      // piece not yet on board
private const val PIECES_EACH  = 4

private enum class LC(val idx: Int) { RED(0), BLUE(1), GREEN(2), YELLOW(3) }

// Where each colour enters the ring (ring index)
private val RING_ENTRY = mapOf(LC.RED to 0, LC.BLUE to 13, LC.GREEN to 26, LC.YELLOW to 39)

// Safe squares: start cells + midpoints  (from LudoGameEngine reference)
private val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)

// ─── 52-cell ring (col, row) in a 15×15 grid ─────────────────────────────────
// Clockwise, (0,0) = top-left.
// Red  starts at 0,  Blue at 13, Green at 26, Yellow at 39.
// Each colour has exactly 13 ring cells before the next colour's start.
private val RING = listOf(
    // Red segment  [0-12]:  col 6 up, row 8 left, left-edge down
    6 to 14, 6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
    5 to 8,  4 to 8,  3 to 8,  2 to 8,  1 to 8,  0 to 8,  0 to 7,
    // Blue segment [13-25]: left-edge up, row 6 right, col 6 up
    0 to 6,  1 to 6,  2 to 6,  3 to 6,  4 to 6,  5 to 6,
    6 to 5,  6 to 4,  6 to 3,  6 to 2,  6 to 1,  6 to 0,  7 to 0,
    // Green segment[26-38]: top-edge right, col 8 down, row 6 right
    8 to 0,  8 to 1,  8 to 2,  8 to 3,  8 to 4,  8 to 5,  8 to 6,
    9 to 6,  10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
    // Yellow segment[39-51]: col 14 down, row 8 left, col 8 down
    14 to 8, 13 to 8, 12 to 8, 11 to 8, 10 to 8,  9 to 8,
    8 to 9,  8 to 10, 8 to 11, 8 to 12, 8 to 13,  8 to 14, 7 to 14
)

// ─── Home columns: 6 cells from ring-exit toward centre (col, row) ────────────
private val HOME_COL = mapOf(
    LC.RED    to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9,  7 to 8),
    LC.BLUE   to listOf(1 to 7,  2 to 7,  3 to 7,  4 to 7,  5 to 7,  6 to 7),
    LC.GREEN  to listOf(7 to 1,  7 to 2,  7 to 3,  7 to 4,  7 to 5,  7 to 6),
    LC.YELLOW to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7,  8 to 7)
)

// ─── Yard slots (col, row) as floats – 4 circles per yard ────────────────────
private val YARD_SLOTS = mapOf(
    LC.RED    to listOf(1.5f to 10.5f, 3.5f to 10.5f, 1.5f to 12.5f, 3.5f to 12.5f),
    LC.BLUE   to listOf(1.5f to  1.5f, 3.5f to  1.5f, 1.5f to  3.5f, 3.5f to  3.5f),
    LC.GREEN  to listOf(10.5f to 1.5f, 12.5f to 1.5f, 10.5f to 3.5f, 12.5f to 3.5f),
    LC.YELLOW to listOf(10.5f to 10.5f,12.5f to 10.5f,10.5f to 12.5f,12.5f to 12.5f)
)

// ─── Piece ────────────────────────────────────────────────────────────────────
private data class Piece(
    val color: LC,
    val id: Int,
    var pos: Int = IN_YARD,
    var done: Boolean = false
)

// ─── Player ───────────────────────────────────────────────────────────────────
private data class Player(
    val color: LC,
    val pieces: List<Piece> = List(PIECES_EACH) { Piece(color, it) },
    var human: Boolean = true
) {
    val allDone get() = pieces.all { it.done }
}

// ─── Movement rules ───────────────────────────────────────────────────────────
private object Rules {

    /** Relative clockwise distance from [start] to [pos] on the 52-ring. */
    private fun relDist(start: Int, pos: Int) =
        if (pos >= start) pos - start else RING_SIZE - start + pos

    /**
     * New logical position after [steps] from [from] for colour [c].
     * Returns IN_YARD when the move is illegal (overshoot / no 6 from yard).
     */
    fun advance(c: LC, from: Int, steps: Int): Int {
        if (from == IN_YARD)  return if (steps == 6) RING_ENTRY[c]!! else IN_YARD
        if (from >= RING_SIZE) {            // already in home column
            val np = from + steps
            return when {
                np == FINISH_POS -> FINISH_POS
                np > FINISH_POS  -> IN_YARD  // overshoot
                else             -> np
            }
        }
        val entry    = RING_ENTRY[c]!!
        val traveled = relDist(entry, from)
        val total    = traveled + steps
        return when {
            total >= RING_SIZE -> {
                val homeStep = total - RING_SIZE
                if (homeStep >= HOME_DEPTH) IN_YARD else RING_SIZE + homeStep
            }
            else -> (entry + total) % RING_SIZE
        }
    }

    /** All pieces of [p] that can legally move with [dice]. */
    fun movable(p: Player, dice: Int): List<Piece> =
        p.pieces.filter { !it.done && advance(it.color, it.pos, dice) != IN_YARD }

    /** Find an opponent piece at [pos] that can be captured (not on safe cell). */
    fun captureAt(pos: Int, mover: LC, all: List<Player>): Piece? {
        if (pos < 0 || pos >= RING_SIZE || pos in SAFE) return null
        for (pl in all) {
            if (pl.color == mover) continue
            for (p in pl.pieces) if (p.pos == pos && !p.done) return p
        }
        return null
    }
}

// ─── AI (adapted from LudoAI.kt reference) ───────────────────────────────────
private object AI {
    fun pick(player: Player, dice: Int, all: List<Player>): Piece? {
        val cands = Rules.movable(player, dice)
        if (cands.isEmpty()) return null
        data class C(val p: Piece, val np: Int, val caps: Boolean, val wins: Boolean)
        val rich = cands.map { p ->
            val np   = Rules.advance(p.color, p.pos, dice)
            val caps = np in 0 until RING_SIZE && Rules.captureAt(np, player.color, all) != null
            val wins = np == FINISH_POS
            C(p, np, caps, wins)
        }
        // Priority 1 – finish a piece immediately
        rich.firstOrNull { it.wins }?.let { return it.p }
        // Priority 2 – capture an opponent
        rich.firstOrNull { it.caps }?.let { return it.p }
        // Priority 3 – advance piece almost home
        rich.filter { it.np in 48..FINISH_POS }.maxByOrNull { it.np }?.let { return it.p }
        // Priority 4 – enter board from yard
        rich.firstOrNull { it.p.pos == IN_YARD }?.let { return it.p }
        // Priority 5 – advance furthest piece
        rich.maxByOrNull { it.np }?.let { return it.p }
        return cands.first()
    }
}

// ─── Cell centre lookup (col·float, row·float) ───────────────────────────────
private fun pieceCell(piece: Piece): Pair<Float, Float>? {
    if (piece.done) return 7.5f to 7.5f
    val pos = piece.pos
    return when {
        pos == IN_YARD -> YARD_SLOTS[piece.color]?.getOrNull(piece.id)
        pos in 0 until RING_SIZE ->
            RING.getOrNull(pos)?.let { (c, r) -> c + 0.5f to r + 0.5f }
        pos in RING_SIZE..FINISH_POS ->
            HOME_COL[piece.color]?.getOrNull(pos - RING_SIZE)?.let { (c, r) -> c + 0.5f to r + 0.5f }
        else -> null
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOARD VIEW
// ═══════════════════════════════════════════════════════════════════════════════

private class BoardView(ctx: Context) : View(ctx) {

    var players:  List<Player>  = emptyList(); set(v) { field = v; invalidate() }
    var movable:  List<Piece>   = emptyList(); set(v) { field = v; invalidate() }
    var selected: Piece?        = null;        set(v) { field = v; invalidate() }
    var onTap: ((Piece) -> Unit)? = null

    private val path = Path()
    private val pp   = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cell = 0f

    // Board colours
    private val COL = intArrayOf(
        Color.parseColor("#EF5350"),   // Red
        Color.parseColor("#42A5F5"),   // Blue
        Color.parseColor("#66BB6A"),   // Green
        Color.parseColor("#FFCA28")    // Yellow
    )
    private val YARD_COL = intArrayOf(
        Color.parseColor("#3D0E0E"),
        Color.parseColor("#0A1835"),
        Color.parseColor("#0D3519"),
        Color.parseColor("#3D3300")
    )

    override fun onMeasure(ws: Int, hs: Int) {
        val s = min(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs))
        setMeasuredDimension(s, s)
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { cell = w / 15f }

    private fun cl(c: Float)  = c * cell         // left edge of column c
    private fun ct(r: Float)  = r * cell         // top edge of row r
    private fun cx(c: Float)  = (c + 0.5f) * cell  // centre-x of col c
    private fun cy(r: Float)  = (r + 0.5f) * cell  // centre-y of row r

    // ─── Draw ────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (cell == 0f) return
        drawBackground(canvas)
        drawTrackCross(canvas)
        drawYards(canvas)
        drawHomeColumns(canvas)
        drawCentreHome(canvas)
        drawSafeStars(canvas)
        drawGridLines(canvas)
        drawPieces(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        pp.color = Color.parseColor("#1A1B22"); pp.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, cell * 15, cell * 15, pp)
    }

    private fun drawTrackCross(canvas: Canvas) {
        pp.color = Color.parseColor("#23242F"); pp.style = Paint.Style.FILL
        canvas.drawRect(0f,      ct(6f), cell * 15, ct(9f), pp)   // horizontal
        canvas.drawRect(cl(6f),  0f,     cl(9f),    cell * 15, pp) // vertical
    }

    private fun drawYards(canvas: Canvas) {
        val defs = listOf(
            LC.RED    to (0 to 9),   // bottom-left
            LC.BLUE   to (0 to 0),   // top-left
            LC.GREEN  to (9 to 0),   // top-right
            LC.YELLOW to (9 to 9)    // bottom-right
        )
        defs.forEach { (lc, cr) ->
            val (col, row) = cr
            val ci = lc.idx

            // Outer block
            pp.color = YARD_COL[ci]; pp.style = Paint.Style.FILL
            canvas.drawRect(cl(col.toFloat()), ct(row.toFloat()),
                            cl(col + 6f), ct(row + 6f), pp)
            // Inner recess
            pp.color = Color.parseColor("#141520")
            canvas.drawRect(cl(col + 1f), ct(row + 1f),
                            cl(col + 5f), ct(row + 5f), pp)

            // 4 yard circles
            val sColF = listOf(col+1.5f, col+3.5f, col+1.5f, col+3.5f)
            val sRowF = listOf(row+1.5f, row+1.5f, row+3.5f, row+3.5f)
            for (i in 0..3) {
                pp.color = COL[ci]; pp.style = Paint.Style.FILL
                canvas.drawCircle(cx(sColF[i]), cy(sRowF[i]), cell * 0.38f, pp)
                pp.color = Color.parseColor("#141520")
                canvas.drawCircle(cx(sColF[i]), cy(sRowF[i]), cell * 0.26f, pp)
            }
        }
    }

    private fun drawHomeColumns(canvas: Canvas) {
        for (lc in LC.values()) {
            val cells = HOME_COL[lc] ?: continue
            cells.forEach { (c, r) ->
                pp.color = COL[lc.idx]; pp.alpha = 110; pp.style = Paint.Style.FILL
                canvas.drawRect(cl(c.toFloat()), ct(r.toFloat()),
                                cl(c + 1f), ct(r + 1f), pp)
                pp.alpha = 255
            }
        }
    }

    private fun drawCentreHome(canvas: Canvas) {
        val ccx = cx(7f); val ccy = cy(7f)
        val tl = cl(6f) to ct(6f); val tr = cl(9f) to ct(6f)
        val bl = cl(6f) to ct(9f); val br = cl(9f) to ct(9f)
        val ctr = ccx to ccy
        listOf(
            LC.BLUE   to (tl to tr),
            LC.RED    to (bl to br),
            LC.GREEN  to (tr to br),
            LC.YELLOW to (tl to bl)
        ).forEach { (lc, pts) ->
            path.reset()
            path.moveTo(ctr.first,     ctr.second)
            path.lineTo(pts.first.first, pts.first.second)
            path.lineTo(pts.second.first, pts.second.second)
            path.close()
            pp.color = COL[lc.idx]; pp.style = Paint.Style.FILL; pp.alpha = 230
            canvas.drawPath(path, pp)
            pp.alpha = 255
        }
    }

    private fun drawSafeStars(canvas: Canvas) {
        val starts = RING_ENTRY.values.toSet()
        SAFE.filter { it !in starts }.forEach { pos ->
            val (c, r) = RING[pos]
            drawStar(canvas, cx(c.toFloat()), cy(r.toFloat()), cell * 0.28f)
        }
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float, outer: Float) {
        val inner = outer * 0.45f; val pts = 5
        val step  = Math.PI / pts
        path.reset()
        for (i in 0 until pts * 2) {
            val a  = i * step - Math.PI / 2
            val r  = if (i % 2 == 0) outer else inner
            val px = x + (r * cos(a)).toFloat()
            val py = y + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        pp.color = Color.parseColor("#FFD700"); pp.style = Paint.Style.FILL; pp.alpha = 200
        canvas.drawPath(path, pp)
        pp.alpha = 255
    }

    private fun drawGridLines(canvas: Canvas) {
        pp.color = Color.parseColor("#2C2D3A"); pp.style = Paint.Style.STROKE; pp.strokeWidth = 0.8f
        for (i in 0..15) {
            canvas.drawLine(cl(i.toFloat()), 0f,      cl(i.toFloat()), cell * 15, pp)
            canvas.drawLine(0f,             ct(i.toFloat()), cell * 15, ct(i.toFloat()), pp)
        }
        pp.style = Paint.Style.FILL
    }

    // ─── Pieces ───────────────────────────────────────────────────────────────

    private fun drawPieces(canvas: Canvas) {
        val all = players.flatMap { it.pieces }

        // Group by cell for stacking offset
        val groups = mutableMapOf<Pair<Float, Float>, MutableList<Piece>>()
        all.filter { !it.done }.forEach { p ->
            val cell = pieceCell(p) ?: return@forEach
            groups.getOrPut(cell) { mutableListOf() }.add(p)
        }

        groups.forEach { (cellCentre, pieces) ->
            val pcx = cellCentre.first  * cell
            val pcy = cellCentre.second * cell
            val n   = pieces.size
            val offs = stackOffsets(n)
            pieces.forEachIndexed { i, piece ->
                val ox = offs.getOrElse(i) { 0f to 0f }.first
                val oy = offs.getOrElse(i) { 0f to 0f }.second
                drawOnePiece(canvas, pcx + ox, pcy + oy, piece)
            }
        }

        // Finished pieces huddle at centre
        val done = all.filter { it.done }
        done.forEachIndexed { i, piece ->
            val small = listOf(-0.15f to -0.15f, 0.15f to -0.15f,
                                -0.15f to  0.15f, 0.15f to  0.15f,
                                 0f    to  0f,   -0.3f  to  0f,
                                 0.3f  to  0f,    0f    to -0.3f)
            val off = small.getOrElse(i) { 0f to 0f }
            val pcx = cx(7f) + off.first  * cell
            val pcy = cy(7f) + off.second * cell
            pp.color = COL[piece.color.idx]; pp.alpha = 220; pp.style = Paint.Style.FILL
            canvas.drawCircle(pcx, pcy, cell * 0.18f, pp)
            pp.alpha = 255
        }
    }

    private fun drawOnePiece(canvas: Canvas, pcx: Float, pcy: Float, piece: Piece) {
        val isMovable  = movable.contains(piece)
        val isSelected = selected == piece
        val radius     = cell * 0.32f

        if (isMovable) {
            pp.color = if (isSelected) Color.parseColor("#00E5FF") else Color.WHITE
            pp.style = Paint.Style.STROKE; pp.strokeWidth = cell * 0.10f; pp.alpha = 220
            canvas.drawCircle(pcx, pcy, radius + cell * 0.07f, pp)
            pp.alpha = 255
        }

        pp.style = Paint.Style.FILL
        pp.color = Color.BLACK; pp.alpha = 60
        canvas.drawCircle(pcx + cell * 0.04f, pcy + cell * 0.04f, radius, pp)

        pp.color = COL[piece.color.idx]; pp.alpha = 255
        canvas.drawCircle(pcx, pcy, radius, pp)

        pp.color = Color.WHITE; pp.alpha = 65
        canvas.drawCircle(pcx - radius * 0.22f, pcy - radius * 0.22f, radius * 0.38f, pp)

        pp.color = Color.BLACK; pp.alpha = 90
        canvas.drawCircle(pcx, pcy, cell * 0.085f, pp)
        pp.alpha = 255
    }

    private fun stackOffsets(n: Int): List<Pair<Float, Float>> {
        val d = cell * 0.18f
        return when (n) {
            1    -> listOf(0f to 0f)
            2    -> listOf(-d to 0f, d to 0f)
            3    -> listOf(-d to d*0.5f, d to d*0.5f, 0f to -d*0.7f)
            else -> listOf(-d to -d, d to -d, -d to d, d to d)
        }
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP || cell == 0f || movable.isEmpty()) return true
        val tx = e.x; val ty = e.y
        var best: Piece? = null; var bestD = Float.MAX_VALUE
        for (piece in movable) {
            val (cf, rf) = pieceCell(piece) ?: continue
            val px = cf * cell; val py = rf * cell
            val d  = sqrt((tx - px).pow(2) + (ty - py).pow(2))
            if (d < cell * 0.55f && d < bestD) { bestD = d; best = piece }
        }
        best?.let { onTap?.invoke(it) }
        return true
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HUD VIEW
// ═══════════════════════════════════════════════════════════════════════════════

private class HudView(ctx: Context) : View(ctx) {

    var playerColor: Int = Color.parseColor("#EF5350"); set(v) { field = v; invalidate() }
    var status: String = "Roll the dice";              set(v) { field = v; invalidate() }
    var diceVal: Int = 0;                              set(v) { field = v; invalidate() }
    var diceEnabled: Boolean = true;                   set(v) { field = v; invalidate() }

    var onBack: (() -> Unit)? = null
    var onDice: (() -> Unit)? = null

    private val dp  = ctx.resources.displayMetrics.density
    private val sp  = ctx.resources.displayMetrics.scaledDensity
    private val pp  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diceRct = RectF()
    private val backRct = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Background
        pp.color = Color.parseColor("#1A1B22"); pp.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, pp)
        pp.color = Color.parseColor("#2C2D3A")
        canvas.drawRect(0f, h - dp, w, h, pp)

        // ← back
        backRct.set(dp * 4f, dp * 8f, dp * 46f, h - dp * 8f)
        pp.color = Color.parseColor("#7FC8F8"); pp.textAlign = Paint.Align.CENTER
        pp.textSize = 22f * sp; pp.isFakeBoldText = true; pp.style = Paint.Style.FILL
        canvas.drawText("←", backRct.centerX(), backRct.centerY() + pp.textSize * 0.36f, pp)

        // Player colour dot
        val dotX = dp * 64f; val dotR = h * 0.18f
        pp.color = playerColor
        canvas.drawCircle(dotX, h / 2f, dotR, pp)
        pp.color = Color.WHITE; pp.style = Paint.Style.STROKE; pp.strokeWidth = 1.5f
        canvas.drawCircle(dotX, h / 2f, dotR, pp)
        pp.style = Paint.Style.FILL

        // Status text
        pp.color = Color.WHITE; pp.textAlign = Paint.Align.CENTER; pp.textSize = 13f * sp; pp.isFakeBoldText = false
        canvas.drawText(status, w / 2f, h / 2f + pp.textSize * 0.36f, pp)

        // Dice
        val ds = h * 0.68f
        val dx = w - dp * 10f - ds; val dy = h * 0.16f
        diceRct.set(dx, dy, dx + ds, dy + ds)
        drawDice(canvas, diceRct, diceVal, diceEnabled)
    }

    private fun drawDice(canvas: Canvas, r: RectF, v: Int, enabled: Boolean) {
        val corner = r.width() * 0.18f
        // Shadow
        pp.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(r.left + 2, r.top + 2, r.right + 2, r.bottom + 2, corner, corner, pp)
        // Face
        pp.color = if (enabled) Color.parseColor("#F5F5F5") else Color.parseColor("#444455")
        canvas.drawRoundRect(r, corner, corner, pp)
        // Border
        pp.color = Color.parseColor("#BDBDBD"); pp.style = Paint.Style.STROKE; pp.strokeWidth = 1.5f
        canvas.drawRoundRect(r, corner, corner, pp)
        pp.style = Paint.Style.FILL

        if (v in 1..6) {
            val dotR   = r.width() * 0.09f
            val iL     = r.left   + r.width()  * 0.18f
            val iT     = r.top    + r.height()  * 0.18f
            val iW     = r.width()  * 0.64f
            val dotCol = if (v == 6) Color.parseColor("#EF5350") else Color.parseColor("#212121")
            val dots   = when (v) {
                1 -> listOf(0.5f to 0.5f)
                2 -> listOf(0.25f to 0.25f, 0.75f to 0.75f)
                3 -> listOf(0.25f to 0.25f, 0.5f  to 0.5f,  0.75f to 0.75f)
                4 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f)
                5 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.5f  to 0.5f,  0.25f to 0.75f, 0.75f to 0.75f)
                6 -> listOf(0.25f to 0.2f,  0.75f to 0.2f,  0.25f to 0.5f,  0.75f to 0.5f,  0.25f to 0.8f,  0.75f to 0.8f)
                else -> emptyList()
            }
            pp.color = dotCol
            dots.forEach { (rx, ry) ->
                canvas.drawCircle(iL + rx * iW, iT + ry * iW, dotR, pp)
            }
        } else {
            pp.color = Color.parseColor("#666688"); pp.textAlign = Paint.Align.CENTER
            pp.textSize = r.width() * 0.46f
            canvas.drawText("?", r.centerX(), r.centerY() + pp.textSize * 0.36f, pp)
            pp.textAlign = Paint.Align.LEFT
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            when {
                backRct.contains(e.x, e.y)              -> onBack?.invoke()
                diceRct.contains(e.x, e.y) && diceEnabled -> onDice?.invoke()
            }
        }
        return true
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTIVITY
// ═══════════════════════════════════════════════════════════════════════════════

class LudoActivity : AppCompatActivity() {

    private val handler  = Handler(Looper.getMainLooper())
    private lateinit var board: BoardView
    private lateinit var hud:   HudView

    // Game state
    private var players    = listOf<Player>()
    private var curIdx     = 0
    private var dice       = 0
    private var rolled     = false
    private var canMove    = listOf<Piece>()
    private var vsAI       = true
    private var over       = false
    private var consec6    = 0

    private val COLORS = intArrayOf(
        Color.parseColor("#EF5350"),  // RED
        Color.parseColor("#42A5F5"),  // BLUE
        Color.parseColor("#66BB6A"),  // GREEN
        Color.parseColor("#FFCA28")   // YELLOW
    )

    private val cur get() = players.getOrNull(curIdx)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hud   = HudView(this)
        board = BoardView(this)

        root.addView(hud,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (58 * dp).toInt()))
        root.addView(board, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        AdManager.attachBanner(root)
        setContentView(root)

        hud.onBack = { onBackPressedDispatcher.onBackPressed() }
        hud.onDice = { tapDice() }
        board.onTap = { tapPiece(it) }

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }

    // ─── Mode dialog ─────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_MinWidth)
            .setTitle("Ludo")
            .setMessage("Choose mode")
            .setCancelable(false)
            .setPositiveButton("vs AI") { _, _ -> vsAI = true;  newGame() }
            .setNeutralButton("2 Players") { _, _ -> vsAI = false; newGame() }
            .setNegativeButton("How to Play") { _, _ -> showRules() }
            .show()
            .window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun showRules() {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = """Ludo — How to Play

GOAL
Move all 4 pieces from your yard into the centre home triangle.

ROLLING
• Tap the dice on your turn.
• You need a 6 to bring a piece out of the yard.
• Rolling a 6 gives you a bonus turn.
• Three 6s in a row forfeits your turn.

MOVING
• Pieces travel clockwise around the 52-square outer ring.
• After a full loop, pieces enter your coloured home column.
• The piece must land exactly on the finish square.
• Pieces in the home column cannot be captured.

CAPTURING
• Land on an opponent piece to send it back to their yard.
• Stars (⭐) are safe squares — no captures allowed there.
• Capturing gives a bonus turn.

WINNING
• First player to finish all 4 pieces wins!

In vs-AI mode you play Red; AI plays Yellow."""
            setTextColor(Color.parseColor("#CCCCCC"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
        }
        val sv = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_MinWidth)
            .setView(sv)
            .setPositiveButton("Play") { _, _ -> showModeDialog() }
            .setNegativeButton("Back") { _, _ -> showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    // ─── Game setup ──────────────────────────────────────────────────────────

    private fun newGame() {
        players  = listOf(
            Player(LC.RED,    human = true),
            Player(LC.YELLOW, human = !vsAI)
        )
        curIdx   = 0; dice = 0; rolled = false; canMove = emptyList()
        over     = false; consec6 = 0
        SoundPlayer.play("game_start")
        syncBoard(); syncHud()
    }

    // ─── Input handlers ───────────────────────────────────────────────────────

    private fun tapDice() {
        val p = cur ?: return
        if (over || rolled || !p.human) return
        roll()
    }

    private fun tapPiece(piece: Piece) {
        val p = cur ?: return
        if (over || !rolled || !p.human || !canMove.contains(piece)) return
        board.selected = piece
        executeMove(piece)
    }

    // ─── Roll ─────────────────────────────────────────────────────────────────

    private fun roll() {
        val p = cur ?: return
        dice   = (1..6).random()
        rolled = true
        SoundPlayer.play("ui_click")

        if (dice == 6) consec6++ else consec6 = 0

        // Three 6s: forfeit
        if (consec6 == 3) {
            consec6 = 0; canMove = emptyList()
            syncBoard(); syncHud("Three 6s — turn forfeited!")
            handler.postDelayed({ nextTurn() }, 1000)
            return
        }

        canMove = Rules.movable(p, dice)
        syncBoard(); syncHud()

        when {
            canMove.isEmpty() -> {
                // No legal moves
                handler.postDelayed({
                    if (dice != 6) nextTurn() else { rolled = false; syncHud() }
                }, 800)
            }
            !p.human -> {
                // AI picks
                handler.postDelayed({
                    val choice = AI.pick(p, dice, players)
                    if (choice != null) executeMove(choice) else nextTurn()
                }, 700)
            }
            // Human waits for tapPiece
        }
    }

    // ─── Execute a move ───────────────────────────────────────────────────────

    private fun executeMove(piece: Piece) {
        val np      = Rules.advance(piece.color, piece.pos, dice)
        val fromYard = piece.pos == IN_YARD

        // Capture
        val captured = if (np in 0 until RING_SIZE) Rules.captureAt(np, piece.color, players) else null
        captured?.let { it.pos = IN_YARD; SoundPlayer.play("mora_capture") }

        // Move piece
        piece.pos = np
        if (np == FINISH_POS) { piece.done = true; SoundPlayer.play("mora_place") }
        else SoundPlayer.play("mora_move")

        board.selected = null; canMove = emptyList()
        syncBoard()

        val p = cur ?: return

        // Win check
        if (p.allDone) {
            over = true; syncHud()
            handler.postDelayed({ showWin(p) }, 500)
            return
        }

        val bonus = dice == 6 || captured != null || piece.done
        if (bonus) {
            rolled = false; consec6 = if (dice == 6) consec6 else 0
            syncHud()
            if (!p.human) handler.postDelayed({ roll() }, 900)
        } else {
            nextTurn()
        }
    }

    // ─── Turn management ──────────────────────────────────────────────────────

    private fun nextTurn() {
        curIdx  = (curIdx + 1) % players.size
        dice    = 0; rolled = false; canMove = emptyList(); consec6 = 0
        board.selected = null
        syncBoard(); syncHud()
        val p = cur ?: return
        if (!p.human) handler.postDelayed({ roll() }, 900)
    }

    // ─── Win dialog ───────────────────────────────────────────────────────────

    private fun showWin(winner: Player) {
        SoundPlayer.play("game_end")
        val name = colorName(winner.color)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_MinWidth)
            .setTitle("🏆  $name Wins!")
            .setMessage("All pieces made it home.")
            .setCancelable(false)
            .setPositiveButton("Play Again") { _, _ -> showModeDialog() }
            .setNegativeButton("Menu") { _, _ -> finish() }
            .show()
            .window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    // ─── UI sync ──────────────────────────────────────────────────────────────

    private fun syncBoard() {
        board.players = players; board.movable = canMove
    }

    private fun syncHud(override: String? = null) {
        val p = cur ?: return
        hud.playerColor  = COLORS[p.color.idx]
        hud.diceVal      = dice
        hud.diceEnabled  = !rolled && p.human && !over
        hud.status       = override ?: when {
            over                          -> "Game over"
            !rolled &&  p.human           -> "${colorName(p.color)}'s turn — tap dice"
            !rolled && !p.human           -> "AI thinking…"
            canMove.isEmpty() && dice != 6 -> "No moves — passing"
            canMove.isEmpty() && dice == 6 -> "No moves — roll again"
            p.human                       -> "Choose a piece"
            else                          -> "AI moving…"
        }
    }

    private fun colorName(c: LC) = when (c) {
        LC.RED -> "Red"; LC.YELLOW -> "Yellow"; LC.GREEN -> "Green"; LC.BLUE -> "Blue"
    }

    // ─── Fullscreen ───────────────────────────────────────────────────────────

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE          or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN   or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}
