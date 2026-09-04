package com.mkdev.mkboardgames.games.mancala

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

/**
 * Kalah, the six-pit Mancala variant used by most commercial Mancala boards.
 *
 * The circular order is:
 * south pits 0..5 → south store 6 → north pits 7..12 → north store 13.
 * The board array contains one [MancalaPitPiece] per pit/store; its value is
 * deliberately the current stone count so the normal GameState contract still
 * gives us immutable snapshots for undo and pause/resume.
 */
data class MancalaPitPiece(
    override val color: PieceColor,
    val stones: Int,
    val store: Boolean = false,
) : Piece(color) {
    override fun symbol() = if (store) "▰" else "●"
    override fun value() = 100 + stones
}

class MancalaRuleEngine : RuleEngine {
    companion object {
        const val PITS_PER_SIDE = 6
        const val BOARD_CELLS = 14
        const val SOUTH_STORE = 6
        const val NORTH_STORE = 13
        const val INITIAL_STONES = 4

        fun isSouthPit(index: Int) = index in 0 until PITS_PER_SIDE
        fun isNorthPit(index: Int) = index in (SOUTH_STORE + 1)..(NORTH_STORE - 1)
        fun storeFor(color: PieceColor) = if (color == PieceColor.WHITE) SOUTH_STORE else NORTH_STORE
        fun ownsPit(color: PieceColor, index: Int) =
            if (color == PieceColor.WHITE) isSouthPit(index) else isNorthPit(index)

        fun oppositePit(index: Int) =
            if (index in 0 until BOARD_CELLS && index != SOUTH_STORE && index != NORTH_STORE) {
                12 - index
            } else {
                -1
            }
    }

