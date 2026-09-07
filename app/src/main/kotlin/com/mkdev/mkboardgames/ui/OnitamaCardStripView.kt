package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.games.onitama.OnitamaCard
import kotlin.math.min

class OnitamaCardStripView(
    context: Context,
    private val ownerLabel: String,
) : View(context) {
    var cards: List<OnitamaCard> = emptyList()
        set(value) {
            field = value.take(2)
            invalidate()
        }
    var sideCard: OnitamaCard? = null
        set(value) {
            field = value
            invalidate()
        }
    var interactive: Boolean = false
    var selectedIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }
    var onCardSelected: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cardRects = Array(2) { RectF() }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.argb(175, 7, 21, 34))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 9f * density
        textPaint.color = if (ownerLabel == "WHITE") Color.parseColor("#FFF6DE") else Color.parseColor("#B9D0D6")
        canvas.drawText(ownerLabel, 10f * density, 16f * density, textPaint)

        val sideWidth = if (sideCard != null) min(w * 0.25f, 116f * density) else 0f
        val cardsLeft = 8f * density
        val cardsRight = w - sideWidth - 7f * density
        val gap = 6f * density
        val cardWidth = ((cardsRight - cardsLeft) - gap) / 2f
        val cardTop = 21f * density
        val cardBottom = h - 5f * density
        cards.forEachIndexed { index, card ->
            cardRects[index].set(
                cardsLeft + index * (cardWidth + gap),
                cardTop,
                cardsLeft + index * (cardWidth + gap) + cardWidth,
                cardBottom,
            )
            drawCard(canvas, cardRects[index], card, index == selectedIndex)
        }

        sideCard?.let { card ->
            val left = cardsRight + 4f * density
            val rect = RectF(left, cardTop, w - 7f * density, cardBottom)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 7f * density
            textPaint.color = Color.argb(175, 228, 241, 240)
            canvas.drawText("SIDE", rect.centerX(), 16f * density, textPaint)
            drawCard(canvas, rect, card, false)
        }

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }
        canvas.drawRect(0f, h - density, w, h, divider)
    }

    private fun drawCard(canvas: Canvas, rect: RectF, card: OnitamaCard, selected: Boolean) {
        val shell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.parseColor("#315F67") else Color.parseColor("#172E38")
            setShadowLayer(5f * density, 0f, 2f * density, Color.argb(130, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, 8f * density, 8f * density, shell)
        shell.clearShadowLayer()
        borderPaint.color = if (selected) Color.parseColor("#FFE09C") else Color.argb(120, 180, 213, 207)
        borderPaint.strokeWidth = if (selected) 2f * density else density
        canvas.drawRoundRect(rect, 8f * density, 8f * density, borderPaint)

        card.assetName?.let { asset ->
            val bitmap = bitmapCache.getOrPut(asset) {
                runCatching { getContext().assets.open(asset).use(BitmapFactory::decodeStream) }.getOrNull()
            }
            if (bitmap != null) {
                val inset = 3f * density
                canvas.drawBitmap(bitmap, null, RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), imagePaint)
                return
            }
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = min(11f * density, rect.width() * 0.14f)
        textPaint.color = Color.parseColor("#FFE09C")
        canvas.drawText(card.name.uppercase(), rect.centerX(), rect.top + 17f * density, textPaint)
        drawPattern(canvas, rect, card)
    }

    private fun drawPattern(canvas: Canvas, rect: RectF, card: OnitamaCard) {
        val center = PointF(rect.centerX(), rect.centerY() + 4f * density)
        val cell = min(rect.width(), rect.height()) * 0.16f
        val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B4D7C5") }
        val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#182B31") }
        canvas.drawRect(center.x - cell * 0.5f, center.y - cell * 0.5f, center.x + cell * 0.5f, center.y + cell * 0.5f, originPaint)
        card.moves.forEach { move ->
            canvas.drawCircle(center.x + move.col * cell, center.y + move.row * cell, cell * 0.29f, patternPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive || event.actionMasked != MotionEvent.ACTION_UP) return true
        val selected = cardRects.indexOfFirst { it.contains(event.x, event.y) }
        if (selected >= 0 && selected < cards.size) {
            onCardSelected?.invoke(selected)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}