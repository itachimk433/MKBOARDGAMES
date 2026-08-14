package com.mkdev.nexboard.games.connectfour

import com.mkdev.nexboard.engine.*
import kotlin.math.abs

data class ConnectFourPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 1
}

/**
 * Standard Connect Four: seven columns by six rows. Pieces fall to the
 * lowest available row in the selected column.
 */
class ConnectFourRuleEngine : RuleEngine {
    companion object {
        const val ROWS = 6
        const val COLUMNS = 7
        val DROP = Position(-1, -1)
    }

    override fun initialState() = GameState(
        board = arrayOfNulls(ROWS * COLUMNS),
        boardSize = COLUMNS,
        currentTurn = PieceColor.WHITE,
        status = GameStatus.IN_PROGRESS
    )

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> = emptyList()

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        val center = (COLUMNS - 1) / 2.0
        return (0 until COLUMNS)
            .mapNotNull { col ->
                val row = landingRow(state, col) ?: return@mapNotNull null
                Move(DROP, Position(row, col)) to abs(col - center)
            }
            .sortedWith(compareBy({ it.second }, { it.first.to.col }))
            .map { it.first }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        val col = move.to.col
        val row = landingRow(state, col) ?: return state
        val board = state.board.copyOf()
        board[row * COLUMNS + col] = ConnectFourPiece(state.currentTurn)
        val appliedMove = move.copy(to = Position(row, col))
        val next = state.copy(
            board = board,
            currentTurn = state.currentTurn.opponent(),
            moveHistory = state.moveHistory + appliedMove
        )
        return next.copy(status = gameStatus(next))
    }

    fun landingRow(state: GameState, column: Int): Int? {
        if (column !in 0 until COLUMNS) return null
        for (row in ROWS - 1 downTo 0) {
            if (state.board[row * COLUMNS + column] == null) return row
        }
        return null
    }

    override fun gameStatus(state: GameState): GameStatus {
        if (winningLine(state, PieceColor.WHITE) != null) return GameStatus.WHITE_WINS
        if (winningLine(state, PieceColor.BLACK) != null) return GameStatus.BLACK_WINS
        return if (state.board.none { it == null }) GameStatus.DRAW else GameStatus.IN_PROGRESS
    }

    fun winningLine(state: GameState): List<Int>? {
        val white = winningLine(state, PieceColor.WHITE)
        return white ?: winningLine(state, PieceColor.BLACK)
    }

    private fun winningLine(state: GameState, color: PieceColor): List<Int>? {
        val directions = arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (row in 0 until ROWS) for (col in 0 until COLUMNS) {
            if ((state.board[row * COLUMNS + col] as? ConnectFourPiece)?.color != color) continue
            for ((dr, dc) in directions) {
                val line = (0 until 4).map { step -> row + dr * step to col + dc * step }
                if (line.all { (r, c) ->
                        r in 0 until ROWS && c in 0 until COLUMNS &&
                            (state.board[r * COLUMNS + c] as? ConnectFourPiece)?.color == color
                    }) {
                    return line.map { (r, c) -> r * COLUMNS + c }
                }
            }
        }
        return null
    }

    override fun evaluate(state: GameState): Int {
        return when (state.status) {
            GameStatus.WHITE_WINS -> 1_000_000 - state.moveHistory.size * 10
            GameStatus.BLACK_WINS -> -(1_000_000 - state.moveHistory.size * 10)
            GameStatus.DRAW -> 0
            GameStatus.IN_PROGRESS -> evaluatePosition(state)
        }
    }

    private fun evaluatePosition(state: GameState): Int {
        var score = 0
        val centerCount = (0 until ROWS).count {
            (state.board[it * COLUMNS + COLUMNS / 2] as? ConnectFourPiece)?.color == PieceColor.WHITE
        }
        val centerBlack = (0 until ROWS).count {
            (state.board[it * COLUMNS + COLUMNS / 2] as? ConnectFourPiece)?.color == PieceColor.BLACK
        }
        score += (centerCount - centerBlack) * 6

        val windows = mutableListOf<List<Int>>()
        for (row in 0 until ROWS) for (col in 0 until COLUMNS) {
            for ((dr, dc) in arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                val cells = (0 until 4).map { step -> row + dr * step to col + dc * step }
                if (cells.all { (r, c) -> r in 0 until ROWS && c in 0 until COLUMNS }) {
                    windows += cells.map { (r, c) -> r * COLUMNS + c }
                }
            }
        }
        for (window in windows) {
            val pieces = window.map { state.board[it] as? ConnectFourPiece }
            val white = pieces.count { it?.color == PieceColor.WHITE }
            val black = pieces.count { it?.color == PieceColor.BLACK }
            if (white > 0 && black == 0) {
                score += windowScore(state, window, white)
            }
            if (black > 0 && white == 0) {
                score -= windowScore(state, window, black)
            }
        }
        return score
    }

    /**
     * A three-piece window is only dangerous when its empty cell is actually
     * playable. Weighting those threats heavily makes the evaluator prefer
     * creating and stopping real winning drops instead of merely building
     * attractive-looking patterns above unsupported cells.
     */
    private fun windowScore(
        state: GameState,
        window: List<Int>,
        count: Int
    ) = when (count) {
        1 -> 2
        2 -> 12
        3 -> {
            val empty = window.firstOrNull { state.board[it] == null }
            if (empty != null && isPlayableCell(state, empty)) 5_000 else 80
        }
        else -> 0
    }

    private fun isPlayableCell(state: GameState, index: Int): Boolean {
        val row = index / COLUMNS
        val col = index % COLUMNS
        return landingRow(state, col) == row
    }
}