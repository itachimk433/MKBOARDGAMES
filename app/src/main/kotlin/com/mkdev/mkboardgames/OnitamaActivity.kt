package com.mkdev.mkboardgames

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.games.onitama.OnitamaAIPlayer
import com.mkdev.mkboardgames.games.onitama.OnitamaRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.ChessRulesView
import com.mkdev.mkboardgames.ui.OnitamaBoardView
import com.mkdev.mkboardgames.ui.OnitamaCardsView
import com.mkdev.mkboardgames.ui.OnitamaCardStripView
import com.mkdev.mkboardgames.ui.OnitamaAtmosphereView
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class OnitamaActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_GAME = "ONITAMA"
    }

    private val engine = OnitamaRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var resultRecorded = false
    private var autoplayEnabled = false
    private val autoplayAllowed: Boolean
        get() = SettingsManager.currentMode(this) == GameMode.IRREGULAR
    private var aiRequestToken = 0
    private var aiJob: Job? = null
    private val previousStates = ArrayDeque<GameState>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var gameRoot: View
    private lateinit var screenRoot: FrameLayout
    private lateinit var hudView: StandardGameHudView
    private lateinit var boardView: OnitamaBoardView
    private lateinit var topCards: OnitamaCardStripView
    private lateinit var bottomCards: OnitamaCardStripView
    private lateinit var autoplayButton: AutoplayButtonView
    private lateinit var gameOverView: SnakesLaddersGameOverView
    private var activeOverlay: View? = null
    private var whiteCardIndex = 0
    private var blackCardIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()
        SoundPlayer.init(this)
        val density = resources.displayMetrics.density

        hudView = StandardGameHudView(this, labelTextSizeSp = 12f).apply {
            onBack = { onBackPressed() }
            onUndo = { undoMove() }
            onMenu = { if (!boardView.isLocked) showMenu() }
        }
        topCards = OnitamaCardStripView(this, "BLACK")
        bottomCards = OnitamaCardStripView(this, "WHITE")
        boardView = OnitamaBoardView(this).apply {
            onMoveMade = { move, fromComputer -> commitMove(move, fromComputer) }
            onGameOverTapped = { if (activeOverlay == null) showResultDialog() }
        }
        topCards.onCardSelected = { index ->
            blackCardIndex = index
            syncCards()
        }
        bottomCards.onCardSelected = { index ->
            whiteCardIndex = index
            syncCards()
        }
        autoplayButton = AutoplayButtonView(this).apply {
            onAutoplayChanged = { enabled ->
                autoplayEnabled = autoplayAllowed && enabled
                if (autoplayAllowed && enabled) triggerAI()
                else {
                    aiRequestToken++
                    aiJob?.cancel()
                    aiJob = null
                    boardView.cancelMoveAnimation()
                    updateHud()
                }
            }
        }

        val gameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(hudView, LinearLayout.LayoutParams(-1, (62f * density).toInt()))
            addView(topCards, LinearLayout.LayoutParams(-1, (98f * density).toInt()))
            addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
            addView(bottomCards, LinearLayout.LayoutParams(-1, (98f * density).toInt()))
        }
        val gameFrame = FrameLayout(this).apply {
            addView(
                OnitamaAtmosphereView(this@OnitamaActivity),
                FrameLayout.LayoutParams(-1, -1),
            )
            addView(gameLayout, FrameLayout.LayoutParams(-1, -1))
            addView(
                autoplayButton,
                FrameLayout.LayoutParams((78f * density).toInt(), (78f * density).toInt()).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(0, 0, (10f * density).toInt(), (104f * density).toInt())
                },
            )
        }
        gameRoot = gameFrame
        screenRoot = FrameLayout(this)
        screenRoot.addView(gameRoot, FrameLayout.LayoutParams(-1, -1))
        gameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            bottomCaptureTopPxProvider = { bottomCards.top.toFloat() }
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
        autoplayButton.visibility = View.GONE

        showModeDialog()
    }

    private fun showModeDialog() {
        gameRoot.visibility = View.GONE
        val menu = ChessMenuView(
            this,
            PausedMatchStore.has(this, "ONITAMA"),
            gameLabel = "O N I T A M A",
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
            PausedMatchStore.load(this, "ONITAMA")?.let { paused ->
                vsAI = paused.vsAI
                playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }.getOrDefault(PieceColor.WHITE)
                startGame(paused)
            }
        }
        showOverlay(menu) {
            if (matchStarted) showBoardAfterDialog() else finish()
        }
    }

    private fun showColorPicker() {
        showChoiceOverlay(
            "Choose your side",
            "White moves first. Pick the side you control.",
            listOf(
                ChessChoiceView.Choice("White", "Moves first", "", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Black", "Moves second", "", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showModeDialog() },
        ) {
            playerColor = if (it == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        gameOverView.visibility = View.GONE
        matchStarted = true
        MusicPlayer.enterOnitamaMatch(this)
        resultRecorded = false
        autoplayEnabled = false
        previousStates.clear()
        whiteCardIndex = 0
        blackCardIndex = 0
        PausedMatchStore.clear(this, "ONITAMA")
        autoplayButton.visibility = if (autoplayAllowed && vsAI) View.VISIBLE else View.GONE
        autoplayButton.setAutoplayEnabled(false, animate = false)
        if (vsAI) SettingsManager.setActiveGame(this, "onitama")
        val setupSeed = restoring?.setupSeed ?: System.nanoTime()
        gameState = restoring?.setupSeed?.let(engine::initialState) ?: engine.initialState(setupSeed)
        boardView.playerColor = playerColor
        boardView.vsAI = vsAI
        boardView.gameState = gameState
        showBoardAfterDialog(false)

        restoring?.moves?.forEach { move ->
            val previous = gameState
            val next = engine.applyMove(gameState, move)
            if (next !== previous) {
                previousStates.add(previous)
                gameState = next
            }
        }
        boardView.gameState = gameState
        syncCards()
        updateHud()
        if (vsAI && gameState.status == GameStatus.IN_PROGRESS && aiControlsCurrentTurn()) triggerAI()
    }

    private fun commitMove(move: Move, fromComputer: Boolean) {
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) return
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
        syncCards()
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        updateHud()
        SoundPlayer.playMovement("checkers_move")

        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
            autoplayButton.setAutoplayEnabled(false, animate = false)
            recordResult()
            SoundPlayer.play("game_end")
            scope.launch {
                delay(850L)
                if (activityResumed) showResultDialog()
            }
        } else if (vsAI && aiControlsCurrentTurn()) {
            triggerAI()
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
        aiJob = scope.launch {
            try {
                val move = withContext(Dispatchers.Default) {
                    try {
                        OnitamaAIPlayer(
                            engine,
                            SettingsManager.onitamaAiDepth(this@OnitamaActivity),
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
                move?.let {
                    selectCardForMove(it)
                    boardView.animateMove(it, fromComputer = true)
                } ?: run {
                    boardView.isLocked = false
                    updateHud()
                }
            } finally {
                if (requestToken == aiRequestToken) aiJob = null
            }
        }
    }

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && (gameState.currentTurn != playerColor ||
            (autoplayAllowed && autoplayEnabled))

    private fun selectCardForMove(move: Move) {
        val cardId = move.metadata["card"] as? String ?: return
        val cards = engine.cards(gameState, gameState.currentTurn)
        val selectedIndex = cards.indexOfFirst { it.id == cardId }
        if (selectedIndex < 0) return
        if (gameState.currentTurn == PieceColor.WHITE) {
            whiteCardIndex = selectedIndex
        } else {
            blackCardIndex = selectedIndex
        }
        syncCards()
    }

    private fun syncCards() {
        topCards.cards = engine.cards(gameState, PieceColor.BLACK)
        bottomCards.cards = engine.cards(gameState, PieceColor.WHITE)
        topCards.sideCard = engine.sideCard(gameState)
        bottomCards.sideCard = null

        val currentCards = engine.cards(gameState, gameState.currentTurn)
        val currentIndex = if (gameState.currentTurn == PieceColor.WHITE) whiteCardIndex else blackCardIndex
        val safeIndex = currentIndex.coerceIn(0, (currentCards.size - 1).coerceAtLeast(0))
        if (gameState.currentTurn == PieceColor.WHITE) whiteCardIndex = safeIndex else blackCardIndex = safeIndex
        boardView.selectedCardId = currentCards.getOrNull(safeIndex)?.id
        topCards.selectedIndex = blackCardIndex
        bottomCards.selectedIndex = whiteCardIndex
        topCards.interactive = !vsAI || (gameState.currentTurn == PieceColor.BLACK && gameState.currentTurn == playerColor)
        bottomCards.interactive = !vsAI || (gameState.currentTurn == PieceColor.WHITE && gameState.currentTurn == playerColor)
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
            accentColor = if (gameState.currentTurn == PieceColor.WHITE) Color.WHITE else Color.parseColor("#FFD54F"),
        )
        hudView.setThinking(vsAI && gameState.status == GameStatus.IN_PROGRESS && gameState.currentTurn != playerColor)
    }

    private fun undoMove() {
        if (previousStates.isEmpty() || boardView.isLocked) return
        stopAutomatedGameplay()
        val steps = if (vsAI && gameState.currentTurn == playerColor && previousStates.size >= 2) 2 else 1
        repeat(steps) {
            if (previousStates.isNotEmpty()) gameState = previousStates.removeLast()
        }
        boardView.gameState = gameState
        boardView.isLocked = false
        syncCards()
        updateHud()
        if (vsAI && aiControlsCurrentTurn()) triggerAI()
    }

    private fun showMenu() {
        MusicPlayer.enterPausedMatch(this)
        stopAutomatedGameplay()
        boardView.isLocked = true
        showChoiceOverlay(
            "Game Menu",
            "What would you like to do?",
            listOf(
                ChessChoiceView.Choice("New Game / Restart", "Start a fresh Onitama match", "↻", Color.parseColor("#E3B86A")),
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
        val current = SettingsManager.getOnitamaDifficulty(this)
        showChoiceOverlay(
            "CPU Difficulty",
            "Choose the computer's strength.",
            listOf(
                ChessChoiceView.Choice("Easy${if (current == 0) "  ✓" else ""}", "A relaxed opponent", "I", Color.parseColor("#8EC7B9")),
                ChessChoiceView.Choice("Medium${if (current == 1) "  ✓" else ""}", "A balanced match", "II", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Hard${if (current == 2) "  ✓" else ""}", "A sharper opponent", "III", Color.parseColor("#E58A7A")),
                ChessChoiceView.Choice("Back", "Return to the menu", "↩", Color.parseColor("#A9B6E8")),
            ),
            onCancel = { showMenu() },
        ) {
            if (it < 3) {
                SettingsManager.setOnitamaDifficulty(this, it)
                showMenu()
            } else showMenu()
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        val rules = ChessRulesView(
            this,
            "Onitama",
            "Overview\nOnitama is a fast martial-arts duel on a 5 × 5 board. Each player commands one Master and four Students. White moves first.\n\nMoving\nChoose one of your two movement cards. Select one of your pieces, then choose a highlighted destination. A move must follow the card exactly, stay on the board, and land on an empty square or an opposing piece.\n\nCards & Swapping\nAfter moving, swap the card you used with the shared SIDE card. The old SIDE card joins your hand, while the card you used becomes the new SIDE card. Your newly received card can be used on a later turn.\n\nCapturing\nA piece may capture an opposing piece by landing on its square. The Master is a piece too and may use either card. Your pieces may never land on a friendly piece.\n\nWinning\nCapture the opposing Master or move your Master onto the opposing Temple Arch. The match ends immediately when either victory condition is reached.",
            gameLabel = "O N I T A M A",
            headerSymbol = "◆",
        )
        rules.onDone = {
            dismissOverlay()
            if (showModeAfter) showModeDialog() else showBoardAfterDialog()
        }
        rules.onViewCards = { showCards(showModeAfter) }
        showOverlay(rules) {
            if (showModeAfter) showModeDialog() else showBoardAfterDialog()
        }
    }

    private fun showCards(showModeAfter: Boolean) {
        val cards = OnitamaCardsView(this).apply {
            onBack = { showRules(showModeAfter) }
        }
        showOverlay(cards) {
            showRules(showModeAfter)
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
            gameLabel = "O N I T A M A",
            headerSymbol = "◆",
            fullScreenOverride = true,
        )
        overlay.onChoiceSelected = {
            dismissOverlay(revealGame = false)
            onChoice(it)
        }
        showOverlay(overlay, onCancel = onCancel)
    }

    private fun showOverlay(view: View, onCancel: () -> Unit) {
        dismissOverlay(revealGame = false)
        activeOverlay = view
        view.tag = onCancel
        screenRoot.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.requestFocus()
        gameRoot.visibility = View.GONE
    }

    private fun dismissOverlay(revealGame: Boolean = true) {
        activeOverlay?.let { screenRoot.removeView(it) }
        activeOverlay = null
        if (revealGame) gameRoot.visibility = View.VISIBLE
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        dismissOverlay()
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        gameRoot.visibility = View.VISIBLE
        if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            MusicPlayer.resumeMatch(this)
        }
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        syncCards()
        updateHud()
        if (resumeAi) triggerAI()
    }

    private fun showLeaveMatchDialog() {
        MusicPlayer.enterPausedMatch(this)
        stopAutomatedGameplay()
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
                    gameRoot.visibility = View.GONE
                    savePausedMatch()
                    finish()
                }
                1 -> {
                    gameRoot.visibility = View.GONE
                    PausedMatchStore.clear(this, "ONITAMA")
                    if (vsAI) SettingsManager.recordForfeit(this)
                    finish()
                }
                else -> showBoardAfterDialog()
            }
        }
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
        showBoardAfterDialog(false)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE, "ONITAMA")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(gameState.moveHistory))
            putExtra(ReplayActivity.EXTRA_RESULT, resultLabel)
            putExtra(
                ReplayActivity.EXTRA_ONITAMA_SETUP_SEED,
                (gameState.metadata[OnitamaRuleEngine.SETUP_SEED] as? Long) ?: Long.MIN_VALUE,
            )
        })
    }

    private fun savePausedMatch() {
        if (gameState.moveHistory.isNotEmpty() && gameState.status == GameStatus.IN_PROGRESS) {
            PausedMatchStore.save(
                this,
                "ONITAMA",
                vsAI,
                playerColor.name,
                gameState.moveHistory,
                setupSeed = gameState.metadata[OnitamaRuleEngine.SETUP_SEED] as? Long,
            )
        }
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

    private fun showHome() {
        stopAutomatedGameplay()
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        gameRoot.visibility = View.GONE
        PausedMatchStore.clear(this, "ONITAMA")
        finish()
    }

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        aiRequestToken++
        aiJob?.cancel()
        aiJob = null
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) boardView.isLocked = false
        if (::hudView.isInitialized) hudView.setThinking(false)
        if (::autoplayButton.isInitialized) autoplayButton.setAutoplayEnabled(false, animate = false)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        makeFullscreen()
        if (::boardView.isInitialized && matchStarted && activeOverlay == null) triggerAI()
    }

    override fun onPause() {
        activityResumed = false
        aiRequestToken++
        aiJob?.cancel()
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (matchStarted && gameState.status != GameStatus.IN_PROGRESS) {
            return
        }
        if (activeOverlay != null) {
            val callback = activeOverlay?.tag as? (() -> Unit)
            dismissOverlay(revealGame = false)
            callback?.invoke()
        } else if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            showLeaveMatchDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}