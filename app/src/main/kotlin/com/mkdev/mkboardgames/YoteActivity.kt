package com.mkdev.mkboardgames

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.yote.YoteAutoplayLoopDetector
import com.mkdev.mkboardgames.games.yote.YoteAIPlayer
import com.mkdev.mkboardgames.games.yote.YoteRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.StyledDialogs
import com.mkdev.mkboardgames.ui.YoteBoardView
import com.mkdev.mkboardgames.ui.YotePieceStripView
import com.mkdev.mkboardgames.ui.YoteRulesView
import kotlinx.coroutines.*

class YoteActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_GAME = "YOTE"
    }

    private val engine = YoteRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var resultRecorded = false
    private var autoplayEnabled = false
    private val autoplayAllowed: Boolean
        get() = SettingsManager.currentMode(this) == GameMode.IRREGULAR
    private var autoplayMoveInProgress = false
    private var aiRequestToken = 0
    private var aiJob: Job? = null
    private val autoplayLoopDetector = YoteAutoplayLoopDetector()
    private val previousStates = ArrayDeque<com.mkdev.mkboardgames.engine.GameState>()
    private var undosRemaining = 3
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var gameRoot: View
    private lateinit var screenRoot: FrameLayout
    private lateinit var hudView: StandardGameHudView
    private lateinit var boardView: YoteBoardView
    private lateinit var topInfoView: YotePieceStripView
    private lateinit var bottomInfoView: YotePieceStripView
    private lateinit var autoplayButton: AutoplayButtonView
    private lateinit var gameOverView: SnakesLaddersGameOverView
    private var activeOverlay: View? = null
    private val capturedByWhite = mutableListOf<Piece>()
    private val capturedByBlack = mutableListOf<Piece>()
    private var pendingBonusCaptureMove: Move? = null
    private var pendingBonusCapturePositions: List<Position> = emptyList()
    private var pendingBonusCaptureFromComputer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()
        val density = resources.displayMetrics.density

        val gameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#071522"))
        }
        hudView = StandardGameHudView(this, labelTextSizeSp = 12f).apply {
            onBack = { onBackPressed() }
            onUndo = { undoMove() }
            onMenu = { if (!boardView.isLocked) showMenu() }
        }
        topInfoView = YotePieceStripView(this, PieceColor.BLACK)
        bottomInfoView = YotePieceStripView(this, PieceColor.WHITE)

        boardView = YoteBoardView(this).apply {
            onMoveMade = { move, fromComputer -> handleBoardMove(move, fromComputer) }
            onGameOverTapped = { if (activeOverlay == null) showResultDialog() }
            onBoardTapped = {
                val pendingMove = pendingBonusCaptureMove
                if (pendingMove != null && activeOverlay == null) {
                    showBonusCapturePicker(
                        pendingMove,
                        pendingBonusCapturePositions,
                        pendingBonusCaptureFromComputer,
                    )
                    true
                } else {
                    false
                }
            }
        }

        autoplayButton = AutoplayButtonView(this).apply {
            onAutoplayChanged = { enabled ->
                if (autoplayAllowed && vsAI) {
                    autoplayEnabled = enabled
                    if (!enabled) autoplayLoopDetector.reset()
                    if (enabled && matchStarted && gameState.status == GameStatus.IN_PROGRESS &&
                        !boardView.isLocked && aiControlsCurrentTurn()
                    ) {
                        triggerAI()
                    }
                }
            }
        }

        gameLayout.addView(hudView, LinearLayout.LayoutParams(-1, (56 * density).toInt()))
        gameLayout.addView(topInfoView, LinearLayout.LayoutParams(-1, (34 * density).toInt()))
        gameLayout.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        gameLayout.addView(bottomInfoView, LinearLayout.LayoutParams(-1, (34 * density).toInt()))
        gameLayout.addView(
            autoplayButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (76 * density).toInt(),
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            },
        )
        AdManager.attachBanner(gameLayout)
        gameRoot = gameLayout

        screenRoot = FrameLayout(this)
        gameLayout.visibility = View.GONE
        autoplayButton.visibility = View.GONE
        screenRoot.addView(gameLayout, FrameLayout.LayoutParams(-1, -1))
        gameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            bottomCaptureTopPxProvider = { bottomInfoView.top.toFloat() }
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
                showHome()
            }
        }
        screenRoot.addView(gameOverView, FrameLayout.LayoutParams(-1, -1))
        setContentView(screenRoot)
        SoundPlayer.init(this)
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        showModeDialog()

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.postDelayed({ makeFullscreen() }, 200)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        makeFullscreen()
        if (::boardView.isInitialized) {
            updateHud()
            boardView.resumeMoveAnimation()
            if (!boardView.hasPendingMoveAnimation()) resumeComputerTurnIfNeeded()
        }
    }

    private fun pauseAutomatedGameplayForLifecycle() {
        aiRequestToken++
        aiJob?.cancel()
        aiJob = null
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) {
            if (boardView.hasPendingMoveAnimation()) boardView.pauseMoveAnimation()
            else boardView.isLocked = false
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
    }

    override fun onPause() {
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

    override fun onDestroy() {
        stopAutomatedGameplay()
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (activeOverlay != null) {
            cancelOverlay()
        } else if (boardView.isLocked) {
            return
        } else if (!matchStarted) {
            @Suppress("DEPRECATION") super.onBackPressed()
        } else if (gameState.status != GameStatus.IN_PROGRESS) {
            showResultDialog()
        } else if (!canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
        } else {
            showLeaveMatchDialog()
        }
    }

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
        gameRoot.visibility = View.GONE
        val menu = ChessMenuView(
            this,
            PausedMatchStore.has(this, "YOTE"),
            gameLabel = "Y O T É",
        )
        menu.onVsAi = {
            dismissOverlay()
            vsAI = true
            showColorPicker()
        }
        menu.onTwoPlayers = {
            dismissOverlay()
            vsAI = false
            playerColor = PieceColor.WHITE
            startGame()
        }
        menu.onHowToPlay = {
            dismissOverlay()
            showRules(showModeAfter = !matchStarted)
        }
        menu.onResumeMatch = {
            dismissOverlay()
            resumePausedMatch()
        }
        showOverlay(menu) {
            if (matchStarted) showBoardAfterDialog() else finish()
        }
    }

    private fun showColorPicker() {
        showChoiceOverlay(
            title = "Choose your side",
            subtitle = "White moves first. Pick the side you control.",
            choices = listOf(
                ChessChoiceView.Choice("White", "Moves first", "", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Black", "Moves second", "", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showModeDialog() },
        ) {
            playerColor = if (it == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun showChoiceOverlay(
        title: String,
        subtitle: String,
        choices: List<ChessChoiceView.Choice>,
        gridChoices: Boolean = false,
        onDismiss: (() -> Unit)? = null,
        onCancel: () -> Unit,
        onChoice: (Int) -> Unit,
    ) {
        val overlay = ChessChoiceView(
            this,
            title,
            subtitle,
            choices,
            gameLabel = "Y O T É",
            headerSymbol = "●",
            fullScreenOverride = !gridChoices,
            gridChoices = gridChoices,
            compactGrid = gridChoices,
        )
        overlay.onChoiceSelected = {
            dismissOverlay()
            onChoice(it)
        }
        overlay.onDismissRequested = {
            dismissOverlay()
            onDismiss?.invoke()
        }
        overlay.onBackRequested = {
            cancelOverlay()
        }
        showOverlay(overlay, bottomAligned = gridChoices, onCancel = onCancel)
    }

    private fun showOverlay(
        view: View,
        bottomAligned: Boolean = false,
        onCancel: () -> Unit = { showBoardAfterDialog() },
    ) {
        dismissOverlay()
        activeOverlay = view
        view.tag = onCancel
        screenRoot.addView(
            view,
            FrameLayout.LayoutParams(
                -1,
                if (bottomAligned) FrameLayout.LayoutParams.WRAP_CONTENT else -1,
            ).apply {
                if (bottomAligned) gravity = Gravity.BOTTOM
            },
        )
        view.requestFocus()
    }

    private fun dismissOverlay() {
        activeOverlay?.let { screenRoot.removeView(it) }
        activeOverlay = null
    }

    private fun cancelOverlay() {
        val callback = activeOverlay?.tag as? (() -> Unit)
        dismissOverlay()
        callback?.invoke()
    }

    private fun showLeaveMatchDialog() {
        if (!canPauseMatch()) return
        MusicPlayer.enterPausedMatch(this)
        stopAutomatedGameplay()
        boardView.isLocked = true
        showChoiceOverlay(
            "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                ChessChoiceView.Choice("Pause & Exit", "Save and resume later", "Ⅱ", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Leave Match", "Forfeit this game", "⚑", Color.parseColor("#E58A7A")),
                ChessChoiceView.Choice("Keep Playing", "Return to the board", "↩", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showBoardAfterDialog() },
        ) {
            when (it) {
                0 -> {
                    savePausedMatch()
                    finish()
                }
                1 -> {
                    PausedMatchStore.clear(this, "YOTE")
                    if (vsAI) SettingsManager.recordForfeit(this)
                    finish()
                }
                else -> showBoardAfterDialog()
            }
        }
    }

    private fun showMenu() {
        if (!canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        MusicPlayer.enterPausedMatch(this)
        boardView.isLocked = true
        autoplayEnabled = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        showChoiceOverlay(
            "Game Menu",
            "What would you like to do?",
            listOf(
                ChessChoiceView.Choice("New Game / Restart", "Start a fresh Yoté match", "↻", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("How To Play", "Review the rules", "?", Color.parseColor("#8EC7B9")),
                ChessChoiceView.Choice("CPU Difficulty", "Choose the challenge", "◆", Color.parseColor("#A9B6E8")),
                ChessChoiceView.Choice("Home", "Return to the catalogue", "⌂", Color.parseColor("#E58A7A")),
            ),
            onCancel = { showBoardAfterDialog() },
        ) {
            when (it) {
                0 -> showRestartConfirmation()
                1 -> showRules(false)
                2 -> showDifficultyMenu()
                else -> showHome()
            }
        }
    }

    private fun showRestartConfirmation() {
        showChoiceOverlay(
            "Restart this game?",
            "Your current progress will be lost.",
            listOf(
                ChessChoiceView.Choice("Restart", "Begin from the opening position", "↻", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Cancel", "Keep the current match", "↩", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showMenu() },
        ) {
            if (it == 0) startGame() else showMenu()
        }
    }

    private fun showDifficultyMenu() {
        val current = SettingsManager.getYoteDifficulty(this)
        showChoiceOverlay(
            "CPU Difficulty",
            "Choose the computer’s strength.",
            listOf(
                ChessChoiceView.Choice("Easy${if (current == 0) "  ✓" else ""}", "A relaxed opponent", "I", Color.parseColor("#8EC7B9")),
                ChessChoiceView.Choice("Medium${if (current == 1) "  ✓" else ""}", "A balanced match", "II", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Hard${if (current == 2) "  ✓" else ""}", "A sharper opponent", "III", Color.parseColor("#E58A7A")),
            ),
            onCancel = { showMenu() },
        ) {
            SettingsManager.setYoteDifficulty(this, it)
            showMenu()
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        val rules = YoteRulesView(this)
        rules.onBack = {
            dismissOverlay()
            if (showModeAfter) showModeDialog() else showBoardAfterDialog()
        }
        showOverlay(rules) {
            if (showModeAfter) showModeDialog() else showBoardAfterDialog()
        }
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        gameOverView.visibility = View.GONE
        matchStarted = true
        MusicPlayer.enterMatch(this)
        resultRecorded = false
        autoplayEnabled = false
        autoplayMoveInProgress = false
        previousStates.clear()
        undosRemaining = SettingsManager.undoCredits(this, "YOTE")
        AdManager.loadRewarded(this)
        capturedByWhite.clear()
        capturedByBlack.clear()
        pendingBonusCaptureMove = null
        pendingBonusCapturePositions = emptyList()
        pendingBonusCaptureFromComputer = false
        PausedMatchStore.clear(this, "YOTE")
        autoplayButton.visibility = if (autoplayAllowed && vsAI) View.VISIBLE else View.GONE
        autoplayButton.setAutoplayEnabled(false, animate = false)
        if (vsAI) SettingsManager.setActiveGame(this, "yote")
        gameState = engine.initialState()
        boardView.playerColor = playerColor
        boardView.vsAI = vsAI
        boardView.gameState = gameState
        showBoardAfterDialog(false)

        restoring?.moves?.forEach { move ->
            val previous = gameState
            val next = engine.applyMove(gameState, move)
            if (next !== previous) {
                previousStates.add(previous)
                recordCaptures(previous, next.moveHistory.lastOrNull() ?: move)
                gameState = next
            }
        }
        boardView.gameState = gameState
        updateHud()
        if (vsAI && gameState.status == GameStatus.IN_PROGRESS && aiControlsCurrentTurn()) {
            triggerAI()
        }
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, "YOTE") ?: run {
            showModeDialog()
            return
        }
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }.getOrDefault(PieceColor.WHITE)
        startGame(paused)
    }

    private fun handleBoardMove(move: Move, fromComputer: Boolean) {
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) return
        // Human taps are blocked during the computer's turn, but the
        // computer's own animation must still commit through this callback.
        if (!fromComputer && vsAI && gameState.currentTurn != playerColor && !autoplayEnabled) {
            boardView.isLocked = false
            updateHud()
            return
        }
        val bonus = engine.availableBonusCaptures(gameState, move)
        if (bonus.isNotEmpty()) {
            boardView.isLocked = true
            showBonusCapturePicker(move, bonus, fromComputer)
        } else {
            commitMove(move, fromComputer)
        }
    }

    private fun showBonusCapturePicker(
        move: Move,
        positions: List<Position>,
        fromComputer: Boolean,
    ) {
        boardView.isLocked = true
        pendingBonusCaptureMove = move
        pendingBonusCapturePositions = positions
        pendingBonusCaptureFromComputer = fromComputer
        val choices = positions.map { position ->
            ChessChoiceView.Choice(
                label = coordinate(position),
                detail = "Remove one extra opponent stone",
                symbol = "×",
                accent = Color.parseColor("#E58A7A"),
            )
        }
        showChoiceOverlay(
            "Double Capture",
            "Choose one extra opponent stone to remove.",
            choices,
            onDismiss = { boardView.isLocked = false },
            onCancel = { showBonusCapturePicker(move, positions, fromComputer) },
            onChoice = { index ->
                val completedMove = engine.completeBonusCapture(gameState, move, positions[index])
                pendingBonusCaptureMove = null
                pendingBonusCapturePositions = emptyList()
                pendingBonusCaptureFromComputer = false
                screenRoot.post { commitMove(completedMove, fromComputer) }
            },
            gridChoices = true,
        )
    }

    private fun commitMove(move: Move, fromComputer: Boolean = false) {
        val previous = gameState
        val nextState = engine.applyMove(previous, move)
        // A cancelled animation can finish after the activity has already
        // advanced to another state. Treat that callback as stale instead of
        // leaving the board locked or showing a permanent AI-thinking label.
        if (nextState === gameState) {
            boardView.isLocked = false
            updateHud()
            return
        }
        previousStates.add(gameState)
        val capturedPositions = buildList {
            addAll(move.captures)
            (move.metadata["bonusCapture"] as? Position)?.let(::add)
        }.distinct()
        boardView.flashCapturedPieces(
            capturedPositions.mapNotNull { position ->
                previous.get(position)?.let { piece -> position to piece.color }
            },
        )
        recordCaptures(previous, nextState.moveHistory.lastOrNull() ?: move)
        gameState = nextState
        boardView.gameState = gameState
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        updateHud()
        playYoteMoveSound(move)
        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
            autoplayMoveInProgress = false
            autoplayButton.setAutoplayEnabled(false, animate = false)
            recordResult()
            SoundPlayer.play(if (gameState.status == GameStatus.DRAW) "game_draw" else "game_end")
            scope.launch {
                delay(850L)
                if (activityResumed) showResultDialog()
            }
        } else {
            autoplayMoveInProgress = false
            val loopDetected = autoplayEnabled && fromComputer && autoplayLoopDetector.record(move)
            if (loopDetected) {
                stopAutoplayForLoop()
            } else if (vsAI && aiControlsCurrentTurn()) {
                triggerAI()
            }
        }
    }

    private fun undoMove() {
        if (previousStates.isEmpty() || boardView.isLocked) return
        if (undosRemaining == 0) {
            UndoRewardDialog.show(this) {
                undosRemaining += 2
                SettingsManager.setUndoCredits(this, "YOTE", undosRemaining)
                updateHud()
            }
            return
        }

        stopAutomatedGameplay()
        val steps = if (vsAI && gameState.currentTurn == playerColor && previousStates.size >= 2) {
            2
        } else {
            1
        }
        repeat(steps) {
            if (previousStates.isNotEmpty()) {
                gameState = previousStates.removeLast()
            }
        }
        rebuildCapturedPieces()
        boardView.gameState = gameState
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        undosRemaining--
        SettingsManager.setUndoCredits(this, "YOTE", undosRemaining)
        updateHud()
        resumeComputerTurnIfNeeded()
    }

    private fun rebuildCapturedPieces() {
        capturedByWhite.clear()
        capturedByBlack.clear()
        var replayState = engine.initialState()
        gameState.moveHistory.forEach { move ->
            val nextState = engine.applyMove(replayState, move)
            if (nextState === replayState) return@forEach
            recordCaptures(replayState, nextState.moveHistory.lastOrNull() ?: move)
            replayState = nextState
        }
    }

    private fun updateHud() {
        val turn = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "Computer turn"
            gameState.currentTurn == PieceColor.WHITE -> "White's turn"
            else -> "Black's turn"
        }
        val displayTurn = if (
            gameState.status == GameStatus.IN_PROGRESS &&
            vsAI &&
            gameState.currentTurn != playerColor
        ) {
            "CPU Turn"
        } else {
            turn
        }
        hudView.setInfo(
            displayTurn,
            undo = previousStates.isNotEmpty(),
            undoCount = undosRemaining,
            accentColor = if (gameState.currentTurn == PieceColor.WHITE) {
                Color.WHITE
            } else {
                Color.parseColor("#FFD54F")
            },
        )
        topInfoView.update(engine.reserveCount(gameState, PieceColor.BLACK), capturedByBlack)
        bottomInfoView.update(engine.reserveCount(gameState, PieceColor.WHITE), capturedByWhite)
    }

    private fun playYoteMoveSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                SoundPlayer.play("game_end")
                return
            }
            GameStatus.DRAW -> {
                SoundPlayer.play("game_draw")
                return
            }
            GameStatus.IN_PROGRESS -> Unit
        }
        if (move.captures.isNotEmpty() || move.metadata["bonusCapture"] != null) {
            SoundPlayer.playMovement("checkers_capture")
        } else {
            SoundPlayer.playMovement("checkers_move")
        }
    }

    private fun recordCaptures(previous: com.mkdev.mkboardgames.engine.GameState, move: Move) {
        val capturedPositions = buildList {
            addAll(move.captures)
            (move.metadata["bonusCapture"] as? Position)?.let(::add)
        }.distinct()
        val capturedPieces = capturedPositions.mapNotNull { previous.get(it) }
        if (previous.currentTurn == PieceColor.WHITE) {
            capturedByWhite.addAll(capturedPieces)
        } else {
            capturedByBlack.addAll(capturedPieces)
        }
    }

    private fun triggerAI() {
        if (!activityResumed || activeOverlay != null || gameState.status != GameStatus.IN_PROGRESS ||
            !aiControlsCurrentTurn() || aiJob?.isActive == true
        ) return
        boardView.isLocked = true
        hudView.setThinking(true)
        val snapshot = gameState
        val requestToken = ++aiRequestToken
        autoplayMoveInProgress = autoplayEnabled && snapshot.currentTurn == playerColor
        aiJob = scope.launch {
            try {
                val move = withContext(Dispatchers.Default) {
                    try {
                        YoteAIPlayer(
                            engine,
                            SettingsManager.yoteAiDepth(this@YoteActivity),
                        ).bestMove(snapshot)
                    } catch (_: Throwable) {
                        null
                    } ?: runCatching {
                        engine.allLegalMoves(snapshot, snapshot.currentTurn).firstOrNull()
                    }.getOrNull()
                }
                hudView.setThinking(false)
                if (!activityResumed || requestToken != aiRequestToken || snapshot != gameState ||
                    activeOverlay != null
                ) {
                    boardView.isLocked = false
                    updateHud()
                    return@launch
                }
                move?.let { boardView.animateMove(it, fromComputer = true) } ?: run {
                    boardView.isLocked = false
                    updateHud()
                }
            } finally {
                if (requestToken == aiRequestToken) aiJob = null
            }
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (::boardView.isInitialized && matchStarted && vsAI &&
            activeOverlay == null && gameState.status == GameStatus.IN_PROGRESS &&
            aiControlsCurrentTurn()
        ) triggerAI()
    }

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && (gameState.currentTurn != playerColor ||
            (autoplayAllowed && autoplayEnabled))

    private fun canPauseMatch(): Boolean =
        matchStarted &&
            gameState.status == GameStatus.IN_PROGRESS &&
            !aiControlsCurrentTurn() &&
            !boardView.isLocked &&
            !boardView.hasPendingMoveAnimation()

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        autoplayMoveInProgress = false
        autoplayLoopDetector.reset()
        aiRequestToken++
        aiJob?.cancel()
        aiJob = null
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) boardView.isLocked = false
        if (::hudView.isInitialized) hudView.setThinking(false)
        if (::autoplayButton.isInitialized) autoplayButton.setAutoplayEnabled(false, animate = false)
    }

    private fun stopAutoplayForLoop() {
        stopAutomatedGameplay()
        // Autoplay controls both sides, so after a loop the next side is the
        // side the user must take over. Otherwise the board still rejects
        // taps for the original CPU side and leaves the HUD on "CPU Turn".
        playerColor = gameState.currentTurn
        boardView.playerColor = playerColor
        boardView.isLocked = false
        updateHud()
        Toast.makeText(this, "Loop detected, manual play required", Toast.LENGTH_LONG).show()
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        gameOverView.winnerLabel = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.WHITE) "You" else "White"}"
            GameStatus.BLACK_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.BLACK) "You" else "Black"}"
            GameStatus.DRAW -> "Draw"
            GameStatus.IN_PROGRESS -> return
        }
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun currentResultLabel(): String = when (gameState.status) {
        GameStatus.WHITE_WINS -> "White wins"
        GameStatus.BLACK_WINS -> "Black wins"
        GameStatus.DRAW -> "Draw"
        GameStatus.IN_PROGRESS -> ""
    }

    private fun launchReplay(resultLabel: String) {
        showBoardAfterDialog()
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE, "YOTE")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(gameState.moveHistory))
            putExtra(ReplayActivity.EXTRA_RESULT, resultLabel)
        })
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            MusicPlayer.resumeMatch(this)
        }
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        dismissOverlay()
        gameRoot.visibility = View.VISIBLE
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun showHome() {
        stopAutomatedGameplay()
        PausedMatchStore.clear(this, "YOTE")
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        dismissOverlay()
        finish()
    }

    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW -> SettingsManager.recordDraw(this)
            GameStatus.IN_PROGRESS -> Unit
        }
    }

    private fun savePausedMatch() {
        if (gameState.moveHistory.isNotEmpty() && gameState.status == GameStatus.IN_PROGRESS) {
            PausedMatchStore.save(
                this,
                "YOTE",
                vsAI,
                playerColor.name,
                gameState.moveHistory,
            )
        }
    }

    private fun coordinate(position: Position): String =
        "${('A'.code + position.col).toChar()}${position.row + 1}"
}