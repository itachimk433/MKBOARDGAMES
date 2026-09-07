package com.mkdev.mkboardgames

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.yote.YoteAIPlayer
import com.mkdev.mkboardgames.games.yote.YoteRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.MancalaWoodButton
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
    private var autoplayMoveInProgress = false
    private var aiRequestToken = 0
    private var aiJob: Job? = null
    private val previousStates = ArrayDeque<com.mkdev.mkboardgames.engine.GameState>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var gameRoot: View
    private lateinit var screenRoot: FrameLayout
    private lateinit var boardView: YoteBoardView
    private lateinit var statusView: TextView
    private lateinit var topInfoView: YotePieceStripView
    private lateinit var bottomInfoView: YotePieceStripView
    private lateinit var autoplayButton: AutoplayButtonView
    private var activeOverlay: View? = null
    private val capturedByWhite = mutableListOf<Piece>()
    private val capturedByBlack = mutableListOf<Piece>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        val density = resources.displayMetrics.density

        val gameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#071522"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (3 * density).toInt())
        }
        val title = TextView(this).apply {
            text = "YOTÉ"
            setTextColor(Color.parseColor("#E7C995"))
            textSize = 20f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f
        }
        statusView = TextView(this).apply {
            setTextColor(Color.parseColor("#D4C8BD"))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        topInfoView = YotePieceStripView(this, PieceColor.BLACK)
        bottomInfoView = YotePieceStripView(this, PieceColor.WHITE)
        header.addView(title, LinearLayout.LayoutParams(-1, (30 * density).toInt()))
        header.addView(statusView, LinearLayout.LayoutParams(-1, (24 * density).toInt()))

        boardView = YoteBoardView(this).apply {
            onMoveMade = { move, fromComputer -> handleBoardMove(move, fromComputer) }
            onGameOverTapped = { if (activeOverlay == null) showResultDialog() }
        }

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (5 * density).toInt())
        }
        autoplayButton = AutoplayButtonView(this, circularStyle = false).apply {
            onAutoplayChanged = { enabled ->
                if (vsAI) {
                    autoplayEnabled = enabled
                    if (enabled && matchStarted && gameState.status == GameStatus.IN_PROGRESS &&
                        !boardView.isLocked && aiControlsCurrentTurn()
                    ) {
                        triggerAI()
                    }
                }
            }
        }
        val menuButton = MancalaWoodButton(this, "Menu").apply {
            style = MancalaWoodButton.Style.GOLD
            onClick = { if (!boardView.isLocked) showMenu() }
        }
        controls.addView(autoplayButton, LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f))
        controls.addView(menuButton, LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f))

        gameLayout.addView(header, LinearLayout.LayoutParams(-1, (58 * density).toInt()))
        gameLayout.addView(topInfoView, LinearLayout.LayoutParams(-1, (34 * density).toInt()))
        gameLayout.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        gameLayout.addView(bottomInfoView, LinearLayout.LayoutParams(-1, (34 * density).toInt()))
        gameLayout.addView(controls, LinearLayout.LayoutParams(-1, (56 * density).toInt()))
        gameRoot = gameLayout

        screenRoot = FrameLayout(this)
        gameLayout.visibility = View.GONE
        screenRoot.addView(gameLayout, FrameLayout.LayoutParams(-1, -1))
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
            resumeComputerTurnIfNeeded()
        }
    }

    override fun onPause() {
        activityResumed = false
        if (::boardView.isInitialized) boardView.cancelMoveAnimation()
        stopAutomatedGameplay()
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
            fullScreenOverride = true,
        )
        overlay.onChoiceSelected = {
            dismissOverlay()
            onChoice(it)
        }
        showOverlay(overlay, onCancel)
    }

    private fun showOverlay(view: View, onCancel: () -> Unit = { showBoardAfterDialog() }) {
        dismissOverlay()
        activeOverlay = view
        view.tag = onCancel
        screenRoot.addView(view, FrameLayout.LayoutParams(-1, -1))
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
                ChessChoiceView.Choice("Home", "Save and return to the catalogue", "⌂", Color.parseColor("#E58A7A")),
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
                ChessChoiceView.Choice("Back", "Return to the menu", "↩", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showMenu() },
        ) {
            if (it < 3) {
                SettingsManager.setYoteDifficulty(this, it)
                showMenu()
            } else {
                showMenu()
            }
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
        matchStarted = true
        resultRecorded = false
        autoplayEnabled = false
        autoplayMoveInProgress = false
        previousStates.clear()
        capturedByWhite.clear()
        capturedByBlack.clear()
        PausedMatchStore.clear(this, "YOTE")
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
            showBonusCapturePicker(move, bonus)
        } else {
            commitMove(move)
        }
    }

    private fun showBonusCapturePicker(move: Move, positions: List<Position>) {
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
            "Yoté rewards a jump with one extra capture anywhere on the board.",
            choices,
            onCancel = { showBonusCapturePicker(move, positions) },
        ) { index ->
            commitMove(engine.completeBonusCapture(gameState, move, positions[index]))
        }
    }

    private fun commitMove(move: Move) {
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
        recordCaptures(previous, nextState.moveHistory.lastOrNull() ?: move)
        gameState = nextState
        boardView.gameState = gameState
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        updateHud()
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
            if (vsAI && aiControlsCurrentTurn()) triggerAI()
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
        val reserve = "W ${engine.reserveCount(gameState, PieceColor.WHITE)} · " +
            "B ${engine.reserveCount(gameState, PieceColor.BLACK)} in reserve"
        statusView.text = "$turn  ·  $reserve"
        topInfoView.update(engine.reserveCount(gameState, PieceColor.BLACK), capturedByBlack)
        bottomInfoView.update(engine.reserveCount(gameState, PieceColor.WHITE), capturedByWhite)
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
        statusView.text = "Computer is thinking…"
        val snapshot = gameState
        val requestToken = ++aiRequestToken
        autoplayMoveInProgress = autoplayEnabled && snapshot.currentTurn == playerColor
        aiJob = scope.launch {
            try {
                val move = withContext(Dispatchers.Default) {
                    YoteAIPlayer(engine, SettingsManager.yoteAiDepth(this@YoteActivity)).bestMove(snapshot)
                }
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
        vsAI && (gameState.currentTurn != playerColor || autoplayEnabled)

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        autoplayMoveInProgress = false
        aiRequestToken++
        aiJob?.cancel()
        aiJob = null
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) boardView.isLocked = false
        if (::autoplayButton.isInitialized) autoplayButton.setAutoplayEnabled(false, animate = false)
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val message = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win!" else "White wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win!" else "Black wins!"
            GameStatus.DRAW -> "It's a draw!"
            GameStatus.IN_PROGRESS -> return
        }
        StyledDialogs.showChoices(
            this,
            "Game Over",
            message,
            listOf(
                StyledDialogs.choice("Play Again", "Start a fresh game", "↻", "#E3B86A"),
                StyledDialogs.choice("Main Menu", "Choose another match", "⌂", "#E58A7A"),
            ),
            420f,
            "Y O T É",
            onCancel = { showBoardAfterDialog() },
            fullScreen = false,
        ) { which, dialog ->
            dialog.dismiss()
            if (which == 0) startGame() else showHome()
        }
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        dismissOverlay()
        gameRoot.visibility = View.VISIBLE
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun showHome() {
        stopAutomatedGameplay()
        PausedMatchStore.clear(this, "YOTE")
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