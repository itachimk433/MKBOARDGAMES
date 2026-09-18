package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun isChessStyledLabel(label: String): Boolean =
    label.replace(" ", "").equals("CHESS", ignoreCase = true)

internal fun isAmazonsStyledLabel(label: String): Boolean =
    label.replace(" ", "").equals("AMAZONS", ignoreCase = true)

internal fun isDraughtsStyledLabel(label: String): Boolean =
    label.replace(" ", "").uppercase() in setOf("DRAUGHTS", "INTLDRAUGHTS")

internal fun isOthelloStyledLabel(label: String): Boolean =
    label.replace(" ", "").uppercase() == "OTHELLO"

internal fun isMorabarabaStyledLabel(label: String): Boolean =
    label.replace(" ", "").uppercase() == "MORABARABA"

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

internal fun drawChessWoodButton(
    canvas: Canvas,
    rect: RectF,
    pressed: Boolean,
    unit: Float,
    maxCornerRadius: Float? = null,
) {
    val offset = if (pressed) 2f * unit else 0f
    val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
    val radius = (drawn.height() * 0.2f).let { defaultRadius ->
        maxCornerRadius?.let { min(defaultRadius, it) } ?: defaultRadius
    }
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

private data class DraughtsPalette(
    val top: Int,
    val middle: Int,
    val lower: Int,
    val bottom: Int,
)

private data class DraughtsFloatSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val horizontalRange: Float,
    val verticalRange: Float,
    val speed: Float,
    val phase: Float,
    val dark: Boolean,
)

private val draughtsPalettes = arrayOf(
    DraughtsPalette(
        Color.parseColor("#183B58"),
        Color.parseColor("#254A63"),
        Color.parseColor("#242D50"),
        Color.parseColor("#081728"),
    ),
    DraughtsPalette(
        Color.parseColor("#3B315F"),
        Color.parseColor("#31536A"),
        Color.parseColor("#173C4D"),
        Color.parseColor("#071A2B"),
    ),
    DraughtsPalette(
        Color.parseColor("#49314F"),
        Color.parseColor("#315066"),
        Color.parseColor("#124554"),
        Color.parseColor("#081A27"),
    ),
)

private val draughtsFloatSeeds = listOf(
    DraughtsFloatSeed(0.12f, 0.18f, 0.024f, 0.055f, 0.075f, 0.82f, 0.3f, dark = false),
    DraughtsFloatSeed(0.84f, 0.16f, 0.021f, 0.07f, 0.06f, 0.64f, 1.8f, dark = true),
    DraughtsFloatSeed(0.88f, 0.46f, 0.026f, 0.075f, 0.085f, 0.58f, 3.4f, dark = false),
    DraughtsFloatSeed(0.14f, 0.64f, 0.022f, 0.06f, 0.08f, 0.74f, 4.7f, dark = true),
    DraughtsFloatSeed(0.78f, 0.77f, 0.029f, 0.075f, 0.065f, 0.49f, 5.5f, dark = false),
    DraughtsFloatSeed(0.30f, 0.88f, 0.020f, 0.065f, 0.05f, 0.68f, 2.6f, dark = true),
)

private data class OthelloFloatSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val horizontalRange: Float,
    val verticalRange: Float,
    val speed: Float,
    val phase: Float,
    val dark: Boolean,
)

private val othelloFloatSeeds = listOf(
    OthelloFloatSeed(0.12f, 0.17f, 0.025f, 0.06f, 0.07f, 0.34f, 0.4f, dark = false),
    OthelloFloatSeed(0.84f, 0.19f, 0.022f, 0.075f, 0.06f, 0.27f, 1.8f, dark = true),
    OthelloFloatSeed(0.88f, 0.47f, 0.027f, 0.08f, 0.08f, 0.22f, 3.4f, dark = false),
    OthelloFloatSeed(0.14f, 0.65f, 0.023f, 0.065f, 0.08f, 0.31f, 4.7f, dark = true),
    OthelloFloatSeed(0.78f, 0.78f, 0.029f, 0.08f, 0.065f, 0.19f, 5.5f, dark = false),
    OthelloFloatSeed(0.30f, 0.87f, 0.021f, 0.07f, 0.055f, 0.25f, 2.6f, dark = true),
)

private data class MorabarabaFloatSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val horizontalRange: Float,
    val verticalRange: Float,
    val speed: Float,
    val phase: Float,
    val dark: Boolean,
)

