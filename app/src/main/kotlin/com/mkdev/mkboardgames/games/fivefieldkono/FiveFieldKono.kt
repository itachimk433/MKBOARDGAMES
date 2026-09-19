package com.mkdev.mkboardgames.games.fivefieldkono

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

data class FiveFieldKonoPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 100
}

/**
 * Five Field Kono is played on a 5 × 5 point grid. Each player starts with
 * five pieces on their home row and tries to occupy the opponent's home row.
 *
 * A piece moves diagonally to a neighbouring empty point. It may also jump
 * diagonally over one occupied point into an empty point. Jumps do not remove
 * the jumped piece; the objective is positional rather than capture-based.
 */
class FiveFieldKonoRuleEngine : RuleEngine {
    companion object {
        const val SIZE = 5
        const val BOARD_CELLS = SIZE * SIZE
        const val PIECES_PER_PLAYER = SIZE

        private val DIAGONALS = listOf(
            Position(-1, -1),
            Position(-1, 1),
            Position(1, -1),
            Position(1, 1),
        )

        fun indexOf(position: Position): Int =
            if (position.row in 0 until SIZE && position.col in 0 until SIZE) {
                position.row * SIZE + position.col
            } else {
                -1
            }

        fun positionAt(index: Int): Position = Position(index / SIZE, index % SIZE)
    }

    override fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_CELLS)
        for (col in 0 until SIZE) {
            board[indexOf(Position(0, col))] = FiveFieldKonoPiece(PieceColor.BLACK)
            board[indexOf(Position(SIZE - 1, col))] = FiveFieldKonoPiece(PieceColor.WHITE)
        }
        return GameState(
            board = board,
            boardSize = SIZE,
            currentTurn = PieceColor.WHITE,
            status = GameStatus.IN_PROGRESS,
        )
    }

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return movementMoves(state, position, state.currentTurn)
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> =
        if (state.status != GameStatus.IN_PROGRESS) {
            emptyList()
        } else {
            (0 until BOARD_CELLS).flatMap { movementMoves(state, positionAt(it), color) }
        }

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state
        val matching = allLegalMoves(state, state.currentTurn).firstOrNull {
            it.from == move.from && it.to == move.to
        } ?: return state
        val fromIndex = indexOf(matching.from)
        val toIndex = indexOf(matching.to)
        if (fromIndex < 0 || toIndex < 0) return state

        val board = state.board.copyOf()
        val piece = board[fromIndex] ?: return state
        board[fromIndex] = null
        board[toIndex] = piece
        val nextTurn = state.currentTurn.opponent()
        val next = state.copy(
            board = board,
            currentTurn = nextTurn,
            status = GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + matching,
        )
        return next.copy(status = gameStatus(next))
    }

    override fun gameStatus(state: GameState): GameStatus {
        val whiteWon = occupiesGoalRow(state, PieceColor.WHITE)
        val blackWon = occupiesGoalRow(state, PieceColor.BLACK)
        return when {
            whiteWon -> GameStatus.WHITE_WINS
            blackWon -> GameStatus.BLACK_WINS
            allLegalMoves(state, state.currentTurn).isEmpty() ->
                if (state.currentTurn == PieceColor.WHITE) {
                    GameStatus.BLACK_WINS
                } else {
                    GameStatus.WHITE_WINS
                }
            else -> GameStatus.IN_PROGRESS
        }
    }

    override fun evaluate(state: GameState): Int {
        if (state.status != GameStatus.IN_PROGRESS) {
            return when (state.status) {
                GameStatus.WHITE_WINS -> 100_000
                GameStatus.BLACK_WINS -> -100_000
                GameStatus.DRAW, GameStatus.IN_PROGRESS -> 0
            }
        }

        var score = 0
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                when (state.get(row, col)?.color) {
                    PieceColor.WHITE -> score += (SIZE - 1 - row) * 30
                    PieceColor.BLACK -> score -= row * 30
                    null -> Unit
                }
            }
        }
        score += (allLegalMoves(state, PieceColor.WHITE).size -
            allLegalMoves(state, PieceColor.BLACK).size) * 2
        if (state.currentTurn == PieceColor.WHITE) score += 1 else score -= 1
        return score
    }

    private fun movementMoves(
        state: GameState,
        from: Position,
        color: PieceColor,
    ): List<Move> {
        if (state.get(from)?.color != color) return emptyList()
        return buildList {
            DIAGONALS.forEach { direction ->
                val adjacent = from + direction
                if (!isOnBoard(adjacent)) return@forEach
                if (state.get(adjacent) == null) {
                    add(Move(from, adjacent))
                } else {
                    val landing = adjacent + direction
                    if (isOnBoard(landing) && state.get(landing) == null) {
                        add(Move(from, landing, metadata = mapOf("jump" to true)))
                    }
                }
            }
        }
    }

    private fun occupiesGoalRow(state: GameState, color: PieceColor): Boolean {
        val goalRow = if (color == PieceColor.WHITE) 0 else SIZE - 1
        return (0 until SIZE).all { col ->
            state.get(goalRow, col)?.color == color
        }
    }

    private fun isOnBoard(position: Position): Boolean =
        position.row in 0 until SIZE && position.col in 0 until SIZE
}
