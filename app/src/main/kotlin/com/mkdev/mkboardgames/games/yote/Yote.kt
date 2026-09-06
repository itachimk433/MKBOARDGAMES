package com.mkdev.mkboardgames.games.yote

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

data class YotePiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 100
}

/**
 * Yoté is played on a 5 × 6 board. Pieces enter from a reserve at any time,
 * and a jump capture earns the player one extra capture anywhere on the board.
 */
class YoteRuleEngine : RuleEngine {
    companion object {
        const val ROWS = 5
        const val COLUMNS = 6
        const val BOARD_CELLS = ROWS * COLUMNS
        const val RESERVE_ROW = -1
        const val PIECES_PER_PLAYER = 12
        val RESERVE = Position(RESERVE_ROW, 0)

        private val DIRECTIONS = listOf(
            Position(-1, 0),
            Position(1, 0),
            Position(0, -1),
            Position(0, 1),
        )

        fun indexOf(position: Position): Int =
            if (position.row in 0 until ROWS && position.col in 0 until COLUMNS) {
                position.row * COLUMNS + position.col
            } else {
                -1
            }

        fun positionAt(index: Int): Position =
            Position(index / COLUMNS, index % COLUMNS)
    }

    override fun initialState(): GameState = GameState(
        board = arrayOfNulls(BOARD_CELLS),
        boardSize = COLUMNS,
        currentTurn = PieceColor.WHITE,
        status = GameStatus.IN_PROGRESS,
        hands = mapOf(
            PieceColor.WHITE to List(PIECES_PER_PLAYER) { YotePiece(PieceColor.WHITE) },
            PieceColor.BLACK to List(PIECES_PER_PLAYER) { YotePiece(PieceColor.BLACK) },
        ),
    )

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return if (position == RESERVE) {
            placementMoves(state, state.currentTurn)
        } else {
            movementMoves(state, position, state.currentTurn)
        }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return placementMoves(state, color) +
            (0 until BOARD_CELLS).flatMap { index ->
                movementMoves(state, positionAt(index), color)
            }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state
        val mover = state.currentTurn
        val legal = allLegalMoves(state, mover)
        val matching = legal.firstOrNull {
            it.from == move.from && it.to == move.to && it.captures == move.captures
        } ?: legal.firstOrNull { it.from == move.from && it.to == move.to }
            ?: return state

        val board = state.board.copyOf()
        val hands = state.hands.mapValues { (_, pieces) -> pieces.toList() }.toMutableMap()
        val captures = matching.captures.toMutableList()

        if (matching.from == RESERVE) {
            val reserve = hands[mover].orEmpty()
            if (reserve.isEmpty()) return state
            board[indexOf(matching.to)] = YotePiece(mover)
            hands[mover] = reserve.drop(1)
        } else {
            val fromIndex = indexOf(matching.from)
            val toIndex = indexOf(matching.to)
            val piece = board.getOrNull(fromIndex) as? YotePiece ?: return state
            if (piece.color != mover || board[toIndex] != null) return state
            board[fromIndex] = null
            board[toIndex] = piece
        }

        captures.forEach { captured ->
            val captureIndex = indexOf(captured)
            if (captureIndex >= 0 && board[captureIndex]?.color == mover.opponent()) {
                board[captureIndex] = null
            }
        }

        val bonusCapture = move.metadata["bonusCapture"] as? Position
        if (matching.isCapture && bonusCapture != null && bonusCapture !in captures) {
            val bonusIndex = indexOf(bonusCapture)
            if (bonusIndex >= 0 && board[bonusIndex]?.color == mover.opponent()) {
                board[bonusIndex] = null
                captures += bonusCapture
            }
        }

