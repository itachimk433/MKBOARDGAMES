package com.mkdev.mkboardgames

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
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
import kotlinx.coroutines.*
import kotlin.math.*

class MancalaActivity : AppCompatActivity() {
    private val engine = MancalaRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var resultRecorded = false
    private val previousStates = ArrayDeque<GameState>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private data class MoveAnimation(
        val from: Int,
        val path: List<Int>,
        val before: IntArray,
        val after: IntArray,
        val color: PieceColor,
    ) {
        val pickupDuration = 170f
        val placementDuration = 88f
        val settleDuration = 250f
        val totalDuration =
            pickupDuration + path.size * placementDuration + settleDuration
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
        val undo = actionButton("Undo")
        val menu = actionButton("Menu")
        undo.setOnClickListener { undoMove() }
        menu.setOnClickListener { showMenu() }
        controls.addView(undo, LinearLayout.LayoutParams(0, 46 * dp.toInt(), 1f))
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
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) boardView.isLocked = false
        super.onPause()
    }

    override fun onDestroy() {
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
            gameState.currentTurn != playerColor
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
        if (vsAI && gameState.currentTurn != playerColor) return
        val move = engine.legalMovesFrom(gameState, Position(0, index)).firstOrNull() ?: return
        playMove(move)
    }

    private fun playMove(move: Move) {
        previousStates.add(gameState)
        gameState = engine.applyMove(gameState, move)
        if (gameState.status == GameStatus.IN_PROGRESS) {
            boardView.onMoveAnimationFinished = {
                boardView.isLocked = false
                if (vsAI && gameState.currentTurn != playerColor) triggerAI()
            }
        }
        boardView.setGameState(gameState)
        updateHud()
        if (gameState.status != GameStatus.IN_PROGRESS) {
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
        if (!vsAI || !activityResumed || gameState.status != GameStatus.IN_PROGRESS ||
            gameState.currentTurn == playerColor
        ) return
        boardView.isLocked = true
        statusView.text = "Computer is thinking…"
        val snapshot = gameState
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                MancalaAIPlayer(engine, SettingsManager.mancalaAiDepth(this@MancalaActivity)).bestMove(snapshot)
            }
            if (!activityResumed || snapshot != gameState) return@launch
            boardView.isLocked = false
            if (move != null) playMove(move)
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (::boardView.isInitialized && matchStarted && vsAI &&
            activeMancalaOverlay == null &&
            gameState.status == GameStatus.IN_PROGRESS &&
            gameState.currentTurn != playerColor
        ) triggerAI()
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
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stoneRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f * resources.displayMetrics.density
        }
        private val stoneShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 20, 8, 3)
        }
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E7C995")
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
        }
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
        private val chipOffsets = listOf(
            -0.30f to -0.30f, 0f to -0.30f, 0.30f to -0.30f,
            -0.30f to 0f, 0f to 0f, 0.30f to 0f,
            -0.30f to 0.30f, 0f to 0.30f, 0.30f to 0.30f,
            -0.14f to -0.14f, 0.14f to -0.14f, 0f to 0.16f,
        )
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

        fun setGameState(newState: GameState, animate: Boolean = true) {
            animationGeneration++
            moveAnimator?.cancel()
            moveAnimator = null
            val previousState = state
            state = newState
            val move = newState.moveHistory.lastOrNull()
            if (!animate || move == null) {
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
                moveAnimation = null
                animationProgress = 1f
                isLocked = false
                invalidate()
                onMoveAnimationFinished?.invoke()
                onMoveAnimationFinished = null
                return
            }

            val generation = animationGeneration
            moveAnimation = MoveAnimation(
                from = move.from.col,
                path = path,
                before = countsOf(previousState),
                after = countsOf(newState),
                color = previousState.currentTurn,
            )
            animationProgress = 0f
            isLocked = true
            moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = moveAnimation!!.totalDuration.toLong()
                interpolator = DecelerateInterpolator()
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
            labelPaint.textSize = boardRect.width() * 0.07f
            val top = centerFor(MancalaRuleEngine.NORTH_STORE)
            val bottom = centerFor(MancalaRuleEngine.SOUTH_STORE)
            canvas.drawText("NORTH", top.x, top.y - boardRect.height() * 0.045f, labelPaint)
            canvas.drawText("SOUTH", bottom.x, bottom.y + boardRect.height() * 0.06f, labelPaint)
        }

        private fun drawPits(canvas: Canvas) {
            val stoneSpreadRadius = boardRect.width() * 0.078f
            val chipRadius = boardRect.width() * 0.022f
            val counts = moveAnimation?.let { visibleCounts(it) } ?: countsOf(state)
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                val point = centerFor(index)
                val hole = holeMeasurementFor(index)
                val count = counts[index]
                val isStore = index == MancalaRuleEngine.SOUTH_STORE || index == MancalaRuleEngine.NORTH_STORE
                if (!isStore && isSelectable(index)) {
                    canvas.drawCircle(point.x, point.y, radiusFor(hole), highlightPaint)
                }
                if (count == 0) continue
                val owner = if (index <= MancalaRuleEngine.SOUTH_STORE) PieceColor.WHITE else PieceColor.BLACK
                if (isStore) {
                    drawStoreStones(canvas, point, count, chipRadius, owner)
                    countPaint.textSize = boardRect.width() * 0.10f
                    countPaint.color = Color.argb(235, 255, 244, 221)
                    canvas.drawText(count.toString(), point.x, point.y + countPaint.textSize * 0.35f, countPaint)
                    continue
                }
                chipOffsets.take(min(count, chipOffsets.size)).forEach { (dx, dy) ->
                    drawStone(
                        canvas,
                        point.x + dx * stoneSpreadRadius,
                        point.y + dy * stoneSpreadRadius,
                        chipRadius,
                        owner,
                    )
                }
                if (count > chipOffsets.size) {
                    countPaint.textSize = boardRect.width() * 0.055f
                    countPaint.color = Color.argb(235, 255, 244, 221)
                    canvas.drawText(count.toString(), point.x, point.y + countPaint.textSize * 0.35f, countPaint)
                }
            }
        }

        private fun drawStoreStones(
            canvas: Canvas,
            center: PointF,
            count: Int,
            radius: Float,
            owner: PieceColor,
        ) {
            val visible = min(count, 18)
            val xSpacing = boardRect.width() * 0.075f
            val ySpacing = boardRect.height() * 0.026f
            repeat(visible) { index ->
                val column = index % 6
                val row = index / 6
                drawStone(
                    canvas,
                    center.x + (column - 2.5f) * xSpacing,
                    center.y + (row - 1f) * ySpacing,
                    radius * 0.9f,
                    owner,
                )
            }
        }

        private fun visibleCounts(animation: MoveAnimation): IntArray {
            val counts = animation.before.copyOf()
            val elapsed = animationProgress * animation.totalDuration
            if (elapsed < animation.pickupDuration) {
                val pickup = (elapsed / animation.pickupDuration).coerceIn(0f, 1f)
                counts[animation.from] =
                    (animation.before[animation.from] * (1f - pickup)).roundToInt()
                return counts
            }

            counts[animation.from] = 0
            val sowingElapsed = elapsed - animation.pickupDuration
            val landed = floor(sowingElapsed / animation.placementDuration)
                .toInt()
                .coerceIn(0, animation.path.size)
            repeat(landed) { counts[animation.path[it]]++ }
            return if (sowingElapsed >= animation.path.size * animation.placementDuration) {
                animation.after.copyOf()
            } else {
                counts
            }
        }

        private fun drawMoveAnimation(canvas: Canvas) {
            val animation = moveAnimation ?: return
            val elapsed = animationProgress * animation.totalDuration
            val from = centerFor(animation.from)
            val travelRadius = boardRect.width() * 0.105f
            val chipRadius = boardRect.width() * 0.022f
            val placed = if (elapsed < animation.pickupDuration) {
                0
            } else {
                floor((elapsed - animation.pickupDuration) / animation.placementDuration)
                    .toInt()
                    .coerceIn(0, animation.path.size)
            }
            val moving = if (elapsed >= animation.pickupDuration &&
                placed < animation.path.size
            ) 1 else 0
            val held = if (elapsed < animation.pickupDuration) {
                (animation.before[animation.from] *
                    (elapsed / animation.pickupDuration).coerceIn(0f, 1f)).roundToInt()
            } else {
                (animation.path.size - placed - moving).coerceAtLeast(0)
            }
            if (held > 0) {
                drawHeldStones(canvas, from, held, chipRadius, animation.color, elapsed)
            }

            if (moving == 1) {
                val index = placed
                val start = if (index == 0) from else centerFor(animation.path[index - 1])
                val end = centerFor(animation.path[index])
                val local = ((elapsed - animation.pickupDuration) %
                    animation.placementDuration) / animation.placementDuration
                val x = start.x + (end.x - start.x) * local
                val y = start.y + (end.y - start.y) * local -
                    sin(local * PI).toFloat() * travelRadius * 1.25f
                drawStone(
                    canvas,
                    x,
                    y,
                    chipRadius * (1f + 0.13f * sin(local * PI).toFloat()),
                    animation.color,
                    elevation = travelRadius * 0.18f,
                )
                drawLandingRipple(
                    canvas,
                    start,
                    radiusFor(holeMeasurementFor(animation.from)),
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

        private fun drawHeldStones(
            canvas: Canvas,
            center: PointF,
            count: Int,
            radius: Float,
            color: PieceColor,
            elapsed: Float,
        ) {
            val lift = if (elapsed < 170f) {
                sin((elapsed / 170f).coerceIn(0f, 1f) * PI).toFloat() * radius * 2.4f
            } else {
                radius * 2.4f
            }
            val visible = min(count, chipOffsets.size)
            chipOffsets.take(visible).forEach { (dx, dy) ->
                drawStone(
                    canvas,
                    center.x + dx * radius * 1.8f,
                    center.y + dy * radius * 1.8f - lift,
                    radius * 0.92f,
                    color,
                    elevation = radius * 1.2f,
                )
            }
            if (count > visible) {
                countPaint.textSize = boardRect.width() * 0.046f
                countPaint.color = Color.argb(240, 255, 244, 221)
                canvas.drawText(
                    count.toString(),
                    center.x,
                    center.y - lift + countPaint.textSize * 0.35f,
                    countPaint,
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

        private fun drawStone(
            canvas: Canvas,
            x: Float,
            y: Float,
            radius: Float,
            owner: PieceColor,
            elevation: Float = 0f,
        ) {
            val shadowRadius = radius * (1.02f + elevation / radius * 0.10f)
            canvas.drawOval(
                RectF(
                    x - shadowRadius,
                    y + radius * 0.36f,
                    x + shadowRadius,
                    y + radius * 0.78f,
                ),
                stoneShadowPaint,
            )
            val colors = if (owner == PieceColor.WHITE) {
                intArrayOf(
                    Color.rgb(255, 227, 151),
                    Color.rgb(213, 133, 49),
                    Color.rgb(111, 50, 18),
                )
            } else {
                intArrayOf(
                    Color.rgb(196, 236, 241),
                    Color.rgb(53, 137, 153),
                    Color.rgb(19, 52, 69),
                )
            }
            stonePaint.shader = RadialGradient(
                x - radius * 0.32f,
                y - radius * 0.42f,
                radius * 1.18f,
                colors,
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radius, stonePaint)
            stonePaint.shader = null
            stoneRimPaint.color = Color.argb(155, 255, 241, 207)
            canvas.drawCircle(x, y, radius * 0.94f, stoneRimPaint)
            stonePaint.color = Color.argb(125, 255, 255, 255)
            canvas.drawOval(
                RectF(
                    x - radius * 0.48f,
                    y - radius * 0.58f,
                    x - radius * 0.05f,
                    y - radius * 0.18f,
                ),
                stonePaint,
            )
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
    }
}