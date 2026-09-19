package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

enum class BoardSelectionPreview {
    GRID,
    CHECKERS,
    OTHELLO,
    FOX_AND_GEESE,
    XIANGQI,
    SHOGI,
    CONNECT_FOUR,
    MORABARABA,
}

data class BoardSelectionOption(
    val title: String,
    val detail: String,
    val assetName: String? = null,
    val preview: BoardSelectionPreview = BoardSelectionPreview.GRID,
)

/**
 * Shared full-screen board picker for games whose board presentation is chosen
 * before the match begins.
 */
class BoardSelectionView(
    context: Context,
    private val matchLabel: String,
    private val options: List<BoardSelectionOption>,
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

    var onSelectionConfirmed: ((Int) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val bitmaps = options.map { option ->
        option.assetName?.let { name ->
            runCatching {
                context.assets.open(name).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    private val frameBitmap = runCatching {
        context.assets.open("board_selection_frame.webp").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val cardRects = options.map { RectF() }
    private val imageRects = options.map { RectF() }
    private val backRect = RectF()
    private val backTouchRect = RectF()
    private val continueRect = RectF()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = Color.argb(52, 0, 0, 0) }
    private val dimPaint = Paint().apply { color = Color.argb(122, 2, 9, 18) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(25f)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BFD2D4")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }
    private val boardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(14f)
    }
    private val boardDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6A2D1B")
        textAlign = Paint.Align.CENTER
        textSize = sp(9f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
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
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(9f)
    }

    private var selectedOption: Int? = null
    private var pressedOption: Int? = null
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
        contentDescription = "Choose a board"
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val top = dp(128f).coerceAtMost(height * 0.22f)
        val horizontalPadding = dp(18f)
        val gap = dp(9f)
        val cardWidth = ((width - horizontalPadding * 2f - gap) / 2f).coerceAtLeast(1f)
        val rowCount = (options.size + 1) / 2
        val controlsHeight = dp(98f)
        val availableHeight = (height - top - controlsHeight - dp(16f)).coerceAtLeast(dp(90f))
        val rowGap = if (rowCount > 1) gap else 0f
        val cardHeight = min(
            cardWidth,
            ((availableHeight - rowGap * (rowCount - 1)) / rowCount).coerceAtLeast(dp(86f)),
        )

        options.forEachIndexed { index, _ ->
            val row = index / 2
            val column = index % 2
            val itemsInRow = min(2, options.size - row * 2)
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
        val continueTop = height - dp(78f)
        val continueLeft = width - dp(16f) - continueSize
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
            0f, 0f, width.toFloat(), height.toFloat(),
            topColor, bottomColor, Shader.TileMode.CLAMP,
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
        canvas.drawText("Playing $matchLabel · Select a board", width / 2f, dp(101f), subtitlePaint)
        options.forEachIndexed { index, option -> drawBoardCard(canvas, index, option) }
        canvas.drawText(
            "Choose a board, then tap the arrow to begin.",
            width / 2f,
            height - dp(11f),
            footerPaint,
        )
        drawContinueButton(canvas)
    }

    private fun drawBoardCard(canvas: Canvas, index: Int, option: BoardSelectionOption) {
        val card = cardRects[index]
        val image = imageRects[index]
        val pressed = pressedOption == index
        val selected = selectedOption == index
        val offset = if (pressed) dp(2f) else 0f
        val drawnCard = RectF(card.left, card.top + offset, card.right, card.bottom + offset)
        val drawnImage = RectF(image.left, image.top + offset, image.right, image.bottom + offset)

        drawCardBase(canvas, drawnCard)
        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(drawnImage, dp(8f), dp(8f), Path.Direction.CW)
        })
        bitmaps[index]?.let { drawBitmapCover(canvas, it, drawnImage) }
            ?: drawGeneratedPreview(canvas, option.preview, drawnImage)
        canvas.drawRect(drawnImage, scrimPaint)
        canvas.restore()
        frameBitmap?.let { drawFrameAlignedToImage(canvas, it, drawnImage, drawnCard) }
        canvas.drawText(option.title, drawnCard.centerX(), drawnCard.bottom - dp(25f), boardTitlePaint)
        canvas.drawText(option.detail, drawnCard.centerX(), drawnCard.bottom - dp(10f), boardDetailPaint)

        if (selectedOption != null && !selected) {
            canvas.drawRoundRect(drawnCard, dp(10f), dp(10f), dimPaint)
        }
        if (selected) {
            selectionBorderPaint.color = Color.parseColor("#F6D78F")
            selectionBorderPaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(drawnCard, dp(10f), dp(10f), selectionBorderPaint)
        }
    }

    private fun drawGeneratedPreview(canvas: Canvas, preview: BoardSelectionPreview, rect: RectF) {
        when (preview) {
            BoardSelectionPreview.CONNECT_FOUR -> {
                cardPaint.color = Color.parseColor("#2D71B8")
                canvas.drawRect(rect, cardPaint)
                cardPaint.color = Color.parseColor("#E6D7B7")
                val cell = rect.width() / 7f
                for (row in 0 until 6) for (column in 0 until 7) {
                    canvas.drawCircle(
                        rect.left + cell * (column + 0.5f),
                        rect.top + rect.height() * (row + 0.5f) / 6f,
                        cell * 0.31f,
                        cardPaint,
                    )
                }
            }
            BoardSelectionPreview.MORABARABA -> {
                cardPaint.color = Color.parseColor("#D5A871")
                canvas.drawRect(rect, cardPaint)
                cardPaint.color = Color.parseColor("#5A311D")
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = dp(2f)
                canvas.drawRect(rect.left + rect.width() * .16f, rect.top + rect.height() * .12f,
                    rect.right - rect.width() * .16f, rect.bottom - rect.height() * .12f, cardPaint)
                canvas.drawRect(rect.left + rect.width() * .31f, rect.top + rect.height() * .27f,
                    rect.right - rect.width() * .31f, rect.bottom - rect.height() * .27f, cardPaint)
                canvas.drawLine(rect.centerX(), rect.top + rect.height() * .12f,
                    rect.centerX(), rect.bottom - rect.height() * .12f, cardPaint)
                canvas.drawLine(rect.left + rect.width() * .16f, rect.centerY(),
                    rect.right - rect.width() * .16f, rect.centerY(), cardPaint)
                cardPaint.style = Paint.Style.FILL
            }
            BoardSelectionPreview.FOX_AND_GEESE -> drawCrossPreview(canvas, rect)
            BoardSelectionPreview.OTHELLO -> drawGridPreview(canvas, rect, 8, Color.parseColor("#2E7B50"))
            BoardSelectionPreview.XIANGQI -> drawGridPreview(canvas, rect, 9, Color.parseColor("#D0A05C"), rows = 10)
            BoardSelectionPreview.SHOGI -> drawGridPreview(canvas, rect, 9, Color.parseColor("#C79758"))
            BoardSelectionPreview.CHECKERS -> drawCheckerPreview(canvas, rect)
            BoardSelectionPreview.GRID -> drawGridPreview(canvas, rect, 8, Color.parseColor("#9B6944"))
        }
    }

    private fun drawCheckerPreview(canvas: Canvas, rect: RectF) {
        val cell = rect.width() / 8f
        val light = Paint(Paint.ANTI_ALIAS_FLAG)
        val dark = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0 until 8) for (column in 0 until 8) {
            light.color = if ((row + column) % 2 == 0) Color.parseColor("#EAD9B8")
            else Color.parseColor("#8A5135")
            canvas.drawRect(
                rect.left + column * cell,
                rect.top + row * cell,
                rect.left + (column + 1) * cell,
                rect.top + (row + 1) * cell,
                light,
            )
        }
        dark.color = Color.argb(70, 0, 0, 0)
        canvas.drawRect(rect, dark)
    }

    private fun drawGridPreview(
        canvas: Canvas,
        rect: RectF,
        columns: Int,
        color: Int,
        rows: Int = columns,
    ) {
        cardPaint.color = color
        canvas.drawRect(rect, cardPaint)
        cardPaint.color = Color.argb(155, 75, 43, 24)
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = dp(1.3f)
        for (column in 0 until columns) {
            val x = rect.left + rect.width() * column / (columns - 1).coerceAtLeast(1)
            canvas.drawLine(x, rect.top, x, rect.bottom, cardPaint)
        }
        for (row in 0 until rows) {
            val y = rect.top + rect.height() * row / (rows - 1).coerceAtLeast(1)
            canvas.drawLine(rect.left, y, rect.right, y, cardPaint)
        }
        cardPaint.style = Paint.Style.FILL
    }

    private fun drawCrossPreview(canvas: Canvas, rect: RectF) {
        cardPaint.color = Color.parseColor("#D6A96A")
        canvas.drawRect(rect, cardPaint)
        cardPaint.color = Color.parseColor("#6A3A20")
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = dp(2f)
        val left = rect.left + rect.width() * .22f
        val right = rect.right - rect.width() * .22f
        val top = rect.top + rect.height() * .1f
        val bottom = rect.bottom - rect.height() * .1f
        canvas.drawRect(left, top, right, bottom, cardPaint)
        canvas.drawRect(rect.left + rect.width() * .1f, rect.top + rect.height() * .32f,
            rect.right - rect.width() * .1f, rect.bottom - rect.height() * .32f, cardPaint)
        cardPaint.style = Paint.Style.FILL
    }

    private fun drawContinueButton(canvas: Canvas) {
        val enabled = selectedOption != null
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) Color.parseColor("#E3B86A") else Color.argb(80, 180, 197, 201)
        }
        canvas.drawRoundRect(continueRect, dp(12f), dp(12f), buttonPaint)
        continuePaint.color = if (enabled) Color.WHITE else Color.argb(110, 220, 230, 232)
        val offset = if (pressedContinue) dp(2f) else 0f
        canvas.save()
        canvas.translate(offset, offset)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedBack = backTouchRect.contains(event.x, event.y)
                pressedOption = null
                pressedContinue = false
                if (!pressedBack) {
                    pressedOption = cardRects.indexOfFirst { it.contains(event.x, event.y) }
                        .takeIf { it >= 0 }
                    if (pressedOption == null && continueRect.contains(event.x, event.y)) {
                        pressedContinue = true
                    }
                }
                if (pressedBack || pressedOption != null || pressedContinue) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedBack && !backTouchRect.contains(event.x, event.y)) pressedBack = false
                if (pressedOption != null && !cardRects[pressedOption!!].contains(event.x, event.y)) {
                    pressedOption = null
                }
                if (pressedContinue && !continueRect.contains(event.x, event.y)) pressedContinue = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val tappedOption = pressedOption?.takeIf { cardRects[it].contains(event.x, event.y) }
                val tappedContinue = pressedContinue && continueRect.contains(event.x, event.y)
                val tappedBack = pressedBack && backTouchRect.contains(event.x, event.y)
                pressedOption = null
                pressedContinue = false
                pressedBack = false
                when {
                    tappedBack -> {
                        SoundPlayer.play("ui_click")
                        onBackClicked?.invoke()
                    }
                    tappedOption != null -> {
                        SoundPlayer.play("ui_click")
                        selectedOption = if (selectedOption == tappedOption) null else tappedOption
                    }
                    tappedContinue && selectedOption != null -> {
                        SoundPlayer.play("ui_click")
                        onSelectionConfirmed?.invoke(selectedOption!!)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedOption = null
                pressedContinue = false
                pressedBack = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val destinationAspect = destination.width() / destination.height()
        val source = if (sourceAspect > destinationAspect) {
            val croppedWidth = (bitmap.height * destinationAspect).toInt()
            Rect((bitmap.width - croppedWidth) / 2, 0, (bitmap.width + croppedWidth) / 2, bitmap.height)
        } else {
            val croppedHeight = (bitmap.width / destinationAspect).toInt()
            Rect(0, (bitmap.height - croppedHeight) / 2, bitmap.width, (bitmap.height + croppedHeight) / 2)
        }
        canvas.drawBitmap(bitmap, source, destination, imagePaint)
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glowPaint.shader = android.graphics.RadialGradient(
            x, y, radius,
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