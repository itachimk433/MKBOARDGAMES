package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePiece
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePieceType
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import java.util.Locale

/**
 * A horizontal strip that renders a list of captured pieces left-to-right,
 * or a compact player score summary for games such as Go.
 *
 * Top strip    → WHITE pieces captured BY Black  (placed near Black's side of the board)
 * Bottom strip → BLACK pieces captured BY White  (placed near White's side of the board)
 *
 * Each piece is rendered in its own color so the visual matches the board:
 *   White pieces → cream/light
 *   Black pieces → dark/grey
 *
 * An optional label (e.g. "Black's captures" or "Black ⚔") can be set via [setLabel].
 */
class CaptureStripView(context: Context) : View(context) {

    private val dp   = resources.displayMetrics.density
    private val sp   = resources.displayMetrics.scaledDensity
    private val bgP  = Paint().apply { color = Color.parseColor("#1A1A1A") }
    private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }

    private val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.LEFT
        textSize = 9f * sp.coerceAtMost(3f); isFakeBoldText = true; letterSpacing = 0.08f
    }
    private val summaryNameP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.LEFT; isFakeBoldText = true
        textSize = 13f * sp.coerceAtMost(3f)
    }
    private val summaryMetaP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B8B8B"); textAlign = Paint.Align.CENTER
        textSize = 8f * sp.coerceAtMost(3f); isFakeBoldText = true; letterSpacing = 0.08f
    }
    private val summaryValueP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        textSize = 15f * sp.coerceAtMost(3f)
    }
    private val summaryDividerP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#303030")
    }
    private val summaryAccentP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8")
    }

    // Two-pass text paints — reused for every piece
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; style = Paint.Style.STROKE
    }
    private val fillP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; style = Paint.Style.FILL
    }
    // For checkers: circle-based rendering
    private val circleP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kingP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var pieces: List<Piece> = emptyList()
    private var label: String = ""
    private var selectable = false
    private var summaryMode = false
    private var summaryPlayer = ""
    private var summaryColor = PieceColor.BLACK
    private var summaryScore = 0.0
    private var summaryCaptures = 0
    private var summaryActive = false
    var onPieceSelected: ((Piece) -> Unit)? = null
    var dividerOnTop: Boolean = false

    fun update(newPieces: List<Piece>) {
        pieces = newPieces; invalidate()
    }

    /** Optionally set a label shown at the far-right of the strip (e.g. "Black's captures"). */
    fun setLabel(text: String) {
        label = text; invalidate()
    }

    fun setSummary(
        player: String,
        playerColor: PieceColor,
        score: Double,
        captures: Int,
        active: Boolean,
    ) {
        summaryMode = true
        summaryPlayer = player
        summaryColor = playerColor
        summaryScore = score
        summaryCaptures = captures
        summaryActive = active
        selectable = false
        isClickable = false
        invalidate()
    }

    fun clearSummary() {
        summaryMode = false
        invalidate()
    }

    fun setSelectable(value: Boolean) {
        selectable = value
        isClickable = value
        invalidate()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP && selectable && pieces.isNotEmpty()) {
            val gap = height * 0.62f
            if (gap > 0f) {
                val index = ((event.x - gap * 0.2f) / gap).toInt()
                pieces.getOrNull(index)?.let { onPieceSelected?.invoke(it) }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgP)
        if (dividerOnTop) canvas.drawRect(0f, 0f, w, dp, divP)
        else              canvas.drawRect(0f, h - dp, w, h, divP)

        if (summaryMode) {
            drawSummary(canvas)
            return
        }

        // Draw optional label on the right
        if (label.isNotEmpty()) {
            lblP.textSize = 9f * sp.coerceAtMost(3f)
            val labelX = w - lblP.measureText(label) - 8f * dp
            val labelY = h * 0.45f + lblP.textSize * 0.36f
            canvas.drawText(label, labelX, labelY, lblP)
        }

        if (pieces.isEmpty()) return

        val sz  = h * 0.52f     // glyph/circle size
        val gap = h * 0.62f     // horizontal stride
        var x   = gap * 0.52f   // start X

        strokeP.textSize = sz; fillP.textSize = sz
        strokeP.strokeWidth = sz * 0.04f

        val baseY = h * 0.72f   // text baseline

        // Clip pieces area away from label
        val maxX = if (label.isNotEmpty()) w - lblP.measureText(label) - 20f * dp else w - gap * 0.3f

        for (p in pieces) {
            if (x > maxX) break

            when (p) {
                is CheckersPiece -> drawCheckersPiece(canvas, p, x + sz * 0.5f, h / 2f, sz * 0.36f)
                is ChessPiece    -> drawChessPiece(canvas, p, x, baseY)
                is FoxAndGeesePiece -> drawFoxAndGeesePiece(canvas, p, x, baseY)
                is ShogiPiece   -> drawShogiPiece(canvas, p, x, baseY)
                else             -> drawMorabaraPiece(canvas, p, x + sz * 0.5f, h / 2f, sz * 0.36f)
            }
            x += gap
        }
    }

    private fun drawSummary(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val accent = if (summaryColor == PieceColor.WHITE) Color.parseColor("#E8E8E8")
        else Color.parseColor("#8B9AA8")

        // Keep the active player visible without competing with the board.
        summaryAccentP.color = if (summaryActive) Color.parseColor("#7FC8F8") else accent
        canvas.drawRect(0f, 0f, if (summaryActive) 3f * dp else 1f * dp, h, summaryAccentP)

        canvas.drawLine(w * 0.38f, 9f * dp, w * 0.38f, h - 9f * dp, summaryDividerP)
        canvas.drawLine(w * 0.68f, 9f * dp, w * 0.68f, h - 9f * dp, summaryDividerP)

        circleP.style = Paint.Style.FILL
        circleP.color = if (summaryColor == PieceColor.WHITE) Color.parseColor("#F5F5F5")
        else Color.parseColor("#252B30")
        val indicatorX = 16f * dp
        val indicatorY = h / 2f
        canvas.drawCircle(indicatorX, indicatorY, 6f * dp, circleP)
        circleP.style = Paint.Style.STROKE
        circleP.strokeWidth = 1.2f * dp
        circleP.color = if (summaryColor == PieceColor.WHITE) Color.parseColor("#AAAAAA")
        else Color.parseColor("#66727D")
        canvas.drawCircle(indicatorX, indicatorY, 6f * dp, circleP)
        circleP.style = Paint.Style.FILL

        summaryNameP.color = if (summaryActive) Color.WHITE else Color.parseColor("#D0D0D0")
        canvas.drawText(
            summaryPlayer,
            29f * dp,
            indicatorY + summaryNameP.textSize * 0.35f,
            summaryNameP,
        )

        drawMetric(canvas, "SCORE", formatScore(summaryScore), w * 0.52f)
        drawMetric(canvas, "CAPTURED", summaryCaptures.toString(), w * 0.83f)
    }

    private fun drawMetric(canvas: Canvas, label: String, value: String, centerX: Float) {
        val h = height.toFloat()
        canvas.drawText(label, centerX, h / 2f - 3f * dp, summaryMetaP)
        canvas.drawText(value, centerX, h / 2f + 15f * dp, summaryValueP)
    }

    private fun formatScore(score: Double): String =
        if (score == score.toInt().toDouble()) score.toInt().toString()
        else String.format(Locale.US, "%.1f", score)

    /**
     * Draws a chess piece symbol using two-pass (stroke + fill) rendering.
     *
     * Each piece is rendered in its own color — matching how it appears on the board:
     *   WHITE pieces → cream fill with dark stroke
     *   BLACK pieces → light-grey visible rendering on dark background
     */
    private fun drawChessPiece(canvas: Canvas, piece: ChessPiece, x: Float, baseY: Float) {
        if (piece.color == PieceColor.WHITE) {
            strokeP.color = Color.parseColor("#555555")
            canvas.drawText(piece.symbol(), x, baseY, strokeP)
            fillP.color = Color.parseColor("#FFFDE7")
            canvas.drawText(piece.symbol(), x, baseY, fillP)
        } else {
            // Black piece on dark background — light stroke + mid-grey fill for visibility
            strokeP.color = Color.parseColor("#CCCCCC")
            canvas.drawText(piece.symbol(), x, baseY, strokeP)
            fillP.color = Color.parseColor("#8A8A8A")
            canvas.drawText(piece.symbol(), x, baseY, fillP)
        }
    }

    private fun drawFoxAndGeesePiece(canvas: Canvas, piece: FoxAndGeesePiece, x: Float, baseY: Float) {
        val radius = fillP.textSize * .38f
        val fox = piece.type == FoxAndGeesePieceType.FOX
        val cy = baseY - radius * .25f
        val base = if (fox) Color.parseColor("#35B7A1") else Color.parseColor("#F2F2F2")
        val highlight = if (fox) Color.parseColor("#A8F1D7") else Color.WHITE
        val edge = if (fox) Color.parseColor("#126E69") else Color.parseColor("#858585")
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x - radius * .30f, cy - radius * .38f, radius * 1.25f,
                intArrayOf(highlight, base, edge),
                floatArrayOf(0f, .58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(x, cy, radius, face)
        strokeP.color = edge
        strokeP.style = Paint.Style.STROKE
        strokeP.strokeWidth = maxOf(1f, radius * .10f)
        canvas.drawCircle(x, cy, radius * .91f, strokeP)
        strokeP.strokeWidth = maxOf(1f, radius * .04f)
        strokeP.color = if (fox) Color.parseColor("#D7FFF0") else Color.parseColor("#C7C7C7")
        canvas.drawCircle(x, cy, radius * .72f, strokeP)
        strokeP.style = Paint.Style.FILL
        val originalTextSize = fillP.textSize
        val originalFakeBold = fillP.isFakeBoldText
        fillP.color = if (fox) Color.parseColor("#083F43") else Color.parseColor("#333333")
        fillP.textAlign = Paint.Align.CENTER
        fillP.textSize = radius * 1.02f
        fillP.isFakeBoldText = true
        val metrics = fillP.fontMetrics
        canvas.drawText(if (fox) "F" else "G", x, cy - (metrics.ascent + metrics.descent) / 2f, fillP)
        fillP.textAlign = Paint.Align.LEFT
        fillP.textSize = originalTextSize
        fillP.isFakeBoldText = originalFakeBold
    }

    private fun drawShogiPiece(canvas: Canvas, piece: ShogiPiece, x: Float, baseY: Float) {
        strokeP.textSize = fillP.textSize
        strokeP.strokeWidth = strokeP.textSize * 0.04f
        strokeP.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#555555")
        else Color.parseColor("#CCCCCC")
        fillP.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDE7")
        else Color.parseColor("#8A8A8A")
        canvas.drawText(piece.symbol(), x, baseY, strokeP)
        canvas.drawText(piece.symbol(), x, baseY, fillP)
    }

    /**
     * Draws a Morabaraba piece as a circle matching MorabaraBoardView colors:
     *   WHITE → near-white (#F5F5F5) with light rim
     *   BLACK → near-black (#212121) with dark rim
     */
    private fun drawMorabaraPiece(canvas: Canvas, piece: Piece, cx: Float, cy: Float, r: Float) {
        circleP.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(cx + 1f, cy + 1.5f, r, circleP)
        circleP.style = Paint.Style.FILL
        circleP.color = if (piece.color == PieceColor.WHITE)
            Color.parseColor("#F5F5F5") else Color.parseColor("#212121")
        canvas.drawCircle(cx, cy, r, circleP)
        circleP.style = Paint.Style.STROKE
        circleP.strokeWidth = r * 0.12f
        circleP.color = if (piece.color == PieceColor.WHITE)
            Color.parseColor("#AAAAAA") else Color.parseColor("#555555")
        canvas.drawCircle(cx, cy, r * 0.88f, circleP)
        circleP.style = Paint.Style.FILL
    }

    /**
     * Draws a checkers piece as a small circle.
     * Each piece uses its own color — WHITE pieces appear light, BLACK pieces appear dark.
     */
    private fun drawCheckersPiece(canvas: Canvas, piece: CheckersPiece, cx: Float, cy: Float, r: Float) {
        // Shadow
        circleP.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(cx + 1f, cy + 1.5f, r, circleP)
        // Fill — white pieces are light, black pieces are dark
        circleP.style = Paint.Style.FILL
        circleP.color = if (piece.color == PieceColor.WHITE)
            Color.parseColor("#F0F0F0") else Color.parseColor("#2E2E2E")
        canvas.drawCircle(cx, cy, r, circleP)
        // Rim
        circleP.style = Paint.Style.STROKE
        circleP.strokeWidth = r * 0.12f
        circleP.color = if (piece.color == PieceColor.WHITE)
            Color.parseColor("#AAAAAA") else Color.parseColor("#888888")
        canvas.drawCircle(cx, cy, r * 0.88f, circleP)
        circleP.style = Paint.Style.FILL
        // Use the same crown mark as the board for captured kings.
        if (piece.isKing) {
            kingP.color = if (piece.color == PieceColor.WHITE)
                Color.parseColor("#7FC8F8") else Color.parseColor("#EF5350")
            kingP.textSize = r * 1.05f
            val metrics = kingP.fontMetrics
            canvas.drawText(
                CheckersPiece.KING_SYMBOL,
                cx,
                cy - (metrics.ascent + metrics.descent) / 2f,
                kingP,
            )
        }
    }
}
