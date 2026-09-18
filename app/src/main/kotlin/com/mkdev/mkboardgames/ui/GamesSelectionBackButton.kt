package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * The shared back button used by the games-selection screen and every
 * full-screen game setup surface.
 */
internal object GamesSelectionBackButton {
    fun draw(
        canvas: Canvas,
        rect: RectF,
        unit: Float,
        lightMode: Boolean = false,
    ) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lightMode) {
                Color.WHITE
            } else {
                Color.argb(210, 34, 18, 13)
            }
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * unit
            color = Color.parseColor(if (lightMode) "#1976A8" else "#D3A05F")
        }
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (lightMode) "#1976A8" else "#F7D99B")
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * unit
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        canvas.drawRoundRect(rect, 10f * unit, 10f * unit, background)
        canvas.drawRoundRect(rect, 10f * unit, 10f * unit, edge)
        val centerY = rect.centerY()
        val tipX = rect.left + 9f * unit
        canvas.drawLine(tipX, centerY, rect.right - 8f * unit, centerY, arrow)
        canvas.drawLine(tipX, centerY, tipX + 9f * unit, centerY - 8f * unit, arrow)
        canvas.drawLine(tipX, centerY, tipX + 9f * unit, centerY + 8f * unit, arrow)
    }
}