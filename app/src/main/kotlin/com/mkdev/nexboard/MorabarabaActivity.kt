package com.mkdev.nexboard

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.nexboard.engine.*
import com.mkdev.nexboard.games.morabaraba.MorabarabaBoard
import com.mkdev.nexboard.games.morabaraba.MorabarabaRuleEngine
import com.mkdev.nexboard.ui.CaptureStripView
import com.mkdev.nexboard.ui.MorabaraBoardView
import kotlinx.coroutines.*

class MorabarabaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "MORABARABA"
    }

    private lateinit var boardView:         MorabaraBoardView
    private lateinit var hudView:           MorabarabaHudView
    private lateinit var topCaptureView:    CaptureStripView
    private lateinit var bottomCaptureView: CaptureStripView
    private var engine:                     MorabarabaRuleEngine = MorabarabaRuleEngine()
    private var gameState:                  GameState = GameState(arrayOfNulls(49), boardSize = 7)
    private var vsAI                        = true
    private var playerColor                 = PieceColor.WHITE
    private var pieceCount                  = 12
    private val scope                       = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val moveHistory                 = ArrayDeque<GameState>()
    private var aiJob: Job?                 = null

    /** Stats recorded once per game. */
    private var resultRecorded = false

    private var capturedByWhite    = mutableListOf<Piece>()
    private var capturedByBlack    = mutableListOf<Piece>()
    private val captureSnapshots = ArrayDeque<Pair<List<Piece>, List<Piece>>>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0E0E"))
        }

        hudView          = MorabarabaHudView(this)
        topCaptureView   = CaptureStripView(this).also { it.dividerOnTop = false }
        boardView        = MorabaraBoardView(this)
        bottomCaptureView = CaptureStripView(this).also { it.dividerOnTop = true }

        val hudH = (72 * dp).toInt()
        val capH = (36 * dp).toInt()

        root.addView(hudView,          LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hudH))
        root.addView(topCaptureView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        root.addView(boardView,        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(bottomCaptureView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))

        if (NexBoardApp.hasHms(this)) {
            try {
                val bannerView = com.huawei.hms.ads.banner.BannerView(this).apply {
                    setAdId("g2jnehr5cv")
                    bannerAdSize = com.huawei.hms.ads.BannerAdSize.BANNER_SIZE_320_50
                    setAdListener(object : com.huawei.hms.ads.AdListener() {
                        override fun onAdLoaded() {
                            android.widget.Toast.makeText(this@MorabarabaActivity, "Banner loaded ✓", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        override fun onAdFailed(errorCode: Int) {
                            android.widget.Toast.makeText(this@MorabarabaActivity, "Banner error: $errorCode", android.widget.Toast.LENGTH_LONG).show()
                        }
                    })
                }
                root.addView(bannerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                setContentView(root)
                bannerView.loadAd(com.huawei.hms.ads.AdParam.Builder().build())
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "HMS banner error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                setContentView(root)
            }
        } else {
            setContentView(root)
        }
        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "morabaraba")
            boardView.applyTheme()
        }
    }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (moveHistory.isEmpty()) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
        }
        if (gameState.status != GameStatus.IN_PROGRESS) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
        }
        AlertDialog.Builder(this).setTitle("Leave Match?").setMessage("Leaving counts as a forfeit.")
            .setPositiveButton("Leave") { _, _ ->
                if (vsAI) SettingsManager.recordForfeit(this)
                @Suppress("DEPRECATION") super.onBackPressed()
            }
            .setNegativeButton("Keep Playing", null).show()
    }

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this).setTitle("Morabaraba")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, w ->
                when (w) {
                    0 -> showVariantDialog(isVsAI = true)
                    1 -> showVariantDialog(isVsAI = false)
                    2 -> showTutorial(showModeAfter = moveHistory.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moveHistory.isEmpty()) finish() }
            .show()
    }

    private fun showVariantDialog(isVsAI: Boolean) {
        AlertDialog.Builder(this).setTitle("Choose Variant")
            .setItems(arrayOf("6 Cows — Simple", "9 Cows — Classic", "12 Cows — Morabaraba")) { _, w ->
                pieceCount = when (w) { 0 -> 6; 1 -> 9; else -> 12 }
                vsAI = isVsAI
                if (isVsAI) showColorPickerDialog() else { playerColor = PieceColor.WHITE; startGame() }
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showColorPickerDialog() {
        AlertDialog.Builder(this).setTitle("Play as")
            .setItems(arrayOf("White ● (moves first)", "Black ● (moves second)")) { _, which ->
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showVariantDialog(isVsAI = true) }
            .show()
    }

    private fun startGame() {
        resultRecorded = false
        aiJob?.cancel(); aiJob = null
        moveHistory.clear()
        capturedByWhite.clear(); capturedByBlack.clear(); captureSnapshots.clear()
        SettingsManager.activateGameTheme(this, "morabaraba")
        if (vsAI) SettingsManager.setActiveGame(this, "morabaraba")
        SoundPlayer.init(this)
        engine                 = MorabarabaRuleEngine(pieceCount)
        gameState              = engine.initialState()
        boardView.ruleEngine   = engine
        boardView.gameState    = gameState
        boardView.playerColor  = playerColor
        boardView.vsAI         = vsAI
        boardView.isLocked     = false
        boardView.onMoveMade   = ::handleMove
        boardView.applyTheme()
        // Tap after game over → re-show result dialog
        boardView.onGameOverTapped = { showResult() }

        topCaptureView.setLabel(
            if (!vsAI) "Black's captures" else "Black ⚔"
        )
        bottomCaptureView.setLabel(
            if (!vsAI) "White's captures" else "White ⚔"
        )

        topCaptureView.update(emptyList())
        bottomCaptureView.update(emptyList())
        SoundPlayer.play("game_start")
        updateHud()
        if (vsAI && gameState.currentTurn != playerColor) triggerAI()
    }

    private fun handleMove(move: Move) {
        if (boardView.isLocked) return
        try {
            val prev       = gameState
            val moverColor = prev.currentTurn

            for (capPos in move.captures) {
                val capPiece = prev.get(capPos) ?: continue
                if (moverColor == PieceColor.WHITE) capturedByWhite.add(capPiece)
                else capturedByBlack.add(capPiece)
            }
            captureSnapshots.addLast(capturedByWhite.toList() to capturedByBlack.toList())
            gameState           = engine.applyMove(gameState, move)
            moveHistory.addLast(prev)
            boardView.gameState = gameState
            topCaptureView.update(capturedByBlack)
            bottomCaptureView.update(capturedByWhite)
            updateHud()
            playMorabarabaSound(prev, move)
            if (gameState.status != GameStatus.IN_PROGRESS) {
                recordResult()
                showResult()
                return
            }
            if (vsAI && gameState.currentTurn != playerColor) triggerAI()
        } catch (e: Exception) {
            boardView.isLocked = false
        }
    }

    private fun playMorabarabaSound(prev: com.mkdev.nexboard.engine.GameState, move: Move) {
        when (gameState.status) {
            com.mkdev.nexboard.engine.GameStatus.WHITE_WINS, com.mkdev.nexboard.engine.GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ SoundPlayer.play("game_end") }, 200)
                return
            }
            com.mkdev.nexboard.engine.GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        val wPlacedPrev = (prev.metadata[com.mkdev.nexboard.games.morabaraba.MorabarabaBoard.META_W] as? Int) ?: 0
        val bPlacedPrev = (prev.metadata[com.mkdev.nexboard.games.morabaraba.MorabarabaBoard.META_B] as? Int) ?: 0
        val isPlacementPhase = wPlacedPrev < engine.pieceCount || bPlacedPrev < engine.pieceCount
        when {
            move.captures.isNotEmpty() -> {
                SoundPlayer.playMovement("mora_capture")
                boardView.postDelayed({ SoundPlayer.playMovement("mora_mill") }, 250)
            }
            isPlacementPhase -> SoundPlayer.playMovement("mora_place")
            else             -> SoundPlayer.playMovement("mora_move")
        }
    }

    /** Record win/loss/draw once per game. */
    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW       -> SettingsManager.recordDraw(this)
            else -> {}
        }
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun triggerAI() {
        boardView.isLocked = true
        hudView.setThinking(true)
        aiJob?.cancel()
        aiJob = scope.launch {
            val depth  = SettingsManager.morabarabaAiDepth(this@MorabarabaActivity)
            val timeMs = SettingsManager.morabarabaAiTimeLimitMs(this@MorabarabaActivity)
            val ai = AIPlayer(engine, depth, timeMs)
            val move = withContext(Dispatchers.Default) {
                val legal = engine.allLegalMoves(gameState, gameState.currentTurn)
                try {
                    val best = ai.bestMove(gameState)
                    if (best != null && legal.any { it.from == best.from && it.to == best.to }) best
                    else legal.randomOrNull()
                } catch (e: Throwable) {
                    legal.randomOrNull()
                }
            }
            if (!isActive) return@launch
            hudView.setThinking(false)
            if (move != null) boardView.animateExternalMove(move)
            else boardView.isLocked = false
        }
    }

    // ─── HUD helpers ─────────────────────────────────────────────────────────

    private fun updateHud() {
        val wPlaced = (gameState.metadata[MorabarabaBoard.META_W] as? Int) ?: 0
        val bPlaced = (gameState.metadata[MorabarabaBoard.META_B] as? Int) ?: 0
        val pc      = engine.pieceCount
        val wCount  = engine.activePositions.count { gameState.get(it)?.color == PieceColor.WHITE }
        val bCount  = engine.activePositions.count { gameState.get(it)?.color == PieceColor.BLACK }
        val inPlace = wPlaced < pc || bPlaced < pc

        val wFlying = !inPlace && wCount == 3 && wPlaced >= pc
        val bFlying = !inPlace && bCount == 3 && bPlaced >= pc
        val flyingMe = vsAI && (
            (gameState.currentTurn == playerColor && playerColor == PieceColor.WHITE && wFlying) ||
            (gameState.currentTurn == playerColor && playerColor == PieceColor.BLACK && bFlying)
        )
        val aiFlying = vsAI && (
            (playerColor == PieceColor.WHITE && bFlying) ||
            (playerColor == PieceColor.BLACK && wFlying)
        )

        val (sub1, sub2) = if (inPlace) {
            val wLeft = pc - wPlaced; val bLeft = pc - bPlaced
            "Place  W:$wLeft left  B:$bLeft left" to "$wCount vs $bCount on board"
        } else {
            val flyTag = when {
                flyingMe  -> "  ✈ You can fly to any empty spot!"
                aiFlying  -> "  ✈ AI is in flying mode!"
                wFlying && !vsAI -> "  ✈ White is flying"
                bFlying && !vsAI -> "  ✈ Black is flying"
                else -> ""
            }
            "W: $wCount pieces   B: $bCount pieces" to flyTag
        }

        val label = if (vsAI && gameState.currentTurn == playerColor) "Your turn"
                    else "${if (gameState.currentTurn == PieceColor.WHITE) "White" else "Black"} to move"
        hudView.update(label, sub1, sub2, canUndo = moveHistory.isNotEmpty())
    }

    @Suppress("DEPRECATION")
    fun onBack()  { onBackPressed() }
    fun onUndo()  { doUndo() }
    fun onMenu()  { showMenuDialog() }

    private fun showMenuDialog() {
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("AI Difficulty")
        items.add("Main Menu")
        val arr = items.toTypedArray()
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(arr) { _, which ->
                when (arr[which]) {
                    "New Game" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Forfeit Match?")
                            .setMessage("Starting a new game counts as a forfeit.")
                            .setPositiveButton("Forfeit & New Game") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            }.setNegativeButton("Cancel", null).show()
                    } else showModeDialog()
                    "How to Play"   -> showTutorial(showModeAfter = false)
                    "AI Difficulty" -> showDifficultyDialog()
                    "Main Menu" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Leave Match?")
                            .setMessage("Leaving counts as a forfeit.")
                            .setPositiveButton("Leave") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                finish()
                            }.setNegativeButton("Cancel", null).show()
                    } else finish()
                }
            }.show()
    }

    private fun showDifficultyDialog() {
        val current = SettingsManager.getMorabarabaDifficulty(this)
        AlertDialog.Builder(this).setTitle("AI Difficulty")
            .setSingleChoiceItems(arrayOf("Easy", "Medium", "Hard"), current) { dlg, which ->
                val changed = which != current
                SettingsManager.setMorabarabaDifficulty(this, which); dlg.dismiss()
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    AlertDialog.Builder(this).setTitle("Restart Match?")
                        .setMessage("Difficulty changed. Restart now?")
                        .setPositiveButton("Restart") { _, _ -> showModeDialog() }
                        .setNegativeButton("Keep Playing", null).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doUndo() {
        if (moveHistory.isEmpty()) return
        aiJob?.cancel(); aiJob = null
        boardView.cancelAnim()
        hudView.setThinking(false)
        if (vsAI && moveHistory.size >= 2) {
            moveHistory.removeLast()
            captureSnapshots.removeLastOrNull()
        }
        gameState = moveHistory.removeLastOrNull() ?: engine.initialState()
        captureSnapshots.removeLastOrNull()
        val (cw, cb) = captureSnapshots.lastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())
        capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
        topCaptureView.update(capturedByBlack)
        bottomCaptureView.update(capturedByWhite)
        boardView.gameState = gameState
        updateHud()
    }

    // ─── Results ─────────────────────────────────────────────────────────────

    private fun showResult() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val msg = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win! 🎉" else "White wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win! 🎉" else "Black wins!"
            else -> "Draw!"
        }
        val resultLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> "White wins"
            GameStatus.BLACK_WINS -> "Black wins"
            else -> "Draw"
        }
        AlertDialog.Builder(this).setTitle("Game Over").setMessage(msg)
            .setPositiveButton("Play Again")   { _, _ -> showModeDialog() }
            .setNeutralButton("Watch Replay")  { _, _ -> launchReplay(resultLabel) }
            .setNegativeButton("Main Menu")    { _, _ -> finish() }
            .setCancelable(true)
            .show()
    }

    // ─── Replay ───────────────────────────────────────────────────────────────

    private fun launchReplay(resultLabel: String) {
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  "MORABARABA")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
        })
    }

    // ─── Tutorial ────────────────────────────────────────────────────────────

    private fun showTutorial(showModeAfter: Boolean = false) {
        val dp   = resources.displayMetrics.density
        val tv   = TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
MORABARABA — Rules

Overview
A traditional South African strategy game played on a board of three concentric squares connected by lines. Each player has cows (●).

─────────────────────────

Phase 1 — Placing Cows
Players alternate placing one cow per turn on any empty point.

─────────────────────────

Forming a Mill
When 3 of your cows line up along any marked line, you form a "mill". You immediately remove one of your opponent's cows.
• You cannot remove a cow that is already in a mill — unless those are the only cows left.

─────────────────────────

Phase 2 — Moving Cows
Once all cows are placed, players slide one cow at a time to an adjacent empty point along the lines.
Forming a new mill still wins you a capture.

─────────────────────────

Flying
When a player is reduced to exactly 3 cows, they may "fly" — jump to any empty point instead of sliding.

─────────────────────────

Winning
You win by either:
• Reducing your opponent to fewer than 3 cows, OR
• Leaving your opponent with no legal moves.
            """.trimIndent()
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this).setTitle("How to Play Morabaraba")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
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

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class MorabarabaHudView(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP   = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP  = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            textSize = 13f * sp.coerceAtMost(3f)
        }
        private val subP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val sub2P = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val dimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private var title    = "White to move"
        private var sub1     = ""
        private var sub2     = ""
        private var canUndo  = false
        private var thinking = false

        private val backRect = RectF()
        private val undoRect = RectF()
        private val menuRect = RectF()

        fun update(t: String, s1: String, s2: String = "", canUndo: Boolean) {
            title = t; sub1 = s1; sub2 = s2; this.canUndo = canUndo; invalidate()
        }
        fun setThinking(t: Boolean) { thinking = t; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 50f * dp; val bh = 28f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp, by, 6f * dp + bw, by + bh)
            undoRect.set(w - bw * 2.2f, by, w - bw * 1.1f, by + bh)
            menuRect.set(w - bw * 1.05f, by, w - 4f * dp, by + bh)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onBack() }
                    undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndo() }
                    menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenu() }
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, h - dp, w, h, divP)

            val rr = 5f * dp
            canvas.drawRoundRect(backRect, rr, rr, btnBgP)
            canvas.drawRoundRect(undoRect, rr, rr, btnBgP)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgP)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnP.textSize * 0.36f, btnP)
            canvas.drawText("Undo",   undoRect.centerX(), undoRect.centerY() + btnP.textSize * 0.36f,
                if (canUndo) btnP else dimP)
            canvas.drawText("Menu",   menuRect.centerX(), menuRect.centerY() + btnP.textSize * 0.36f, btnP)

            val cx = w / 2f
            val titleStr = if (thinking) "Thinking…" else title
            canvas.drawText(titleStr, cx, h * 0.38f, txtP)
            canvas.drawText(sub1, cx, h * 0.62f, subP)
            if (sub2.isNotEmpty()) canvas.drawText(sub2, cx, h * 0.82f, sub2P)
        }
    }
}
