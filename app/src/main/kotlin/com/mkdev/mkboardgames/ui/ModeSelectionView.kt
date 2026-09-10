package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.GameMode
import com.mkdev.mkboardgames.R
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * The first screen shown when the app opens.
 *
 * This screen chooses the ruleset context and provides the app-level settings
 * entry. The game catalogue owns its separate gameplay settings surface.
 */
class ModeSelectionView(context: Context) : View(context) {

    var onModeSelected: ((GameMode) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onStatsClicked: (() -> Unit)? = null

    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val normalRect = RectF()
    private val irregularRect = RectF()
    private val challengesRect = RectF()
    private val settingsRect = RectF()
    private val settingsTouchRect = RectF()
    private val statsRect = RectF()
    private val statsTouchRect = RectF()
    private var pressedMode: GameMode? = null
    private var pressedSettings = false
    private var pressedStats = false
    private var loadingMode: GameMode? = null
    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var settingsRotation = 0f
    private var settingsAnimator: ValueAnimator? = null
    private var normalScale = 1f
    private var irregularScale = 1f
    private var challengesScale = 1f
    private val logoBitmap: Bitmap? = try {
        (context.resources.getDrawable(R.drawable.ic_app_logo, null) as? android.graphics.drawable.BitmapDrawable)?.bitmap
    } catch (_: Exception) {
        null
    }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val backgroundBitmap: Bitmap? = try {
        context.assets.open("mode_selection_background.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundScrimPaint = Paint().apply {
        color = Color.argb(58, 0, 0, 0)
    }
    private val modeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
        setShadowLayer(3f * unit, 0f, 2f * unit, Color.argb(220, 0, 0, 0))
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * textScale
        letterSpacing = 0.12f
    }
    private val versionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textAlign = Paint.Align.LEFT
        textSize = 10f * textScale
    }
    private val modeTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * textScale
        setShadowLayer(2f * unit, 0f, 1f * unit, Color.argb(230, 0, 0, 0))
    }
    private val modeDescriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        textAlign = Paint.Align.CENTER
        textSize = 8f * textScale
        setShadowLayer(1.5f * unit, 0f, 1f * unit, Color.argb(210, 0, 0, 0))
    }
    private val brownWoodCardRenderer = BrownWoodCardRenderer(unit)
    private val loadingRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#63301F")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * unit
        strokeCap = Paint.Cap.ROUND
    }
    private val settingsButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 34, 18, 13)
    }
    private val settingsButtonEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D3A05F")
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * unit
    }
    private val settingsGearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.FILL
    }
    private val settingsGearEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#85502D")
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
    }
    private val settingsGearHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#321718")
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
        SoundPlayer.init(context)
    }

    override fun onDetachedFromWindow() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        settingsAnimator?.cancel()
        settingsAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val originalButtonWidth = min(width - 48f * unit, 360f * unit)
        val buttonWidth = originalButtonWidth / 1.5f * 1.1f
        val left = (width - buttonWidth) / 2f
        val buttonHeight = 136f / 3f * 1.1f * unit
        val gap = 10f * unit
        val totalHeight = buttonHeight * 3f + gap * 2f
        val firstTop = (height * 0.16f).coerceAtMost(
            (height - totalHeight - 12f * unit).coerceAtLeast(12f * unit),
        )
        normalRect.set(left, firstTop, left + buttonWidth, firstTop + buttonHeight)
        irregularRect.set(
            left,
            normalRect.bottom + gap,
            left + buttonWidth,
            normalRect.bottom + gap + buttonHeight,
        )
        challengesRect.set(
            left,
            irregularRect.bottom + gap,
            left + buttonWidth,
            irregularRect.bottom + gap + buttonHeight,
        )
        val settingsSize = 34f * unit
        val settingsLeft = width - settingsSize - 14f * unit
        val settingsTop = height - settingsSize - 18f * unit
        settingsRect.set(settingsLeft, settingsTop, settingsLeft + settingsSize, settingsTop + settingsSize)
        settingsTouchRect.set(
            settingsLeft - 8f * unit,
            settingsTop - 8f * unit,
            settingsLeft + settingsSize + 8f * unit,
            settingsTop + settingsSize + 8f * unit,
        )
        val logoSize = 58f * unit
        val logoCenterX = width / 2f
        val logoCenterY = min(height * 0.055f, 52f * unit)
        statsRect.set(
            logoCenterX - logoSize / 2f,
            logoCenterY - logoSize / 2f,
            logoCenterX + logoSize / 2f,
            logoCenterY + logoSize / 2f,
        )
        statsTouchRect.set(
            statsRect.left - 10f * unit,
            statsRect.top - 10f * unit,
            statsRect.right + 10f * unit,
            statsRect.bottom + 10f * unit,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = backgroundBitmap
        if (bitmap == null) {
            canvas.drawColor(Color.parseColor("#121212"))
        } else {
            val scale = maxOf(
                width.toFloat() / bitmap.width.toFloat(),
                height.toFloat() / bitmap.height.toFloat(),
            )
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val left = (width - scaledWidth) / 2f
            val top = (height - scaledHeight) / 2f
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left, top, left + scaledWidth, top + scaledHeight),
                backgroundPaint,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundScrimPaint)
        }

        canvas.drawText("v1.2", 12f * unit, 12f * unit + versionPaint.textSize, versionPaint)
        val centerX = width / 2f
        logoBitmap?.let {
            canvas.drawBitmap(it, null, statsRect, logoPaint)
        }
        canvas.drawText("SELECT MODE", centerX, height * 0.13f, sectionPaint)

        drawModeCard(canvas, normalRect, GameMode.NORMAL, "♟️", "Play", "Standard rules")
        drawModeCard(
            canvas,
            irregularRect,
            GameMode.IRREGULAR,
            "♟️♟️",
            "Play (IRREGULAR MODE)",
            "Irregular rules",
        )
        drawModeCard(
            canvas,
            challengesRect,
            GameMode.CHALLENGES,
            "♞",
            "Challenges",
            "25 chess challenges",
        )
        drawSettings(canvas)
    }

    private fun drawModeCard(
        canvas: Canvas,
        rect: RectF,
        mode: GameMode,
        symbol: String,
        label: String,
        description: String,
    ) {
        val scale = when (mode) {
            GameMode.NORMAL -> normalScale
            GameMode.IRREGULAR -> irregularScale
            GameMode.CHALLENGES -> challengesScale
        }
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        brownWoodCardRenderer.draw(canvas, rect, pressedMode == mode)
        val iconCenterX = rect.left + 32f * unit
        val textCenterX = rect.left + 32f * unit + (rect.width() - 32f * unit) / 2f
        canvas.drawText(symbol, iconCenterX, rect.centerY() + 8f * unit, modeIconPaint)
        canvas.drawText(label, textCenterX, rect.centerY() + 1f * unit, modeTitlePaint)
        canvas.drawText(description, textCenterX, rect.centerY() + 14f * unit, modeDescriptionPaint)
        if (loadingMode == mode) drawLoadingRing(canvas, rect)
        canvas.restore()
    }

    private fun drawLoadingRing(canvas: Canvas, rect: RectF) {
        val radius = 8f * unit
        val centerX = rect.right - 17f * unit
        val centerY = rect.top + 17f * unit
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            loadingAngle,
            285f,
            false,
            loadingRingPaint,
        )
    }

    private fun drawSettings(canvas: Canvas) {
        val centerX = settingsRect.centerX()
        val centerY = settingsRect.centerY()
        if (pressedSettings) {
            val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(62, 247, 217, 155)
            }
            canvas.drawRoundRect(settingsTouchRect, 10f * unit, 10f * unit, pressedPaint)
        }

        canvas.drawCircle(centerX, centerY, 17f * unit, settingsButtonPaint)
        canvas.drawCircle(centerX, centerY, 17f * unit, settingsButtonEdgePaint)

        canvas.save()
        canvas.rotate(settingsRotation, centerX, centerY)
        val gearPath = Path()
        val teeth = 8
        val points = teeth * 4
        val outerRadius = 12.5f * unit
        val innerRadius = 9.2f * unit
        for (i in 0 until points) {
            val angle = (-Math.PI / 2.0 + (Math.PI * 2.0 * i / points)).toFloat()
            val radius = when (i % 4) {
                1, 2 -> outerRadius
                else -> innerRadius
            }
            val x = centerX + kotlin.math.cos(angle) * radius
            val y = centerY + kotlin.math.sin(angle) * radius
            if (i == 0) gearPath.moveTo(x, y) else gearPath.lineTo(x, y)
        }
        gearPath.close()
        canvas.drawPath(gearPath, settingsGearPaint)
        canvas.drawPath(gearPath, settingsGearEdgePaint)
        canvas.drawCircle(centerX, centerY, 4.2f * unit, settingsGearHolePaint)
        canvas.restore()
    }

    private fun animateSettingsSpin() {
        settingsAnimator?.cancel()
        settingsAnimator = ValueAnimator.ofFloat(settingsRotation, settingsRotation + 360f).apply {
            duration = 650L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                settingsRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateCardScale(mode: GameMode, target: Float) {
        val from = when (mode) {
            GameMode.NORMAL -> normalScale
            GameMode.IRREGULAR -> irregularScale
            GameMode.CHALLENGES -> challengesScale
        }
        ValueAnimator.ofFloat(from, target).apply {
            duration = if (target < 1f) 70L else 110L
            addUpdateListener {
                when (mode) {
                    GameMode.NORMAL -> normalScale = it.animatedValue as Float
                    GameMode.IRREGULAR -> irregularScale = it.animatedValue as Float
                    GameMode.CHALLENGES -> challengesScale = it.animatedValue as Float
                }
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (loadingMode != null) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedStats = statsTouchRect.contains(event.x, event.y)
                pressedSettings = !pressedStats && settingsTouchRect.contains(event.x, event.y)
                pressedMode = if (pressedSettings || pressedStats) {
                    null
                } else {
                    when {
                        normalRect.contains(event.x, event.y) -> GameMode.NORMAL
                        irregularRect.contains(event.x, event.y) -> GameMode.IRREGULAR
                        challengesRect.contains(event.x, event.y) -> GameMode.CHALLENGES
                        else -> null
                    }
                }
                pressedMode?.let { animateCardScale(it, 0.96f) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedStats && !statsTouchRect.contains(event.x, event.y)) {
                    pressedStats = false
                    invalidate()
                    return true
                }
                if (pressedSettings && !settingsTouchRect.contains(event.x, event.y)) {
                    pressedSettings = false
                    invalidate()
                    return true
                }
                val current = pressedMode
                if (current != null) {
                    val rect = modeRect(current)
                    if (!rect.contains(event.x, event.y)) {
                        animateCardScale(current, 1f)
                        pressedMode = null
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (pressedStats) {
                    val selectedStats = statsTouchRect.contains(event.x, event.y)
                    pressedStats = false
                    if (selectedStats) {
                        SoundPlayer.play("ui_click")
                        onStatsClicked?.invoke()
                    }
                    invalidate()
                    return true
                }
                if (pressedSettings) {
                    val selectedSettings = settingsTouchRect.contains(event.x, event.y)
                    pressedSettings = false
                    if (selectedSettings) {
                        SoundPlayer.play("ui_click")
                        animateSettingsSpin()
                        postDelayed({ onSettingsClicked?.invoke() }, 180L)
                    }
                    invalidate()
                    return true
                }
                val selected = pressedMode
                val rect = selected?.let(::modeRect)
                pressedMode = null
                selected?.let { animateCardScale(it, 1f) }
                invalidate()
                if (selected != null && rect?.contains(event.x, event.y) == true) {
                    SoundPlayer.play("ui_click")
                    loadingMode = selected
                    loadingAnimator?.cancel()
                    loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                        duration = 700L
                        repeatCount = ValueAnimator.INFINITE
                        addUpdateListener {
                            loadingAngle = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
                    postDelayed({
                        if (loadingMode == selected) onModeSelected?.invoke(selected)
                    }, 260L)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedStats = false
                pressedSettings = false
                pressedMode?.let { animateCardScale(it, 1f) }
                pressedMode = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun modeRect(mode: GameMode): RectF = when (mode) {
        GameMode.NORMAL -> normalRect
        GameMode.IRREGULAR -> irregularRect
        GameMode.CHALLENGES -> challengesRect
    }
}