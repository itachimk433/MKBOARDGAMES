package com.mkdev.mkboardgames

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.connectfour.ConnectFourPiece
import com.mkdev.mkboardgames.games.connectfour.ConnectFourRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.BoardSelectionOption
import com.mkdev.mkboardgames.ui.BoardSelectionPreview
import com.mkdev.mkboardgames.ui.BoardSelectionView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.ConnectFourBoardStyle
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class ConnectFourActivity : AppCompatActivity() {
    private val engine = ConnectFourRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private val moveHistory = ArrayDeque<GameState>()
    private var undosRemaining = 3
    private val redoGameStates = ArrayDeque<GameState>()
    private val redoRemovedMoves = ArrayDeque<List<GameState>>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scoreRed = 0
    private var scoreYellow = 0
    private var scoreDraws = 0
    private var resultRecorded = false
    private var interstitialAd: Any? = null
    private var autoplayEnabled = false
    private val autoplayAllowed: Boolean
        get() = SettingsManager.currentMode(this) == GameMode.IRREGULAR
    private var exitPosted = false

    private lateinit var hudView: StandardGameHudView
    private lateinit var boardView: ConnectBoardView
    private lateinit var scoreView: ScoreView
    private lateinit var autoplayButton: AutoplayButtonView
    private lateinit var gameRoot: View
    private lateinit var gameOverView: SnakesLaddersGameOverView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        hudView = StandardGameHudView(this).apply {
            onBack = { this@ConnectFourActivity.onBackPressed() }
            onUndo = { this@ConnectFourActivity.onUndoClicked() }
            onRedo = { this@ConnectFourActivity.onRedoClicked() }
            onMenu = { this@ConnectFourActivity.onMenuClicked() }
        }
        boardView = ConnectBoardView(this)
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

        root.addView(hudView, LinearLayout.LayoutParams(-1, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        root.addView(autoplayButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (76 * dp).toInt(),
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        autoplayButton.visibility = View.GONE
        root.addView(scoreView, LinearLayout.LayoutParams(-1, (48 * dp).toInt()))
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
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.postDelayed({ makeFullscreen() }, 200)
            }
        }
        showModeDialog()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "connect_four")
            boardView.applyTheme()
            boardView.resumeDropAnimation()
        }
        if (!::boardView.isInitialized || !boardView.hasPendingDropAnimation()) {
            resumeComputerTurnIfNeeded()
        }
    }

    override fun onPause() {
        SoundPlayer.stopAll()
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

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
        if (::boardView.isInitialized) {
            boardView.cancelDropAnimation()
            boardView.isLocked = false
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
        }
    }

    private fun pauseAutomatedGameplayForLifecycle() {
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) {
            if (boardView.hasPendingDropAnimation()) boardView.pauseDropAnimation()
            else boardView.isLocked = false
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
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
        stopAutomatedGameplay()
        MusicPlayer.enterPausedMatch(this)
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(this, "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
            ), 520f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { which, dialog ->
                dialog.dismiss()
                when (which) {
                    0 -> pauseMatchAndExit()
                    1 -> {
                        clearPausedMatch()
                        if (vsAI) SettingsManager.recordForfeit(this)
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
            gameType = "CONNECT_FOUR",
            vsAI = vsAI,
            playerColor = playerColor.name,
            moves = gameState.moveHistory,
            boardStyle = boardView.boardStyle.ordinal,
        )
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, "CONNECT_FOUR")

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun showModeDialog() {
        hideBoardWhileDialogIsOpen()
        val paused = PausedMatchStore.has(this, "CONNECT_FOUR")
        val menuView = ChessMenuView(
            this,
            paused,
            gameLabel = "C O N N E C T · F O U R",
        )
        menuView.onVsAi = {
            StyledDialogs.dismiss()
            vsAI = true
            showBoardSelection()
        }
        menuView.onTwoPlayers = {
            StyledDialogs.dismiss()
            vsAI = false
            playerColor = PieceColor.WHITE
            showBoardSelection()
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

    private fun showBoardSelection() {
        hideBoardWhileDialogIsOpen()
        val picker = BoardSelectionView(
            this,
            matchLabel = if (vsAI) "vs CPU" else "2 Players",
            options = listOf(
                BoardSelectionOption(
                    "Canvas board",
                    "Clean and modern",
                    preview = BoardSelectionPreview.CONNECT_FOUR,
                ),
                BoardSelectionOption(
                    "Blue board",
                    "Bright arcade feel",
                    "connect_four_blue.webp",
                    BoardSelectionPreview.CONNECT_FOUR,
                ),
            ),
        )
        picker.onSelectionConfirmed = { styleIndex ->
            boardView.boardStyle = ConnectFourBoardStyle.entries[styleIndex]
            StyledDialogs.dismiss()
            if (vsAI) showColorPickerDialog() else startGame()
        }
        picker.onBackClicked = {
            StyledDialogs.dismiss()
            showModeDialog()
        }
        StyledDialogs.showFullScreenView(this, picker) {
            showModeDialog()
        }
    }

    private fun showColorPickerDialog() {
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(this, "Play As", "Choose your side before the first move.",
            listOf(
                StyledDialogs.choice("Red", "Moves first", "", "#E3B86A"),
                StyledDialogs.choice("Yellow", "Moves second", "", "#A9B6E8"),
            ), 420f, "C O N N E C T · F O U R", onCancel = { showModeDialog() }) { which, dialog ->
                dialog.dismiss()
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
    }

    private fun showRules(showModeAfter: Boolean) {
        hideBoardWhileDialogIsOpen()
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            val dp = resources.displayMetrics.density
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
CONNECT FOUR — Rules

Overview
Players take turns dropping coloured discs into one of seven columns. Red moves first.

Dropping a Disc
Tap any column with an empty space. The disc falls to the lowest available row.

Winning
Be the first player to connect four discs horizontally, vertically, or diagonally.

Draw
The game is a draw when the board is full and neither player has connected four.

Strategy
Control the centre columns, build threats in more than one direction, and block your opponent's winning move.
            """.trimIndent()
        }
        StyledDialogs.showRules(
            this,
            "Connect Four",
            tv.text.toString(),
            "C O N N E C T · F O U R",
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() },
        )
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        showBoardAfterDialog(resumeAi = false)
        matchStarted = true
        MusicPlayer.enterMatch(this)
        resultRecorded = false
        interstitialAd = null
        redoGameStates.clear()
        redoRemovedMoves.clear()
        undosRemaining = SettingsManager.undoCredits(this, "CONNECT_FOUR")
        AdManager.loadInterstitial(this) { interstitialAd = it }
        AdManager.loadRewarded(this)
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, "connect_four")
        if (vsAI) SettingsManager.setActiveGame(this, "connect_four")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        restoring?.boardStyle?.let { index ->
            boardView.boardStyle = ConnectFourBoardStyle.entries
                .getOrElse(index) { ConnectFourBoardStyle.CANVAS }
        }
        moveHistory.clear()
        boardView.reset(gameState)
        autoplayEnabled = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        autoplayButton.visibility = if (autoplayAllowed && vsAI) View.VISIBLE else View.GONE
        boardView.onGameOverTapped = { showResultDialog() }
        scoreView.update(scoreRed, scoreDraws, scoreYellow)
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
        val paused = PausedMatchStore.load(this, "CONNECT_FOUR") ?: run {
            showModeDialog()
            return
        }
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }
            .getOrDefault(PieceColor.WHITE)
        startGame(paused)
    }

    private fun restoreMoves(moves: List<Move>) {
        for (move in moves) {
            moveHistory.add(gameState)
            gameState = engine.applyMove(gameState, move)
        }
        boardView.reset(gameState)
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        scoreView.update(scoreRed, scoreDraws, scoreYellow)
        updateHud()
    }

    private fun handleMove(move: Move, fromAI: Boolean = false) {
        if (!activityResumed) return
        if (boardView.isLocked && !fromAI) return
        boardView.isLocked = true
        redoGameStates.clear()
        redoRemovedMoves.clear()
        moveHistory.add(gameState)
        gameState = engine.applyMove(gameState, move)
        val appliedMove = gameState.lastMove
        val onDropAnimationFinished: (() -> Unit)? =
            if (gameState.status == GameStatus.IN_PROGRESS) {
                {
                    if (vsAI && aiControlsCurrentTurn()) triggerAI()
                    else boardView.isLocked = false
                }
            } else {
                null
            }
        boardView.setGameState(
            gameState,
            appliedMove?.to,
            onDropAnimationFinished = onDropAnimationFinished
        )
        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
            autoplayButton.setAutoplayEnabled(false, animate = false)
            boardView.isLocked = true
            when (gameState.status) {
                GameStatus.WHITE_WINS -> { scoreRed++; SoundPlayer.play("game_end") }
                GameStatus.BLACK_WINS -> { scoreYellow++; SoundPlayer.play("game_end") }
                GameStatus.DRAW -> { scoreDraws++; SoundPlayer.play("game_draw") }
                else -> {}
            }
            scoreView.update(scoreRed, scoreDraws, scoreYellow)
            boardView.showWinLine(engine.winningLine(gameState))
            updateHud()
            recordResult()
            val ad = interstitialAd
            interstitialAd = null
            AdManager.showInterstitial(this, ad)
            AdManager.loadInterstitial(this) { interstitialAd = it }
            scope.launch { delay(1200); showResultDialog() }
            return
        }
        SoundPlayer.playMovement(if (gameState.currentTurn == PieceColor.WHITE) "ttt_o" else "ttt_x")
        updateHud()
    }

    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW -> SettingsManager.recordDraw(this)
            else -> {}
        }
    }

    private fun triggerAI() {
        if (!vsAI || !activityResumed || gameState.status != GameStatus.IN_PROGRESS) return
        boardView.isLocked = true
        hudView.setThinking(true)
        val snapshot = gameState
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    val difficulty = SettingsManager.getConnectFourDifficulty(this@ConnectFourActivity)
                    val depth = SettingsManager.connectFourAiDepth(this@ConnectFourActivity)
                    val window = when (difficulty) { 0 -> 80; 2 -> 0; else -> 25 }
                    if (difficulty == 0 && Math.random() < 0.25) {
                        engine.allLegalMoves(snapshot, snapshot.currentTurn).randomOrNull()
                    } else {
                        AIPlayer(
                            engine,
                            maxDepth = depth,
                            timeLimitMs = SettingsManager.connectFourAiTimeLimitMs(this@ConnectFourActivity),
                            varietyWindowOverride = window
                        ).bestMove(snapshot)
                    }
                } catch (_: Throwable) { null }
            }
            if (!isActive || !activityResumed) {
                boardView.isLocked = false
                return@launch
            }
            hudView.setThinking(false)
            val safeMove = move ?: runCatching {
                engine.allLegalMoves(snapshot, snapshot.currentTurn).firstOrNull()
            }.getOrNull()
            if (safeMove != null && snapshot == gameState) handleMove(safeMove, fromAI = true)
            else boardView.isLocked = false
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

    private fun updateHud() {
        val redTurn = gameState.currentTurn == PieceColor.WHITE
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "CPU Turn"
            else -> "${if (redTurn) "Red" else "Yellow"}'s turn"
        }
        hudView.setInfo(
            value = label,
            undo = moveHistory.isNotEmpty(),
            undoCount = undosRemaining,
            redo = redoGameStates.isNotEmpty(),
            accentColor = if (redTurn) Color.parseColor("#EF5350") else Color.parseColor("#FFD54F"),
        )
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
        if (undosRemaining == 0) {
            UndoRewardDialog.show(this) {
                undosRemaining += 2
                SettingsManager.setUndoCredits(this, "CONNECT_FOUR", undosRemaining)
                updateHud()
            }
            return
        }
        scope.coroutineContext.cancelChildren()
        hudView.setThinking(false)
        val previous = gameState
        val removed = mutableListOf<GameState>()
        if (vsAI && moveHistory.size >= 2) removed.add(moveHistory.removeLast())
        val restored = moveHistory.removeLastOrNull() ?: return
        removed.add(restored)
        gameState = restored
        redoGameStates.add(previous)
        redoRemovedMoves.add(removed)
        undosRemaining--
        SettingsManager.setUndoCredits(this, "CONNECT_FOUR", undosRemaining)
        boardView.reset(gameState)
        updateHud()
    }

    fun onRedoClicked() {
        if (redoGameStates.isEmpty() || boardView.isLocked) return
        scope.coroutineContext.cancelChildren()
        hudView.setThinking(false)
        val next = redoGameStates.removeLast()
        val removed = redoRemovedMoves.removeLast()
        for (i in removed.indices.reversed()) moveHistory.add(removed[i])
        gameState = next
        boardView.reset(gameState)
        updateHud()
    }

    fun onMenuClicked() {
        // In-app dialogs do not trigger onPause(), so cancel automated play
        // before hiding the board behind the menu.
        stopAutomatedGameplay()
        hideBoardWhileDialogIsOpen()
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        if (inProgress) MusicPlayer.enterPausedMatch(this)
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("CPU Difficulty")
        items.add("Main Menu")
        StyledDialogs.showChoices(this, "Menu", "Choose what to do next.",
            items.map { item ->
                when (item) {
                    "New Game" -> StyledDialogs.choice(item, if (inProgress) "Start over and forfeit" else "Begin a fresh match", "↻", "#E3B86A")
                    "How to Play" -> StyledDialogs.choice(item, "Review the essentials", "?", "#A9B6E8")
                    "CPU Difficulty" -> StyledDialogs.choice(item, "Adjust the challenge", "●", "#8EC7B9")
                    else -> StyledDialogs.choice(item, if (inProgress) "Leave this match" else "Choose another game", "⌂", "#E58A7A")
                }
            }, 520f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { which, menu ->
            menu.dismiss()
            when (items[which]) {
                "New Game" -> if (inProgress) {
                    StyledDialogs.showChoices(this, "Forfeit Match?", "Starting a new game counts as a forfeit.",
                        listOf(
                            StyledDialogs.choice("Forfeit & New Game", "Start over now", "↻", "#E58A7A"),
                            StyledDialogs.choice("Cancel", "Keep the current match", "↩", "#A9B6E8"),
                        ), 420f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { selected, confirm ->
                            confirm.dismiss()
                            if (selected == 0) {
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            } else showBoardAfterDialog()
                        }
                } else showModeDialog()
                "How to Play" -> showRules(false)
                "CPU Difficulty" -> showDifficultyDialog()
                "Main Menu" -> if (inProgress) {
                    StyledDialogs.showChoices(this, "Leave Match?", "Pause to resume later, or leave to forfeit.",
                        listOf(
                            StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                            StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                            StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
                        ), 520f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { selected, leave ->
                            leave.dismiss()
                            when (selected) {
                                0 -> pauseMatchAndExit()
                                1 -> { clearPausedMatch(); if (vsAI) SettingsManager.recordForfeit(this); finish() }
                                2 -> showBoardAfterDialog()
                            }
                        }
                } else {
                    clearPausedMatch()
                    finish()
                }
            }
        }
    }

    private fun showDifficultyDialog() {
        hideBoardWhileDialogIsOpen()
        val labels = arrayOf("Easy", "Medium", "Hard")
        val current = SettingsManager.getConnectFourDifficulty(this)
        StyledDialogs.showChoices(this, "CPU Difficulty", "Adjust the challenge.",
            labels.mapIndexed { index, label ->
                StyledDialogs.choice(
                    "$label${if (index == current) "  ✓" else ""}",
                    if (index == current) "Current setting" else "Computer strength",
                    listOf("I", "II", "III")[index],
                    listOf("#8EC7B9", "#E3B86A", "#E58A7A")[index],
                )
            }, 520f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { which, dialog ->
                dialog.dismiss()
                val changed = which != current
                SettingsManager.setConnectFourDifficulty(this, which)
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    StyledDialogs.showChoices(this, "Restart Match?", "Difficulty changed. Restart now.",
                        listOf(
                            StyledDialogs.choice("Restart", "Start with the new difficulty", "↻", "#E3B86A"),
                            StyledDialogs.choice("Keep Playing", "Leave the current match unchanged", "↩", "#A9B6E8"),
                        ), 420f, "C O N N E C T · F O U R", onCancel = { showBoardAfterDialog() }) { restart, restartDialog ->
                            restartDialog.dismiss()
                            if (restart == 0) startGame() else showBoardAfterDialog()
                        }
                } else showBoardAfterDialog()
            }
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        gameOverView.winnerLabel = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.WHITE) "You" else "Red"}"
            GameStatus.BLACK_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.BLACK) "You" else "Yellow"}"
            GameStatus.DRAW -> "Draw"
            else -> return
        }
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun currentResultLabel(): String = when (gameState.status) {
        GameStatus.WHITE_WINS -> "Red wins"
        GameStatus.BLACK_WINS -> "Yellow wins"
        GameStatus.DRAW -> "Draw"
        else -> ""
    }

    private fun launchReplay(resultLabel: String) {
        showBoardAfterDialog()
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE, "CONNECTFOUR")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(gameState.moveHistory))
            putExtra(ReplayActivity.EXTRA_RESULT, resultLabel)
            putExtra(ReplayActivity.EXTRA_BOARD_STYLE_INDEX, boardView.boardStyle.ordinal)
            putExtra(ReplayActivity.EXTRA_LOCK_BOARD_STYLE, true)
        })
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

    inner class ConnectBoardView(ctx: Context) : View(ctx) {
        var isLocked = false
        var onGameOverTapped: (() -> Unit)? = null
        private var state = engine.initialState()
        private var lastMove: Position? = null
        private var winLine: List<Int>? = null
        private var fallingAnimator: ValueAnimator? = null
        private var fallingIndex: Int? = null
        private var fallingColor: PieceColor? = null
        private var fallingProgress = 0f
        private var dropAnimationCompletion: (() -> Unit)? = null
        private val dp = resources.displayMetrics.density
        private var boardLeft = 0f; private var boardTop = 0f; private var cellSize = 0f
        private val boardImageRect = RectF()
        private var imageCellWidth = 0f
        private var imageCellHeight = 0f
        var boardStyle: ConnectFourBoardStyle = ConnectFourBoardStyle.CANVAS
            set(value) {
                if (field == value) return
                field = value
                updateBoardGeometry()
                winP.strokeWidth = cellSize * 0.065f
                invalidate()
            }
        private val blueBoardBitmap: Bitmap? = try {
            context.assets.open("connect_four_blue.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val redPieceBitmap: Bitmap? = try {
            context.assets.open("connect_four_red_piece.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val yellowPieceBitmap: Bitmap? = try {
            context.assets.open("connect_four_yellow_piece.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        // The transparent boards are trimmed from the same 1536×1024
        // composition. These measured hole centres keep pieces, highlights,
        // animations, and touch columns aligned with the seven-column artwork.
        private val imageGridX = floatArrayOf(
            207f / 1261f, 349f / 1261f, 490.5f / 1261f,
            633f / 1261f, 774f / 1261f, 915f / 1261f,
            1057f / 1261f,
        )
        private val imageGridY = floatArrayOf(
            128.5f / 1002f, 263.5f / 1002f, 399f / 1002f,
            535.5f / 1002f, 671f / 1002f, 807f / 1002f,
        )
        private var boardColor = Color.parseColor("#24527A")
        private var accent = Color.parseColor("#7FC8F8")
        private val bgP = Paint().apply { color = Color.parseColor("#121212") }
        private val boardP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val holeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#101820") }
        private val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        private val yellowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
        private val pieceBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val highlightP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val winP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

        fun applyTheme() {
            val theme = SettingsManager.currentTheme(context)
            accent = theme.accent
            boardColor = Color.rgb(
                (Color.red(theme.dark) * 0.75f + Color.blue(theme.accent) * 0.25f).toInt(),
                (Color.green(theme.dark) * 0.75f + Color.green(theme.accent) * 0.25f).toInt(),
                (Color.blue(theme.dark) * 0.75f + Color.red(theme.accent) * 0.25f).toInt()
            )
            highlightP.color = Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent))
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            updateBoardGeometry()
            winP.strokeWidth = cellSize * 0.065f
        }

        private fun updateBoardGeometry() {
            if (width <= 0 || height <= 0) return
            val bitmap = boardBitmap()
            if (bitmap != null) {
                val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                boardImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                imageCellWidth = (imageGridX.last() - imageGridX.first()) *
                    boardImageRect.width() / (ConnectFourRuleEngine.COLUMNS - 1)
                imageCellHeight = (imageGridY.last() - imageGridY.first()) *
                    boardImageRect.height() / (ConnectFourRuleEngine.ROWS - 1)
                cellSize = minOf(imageCellWidth, imageCellHeight)
                return
            }

            val pad = 20f * dp
            cellSize = minOf(
                (width - pad * 2) / ConnectFourRuleEngine.COLUMNS,
                (height - pad * 2) / ConnectFourRuleEngine.ROWS,
            )
            boardLeft = (width - cellSize * ConnectFourRuleEngine.COLUMNS) / 2f
            boardTop = (height - cellSize * ConnectFourRuleEngine.ROWS) / 2f
        }

        private fun boardBitmap(): Bitmap? = when (boardStyle) {
            ConnectFourBoardStyle.CANVAS -> null
            ConnectFourBoardStyle.BLUE -> blueBoardBitmap
        }

        private fun isImageBoard() = boardBitmap() != null

        private fun imageColumnCenter(column: Int): Float =
            boardImageRect.left + boardImageRect.width() * imageGridX[column]

        private fun imageRowCenter(row: Int): Float =
            boardImageRect.top + boardImageRect.height() * imageGridY[row]

        private fun imageColumnForX(x: Float): Int? {
            val boundaries = FloatArray(ConnectFourRuleEngine.COLUMNS + 1) { index ->
                when (index) {
                    0 -> boardImageRect.left
                    ConnectFourRuleEngine.COLUMNS -> boardImageRect.right
                    else -> (imageColumnCenter(index - 1) + imageColumnCenter(index)) / 2f
                }
            }
            return (0 until ConnectFourRuleEngine.COLUMNS).firstOrNull {
                x >= boundaries[it] && x < boundaries[it + 1]
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                if (state.status != GameStatus.IN_PROGRESS) {
                    onGameOverTapped?.invoke()
                    return true
                }
                if (!isLocked && cellSize > 0f) {
                    val col = if (isImageBoard()) {
                        imageColumnForX(event.x)
                    } else {
                        ((event.x - boardLeft) / cellSize).toInt()
                            .takeIf { it in 0 until ConnectFourRuleEngine.COLUMNS }
                    }
                    if (col != null && engine.landingRow(state, col) != null) {
                        handleMove(Move(ConnectFourRuleEngine.DROP, Position(0, col)))
                    }
                }
            }
            return true
        }

        fun setGameState(
            newState: GameState,
            last: Position? = null,
            onDropAnimationFinished: (() -> Unit)? = null
        ) {
            state = newState
            lastMove = last
            dropAnimationCompletion = null
            fallingAnimator?.cancel()
            fallingAnimator = null
            fallingIndex = null
            fallingColor = null
            fallingProgress = 0f
            dropAnimationCompletion = onDropAnimationFinished
            last?.let {
                val idx = it.row * ConnectFourRuleEngine.COLUMNS + it.col
                val piece = newState.get(it) as? ConnectFourPiece ?: return@let
                fallingIndex = idx
                fallingColor = piece.color
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    // A lower slot means a longer, more physical-looking drop.
                    duration = 250L + (it.row + 1) * 55L
                    interpolator = AccelerateInterpolator(1.25f)
                    addUpdateListener { animation ->
                        fallingProgress = animation.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (fallingIndex == idx) {
                                fallingAnimator = null
                                fallingIndex = null
                                fallingColor = null
                                val completion = dropAnimationCompletion
                                dropAnimationCompletion = null
                                invalidate()
                                completion?.invoke()
                            }
                        }
                    })
                }
                fallingAnimator = animator
                animator.start()
            }
            if (last == null) {
                val completion = dropAnimationCompletion
                dropAnimationCompletion = null
                completion?.invoke()
            }
            invalidate()
        }

        fun showWinLine(line: List<Int>?) {
            winLine = line
            invalidate()
        }

        fun reset(newState: GameState) {
            state = newState
            lastMove = null
            winLine = null
            dropAnimationCompletion = null
            fallingAnimator?.cancel()
            fallingAnimator = null
            fallingIndex = null
            fallingColor = null
            fallingProgress = 0f
            isLocked = false
            invalidate()
        }

        fun cancelDropAnimation() {
            // Clear the completion before canceling: ValueAnimator.cancel()
            // still dispatches onAnimationEnd to its listeners.
            dropAnimationCompletion = null
            fallingAnimator?.cancel()
            fallingAnimator = null
            fallingIndex = null
            fallingColor = null
            fallingProgress = 0f
            invalidate()
        }

        fun pauseDropAnimation() {
            fallingAnimator?.pause()
        }

        fun resumeDropAnimation() {
            fallingAnimator?.resume()
        }

        fun hasPendingDropAnimation(): Boolean = fallingAnimator != null || dropAnimationCompletion != null

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            val right = boardLeft + ConnectFourRuleEngine.COLUMNS * cellSize
            val bottom = boardTop + ConnectFourRuleEngine.ROWS * cellSize
            val fallingRow = fallingIndex?.div(ConnectFourRuleEngine.COLUMNS)
            val fallingCol = fallingIndex?.rem(ConnectFourRuleEngine.COLUMNS)
            val fallingX = fallingCol?.let {
                if (isImageBoard()) imageColumnCenter(it)
                else boardLeft + it * cellSize + cellSize / 2f
            }
            val fallingTargetY = fallingRow?.let {
                if (isImageBoard()) imageRowCenter(it)
                else boardTop + it * cellSize + cellSize / 2f
            }
            val fallingStartY = if (isImageBoard()) {
                boardImageRect.top - cellSize * 0.85f
            } else {
                boardTop - cellSize * 0.85f
            }
            val boardStartY = if (isImageBoard()) boardImageRect.top else boardTop
            val fallingY = fallingTargetY?.let {
                fallingStartY + (it - fallingStartY) * fallingProgress
            }
            val fallingRadius = cellSize * 0.37f

            // Draw the part above the board first. Once it reaches the board,
            // the board face is drawn over it and only the circular openings
            // reveal the disc below, so it can never paint over the frame.
            if (fallingX != null && fallingY != null && fallingColor != null && fallingY < boardStartY) {
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
            }

            if (isImageBoard()) {
                boardBitmap()?.let {
                    canvas.drawBitmap(
                        it,
                        null,
                        boardImageRect,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                }
            } else {
                boardP.color = boardColor
                canvas.drawRoundRect(boardLeft, boardTop, right, bottom, cellSize * 0.14f, cellSize * 0.14f, boardP)
            }
            val holePath = Path()
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val cx = if (isImageBoard()) imageColumnCenter(col)
                else boardLeft + col * cellSize + cellSize / 2f
                val cy = if (isImageBoard()) imageRowCenter(row)
                else boardTop + row * cellSize + cellSize / 2f
                val holeRadius = if (isImageBoard()) cellSize * 0.37f else cellSize * 0.36f
                if (!isImageBoard()) canvas.drawCircle(cx, cy, holeRadius, holeP)
                holePath.addCircle(cx, cy, holeRadius, Path.Direction.CW)
            }

            if (fallingX != null && fallingY != null && fallingColor != null && fallingY >= boardStartY) {
                canvas.save()
                canvas.clipPath(holePath)
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
                canvas.restore()
            }
            lastMove?.let {
                val cx = if (isImageBoard()) imageColumnCenter(it.col)
                else boardLeft + it.col * cellSize + cellSize / 2f
                val cy = if (isImageBoard()) imageRowCenter(it.row)
                else boardTop + it.row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.43f, highlightP)
            }
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val piece = state.get(row, col) as? ConnectFourPiece ?: continue
                val idx = row * ConnectFourRuleEngine.COLUMNS + col
                if (idx == fallingIndex) continue
                val cx = if (isImageBoard()) imageColumnCenter(col)
                else boardLeft + col * cellSize + cellSize / 2f
                val cy = if (isImageBoard()) imageRowCenter(row)
                else boardTop + row * cellSize + cellSize / 2f
                drawDisc(canvas, cx, cy, cellSize * 0.37f, piece.color)
            }
            winLine?.takeIf { it.size >= 2 }?.let {
                winP.color = Color.argb(230, 255, 255, 255)
                val first = it.first(); val last = it.last()
                val firstX = if (isImageBoard()) imageColumnCenter(first % ConnectFourRuleEngine.COLUMNS)
                else boardLeft + (first % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f
                val firstY = if (isImageBoard()) imageRowCenter(first / ConnectFourRuleEngine.COLUMNS)
                else boardTop + (first / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f
                val lastX = if (isImageBoard()) imageColumnCenter(last % ConnectFourRuleEngine.COLUMNS)
                else boardLeft + (last % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f
                val lastY = if (isImageBoard()) imageRowCenter(last / ConnectFourRuleEngine.COLUMNS)
                else boardTop + (last / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f
                canvas.drawLine(
                    firstX,
                    firstY,
                    lastX,
                    lastY,
                    winP
                )
            }
        }

        private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: PieceColor) {
            val bitmap = if (color == PieceColor.WHITE) yellowPieceBitmap else redPieceBitmap
            if (bitmap != null) {
                // The source images are already trimmed to their visible
                // artwork. Drawing into the measured hole bounds lets the
                // transparent edge meet the slot edge without changing the
                // board's perspective or the touch geometry.
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    pieceBitmapPaint,
                )
            } else {
                canvas.drawCircle(cx, cy, radius, if (color == PieceColor.WHITE) redP else yellowP)
            }
        }
    }

    inner class ScoreView(ctx: Context) : View(ctx) {
        private var red = 0; private var draw = 0; private var yellow = 0
        private val dp = resources.displayMetrics.density; private val sp = resources.displayMetrics.scaledDensity
        private val bgP = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350"); textAlign = Paint.Align.CENTER; textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true }
        private val yellowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F"); textAlign = Paint.Align.CENTER; textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true }
        private val drawP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER; textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true }
        private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#616161"); textAlign = Paint.Align.CENTER; textSize = 9f * sp.coerceAtMost(3f) }
        fun update(r: Int, d: Int, y: Int) { red = r; draw = d; yellow = y; invalidate() }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            canvas.drawRect(0f, 0f, width.toFloat(), dp, divP)
            val third = width / 3f; val cy = height / 2f
            canvas.drawText(red.toString(), third * .5f, cy + redP.textSize * .36f + 2f, redP)
            canvas.drawText(draw.toString(), third * 1.5f, cy + drawP.textSize * .36f + 2f, drawP)
            canvas.drawText(yellow.toString(), third * 2.5f, cy + yellowP.textSize * .36f + 2f, yellowP)
            canvas.drawText("Red", third * .5f, cy - labelP.textSize, labelP)
            canvas.drawText("Draw", third * 1.5f, cy - labelP.textSize, labelP)
            canvas.drawText("Yellow", third * 2.5f, cy - labelP.textSize, labelP)
        }
    }
}