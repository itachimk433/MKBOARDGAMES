package com.mkdev.mkboardgames.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class SnakesLaddersEntryRule(
    val title: String,
) {
    REQUIRE_SIX(
        title = "Roll a 6 to enter",
    ),
    ANY_ROLL(
        title = "Enter on any roll",
    ),
}

/**
 * Board picker shown after the player chooses a Snakes & Ladders match type.
 *
 * The game uses a custom canvas UI, so this keeps board selection consistent
 * with the existing match menu instead of introducing a platform dialog.
 */
class SnakesLaddersBoardSelectionView(
    context: android.content.Context,
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
        const val FRAME_VISIBLE_LEFT = 70f
        const val FRAME_VISIBLE_TOP = 87f
        const val FRAME_VISIBLE_RIGHT = 530f
        const val FRAME_VISIBLE_BOTTOM = 505f
        const val FRAME_SOURCE_SIZE = 600f
    }

    var onSelectionConfirmed:
        ((SnakesLaddersBoardView.Board, SnakesLaddersEntryRule) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val boards = SnakesLaddersBoardView.Board.values()
    private val rules = SnakesLaddersEntryRule.values()
    private val snakesLaddersIconBitmap: Bitmap? = runCatching {
        context.assets.open("snakes_ladders_icon.webp").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val bitmaps = boards.map { board ->
        runCatching {
            context.assets.open(board.assetName).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    private val frameBitmap: Bitmap? = runCatching {
        context.assets.open("board_selection_frame.webp").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val cardRects = boards.map { RectF() }
    private val imageRects = boards.map { RectF() }
    private val ruleRects = rules.map { RectF() }
    private val continueRect = RectF()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
        textSize = sp(15f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply {
        color = Color.argb(55, 0, 0, 0)
    }
    private val dimPaint = Paint().apply {
        color = Color.argb(125, 2, 9, 18)
    }
    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val ruleTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(13f)
    }
    private val continuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val continueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BFD2D4")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(9f)
    }
    private val backRect = RectF()
    private val backTouchRect = RectF()
    private val iconRect = RectF()
    private var pressedBoard: Int? = null
    private var pressedRule: Int? = null
    private var pressedContinue = false
    private var pressedBack = false
    private var selectedBoard: Int? = null
    private var selectedRule: Int? = SnakesLaddersEntryRule.REQUIRE_SIX.ordinal
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
        PlainGameButtonAssets.initialize(context)
        isClickable = true
        contentDescription = "Choose a Snakes and Ladders board"
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
        val top = dp(138f).coerceAtMost(height * 0.25f)
        val horizontalPadding = dp(18f)
        val gap = dp(10f)
        val cardWidth = ((width - horizontalPadding * 2f - gap) / 2f).coerceAtLeast(1f)
        val rowCount = (boards.size + 1) / 2
        val controlsHeight = dp(156f)
        val availableHeight = (height - top - controlsHeight - dp(16f)).coerceAtLeast(dp(120f))
        val rowGap = if (rowCount > 1) gap else 0f
        val cardHeight = min(
            cardWidth,
            ((availableHeight - rowGap * (rowCount - 1)) / rowCount).coerceAtLeast(dp(120f)),
        )

        boards.forEachIndexed { index, _ ->
            val row = index / 2
            val column = index % 2
            val itemsInRow = min(2, boards.size - row * 2)
            val rowWidth = itemsInRow * cardWidth + (itemsInRow - 1) * gap
            val left = (width - rowWidth) / 2f + column * (cardWidth + gap)
            val rowTop = top + row * (cardHeight + rowGap)
            cardRects[index].set(left, rowTop, left + cardWidth, rowTop + cardHeight)
            val imageWidth = cardWidth - dp(24f)
            val imageHeight = imageWidth *
                (FRAME_INNER_BOTTOM - FRAME_INNER_TOP) /
                (FRAME_INNER_RIGHT - FRAME_INNER_LEFT)
            imageRects[index].set(
                left + dp(12f),
                rowTop + dp(14f),
                left + dp(12f) + imageWidth,
                rowTop + dp(14f) + imageHeight,
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
        val iconSize = dp(54f)
        val iconRight = width - dp(14f)
        iconRect.set(iconRight - iconSize, dp(8f), iconRight, dp(8f) + iconSize)

        val ruleTop = height - dp(94f)
        val continueSize = dp(52f)
        val continueLeft = width - dp(16f) - continueSize
        continueRect.set(continueLeft, ruleTop, continueLeft + continueSize, ruleTop + continueSize)
        val ruleGap = dp(8f)
        val ruleLeft = dp(18f)
        val ruleRight = continueLeft - dp(12f)
        val ruleWidth = ((ruleRight - ruleLeft - ruleGap) / rules.size).coerceAtLeast(dp(80f))
        rules.forEachIndexed { index, _ ->
            val left = ruleLeft + index * (ruleWidth + ruleGap)
            ruleRects[index].set(left, ruleTop, left + ruleWidth, ruleTop + continueSize)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val phase = backgroundPhase * (Math.PI.toFloat() * 2f)
        val topColor = blendColor(
            Color.parseColor("#102C32"),
            Color.parseColor("#27355E"),
            ((sin(phase * 0.55f) + 1f) / 2f),
        )
        val bottomColor = blendColor(
            Color.parseColor("#061321"),
            Color.parseColor("#112C46"),
            ((cos(phase * 0.42f) + 1f) / 2f),
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
        snakesLaddersIconBitmap?.let { canvas.drawBitmap(it, null, iconRect, iconPaint) }
        canvas.drawText("Choose your board", width / 2f, dp(70f), titlePaint)
        canvas.drawText(
            "Playing $matchLabel · Select a board and entry rule",
            width / 2f,
            dp(103f),
            subtitlePaint,
        )

        boards.forEachIndexed { index, board ->
            drawBoardCard(canvas, index, board)
        }
        canvas.drawText(
            "Choose both options, then tap the arrow to begin.",
            width / 2f,
            height - dp(14f),
            footerPaint,
        )
        drawRuleChoices(canvas)
    }

    private fun drawBoardCard(
        canvas: Canvas,
        index: Int,
        board: SnakesLaddersBoardView.Board,
    ) {
        val card = cardRects[index]
        val image = imageRects[index]
        val pressed = pressedBoard == index
        val selected = selectedBoard == index
        val offset = if (pressed) 2f * density else 0f
        val drawnCard = RectF(card.left, card.top + offset, card.right, card.bottom + offset)
        val drawnImage = RectF(image.left, image.top + offset, image.right, image.bottom + offset)

        canvas.save()
        canvas.clipRect(drawnImage)
        bitmaps[index]?.let { bitmap ->
            drawBitmapCover(canvas, bitmap, drawnImage)
        } ?: run {
            cardPaint.color = Color.parseColor("#2A4145")
            canvas.drawRect(drawnImage, cardPaint)
        }
        canvas.drawRect(drawnImage, scrimPaint)
        canvas.restore()
        val frameBounds = frameBitmap?.let {
            drawFrameAlignedToImage(canvas, it, drawnImage, drawnCard)
        } ?: RectF(drawnImage)

        canvas.drawText(board.displayName, drawnCard.centerX(), drawnCard.bottom - dp(31f), boardTitlePaint)
        if (selectedBoard != null && !selected) {
            canvas.drawRect(frameBounds, dimPaint)
        }
        if (selected) {
            selectionBorderPaint.color = Color.parseColor("#F6D78F")
            selectionBorderPaint.strokeWidth = dp(3f)
            canvas.drawRect(frameBounds, selectionBorderPaint)
        }
    }

    private fun drawRuleChoices(canvas: Canvas) {
        canvas.drawText(
            "Choose the entry rule",
            (ruleRects.first().left + ruleRects.last().right) / 2f,
            ruleRects.first().top - dp(10f),
            ruleTitlePaint,
        )
        rules.forEachIndexed { index, rule ->
            val rect = ruleRects[index]
            val selected = selectedRule == index
            val pressed = pressedRule == index
            drawChessWoodButton(canvas, rect, pressed, density, maxCornerRadius = dp(10f))
            val buttonBounds = PlainGameButtonAssets.styleFor(rect)?.let { style ->
                PlainGameButtonAssets.visibleRect(rect, style, pressed)
            } ?: RectF(rect)
            val buttonRadius = min(buttonBounds.height() * 0.2f, dp(10f))
            if (selected) {
                selectionBorderPaint.color = Color.parseColor("#F6D78F")
                selectionBorderPaint.strokeWidth = dp(3f)
                canvas.drawRoundRect(buttonBounds, buttonRadius, buttonRadius, selectionBorderPaint)
            } else if (selectedRule != null) {
                canvas.drawRoundRect(buttonBounds, buttonRadius, buttonRadius, dimPaint)
            }
            val textColor = if (selected) Color.WHITE else Color.parseColor("#E6D6C0")
            ruleTitlePaint.color = textColor
            val titleBaseline = rect.centerY() - (ruleTitlePaint.ascent() + ruleTitlePaint.descent()) / 2f
            canvas.drawText(rule.title, rect.centerX(), titleBaseline, ruleTitlePaint)
        }

        val enabled = selectedBoard != null && selectedRule != null
        val centerX = continueRect.centerX()
        val centerY = continueRect.centerY()
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) Color.parseColor("#E3B86A") else Color.argb(80, 180, 197, 201)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(continueRect, dp(12f), dp(12f), buttonPaint)
        continuePaint.color = if (enabled) Color.WHITE else Color.argb(110, 220, 230, 232)
        val arrowOffset = if (pressedContinue) dp(2f) else 0f
        canvas.save()
        canvas.translate(arrowOffset, arrowOffset)
        canvas.drawLine(centerX - dp(10f), centerY, centerX + dp(9f), centerY, continuePaint)
        canvas.drawLine(centerX + dp(9f), centerY, centerX + dp(1f), centerY - dp(8f), continuePaint)
        canvas.drawLine(centerX + dp(9f), centerY, centerX + dp(1f), centerY + dp(8f), continuePaint)
        canvas.restore()
        continueLabelPaint.color = if (enabled) Color.parseColor("#F6D78F") else Color.argb(120, 191, 210, 212)
        canvas.drawText("START", centerX, continueRect.bottom + dp(13f), continueLabelPaint)
    }

    private fun drawFrameAlignedToImage(
        canvas: Canvas,
        frame: Bitmap,
        image: RectF,
        card: RectF,
    ): RectF {
        val innerWidth = FRAME_INNER_RIGHT - FRAME_INNER_LEFT
        val innerHeight = FRAME_INNER_BOTTOM - FRAME_INNER_TOP
        val scale = min(image.width() / innerWidth, image.height() / innerHeight)
        val frameSize = FRAME_SOURCE_SIZE * scale
        val frameLeft = image.left - FRAME_INNER_LEFT * scale
        val frameTop = image.top - FRAME_INNER_TOP * scale
        val destination = RectF(
            frameLeft,
            frameTop,
            frameLeft + frameSize,
            frameTop + frameSize,
        )
        val visibleBounds = RectF(
            frameLeft + FRAME_VISIBLE_LEFT * scale,
            frameTop + FRAME_VISIBLE_TOP * scale,
            frameLeft + FRAME_VISIBLE_RIGHT * scale,
            frameTop + FRAME_VISIBLE_BOTTOM * scale,
        )

        canvas.save()
        canvas.clipRect(card)
        canvas.drawBitmap(frame, null, destination, imagePaint)
        canvas.restore()
        visibleBounds.intersect(card)
        return visibleBounds
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

    private fun drawBackButton(canvas: Canvas) {
        GamesSelectionBackButton.draw(canvas, backRect, density)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedBack = backTouchRect.contains(event.x, event.y)
                pressedBoard = null
                pressedRule = null
                pressedContinue = false
                if (!pressedBack) {
                    pressedBoard = cardRects.indexOfFirst { it.contains(event.x, event.y) }
                        .takeIf { it >= 0 }
                    if (pressedBoard == null) {
                        pressedRule = ruleRects.indexOfFirst { it.contains(event.x, event.y) }
                            .takeIf { it >= 0 }
                    }
                    if (pressedBoard == null && pressedRule == null &&
                        continueRect.contains(event.x, event.y)
                    ) {
                        pressedContinue = true
                    }
                }
                if (pressedBoard != null || pressedRule != null || pressedContinue) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedBack && !backTouchRect.contains(event.x, event.y)) pressedBack = false
                if (pressedBoard != null && !cardRects[pressedBoard!!].contains(event.x, event.y)) {
                    pressedBoard = null
                }
                if (pressedRule != null && !ruleRects[pressedRule!!].contains(event.x, event.y)) {
                    pressedRule = null
                }
                if (pressedContinue && !continueRect.contains(event.x, event.y)) {
                    pressedContinue = false
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tappedBoard = pressedBoard?.takeIf { cardRects[it].contains(event.x, event.y) }
                val tappedRule = pressedRule?.takeIf { ruleRects[it].contains(event.x, event.y) }
                val tappedContinue = pressedContinue && continueRect.contains(event.x, event.y)
                val selectedBack = pressedBack && backTouchRect.contains(event.x, event.y)
                pressedBoard = null
                pressedRule = null
                pressedContinue = false
                pressedBack = false
                when {
                    selectedBack -> {
                        SoundPlayer.play("ui_click")
                        onBackClicked?.invoke()
                    }
                    tappedBoard != null -> {
                        SoundPlayer.play("ui_click")
                        selectedBoard = if (selectedBoard == tappedBoard) null else tappedBoard
                    }
                    tappedRule != null -> {
                        SoundPlayer.play("ui_click")
                        selectedRule = if (selectedRule == tappedRule) null else tappedRule
                    }
                    tappedContinue && selectedBoard != null && selectedRule != null -> {
                        SoundPlayer.play("ui_click")
                        onSelectionConfirmed?.invoke(
                            boards[selectedBoard!!],
                            rules[selectedRule!!],
                        )
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedBoard = null
                pressedRule = null
                pressedContinue = false
                pressedBack = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * textScale

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glowPaint.shader = RadialGradient(
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
}