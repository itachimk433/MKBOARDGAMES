package com.mkdev.mkboardgames

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.mancala.MancalaAIPlayer
import com.mkdev.mkboardgames.games.mancala.MancalaRuleEngine
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.MancalaRulesView
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.StandardGameHudView
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

class MancalaActivity : AppCompatActivity() {
    private val engine = MancalaRuleEngine()
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
    private var movementSpeedMultiplier = 1f
    private val previousStates = ArrayDeque<GameState>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private data class StoneAppearance(
        val id: Int,
        val color: PieceColor,
        val variation: Int,
    )

    private data class SettlementTransfer(
        val stone: StoneAppearance,
        val from: Int,
        val to: Int,
        val route: List<Int>,
    )

    private data class StyleTransition(
        val preSettlementStyles: Array<List<StoneAppearance>>,
        val afterStyles: Array<List<StoneAppearance>>,
        val settlementTransfers: List<SettlementTransfer>,
    )

    private data class CaptureFeedback(
        val stones: Int,
        val mover: PieceColor,
    )

    private data class MoveAnimation(
        val from: Int,
        val path: List<Int>,
        val sowingRoutes: List<List<Int>>,
        val before: IntArray,
        val after: IntArray,
        val beforeStyles: Array<List<StoneAppearance>>,
        val preSettlementStyles: Array<List<StoneAppearance>>,
        val afterStyles: Array<List<StoneAppearance>>,
        val movedStones: List<StoneAppearance>,
        val settlementTransfers: List<SettlementTransfer>,
        val speedMultiplier: Float,
    ) {
        // Stones first gather in a short staging line outside the board. They
        // then fly directly from that line to their landing pits.
        val pickupDuration = 0f
        private val normalizedSpeed = speedMultiplier.coerceIn(1f, 4f)
        private val movementSlowdown = 1.2f
        val stagingTravelDuration = 120f * movementSlowdown / normalizedSpeed
        val stagingGap = 34f * movementSlowdown / normalizedSpeed
        val stagingDuration = if (movedStones.isEmpty()) {
            0f
        } else {
            stagingTravelDuration + (movedStones.size - 1) * stagingGap
        }
        // Keep the selected movement-speed setting proportional to the actual
        // pit-to-pit flight time.
        // Make every piece animation 1.2x slower while preserving the
        // relative differences between the selectable speed settings.
        val placementDuration = 260f * movementSlowdown / normalizedSpeed
        val settleDuration = 600f * movementSlowdown / normalizedSpeed
        val sowingDuration = stagingDuration + sowingRoutes.fold(0f) { total, route ->
            total + routeDuration(route)
        }
        val settlementDuration = settlementTransfers.fold(0f) { total, transfer ->
            total + routeDuration(transfer.route)
        }
        val totalDuration =
            pickupDuration + sowingDuration + settlementDuration + settleDuration

        private fun routeDuration(route: List<Int>): Float =
            maxOf(1, route.size - 1) * placementDuration
    }

    private data class HoleMeasurement(
        val x: Float,
        val y: Float,
        val radius: Float,
    )

    private lateinit var gameRoot: View
    private lateinit var screenRoot: FrameLayout
    private lateinit var boardView: MancalaBoardView
    private lateinit var hudView: StandardGameHudView
    private lateinit var autoplayButton: AutoplayButtonView
    private lateinit var gameOverView: SnakesLaddersGameOverView
    private var activeMancalaOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()
        val dp = resources.displayMetrics.density
        movementSpeedMultiplier = SettingsManager.getMancalaMovementSpeed(this).toFloat()

