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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.mancala.MancalaAIPlayer
import com.mkdev.mkboardgames.games.mancala.MancalaRuleEngine
import com.mkdev.mkboardgames.ui.MancalaChoiceOverlayView
import com.mkdev.mkboardgames.ui.MancalaGameOverView
import com.mkdev.mkboardgames.ui.MancalaHomeView
import com.mkdev.mkboardgames.ui.MancalaRulesView
import com.mkdev.mkboardgames.ui.MancalaWoodButton
import com.mkdev.mkboardgames.ui.AutoplayButtonView
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
    ) {
        // Each stone leaves its original position without a pickup-stack phase.
        val pickupDuration = 0f
        val placementDuration = 320f
        val settleDuration = 600f
        val sowingDuration = sowingRoutes.fold(0f) { total, route ->
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
    private lateinit var homeView: MancalaHomeView
    private lateinit var boardView: MancalaBoardView
    private lateinit var statusView: TextView
    private lateinit var autoplayButton: AutoplayButtonView
    private var activeMancalaOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        val dp = resources.displayMetrics.density

        val gameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#120D0B"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12 * dp.toInt(), 5 * dp.toInt(), 12 * dp.toInt(), 3 * dp.toInt())
        }
        val title = TextView(this).apply {
            text = "MANCALA"
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
        header.addView(title, LinearLayout.LayoutParams(-1, 30 * dp.toInt()))
        header.addView(statusView, LinearLayout.LayoutParams(-1, 24 * dp.toInt()))

        boardView = MancalaBoardView(this)
        boardView.onPitTapped = { handlePitTap(it) }
        boardView.onGameOverTapped = { showResultDialog() }

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(8 * dp.toInt(), 3 * dp.toInt(), 8 * dp.toInt(), 5 * dp.toInt())
        }
        autoplayButton = AutoplayButtonView(this, circularStyle = false)
        autoplayButton.onAutoplayChanged = { enabled ->
            if (vsAI) {
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
        val menu = actionButton("Menu")
        menu.setOnClickListener { showMenu() }
        controls.addView(
            autoplayButton,
            LinearLayout.LayoutParams(0, 46 * dp.toInt(), 1f),
        )
        controls.addView(menu, LinearLayout.LayoutParams(0, 46 * dp.toInt(), 1f))

        gameLayout.addView(header, LinearLayout.LayoutParams(-1, 58 * dp.toInt()))
        gameLayout.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        gameLayout.addView(controls, LinearLayout.LayoutParams(-1, 56 * dp.toInt()))
        gameRoot = gameLayout

        screenRoot = FrameLayout(this)
        homeView = MancalaHomeView(this).apply {
            onPlay = { showModeDialog() }
            onHowToPlay = { showRules(showModeAfter = false) }
            onMore = { showHomeMenu() }
        }
        gameLayout.visibility = View.GONE
        screenRoot.addView(
            gameLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        screenRoot.addView(
            homeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(screenRoot)
        SoundPlayer.init(this)
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.postDelayed({ makeFullscreen() }, 200)
            }
        }
    }

    private fun actionButton(label: String) = MancalaWoodButton(this, label).apply {
        style = MancalaWoodButton.Style.GOLD
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
        super.onPause()
    }

    override fun onDestroy() {
        if (::boardView.isInitialized) stopAutomatedGameplay()
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeMancalaOverlay != null) {
            cancelMancalaOverlay()
            return
        }
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            @Suppress("DEPRECATION") super.onBackPressed()
        } else {
            showMenu()
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
        val paused = PausedMatchStore.has(this, "MANCALA")
        val options = buildList {
            if (paused) add("Resume Match")
            add("vs AI")
            add("2 Players")
            add("How to Play")
        }
        showChoiceOverlay(
            "Start Mancala",
            "Choose how to begin.",
            options,
            onCancel = { if (matchStarted) showBoardAfterDialog() else showHome() },
        ) { which ->
            when (options[which]) {
                "Resume Match" -> resumePausedMatch()
                "vs AI" -> {
                    vsAI = true
                    showColorPickerDialog()
                }
                "2 Players" -> {
                    vsAI = false
                    playerColor = PieceColor.WHITE
                    startGame()
                }
                "How to Play" -> showRules(showModeAfter = !matchStarted)
            }
        }
    }

    private fun showColorPickerDialog() {
        showChoiceOverlay(
            "Choose your side",
            "South moves first. Pick the side you control.",
            listOf("South", "North"),
            onCancel = { showModeDialog() },
        ) { which ->
            playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun showChoiceOverlay(
        title: String,
        subtitle: String,
        options: List<String>,
        onCancel: () -> Unit,
        onChoice: (Int) -> Unit,
    ) {
        val overlay = MancalaChoiceOverlayView(this, title, subtitle, options)
        overlay.onChoice = { index ->
            dismissMancalaOverlay()
            onChoice(index)
        }
        showMancalaOverlay(overlay, onCancel)
    }

    private fun showHomeMenu() {
        showChoiceOverlay(
            "Mancala",
            "Choose an option.",
            listOf("New Game", "How to Play", "AI Difficulty", "Back"),
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

    private fun showMenu() {
        stopAutomatedGameplay()
        boardView.isLocked = true
        showChoiceOverlay(
            "Game Menu",
            "What would you like to do?",
            listOf("New Game / Restart", "How To Play", "AI Difficulty", "Back", "Home"),
            onCancel = { showBoardAfterDialog() },
        ) { which ->
            when (which) {
                0 -> showRestartConfirmation()
                1 -> showRules(false)
                2 -> showDifficultyMenu(returnToHome = false)
                3 -> showBoardAfterDialog()
                else -> showHomeConfirmation()
            }
        }
    }

    private fun showRestartConfirmation() {
        showChoiceOverlay(
            "Restart this game?",
            "Your current progress will be lost.",
            listOf("Restart", "Cancel"),
            onCancel = { showMenu() },
        ) { which ->
            if (which == 0) startGame() else showMenu()
        }
    }

    private fun showHomeConfirmation() {
        showChoiceOverlay(
            "Go to home screen?",
            "Your current match will be saved so you can resume it later.",
            listOf("Go Home", "Stay in Game"),
            onCancel = { showMenu() },
        ) { which ->
            if (which == 0) {
                savePausedMatch()
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
            "Easy${if (current == 0) "  ✓" else ""}",
            "Medium${if (current == 1) "  ✓" else ""}",
            "Hard${if (current == 2) "  ✓" else ""}",
            "Back",
        )
        showChoiceOverlay(
            "AI Difficulty",
            "Choose the computer’s strength.",
            options,
            onCancel = { if (returnToHome) showHome() else showMenu() },
        ) { which ->
            if (which < 3) {
                if (!returnToHome && which != current) {
                    showDifficultyConfirmation(which)
                } else {
                    SettingsManager.setMancalaDifficulty(this, which)
                    if (returnToHome) showHome() else showMenu()
                }
            } else if (returnToHome) {
                showHome()
            } else {
                showMenu()
            }
        }
    }

    private fun showDifficultyConfirmation(level: Int) {
        showChoiceOverlay(
            "Change AI difficulty?",
            "Changing difficulty will start a new game.",
            listOf("Change & Restart", "Cancel"),
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
        dismissMancalaOverlay()
        if (::gameRoot.isInitialized) gameRoot.visibility = View.GONE
        if (::homeView.isInitialized) homeView.visibility = View.VISIBLE
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        matchStarted = true
        resultRecorded = false
        autoplayEnabled = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        autoplayButton.visibility = if (vsAI) View.VISIBLE else View.GONE
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

    private fun playMove(move: Move) {
        previousStates.add(gameState)
        gameState = engine.applyMove(gameState, move)
        if (gameState.status == GameStatus.IN_PROGRESS) {
            boardView.onMoveAnimationFinished = {
                boardView.isLocked = false
                if (vsAI && aiControlsCurrentTurn()) triggerAI()
            }
        }
        boardView.setGameState(gameState)
        updateHud()
        if (gameState.status != GameStatus.IN_PROGRESS) {
            autoplayEnabled = false
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
        statusView.text = "Computer is thinking…"
        val snapshot = gameState
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                MancalaAIPlayer(engine, SettingsManager.mancalaAiDepth(this@MancalaActivity)).bestMove(snapshot)
            }
            val autoplayStillControlsTurn =
                snapshot.currentTurn != playerColor || autoplayEnabled
            if (!activityResumed || snapshot != gameState || !autoplayStillControlsTurn) {
                boardView.isLocked = false
                updateHud()
                return@launch
            }
            boardView.isLocked = false
            if (move != null) playMove(move)
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
        vsAI && (gameState.currentTurn != playerColor || autoplayEnabled)

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) {
            boardView.isLocked = false
            boardView.onMoveAnimationFinished = null
        }
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
        val message = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win!" else "South wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win!" else "North wins!"
            GameStatus.DRAW -> "It's a draw!"
            else -> return
        }
        val overlay = MancalaGameOverView(this, message)
        overlay.onClose = { onBackPressed() }
        overlay.onChoice = { which ->
            dismissMancalaOverlay()
            if (which == 0) {
                startGame()
            } else {
                clearPausedMatch()
                finish()
            }
        }
        showMancalaOverlay(overlay) { showBoardAfterDialog() }
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
        if (::gameRoot.isInitialized) gameRoot.visibility = View.VISIBLE
        if (::homeView.isInitialized) homeView.visibility = View.GONE
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun updateHud() {
        val south = engine.stones(gameState, MancalaRuleEngine.SOUTH_STORE)
        val north = engine.stones(gameState, MancalaRuleEngine.NORTH_STORE)
        val turn = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "Computer turn"
            gameState.currentTurn == PieceColor.WHITE -> "South's turn"
            else -> "North's turn"
        }
        statusView.text = "$turn   •   South $south  ·  North $north"
    }

    inner class MancalaBoardView(context: Context) : View(context) {
        var isLocked = false
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
            val sowingRoutes = path.mapIndexed { pathIndex, destination ->
                engine.pathBetween(
                    from = move.from.col,
                    destination = destination,
                    mover = previousState.currentTurn,
                    minimumSteps = pathIndex + 1,
                ).ifEmpty {
                    // This should not occur for a legal sowing path, but keep
                    // the animation bounded if a future rules variant adds a
                    // destination that is not on the standard ring.
                    listOf(move.from.col, destination)
                }
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
            )
            animationProgress = 0f
            isLocked = true
            moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = moveAnimation!!.totalDuration.toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (generation != animationGeneration) return
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
                            route = engine.forwardPath(
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
                            route = engine.forwardPath(
                                it,
                                MancalaRuleEngine.SOUTH_STORE,
                                PieceColor.WHITE,
                            ),
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
                            route = engine.forwardPath(
                                it,
                                MancalaRuleEngine.NORTH_STORE,
                                PieceColor.BLACK,
                            ),
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
            drawLabels(canvas)
            drawPits(canvas)
            drawMoveAnimation(canvas)
        }

        private fun drawFallbackBoard(canvas: Canvas) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6F351D") }
            canvas.drawRoundRect(boardRect, boardRect.width() * 0.15f, boardRect.width() * 0.15f, paint)
        }

        private fun drawLabels(canvas: Canvas) {
            val counts = moveAnimation?.let { visibleCounts(it) } ?: countsOf(state)
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
                        visibleStyles += animation.beforeStyles[index]
                            .drop((placed + 1).coerceAtMost(animation.beforeStyles[index].size))
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
            return completedRouteCount(
                animation.sowingRoutes,
                animationElapsed(animation) - animation.pickupDuration,
                animation.placementDuration,
            )
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
            val counts = animation.before.copyOf()
            val elapsed = animationProgress * animation.totalDuration
            if (animation.pickupDuration > 0f && elapsed < animation.pickupDuration) {
                val pickup = (elapsed / animation.pickupDuration).coerceIn(0f, 1f)
                counts[animation.from] =
                    (animation.before[animation.from] * (1f - pickup)).roundToInt()
                return counts
            }

            counts[animation.from] = 0
            val sowingElapsed = elapsed - animation.pickupDuration
            val completed = placedCount(animation)
            repeat(completed) { counts[animation.path[it]]++ }
            return if (sowingElapsed >= animation.sowingDuration) {
                animation.after.copyOf()
            } else {
                counts
            }
        }

        private fun drawMoveAnimation(canvas: Canvas) {
            val animation = moveAnimation ?: return
            val elapsed = animationElapsed(animation)
            val from = centerFor(animation.from)
            val chipRadius = boardRect.width() * 0.022f * 2.25f
            val placed = placedCount(animation)
            if (placed < animation.path.size) {
                val index = placed
                val route = animation.sowingRoutes[index]
                val sourceHole = holeMeasurementFor(animation.from)
                val sourceOffset = stableCircularOffset(
                    animation.movedStones[index],
                    animation.from,
                    (radiusFor(sourceHole) - chipRadius * 1.28f).coerceAtLeast(0f),
                )
                val start = PointF(from.x + sourceOffset.x, from.y + sourceOffset.y)
                val routeProgress = activeRouteProgress(
                    animation.sowingRoutes,
                    elapsed - animation.pickupDuration,
                    animation.placementDuration,
                )
                val local = routeProgress?.second ?: 0f
                val routePoint = pointAlongRoute(route, local)
                val stone = animation.movedStones[index]
                val x = routePoint.x + sourceOffset.x * (1f - local)
                val y = routePoint.y + sourceOffset.y * (1f - local)
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
                    radiusFor(holeMeasurementFor(animation.from)),
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
                val settle = ((elapsed - animation.pickupDuration -
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
            super.onDetachedFromWindow()
        }
    }
}