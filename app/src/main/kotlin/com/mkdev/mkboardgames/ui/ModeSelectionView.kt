package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.GameMode
import com.mkdev.mkboardgames.R
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The first screen shown when MK BOARD GAMES opens.
 *
 * This deliberately shares the home screen's title, logo, palette, settings
 * affordance, and wood-card treatment. The game catalogue remains MenuView,
 * so both modes always expose the same games and receive only a mode context.
 */
class ModeSelectionView(context: Context) : View(context) {

    var onModeSelected: ((GameMode) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onLogoTapped: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private var isLightMode = false
    private var isHomeBackgroundEnabled = false
    private var isWoodStyleEnabled = true

    private val logoBitmap: Bitmap? = try {
        (context.resources.getDrawable(R.drawable.ic_app_logo, null) as? android.graphics.drawable.BitmapDrawable)?.bitmap
    } catch (_: Throwable) {
        null
    }
    private val homeBackgroundBitmap: Bitmap? = try {
        context.assets.open("mk_board_home_background.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }

    private val bgPaint = Paint().apply { color = Color.parseColor("#121212") }
    private val homeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 190
    }
    private val homeBackgroundScrimPaint = Paint().apply {
        color = Color.argb(105, 0, 0, 0)
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 28f * sp.coerceAtMost(3f)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textAlign = Paint.Align.CENTER
        textSize = 13f * sp.coerceAtMost(3f)
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.12f
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 17f * sp.coerceAtMost(3f)
    }
    private val buttonDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        textAlign = Paint.Align.CENTER
        textSize = 11f * sp.coerceAtMost(3f)
    }
    private val buttonSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * sp.coerceAtMost(3f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textAlign = Paint.Align.CENTER
        textSize = 10f * sp.coerceAtMost(3f)
    }
    private val gearFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        style = Paint.Style.FILL
    }
    private val gearEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
    }
    private val gearHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#102C32")
        style = Paint.Style.FILL
    }
    private val gearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        textSize = 9f * sp.coerceAtMost(3f)
        isFakeBoldText = true
        letterSpacing = 0.08f
    }

    private val normalRect = RectF()
    private val irregularRect = RectF()
    private val logoRect = RectF()
    private val gearRect = RectF()
    private val gearTouch = RectF()
    private var pressedMode: GameMode? = null
    private var pressedGear = false
    private var logoPressed = false
    private var logoScale = 1f
    private var gearRotation = 0f
    private var scaleAnimator: ValueAnimator? = null
    private var gearAnimator: ValueAnimator? = null

    init {
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isLightMode = SettingsManager.isLightMode(context)
        isHomeBackgroundEnabled = SettingsManager.isBrownHomeStyleEnabled(context)
        isWoodStyleEnabled = isHomeBackgroundEnabled
        applyTheme()
        SoundPlayer.init(context)
    }

    override fun onDetachedFromWindow() {
        scaleAnimator?.cancel()
        gearAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val gearSize = 34f * dp
        val gearX = w - gearSize - 14f * dp
        val gearY = 14f * dp
        gearRect.set(gearX, gearY, gearX + gearSize, gearY + gearSize)
        gearTouch.set(
            gearX - 4f * dp,
            gearY - 4f * dp,
            gearX + gearSize + 4f * dp,
            gearY + gearSize + 18f * dp,
        )

        val logoSize = 58f * dp + 16f * dp
        val logoX = w / 2f
        val logoY = h * 0.075f
        logoRect.set(
            logoX - logoSize / 2f,
            logoY - logoSize / 2f,
            logoX + logoSize / 2f,
            logoY + logoSize / 2f,
        )

        val buttonLeft = 24f * dp
        val buttonRight = w - buttonLeft
        val gap = 12f * dp
        val available = h * 0.50f
        val buttonHeight = min(118f * dp, (available - gap) / 2f)
            .coerceAtLeast(82f * dp)
        val firstTop = h * 0.31f
        normalRect.set(buttonLeft, firstTop, buttonRight, firstTop + buttonHeight)
        irregularRect.set(
            buttonLeft,
            normalRect.bottom + gap,
            buttonRight,
            normalRect.bottom + gap + buttonHeight,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedGear = gearTouch.contains(event.x, event.y)
                if (!pressedGear && logoRect.contains(event.x, event.y)) {
                    logoPressed = true
                    animateLogo(0.85f)
                } else if (!pressedGear) {
                    pressedMode = when {
                        normalRect.contains(event.x, event.y) -> GameMode.NORMAL
                        irregularRect.contains(event.x, event.y) -> GameMode.IRREGULAR
                        else -> null
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                if (logoPressed) {
                    animateLogo(1f)
                    if (logoRect.contains(event.x, event.y)) {
                        SoundPlayer.play("ui_click")
                        onLogoTapped?.invoke()
                    }
                    logoPressed = false
                    invalidate()
                    return true
                }
                if (pressedGear && gearTouch.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    animateGear()
                    postDelayed({ onSettingsClicked?.invoke() }, 180L)
                    pressedGear = false
                    invalidate()
                    return true
                }
                val releasedMode = when {
                    normalRect.contains(event.x, event.y) -> GameMode.NORMAL
                    irregularRect.contains(event.x, event.y) -> GameMode.IRREGULAR
                    else -> null
                }
                if (releasedMode != null && releasedMode == pressedMode) {
                    SoundPlayer.play("ui_click")
                    onModeSelected?.invoke(releasedMode)
                }
                pressedMode = null
                pressedGear = false
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (logoPressed) {
                    animateLogo(1f)
                    logoPressed = false
                }
                pressedMode = null
                pressedGear = false
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (isHomeBackgroundEnabled) drawHomeBackground(canvas)
        drawHeader(canvas)
        drawGear(canvas)
        drawModeButton(
            canvas,
            normalRect,
            GameMode.NORMAL,
            "Play",
            "Standard rules and gameplay",
            "▶",
            Color.parseColor("#E3B86A"),
        )
        drawModeButton(
            canvas,
            irregularRect,
            GameMode.IRREGULAR,
            "Play (IRREGULAR MODE)",
            "Experimental rules, coins and abilities",
            "◆",
            Color.parseColor("#A9B6E8"),
        )
        canvas.drawText("©2026 MKDEV", width / 2f, height * 0.94f, footerPaint)
    }

    private fun drawHeader(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val logoSize = 58f * dp
        val logoX = w / 2f
        val logoY = h * 0.075f
        canvas.save()
        canvas.scale(logoScale, logoScale, logoX, logoY)
        logoBitmap?.let {
            canvas.drawBitmap(
                it,
                null,
                RectF(
                    logoX - logoSize / 2f,
                    logoY - logoSize / 2f,
                    logoX + logoSize / 2f,
                    logoY + logoSize / 2f,
                ),
                bitmapPaint,
            )
        }
        canvas.restore()

        canvas.drawText("MK BOARD GAMES", w / 2f, h * 0.168f, titlePaint)
        canvas.drawText("Your board game hub", w / 2f, h * 0.198f, subPaint)
        canvas.drawLine(w * 0.40f, h * 0.205f, w * 0.60f, h * 0.205f, accentPaint)
        canvas.drawText("CHOOSE YOUR PLAY MODE", w / 2f, h * 0.285f, sectionPaint)
    }

    private fun drawModeButton(
        canvas: Canvas,
        rect: RectF,
        mode: GameMode,
        title: String,
        detail: String,
        symbol: String,
        accent: Int,
    ) {
        val pressed = pressedMode == mode
        if (isLightMode || !isWoodStyleEnabled) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(55, 0, 0, 0)
                maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(
                RectF(rect.left + 3f * dp, rect.top + 4f * dp, rect.right + 3f * dp, rect.bottom + 4f * dp),
                18f * dp,
                18f * dp,
                shadow,
            )
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (pressed) Color.parseColor("#F4F7FA") else Color.WHITE
            }
            canvas.drawRoundRect(rect, 18f * dp, 18f * dp, fill)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0E5EA")
                style = Paint.Style.STROKE
                strokeWidth = dp
            }
            canvas.drawRoundRect(rect, 18f * dp, 18f * dp, border)
        } else {
            drawWoodButton(canvas, rect, pressed)
        }

        val centerX = rect.centerX()
        val symbolY = rect.top + rect.height() * 0.37f
        buttonSymbolPaint.color = accent
        canvas.drawText(symbol, centerX, symbolY, buttonSymbolPaint)
        buttonTitlePaint.color = if (isLightMode) Color.parseColor("#1A1A1A") else Color.WHITE
        buttonDetailPaint.color = if (isLightMode) Color.parseColor("#555555") else Color.parseColor("#BDBDBD")
        canvas.drawText(title, centerX, rect.top + rect.height() * 0.62f, buttonTitlePaint)
        canvas.drawText(detail, centerX, rect.top + rect.height() * 0.79f, buttonDetailPaint)
    }

    private fun drawWoodButton(canvas: Canvas, rect: RectF, pressed: Boolean) {
        val radius = 14f * dp
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(155, 0, 0, 0)
            maskFilter = BlurMaskFilter(5f * dp, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(rect.left + 2.5f * dp, rect.top + 5f * dp, rect.right + 2.5f * dp, rect.bottom + 5f * dp),
            radius,
            radius,
            shadow,
        )
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                if (pressed) {
                    intArrayOf(Color.rgb(119, 70, 39), Color.rgb(76, 37, 20), Color.rgb(105, 57, 30))
                } else {
                    intArrayOf(Color.rgb(139, 83, 46), Color.rgb(77, 37, 20), Color.rgb(119, 64, 34))
                },
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, outer)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 173, 113, 70)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * dp
        }
        canvas.drawRoundRect(
            RectF(rect.left + dp, rect.top + dp, rect.right - dp, rect.bottom - dp),
            radius - dp,
            radius - dp,
            edge,
        )
        val inner = RectF(
            rect.left + 5f * dp,
            rect.top + 5f * dp,
            rect.right - 5f * dp,
            rect.bottom - 5f * dp,
        )
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                inner.top,
                inner.right,
                inner.bottom,
                intArrayOf(Color.rgb(67, 30, 16), Color.rgb(42, 18, 10), Color.rgb(74, 35, 18)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(inner, 9f * dp, 9f * dp, innerPaint)
        val innerEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 186, 124, 75)
            style = Paint.Style.STROKE
            strokeWidth = dp
        }
        canvas.drawRoundRect(
            RectF(inner.left + dp, inner.top + dp, inner.right - dp, inner.bottom - dp),
            8f * dp,
            8f * dp,
            innerEdge,
        )
        val stud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(111, 62, 31) }
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 232, 178, 112) }
        val inset = 7f * dp
        listOf(
            rect.left + inset to rect.top + inset,
            rect.right - inset to rect.top + inset,
            rect.left + inset to rect.bottom - inset,
            rect.right - inset to rect.bottom - inset,
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, 1.8f * dp, stud)
            canvas.drawCircle(x - 0.6f * dp, y - 0.6f * dp, 0.65f * dp, highlight)
        }
    }

    private fun drawHomeBackground(canvas: Canvas) {
        val bitmap = homeBackgroundBitmap ?: return
        val scale = maxOf(
            width.toFloat() / bitmap.width,
            height.toFloat() / bitmap.height,
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            homeBackgroundPaint,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), homeBackgroundScrimPaint)
    }

    private fun drawGear(canvas: Canvas) {
        val cx = gearRect.centerX()
        val cy = gearRect.centerY()
        if (pressedGear) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(48, 227, 184, 106)
            }
            canvas.drawRoundRect(gearTouch, 8f * dp, 8f * dp, bg)
        }
        val button = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightMode) Color.argb(225, 255, 255, 255) else Color.argb(225, 16, 44, 50)
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightMode) Color.parseColor("#B8C8D1") else Color.parseColor("#6C573B")
            style = Paint.Style.STROKE
            strokeWidth = dp
        }
        canvas.drawCircle(cx, cy, 17f * dp, button)
        canvas.drawCircle(cx, cy, 17f * dp, edge)

        canvas.save()
        canvas.rotate(gearRotation, cx, cy)
        val path = Path()
        val points = 32
        for (i in 0 until points) {
            val angle = (-Math.PI / 2.0 + Math.PI * 2.0 * i / points).toFloat()
            val radius = if (i % 4 == 1 || i % 4 == 2) 12.5f * dp else 9.2f * dp
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, gearFillPaint)
        canvas.drawPath(path, gearEdgePaint)
        canvas.drawCircle(cx, cy, 4.2f * dp, gearHolePaint)
        canvas.restore()
        canvas.drawText("Settings", cx, gearRect.bottom + 14f * dp, gearLabelPaint)
    }

    private fun animateLogo(target: Float) {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(logoScale, target).apply {
            duration = 110L
            addUpdateListener {
                logoScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateGear() {
        gearAnimator?.cancel()
        gearAnimator = ValueAnimator.ofFloat(gearRotation, gearRotation + 360f).apply {
            duration = 650L
            addUpdateListener {
                gearRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun applyTheme() {
        if (isLightMode) {
            bgPaint.color = Color.parseColor("#F5F5F5")
            titlePaint.color = Color.parseColor("#1A1A1A")
            subPaint.color = Color.parseColor("#666666")
            gearFillPaint.color = Color.parseColor("#1976A8")
            gearEdgePaint.color = Color.parseColor("#0D5277")
            gearHolePaint.color = Color.parseColor("#F5F5F5")
            gearLabelPaint.color = Color.parseColor("#1976A8")
        } else {
            bgPaint.color = Color.parseColor("#121212")
            titlePaint.color = Color.WHITE
            subPaint.color = Color.parseColor("#9E9E9E")
            gearFillPaint.color = Color.parseColor("#E3B86A")
            gearEdgePaint.color = Color.parseColor("#F7D99B")
            gearHolePaint.color = Color.parseColor("#102C32")
            gearLabelPaint.color = Color.parseColor("#E3B86A")
        }
    }
}