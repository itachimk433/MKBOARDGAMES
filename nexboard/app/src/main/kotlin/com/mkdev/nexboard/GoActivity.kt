package com.mkdev.nexboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.nexboard.engine.*
import com.mkdev.nexboard.games.go.GoRuleEngine
import com.mkdev.nexboard.games.go.GoStone
import com.mkdev.nexboard.games.go.PASS_POS
import kotlinx.coroutines.*

class GoActivity : AppCompatActivity() {

    private val engine      = GoRuleEngine()
    private var gameState   = engine.initialState()
    private var vsAI        = true
    private var playerColor = PieceColor.BLACK   // player piece color when vsAI
    private val moveHistory = ArrayDeque<GameState>()
    private val scope       = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resultRecorded = false

    private var scoreBlack = 0
    private var scoreWhite = 0

    private lateinit var hudView:   GoHudView
    private lateinit var boardView: GoBoardView
    private lateinit var scoreView: GoScoreView
    private lateinit var passBtn:   Button

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView   = GoHudView(this)
        boardView = GoBoardView(this)
        scoreView = GoScoreView(this)

        passBtn = Button(this).apply {
            text = "Pass Turn"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A3A"))
            textSize = 13f
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            addView(passBtn)
        }

        root.addView(hudView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(btnRow,    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scoreView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * dp).toInt()))
        AdManager.attachBanner(root)
        setContentView(root)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        passBtn.setOnClickListener { handlePass() }
        hudView.onBack  = { confirmBack() }
        hudView.onUndo  = { undoMove() }
        hudView.onRules = { showRules(showModeAfter = false) }

        showModeDialog()
    }

    override fun onResume()  { super.onResume();  makeFullscreen(); SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this) }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { confirmBack() }

    private fun confirmBack() {
        if (gameState.status != GameStatus.IN_PROGRESS || moveHistory.isEmpty()) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
        }
        AlertDialog.Builder(this).setTitle("Leave Match?")
            .setMessage("Leaving counts as a forfeit.")
            .setPositiveButton("Leave") { _, _ ->
                if (vsAI) SettingsManager.recordForfeit(this)
                @Suppress("DEPRECATION") super.onBackPressed()
            }
            .setNegativeButton("Keep Playing", null).show()
    }

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this).setTitle("Go  9×9")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> { vsAI = true;  showColorDialog() }
                    1 -> { vsAI = false; playerColor = PieceColor.BLACK; startGame() }
                    2 -> showRules(showModeAfter = moveHistory.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moveHistory.isEmpty()) finish() }
            .show()
    }

    private fun showColorDialog() {
        AlertDialog.Builder(this).setTitle("Play as")
            .setItems(arrayOf("⚫  Black  (moves first)", "⚪  White  (moves second)")) { _, which ->
                playerColor = if (which == 0) PieceColor.BLACK else PieceColor.WHITE
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showRules(showModeAfter: Boolean) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
GO — Rules

Overview
Two players (Black and White) take turns placing stones on the intersections of a 9×9 grid. Black moves first.

─────────────────────────

Capturing
Surround an opponent's stone or group completely — leave it with no empty adjacent intersections (liberties). It is then captured and removed from the board.

─────────────────────────

Ko Rule
You cannot place a stone that recreates the board position from the previous move (prevents infinite repetition).

─────────────────────────

Passing
Instead of placing a stone you may Pass. When both players pass consecutively, the game ends and territory is scored.

─────────────────────────

Scoring (Chinese rules)
Score = stones on board + empty territory you surround + stones you captured.
White receives 6.5 points (komi) to compensate for moving second.
The player with the higher score wins.

─────────────────────────

Strategy
• Secure corners early — they require fewer stones to enclose territory.
• Stones in groups are stronger. Aim for at least two separate "eyes" to make a group immortal.
• Capturing is tempting but building territory is often more valuable.
            """.trimIndent()
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this).setTitle("How to Play Go")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun showResultDialog() {
        val (bScore, wScore) = engine.scoreFor(gameState.board, gameState.metadata)
        val bCaps = gameState.metadata["blackCaptures"] as? Int ?: 0
        val wCaps = gameState.metadata["whiteCaptures"] as? Int ?: 0

        val title = when (gameState.status) {
            GameStatus.BLACK_WINS -> "⚫ Black Wins"
            GameStatus.WHITE_WINS -> "⚪ White Wins"
            else                  -> "Draw"
        }
        val msg = "Black: %.1f pts\nWhite: %.1f pts  (incl. 6.5 komi)\n\nCaptures — Black: %d  |  White: %d".format(
            bScore, wScore, bCaps, wCaps)

        AlertDialog.Builder(this).setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> showModeDialog() }
            .setNegativeButton("Main Menu")  { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun startGame() {
        resultRecorded = false
        SettingsManager.activateGameTheme(this, "go")
        if (vsAI) SettingsManager.setActiveGame(this, "go")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        moveHistory.clear()
        boardView.setGameState(gameState, null)
        boardView.isLocked = false
        updateHud()
        updateScore()
        if (vsAI && gameState.currentTurn != playerColor) triggerAI()
    }

    private fun handlePass() {
        if (boardView.isLocked) return
        val passMove = Move(from = PASS_POS, to = PASS_POS)
        applyAndProcess(passMove)
    }

    fun handleMove(move: Move) {
        if (boardView.isLocked) return
        applyAndProcess(move)
    }

    private fun applyAndProcess(move: Move) {
        val prevBCaps = gameState.metadata["blackCaptures"] as? Int ?: 0
        val prevWCaps = gameState.metadata["whiteCaptures"] as? Int ?: 0
        moveHistory.addLast(gameState)
        gameState = engine.applyMove(gameState, move)
        boardView.setGameState(gameState, if (move.to == PASS_POS) null else move.to)

        val newBCaps = gameState.metadata["blackCaptures"] as? Int ?: 0
        val newWCaps = gameState.metadata["whiteCaptures"] as? Int ?: 0
        val captured = (newBCaps > prevBCaps) || (newWCaps > prevWCaps)

        // Sound
        when {
            move.to == PASS_POS -> SoundPlayer.play("game_start")
            captured            -> SoundPlayer.playMovement("othello_flip")
            else                -> SoundPlayer.playMovement("othello_place")
        }

        updateScore()
        updateHud()

        if (gameState.status != GameStatus.IN_PROGRESS) {
            boardView.isLocked = true
            SoundPlayer.play("game_end")
            recordResult()
            scope.launch { delay(1400); showResultDialog() }
            return
        }
        if (vsAI && gameState.currentTurn != playerColor) {
            boardView.isLocked = true
            triggerAI()
        }
    }

    private fun undoMove() {
        if (moveHistory.isEmpty()) return
        // Undo two moves when vsAI so it's the player's turn again
        val undoCount = if (vsAI) 2 else 1
        repeat(undoCount) { if (moveHistory.isNotEmpty()) gameState = moveHistory.removeLast() }
        boardView.setGameState(gameState, gameState.lastMove?.to?.takeIf { it != PASS_POS })
        boardView.isLocked = false
        updateHud(); updateScore()
    }

    private fun triggerAI() {
        val depth     = SettingsManager.goAiDepth(this)
        val timeMs    = SettingsManager.goAiTimeLimitMs(this)
        val variety   = SettingsManager.goAiVariety(this)
        val aiPlayer  = AIPlayer(engine, maxDepth = depth, timeLimitMs = timeMs, varietyWindowOverride = variety)
        val snapshot  = gameState
        scope.launch {
            val move = withContext(Dispatchers.Default) { aiPlayer.bestMove(snapshot) }
            if (move != null && gameState === snapshot) {
                boardView.isLocked = false
                applyAndProcess(move)
            } else {
                boardView.isLocked = false
            }
        }
    }

    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this)  else SettingsManager.recordLoss(this)
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this)  else SettingsManager.recordLoss(this)
            else                  -> SettingsManager.recordDraw(this)
        }
    }

    private fun updateHud() {
        val passes = gameState.metadata["consecutivePasses"] as? Int ?: 0
        hudView.statusText = when {
            gameState.status == GameStatus.BLACK_WINS -> "⚫ Black wins"
            gameState.status == GameStatus.WHITE_WINS -> "⚪ White wins"
            gameState.status == GameStatus.DRAW       -> "Draw"
            boardView.isLocked && vsAI && gameState.currentTurn != playerColor -> "AI thinking…"
            gameState.currentTurn == PieceColor.BLACK -> if (passes == 1) "⚫ Black's turn  (White passed)" else "⚫ Black's turn"
            else                                       -> if (passes == 1) "⚪ White's turn  (Black passed)" else "⚪ White's turn"
        }
        passBtn.isEnabled = gameState.status == GameStatus.IN_PROGRESS && !boardView.isLocked
    }

    private fun updateScore() {
        val bCaps = gameState.metadata["blackCaptures"] as? Int ?: 0
        val wCaps = gameState.metadata["whiteCaptures"] as? Int ?: 0
        scoreView.update(bCaps, wCaps)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HUD VIEW
    // ══════════════════════════════════════════════════════════════════════════

    inner class GoHudView(ctx: Context) : View(ctx) {
        var statusText: String = ""
            set(v) { field = v; invalidate() }
        var onBack:  (() -> Unit)? = null
        var onUndo:  (() -> Unit)? = null
        var onRules: (() -> Unit)? = null

        private val dp   = ctx.resources.displayMetrics.density
        private val sp   = ctx.resources.displayMetrics.scaledDensity
        private val pp   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val backR   = RectF()
        private val undoR   = RectF()
        private val rulesR  = RectF()

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            backR.set(dp * 8f, dp * 10f, dp * 54f, h - dp * 10f)
            undoR.set(w - dp * 54f, dp * 10f, w - dp * 8f, h - dp * 10f)
            rulesR.set(w / 2f - dp * 28f, dp * 10f, w / 2f + dp * 28f, h - dp * 10f)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backR.contains(e.x, e.y)  -> onBack?.invoke()
                    undoR.contains(e.x, e.y)  -> onUndo?.invoke()
                    rulesR.contains(e.x, e.y) -> onRules?.invoke()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            pp.color = Color.parseColor("#1A1A2E"); pp.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, pp)

            // Back
            pp.color = Color.parseColor("#7FC8F8"); pp.textAlign = Paint.Align.CENTER
            pp.textSize = 11f * sp.coerceAtMost(3f)
            canvas.drawText("◀ BACK", backR.centerX(), backR.centerY() + pp.textSize * 0.4f, pp)

            // Undo
            canvas.drawText("UNDO ↩", undoR.centerX(), undoR.centerY() + pp.textSize * 0.4f, pp)

            // Rules
            pp.color = Color.parseColor("#666688")
            canvas.drawText("RULES", rulesR.centerX(), rulesR.centerY() + pp.textSize * 0.4f, pp)

            // Status
            pp.color = Color.WHITE; pp.textSize = 13f * sp.coerceAtMost(3f)
            canvas.drawText(statusText, w / 2f, h * 0.72f, pp)

            // Bottom separator
            pp.color = Color.parseColor("#2A2A3A"); pp.style = Paint.Style.FILL
            canvas.drawRect(0f, h - dp, w, h, pp)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GO BOARD VIEW
    // ══════════════════════════════════════════════════════════════════════════

    inner class GoBoardView(ctx: Context) : View(ctx) {

        var isLocked  = false
        private var state: GameState = engine.initialState()
        private var lastPos: Position? = null
        private var hintPos: Position? = null   // touch hover hint

        private val dp    = ctx.resources.displayMetrics.density
        private val pp    = Paint(Paint.ANTI_ALIAS_FLAG)
        private val SIZE  = engine.size

        // Board geometry (computed in onSizeChanged)
        private var boardLeft   = 0f
        private var boardTop    = 0f
        private var cellSize    = 0f
        private var stoneRadius = 0f

        // Star point positions for 9×9
        private val stars = listOf(
            Position(2, 2), Position(2, 4), Position(2, 6),
            Position(4, 2), Position(4, 4), Position(4, 6),
            Position(6, 2), Position(6, 4), Position(6, 6)
        )

        fun setGameState(gs: GameState, lastMove: Position?) {
            state = gs; lastPos = lastMove; invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val padding = minOf(w, h) * 0.072f
            val boardSz = minOf(w, h) - 2f * padding
            boardLeft   = (w - boardSz) / 2f
            boardTop    = (h - boardSz) / 2f
            cellSize    = boardSz / (SIZE - 1)
            stoneRadius = cellSize * 0.46f
        }

        private fun screenX(col: Int) = boardLeft + col * cellSize
        private fun screenY(row: Int) = boardTop  + row * cellSize

        private fun nearestIntersection(sx: Float, sy: Float): Position? {
            val col = Math.round((sx - boardLeft) / cellSize)
            val row = Math.round((sy - boardTop)  / cellSize)
            if (col !in 0 until SIZE || row !in 0 until SIZE) return null
            val dx = sx - screenX(col); val dy = sy - screenY(row)
            if (Math.sqrt((dx * dx + dy * dy).toDouble()) > cellSize * 0.55) return null
            return Position(row, col)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked || state.status != GameStatus.IN_PROGRESS) return false
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    hintPos = nearestIntersection(event.x, event.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    hintPos = null
                    val pos = nearestIntersection(event.x, event.y) ?: return true
                    if (!engine.isLegal(state, pos)) return true
                    handleMove(Move(from = pos, to = pos, captures = emptyList()))
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> { hintPos = null; invalidate() }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()

            // Background
            pp.color = Color.parseColor("#121212"); pp.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, pp)

            // Board background (wood tone)
            val bw = (SIZE - 1) * cellSize + stoneRadius * 2.2f
            pp.color = Color.parseColor("#2C1F0E")
            canvas.drawRoundRect(
                boardLeft - stoneRadius * 1.1f, boardTop - stoneRadius * 1.1f,
                boardLeft + bw - stoneRadius * 0.1f, boardTop + bw - stoneRadius * 0.1f,
                dp * 6f, dp * 6f, pp)

            // Grid lines
            pp.color = Color.parseColor("#8B6914")
            pp.style = Paint.Style.STROKE; pp.strokeWidth = dp * 0.9f
            for (i in 0 until SIZE) {
                canvas.drawLine(screenX(0), screenY(i), screenX(SIZE - 1), screenY(i), pp)
                canvas.drawLine(screenX(i), screenY(0), screenX(i), screenY(SIZE - 1), pp)
            }
            pp.style = Paint.Style.FILL

            // Star points
            pp.color = Color.parseColor("#8B6914")
            val starR = stoneRadius * 0.18f
            for (s in stars) canvas.drawCircle(screenX(s.col), screenY(s.row), starR, pp)

            // Hover hint
            val hint = hintPos
            if (hint != null && state.get(hint) == null && engine.isLegal(state, hint)) {
                pp.color = if (state.currentTurn == PieceColor.BLACK)
                    Color.argb(90, 0, 0, 0) else Color.argb(90, 255, 255, 255)
                canvas.drawCircle(screenX(hint.col), screenY(hint.row), stoneRadius, pp)
            }

            // Stones
            for (row in 0 until SIZE) {
                for (col in 0 until SIZE) {
                    val stone = state.get(row, col) as? GoStone ?: continue
                    val sx = screenX(col); val sy = screenY(row)
                    drawStone(canvas, sx, sy, stone.color)
                }
            }

            // Last move marker
            val lp = lastPos
            if (lp != null) {
                val sx = screenX(lp.col); val sy = screenY(lp.row)
                pp.color = Color.parseColor("#FF5252"); pp.style = Paint.Style.FILL
                canvas.drawCircle(sx, sy, stoneRadius * 0.26f, pp)
            }

            // Coordinate labels (A–I, 1–9)
            pp.color = Color.parseColor("#8B6914")
            pp.textAlign = Paint.Align.CENTER
            pp.textSize = cellSize * 0.30f
            for (i in 0 until SIZE) {
                val col = ('A' + i).toString()
                val rowN = (SIZE - i).toString()
                canvas.drawText(col, screenX(i), boardTop - stoneRadius * 1.3f, pp)
                canvas.drawText(rowN, boardLeft - stoneRadius * 1.5f, screenY(i) + pp.textSize * 0.38f, pp)
            }

            // Locked overlay
            if (boardView.isLocked && state.status == GameStatus.IN_PROGRESS) {
                pp.color = Color.argb(30, 0, 0, 0); pp.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, w, h, pp)
            }
        }

        private fun drawStone(canvas: Canvas, cx: Float, cy: Float, color: PieceColor) {
            val r = stoneRadius
            if (color == PieceColor.BLACK) {
                // Shadow
                pp.color = Color.argb(80, 0, 0, 0)
                canvas.drawCircle(cx + r * 0.12f, cy + r * 0.14f, r, pp)
                // Body gradient via two circles
                pp.color = Color.parseColor("#2A2A2A")
                canvas.drawCircle(cx, cy, r, pp)
                pp.color = Color.parseColor("#111111")
                canvas.drawCircle(cx + r * 0.05f, cy + r * 0.05f, r * 0.88f, pp)
                // Highlight
                pp.color = Color.argb(70, 255, 255, 255)
                canvas.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.34f, pp)
            } else {
                // Shadow
                pp.color = Color.argb(60, 0, 0, 0)
                canvas.drawCircle(cx + r * 0.10f, cy + r * 0.12f, r, pp)
                // Body
                pp.color = Color.parseColor("#F5F5F5")
                canvas.drawCircle(cx, cy, r, pp)
                // Slight inner glow
                pp.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(cx - r * 0.06f, cy - r * 0.06f, r * 0.78f, pp)
                // Outline
                pp.color = Color.parseColor("#999999"); pp.style = Paint.Style.STROKE; pp.strokeWidth = dp * 1.2f
                canvas.drawCircle(cx, cy, r, pp); pp.style = Paint.Style.FILL
                // Highlight
                pp.color = Color.argb(180, 255, 255, 255)
                canvas.drawCircle(cx - r * 0.28f, cy - r * 0.30f, r * 0.30f, pp)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCORE VIEW
    // ══════════════════════════════════════════════════════════════════════════

    inner class GoScoreView(ctx: Context) : View(ctx) {

        private var blackCaps = 0
        private var whiteCaps = 0

        private val dp = ctx.resources.displayMetrics.density
        private val sp = ctx.resources.displayMetrics.scaledDensity
        private val pp = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(bCaps: Int, wCaps: Int) {
            blackCaps = bCaps; whiteCaps = wCaps; invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            pp.color = Color.parseColor("#1A1A2E"); pp.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, pp)
            pp.color = Color.parseColor("#2A2A3A")
            canvas.drawRect(0f, 0f, w, dp, pp)

            pp.textAlign = Paint.Align.CENTER
            val cy = h * 0.54f

            // Black
            pp.color = Color.parseColor("#DDDDDD"); pp.textSize = 12f * sp.coerceAtMost(3f)
            canvas.drawText("⚫  Black", w * 0.25f, cy - pp.textSize * 0.6f, pp)
            pp.color = Color.parseColor("#7FC8F8"); pp.textSize = 14f * sp.coerceAtMost(3f); pp.isFakeBoldText = true
            canvas.drawText("Captures: $blackCaps", w * 0.25f, cy + pp.textSize * 0.6f, pp)
            pp.isFakeBoldText = false

            // Divider
            pp.color = Color.parseColor("#333344")
            canvas.drawLine(w / 2f, h * 0.18f, w / 2f, h * 0.82f, pp)

            // White
            pp.color = Color.parseColor("#DDDDDD"); pp.textSize = 12f * sp.coerceAtMost(3f)
            canvas.drawText("⚪  White (+6.5 komi)", w * 0.75f, cy - pp.textSize * 0.6f, pp)
            pp.color = Color.parseColor("#7FC8F8"); pp.textSize = 14f * sp.coerceAtMost(3f); pp.isFakeBoldText = true
            canvas.drawText("Captures: $whiteCaps", w * 0.75f, cy + pp.textSize * 0.6f, pp)
            pp.isFakeBoldText = false
        }
    }
}