        val gameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#120D0B"))
        }
        hudView = StandardGameHudView(
            this,
            showHistoryControls = false,
            labelOffsetDp = -18f,
        ).apply {
            onBack = { onBackPressed() }
            onMenu = { if (!boardView.isMoveAnimating) showMenu() }
        }

        boardView = MancalaBoardView(this)
        boardView.onPitTapped = { handlePitTap(it) }
        boardView.onGameOverTapped = {
            if (!boardView.isMoveAnimating) showResultDialog()
        }

        autoplayButton = AutoplayButtonView(this)
        autoplayButton.onAutoplayChanged = { enabled ->
            if (autoplayAllowed && vsAI) {
                autoplayEnabled = enabled
                if (enabled &&
                    matchStarted &&
                    gameState.status == GameStatus.IN_PROGRESS &&
                    !boardView.isMoveAnimating &&
                    !boardView.isLocked &&
                    aiControlsCurrentTurn()
                ) {
                    triggerAI()
                }
            }
        }
        gameLayout.addView(hudView, LinearLayout.LayoutParams(-1, 56 * dp.toInt()))
        gameLayout.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        gameLayout.addView(
            autoplayButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                76 * dp.toInt(),
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        AdManager.attachBanner(gameLayout)
        gameRoot = gameLayout

        screenRoot = FrameLayout(this)
        gameLayout.visibility = View.GONE
        screenRoot.addView(
            gameLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        gameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            onReplay = {
                visibility = View.GONE
                startGame()
            }
            onHome = {
                visibility = View.GONE
                clearPausedMatch()
                finish()
            }
        }
        screenRoot.addView(
            gameOverView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
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
        if (::boardView.isInitialized) resumeComputerTurnIfNeeded()
    }

    override fun onPause() {
        activityResumed = false
        if (::boardView.isInitialized) stopAutomatedGameplay()
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

    override fun onDestroy() {
        if (::boardView.isInitialized) stopAutomatedGameplay()
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (::boardView.isInitialized && boardView.isMoveAnimating) return
        if (gameState.status != GameStatus.IN_PROGRESS) {
            leaveCompletedGameToHome()
            return
        }
        if (activeMancalaOverlay != null) {
            cancelMancalaOverlay()
            return
        }
        if (!matchStarted) {
            @Suppress("DEPRECATION") super.onBackPressed()
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
        if (::gameRoot.isInitialized) gameRoot.visibility = View.GONE
        val menuView = ChessMenuView(
            this,
            PausedMatchStore.has(this, "MANCALA"),
            gameLabel = "M A N C A L A",
        )
        menuView.onVsAi = {
            dismissMancalaOverlay()
            vsAI = true
            showColorPickerDialog()
        }
        menuView.onTwoPlayers = {
            dismissMancalaOverlay()
            vsAI = false
            playerColor = PieceColor.WHITE
            startGame()
        }
        menuView.onHowToPlay = {
            dismissMancalaOverlay()
            showRules(showModeAfter = !matchStarted)
        }
        menuView.onResumeMatch = {
            dismissMancalaOverlay()
            resumePausedMatch()
        }
        showMancalaOverlay(menuView) {
            if (!matchStarted) finish() else showBoardAfterDialog()
        }
    }

    private fun showColorPickerDialog() {
        showChoiceOverlay(
            "Choose your side",
            "South moves first. Pick the side you control.",
            listOf(
                StyledDialogs.choice("South", "Moves first", "●", "#E3B86A"),
                StyledDialogs.choice("North", "Moves second", "○", "#A9B6E8"),
            ),
            onCancel = { showModeDialog() },
        ) { which ->
            playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun showChoiceOverlay(
        title: String,
        subtitle: String,
        options: List<ChessChoiceView.Choice>,
        onCancel: () -> Unit,
        onChoice: (Int) -> Unit,
    ) {
        val overlay = ChessChoiceView(
            context = this,
            title = title,
            subtitle = subtitle,
            choices = options,
            gameLabel = "M A N C A L A",
            headerSymbol = "●",
            fullScreenOverride = true,
        )
        overlay.onChoiceSelected = { index ->
            dismissMancalaOverlay()
            onChoice(index)
        }
        showMancalaOverlay(overlay, onCancel)
    }

    private fun showHomeMenu() {
        showChoiceOverlay(
            "Mancala",
            "Choose an option.",
            listOf(
                StyledDialogs.choice("New Game", "Start a fresh Mancala match", "↻", "#E3B86A"),
                StyledDialogs.choice("How To Play", "Review the rules", "?", "#8EC7B9"),
                StyledDialogs.choice("CPU Difficulty", "Choose the challenge", "◆", "#A9B6E8"),
                StyledDialogs.choice("Back", "Return to the home screen", "⌂", "#E58A7A"),
            ),
            onCancel = { showHome() },
        ) { which ->
            when (which) {
                0 -> showModeDialog()
                1 -> showRules(false)
                2 -> showDifficultyMenu(returnToHome = true)
                else -> showHome()
            }
        }
    }

    private fun showMancalaOverlay(view: View, onCancel: () -> Unit) {
        dismissMancalaOverlay()
        activeMancalaOverlay = view
        view.setOnClickListener(null)
        screenRoot.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        view.requestFocus()
        view.tag = onCancel
    }

    private fun dismissMancalaOverlay() {
        activeMancalaOverlay?.let { screenRoot.removeView(it) }
        activeMancalaOverlay = null
    }

    private fun cancelMancalaOverlay() {
        val callback = activeMancalaOverlay?.tag as? (() -> Unit)
        dismissMancalaOverlay()
        callback?.invoke()
    }

    private fun showLeaveMatchDialog() {
        MusicPlayer.enterPausedMatch(this)
        stopAutomatedGameplay()
        boardView.isLocked = true
        showChoiceOverlay(
            "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
            ),
            onCancel = { showBoardAfterDialog() },
        ) { which ->
            when (which) {
                0 -> {
                    savePausedMatch()
                    finish()
                }
                1 -> {
                    clearPausedMatch()
                    if (vsAI) SettingsManager.recordForfeit(this)
                    finish()
                }
                else -> showBoardAfterDialog()
            }
        }
    }

    private fun showMenu() {
        if (boardView.isMoveAnimating) return
        MusicPlayer.enterPausedMatch(this)
        stopAutomatedGameplay()
        boardView.isLocked = true
        showChoiceOverlay(
            "Game Menu",
            "What would you like to do?",
            listOf(
                StyledDialogs.choice("New Game / Restart", "Start a fresh Mancala match", "↻", "#E3B86A"),
                StyledDialogs.choice("How To Play", "Review the rules", "?", "#8EC7B9"),
                StyledDialogs.choice("CPU Difficulty", "Choose the challenge", "◆", "#A9B6E8"),
                StyledDialogs.choice("Movement Speed", "Set stone animation speed", "»", "#8EC7B9"),
                StyledDialogs.choice("Home", "Return to the catalogue", "⌂", "#E58A7A"),
            ),
            onCancel = { showBoardAfterDialog() },
        ) { which ->
            when (which) {
                0 -> showRestartConfirmation()
                1 -> showRules(false)
                2 -> showDifficultyMenu(returnToHome = false)
                3 -> showMovementSpeedMenu()
                else -> showHomeConfirmation()
            }
        }
    }

    private fun showMovementSpeedMenu() {
        val current = SettingsManager.getMancalaMovementSpeed(this)
        val options = (1..4).map { multiplier ->
            val label = "${multiplier}x${if (multiplier == current) "  ✓" else ""}"
            val detail = when (multiplier) {
                1 -> "Relaxed stone movement"
                2 -> "Balanced stone movement"
                3 -> "Quick stone movement"
                else -> "Fastest stone movement"
            }
            val symbol = "I".repeat(multiplier)
            StyledDialogs.choice(label, detail, symbol, "#8EC7B9")
        } + StyledDialogs.choice("Back", "Return to the game menu", "↩", "#A9B6E8")
        showChoiceOverlay(
            "Movement Speed",
            "Choose how quickly stones move around the board.",
            options,
            onCancel = { showMenu() },
        ) { which ->
            if (which in 0..3) {
                val multiplier = which + 1
                SettingsManager.setMancalaMovementSpeed(this, multiplier)
                movementSpeedMultiplier = multiplier.toFloat()
                showMenu()
            } else {
                showMenu()
            }
        }
    }

    private fun showRestartConfirmation() {
        showChoiceOverlay(
            "Restart this game?",
            "Your current progress will be lost.",
            listOf(
                StyledDialogs.choice("Restart", "Begin from the opening position", "↻", "#E3B86A"),
                StyledDialogs.choice("Cancel", "Keep the current match", "↩", "#A9B6E8"),
            ),
            onCancel = { showMenu() },
        ) { which ->
            if (which == 0) startGame() else showMenu()
        }
    }

    private fun showHomeConfirmation() {
        showChoiceOverlay(
            "Go to home screen?",
            "Your current match will be lost.",
            listOf(
                StyledDialogs.choice("Go Home", "Return to the catalogue", "⌂", "#E58A7A"),
                StyledDialogs.choice("Stay in Game", "Keep the current match", "↩", "#A9B6E8"),
            ),
            onCancel = { showMenu() },
        ) { which ->
            if (which == 0) {
                clearPausedMatch()
                matchStarted = false
                showHome()
            } else {
                showMenu()
            }
        }
    }

    private fun showDifficultyMenu(returnToHome: Boolean) {
        val current = SettingsManager.getMancalaDifficulty(this)
        val options = listOf(
            StyledDialogs.choice(
                "Easy${if (current == 0) "  ✓" else ""}",
                "A relaxed opponent",
                "I",
                "#8EC7B9",
            ),
            StyledDialogs.choice(
                "Medium${if (current == 1) "  ✓" else ""}",
                "A balanced match",
                "II",
                "#E3B86A",
            ),
            StyledDialogs.choice(
                "Hard${if (current == 2) "  ✓" else ""}",
                "A sharper opponent",
                "III",
                "#E58A7A",
            ),
        )
        showChoiceOverlay(
            "CPU Difficulty",
            "Choose the computer’s strength.",
            options,
            onCancel = { if (returnToHome) showHome() else showMenu() },
        ) { which ->
            if (which < 3) {
                if (!returnToHome && which != current) {
                    showDifficultyConfirmation(which)
                } else {
                    SettingsManager.setMancalaDifficulty(this, which)
                    if (returnToHome) showDifficultyMenu(returnToHome = true) else showMenu()
                }
            }
        }
    }

    private fun showDifficultyConfirmation(level: Int) {
        showChoiceOverlay(
            "Change CPU difficulty?",
            "Changing difficulty will start a new game.",
            listOf(
                StyledDialogs.choice("Change & Restart", "Apply the new challenge", "↻", "#E3B86A"),
                StyledDialogs.choice("Cancel", "Keep the current difficulty", "↩", "#A9B6E8"),
            ),
            onCancel = { showMenu() },
        ) { which ->
            if (which == 0) {
                SettingsManager.setMancalaDifficulty(this, level)
                startGame()
            } else {
                showMenu()
            }
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        val rules = MancalaRulesView(this)
        rules.onBack = { cancelMancalaOverlay() }
        showMancalaOverlay(
            rules,
            onCancel = { if (showModeAfter) showModeDialog() else showHomeOrBoard() },
        )
    }

    private fun showHomeOrBoard() {
        if (matchStarted) showBoardAfterDialog() else showHome()
    }

    private fun showHome() {
        stopAutomatedGameplay()
        dismissMancalaOverlay()
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        if (::gameRoot.isInitialized) gameRoot.visibility = View.GONE
        finish()
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        gameOverView.visibility = View.GONE
        matchStarted = true
        MusicPlayer.enterMatch(this)
        resultRecorded = false
        autoplayEnabled = false
        autoplayMoveInProgress = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        autoplayButton.visibility = if (autoplayAllowed && vsAI) View.VISIBLE else View.GONE
        previousStates.clear()
        PausedMatchStore.clear(this, "MANCALA")
        SettingsManager.activateGameTheme(this, "mancala")
        if (vsAI) SettingsManager.setActiveGame(this, "mancala")
        gameState = engine.initialState()
        boardView.setGameState(gameState, animate = false)
        showBoardAfterDialog(resumeAi = false)

        restoring?.moves?.forEach { move ->
            previousStates.add(gameState)
            gameState = engine.applyMove(gameState, move)
        }
        if (restoring != null) {
            boardView.setGameState(gameState, animate = false)
            PausedMatchStore.clear(this, "MANCALA")
        }
        updateHud()
        if (vsAI && gameState.status == GameStatus.IN_PROGRESS &&
            aiControlsCurrentTurn()
        ) triggerAI()
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, "MANCALA") ?: run {
            showModeDialog()
            return
        }
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }
            .getOrDefault(PieceColor.WHITE)
        startGame(paused)
    }

    private fun handlePitTap(index: Int) {
        if (!matchStarted || boardView.isLocked || gameState.status != GameStatus.IN_PROGRESS) {
            if (gameState.status != GameStatus.IN_PROGRESS) showResultDialog()
            return
        }
        if (vsAI && aiControlsCurrentTurn()) return
        val move = engine.legalMovesFrom(gameState, Position(0, index)).firstOrNull() ?: return
        playMove(move)
    }

    private fun playMove(move: Move, autoplayTurn: Boolean = false) {
        previousStates.add(gameState)
        gameState = engine.applyMove(gameState, move)
        autoplayMoveInProgress = autoplayTurn
        if (gameState.status == GameStatus.IN_PROGRESS) {
            boardView.onMoveAnimationFinished = {
                boardView.isLocked = false
                if (autoplayMoveInProgress) {
                    autoplayMoveInProgress = false
                    autoplayEnabled = false
                    autoplayButton.setAutoplayEnabled(false, animate = false)
                }
                if (vsAI && aiControlsCurrentTurn()) triggerAI()
            }
        }
        boardView.setGameState(gameState)
        updateHud()
        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
            autoplayMoveInProgress = false
            autoplayButton.setAutoplayEnabled(false, animate = false)
            boardView.isLocked = true
            recordResult()
            SoundPlayer.play(if (gameState.status == GameStatus.DRAW) "game_draw" else "game_end")
            scope.launch {
                delay(850)
                if (activityResumed) showResultDialog()
            }
        }
    }

    private fun triggerAI() {
        if (!activityResumed || gameState.status != GameStatus.IN_PROGRESS ||
            !aiControlsCurrentTurn()
        ) return
        boardView.isLocked = true
        hudView.setThinking(true)
        val snapshot = gameState
        val autoplayingPlayerTurn = autoplayEnabled && snapshot.currentTurn == playerColor
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    MancalaAIPlayer(
                        engine,
                        SettingsManager.mancalaAiDepth(this@MancalaActivity),
                    ).bestMove(snapshot)
                } catch (_: Throwable) {
                    null
                } ?: runCatching {
                    engine.allLegalMoves(snapshot, snapshot.currentTurn).firstOrNull()
                }.getOrNull()
            }
            hudView.setThinking(false)
            val autoplayStillControlsTurn =
                snapshot.currentTurn != playerColor || autoplayEnabled
            if (!activityResumed || snapshot != gameState || !autoplayStillControlsTurn) {
                boardView.isLocked = false
                updateHud()
                return@launch
            }
            boardView.isLocked = false
            if (move != null) playMove(move, autoplayTurn = autoplayingPlayerTurn)
            else updateHud()
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (::boardView.isInitialized && matchStarted && vsAI &&
            activeMancalaOverlay == null &&
            gameState.status == GameStatus.IN_PROGRESS &&
            aiControlsCurrentTurn()
        ) triggerAI()
    }

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && (gameState.currentTurn != playerColor ||
            (autoplayAllowed && autoplayEnabled))

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        autoplayMoveInProgress = false
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) {
            boardView.isLocked = false
            boardView.onMoveAnimationFinished = null
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
        }
    }

    private fun undoMove() {
        if (previousStates.isEmpty() || boardView.isLocked) return
        scope.coroutineContext.cancelChildren()
        val steps = if (vsAI && previousStates.size >= 2) 2 else 1
        repeat(steps) {
            if (previousStates.isNotEmpty()) gameState = previousStates.removeLast()
        }
        boardView.onMoveAnimationFinished = null
        boardView.isLocked = false
        boardView.setGameState(gameState, animate = false)
        updateHud()
        resumeComputerTurnIfNeeded()
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        gameOverView.winnerLabel = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.WHITE) "You" else "South"}"
            GameStatus.BLACK_WINS ->
                "Winner: ${if (vsAI && playerColor == PieceColor.BLACK) "You" else "North"}"
            GameStatus.DRAW -> "Draw"
            else -> return
        }
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun leaveCompletedGameToHome() {
        stopAutomatedGameplay()
        dismissMancalaOverlay()
        clearPausedMatch()
        matchStarted = false
        showHome()
    }

    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS ->
                if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this)
                else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS ->
                if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this)
                else SettingsManager.recordLoss(this)
            GameStatus.DRAW -> SettingsManager.recordDraw(this)
            else -> Unit
        }
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, "MANCALA")

    private fun savePausedMatch() {
        if (gameState.moveHistory.isNotEmpty() && gameState.status == GameStatus.IN_PROGRESS) {
            PausedMatchStore.save(
                this,
                "MANCALA",
                vsAI,
                playerColor.name,
                gameState.moveHistory,
            )
        }
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        dismissMancalaOverlay()
        if (::gameOverView.isInitialized) gameOverView.visibility = View.GONE
        if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            MusicPlayer.resumeMatch(this)
        }
        if (::gameRoot.isInitialized) gameRoot.visibility = View.VISIBLE
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun updateHud() {
        val turn = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "CPU Turn"
            gameState.currentTurn == PieceColor.WHITE -> "South's turn"
            else -> "North's turn"
        }
        hudView.setInfo(
            turn,
            undo = false,
            accentColor = if (gameState.currentTurn == PieceColor.WHITE) {
                Color.WHITE
            } else {
                Color.parseColor("#FFD54F")
            },
        )
    }

    inner class MancalaBoardView(context: Context) : View(context) {
        var isLocked = false
        val isMoveAnimating: Boolean
            get() = moveAnimator?.isRunning == true || moveAnimation != null
        var onPitTapped: ((Int) -> Unit)? = null
        var onGameOverTapped: (() -> Unit)? = null
        var onMoveAnimationFinished: (() -> Unit)? = null

        private var state = engine.initialState()
        private var stoneStyles = Array(MancalaRuleEngine.BOARD_CELLS) {
            mutableListOf<StoneAppearance>()
        }
        private var boardRect = RectF()
        private var moveAnimator: ValueAnimator? = null
        private var moveAnimation: MoveAnimation? = null
        private var animationProgress = 1f
        private var animationGeneration = 0
        private var sowingLandingSoundsPlayed = 0
        private var settlementLandingSoundsPlayed = 0
        private var captureFeedback: CaptureFeedback? = null
        private var captureFeedbackAnimator: ValueAnimator? = null
        private var captureFeedbackProgress = 0f
        private var captureFeedbackGeneration = 0
        private val boardBitmap = try {
            context.assets.open("mancala_board.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val stoneBitmaps = arrayOf(
            BitmapFactory.decodeResource(resources, R.drawable.mancala_stone_blue),
            BitmapFactory.decodeResource(resources, R.drawable.mancala_stone_white),
            BitmapFactory.decodeResource(resources, R.drawable.mancala_stone_black),
            BitmapFactory.decodeResource(resources, R.drawable.mancala_stone_green),
        )
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stoneRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f * resources.displayMetrics.density
        }
        private val stoneShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 20, 8, 3)
        }
        private val stoneShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stoneGlintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stoneSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 66, 165, 245)
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
        }
        private val movingHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#42A5F5")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.6f * resources.displayMetrics.density
        }
        private val captureBurstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE09C")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val capturePanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val capturePanelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val captureTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        private val captureTitleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            style = Paint.Style.STROKE
        }
        private val captureSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        private var highlightAnimator: ValueAnimator? = null
        private var highlightProgress = 0f
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 246, 226)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val pitCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        // Measured in source-image pixels from mancala_board.webp (768 x 2048).
        private val leftHoles = arrayOf(
            HoleMeasurement(239f, 410f, 110f),
            HoleMeasurement(239f, 640f, 110f),
            HoleMeasurement(239f, 869f, 110f),
            HoleMeasurement(237f, 1098f, 110f),
            HoleMeasurement(237f, 1325f, 110f),
            HoleMeasurement(237f, 1561f, 112f),
        )
        private val rightHoles = arrayOf(
            HoleMeasurement(521f, 410f, 108f),
            HoleMeasurement(521f, 640f, 110f),
            HoleMeasurement(521f, 869f, 108f),
            HoleMeasurement(521f, 1100f, 110f),
            HoleMeasurement(521f, 1327f, 110f),
            HoleMeasurement(521f, 1563f, 112f),
        )

        init {
            startHighlightAnimation()
        }

        private fun startHighlightAnimation() {
            highlightAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1500L
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    highlightProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun setGameState(newState: GameState, animate: Boolean = true) {
            animationGeneration++
            moveAnimator?.cancel()
            moveAnimator = null
            val previousState = state
            val move = newState.moveHistory.lastOrNull()
            if (!animate || move == null) {
                clearCaptureFeedback()
                state = newState
                resetStoneStyles(newState)
                moveAnimation = null
                animationProgress = 1f
                isLocked = false
                invalidate()
                onMoveAnimationFinished?.invoke()
                onMoveAnimationFinished = null
                return
            }
            val path = engine.sowingPath(previousState, move)
            if (path.isEmpty()) {
                clearCaptureFeedback()
                state = newState
                resetStoneStyles(newState)
                moveAnimation = null
                animationProgress = 1f
                isLocked = false
                invalidate()
                onMoveAnimationFinished?.invoke()
                onMoveAnimationFinished = null
                return
            }
            // The rules still calculate the normal Mancala sowing order, but
            // the visual treatment is intentionally not a board-ring tour:
            // after staging, each stone takes one straight flight to its
            // respective landing pit.
            val sowingRoutes = path.map { destination ->
                listOf(move.from.col, destination)
            }

            val beforeStyles = copyStoneStyles()
            val movedStones = beforeStyles[move.from.col].toList()
            val transition = stylesAfterMove(
                previousState,
                newState,
                move.from.col,
                path,
                beforeStyles,
                movedStones,
            )
            state = newState
            stoneStyles = transition.afterStyles.map { it.toMutableList() }.toTypedArray()
            val captured = (newState.metadata["captured"] as? Int) ?: 0
            if (captured > 0) {
                startCaptureFeedback(captured, previousState.currentTurn)
            } else {
                clearCaptureFeedback()
            }
            val generation = animationGeneration
            moveAnimation = MoveAnimation(
                from = move.from.col,
                path = path,
                sowingRoutes = sowingRoutes,
                before = countsOf(previousState),
                after = countsOf(newState),
                beforeStyles = beforeStyles,
                preSettlementStyles = transition.preSettlementStyles,
                afterStyles = transition.afterStyles,
                movedStones = movedStones,
                settlementTransfers = transition.settlementTransfers,
                speedMultiplier = movementSpeedMultiplier,
            )
            sowingLandingSoundsPlayed = 0
            settlementLandingSoundsPlayed = 0
            animationProgress = 0f
            isLocked = true
            moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = moveAnimation!!.totalDuration.toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    moveAnimation?.let(::playLandingSounds)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (generation != animationGeneration) return
                        moveAnimation?.let(::playLandingSounds)
                        moveAnimator = null
                        moveAnimation = null
                        animationProgress = 1f
                        invalidate()
                        onMoveAnimationFinished?.invoke()
                        onMoveAnimationFinished = null
                    }
                })
                start()
            }
        }

        private fun playLandingSounds(animation: MoveAnimation) {
            val elapsed = animationElapsed(animation)
            val sowingLanded = completedRouteCount(
                animation.sowingRoutes,
                elapsed - animation.pickupDuration - animation.stagingDuration,
                animation.placementDuration,
            )
            while (sowingLandingSoundsPlayed < sowingLanded) {
                SoundPlayer.playMovement("checkers_move")
                sowingLandingSoundsPlayed++
            }

            val settlementLanded = completedRouteCount(
                animation.settlementTransfers.map { it.route },
                elapsed - animation.sowingDuration,
                animation.placementDuration,
            )
            while (settlementLandingSoundsPlayed < settlementLanded) {
                SoundPlayer.playMovement("checkers_move")
                settlementLandingSoundsPlayed++
            }
        }

        private fun startCaptureFeedback(stones: Int, mover: PieceColor) {
            captureFeedbackAnimator?.cancel()
            captureFeedbackAnimator = null
            captureFeedbackProgress = 0f
            val generation = ++captureFeedbackGeneration
            captureFeedback = CaptureFeedback(stones, mover)
            captureFeedbackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2_850L
                startDelay = 40L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    captureFeedbackProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (generation != captureFeedbackGeneration) return
                        captureFeedbackAnimator = null
                        captureFeedback = null
                        captureFeedbackProgress = 0f
                        invalidate()
                    }
                })
                start()
            }
        }

        private fun clearCaptureFeedback() {
            captureFeedbackGeneration++
            captureFeedbackAnimator?.cancel()
            captureFeedbackAnimator = null
            captureFeedback = null
            captureFeedbackProgress = 0f
        }

        private fun countsOf(snapshot: GameState): IntArray =
            IntArray(MancalaRuleEngine.BOARD_CELLS) { index -> engine.stones(snapshot, index) }

        private fun copyStoneStyles(): Array<List<StoneAppearance>> =
            Array(MancalaRuleEngine.BOARD_CELLS) { index -> stoneStyles[index].toList() }

        private fun resetStoneStyles(snapshot: GameState) {
            stoneStyles = Array(MancalaRuleEngine.BOARD_CELLS) { index ->
                val color = colorForCell(index)
                MutableList(engine.stones(snapshot, index)) { stoneIndex ->
                    StoneAppearance(
                        id = index * 1000 + stoneIndex,
                        color = color,
                        variation = stoneIndex,
                    )
                }
            }
        }

        private fun colorForCell(index: Int): PieceColor =
            if (index <= MancalaRuleEngine.SOUTH_STORE) PieceColor.WHITE else PieceColor.BLACK

        private fun stylesAfterMove(
            previousState: GameState,
            newState: GameState,
            from: Int,
            path: List<Int>,
            beforeStyles: Array<List<StoneAppearance>>,
            movedStones: List<StoneAppearance>,
        ): StyleTransition {
            val working = Array(MancalaRuleEngine.BOARD_CELLS) { index ->
                beforeStyles[index].toMutableList()
            }
            working[from].clear()
            path.forEachIndexed { pathIndex, destination ->
                working[destination] += movedStones[pathIndex]
            }

            val captured = (newState.metadata["captured"] as? Int) ?: 0
            val landing = engine.landing(newState)
            val preSettlementStyles = Array(MancalaRuleEngine.BOARD_CELLS) { index ->
                working[index].toList()
            }
            val settlementTransfers = ArrayList<SettlementTransfer>()
            if (captured > 0 && landing != null) {
                val opposite = MancalaRuleEngine.oppositePit(landing)
                val capturedStones = working[landing].toList() +
                    working[opposite].toList()
                listOf(landing, opposite).forEach { source ->
                    working[source].forEach { stone ->
                        settlementTransfers += SettlementTransfer(
                            stone = stone,
                            from = source,
                            to = MancalaRuleEngine.storeFor(previousState.currentTurn),
                            route = engine.capturePath(
                                source,
                                MancalaRuleEngine.storeFor(previousState.currentTurn),
                                previousState.currentTurn,
                            ),
                        )
                    }
                }
                working[landing].clear()
                working[12 - landing].clear()
                working[MancalaRuleEngine.storeFor(previousState.currentTurn)] += capturedStones
            }

            val southEnded = (0 until MancalaRuleEngine.PITS_PER_SIDE)
                .all { engine.stones(newState, it) == 0 }
            val northEnded = (MancalaRuleEngine.SOUTH_STORE + 1 until MancalaRuleEngine.NORTH_STORE)
                .all { engine.stones(newState, it) == 0 }
            if (southEnded) {
                (0 until MancalaRuleEngine.PITS_PER_SIDE).forEach {
                    working[it].forEach { stone ->
                        settlementTransfers += SettlementTransfer(
                            stone = stone,
                            from = it,
                            to = MancalaRuleEngine.SOUTH_STORE,
                            // The final sweep is a visual cleanup, not another
                            // turn. Move each remaining stone directly to the
                            // store so the result is not held up by a full
                            // board-length route for every stone.
                            route = listOf(it, MancalaRuleEngine.SOUTH_STORE),
                        )
                    }
                    working[MancalaRuleEngine.SOUTH_STORE] += working[it]
                    working[it].clear()
                }
            }
            if (northEnded) {
                (MancalaRuleEngine.SOUTH_STORE + 1 until MancalaRuleEngine.NORTH_STORE).forEach {
                    working[it].forEach { stone ->
                        settlementTransfers += SettlementTransfer(
                            stone = stone,
                            from = it,
                            to = MancalaRuleEngine.NORTH_STORE,
                            route = listOf(it, MancalaRuleEngine.NORTH_STORE),
                        )
                    }
                    working[MancalaRuleEngine.NORTH_STORE] += working[it]
                    working[it].clear()
                }
            }

            val normalized = Array(MancalaRuleEngine.BOARD_CELLS) {
                mutableListOf<StoneAppearance>()
            }
            val overflow = ArrayList<StoneAppearance>()
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                val expected = engine.stones(newState, index)
                val keep = min(expected, working[index].size)
                normalized[index] += working[index].take(keep)
                overflow += working[index].drop(keep)
            }
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                val missing = engine.stones(newState, index) - normalized[index].size
                repeat(missing) {
                    val recovered = if (overflow.isNotEmpty()) overflow.removeAt(0) else null
                    normalized[index] += recovered ?: StoneAppearance(
                        id = 100000 + index * 1000 + normalized[index].size,
                        color = colorForCell(index),
                        variation = normalized[index].size,
                    )
                }
            }
            return StyleTransition(
                preSettlementStyles = preSettlementStyles,
                afterStyles = Array(MancalaRuleEngine.BOARD_CELLS) { index ->
                    normalized[index].toList()
                },
                settlementTransfers = settlementTransfers,
            )
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#1B100C"))
            val aspect = 768f / 2048f
            val boardHeight = min(height * 0.98f, width / aspect)
            val boardWidth = boardHeight * aspect
            boardRect.set(
                (width - boardWidth) / 2f,
                (height - boardHeight) / 2f,
                (width + boardWidth) / 2f,
                (height + boardHeight) / 2f,
            )
            boardBitmap?.let { canvas.drawBitmap(it, null, boardRect, bitmapPaint) }
                ?: drawFallbackBoard(canvas)
            val counts = moveAnimation?.let { visibleCounts(it) } ?: countsOf(state)
            drawLabels(canvas, counts)
            drawPits(canvas)
            drawMoveAnimation(canvas)
            drawPitCounts(canvas, counts)
            drawCaptureFeedback(canvas)
        }

        private fun drawFallbackBoard(canvas: Canvas) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6F351D") }
            canvas.drawRoundRect(boardRect, boardRect.width() * 0.15f, boardRect.width() * 0.15f, paint)
        }

        private fun drawLabels(canvas: Canvas, counts: IntArray) {
            val top = centerFor(MancalaRuleEngine.NORTH_STORE)
            val bottom = centerFor(MancalaRuleEngine.SOUTH_STORE)
            val labelSize = boardRect.width() * 0.052f
            val scoreSize = boardRect.width() * 0.085f
            val sideInset = boardRect.width() * 0.13f
            val northX = (boardRect.left - sideInset).coerceAtLeast(scoreSize * 0.8f)
            val southX = (boardRect.right + sideInset).coerceAtMost(width - scoreSize * 0.8f)

            labelPaint.textSize = labelSize
            countPaint.textSize = scoreSize
            countPaint.color = Color.argb(235, 255, 244, 221)
            canvas.drawText("NORTH", northX, top.y - scoreSize * 0.12f, labelPaint)
            canvas.drawText(
                counts[MancalaRuleEngine.NORTH_STORE].toString(),
                northX,
                top.y + scoreSize * 0.82f,
                countPaint,
            )
            canvas.drawText("SOUTH", southX, bottom.y - scoreSize * 0.12f, labelPaint)
            canvas.drawText(
                counts[MancalaRuleEngine.SOUTH_STORE].toString(),
                southX,
                bottom.y + scoreSize * 0.82f,
                countPaint,
            )
        }

        private fun drawPits(canvas: Canvas) {
            val chipRadius = boardRect.width() * 0.022f * 2.25f
            val animation = moveAnimation
            val placed = animation?.let { placedCount(it) } ?: 0
            val settlementPlaced = animation?.let { settlementPlacedCount(it) } ?: 0
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                val point = centerFor(index)
                val hole = holeMeasurementFor(index)
                val isStore = index == MancalaRuleEngine.SOUTH_STORE || index == MancalaRuleEngine.NORTH_STORE
                if (!isStore && isSelectable(index)) {
                    val pitRadius = radiusFor(hole)
                    canvas.drawCircle(point.x, point.y, pitRadius, highlightPaint)
                    val highlightRect = RectF(
                        point.x - pitRadius,
                        point.y - pitRadius,
                        point.x + pitRadius,
                        point.y + pitRadius,
                    )
                    canvas.drawArc(
                        highlightRect,
                        highlightProgress - 45f,
                        105f,
                        false,
                        movingHighlightPaint,
                    )
                }

                val styles = if (animation == null) {
                    stoneStyles[index]
                } else if (animation.settlementTransfers.isNotEmpty() &&
                    animationElapsed(animation) >= animation.sowingDuration
                ) {
                    val visibleStyles = animation.preSettlementStyles[index].toMutableList()
                    animation.settlementTransfers.take(settlementPlaced).forEach { transfer ->
                        if (transfer.from == index) visibleStyles.remove(transfer.stone)
                        if (transfer.to == index) visibleStyles += transfer.stone
                    }
                    if (settlementPlaced < animation.settlementTransfers.size) {
                        val moving = animation.settlementTransfers[settlementPlaced]
                        if (moving.from == index) visibleStyles.remove(moving.stone)
                    }
                    visibleStyles
                } else {
                    val visibleStyles = animation.beforeStyles[index].toMutableList()
                    if (index == animation.from) {
                        visibleStyles.clear()
                        val elapsed = animationElapsed(animation)
                        val removedFromSource = if (elapsed < animation.stagingDuration) {
                            stagingRemovedCount(animation)
                        } else {
                            animation.movedStones.size
                        }
                        visibleStyles += animation.beforeStyles[index]
                            .drop(removedFromSource.coerceAtMost(animation.beforeStyles[index].size))
                    }
                    animation.path.take(placed).forEachIndexed { pathIndex, destination ->
                        if (destination == index) {
                            visibleStyles += animation.movedStones[pathIndex]
                        }
                    }
                    visibleStyles
                }
                if (styles.isEmpty()) continue
                if (isStore) {
                    drawStoreStones(canvas, point, styles, chipRadius, index)
                    continue
                }
                val pitRadius = radiusFor(hole)
                styles.forEach { style ->
                    val offset = stableCircularOffset(
                        style,
                        index,
                        (pitRadius - chipRadius * 1.28f).coerceAtLeast(0f),
                    )
                    drawStone(
                        canvas,
                        point.x + offset.x,
                        point.y + offset.y,
                        stoneRadius(chipRadius, style.color, style.variation),
                        style.color,
                        stoneVariation = style.variation,
                    )
                }
            }
        }

        private fun drawPitCounts(canvas: Canvas, counts: IntArray) {
            val textSize = (boardRect.width() * 0.047f).coerceAtLeast(
                10f * resources.displayMetrics.density,
            )
            pitCountPaint.textSize = textSize
            pitCountPaint.color = Color.WHITE
            pitCountPaint.setShadowLayer(
                textSize * 0.16f,
                0f,
                textSize * 0.08f,
                Color.argb(220, 0, 0, 0),
            )

            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                if (index == MancalaRuleEngine.SOUTH_STORE ||
                    index == MancalaRuleEngine.NORTH_STORE
                ) {
                    continue
                }
                val center = centerFor(index)
                val metrics = pitCountPaint.fontMetrics
                val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(counts[index].toString(), center.x, baseline, pitCountPaint)
            }
            pitCountPaint.clearShadowLayer()
        }

        private fun drawStoreStones(
            canvas: Canvas,
            center: PointF,
            styles: List<StoneAppearance>,
            radius: Float,
            storeIndex: Int,
        ) {
            val maxX = (boardRect.width() * 0.24f - radius * 1.28f).coerceAtLeast(0f)
            val maxY = (boardRect.height() * 0.045f - radius * 1.28f).coerceAtLeast(0f)
            styles.forEach { style ->
                val offset = stableRectOffset(style, storeIndex, maxX, maxY)
                drawStone(
                    canvas,
                    center.x + offset.x,
                    center.y + offset.y,
                    stoneRadius(radius, style.color, style.variation),
                    style.color,
                    stoneVariation = style.variation,
                )
            }
        }

        private fun placedCount(animation: MoveAnimation): Int {
            val placementElapsed = animationElapsed(animation) -
                animation.pickupDuration - animation.stagingDuration
            if (placementElapsed < 0f) return 0
            return completedRouteCount(
                animation.sowingRoutes,
                placementElapsed,
                animation.placementDuration,
            )
        }

        private fun stagingElapsed(animation: MoveAnimation): Float =
            (animationElapsed(animation) - animation.pickupDuration).coerceAtLeast(0f)

        private fun stagingRemovedCount(animation: MoveAnimation): Int {
            val elapsed = stagingElapsed(animation)
            return animation.movedStones.indices.count { index ->
                elapsed >= index * animation.stagingGap
            }
        }

        private fun stagingProgress(animation: MoveAnimation, index: Int): Float =
            ((stagingElapsed(animation) - index * animation.stagingGap) /
                animation.stagingTravelDuration).coerceIn(0f, 1f)

        private fun stagingSlotFor(animation: MoveAnimation, index: Int): PointF {
            val radius = boardRect.width() * 0.022f * 2.25f
            val source = centerFor(animation.from)
            val slotOffset = (index - (animation.movedStones.size - 1) / 2f) * radius * 1.55f
            val leftSpace = boardRect.left
            val rightSpace = width - boardRect.right
            val sideClearance = radius * 2.25f

            val side = when {
                source.x <= boardRect.centerX() && leftSpace >= sideClearance -> -1
                source.x > boardRect.centerX() && rightSpace >= sideClearance -> 1
                leftSpace >= rightSpace && leftSpace >= sideClearance -> -1
                rightSpace >= sideClearance -> 1
                else -> 0
            }
            if (side != 0) {
                val x = if (side < 0) {
                    boardRect.left - radius * 1.55f
                } else {
                    boardRect.right + radius * 1.55f
                }
                return PointF(x, source.y + slotOffset)
            }

            val topSpace = boardRect.top
            val bottomSpace = height - boardRect.bottom
            val verticalSide = if (topSpace >= bottomSpace) -1 else 1
            val y = if (verticalSide < 0) {
                boardRect.top - radius * 1.55f
            } else {
                boardRect.bottom + radius * 1.55f
            }
            return PointF(source.x + slotOffset, y)
        }

        private fun settlementPlacedCount(animation: MoveAnimation): Int {
            return completedRouteCount(
                animation.settlementTransfers.map { it.route },
                animationElapsed(animation) - animation.sowingDuration,
                animation.placementDuration,
            )
        }

        private fun animationElapsed(animation: MoveAnimation): Float =
            animationProgress * animation.totalDuration

        private fun routeDuration(route: List<Int>, placementDuration: Float): Float =
            maxOf(1, route.size - 1) * placementDuration

        private fun completedRouteCount(
            routes: List<List<Int>>,
            elapsed: Float,
            placementDuration: Float,
        ): Int {
            var remaining = elapsed.coerceAtLeast(0f)
            var completed = 0
            routes.forEach { route ->
                val duration = routeDuration(route, placementDuration)
                if (remaining < duration) return completed
                remaining -= duration
                completed++
            }
            return completed
        }

        private fun activeRouteProgress(
            routes: List<List<Int>>,
            elapsed: Float,
            placementDuration: Float,
        ): Pair<Int, Float>? {
            var remaining = elapsed.coerceAtLeast(0f)
            routes.forEachIndexed { index, route ->
                val duration = routeDuration(route, placementDuration)
                if (remaining < duration) {
                    return index to (remaining / duration).coerceIn(0f, 1f)
                }
                remaining -= duration
            }
            return null
        }

        private fun pointAlongRoute(route: List<Int>, progress: Float): PointF {
            if (route.isEmpty()) return PointF(boardRect.centerX(), boardRect.centerY())
            if (route.size == 1) return centerFor(route.first())

            val segmentProgress = progress.coerceIn(0f, 1f) * (route.size - 1)
            val segment = floor(segmentProgress).toInt().coerceAtMost(route.size - 2)
            val local = (segmentProgress - segment).coerceIn(0f, 1f)
            val start = centerFor(route[segment])
            val end = centerFor(route[segment + 1])
            return PointF(
                start.x + (end.x - start.x) * local,
                start.y + (end.y - start.y) * local,
            )
        }

        private fun stableCircularOffset(
            style: StoneAppearance,
            pitIndex: Int,
            maxDistance: Float,
        ): PointF {
            val random = Random(style.id * 7919 + pitIndex * 977 + 53)
            val angle = random.nextFloat() * (2f * PI.toFloat())
            val distance = sqrt(random.nextFloat()) * maxDistance
            return PointF(cos(angle) * distance, sin(angle) * distance)
        }

        private fun stableRectOffset(
            style: StoneAppearance,
            containerIndex: Int,
            maxX: Float,
            maxY: Float,
        ): PointF {
            val random = Random(style.id * 7919 + containerIndex * 977 + 149)
            return PointF(
                (random.nextFloat() * 2f - 1f) * maxX,
                (random.nextFloat() * 2f - 1f) * maxY,
            )
        }

        private fun visibleCounts(animation: MoveAnimation): IntArray {
            val elapsed = animationProgress * animation.totalDuration
            val counts = animation.before.copyOf()
            val pickupElapsed = elapsed.coerceAtLeast(0f)
            if (animation.pickupDuration > 0f && pickupElapsed < animation.pickupDuration) {
                val pickup = (pickupElapsed / animation.pickupDuration).coerceIn(0f, 1f)
                counts[animation.from] =
                    (animation.before[animation.from] * (1f - pickup)).roundToInt()
                return counts
            }

            val sowingElapsed = elapsed - animation.pickupDuration
            if (sowingElapsed < animation.stagingDuration) {
                counts[animation.from] =
                    (animation.before[animation.from] - stagingRemovedCount(animation))
                        .coerceAtLeast(0)
                return counts
            }

            val placementElapsed = sowingElapsed - animation.stagingDuration
            if (placementElapsed < animation.sowingDuration - animation.stagingDuration) {
                val completed = placedCount(animation)
                counts[animation.from] =
                    (animation.before[animation.from] - animation.path.size).coerceAtLeast(0)
                repeat(completed) { counts[animation.path[it]]++ }
                return counts
            }

            // During captures and end-of-round cleanup, mirror the styles that
            // are currently visible instead of jumping straight to the final
            // engine state. A pit loses a stone as it starts moving, and the
            // destination gains it only when the transfer lands.
            val settlementCounts = IntArray(MancalaRuleEngine.BOARD_CELLS) { index ->
                animation.preSettlementStyles[index].size
            }
            val settlementElapsed = elapsed - animation.sowingDuration
            val completedTransfers = settlementPlacedCount(animation)
            repeat(completedTransfers) { transferIndex ->
                val transfer = animation.settlementTransfers[transferIndex]
                settlementCounts[transfer.from] =
                    (settlementCounts[transfer.from] - 1).coerceAtLeast(0)
                settlementCounts[transfer.to]++
            }
            if (completedTransfers < animation.settlementTransfers.size &&
                activeRouteProgress(
                    animation.settlementTransfers.map { it.route },
                    settlementElapsed,
                    animation.placementDuration,
                ) != null
            ) {
                val activeTransfer = animation.settlementTransfers[completedTransfers]
                settlementCounts[activeTransfer.from] =
                    (settlementCounts[activeTransfer.from] - 1).coerceAtLeast(0)
            }
            return if (settlementElapsed >= animation.settlementDuration) {
                animation.after.copyOf()
            } else {
                settlementCounts
            }
        }

        private fun drawMoveAnimation(canvas: Canvas) {
            val animation = moveAnimation ?: return
            val elapsed = animationElapsed(animation)
            val chipRadius = boardRect.width() * 0.022f * 2.25f

            if (elapsed - animation.pickupDuration < animation.stagingDuration) {
                val source = centerFor(animation.from)
                animation.movedStones.forEachIndexed { index, stone ->
                    if (stagingElapsed(animation) < index * animation.stagingGap) {
                        return@forEachIndexed
                    }
                    val sourceHole = holeMeasurementFor(animation.from)
                    val sourceOffset = stableCircularOffset(
                        stone,
                        animation.from,
                        (radiusFor(sourceHole) - chipRadius * 1.28f).coerceAtLeast(0f),
                    )
                    val start = PointF(source.x + sourceOffset.x, source.y + sourceOffset.y)
                    val slot = stagingSlotFor(animation, index)
                    val progress = stagingProgress(animation, index)
                    val x = start.x + (slot.x - start.x) * progress
                    val y = start.y + (slot.y - start.y) * progress
                    drawStone(
                        canvas,
                        x,
                        y,
                        stoneRadius(chipRadius, stone.color, stone.variation),
                        stone.color,
                        stoneVariation = stone.variation,
                    )
                }
                return
            }

            val placed = placedCount(animation)
            if (placed < animation.path.size) {
                val index = placed
                for (waitingIndex in (index + 1) until animation.movedStones.size) {
                    val waiting = animation.movedStones[waitingIndex]
                    val slot = stagingSlotFor(animation, waitingIndex)
                    drawStone(
                        canvas,
                        slot.x,
                        slot.y,
                        stoneRadius(chipRadius, waiting.color, waiting.variation),
                        waiting.color,
                        stoneVariation = waiting.variation,
                    )
                }
                val routeProgress = activeRouteProgress(
                    animation.sowingRoutes,
                    elapsed - animation.pickupDuration - animation.stagingDuration,
                    animation.placementDuration,
                )
                val local = routeProgress?.second ?: 0f
                val stone = animation.movedStones[index]
                val start = stagingSlotFor(animation, index)
                val destination = centerFor(animation.path[index])
                val x = start.x + (destination.x - start.x) * local
                val y = start.y + (destination.y - start.y) * local
                drawStone(
                    canvas,
                    x,
                    y,
                    stoneRadius(chipRadius, stone.color, stone.variation),
                    stone.color,
                    stoneVariation = stone.variation,
                )
                drawLandingRipple(
                    canvas,
                    start,
                    chipRadius * 1.8f,
                    1f - local,
                )
            } else if (animation.settlementTransfers.isNotEmpty() &&
                settlementPlacedCount(animation) < animation.settlementTransfers.size
            ) {
                val settlementPlaced = settlementPlacedCount(animation)
                val transfer = animation.settlementTransfers[settlementPlaced]
                val sourceOffset = stableCircularOffset(
                    transfer.stone,
                    transfer.from,
                    (radiusFor(holeMeasurementFor(transfer.from)) -
                        chipRadius * 1.28f).coerceAtLeast(0f),
                )
                val destinationOffset = stableRectOffset(
                    transfer.stone,
                    transfer.to,
                    (boardRect.width() * 0.24f - chipRadius * 1.28f).coerceAtLeast(0f),
                    (boardRect.height() * 0.045f - chipRadius * 1.28f).coerceAtLeast(0f),
                )
                val settlementRoutes = animation.settlementTransfers.map { it.route }
                val local = activeRouteProgress(
                    settlementRoutes,
                    elapsed - animation.sowingDuration,
                    animation.placementDuration,
                )?.second ?: 0f
                val route = transfer.route.ifEmpty { listOf(transfer.from, transfer.to) }
                val routePoint = pointAlongRoute(route, local)
                val start = PointF(
                    centerFor(transfer.from).x + sourceOffset.x,
                    centerFor(transfer.from).y + sourceOffset.y,
                )
                val x = routePoint.x +
                    sourceOffset.x * (1f - local) +
                    destinationOffset.x * local
                val y = routePoint.y +
                    sourceOffset.y * (1f - local) +
                    destinationOffset.y * local
                drawStone(
                    canvas,
                    x,
                    y,
                    stoneRadius(
                        chipRadius,
                        transfer.stone.color,
                        transfer.stone.variation,
                    ),
                    transfer.stone.color,
                    stoneVariation = transfer.stone.variation,
                )
                drawLandingRipple(
                    canvas,
                    start,
                    radiusFor(holeMeasurementFor(transfer.from)),
                    1f - local,
                )
            } else if (placed > 0) {
                val landingIndex = animation.path[placed - 1]
                val landing = centerFor(landingIndex)
                val settle = ((elapsed - animation.pickupDuration - animation.stagingDuration -
                    animation.path.size * animation.placementDuration) /
                    animation.settleDuration).coerceIn(0f, 1f)
                drawLandingRipple(
                    canvas,
                    landing,
                    radiusFor(holeMeasurementFor(landingIndex)),
                    1f - settle,
                )
            }
        }

        private fun drawCaptureFeedback(canvas: Canvas) {
            val feedback = captureFeedback ?: return
            val progress = captureFeedbackProgress.coerceIn(0f, 1f)
            val intro = (progress / 0.18f).coerceIn(0f, 1f)
            val outro = ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val alpha = (255f * intro * (1f - outro)).roundToInt().coerceIn(0, 255)
            if (alpha == 0) return

            val introScale = 0.72f + 0.40f * android.view.animation.OvershootInterpolator(1.25f)
                .getInterpolation(intro)
            val outroScale = 1f + 0.06f * outro
            val scale = introScale * outroScale
            val density = resources.displayMetrics.density
            val bannerWidth = min(boardRect.width() * 0.82f, 340f * density)
            val bannerHeight = min(boardRect.width() * 0.23f, 96f * density)
            val centerX = boardRect.centerX()
            val centerY = boardRect.top + boardRect.height() * 0.505f
            val rect = RectF(
                -bannerWidth / 2f,
                -bannerHeight / 2f,
                bannerWidth / 2f,
                bannerHeight / 2f,
            )

            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(scale, scale)

            captureBurstPaint.alpha = (alpha * (1f - intro)).roundToInt().coerceIn(0, 255)
            captureBurstPaint.strokeWidth = bannerHeight * 0.035f
            val burstInner = bannerWidth * 0.46f
            val burstOuter = bannerWidth * 0.60f
            for (ray in 0 until 12) {
                val angle = ray * (2f * PI.toFloat() / 12f)
                canvas.drawLine(
                    cos(angle) * burstInner,
                    sin(angle) * burstInner * 0.42f,
                    cos(angle) * burstOuter,
                    sin(angle) * burstOuter * 0.42f,
                    captureBurstPaint,
                )
            }

            capturePanelPaint.shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                Color.parseColor("#8B3C22"),
                Color.parseColor("#3D1518"),
                Shader.TileMode.CLAMP,
            )
            capturePanelPaint.alpha = alpha
            capturePanelPaint.setShadowLayer(
                bannerHeight * 0.16f,
                0f,
                bannerHeight * 0.08f,
                Color.argb((alpha * 0.75f).roundToInt(), 0, 0, 0),
            )
            canvas.drawRoundRect(rect, bannerHeight * 0.25f, bannerHeight * 0.25f, capturePanelPaint)
            capturePanelPaint.clearShadowLayer()
            capturePanelPaint.shader = null

            capturePanelStrokePaint.alpha = alpha
            capturePanelStrokePaint.color = Color.parseColor("#FFE09C")
            capturePanelStrokePaint.strokeWidth = bannerHeight * 0.025f
            canvas.drawRoundRect(
                RectF(
                    rect.left + bannerHeight * 0.045f,
                    rect.top + bannerHeight * 0.045f,
                    rect.right - bannerHeight * 0.045f,
                    rect.bottom - bannerHeight * 0.045f,
                ),
                bannerHeight * 0.21f,
                bannerHeight * 0.21f,
                capturePanelStrokePaint,
            )

            captureTitlePaint.textSize = bannerHeight * 0.30f
            captureTitlePaint.color = Color.parseColor("#FFF4C7")
            captureTitlePaint.alpha = alpha
            captureTitlePaint.setShadowLayer(
                bannerHeight * 0.055f,
                0f,
                bannerHeight * 0.035f,
                Color.argb((alpha * 0.85f).roundToInt(), 40, 8, 3),
            )
            captureTitleStrokePaint.textSize = captureTitlePaint.textSize
            captureTitleStrokePaint.color = Color.parseColor("#632019")
            captureTitleStrokePaint.alpha = alpha
            captureTitleStrokePaint.strokeWidth = bannerHeight * 0.045f
            canvas.drawText("CAPTURED!", 0f, -bannerHeight * 0.035f, captureTitleStrokePaint)
            canvas.drawText("CAPTURED!", 0f, -bannerHeight * 0.035f, captureTitlePaint)
            captureTitlePaint.clearShadowLayer()

            val store = if (feedback.mover == PieceColor.WHITE) "SOUTH" else "NORTH"
            captureSubtitlePaint.textSize = bannerHeight * 0.16f
            captureSubtitlePaint.color = Color.parseColor("#FFE09C")
            captureSubtitlePaint.alpha = alpha
            canvas.drawText(
                "+${feedback.stones} STONES  •  $store STORE",
                0f,
                bannerHeight * 0.30f,
                captureSubtitlePaint,
            )
            canvas.restore()
        }

        private fun drawLandingRipple(
            canvas: Canvas,
            center: PointF,
            pitRadius: Float,
            intensity: Float,
        ) {
            val strength = intensity.coerceIn(0f, 1f)
            ripplePaint.color = Color.argb((110f * strength).roundToInt(), 255, 237, 190)
            canvas.drawCircle(
                center.x,
                center.y,
                pitRadius * (0.45f + 0.45f * (1f - strength)),
                ripplePaint,
            )
        }

        private fun stoneRadius(radius: Float, owner: PieceColor, variation: Int): Float =
            if (owner == PieceColor.BLACK && (variation and 1) == 1) {
                radius * 0.985f
            } else {
                radius
            }

        private fun drawStone(
            canvas: Canvas,
            x: Float,
            y: Float,
            radius: Float,
            owner: PieceColor,
            elevation: Float = 0f,
            stoneVariation: Int = 0,
        ) {
            val lift = (elevation / radius.coerceAtLeast(1f)).coerceIn(0f, 3f)
            val shadowRadius = radius * (1.03f + lift * 0.08f)
            val shadowOffset = radius * (0.38f + lift * 0.13f)
            val shadowAlpha = (125f - lift * 18f).roundToInt().coerceIn(70, 125)
            stoneShadowPaint.shader = RadialGradient(
                x + radius * 0.10f,
                y + shadowOffset,
                shadowRadius * 1.08f,
                intArrayOf(
                    Color.argb(shadowAlpha, 20, 8, 3),
                    Color.argb((shadowAlpha * 0.35f).roundToInt(), 20, 8, 3),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawOval(
                RectF(
                    x - shadowRadius,
                    y + shadowOffset * 0.72f,
                    x + shadowRadius,
                    y + shadowOffset + radius * 0.22f,
                ),
                stoneShadowPaint,
            )
            stoneShadowPaint.shader = null

            stoneBitmapFor(owner, stoneVariation)?.let { bitmap ->
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(x - radius, y - radius, x + radius, y + radius),
                    bitmapPaint,
                )
                return
            }

            val colors = if (owner == PieceColor.WHITE) {
                intArrayOf(
                    Color.rgb(255, 247, 224),
                    Color.rgb(246, 202, 124),
                    Color.rgb(178, 98, 35),
                    Color.rgb(72, 27, 12),
                )
            } else {
                intArrayOf(
                    Color.rgb(225, 249, 251),
                    Color.rgb(111, 201, 212),
                    Color.rgb(37, 111, 130),
                    Color.rgb(10, 29, 40),
                )
            }
            stonePaint.shader = RadialGradient(
                x - radius * 0.36f,
                y - radius * 0.44f,
                radius * 1.20f,
                colors,
                floatArrayOf(0f, 0.22f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius, stonePaint)
            stonePaint.shader = null

            // A translucent lower wash makes the round piece read as a solid object,
            // instead of a flat radial-gradient disc.
            stoneShadePaint.shader = LinearGradient(
                x,
                y - radius,
                x,
                y + radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(68, 0, 0, 0),
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius * 0.995f, stoneShadePaint)
            stoneShadePaint.shader = null

            stoneRimPaint.strokeWidth = max(0.8f * resources.displayMetrics.density, radius * 0.035f)
            stoneRimPaint.shader = LinearGradient(
                x - radius,
                y - radius,
                x + radius,
                y + radius,
                intArrayOf(
                    Color.argb(175, 255, 249, 226),
                    Color.argb(70, 255, 241, 207),
                    Color.argb(180, 35, 15, 9),
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius * 0.955f, stoneRimPaint)
            stoneRimPaint.shader = null

            // Soft reflected light plus a tight specular point provide the glossy
            // highlight visible on real glass/stone game pieces.
            stoneGlintPaint.shader = RadialGradient(
                x - radius * 0.38f,
                y - radius * 0.46f,
                radius * 0.58f,
                intArrayOf(
                    Color.argb(135, 255, 255, 255),
                    Color.argb(44, 255, 255, 255),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawOval(
                RectF(
                    x - radius * 0.72f,
                    y - radius * 0.78f,
                    x - radius * 0.02f,
                    y - radius * 0.18f,
                ),
                stoneGlintPaint,
            )
            stoneGlintPaint.shader = null

            stoneSpecularPaint.color = Color.argb(205, 255, 255, 255)
            canvas.drawOval(
                RectF(
                    x - radius * 0.50f,
                    y - radius * 0.64f,
                    x - radius * 0.22f,
                    y - radius * 0.39f,
                ),
                stoneSpecularPaint,
            )
        }

        private fun stoneBitmapFor(owner: PieceColor, variation: Int): Bitmap? {
            // Keep each side visually distinct while using both supplied variants
            // for that side: South uses blue/white, North uses black/green.
            val paletteOffset = if (owner == PieceColor.WHITE) 0 else 2
            return stoneBitmaps[paletteOffset + (variation and 1)]
        }

        private fun isSelectable(index: Int): Boolean =
            !isLocked && state.status == GameStatus.IN_PROGRESS &&
                (!vsAI || state.currentTurn == playerColor) &&
                MancalaRuleEngine.ownsPit(state.currentTurn, index) &&
                engine.stones(state, index) > 0

        private fun holeMeasurementFor(index: Int): HoleMeasurement? =
            when {
                index in 0 until MancalaRuleEngine.PITS_PER_SIDE -> leftHoles[index]
                index in (MancalaRuleEngine.PITS_PER_SIDE + 1 until MancalaRuleEngine.NORTH_STORE) ->
                    rightHoles[MancalaRuleEngine.NORTH_STORE - index - 1]
                else -> null
            }

        private fun radiusFor(hole: HoleMeasurement?): Float =
            (hole?.radius ?: 110f) / 768f * boardRect.width()

        private fun centerFor(index: Int): PointF {
            val hole = holeMeasurementFor(index)
            if (hole != null) {
                return PointF(
                    boardRect.left + boardRect.width() * hole.x / 768f,
                    boardRect.top + boardRect.height() * hole.y / 2048f,
                )
            }
            return when (index) {
                MancalaRuleEngine.NORTH_STORE ->
                    PointF(boardRect.centerX(), boardRect.top + boardRect.height() * 0.098f)
                MancalaRuleEngine.SOUTH_STORE ->
                    PointF(boardRect.centerX(), boardRect.top + boardRect.height() * 0.897f)
                else -> PointF(boardRect.centerX(), boardRect.centerY())
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            if (state.status != GameStatus.IN_PROGRESS) {
                onGameOverTapped?.invoke()
                return true
            }
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                if (index == MancalaRuleEngine.SOUTH_STORE || index == MancalaRuleEngine.NORTH_STORE) continue
                val point = centerFor(index)
                val pitRadius = radiusFor(holeMeasurementFor(index)) * 1.16f
                val dx = event.x - point.x
                val dy = event.y - point.y
                if (dx * dx + dy * dy <= pitRadius * pitRadius) {
                    onPitTapped?.invoke(index)
                    return true
                }
            }
            return true
        }

        override fun onDetachedFromWindow() {
            highlightAnimator?.cancel()
            highlightAnimator = null
            clearCaptureFeedback()
            super.onDetachedFromWindow()
        }
    }
}