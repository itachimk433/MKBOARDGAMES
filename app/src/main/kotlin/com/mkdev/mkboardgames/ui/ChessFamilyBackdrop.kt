package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class ChessFamilyBackdrop(private val unit: Float) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * unit
    }
    private val starsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaths = Array(3) { Path() }
    private val waveEdgePaths = Array(3) { Path() }

    private val waveBands = arrayOf(
        WaveBand(0.28f, 0.045f, Color.rgb(64, 167, 218)),
        WaveBand(0.49f, 0.06f, Color.rgb(74, 117, 225)),
        WaveBand(0.72f, 0.052f, Color.rgb(126, 83, 213)),
    )

    private val palettes = arrayOf(
        intArrayOf(
            Color.parseColor("#112C68"),
            Color.parseColor("#173C78"),
            Color.parseColor("#102951"),
            Color.parseColor("#061321"),
        ),
        intArrayOf(
            Color.parseColor("#182D68"),
            Color.parseColor("#303A7C"),
            Color.parseColor("#0C4C62"),
            Color.parseColor("#081A2F"),
        ),
        intArrayOf(
            Color.parseColor("#142F52"),
            Color.parseColor("#145064"),
            Color.parseColor("#113D50"),
            Color.parseColor("#06182A"),
        ),
        intArrayOf(
            Color.parseColor("#291F62"),
            Color.parseColor("#432E78"),
            Color.parseColor("#143F63"),
            Color.parseColor("#07162B"),
        ),
    )

    private data class WaveBand(
        val heightRatio: Float,
        val amplitudeRatio: Float,
        val color: Int,
    )

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        animationPhase: Float,
        rounded: Boolean,
    ) {
        val backgroundProgress = (animationPhase * 2f) % 1f
        val phase = backgroundProgress * (2f * PI.toFloat())
        val paletteSlot = backgroundProgress * palettes.size
        val paletteIndex = paletteSlot.toInt().coerceIn(0, palettes.lastIndex)
        val paletteProgress = smoothStep(paletteSlot - paletteIndex)
        val paletteStart = palettes[paletteIndex]
        val paletteEnd = palettes[(paletteIndex + 1) % palettes.size]
        val palette = IntArray(paletteStart.size) { index ->
            blendColor(paletteStart[index], paletteEnd[index], paletteProgress)
        }

        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width * 0.9f,
            height,
            palette,
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        if (rounded) {
            canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, backgroundPaint)
        } else {
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)
        }
        backgroundPaint.shader = null

        drawWaves(canvas, width, height, phase)
        drawGlow(
            canvas,
            width * (0.12f + 0.055f * sin(phase * 0.72f).toFloat()),
            height * (0.18f + 0.045f * cos(phase * 0.54f).toFloat()),
            min(width, height) * 0.58f,
            blendColor(
                Color.rgb(53, 137, 220),
                Color.rgb(115, 89, 226),
                (sin(phase * 0.25f) + 1f) / 2f,
            ),
        )
        drawGlow(
            canvas,
            width * (0.9f + 0.05f * cos(phase * 0.48f).toFloat()),
            height * (0.64f + 0.06f * sin(phase * 0.66f).toFloat()),
            min(width, height) * 0.5f,
            blendColor(
                Color.rgb(22, 194, 190),
                Color.rgb(74, 140, 235),
                (sin(phase * 0.32f + 1.4f) + 1f) / 2f,
            ),
        )
        drawGlow(
            canvas,
            width * (0.46f + 0.08f * sin(phase * 0.4f + 2f).toFloat()),
            height * (1.02f + 0.045f * cos(phase * 0.58f).toFloat()),
            min(width, height) * 0.68f,
            blendColor(
                Color.rgb(71, 37, 134),
                Color.rgb(19, 120, 143),
                (sin(phase * 0.28f + 2f) + 1f) / 2f,
            ),
        )

        for (index in 0 until 62) {
            val baseX = ((index * 83 + 37) % 1000) / 1000f * width
            val baseY = ((index * 47 + 23) % 920) / 1000f * height
            val x = baseX +
                sin(phase * (0.18f + (index % 3) * 0.07f) + index).toFloat() * 7f * unit
            val y = baseY +
                cos(phase * (0.16f + (index % 4) * 0.05f) + index * 0.7f).toFloat() * 5f * unit
            val radius = (0.55f + (index % 4) * 0.45f) * unit
            val twinkle =
                ((sin(phase * (0.7f + (index % 4) * 0.12f) + index) + 1f) * 0.5f).toFloat()
            starsPaint.color = Color.argb(
                58 + ((index % 5) * 22 * twinkle).toInt(),
                220,
                241,
                255,
            )
            canvas.drawCircle(x, y, radius, starsPaint)
            if (index % 11 == 0) {
                starsPaint.color = Color.argb(
                    100 + (30f * twinkle).toInt(),
                    178,
                    224,
                    255,
                )
                canvas.drawCircle(x, y, radius * (2.1f + 0.5f * twinkle), starsPaint)
            }
        }
        starsPaint.color = Color.argb(34, 88, 207, 220)
        canvas.drawCircle(
            width * (0.08f + 0.025f * sin(phase * 0.4f).toFloat()),
            height * 0.72f,
            min(width, height) * 0.18f,
            starsPaint,
        )
        starsPaint.color = Color.argb(25, 150, 109, 226)
        canvas.drawCircle(
            width * 0.88f,
            height * (0.3f + 0.03f * cos(phase * 0.52f).toFloat()),
            min(width, height) * 0.2f,
            starsPaint,
        )
    }

    private fun drawWaves(canvas: Canvas, width: Float, height: Float, phase: Float) {
        val margin = width * 0.14f
        val segmentCount = 64
        waveBands.forEachIndexed { bandIndex, band ->
            val path = wavePaths[bandIndex]
            path.reset()
            val bandPhase = phase * (0.46f + bandIndex * 0.11f) + bandIndex * 1.8f
            val baseY = height * band.heightRatio
            val amplitude = height * band.amplitudeRatio
            path.moveTo(-margin, height)
            path.lineTo(-margin, baseY)
            for (step in 0..segmentCount) {
                val progress = step / segmentCount.toFloat()
                val x = -margin + (width + margin * 2f) * progress
                val wave = sin(progress * (2.15f * PI.toFloat()) + bandPhase).toFloat() * amplitude
                val secondary =
                    cos(progress * (4.5f * PI.toFloat()) - bandPhase * 0.72f).toFloat() *
                        amplitude * 0.28f
                path.lineTo(x, baseY + wave + secondary)
            }
            path.lineTo(width + margin, height)
            path.close()
            wavePaint.color = Color.argb(
                18 + bandIndex * 5,
                Color.red(band.color),
                Color.green(band.color),
                Color.blue(band.color),
            )
            canvas.drawPath(path, wavePaint)

            val edge = waveEdgePaths[bandIndex]
            edge.reset()
            edge.moveTo(-margin, baseY)
            for (step in 0..segmentCount) {
                val progress = step / segmentCount.toFloat()
                val x = -margin + (width + margin * 2f) * progress
                val wave = sin(progress * (2.15f * PI.toFloat()) + bandPhase).toFloat() * amplitude
                val secondary =
                    cos(progress * (4.5f * PI.toFloat()) - bandPhase * 0.72f).toFloat() *
                        amplitude * 0.28f
                edge.lineTo(x, baseY + wave + secondary)
            }
            waveEdgePaint.color = Color.argb(
                34 + bandIndex * 8,
                Color.red(band.color),
                Color.green(band.color),
                Color.blue(band.color),
            )
            canvas.drawPath(edge, waveEdgePaint)
        }

        wavePaint.shader = LinearGradient(
            0f,
            height * 0.42f,
            width,
            height * 0.58f,
            intArrayOf(
                Color.argb(0, 118, 213, 255),
                Color.argb(42, 96, 168, 239),
                Color.argb(0, 166, 119, 238),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, height * 0.37f, width, height * 0.64f, wavePaint)
        wavePaint.shader = null
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        backgroundPaint.shader = RadialGradient(
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
        canvas.drawCircle(x, y, radius, backgroundPaint)
        backgroundPaint.shader = null
    }

    private fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    private fun blendColor(start: Int, end: Int, fraction: Float): Int {
        val amount = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * amount).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * amount).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * amount).toInt(),
        )
    }
}