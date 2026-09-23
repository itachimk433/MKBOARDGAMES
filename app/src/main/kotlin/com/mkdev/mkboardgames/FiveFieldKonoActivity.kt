package com.mkdev.mkboardgames

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.games.fivefieldkono.FiveFieldKonoAIPlayer
import com.mkdev.mkboardgames.games.fivefieldkono.FiveFieldKonoRuleEngine
import com.mkdev.mkboardgames.ui.BoardSelectionOption
import com.mkdev.mkboardgames.ui.BoardSelectionPreview
import com.mkdev.mkboardgames.ui.BoardSelectionView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.FiveFieldKonoBoardStyle
import com.mkdev.mkboardgames.ui.FiveFieldKonoBoardView
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FiveFieldKonoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_GAME = "FIVE_FIELD_KONO"
        private const val GAME_KEY = "FIVE_FIELD_KONO"
        private const val GAME_LABEL = "F I V E · F I E L D · K O N O"
        private const val KONO_RULES = """
            Setup
            Five Field Kono uses a 5 × 5 board. Each player has seven pieces:
            five on their home row and one on each end of the adjacent row.
            White moves first.

            Move
            Move one of your pieces exactly one point diagonally to a neighbouring empty point.
            Pieces cannot jump and no pieces are captured.

            Winning
            Move all seven of your pieces onto the opponent's seven starting points.
            White aims for the top row and its two adjacent corner points;
            Black aims for the bottom row and its two adjacent corner points.

            The Boards
            Choose between the supplied warm wood board and the clean black-and-white board before the match.
        """
    }

    private val engine = FiveFieldKonoRuleEngine()
    private var gameState = engine.initialState()
    private var boardStyle = FiveFieldKonoBoardStyle.WOOD
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var resultRecorded = false
    private var aiJob: Job? = null
    private val previousStates = ArrayDeque<GameState>()
    private var undosRemaining = 3
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var gameRoot: View
    private lateinit var screenRoot: FrameLayout
    private lateinit var hudView: StandardGameHudView
    private lateinit var boardView: FiveFieldKonoBoardView
    private lateinit var gameOverView: SnakesLaddersGameOverView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()

        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#071522"))
        }
        hudView = StandardGameHudView(this, labelTextSizeSp = 12f).apply {
            onBack = { onBackPressed() }
            onUndo = { undoMove() }
            onMenu = { if (!boardView.isLocked) showMenu() }
        }
        boardView = FiveFieldKonoBoardView(this).apply {
            onMoveMade = { move, fromComputer -> handleBoardMove(move, fromComputer) }
            onGameOverTapped = { showResultDialog() }
        }
        layout.addView(hudView, LinearLayout.LayoutParams(-1, (56 * density).toInt()))
        layout.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        AdManager.attachBanner(layout)
        gameRoot = layout

        screenRoot = FrameLayout(this)
        layout.visibility = View.GONE
        screenRoot.addView(layout, FrameLayout.LayoutParams(-1, -1))
        gameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            onReplay = {
                visibility = View.GONE
                showBoardSelection(fromMode = false)
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
            boardView.resumeMoveAnimation()
            if (!boardView.hasPendingMoveAnimation()) resumeComputerTurnIfNeeded()
        }
    }

    override fun onPause() {
        if (!isFinishing && ::boardView.isInitialized) savePausedMatch()
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
        if (!matchStarted) {
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
            PausedMatchStore.has(this, GAME_KEY),
            gameLabel = GAME_LABEL,
        )
        menu.onVsAi = {
            StyledDialogs.dismiss()
            vsAI = true
            showBoardSelection(fromMode = true)
        }
        menu.onTwoPlayers = {
            StyledDialogs.dismiss()
            vsAI = false
            playerColor = PieceColor.WHITE
            showBoardSelection(fromMode = false)
        }
        menu.onHowToPlay = {
            StyledDialogs.dismiss()
            showRules(showModeAfter = !matchStarted)
        }
        menu.onResumeMatch = {
            StyledDialogs.dismiss()
            resumePausedMatch()
        }
        StyledDialogs.showFullScreenView(this, menu) {
            if (matchStarted) showBoardAfterDialog() else finish()
        }
    }

    private fun showBoardSelection(fromMode: Boolean) {
        gameRoot.visibility = View.GONE
        val options = FiveFieldKonoBoardStyle.entries.map {
            BoardSelectionOption(
                title = it.title,
                detail = it.detail,
                assetName = it.assetName,
                preview = BoardSelectionPreview.GRID,
            )
        }
        val picker = BoardSelectionView(this, "Five Field Kono", options)
        picker.onSelectionConfirmed = { index ->
            StyledDialogs.dismiss()
            boardStyle = FiveFieldKonoBoardStyle.entries[index]
            if (fromMode && vsAI) showColorPicker() else startGame()
        }
        picker.onBackClicked = {
            StyledDialogs.dismiss()
            showModeDialog()
        }
        StyledDialogs.showFullScreenView(this, picker) {
            showModeDialog()
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
            onCancel = { showBoardSelection(fromMode = true) },
        ) {
            playerColor = if (it == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        StyledDialogs.showRules(
            this,
            gameName = "Five Field Kono",
            rules = KONO_RULES,
            gameLabel = GAME_LABEL,
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() },
        )
    }

    private fun showChoiceOverlay(
        title: String,
        subtitle: String,
        choices: List<ChessChoiceView.Choice>,
        onCancel: () -> Unit,
        onChoice: (Int) -> Unit,
    ) {
        gameRoot.visibility = View.GONE
        StyledDialogs.showChoices(
            this,
            title,
            subtitle,
            choices,
            gameLabel = GAME_LABEL,
            onCancel = onCancel,
        ) { index, _ ->
            onChoice(index)
        }
    }

    private fun dismissOverlay() {
        StyledDialogs.dismiss()
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        stopAutomatedGameplay()
        dismissOverlay()
        gameOverView.visibility = View.GONE
        gameRoot.visibility = View.VISIBLE
        matchStarted = true
        resultRecorded = false
        previousStates.clear()
        undosRemaining = SettingsManager.undoCredits(this, "FIVE_FIELD_KONO")
        AdManager.loadRewarded(this)
        if (restoring == null) PausedMatchStore.clear(this, GAME_KEY)
        if (vsAI) SettingsManager.setActiveGame(this, "five_field_kono")

        gameState = engine.initialState()
        boardView.boardStyle = boardStyle
        boardView.playerColor = playerColor
        boardView.vsAI = vsAI
        boardView.gameState = gameState
        boardView.isLocked = false

        restoring?.moves?.forEach { move ->
            val previous = gameState
            val next = engine.applyMove(previous, move)
            if (next !== previous) {
                previousStates.add(previous)
                gameState = next
            }
        }
        boardView.gameState = gameState
        updateHud()
        if (vsAI && gameState.status == GameStatus.IN_PROGRESS && gameState.currentTurn != playerColor) {
            triggerAI()
        }
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, GAME_KEY) ?: run {
            showModeDialog()
            return
        }
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }.getOrDefault(PieceColor.WHITE)
        boardStyle = FiveFieldKonoBoardStyle.entries.getOrElse(paused.boardStyle ?: 0) {
            FiveFieldKonoBoardStyle.WOOD
        }
        startGame(paused)
    }

    private fun handleBoardMove(move: Move, fromComputer: Boolean) {
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) return
        if (!fromComputer && vsAI && gameState.currentTurn != playerColor) {
            boardView.isLocked = false
            updateHud()
            return
        }
        commitMove(move)
    }

    private fun commitMove(move: Move) {
        val previous = gameState
        val next = engine.applyMove(previous, move)
        if (next === previous) {
            boardView.isLocked = false
            updateHud()
            return
        }
        previousStates.add(previous)
        gameState = next
        boardView.gameState = gameState
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        SoundPlayer.playMovement("checkers_move")
        updateHud()
        if (gameState.status != GameStatus.IN_PROGRESS) {
            recordResult()
            SoundPlayer.play("game_end")
            scope.launch {
                delay(700L)
                if (activityResumed) showResultDialog()
            }
        } else {
            resumeComputerTurnIfNeeded()
        }
    }

    private fun undoMove() {
        if (previousStates.isEmpty() || boardView.isLocked) return
        if (undosRemaining == 0) {
            UndoRewardDialog.show(this) {
                undosRemaining += 2
                SettingsManager.setUndoCredits(this, "FIVE_FIELD_KONO", undosRemaining)
                updateHud()
            }
            return
        }
        stopAutomatedGameplay()
        val steps = if (vsAI && gameState.currentTurn == playerColor && previousStates.size >= 2) 2 else 1
        repeat(steps) {
            if (previousStates.isNotEmpty()) gameState = previousStates.removeLast()
        }
        boardView.gameState = gameState
        boardView.isLocked = false
        undosRemaining--
        SettingsManager.setUndoCredits(this, "FIVE_FIELD_KONO", undosRemaining)
        updateHud()
        resumeComputerTurnIfNeeded()
    }

    private fun updateHud() {
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "CPU Turn"
            gameState.currentTurn == PieceColor.WHITE -> "White's turn"
            else -> "Black's turn"
        }
        hudView.setInfo(
            label,
            undo = previousStates.isNotEmpty(),
            undoCount = undosRemaining,
            accentColor = if (gameState.currentTurn == PieceColor.WHITE) Color.WHITE else Color.parseColor("#FFD54F"),
        )
    }

    private fun triggerAI() {
        if (!activityResumed || StyledDialogs.hasActiveOverlay() || !vsAI ||
            gameState.status != GameStatus.IN_PROGRESS || gameState.currentTurn == playerColor ||
            aiJob?.isActive == true
        ) return
        boardView.isLocked = true
        hudView.setThinking(true)
        val snapshot = gameState
        aiJob = scope.launch {
            val move = withContext(Dispatchers.Default) {
                runCatching {
                    val profile = SettingsManager.fiveFieldKonoAiProfileForLevel(
                        SettingsManager.getFiveFieldKonoDifficulty(this@FiveFieldKonoActivity),
                    )
                    FiveFieldKonoAIPlayer(
                        engine = engine,
                        maxDepth = profile.depth,
                        timeLimitMs = profile.timeLimitMs,
                        choiceWindow = profile.choiceWindow,
                    ).bestMove(snapshot)
                }.getOrNull() ?: engine.allLegalMoves(snapshot, snapshot.currentTurn).firstOrNull()
            }
            hudView.setThinking(false)
            if (activityResumed && snapshot == gameState &&
                !StyledDialogs.hasActiveOverlay() && move != null
            ) {
                boardView.animateMove(move, fromComputer = true)
            } else {
                boardView.isLocked = false
                updateHud()
            }
            aiJob = null
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (matchStarted && vsAI && gameState.status == GameStatus.IN_PROGRESS &&
            gameState.currentTurn != playerColor
        ) triggerAI()
    }

    private fun stopAutomatedGameplay() {
        aiJob?.cancel()
        aiJob = null
        if (::boardView.isInitialized) boardView.cancelMoveAnimation()
        if (::hudView.isInitialized) hudView.setThinking(false)
    }

    private fun pauseAutomatedGameplayForLifecycle() {
        aiJob?.cancel()
        aiJob = null
        if (::boardView.isInitialized) {
            if (boardView.hasPendingMoveAnimation()) boardView.pauseMoveAnimation()
            else boardView.isLocked = false
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
    }

    private fun showMenu() {
        if (!canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        stopAutomatedGameplay()
        showChoiceOverlay(
            "Game Menu",
            "What would you like to do?",
            listOf(
                ChessChoiceView.Choice("New Game / Restart", "Start a fresh Kono match", "↻", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("How To Play", "Review the rules", "?", Color.parseColor("#8EC7B9")),
                ChessChoiceView.Choice("CPU Difficulty", "Choose the challenge", "◆", Color.parseColor("#A9B6E8")),
                ChessChoiceView.Choice("Home", "Return to the catalogue", "⌂", Color.parseColor("#E58A7A")),
            ),
            onCancel = { showBoardAfterDialog() },
        ) {
            when (it) {
                0 -> showBoardSelection(fromMode = false)
                1 -> showRules(false)
                2 -> showDifficultyMenu()
                else -> showHome()
            }
        }
    }

    private fun showDifficultyMenu() {
        val current = SettingsManager.getFiveFieldKonoDifficulty(this)
        showChoiceOverlay(
            "CPU Difficulty",
            "Choose the computer's strength.",
            listOf(
                ChessChoiceView.Choice("Easy${if (current == 0) "  ✓" else ""}", "A relaxed opponent", "I", Color.parseColor("#8EC7B9")),
                ChessChoiceView.Choice("Medium${if (current == 1) "  ✓" else ""}", "A balanced match", "II", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Hard${if (current == 2) "  ✓" else ""}", "A sharper opponent", "III", Color.parseColor("#E58A7A")),
            ),
            onCancel = { showMenu() },
        ) {
            SettingsManager.setFiveFieldKonoDifficulty(this, it)
            showMenu()
        }
    }

    private fun showLeaveMatchDialog() {
        MusicPlayer.enterPausedMatch(this)
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
                    PausedMatchStore.clear(this, GAME_KEY)
                    if (vsAI) SettingsManager.recordForfeit(this)
                    finish()
                }
                else -> showBoardAfterDialog()
            }
        }
    }

    private fun canPauseMatch(): Boolean =
        matchStarted &&
            gameState.status == GameStatus.IN_PROGRESS &&
            !(vsAI && gameState.currentTurn != playerColor) &&
            !boardView.isLocked &&
            !boardView.hasPendingMoveAnimation()

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        gameOverView.winnerLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> "Winner: ${if (vsAI && playerColor == PieceColor.WHITE) "You" else "White"}"
            GameStatus.BLACK_WINS -> "Winner: ${if (vsAI && playerColor == PieceColor.BLACK) "You" else "Black"}"
            GameStatus.DRAW -> "Draw"
            GameStatus.IN_PROGRESS -> return
        }
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun showBoardAfterDialog() {
        dismissOverlay()
        gameRoot.visibility = View.VISIBLE
        gameOverView.visibility = View.GONE
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        MusicPlayer.resumeMatch(this)
        resumeComputerTurnIfNeeded()
    }

    private fun showHome() {
        stopAutomatedGameplay()
        PausedMatchStore.clear(this, GAME_KEY)
        finish()
    }

    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS ->
                if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS ->
                if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW -> SettingsManager.recordDraw(this)
            GameStatus.IN_PROGRESS -> Unit
        }
    }

    private fun savePausedMatch() {
        if (gameState.moveHistory.isEmpty() || gameState.status != GameStatus.IN_PROGRESS) return
        PausedMatchStore.save(
            this,
            GAME_KEY,
            vsAI,
            playerColor.name,
            gameState.moveHistory,
            boardSize = FiveFieldKonoRuleEngine.SIZE,
            boardStyle = boardStyle.ordinal,
        )
    }
}