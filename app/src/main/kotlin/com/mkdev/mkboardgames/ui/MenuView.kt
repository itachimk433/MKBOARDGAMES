package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.mkdev.mkboardgames.GameMode
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseSetup
import com.mkdev.mkboardgames.games.go.GoSetup

class MenuView(
    context: Context,
    private val isChallengeMenu: Boolean = false,
) : View(context) {

    var onGameSelected: ((GameType) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

    /** Applied at attach-time from saved preferences; controls colour scheme. */
    var isLightMode: Boolean = false
        set(v) {
            field = v
            applyLightTheme()
            applyHomeStyleColors()
            invalidate()
        }

    var homeStyle: SettingsManager.HomeStyle = SettingsManager.HomeStyle.CLASSIC
        set(v) {
            field = v
            applyHomeStyleColors()
            updateCardLayout()
            invalidate()
        }

    private var currentGameMode = com.mkdev.mkboardgames.SettingsManager.currentMode(context)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isLightMode = com.mkdev.mkboardgames.SettingsManager.isLightMode(context)
        currentGameMode = if (isChallengeMenu) {
            GameMode.CHALLENGES
        } else {
            com.mkdev.mkboardgames.SettingsManager.currentMode(context)
        }
        homeStyle = SettingsManager.getHomeStyle(context)
        com.mkdev.mkboardgames.SoundPlayer.init(context)
    }

    enum class GameType {
        CHESS, AMAZONS, CHECKERS, INTERNATIONAL_DRAUGHTS, OTHELLO, MORABARABA, TICTACTOE, CONNECT_FOUR,
        FOX_AND_GEESE, LUDO, SNAKES_LADDERS, XIANGQI, SHOGI, GO, MANCALA, YOTE, ONITAMA,
        FIVE_FIELD_KONO
    }

    private data class Card(val type: GameType, var rect: RectF = RectF())
    private val cardOrderPreferences =
        context.getSharedPreferences("game_catalogue_layout", Context.MODE_PRIVATE)
    private val cards = mutableListOf<Card>()

    private val dp = context.resources.displayMetrics.density
    private val sp = context.resources.displayMetrics.scaledDensity

    private val brownWoodCardRenderer = BrownWoodCardRenderer(dp)

    private val challengeLockOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(112, 8, 17, 25)
    }
    private val challengeLockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val challengeLockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val homeBackgroundBitmap: Bitmap? = try {
        context.assets.open("mk_board_home_background.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val gameCardFrameBitmap: Bitmap? = try {
        context.assets.open("brown_game_card.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    private val ludoHomeIconBitmap: Bitmap? = try {
        context.assets.open("ludo_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val amazonsHomeIconBitmap: Bitmap? = try {
        context.assets.open("amazons_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val othelloHomeIconBitmap: Bitmap? = try {
        context.assets.open("othello_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val chessHomeIconBitmap: Bitmap? = try {
        context.assets.open("chess_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val internationalDraughtsHomeIconBitmap: Bitmap? = try {
        context.assets.open("international_draughts_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val draughtsHomeIconBitmap: Bitmap? = try {
        context.assets.open("draughts_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val morabarabaHomeIconBitmap: Bitmap? = try {
        context.assets.open("morabaraba_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val ticTacToeHomeIconBitmap: Bitmap? = try {
        context.assets.open("tictactoe_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val connectFourHomeIconBitmap: Bitmap? = try {
        context.assets.open("connect_four_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val shogiHomeIconBitmap: Bitmap? = try {
        context.assets.open("shogi_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val xiangqiHomeIconBitmap: Bitmap? = try {
        context.assets.open("xiangqi_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val goHomeIconBitmap: Bitmap? = try {
        context.assets.open("go_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val foxAndGeeseHomeIconBitmap: Bitmap? = try {
        context.assets.open("fox_and_geese_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val mancalaHomeIconBitmap: Bitmap? = try {
        context.assets.open("mancala_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val yoteBoardBitmap: Bitmap? = try {
        context.assets.open("yote_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val onitamaHomeIconBitmap: Bitmap? = try {
        context.assets.open("onitama_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val fiveFieldKonoHomeIconBitmap: Bitmap? = try {
        context.assets.open("five_field_kono_home_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
    private val snakesLaddersIconBitmap: Bitmap? = try {
        context.assets.open("snakes_ladders_icon.webp").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    private val bgPaint        = Paint().apply { color = Color.parseColor("#121212") }
    private val cardPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#202429") }
    private val cardHiPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B3138") }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#343A42")
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        textSize = 14f * sp.coerceAtMost(3f)
        setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(230, 0, 0, 0))
    }
    private val cardDescPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD"); textAlign = Paint.Align.CENTER
        textSize = 7.5f * sp.coerceAtMost(3f)
        setShadowLayer(1.5f * dp, 0f, 1f * dp, Color.argb(210, 0, 0, 0))
    }
    private val cardLoadingRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val copyrightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
        textSize = 10f * sp.coerceAtMost(3f)
    }
    private val irregularModePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 15f * sp.coerceAtMost(3f)
        letterSpacing = 0.12f
        setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(210, 0, 0, 0))
    }
    private val gearFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        style = Paint.Style.FILL
    }
    private val gearEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
    }
    private val gearHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#102C32")
        style = Paint.Style.FILL
    }
    private val gearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A"); textAlign = Paint.Align.CENTER
        textSize = 9f * sp.coerceAtMost(3f); isFakeBoldText = true; letterSpacing = 0.08f
    }
    private val miniLightPaint = Paint().apply { color = Color.parseColor("#F0D9B5") }
    private val miniDarkPaint  = Paint().apply { color = Color.parseColor("#B58863") }
    private val bitmapPaint    = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val homeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 190
    }
    private val homeBackgroundScrimPaint = Paint().apply {
        color = Color.argb(105, 0, 0, 0)
    }
    private val gridColumns = 3
    private val baseCardH = 136f * dp
    private val gameCardScale: Float
        get() = if (homeStyle == SettingsManager.HomeStyle.CLASSIC) 1f else 1.06f
    private val cardH get() = baseCardH
    private val gridPadding = 12f * dp
    private val gridSpacing = 8f * dp
    private val cardW get() =
        ((width - (gridPadding * 2f) - (gridSpacing * (gridColumns - 1))) / gridColumns)
            .coerceAtLeast(1f)

    private val gearRect  = RectF()
    private val gearTouch = RectF()
    private val backRect = RectF()
    private val backTouch = RectF()
    private var pressedCard: GameType? = null
    private var loadingCard: GameType? = null
    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var pressedGear  = false
    private var pressedBack = false
    private var gearRotation  = 0f
    private var gearSpinAnim: ValueAnimator? = null
    private val cardScales    = HashMap<GameType, Float>()

    private fun animateCardScale(type: GameType, to: Float) {
        val from = cardScales[type] ?: 1f
        ValueAnimator.ofFloat(from, to).apply {
            duration = if (to < 1f) 70L else 110L
            addUpdateListener { cardScales[type] = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ── Scroll state ─────────────────────────────────────────────────────────
    private var scrollY    = 0f
    private var maxScrollY = 0f
    private var lastTouchY = 0f
    private var downTouchY = 0f
    private var touchSlop = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null
    private val scrollScroller = OverScroller(context, DecelerateInterpolator(1.5f))
    private var headerH    = 0f   // Y where cards begin (below the compact header)
    private var lastTouchX = 0f
    private var arrangingCard: GameType? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragCenterX = 0f
    private var dragCenterY = 0f
    private var arrangeMode = false
    private var arrangeTargetIndex = -1
    private var arrangePreviewProgress = 1f
    private val arrangePreviewStart = HashMap<GameType, RectF>()
    private val arrangePreviewEnd = HashMap<GameType, RectF>()
    private var arrangePreviewAnimator: ValueAnimator? = null
    private var arrangeDropAnimator: ValueAnimator? = null
    private val arrangeTargetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(62, 247, 217, 155)
    }
    private val arrangeTargetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 247, 217, 155)
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }

    private val longPressToArrange = Runnable {
        val card = pressedCard ?: return@Runnable
        if (!isDragging && !pressedBack && !pressedGear && !isChallengeLocked(card)) {
            arrangeDropAnimator?.cancel()
            arrangeDropAnimator = null
            arrangingCard = card
            arrangeMode = true
            arrangeTargetIndex = cards.indexOfFirst { it.type == card }
            arrangePreviewProgress = 1f
            arrangePreviewStart.clear()
            arrangePreviewEnd.clear()
            arrangePreviewAnimator?.cancel()
            cardScales[card] = 1f
            dragCenterX = lastTouchX
            dragCenterY = lastTouchY + scrollY
            cards.firstOrNull { it.type == card }?.let {
                dragOffsetX = lastTouchX - it.rect.centerX()
                dragOffsetY = dragCenterY - it.rect.centerY()
                dragCenterX = lastTouchX - dragOffsetX
                dragCenterY -= dragOffsetY
            }
            pressedCard = null
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
            invalidate()
        }
    }

    init {
        PlainGameButtonAssets.initialize(context)
        val defaultOrder = listOf(
            GameType.CHESS, GameType.AMAZONS, GameType.CHECKERS,
            GameType.INTERNATIONAL_DRAUGHTS, GameType.OTHELLO, GameType.MORABARABA,
            GameType.TICTACTOE, GameType.CONNECT_FOUR, GameType.FOX_AND_GEESE,
            GameType.LUDO, GameType.SNAKES_LADDERS, GameType.XIANGQI,
            GameType.SHOGI, GameType.GO, GameType.MANCALA, GameType.YOTE,
            GameType.ONITAMA, GameType.FIVE_FIELD_KONO,
        )
        val savedOrder = cardOrderPreferences.getString("order", null)
            ?.split(",")
            ?.mapNotNull { value -> runCatching { GameType.valueOf(value) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        val order = (savedOrder + defaultOrder).distinct()
        cards.addAll(order.map(::Card))
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        headerH = if (currentGameMode == GameMode.IRREGULAR) 82f * dp else 64f * dp
        updateCardLayout()

        val backSize = 34f * dp
        val backLeft = 14f * dp
        val backTop = 14f * dp
        backRect.set(backLeft, backTop, backLeft + backSize, backTop + backSize)
        backTouch.set(
            backLeft - 6f * dp,
            backTop - 6f * dp,
            backLeft + backSize + 6f * dp,
            backTop + backSize + 6f * dp,
        )
        val gs = 34f * dp; val gx = w - gs - 14f * dp; val gy = 14f * dp
        gearRect.set(gx, gy, gx + gs, gy + gs)
        gearTouch.set(gx - 4f * dp, gy - 4f * dp, gx + gs + 4f * dp, gy + gs + 18f * dp)
    }

    private fun updateCardLayout() {
        scrollScroller.forceFinished(true)
        for (i in cards.indices) {
            cards[i].rect = cardSlotRect(i)
        }
        val contentBottom = scaledCardRect(cards.last()).bottom + 56f * dp   // room for two-line footer
        maxScrollY = maxOf(0f, contentBottom - height)
        scrollY = scrollY.coerceIn(0f, maxScrollY)
    }

    private fun saveCardOrder() {
        cardOrderPreferences.edit()
            .putString("order", cards.joinToString(",") { it.type.name })
            .apply()
    }

    private fun cancelLongPressToArrange() {
        removeCallbacks(longPressToArrange)
    }

    private fun finishArranging(dropX: Float, dropY: Float) {
        val draggedType = arrangingCard ?: return
        val fromIndex = cards.indexOfFirst { it.type == draggedType }
        if (fromIndex < 0) {
            clearArrangeState()
            return
        }
        val targetIndex = nearestCardSlot(dropX, dropY)
        animateArrangePreviewTo(targetIndex)

        val targetRect = cardSlotRect(targetIndex)
        val startX = dragCenterX
        val startY = dragCenterY
        val dropAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator(1.7f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                dragCenterX = startX + (targetRect.centerX() - startX) * progress
                dragCenterY = startY + (targetRect.centerY() - startY) * progress
                postInvalidateOnAnimation()
            }
        }
        dropAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (arrangeDropAnimator !== dropAnimator) return
                arrangeDropAnimator = null
                completeArranging(targetIndex)
            }
        })
        arrangeDropAnimator = dropAnimator
        dropAnimator.start()
    }

    private fun completeArranging(targetIndex: Int) {
        val draggedType = arrangingCard ?: return
        val fromIndex = cards.indexOfFirst { it.type == draggedType }
        if (fromIndex < 0) {
            clearArrangeState()
            return
        }
        val movedCard = cards.removeAt(fromIndex)
        // targetIndex is the final slot shown by the drop animation. Removing
        // a card from above it already shifts the remaining cards left; the
        // target slot must not be decremented a second time.
        cards.add(targetIndex.coerceIn(0, cards.size), movedCard)
        saveCardOrder()
        clearArrangeState()
        updateCardLayout()
        invalidate()
    }

    private fun clearArrangeState() {
        arrangeDropAnimator?.let {
            arrangeDropAnimator = null
            it.cancel()
        }
        arrangePreviewAnimator?.cancel()
        arrangePreviewAnimator = null
        arrangePreviewStart.clear()
        arrangePreviewEnd.clear()
        arrangePreviewProgress = 1f
        arrangeTargetIndex = -1
        arrangingCard = null
        arrangeMode = false
    }

    private fun cardSlotRect(index: Int): RectF {
        val column = index % gridColumns
        val row = index / gridColumns
        val left = gridPadding + column * (cardW + gridSpacing)
        val top = headerH + row * (cardH + gridSpacing)
        return RectF(left, top, left + cardW, top + cardH)
    }

    private fun nearestCardSlot(x: Float, contentY: Float): Int {
        if (cards.isEmpty()) return 0
        return cards.indices.minByOrNull { index ->
            // Use stable grid slots rather than preview positions. The
            // preview animates cards around the target while the drop itself
            // is always resolved against a slot in the grid.
            val rect = cardSlotRect(index)
            val dx = x - rect.centerX()
            val dy = contentY - rect.centerY()
            dx * dx + dy * dy
        } ?: 0
    }

    private fun previewSlotFor(cardIndex: Int, targetIndex: Int): Int {
        val draggedIndex = cards.indexOfFirst { it.type == arrangingCard }
        if (draggedIndex < 0 || cardIndex == draggedIndex) return cardIndex
        return when {
            draggedIndex < targetIndex && cardIndex in (draggedIndex + 1)..targetIndex -> cardIndex - 1
            draggedIndex > targetIndex && cardIndex in targetIndex until draggedIndex -> cardIndex + 1
            else -> cardIndex
        }
    }

    private fun currentArrangeRect(card: Card): RectF {
        val start = arrangePreviewStart[card.type]
        val end = arrangePreviewEnd[card.type]
        if (start == null || end == null) return RectF(card.rect)
        val progress = arrangePreviewProgress
        return RectF(
            start.left + (end.left - start.left) * progress,
            start.top + (end.top - start.top) * progress,
            start.right + (end.right - start.right) * progress,
            start.bottom + (end.bottom - start.bottom) * progress,
        )
    }

    private fun animateArrangePreviewTo(targetIndex: Int) {
        if (targetIndex !in cards.indices || targetIndex == arrangeTargetIndex && arrangePreviewEnd.isNotEmpty()) {
            return
        }
        arrangePreviewAnimator?.cancel()
        val currentRects = cards.associate { card ->
            card.type to currentArrangeRect(card)
        }
        arrangePreviewStart.clear()
        arrangePreviewStart.putAll(currentRects)
        arrangeTargetIndex = targetIndex
        arrangePreviewEnd.clear()
        cards.forEachIndexed { index, card ->
            if (card.type != arrangingCard) {
                arrangePreviewEnd[card.type] = cardSlotRect(previewSlotFor(index, targetIndex))
            }
        }
        arrangePreviewProgress = 0f
        arrangePreviewAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                arrangePreviewProgress = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun updateArrangePosition(x: Float, screenY: Float) {
        val edgeSize = 56f * dp
        val scrollStep = 14f * dp
        scrollY = when {
            screenY < edgeSize -> (scrollY - scrollStep).coerceAtLeast(0f)
            screenY > height - edgeSize -> (scrollY + scrollStep).coerceAtMost(maxScrollY)
            else -> scrollY
        }
        dragCenterX = x - dragOffsetX
        dragCenterY = screenY + scrollY - dragOffsetY
        animateArrangePreviewTo(nearestCardSlot(dragCenterX, dragCenterY))
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (loadingCard != null) return true

        // Content Y = screen Y shifted by current scroll offset. The complete
        // home surface, including the header and footer, shares this coordinate
        // space so that everything scrolls together.
        val cy = event.y + scrollY
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scrollScroller.forceFinished(true)
                cancelLongPressToArrange()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downTouchY = event.y
                lastTouchX = event.x
                isDragging = false
                lastTouchY  = event.y
                pressedBack = backTouch.contains(event.x, event.y)
                pressedGear = !pressedBack && gearTouch.contains(event.x, cy)
                pressedCard =
                    if (!pressedGear && !pressedBack) cards.firstOrNull {
                        scaledCardRect(it).contains(event.x, cy)
                    }?.type
                    else null
                pressedCard?.let { animateCardScale(it, 0.96f) }
                if (pressedCard != null) postDelayed(longPressToArrange, 2_000L)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                lastTouchX = event.x
                if (arrangingCard != null) {
                    updateArrangePosition(event.x, event.y)
                    postInvalidateOnAnimation()
                    return true
                }
                if (!isDragging && kotlin.math.abs(event.y - downTouchY) > touchSlop) {
                    isDragging = true
                    cancelLongPressToArrange()
                }
                if (pressedBack && !backTouch.contains(event.x, event.y)) {
                    pressedBack = false
                    invalidate()
                    return true
                }
                val dy = lastTouchY - event.y
                if (isDragging) {
                    pressedCard?.let { animateCardScale(it, 1f) }
                    pressedCard = null; pressedGear = false
                }
                scrollY = (scrollY + dy).coerceIn(0f, maxScrollY)
                lastTouchY = event.y
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                cancelLongPressToArrange()
                if (arrangingCard != null) {
                    finishArranging(event.x, event.y + scrollY)
                    velocityTracker?.recycle()
                    velocityTracker = null
                    pressedCard = null
                    pressedGear = false
                    pressedBack = false
                    return true
                }
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    if (kotlin.math.abs(velocityY) > 80f && maxScrollY > 0f) {
                        scrollScroller.fling(
                            0,
                            scrollY.toInt(),
                            0,
                            -velocityY.toInt(),
                            0,
                            0,
                            0,
                            maxScrollY.toInt(),
                        )
                        postInvalidateOnAnimation()
                    }
                    velocityTracker?.recycle()
                    velocityTracker = null
                    pressedCard = null
                    pressedGear = false
                    pressedBack = false
                    invalidate()
                    return true
                }
                velocityTracker?.recycle()
                velocityTracker = null
                if (pressedBack) {
                    val selectedBack = backTouch.contains(event.x, event.y)
                    pressedBack = false
                    if (selectedBack) {
                        com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                        onBackClicked?.invoke()
                    }
                    invalidate()
                    return true
                }
                if (pressedGear && gearTouch.contains(event.x, cy)) {
                    com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                    animateGearSpin()
                    postDelayed({ onSettingsClicked?.invoke() }, 180L)
                    pressedGear = false; invalidate(); return true
                }
                val hit = cards.firstOrNull { scaledCardRect(it).contains(event.x, cy) }?.type
                pressedCard?.let { animateCardScale(it, 1f) }
                if (hit != null && hit == pressedCard && !isChallengeLocked(hit)) {
                    com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                    loadingCard = hit
                    loadingAngle = 0f
                    loadingAnimator?.cancel()
                    loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                        duration = 700L
                        repeatCount = ValueAnimator.INFINITE
                        addUpdateListener {
                            loadingAngle = it.animatedValue as Float
                            postInvalidateOnAnimation()
                        }
                        start()
                    }
                    postDelayed({
                        if (loadingCard == hit) onGameSelected?.invoke(hit)
                    }, 140L)
                }
                pressedCard = null; pressedGear = false; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPressToArrange()
                velocityTracker?.recycle()
                velocityTracker = null
                scrollScroller.forceFinished(true)
                clearArrangeState()
                pressedCard?.let { animateCardScale(it, 1f) }
                pressedCard = null; pressedGear = false; pressedBack = false; invalidate()
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scrollScroller.computeScrollOffset()) {
            scrollY = scrollScroller.currY.toFloat().coerceIn(0f, maxScrollY)
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        cancelLongPressToArrange()
        velocityTracker?.recycle()
        velocityTracker = null
        scrollScroller.forceFinished(true)
        clearArrangeState()
        loadingAnimator?.cancel()
        loadingAnimator = null
        super.onDetachedFromWindow()
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (homeStyle == SettingsManager.HomeStyle.BROWN) drawHomeBackground(canvas)
        // The header, game cards, and footer are one continuous scrollable
        // surface. This keeps the home screen predictable on short displays
        // and makes the version/settings area move with the game catalogue.
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(0f, -scrollY)
        drawGear(canvas)
        if (currentGameMode == GameMode.IRREGULAR) {
            canvas.drawText("IRREGULAR MODE", width / 2f, headerH - 18f * dp, irregularModePaint)
        }
        if (arrangeMode && arrangeTargetIndex in cards.indices) {
            val target = cardSlotRect(arrangeTargetIndex)
            canvas.drawRoundRect(target, 18f * dp, 18f * dp, arrangeTargetFillPaint)
            canvas.drawRoundRect(target, 18f * dp, 18f * dp, arrangeTargetBorderPaint)
        }
        cards.forEach { card ->
            if (card.type != arrangingCard) {
                if (arrangeMode) {
                    val preview = currentArrangeRect(card)
                    drawCardAtCenter(canvas, card, preview.centerX(), preview.centerY())
                } else {
                    drawCard(canvas, card)
                }
            }
        }
        drawFooter(canvas)
        arrangingCard?.let { type ->
            cards.firstOrNull { it.type == type }?.let { card ->
                canvas.save()
                canvas.translate(
                    dragCenterX - card.rect.centerX(),
                    dragCenterY - card.rect.centerY(),
                )
                canvas.scale(1.04f, 1.04f, card.rect.centerX(), card.rect.centerY())
                drawCard(canvas, card)
                canvas.restore()
            }
        }
        canvas.restore()
        drawBackArrow(canvas)
        if (arrangeMode) drawArrangeHint(canvas)
    }

    private fun drawCardAtCenter(canvas: Canvas, card: Card, centerX: Float, centerY: Float) {
        canvas.save()
        canvas.translate(centerX - card.rect.centerX(), centerY - card.rect.centerY())
        drawCard(canvas, card)
        canvas.restore()
    }

    private fun drawArrangeHint(canvas: Canvas) {
        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 35, 22, 15)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 11f * sp.coerceAtMost(2f)
        }
        val hintRect = RectF(
            width / 2f - 105f * dp,
            8f * dp,
            width / 2f + 105f * dp,
            36f * dp,
        )
        canvas.drawRoundRect(hintRect, 14f * dp, 14f * dp, hintPaint)
        hintPaint.color = Color.parseColor("#F7D99B")
        canvas.drawText("Release to place game", hintRect.centerX(), hintRect.centerY() + 4f * dp, hintPaint)
    }

    private fun drawBackArrow(canvas: Canvas) {
        GamesSelectionBackButton.draw(canvas, backRect, dp, isLightMode)
    }

    private fun drawHomeBackground(canvas: Canvas) {
        val bitmap = homeBackgroundBitmap ?: return
        val scale = maxOf(
            width.toFloat() / bitmap.width.toFloat(),
            height.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            homeBackgroundPaint,
        )
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            homeBackgroundScrimPaint,
        )
    }

    private fun drawCard(canvas: Canvas, card: Card) {
        val scale = cardScales[card.type] ?: 1f
        val r = scaledCardRect(card); val pressed = pressedCard == card.type
        if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, r.centerX(), r.centerY()) }
        if (homeStyle == SettingsManager.HomeStyle.BROWN && !isLightMode) {
            drawWoodCardShell(canvas, r, pressed)
        } else {
            val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(50, 0, 0, 0)
                maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            }
            val cardRadius = 18f * dp
            canvas.drawRoundRect(
                RectF(r.left + 3f * dp, r.top + 4f * dp, r.right + 3f * dp, r.bottom + 4f * dp),
                cardRadius, cardRadius, shadowP
            )
            canvas.drawRoundRect(r, cardRadius, cardRadius, if (pressed) cardHiPaint else cardPaint)
            canvas.drawRoundRect(
                RectF(r.left + 0.5f * dp, r.top + 0.5f * dp, r.right - 0.5f * dp, r.bottom - 0.5f * dp),
                cardRadius, cardRadius, cardBorderPaint
            )
        }

        val contentCardH = r.height()
        val iconScale = if (
            card.type == GameType.AMAZONS || card.type == GameType.MANCALA
        ) {
            1f
        } else {
            0.95f
        }
        val previewSz   = minOf(contentCardH * 0.46f, r.width() * 0.64f) * iconScale
        val previewLeft = r.centerX() - previewSz / 2f
        val previewTop  = r.top + 10f * dp
        drawMiniBoard(canvas, previewLeft, previewTop, previewSz, card.type)

        val (title, desc) = when (card.type) {
            GameType.CHESS       -> "Chess"        to "vs CPU  •  2 Players"
            GameType.AMAZONS     -> "Amazons"      to "vs CPU  •  2 Players"
            GameType.CHECKERS    -> "Draughts"     to "vs CPU  •  2 Players"
            GameType.INTERNATIONAL_DRAUGHTS ->
                "International Draughts" to "vs CPU  •  2 Players"
            GameType.OTHELLO     -> "Othello"      to "vs CPU  •  2 Players"
            GameType.MORABARABA  -> "Morabaraba"   to "vs CPU  •  2 Players"
            GameType.TICTACTOE   -> "Tic-Tac-Toe"  to "vs CPU  •  2 Players"
            GameType.CONNECT_FOUR -> "Connect Four" to "vs CPU  •  2 Players"
            GameType.FOX_AND_GEESE -> "Fox & Geese" to "vs CPU  •  2 Players"
            GameType.LUDO         -> "Ludo"         to "vs CPU  •  4 Players"
            GameType.SNAKES_LADDERS -> "Snakes & Ladders" to "vs CPU  •  2 Players"
            GameType.XIANGQI      -> "Xiangqi 象棋"  to "vs CPU  •  2 Players"
            GameType.SHOGI        -> "Shogi 将棋"    to "vs CPU  •  2 Players"
            GameType.GO           -> "Go 围棋"       to "vs CPU  •  2 Players"
            GameType.MANCALA      -> "Mancala"       to "vs CPU  •  2 Players"
            GameType.YOTE         -> "Yoté"          to "vs CPU  •  2 Players"
            GameType.ONITAMA      -> "Onitama"       to "vs CPU  •  2 Players"
            GameType.FIVE_FIELD_KONO -> "Five-Field Kono" to "vs CPU  •  2 Players"
        }

        val titleLines = when (title) {
            "International Draughts" -> listOf("International", "Draughts")
            "Snakes & Ladders" -> listOf("Snakes &", "Ladders")
            "Five-Field Kono" -> listOf("Five-Field", "Kono")
            else -> listOf(title)
        }
        val titlePaint = cardTitlePaint
        val descPaint = cardDescPaint
        val titleStartY = previewTop + previewSz + titlePaint.textSize + 5f * dp
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                r.centerX(),
                titleStartY + index * (titlePaint.textSize + 1f * dp),
                titlePaint
            )
        }
        val descY = titleStartY + titleLines.size * (titlePaint.textSize + 1f * dp) + 2f * dp
        canvas.drawText(desc, r.centerX(), descY, descPaint)
        if (isChallengeLocked(card.type)) {
            drawChallengeLockOverlay(canvas, r)
        }
        if (loadingCard == card.type) {
            drawLoadingRing(canvas, r)
        }
        if (scale != 1f) canvas.restore()
    }

    private fun drawLoadingRing(canvas: Canvas, rect: RectF) {
        val radius = 5.5f * dp
        val centerX = rect.right - 17f * dp
        val centerY = rect.top + 17f * dp
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
            cardLoadingRingPaint,
        )
    }

    private fun scaledCardRect(card: Card): RectF {
        val rect = card.rect
        val halfWidth = rect.width() * gameCardScale / 2f
        val halfHeight = rect.height() * gameCardScale / 2f
        return RectF(
            rect.centerX() - halfWidth,
            rect.centerY() - halfHeight,
            rect.centerX() + halfWidth,
            rect.centerY() + halfHeight,
        )
    }

    private fun isChallengeLocked(type: GameType): Boolean =
        isChallengeMenu && type != GameType.CHESS

    private fun drawChallengeLockOverlay(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(rect, 18f * dp, 18f * dp, challengeLockOverlayPaint)

        val lockCenterX = rect.right - 26f * dp
        val lockTop = rect.top + 15f * dp
        challengeLockPaint.strokeWidth = 1.8f * dp
        canvas.drawRoundRect(
            RectF(
                lockCenterX - 8f * dp,
                lockTop + 9f * dp,
                lockCenterX + 8f * dp,
                lockTop + 22f * dp,
            ),
            2.5f * dp,
            2.5f * dp,
            challengeLockPaint,
        )
        canvas.drawArc(
            RectF(
                lockCenterX - 5.5f * dp,
                lockTop,
                lockCenterX + 5.5f * dp,
                lockTop + 16f * dp,
            ),
            180f,
            -180f,
            false,
            challengeLockPaint,
        )
        canvas.drawCircle(lockCenterX, lockTop + 15f * dp, 1.4f * dp, challengeLockPaint)

        challengeLockTextPaint.textSize = 8f * sp.coerceAtMost(3f)
        canvas.drawText(
            "LOCKED",
            lockCenterX,
            lockTop + 34f * dp,
            challengeLockTextPaint,
        )
    }

    private fun drawWoodCardShell(canvas: Canvas, r: RectF, pressed: Boolean) {
        gameCardFrameBitmap?.let {
            canvas.drawBitmap(
                it,
                null,
                r,
                bitmapPaint,
            )
            return
        }
        brownWoodCardRenderer.draw(canvas, r, pressed)
    }

    private fun drawMiniBoard(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        when (type) {
            GameType.CHESS, GameType.INTERNATIONAL_DRAUGHTS ->
                drawChessCheckersMini(canvas, left, top, size, type)
            GameType.AMAZONS -> drawAmazonsMini(canvas, left, top, size)
            GameType.CHECKERS -> drawChessCheckersMini(canvas, left, top, size, type)
            GameType.OTHELLO    -> drawOthelloMini(canvas, left, top, size)
            GameType.MORABARABA -> drawMorabarabaMini(canvas, left, top, size)
            GameType.TICTACTOE  -> drawTicTacToeMini(canvas, left, top, size)
            GameType.CONNECT_FOUR -> drawConnectFourMini(canvas, left, top, size)
            GameType.FOX_AND_GEESE -> drawFoxAndGeeseMini(canvas, left, top, size)
            GameType.LUDO -> drawLudoMini(canvas, left, top, size)
            GameType.SNAKES_LADDERS -> drawSnakesLaddersMini(canvas, left, top, size)
            GameType.XIANGQI -> drawXiangqiMini(canvas, left, top, size)
            GameType.SHOGI -> drawShogiMini(canvas, left, top, size)
            GameType.GO -> drawGoMini(canvas, left, top, size)
            GameType.MANCALA -> drawMancalaMini(canvas, left, top, size)
            GameType.YOTE -> drawYoteMini(canvas, left, top, size)
            GameType.ONITAMA -> drawOnitamaMini(canvas, left, top, size)
            GameType.FIVE_FIELD_KONO -> drawFiveFieldKonoMini(canvas, left, top, size)
        }
    }

    private fun drawFiveFieldKonoMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        fiveFieldKonoHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A8653B") }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A1E14")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, size * 0.022f)
        }
        canvas.drawRoundRect(
            RectF(left, top, left + size, top + size),
            size * 0.05f, size * 0.05f, board,
        )
        val cell = size * 0.17f
        val gridLeft = left + size * 0.16f
        val gridTop = top + size * 0.16f
        for (row in 0..4) {
            val y = gridTop + row * cell
            canvas.drawLine(gridLeft, y, gridLeft + 4 * cell, y, line)
        }
        for (col in 0..4) {
            val x = gridLeft + col * cell
            canvas.drawLine(x, gridTop, x, gridTop + 4 * cell, line)
        }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#171B20") }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3E2BB") }
        val radius = size * 0.055f
        for (col in 0..4) {
            canvas.drawCircle(gridLeft + col * cell, gridTop, radius, black)
            canvas.drawCircle(gridLeft + col * cell, gridTop + 4 * cell, radius, white)
        }
    }

    private fun drawAmazonsMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        amazonsHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val cell = size / 8f
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D8C39A") }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7E5137") }
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                canvas.drawRect(
                    left + col * cell,
                    top + row * cell,
                    left + (col + 1) * cell,
                    top + (row + 1) * cell,
                    if ((row + col) % 2 == 0) light else dark,
                )
            }
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF8E8")
            textAlign = Paint.Align.CENTER
            textSize = cell * 0.72f
        }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#211A18")
            textAlign = Paint.Align.CENTER
            textSize = cell * 0.72f
        }
        listOf(0 to 2, 0 to 5, 2 to 0, 2 to 7).forEach { (row, col) ->
            canvas.drawText("♛", left + (col + 0.5f) * cell, top + (row + 0.74f) * cell, black)
        }
        listOf(5 to 0, 5 to 7, 7 to 2, 7 to 5).forEach { (row, col) ->
            canvas.drawText("♕", left + (col + 0.5f) * cell, top + (row + 0.74f) * cell, white)
        }
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 44, 34, 29)
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.08f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(
            left + 3.5f * cell, top + 3.5f * cell,
            left + 4.5f * cell, top + 4.5f * cell, arrow,
        )
        canvas.drawLine(
            left + 4.5f * cell, top + 4.5f * cell,
            left + 4.05f * cell, top + 4.42f * cell, arrow,
        )
        canvas.drawLine(
            left + 4.5f * cell, top + 4.5f * cell,
            left + 4.42f * cell, top + 4.05f * cell, arrow,
        )
    }

    private fun drawOnitamaMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        onitamaHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9E0C8") }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26343A")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, size * 0.018f)
        }
        canvas.drawRect(left, top, left + size, top + size, board)
        for (i in 0..5) {
            val offset = size * i / 5f
            canvas.drawLine(left + offset, top, left + offset, top + size, line)
            canvas.drawLine(left, top + offset, left + size, top + offset, line)
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4B96C") }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#216176") }
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4).forEach { (row, col) ->
            canvas.drawCircle(left + (col + 0.5f) * size / 5f, top + (row + 0.5f) * size / 5f, size * 0.055f, black)
        }
        listOf(4 to 0, 4 to 1, 4 to 2, 4 to 3, 4 to 4).forEach { (row, col) ->
            canvas.drawCircle(left + (col + 0.5f) * size / 5f, top + (row + 0.5f) * size / 5f, size * 0.055f, white)
        }
    }

    private fun drawYoteMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        yoteBoardBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9A5B31") }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#351B12")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, size * 0.025f)
        }
        canvas.drawRoundRect(RectF(left, top, left + size, top + size * 0.78f), size * 0.06f, size * 0.06f, board)
        for (row in 0..5) {
            val y = top + size * 0.78f * row / 5f
            canvas.drawLine(left, y, left + size, y, line)
        }
        for (col in 0..6) {
            val x = left + size * col / 6f
            canvas.drawLine(x, top, x, top + size * 0.78f, line)
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4E0AF") }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A252A") }
        val radius = size * 0.06f
        listOf(0 to 0, 4 to 5, 2 to 2).forEach { (r, c) ->
            canvas.drawCircle(left + (c + 0.5f) * size / 6f, top + (r + 0.5f) * size * 0.78f / 5f, radius, white)
        }
        listOf(0 to 5, 4 to 0, 2 to 4).forEach { (r, c) ->
            canvas.drawCircle(left + (c + 0.5f) * size / 6f, top + (r + 0.5f) * size * 0.78f / 5f, radius, black)
        }
    }

    private fun drawMancalaMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        mancalaHomeIconBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6F351D") }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B87542")
            style = Paint.Style.STROKE
            strokeWidth = size * 0.045f
        }
        val pitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B1712") }
        val south = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7C995") }
        val north = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#79B4D8") }
        val radius = size * 0.075f
        canvas.drawRoundRect(
            RectF(left + size * 0.12f, top, left + size * 0.88f, top + size),
            size * 0.12f, size * 0.12f, board
        )
        canvas.drawRoundRect(
            RectF(left + size * 0.12f, top, left + size * 0.88f, top + size),
            size * 0.12f, size * 0.12f, rim
        )
        canvas.drawOval(
            RectF(left + size * 0.31f, top + size * 0.03f, left + size * 0.69f, top + size * 0.18f),
            pitPaint
        )
        canvas.drawOval(
            RectF(left + size * 0.31f, top + size * 0.82f, left + size * 0.69f, top + size * 0.97f),
            pitPaint
        )
        for (row in 0 until 6) {
            val y = top + size * (0.24f + row * 0.105f)
            canvas.drawCircle(left + size * 0.33f, y, radius, pitPaint)
            canvas.drawCircle(left + size * 0.67f, y, radius, pitPaint)
            canvas.drawCircle(left + size * 0.33f - radius * 0.3f, y - radius * 0.2f, radius * 0.24f, south)
            canvas.drawCircle(left + size * 0.67f + radius * 0.3f, y + radius * 0.1f, radius * 0.24f, north)
        }
    }

    private fun drawGoMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        goHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val cell = size / (GoSetup.BOARD_SIZE - 1).toFloat()
        val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C98525")
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A211B")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, size * 0.018f)
        }
        canvas.drawRect(left, top, left + size, top + size, boardPaint)
        for (i in 0 until GoSetup.BOARD_SIZE) {
            val offset = i * cell
            canvas.drawLine(left + offset, top, left + offset, top + size, linePaint)
            canvas.drawLine(left, top + offset, left + size, top + offset, linePaint)
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A211B")
        }
        for (row in setOf(3, 6, 9)) for (col in setOf(3, 6, 9)) {
            canvas.drawCircle(left + col * cell, top + row * cell, size * 0.035f, starPaint)
        }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 22, 22) }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 241, 232) }
        val stoneRadius = size * 0.036f
        listOf(2 to 2, 3 to 3, 9 to 10, 10 to 9).forEach { (row, col) ->
            canvas.drawCircle(left + col * cell, top + row * cell, stoneRadius, black)
        }
        listOf(2 to 3, 3 to 2, 9 to 9, 10 to 10).forEach { (row, col) ->
            canvas.drawCircle(left + col * cell, top + row * cell, stoneRadius, white)
        }
    }

    private fun drawShogiMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        val boardRect = RectF(left, top, left + size, top + size)
        shogiHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, boardRect, bitmapPaint)
            return
        }

        val cell = size / 9f
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C38A4C") }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33251A")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.035f
        }
        canvas.drawRect(boardRect, board)
        for (i in 0..9) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, line)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, line)
        }

        val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = cell * 0.48f
            isFakeBoldText = true
        }
        val positions = listOf(
            Triple(0, 0, "香"), Triple(0, 4, "玉"), Triple(0, 8, "香"),
            Triple(1, 1, "飛"), Triple(1, 7, "角"),
            Triple(2, 0, "歩"), Triple(2, 4, "歩"), Triple(2, 8, "歩"),
            Triple(6, 0, "歩"), Triple(6, 4, "歩"), Triple(6, 8, "歩"),
            Triple(7, 1, "角"), Triple(7, 7, "飛"),
            Triple(8, 0, "香"), Triple(8, 4, "玉"), Triple(8, 8, "香"),
        )
        positions.forEach { (row, col, symbol) ->
            piecePaint.color = if (row < 4) Color.parseColor("#2A211B") else Color.parseColor("#FFFDF2")
            val cx = left + col * cell + cell / 2f
            val cy = top + row * cell + cell / 2f
            canvas.drawText(symbol, cx, cy - (piecePaint.ascent() + piecePaint.descent()) / 2f, piecePaint)
        }
    }

    private fun drawXiangqiMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        xiangqiHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val cellW = size / 9f
        val cellH = size / 10f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D48A25")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
        }
        for (r in 0..9) canvas.drawLine(left, top + r * cellH, left + size, top + r * cellH, linePaint)
        for (c in 0..8) canvas.drawLine(left + c * cellW, top, left + c * cellW, top + size, linePaint)
    }

    private fun drawLudoMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        if (ludoHomeIconBitmap != null) {
            val boardRect = RectF(left, top, left + size, top + size)
            val boardPath = Path().apply {
                addRoundRect(boardRect, size * 0.06f, size * 0.06f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(boardPath)
            canvas.drawBitmap(ludoHomeIconBitmap, null, boardRect, bitmapPaint)
            canvas.restore()
            return
        }

        val cell = size / 7f
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#26313A") }
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), cell * 0.5f, cell * 0.5f, board)
        val colors = intArrayOf(
            Color.parseColor("#E4574F"), Color.parseColor("#4F8DDA"),
            Color.parseColor("#42A778"), Color.parseColor("#E8B84A")
        )
        val rects = arrayOf(
            RectF(left, top + cell * 4, left + cell * 3, top + size),
            RectF(left, top, left + cell * 3, top + cell * 3),
            RectF(left + cell * 4, top, left + size, top + cell * 3),
            RectF(left + cell * 4, top + cell * 4, left + size, top + size)
        )
        for (i in rects.indices) {
            board.color = colors[i]
            board.alpha = 90
            canvas.drawRoundRect(rects[i], cell * 0.35f, cell * 0.35f, board)
            board.alpha = 255
        }
        board.color = Color.parseColor("#EEEAE0")
        for (position in arrayOf(
            Position(0, 3), Position(3, 6), Position(6, 3), Position(3, 0)
        )) {
            canvas.drawRect(left + position.col * cell, top + position.row * cell,
                left + (position.col + 1) * cell, top + (position.row + 1) * cell, board)
        }
        for (i in colors.indices) {
            board.color = colors[i]
            canvas.drawCircle(
                left + cell * (1.25f + (i % 2) * 4.5f),
                top + cell * (1.25f + (i / 2) * 4.5f),
                cell * 0.28f, board
            )
        }
    }

    private fun drawSnakesLaddersMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        snakesLaddersIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }
        val cell = size / 10f
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE36B") }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B9D9B6") }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, cell * 0.04f)
        }
        for (row in 0 until 10) {
            for (column in 0 until 10) {
                val paint = if ((row + column) % 2 == 0) light else dark
                canvas.drawRect(
                    left + column * cell,
                    top + row * cell,
                    left + (column + 1) * cell,
                    top + (row + 1) * cell,
                    paint,
                )
            }
        }
        canvas.drawRect(left, top, left + size, top + size, line)
        val ladder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 62, 66)
            strokeWidth = cell * 0.11f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(left + cell * 1.2f, top + cell * 8.8f, left + cell * 4.4f, top + cell * 2.0f, ladder)
        canvas.drawLine(left + cell * 7.1f, top + cell * 8.5f, left + cell * 8.9f, top + cell * 1.1f, ladder)
        val snake = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 35, 75)
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.22f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path().apply {
            moveTo(left + cell * 7.0f, top + cell * 1.3f)
            cubicTo(
                left + cell * 5.4f, top + cell * 2.6f,
                left + cell * 8.6f, top + cell * 4.8f,
                left + cell * 6.4f, top + cell * 6.4f,
            )
            cubicTo(
                left + cell * 4.9f, top + cell * 7.6f,
                left + cell * 6.6f, top + cell * 8.5f,
                left + cell * 5.6f, top + cell * 9.1f,
            )
        }
        canvas.drawPath(path, snake)
    }

    private fun drawChessCheckersMini(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        if (type == GameType.CHESS) {
            chessHomeIconBitmap?.let {
                canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
                return
            }
        }
        if (type == GameType.INTERNATIONAL_DRAUGHTS) {
            internationalDraughtsHomeIconBitmap?.let {
                canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
                return
            }
        }
        if (type == GameType.CHECKERS) {
            draughtsHomeIconBitmap?.let {
                canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
                return
            }
        }

        val boardSize = if (type == GameType.INTERNATIONAL_DRAUGHTS) 10 else 8
        val cell = size / boardSize.toFloat()
        for (r in 0 until boardSize) for (c in 0 until boardSize) {
            val l = left + c * cell; val t = top + r * cell
            canvas.drawRect(l, t, l + cell, t + cell, if ((r + c) % 2 == 0) miniLightPaint else miniDarkPaint)
        }

        if (type == GameType.CHESS) {
            // A recognisable mid-game position reads much better than two
            // isolated pieces, especially on the small home-screen card.
            val position = arrayOf(
                "♜·♝·♚·♞♜",
                "♟♟♟·♟♟♟♟",
                "··♞·····",
                "·····♟··",
                "····♙···",
                "··♘··♘··",
                "♙♙♙·♕♙♙♙",
                "♖·♗·♔♗♘♖",
            )
            val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = cell * 0.78f
                isFakeBoldText = true
                setShadowLayer(cell * 0.05f, 0f, cell * 0.04f, Color.argb(100, 0, 0, 0))
            }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 127, 200, 248)
            }
            canvas.drawRect(
                left + cell * 4f,
                top + cell * 4f,
                left + cell * 5f,
                top + cell * 5f,
                highlightPaint,
            )
            for (r in position.indices) {
                for (c in position[r].indices) {
                    val piece = position[r][c]
                    if (piece == '·') continue
                    piecePaint.color = if (r < 4) Color.parseColor("#252A30")
                        else Color.parseColor("#FFFDE7")
                    val metrics = piecePaint.fontMetrics
                    val baseline = top + r * cell + cell / 2f -
                        (metrics.ascent + metrics.descent) / 2f
                    canvas.drawText(
                        piece.toString(),
                        left + c * cell + cell / 2f,
                        baseline,
                        piecePaint,
                    )
                }
            }
        } else {
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
            val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(75, 0, 0, 0)
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = cell * 0.045f
                color = Color.argb(150, 255, 255, 255)
            }
            val playableRows = if (boardSize == 10) 3 else 2
            val topPieces = buildList {
                for (r in 0 until playableRows) {
                    for (c in 0 until boardSize) {
                        if ((r + c) % 2 == 1) add(r to c)
                    }
                }
            }
            val bottomPieces = buildList {
                for (r in (boardSize - playableRows) until boardSize) {
                    for (c in 0 until boardSize) {
                        if ((r + c) % 2 == 1) add(r to c)
                    }
                }
            }
            fun piece(row: Int, col: Int, paint: Paint, crowned: Boolean = false) {
                val cx = left + col * cell + cell / 2f
                val cy = top + row * cell + cell / 2f
                canvas.drawCircle(cx + cell * 0.04f, cy + cell * 0.05f, cell * 0.34f, shadow)
                canvas.drawCircle(cx, cy, cell * 0.32f, paint)
                if (crowned) {
                    canvas.drawCircle(cx, cy, cell * 0.18f, ring)
                    canvas.drawCircle(cx, cy, cell * 0.07f, ring)
                }
            }
            topPieces.forEachIndexed { index, (row, col) ->
                piece(row, col, black, crowned = index == 2)
            }
            bottomPieces.forEachIndexed { index, (row, col) ->
                piece(row, col, white, crowned = index == 4)
            }
        }
    }

    private fun drawOthelloMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        othelloHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val boardSize = 8
        val cell = size / boardSize.toFloat()
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A684B") }
        val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8AC39B")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.035f
        }
        canvas.drawRect(left, top, left + size, top + size, bgP)
        for (i in 0..boardSize) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, gridP)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, gridP)
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4F0DE") }
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16232A") }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
        val pieces = listOf(
            1 to 2, 1 to 4, 2 to 3, 2 to 5, 3 to 2, 3 to 4,
            4 to 3, 4 to 5, 5 to 2, 5 to 4, 6 to 3, 6 to 5,
        )
        val whitePieces = setOf(1 to 2, 2 to 3, 3 to 4, 4 to 3, 5 to 2, 6 to 5)
        val legalMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7E8B6")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.08f
        }
        pieces.forEach { (row, col) ->
            val cx = left + col * cell + cell / 2f
            val cy = top + row * cell + cell / 2f
            val radius = cell * 0.36f
            canvas.drawCircle(cx + cell * 0.04f, cy + cell * 0.05f, radius, shadow)
            canvas.drawCircle(cx, cy, radius, if ((row to col) in whitePieces) white else black)
        }
        listOf(2 to 2, 3 to 5, 4 to 4, 5 to 3).forEach { (row, col) ->
            canvas.drawCircle(
                left + col * cell + cell / 2f,
                top + row * cell + cell / 2f,
                cell * 0.16f,
                legalMovePaint,
            )
        }
    }

    private fun drawMorabarabaMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        morabarabaHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); style = Paint.Style.STROKE
            strokeWidth = size * 0.03f; alpha = 180
        }
        val np = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC8F8"); alpha = 150 }
        val s  = size; val m  = s * 0.5f
        val off = arrayOf(0f, s * 0.22f, s * 0.44f)
        for (o in off) {
            val x1 = left + o; val y1 = top + o
            val x2 = left + s - o; val y2 = top + s - o
            canvas.drawRect(x1, y1, x2, y2, lp)
        }
        canvas.drawLine(left + m, top, left + m, top + off[1], lp)
        canvas.drawLine(left + m, top + s - off[1], left + m, top + s, lp)
        canvas.drawLine(left, top + m, left + off[1], top + m, lp)
        canvas.drawLine(left + s - off[1], top + m, left + s, top + m, lp)
        val nr = size * 0.045f
        for (o in off) {
            canvas.drawCircle(left + o, top + o, nr, np)
            canvas.drawCircle(left + s - o, top + o, nr, np)
            canvas.drawCircle(left + o, top + s - o, nr, np)
            canvas.drawCircle(left + s - o, top + s - o, nr, np)
            canvas.drawCircle(left + m, top + o, nr, np)
            canvas.drawCircle(left + o, top + m, nr, np)
            canvas.drawCircle(left + s - o, top + m, nr, np)
            canvas.drawCircle(left + m, top + s - o, nr, np)
        }
    }

    private fun drawTicTacToeMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        ticTacToeHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }
        val cell = size / 3f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444"); style = Paint.Style.STROKE
            strokeWidth = size * 0.03f; strokeCap = Paint.Cap.ROUND
        }
        for (i in 1..2) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, lp)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, lp)
        }
        val pad = cell * 0.22f
        val xp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); style = Paint.Style.STROKE
            strokeWidth = size * 0.055f; strokeCap = Paint.Cap.ROUND
        }
        val op = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); style = Paint.Style.STROKE; strokeWidth = size * 0.048f
        }
        canvas.drawLine(left + pad, top + pad, left + cell - pad, top + cell - pad, xp)
        canvas.drawLine(left + cell - pad, top + pad, left + pad, top + cell - pad, xp)
        canvas.drawCircle(left + cell * 1.5f, top + cell * 1.5f, cell * 0.28f, op)
        val x2 = left + cell * 2f; val y2 = top + cell * 2f
        canvas.drawLine(x2 + pad, y2 + pad, x2 + cell - pad, y2 + cell - pad, xp)
        canvas.drawLine(x2 + cell - pad, y2 + pad, x2 + pad, y2 + cell - pad, xp)
        canvas.drawCircle(left + cell * 0.5f, top + cell * 2.5f, cell * 0.24f, op)
        val xc = left + cell * 1.5f; val yr = top + cell * 0.5f; val rr = cell * 0.22f
        canvas.drawLine(xc - rr, yr - rr, xc + rr, yr + rr, xp)
        canvas.drawLine(xc + rr, yr - rr, xc - rr, yr + rr, xp)
    }

    private fun drawConnectFourMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        connectFourHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val cols = 7
        val rows = 6
        val cell = size / cols
        val boardP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#24527A") }
        val holeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#101820") }
        val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        val yellowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
        canvas.drawRoundRect(left, top, left + size, top + cell * rows, cell * .12f, cell * .12f, boardP)
        for (r in 0 until rows) for (c in 0 until cols) {
            val cx = left + c * cell + cell / 2f
            val cy = top + r * cell + cell / 2f
            canvas.drawCircle(cx, cy, cell * .35f, holeP)
        }
        fun piece(row: Int, col: Int, paint: Paint) {
            canvas.drawCircle(left + col * cell + cell / 2f, top + row * cell + cell / 2f, cell * .29f, paint)
        }
        piece(5, 0, redP); piece(5, 1, yellowP); piece(4, 1, redP)
        piece(5, 3, redP); piece(4, 3, yellowP); piece(3, 3, redP)
        piece(5, 5, yellowP); piece(5, 6, redP)
    }

    private fun drawFoxAndGeeseMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        foxAndGeeseHomeIconBitmap?.let {
            canvas.drawBitmap(it, null, RectF(left, top, left + size, top + size), bitmapPaint)
            return
        }

        val boardSize = FoxAndGeeseSetup.BOARD_SIZE
        val cell = size / boardSize.toFloat()
        fun point(row: Int, col: Int): Pair<Float, Float> =
            left + col * cell + cell / 2f to top + row * cell + cell / 2f
        fun isPlayable(row: Int, col: Int) =
            FoxAndGeeseSetup.isPlayable(Position(row, col))

        val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5E9D0")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B99D78")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.04f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#665849")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.055f
            strokeCap = Paint.Cap.ROUND
        }
        val cross = Path().apply {
            moveTo(left + 2f * cell, top)
            lineTo(left + 5f * cell, top)
            lineTo(left + 5f * cell, top + 2f * cell)
            lineTo(left + size, top + 2f * cell)
            lineTo(left + size, top + 5f * cell)
            lineTo(left + 5f * cell, top + 5f * cell)
            lineTo(left + 5f * cell, top + size)
            lineTo(left + 2f * cell, top + size)
            lineTo(left + 2f * cell, top + 5f * cell)
            lineTo(left, top + 5f * cell)
            lineTo(left, top + 2f * cell)
            lineTo(left + 2f * cell, top + 2f * cell)
            close()
        }
        canvas.drawPath(cross, boardPaint)
        canvas.drawPath(cross, borderPaint)

        val dirs = listOf(
            -1 to -1, -1 to 0, -1 to 1,
             0 to -1,           0 to 1,
             1 to -1,  1 to 0,  1 to 1
        )
        for (row in 0 until boardSize) for (col in 0 until boardSize) {
            if (!isPlayable(row, col)) continue
            for ((dr, dc) in dirs) {
                val nr = row + dr
                val nc = col + dc
                if (!isPlayable(nr, nc)) continue
                if (nr < row || (nr == row && nc <= col)) continue
                if (!FoxAndGeeseSetup.isConnected(Position(row, col), Position(nr, nc))) continue
                val (x1, y1) = point(row, col)
                val (x2, y2) = point(nr, nc)
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF9ED")
        }
        val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#665849")
            style = Paint.Style.STROKE
            strokeWidth = cell * 0.03f
        }
        for (row in 0 until boardSize) for (col in 0 until boardSize) {
            if (!isPlayable(row, col)) continue
            val (cx, cy) = point(row, col)
            canvas.drawCircle(cx, cy, cell * 0.10f, pointPaint)
            canvas.drawCircle(cx, cy, cell * 0.10f, pointBorderPaint)
        }

        val foxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4FAF9B")
        }
        val goosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2F2F2")
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 0, 0, 0)
        }
        fun piece(row: Int, col: Int, paint: Paint) {
            val (cx, cy) = point(row, col)
            canvas.drawCircle(cx + cell * 0.04f, cy + cell * 0.05f, cell * 0.27f, shadowPaint)
            canvas.drawCircle(cx, cy, cell * 0.27f, paint)
        }
        piece(2, 3, foxPaint)
        for (col in 0 until boardSize) piece(4, col, goosePaint)
        for (row in 5..6) for (col in 2..4) piece(row, col, goosePaint)
    }

    private fun drawGear(canvas: Canvas) {
        val cx = gearRect.centerX(); val cy = gearRect.centerY()
        if (pressedGear) {
            val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(48, 227, 184, 106) }
            canvas.drawRoundRect(gearTouch, 8f * dp, 8f * dp, bgP)
        }

        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightMode) Color.argb(225, 255, 255, 255)
            else Color.argb(225, 16, 44, 50)
        }
        val buttonEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightMode) Color.parseColor("#B8C8D1")
            else Color.parseColor("#6C573B")
            style = Paint.Style.STROKE
            strokeWidth = 1f * dp
        }
        canvas.drawCircle(cx, cy, 17f * dp, buttonPaint)
        canvas.drawCircle(cx, cy, 17f * dp, buttonEdgePaint)

        canvas.save()
        canvas.rotate(gearRotation, cx, cy)
        val gearPath = Path()
        val teeth = 8
        val points = teeth * 4
        val outerRadius = 12.5f * dp
        val innerRadius = 9.2f * dp
        for (i in 0 until points) {
            val angle = (-Math.PI / 2.0 + (Math.PI * 2.0 * i / points)).toFloat()
            val radius = when (i % 4) {
                1, 2 -> outerRadius
                else -> innerRadius
            }
            val x = cx + kotlin.math.cos(angle) * radius
            val y = cy + kotlin.math.sin(angle) * radius
            if (i == 0) gearPath.moveTo(x, y) else gearPath.lineTo(x, y)
        }
        gearPath.close()
        canvas.drawPath(gearPath, gearFillPaint)
        canvas.drawPath(gearPath, gearEdgePaint)
        canvas.drawCircle(cx, cy, 4.2f * dp, gearHolePaint)
        canvas.restore()

        canvas.drawText("Settings", cx, gearRect.bottom + 14f * dp, gearLabelPaint)
    }

    private fun animateGearSpin() {
        gearSpinAnim?.cancel()
        gearSpinAnim = ValueAnimator.ofFloat(gearRotation, gearRotation + 360f).apply {
            duration = 650L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                gearRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun applyLightTheme() {
        if (isLightMode) {
            bgPaint.color        = Color.parseColor("#F5F5F5")
            cardPaint.color      = Color.parseColor("#FFFFFF")
            cardHiPaint.color    = Color.parseColor("#F4F7FA")
            cardBorderPaint.color = Color.parseColor("#E0E5EA")
            cardTitlePaint.color = Color.parseColor("#1A1A1A")
            cardDescPaint.color  = Color.parseColor("#555555")
            copyrightPaint.color = Color.parseColor("#999999")
            gearFillPaint.color  = Color.parseColor("#1976A8")
            gearEdgePaint.color  = Color.parseColor("#0D5277")
            gearHolePaint.color  = Color.parseColor("#F5F5F5")
            gearLabelPaint.color = Color.parseColor("#1976A8")
            irregularModePaint.color = Color.parseColor("#1976A8")
        } else {
            bgPaint.color        = Color.parseColor("#121212")
            cardPaint.color      = Color.parseColor("#202429")
            cardHiPaint.color    = Color.parseColor("#2B3138")
            cardBorderPaint.color = Color.parseColor("#343A42")
            cardTitlePaint.color = Color.WHITE
            cardDescPaint.color  = Color.parseColor("#BDBDBD")
            copyrightPaint.color = Color.parseColor("#555555")
            gearFillPaint.color  = Color.parseColor("#E3B86A")
            gearEdgePaint.color  = Color.parseColor("#F7D99B")
            gearHolePaint.color  = Color.parseColor("#102C32")
            gearLabelPaint.color = Color.parseColor("#E3B86A")
            irregularModePaint.color = Color.parseColor("#F7D99B")
        }
    }

    private fun applyHomeStyleColors() {
        applyLightTheme()
        applyHomeCardTextTheme()
    }

    private fun applyHomeCardTextTheme() {
        if (isLightMode) {
            cardTitlePaint.color = Color.parseColor("#1A1A1A")
            cardDescPaint.color = Color.parseColor("#555555")
            cardTitlePaint.setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(230, 0, 0, 0))
            cardDescPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, Color.argb(210, 0, 0, 0))
            copyrightPaint.color = Color.parseColor("#999999")
        } else {
            cardTitlePaint.color = Color.WHITE
            cardDescPaint.color = Color.parseColor("#BDBDBD")
            cardTitlePaint.setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(230, 0, 0, 0))
            cardDescPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, Color.argb(210, 0, 0, 0))
            copyrightPaint.color = Color.parseColor("#555555")
        }
    }

    private fun drawFooter(canvas: Canvas) {
        // positioned in content (unscrolled) coordinates — below the last card
        val lineH = copyrightPaint.textSize * 1.8f
        val moreY = cards.last().rect.bottom + 20f * dp
        canvas.drawText("More games coming soon", width / 2f, moreY, copyrightPaint)
        canvas.drawText("©2026 MKDEV", width / 2f, moreY + lineH, copyrightPaint)
    }
}
