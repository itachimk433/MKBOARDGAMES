package com.mkdev.nexboard

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.nexboard.engine.*
import com.mkdev.nexboard.games.checkers.CheckersPiece
import com.mkdev.nexboard.games.checkers.CheckersRuleEngine
import com.mkdev.nexboard.games.chess.ChessPiece
import com.mkdev.nexboard.games.chess.ChessRuleEngine
import com.mkdev.nexboard.games.othello.OthelloRuleEngine
import com.mkdev.nexboard.ui.BoardView
import com.mkdev.nexboard.ui.CaptureStripView
import kotlinx.coroutines.*

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "game_type"
    }

    private lateinit var boardView:        BoardView
    private lateinit var hudView:          HudView
    private lateinit var topCaptureView:   CaptureStripView
    private lateinit var bottomCaptureView: CaptureStripView
    private lateinit var engine:           RuleEngine
    private lateinit var gameType:         String

    private var gameState: GameState = GameState(arrayOfNulls(64))
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private val moveHistory      = ArrayDeque<GameState>()
    private val scope            = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Result guard: stats recorded exactly once per game
    private var resultRecorded = false

    private var capturedByWhite = mutableListOf<Piece>()
    private var capturedByBlack = mutableListOf<Piece>()
    private val captureSnapshots = ArrayDeque<Pair<List<Piece>, List<Piece>>>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)

        gameType = intent.getStringExtra(EXTRA_GAME) ?: "CHESS"
        engine   = when (gameType) {
            "OTHELLO"   -> OthelloRuleEngine()
            "CHECKERS"  -> CheckersRuleEngine()
            else        -> ChessRuleEngine()
        }

        val dp    = resources.displayMetrics.density
        val hudH  = (56 * dp).toInt()
        val capH  = (36 * dp).toInt()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView          = HudView(this)
        topCaptureView   = CaptureStripView(this).also { it.dividerOnTop = false }
        boardView        = BoardView(this)
        bottomCaptureView = CaptureStripView(this).also { it.dividerOnTop = true }

        container.addView(hudView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hudH))
        container.addView(topCaptureView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        container.addView(boardView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                .apply { weight = 1f })
        container.addView(bottomCaptureView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))

        val showCaptures = gameType != "OTHELLO"
        topCaptureView.visibility    = if (showCaptures) View.VISIBLE else View.GONE
        bottomCaptureView.visibility = if (showCaptures) View.VISIBLE else View.GONE

        var bannerView: com.huawei.hms.ads.banner.BannerView? = null
        try {
            com.huawei.hms.ads.HwAds.init(this)
            bannerView = com.huawei.hms.ads.banner.BannerView(this).apply {
                adId = "g2jnehr5cv"
                bannerAdSize = com.huawei.hms.ads.BannerAdSize.BANNER_SIZE_320_50
            }
            container.addView(bannerView,
                android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } catch (_: Exception) {}

        setContentView(container)
        bannerView?.loadAd(com.huawei.hms.ads.AdParam.Builder().build())

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) makeFullscreen()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameState.status != GameStatus.IN_PROGRESS || moveHistory.isEmpty()) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
        }
        AlertDialog.Builder(this).setTitle("Leave Match?").setMessage("Leaving counts as a forfeit.")
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

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        val gameName = when (gameType) {
            "OTHELLO"  -> "Othello"
            "CHECKERS" -> "Checkers"
            else       -> "Chess"
        }
        AlertDialog.Builder(this).setTitle(gameName)
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> { vsAI = true; showColorPickerDialog() }
                    1 -> { vsAI = false; playerColor = PieceColor.WHITE; startGame() }
                    2 -> showRules(showModeAfter = moveHistory.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moveHistory.isEmpty()) finish() }
            .show()
    }

    private fun showColorPickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Play as")
            .setItems(arrayOf("White (moves first)", "Black (moves second)")) { _, which ->
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showRules(showModeAfter: Boolean = false) {
        val gameName = when (gameType) {
            "OTHELLO"  -> "Othello"
            "CHECKERS" -> "Checkers"
            else       -> "Chess"
        }
        val rulesText = when (gameType) {
            "OTHELLO" -> """
OTHELLO — Rules

Overview
Othello (Reversi) is played on an 8×8 board. Each player has discs that are white on one side and black on the other. You choose your colour when starting a game vs AI.

─────────────────────────

Placing a Disc
On your turn, place a disc on any empty square that traps one or more of your opponent's discs between your new disc and another of your own.

─────────────────────────

Flipping
All opponent discs trapped in a straight line (horizontal, vertical, or diagonal) between your new disc and another of your own are flipped to your colour.

─────────────────────────

Passing
If you have no legal move, your turn is skipped. If neither player can move, the game ends.

─────────────────────────

Winning
When the board is full (or no legal moves remain), the player with more discs wins. If counts are equal, it's a draw.
            """.trimIndent()

            "CHECKERS" -> """
CHECKERS — Rules

Overview
Played on the dark squares of an 8×8 board. You choose your colour when starting a game vs AI. Pieces start on the first 3 rows of each side.

─────────────────────────

Moving
Pieces move diagonally forward one square at a time to an empty dark square.

─────────────────────────

Jumping
If an opponent's piece is diagonally adjacent and the square beyond it is empty, you must jump over it — capturing it. Multiple jumps in one turn are mandatory if available.

─────────────────────────

Kinging
When a piece reaches the far end of the board it becomes a King (marked ♔). Kings may move and jump diagonally in any direction.

─────────────────────────

Winning
Capture all of your opponent's pieces, or leave them with no legal moves.
            """.trimIndent()

            else -> """
CHESS — Rules

Overview
Two players command 16 pieces each (White and Black) on an 8×8 board. You choose your colour when starting a game vs AI.

─────────────────────────

Pieces & How They Move
♙ Pawn — forward one square; captures diagonally. On first move it may advance two squares. En passant applies.
♖ Rook — any number of squares horizontally or vertically.
♘ Knight — L-shaped: 2 squares in one direction then 1 sideways. Jumps over pieces.
♗ Bishop — any number of squares diagonally.
♕ Queen — combines Rook + Bishop.
♔ King — one square in any direction.

─────────────────────────

Castling
A special King-side or Queen-side move: the King slides 2 squares toward a Rook, and that Rook jumps to the other side of the King. Neither piece may have moved before, and the squares between them must be clear and not under attack.

─────────────────────────

Check & Checkmate
If your King is under attack you are in check — you must escape immediately. If you cannot escape, it is checkmate and you lose.

─────────────────────────

Promotion
A Pawn reaching the far rank is promoted — usually to a Queen.

─────────────────────────

Winning
Checkmate your opponent's King.
            """.trimIndent()
        }

        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = rulesText
        }
        val sv = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle("How to Play $gameName")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun startGame() {
        resultRecorded = false
        moveHistory.clear(); capturedByWhite.clear(); capturedByBlack.clear(); captureSnapshots.clear()
        SettingsManager.activateGameTheme(this, gameType.lowercase())
        if (vsAI) SettingsManager.setActiveGame(this, gameType.lowercase())

        gameState = engine.initialState()
        boardView.ruleEngine           = engine
        boardView.showMustCaptureHints = (gameType == "CHECKERS")
        boardView.directMoveMode       = (gameType == "OTHELLO")
        boardView.gameState            = gameState
        boardView.isFlipped            = false
        boardView.isLocked             = false
        boardView.rotateBlackPieces    = (!vsAI && gameType == "CHESS")
        boardView.onMoveMade           = ::handleMove
        // Tap after game ends → re-show result dialog without double-recording stats
        boardView.onGameOverTapped     = { showResultDialog() }
        boardView.refreshTheme()

        topCaptureView.setLabel(
            if (!vsAI && gameType == "CHESS") "Black's captures" else "Black ⚔"
        )
        bottomCaptureView.setLabel(
            if (!vsAI && gameType == "CHESS") "White's captures" else "White ⚔"
        )

        topCaptureView.update(emptyList())
        bottomCaptureView.update(emptyList())
        if (gameType == "CHESS" || gameType == "CHECKERS" || gameType == "OTHELLO") SoundPlayer.play("game_start")
        updateHud()
        if (vsAI && gameState.currentTurn != playerColor) triggerAI()
    }

    private fun handleMove(move: Move) {
        if (boardView.isLocked) return
        try {
            val moverColor = gameState.get(move.from)?.color
                ?: gameState.currentTurn
            for (cap in move.captures) {
                val capPiece = gameState.get(cap) ?: continue
                if (gameType == "OTHELLO") continue
                if (moverColor == PieceColor.WHITE) capturedByWhite.add(capPiece)
                else capturedByBlack.add(capPiece)
            }
            captureSnapshots.addLast(capturedByWhite.toList() to capturedByBlack.toList())
            moveHistory.addLast(gameState)
            gameState = engine.applyMove(gameState, move)
            boardView.gameState = gameState
            // Othello: pop-animate placed disc + all flipped discs
            if (gameType == "OTHELLO") {
                val popSet = (move.captures + listOf(move.to)).toSet()
                if (popSet.isNotEmpty()) boardView.playOthelloPopAnim(popSet)
            }
            topCaptureView.update(capturedByBlack)
            bottomCaptureView.update(capturedByWhite)
            updateHud()
            if (gameType == "CHESS") playChessSound(move)
            if (gameType == "CHECKERS") playCheckersSound(move)
            if (gameType == "OTHELLO") playOthelloSound(move)
            if (gameState.status != GameStatus.IN_PROGRESS) {
                recordResult()
                showResultDialog()
                return
            }
            if (vsAI && gameState.currentTurn != playerColor) triggerAI()
        } catch (e: Exception) {
            Toast.makeText(this, "Move error — please try again", Toast.LENGTH_SHORT).show()
            if (moveHistory.isNotEmpty()) {
                gameState = moveHistory.removeLast(); captureSnapshots.removeLastOrNull()
                val (cw, cb) = captureSnapshots.lastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())
                capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
                boardView.gameState = gameState; boardView.isLocked = false
                topCaptureView.update(capturedByBlack)
                bottomCaptureView.update(capturedByWhite)
            }
        }
    }

    /** Record win/loss/draw once per game. Safe to call multiple times. */
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

    // ─── Chess sounds ─────────────────────────────────────────────────────────

    private fun playChessSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                SoundPlayer.play("move_check")
                boardView.postDelayed({ SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        // Primary move sound
        when {
            move.promotionType != null      -> SoundPlayer.playMovement("promote")
            move.metadata["castle"] != null -> SoundPlayer.playMovement("castle")
            move.captures.isNotEmpty()      -> SoundPlayer.playMovement("capture")
            vsAI && gameState.currentTurn == playerColor ->
                SoundPlayer.playMovement("move_opponent")
            else -> SoundPlayer.playMovement("move_self")
        }
        // Check sound plays on top (SoundPool supports simultaneous streams)
        val isCheck = (engine as? com.mkdev.nexboard.games.chess.ChessRuleEngine)
            ?.isInCheck(gameState, gameState.currentTurn) == true
        if (isCheck) SoundPlayer.playMovement("move_check")
    }

    // ─── Checkers sounds ──────────────────────────────────────────────────────

    private fun playCheckersSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        when {
            move.captures.isNotEmpty() -> SoundPlayer.playMovement("checkers_capture")
            else -> {
                val prevState = moveHistory.lastOrNull()
                val wasKing = prevState?.get(move.from)?.let { (it as? com.mkdev.nexboard.games.checkers.CheckersPiece)?.isKing } ?: false
                val isNowKing = (gameState.get(move.to) as? com.mkdev.nexboard.games.checkers.CheckersPiece)?.isKing ?: false
                if (!wasKing && isNowKing) SoundPlayer.playMovement("checkers_king")
                else SoundPlayer.playMovement("checkers_move")
            }
        }
    }

    // ─── Othello sounds ───────────────────────────────────────────────────────

    private fun playOthelloSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        SoundPlayer.playMovement("othello_place")
        if (move.captures.isNotEmpty()) {
            boardView.postDelayed({ SoundPlayer.playMovement("othello_flip") }, 150)
        }
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun triggerAI() {
        boardView.isLocked = true; hudView.setThinking(true)
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    when (gameType) {
                        "OTHELLO"  -> {
                            val ai = AIPlayer(engine, maxDepth = SettingsManager.othelloAiDepth(this@GameActivity), timeLimitMs = 3000L)
                            ai.bestMove(gameState)
                                ?: engine.allLegalMoves(gameState, gameState.currentTurn).randomOrNull()
                        }
                        "CHECKERS" -> {
                            val ai = AIPlayer(engine, maxDepth = SettingsManager.checkersAiDepth(this@GameActivity))
                            ai.bestMove(gameState)
                        }
                        else -> {
                            // Chess — deeper search with quiescence and time limit
                            val depth    = SettingsManager.chessAiDepth(this@GameActivity)
                            val timeMs   = SettingsManager.chessAiTimeLimitMs(this@GameActivity)
                            val quiesce  = SettingsManager.chessAiQuiesceDepth(this@GameActivity)
                            val ai = AIPlayer(engine, maxDepth = depth, timeLimitMs = timeMs, quiesceDepth = quiesce)
                            ai.bestMove(gameState)
                        }
                    }
                } catch (e: Throwable) { null }
            }
            hudView.setThinking(false)
            if (move != null) boardView.animateExternalMove(move)
            else boardView.isLocked = false
        }
    }

    // ─── HUD helpers ─────────────────────────────────────────────────────────

    private fun updateHud() {
        val turn  = if (gameState.currentTurn == PieceColor.WHITE) "White" else "Black"
        val label = if (vsAI && gameState.currentTurn == playerColor) "Your turn" else "$turn to move"
        hudView.setInfo(label, canUndo = moveHistory.isNotEmpty())
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
        var undoCount = 1
        if (vsAI && moveHistory.size >= 2) { moveHistory.removeLast(); undoCount = 2 }
        gameState = moveHistory.removeLastOrNull() ?: return
        repeat(undoCount) { captureSnapshots.removeLastOrNull() }
        val (cw, cb) = captureSnapshots.lastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())
        capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
        topCaptureView.update(capturedByBlack)
        bottomCaptureView.update(capturedByWhite)
        boardView.isLocked = false; boardView.gameState = gameState; updateHud()
    }

    fun onMenuClicked() {
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        val items = mutableListOf("New Game", "How to Play")
        // Othello difficulty is fixed (always hard) — only Chess and Checkers show difficulty
        if (vsAI && gameType != "OTHELLO") items.add("AI Difficulty")
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
                    "How to Play"   -> showRules(showModeAfter = false)
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
        val getDiff: () -> Int
        val setDiff: (Int) -> Unit
        when (gameType) {
            "CHECKERS" -> { getDiff = { SettingsManager.getCheckersDifficulty(this) }; setDiff = { v -> SettingsManager.setCheckersDifficulty(this, v) } }
            else       -> { getDiff = { SettingsManager.getChessDifficulty(this) };    setDiff = { v -> SettingsManager.setChessDifficulty(this, v) } }
        }
        val current = getDiff()
        val labels  = arrayOf("Easy", "Medium", "Hard")
        AlertDialog.Builder(this).setTitle("AI Difficulty")
            .setSingleChoiceItems(labels, current) { dlg, which ->
                val changed = which != current
                setDiff(which); dlg.dismiss()
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    AlertDialog.Builder(this).setTitle("Restart Match?")
                        .setMessage("Difficulty changed. Restart now?")
                        .setPositiveButton("Restart") { _, _ -> startGame() }
                        .setNegativeButton("Keep Playing", null).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Result dialog ────────────────────────────────────────────────────────

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val msg = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                if (vsAI && playerColor == PieceColor.WHITE) "You win! 🎉" else "White wins!"
            GameStatus.BLACK_WINS ->
                if (vsAI && playerColor == PieceColor.BLACK) "You win! 🎉" else "Black wins!"
            GameStatus.DRAW -> "Draw! Well played."
            else -> ""
        }
        val resultLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> "White wins"
            GameStatus.BLACK_WINS -> "Black wins"
            GameStatus.DRAW       -> "Draw"
            else -> ""
        }
        val builder = AlertDialog.Builder(this).setTitle("Game Over").setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> startGame() }
            .setNegativeButton("Main Menu")  { _, _ -> finish() }
            .setCancelable(true)

        if (gameType != "OTHELLO") {
            builder.setNeutralButton("Watch Replay") { _, _ -> launchReplay(resultLabel) }
        }
        builder.show()
    }

    private fun launchReplay(resultLabel: String) {
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  gameType)
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
        })
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class HudView(ctx: Context) : View(ctx) {
        private var title    = "White to move"
        private var canUndo  = false
        private var thinking = false

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity

        private val bgPaint        = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divPaint       = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.LEFT; isFakeBoldText = true
            textSize = 15f * sp.coerceAtMost(3f)
        }
        private val subPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.LEFT
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val btnBgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val dimPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private val backRect = RectF()
        private val undoRect = RectF()
        private val menuRect = RectF()

        fun setInfo(label: String, canUndo: Boolean) {
            title = label; this.canUndo = canUndo; invalidate()
        }
        fun setThinking(t: Boolean) { thinking = t; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 50f * dp; val bh = 28f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp, by, 6f * dp + bw, by + bh)
            undoRect.set(w - bw * 2.2f, by, w - bw * 1.1f, by + bh)
            menuRect.set(w - bw * 1.05f, by, w - 4f * dp, by + bh)
        }

        @Suppress("DEPRECATION")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); this@GameActivity.onBackPressed() }
                    undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndoClicked() }
                    menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenuClicked() }
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            canvas.drawRect(0f, h - dp, w, h, divPaint)

            val rr = 5f * dp
            canvas.drawRoundRect(backRect, rr, rr, btnBgPaint)
            canvas.drawRoundRect(undoRect, rr, rr, btnBgPaint)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgPaint)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnPaint.textSize * 0.36f, btnPaint)
            canvas.drawText("Undo", undoRect.centerX(), undoRect.centerY() + btnPaint.textSize * 0.36f,
                if (canUndo) btnPaint else dimPaint)
            canvas.drawText("Menu", menuRect.centerX(), menuRect.centerY() + btnPaint.textSize * 0.36f, btnPaint)

            val cx = (backRect.right + undoRect.left) / 2f
            val sub = if (thinking) "Thinking…" else ""
            canvas.drawText(title, cx, h / 2f - txtPaint.textSize * 0.15f, txtPaint.also { it.textAlign = Paint.Align.CENTER })
            if (sub.isNotEmpty()) canvas.drawText(sub, cx, h / 2f + subPaint.textSize * 1.1f, subPaint.also { it.textAlign = Paint.Align.CENTER })
            txtPaint.textAlign = Paint.Align.LEFT
            subPaint.textAlign = Paint.Align.LEFT
        }
    }
}
