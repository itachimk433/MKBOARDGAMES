package com.mkdev.nexboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.View
import com.mkdev.nexboard.R
import com.mkdev.nexboard.SettingsManager

class MenuView(context: Context) : View(context) {

    var onGameSelected: ((GameType) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onLogoTapped: (() -> Unit)? = null

    /** Applied at attach-time from saved preferences; controls colour scheme. */
    var isLightMode: Boolean = false
        set(v) { field = v; applyLightTheme(); invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isLightMode = com.mkdev.nexboard.SettingsManager.isLightMode(context)
        com.mkdev.nexboard.SoundPlayer.init(context)
    }

    enum class GameType { CHESS, CHECKERS, OTHELLO, MORABARABA, TICTACTOE, LUDO }

    private data class Card(val type: GameType, var rect: RectF = RectF())
    private val cards = listOf(
        Card(GameType.CHESS), Card(GameType.CHECKERS),
        Card(GameType.OTHELLO), Card(GameType.MORABARABA), Card(GameType.TICTACTOE),
        Card(GameType.LUDO)
    )

    private val dp = context.resources.displayMetrics.density
    private val sp = context.resources.displayMetrics.scaledDensity

    private val logoBitmap: Bitmap? = try {
        (context.resources.getDrawable(R.drawable.ic_app_logo, null) as? BitmapDrawable)?.bitmap
    } catch (e: Exception) { null }

    private val bgPaint        = Paint().apply { color = Color.parseColor("#121212") }
    private val cardPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E1E1E") }
    private val cardHiPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A2A") }
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
        textSize = 18f * sp.coerceAtMost(3f)
    }
    private val cardDescPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD"); textAlign = Paint.Align.CENTER
        textSize = 12f * sp.coerceAtMost(3f)
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

