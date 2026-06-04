package com.nexboard

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
import com.nexboard.engine.*
import com.nexboard.games.tictactoe.TicTacToePiece
import com.nexboard.games.tictactoe.TicTacToeRuleEngine
import kotlinx.coroutines.*

class TicTacToeActivity : AppCompatActivity() {

    private var boardSize   = 3
    private var winLength   = 3
    private var engine      = TicTacToeRuleEngine(boardSize, winLength)
    private var gameState   = engine.initialState()
    private var vsAI        = true
    private var playerColor = PieceColor.WHITE
    private val moveHistory = ArrayDeque<GameState>()
    private val scope       = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scoreX     = 0
    private var scoreO     = 0
    private var scoreDraws = 0

    /** Stats recorded once per game — prevents double-counting on re-shown result dialog. */
    private var resultRecorded = false

    private lateinit var hudView:   HudView
    private lateinit var boardView: TicBoardView
    private lateinit var scoreView: ScoreView

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView   = HudView(this)
        boardView = TicBoardView(this)
        scoreView = ScoreView(this)

        root.addView(hudView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(scoreView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))

        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
            val adView = com.google.android.gms.ads.AdView(this).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
            root.addView(adView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } catch (_: Exception) {}

        setContentView(root)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "ttt")
            boardView.applyTheme()
        }
    }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameState.status != GameStatus.IN_PROGRESS || moveHistory.isEmpty()) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
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
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this).setTitle("Tic-Tac-Toe")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> { vsAI = true;  showBoardSizeDialog(fromMode = true) }
                    1 -> { vsAI = false; playerColor = PieceColor.WHITE; showBoardSizeDialog(fromMode = false) }
                    2 -> showRules(showModeAfter = moveHistory.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moveHistory.isEmpty()) finish() }
            .show()
    }

    private fun showBoardSizeDialog(fromMode: Boolean) {
        // Win lengths: 3→3, 4→4, 5→4, 6→5, 7→5
        val sizeLabels = arrayOf(
            "3×3 — Classic  (3 in a row)",
            "4×4 — Medium   (4 in a row)",
            "5×5 — Large    (4 in a row)",
            "6×6 — X-Large  (5 in a row)",
            "7×7 — Mega     (5 in a row)"
        )
        AlertDialog.Builder(this).setTitle("Board Size")
            .setItems(sizeLabels) { _, which ->
                boardSize = which + 3
                winLength = winLengthFor(boardSize)
                engine    = TicTacToeRuleEngine(boardSize, winLength)
                boardView.updateBoardSize(boardSize)
                if (fromMode && vsAI) showColorPickerDialog() else startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    /** Returns the appropriate win-line length for the given board size. */
    private fun winLengthFor(size: Int): Int = when (size) {
        3    -> 3
        4    -> 4
        5    -> 4
        else -> 5   // 6×6, 7×7
    }

    private fun showColorPickerDialog() {
        AlertDialog.Builder(this).setTitle("Play as")
            .setItems(arrayOf("X  (goes first)", "O  (goes second)")) { _, which ->
                playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showBoardSizeDialog(fromMode = true) }
            .show()
    }

    private fun showRules(showModeAfter: Boolean = false) {
        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
TIC-TAC-TOE — Rules

Overview
Played on a square grid. X always goes first. Players take turns placing their mark on an empty cell.

─────────────────────────

Board Sizes & Win Conditions
• 3×3 board: get 3 in a row to win
• 4×4 board: get 4 in a row to win
• 5×5 board: get 4 in a row to win
• 6×6 board: get 5 in a row to win
• 7×7 board: get 5 in a row to win

─────────────────────────

Winning
The first player to get the required number of marks in a row — horizontally, vertically, or diagonally — wins.

─────────────────────────

Draw
The game ends as a draw if:
• All cells are filled and neither player has won, OR
• No win line can possibly be completed by either player — the game ends early instead of playing out pointlessly.

─────────────────────────

Strategy
• On 3×3, the centre is part of 4 win lines — take it early.
• Block your opponent if they have 2 (or more) in a row.
• Set up a "fork" — two simultaneous winning threats your opponent can't both block.
• On larger boards, control the centre region and connect threats.
            """.trimIndent()
        }
        val sv = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this).setTitle("How to Play Tic-Tac-Toe")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun startGame() {
        resultRecorded = false
        SettingsManager.activateGameTheme(this, "ttt")
        if (vsAI) SettingsManager.setActiveGame(this, "ttt")
        SoundPlayer.init(this)
        gameState = engine.initialState()
        moveHistory.clear()
        boardView.reset(gameState)
        // Re-show result dialog when board is tapped after game over
        boardView.onGameOverTapped = { showResultDialog() }
        scoreView.setLabels(boardSize)
        updateHud()
        if (vsAI && gameState.currentTurn != playerColor) triggerAI()
    }

    internal fun handleMove(move: Move) {
        if (boardView.isLocked) return
        val moverWasX = gameState.currentTurn == PieceColor.WHITE
        moveHistory.addLast(gameState)
        gameState = engine.applyMove(gameState, move)
        boardView.setGameState(gameState, lastMove = move.to)

        if (gameState.status != GameStatus.IN_PROGRESS) {
            boardView.isLocked = true
            when (gameState.status) {
                GameStatus.WHITE_WINS -> { scoreX++; SoundPlayer.play("game_end") }
                GameStatus.BLACK_WINS -> { scoreO++; SoundPlayer.play("game_end") }
                else                  -> { scoreDraws++; SoundPlayer.play("game_draw") }
            }
            scoreView.update(scoreX, scoreDraws, scoreO)
            boardView.showWinLine(engine.winningLine(gameState))
            updateHud()
            recordResult()
            scope.launch { delay(1200); showResultDialog() }
            return
        }
        if (moverWasX) SoundPlayer.play("ttt_x") else SoundPlayer.play("ttt_o")
        updateHud()
        if (vsAI && gameState.currentTurn != playerColor) {
            boardView.isLocked = true
            triggerAI()
        }
    }

    /** Record stats once per game. */
    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW       -> SettingsManager.recordDraw(this)
            else -> {}
        }
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun triggerAI() {
        hudView.setThinking(true)
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                try {
                    AIPlayer(engine, maxDepth = SettingsManager.tttAiDepth(this@TicTacToeActivity, boardSize), timeLimitMs = 2500L).bestMove(gameState)
                } catch (e: Throwable) { null }
            }
            hudView.setThinking(false)
            if (move != null) { boardView.isLocked = false; handleMove(move) }
            else boardView.isLocked = false
        }
    }

    // ─── HUD / undo / menu ────────────────────────────────────────────────────

    private fun updateHud() {
        val isX = gameState.currentTurn == PieceColor.WHITE
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            else -> "${if (isX) "X" else "O"}'s turn"
        }
        hudView.setInfo(label, canUndo = moveHistory.isNotEmpty(), isX = isX)
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
        scope.coroutineContext.cancelChildren()
        hudView.setThinking(false)
        if (vsAI && moveHistory.size >= 2) moveHistory.removeLast()
        gameState = moveHistory.removeLastOrNull() ?: return
        boardView.isLocked = false
        boardView.reset(gameState)
        updateHud()
    }

    fun onMenuClicked() {
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("AI Difficulty")
        items.add("Main Menu")
        val arr = items.toTypedArray()
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(arr) { _, which ->
                when (arr[which]) {
                    "New Game" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Forfeit Match?")
                            .setMessage("Starting a new game counts as a forfeit.")
                            .setPositiveButton("Forfeit & New Game") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            }.setNegativeButton("Cancel", null).show()
                    } else showModeDialog()
                    "How to Play" -> showRules(showModeAfter = false)
                    "AI Difficulty" -> {
                        val diffs = arrayOf("Easy", "Medium", "Hard")
                        var diff = SettingsManager.getTttDifficulty(this)
                        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_MinWidth)
                            .setTitle("AI Difficulty")
                            .setSingleChoiceItems(diffs, diff) { d, i ->
                                SettingsManager.setTttDifficulty(this, i); diff = i; d.dismiss()
                            }.show()
                    }
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

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val msg = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                if (vsAI && playerColor == PieceColor.WHITE) "You win! 🎉" else "X wins!"
            GameStatus.BLACK_WINS ->
                if (vsAI && playerColor == PieceColor.BLACK) "You win! 🎉" else "O wins!"
            GameStatus.DRAW -> "It's a draw!"
            else -> return
        }
        val resultLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> "X wins"
            GameStatus.BLACK_WINS -> "O wins"
            GameStatus.DRAW       -> "Draw"
            else -> ""
        }
        AlertDialog.Builder(this).setTitle("Game Over").setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> startGame() }
            .setNegativeButton("Main Menu")  { _, _ -> finish() }
            .setNeutralButton("Watch Replay") { _, _ -> launchReplay(resultLabel) }
            .setCancelable(true).show()
    }

    private fun launchReplay(resultLabel: String) {
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  "TICTACTOE")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
            putExtra(ReplayActivity.EXTRA_BOARD_SIZE, boardSize)
        })
    }

    // ─── Board View ───────────────────────────────────────────────────────────

    inner class TicBoardView(ctx: Context) : View(ctx) {

        var isLocked = false
        /** Called when the board is tapped after game over — re-shows result dialog. */
        var onGameOverTapped: (() -> Unit)? = null

        private var bs          = boardSize
        private var state       = engine.initialState()
        private var lastMoveTo: Position? = null
        private var winLine:    List<Int>? = null
        private var winAlpha    = 0f
        private val cellScale   = HashMap<Int, Float>()

        private val dp = resources.displayMetrics.density

        private var boardLeft = 0f
        private var boardTop  = 0f
        private var cellSize  = 0f

        private val bgP   = Paint().apply { color = Color.parseColor("#121212") }
        private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#383838"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val xP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val oP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val hlP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(35, 255, 215, 0) }
        private val winP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }

        fun applyTheme() {
            val t = com.nexboard.SettingsManager.currentTheme(context)
            lineP.color = t.dark.let { c ->
                android.graphics.Color.argb(200,
                    android.graphics.Color.red(c),
                    android.graphics.Color.green(c),
                    android.graphics.Color.blue(c))
            }
            val ac = t.accent
            hlP.color = android.graphics.Color.argb(40,
                android.graphics.Color.red(ac),
                android.graphics.Color.green(ac),
                android.graphics.Color.blue(ac))
            invalidate()
        }

        fun updateBoardSize(newBs: Int) {
            bs    = newBs
            state = GameState(board = arrayOfNulls(newBs * newBs), boardSize = newBs)
            winLine = null; winAlpha = 0f; cellScale.clear(); lastMoveTo = null
            if (width > 0 && height > 0) recalc(width, height)
            requestLayout(); invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { recalc(w, h) }

        private fun recalc(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val pad  = 24f * dp
            val size = minOf(w - pad * 2, h - pad * 2)
            cellSize  = size / bs.toFloat()
            boardLeft = (w - size) / 2f
            boardTop  = (h - size) / 2f
            lineP.strokeWidth = maxOf(cellSize * 0.022f, 2f)
            xP.strokeWidth    = cellSize * 0.085f
            oP.strokeWidth    = cellSize * 0.085f
            winP.strokeWidth  = cellSize * 0.05f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                // Game is over — fire callback to re-show result dialog
                if (gameState.status != GameStatus.IN_PROGRESS) {
                    onGameOverTapped?.invoke()
                    return true
                }
                if (!isLocked && cellSize > 0f) {
                    val col = ((event.x - boardLeft) / cellSize).toInt()
                    val row = ((event.y - boardTop)  / cellSize).toInt()
                    if (col in 0 until bs && row in 0 until bs && state.get(row, col) == null) {
                        handleMove(Move(TicTacToeRuleEngine.PLACE, Position(row, col)))
                    }
                }
            }
            return true
        }

        fun setGameState(newState: GameState, lastMove: Position? = null) {
            state = newState; lastMoveTo = lastMove
            if (lastMove != null) {
                val idx = lastMove.row * bs + lastMove.col
                cellScale[idx] = 0f
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 250L; interpolator = OvershootInterpolator(1.5f)
                    addUpdateListener { cellScale[idx] = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else invalidate()
        }

        fun showWinLine(line: List<Int>?) {
            if (line == null) { invalidate(); return }
            winLine = line; winAlpha = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500L
                addUpdateListener { winAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        fun reset(newState: GameState) {
            bs = boardSize; state = newState; lastMoveTo = null
            winLine = null; winAlpha = 0f; cellScale.clear(); isLocked = false
            if (width > 0 && height > 0) recalc(width, height)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            drawGrid(canvas); drawHighlight(canvas); drawPieces(canvas); drawWinLine(canvas)
        }

        private fun drawGrid(canvas: Canvas) {
            val right  = boardLeft + bs * cellSize
            val bottom = boardTop  + bs * cellSize
            for (i in 1 until bs) {
                canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, bottom, lineP)
                canvas.drawLine(boardLeft, boardTop + i * cellSize, right, boardTop + i * cellSize, lineP)
            }
        }

        private fun drawHighlight(canvas: Canvas) {
            lastMoveTo?.let { pos ->
                canvas.drawRect(
                    boardLeft + pos.col * cellSize, boardTop + pos.row * cellSize,
                    boardLeft + (pos.col + 1) * cellSize, boardTop + (pos.row + 1) * cellSize, hlP)
            }
        }

        private fun drawPieces(canvas: Canvas) {
            for (row in 0 until bs) for (col in 0 until bs) {
                val piece = state.get(row, col) as? TicTacToePiece ?: continue
                val idx   = row * bs + col
                val scale = cellScale[idx] ?: 1f
                val cx    = boardLeft + col * cellSize + cellSize / 2f
                val cy    = boardTop  + row * cellSize + cellSize / 2f
                val r     = cellSize * 0.29f * scale
                if (piece.color == PieceColor.WHITE) {
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, xP)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, xP)
                } else {
                    canvas.drawCircle(cx, cy, r, oP)
                }
            }
        }

        private fun drawWinLine(canvas: Canvas) {
            val line = winLine ?: return
            if (line.size < 2 || winAlpha <= 0f) return
            val first = line.first(); val last = line.last()
            val r0 = first / bs; val c0 = first % bs
            val r1 = last  / bs; val c1 = last  % bs
            winP.color = Color.argb((winAlpha * 220).toInt(), 255, 193, 7)
            winP.strokeWidth = cellSize * 0.055f
            canvas.drawLine(
                boardLeft + c0 * cellSize + cellSize / 2f, boardTop + r0 * cellSize + cellSize / 2f,
                boardLeft + c1 * cellSize + cellSize / 2f, boardTop + r1 * cellSize + cellSize / 2f, winP)
        }
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class HudView(ctx: Context) : View(ctx) {
        private var label    = "X's turn"
        private var canUndo  = false
        private var thinking = false
        private var isX      = true

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP   = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP  = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = 14f * sp.coerceAtMost(3f)
        }
        private val subP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val dimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }

        private val backRect = RectF(); private val undoRect = RectF(); private val menuRect = RectF()

        fun setInfo(l: String, canUndo: Boolean, isX: Boolean) {
            label = l; this.canUndo = canUndo; this.isX = isX; invalidate()
        }
        fun setThinking(t: Boolean) { thinking = t; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 48f * dp; val bh = 26f * dp; val by = (h - bh) / 2f
            backRect.set(6f*dp, by, 6f*dp+bw, by+bh)
            undoRect.set(w-bw*2.2f, by, w-bw*1.1f, by+bh)
            menuRect.set(w-bw*1.05f, by, w-4f*dp, by+bh)
        }

        @Suppress("DEPRECATION")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> this@TicTacToeActivity.onBackPressed()
                    undoRect.contains(e.x, e.y) -> onUndoClicked()
                    menuRect.contains(e.x, e.y) -> onMenuClicked()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, h-dp, w, h, divP)
            val rr = 5f * dp
            canvas.drawRoundRect(backRect, rr, rr, btnBgP)
            canvas.drawRoundRect(undoRect, rr, rr, btnBgP)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgP)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY()+btnP.textSize*0.36f, btnP)
            canvas.drawText("Undo",   undoRect.centerX(), undoRect.centerY()+btnP.textSize*0.36f, if (canUndo) btnP else dimP)
            canvas.drawText("Menu",   menuRect.centerX(), menuRect.centerY()+btnP.textSize*0.36f, btnP)
            val cx = w / 2f
            txtP.color = if (isX) Color.parseColor("#EF5350") else Color.parseColor("#7FC8F8")
            canvas.drawText(label, cx, h/2f - txtP.textSize*0.15f, txtP)
            if (thinking) canvas.drawText("Thinking…", cx, h/2f + subP.textSize*1.1f, subP)
        }
    }

    // ─── Score View ───────────────────────────────────────────────────────────

    inner class ScoreView(ctx: Context) : View(ctx) {
        private var xLabel = "X"
        private var oLabel = "O"
        private var x = 0; private var d = 0; private var o = 0

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP  = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val xP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val oP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val dP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 15f * sp.coerceAtMost(3f); isFakeBoldText = true
        }
        private val lP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#616161"); textAlign = Paint.Align.CENTER
            textSize = 9f * sp.coerceAtMost(3f)
        }

        fun setLabels(bs: Int) {
            xLabel = if (bs == 3) "X" else "X"; oLabel = if (bs == 3) "O" else "O"; invalidate()
        }
        fun update(xs: Int, ds: Int, os: Int) { x = xs; d = ds; o = os; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, 0f, w, dp, divP)
            val third = w / 3f; val cy = h / 2f
            val numY = cy - lP.textSize; val lblY = cy + xP.textSize * 0.36f + 2f
            canvas.drawText(x.toString(), third*0.5f, lblY, xP)
            canvas.drawText(d.toString(), third*1.5f, lblY, dP)
            canvas.drawText(o.toString(), third*2.5f, lblY, oP)
            canvas.drawText(xLabel,  third*0.5f, numY, lP)
            canvas.drawText("Draw",  third*1.5f, numY, lP)
            canvas.drawText(oLabel,  third*2.5f, numY, lP)
        }
    }
}