        val next = state.copy(
            board = board,
            currentTurn = mover.opponent(),
            status = GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + move.copy(
                captures = captures,
                metadata = if (bonusCapture != null) {
                    move.metadata + ("bonusCapture" to bonusCapture)
                } else {
                    move.metadata
                },
            ),
            metadata = mapOf<String, Any>(
                "lastMoveWasCapture" to matching.isCapture,
                "lastCaptureCount" to captures.size,
                "lastBonusCapture" to bonusCapture ?: Position(-1, -1),
            ),
            hands = hands,
        )
        return next.copy(status = gameStatus(next))
    }

    override fun gameStatus(state: GameState): GameStatus {
        val whiteOnBoard = countOnBoard(state, PieceColor.WHITE)
        val blackOnBoard = countOnBoard(state, PieceColor.BLACK)
        val whiteReserve = state.hands[PieceColor.WHITE].orEmpty().size
        val blackReserve = state.hands[PieceColor.BLACK].orEmpty().size
        return when {
            whiteOnBoard + whiteReserve == 0 -> GameStatus.BLACK_WINS
            blackOnBoard + blackReserve == 0 -> GameStatus.WHITE_WINS
            state.hands[state.currentTurn].orEmpty().isEmpty() &&
                allLegalMoves(state, state.currentTurn).isEmpty() ->
                if (allLegalMoves(state, state.currentTurn.opponent()).isNotEmpty()) {
                    if (state.currentTurn == PieceColor.WHITE) GameStatus.BLACK_WINS
                    else GameStatus.WHITE_WINS
                } else if (whiteOnBoard > blackOnBoard) {
                    GameStatus.WHITE_WINS
                } else if (blackOnBoard > whiteOnBoard) {
                    GameStatus.BLACK_WINS
                } else {
                    GameStatus.DRAW
                }
            else -> GameStatus.IN_PROGRESS
        }
    }

    override fun evaluate(state: GameState): Int {
        if (state.status != GameStatus.IN_PROGRESS) {
            return when (state.status) {
                GameStatus.WHITE_WINS -> 100_000
                GameStatus.BLACK_WINS -> -100_000
                GameStatus.DRAW -> 0
                GameStatus.IN_PROGRESS -> 0
            }
        }
        val material = countOnBoard(state, PieceColor.WHITE) -
            countOnBoard(state, PieceColor.BLACK)
        val reserve = state.hands[PieceColor.WHITE].orEmpty().size -
            state.hands[PieceColor.BLACK].orEmpty().size
        val mobility = allLegalMoves(state, PieceColor.WHITE).size -
            allLegalMoves(state, PieceColor.BLACK).size
        val turnBonus = if (state.currentTurn == PieceColor.WHITE) 2 else -2
        return material * 120 + reserve * 8 + mobility * 3 + turnBonus
    }

    /**
     * Returns the opponent's pieces available for Yoté's mandatory extra
     * capture after [move]. The original jump capture is removed first.
     */
    fun availableBonusCaptures(state: GameState, move: Move): List<Position> {
        if (!move.isCapture || move.metadata.containsKey("bonusCapture")) return emptyList()
        val primary = applyPrimaryCapture(state, move) ?: return emptyList()
        return (0 until BOARD_CELLS)
            .map(::positionAt)
            .filter { primary.get(it)?.color == state.currentTurn.opponent() }
    }

    fun completeBonusCapture(state: GameState, move: Move, position: Position): Move =
        move.copy(metadata = move.metadata + ("bonusCapture" to position))

    fun reserveCount(state: GameState, color: PieceColor): Int =
        state.hands[color].orEmpty().size

    fun countOnBoard(state: GameState, color: PieceColor): Int =
        state.board.count { it?.color == color }

    private fun placementMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.hands[color].orEmpty().isEmpty()) return emptyList()
        return (0 until BOARD_CELLS)
            .map(::positionAt)
            .filter { state.get(it) == null }
            .map { Move(RESERVE, it, metadata = mapOf("placement" to true)) }
    }

    private fun movementMoves(
        state: GameState,
        from: Position,
        color: PieceColor,
    ): List<Move> {
        if (state.get(from)?.color != color) return emptyList()
        return buildList {
            DIRECTIONS.forEach { direction ->
                val adjacent = from + direction
                if (!isOnBoard(adjacent)) return@forEach
                if (state.get(adjacent) == null) {
                    add(Move(from, adjacent))
                } else if (state.get(adjacent)?.color == color.opponent()) {
                    val landing = adjacent + direction
                    if (isOnBoard(landing) && state.get(landing) == null) {
                        add(
                            Move(
                                from = from,
                                to = landing,
                                captures = listOf(adjacent),
                                metadata = mapOf("jump" to true),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun isOnBoard(position: Position): Boolean =
        position.row in 0 until ROWS && position.col in 0 until COLUMNS

    private fun applyPrimaryCapture(state: GameState, move: Move): GameState? {
        val matching = allLegalMoves(state, state.currentTurn).firstOrNull {
            it.from == move.from && it.to == move.to
        } ?: return null
        if (!matching.isCapture) return null
        val board = state.board.copyOf()
        if (matching.from == RESERVE) return null
        board[indexOf(matching.from)] = null
        board[indexOf(matching.to)] = YotePiece(state.currentTurn)
        matching.captures.forEach { board[indexOf(it)] = null }
        return state.copy(board = board)
    }
}

class YoteAIPlayer(
    private val engine: YoteRuleEngine,
    private val maxDepth: Int = 4,
) {
    fun bestMove(state: GameState): Move? {
        val candidates = expandTurns(state)
        if (candidates.isEmpty()) return null
        val maximizing = state.currentTurn == PieceColor.WHITE
        var best = candidates.first()
        var bestScore = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE
        candidates.forEach { move ->
            val score = search(engine.applyMove(state, move), maxDepth - 1, alpha, beta)
            if ((maximizing && score > bestScore) || (!maximizing && score < bestScore)) {
                best = move
                bestScore = score
            }
            if (maximizing) alpha = maxOf(alpha, bestScore) else beta = minOf(beta, bestScore)
        }
        return best
    }

    private fun search(state: GameState, depth: Int, alphaStart: Int, betaStart: Int): Int {
        if (depth <= 0 || state.status != GameStatus.IN_PROGRESS) return engine.evaluate(state)
        val moves = expandTurns(state)
        if (moves.isEmpty()) return engine.evaluate(state)
        val maximizing = state.currentTurn == PieceColor.WHITE
        var alpha = alphaStart
        var beta = betaStart
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        moves.forEach { move ->
            val score = search(engine.applyMove(state, move), depth - 1, alpha, beta)
            if (maximizing) {
                best = maxOf(best, score)
                alpha = maxOf(alpha, best)
            } else {
                best = minOf(best, score)
                beta = minOf(beta, best)
            }
            if (beta <= alpha) return best
        }
        return best
    }

    private fun expandTurns(state: GameState): List<Move> =
        engine.allLegalMoves(state, state.currentTurn).flatMap { move ->
            val bonus = engine.availableBonusCaptures(state, move)
            if (bonus.isEmpty()) listOf(move)
            else bonus.map { engine.completeBonusCapture(state, move, it) }
        }
}