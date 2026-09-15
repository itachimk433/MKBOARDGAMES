package com.mkdev.mkboardgames.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(15f)
    }
    private val boardDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6A2D1B")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val backArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
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
        val rowCount = (boards.size + 1) / 2
        val availableHeight = (height - top - dp(58f)).coerceAtLeast(dp(120f))
        val rowGap = if (rowCount > 1) gap else 0f
        val cardHeight = min(
            cardWidth * 1.18f,
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
            imageRects[index].set(
                left + dp(9f),
                rowTop + dp(9f),
                left + cardWidth - dp(9f),
                rowTop + cardHeight - dp(58f),
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
            "Each board has its own ladder and snake layout.",
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
        val offset = if (pressed) 2f * density else 0f
        val drawnCard = RectF(card.left, card.top + offset, card.right, card.bottom + offset)
        val drawnImage = RectF(image.left, image.top + offset, image.right, image.bottom + offset)

        drawChessWoodButton(canvas, card, pressed, density)

        val clipPath = Path().apply {
            addRoundRect(drawnImage, dp(9f), dp(9f), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        bitmaps[index]?.let { bitmap ->
            drawBitmapCover(canvas, bitmap, drawnImage)
        } ?: run {
            cardPaint.color = Color.parseColor("#2A4145")
            canvas.drawRect(drawnImage, cardPaint)
        }
        canvas.drawRect(drawnImage, scrimPaint)
        canvas.restore()

        canvas.drawText(board.displayName, drawnCard.centerX(), drawnCard.bottom - dp(31f), boardTitlePaint)
        canvas.drawText(
            when (board) {
                SnakesLaddersBoardView.Board.ONE -> "Classic board"
                SnakesLaddersBoardView.Board.TWO -> "Winter board"
                SnakesLaddersBoardView.Board.THREE -> "Haunted board"
                SnakesLaddersBoardView.Board.FOUR -> "Forest board"
            },
            drawnCard.centerX(),
            drawnCard.bottom - dp(14f),
            boardDetailPaint,
        )
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
        drawChessWoodButton(canvas, backRect, pressedBack, density)
        backArrowPaint.color = Color.parseColor("#4A1714")
        val offset = if (pressedBack) 2f * density else 0f
        val cy = backRect.centerY() + offset
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