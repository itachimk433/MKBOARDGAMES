package com.mkdev.mkboardgames.games.amazons

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

/**
 * Amazons: move one amazon like a chess queen, then fire an arrow from its
 * destination like a queen. The arrow permanently blocks the landing square.
 */
class AmazonsRuleEngine(val boardSize: Int) : com.mkdev.mkboardgames.engine.RuleEngine {

    companion object {
        const val ARROW_METADATA = "arrow"
        private val DIRECTIONS = listOf(
            Position(-1, -1), Position(-1, 0), Position(-1, 1),
            Position(0, -1), Position(0, 1),
            Position(1, -1), Position(1, 0), Position(1, 1),
        )
    }

    init {
        require(boardSize == 8 || boardSize == 10) {
            "Amazons supports only 8×8 and 10×10 boards"
        }
    }

    override fun initialState(): GameState = AmazonsSetup.initialState(boardSize)

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val amazon = state.get(position) as? AmazonsPiece ?: return emptyList()
        if (amazon.type != AmazonsPieceType.AMAZON ||
            amazon.color != state.currentTurn
        ) return emptyList()

        val moves = mutableListOf<Move>()
        for (destination in raySquares(state, position)) {
            val movedBoard = state.board.copyOf()
            movedBoard[position.row * boardSize + position.col] = null
            movedBoard[destination.row * boardSize + destination.col] = amazon
            val movedState = state.copy(board = movedBoard)

            for (arrow in raySquares(movedState, destination)) {
                moves += Move(
                    from = position,
                    to = destination,
                    metadata = mapOf(ARROW_METADATA to arrow),
                )
            }
        }
        return moves
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> =
        state.board.indices.flatMap { index ->
            val position = Position(index / boardSize, index % boardSize)
            if ((state.get(position) as? AmazonsPiece)?.let {
                    it.type == AmazonsPieceType.AMAZON && it.color == color
                } == true
            ) {
                legalMovesFrom(state.copy(currentTurn = color), position)
            } else {
                emptyList()
            }
        }

    override fun applyMove(state: GameState, move: Move): GameState {
        return applyMoveInternal(state, move, calculateStatus = true)
    }

    /**
     * Apply a move without recalculating mobility. AI search calls this because
     * Amazons has a large branching factor and only needs the resulting board
     * to evaluate a candidate. The public rule-engine path still calculates
     * status so the live game ends immediately when a player is trapped.
     */
    fun applyMoveForSearch(state: GameState, move: Move): GameState =
        applyMoveInternal(state, move, calculateStatus = false)

    private fun applyMoveInternal(
        state: GameState,
        move: Move,
        calculateStatus: Boolean,
    ): GameState {
        val amazon = state.get(move.from) as? AmazonsPiece
            ?: error("Amazons move must start on an amazon")
        val arrow = move.metadata[ARROW_METADATA] as? Position
            ?: error("Amazons move must include an arrow position")
        require(move.to.isValid(boardSize) && arrow.isValid(boardSize)) {
            "Amazons move is outside the board"
        }
        require(state.get(move.to) == null) {
            "Amazons move must land on empty squares"
        }
        require(isClearRay(state, move.from, move.to)) {
            "Amazon path is blocked"
        }

        val movedBoard = state.board.copyOf()
        movedBoard[move.from.row * boardSize + move.from.col] = null
        movedBoard[move.to.row * boardSize + move.to.col] = amazon

        val arrowState = state.copy(board = movedBoard)
        require(arrowState.get(arrow) == null) {
            "Arrow must land on an empty square"
        }
        require(isClearRay(arrowState, move.to, arrow)) {
            "Arrow path is blocked"
        }
        movedBoard[arrow.row * boardSize + arrow.col] =
            AmazonsPiece(AmazonsPieceType.ARROW, amazon.color)

        val nextState = state.withBoard(
            newBoard = movedBoard,
            turn = amazon.color.opponent(),
            move = move,
        )
        return if (calculateStatus) nextState.copy(status = gameStatus(nextState)) else nextState
    }

    override fun gameStatus(state: GameState): GameStatus {
        if (allLegalMoves(state, state.currentTurn).isNotEmpty()) {
            return GameStatus.IN_PROGRESS
        }
        return if (state.currentTurn == PieceColor.WHITE) {
            GameStatus.BLACK_WINS
        } else {
            GameStatus.WHITE_WINS
        }
    }

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.WHITE_WINS) return 100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW) return 0

        val whiteMobility = allLegalMoves(state, PieceColor.WHITE).size
        val blackMobility = allLegalMoves(state, PieceColor.BLACK).size
        val whiteAmazonCount = state.board.count {
            it is AmazonsPiece &&
                it.type == AmazonsPieceType.AMAZON &&
                it.color == PieceColor.WHITE
        }
        val blackAmazonCount = state.board.count {
            it is AmazonsPiece &&
                it.type == AmazonsPieceType.AMAZON &&
                it.color == PieceColor.BLACK
        }
        return (whiteMobility - blackMobility) +
            (whiteAmazonCount - blackAmazonCount) * 100
    }

    /**
     * A cheaper mobility evaluation for bounded AI search. The public
     * evaluation counts complete move objects for both sides; doing that at
     * every search leaf is needlessly expensive for Amazons, where each move
     * contains a second queen ray for the arrow. Search only needs the side
     * to move's mobility, with the sign flipped to White's perspective.
     */
    fun evaluateForSearch(state: GameState): Int {
        val mobility = mobilityCount(state, state.currentTurn)
        if (mobility == 0) {
            return if (state.currentTurn == PieceColor.WHITE) -100_000 else 100_000
        }
        val whiteAmazonCount = state.board.count {
            it is AmazonsPiece &&
                it.type == AmazonsPieceType.AMAZON &&
                it.color == PieceColor.WHITE
        }
        val blackAmazonCount = state.board.count {
            it is AmazonsPiece &&
                it.type == AmazonsPieceType.AMAZON &&
                it.color == PieceColor.BLACK
        }
        return (if (state.currentTurn == PieceColor.WHITE) -mobility else mobility) +
            (whiteAmazonCount - blackAmazonCount) * 100
    }

    private fun mobilityCount(state: GameState, color: PieceColor): Int {
        var count = 0
        state.board.indices.forEach { index ->
            val position = Position(index / boardSize, index % boardSize)
            val amazon = state.get(position) as? AmazonsPiece ?: return@forEach
            if (amazon.type != AmazonsPieceType.AMAZON || amazon.color != color) return@forEach
            raySquares(state, position).forEach { destination ->
                val movedBoard = state.board.copyOf()
                movedBoard[position.row * boardSize + position.col] = null
                movedBoard[destination.row * boardSize + destination.col] = amazon
                count += raySquares(state.copy(board = movedBoard), destination).size
            }
        }
        return count
    }

    private fun raySquares(state: GameState, start: Position): List<Position> {
        val squares = mutableListOf<Position>()
        for (direction in DIRECTIONS) {
            var current = start + direction
            while (current.isValid(boardSize) && state.get(current) == null) {
                squares += current
                current += direction
            }
        }
        return squares
    }

    private fun isClearRay(state: GameState, from: Position, to: Position): Boolean =
        raySquares(state, from).contains(to)
}