package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.SoundPlayer

internal fun isFullScreenStyledGameLabel(gameLabel: String): Boolean =
    gameLabel.replace(" ", "").replace("·", "").uppercase() in setOf(
        "CHESS",
        "DRAUGHTS",
        "INTLDRAUGHTS",
        "OTHELLO",
        "FOX&GEESE",
        "GO",
        "SHOGI",
        "XIANGQI",
        "MORABARABA",
        "TICTACTOE",
        "CONNECTFOUR",
        "LUDO",
        "MANCALA",
    )

/**
 * The shared Chess-styled choice surface used after selecting "vs AI".
 * Keeping it as a view instead of an AlertDialog makes the side picker feel
 * like part of the same game flow as the main Chess menu.
 */
class ChessChoiceView(
    context: Context,
    private val title: String,
    private val subtitle: String,
    choices: List<Choice>,
    private val gameLabel: String = "C H E S S",
    private val headerSymbol: String = "●",
    fullScreenOverride: Boolean? = null,
) : View(context) {

    data class Choice(
        val label: String,
        val detail: String,
        val symbol: String,
        val accent: Int,
    )

    var onChoiceSelected: ((Int) -> Unit)? = null

    private data class ChoiceHit(
        val choice: Choice,
        val index: Int,
        var rect: RectF = RectF(),
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val unit = density.coerceAtLeast(1f)
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val fullScreen = fullScreenOverride ?: isFullScreenStyledGameLabel(gameLabel)
    private val isChess = isChessStyledLabel(gameLabel)
    private val isDraughts = isDraughtsStyledLabel(gameLabel)
    private val isOthello = isOthelloStyledLabel(gameLabel)
    private val isMorabaraba = isMorabarabaStyledLabel(gameLabel)
    private val isFoxAndGeese =
        gameLabel.replace(" ", "").replace("·", "").equals("FOX&GEESE", ignoreCase = true)
    private val isGo = gameLabel.replace(" ", "").equals("GO", ignoreCase = true)
    private val isShogi = gameLabel.replace(" ", "").equals("SHOGI", ignoreCase = true)
    private val isXiangqi = gameLabel.replace(" ", "").equals("XIANGQI", ignoreCase = true)
    private val isTicTacToe = gameLabel.replace(" ", "").replace("·", "").equals("TICTACTOE", ignoreCase = true)
    private val isConnectFour = gameLabel.replace(" ", "").replace("·", "").equals("CONNECTFOUR", ignoreCase = true)
    private val isLudo = gameLabel.replace(" ", "").equals("LUDO", ignoreCase = true)
    private val isMancala = gameLabel.replace(" ", "").equals("MANCALA", ignoreCase = true)
    private val isChessFamily =
        isChess || isDraughts || isOthello || isFoxAndGeese || isGo || isShogi ||
            isXiangqi || isTicTacToe || isConnectFour || isLudo || isMancala
    private val gameIconBitmap = run {
        val assetName = when {
            isChess -> "chess_home_icon.png"
            isDraughts && gameLabel.replace(" ", "").equals("INTLDRAUGHTS", ignoreCase = true) ->
                "international_draughts_home_icon.png"
            isDraughts -> "draughts_home_icon.png"
            isOthello -> "othello_home_icon.png"
            isFoxAndGeese -> "fox_and_geese_home_icon.png"
            isGo -> "go_home_icon.png"
            isShogi -> "shogi_home_icon.png"
            isXiangqi -> "xiangqi_home_icon.png"
            isConnectFour -> "connect_four_home_icon.png"
            isLudo -> "ludo_home_icon.png"
            isMancala -> "mancala_home_icon.webp"
            else -> null
        }
        assetName?.let {
            try {
                context.assets.open(it).use { stream -> BitmapFactory.decodeStream(stream) }
            } catch (_: Throwable) {
                null
            }
        }
    }
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
    }
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 21f * textScale
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.18f
        textSize = 11f * textScale
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 26f * textScale
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 12f * textScale
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 16f * textScale
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 11f * textScale
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }
    private val gameIconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val hits = choices.mapIndexed { index, choice -> ChoiceHit(choice, index) }
    private val scales = HashMap<Int, Float>()
    private var pressedIndex: Int? = null
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var contentOffset = 0f
    private var atmospherePhase = 0f
    private val chessFamilyBackdrop = ChessFamilyBackdrop(unit)
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        hits.forEach { scales[it.index] = 1f }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isChessFamily || isMorabaraba) atmosphereAnimator.start()
    }

    override fun onDetachedFromWindow() {
        atmosphereAnimator.cancel()
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (178f + hits.size * 104f) * unit
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = if (fullScreen && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val contentHeight = (178f + hits.size * 104f) * unit
        contentOffset = if (fullScreen) {
            ((height - contentHeight) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        val sidePadding = 22f * unit
        val top = contentOffset + 153f * unit
        val cardHeight = 88f * unit
        val gap = 12f * unit
        hits.forEachIndexed { index, hit ->
            val cardTop = top + index * (cardHeight + gap)
            hit.rect = RectF(sidePadding, cardTop, width - sidePadding, cardTop + cardHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (isChessFamily || isMorabaraba) {
            if (isMorabaraba) {
                drawMorabarabaAtmosphere(
                    canvas,
                    width,
                    height,
                    unit,
                    rounded = !fullScreen,
                    phase = atmospherePhase,
                )
            } else {
                chessFamilyBackdrop.draw(
                    canvas,
                    width,
                    height,
                    atmospherePhase,
                    rounded = !fullScreen,
                )
            }
        } else {
            surfacePaint.shader = LinearGradient(
                0f,
                0f,
                width,
                height,
                Color.parseColor("#102C32"),
                Color.parseColor("#0B1D25"),
                Shader.TileMode.CLAMP,
            )
            if (fullScreen) {
                canvas.drawRect(0f, 0f, width, height, surfacePaint)
            } else {
                canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, surfacePaint)
            }
            surfacePaint.shader = null
        }

        when {
            isChessFamily -> drawChessFamilyHeader(canvas, width, contentOffset)
            isMorabaraba -> drawMorabarabaHeader(canvas, width, contentOffset)
            else -> drawHeader(canvas, width, contentOffset)
        }
        hits.forEach { drawChoice(canvas, it) }
    }

    private fun drawChessHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        crownPaint.color = Color.parseColor("#FFB45E")
        canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        eyebrowPaint.color = Color.parseColor("#FFE09C")
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawChessFamilyHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val gameIcon = gameIconBitmap
        if (gameIcon != null) {
            val size = minOf(width * 0.22f, 66f * unit)
            val top = topOffset + 5f * unit
            canvas.drawBitmap(
                gameIcon,
                null,
                RectF(
                    center - size / 2f,
                    top,
                    center + size / 2f,
                    top + size,
                ),
                gameIconPaint,
            )
        } else {
            crownPaint.color = Color.parseColor("#FFB45E")
            canvas.drawText(
                if (isTicTacToe) "✕" else headerSymbol,
                center,
                topOffset + 43f * unit,
                crownPaint,
            )
            eyebrowPaint.color = Color.parseColor("#FFE09C")
            canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        }
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawDraughtsHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(
            center - 118f * unit,
            topOffset + 36f * unit,
            center - 42f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawLine(
            center + 42f * unit,
            topOffset + 36f * unit,
            center + 118f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        crownPaint.color = Color.parseColor("#FFB45E")
        crownPaint.textSize = 22f * textScale
        canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        eyebrowPaint.color = Color.parseColor("#FFE09C")
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawOthelloHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(
            center - 118f * unit,
            topOffset + 36f * unit,
            center - 42f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawLine(
            center + 42f * unit,
            topOffset + 36f * unit,
            center + 118f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        crownPaint.color = Color.parseColor("#FFB45E")
        crownPaint.textSize = 22f * textScale
        canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        eyebrowPaint.color = Color.parseColor("#FFE09C")
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawMorabarabaHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(
            center - 118f * unit,
            topOffset + 36f * unit,
            center - 42f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawLine(
            center + 42f * unit,
            topOffset + 36f * unit,
            center + 118f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        crownPaint.color = Color.parseColor("#FFB45E")
        crownPaint.textSize = 22f * textScale
        canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        // Keep the animated Morabaraba surface free of a second game-name
        // treatment; the circular pieces are its visual signature.
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(
            center - 118f * unit,
            topOffset + 36f * unit,
            center - 42f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawLine(
            center + 42f * unit,
            topOffset + 36f * unit,
            center + 118f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        canvas.drawText(subtitle, center, topOffset + 125f * unit, subtitlePaint)
    }

    private fun drawChoice(canvas: Canvas, hit: ChoiceHit) {
        if (isChessFamily || isMorabaraba) {
            if (isMorabaraba) drawOthelloChoice(canvas, hit) else drawChessChoice(canvas, hit)
            return
        }
        val scale = scales[hit.index] ?: 1f
        val rect = hit.rect
        val pressed = pressedIndex == hit.index

        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        cardPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(rect, 8f * unit, 8f * unit, cardPaint)
        borderPaint.color = hit.choice.accent
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * unit, rect.top + 0.5f * unit, rect.right - 0.5f * unit, rect.bottom - 0.5f * unit),
            8f * unit,
            8f * unit,
            borderPaint,
        )
        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = hit.choice.accent
            canvas.drawText(hit.choice.symbol, rect.centerX(), rect.top + 29f * unit, iconPaint)
            canvas.drawText(hit.choice.label, rect.centerX(), rect.top + 56f * unit, labelPaint)
            canvas.drawText(hit.choice.detail, rect.centerX(), rect.top + 74f * unit, detailPaint)
        } else {
            drawCenteredChoiceText(canvas, rect, hit.choice)
        }
        canvas.restore()
    }

    private fun drawDraughtsChoice(canvas: Canvas, hit: ChoiceHit) {
        val rect = hit.rect
        val pressed = pressedIndex == hit.index
        drawDraughtsButton(canvas, rect, pressed, unit)
        val top = rect.top + if (pressed) 2f * unit else 0f

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#4B211F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#321718")
            canvas.drawText(hit.choice.label, rect.centerX(), top + 56f * unit, labelPaint)
            detailPaint.color = Color.parseColor("#5D2C27")
            canvas.drawText(hit.choice.detail, rect.centerX(), top + 74f * unit, detailPaint)
        } else {
            labelPaint.color = Color.parseColor("#321718")
            detailPaint.color = Color.parseColor("#5D2C27")
            drawCenteredChoiceText(
                canvas,
                RectF(rect.left, top, rect.right, rect.bottom + (top - rect.top)),
                hit.choice,
            )
        }
    }

    private fun drawOthelloChoice(canvas: Canvas, hit: ChoiceHit) {
        val rect = hit.rect
        val pressed = pressedIndex == hit.index
        drawOthelloButton(canvas, rect, pressed, unit)
        val top = rect.top + if (pressed) 2f * unit else 0f

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#4B211F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#321718")
            canvas.drawText(hit.choice.label, rect.centerX(), top + 56f * unit, labelPaint)
            detailPaint.color = Color.parseColor("#5D2C27")
            canvas.drawText(hit.choice.detail, rect.centerX(), top + 74f * unit, detailPaint)
        } else {
            labelPaint.color = Color.parseColor("#321718")
            detailPaint.color = Color.parseColor("#5D2C27")
            drawCenteredChoiceText(
                canvas,
                RectF(rect.left, top, rect.right, rect.bottom + (top - rect.top)),
                hit.choice,
            )
        }
    }

    private fun drawChessChoice(canvas: Canvas, hit: ChoiceHit) {
        val rect = hit.rect
        val pressed = pressedIndex == hit.index
        drawChessWoodButton(canvas, rect, pressed, unit)
        val top = rect.top + if (pressed) 2f * unit else 0f

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#63301F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#4A1714")
            canvas.drawText(hit.choice.label, rect.centerX(), top + 56f * unit, labelPaint)
            detailPaint.color = Color.parseColor("#6A2D1B")
            canvas.drawText(hit.choice.detail, rect.centerX(), top + 74f * unit, detailPaint)
        } else {
            labelPaint.color = Color.parseColor("#4A1714")
            detailPaint.color = Color.parseColor("#6A2D1B")
            drawCenteredChoiceText(
                canvas,
                RectF(rect.left, top, rect.right, rect.bottom + (top - rect.top)),
                hit.choice,
            )
        }
    }

    private fun drawCenteredChoiceText(canvas: Canvas, rect: RectF, choice: Choice) {
        val labelMetrics = labelPaint.fontMetrics
        val detailMetrics = detailPaint.fontMetrics
        val labelHeight = labelMetrics.descent - labelMetrics.ascent
        val detailHeight = detailMetrics.descent - detailMetrics.ascent
        val gap = 3f * unit
        val groupHeight = labelHeight + gap + detailHeight
        val groupTop = rect.centerY() - groupHeight / 2f
        val labelBaseline = groupTop - labelMetrics.ascent
        val detailBaseline = groupTop + labelHeight + gap - detailMetrics.ascent
        canvas.drawText(choice.label, rect.centerX(), labelBaseline, labelPaint)
        canvas.drawText(choice.detail, rect.centerX(), detailBaseline, detailPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressedIndex = hits.firstOrNull { it.rect.contains(event.x, event.y) }?.index
                pressedIndex?.let { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    if (!isChessFamily && !isMorabaraba) pressedIndex?.let { animateScale(it, 1f) }
                    pressedIndex = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val selected = pressedIndex
                if (!isChessFamily && !isMorabaraba) selected?.let { animateScale(it, 1f) }
                if (selected != null && hits[selected].rect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    onChoiceSelected?.invoke(selected)
                }
                pressedIndex = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isChessFamily && !isMorabaraba) pressedIndex?.let { animateScale(it, 1f) }
                pressedIndex = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun animateScale(index: Int, target: Float) {
        animator?.cancel()
        val current = scales[index] ?: 1f
        animator = ValueAnimator.ofFloat(current, target).apply {
            duration = if (target < 1f) 80L else 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scales[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}