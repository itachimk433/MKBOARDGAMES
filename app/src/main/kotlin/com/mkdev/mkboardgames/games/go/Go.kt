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
        currentTurn = PieceColor.WHITE,
    )
}


data class GoPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol(): String = "●"
    override fun value(): Int = 100
}

class GoRuleEngine : RuleEngine {
    companion object {
        const val PASS_METADATA = "pass"
        private const val PASS_COUNT = "passCount"
        private const val KO_INDEX = "koIndex"
        private const val WHITE_CAPTURES = "whiteCaptures"
        private const val BLACK_CAPTURES = "blackCaptures"
        private const val KOMI = 7

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
        if (state.status != GameStatus.IN_PROGRESS || !position.isValid(GoSetup.BOARD_SIZE)) {
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

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state

        if (move.metadata[PASS_METADATA] == true) {
            val passCount = (state.metadata[PASS_COUNT] as? Int ?: 0) + 1
            val nextMetadata = state.metadata + mapOf(
                PASS_COUNT to passCount,
                KO_INDEX to -1,
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

        val capturesForKo = move.captures
        val koIndex = if (capturesForKo.size == 1) {
            val group = groupAt(board, state.boardSize, position)
            if (group.size == 1 && liberties(board, state.boardSize, group).size == 1) {
                capturesForKo.first().row * state.boardSize + capturesForKo.first().col
            } else {
                -1
            }
        } else {
            -1
        }
        val whiteCaptures = (state.metadata[WHITE_CAPTURES] as? Int ?: 0) +
            if (state.currentTurn == PieceColor.WHITE) move.captures.size else 0
        val blackCaptures = (state.metadata[BLACK_CAPTURES] as? Int ?: 0) +
            if (state.currentTurn == PieceColor.BLACK) move.captures.size else 0
        val nextMetadata = state.metadata + mapOf(
            PASS_COUNT to 0,
            KO_INDEX to koIndex,
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
        val white = state.board.count { it?.color == PieceColor.WHITE }
        val black = state.board.count { it?.color == PieceColor.BLACK }
        val whiteCaptures = state.metadata[WHITE_CAPTURES] as? Int ?: 0
        val blackCaptures = state.metadata[BLACK_CAPTURES] as? Int ?: 0
        return (white - black) * 10 + (whiteCaptures - blackCaptures) * 25
    }

    private fun placementMove(state: GameState, position: Position): Move? {
        if (state.get(position) != null) return null
        val koIndex = state.metadata[KO_INDEX] as? Int ?: -1
        if (position.row * state.boardSize + position.col == koIndex) return null

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

    private fun scoreStatus(board: Array<Piece?>, size: Int): GameStatus {
        var whiteScore = board.count { it?.color == PieceColor.WHITE }
        var blackScore = board.count { it?.color == PieceColor.BLACK } + KOMI
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
                    if (borderingColors.single() == PieceColor.WHITE) whiteScore += region.size
                    else blackScore += region.size
                }
            }
        }
        return when {
            whiteScore > blackScore -> GameStatus.WHITE_WINS
            blackScore > whiteScore -> GameStatus.BLACK_WINS
            else -> GameStatus.DRAW
        }
    }
}