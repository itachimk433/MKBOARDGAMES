package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

internal fun isChessStyledLabel(label: String): Boolean =
    label.replace(" ", "").equals("CHESS", ignoreCase = true)

internal fun drawChessAtmosphere(canvas: Canvas, width: Float, height: Float, unit: Float, rounded: Boolean) {
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width * 0.9f,
            height,
            intArrayOf(
                Color.parseColor("#112C68"),
                Color.parseColor("#173C78"),
                Color.parseColor("#102951"),
                Color.parseColor("#061321"),
            ),
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    if (rounded) {
        canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, background)
    } else {
        canvas.drawRect(0f, 0f, width, height, background)
    }

    drawChessGlow(canvas, width * 0.12f, height * 0.18f, min(width, height) * 0.58f, unit, Color.rgb(53, 137, 220))
    drawChessGlow(canvas, width * 0.9f, height * 0.64f, min(width, height) * 0.5f, unit, Color.rgb(22, 194, 190))
    drawChessGlow(canvas, width * 0.46f, height * 1.02f, min(width, height) * 0.68f, unit, Color.rgb(71, 37, 134))

    val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            height * 0.48f,
            width,
            height * 0.58f,
            intArrayOf(
                Color.argb(0, 104, 194, 255),
                Color.argb(54, 77, 158, 232),
                Color.argb(0, 104, 194, 255),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, height * 0.4f, width, height * 0.65f, horizon)

    val stars = Paint(Paint.ANTI_ALIAS_FLAG)
    for (index in 0 until 62) {
        val x = ((index * 83 + 37) % 1000) / 1000f * width
        val y = ((index * 47 + 23) % 920) / 1000f * height
        val radius = (0.55f + (index % 4) * 0.45f) * unit
        stars.color = Color.argb(70 + (index % 5) * 28, 220, 241, 255)
        canvas.drawCircle(x, y, radius, stars)
        if (index % 11 == 0) {
            stars.color = Color.argb(130, 178, 224, 255)
            canvas.drawCircle(x, y, radius * 2.4f, stars)
        }
    }
    stars.color = Color.argb(34, 88, 207, 220)
    canvas.drawCircle(width * 0.08f, height * 0.72f, min(width, height) * 0.18f, stars)
    stars.color = Color.argb(25, 150, 109, 226)
    canvas.drawCircle(width * 0.88f, height * 0.3f, min(width, height) * 0.2f, stars)
}

private fun drawChessGlow(canvas: Canvas, x: Float, y: Float, radius: Float, unit: Float, color: Int) {
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                Color.argb(88, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(x, y, radius, glow)
}

internal fun drawChessWoodButton(canvas: Canvas, rect: RectF, pressed: Boolean, unit: Float) {
    val offset = if (pressed) 2f * unit else 0f
    val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
    val radius = drawn.height() * 0.2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            drawn.top,
            0f,
            drawn.bottom,
            intArrayOf(
                Color.parseColor("#F7D99B"),
                Color.parseColor("#C8894C"),
                Color.parseColor("#85502D"),
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        setShadowLayer(
            if (pressed) 1f * unit else 4f * unit,
            0f,
            if (pressed) 1f * unit else 3f * unit,
            Color.argb(170, 25, 9, 5),
        )
    }
    canvas.drawRoundRect(drawn, radius, radius, fill)
    fill.clearShadowLayer()

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * unit
        color = Color.parseColor("#7B4025")
    }
    canvas.drawRoundRect(drawn, radius, radius, border)
    border.strokeWidth = unit
    border.color = Color.argb(180, 255, 246, 220)
    val inner = RectF(
        drawn.left + 3f * unit,
        drawn.top + 3f * unit,
        drawn.right - 3f * unit,
        drawn.bottom - 3f * unit,
    )
    canvas.drawRoundRect(inner, radius * 0.82f, radius * 0.82f, border)
}