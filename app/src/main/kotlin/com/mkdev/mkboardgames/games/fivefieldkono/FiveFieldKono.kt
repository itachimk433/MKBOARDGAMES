package com.mkdev.mkboardgames.games.fivefieldkono

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine
import java.util.Random

data class FiveFieldKonoPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 100
}

/**
 * Five Field Kono is played on a 5 × 5 point grid. Each player starts with
 * seven pieces: five on their home row and one on each end of the adjacent
 * row. The first player to occupy all seven of the opponent's starting
 * points wins.
 *
 * A piece moves exactly one point diagonally to an empty point. There are no
 * captures or jumps; the objective is positional rather than capture-based.
 */
class FiveFieldKonoRuleEngine : RuleEngine {
    companion object {
        const val SIZE = 5
        const val BOARD_CELLS = SIZE * SIZE
        const val PIECES_PER_PLAYER = 7

        private val DIAGONALS = listOf(
            Position(-1, -1),
            Position(-1, 1),
            Position(1, -1),
            Position(1, 1),
        )

        private val WHITE_GOAL = buildList {
            for (col in 0 until SIZE) add(Position(0, col))
            add(Position(1, 0))
            add(Position(1, SIZE - 1))
        }
        private val BLACK_GOAL = buildList {
            for (col in 0 until SIZE) add(Position(SIZE - 1, col))
            add(Position(SIZE - 2, 0))
            add(Position(SIZE - 2, SIZE - 1))
        }

        fun indexOf(position: Position): Int =
            if (position.row in 0 until SIZE && position.col in 0 until SIZE) {
                position.row * SIZE + position.col
            } else {
                -1
            }

        fun positionAt(index: Int): Position = Position(index / SIZE, index % SIZE)

        fun goalPositions(color: PieceColor): List<Position> =
            if (color == PieceColor.WHITE) WHITE_GOAL else BLACK_GOAL
    }

    override fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_CELLS)
        for (col in 0 until SIZE) {
            board[indexOf(Position(0, col))] = FiveFieldKonoPiece(PieceColor.BLACK)
            board[indexOf(Position(SIZE - 1, col))] = FiveFieldKonoPiece(PieceColor.WHITE)
        }
        board[indexOf(Position(1, 0))] = FiveFieldKonoPiece(PieceColor.BLACK)
        board[indexOf(Position(1, SIZE - 1))] = FiveFieldKonoPiece(PieceColor.BLACK)
        board[indexOf(Position(SIZE - 2, 0))] = FiveFieldKonoPiece(PieceColor.WHITE)
        board[indexOf(Position(SIZE - 2, SIZE - 1))] = FiveFieldKonoPiece(PieceColor.WHITE)
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
        val whiteGoalCount = goalPositions(PieceColor.WHITE).count {
            state.get(it)?.color == PieceColor.WHITE
        }
        val blackGoalCount = goalPositions(PieceColor.BLACK).count {
            state.get(it)?.color == PieceColor.BLACK
        }
        score += (whiteGoalCount - blackGoalCount) * 180
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
                if (state.get(adjacent) == null) add(Move(from, adjacent))
            }
        }
    }

    private fun occupiesGoalRow(state: GameState, color: PieceColor): Boolean {
        return goalPositions(color).all { state.get(it)?.color == color }
    }

    private fun isOnBoard(position: Position): Boolean =
        position.row in 0 until SIZE && position.col in 0 until SIZE
}

/**
 * Kono-specific minimax search.
 *
 * The generic board-game search is capture-oriented and does not understand
 * Kono's race to occupy a seven-point goal zone. This player evaluates
 * advancement, goal-zone occupation, mobility, and turn tempo directly, then
 * uses progressively deeper alpha-beta searches for the three difficulty
 * levels.
 */
class FiveFieldKonoAIPlayer(
    private val engine: FiveFieldKonoRuleEngine,
    private val maxDepth: Int,
    private val timeLimitMs: Long,
    private val choiceWindow: Int = 0,
) {
    private var deadline = Long.MAX_VALUE
    private var searchAborted = false
    private val random = Random()

    fun bestMove(state: GameState): Move? {
        val moves = orderedMoves(state)
        if (moves.isEmpty()) return null

        deadline = System.currentTimeMillis() + timeLimitMs
        searchAborted = false
        var completedScores = emptyList<Pair<Move, Int>>()

        for (depth in 1..maxDepth.coerceAtLeast(1)) {
            val scores = mutableListOf<Pair<Move, Int>>()
            searchAborted = false
            for (move in moves) {
                if (expired()) {
                    searchAborted = true
                    break
                }
                val score = search(
                    engine.applyMove(state, move),
                    depth - 1,
                    Int.MIN_VALUE,
                    Int.MAX_VALUE,
                )
                if (searchAborted) break
                scores += move to score
            }
            if (searchAborted || scores.size != moves.size) break
            completedScores = scores
        }

        if (completedScores.isEmpty()) return moves.first()
        val maximizing = state.currentTurn == PieceColor.WHITE
        val bestScore = if (maximizing) {
            completedScores.maxOf { it.second }
        } else {
            completedScores.minOf { it.second }
        }
        val candidates = if (choiceWindow <= 0) {
            completedScores.filter { it.second == bestScore }
        } else {
            completedScores.filter { (_, score) ->
                kotlin.math.abs(score - bestScore) <= choiceWindow
            }
        }
        return if (choiceWindow <= 0) {
            candidates.firstOrNull()?.first ?: completedScores.first().first
        } else if (candidates.isNotEmpty()) {
            candidates[random.nextInt(candidates.size)].first
        } else {
            completedScores.first().first
        }
    }

    private fun search(
        state: GameState,
        depth: Int,
        alphaStart: Int,
        betaStart: Int,
    ): Int {
        if (expired()) {
            searchAborted = true
            return engine.evaluate(state)
        }
        if (depth <= 0 || state.status != GameStatus.IN_PROGRESS) {
            return engine.evaluate(state)
        }

        val moves = orderedMoves(state)
        if (moves.isEmpty()) return engine.evaluate(state)

        var alpha = alphaStart
        var beta = betaStart
        return if (state.currentTurn == PieceColor.WHITE) {
            var best = Int.MIN_VALUE
            for (move in moves) {
                best = maxOf(best, search(engine.applyMove(state, move), depth - 1, alpha, beta))
                if (searchAborted) return engine.evaluate(state)
                alpha = maxOf(alpha, best)
                if (beta <= alpha) break
            }
            best
        } else {
            var best = Int.MAX_VALUE
            for (move in moves) {
                best = minOf(best, search(engine.applyMove(state, move), depth - 1, alpha, beta))
                if (searchAborted) return engine.evaluate(state)
                beta = minOf(beta, best)
                if (beta <= alpha) break
            }
            best
        }
    }

    private fun orderedMoves(state: GameState): List<Move> {
        val color = state.currentTurn
        return engine.allLegalMoves(state, color).sortedByDescending { move ->
            val fromProgress = progress(move.from, color)
            val toProgress = progress(move.to, color)
            val goalBonus = if (move.to in FiveFieldKonoRuleEngine.goalPositions(color)) 80 else 0
            (toProgress - fromProgress) * 100 + goalBonus +
                if (move.to.row == 2 && move.to.col == 2) 8 else 0
        }
    }

    private fun progress(position: Position, color: PieceColor): Int =
        if (color == PieceColor.WHITE) {
            FiveFieldKonoRuleEngine.SIZE - 1 - position.row
        } else {
            position.row
        }

    private fun expired(): Boolean = System.currentTimeMillis() >= deadline
}
