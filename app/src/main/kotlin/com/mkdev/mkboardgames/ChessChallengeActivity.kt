package com.mkdev.mkboardgames

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.challenges.ChessPuzzle
import com.mkdev.mkboardgames.challenges.ChessPuzzleData
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.chess.*
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.ChallengeLevelGridView
import com.mkdev.mkboardgames.ui.StandardGameHudView

class ChessChallengeActivity : AppCompatActivity() {

    private val engine = ChessRuleEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val puzzles = ChessPuzzleData.all
    private var selectedLevel = 1
    private var currentPuzzle: ChessPuzzle? = null
    private var puzzleState: GameState? = null
    private var playerStartState: GameState? = null
    private var solutionMoves: List<String> = emptyList()
    private var expectedMoveIndex = 1
    private var boardView: BoardView? = null
    private var gameHud: StandardGameHudView? = null
    private var completed = false
    private var resetting = false

    private val progressPrefs by lazy {
        getSharedPreferences("chess_challenge_progress", Context.MODE_PRIVATE)
    }

    private val highestCompleted: Int
        get() = progressPrefs.getInt(KEY_HIGHEST_COMPLETED, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        selectedLevel = intent.getIntExtra(EXTRA_LEVEL, 1).coerceIn(1, puzzles.size)
        showLevelList()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (boardView != null) {
            showLevelList()
        } else {
            finish()
        }
    }