    override fun initialState(): GameState = stateFrom(
        IntArray(BOARD_CELLS) { index ->
            if (index == SOUTH_STORE || index == NORTH_STORE) 0 else INITIAL_STONES
        },
        PieceColor.WHITE,
    )

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS || position.row != 0) return emptyList()
        val index = position.col
        if (!ownsPit(state.currentTurn, index) || stones(state, index) == 0) return emptyList()
        return listOf(Move(Position(0, index), Position(0, index)))
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return (0 until BOARD_CELLS)
            .filter { ownsPit(color, it) && stones(state, it) > 0 }
            .map { Move(Position(0, it), Position(0, it)) }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state
        val from = move.from.col
        if (move.from.row != 0 || from !in 0 until BOARD_CELLS ||
            allLegalMoves(state, state.currentTurn).none { it.from.col == from }
        ) return state

        val counts = counts(state)
        var hand = counts[from]
        counts[from] = 0
        var landing = from
        val mover = state.currentTurn
        val opponentStore = storeFor(mover.opponent())

        while (hand > 0) {
            landing = (landing + 1) % BOARD_CELLS
            if (landing == opponentStore) continue
            counts[landing]++
            hand--
        }

        var captured = 0
        var capturedFrom = -1
        if (ownsPit(mover, landing) && counts[landing] == 1) {
            val opposite = oppositePit(landing)
            if (counts[opposite] > 0) {
                captured = counts[opposite] + counts[landing]
                capturedFrom = opposite
                counts[storeFor(mover)] += captured
                counts[opposite] = 0
                counts[landing] = 0
            }
        }

        val extraTurn = landing == storeFor(mover)
        val sideEnded = sideEmpty(counts, PieceColor.WHITE) || sideEmpty(counts, PieceColor.BLACK)
        if (sideEnded) sweepRemaining(counts)

        val nextTurn = if (extraTurn && !sideEnded) mover else mover.opponent()
        val candidate = stateFrom(counts, nextTurn).copy(
            moveHistory = state.moveHistory + move,
            metadata = state.metadata + mapOf(
                "lastLanding" to landing,
                "extraTurn" to extraTurn,
                "captured" to captured,
                "capturedFrom" to capturedFrom,
                "captureStore" to storeFor(mover),
            ),
        )
        return candidate.copy(status = gameStatus(candidate))
    }

    override fun gameStatus(state: GameState): GameStatus {
        if (!sideEmpty(state, PieceColor.WHITE) && !sideEmpty(state, PieceColor.BLACK)) {
            return GameStatus.IN_PROGRESS
        }
        val southScore = stones(state, SOUTH_STORE)
        val northScore = stones(state, NORTH_STORE)
        return when {
            southScore > northScore -> GameStatus.WHITE_WINS
            northScore > southScore -> GameStatus.BLACK_WINS
            else -> GameStatus.DRAW
        }
    }

    override fun evaluate(state: GameState): Int {
        val south = stones(state, SOUTH_STORE)
        val north = stones(state, NORTH_STORE)
        if (state.status != GameStatus.IN_PROGRESS) return (south - north) * 10_000
        val southPits = (0 until PITS_PER_SIDE).sumOf { stones(state, it) }
        val northPits = (7 until NORTH_STORE).sumOf { stones(state, it) }
        val turnBonus = if (state.currentTurn == PieceColor.WHITE) 2 else -2
        return (south - north) * 100 + (southPits - northPits) + turnBonus
    }

    fun stones(state: GameState, index: Int): Int =
        if (index in 0 until BOARD_CELLS) (state.board[index] as? MancalaPitPiece)?.stones ?: 0 else 0

    fun landing(state: GameState): Int? =
        (state.metadata["lastLanding"] as? Int)

    /** Returns each pit/store visited by the stones from [move], in order. */
    fun sowingPath(state: GameState, move: Move): List<Int> {
        val from = move.from.col
        if (move.from.row != 0 || from !in 0 until BOARD_CELLS) return emptyList()
        var hand = stones(state, from)
        if (!ownsPit(state.currentTurn, from) || hand == 0) return emptyList()

        val skippedStore = storeFor(state.currentTurn.opponent())
        var cursor = from
        val path = ArrayList<Int>(hand)
        while (hand > 0) {
            cursor = (cursor + 1) % BOARD_CELLS
            if (cursor == skippedStore) continue
            path += cursor
            hand--
        }
        return path
    }

    /**
     * Returns the forward board route from one cell to a player's store.
     *
     * Captures are removed from the opposite pit immediately by the rules, but
     * the UI can use this route to show the captured stone travelling forward
     * around the same ring rather than appearing to reverse direction.
     */
    fun forwardPath(from: Int, destination: Int, mover: PieceColor): List<Int> {
        if (from !in 0 until BOARD_CELLS ||
            destination !in 0 until BOARD_CELLS ||
            from == SOUTH_STORE ||
            from == NORTH_STORE ||
            destination != storeFor(mover)
        ) return emptyList()

        return pathBetween(from, destination, mover)
    }

    /**
     * Returns every board cell from [from] through [destination], inclusive,
     * moving in the same direction as [mover]. The opponent's store is never
     * part of the route.
     *
     * This is separate from [sowingPath], which returns only the landing cell
     * for each stone. The UI uses this full route so a stone visibly advances
     * one pit at a time instead of jumping directly to a later destination.
     */
    fun pathBetween(
        from: Int,
        destination: Int,
        mover: PieceColor,
        minimumSteps: Int = 0,
    ): List<Int> {
        if (from !in 0 until BOARD_CELLS ||
            destination !in 0 until BOARD_CELLS ||
            from == SOUTH_STORE ||
            from == NORTH_STORE ||
            destination == storeFor(mover.opponent()) ||
            minimumSteps < 0
        ) return emptyList()

        val skippedStore = storeFor(mover.opponent())
        val path = ArrayList<Int>()
        var cursor = from
        var steps = 0
        path += cursor
        val maxSteps = maxOf(BOARD_CELLS, minimumSteps + BOARD_CELLS)
        while ((cursor != destination || steps < minimumSteps) && steps < maxSteps) {
            cursor = (cursor + 1) % BOARD_CELLS
            if (cursor != skippedStore) {
                path += cursor
                steps++
            }
        }
        return if (cursor == destination && steps >= minimumSteps) path else emptyList()
    }

    private fun counts(state: GameState) = IntArray(BOARD_CELLS) { stones(state, it) }

    private fun stateFrom(counts: IntArray, turn: PieceColor): GameState =
        GameState(
            board = Array(BOARD_CELLS) { index ->
                val owner = if (index <= SOUTH_STORE) PieceColor.WHITE else PieceColor.BLACK
                MancalaPitPiece(owner, counts[index], index == SOUTH_STORE || index == NORTH_STORE)
            },
            boardSize = BOARD_CELLS,
            currentTurn = turn,
            status = GameStatus.IN_PROGRESS,
        )

    private fun sideEmpty(counts: IntArray, color: PieceColor): Boolean =
        if (color == PieceColor.WHITE) {
            (0 until PITS_PER_SIDE).all { counts[it] == 0 }
        } else {
            (7 until NORTH_STORE).all { counts[it] == 0 }
        }

    private fun sideEmpty(state: GameState, color: PieceColor): Boolean =
        sideEmpty(counts(state), color)

    private fun sweepRemaining(counts: IntArray) {
        val southRemaining = (0 until PITS_PER_SIDE).sumOf { counts[it] }
        val northRemaining = (7 until NORTH_STORE).sumOf { counts[it] }
        counts[SOUTH_STORE] += southRemaining
        counts[NORTH_STORE] += northRemaining
        for (index in 0 until PITS_PER_SIDE) counts[index] = 0
        for (index in 7 until NORTH_STORE) counts[index] = 0
    }
}

/** Small alpha-beta player for Mancala. It keeps the stone counts in its score path. */
class MancalaAIPlayer(
    private val engine: MancalaRuleEngine,
    private val maxDepth: Int = 7,
) {
    fun bestMove(state: GameState): Move? {
        val moves = engine.allLegalMoves(state, state.currentTurn)
        if (moves.isEmpty()) return null
        val maximizing = state.currentTurn == PieceColor.WHITE
        var best = moves.first()
        var bestScore = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE
        for (move in moves) {
            val score = search(engine.applyMove(state, move), maxDepth - 1, alpha, beta)
            if ((maximizing && score > bestScore) || (!maximizing && score < bestScore)) {
                bestScore = score
                best = move
            }
            if (maximizing) alpha = maxOf(alpha, bestScore) else beta = minOf(beta, bestScore)
        }
        return best
    }

    private fun search(state: GameState, depth: Int, alphaStart: Int, betaStart: Int): Int {
        if (depth <= 0 || state.status != GameStatus.IN_PROGRESS) return engine.evaluate(state)
        val moves = engine.allLegalMoves(state, state.currentTurn)
        if (moves.isEmpty()) return engine.evaluate(state)
        val maximizing = state.currentTurn == PieceColor.WHITE
        var alpha = alphaStart
        var beta = betaStart
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (move in moves) {
            val score = search(engine.applyMove(state, move), depth - 1, alpha, beta)
            if (maximizing) {
                best = maxOf(best, score)
                alpha = maxOf(alpha, best)
            } else {
                best = minOf(best, score)
                beta = minOf(beta, best)
            }
            if (beta <= alpha) break
        }
        return best
    }
}