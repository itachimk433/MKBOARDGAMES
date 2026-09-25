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
import kotlin.math.min

internal fun isFullScreenStyledGameLabel(gameLabel: String): Boolean =
    gameLabel.replace(" ", "").replace("·", "").uppercase() in setOf(
        "CHESS",
        "AMAZONS",
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
        "SNAKES&LADDERS",
        "MANCALA",
        "YOTE",
        "YOTÉ",
        "ONITAMA",
        "FIVEFIELDKONO",
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
    private val gridChoices: Boolean = false,
    private val compactGrid: Boolean = false,
    private val showChoiceInfo: Boolean = true,
    private val dismissOnEmptyTap: Boolean = false,
    private val showBackButton: Boolean = !title.equals("Leave Match?", ignoreCase = true),
) : View(context) {

    data class Choice(
        val label: String,
        val detail: String,
        val symbol: String,
        val accent: Int,
        val assetName: String? = null,
        val enabled: Boolean = true,
    )

    var onChoiceSelected: ((Int) -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null
    var onBackRequested: (() -> Unit)? = null

    private data class ChoiceHit(
        val choice: Choice,
        val index: Int,
        val assetBitmap: Bitmap? = null,
        var rect: RectF = RectF(),
        var infoRect: RectF = RectF(),
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val unit = density.coerceAtLeast(1f)
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val fullScreen = fullScreenOverride ?: isFullScreenStyledGameLabel(gameLabel)
    private val isChess = isChessStyledLabel(gameLabel)
    private val isAmazons = isAmazonsStyledLabel(gameLabel)
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
    private val isSnakesLadders =
        gameLabel.replace(" ", "").replace("&", "").equals("SNAKESLADDERS", ignoreCase = true)
    private val isMancala = gameLabel.replace(" ", "").equals("MANCALA", ignoreCase = true)
    private val isYote = gameLabel.replace(" ", "").replace("É", "E").equals("YOTE", ignoreCase = true)
    private val isOnitama = gameLabel.replace(" ", "").equals("ONITAMA", ignoreCase = true)
    private val isFiveFieldKono =
        gameLabel.replace(" ", "").replace("·", "").equals("FIVEFIELDKONO", ignoreCase = true)
    private val isChessFamily =
        isChess || isAmazons || isDraughts || isOthello || isFoxAndGeese || isGo || isShogi ||
            isXiangqi || isTicTacToe || isConnectFour || isLudo || isSnakesLadders ||
            isMancala || isYote || isOnitama || isFiveFieldKono
    private val useLabelOnlyChoices = true
    private val choiceButtonScale = 0.93f
    private val lowerButtonLift = 0.24f
    private val gameIconBitmap = run {
        val assetName = when {
            isChess -> "chess_home_icon.webp"
            isAmazons -> "amazons_home_icon.webp"
            isDraughts && gameLabel.replace(" ", "").equals("INTLDRAUGHTS", ignoreCase = true) ->
                "international_draughts_home_icon.webp"
            isDraughts -> "draughts_home_icon.webp"
            isOthello -> "othello_home_icon.webp"
            isMorabaraba -> "morabaraba_home_icon.webp"
            isFoxAndGeese -> "fox_and_geese_home_icon.webp"
            isGo -> "go_home_icon.webp"
            isShogi -> "shogi_home_icon.webp"
            isXiangqi -> "xiangqi_home_icon.webp"
            isConnectFour -> "connect_four_home_icon.webp"
            isLudo -> "ludo_home_icon.webp"
            isSnakesLadders -> "snakes_ladders_icon.webp"
            isMancala -> "mancala_home_icon.webp"
            isYote -> "yote_home_icon.webp"
            isOnitama -> "onitama_home_icon.webp"
            isFiveFieldKono -> "five_field_kono_home_icon.webp"
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
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        textSize = 16f * textScale
    }
    private val plainChoiceLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        setShadowLayer(2f * unit, 0f, 2f * unit, Color.argb(210, 0, 0, 0))
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
    private val infoCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D2C27")
    }
    private val infoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE9B5")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * textScale
    }
    private val infoPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#152C3E")
    }
    private val infoPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * unit
        color = Color.parseColor("#E3B86A")
    }
    private val infoPanelTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 18f * textScale
    }
    private val infoPanelDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6E8FF")
        textAlign = Paint.Align.CENTER
        textSize = 13f * textScale
    }
    private val infoPanelHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }
    private val closeButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val closeButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val choiceAssetPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hits = choices.mapIndexed { index, choice ->
        ChoiceHit(choice, index, choice.assetName?.let(::loadAssetBitmap))
    }
    private val scales = HashMap<Int, Float>()
    private var pressedIndex: Int? = null
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var pressedInfoIndex: Int? = null
    private var infoIndex: Int? = null
    private var pressedClose = false
    private var pressedBack = false
    private val closeRect = RectF()
    private val backRect = RectF()
    private val backTouchRect = RectF()
    private var contentOffset = 0f
    private var atmospherePhase = 0f
    private val chessFamilyBackdrop = ChessFamilyBackdrop(unit)
    private val atmosphereAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 36_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            atmospherePhase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    init {
        PlainGameButtonAssets.initialize(context)
        isClickable = true
        isFocusable = true
        contentDescription = "$title. $subtitle"
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
        val rows = if (gridChoices) (hits.size + 1) / 2 else hits.size
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val sidePadding = (if (compactGrid) 12f else 22f) * unit
        val availableCardWidth = (measuredWidth - sidePadding * 2f).coerceAtLeast(0f)
        val choiceWidth = if (gridChoices) {
            ((availableCardWidth - 6f * unit) / 2f) * choiceButtonScale
        } else {
            availableCardWidth * choiceButtonScale
        }
        val cardHeight = when {
            compactGrid -> 44f * unit * choiceButtonScale
            gridChoices -> PlainGameButtonAssets.heightForWidth(choiceWidth, PlainGameButtonAssets.Style.SHORT)
            else -> PlainGameButtonAssets.heightForWidth(
                choiceWidth,
                PlainGameButtonAssets.Style.LONG,
            )
        }.coerceAtLeast(44f * unit * choiceButtonScale)
        val gap = if (compactGrid) {
            6f * unit
        } else {
            10f * unit
        }
        val desiredHeight = if (compactGrid) {
            84f * unit + rows * (cardHeight + gap)
        } else {
            178f * unit + rows * (cardHeight + gap)
        }
        val measuredHeight = if (!compactGrid && fullScreen && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val rows = if (gridChoices) (hits.size + 1) / 2 else hits.size
        val sidePadding = (if (compactGrid) 12f else 22f) * unit
        val availableCardWidth = (width - sidePadding * 2f).coerceAtLeast(0f)
        val choiceWidth = if (gridChoices) {
            ((availableCardWidth - 6f * unit) / 2f) * choiceButtonScale
        } else {
            availableCardWidth * choiceButtonScale
        }
        val cardHeight = when {
            compactGrid -> 44f * unit * choiceButtonScale
            gridChoices -> PlainGameButtonAssets.heightForWidth(choiceWidth, PlainGameButtonAssets.Style.SHORT)
            else -> PlainGameButtonAssets.heightForWidth(
                choiceWidth,
                PlainGameButtonAssets.Style.LONG,
            )
        }.coerceAtLeast(44f * unit * choiceButtonScale)
        val gap = if (compactGrid) {
            6f * unit
        } else {
            10f * unit
        }
        val rowStride = cardHeight + gap - cardHeight * lowerButtonLift
        val contentHeight = if (compactGrid) {
            (84f * unit) + rows * (cardHeight + gap)
        } else {
            (178f * unit) + rows * (cardHeight + gap)
        }
        contentOffset = if (fullScreen && !compactGrid) {
            ((height - contentHeight) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        val top = contentOffset + (if (compactGrid) 70f else 153f) * unit
        if (gridChoices) {
            val columnGap = (if (compactGrid) 6f else 10f) * unit
            val cardWidth = choiceWidth
            val gridWidth = cardWidth * 2f + columnGap
            val gridLeft = (width - gridWidth) / 2f
            hits.forEachIndexed { index, hit ->
                val row = index / 2
                val column = index % 2
                val left = gridLeft + column * (cardWidth + columnGap)
                val cardTop = top + row * rowStride
                hit.rect = RectF(left, cardTop, left + cardWidth, cardTop + cardHeight)
                hit.infoRect = if (showChoiceInfo) {
                    RectF(
                        hit.rect.right - 30f * unit,
                        hit.rect.top + 6f * unit,
                        hit.rect.right - 6f * unit,
                        hit.rect.top + 30f * unit,
                    )
                } else {
                    RectF()
                }
            }
        } else {
            val cardWidth = choiceWidth
            hits.forEachIndexed { index, hit ->
                val cardTop = top + index * rowStride
                val cardLeft = (width - cardWidth) / 2f
                hit.rect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
                hit.infoRect.setEmpty()
            }
        }
        if (compactGrid) {
            closeRect.set(
                width - 52f * unit,
                4f * unit,
                width - 4f * unit,
                52f * unit,
            )
        } else {
            closeRect.setEmpty()
        }
        if (showBackButton) {
            val backSize = 34f * unit
            val backLeft = 14f * unit
            val backTop = 14f * unit
            backRect.set(backLeft, backTop, backLeft + backSize, backTop + backSize)
            backTouchRect.set(
                backLeft - 6f * unit,
                backTop - 6f * unit,
                backLeft + backSize + 6f * unit,
                backTop + backSize + 6f * unit,
            )
        } else {
            backRect.setEmpty()
            backTouchRect.setEmpty()
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

        if (showBackButton) drawBackButton(canvas)
        when {
            compactGrid -> drawCompactGridHeader(canvas, width)
            isChessFamily -> drawChessFamilyHeader(canvas, width, contentOffset)
            isMorabaraba -> drawMorabarabaHeader(canvas, width, contentOffset)
            else -> drawHeader(canvas, width, contentOffset)
        }
        hits.forEach { drawChoice(canvas, it) }
        infoIndex?.let { drawChoiceInfo(canvas, hits[it]) }
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
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
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
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
    }

    private fun drawCompactGridHeader(canvas: Canvas, width: Float) {
        val center = width / 2f
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 18f * textScale
        canvas.drawText(title, center, 27f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        subtitlePaint.textSize = 10f * textScale
        canvas.drawText(subtitle, center, 47f * unit, subtitlePaint)
        closeButtonPaint.color = Color.argb(150, 7, 21, 34)
        canvas.drawCircle(closeRect.centerX(), closeRect.centerY(), 19f * unit, closeButtonPaint)
        closeButtonTextPaint.color = Color.parseColor("#F8E6C2")
        closeButtonTextPaint.textSize = 20f * textScale
        canvas.drawText(
            "×",
            closeRect.centerX(),
            closeRect.centerY() -
                (closeButtonTextPaint.ascent() + closeButtonTextPaint.descent()) / 2f,
            closeButtonTextPaint,
        )
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
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
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
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
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
        gameIconBitmap?.let { bitmap ->
            val size = minOf(width * 0.16f, 46f * unit)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    center - size / 2f,
                    topOffset + 2f * unit,
                    center + size / 2f,
                    topOffset + 2f * unit + size,
                ),
                gameIconPaint,
            )
        } ?: run {
            crownPaint.color = Color.parseColor("#FFB45E")
            crownPaint.textSize = 22f * textScale
            canvas.drawText(headerSymbol, center, topOffset + 43f * unit, crownPaint)
        }
        // Keep the animated Morabaraba surface free of a second game-name
        // treatment; the circular pieces are its visual signature.
        titlePaint.color = Color.WHITE
        canvas.drawText(title, center, topOffset + 99f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
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
        drawWrappedSubtitle(canvas, subtitle, center, topOffset + 125f * unit, width)
    }

    private fun drawWrappedSubtitle(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        availableWidth: Float,
    ) {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var line = ""
            paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && subtitlePaint.measureText(candidate) > availableWidth - 44f * unit) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
        }
        val visible = lines.take(5).ifEmpty { listOf("") }
        val lineHeight = 16f * unit
        val firstBaseline = centerY - (visible.size - 1) * lineHeight / 2f
        visible.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + index * lineHeight, subtitlePaint)
        }
    }

    private fun drawChoice(canvas: Canvas, hit: ChoiceHit) {
        val layer = if (hit.choice.enabled) {
            null
        } else {
            canvas.saveLayerAlpha(hit.rect, 118)
        }
        try {
            if (gridChoices) {
                drawGridChoice(canvas, hit)
                return
            }
            if (useLabelOnlyChoices) {
                val rect = hit.rect
                val pressed = pressedIndex == hit.index
                drawChessWoodButton(canvas, rect, pressed, unit)
                val top = rect.top + if (pressed) 2f * unit else 0f
                drawLabelOnlyChoice(canvas, rect, top, hit.choice)
                return
            }
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
                canvas.drawText(hit.choice.label.asOptionItalicText(), rect.centerX(), rect.top + 56f * unit, labelPaint)
                canvas.drawText(hit.choice.detail, rect.centerX(), rect.top + 74f * unit, detailPaint)
            } else {
                drawCenteredChoiceText(canvas, rect, hit.choice)
            }
            canvas.restore()
        } finally {
            layer?.let(canvas::restoreToCount)
        }
    }

    private fun drawGridChoice(canvas: Canvas, hit: ChoiceHit) {
        val rect = hit.rect
        val pressed = pressedIndex == hit.index
        drawChessWoodButton(canvas, rect, pressed, unit)
        val top = rect.top + if (pressed) 2f * unit else 0f

        if (useLabelOnlyChoices) {
            drawLabelOnlyChoice(canvas, rect, top, hit.choice)
            return
        }
        if (hit.assetBitmap != null) {
            val tokenHeight = if (compactGrid) 18f else 34f
            val tokenWidth = tokenHeight * hit.assetBitmap.width.toFloat() /
                hit.assetBitmap.height.toFloat()
            val tokenTop = top + (if (compactGrid) 3f else 4f) * unit
            canvas.drawBitmap(
                hit.assetBitmap,
                null,
                RectF(
                    rect.centerX() - tokenWidth * unit / 2f,
                    tokenTop,
                    rect.centerX() + tokenWidth * unit / 2f,
                    tokenTop + tokenHeight * unit,
                ),
                choiceAssetPaint,
            )
        } else {
            iconPaint.color = Color.parseColor("#63301F")
            iconPaint.textSize = if (compactGrid) 15f * textScale else 21f * textScale
            canvas.drawText(
                hit.choice.symbol,
                rect.centerX(),
                top + (if (compactGrid) 18f else 31f) * unit,
                iconPaint,
            )
        }
        labelPaint.color = Color.parseColor("#4A1714")
        labelPaint.textSize = if (compactGrid) 13f * textScale else 16f * textScale
        canvas.drawText(
            hit.choice.label,
            rect.centerX(),
            top + (if (compactGrid) 36f else 62f) * unit,
            labelPaint,
        )

        detailPaint.color = Color.parseColor("#6A2D1B")
        detailPaint.textSize = if (compactGrid) 9f * textScale else 11f * textScale
        canvas.drawText(
            hit.choice.detail,
            rect.centerX(),
            top + (if (compactGrid) 48f else 79f) * unit,
            detailPaint,
        )
        if (showChoiceInfo) {
            canvas.drawCircle(
                hit.infoRect.centerX(),
                hit.infoRect.centerY(),
                (if (compactGrid) 7f else 9f) * unit,
                infoCirclePaint,
            )
            canvas.drawText(
                "i",
                hit.infoRect.centerX(),
                hit.infoRect.centerY() - (infoTextPaint.ascent() + infoTextPaint.descent()) / 2f,
                infoTextPaint,
            )
        }
    }

    private fun drawBackButton(canvas: Canvas) {
        GamesSelectionBackButton.draw(canvas, backRect, unit)
    }

    private fun requestBack() {
        onBackRequested?.invoke()
            ?: onDismissRequested?.invoke()
            ?: (context as? android.app.Activity)?.onBackPressed()
    }

    private fun loadAssetBitmap(assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun drawChoiceInfo(canvas: Canvas, hit: ChoiceHit) {
        canvas.drawColor(Color.argb(165, 0, 0, 0))
        val panelWidth = (width - 44f * unit).coerceAtMost(340f * unit)
        val panelHeight = 164f * unit
        val panel = RectF(
            (width - panelWidth) / 2f,
            (height - panelHeight) / 2f,
            (width + panelWidth) / 2f,
            (height + panelHeight) / 2f,
        )
        canvas.drawRoundRect(panel, 14f * unit, 14f * unit, infoPanelPaint)
        canvas.drawRoundRect(panel, 14f * unit, 14f * unit, infoPanelBorderPaint)
        canvas.drawText(hit.choice.label, panel.centerX(), panel.top + 42f * unit, infoPanelTitlePaint)
        canvas.drawText(hit.choice.detail, panel.centerX(), panel.top + 82f * unit, infoPanelDetailPaint)
        canvas.drawText("Tap anywhere to close", panel.centerX(), panel.bottom - 24f * unit, infoPanelHintPaint)
    }

    private fun drawDraughtsChoice(canvas: Canvas, hit: ChoiceHit) {
        val rect = hit.rect
        val pressed = pressedIndex == hit.index
        drawDraughtsButton(canvas, rect, pressed, unit)
        val top = rect.top + if (pressed) 2f * unit else 0f
        if (useLabelOnlyChoices) {
            drawLabelOnlyChoice(canvas, rect, top, hit.choice)
            return
        }

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#4B211F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#321718")
            canvas.drawText(hit.choice.label.asOptionItalicText(), rect.centerX(), top + 56f * unit, labelPaint)
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
        if (useLabelOnlyChoices) {
            drawLabelOnlyChoice(canvas, rect, top, hit.choice)
            return
        }

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#4B211F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#321718")
            canvas.drawText(hit.choice.label.asOptionItalicText(), rect.centerX(), top + 56f * unit, labelPaint)
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
        if (useLabelOnlyChoices) {
            drawLabelOnlyChoice(canvas, rect, top, hit.choice)
            return
        }

        if (hit.choice.symbol.isNotBlank()) {
            iconPaint.color = Color.parseColor("#63301F")
            iconPaint.textSize = 23f * textScale
            canvas.drawText(hit.choice.symbol, rect.centerX(), top + 29f * unit, iconPaint)
            labelPaint.color = Color.parseColor("#4A1714")
            canvas.drawText(hit.choice.label.asOptionItalicText(), rect.centerX(), top + 56f * unit, labelPaint)
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

    private fun drawLabelOnlyChoice(
        canvas: Canvas,
        rect: RectF,
        top: Float,
        choice: Choice,
    ) {
        val displayLabel = choice.label.asOptionItalicText()
        val drawnRect = RectF(
            rect.left,
            top,
            rect.right,
            rect.bottom + (top - rect.top),
        )
        plainChoiceLabelPaint.textSize = min(21f * textScale, drawnRect.height() * 0.28f)
        val maxTextWidth = (drawnRect.width() - 24f * unit).coerceAtLeast(1f)
        while (
            plainChoiceLabelPaint.textSize > 12f * textScale &&
            plainChoiceLabelPaint.measureText(displayLabel) > maxTextWidth
        ) {
            plainChoiceLabelPaint.textSize *= 0.94f
        }
        val measuredWidth = plainChoiceLabelPaint.measureText(displayLabel)
        if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
            plainChoiceLabelPaint.textSize *= maxTextWidth / measuredWidth
        }
        val metrics = plainChoiceLabelPaint.fontMetrics
        val baseline = drawnRect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            displayLabel,
            drawnRect.centerX(),
            baseline,
            plainChoiceLabelPaint,
        )
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
        canvas.drawText(choice.label.asOptionItalicText(), rect.centerX(), labelBaseline, labelPaint)
        canvas.drawText(choice.detail, rect.centerX(), detailBaseline, detailPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (infoIndex != null) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                infoIndex = null
                invalidate()
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressedBack = showBackButton && backTouchRect.contains(event.x, event.y)
                if (pressedBack) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                    return true
                }
                pressedClose = compactGrid && closeRect.contains(event.x, event.y)
                if (pressedClose) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                    return true
                }
                val hit = hits.firstOrNull { it.rect.contains(event.x, event.y) }
                pressedInfoIndex = if (gridChoices && showChoiceInfo &&
                    hit?.infoRect?.contains(event.x, event.y) == true
                ) {
                    hit.index
                } else {
                    null
                }
                pressedIndex = if (pressedInfoIndex == null) hit?.index else null
                pressedIndex?.let { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    if (!isChessFamily && !isMorabaraba) pressedIndex?.let { animateScale(it, 1f) }
                    pressedInfoIndex = null
                    pressedClose = false
                    pressedBack = false
                    pressedIndex = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressedBack) {
                    val shouldGoBack = backTouchRect.contains(event.x, event.y)
                    pressedBack = false
                    if (shouldGoBack) {
                        SoundPlayer.play("ui_click")
                        requestBack()
                    }
                    invalidate()
                    return true
                }
                if (pressedClose) {
                    val shouldDismiss = closeRect.contains(event.x, event.y)
                    pressedClose = false
                    if (shouldDismiss) {
                        SoundPlayer.play("ui_click")
                        onDismissRequested?.invoke()
                    }
                    invalidate()
                    return true
                }
                val selectedInfo = pressedInfoIndex
                pressedInfoIndex = null
                if (selectedInfo != null) {
                    if (hits[selectedInfo].infoRect.contains(event.x, event.y)) {
                        infoIndex = selectedInfo
                    }
                    pressedIndex = null
                    invalidate()
                    return true
                }
                val selected = pressedIndex
                if (!isChessFamily && !isMorabaraba) selected?.let { animateScale(it, 1f) }
                if (selected != null && hits[selected].rect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    if (hits[selected].choice.enabled) {
                        onChoiceSelected?.invoke(selected)
                    }
                } else if (dismissOnEmptyTap) {
                    onDismissRequested?.invoke()
                }
                pressedIndex = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isChessFamily && !isMorabaraba) pressedIndex?.let { animateScale(it, 1f) }
                pressedInfoIndex = null
                pressedClose = false
                pressedBack = false
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