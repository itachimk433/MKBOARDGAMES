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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

/**
 * A focused setup surface for Chess.
 *
 * The regular game catalogue is intentionally left alone. Chess gets a
 * dedicated board-like surface because it is the most frequently opened game
 * and benefits from making the next action obvious at a glance.
 */
class ChessMenuView(
    context: Context,
    private val hasResumeMatch: Boolean,
    private val gameLabel: String = "C H E S S",
) : View(context) {

    var onVsAi: (() -> Unit)? = null
    var onTwoPlayers: (() -> Unit)? = null
    var onHowToPlay: (() -> Unit)? = null
    var onResumeMatch: (() -> Unit)? = null

    private data class MenuAction(
        val label: String,
        val detail: String,
        val symbol: String,
        val accent: Int,
        val action: () -> Unit,
        var rect: RectF = RectF(),
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val unit = density.coerceAtLeast(1f)
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val fullScreen = isFullScreenStyledGameLabel(gameLabel)
    private val isChess = gameLabel.replace(" ", "").equals("CHESS", ignoreCase = true)
    private val isDraughts = isDraughtsStyledLabel(gameLabel)
    private val isOthello = isOthelloStyledLabel(gameLabel)
    private val isMorabaraba = isMorabarabaStyledLabel(gameLabel)
    private val isFoxAndGeese = gameLabel.replace(" ", "").replace("·", "").equals("FOX&GEESE", ignoreCase = true)
    private val isGo = gameLabel.replace(" ", "").equals("GO", ignoreCase = true)
    private val isShogi = gameLabel.replace(" ", "").equals("SHOGI", ignoreCase = true)
    private val isXiangqi = gameLabel.replace(" ", "").equals("XIANGQI", ignoreCase = true)
    private val isTicTacToe = gameLabel.replace(" ", "").replace("·", "").equals("TICTACTOE", ignoreCase = true)
    private val isConnectFour = gameLabel.replace(" ", "").replace("·", "").equals("CONNECTFOUR", ignoreCase = true)
    private val isLudo = gameLabel.replace(" ", "").equals("LUDO", ignoreCase = true)
    private val isMancala = gameLabel.replace(" ", "").equals("MANCALA", ignoreCase = true)
    private val isYote = gameLabel.replace(" ", "").replace("É", "E").equals("YOTE", ignoreCase = true)
    private val isOnitama = gameLabel.replace(" ", "").equals("ONITAMA", ignoreCase = true)
    private val isChessFamily =
        isChess || isDraughts || isOthello || isFoxAndGeese || isGo || isShogi ||
            isXiangqi || isTicTacToe || isConnectFour || isLudo || isMancala || isYote || isOnitama
    private val isInternationalDraughts =
        gameLabel.replace(" ", "").equals("INTLDRAUGHTS", ignoreCase = true)
    private val contentHeightDp = when {
        (isChessFamily || isMorabaraba) && hasResumeMatch -> 548f
        isChessFamily || isMorabaraba -> 500f
        hasResumeMatch -> 462f
        else -> 414f
    }
    private val gameHomeIconBitmap = run {
        val assetName = when {
            isChess -> "chess_home_icon.png"
            isDraughts && isInternationalDraughts -> "international_draughts_home_icon.png"
            isDraughts -> "draughts_home_icon.png"
            isOthello -> "othello_home_icon.png"
            isMorabaraba -> "morabaraba_home_icon.png"
            isFoxAndGeese -> "fox_and_geese_home_icon.png"
            isGo -> "go_home_icon.png"
            isShogi -> "shogi_home_icon.png"
            isXiangqi -> "xiangqi_home_icon.png"
            isTicTacToe -> "tictactoe_home_icon.png"
            isConnectFour -> "connect_four_home_icon.png"
            isLudo -> "ludo_home_icon.png"
            isMancala -> "mancala_home_icon.webp"
            isYote -> "yote_home_icon.webp"
            isOnitama -> "onitama_home_icon.webp"
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
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
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
        textSize = 27f * textScale
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 12f * textScale
    }
    private val actionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 14f * textScale
    }
    private val actionDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }
    private val actionSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
    }
    private val resumePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * textScale
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }
    private val chessBackdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chessHeroPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gameIconTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.04f
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val chessWavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chessWaveEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * unit
    }
    private val floatingPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val chessStarsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chessWavePaths = Array(3) { Path() }
    private val chessWaveEdgePaths = Array(3) { Path() }
    private val backgroundSpeedMultiplier = 2f
    private val floatingPieceSpeedMultiplier = 2.3f

    private data class FloatingPiece(
        val symbol: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val horizontalRange: Float,
        val verticalRange: Float,
        val orbitSpeed: Float,
        val phase: Float,
        val alpha: Int,
        val tint: Int,
    )

    private data class WaveBand(
        val heightRatio: Float,
        val amplitudeRatio: Float,
        val color: Int,
    )

    private val floatingPieces = listOf(
        FloatingPiece("♞", 0.14f, 0.20f, 30f, 0.075f, 0.085f, 0.92f, 0.5f, 86, Color.rgb(174, 220, 255)),
        FloatingPiece("♟", 0.84f, 0.18f, 24f, 0.10f, 0.06f, 0.72f, 2.3f, 72, Color.rgb(149, 193, 255)),
        FloatingPiece("♜", 0.82f, 0.48f, 28f, 0.13f, 0.095f, 0.64f, 4.4f, 68, Color.rgb(165, 238, 222)),
        FloatingPiece("♗", 0.16f, 0.69f, 25f, 0.085f, 0.095f, 0.78f, 1.4f, 64, Color.rgb(196, 173, 255)),
        FloatingPiece("♛", 0.78f, 0.78f, 32f, 0.13f, 0.075f, 0.52f, 5.1f, 60, Color.rgb(225, 190, 255)),
        FloatingPiece("♙", 0.30f, 0.86f, 22f, 0.10f, 0.06f, 0.66f, 3.2f, 56, Color.rgb(133, 222, 223)),
    )
    private val chessWaveBands = arrayOf(
        WaveBand(0.28f, 0.045f, Color.rgb(64, 167, 218)),
        WaveBand(0.49f, 0.06f, Color.rgb(74, 117, 225)),
        WaveBand(0.72f, 0.052f, Color.rgb(126, 83, 213)),
    )

    private var backgroundPhase = 0f
    private val backgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 42_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            backgroundPhase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    private lateinit var actions: List<MenuAction>
    private var pressedAction: MenuAction? = null
    private var resumePressed = false
    private var actionScale = HashMap<String, Float>()
    private var actionAnimator: ValueAnimator? = null
    private val resumeRect = RectF()
    private var downX = 0f
    private var downY = 0f
    private var contentOffset = 0f

    init {
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isChessFamily || isMorabaraba) {
            backgroundAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        backgroundAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = contentHeightDp * unit
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = if (fullScreen && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val contentHeight = contentHeightDp * unit
        contentOffset = if (fullScreen) {
            ((height - contentHeight) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        val sidePadding = 18f * unit
        val gap = 10f * unit
        val actionWidth = (width - sidePadding * 2f - gap) / 2f
        val actionTop = contentOffset +
            (if (isChessFamily || isMorabaraba) 244f else 164f) * unit
        val actionHeight = 82f * unit

        actions = listOf(
            MenuAction(
                label = "vs CPU",
                detail = "Challenge the board",
                symbol = when {
                    isChess -> "♞"
                    isChessFamily || isMorabaraba -> "●"
                    else -> ""
                },
                accent = Color.parseColor("#E3B86A"),
                action = { onVsAi?.invoke() },
            ),
            MenuAction(
                label = if (isLudo) "4 Players" else "2 Players",
                detail = "Play on one board",
                symbol = when {
                    isChess -> "♙"
                    isChessFamily || isMorabaraba -> "○"
                    else -> ""
                },
                accent = Color.parseColor("#8EC7B9"),
                action = { onTwoPlayers?.invoke() },
            ),
            MenuAction(
                label = "How To Play",
                detail = "Learn the essentials",
                symbol = "?",
                accent = Color.parseColor("#A9B6E8"),
                action = { onHowToPlay?.invoke() },
            ),
        )

        actions.forEachIndexed { index, action ->
            if (index < 2) {
                val left = sidePadding + index * (actionWidth + gap)
                action.rect = RectF(left, actionTop, left + actionWidth, actionTop + actionHeight)
            } else {
                val top = actionTop + actionHeight + gap
                action.rect = RectF(sidePadding, top, width - sidePadding, top + actionHeight)
            }
            actionScale[action.label] = 1f
        }
        resumeRect.set(
            width * 0.18f,
            contentOffset + contentHeight - 56f * unit,
            width * 0.82f,
            contentOffset + contentHeight - 8f * unit,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (isChessFamily || isMorabaraba) {
            drawChessBackdrop(
                canvas,
                width,
                height,
                drawFloatingPieces = isChess,
            )
            drawChessHero(canvas, width, contentOffset)
            drawChessHeader(canvas, width, contentOffset)
        } else {
            val corner = 12f * unit
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
                canvas.drawRoundRect(0f, 0f, width, height, corner, corner, surfacePaint)
            }
            surfacePaint.shader = null
        }

        if (!isChessFamily && !isMorabaraba) {
            drawHeader(canvas, width, contentOffset)
        }
        actions.forEach { drawAction(canvas, it) }
        if (hasResumeMatch) {
            drawResumeAction(canvas, width)
        } else {
            canvas.drawText(
                "Choose your move. The board is waiting.",
                width / 2f,
                contentOffset + contentHeightDp * unit - 17f * unit,
                footerPaint,
            )
        }
    }

    private fun drawChessBackdrop(
        canvas: Canvas,
        width: Float,
        height: Float,
        drawFloatingPieces: Boolean,
    ) {
        val backgroundProgress = (backgroundPhase * backgroundSpeedMultiplier) % 1f
        val phase = backgroundProgress * (2f * PI.toFloat())
        val paletteSlot = backgroundProgress * chessPalettes.size
        val paletteIndex = paletteSlot.toInt().coerceIn(0, chessPalettes.lastIndex)
        val paletteProgress = smoothStep(paletteSlot - paletteIndex)
        val paletteStart = chessPalettes[paletteIndex]
        val paletteEnd = chessPalettes[(paletteIndex + 1) % chessPalettes.size]
        val palette = IntArray(paletteStart.size) { index ->
            blendColor(paletteStart[index], paletteEnd[index], paletteProgress)
        }

        chessBackdropPaint.shader = LinearGradient(
            0f,
            0f,
            width * 0.9f,
            height,
            palette,
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, chessBackdropPaint)
        chessBackdropPaint.shader = null

        drawChessWaves(canvas, width, height, phase)

        drawChessGlow(
            canvas,
            width * (0.12f + 0.055f * sin(phase * 0.72f).toFloat()),
            height * (0.18f + 0.045f * cos(phase * 0.54f).toFloat()),
            min(width, height) * 0.58f,
            blendColor(Color.rgb(53, 137, 220), Color.rgb(115, 89, 226), (sin(phase * 0.25f) + 1f) / 2f),
        )
        drawChessGlow(
            canvas,
            width * (0.9f + 0.05f * cos(phase * 0.48f).toFloat()),
            height * (0.64f + 0.06f * sin(phase * 0.66f).toFloat()),
            min(width, height) * 0.5f,
            blendColor(Color.rgb(22, 194, 190), Color.rgb(74, 140, 235), (sin(phase * 0.32f + 1.4f) + 1f) / 2f),
        )
        drawChessGlow(
            canvas,
            width * (0.46f + 0.08f * sin(phase * 0.4f + 2f).toFloat()),
            height * (1.02f + 0.045f * cos(phase * 0.58f).toFloat()),
            min(width, height) * 0.68f,
            blendColor(Color.rgb(71, 37, 134), Color.rgb(19, 120, 143), (sin(phase * 0.28f + 2f) + 1f) / 2f),
        )

        val stars = chessStarsPaint
        for (index in 0 until 62) {
            val baseX = ((index * 83 + 37) % 1000) / 1000f * width
            val baseY = ((index * 47 + 23) % 920) / 1000f * height
            val x = baseX + sin(phase * (0.18f + (index % 3) * 0.07f) + index).toFloat() * 7f * unit
            val y = baseY + cos(phase * (0.16f + (index % 4) * 0.05f) + index * 0.7f).toFloat() * 5f * unit
            val radius = (0.55f + (index % 4) * 0.45f) * unit
            val twinkle = ((sin(phase * (0.7f + (index % 4) * 0.12f) + index) + 1f) * 0.5f).toFloat()
            stars.color = Color.argb(58 + ((index % 5) * 22 * twinkle).toInt(), 220, 241, 255)
            canvas.drawCircle(x, y, radius, stars)
            if (index % 11 == 0) {
                stars.color = Color.argb(100 + (30f * twinkle).toInt(), 178, 224, 255)
                canvas.drawCircle(x, y, radius * (2.1f + 0.5f * twinkle), stars)
            }
        }
        stars.color = Color.argb(34, 88, 207, 220)
        canvas.drawCircle(
            width * (0.08f + 0.025f * sin(phase * 0.4f).toFloat()),
            height * 0.72f,
            min(width, height) * 0.18f,
            stars,
        )
        stars.color = Color.argb(25, 150, 109, 226)
        canvas.drawCircle(
            width * 0.88f,
            height * (0.3f + 0.03f * cos(phase * 0.52f).toFloat()),
            min(width, height) * 0.2f,
            stars,
        )
        if (drawFloatingPieces) {
            drawFloatingChessPieces(canvas, width, height)
        }
    }

    private fun drawChessWaves(canvas: Canvas, width: Float, height: Float, phase: Float) {
        val margin = width * 0.14f
        val segmentCount = 64

        chessWaveBands.forEachIndexed { bandIndex, band ->
            val path = chessWavePaths[bandIndex]
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
                val secondary = cos(progress * (4.5f * PI.toFloat()) - bandPhase * 0.72f).toFloat() * amplitude * 0.28f
                path.lineTo(x, baseY + wave + secondary)
            }
            path.lineTo(width + margin, height)
            path.close()

            chessWavePaint.color = Color.argb(
                18 + bandIndex * 5,
                Color.red(band.color),
                Color.green(band.color),
                Color.blue(band.color),
            )
            canvas.drawPath(path, chessWavePaint)

            val edge = chessWaveEdgePaths[bandIndex]
            edge.reset()
            edge.moveTo(-margin, baseY)
            for (step in 0..segmentCount) {
                val progress = step / segmentCount.toFloat()
                val x = -margin + (width + margin * 2f) * progress
                val wave = sin(progress * (2.15f * PI.toFloat()) + bandPhase).toFloat() * amplitude
                val secondary = cos(progress * (4.5f * PI.toFloat()) - bandPhase * 0.72f).toFloat() * amplitude * 0.28f
                edge.lineTo(x, baseY + wave + secondary)
            }
            chessWaveEdgePaint.color = Color.argb(
                34 + bandIndex * 8,
                Color.red(band.color),
                Color.green(band.color),
                Color.blue(band.color),
            )
            canvas.drawPath(edge, chessWaveEdgePaint)
        }

        chessWavePaint.shader = LinearGradient(
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
        canvas.drawRect(0f, height * 0.37f, width, height * 0.64f, chessWavePaint)
        chessWavePaint.shader = null
    }

    private fun drawFloatingChessPieces(canvas: Canvas, width: Float, height: Float) {
        val pieceProgress = backgroundPhase * floatingPieceSpeedMultiplier
        val piecePhase = pieceProgress * (2f * PI.toFloat())
        floatingPieces.forEachIndexed { index, piece ->
            val primaryPhase = piecePhase * piece.orbitSpeed + piece.phase
            val secondaryPhase = piecePhase * (piece.orbitSpeed * 0.57f + 0.11f) + piece.phase * 1.7f
            val horizontalMotion = sin(primaryPhase).toFloat() * piece.horizontalRange +
                cos(secondaryPhase).toFloat() * piece.horizontalRange * 0.42f
            val verticalMotion = cos(primaryPhase * 0.83f + piece.phase * 0.35f).toFloat() * piece.verticalRange +
                sin(secondaryPhase * 1.13f).toFloat() * piece.verticalRange * 0.45f
            val x = width * (piece.x + horizontalMotion)
            val y = height * (piece.y + verticalMotion)
            val rotation = (
                sin(primaryPhase * 0.72f).toFloat() * (4f + index * 0.7f) +
                    cos(secondaryPhase * 0.65f).toFloat() * 2.5f
                )
            val size = piece.size * unit * (
                0.96f + 0.06f * sin(primaryPhase * 0.8f + piece.phase).toFloat()
                )

            floatingPiecePaint.color = Color.argb(piece.alpha, Color.red(piece.tint), Color.green(piece.tint), Color.blue(piece.tint))
            floatingPiecePaint.textSize = size
            for (copy in -1..1) {
                val drawX = x + copy * width
                canvas.save()
                canvas.rotate(rotation, drawX, y)
                canvas.drawText(piece.symbol, drawX, y, floatingPiecePaint)
                canvas.restore()
            }
        }
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

    private val chessPalettes = arrayOf(
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

    private fun drawChessGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        chessBackdropPaint.shader = RadialGradient(
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
        canvas.drawCircle(x, y, radius, chessBackdropPaint)
        chessBackdropPaint.shader = null
    }

    private fun drawChessHero(canvas: Canvas, width: Float, topOffset: Float) {
        gameHomeIconBitmap?.let { bitmap ->
            val size = min(width * 0.36f, 150f * unit)
            val top = topOffset + 18f * unit
            chessHeroPaint.alpha = 255
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    (width - size) / 2f,
                    top,
                    (width + size) / 2f,
                    top + size,
                ),
                chessHeroPaint,
            )
        }
    }

    private fun drawDraughtsHero(canvas: Canvas, width: Float, topOffset: Float) {
        gameHomeIconBitmap?.let { bitmap ->
            val size = min(width * 0.36f, 150f * unit)
            val top = topOffset + 18f * unit
            chessHeroPaint.alpha = 255
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    (width - size) / 2f,
                    top,
                    (width + size) / 2f,
                    top + size,
                ),
                chessHeroPaint,
            )
            drawGameHeroTitle(
                canvas,
                if (isInternationalDraughts) "INTERNATIONAL DRAUGHTS" else "DRAUGHTS",
                width / 2f,
                top,
                size,
            )
        }
    }

    private fun drawOthelloHero(canvas: Canvas, width: Float, topOffset: Float) {
        gameHomeIconBitmap?.let { bitmap ->
            val size = min(width * 0.36f, 150f * unit)
            val top = topOffset + 18f * unit
            chessHeroPaint.alpha = 255
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    (width - size) / 2f,
                    top,
                    (width + size) / 2f,
                    top + size,
                ),
                chessHeroPaint,
            )
            drawGameHeroTitle(canvas, "OTHELLO", width / 2f, top, size)
        }
    }

    private fun drawGameHeroTitle(
        canvas: Canvas,
        label: String,
        centerX: Float,
        top: Float,
        size: Float,
    ) {
        val maxWidth = centerX * 2f * 0.92f
        gameIconTitlePaint.textSize = 92f * textScale
        val internationalTitleWidth = gameIconTitlePaint.measureText("INTERNATIONAL DRAUGHTS")
        if (internationalTitleWidth > maxWidth) {
            gameIconTitlePaint.textSize *= maxWidth / internationalTitleWidth
        }
        val baseline = top - 36f * unit
        val titleHeight = gameIconTitlePaint.textSize

        gameIconTitlePaint.style = Paint.Style.STROKE
        gameIconTitlePaint.strokeWidth = 5f * unit
        gameIconTitlePaint.shader = null
        gameIconTitlePaint.color = Color.argb(235, 42, 22, 16)
        gameIconTitlePaint.setShadowLayer(
            7f * unit,
            0f,
            5f * unit,
            Color.argb(210, 8, 11, 20),
        )
        canvas.drawText(label, centerX, baseline, gameIconTitlePaint)

        gameIconTitlePaint.style = Paint.Style.FILL
        gameIconTitlePaint.clearShadowLayer()
        gameIconTitlePaint.shader = LinearGradient(
            0f,
            baseline - titleHeight,
            0f,
            baseline + 4f * unit,
            intArrayOf(
                Color.parseColor("#FFF3C8"),
                Color.parseColor("#E6B86F"),
                Color.parseColor("#8A4C1F"),
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawText(label, centerX, baseline, gameIconTitlePaint)
        gameIconTitlePaint.shader = null
    }

    private fun drawDraughtsHeader(canvas: Canvas, width: Float, topOffset: Float) {
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 24f * textScale
        canvas.drawText("Choose your match", width / 2f, topOffset + 196f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(
            "Choose your side and make every capture count.",
            width / 2f,
            topOffset + 217f * unit,
            subtitlePaint,
        )
    }

    private fun drawOthelloHeader(canvas: Canvas, width: Float, topOffset: Float) {
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 24f * textScale
        canvas.drawText("Choose your match", width / 2f, topOffset + 196f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(
            "Place wisely, flip the board, and take control.",
            width / 2f,
            topOffset + 217f * unit,
            subtitlePaint,
        )
    }

    private fun drawChessHeader(canvas: Canvas, width: Float, topOffset: Float) {
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 24f * textScale
        canvas.drawText("Choose your match", width / 2f, topOffset + 196f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(
            "A good game starts with the right opponent.",
            width / 2f,
            topOffset + 217f * unit,
            subtitlePaint,
        )
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
        canvas.drawText(
            "●",
            center,
            topOffset + 43f * unit,
            actionSymbolPaint.apply {
            color = Color.parseColor("#E3B86A")
            textSize = 21f * textScale
        })
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        canvas.drawText("Choose your match", center, topOffset + 99f * unit, titlePaint)
        canvas.drawText(
            "A good game starts with the right opponent.",
            center,
            topOffset + 125f * unit,
            subtitlePaint,
        )
    }

    private fun drawAction(canvas: Canvas, action: MenuAction) {
        if (isChessFamily || isMorabaraba) {
            if (isOthello) drawOthelloAction(canvas, action) else drawChessAction(canvas, action)
            return
        }
        val scale = actionScale[action.label] ?: 1f
        val rect = action.rect
        val pressed = pressedAction == action

        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())

        panelPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(rect, 8f * unit, 8f * unit, panelPaint)
        panelBorderPaint.color = if (pressed) action.accent else Color.parseColor("#2C5960")
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * unit, rect.top + 0.5f * unit, rect.right - 0.5f * unit, rect.bottom - 0.5f * unit),
            8f * unit,
            8f * unit,
            panelBorderPaint,
        )

        if (action.symbol.isNotBlank()) {
            actionSymbolPaint.color = action.accent
            actionSymbolPaint.textSize = 20f * textScale
            canvas.drawText(action.symbol, rect.centerX(), rect.top + 25f * unit, actionSymbolPaint)
            actionLabelPaint.color = Color.WHITE
            canvas.drawText(action.label, rect.centerX(), rect.top + 52f * unit, actionLabelPaint)
            canvas.drawText(action.detail, rect.centerX(), rect.top + 69f * unit, actionDetailPaint)
        } else {
            drawCenteredActionText(canvas, rect, action)
        }
        canvas.restore()
    }

    private fun drawDraughtsAction(canvas: Canvas, action: MenuAction) {
        val rect = action.rect
        val pressed = pressedAction == action
        drawDraughtsButton(canvas, rect, pressed, unit)
        val drawnTop = rect.top + if (pressed) 2f * unit else 0f
        actionSymbolPaint.color = Color.parseColor("#4B211F")
        actionSymbolPaint.textSize = 23f * textScale
        canvas.drawText(action.symbol, rect.centerX(), drawnTop + 28f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.parseColor("#321718")
        canvas.drawText(action.label, rect.centerX(), drawnTop + 55f * unit, actionLabelPaint)
        actionDetailPaint.color = Color.parseColor("#5D2C27")
        canvas.drawText(action.detail, rect.centerX(), drawnTop + 72f * unit, actionDetailPaint)
    }

    private fun drawOthelloAction(canvas: Canvas, action: MenuAction) {
        val rect = action.rect
        val pressed = pressedAction == action
        drawOthelloButton(canvas, rect, pressed, unit)
        val drawnTop = rect.top + if (pressed) 2f * unit else 0f
        actionSymbolPaint.color = Color.parseColor("#4B211F")
        actionSymbolPaint.textSize = 23f * textScale
        canvas.drawText(action.symbol, rect.centerX(), drawnTop + 28f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.parseColor("#321718")
        canvas.drawText(action.label, rect.centerX(), drawnTop + 55f * unit, actionLabelPaint)
        actionDetailPaint.color = Color.parseColor("#5D2C27")
        canvas.drawText(action.detail, rect.centerX(), drawnTop + 72f * unit, actionDetailPaint)
    }

    private fun drawChessAction(canvas: Canvas, action: MenuAction) {
        val rect = action.rect
        val pressed = pressedAction == action
        drawChessWoodButton(canvas, rect, pressed, unit)
        val drawnTop = rect.top + if (pressed) 2f * unit else 0f
        actionSymbolPaint.color = Color.parseColor("#63301F")
        actionSymbolPaint.textSize = 23f * textScale
        canvas.drawText(action.symbol, rect.centerX(), drawnTop + 28f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.parseColor("#4A1714")
        canvas.drawText(action.label, rect.centerX(), drawnTop + 55f * unit, actionLabelPaint)
        actionDetailPaint.color = Color.parseColor("#6A2D1B")
        canvas.drawText(action.detail, rect.centerX(), drawnTop + 72f * unit, actionDetailPaint)
    }

    private fun drawCenteredActionText(canvas: Canvas, rect: RectF, action: MenuAction) {
        actionLabelPaint.color = Color.WHITE
        val labelMetrics = actionLabelPaint.fontMetrics
        val detailMetrics = actionDetailPaint.fontMetrics
        val labelHeight = labelMetrics.descent - labelMetrics.ascent
        val detailHeight = detailMetrics.descent - detailMetrics.ascent
        val gap = 3f * unit
        val groupHeight = labelHeight + gap + detailHeight
        val groupTop = rect.centerY() - groupHeight / 2f
        val labelBaseline = groupTop - labelMetrics.ascent
        val detailBaseline = groupTop + labelHeight + gap - detailMetrics.ascent
        canvas.drawText(action.label, rect.centerX(), labelBaseline, actionLabelPaint)
        canvas.drawText(action.detail, rect.centerX(), detailBaseline, actionDetailPaint)
    }

    private fun drawResumeAction(canvas: Canvas, width: Float) {
        if (isChessFamily || isMorabaraba) {
            if (isOthello) drawOthelloResumeAction(canvas, width) else drawChessResumeAction(canvas, width)
            return
        }
        panelPaint.color = if (resumePressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelPaint)
        panelBorderPaint.color = Color.parseColor("#2C5960")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelBorderPaint)
        canvas.drawText("RESUME SAVED MATCH", width / 2f, resumeRect.top + 20f * unit, resumePaint)
        canvas.drawText("Tap here to continue your last game", width / 2f, resumeRect.top + 38f * unit, footerPaint)
    }

    private fun drawDraughtsResumeAction(canvas: Canvas, width: Float) {
        drawDraughtsButton(canvas, resumeRect, resumePressed, unit)
        val drawnTop = resumeRect.top + if (resumePressed) 2f * unit else 0f
        resumePaint.color = Color.parseColor("#321718")
        canvas.drawText("RESUME SAVED MATCH", width / 2f, drawnTop + 20f * unit, resumePaint)
        footerPaint.color = Color.parseColor("#5D2C27")
        canvas.drawText("Tap here to continue your last game", width / 2f, drawnTop + 38f * unit, footerPaint)
    }

    private fun drawOthelloResumeAction(canvas: Canvas, width: Float) {
        drawOthelloButton(canvas, resumeRect, resumePressed, unit)
        val drawnTop = resumeRect.top + if (resumePressed) 2f * unit else 0f
        resumePaint.color = Color.parseColor("#321718")
        canvas.drawText("RESUME SAVED MATCH", width / 2f, drawnTop + 20f * unit, resumePaint)
        footerPaint.color = Color.parseColor("#5D2C27")
        canvas.drawText("Tap here to continue your last game", width / 2f, drawnTop + 38f * unit, footerPaint)
    }

    private fun drawChessResumeAction(canvas: Canvas, width: Float) {
        drawChessWoodButton(canvas, resumeRect, resumePressed, unit)
        val drawnTop = resumeRect.top + if (resumePressed) 2f * unit else 0f
        resumePaint.color = Color.parseColor("#4A1714")
        canvas.drawText("RESUME SAVED MATCH", width / 2f, drawnTop + 20f * unit, resumePaint)
        footerPaint.color = Color.parseColor("#6A2D1B")
        canvas.drawText("Tap here to continue your last game", width / 2f, drawnTop + 38f * unit, footerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressedAction = actions.firstOrNull { it.rect.contains(event.x, event.y) }
                if (pressedAction == null && hasResumeMatch && resumeRect.contains(event.x, event.y)) {
                    resumePressed = true
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                pressedAction?.let { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    if (!isChessFamily && !isMorabaraba) pressedAction?.let { animateAction(it, 1f) }
                    pressedAction = null
                    resumePressed = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val action = pressedAction
                if (!isChessFamily && !isMorabaraba) action?.let { animateAction(it, 1f) }
                if (action != null && action.rect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    action.action()
                } else if (hasResumeMatch && resumePressed && resumeRect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    onResumeMatch?.invoke()
                }
                pressedAction = null
                resumePressed = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isChessFamily && !isMorabaraba) pressedAction?.let { animateAction(it, 1f) }
                pressedAction = null
                resumePressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun animateAction(action: MenuAction, target: Float) {
        actionAnimator?.cancel()
        val current = actionScale[action.label] ?: 1f
        actionAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = if (target < 1f) 80L else 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                actionScale[action.label] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}