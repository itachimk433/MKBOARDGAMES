package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor

/**
 * Compact Yoté status strip. It keeps each player's reserve visible while
 * showing the actual stones that player has captured.
 */
class YotePieceStripView(
    context: Context,
    private val playerColor: PieceColor,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var reserveCount = 12
    private var capturedPieces: List<Piece> = emptyList()

    fun update(reserveCount: Int, capturedPieces: List<Piece>) {
        this.reserveCount = reserveCount
        this.capturedPieces = capturedPieces
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(170, 7, 21, 34))

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
        }
        canvas.drawRect(0f, h - density, w, h, divider)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10f * density
        textPaint.color = if (playerColor == PieceColor.WHITE) {
            Color.parseColor("#FFF6DE")
        } else {
            Color.parseColor("#B9D0D6")
        }
        val player = if (playerColor == PieceColor.WHITE) "WHITE" else "BLACK"
        canvas.drawText(
            "$player  ·  $reserveCount OUTSIDE",
            10f * density,
            h * 0.66f,
            textPaint,
        )

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 9f * density
        textPaint.color = Color.argb(190, 229, 240, 240)
        canvas.drawText(
            "CAPTURED ${capturedPieces.size}",
            w - 10f * density,
            h * 0.66f,
            textPaint,
        )

        val radius = h * 0.19f
        val gap = radius * 2.25f
        val capturedLabelWidth = textPaint.measureText("CAPTURED ${capturedPieces.size}")
        var x = w - capturedLabelWidth - 20f * density - radius
        for (piece in capturedPieces.asReversed()) {
            if (x - radius < w * 0.48f) break
            drawStone(canvas, x, h * 0.43f, radius, piece.color)
            x -= gap
        }
    }

    private fun drawStone(canvas: Canvas, x: Float, y: Float, radius: Float, color: PieceColor) {
        stonePaint.shader = RadialGradient(
            x - radius * 0.3f,
            y - radius * 0.35f,
            radius * 1.35f,
            if (color == PieceColor.WHITE) {
                intArrayOf(
                    Color.parseColor("#FFF8E8"),
                    Color.parseColor("#D8B77A"),
                    Color.parseColor("#8B542F"),
                )
            } else {
                intArrayOf(
                    Color.parseColor("#657C83"),
                    Color.parseColor("#1D3039"),
                    Color.parseColor("#050A0E"),
                )
            },
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, stonePaint)
        stonePaint.shader = null
        rimPaint.color = if (color == PieceColor.WHITE) {
            Color.argb(190, 255, 245, 208)
        } else {
            Color.argb(190, 117, 166, 174)
        }
        rimPaint.strokeWidth = maxOf(1f, radius * 0.12f)
        canvas.drawCircle(x, y, radius, rimPaint)
    }
}