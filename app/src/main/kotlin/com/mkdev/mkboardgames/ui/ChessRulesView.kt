package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import com.mkdev.mkboardgames.SoundPlayer
import java.util.Locale

/**
 * A scrollable rules panel that matches the Chess setup and side-picker
 * surfaces instead of falling back to a platform AlertDialog.
 */
class ChessRulesView(
    context: Context,
    private val gameName: String,
    private val rulesText: String,
    private val gameLabel: String = "C H E S S",
    private val headerSymbol: String = "♛",
) : LinearLayout(context) {

    var onDone: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val isChess = isChessStyledLabel(gameLabel)
    private val isDraughts = isDraughtsStyledLabel(gameLabel)

    init {
        if (isChess || isDraughts) {
            configureStyledRules()
        } else {
            configureClassicRules()
        }
    }

    private fun configureStyledRules() {
        orientation = VERTICAL
        setPadding(0, 0, 0, 0)
        setBackgroundColor(Color.TRANSPARENT)
        addView(ChessMancalaRulesView(context, rulesText, gameLabel, headerSymbol).apply {
            onBack = { onDone?.invoke() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun configureClassicRules() {
        orientation = VERTICAL
        setPadding((18f * density).toInt(), (18f * density).toInt(), (18f * density).toInt(), (14f * density).toInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (isChess) {
                intArrayOf(Color.parseColor("#112C68"), Color.parseColor("#061321"))
            } else {
                intArrayOf(Color.parseColor("#102C32"), Color.parseColor("#0B1D25"))
            },
        ).apply {
            cornerRadius = 12f * density
            setStroke(
                (1f * density).toInt(),
                if (isChess) Color.parseColor("#6D89C4") else Color.parseColor("#2C5960"),
            )
        }

        addView(TextView(context).apply {
            text = headerSymbol
            gravity = Gravity.CENTER
            setTextColor(if (isChess) Color.parseColor("#FFB45E") else Color.parseColor("#E3B86A"))
            setTextSize(22f)
            setPadding(0, 0, 0, (1f * density).toInt())
        }, LayoutParams(LayoutParams.MATCH_PARENT, (32f * density).toInt()))

        addView(TextView(context).apply {
            text = gameLabel
            gravity = Gravity.CENTER
            setTextColor(if (isChess) Color.parseColor("#FFE09C") else Color.parseColor("#E3B86A"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(11f)
            letterSpacing = 0.18f
        }, LayoutParams(LayoutParams.MATCH_PARENT, (22f * density).toInt()))

        addView(TextView(context).apply {
            text = "How To Play $gameName"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(25f)
            setIncludeFontPadding(false)
            minimumHeight = (46f * density).toInt()
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = "Learn the essentials before your first move."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9FB5B8"))
            setTextSize(12f)
        }, LayoutParams(LayoutParams.MATCH_PARENT, (30f * density).toInt()))

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = roundedBackground(
                if (isChess) Color.parseColor("#102951") else Color.parseColor("#0D252B"),
                8f,
            )
        }
        scroll.addView(TextView(context).apply {
            text = rulesText
            setTextColor(if (isChess) Color.parseColor("#E4F1F0") else Color.parseColor("#D7E1E0"))
            setTextSize(14f)
            setLineSpacing(4f * density, 1f)
            setPadding((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (18f * density).toInt())
        })
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
            weight = 1f
            topMargin = (8f * density).toInt()
        })

        addView(TextView(context).apply {
            text = "Got it"
            gravity = Gravity.CENTER
            setTextColor(if (isChess) Color.parseColor("#4A1714") else Color.parseColor("#102C32"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(14f)
            background = if (isChess) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#F7D99B"),
                        Color.parseColor("#C8894C"),
                        Color.parseColor("#85502D"),
                    ),
                ).apply {
                    cornerRadius = 8f * density
                }
            } else {
                roundedBackground(Color.parseColor("#E3B86A"), 8f)
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onDone?.invoke()
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, (46f * density).toInt()).apply {
            topMargin = (12f * density).toInt()
        })
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
}

private class ChessMancalaRulesView(
    context: Context,
    rulesText: String,
    private val gameLabel: String,
    private val headerSymbol: String,
) : View(context) {
    var onBack: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val isDraughts = isDraughtsStyledLabel(gameLabel)
    private val backRect = RectF()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sections = parseSections(rulesText)
    private var atmospherePhase = 0f
    private val atmosphereAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 36_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            atmospherePhase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "${if (isDraughts) "Draughts" else "Chess"} how to play"
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isDraughts) atmosphereAnimator.start()
    }

    override fun onDetachedFromWindow() {
        atmosphereAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        drawChessAtmosphere(canvas, width, height, density, rounded = false)

        val panelWidth = minOf(width * 0.9f, dp(610f))
        val panelHeight = minOf(height * 0.9f, dp(700f))
        val panel = RectF(
            (width - panelWidth) / 2f,
            (height - panelHeight) / 2f,
            (width + panelWidth) / 2f,
            (height + panelHeight) / 2f,
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
            setShadowLayer(dp(18f), 0f, dp(8f), Color.BLACK)
        }
        canvas.drawRoundRect(panel, dp(26f), dp(26f), panelPaint)
        panelPaint.clearShadowLayer()

        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        titlePaint.color = Color.WHITE
        titlePaint.textSize = minOf(dp(28f), panelWidth * 0.08f)
        val displayLabel = if (isDraughts) {
            gameLabel.replace(" ", "").replace("INTL", "INTL ")
        } else {
            "CHESS"
        }
        canvas.drawText(displayLabel, panel.centerX(), panel.top + dp(52f), titlePaint)
        titlePaint.color = Color.parseColor("#FFE09C")
        titlePaint.textSize = minOf(dp(22f), panelWidth * 0.065f)
        canvas.drawText("HOW TO PLAY", panel.centerX(), panel.top + dp(84f), titlePaint)

        val contentLeft = panel.left + dp(28f)
        val contentWidth = panel.width() - dp(56f)
        val contentBottom = panel.bottom - dp(82f)
        val lineHeight = dp(15f)
        var y = panel.top + dp(122f)
        headingPaint.color = Color.parseColor("#FFE09C")
        headingPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        headingPaint.textSize = minOf(dp(14f), contentWidth * 0.043f)
        bodyPaint.textAlign = Paint.Align.LEFT
        bodyPaint.typeface = Typeface.DEFAULT
        bodyPaint.color = Color.parseColor("#E4F1F0")
        bodyPaint.textSize = minOf(dp(12.5f), contentWidth * 0.038f)

        canvas.save()
        canvas.clipRect(contentLeft, panel.top + dp(108f), panel.right - dp(18f), contentBottom)
        sections.forEach { (heading, body) ->
            canvas.drawText(heading, contentLeft, y, headingPaint)
            y += lineHeight
            y = drawWrapped(canvas, body, contentLeft, contentWidth, y, lineHeight, bodyPaint)
            y += dp(6f)
        }
        canvas.restore()

        val buttonWidth = minOf(panel.width() * 0.42f, dp(210f))
        backRect.set(
            panel.centerX() - buttonWidth / 2f,
            panel.bottom - dp(62f),
            panel.centerX() + buttonWidth / 2f,
            panel.bottom - dp(14f),
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
        value.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                y += lineHeight
            } else {
                var line = ""
                paragraph.trim().split(Regex("\\s+")).forEach { word ->
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
            }
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
            setShadowLayer(dp(5f), 0f, dp(3f), Color.BLACK)
        }
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = Color.parseColor("#733A25")
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)
        bodyPaint.textAlign = Paint.Align.CENTER
        bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        bodyPaint.color = Color.parseColor("#4A1714")
        bodyPaint.textSize = minOf(dp(19f), rect.height() * 0.4f)
        val metrics = bodyPaint.fontMetrics
        canvas.drawText("Back", rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, bodyPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && backRect.contains(event.x, event.y)) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            SoundPlayer.play("ui_click")
            onBack?.invoke()
        }
        return true
    }

    private fun dp(value: Float): Float = value * density

    private fun parseSections(rulesText: String): List<Pair<String, String>> {
        val headings = setOf(
            "Overview",
            "Pieces & How They Move",
            "Castling",
            "Check & Checkmate",
            "Promotion",
            "Moving",
            "Jumping",
            "Kinging",
            "Capturing",
            "Flying Kings",
            "Winning",
        )
        val sections = mutableListOf<Pair<String, String>>()
        var currentHeading: String? = null
        val body = StringBuilder()

        fun flush() {
            val heading = currentHeading ?: return
            sections += heading.uppercase(Locale.US) to body.toString().trim()
            body.clear()
        }

        rulesText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line in headings -> {
                    flush()
                    currentHeading = line
                }
                line.isEmpty() || line.all { it == '─' } || line.endsWith("— Rules") -> Unit
                currentHeading != null -> {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(line)
                }
            }
        }
        flush()
        return sections
    }
}