    private fun showLevelList() {
        handler.removeCallbacksAndMessages(null)
        boardView = null
        gameHud = null
        val root = verticalRoot()
        root.addView(topBar("CHESS CHALLENGES", "100 tactical levels") {
            finish()
        })

        val summary = TextView(this).apply {
            text = "Complete levels in order.\nHighest completed: ${highestCompleted.coerceAtMost(puzzles.size)} / ${puzzles.size}"
            setTextColor(Color.parseColor("#B7C9D1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(20), dp(13), dp(20), dp(12))
        }
        root.addView(summary)

        val levelGrid = ChallengeLevelGridView(this, puzzles.size, highestCompleted).apply {
            onLevelSelected = { level ->
                selectedLevel = level
                showPuzzle(level)
            }
        }
        root.addView(levelGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showPuzzle(level: Int) {
        handler.removeCallbacksAndMessages(null)
        selectedLevel = level.coerceIn(1, puzzles.size)
        currentPuzzle = puzzles[selectedLevel - 1]
        completed = false
        resetting = false
        val root = verticalRoot()

        val hud = StandardGameHudView(
            this,
            showHistoryControls = false,
            labelTextSizeSp = 15f,
            backLabel = "← Levels",
            menuLabel = "Reset",
        ).apply {
            setInfo(
                value = "LEVEL ${selectedLevel.toString().padStart(2, '0')}",
                undo = false,
                detail = "Loading puzzle…",
                accentColor = Color.parseColor("#F7D99B"),
            )
            onBack = { showLevelList() }
            onMenu = { resetPuzzle() }
        }
        gameHud = hud
        root.addView(hud, LinearLayout.LayoutParams(-1, dp(56)))

        val board = BoardView(this).apply {
            ruleEngine = engine
            chessBoardStyle = ChessBoardStyle.CANVAS
            isFlipped = false
            onMoveMade = ::handlePlayerMove
            onPromotionChoice = ::showPromotionChoice
            isLocked = true
        }
        boardView = board
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        val puzzle = currentPuzzle!!
        loadPuzzle(puzzle)
    }

    private fun setChallengeStatus(text: String) {
        gameHud?.setInfo(
            value = "LEVEL ${selectedLevel.toString().padStart(2, '0')}",
            undo = false,
            detail = text,
            accentColor = Color.parseColor("#F7D99B"),
        )
    }

    private fun loadPuzzle(puzzle: ChessPuzzle) {
        val initial = ChessFenParser.parse(puzzle.fen)
        solutionMoves = puzzle.moves.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        expectedMoveIndex = 1
        val firstMove = solutionMoves.firstOrNull()?.let { findMove(initial, it) }
        if (firstMove == null) {
            puzzleState = initial
            playerStartState = initial
            boardView?.gameState = initial
            boardView?.isLocked = true
            setChallengeStatus("This puzzle could not be loaded.")
            return
        }
        val afterOpponent = engine.applyMove(initial, firstMove)
        puzzleState = afterOpponent.copy(status = GameStatus.IN_PROGRESS)
        playerStartState = puzzleState
        boardView?.gameState = puzzleState!!
        boardView?.isFlipped = puzzleState!!.currentTurn == PieceColor.BLACK
        boardView?.isLocked = false
        setChallengeStatus("Your move. Find the best continuation.")
    }

    private fun handlePlayerMove(move: Move) {
        if (completed || resetting) return
        val state = puzzleState ?: return
        val expectedUci = solutionMoves.getOrNull(expectedMoveIndex) ?: return
        val expected = findMove(state, expectedUci)
        if (expected == null || !sameMove(move, expected)) {
            showWrongMove()
            return
        }

        puzzleState = engine.applyMove(state, expected).copy(status = GameStatus.IN_PROGRESS)
        expectedMoveIndex++
        boardView?.gameState = puzzleState!!

        if (expectedMoveIndex >= solutionMoves.size) {
            completeLevel()
        } else {
            boardView?.isLocked = true
            setChallengeStatus("Correct. Watch the reply…")
            handler.postDelayed({ playOpponentReply() }, 380L)
        }
    }

    private fun playOpponentReply() {
        if (completed) return
        val state = puzzleState ?: return
        val replyUci = solutionMoves.getOrNull(expectedMoveIndex) ?: return
        val reply = findMove(state, replyUci)
        if (reply == null) {
            showWrongMove()
            return
        }
        puzzleState = engine.applyMove(state, reply).copy(status = GameStatus.IN_PROGRESS)
        expectedMoveIndex++
        boardView?.gameState = puzzleState!!
        boardView?.isFlipped = puzzleState!!.currentTurn == PieceColor.BLACK
        if (expectedMoveIndex >= solutionMoves.size) completeLevel()
        else {
            boardView?.isLocked = false
            setChallengeStatus("Your move. Find the best continuation.")
        }
    }

    private fun completeLevel() {
        completed = true
        boardView?.isLocked = true
        if (selectedLevel > highestCompleted) {
            progressPrefs.edit().putInt(KEY_HIGHEST_COMPLETED, selectedLevel).apply()
        }
        setChallengeStatus("Level complete. Excellent calculation.")
    }

    private fun showWrongMove() {
        resetting = true
        boardView?.isLocked = true
        setChallengeStatus("Not quite. Resetting the position…")
        handler.postDelayed({ resetPuzzle() }, 900L)
    }

    private fun resetPuzzle() {
        handler.removeCallbacksAndMessages(null)
        val start = playerStartState ?: return
        puzzleState = start
        expectedMoveIndex = 1
        completed = false
        resetting = false
        boardView?.gameState = start
        boardView?.isFlipped = start.currentTurn == PieceColor.BLACK
        boardView?.isLocked = false
        setChallengeStatus("Your move. Find the best continuation.")
    }

    private fun showPromotionChoice(choices: List<Move>) {
        val available = choices.filter { it.promotionType != null }
        if (available.isEmpty()) return
        val names = available.map { it.promotionType!!.lowercase().replaceFirstChar(Char::uppercaseChar) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a promotion")
            .setItems(names) { _, which -> boardView?.animateExternalMove(available[which]) }
            .setOnCancelListener { boardView?.isLocked = false }
            .show()
    }

    private fun findMove(state: GameState, uci: String): Move? {
        if (uci.length < 4) return null
        val from = ChessFenParser.position(uci.substring(0, 2)) ?: return null
        val to = ChessFenParser.position(uci.substring(2, 4)) ?: return null
        val promotion = uci.getOrNull(4)?.uppercaseChar()?.let {
            when (it) {
                'Q' -> "QUEEN"
                'R' -> "ROOK"
                'B' -> "BISHOP"
                'N' -> "KNIGHT"
                else -> null
            }
        }
        return engine.legalMovesFrom(state, from).firstOrNull { move ->
            move.to == to && (promotion == null || move.promotionType == promotion)
        }
    }

    private fun sameMove(a: Move, b: Move): Boolean =
        a.from == b.from && a.to == b.to && a.promotionType == b.promotionType

    private fun verticalRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#061321"))
    }

    private fun topBar(title: String, subtitle: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(actionButton("‹") { onBack() }, LinearLayout.LayoutParams(dp(48), dp(46)))
            addView(LinearLayout(this@ChessChallengeActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(TextView(this@ChessChallengeActivity).apply {
                    text = title
                    tag = "level-title"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                })
                addView(TextView(this@ChessChallengeActivity).apply {
                    text = subtitle
                    tag = "level-subtitle"
                    setTextColor(Color.parseColor("#AFC6CC"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = android.view.Gravity.CENTER
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Space(this@ChessChallengeActivity), LinearLayout.LayoutParams(dp(48), dp(46)))
        }

    private fun actionButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = android.view.Gravity.CENTER
        setTextColor(Color.parseColor("#F7D99B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label == "‹") 28f else 12f)
        typeface = Typeface.DEFAULT_BOLD
        background = roundedBackground("#172B35", "#39505B", 10f)
        setOnClickListener { action() }
    }

    private fun roundedBackground(fill: String, stroke: String, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(dp(1), Color.parseColor(stroke))
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        const val EXTRA_LEVEL = "challenge_level"
        private const val KEY_HIGHEST_COMPLETED = "highest_chess_level"
    }
}

private object ChessFenParser {
    fun parse(fen: String): GameState {
        val fields = fen.trim().split(Regex("\\s+"))
        val board = arrayOfNulls<Piece>(64)
        val ranks = fields.firstOrNull()?.split("/") ?: emptyList()
        ranks.take(8).forEachIndexed { row, rank ->
            var col = 0
            rank.forEach { token ->
                if (token.isDigit()) col += token.digitToInt()
                else if (col < 8) {
                    val color = if (token.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (token.lowercaseChar()) {
                        'k' -> ChessPieceType.KING
                        'q' -> ChessPieceType.QUEEN
                        'r' -> ChessPieceType.ROOK
                        'b' -> ChessPieceType.BISHOP
                        'n' -> ChessPieceType.KNIGHT
                        'p' -> ChessPieceType.PAWN
                        else -> null
                    }
                    if (type != null) board[row * 8 + col] = ChessPiece(type, color)
                    col++
                }
            }
        }
        val castling = fields.getOrNull(2) ?: "-"
        val enPassant = fields.getOrNull(3)?.takeIf { it.length == 2 }?.let { it[0] - 'a' } ?: -1
        return GameState(
            board = board,
            currentTurn = if (fields.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE,
            metadata = mapOf(
                "castleWK" to castling.contains('K'),
                "castleWQ" to castling.contains('Q'),
                "castleBK" to castling.contains('k'),
                "castleBQ" to castling.contains('q'),
                "enPassant" to enPassant,
            ),
        )
    }

    fun position(square: String): Position? {
        if (square.length != 2) return null
        val col = square[0] - 'a'
        val row = 8 - (square[1] - '0')
        return if (row in 0..7 && col in 0..7) Position(row, col) else null
    }
}