private val morabarabaFloatSeeds = listOf(
    MorabarabaFloatSeed(0.12f, 0.17f, 0.025f, 0.055f, 0.065f, 0.18f, 0.4f, dark = false),
    MorabarabaFloatSeed(0.84f, 0.19f, 0.022f, 0.068f, 0.055f, 0.14f, 1.8f, dark = true),
    MorabarabaFloatSeed(0.88f, 0.47f, 0.027f, 0.072f, 0.072f, 0.11f, 3.4f, dark = false),
    MorabarabaFloatSeed(0.14f, 0.65f, 0.023f, 0.058f, 0.068f, 0.16f, 4.7f, dark = true),
    MorabarabaFloatSeed(0.78f, 0.78f, 0.029f, 0.074f, 0.058f, 0.095f, 5.5f, dark = false),
    MorabarabaFloatSeed(0.30f, 0.87f, 0.021f, 0.064f, 0.048f, 0.13f, 2.6f, dark = true),
)

private val draughtsAtmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val draughtsPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val draughtsPieceEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
}
private val othelloPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val othelloPieceEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
}
private val morabarabaPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val morabarabaPieceEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
}

internal fun drawDraughtsAtmosphere(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    rounded: Boolean,
    phase: Float,
) {
    val cycle = ((phase % 1f) + 1f) % 1f
    val paletteSlot = cycle * draughtsPalettes.size
    val paletteIndex = paletteSlot.toInt().coerceIn(0, draughtsPalettes.lastIndex)
    val paletteProgress = smoothDraughtsStep(paletteSlot - paletteIndex)
    val start = draughtsPalettes[paletteIndex]
    val end = draughtsPalettes[(paletteIndex + 1) % draughtsPalettes.size]
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width * 0.92f,
            height,
            intArrayOf(
                blendDraughtsColor(start.top, end.top, paletteProgress),
                blendDraughtsColor(start.middle, end.middle, paletteProgress),
                blendDraughtsColor(start.lower, end.lower, paletteProgress),
                blendDraughtsColor(start.bottom, end.bottom, paletteProgress),
            ),
            floatArrayOf(0f, 0.31f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    if (rounded) {
        canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, background)
    } else {
        canvas.drawRect(0f, 0f, width, height, background)
    }

    val radians = cycle * (2f * PI.toFloat())
    drawDraughtsGlow(
        canvas,
        width * (0.12f + 0.05f * sin(radians * 0.7f).toFloat()),
        height * (0.18f + 0.04f * cos(radians * 0.5f).toFloat()),
        min(width, height) * 0.58f,
        blendDraughtsColor(Color.rgb(53, 157, 208), Color.rgb(137, 91, 192), (sin(radians * 0.26f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.88f + 0.045f * cos(radians * 0.42f).toFloat()),
        height * (0.63f + 0.055f * sin(radians * 0.63f).toFloat()),
        min(width, height) * 0.52f,
        blendDraughtsColor(Color.rgb(28, 173, 169), Color.rgb(83, 124, 219), (sin(radians * 0.31f + 1.2f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.46f + 0.07f * sin(radians * 0.36f + 2f).toFloat()),
        height * (1.02f + 0.04f * cos(radians * 0.54f).toFloat()),
        min(width, height) * 0.7f,
        Color.rgb(93, 55, 147),
    )

    drawDraughtsWaves(canvas, width, height, radians)

    val stars = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(48) { index ->
        val baseX = ((index * 97 + 19) % 1000) / 1000f * width
        val baseY = ((index * 43 + 31) % 940) / 1000f * height
        val x = baseX + sin(radians * (0.15f + (index % 3) * 0.06f) + index).toFloat() * 6f * unit
        val y = baseY + cos(radians * (0.14f + (index % 4) * 0.04f) + index).toFloat() * 5f * unit
        val twinkle = ((sin(radians * (0.55f + (index % 4) * 0.1f) + index) + 1f) * 0.5f).toFloat()
        stars.color = Color.argb(42 + (index % 4) * 18 + (twinkle * 18f).toInt(), 224, 242, 238)
        canvas.drawCircle(x, y, (0.55f + (index % 3) * 0.42f) * unit, stars)
    }

    drawDraughtsFloatingPieces(canvas, width, height, unit, radians, 0.82f)
}

internal fun drawOthelloAtmosphere(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    rounded: Boolean,
    phase: Float,
) {
    // Othello keeps the same palette and wave language as Draughts, but moves
    // through it more gently so the discs feel like they are floating.
    val cycle = (((phase * 0.62f) % 1f) + 1f) % 1f
    val paletteSlot = cycle * draughtsPalettes.size
    val paletteIndex = paletteSlot.toInt().coerceIn(0, draughtsPalettes.lastIndex)
    val paletteProgress = smoothDraughtsStep(paletteSlot - paletteIndex)
    val start = draughtsPalettes[paletteIndex]
    val end = draughtsPalettes[(paletteIndex + 1) % draughtsPalettes.size]
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width * 0.92f,
            height,
            intArrayOf(
                blendDraughtsColor(start.top, end.top, paletteProgress),
                blendDraughtsColor(start.middle, end.middle, paletteProgress),
                blendDraughtsColor(start.lower, end.lower, paletteProgress),
                blendDraughtsColor(start.bottom, end.bottom, paletteProgress),
            ),
            floatArrayOf(0f, 0.31f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    if (rounded) {
        canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, background)
    } else {
        canvas.drawRect(0f, 0f, width, height, background)
    }

    val radians = cycle * (2f * PI.toFloat())
    drawDraughtsGlow(
        canvas,
        width * (0.12f + 0.035f * sin(radians * 0.45f).toFloat()),
        height * (0.18f + 0.028f * cos(radians * 0.34f).toFloat()),
        min(width, height) * 0.58f,
        blendDraughtsColor(Color.rgb(53, 157, 208), Color.rgb(137, 91, 192), (sin(radians * 0.18f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.88f + 0.032f * cos(radians * 0.27f).toFloat()),
        height * (0.63f + 0.038f * sin(radians * 0.39f).toFloat()),
        min(width, height) * 0.52f,
        blendDraughtsColor(Color.rgb(28, 173, 169), Color.rgb(83, 124, 219), (sin(radians * 0.22f + 1.2f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.46f + 0.045f * sin(radians * 0.25f + 2f).toFloat()),
        height * (1.02f + 0.028f * cos(radians * 0.37f).toFloat()),
        min(width, height) * 0.7f,
        Color.rgb(93, 55, 147),
    )

    drawDraughtsWaves(canvas, width, height, radians * 0.56f)

    val stars = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(48) { index ->
        val baseX = ((index * 97 + 19) % 1000) / 1000f * width
        val baseY = ((index * 43 + 31) % 940) / 1000f * height
        val x = baseX + sin(radians * (0.09f + (index % 3) * 0.035f) + index).toFloat() * 4f * unit
        val y = baseY + cos(radians * (0.085f + (index % 4) * 0.025f) + index).toFloat() * 3.5f * unit
        val twinkle = ((sin(radians * (0.32f + (index % 4) * 0.06f) + index) + 1f) * 0.5f).toFloat()
        stars.color = Color.argb(42 + (index % 4) * 18 + (twinkle * 14f).toInt(), 224, 242, 238)
        canvas.drawCircle(x, y, (0.55f + (index % 3) * 0.42f) * unit, stars)
    }

    drawOthelloFloatingPieces(canvas, width, height, unit, radians, 0.82f)
}

internal fun drawMorabarabaAtmosphere(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    rounded: Boolean,
    phase: Float,
) {
    // Morabaraba uses the same established game palette, but breathes more
    // slowly so the cow pieces feel suspended rather than swept across the
    // screen.
    val cycle = (((phase * 0.38f) % 1f) + 1f) % 1f
    val paletteSlot = cycle * draughtsPalettes.size
    val paletteIndex = paletteSlot.toInt().coerceIn(0, draughtsPalettes.lastIndex)
    val paletteProgress = smoothDraughtsStep(paletteSlot - paletteIndex)
    val start = draughtsPalettes[paletteIndex]
    val end = draughtsPalettes[(paletteIndex + 1) % draughtsPalettes.size]
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width * 0.92f,
            height,
            intArrayOf(
                blendDraughtsColor(start.top, end.top, paletteProgress),
                blendDraughtsColor(start.middle, end.middle, paletteProgress),
                blendDraughtsColor(start.lower, end.lower, paletteProgress),
                blendDraughtsColor(start.bottom, end.bottom, paletteProgress),
            ),
            floatArrayOf(0f, 0.31f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    if (rounded) {
        canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, background)
    } else {
        canvas.drawRect(0f, 0f, width, height, background)
    }

    val radians = cycle * (2f * PI.toFloat())
    drawDraughtsGlow(
        canvas,
        width * (0.12f + 0.025f * sin(radians * 0.34f).toFloat()),
        height * (0.18f + 0.022f * cos(radians * 0.26f).toFloat()),
        min(width, height) * 0.58f,
        blendDraughtsColor(Color.rgb(53, 157, 208), Color.rgb(137, 91, 192), (sin(radians * 0.14f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.88f + 0.022f * cos(radians * 0.22f).toFloat()),
        height * (0.63f + 0.028f * sin(radians * 0.31f).toFloat()),
        min(width, height) * 0.52f,
        blendDraughtsColor(Color.rgb(28, 173, 169), Color.rgb(83, 124, 219), (sin(radians * 0.17f + 1.2f) + 1f) / 2f),
    )
    drawDraughtsGlow(
        canvas,
        width * (0.46f + 0.032f * sin(radians * 0.20f + 2f).toFloat()),
        height * (1.02f + 0.020f * cos(radians * 0.28f).toFloat()),
        min(width, height) * 0.7f,
        Color.rgb(93, 55, 147),
    )

    drawDraughtsWaves(canvas, width, height, radians * 0.34f)

    val stars = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(48) { index ->
        val baseX = ((index * 97 + 19) % 1000) / 1000f * width
        val baseY = ((index * 43 + 31) % 940) / 1000f * height
        val x = baseX + sin(radians * (0.055f + (index % 3) * 0.022f) + index).toFloat() * 3f * unit
        val y = baseY + cos(radians * (0.05f + (index % 4) * 0.016f) + index).toFloat() * 2.5f * unit
        val twinkle = ((sin(radians * (0.2f + (index % 4) * 0.035f) + index) + 1f) * 0.5f).toFloat()
        stars.color = Color.argb(42 + (index % 4) * 18 + (twinkle * 10f).toInt(), 224, 242, 238)
        canvas.drawCircle(x, y, (0.55f + (index % 3) * 0.42f) * unit, stars)
    }

    drawMorabarabaFloatingPieces(canvas, width, height, unit, radians, 0.82f)
}

private fun drawMorabarabaFloatingPieces(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    radians: Float,
    opacity: Float,
) {
    morabarabaFloatSeeds.forEach { seed ->
        val primary = radians * seed.speed + seed.phase
        val secondary = radians * (seed.speed * 0.54f + 0.04f) + seed.phase * 1.6f
        val x = width * (
            seed.x +
                sin(primary).toFloat() * seed.horizontalRange +
                cos(secondary).toFloat() * seed.horizontalRange * 0.28f
            )
        val y = height * (
            seed.y +
                cos(primary * 0.76f + seed.phase * 0.22f).toFloat() * seed.verticalRange +
                sin(secondary * 1.05f).toFloat() * seed.verticalRange * 0.28f
            )
        val radius = min(width, height) * seed.size *
            (0.98f + 0.025f * sin(primary * 0.62f).toFloat())
        val alpha = (if (seed.dark) 78f else 92f) * opacity
        val base = if (seed.dark) Color.rgb(50, 31, 39) else Color.rgb(218, 174, 103)
        val middle = if (seed.dark) Color.rgb(92, 59, 69) else Color.rgb(255, 223, 151)
        val edge = if (seed.dark) Color.rgb(137, 87, 95) else Color.rgb(255, 238, 184)

        morabarabaPiecePaint.shader = RadialGradient(
            x - radius * 0.28f,
            y - radius * 0.34f,
            radius * 1.25f,
            intArrayOf(
                Color.argb(alpha.toInt(), Color.red(middle), Color.green(middle), Color.blue(middle)),
                Color.argb(alpha.toInt(), Color.red(base), Color.green(base), Color.blue(base)),
                Color.argb(alpha.toInt(), Color.red(base), Color.green(base), Color.blue(base)),
            ),
            floatArrayOf(0f, 0.64f, 1f),
            Shader.TileMode.CLAMP,
        )
        morabarabaPiecePaint.setShadowLayer(
            radius * 0.38f,
            0f,
            radius * 0.14f,
            Color.argb((alpha * 0.72f).toInt(), 0, 0, 0),
        )
        canvas.drawCircle(x, y, radius, morabarabaPiecePaint)
        morabarabaPiecePaint.clearShadowLayer()
        morabarabaPiecePaint.shader = null

        morabarabaPieceEdgePaint.strokeWidth = maxOf(unit, radius * 0.11f)
        morabarabaPieceEdgePaint.color = Color.argb(
            alpha.toInt(),
            Color.red(edge),
            Color.green(edge),
            Color.blue(edge),
        )
        canvas.drawCircle(x, y, radius * 0.78f, morabarabaPieceEdgePaint)
        morabarabaPieceEdgePaint.color = Color.argb((alpha * 0.68f).toInt(), 255, 255, 255)
        canvas.drawCircle(x - radius * 0.24f, y - radius * 0.28f, radius * 0.16f, morabarabaPieceEdgePaint)
    }
}

private fun drawOthelloFloatingPieces(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    radians: Float,
    opacity: Float,
) {
    othelloFloatSeeds.forEach { seed ->
        val primary = radians * seed.speed + seed.phase
        val secondary = radians * (seed.speed * 0.58f + 0.08f) + seed.phase * 1.6f
        val x = width * (
            seed.x +
                sin(primary).toFloat() * seed.horizontalRange +
                cos(secondary).toFloat() * seed.horizontalRange * 0.34f
            )
        val y = height * (
            seed.y +
                cos(primary * 0.8f + seed.phase * 0.28f).toFloat() * seed.verticalRange +
                sin(secondary * 1.1f).toFloat() * seed.verticalRange * 0.34f
            )
        val radius = min(width, height) * seed.size *
            (0.97f + 0.04f * sin(primary * 0.72f).toFloat())
        val alpha = (if (seed.dark) 82f else 94f) * opacity
        val base = if (seed.dark) Color.rgb(27, 25, 33) else Color.rgb(225, 219, 197)
        val middle = if (seed.dark) Color.rgb(71, 57, 74) else Color.rgb(247, 237, 209)
        val edge = if (seed.dark) Color.rgb(120, 86, 91) else Color.rgb(255, 245, 219)

        othelloPiecePaint.shader = RadialGradient(
            x - radius * 0.28f,
            y - radius * 0.34f,
            radius * 1.25f,
            intArrayOf(
                Color.argb(alpha.toInt(), Color.red(middle), Color.green(middle), Color.blue(middle)),
                Color.argb(alpha.toInt(), Color.red(base), Color.green(base), Color.blue(base)),
                Color.argb(alpha.toInt(), Color.red(base), Color.green(base), Color.blue(base)),
            ),
            floatArrayOf(0f, 0.64f, 1f),
            Shader.TileMode.CLAMP,
        )
        othelloPiecePaint.setShadowLayer(radius * 0.38f, 0f, radius * 0.14f, Color.argb((alpha * 0.82f).toInt(), 0, 0, 0))
        canvas.drawCircle(x, y, radius, othelloPiecePaint)
        othelloPiecePaint.clearShadowLayer()
        othelloPiecePaint.shader = null

        othelloPieceEdgePaint.strokeWidth = maxOf(unit, radius * 0.11f)
        othelloPieceEdgePaint.color = Color.argb(alpha.toInt(), Color.red(edge), Color.green(edge), Color.blue(edge))
        canvas.drawCircle(x, y, radius * 0.78f, othelloPieceEdgePaint)
        othelloPieceEdgePaint.color = Color.argb((alpha * 0.72f).toInt(), 255, 255, 255)
        canvas.drawCircle(x - radius * 0.24f, y - radius * 0.28f, radius * 0.16f, othelloPieceEdgePaint)
    }
}

private fun drawDraughtsWaves(canvas: Canvas, width: Float, height: Float, radians: Float) {
    val waveColors = intArrayOf(
        Color.rgb(62, 171, 201),
        Color.rgb(92, 124, 213),
        Color.rgb(155, 91, 181),
    )
    val segmentCount = 56
    val margin = width * 0.15f
    waveColors.forEachIndexed { bandIndex, color ->
        val baseY = height * (0.3f + bandIndex * 0.2f)
        val amplitude = height * (0.04f + bandIndex * 0.012f)
        val wavePhase = radians * (0.42f + bandIndex * 0.1f) + bandIndex * 1.9f
        val path = Path()
        path.moveTo(-margin, height)
        path.lineTo(-margin, baseY)
        for (step in 0..segmentCount) {
            val progress = step / segmentCount.toFloat()
            val x = -margin + (width + margin * 2f) * progress
            val primary = sin(progress * (2.05f * PI.toFloat()) + wavePhase).toFloat() * amplitude
            val secondary = cos(progress * (4.3f * PI.toFloat()) - wavePhase * 0.7f).toFloat() * amplitude * 0.24f
            path.lineTo(x, baseY + primary + secondary)
        }
        path.lineTo(width + margin, height)
        path.close()
        draughtsAtmospherePaint.color = Color.argb(15 + bandIndex * 5, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawPath(path, draughtsAtmospherePaint)
    }
}

private fun drawDraughtsGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
    draughtsAtmospherePaint.shader = RadialGradient(
        x,
        y,
        radius,
        intArrayOf(
            Color.argb(76, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(x, y, radius, draughtsAtmospherePaint)
    draughtsAtmospherePaint.shader = null
}

private fun drawDraughtsFloatingPieces(
    canvas: Canvas,
    width: Float,
    height: Float,
    unit: Float,
    radians: Float,
    opacity: Float,
) {
    draughtsFloatSeeds.forEachIndexed { index, seed ->
        val primary = radians * seed.speed + seed.phase
        val secondary = radians * (seed.speed * 0.59f + 0.1f) + seed.phase * 1.6f
        val x = width * (
            seed.x +
                sin(primary).toFloat() * seed.horizontalRange +
                cos(secondary).toFloat() * seed.horizontalRange * 0.38f
            )
        val y = height * (
            seed.y +
                cos(primary * 0.82f + seed.phase * 0.3f).toFloat() * seed.verticalRange +
                sin(secondary * 1.15f).toFloat() * seed.verticalRange * 0.4f
            )
        val radius = min(width, height) * seed.size * (0.96f + 0.05f * sin(primary * 0.8f).toFloat())
        val alpha = (if (seed.dark) 76f else 96f) * opacity
        val baseColor = if (seed.dark) Color.rgb(50, 31, 39) else Color.rgb(218, 174, 103)
        val highlightColor = if (seed.dark) Color.rgb(105, 65, 72) else Color.rgb(255, 223, 151)

        draughtsPiecePaint.color = Color.argb(alpha.toInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        draughtsPiecePaint.setShadowLayer(radius * 0.38f, 0f, radius * 0.14f, Color.argb((alpha * 0.8f).toInt(), 0, 0, 0))
        canvas.drawCircle(x, y, radius, draughtsPiecePaint)
        draughtsPiecePaint.clearShadowLayer()

        draughtsPieceEdgePaint.strokeWidth = maxOf(unit, radius * 0.12f)
        draughtsPieceEdgePaint.color = Color.argb(alpha.toInt(), Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
        canvas.drawCircle(x, y, radius * 0.78f, draughtsPieceEdgePaint)
        draughtsPieceEdgePaint.color = Color.argb((alpha * 0.7f).toInt(), Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
        canvas.drawCircle(x - radius * 0.2f, y - radius * 0.24f, radius * 0.18f, draughtsPieceEdgePaint)
    }
}

private fun smoothDraughtsStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

private fun blendDraughtsColor(start: Int, end: Int, fraction: Float): Int {
    val amount = fraction.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(start) + (Color.red(end) - Color.red(start)) * amount).toInt(),
        (Color.green(start) + (Color.green(end) - Color.green(start)) * amount).toInt(),
        (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * amount).toInt(),
    )
}

internal fun drawDraughtsButton(canvas: Canvas, rect: RectF, pressed: Boolean, unit: Float) {
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
                Color.parseColor("#EFD19A"),
                Color.parseColor("#B7774A"),
                Color.parseColor("#6F3D32"),
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        setShadowLayer(
            if (pressed) 1f * unit else 4f * unit,
            0f,
            if (pressed) 1f * unit else 3f * unit,
            Color.argb(170, 22, 9, 16),
        )
    }
    canvas.drawRoundRect(drawn, radius, radius, fill)
    fill.clearShadowLayer()

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * unit
        color = Color.parseColor("#71322C")
    }
    canvas.drawRoundRect(drawn, radius, radius, border)
    border.strokeWidth = unit
    border.color = Color.argb(180, 255, 238, 204)
    val inner = RectF(
        drawn.left + 3f * unit,
        drawn.top + 3f * unit,
        drawn.right - 3f * unit,
        drawn.bottom - 3f * unit,
    )
    canvas.drawRoundRect(inner, radius * 0.82f, radius * 0.82f, border)
}

internal fun drawOthelloButton(canvas: Canvas, rect: RectF, pressed: Boolean, unit: Float) {
    drawDraughtsButton(canvas, rect, pressed, unit)
}