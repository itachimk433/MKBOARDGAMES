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
                    SoundPlayer.play("ui_click")
                    onClick?.invoke()
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
}

/**
 * Full-screen Mancala home surface. The space backdrop and board are drawn
 * here so the screen remains responsive in both orientations.
 */
class MancalaHomeView(context: Context) : View(context) {
    var onPlay: (() -> Unit)? = null
    var onHowToPlay: (() -> Unit)? = null
    var onRewards: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onSound: (() -> Unit)? = null
    var onMusic: (() -> Unit)? = null

    private val boardBitmap = try {
        context.assets.open("mancala_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        isClickable = true
        isFocusable = true
        contentDescription = "Mancala home"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(
            0f,
            0f,
            w,
            h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    h,
                    Color.parseColor("#176A9B"),
                    Color.parseColor("#061727"),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        drawStars(canvas, w, h)
        drawBoard(canvas, w, h)
        drawTitle(canvas, w, h)

        buttonRects.clear()
        val compact = w < dp(context, 620f)
        val topSize = min(dp(context, if (compact) 47f else 56f), h * 0.13f)
        drawIconButton(canvas, "sound", RectF(w - topSize * 2.35f, dp(context, 12f), w - topSize * 1.25f, dp(context, 12f) + topSize), "◖")
        drawIconButton(canvas, "music", RectF(w - topSize * 1.15f, dp(context, 12f), w - dp(context, 12f), dp(context, 12f) + topSize), "♪")

        val mainWidth = min(w * 0.42f, dp(context, 250f))
        val mainHeight = min(dp(context, 58f), h * 0.13f)
        val mainLeft = (w - mainWidth) / 2f
        val playTop = h * 0.46f
        drawWoodButton(canvas, "play", RectF(mainLeft, playTop, mainLeft + mainWidth, playTop + mainHeight), "Play")
        val rulesTop = playTop + mainHeight + dp(context, 18f)
        drawWoodButton(canvas, "rules", RectF(mainLeft, rulesTop, mainLeft + mainWidth, rulesTop + mainHeight), "How To Play")

        val bottomY = h - min(dp(context, 62f), h * 0.14f)
        val iconSize = min(dp(context, 52f), h * 0.12f)
        drawIconButton(canvas, "rewards", RectF(dp(context, 14f), bottomY, dp(context, 14f) + iconSize, bottomY + iconSize), "★")
        drawIconButton(canvas, "settings", RectF(w - iconSize * 2.2f, bottomY, w - iconSize * 1.2f, bottomY + iconSize), "⚙")
        drawIconButton(canvas, "more", RectF(w - iconSize * 1.1f, bottomY, w - dp(context, 14f), bottomY + iconSize), "⋮")
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 48) {
            val x = ((i * 83 + 37) % 1000) / 1000f * w
            val y = ((i * 47 + 23) % 920) / 1000f * h
            val radius = dp(context, 0.7f + (i % 3) * 0.55f)
            paint.color = Color.argb(75 + (i % 4) * 35, 225, 245, 255)
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.color = Color.argb(70, 83, 210, 241)
        canvas.drawCircle(w * 0.16f, h * 0.68f, min(w, h) * 0.22f, paint)
        paint.color = Color.argb(42, 255, 224, 147)
        canvas.drawCircle(w * 0.82f, h * 0.36f, min(w, h) * 0.2f, paint)
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
        titlePaint.textSize = min(w * 0.115f, h * 0.17f).coerceAtLeast(dp(context, 32f))
        titlePaint.setShadowLayer(dp(context, 5f), 0f, dp(context, 6f), Color.argb(200, 24, 15, 12))
        titlePaint.shader = LinearGradient(
            0f,
            h * 0.08f,
            0f,
            h * 0.25f,
            Color.parseColor("#FFE09C"),
            Color.parseColor("#E66D2F"),
            Shader.TileMode.CLAMP,
        )
        canvas.drawText("MANCALA", w / 2f, h * 0.25f, titlePaint)
        titlePaint.shader = null
        titlePaint.clearShadowLayer()
    }

    private fun drawWoodButton(canvas: Canvas, key: String, rect: RectF, label: String) {
        buttonRects[key] = rect
        val colors = woodColors(MancalaWoodButton.Style.GOLD)
        val isPressed = pressedKey == key
        val offset = if (isPressed) dp(context, 2f) else 0f
        val drawn = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
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
                    "play" -> onPlay?.invoke()
                    "rules" -> onHowToPlay?.invoke()
                    "rewards" -> onRewards?.invoke()
                    "settings", "more" -> onSettings?.invoke()
                    "sound" -> onSound?.invoke()
                    "music" -> onMusic?.invoke()
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
    private val density = resources.displayMetrics.density
    private val hits = mutableListOf<RectF>()
    private var pressedIndex = -1
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
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
        val panelHeight = min(h * 0.82f, dp(context, 510f))
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
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = min(dp(context, 29f), panelWidth * 0.09f)
        canvas.drawText(title, panel.centerX(), panel.top + dp(context, 61f), textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#C8E8E9")
        textPaint.textSize = min(dp(context, 15f), panelWidth * 0.047f)
        canvas.drawText(subtitle, panel.centerX(), panel.top + dp(context, 91f), textPaint)

        hits.clear()
        val gap = dp(context, 14f)
        val buttonHeight = min(dp(context, 58f), (panel.height() - dp(context, 138f) - gap * (options.size - 1)) / options.size)
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

    private fun drawChoiceButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
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

class MancalaPauseView(context: Context) : MancalaChoiceOverlayView(
    context,
    "Game Paused",
    "Your match is safe. What would you like to do?",
    listOf("Resume", "Restart", "Quit"),
)

class MancalaRewardsView(context: Context) : View(context) {
    var onClaim: (() -> Unit)? = null
    private val density = resources.displayMetrics.density
    private val claimRect = RectF()
    private var pressed = false
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Daily rewards"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(205, 0, 6, 14))
        val panelWidth = min(w * 0.9f, dp(context, 760f))
        val panelHeight = min(h * 0.72f, dp(context, 340f))
        val panel = RectF(
            (w - panelWidth) / 2f,
            (h - panelHeight) / 2f,
            (w + panelWidth) / 2f,
            (h + panelHeight) / 2f,
        )
        canvas.drawRoundRect(
            panel,
            dp(context, 28f),
            dp(context, 28f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#176A86")
                setShadowLayer(dp(context, 18f), 0f, dp(context, 8f), Color.BLACK)
            },
        )
        val header = RectF(panel.left, panel.top, panel.right, panel.top + dp(context, 52f))
        canvas.drawRect(header, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6F1E9") })
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#421917")
        textPaint.textSize = min(dp(context, 22f), panelWidth * 0.05f)
        canvas.drawText("DAILY REWARDS", panel.centerX(), panel.top + dp(context, 34f), textPaint)
        textPaint.color = Color.parseColor("#FFE20A")
        textPaint.textSize = min(dp(context, 23f), panelWidth * 0.05f)
        canvas.drawText("CONGRATULATIONS", panel.centerX(), panel.top + dp(context, 91f), textPaint)

        val rewards = listOf("20", "40", "80", "150", "200", "50")
        val columns = if (panelWidth < dp(context, 560f)) 3 else 6
        val rows = (rewards.size + columns - 1) / columns
        val cardGap = dp(context, 8f)
        val cardTop = panel.top + dp(context, 112f)
        val availableWidth = panel.width() - dp(context, 56f) - cardGap * (columns - 1)
        val cardWidth = (availableWidth / columns).coerceAtLeast(dp(context, 48f))
        val cardHeight = min(
            dp(context, if (rows == 1) 132f else 104f),
            (panel.height() - dp(context, 186f) - cardGap * (rows - 1)) / rows,
        )
        rewards.forEachIndexed { index, reward ->
            val column = index % columns
            val row = index / columns
            val left = panel.left + dp(context, 28f) + column * (cardWidth + cardGap)
            val top = cardTop + row * (cardHeight + cardGap)
            val card = RectF(left, top, left + cardWidth, top + cardHeight)
            drawRewardCard(canvas, card, reward, index + 1)
        }

        val buttonWidth = min(panel.width() * 0.36f, dp(context, 220f))
        claimRect.set(
            panel.centerX() - buttonWidth / 2f,
            panel.bottom - dp(context, 62f),
            panel.centerX() + buttonWidth / 2f,
            panel.bottom - dp(context, 14f),
        )
        drawClaimButton(canvas, claimRect, pressed)
    }

    private fun drawRewardCard(canvas: Canvas, rect: RectF, reward: String, day: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                Color.parseColor("#E5A447"),
                Color.parseColor("#955528"),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, dp(context, 10f), dp(context, 10f), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(context, 1.3f)
        paint.color = Color.parseColor("#5F301B")
        canvas.drawRoundRect(rect, dp(context, 10f), dp(context, 10f), paint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#411717")
        textPaint.textSize = min(dp(context, 18f), rect.width() * 0.35f)
        canvas.drawText("$reward  ●", rect.centerX(), rect.top + rect.height() * 0.28f, textPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = min(dp(context, 16f), rect.width() * 0.32f)
        canvas.drawText("Day $day", rect.centerX(), rect.bottom - rect.height() * 0.12f, textPaint)
        textPaint.color = Color.argb(130, 255, 242, 190)
        textPaint.textSize = rect.width() * 0.42f
        canvas.drawText("●", rect.centerX(), rect.centerY() + rect.height() * 0.08f, textPaint)
    }

    private fun drawClaimButton(canvas: Canvas, rect: RectF, isPressed: Boolean) {
        val offset = if (isPressed) dp(context, 2f) else 0f
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
        }
        canvas.drawRoundRect(drawn, dp(context, 13f), dp(context, 13f), paint)
        textPaint.color = Color.parseColor("#4A1714")
        textPaint.textSize = min(dp(context, 20f), rect.height() * 0.4f)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val metrics = textPaint.fontMetrics
        canvas.drawText("Claim", drawn.centerX(), drawn.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = claimRect.contains(event.x, event.y)
                if (pressed) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed && !claimRect.contains(event.x, event.y)) {
                    pressed = false
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldClaim = pressed && claimRect.contains(event.x, event.y)
                pressed = false
                invalidate()
                if (shouldClaim) {
                    SoundPlayer.play("ui_click")
                    onClaim?.invoke()
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
}