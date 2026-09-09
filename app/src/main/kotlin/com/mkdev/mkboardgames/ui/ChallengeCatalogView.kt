package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Challenge-mode catalogue. Chess is live today; the rest of the board-game
 * catalogue is deliberately visible so future challenge packs have a home.
 */
class ChallengeCatalogView(context: Context) : View(context) {

    var onChessSelected: (() -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    private data class ChallengeCard(
        val title: String,
        val detail: String,
        val unlocked: Boolean,
        var rect: RectF = RectF(),
    )

    private val cards = listOf(
        ChallengeCard("Chess", "100 levels • beginner to master", true),
        ChallengeCard("Checkers", "Challenge pack coming soon", false),
        ChallengeCard("International Draughts", "Challenge pack coming soon", false),
        ChallengeCard("Othello", "Challenge pack coming soon", false),
        ChallengeCard("Morabaraba", "Challenge pack coming soon", false),
        ChallengeCard("Tic-Tac-Toe", "Challenge pack coming soon", false),
        ChallengeCard("Connect Four", "Challenge pack coming soon", false),
        ChallengeCard("Fox and Geese", "Challenge pack coming soon", false),
        ChallengeCard("Ludo", "Challenge pack coming soon", false),
        ChallengeCard("Xiangqi", "Challenge pack coming soon", false),
        ChallengeCard("Shogi", "Challenge pack coming soon", false),
        ChallengeCard("Go", "Challenge pack coming soon", false),
        ChallengeCard("Mancala", "Challenge pack coming soon", false),
        ChallengeCard("Yoté", "Challenge pack coming soon", false),
        ChallengeCard("Onitama", "Challenge pack coming soon", false),
    )

    private val dp = resources.displayMetrics.density.coerceAtLeast(1f)
    private val sp = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B7C9D1")
        textAlign = Paint.Align.CENTER
    }
    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val chessIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 28, 18, 15)
    }
    private val backEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D3A05F")
        style = Paint.Style.STROKE
    }
    private val backArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backRect = RectF()
    private val backTouch = RectF()
    private var scrollY = 0f
    private var maxScrollY = 0f
    private var lastTouchY = 0f
    private var pressedCard: ChallengeCard? = null
    private var pressedBack = false
    private var cardScale = HashMap<String, Float>()
    private var cardAnimator: ValueAnimator? = null

    init {
        isClickable = true
        cards.forEach { cardScale[it.title] = 1f }
    }

    override fun onDetachedFromWindow() {
        cardAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val padding = 14f * dp
        val gap = 10f * dp
        val columns = 2
        val cardW = (width - padding * 2f - gap) / columns
        val cardH = 122f * dp
        val headerH = 122f * dp
        cards.forEachIndexed { index, card ->
            val column = index % columns
            val row = index / columns
            val left = padding + column * (cardW + gap)
            val top = headerH + row * (cardH + gap)
            card.rect = RectF(left, top, left + cardW, top + cardH)
        }
        maxScrollY = (cards.last().rect.bottom + 30f * dp - height).coerceAtLeast(0f)
        val backSize = 34f * dp
        backRect.set(14f * dp, 14f * dp, 14f * dp + backSize, 14f * dp + backSize)
        backTouch.set(
            backRect.left - 7f * dp,
            backRect.top - 7f * dp,
            backRect.right + 7f * dp,
            backRect.bottom + 7f * dp,
        )
    }

    override fun onDraw(canvas: Canvas) {
        backgroundPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#071A2B"),
            Color.parseColor("#102C32"),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(0f, -scrollY)
        drawHeader(canvas)
        cards.forEach { drawCard(canvas, it) }
        canvas.restore()
        drawBackButton(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        titlePaint.textSize = 24f * sp
        subtitlePaint.textSize = 11f * sp
        canvas.drawText("CHALLENGES", width / 2f, 44f * dp, titlePaint)
        canvas.drawText("Solve the board, one level at a time.", width / 2f, 68f * dp, subtitlePaint)
        canvas.drawText("Chess is available now • more games are on the way", width / 2f, 92f * dp, subtitlePaint)
    }

    private fun drawCard(canvas: Canvas, card: ChallengeCard) {
        val scale = cardScale[card.title] ?: 1f
        val r = card.rect
        if (scale != 1f) canvas.save().also { canvas.scale(scale, scale, r.centerX(), r.centerY()) }

        cardPaint.color = if (card.unlocked) Color.parseColor("#214A55") else Color.parseColor("#172B35")
        canvas.drawRoundRect(r, 16f * dp, 16f * dp, cardPaint)
        borderPaint.color = if (card.unlocked) Color.parseColor("#D3A05F") else Color.parseColor("#39505B")
        borderPaint.strokeWidth = if (card.unlocked) 1.5f * dp else 1f * dp
        canvas.drawRoundRect(r, 16f * dp, 16f * dp, borderPaint)

        if (card.unlocked) {
            chessIconPaint.textSize = 38f * sp
            canvas.drawText("♞", r.left + 38f * dp, r.top + 50f * dp, chessIconPaint)
        } else {
            drawLock(canvas, r.left + 38f * dp, r.top + 43f * dp)
        }

        cardTitlePaint.textSize = 14f * sp
        detailPaint.textSize = 9f * sp
        cardTitlePaint.color = if (card.unlocked) Color.WHITE else Color.parseColor("#A5B3B9")
        detailPaint.color = if (card.unlocked) Color.parseColor("#C5E5E0") else Color.parseColor("#71858D")
        canvas.drawText(card.title, r.left + 72f * dp, r.top + 43f * dp, cardTitlePaint)
        canvas.drawText(card.detail, r.left + 72f * dp, r.top + 64f * dp, detailPaint)
        if (card.unlocked) {
            detailPaint.color = Color.parseColor("#E3B86A")
            detailPaint.textSize = 9f * sp
            canvas.drawText("TAP TO ENTER", r.left + 72f * dp, r.top + 88f * dp, detailPaint)
        } else {
            detailPaint.color = Color.parseColor("#8799A0")
            canvas.drawText("LOCKED", r.left + 72f * dp, r.top + 88f * dp, detailPaint)
        }

        if (scale != 1f) canvas.restore()
    }

    private fun drawLock(canvas: Canvas, centerX: Float, top: Float) {
        lockPaint.strokeWidth = 2f * dp
        canvas.drawRoundRect(
            RectF(centerX - 12f * dp, top + 13f * dp, centerX + 12f * dp, top + 31f * dp),
            3f * dp, 3f * dp, lockPaint,
        )
        canvas.drawArc(
            RectF(centerX - 8f * dp, top, centerX + 8f * dp, top + 24f * dp),
            180f, -180f, false, lockPaint,
        )
        canvas.drawCircle(centerX, top + 22f * dp, 2f * dp, lockPaint)
    }

    private fun drawBackButton(canvas: Canvas) {
        canvas.drawRoundRect(backRect, 10f * dp, 10f * dp, backPaint)
        backEdgePaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(backRect, 10f * dp, 10f * dp, backEdgePaint)
        val cy = backRect.centerY()
        backArrowPaint.strokeWidth = 2.2f * dp
        canvas.drawLine(backRect.left + 9f * dp, cy, backRect.right - 8f * dp, cy, backArrowPaint)
        canvas.drawLine(backRect.left + 9f * dp, cy, backRect.left + 18f * dp, cy - 8f * dp, backArrowPaint)
        canvas.drawLine(backRect.left + 9f * dp, cy, backRect.left + 18f * dp, cy + 8f * dp, backArrowPaint)
    }

    private fun animateCard(card: ChallengeCard, target: Float) {
        cardAnimator?.cancel()
        val from = cardScale[card.title] ?: 1f
        cardAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = if (target < 1f) 70L else 110L
            addUpdateListener {
                cardScale[card.title] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val contentY = event.y + scrollY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                pressedBack = backTouch.contains(event.x, event.y)
                pressedCard = if (!pressedBack) cards.firstOrNull { it.rect.contains(event.x, contentY) } else null
                pressedCard?.let { animateCard(it, 0.97f) }
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = lastTouchY - event.y
                if (abs(delta) > 5f) {
                    pressedCard?.let { animateCard(it, 1f) }
                    pressedCard = null
                }
                scrollY = (scrollY + delta).coerceIn(0f, maxScrollY)
                lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (pressedBack) {
                    val selected = backTouch.contains(event.x, event.y)
                    pressedBack = false
                    if (selected) onBackClicked?.invoke()
                } else {
                    val card = pressedCard
                    card?.let { animateCard(it, 1f) }
                    if (card != null && card.rect.contains(event.x, contentY) && card.unlocked) {
                        onChessSelected?.invoke()
                    }
                    pressedCard = null
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCard?.let { animateCard(it, 1f) }
                pressedCard = null
                pressedBack = false
                invalidate()
            }
        }
        return true
    }
}