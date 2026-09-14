package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Shared controls for the Snakes & Ladders teal surfaces.
 *
 * The board picker is the visual source of truth for this game: dark teal
 * panels, quiet borders, and a brighter border only while a control is held.
 * Keeping the renderer here prevents the shared chess-style screens from
 * changing when this game is restyled.
 */
internal object SnakesLaddersTheme {
    const val SURFACE = "#16353B"
    const val PRESSED_SURFACE = "#21454A"
    const val BORDER = "#2C5960"
    val TEXT = Color.WHITE
    const val DETAIL = "#BFD2D4"
}

internal fun drawSnakesLaddersButton(
    canvas: Canvas,
    rect: RectF,
    pressed: Boolean,
    unit: Float,
    accent: Int = Color.parseColor("#8EC7B9"),
) {
    val offset = if (pressed) 2f * unit else 0f
    val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
    val radius = 8f * unit

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(
            if (pressed) SnakesLaddersTheme.PRESSED_SURFACE else SnakesLaddersTheme.SURFACE,
        )
        setShadowLayer(
            if (pressed) 1f * unit else 4f * unit,
            0f,
            if (pressed) 1f * unit else 3f * unit,
            Color.argb(125, 0, 10, 15),
        )
    }
    canvas.drawRoundRect(drawn, radius, radius, fill)
    fill.clearShadowLayer()

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = if (pressed) 1.5f * unit else unit
        color = if (pressed) accent else Color.parseColor(SnakesLaddersTheme.BORDER)
    }
    canvas.drawRoundRect(drawn, radius, radius, border)
}