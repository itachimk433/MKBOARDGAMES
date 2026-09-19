package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Chess board setup surface shown after choosing a match type.
 */
class ChessBoardSelectionView(
    context: Context,
    private val matchLabel: String,
) : View(context) {

    // The supplied frame has transparent padding around the visible wood and
    // the board opening. Align the artwork to that opening instead of scaling
    // the complete 600dp source image to the card.
    private companion object {
        const val FRAME_INNER_LEFT = 104f
        const val FRAME_INNER_TOP = 114f
        const val FRAME_INNER_RIGHT = 498f
        const val FRAME_INNER_BOTTOM = 398f
        const val FRAME_SOURCE_SIZE = 600f
    }

    var onSelectionConfirmed: ((ChessBoardStyle) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val styles = ChessBoardStyle.entries.toList()
    private val styleNames = listOf(
        "Canvas Board" to "Clean and modern",
        "Classic Wood" to "Warm tournament feel",
        "Supplied Wood" to "Rich natural grain",
        "Realistic Dark" to "High-contrast frame",
        "Black & White" to "Bold monochrome",
    )
    private val boardBitmaps = mapOf(
        ChessBoardStyle.CLASSIC_WOOD to loadBitmap("chess_board.jpg"),
        ChessBoardStyle.SUPPLIED_WOOD to loadBitmap("chess_board_wood.jpg"),
        ChessBoardStyle.BLACK_WHITE to loadBitmap("chess_board_black_white.png"),
    )
    private val frameBitmap = loadBitmap("board_selection_frame.webp")
    private val realisticBitmap = createRealisticPreview()
    private val cardRects = styles.map { RectF() }
    private val imageRects = styles.map { RectF() }
    private val continueRect = RectF()
    private val backRect = RectF()
    private val backTouchRect = RectF()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = Color.argb(52, 0, 0, 0) }
    private val dimPaint = Paint().apply { color = Color.argb(122, 2, 9, 18) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(25f)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BFD2D4")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }
    private val boardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(14f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val continuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val continueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F6D78F")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(9f)
    }

    private var selectedStyle: Int? = null
    private var pressedStyle: Int? = null
    private var pressedContinue = false
    private var pressedBack = false
    private var backgroundPhase = 0f
    private val backgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 36_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            backgroundPhase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    init {
        isClickable = true
        contentDescription = "Choose a Chess board"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        backgroundAnimator.start()
    }

    override fun onDetachedFromWindow() {
        backgroundAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val top = dp(132f).coerceAtMost(height * 0.21f)
        val horizontalPadding = dp(18f)
        val gap = dp(9f)
        val cardWidth = ((width - horizontalPadding * 2f - gap) / 2f).coerceAtLeast(1f)
        val rowCount = (styles.size + 1) / 2
        val controlsHeight = dp(98f)
        val availableHeight =
            (height - top - controlsHeight - dp(16f)).coerceAtLeast(dp(90f))
        val rowGap = if (rowCount > 1) gap else 0f
        val cardHeight = min(
            cardWidth,
            ((availableHeight - rowGap * (rowCount - 1)) / rowCount)
                .coerceAtLeast(dp(90f)),
        )

        styles.forEachIndexed { index, _ ->
            val row = index / 2
            val column = index % 2
            val itemsInRow = min(2, styles.size - row * 2)
            val rowWidth = itemsInRow * cardWidth + (itemsInRow - 1) * gap
            val left = (width - rowWidth) / 2f + column * (cardWidth + gap)
            val rowTop = top + row * (cardHeight + rowGap)
            cardRects[index].set(left, rowTop, left + cardWidth, rowTop + cardHeight)
            val imageWidth = cardWidth - dp(34f)
            val imageHeight = imageWidth *
                (FRAME_INNER_BOTTOM - FRAME_INNER_TOP) /
                (FRAME_INNER_RIGHT - FRAME_INNER_LEFT)
            imageRects[index].set(
                left + dp(17f),
                rowTop + dp(18f),
                left + dp(17f) + imageWidth,
                rowTop + dp(18f) + imageHeight,
            )
        }

        val backSize = dp(34f)
        val backLeft = dp(14f)
        val backTop = dp(14f)
        backRect.set(backLeft, backTop, backLeft + backSize, backTop + backSize)
        backTouchRect.set(
            backLeft - dp(6f),
            backTop - dp(6f),
            backLeft + backSize + dp(6f),
            backTop + backSize + dp(6f),
        )

        val continueSize = dp(50f)
        val continueLeft = width - dp(16f) - continueSize
        val continueTop = height - dp(78f)
        continueRect.set(continueLeft, continueTop, continueLeft + continueSize, continueTop + continueSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val phase = backgroundPhase * (Math.PI.toFloat() * 2f)
        val topColor = blendColor(
            Color.parseColor("#102C32"),
            Color.parseColor("#27355E"),
            (sin(phase * 0.55f) + 1f) / 2f,
        )
        val bottomColor = blendColor(
            Color.parseColor("#061321"),
            Color.parseColor("#112C46"),
            (cos(phase * 0.42f) + 1f) / 2f,
        )
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            topColor,
            bottomColor,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null
        drawGlow(
            canvas,
            width * (0.18f + 0.08f * sin(phase * 0.7f)),
            height * (0.22f + 0.05f * cos(phase * 0.5f)),
            min(width, height) * 0.48f,
            Color.rgb(34, 156, 184),
        )
        drawGlow(
            canvas,
            width * (0.86f + 0.06f * cos(phase * 0.48f)),
            height * (0.72f + 0.05f * sin(phase * 0.62f)),
            min(width, height) * 0.42f,
            Color.rgb(102, 76, 182),
        )

        drawBackButton(canvas)
        canvas.drawText("Choose your board", width / 2f, dp(68f), titlePaint)
        canvas.drawText(
            "Playing $matchLabel · Select a board",
            width / 2f,
            dp(101f),
            subtitlePaint,
        )

        styles.forEachIndexed { index, style -> drawBoardCard(canvas, index, style) }
        drawContinueButton(canvas)
        canvas.drawText(
            "Choose a board, then tap the arrow to continue.",
            width / 2f,
            height - dp(12f),
            footerPaint,
        )
    }

    private fun drawBoardCard(canvas: Canvas, index: Int, style: ChessBoardStyle) {
        val card = cardRects[index]
        val image = imageRects[index]
        val pressed = pressedStyle == index
        val selected = selectedStyle == index
        val offset = if (pressed) dp(2f) else 0f
        val drawnCard = RectF(card.left, card.top + offset, card.right, card.bottom + offset)
        val drawnImage = RectF(image.left, image.top + offset, image.right, image.bottom + offset)

        drawCardBase(canvas, drawnCard)
        canvas.save()
        canvas.clipRect(drawnImage)
        drawPreview(canvas, style, drawnImage)
        canvas.drawRect(drawnImage, scrimPaint)
        canvas.restore()
        frameBitmap?.let { drawFrameAlignedToImage(canvas, it, drawnImage, drawnCard) }

        val name = styleNames[index].first
        canvas.drawText(name, drawnCard.centerX(), drawnCard.bottom - dp(26f), boardTitlePaint)

        if (selectedStyle != null && !selected) {
            canvas.drawRoundRect(drawnCard, dp(10f), dp(10f), dimPaint)
        }
        if (selected) {
            selectionBorderPaint.color = Color.parseColor("#F6D78F")
            selectionBorderPaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(drawnCard, dp(10f), dp(10f), selectionBorderPaint)
        }
    }

    private fun drawPreview(canvas: Canvas, style: ChessBoardStyle, destination: RectF) {
        val bitmap = when (style) {
            ChessBoardStyle.CANVAS -> null
            ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticBitmap
            else -> boardBitmaps[style]
        }
        if (bitmap != null) {
            drawBitmapCover(canvas, bitmap, destination)
            return
        }

        val square = destination.width() / 8f
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAD9B8") }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A5135") }
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                light.color = if ((row + column) % 2 == 0) {
                    Color.parseColor("#F0D9B5")
                } else {
                    Color.parseColor("#B58863")
                }
                canvas.drawRect(
                    destination.left + column * square,
                    destination.top + row * square,
                    destination.left + (column + 1) * square,
                    destination.top + (row + 1) * square,
                    light,
                )
            }
        }
        val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = square * 0.67f
        }
        val pieces = arrayOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜")
        for (column in pieces.indices) {
            piecePaint.color = Color.parseColor("#33251D")
            canvas.drawText(
                pieces[column],
                destination.left + (column + 0.5f) * square,
                destination.top + square * 0.82f,
                piecePaint,
            )
            piecePaint.color = Color.parseColor("#F7E8CF")
            canvas.drawText(
                "♟",
                destination.left + (column + 0.5f) * square,
                destination.top + square * 1.82f,
                piecePaint,
            )
        }
        dark.color = Color.argb(70, 0, 0, 0)
        canvas.drawRect(destination, dark)
    }

    private fun drawContinueButton(canvas: Canvas) {
        val enabled = selectedStyle != null
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) Color.parseColor("#E3B86A") else Color.argb(80, 180, 197, 201)
        }
        canvas.drawRoundRect(continueRect, dp(12f), dp(12f), buttonPaint)
        continuePaint.color = if (enabled) Color.WHITE else Color.argb(110, 220, 230, 232)
        val arrowOffset = if (pressedContinue) dp(2f) else 0f
        canvas.save()
        canvas.translate(arrowOffset, arrowOffset)
        val centerX = continueRect.centerX()
        val centerY = continueRect.centerY()
        canvas.drawLine(centerX - dp(10f), centerY, centerX + dp(9f), centerY, continuePaint)
        canvas.drawLine(centerX + dp(9f), centerY, centerX + dp(1f), centerY - dp(8f), continuePaint)
        canvas.drawLine(centerX + dp(9f), centerY, centerX + dp(1f), centerY + dp(8f), continuePaint)
        canvas.restore()
        continueLabelPaint.color =
            if (enabled) Color.parseColor("#F6D78F") else Color.argb(120, 191, 210, 212)
        canvas.drawText("START", continueRect.centerX(), continueRect.bottom + dp(12f), continueLabelPaint)
    }

    private fun drawBackButton(canvas: Canvas) {
        GamesSelectionBackButton.draw(canvas, backRect, density)
    }

    private fun drawCardBase(canvas: Canvas, rect: RectF) {
        cardPaint.color = Color.parseColor("#17262D")
        canvas.drawRoundRect(rect, dp(8f), dp(8f), cardPaint)
    }

    private fun drawFrameAlignedToImage(
        canvas: Canvas,
        frame: Bitmap,
        image: RectF,
        card: RectF,
    ) {
        val innerWidth = FRAME_INNER_RIGHT - FRAME_INNER_LEFT
        val innerHeight = FRAME_INNER_BOTTOM - FRAME_INNER_TOP
        val scale = min(image.width() / innerWidth, image.height() / innerHeight)
        val frameSize = FRAME_SOURCE_SIZE * scale
        val destination = RectF(
            image.left - FRAME_INNER_LEFT * scale,
            image.top - FRAME_INNER_TOP * scale,
            image.left - FRAME_INNER_LEFT * scale + frameSize,
            image.top - FRAME_INNER_TOP * scale + frameSize,
        )

        canvas.save()
        canvas.clipRect(card)
        canvas.drawBitmap(frame, null, destination, imagePaint)
        canvas.restore()
    }

    private fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val destinationAspect = destination.width() / destination.height()
        val source = if (sourceAspect > destinationAspect) {
            val croppedWidth = (bitmap.height * destinationAspect).toInt()
            Rect(
                (bitmap.width - croppedWidth) / 2,
                0,
                (bitmap.width + croppedWidth) / 2,
                bitmap.height,
            )
        } else {
            val croppedHeight = (bitmap.width / destinationAspect).toInt()
            Rect(
                0,
                (bitmap.height - croppedHeight) / 2,
                bitmap.width,
                (bitmap.height + croppedHeight) / 2,
            )
        }
        canvas.drawBitmap(bitmap, source, destination, imagePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedBack = backTouchRect.contains(event.x, event.y)
                pressedStyle = null
                pressedContinue = false
                if (!pressedBack) {
                    pressedStyle = cardRects.indexOfFirst { it.contains(event.x, event.y) }
                        .takeIf { it >= 0 }
                    if (pressedStyle == null &&
                        continueRect.contains(event.x, event.y)
                    ) {
                        pressedContinue = true
                    }
                }
                if (pressedBack || pressedStyle != null || pressedContinue) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedBack && !backTouchRect.contains(event.x, event.y)) pressedBack = false
                if (pressedStyle != null && !cardRects[pressedStyle!!].contains(event.x, event.y)) {
                    pressedStyle = null
                }
                if (pressedContinue && !continueRect.contains(event.x, event.y)) {
                    pressedContinue = false
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tappedStyle = pressedStyle?.takeIf { cardRects[it].contains(event.x, event.y) }
                val tappedContinue = pressedContinue && continueRect.contains(event.x, event.y)
                val tappedBack = pressedBack && backTouchRect.contains(event.x, event.y)
                pressedStyle = null
                pressedContinue = false
                pressedBack = false
                when {
                    tappedBack -> {
                        SoundPlayer.play("ui_click")
                        onBackClicked?.invoke()
                    }
                    tappedStyle != null -> {
                        SoundPlayer.play("ui_click")
                        selectedStyle = if (selectedStyle == tappedStyle) null else tappedStyle
                    }
                    tappedContinue && selectedStyle != null -> {
                        SoundPlayer.play("ui_click")
                        onSelectionConfirmed?.invoke(styles[selectedStyle!!])
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedStyle = null
                pressedContinue = false
                pressedBack = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun loadBitmap(assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun createRealisticPreview(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2F3540") }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), frame)
        val cell = 26f
        val inset = 24f
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                frame.color = if ((row + column) % 2 == 0) {
                    Color.parseColor("#EAE5DA")
                } else {
                    Color.parseColor("#30363F")
                }
                canvas.drawRect(
                    inset + column * cell,
                    inset + row * cell,
                    inset + (column + 1) * cell,
                    inset + (row + 1) * cell,
                    frame,
                )
            }
        }
        return bitmap
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glowPaint.shader = android.graphics.RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
    }

    private fun blendColor(start: Int, end: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * t).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * t).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t).toInt(),
        )
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * textScale
}