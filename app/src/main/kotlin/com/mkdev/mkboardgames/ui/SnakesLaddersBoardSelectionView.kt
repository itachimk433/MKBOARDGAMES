package com.mkdev.mkboardgames.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

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

    var onBoardSelected: ((SnakesLaddersBoardView.Board) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val boards = SnakesLaddersBoardView.Board.values()
    private val bitmaps = boards.map { board ->
        runCatching {
            context.assets.open(board.assetName).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    private val cardRects = boards.map { RectF() }
    private val imageRects = boards.map { RectF() }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(25f)
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.16f
        textSize = sp(11f)
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
        textSize = sp(15f)
    }
    private val boardDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AFC2C4")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 34, 18, 13)
    }
    private val backBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D3A05F")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val backArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint().apply {
        color = Color.argb(55, 0, 0, 0)
    }
    private val backRect = RectF()
    private val backTouchRect = RectF()
    private var pressedBoard: Int? = null
    private var pressedBack = false

    init {
        isClickable = true
        contentDescription = "Choose a Snakes and Ladders board"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val top = dp(164f).coerceAtMost(height * 0.32f)
        val horizontalPadding = dp(18f)
        val gap = dp(10f)
        val cardWidth = ((width - horizontalPadding * 2f - gap) / 2f).coerceAtLeast(1f)
        val availableHeight = (height - top - dp(58f)).coerceAtLeast(dp(120f))
        val cardHeight = min(cardWidth * 1.18f, availableHeight)

        boards.forEachIndexed { index, _ ->
            val left = horizontalPadding + index * (cardWidth + gap)
            cardRects[index].set(left, top, left + cardWidth, top + cardHeight)
            imageRects[index].set(
                left + dp(9f),
                top + dp(9f),
                left + cardWidth - dp(9f),
                top + cardHeight - dp(58f),
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundPaint.shader = android.graphics.LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Color.parseColor("#102C32"),
            Color.parseColor("#061321"),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        drawBackButton(canvas)
        canvas.drawText("S N A K E S & L A D D E R S", width / 2f, dp(58f), eyebrowPaint)
        canvas.drawText("Choose your board", width / 2f, dp(98f), titlePaint)
        canvas.drawText(
            "Playing $matchLabel · Pick a board to begin",
            width / 2f,
            dp(124f),
            subtitlePaint,
        )

        boards.forEachIndexed { index, board ->
            drawBoardCard(canvas, index, board)
        }
        canvas.drawText(
            "Both boards use the same Snakes & Ladders rules.",
            width / 2f,
            height - dp(22f),
            footerPaint,
        )
    }

    private fun drawBoardCard(
        canvas: Canvas,
        index: Int,
        board: SnakesLaddersBoardView.Board,
    ) {
        val card = cardRects[index]
        val image = imageRects[index]
        val pressed = pressedBoard == index
        val radius = dp(14f)

        cardPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(card, radius, radius, cardPaint)
        borderPaint.color = if (pressed) board.accentColor else Color.parseColor("#2C5960")
        canvas.drawRoundRect(
            RectF(card.left + dp(0.5f), card.top + dp(0.5f), card.right - dp(0.5f), card.bottom - dp(0.5f)),
            radius,
            radius,
            borderPaint,
        )

        val clipPath = Path().apply {
            addRoundRect(image, dp(9f), dp(9f), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        bitmaps[index]?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, image, imagePaint)
        } ?: run {
            cardPaint.color = Color.parseColor("#2A4145")
            canvas.drawRect(image, cardPaint)
        }
        canvas.drawRect(image, scrimPaint)
        canvas.restore()

        canvas.drawText(board.displayName, card.centerX(), card.bottom - dp(31f), boardTitlePaint)
        canvas.drawText(
            if (index == 0) "Classic board" else "Winter board",
            card.centerX(),
            card.bottom - dp(14f),
            boardDetailPaint,
        )
    }

    private fun drawBackButton(canvas: Canvas) {
        val radius = dp(10f)
        canvas.drawRoundRect(backRect, radius, radius, backPaint)
        canvas.drawRoundRect(backRect, radius, radius, backBorderPaint)
        val cy = backRect.centerY()
        val tipX = backRect.left + dp(9f)
        canvas.drawLine(tipX, cy, backRect.right - dp(8f), cy, backArrowPaint)
        canvas.drawLine(tipX, cy, tipX + dp(9f), cy - dp(8f), backArrowPaint)
        canvas.drawLine(tipX, cy, tipX + dp(9f), cy + dp(8f), backArrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedBack = backTouchRect.contains(event.x, event.y)
                pressedBoard = if (pressedBack) {
                    null
                } else {
                    cardRects.indexOfFirst { it.contains(event.x, event.y) }.takeIf { it >= 0 }
                }
                pressedBoard?.let { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedBack && !backTouchRect.contains(event.x, event.y)) pressedBack = false
                if (pressedBoard != null && !cardRects[pressedBoard!!].contains(event.x, event.y)) {
                    pressedBoard = null
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selectedBoard = pressedBoard?.takeIf { cardRects[it].contains(event.x, event.y) }
                val selectedBack = pressedBack && backTouchRect.contains(event.x, event.y)
                pressedBoard = null
                pressedBack = false
                when {
                    selectedBack -> {
                        SoundPlayer.play("ui_click")
                        onBackClicked?.invoke()
                    }
                    selectedBoard != null -> {
                        SoundPlayer.play("ui_click")
                        onBoardSelected?.invoke(boards[selectedBoard])
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedBoard = null
                pressedBack = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * textScale
}