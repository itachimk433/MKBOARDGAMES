package com.mkdev.nexboard.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.mkdev.nexboard.engine.Piece
import com.mkdev.nexboard.engine.PieceColor
import com.mkdev.nexboard.games.chess.ChessPiece
import com.mkdev.nexboard.games.checkers.CheckersPiece

/**
 * A horizontal strip that renders a list of captured pieces left-to-right.
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

    // Two-pass text paints — reused for every piece
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; style = Paint.Style.STROKE
    }
    private val fillP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; style = Paint.Style.FILL
    }
    // For checkers: circle-based rendering
    private val circleP = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pieces: List<Piece> = emptyList()
    private var label: String = ""
    var dividerOnTop: Boolean = false

    fun update(newPieces: List<Piece>) {
        pieces = newPieces; invalidate()
    }

    /** Optionally set a label shown at the far-right of the strip (e.g. "Black's captures"). */
    fun setLabel(text: String) {
        label = text; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgP)
        if (dividerOnTop) canvas.drawRect(0f, 0f, w, dp, divP)
        else              canvas.drawRect(0f, h - dp, w, h, divP)

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
                else             -> drawMorabaraPiece(canvas, p, x + sz * 0.5f, h / 2f, sz * 0.36f)
            }
            x += gap
        }
    }

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
        // King crown dot
        if (piece.isKing) {
            circleP.color = if (piece.color == PieceColor.WHITE)
                Color.parseColor("#7FC8F8") else Color.parseColor("#EF5350")
            canvas.drawCircle(cx, cy, r * 0.28f, circleP)
        }
    }
}