    private val cardH = 82f * dp
    private val cardW get() = width * 0.88f

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
        val cx      = (w - cardW) / 2f
        val spacing = 8f * dp
        for (i in cards.indices) {
            val top = headerH + i * (cardH + spacing)
            cards[i].rect = RectF(cx, top, cx + cardW, top + cardH)
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
                        com.mkdev.nexboard.SoundPlayer.play("ui_click")
                        onLogoTapped?.invoke()
                    }
                    logoPressed = false; invalidate(); return true
                }
                if (pressedGear && gearTouch.contains(event.x, event.y)) {
                    com.mkdev.nexboard.SoundPlayer.play("ui_click")
                    onSettingsClicked?.invoke(); pressedGear = false; invalidate(); return true
                }
                val hit = cards.firstOrNull { it.rect.contains(event.x, cy) }?.type
                pressedCard?.let { animateCardScale(it, 1f) }
                if (hit != null && hit == pressedCard) {
                    com.mkdev.nexboard.SoundPlayer.play("ui_click")
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

        canvas.drawText("NexBoard", w / 2f, h * 0.168f, titlePaint)
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
        canvas.drawRoundRect(RectF(r.left + 3f, r.top + 4f, r.right + 3f, r.bottom + 4f), 16f * dp, 16f * dp, shadowP)
        canvas.drawRoundRect(r, 16f * dp, 16f * dp, if (pressed) cardHiPaint else cardPaint)
        canvas.drawRoundRect(RectF(r.left, r.top, r.left + 5f * dp, r.bottom), 3f * dp, 3f * dp, accentPaint)

        val previewSz   = r.height() * 0.72f
        val previewLeft = r.right - previewSz - 14f * dp
        val previewTop  = r.top + (r.height() - previewSz) / 2f
        drawMiniBoard(canvas, previewLeft, previewTop, previewSz, card.type)

        val textCx = r.left + (r.width() - previewSz - 28f * dp) / 2f + r.left
        val midY   = r.centerY()

        val (title, desc) = when (card.type) {
            GameType.CHESS       -> "Chess"        to "vs AI  •  2 Players"
            GameType.CHECKERS    -> "Checkers"     to "vs AI  •  2 Players"
            GameType.OTHELLO     -> "Othello"      to "vs AI  •  2 Players"
            GameType.MORABARABA  -> "Morabaraba"   to "vs AI  •  2 Players"
            GameType.TICTACTOE   -> "Tic-Tac-Toe"  to "vs AI  •  2 Players"
            GameType.LUDO        -> "Ludo"         to "vs AI  •  2 Players"
        }

        canvas.drawText(title, textCx, midY - 9f * dp + cardTitlePaint.textSize * 0.4f, cardTitlePaint)
        canvas.drawText(desc,  textCx, midY + 16f * dp, cardDescPaint)
        if (scale != 1f) canvas.restore()
    }

    private fun drawMiniBoard(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        when (type) {
            GameType.CHESS, GameType.CHECKERS -> drawChessCheckersMini(canvas, left, top, size, type)
            GameType.OTHELLO    -> drawOthelloMini(canvas, left, top, size)
            GameType.MORABARABA -> drawMorabarabaMini(canvas, left, top, size)
            GameType.TICTACTOE  -> drawTicTacToeMini(canvas, left, top, size)
            GameType.LUDO       -> drawLudoMini(canvas, left, top, size)
        }
    }

    private fun drawChessCheckersMini(canvas: Canvas, left: Float, top: Float, size: Float, type: GameType) {
        val cell = size / 4f
        for (r in 0..3) for (c in 0..3) {
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

    private fun drawLudoMini(canvas: Canvas, left: Float, top: Float, size: Float) {
        val cell = size / 5f
        // Background
        val bgP = Paint().apply { color = Color.parseColor("#111118") }
        canvas.drawRect(left, top, left + size, top + size, bgP)
        // Yard corner fills
        fun yard(col: Int, row: Int, color: Int) {
            val x = left + col * cell; val y = top + row * cell
            val yP = Paint().apply { this.color = color }
            canvas.drawRect(x, y, x + cell * 2f, y + cell * 2f, yP)
        }
        yard(0, 0, Color.parseColor("#4A1515"))   // Red TL
        yard(3, 0, Color.parseColor("#154A15"))   // Green TR
        yard(3, 3, Color.parseColor("#4A4A10"))   // Yellow BR
        yard(0, 3, Color.parseColor("#10204A"))   // Blue BL
        // Cross track (horizontal + vertical bar)
        val trackP = Paint().apply { color = Color.parseColor("#22222E") }
        canvas.drawRect(left, top + cell * 2f, left + size, top + cell * 3f, trackP)
        canvas.drawRect(left + cell * 2f, top, left + cell * 3f, top + size, trackP)
        // Player home column colour strips
        val rColP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350"); alpha = 170 }
        val yColP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFCA28"); alpha = 170 }
        canvas.drawRect(left,              top + cell * 2.15f, left + cell * 2f, top + cell * 2.85f, rColP)
        canvas.drawRect(left + cell * 3f,  top + cell * 2.15f, left + size,     top + cell * 2.85f, yColP)
        // Centre star
        val sP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER; textSize = cell * 0.75f
        }
        canvas.drawText("★", left + size / 2f, top + size / 2f + sP.textSize * 0.36f, sP)
        // Two tokens on the horizontal arm
        val rP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        val yP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFCA28") }
        val pr = cell * 0.27f
        canvas.drawCircle(left + cell * 0.75f, top + cell * 2.5f, pr, rP)
        canvas.drawCircle(left + cell * 4.25f, top + cell * 2.5f, pr, yP)
        val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 0.6f; alpha = 160 }
        canvas.drawCircle(left + cell * 0.75f, top + cell * 2.5f, pr, rimP)
        canvas.drawCircle(left + cell * 4.25f, top + cell * 2.5f, pr, rimP)
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
            cardHiPaint.color    = Color.parseColor("#EFEFEF")
            titlePaint.color     = Color.parseColor("#1A1A1A")
            subPaint.color       = Color.parseColor("#666666")
            cardTitlePaint.color = Color.parseColor("#1A1A1A")
            cardDescPaint.color  = Color.parseColor("#555555")
            copyrightPaint.color = Color.parseColor("#999999")
        } else {
            bgPaint.color        = Color.parseColor("#121212")
            cardPaint.color      = Color.parseColor("#1E1E1E")
            cardHiPaint.color    = Color.parseColor("#2A2A2A")
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
