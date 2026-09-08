package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.GameMode
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * The first screen shown when the app opens.
 *
 * This screen only chooses the ruleset context. The app logo and Settings
 * belong to the game catalogue and are intentionally not rendered here.
 */
class ModeSelectionView(context: Context) : View(context) {

    var onModeSelected: ((GameMode) -> Unit)? = null

    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val normalRect = RectF()
    private val irregularRect = RectF()
    private var pressedMode: GameMode? = null
    private var loadingMode: GameMode? = null
    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var normalScale = 1f
    private var irregularScale = 1f

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
        textSize = 38f * textScale
        setShadowLayer(3f * unit, 0f, 2f * unit, Color.argb(220, 0, 0, 0))
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * textScale
        letterSpacing = 0.12f
    }
    private val modeTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 14f * textScale
        setShadowLayer(2f * unit, 0f, 1f * unit, Color.argb(230, 0, 0, 0))
    }
    private val modeDescriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
        setShadowLayer(1.5f * unit, 0f, 1f * unit, Color.argb(210, 0, 0, 0))
    }
    private val brownWoodCardRenderer = BrownWoodCardRenderer(unit)
    private val loadingRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#63301F")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * unit
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        SoundPlayer.init(context)
    }

    override fun onDetachedFromWindow() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val buttonWidth = min(width - 48f * unit, 360f * unit)
        val left = (width - buttonWidth) / 2f
        val buttonHeight = 136f * unit
        val gap = 12f * unit
        val totalHeight = buttonHeight * 2f + gap
        val firstTop = (height * 0.235f).coerceAtMost(
            (height - totalHeight - 12f * unit).coerceAtLeast(12f * unit),
        )
        normalRect.set(left, firstTop, left + buttonWidth, firstTop + buttonHeight)
        irregularRect.set(
            left,
            normalRect.bottom + gap,
            left + buttonWidth,
            normalRect.bottom + gap + buttonHeight,
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

        val centerX = width / 2f
        canvas.drawText("SELECT MODE", centerX, height * 0.205f, sectionPaint)

        drawModeCard(canvas, normalRect, GameMode.NORMAL, "♟️", "Play", "Standard rules")
        drawModeCard(
            canvas,
            irregularRect,
            GameMode.IRREGULAR,
            "♟️♟️",
            "Play (IRREGULAR MODE)",
            "Irregular rules",
        )
    }

    private fun drawModeCard(
        canvas: Canvas,
        rect: RectF,
        mode: GameMode,
        symbol: String,
        label: String,
        description: String,
    ) {
        val scale = if (mode == GameMode.NORMAL) normalScale else irregularScale
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        brownWoodCardRenderer.draw(canvas, rect, pressedMode == mode)
        canvas.drawText(symbol, rect.centerX(), rect.top + 52f * unit, modeIconPaint)
        canvas.drawText(label, rect.centerX(), rect.top + 101f * unit, modeTitlePaint)
        canvas.drawText(description, rect.centerX(), rect.top + 119f * unit, modeDescriptionPaint)
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

    private fun animateCardScale(mode: GameMode, target: Float) {
        val from = if (mode == GameMode.NORMAL) normalScale else irregularScale
        ValueAnimator.ofFloat(from, target).apply {
            duration = if (target < 1f) 70L else 110L
            addUpdateListener {
                if (mode == GameMode.NORMAL) normalScale = it.animatedValue as Float
                else irregularScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (loadingMode != null) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedMode = when {
                    normalRect.contains(event.x, event.y) -> GameMode.NORMAL
                    irregularRect.contains(event.x, event.y) -> GameMode.IRREGULAR
                    else -> null
                }
                pressedMode?.let { animateCardScale(it, 0.96f) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val current = pressedMode
                if (current != null) {
                    val rect = if (current == GameMode.NORMAL) normalRect else irregularRect
                    if (!rect.contains(event.x, event.y)) {
                        animateCardScale(current, 1f)
                        pressedMode = null
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selected = pressedMode
                val rect = when (selected) {
                    GameMode.NORMAL -> normalRect
                    GameMode.IRREGULAR -> irregularRect
                    null -> null
                }
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
                pressedMode?.let { animateCardScale(it, 1f) }
                pressedMode = null
                invalidate()
                return true
            }
        }
        return true
    }
}