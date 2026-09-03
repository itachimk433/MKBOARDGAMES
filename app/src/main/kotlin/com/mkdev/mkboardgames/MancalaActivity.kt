package com.mkdev.mkboardgames

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.Button
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
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*
import kotlin.math.min

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

    private lateinit var gameRoot: View
    private lateinit var boardView: MancalaBoardView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
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

        root.addView(header, LinearLayout.LayoutParams(-1, 58 * dp.toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        root.addView(controls, LinearLayout.LayoutParams(-1, 56 * dp.toInt()))
        gameRoot = root
        setContentView(root)
        hideBoardWhileDialogIsOpen()
        showModeDialog()

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.postDelayed({ makeFullscreen() }, 200)
            }
        }
    }

    private fun actionButton(label: String) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#E7C995"))
        setAllCaps(false)
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
        hideBoardWhileDialogIsOpen()
        val paused = PausedMatchStore.has(this, "MANCALA")
        val options = buildList {
            if (paused) add("Resume Match")
            add("vs AI")
            add("2 Players")
            add("How to Play")
        }
        StyledDialogs.showChoices(
            this,
            "Mancala",
            "Choose how to begin.",
            options.map { item ->
                when (item) {
                    "Resume Match" -> StyledDialogs.choice(item, "Continue where you left off", "Ⅱ", "#E3B86A")
                    "vs AI" -> StyledDialogs.choice(item, "Play against the computer", "", "#8EC7B9")
                    "2 Players" -> StyledDialogs.choice(item, "Share the wooden board", "", "#A9B6E8")
                    else -> StyledDialogs.choice(item, "Review the essentials", "?", "#E58A7A")
                }
            },
            520f,
            "M A N C A L A",
            onCancel = { if (matchStarted) showBoardAfterDialog() else finish() },
        ) { which, dialog ->
            dialog.dismiss()
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
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(
            this,
            "Choose your side",
            "South moves first. Pick the side you control.",
            listOf(
                StyledDialogs.choice("South", "Start the opening move", "", "#E7C995"),
                StyledDialogs.choice("North", "Let the computer move first", "", "#79B4D8"),
            ),
            420f,
            "M A N C A L A",
            onCancel = { showModeDialog() },
        ) { which, dialog ->
            dialog.dismiss()
            playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
            startGame()
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        val rules = """
            MANCALA — Kalah rules

            Setup
            Each player owns six pits and the store at their end of the board. Begin with four stones in every pit. South moves first.

            Sowing
            Choose a pit on your side. Pick up every stone and place them one at a time into the following pits, moving around the board. Skip your opponent's store.

            Extra turns
            If your last stone lands in your own store, take another turn.

            Captures
            If your last stone lands in an empty pit on your side, capture that stone and all stones in the directly opposite pit. Put them in your store.

            Ending the game
            When one side has no stones left in its six pits, the other side's remaining stones move to its store. The higher store total wins.
        """.trimIndent()
        StyledDialogs.showRules(
            this,
            "Mancala",
            rules,
            "M A N C A L A",
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() },
        )
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        matchStarted = true
        resultRecorded = false
        previousStates.clear()
        PausedMatchStore.clear(this, "MANCALA")
        SettingsManager.activateGameTheme(this, "mancala")
        if (vsAI) SettingsManager.setActiveGame(this, "mancala")
        gameState = engine.initialState()
        boardView.setGameState(gameState)
        showBoardAfterDialog(resumeAi = false)

        restoring?.moves?.forEach { move ->
            previousStates.addLast(gameState)
            gameState = engine.applyMove(gameState, move)
        }
        if (restoring != null) {
            boardView.setGameState(gameState)
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
        previousStates.addLast(gameState)
        gameState = engine.applyMove(gameState, move)
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
        } else if (vsAI && gameState.currentTurn != playerColor) {
            triggerAI()
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
                MancalaAIPlayer(engine).bestMove(snapshot)
            }
            if (!activityResumed || snapshot != gameState) return@launch
            boardView.isLocked = false
            if (move != null) playMove(move)
        }
    }

    private fun resumeComputerTurnIfNeeded() {
        if (::boardView.isInitialized && matchStarted && vsAI &&
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
        boardView.isLocked = false
        boardView.setGameState(gameState)
        updateHud()
    }

    private fun showMenu() {
        boardView.isLocked = true
        hideBoardWhileDialogIsOpen()
        val items = listOf("New Game", "How to Play", "Main Menu")
        StyledDialogs.showChoices(
            this,
            "Menu",
            "Choose what to do next.",
            items.map {
                when (it) {
                    "New Game" -> StyledDialogs.choice(it, "Start a fresh match", "↻", "#E3B86A")
                    "How to Play" -> StyledDialogs.choice(it, "Review the essentials", "?", "#A9B6E8")
                    else -> StyledDialogs.choice(it, "Pause and return to the game list", "Ⅱ", "#E58A7A")
                }
            },
            420f,
            "M A N C A L A",
            onCancel = { showBoardAfterDialog() },
        ) { which, dialog ->
            dialog.dismiss()
            when (items[which]) {
                "New Game" -> showModeDialog()
                "How to Play" -> showRules(false)
                "Main Menu" -> {
                    if (gameState.status == GameStatus.IN_PROGRESS) pauseMatchAndExit() else {
                        clearPausedMatch()
                        finish()
                    }
                }
            }
        }
    }

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        hideBoardWhileDialogIsOpen()
        val message = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win!" else "South wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win!" else "North wins!"
            GameStatus.DRAW -> "It's a draw!"
            else -> return
        }
        StyledDialogs.showChoices(
            this,
            "Game Over",
            message,
            listOf(
                StyledDialogs.choice("Play Again", "Start a fresh match", "↻", "#E3B86A"),
                StyledDialogs.choice("Main Menu", "Choose another match", "⌂", "#E58A7A"),
            ),
            420f,
            "M A N C A L A",
            onCancel = { showBoardAfterDialog() },
            fullScreen = false,
        ) { which, dialog ->
            dialog.dismiss()
            if (which == 0) startGame() else {
                clearPausedMatch()
                finish()
            }
        }
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

    private fun pauseMatchAndExit() {
        if (gameState.moveHistory.isNotEmpty() && gameState.status == GameStatus.IN_PROGRESS) {
            PausedMatchStore.save(
                this,
                "MANCALA",
                vsAI,
                playerColor.name,
                gameState.moveHistory,
            )
        }
        finish()
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, "MANCALA")

    private fun hideBoardWhileDialogIsOpen() {
        if (::gameRoot.isInitialized) gameRoot.visibility = View.INVISIBLE
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        if (::gameRoot.isInitialized) gameRoot.visibility = View.VISIBLE
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

        private var state = engine.initialState()
        private var boardRect = RectF()
        private val boardBitmap = try {
            context.assets.open("mancala_board.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

        fun setGameState(newState: GameState) {
            state = newState
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#120D0B"))
            val aspect = 724f / 2172f
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
            val pitRadius = boardRect.width() * 0.105f
            val chipRadius = boardRect.width() * 0.018f
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                val point = centerFor(index)
                val count = engine.stones(state, index)
                val isStore = index == MancalaRuleEngine.SOUTH_STORE || index == MancalaRuleEngine.NORTH_STORE
                if (!isStore && isSelectable(index)) {
                    canvas.drawCircle(point.x, point.y, pitRadius, highlightPaint)
                }
                if (count == 0) continue
                val owner = if (index <= MancalaRuleEngine.SOUTH_STORE) PieceColor.WHITE else PieceColor.BLACK
                stonePaint.color = if (owner == PieceColor.WHITE) {
                    Color.parseColor("#E7C995")
                } else {
                    Color.parseColor("#79B4D8")
                }
                if (isStore) {
                    countPaint.textSize = boardRect.width() * 0.10f
                    canvas.drawText(count.toString(), point.x, point.y + countPaint.textSize * 0.35f, countPaint)
                    continue
                }
                chipOffsets.take(min(count, chipOffsets.size)).forEach { (dx, dy) ->
                    canvas.drawCircle(
                        point.x + dx * pitRadius,
                        point.y + dy * pitRadius,
                        chipRadius,
                        stonePaint,
                    )
                }
                if (count > chipOffsets.size) {
                    countPaint.textSize = boardRect.width() * 0.055f
                    canvas.drawText(count.toString(), point.x, point.y + countPaint.textSize * 0.35f, countPaint)
                }
            }
        }

        private fun isSelectable(index: Int): Boolean =
            !isLocked && state.status == GameStatus.IN_PROGRESS &&
                (!vsAI || state.currentTurn == playerColor) &&
                MancalaRuleEngine.ownsPit(state.currentTurn, index) &&
                engine.stones(state, index) > 0

        private fun centerFor(index: Int): PointF {
            val yStart = boardRect.top + boardRect.height() * 0.245f
            val yStep = boardRect.height() * 0.105f
            return when {
                index == MancalaRuleEngine.NORTH_STORE ->
                    PointF(boardRect.centerX(), boardRect.top + boardRect.height() * 0.125f)
                index == MancalaRuleEngine.SOUTH_STORE ->
                    PointF(boardRect.centerX(), boardRect.top + boardRect.height() * 0.875f)
                index in 0 until MancalaRuleEngine.PITS_PER_SIDE ->
                    PointF(boardRect.left + boardRect.width() * 0.31f, yStart + index * yStep)
                else -> {
                    val row = 12 - index
                    PointF(boardRect.left + boardRect.width() * 0.69f, yStart + row * yStep)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            if (state.status != GameStatus.IN_PROGRESS) {
                onGameOverTapped?.invoke()
                return true
            }
            val pitRadius = boardRect.width() * 0.14f
            for (index in 0 until MancalaRuleEngine.BOARD_CELLS) {
                if (index == MancalaRuleEngine.SOUTH_STORE || index == MancalaRuleEngine.NORTH_STORE) continue
                val point = centerFor(index)
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