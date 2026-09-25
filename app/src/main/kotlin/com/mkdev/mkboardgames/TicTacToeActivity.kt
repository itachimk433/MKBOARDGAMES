package com.mkdev.mkboardgames

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.tictactoe.TicTacToePiece
import com.mkdev.mkboardgames.games.tictactoe.TicTacToeRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class TicTacToeActivity : AppCompatActivity() {

    private var boardSize   = 3
    private var winLength   = 3
    private var engine      = TicTacToeRuleEngine(boardSize, winLength)
    private var gameState   = engine.initialState()
    private var vsAI        = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private val moveHistory = ArrayDeque<GameState>()
    private var undosRemaining = 3
    private val scope       = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scoreX     = 0
    private var scoreO     = 0
    private var scoreDraws = 0

    /** Stats recorded once per game — prevents double-counting on re-shown result dialog. */
    private var resultRecorded = false
    private var interstitialAd: Any? = null

    private val redoGameStates   = ArrayDeque<GameState>()
    private val redoRemovedMoves = ArrayDeque<List<GameState>>()

    private lateinit var hudView:   StandardGameHudView
    private lateinit var boardView: TicBoardView
    private lateinit var scoreView: ScoreView
    private lateinit var autoplayButton: AutoplayButtonView
    private lateinit var gameRoot: View
    private lateinit var gameOverView: SnakesLaddersGameOverView
    private var autoplayEnabled = false
    private val autoplayAllowed: Boolean
        get() = SettingsManager.currentMode(this) == GameMode.IRREGULAR
    private var exitPosted = false

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView = StandardGameHudView(this).apply {
            onBack = { this@TicTacToeActivity.onBackPressed() }
            onUndo = { this@TicTacToeActivity.onUndoClicked() }
            onRedo = { this@TicTacToeActivity.onRedoClicked() }
            onMenu = { this@TicTacToeActivity.onMenuClicked() }
        }
        boardView = TicBoardView(this)
        scoreView = ScoreView(this)
        autoplayButton = AutoplayButtonView(this)
        autoplayButton.onAutoplayChanged = { enabled ->
            if (autoplayAllowed && vsAI) {
                autoplayEnabled = enabled
                if (enabled &&
                    matchStarted &&
                    gameState.status == GameStatus.IN_PROGRESS &&
                    !boardView.isLocked &&
                    aiControlsCurrentTurn()
                ) {
                    triggerAI()
                }
            }
        }

        root.addView(hudView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(autoplayButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (76 * dp).toInt(),
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        autoplayButton.visibility = View.GONE
        root.addView(scoreView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))

        AdManager.attachBanner(root)
        gameRoot = root
        gameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            bottomCaptureTopPxProvider = { scoreView.top.toFloat() }
            onReplay = {
                visibility = View.GONE
                startGame()
            }
            onWatchReplay = {
                visibility = View.GONE
                launchReplay(currentResultLabel())
            }
            onHome = {
                visibility = View.GONE
                clearPausedMatch()
                finish()
            }
        }
        val screenRoot = FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(-1, -1))
            addView(gameOverView, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(screenRoot)
        hideBoardUntilMatchStarts()

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        activityResumed = true
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "ttt")
            boardView.applyTheme()
        }
        resumeComputerTurnIfNeeded()
    }
    override fun onPause() {
        SoundPlayer.stopAll()
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() {
        stopAutomatedGameplay()
        super.onDestroy()
        scope.cancel()
    }

    override fun finish() {
        finishToHome()
    }

    override fun finishAfterTransition() {
        finishToHome()
    }

    private fun finishToHome() {
        if (exitPosted) return
        exitPosted = true
        if (::gameRoot.isInitialized) gameRoot.visibility = View.GONE
        window.decorView.postDelayed({
            super.finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 16L)
    }

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) boardView.isLocked = false
        if (::hudView.isInitialized) hudView.setThinking(false)
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (matchStarted && gameState.status != GameStatus.IN_PROGRESS) {
            showResultDialog()
            return
        }
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            finish()
            return
        }
        if (!canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        stopAutomatedGameplay()
        MusicPlayer.enterPausedMatch(this)
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(this, "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
            ), 520f, "T I C · T A C · T O E", onCancel = { showBoardAfterDialog() }) { which, dialog ->
            dialog.dismiss()
            when (which) {
                0 -> pauseMatchAndExit()
                1 -> {
                    clearPausedMatch()
                    finish()
                }
                2 -> showBoardAfterDialog()
            }
        }
    }

    private fun pauseMatchAndExit() {
        stopAutomatedGameplay()
        savePausedMatch()
        finish()
    }

    private fun savePausedMatch() {
        if (gameState.status != GameStatus.IN_PROGRESS || gameState.moveHistory.isEmpty()) return
        PausedMatchStore.save(
            this,
            gameType = "TICTACTOE",
            vsAI = vsAI,
            playerColor = playerColor.name,
            boardSize = boardSize,
            winLength = winLength,
            moves = gameState.moveHistory,
        )
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, "TICTACTOE")

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
        hideBoardWhileDialogIsOpen()
        val paused = PausedMatchStore.has(this, "TICTACTOE")
        val menuView = ChessMenuView(
            this,
            paused,
            gameLabel = "T I C · T A C · T O E",
        )
        menuView.onVsAi = {
            StyledDialogs.dismiss()
            vsAI = true
            showBoardSizeDialog(fromMode = true)
        }
        menuView.onTwoPlayers = {
            StyledDialogs.dismiss()
            vsAI = false
            playerColor = PieceColor.WHITE
            showBoardSizeDialog(fromMode = false)
        }
        menuView.onHowToPlay = {
            StyledDialogs.dismiss()
            showRules(showModeAfter = !matchStarted)
        }
        menuView.onResumeMatch = {
            StyledDialogs.dismiss()
            resumePausedMatch()
        }
        StyledDialogs.showFullScreenView(this, menuView) {
                if (!matchStarted) finish() else showBoardAfterDialog()
        }
    }

    private fun showBoardSizeDialog(fromMode: Boolean) {
        hideBoardWhileDialogIsOpen()
        val sizeLabels = arrayOf(
            "3×3 — Classic  (3 in a row)",
            "4×4 — Medium   (4 in a row)",
            "5×5 — Large    (4 in a row)"
        )
        StyledDialogs.showChoices(this, "Board Size", "Choose the board that suits your match.",
            sizeLabels.mapIndexed { index, label ->
                StyledDialogs.choice(label, if (index == 0) "Classic game" else "More room to play", listOf("III", "IV", "V")[index], listOf("#E3B86A", "#8EC7B9", "#A9B6E8")[index])
            }, 520f, "T I C · T A C · T O E", onCancel = { showModeDialog() }) { which, dialog ->
                dialog.dismiss()
                boardSize = which + 3
                winLength = winLengthFor(boardSize)
                engine    = TicTacToeRuleEngine(boardSize, winLength)
                boardView.updateBoardSize(boardSize)
                if (fromMode && vsAI) showColorPickerDialog() else startGame()
            }
    }

    /** Returns the appropriate win-line length for the given board size. */
    private fun winLengthFor(size: Int): Int = when (size) {
        3    -> 3
        4    -> 4
        else -> 4   // 5×5
    }

    private fun showColorPickerDialog() {
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(this, "Play As", "Choose your side before the first move.",
            listOf(
                StyledDialogs.choice("X", "Goes first", "", "#E3B86A"),
                StyledDialogs.choice("O", "Goes second", "", "#A9B6E8"),
            ), 420f, "T I C · T A C · T O E", onCancel = { showBoardSizeDialog(fromMode = true) }) { which, dialog ->
                dialog.dismiss()
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
    }

    private fun showRules(showModeAfter: Boolean = false) {
        hideBoardWhileDialogIsOpen()
        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
TIC-TAC-TOE — Rules

Overview
Played on a square grid. X always goes first. Players take turns placing their mark on an empty cell.

─────────────────────────

Board Sizes & Win Conditions
• 3×3 board: get 3 in a row to win
• 4×4 board: get 4 in a row to win
• 5×5 board: get 4 in a row to win

─────────────────────────

Winning
The first player to get the required number of marks in a row — horizontally, vertically, or diagonally — wins.

─────────────────────────

Draw
The game ends as a draw if:
• All cells are filled and neither player has won, OR
• No win line can possibly be completed by either player — the game ends early instead of playing out pointlessly.

─────────────────────────

Strategy
• On 3×3, the centre is part of 4 win lines — take it early.
• Block your opponent if they have 2 (or more) in a row.
• Set up a "fork" — two simultaneous winning threats your opponent can't both block.
• On larger boards, control the centre region and connect threats.
            """.trimIndent()
        }
        StyledDialogs.showRules(
            this,
            "Tic-Tac-Toe",
            tv.text.toString(),
            "T I C · T A C · T O E",
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() },
        )
    }

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        showBoardAfterDialog(resumeAi = false)
        matchStarted = true
        SettingsManager.recordRecentlyPlayed(this, "TICTACTOE")
        MusicPlayer.enterMatch(this)
        resultRecorded = false
        interstitialAd = null
        redoGameStates.clear(); redoRemovedMoves.clear()
        undosRemaining = SettingsManager.undoCredits(this, "TIC_TAC_TOE", vsAI)
        AdManager.loadInterstitial(this) { interstitialAd = it }
        AdManager.loadRewarded(this)
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, "ttt")
        if (vsAI) SettingsManager.setActiveGame(this, "ttt")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        moveHistory.clear()
        boardView.reset(gameState)
        autoplayEnabled = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        autoplayButton.visibility = if (autoplayAllowed && vsAI) View.VISIBLE else View.GONE
        // Re-show result dialog when board is tapped after game over
        boardView.onGameOverTapped = { showResultDialog() }
        scoreView.setLabels(boardSize)
        updateHud()
        if (restoring != null) {
            restoreMoves(restoring.moves)
            clearPausedMatch()
            if (vsAI && gameState.currentTurn != playerColor) triggerAI()
        } else if (vsAI && gameState.currentTurn != playerColor) {
            triggerAI()
        }
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, "TICTACTOE") ?: run {
            showModeDialog()
            return
        }
        boardSize = paused.boardSize ?: 3
        winLength = paused.winLength ?: winLengthFor(boardSize)
        engine = TicTacToeRuleEngine(boardSize, winLength)
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }
            .getOrDefault(PieceColor.WHITE)
        boardView.updateBoardSize(boardSize)
        startGame(paused)
    }

    private fun restoreMoves(moves: List<Move>) {
        for (move in moves) {
            moveHistory.add(gameState)
            gameState = engine.applyMove(gameState, move)
        }
        boardView.setGameState(gameState, lastMove = gameState.lastMove?.to)
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        scoreView.setLabels(boardSize)
        updateHud()
    }

    internal fun handleMove(move: Move) {
        if (boardView.isLocked) return
        val moverWasX = gameState.currentTurn == PieceColor.WHITE
        redoGameStates.clear(); redoRemovedMoves.clear()
        moveHistory.add(gameState)
        gameState = engine.applyMove(gameState, move)
        boardView.setGameState(gameState, lastMove = move.to)

        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
            autoplayButton.setAutoplayEnabled(false, animate = false)
            boardView.isLocked = true
            when (gameState.status) {
                GameStatus.WHITE_WINS -> { scoreX++; SoundPlayer.play("game_end") }
                GameStatus.BLACK_WINS -> { scoreO++; SoundPlayer.play("game_end") }
                else                  -> { scoreDraws++; SoundPlayer.play("game_draw") }
            }
            scoreView.update(scoreX, scoreDraws, scoreO)
            boardView.showWinLine(engine.winningLine(gameState))
            updateHud()
            recordResult()
            val ad = interstitialAd; interstitialAd = null
            AdManager.showInterstitial(this, ad)
            AdManager.loadInterstitial(this) { interstitialAd = it }
            scope.launch { delay(1200); showResultDialog() }
            return
        }
        if (moverWasX) SoundPlayer.playMovement("ttt_x") else SoundPlayer.playMovement("ttt_o")
        updateHud()
        if (vsAI && aiControlsCurrentTurn()) {
            boardView.isLocked = true
            triggerAI()
        }
    }

    /** Record stats once per game. */
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
        if (!vsAI || !activityResumed || gameState.status != GameStatus.IN_PROGRESS) return
        boardView.isLocked = true
        hudView.setThinking(true)
        val snapshot = gameState
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    val depth = SettingsManager.tttAiDepth(this@TicTacToeActivity, boardSize)
                    val diff  = SettingsManager.getTttDifficulty(this@TicTacToeActivity)
                    // Easy: 40% chance of a fully random legal move — guarantees beatable play.
                    // Medium: small variety window for natural-feeling but still strong play.
                    // Hard: variety window = 0 → always picks the deterministic best move.
                    //   Without this, a complete-depth search finds all moves tie at score 0
                    //   (forced draw on 3×3), causing the AI to pick randomly — paradoxically
                    //   making Hard feel weaker than Easy which uses heuristics consistently.
                    if (diff == 0 && Math.random() < 0.40) {
                        engine.allLegalMoves(snapshot, snapshot.currentTurn).randomOrNull()
                    } else {
                        val varWindow = when (diff) { 0 -> 80; 2 -> 0; else -> 25 }
                        AIPlayer(engine, maxDepth = depth, timeLimitMs = 2500L, varietyWindowOverride = varWindow).bestMove(snapshot)
                    }
                } catch (e: Throwable) { null }
            }
            if (!isActive || !activityResumed) {
                boardView.isLocked = false
                return@launch
            }
            val safeMove = move ?: runCatching {
                engine.allLegalMoves(snapshot, snapshot.currentTurn).firstOrNull()
            }.getOrNull()
            if (safeMove != null && snapshot == gameState) {
                // On a small board the search can finish in a single frame.
                // Keep the CPU turn and its piece animation perceptible,
                // especially on the classic 3x3 board.
                delay(if (boardSize == 3) 320L else 220L)
                if (!isActive || !activityResumed || snapshot != gameState) {
                    hudView.setThinking(false)
                    boardView.isLocked = false
                    return@launch
                }
                hudView.setThinking(false)
                boardView.isLocked = false
                handleMove(safeMove)
            } else {
                hudView.setThinking(false)
                boardView.isLocked = false
            }
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (!activityResumed ||
            !::boardView.isInitialized ||
            !matchStarted ||
            gameState.status != GameStatus.IN_PROGRESS ||
            !vsAI ||
            !aiControlsCurrentTurn()
        ) return
        triggerAI()
    }

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && (gameState.currentTurn != playerColor ||
            (autoplayAllowed && autoplayEnabled))

    private fun canPauseMatch(): Boolean =
        matchStarted &&
            gameState.status == GameStatus.IN_PROGRESS &&
            !aiControlsCurrentTurn() &&
            !boardView.isLocked

    // ─── HUD / undo / menu ────────────────────────────────────────────────────

    private fun updateHud() {
        val isX = gameState.currentTurn == PieceColor.WHITE
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "CPU Turn"
            else -> "${if (isX) "X" else "O"}'s turn"
        }
        hudView.setInfo(
            value = label,
            undo = moveHistory.isNotEmpty(),
            undoCount = undosRemaining,
            redo = redoGameStates.isNotEmpty(),
            accentColor = if (isX) Color.parseColor("#EF5350") else Color.parseColor("#7FC8F8"),
        )
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
        if (undosRemaining == 0) {
            UndoRewardDialog.show(this, undosRemaining) {
                undosRemaining = SettingsManager.grantUndoCredits(
                    this,
                    "TIC_TAC_TOE",
                    undosRemaining,
                    2,
                    vsAI,
                )
                updateHud()
                undosRemaining
            }
            return
        }
        scope.coroutineContext.cancelChildren()
        hudView.setThinking(false)
        val prevState = gameState
        val removed   = mutableListOf<GameState>()
        if (vsAI && moveHistory.size >= 2) removed.add(moveHistory.removeLast())
        val restored  = moveHistory.removeLastOrNull() ?: return
        removed.add(restored)
        gameState = restored
        redoGameStates.add(prevState)
        redoRemovedMoves.add(removed)
        undosRemaining = SettingsManager.consumeUndoCredit(
            this,
            "TIC_TAC_TOE",
            undosRemaining,
            vsAI,
        )
        boardView.isLocked = false
        boardView.reset(gameState)
        updateHud()
    }

    fun onRedoClicked() {
        if (redoGameStates.isEmpty() || boardView.isLocked) return
        scope.coroutineContext.cancelChildren()
        hudView.setThinking(false)
        val nextState = redoGameStates.removeLast()
        val removed   = redoRemovedMoves.removeLast()
        for (i in removed.indices.reversed()) moveHistory.add(removed[i])
        undosRemaining = SettingsManager.refundUndoCredit(
            this,
            "TIC_TAC_TOE",
            undosRemaining,
            vsAI,
        )
        gameState = nextState
        boardView.isLocked = false
        boardView.reset(gameState)
        updateHud()
    }

    fun onMenuClicked() {
        val matchActive = matchStarted && gameState.status == GameStatus.IN_PROGRESS
        if (matchActive && !canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        // In-app dialogs do not trigger onPause(), so cancel automated play
        // before hiding the board behind the menu.
        stopAutomatedGameplay()
        hideBoardWhileDialogIsOpen()
        val inProgress = matchActive && moveHistory.isNotEmpty()
        if (inProgress) MusicPlayer.enterPausedMatch(this)
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("CPU Difficulty")
        items.add("Main Menu")
        StyledDialogs.showChoices(this, "Menu", "Choose what to do next.",
            items.map { item ->
                when (item) {
                    "New Game" -> StyledDialogs.choice(item, if (inProgress) "Start over and forfeit" else "Begin a fresh match", "↻", "#E3B86A")
                    "How to Play" -> StyledDialogs.choice(item, "Review the essentials", "?", "#A9B6E8")
                    "CPU Difficulty" -> StyledDialogs.choice(item, "Adjust the challenge", "♞", "#8EC7B9")
                    else -> StyledDialogs.choice(item, if (inProgress) "Leave this match" else "Choose another game", "⌂", "#E58A7A")
                }
            }, 520f, "T I C · T A C · T O E", onCancel = { showBoardAfterDialog() }) { which, dialog ->
                dialog.dismiss()
                when (items[which]) {
                    "New Game" -> if (inProgress) {
                        StyledDialogs.showChoices(this, "Forfeit Match?", "Starting a new game counts as a forfeit.",
                            listOf(
                                StyledDialogs.choice("Forfeit & New Game", "Start over now", "↻", "#E58A7A"),
                                StyledDialogs.choice("Cancel", "Keep the current match", "↩", "#A9B6E8"),
                            ), 420f, "T I C · T A C · T O E", onCancel = { showBoardAfterDialog() }) { selected, confirm ->
                                confirm.dismiss()
                                if (selected == 0) {
                                    showModeDialog()
                                } else showBoardAfterDialog()
                            }
                    } else showModeDialog()
                    "How to Play" -> showRules(showModeAfter = false)
                    "CPU Difficulty" -> {
                        val current = SettingsManager.getTttDifficulty(this)
                        StyledDialogs.showChoices(
                            this,
                            "CPU Difficulty",
                            "Win 2 consecutive games at each level to unlock the next.",
                            com.mkdev.mkboardgames.ui.DifficultyChoices.create(
                                this,
                                "ttt",
                                current,
                                listOf("Easy", "Medium", "Hard"),
                            ),
                            520f,
                            "T I C · T A C · T O E",
                            onCancel = { showBoardAfterDialog() },
                        ) { selected, difficulty ->
                            difficulty.dismiss()
                            SettingsManager.setTttDifficulty(this, selected)
                            showBoardAfterDialog()
                            }
                    }
                    "Main Menu" -> if (inProgress) {
                        StyledDialogs.showChoices(this, "Leave Match?", "Pause to resume later, or leave to forfeit.",
                            listOf(
                                StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                                StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
                            ), 520f, "T I C · T A C · T O E", onCancel = { showBoardAfterDialog() }) { selected, leave ->
                                leave.dismiss()
                                when (selected) {
                                    0 -> pauseMatchAndExit()
                                    1 -> { clearPausedMatch(); finish() }
                                    2 -> showBoardAfterDialog()
                                }
                            }
                    } else { clearPausedMatch(); finish() }
                }
            }
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        gameOverView.winnerLabel = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.WHITE) "You" else "X"}"
            GameStatus.BLACK_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.BLACK) "You" else "O"}"
            GameStatus.DRAW -> "Draw"
            else -> return
        }
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun currentResultLabel(): String = when (gameState.status) {
        GameStatus.WHITE_WINS -> "X wins"
        GameStatus.BLACK_WINS -> "O wins"
        GameStatus.DRAW -> "Draw"
        else -> ""
    }

    private fun hideBoardWhileDialogIsOpen() {
        // Keep the game surface mounted under the in-activity overlay.
        gameRoot.visibility = View.VISIBLE
    }

    private fun hideBoardUntilMatchStarts() {
        gameRoot.visibility = View.INVISIBLE
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            MusicPlayer.resumeMatch(this)
        }
        gameRoot.visibility = View.VISIBLE
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun launchReplay(resultLabel: String) {
        showBoardAfterDialog()
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  "TICTACTOE")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
            putExtra(ReplayActivity.EXTRA_BOARD_SIZE, boardSize)
            putExtra(ReplayActivity.EXTRA_BOARD_STYLE_INDEX, 0)
            putExtra(ReplayActivity.EXTRA_LOCK_BOARD_STYLE, true)
        })
    }

    // ─── Board View ───────────────────────────────────────────────────────────

    inner class TicBoardView(ctx: Context) : View(ctx) {

        var isLocked = false
        /** Called when the board is tapped after game over — re-shows result dialog. */
        var onGameOverTapped: (() -> Unit)? = null

        private var bs          = boardSize
        private var state       = engine.initialState()
        private var lastMoveTo: Position? = null
        private var winLine:    List<Int>? = null
        private var winAlpha    = 0f
        private val cellScale   = HashMap<Int, Float>()

        private val dp = resources.displayMetrics.density

        private var boardLeft = 0f
        private var boardTop  = 0f
        private var cellSize  = 0f

        private val bgP   = Paint().apply { color = Color.parseColor("#121212") }
        private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#383838"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val xP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val oP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val hlP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(35, 255, 215, 0) }
        private val winP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }

        fun applyTheme() {
            val t = com.mkdev.mkboardgames.SettingsManager.currentTheme(context)
            lineP.color = t.dark.let { c ->
                android.graphics.Color.argb(200,
                    android.graphics.Color.red(c),
                    android.graphics.Color.green(c),
                    android.graphics.Color.blue(c))
            }
            val ac = t.accent
            hlP.color = android.graphics.Color.argb(40,
                android.graphics.Color.red(ac),
                android.graphics.Color.green(ac),
                android.graphics.Color.blue(ac))
            invalidate()
        }

        fun updateBoardSize(newBs: Int) {
            bs    = newBs
            state = GameState(board = arrayOfNulls(newBs * newBs), boardSize = newBs)
            winLine = null; winAlpha = 0f; cellScale.clear(); lastMoveTo = null
            if (width > 0 && height > 0) recalc(width, height)
            requestLayout(); invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { recalc(w, h) }

        private fun recalc(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val pad  = 24f * dp
            val size = minOf(w - pad * 2, h - pad * 2)
            cellSize  = size / bs.toFloat()
            boardLeft = (w - size) / 2f
            boardTop  = (h - size) / 2f
            lineP.strokeWidth = maxOf(cellSize * 0.022f, 2f)
            xP.strokeWidth    = cellSize * 0.085f
            oP.strokeWidth    = cellSize * 0.085f
            winP.strokeWidth  = cellSize * 0.05f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                // Game is over — fire callback to re-show result dialog
                if (gameState.status != GameStatus.IN_PROGRESS) {
                    onGameOverTapped?.invoke()
                    return true
                }
                if (!isLocked && cellSize > 0f) {
                    val col = ((event.x - boardLeft) / cellSize).toInt()
                    val row = ((event.y - boardTop)  / cellSize).toInt()
                    if (col in 0 until bs && row in 0 until bs && state.get(row, col) == null) {
                        handleMove(Move(TicTacToeRuleEngine.PLACE, Position(row, col)))
                    }
                }
            }
            return true
        }

        fun setGameState(newState: GameState, lastMove: Position? = null) {
            state = newState; lastMoveTo = lastMove
            if (lastMove != null) {
                val idx = lastMove.row * bs + lastMove.col
                cellScale[idx] = 0f
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = if (bs == 3) 430L else 340L
                    interpolator = OvershootInterpolator(1.5f)
                    addUpdateListener { cellScale[idx] = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else invalidate()
        }

        fun showWinLine(line: List<Int>?) {
            if (line == null) { invalidate(); return }
            winLine = line; winAlpha = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500L
                addUpdateListener { winAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        fun reset(newState: GameState) {
            bs = boardSize; state = newState; lastMoveTo = null
            winLine = null; winAlpha = 0f; cellScale.clear(); isLocked = false
            if (width > 0 && height > 0) recalc(width, height)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            drawGrid(canvas); drawHighlight(canvas); drawPieces(canvas); drawWinLine(canvas)
        }

        private fun drawGrid(canvas: Canvas) {
            val right  = boardLeft + bs * cellSize
            val bottom = boardTop  + bs * cellSize
            for (i in 1 until bs) {
                canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, bottom, lineP)
                canvas.drawLine(boardLeft, boardTop + i * cellSize, right, boardTop + i * cellSize, lineP)
            }
        }

        private fun drawHighlight(canvas: Canvas) {
            lastMoveTo?.let { pos ->
                canvas.drawRect(
                    boardLeft + pos.col * cellSize, boardTop + pos.row * cellSize,
                    boardLeft + (pos.col + 1) * cellSize, boardTop + (pos.row + 1) * cellSize, hlP)
            }
        }

        private fun drawPieces(canvas: Canvas) {
            for (row in 0 until bs) for (col in 0 until bs) {
                val piece = state.get(row, col) as? TicTacToePiece ?: continue
                val idx   = row * bs + col
                val scale = cellScale[idx] ?: 1f
                val cx    = boardLeft + col * cellSize + cellSize / 2f
                val cy    = boardTop  + row * cellSize + cellSize / 2f
                val r     = cellSize * 0.29f * scale
                if (piece.color == PieceColor.WHITE) {
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, xP)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, xP)
                } else {
                    canvas.drawCircle(cx, cy, r, oP)
                }
            }
        }

        private fun drawWinLine(canvas: Canvas) {
            val line = winLine ?: return
            if (line.size < 2 || winAlpha <= 0f) return
            val first = line.first(); val last = line.last()
            val r0 = first / bs; val c0 = first % bs
            val r1 = last  / bs; val c1 = last  % bs
            winP.color = Color.argb((winAlpha * 220).toInt(), 255, 193, 7)
            winP.strokeWidth = cellSize * 0.055f
            canvas.drawLine(
                boardLeft + c0 * cellSize + cellSize / 2f, boardTop + r0 * cellSize + cellSize / 2f,
                boardLeft + c1 * cellSize + cellSize / 2f, boardTop + r1 * cellSize + cellSize / 2f, winP)
        }
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    // ─── Score View ───────────────────────────────────────────────────────────

    inner class ScoreView(ctx: Context) : View(ctx) {
        private var xLabel = "X"
        private var oLabel = "O"
        private var x = 0; private var d = 0; private var o = 0

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP  = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val xP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val oP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val dP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val lP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#616161"); textAlign = Paint.Align.CENTER
            textSize = 9f * sp.coerceAtMost(3f)
        }

        fun setLabels(bs: Int) {
            xLabel = if (bs == 3) "X" else "X"; oLabel = if (bs == 3) "O" else "O"; invalidate()
        }
        fun update(xs: Int, ds: Int, os: Int) { x = xs; d = ds; o = os; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, 0f, w, dp, divP)
            val third = w / 3f; val cy = h / 2f
            val numY = cy - lP.textSize; val lblY = cy + xP.textSize * 0.36f + 2f
            canvas.drawText(x.toString(), third*0.5f, lblY, xP)
            canvas.drawText(d.toString(), third*1.5f, lblY, dP)
            canvas.drawText(o.toString(), third*2.5f, lblY, oP)
            canvas.drawText(xLabel,  third*0.5f, numY, lP)
            canvas.drawText("Draw",  third*1.5f, numY, lP)
            canvas.drawText(oLabel,  third*2.5f, numY, lP)
        }
    }
}
