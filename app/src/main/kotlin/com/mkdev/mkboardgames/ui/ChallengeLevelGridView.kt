package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.abs
import kotlin.math.ceil

data class ChallengeSection(
    val firstLevel: Int,
    val title: String,
)

/**
 * Four-column challenge selector using the same wood/gold card language as the
 * game catalogue. The view owns scrolling so the level cards stay compact on
 * phones without introducing a second widget style.
 */
class ChallengeLevelGridView(
    context: Context,
    private val levelCount: Int,
    private val highestCompleted: Int,
    private val starsByLevel: IntArray = IntArray(levelCount),
    private val subtitles: List<String> = emptyList(),
    private val lockFutureChallenges: Boolean = false,
    private val sections: List<ChallengeSection> = emptyList(),
) : View(context) {

    var onLevelSelected: ((Int) -> Unit)? = null
    var onLockedLevelSelected: ((Int) -> Unit)? = null

    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val woodCardRenderer = BrownWoodCardRenderer(unit)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * textScale
    }
    private val completedLevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE5A8")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * textScale
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 10f * textScale
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B7C9D1")
        textAlign = Paint.Align.CENTER
        textSize = 7.5f * textScale
    }
    private val loadingRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.STROKE
        strokeWidth = 1.76f * unit
        strokeCap = Paint.Cap.ROUND
    }
    private val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
        textSize = 13f * textScale
    }
    private val sectionRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D5132")
        strokeWidth = unit
    }

    private val cardRects = ArrayList<RectF>(levelCount)
    private data class SectionHeaderLayout(val top: Float, val title: String)
    private val sectionHeaders = ArrayList<SectionHeaderLayout>()
    private val cardScales = FloatArray(levelCount) { 1f }
    private var pressedIndex = -1
    private var loadingIndex = -1
    private var loadingAngle = 0f
    private var scrollOffset = 0f
    private var maxScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var scaleAnimator: ValueAnimator? = null
    private var loadingAnimator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val scroller = OverScroller(context, DecelerateInterpolator(1.4f))

    init {
        isClickable = true
        isFocusable = true
    contentDescription = "Chess challenge selector. Choose any available challenge."
        setBackgroundColor(Color.parseColor("#061321"))
    }

    override fun onDetachedFromWindow() {
        scaleAnimator?.cancel()
        loadingAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        scroller.forceFinished(true)
        super.onDetachedFromWindow()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll)
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val sidePadding = 14f * unit
        val columnGap = 8f * unit
        val cardWidth = ((width - sidePadding * 2f - columnGap * 3f) / 4f)
            .coerceAtLeast(1f)
        val cardHeight = 64f * unit
        val rowGap = 10f * unit
        val sectionHeaderHeight = 34f * unit
        val sectionGap = 12f * unit

        cardRects.clear()
        sectionHeaders.clear()
        val configuredSections = sections
            .filter { it.firstLevel in 1..levelCount }
            .associateBy { it.firstLevel }
        val sectionStarts = (listOf(1) + configuredSections.keys)
            .distinct()
            .sorted()
        var contentTop = 8f * unit
        sectionStarts.forEachIndexed { sectionIndex, startLevel ->
            val endLevel = sectionStarts.getOrNull(sectionIndex + 1) ?: (levelCount + 1)
            val itemCount = (endLevel - startLevel).coerceAtLeast(0)
            configuredSections[startLevel]?.let { section ->
                sectionHeaders += SectionHeaderLayout(contentTop, section.title)
                contentTop += sectionHeaderHeight
            }
            val rowCount = ceil(itemCount / 4f).toInt()
            repeat(itemCount) { offset ->
                val row = offset / 4
                val column = offset % 4
                val left = sidePadding + column * (cardWidth + columnGap)
                val top = contentTop + row * (cardHeight + rowGap)
                cardRects += RectF(left, top, left + cardWidth, top + cardHeight)
            }
            contentTop += rowCount * cardHeight +
                (rowCount - 1).coerceAtLeast(0) * rowGap + sectionGap
        }

        maxScroll = (contentTop - height + 6f * unit).coerceAtLeast(0f)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#061321"))
        canvas.save()
        canvas.translate(0f, -scrollOffset)

        sectionHeaders.forEach { header ->
            canvas.drawText(
                header.title,
                14f * unit,
                header.top + 20f * unit,
                sectionTitlePaint,
            )
            canvas.drawLine(
                14f * unit,
                header.top + 27f * unit,
                width - 14f * unit,
                header.top + 27f * unit,
                sectionRulePaint,
            )
        }

        cardRects.forEachIndexed { index, rect ->
            val scale = cardScales[index]
            canvas.save()
            canvas.scale(scale, scale, rect.centerX(), rect.centerY())
            woodCardRenderer.draw(canvas, rect, pressedIndex == index)
            canvas.drawText(
                "Challenge ${(index + 1).toString().padStart(2, '0')}",
                rect.centerX(),
                rect.centerY() - 11f * unit,
                if (index + 1 <= highestCompleted) completedLevelPaint else levelPaint,
            )
            val subtitle = subtitles.getOrNull(index)
                ?.replace("Mate in ", "M")
                ?.replace("Direct mate", "Mate")
                ?.take(19)
            if (!subtitle.isNullOrBlank()) {
                canvas.drawText(
                    subtitle,
                    rect.centerX(),
                    rect.centerY() + 3f * unit,
                    subtitlePaint,
                )
            }
            val stars = starsByLevel.getOrNull(index)?.coerceIn(0, 3) ?: 0
            if (stars > 0) {
                canvas.drawText(
                    "★".repeat(stars),
                    rect.centerX(),
                    rect.centerY() + 19f * unit,
                    starPaint,
                )
            }
            if (loadingIndex == index) {
                val radius = 5.6f * unit
                val centerX = rect.right - 17f * unit
                val centerY = rect.top + 17f * unit
                canvas.drawArc(
                    RectF(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius,
                    ),
                    loadingAngle,
                    285f,
                    false,
                    loadingRingPaint,
                )
            }
            if (lockFutureChallenges && index + 1 > highestCompleted + 1) {
                canvas.drawText("LOCKED", rect.centerX(), rect.bottom - 8f * unit, subtitlePaint)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val contentY = event.y + scrollOffset
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x
                downY = event.y
                lastY = event.y
                dragging = false
                pressedIndex = cardAt(event.x, contentY)
                pressedIndex.takeIf { it >= 0 }?.let { animateCardScale(it, 0.94f) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = lastY - event.y
                if (!dragging && abs(event.y - downY) > touchSlop) {
                    dragging = true
                    pressedIndex.takeIf { it >= 0 }?.let { animateCardScale(it, 1f) }
                    pressedIndex = -1
                }
                scrollOffset = (scrollOffset + dy).coerceIn(0f, maxScroll)
                lastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (dragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    if (abs(velocityY) >= minimumFlingVelocity) {
                        scroller.fling(
                            0,
                            scrollOffset.toInt(),
                            0,
                            -velocityY.toInt(),
                            0,
                            0,
                            0,
                            maxScroll.toInt(),
                        )
                        postInvalidateOnAnimation()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                val selected = pressedIndex
                val stillOnCard = selected >= 0 && cardAt(event.x, event.y + scrollOffset) == selected
                if (selected >= 0) animateCardScale(selected, 1f)
                pressedIndex = -1
                if (
                    selected >= 0 &&
                    stillOnCard &&
                    abs(event.x - downX) <= 18f * unit &&
                    abs(event.y - downY) <= 18f * unit
                ) {
                    SoundPlayer.play("ui_click")
                    if (!lockFutureChallenges || selected + 1 <= highestCompleted + 1) {
                        startLoading(selected)
                    } else {
                        onLockedLevelSelected?.invoke(selected + 1)
                    }
                }
                dragging = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                if (pressedIndex >= 0) animateCardScale(pressedIndex, 1f)
                pressedIndex = -1
                dragging = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun cardAt(x: Float, contentY: Float): Int =
        cardRects.indexOfFirst { it.contains(x, contentY) }

    private fun animateCardScale(index: Int, target: Float) {
        scaleAnimator?.cancel()
        val from = cardScales[index]
        scaleAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = if (target < 1f) 70L else 110L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                cardScales[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startLoading(index: Int) {
        loadingAnimator?.cancel()
        loadingIndex = index
        loadingAngle = 0f
        loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                loadingAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        postDelayed({
            if (loadingIndex == index) onLevelSelected?.invoke(index + 1)
        }, 260L)
        invalidate()
    }
}