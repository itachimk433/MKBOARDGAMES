package com.mkdev.nexboard

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.nexboard.engine.*
import com.mkdev.nexboard.games.connectfour.ConnectFourPiece
import com.mkdev.nexboard.games.connectfour.ConnectFourRuleEngine
import kotlinx.coroutines.*

class ConnectFourActivity : AppCompatActivity() {
    private val engine = ConnectFourRuleEngine()
    private var gameState = engine.initialState()
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
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
        if (gameState.status != GameStatus.IN_PROGRESS || moveHistory.isEmpty()) {
            @Suppress("DEPRECATION") super.onBackPressed()
            return
        }
        AlertDialog.Builder(this).setTitle("Leave Match?")
            .setMessage("Leaving counts as a forfeit.")
            .setPositiveButton("Leave") { _, _ ->
                if (vsAI) SettingsManager.recordForfeit(this)
                @Suppress("DEPRECATION") super.onBackPressed()
            }
            .setNegativeButton("Keep Playing", null).show()
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
        AlertDialog.Builder(this).setTitle("Connect Four")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> { vsAI = true; showColorPickerDialog() }
                    1 -> { vsAI = false; playerColor = PieceColor.WHITE; startGame() }
                    2 -> showRules(showModeAfter = moveHistory.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moveHistory.isEmpty()) finish() }
            .show()
    }

    private fun showColorPickerDialog() {
        AlertDialog.Builder(this).setTitle("Play as")
            .setItems(arrayOf("Red (moves first)", "Yellow (moves second)")) { _, which ->
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
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
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(tv)
        }
        AlertDialog.Builder(this).setTitle("How to Play Connect Four")
            .setView(scroll)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun startGame() {
        resultRecorded = false
        interstitialAd = null
        redoGameStates.clear()
        redoRemovedMoves.clear()
        AdManager.loadInterstitial(this) { interstitialAd = it }
        SettingsManager.activateGameTheme(this, "connect_four")
        if (vsAI) SettingsManager.setActiveGame(this, "connect_four")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        moveHistory.clear()
        boardView.reset(gameState)
        boardView.onGameOverTapped = { showResultDialog() }
        scoreView.update(scoreRed, scoreDraws, scoreYellow)
        updateHud()
        if (vsAI && gameState.currentTurn != playerColor) triggerAI()
    }

    private fun handleMove(move: Move) {
        if (boardView.isLocked) return
        redoGameStates.clear()
        redoRemovedMoves.clear()
        moveHistory.add(gameState)
        gameState = engine.applyMove(gameState, move)
        val appliedMove = gameState.lastMove
        boardView.setGameState(gameState, appliedMove?.to)
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
        if (vsAI && gameState.currentTurn != playerColor) {
            boardView.isLocked = true
            triggerAI()
        }
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
            if (move != null) { boardView.isLocked = false; handleMove(move) }
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
        val labels = items.toTypedArray()
        AlertDialog.Builder(this).setTitle("Menu").setItems(labels) { _, which ->
            when (labels[which]) {
                "New Game" -> if (inProgress) {
                    AlertDialog.Builder(this).setTitle("Forfeit Match?")
                        .setMessage("Starting a new game counts as a forfeit.")
                        .setPositiveButton("Forfeit & New Game") { _, _ ->
                            if (vsAI) SettingsManager.recordForfeit(this)
                            showModeDialog()
                        }.setNegativeButton("Cancel", null).show()
                } else showModeDialog()
                "How to Play" -> showRules(false)
                "AI Difficulty" -> showDifficultyDialog()
                "Main Menu" -> if (inProgress) {
                    AlertDialog.Builder(this).setTitle("Leave Match?")
                        .setMessage("Leaving counts as a forfeit.")
                        .setPositiveButton("Leave") { _, _ ->
                            if (vsAI) SettingsManager.recordForfeit(this)
                            finish()
                        }.setNegativeButton("Cancel", null).show()
                } else finish()
            }
        }.show()
    }

    private fun showDifficultyDialog() {
        val labels = arrayOf("Easy", "Medium", "Hard")
        val current = SettingsManager.getConnectFourDifficulty(this)
        AlertDialog.Builder(this).setTitle("AI Difficulty")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val changed = which != current
                SettingsManager.setConnectFourDifficulty(this, which)
                dialog.dismiss()
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    AlertDialog.Builder(this).setTitle("Restart Match?")
                        .setMessage("Difficulty changed. Restart now?")
                        .setPositiveButton("Restart") { _, _ -> startGame() }
                        .setNegativeButton("Keep Playing", null).show()
                }
            }.setNegativeButton("Cancel", null).show()
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
        AlertDialog.Builder(this).setTitle("Game Over").setMessage(message)
            .setPositiveButton("Play Again") { _, _ -> startGame() }
            .setNegativeButton("Main Menu") { _, _ -> finish() }
            .setNeutralButton("Watch Replay") { _, _ ->
                startActivity(Intent(this, ReplayActivity::class.java).apply {
                    putExtra(ReplayActivity.EXTRA_GAME_TYPE, "CONNECTFOUR")
                    putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(gameState.moveHistory))
                    putExtra(ReplayActivity.EXTRA_RESULT, result)
                })
            }
            .setCancelable(true).show()
    }

    inner class ConnectBoardView(ctx: Context) : View(ctx) {
        var isLocked = false
        var onGameOverTapped: (() -> Unit)? = null
        private var state = engine.initialState()
        private var lastMove: Position? = null
        private var winLine: List<Int>? = null
        private val cellScale = HashMap<Int, Float>()
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

        fun setGameState(newState: GameState, last: Position? = null) {
            state = newState
            lastMove = last
            last?.let {
                val idx = it.row * ConnectFourRuleEngine.COLUMNS + it.col
                cellScale[idx] = 0f
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 280L
                    interpolator = OvershootInterpolator(1.35f)
                    addUpdateListener { cellScale[idx] = it.animatedValue as Float; invalidate() }
                    start()
                }
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
            cellScale.clear()
            isLocked = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            val right = boardLeft + ConnectFourRuleEngine.COLUMNS * cellSize
            val bottom = boardTop + ConnectFourRuleEngine.ROWS * cellSize
            boardP.color = boardColor
            canvas.drawRoundRect(boardLeft, boardTop, right, bottom, cellSize * 0.14f, cellSize * 0.14f, boardP)
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val cx = boardLeft + col * cellSize + cellSize / 2f
                val cy = boardTop + row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.36f, holeP)
            }
            lastMove?.let {
                val cx = boardLeft + it.col * cellSize + cellSize / 2f
                val cy = boardTop + it.row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.43f, highlightP)
            }
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val piece = state.get(row, col) as? ConnectFourPiece ?: continue
                val idx = row * ConnectFourRuleEngine.COLUMNS + col
                val cx = boardLeft + col * cellSize + cellSize / 2f
                val cy = boardTop + row * cellSize + cellSize / 2f
                val radius = cellSize * 0.31f * (cellScale[idx] ?: 1f)
                canvas.drawCircle(cx, cy, radius, if (piece.color == PieceColor.WHITE) redP else yellowP)
                canvas.drawCircle(cx - radius * 0.22f, cy - radius * 0.25f, radius * 0.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) })
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