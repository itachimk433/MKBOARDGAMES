package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

private fun dp(context: Context, value: Float): Float =
    value * context.resources.displayMetrics.density

private fun woodColors(style: MancalaWoodButton.Style): IntArray = when (style) {
    MancalaWoodButton.Style.GOLD -> intArrayOf(
        Color.parseColor("#F7D99B"),
        Color.parseColor("#C8894C"),
        Color.parseColor("#85502D"),
    )
    MancalaWoodButton.Style.BLUE -> intArrayOf(
        Color.parseColor("#D7F0F2"),
        Color.parseColor("#6FB6C6"),
        Color.parseColor("#2A6678"),
    )
    MancalaWoodButton.Style.RED -> intArrayOf(
        Color.parseColor("#F5B0A0"),
        Color.parseColor("#B95D4D"),
        Color.parseColor("#6A2D2B"),
    )
}

/**
 * A small, reusable button that matches the warm wood controls in the Mancala
 * reference screens without relying on a bitmap that would blur when resized.
 */
class MancalaWoodButton(
    context: Context,
    private val label: String,
    var style: Style = Style.GOLD,
) : View(context) {
    enum class Style { GOLD, BLUE, RED }

    var onClick: (() -> Unit)? = null
    private var pressed = false
    private var downX = 0f
    private var downY = 0f
    private val density = resources.displayMetrics.density
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(context, 1.5f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(context, 1f)
        color = Color.argb(175, 255, 246, 220)
    }

    init {
        PlainGameButtonAssets.initialize(context)
        isClickable = true
        isFocusable = true
        contentDescription = label
        minimumHeight = dp(context, 44f).toInt()
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = dp(context, 3f)
        val lift = if (pressed) dp(context, 2f) else 0f
        val rect = RectF(inset, inset + lift, width - inset, height - inset + lift)
        val plateStyle = PlainGameButtonAssets.styleFor(rect)
        if (plateStyle != null && PlainGameButtonAssets.draw(canvas, rect, plateStyle, pressed)) {
            labelPaint.color = Color.WHITE
            labelPaint.textSize = min(width * 0.16f, height * 0.4f).coerceAtLeast(dp(context, 12f))
            val metrics = labelPaint.fontMetrics
            val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, rect.centerX(), baseline, labelPaint)
            return
        }
        val radius = min(rect.height(), rect.width()) * 0.18f

        val colors = woodColors(style)
        val gradient = LinearGradient(
            rect.left,
            rect.top,
            rect.left,
            rect.bottom,
            colors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            setShadowLayer(
                dp(context, if (pressed) 1f else 4f),
                0f,
                dp(context, if (pressed) 1f else 3f),
                Color.argb(170, 25, 9, 5),
            )
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.clearShadowLayer()

        borderPaint.color = colors[2]
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        val inner = RectF(
            rect.left + dp(context, 3f),
            rect.top + dp(context, 3f),
            rect.right - dp(context, 3f),
            rect.bottom - dp(context, 3f),
        )
        canvas.drawRoundRect(inner, radius * 0.78f, radius * 0.78f, highlightPaint)

        labelPaint.textSize = min(width * 0.16f, height * 0.4f).coerceAtLeast(dp(context, 12f))
        val metrics = labelPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, rect.centerX(), baseline, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = kotlin.math.hypot(
                    (event.x - downX).toDouble(),
                    (event.y - downY).toDouble(),
                )
                if (moved > dp(context, 18f)) {
                    pressed = false
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldClick = pressed
                pressed = false
                invalidate()
                if (shouldClick) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        SoundPlayer.play("ui_click")
        onClick?.invoke()
        return true
    }
}

/**
 * Full-screen Mancala home surface. The space backdrop and board are drawn
 * here so the screen remains responsive in both orientations.
 */
class MancalaHomeView(context: Context) : View(context) {
    var onPlay: (() -> Unit)? = null
    var onHowToPlay: (() -> Unit)? = null
    var onMore: (() -> Unit)? = null
    var onHome: (() -> Unit)? = null

    private val boardBitmap = try {
        context.assets.open("mancala_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val longButtonBitmap = try {
        context.assets.open("game_button_long.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val buttonBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val buttonLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
        setShadowLayer(dp(context, 2f), 0f, dp(context, 2f), Color.argb(210, 0, 0, 0))
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        setLetterSpacing(0.045f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonRects = linkedMapOf<String, RectF>()
    private var pressedKey: String? = null

    init {
        PlainGameButtonAssets.initialize(context)
        isClickable = true
        isFocusable = true
        contentDescription = "Mancala home"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawBackdrop(canvas, w, h)
        drawBoard(canvas, w, h)
        drawTitle(canvas, w, h)

        buttonRects.clear()
        val homeSize = min(dp(context, 52f), h * 0.12f)
        val homeInset = dp(context, 14f)
        drawIconButton(
            canvas,
            "home",
            RectF(homeInset, homeInset, homeInset + homeSize, homeInset + homeSize),
            "⌂",
        )
        val mainWidth = min(w * 0.42f, dp(context, 250f))
        val mainHeight = longButtonBitmap?.let {
            mainWidth * it.height.toFloat() / it.width.toFloat()
        } ?: min(dp(context, 58f), h * 0.13f)
        val mainLeft = (w - mainWidth) / 2f
        val playTop = h * 0.46f
        drawWoodButton(canvas, "play", RectF(mainLeft, playTop, mainLeft + mainWidth, playTop + mainHeight), "Play")
        val rulesTop = playTop + mainHeight + dp(context, 18f)
        drawWoodButton(canvas, "rules", RectF(mainLeft, rulesTop, mainLeft + mainWidth, rulesTop + mainHeight), "How To Play")

        val bottomY = h - min(dp(context, 62f), h * 0.14f)
        val iconSize = min(dp(context, 52f), h * 0.12f)
        drawIconButton(canvas, "more", RectF(w - iconSize * 1.1f, bottomY, w - dp(context, 14f), bottomY + iconSize), "⋮")
    }

    private fun drawBackdrop(canvas: Canvas, w: Float, h: Float) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                w * 0.9f,
                h,
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
        canvas.drawRect(0f, 0f, w, h, background)

        // Large, soft pools of colour make the space backdrop feel atmospheric
        // without competing with the board or the controls.
        drawGlow(
            canvas,
            w * 0.12f,
            h * 0.18f,
            min(w, h) * 0.58f,
            Color.rgb(53, 137, 220),
        )
        drawGlow(
            canvas,
            w * 0.9f,
            h * 0.64f,
            min(w, h) * 0.5f,
            Color.rgb(22, 194, 190),
        )
        drawGlow(
            canvas,
            w * 0.46f,
            h * 1.02f,
            min(w, h) * 0.68f,
            Color.rgb(71, 37, 134),
        )

        val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                h * 0.48f,
                w,
                h * 0.58f,
                intArrayOf(
                    Color.argb(0, 104, 194, 255),
                    Color.argb(54, 77, 158, 232),
                    Color.argb(0, 104, 194, 255),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, h * 0.4f, w, h * 0.65f, horizon)

        drawStars(canvas, w, h)
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
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

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 62) {
            val x = ((i * 83 + 37) % 1000) / 1000f * w
            val y = ((i * 47 + 23) % 920) / 1000f * h
            val radius = dp(context, 0.55f + (i % 4) * 0.45f)
            paint.color = Color.argb(70 + (i % 5) * 28, 220, 241, 255)
            canvas.drawCircle(x, y, radius, paint)
            if (i % 11 == 0) {
                paint.color = Color.argb(130, 178, 224, 255)
                canvas.drawCircle(x, y, radius * 2.4f, paint)
            }
        }

        // A couple of low-contrast planetary silhouettes keep the screen
        // playful while leaving the warm Mancala board as the focal point.
        paint.color = Color.argb(34, 88, 207, 220)
        canvas.drawCircle(w * 0.08f, h * 0.72f, min(w, h) * 0.18f, paint)
        paint.color = Color.argb(25, 150, 109, 226)
        canvas.drawCircle(w * 0.88f, h * 0.3f, min(w, h) * 0.2f, paint)
    }

    private fun drawBoard(canvas: Canvas, w: Float, h: Float) {
        val boardHeight = min(h * 0.83f, w * 0.76f)
        val boardWidth = boardHeight / 2.667f
        val rect = RectF(
            (w - boardWidth) / 2f,
            (h - boardHeight) / 2f + h * 0.1f,
            (w + boardWidth) / 2f,
            (h + boardHeight) / 2f + h * 0.1f,
        )
        canvas.save()
        canvas.rotate(-12f, rect.centerX(), rect.centerY())
        boardBitmap?.let {
            canvas.drawBitmap(it, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 225
                setShadowLayer(dp(context, 18f), 0f, dp(context, 12f), Color.argb(150, 0, 0, 0))
            })
        } ?: run {
            canvas.drawRoundRect(
                rect,
                dp(context, 20f),
                dp(context, 20f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7D4429") },
            )
        }
        canvas.restore()
    }

    private fun drawTitle(canvas: Canvas, w: Float, h: Float) {
        titlePaint.textSize = min(w * 0.145f, h * 0.2f).coerceAtLeast(dp(context, 36f))
        titlePaint.style = Paint.Style.STROKE
        titlePaint.strokeWidth = dp(context, 3.5f)
        titlePaint.color = Color.argb(220, 49, 20, 56)
        titlePaint.shader = null
        titlePaint.setShadowLayer(dp(context, 7f), 0f, dp(context, 7f), Color.argb(190, 3, 8, 26))
        canvas.drawText("MANCALA", w / 2f, h * 0.245f, titlePaint)

        titlePaint.style = Paint.Style.FILL
        titlePaint.shader = LinearGradient(
            0f,
            h * 0.08f,
            0f,
            h * 0.26f,
            intArrayOf(
                Color.parseColor("#FFF1B6"),
                Color.parseColor("#FFB45E"),
                Color.parseColor("#F07843"),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        titlePaint.setShadowLayer(dp(context, 4f), 0f, dp(context, 4f), Color.argb(180, 226, 76, 43))
        canvas.drawText("MANCALA", w / 2f, h * 0.245f, titlePaint)
        titlePaint.shader = null
        titlePaint.clearShadowLayer()
        titlePaint.strokeWidth = 0f
    }

    private fun drawWoodButton(canvas: Canvas, key: String, rect: RectF, label: String) {
        buttonRects[key] = rect
        val colors = woodColors(MancalaWoodButton.Style.GOLD)
        val isPressed = pressedKey == key
        val offset = if (isPressed) dp(context, 2f) else 0f
        val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
        longButtonBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, drawn, buttonBitmapPaint)
            buttonLabelPaint.textSize = min(drawn.height() * 0.28f, dp(context, 24f))
            val metrics = buttonLabelPaint.fontMetrics
            val baseline = drawn.centerY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, drawn.centerX(), baseline, buttonLabelPaint)
            return
        }
        val radius = drawn.height() * 0.2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                drawn.top,
                0f,
                drawn.bottom,
                colors,
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, if (isPressed) 2f else 6f), 0f, dp(context, 4f), Color.argb(190, 15, 4, 2))
        }
        canvas.drawRoundRect(drawn, radius, radius, paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(context, 2f)
        paint.color = Color.parseColor("#7B4025")
        canvas.drawRoundRect(drawn, radius, radius, paint)
        paint.strokeWidth = dp(context, 1f)
        paint.color = Color.argb(180, 255, 246, 220)
        canvas.drawRoundRect(
            RectF(drawn.left + dp(context, 3f), drawn.top + dp(context, 3f), drawn.right - dp(context, 3f), drawn.bottom - dp(context, 3f)),
            radius * 0.82f,
            radius * 0.82f,
            paint,
        )
        bodyPaint.textSize = min(drawn.height() * 0.42f, dp(context, 22f))
        bodyPaint.color = Color.parseColor("#4A1714")
        val metrics = bodyPaint.fontMetrics
        canvas.drawText(label, drawn.centerX(), drawn.centerY() - (metrics.ascent + metrics.descent) / 2f, bodyPaint)
    }

    private fun drawIconButton(canvas: Canvas, key: String, rect: RectF, label: String) {
        buttonRects[key] = rect
        val isPressed = pressedKey == key
        val offset = if (isPressed) dp(context, 2f) else 0f
        val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
        val radius = drawn.height() * 0.18f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                drawn.top,
                0f,
                drawn.bottom,
                Color.parseColor("#F2D095"),
                Color.parseColor("#93562F"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, 4f), 0f, dp(context, 3f), Color.argb(180, 10, 5, 2))
        }
        canvas.drawRoundRect(drawn, radius, radius, paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(context, 1f)
        paint.color = Color.parseColor("#71391F")
        canvas.drawRoundRect(drawn, radius, radius, paint)
        iconPaint.textSize = drawn.height() * 0.44f
        iconPaint.color = Color.parseColor("#51231A")
        val metrics = iconPaint.fontMetrics
        canvas.drawText(label, drawn.centerX(), drawn.centerY() - (metrics.ascent + metrics.descent) / 2f, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = buttonRects.entries.firstOrNull { it.value.contains(event.x, event.y) }?.key
                pressedKey?.let {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedKey != null && !buttonRects.getValue(pressedKey!!).contains(event.x, event.y)) {
                    pressedKey = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = pressedKey
                val hit = key != null && buttonRects[key]?.contains(event.x, event.y) == true
                pressedKey = null
                invalidate()
                if (!hit) return true
                SoundPlayer.play("ui_click")
                when (key) {
                    "home" -> onHome?.invoke()
                    "play" -> onPlay?.invoke()
                    "rules" -> onHowToPlay?.invoke()
                    "more" -> onMore?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }
        return true
    }
}

open class MancalaChoiceOverlayView(
    context: Context,
    private val title: String,
    private val subtitle: String,
    private val options: List<String>,
) : View(context) {
    var onChoice: ((Int) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    private val density = resources.displayMetrics.density
    private val hits = mutableListOf<RectF>()
    private val closeRect = RectF()
    private var pressedIndex = -1
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        PlainGameButtonAssets.initialize(context)
        isClickable = true
        isFocusable = true
        contentDescription = title
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(190, 0, 6, 12))
        val panelWidth = min(w * 0.88f, dp(context, 520f))
        val optionCount = options.size.coerceAtLeast(1)
        val gap = dp(context, 14f)
        val compactHeight = dp(context, 150f) +
            optionCount * dp(context, 58f) +
            (optionCount - 1) * gap +
            dp(context, 45f)
        val panelHeight = min(h * 0.82f, compactHeight)
        val panel = RectF(
            (w - panelWidth) / 2f,
            (h - panelHeight) / 2f,
            (w + panelWidth) / 2f,
            (h + panelHeight) / 2f,
        )
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                panel.top,
                0f,
                panel.bottom,
                Color.parseColor("#17677F"),
                Color.parseColor("#0C3344"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, 16f), 0f, dp(context, 8f), Color.argb(220, 0, 0, 0))
        }
        canvas.drawRoundRect(panel, dp(context, 25f), dp(context, 25f), panelPaint)
        panelPaint.clearShadowLayer()
        if (onClose != null) {
            closeRect.set(
                panel.right - dp(context, 54f),
                panel.top + dp(context, 18f),
                panel.right - dp(context, 18f),
                panel.top + dp(context, 54f),
            )
            drawCloseButton(canvas, closeRect)
        } else {
            closeRect.setEmpty()
        }
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = min(dp(context, 29f), panelWidth * 0.09f)
        canvas.drawText(title, panel.centerX(), panel.top + dp(context, 61f), textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#C8E8E9")
        textPaint.textSize = min(dp(context, 15f), panelWidth * 0.047f)
        drawWrappedCenteredText(
            canvas = canvas,
            text = subtitle,
            centerX = panel.centerX(),
            centerY = panel.top + dp(context, 91f),
            maxWidth = panelWidth - dp(context, 52f),
            lineSpacing = dp(context, 18f),
        )

        hits.clear()
        val buttonHeight = min(
            dp(context, 58f),
            (panel.height() - dp(context, 138f) - gap * (optionCount - 1)) / optionCount,
        )
        options.forEachIndexed { index, option ->
            val top = panel.top + dp(context, 116f) + index * (buttonHeight + gap)
            val rect = RectF(panel.left + panelWidth * 0.13f, top, panel.right - panelWidth * 0.13f, top + buttonHeight)
            hits += rect
            drawChoiceButton(canvas, rect, option, pressedIndex == index)
        }
        textPaint.color = Color.argb(180, 214, 239, 238)
        textPaint.textSize = dp(context, 12f)
        canvas.drawText("Tap an option to continue", panel.centerX(), panel.bottom - dp(context, 24f), textPaint)
    }

    private fun drawWrappedCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        lineSpacing: Float,
    ) {
        val lines = wrapText(text, maxWidth)
        val firstBaseline = centerY - (lines.size - 1) * lineSpacing / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + index * lineSpacing, textPaint)
        }
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.trim().split(Regex("\\s+")).filter(String::isNotEmpty).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf("") }
    }

    private fun drawCloseButton(canvas: Canvas, rect: RectF) {
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 20, 30)
            setShadowLayer(dp(context, 4f), 0f, dp(context, 2f), Color.argb(100, 0, 0, 0))
        }
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.5f, buttonPaint)
        buttonPaint.clearShadowLayer()
        buttonPaint.style = Paint.Style.STROKE
        buttonPaint.strokeWidth = dp(context, 2f)
        buttonPaint.strokeCap = Paint.Cap.ROUND
        buttonPaint.color = Color.argb(225, 235, 249, 248)
        val inset = dp(context, 11f)
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, buttonPaint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.left + inset, rect.bottom - inset, buttonPaint)
    }

    private fun drawChoiceButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        if (PlainGameButtonAssets.draw(
                canvas,
                rect,
                PlainGameButtonAssets.Style.LONG,
                pressed,
            )
        ) {
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = min(dp(context, 20f), rect.height() * 0.28f)
            val metrics = textPaint.fontMetrics
            val offset = if (pressed) dp(context, 2f) else 0f
            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() + offset - (metrics.ascent + metrics.descent) / 2f,
                textPaint,
            )
            return
        }
        val offset = if (pressed) dp(context, 2f) else 0f
        val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                drawn.top,
                0f,
                drawn.bottom,
                Color.parseColor("#F5D49A"),
                Color.parseColor("#A76438"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, 5f), 0f, dp(context, 3f), Color.argb(170, 0, 0, 0))
        }
        canvas.drawRoundRect(drawn, dp(context, 14f), dp(context, 14f), paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(context, 1.5f)
        paint.color = Color.parseColor("#733A25")
        canvas.drawRoundRect(drawn, dp(context, 14f), dp(context, 14f), paint)
        textPaint.color = Color.parseColor("#4A1714")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = min(dp(context, 20f), rect.height() * 0.36f)
        val metrics = textPaint.fontMetrics
        canvas.drawText(label, drawn.centerX(), drawn.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hits.indexOfFirst { it.contains(event.x, event.y) }
                if (pressedIndex >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedIndex >= 0 && !hits[pressedIndex].contains(event.x, event.y)) {
                    pressedIndex = -1
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (closeRect.contains(event.x, event.y) && onClose != null) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    SoundPlayer.play("ui_click")
                    onClose?.invoke()
                    return true
                }
                val selected = pressedIndex
                val valid = selected >= 0 && selected < hits.size && hits[selected].contains(event.x, event.y)
                pressedIndex = -1
                invalidate()
                if (valid) {
                    SoundPlayer.play("ui_click")
                    onChoice?.invoke(selected)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return true
    }
}

class MancalaRulesView(context: Context) : View(context) {
    var onBack: (() -> Unit)? = null
    private val backRect = RectF()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Mancala how to play"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(205, 0, 6, 14))

        val panelWidth = min(w * 0.9f, dp(context, 610f))
        val panelHeight = min(h * 0.9f, dp(context, 700f))
        val panel = RectF(
            (w - panelWidth) / 2f,
            (h - panelHeight) / 2f,
            (w + panelWidth) / 2f,
            (h + panelHeight) / 2f,
        )
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                panel.top,
                0f,
                panel.bottom,
                Color.parseColor("#17677F"),
                Color.parseColor("#0C3344"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, 18f), 0f, dp(context, 8f), Color.BLACK)
        }
        canvas.drawRoundRect(panel, dp(context, 26f), dp(context, 26f), panelPaint)
        panelPaint.clearShadowLayer()

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.WHITE
        textPaint.textSize = min(dp(context, 28f), panelWidth * 0.08f)
        canvas.drawText("MANCALA", panel.centerX(), panel.top + dp(context, 52f), textPaint)
        textPaint.color = Color.parseColor("#FFE09C")
        textPaint.textSize = min(dp(context, 22f), panelWidth * 0.065f)
        canvas.drawText("HOW TO PLAY", panel.centerX(), panel.top + dp(context, 84f), textPaint)

        val contentLeft = panel.left + dp(context, 28f)
        val contentWidth = panel.width() - dp(context, 56f)
        var y = panel.top + dp(context, 122f)
        val lineHeight = dp(context, 18f)
        val sections = listOf(
            "SETUP" to "Each player owns six pits and the store at their end. Begin with four stones in every pit. South moves first.",
            "SOWING" to "Choose a pit on your side, pick up every stone, and place them one at a time into the following pits. Skip the opponent’s store.",
            "EXTRA TURNS" to "If your last stone lands in your own store, take another turn.",
            "CAPTURES" to "If your last stone lands in an empty pit on your side, capture that stone and all stones in the directly opposite pit.",
            "ENDING THE GAME" to "When one side has no stones left in its six pits, the other side’s remaining stones move to its store. The higher total wins.",
        )
        sections.forEach { (heading, body) ->
            headingPaint.color = Color.parseColor("#FFE09C")
            headingPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            headingPaint.textSize = min(dp(context, 15f), contentWidth * 0.045f)
            canvas.drawText(heading, contentLeft, y, headingPaint)
            y += lineHeight
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.parseColor("#E4F1F0")
            textPaint.textSize = min(dp(context, 14f), contentWidth * 0.042f)
            y = drawWrapped(canvas, body, contentLeft, contentWidth, y, lineHeight, textPaint)
            y += dp(context, 12f)
        }

        val buttonWidth = min(panel.width() * 0.42f, dp(context, 210f))
        backRect.set(
            panel.centerX() - buttonWidth / 2f,
            panel.bottom - dp(context, 62f),
            panel.centerX() + buttonWidth / 2f,
            panel.bottom - dp(context, 14f),
        )
        drawBackButton(canvas, backRect)
    }

    private fun drawWrapped(
        canvas: Canvas,
        value: String,
        left: Float,
        maxWidth: Float,
        startY: Float,
        lineHeight: Float,
        paint: Paint,
    ): Float {
        var y = startY
        var line = ""
        value.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                canvas.drawText(line, left, y, paint)
                y += lineHeight
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, left, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun drawBackButton(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                Color.parseColor("#F5D49A"),
                Color.parseColor("#A76438"),
                Shader.TileMode.CLAMP,
            )
            setShadowLayer(dp(context, 5f), 0f, dp(context, 3f), Color.BLACK)
        }
        canvas.drawRoundRect(rect, dp(context, 14f), dp(context, 14f), paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(context, 1.5f)
        paint.color = Color.parseColor("#733A25")
        canvas.drawRoundRect(rect, dp(context, 14f), dp(context, 14f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#4A1714")
        textPaint.textSize = min(dp(context, 19f), rect.height() * 0.4f)
        val metrics = textPaint.fontMetrics
        canvas.drawText("Back", rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && backRect.contains(event.x, event.y)) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            SoundPlayer.play("ui_click")
            onBack?.invoke()
        }
        return true
    }
}