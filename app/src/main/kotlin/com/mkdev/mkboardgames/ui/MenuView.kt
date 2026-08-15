package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.R
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseSetup

class MenuView(context: Context) : View(context) {

    var onGameSelected: ((GameType) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onLogoTapped: (() -> Unit)? = null

    /** Applied at attach-time from saved preferences; controls colour scheme. */
    var isLightMode: Boolean = false
        set(v) { field = v; applyLightTheme(); invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isLightMode = com.mkdev.mkboardgames.SettingsManager.isLightMode(context)
        com.mkdev.mkboardgames.SoundPlayer.init(context)
    }

    enum class GameType {
        CHESS, CHECKERS, INTERNATIONAL_DRAUGHTS, OTHELLO, MORABARABA, TICTACTOE, CONNECT_FOUR,
        FOX_AND_GEESE
    }

    private data class Card(val type: GameType, var rect: RectF = RectF())
    private val cards = listOf(
        Card(GameType.CHESS), Card(GameType.CHECKERS),
        Card(GameType.INTERNATIONAL_DRAUGHTS),
        Card(GameType.OTHELLO), Card(GameType.MORABARABA),
        Card(GameType.TICTACTOE), Card(GameType.CONNECT_FOUR),
        Card(GameType.FOX_AND_GEESE)
    )

    private val dp = context.resources.displayMetrics.density
    private val sp = context.resources.displayMetrics.scaledDensity

    private val logoBitmap: Bitmap? = try {
        (context.resources.getDrawable(R.drawable.ic_app_logo, null) as? BitmapDrawable)?.bitmap
    } catch (e: Exception) { null }

    private val bgPaint        = Paint().apply { color = Color.parseColor("#121212") }
    private val cardPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#202429") }
    private val cardHiPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B3138") }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#343A42")
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val accentPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC8F8"); style = Paint.Style.FILL }
    private val titlePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        textSize = 28f * sp.coerceAtMost(3f)
    }
    private val subPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
        textSize = 13f * sp.coerceAtMost(3f)
    }
    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        textSize = 14f * sp.coerceAtMost(3f)
    }
    private val cardDescPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD"); textAlign = Paint.Align.CENTER
        textSize = 10f * sp.coerceAtMost(3f)
    }
    private val copyrightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
        textSize = 10f * sp.coerceAtMost(3f)
    }
    private val versionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.LEFT
        textSize = 10f * sp.coerceAtMost(3f)
    }
    private val gearIconPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
        textSize = 22f * sp.coerceAtMost(3f)
    }
    private val gearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
        textSize = 9f * sp.coerceAtMost(3f); isFakeBoldText = true; letterSpacing = 0.08f
    }
    private val miniLightPaint = Paint().apply { color = Color.parseColor("#F0D9B5") }
    private val miniDarkPaint  = Paint().apply { color = Color.parseColor("#B58863") }
    private val miniPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bitmapPaint    = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val gridColumns = 3
    private val cardH = 136f * dp
    private val gridPadding = 12f * dp
    private val gridSpacing = 8f * dp
    private val cardW get() =
        ((width - (gridPadding * 2f) - (gridSpacing * (gridColumns - 1))) / gridColumns)
            .coerceAtLeast(1f)

    private val gearRect  = RectF()
    private val gearTouch = RectF()
    private val logoRect  = RectF()
    private var pressedCard: GameType? = null
    private var pressedGear  = false
    private var logoPressed  = false
    private var logoScale     = 1f
    private var scaleAnim: ValueAnimator? = null
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
    private var headerH    = 0f   // Y where cards begin (below the title block)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        headerH = h * 0.245f
        for (i in cards.indices) {
            val column = i % gridColumns
            val row = i / gridColumns
            val left = gridPadding + column * (cardW + gridSpacing)
            val top = headerH + row * (cardH + gridSpacing)
            cards[i].rect = RectF(left, top, left + cardW, top + cardH)
        }
        val contentBottom = cards.last().rect.bottom + 56f * dp   // room for two-line footer
        maxScrollY = maxOf(0f, contentBottom - h)

        val gs = 34f * dp; val gx = w - gs - 14f * dp; val gy = 14f * dp
        gearRect.set(gx, gy, gx + gs, gy + gs)
        gearTouch.set(gx - 4f * dp, gy - 4f * dp, gx + gs + 4f * dp, gy + gs + 18f * dp)

        val logoSize = 58f * dp + 16f * dp
        val lx2 = w / 2f; val ly2 = h * 0.075f
        logoRect.set(lx2 - logoSize / 2f, ly2 - logoSize / 2f, lx2 + logoSize / 2f, ly2 + logoSize / 2f)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Content Y = screen Y shifted by current scroll offset
        val cy = event.y + scrollY
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY  = event.y
                pressedGear = gearTouch.contains(event.x, event.y)
                if (!pressedGear && logoRect.contains(event.x, event.y)) {
                    logoPressed = true; animateLogoScale(0.85f)
                } else {
                    pressedCard = if (!pressedGear) cards.firstOrNull { it.rect.contains(event.x, cy) }?.type else null
                    pressedCard?.let { animateCardScale(it, 0.96f) }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (kotlin.math.abs(dy) > 6f) {
                    pressedCard?.let { animateCardScale(it, 1f) }
                    pressedCard = null; logoPressed = false
                }
                scrollY = (scrollY + dy).coerceIn(0f, maxScrollY)
                lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (logoPressed) {
                    animateLogoScale(1f)
                    if (logoRect.contains(event.x, event.y)) {
                        com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                        onLogoTapped?.invoke()
                    }
                    logoPressed = false; invalidate(); return true
                }
                if (pressedGear && gearTouch.contains(event.x, event.y)) {
                    com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                    onSettingsClicked?.invoke(); pressedGear = false; invalidate(); return true
                }
                val hit = cards.firstOrNull { it.rect.contains(event.x, cy) }?.type
                pressedCard?.let { animateCardScale(it, 1f) }
                if (hit != null && hit == pressedCard) {
                    com.mkdev.mkboardgames.SoundPlayer.play("ui_click")
                    onGameSelected?.invoke(hit)
                }
                pressedCard = null; pressedGear = false; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (logoPressed) { animateLogoScale(1f); logoPressed = false }
                pressedCard?.let { animateCardScale(it, 1f) }
                pressedCard = null; pressedGear = false; invalidate()
            }
        }
        return true
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawTitle(canvas)    // fixed — not affected by scroll
        drawGear(canvas)     // fixed — top-right corner
        // Version label — fixed top-left corner
        canvas.drawText("v1.2", 12f * dp, 12f * dp + versionPaint.textSize, versionPaint)

        // Scrollable region: cards + footer
        canvas.save()
        canvas.clipRect(0f, headerH - 10f * dp, width.toFloat(), height.toFloat())
        canvas.translate(0f, -scrollY)
        cards.forEach { drawCard(canvas, it) }
        drawFooter(canvas)
        canvas.restore()
    }

    private fun drawTitle(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val logoSize = 58f * dp; val lx = w / 2f; val ly = h * 0.075f

        canvas.save()
        canvas.scale(logoScale, logoScale, lx, ly)
        if (logoBitmap != null) {
            val left = lx - logoSize / 2f; val top = ly - logoSize / 2f
            canvas.drawBitmap(logoBitmap, null, RectF(left, top, left + logoSize, top + logoSize), bitmapPaint)
        } else {
            val ls = 14f * dp
            val sq1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0D9B5") }
            val sq2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC8F8") }
            canvas.drawRect(lx - ls * 1.1f, ly - ls, lx + ls * 0.1f, ly + ls, sq1)
            canvas.drawRect(lx - ls * 0.1f, ly - ls, lx + ls * 1.1f, ly + ls, sq2)
        }
        canvas.restore()

        canvas.drawText("MK~Board Games", w / 2f, h * 0.168f, titlePaint)
        canvas.drawText("Your board game hub", w / 2f, h * 0.198f, subPaint)
        accentPaint.style = Paint.Style.STROKE; accentPaint.strokeWidth = 1.5f * dp
        canvas.drawLine(w * 0.40f, h * 0.205f, w * 0.60f, h * 0.205f, accentPaint)
        accentPaint.style = Paint.Style.FILL
    }

    private fun animateLogoScale(target: Float) {
        scaleAnim?.cancel()
        scaleAnim = ValueAnimator.ofFloat(logoScale, target).apply {
            duration = 110L
            addUpdateListener { logoScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun drawCard(canvas: Canvas, card: Card) {
        val scale = cardScales[card.type] ?: 1f
        val r = card.rect; val pressed = pressedCard == card.type
        if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, r.centerX(), r.centerY()) }
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

        val previewSz   = minOf(r.height() * 0.46f, r.width() * 0.64f)
        val previewLeft = r.centerX() - previewSz / 2f
        val previewTop  = r.top + 10f * dp
        drawMiniBoard(canvas, previewLeft, previewTop, previewSz, card.type)

        val (title, desc) = when (card.type) {
            GameType.CHESS       -> "Chess"        to "vs AI  •  2 Players"
            GameType.CHECKERS    -> "Draughts"     to "vs AI  •  2 Players"
            GameType.INTERNATIONAL_DRAUGHTS ->
                "International Draughts" to "vs AI  •  2 Players"
            GameType.OTHELLO     -> "Othello"      to "vs AI  •  2 Players"
            GameType.MORABARABA  -> "Morabaraba"   to "vs AI  •  2 Players"
            GameType.TICTACTOE   -> "Tic-Tac-Toe"  to "vs AI  •  2 Players"
            GameType.CONNECT_FOUR -> "Connect Four" to "vs AI  •  2 Players"
            GameType.FOX_AND_GEESE -> "Fox and Geese" to "vs AI  •  2 Players"
        }

        val titleLines = if (title == "International Draughts") {
            listOf("International", "Draughts")
        } else {
            listOf(title)
        }
        val titleStartY = previewTop + previewSz + cardTitlePaint.textSize + 5f * dp
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                r.centerX(),
                titleStartY + index * (cardTitlePaint.textSize + 1f * dp),
                cardTitlePaint
            )
        }
        val descY = titleStartY + titleLines.size * (cardTitlePaint.textSize + 1f * dp) + 2f * dp
        canvas.drawText(desc, r.centerX(), descY, cardDescPaint)
        if (scale != 1f) canvas.restore()
    }

    private fun drawMiniBoard(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        when (type) {
            GameType.CHESS, GameType.CHECKERS, GameType.INTERNATIONAL_DRAUGHTS ->
                drawChessCheckersMini(canvas, left, top, size, type)
            GameType.OTHELLO    -> drawOthelloMini(canvas, left, top, size)
            GameType.MORABARABA -> drawMorabarabaMini(canvas, left, top, size)
            GameType.TICTACTOE  -> drawTicTacToeMini(canvas, left, top, size)
            GameType.CONNECT_FOUR -> drawConnectFourMini(canvas, left, top, size)
            GameType.FOX_AND_GEESE -> drawFoxAndGeeseMini(canvas, left, top, size)
        }
    }

    private fun drawChessCheckersMini(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        val boardSize = if (type == GameType.INTERNATIONAL_DRAUGHTS) 5 else 4
        val cell = size / boardSize.toFloat()
        for (r in 0 until boardSize) for (c in 0 until boardSize) {
            val l = left + c * cell; val t = top + r * cell
            canvas.drawRect(l, t, l + cell, t + cell, if ((r + c) % 2 == 0) miniLightPaint else miniDarkPaint)
        }
        miniPiecePaint.textSize = cell * 0.62f
        if (type == GameType.CHESS) {
            miniPiecePaint.color = Color.parseColor("#FFFDE7")
            canvas.drawText("♔", left + cell * 1.5f, top + cell * 3.5f + miniPiecePaint.textSize * 0.36f, miniPiecePaint)
            miniPiecePaint.color = Color.parseColor("#212121")
            canvas.drawText("♛", left + cell * 2.5f, top + cell * 0.5f + miniPiecePaint.textSize * 0.36f, miniPiecePaint)
        } else {
            val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
            canvas.drawCircle(left + cell * 0.5f, top + cell * 2.5f, cell * 0.33f, wp)
            canvas.drawCircle(left + cell * 1.5f, top + cell * 3.5f, cell * 0.33f, wp)
            canvas.drawCircle(left + cell * 1.5f, top + cell * 0.5f, cell * 0.33f, bp)
            canvas.drawCircle(left + cell * 2.5f, top + cell * 1.5f, cell * 0.33f, bp)
        }
    }

    private fun drawOthelloMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        val cell = size / 4f
        val bgP = Paint().apply { color = Color.parseColor("#2A4A2A") }
        val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A6A3A"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(left, top, left + size, top + size, bgP)
        for (i in 0..4) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, gridP)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, gridP)
        }
        val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
        val r = cell * 0.38f
        canvas.drawCircle(left + cell * 1.5f, top + cell * 1.5f, r, wp)
        canvas.drawCircle(left + cell * 2.5f, top + cell * 2.5f, r, wp)
        canvas.drawCircle(left + cell * 2.5f, top + cell * 1.5f, r, bp)
        canvas.drawCircle(left + cell * 1.5f, top + cell * 2.5f, r, bp)
    }

    private fun drawMorabarabaMini(canvas: Canvas, left: Float, top: Float, size: Float) {
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
            color = Color.parseColor("#E86F2D")
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
            val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 127, 200, 248) }
            canvas.drawRoundRect(gearTouch, 8f * dp, 8f * dp, bgP)
        }
        canvas.drawText("⚙", cx, cy + gearIconPaint.textSize * 0.36f, gearIconPaint)
        canvas.drawText("Settings", cx, gearRect.bottom + 14f * dp, gearLabelPaint)
    }

    private fun applyLightTheme() {
        if (isLightMode) {
            bgPaint.color        = Color.parseColor("#F5F5F5")
            cardPaint.color      = Color.parseColor("#FFFFFF")
            cardHiPaint.color    = Color.parseColor("#F4F7FA")
            cardBorderPaint.color = Color.parseColor("#E0E5EA")
            titlePaint.color     = Color.parseColor("#1A1A1A")
            subPaint.color       = Color.parseColor("#666666")
            cardTitlePaint.color = Color.parseColor("#1A1A1A")
            cardDescPaint.color  = Color.parseColor("#555555")
            copyrightPaint.color = Color.parseColor("#999999")
        } else {
            bgPaint.color        = Color.parseColor("#121212")
            cardPaint.color      = Color.parseColor("#202429")
            cardHiPaint.color    = Color.parseColor("#2B3138")
            cardBorderPaint.color = Color.parseColor("#343A42")
            titlePaint.color     = Color.WHITE
            subPaint.color       = Color.parseColor("#9E9E9E")
            cardTitlePaint.color = Color.WHITE
            cardDescPaint.color  = Color.parseColor("#BDBDBD")
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
