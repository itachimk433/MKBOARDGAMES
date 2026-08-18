package com.mkdev.mkboardgames.games.go

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

object GoSetup {
    const val BOARD_SIZE = 13

    fun initialState(): GameState = GameState(
        board = arrayOfNulls(BOARD_SIZE * BOARD_SIZE),
        boardSize = BOARD_SIZE,
        currentTurn = PieceColor.BLACK,
    )
}


data class GoPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol(): String = "●"
    override fun value(): Int = 100
}

data class GoScoreBreakdown(
    val stones: Int,
    val territory: Int,
    val captures: Int,
    val komi: Double,
    val total: Double,
)

data class GoScoreSummary(
    val black: GoScoreBreakdown,
    val white: GoScoreBreakdown,
)

class GoRuleEngine : RuleEngine {
    companion object {
        const val PASS_METADATA = "pass"
        private const val PASS_COUNT = "passCount"
        private const val POSITION_HISTORY = "positionHistory"
        private const val WHITE_CAPTURES = "whiteCaptures"
        private const val BLACK_CAPTURES = "blackCaptures"
        private const val KOMI = 6.5

        fun passMove(): Move = Move(
            from = Position(-1, -1),
            to = Position(-1, -1),
            metadata = mapOf(PASS_METADATA to true),
        )
    }

    private val directions = arrayOf(
        Position(-1, 0), Position(1, 0),
        Position(0, -1), Position(0, 1),
    )

