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
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.connectfour.ConnectFourPiece
import com.mkdev.mkboardgames.games.connectfour.ConnectFourRuleEngine
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class ConnectFourActivity : AppCompatActivity() {
    private val engine = ConnectFourRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private val moveHistory = ArrayDeque<GameState>()
    private val redoGameStates = ArrayDeque<GameState>()
    private val redoRemovedMoves = ArrayDeque<List<GameState>>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scoreRed = 0
    private var scoreYellow = 0
    private var scoreDraws = 0
    private var resultRecorded = false
    private var interstitialAd: Any? = null

    private lateinit var hudView: HudView
    private lateinit var boardView: ConnectBoardView
    private lateinit var scoreView: ScoreView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        hudView = HudView(this)
        boardView = ConnectBoardView(this)
        scoreView = ScoreView(this)
        root.addView(hudView, LinearLayout.LayoutParams(-1, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        root.addView(scoreView, LinearLayout.LayoutParams(-1, (48 * dp).toInt()))
        AdManager.attachBanner(root)
        setContentView(root)
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
        makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "connect_four")
            boardView.applyTheme()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            @Suppress("DEPRECATION") super.onBackPressed()
            return
        }
        StyledDialogs.showChoices(this, "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
            ), 520f, "C O N N E C T · F O U R") { which, dialog ->
                dialog.dismiss()
                when (which) {
                    0 -> pauseMatchAndExit()
                    1 -> {
                        clearPausedMatch()
                        if (vsAI) SettingsManager.recordForfeit(this)
                        @Suppress("DEPRECATION") super.onBackPressed()
                    }
                }
            }
    }

    private fun pauseMatchAndExit() {
        PausedMatchStore.save(
            this,
            gameType = "CONNECT_FOUR",
            vsAI = vsAI,
            playerColor = playerColor.name,
            moves = gameState.moveHistory,
        )
        finish()
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
        val paused = PausedMatchStore.has(this, "CONNECT_FOUR")
        val options = buildList {
            if (paused) add("Resume Match")
            add("vs AI")
            add("2 Players")
            add("How to Play")
        }
        StyledDialogs.showChoices(this, "Connect Four", "Choose how to begin.",
            options.map { item ->
                when (item) {
                    "Resume Match" -> StyledDialogs.choice(item, "Continue where you left off", "Ⅱ", "#E3B86A")
                    "vs AI" -> StyledDialogs.choice(item, "Play against the computer", "♞", "#8EC7B9")
                    "2 Players" -> StyledDialogs.choice(item, "Share the board locally", "♙", "#A9B6E8")
                    else -> StyledDialogs.choice(item, "Review the essentials", "?", "#E58A7A")
                }
            }, 520f, "C O N N E C T · F O U R", onCancel = { if (!matchStarted) finish() }) { which, dialog ->
                dialog.dismiss()
                when (options[which]) {
                    "Resume Match" -> resumePausedMatch()
                    "vs AI" -> { vsAI = true; showColorPickerDialog() }
                    "2 Players" -> { vsAI = false; playerColor = PieceColor.WHITE; startGame() }
                    "How to Play" -> showRules(showModeAfter = !matchStarted)
                }
            }
    }

    private fun showColorPickerDialog() {
        StyledDialogs.showChoices(this, "Play As", "Choose your side before the first move.",
            listOf(
                StyledDialogs.choice("Red", "Moves first", "●", "#E3B86A"),
                StyledDialogs.choice("Yellow", "Moves second", "●", "#A9B6E8"),
            ), 420f, "C O N N E C T · F O U R", onCancel = { showModeDialog() }) { which, dialog ->
                dialog.dismiss()
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
    }

    private fun showRules(showModeAfter: Boolean) {
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
            "How to Play Connect Four",
            tv.text.toString(),
            "C O N N E C T · F O U R",
            onDone = { if (showModeAfter) showModeDialog() },
        )
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        matchStarted = true
        resultRecorded = false
        interstitialAd = null
        redoGameStates.clear()
        redoRemovedMoves.clear()
        AdManager.loadInterstitial(this) { interstitialAd = it }
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, "connect_four")
        if (vsAI) SettingsManager.setActiveGame(this, "connect_four")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        moveHistory.clear()
        boardView.reset(gameState)
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
                    if (vsAI && gameState.currentTurn != playerColor) triggerAI()
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
        hudView.setThinking(true)
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    val difficulty = SettingsManager.getConnectFourDifficulty(this@ConnectFourActivity)
                    val depth = SettingsManager.connectFourAiDepth(this@ConnectFourActivity)
                    val window = when (difficulty) { 0 -> 80; 2 -> 0; else -> 25 }
                    if (difficulty == 0 && Math.random() < 0.25) {
                        engine.allLegalMoves(gameState, gameState.currentTurn).randomOrNull()
                    } else {
                        AIPlayer(
                            engine,
                            maxDepth = depth,
                            timeLimitMs = SettingsManager.connectFourAiTimeLimitMs(this@ConnectFourActivity),
                            varietyWindowOverride = window
                        ).bestMove(gameState)
                    }
                } catch (_: Throwable) { null }
            }
            hudView.setThinking(false)
            if (move != null) handleMove(move, fromAI = true)
            else boardView.isLocked = false
        }
    }

    private fun updateHud() {
        val redTurn = gameState.currentTurn == PieceColor.WHITE
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            else -> "${if (redTurn) "Red" else "Yellow"}'s turn"
        }
        hudView.setInfo(label, moveHistory.isNotEmpty(), redoGameStates.isNotEmpty(), redTurn)
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
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
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("AI Difficulty")
        items.add("Main Menu")
        StyledDialogs.showChoices(this, "Menu", "Choose what to do next.",
            items.map { item ->
                when (item) {
                    "New Game" -> StyledDialogs.choice(item, if (inProgress) "Start over and forfeit" else "Begin a fresh match", "↻", "#E3B86A")
                    "How to Play" -> StyledDialogs.choice(item, "Review the essentials", "?", "#A9B6E8")
                    "AI Difficulty" -> StyledDialogs.choice(item, "Adjust the challenge", "♞", "#8EC7B9")
                    else -> StyledDialogs.choice(item, if (inProgress) "Leave this match" else "Choose another game", "⌂", "#E58A7A")
                }
            }, 520f, "C O N N E C T · F O U R") { which, menu ->
            menu.dismiss()
            when (items[which]) {
                "New Game" -> if (inProgress) {
                    StyledDialogs.showChoices(this, "Forfeit Match?", "Starting a new game counts as a forfeit.",
                        listOf(
                            StyledDialogs.choice("Forfeit & New Game", "Start over now", "↻", "#E58A7A"),
                            StyledDialogs.choice("Cancel", "Keep the current match", "↩", "#A9B6E8"),
                        ), 420f, "C O N N E C T · F O U R") { selected, confirm ->
                            confirm.dismiss()
                            if (selected == 0) {
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            }
                        }
                } else showModeDialog()
                "How to Play" -> showRules(false)
                "AI Difficulty" -> showDifficultyDialog()
                "Main Menu" -> if (inProgress) {
                    StyledDialogs.showChoices(this, "Leave Match?", "Pause to resume later, or leave to forfeit.",
                        listOf(
                            StyledDialogs.choice("Pause & Exit", "Save and resume later", "Ⅱ", "#E3B86A"),
                            StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                            StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
                        ), 520f, "C O N N E C T · F O U R") { selected, leave ->
                            leave.dismiss()
                            when (selected) {
                                0 -> pauseMatchAndExit()
                                1 -> { clearPausedMatch(); if (vsAI) SettingsManager.recordForfeit(this); finish() }
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
        val labels = arrayOf("Easy", "Medium", "Hard")
        val current = SettingsManager.getConnectFourDifficulty(this)
        StyledDialogs.showChoices(this, "AI Difficulty", "Adjust the challenge.",
            labels.mapIndexed { index, label ->
                StyledDialogs.choice(label, if (index == current) "Current setting" else "Computer strength", listOf("I", "II", "III")[index], listOf("#8EC7B9", "#E3B86A", "#E58A7A")[index])
            }, 520f, "C O N N E C T · F O U R") { which, dialog ->
                dialog.dismiss()
                val changed = which != current
                SettingsManager.setConnectFourDifficulty(this, which)
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    StyledDialogs.showChoices(this, "Restart Match?", "Difficulty changed. Restart now.",
                        listOf(
                            StyledDialogs.choice("Restart", "Start with the new difficulty", "↻", "#E3B86A"),
                            StyledDialogs.choice("Keep Playing", "Leave the current match unchanged", "↩", "#A9B6E8"),
                        ), 420f, "C O N N E C T · F O U R") { restart, restartDialog ->
                            restartDialog.dismiss()
                            if (restart == 0) startGame()
                        }
                }
            }
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val message = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win!" else "Red wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win!" else "Yellow wins!"
            GameStatus.DRAW -> "It's a draw!"
            else -> return
        }
        val result = when (gameState.status) {
            GameStatus.WHITE_WINS -> "Red wins"
            GameStatus.BLACK_WINS -> "Yellow wins"
            else -> "Draw"
        }
        StyledDialogs.showChoices(this, "Game Over", message,
            listOf(
                StyledDialogs.choice("Play Again", "Start a fresh game", "↻", "#E3B86A"),
                StyledDialogs.choice("Main Menu", "Choose another match", "⌂", "#E58A7A"),
                StyledDialogs.choice("Watch Replay", "Review the moves", "▶", "#A9B6E8"),
            ), 520f, "C O N N E C T · F O U R") { which, dialog ->
                dialog.dismiss()
                when (which) {
                    0 -> startGame()
                    1 -> finish()
                    2 -> startActivity(Intent(this, ReplayActivity::class.java).apply {
                        putExtra(ReplayActivity.EXTRA_GAME_TYPE, "CONNECTFOUR")
                        putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(gameState.moveHistory))
                        putExtra(ReplayActivity.EXTRA_RESULT, result)
                    })
                }
            }
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
        private var boardColor = Color.parseColor("#24527A")
        private var accent = Color.parseColor("#7FC8F8")
        private val bgP = Paint().apply { color = Color.parseColor("#121212") }
        private val boardP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val holeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#101820") }
        private val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        private val yellowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
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
            val pad = 20f * dp
            cellSize = minOf((w - pad * 2) / ConnectFourRuleEngine.COLUMNS, (h - pad * 2) / ConnectFourRuleEngine.ROWS)
            boardLeft = (w - cellSize * ConnectFourRuleEngine.COLUMNS) / 2f
            boardTop = (h - cellSize * ConnectFourRuleEngine.ROWS) / 2f
            winP.strokeWidth = cellSize * 0.065f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                if (state.status != GameStatus.IN_PROGRESS) {
                    onGameOverTapped?.invoke()
                    return true
                }
                if (!isLocked && cellSize > 0f) {
                    val col = ((event.x - boardLeft) / cellSize).toInt()
                    if (col in 0 until ConnectFourRuleEngine.COLUMNS && engine.landingRow(state, col) != null) {
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

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            val right = boardLeft + ConnectFourRuleEngine.COLUMNS * cellSize
            val bottom = boardTop + ConnectFourRuleEngine.ROWS * cellSize
            val fallingRow = fallingIndex?.div(ConnectFourRuleEngine.COLUMNS)
            val fallingCol = fallingIndex?.rem(ConnectFourRuleEngine.COLUMNS)
            val fallingX = fallingCol?.let { boardLeft + it * cellSize + cellSize / 2f }
            val fallingTargetY = fallingRow?.let { boardTop + it * cellSize + cellSize / 2f }
            val fallingStartY = boardTop - cellSize * 0.85f
            val fallingY = fallingTargetY?.let {
                fallingStartY + (it - fallingStartY) * fallingProgress
            }
            val fallingRadius = cellSize * 0.31f

            // Draw the part above the board first. Once it reaches the board,
            // the board face is drawn over it and only the circular openings
            // reveal the disc below, so it can never paint over the frame.
            if (fallingX != null && fallingY != null && fallingColor != null && fallingY < boardTop) {
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
            }

            boardP.color = boardColor
            canvas.drawRoundRect(boardLeft, boardTop, right, bottom, cellSize * 0.14f, cellSize * 0.14f, boardP)
            val holePath = Path()
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val cx = boardLeft + col * cellSize + cellSize / 2f
                val cy = boardTop + row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.36f, holeP)
                holePath.addCircle(cx, cy, cellSize * 0.36f, Path.Direction.CW)
            }

            if (fallingX != null && fallingY != null && fallingColor != null && fallingY >= boardTop) {
                canvas.save()
                canvas.clipPath(holePath)
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
                canvas.restore()
            }
            lastMove?.let {
                val cx = boardLeft + it.col * cellSize + cellSize / 2f
                val cy = boardTop + it.row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.43f, highlightP)
            }
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val piece = state.get(row, col) as? ConnectFourPiece ?: continue
                val idx = row * ConnectFourRuleEngine.COLUMNS + col
                if (idx == fallingIndex) continue
                val cx = boardLeft + col * cellSize + cellSize / 2f
                val cy = boardTop + row * cellSize + cellSize / 2f
                drawDisc(canvas, cx, cy, cellSize * 0.31f, piece.color)
            }
            winLine?.takeIf { it.size >= 2 }?.let {
                winP.color = Color.argb(230, 255, 255, 255)
                val first = it.first(); val last = it.last()
                canvas.drawLine(
                    boardLeft + (first % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    boardTop + (first / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    boardLeft + (last % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    boardTop + (last / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    winP
                )
            }
        }

        private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: PieceColor) {
            canvas.drawCircle(cx, cy, radius, if (color == PieceColor.WHITE) redP else yellowP)
            canvas.drawCircle(
                cx - radius * 0.22f,
                cy - radius * 0.25f,
                radius * 0.15f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(70, 255, 255, 255) }
            )
        }
    }

    inner class HudView(ctx: Context) : View(ctx) {
        private var label = "Red's turn"; private var canUndo = false; private var canRedo = false
        private var thinking = false; private var redTurn = true
        private val dp = resources.displayMetrics.density; private val sp = resources.displayMetrics.scaledDensity
        private val bgP = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 14f * sp.coerceAtMost(3f) }
        private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER; textSize = 10f * sp.coerceAtMost(3f) }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER; textSize = 10f * sp.coerceAtMost(3f) }
        private val dimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER; textSize = 10f * sp.coerceAtMost(3f) }
        private val backRect = RectF(); private val undoRect = RectF(); private val redoRect = RectF(); private val menuRect = RectF()

        fun setInfo(value: String, undo: Boolean, redo: Boolean, red: Boolean) { label = value; canUndo = undo; canRedo = redo; redTurn = red; invalidate() }
        fun setThinking(value: Boolean) { thinking = value; invalidate() }
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 42f * dp; val bh = 26f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp, by, 6f * dp + bw, by + bh)
            undoRect.set(w - bw * 3.3f, by, w - bw * 2.2f, by + bh)
            redoRect.set(w - bw * 2.15f, by, w - bw * 1.1f, by + bh)
            menuRect.set(w - bw * 1.05f, by, w - 4f * dp, by + bh)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) when {
                backRect.contains(event.x, event.y) -> onBackPressed()
                undoRect.contains(event.x, event.y) -> onUndoClicked()
                redoRect.contains(event.x, event.y) -> onRedoClicked()
                menuRect.contains(event.x, event.y) -> onMenuClicked()
            }
            return true
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            canvas.drawRect(0f, height - dp, width.toFloat(), height.toFloat(), divP)
            val rr = 5f * dp
            listOf(backRect, undoRect, redoRect, menuRect).forEach { canvas.drawRoundRect(it, rr, rr, btnBgP) }
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnP.textSize * .36f, btnP)
            canvas.drawText("Undo", undoRect.centerX(), undoRect.centerY() + btnP.textSize * .36f, if (canUndo) btnP else dimP)
            canvas.drawText("Redo", redoRect.centerX(), redoRect.centerY() + btnP.textSize * .36f, if (canRedo) btnP else dimP)
            canvas.drawText("Menu", menuRect.centerX(), menuRect.centerY() + btnP.textSize * .36f, btnP)
            txtP.color = if (redTurn) Color.parseColor("#EF5350") else Color.parseColor("#FFD54F")
            val cx = width / 2f
            canvas.drawText(label, cx, height / 2f - txtP.textSize * .15f, txtP)
            if (thinking) canvas.drawText("Thinking…", cx, height / 2f + subP.textSize * 1.1f, subP)
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