    override fun initialState(): GameState = GoSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS || !position.isValid(state.boardSize)) {
            return emptyList()
        }
        return placementMove(state, position)?.let { listOf(it) }.orEmpty()
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS || color != state.currentTurn) return emptyList()
        val moves = mutableListOf<Move>()
        for (row in 0 until state.boardSize) {
            for (col in 0 until state.boardSize) {
                placementMove(state, Position(row, col))?.let(moves::add)
            }
        }
        // Passing is always legal and is required to finish a position cleanly.
        moves += passMove()
        return moves
    }

    /**
     * Returns whether the current player has at least one legal stone placement.
     * Passing is intentionally excluded because it is always legal by rule.
     */
    fun hasLegalPlacement(state: GameState): Boolean =
        allLegalMoves(state, state.currentTurn)
            .any { it.metadata[PASS_METADATA] != true }

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state

        if (move.metadata[PASS_METADATA] == true) {
            val passCount = (state.metadata[PASS_COUNT] as? Int ?: 0) + 1
            val nextMetadata = state.metadata + mapOf(
                PASS_COUNT to passCount,
                POSITION_HISTORY to positionHistory(state),
            )
            val nextStatus = if (passCount >= 2) scoreStatus(state.board, state.boardSize)
            else GameStatus.IN_PROGRESS
            return state.copy(
                currentTurn = state.currentTurn.opponent(),
                status = nextStatus,
                moveHistory = state.moveHistory + move,
                metadata = nextMetadata,
            )
        }

        val position = move.to
        if (!position.isValid(state.boardSize) || state.get(position) != null) return state

        val board = state.board.copyOf()
        board[position.row * state.boardSize + position.col] = GoPiece(state.currentTurn)
        for (capture in move.captures) {
            if (capture.isValid(state.boardSize)) {
                board[capture.row * state.boardSize + capture.col] = null
            }
        }

        val whiteCaptures = (state.metadata[WHITE_CAPTURES] as? Int ?: 0) +
            if (state.currentTurn == PieceColor.WHITE) move.captures.size else 0
        val blackCaptures = (state.metadata[BLACK_CAPTURES] as? Int ?: 0) +
            if (state.currentTurn == PieceColor.BLACK) move.captures.size else 0
        val nextMetadata = state.metadata + mapOf(
            PASS_COUNT to 0,
            POSITION_HISTORY to positionHistory(state) + boardKey(board),
            WHITE_CAPTURES to whiteCaptures,
            BLACK_CAPTURES to blackCaptures,
        )

        return state.copy(
            board = board,
            currentTurn = state.currentTurn.opponent(),
            status = if (board.all { it != null }) scoreStatus(board, state.boardSize)
            else GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + move,
            metadata = nextMetadata,
        )
    }

    override fun gameStatus(state: GameState): GameStatus {
        if (state.status != GameStatus.IN_PROGRESS) return state.status
        if ((state.metadata[PASS_COUNT] as? Int ?: 0) >= 2 || state.board.all { it != null }) {
            return scoreStatus(state.board, state.boardSize)
        }
        return GameStatus.IN_PROGRESS
    }

    override fun evaluate(state: GameState): Int {
        val summary = scoreSummary(state)
        return ((summary.white.total - summary.black.total) * 100.0).toInt() +
            (summary.white.captures - summary.black.captures) * 8
    }

    fun scoreSummary(state: GameState): GoScoreSummary {
        val blackStones = state.board.count { it?.color == PieceColor.BLACK }
        val whiteStones = state.board.count { it?.color == PieceColor.WHITE }
        val (blackTerritory, whiteTerritory) = territoryTotals(state.board, state.boardSize)
        val blackCaptures = capturesFor(state, PieceColor.BLACK)
        val whiteCaptures = capturesFor(state, PieceColor.WHITE)
        val blackTotal = (blackStones + blackTerritory).toDouble()
        val whiteTotal = (whiteStones + whiteTerritory).toDouble() + KOMI
        return GoScoreSummary(
            black = GoScoreBreakdown(
                stones = blackStones,
                territory = blackTerritory,
                captures = blackCaptures,
                komi = 0.0,
                total = blackTotal,
            ),
            white = GoScoreBreakdown(
                stones = whiteStones,
                territory = whiteTerritory,
                captures = whiteCaptures,
                komi = KOMI,
                total = whiteTotal,
            ),
        )
    }

    fun capturesFor(state: GameState, color: PieceColor): Int {
        val key = if (color == PieceColor.WHITE) WHITE_CAPTURES else BLACK_CAPTURES
        return state.metadata[key] as? Int ?: 0
    }

    private fun placementMove(state: GameState, position: Position): Move? {
        if (state.get(position) != null) return null

        val board = state.board.copyOf()
        board[position.row * state.boardSize + position.col] = GoPiece(state.currentTurn)
        val captures = mutableListOf<Position>()
        val opponent = state.currentTurn.opponent()

        for (neighbor in neighbors(position, state.boardSize)) {
            if (board[neighbor.row * state.boardSize + neighbor.col]?.color != opponent) continue
            val enemyGroup = groupAt(board, state.boardSize, neighbor)
            if (liberties(board, state.boardSize, enemyGroup).isEmpty()) {
                captures += enemyGroup
                enemyGroup.forEach { board[it.row * state.boardSize + it.col] = null }
            }
        }

        val ownGroup = groupAt(board, state.boardSize, position)
        if (liberties(board, state.boardSize, ownGroup).isEmpty()) return null
        // Positional superko: a legal move may not recreate any earlier board
        // position, not only the immediately previous one.
        if (boardKey(board) in positionHistory(state)) return null
        return Move(from = position, to = position, captures = captures)
    }

    private fun neighbors(position: Position, size: Int): List<Position> =
        directions.map { position + it }.filter { it.isValid(size) }

    private fun groupAt(board: Array<Piece?>, size: Int, start: Position): Set<Position> {
        val color = board[start.row * size + start.col]?.color ?: return emptySet()
        val group = linkedSetOf<Position>()
        val pending = ArrayDeque<Position>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val position = pending.removeFirst()
            if (!group.add(position)) continue
            for (neighbor in neighbors(position, size)) {
                if (board[neighbor.row * size + neighbor.col]?.color == color &&
                    neighbor !in group
                ) {
                    pending.add(neighbor)
                }
            }
        }
        return group
    }

    private fun liberties(
        board: Array<Piece?>,
        size: Int,
        group: Set<Position>,
    ): Set<Position> {
        val result = linkedSetOf<Position>()
        for (position in group) {
            for (neighbor in neighbors(position, size)) {
                if (board[neighbor.row * size + neighbor.col] == null) result += neighbor
            }
        }
        return result
    }

    private fun boardKey(board: Array<Piece?>): String =
        buildString(board.size) {
            board.forEach { piece ->
                append(
                    when (piece?.color) {
                        PieceColor.WHITE -> 'w'
                        PieceColor.BLACK -> 'b'
                        null -> '.'
                    },
                )
            }
        }

    private fun positionHistory(state: GameState): List<String> =
        (state.metadata[POSITION_HISTORY] as? List<*>)
            ?.filterIsInstance<String>()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(boardKey(state.board))

    private fun scoreStatus(board: Array<Piece?>, size: Int): GameStatus {
        val (blackTerritory, whiteTerritory) = territoryTotals(board, size)
        val whiteScore = board.count { it?.color == PieceColor.WHITE }.toDouble() +
            whiteTerritory + KOMI
        val blackScore = board.count { it?.color == PieceColor.BLACK }.toDouble() +
            blackTerritory
        return when {
            whiteScore > blackScore -> GameStatus.WHITE_WINS
            blackScore > whiteScore -> GameStatus.BLACK_WINS
            else -> GameStatus.DRAW
        }
    }

    private fun territoryTotals(board: Array<Piece?>, size: Int): Pair<Int, Int> {
        var blackTerritory = 0
        var whiteTerritory = 0
        val visited = mutableSetOf<Position>()
        for (row in 0 until size) {
            for (col in 0 until size) {
                val start = Position(row, col)
                if (board[row * size + col] != null || start in visited) continue
                val region = linkedSetOf<Position>()
                val borderingColors = mutableSetOf<PieceColor>()
                val pending = ArrayDeque<Position>()
                pending.add(start)
                while (pending.isNotEmpty()) {
                    val position = pending.removeFirst()
                    if (!visited.add(position)) continue
                    region += position
                    for (neighbor in neighbors(position, size)) {
                        val piece = board[neighbor.row * size + neighbor.col]
                        if (piece == null && neighbor !in visited) pending.add(neighbor)
                        else piece?.color?.let { borderingColors += it }
                    }
                }
                if (borderingColors.size == 1) {
                    if (borderingColors.single() == PieceColor.WHITE) whiteTerritory += region.size
                    else blackTerritory += region.size
                }
            }
        }
        return blackTerritory to whiteTerritory
    }
}