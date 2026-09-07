package com.mkdev.mkboardgames.games.onitama

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

data class OnitamaPiece(
    override val color: PieceColor,
    val isMaster: Boolean,
) : Piece(color) {
    override fun symbol() = if (isMaster) "M" else "S"
    override fun value() = if (isMaster) 500 else 100
}

data class OnitamaCard(
    val id: String,
    val name: String,
    val moves: List<Position>,
    val assetName: String? = null,
)

object OnitamaCards {
    /**
     * Coordinates are from White's perspective: row -1 points toward Black's
     * side of the mat. Black mirrors both axes when using a card.
     */
    val all: List<OnitamaCard> = listOf(
        OnitamaCard("tiger", "Tiger", listOf(Position(-2, 0), Position(1, 0))),
        OnitamaCard("dragon", "Dragon", listOf(Position(-1, -2), Position(-1, 2), Position(1, -1), Position(1, 1)), "onitama_card_dragon.webp"),
        OnitamaCard("frog", "Frog", listOf(Position(-1, -1), Position(0, -2), Position(1, 1))),
        OnitamaCard("rabbit", "Rabbit", listOf(Position(-1, 1), Position(0, 2), Position(1, -1))),
        OnitamaCard("crab", "Crab", listOf(Position(-1, 0), Position(0, -2), Position(0, 2))),
        OnitamaCard("elephant", "Elephant", listOf(Position(-1, -1), Position(-1, 1), Position(0, -1), Position(0, 1))),
        OnitamaCard("goose", "Goose", listOf(Position(-1, -1), Position(0, -1), Position(0, 1), Position(1, 1))),
        OnitamaCard("rooster", "Rooster", listOf(Position(-1, 1), Position(0, -1), Position(0, 1), Position(1, -1))),
        OnitamaCard("monkey", "Monkey", listOf(Position(-1, -1), Position(-1, 1), Position(1, -1), Position(1, 1)), "onitama_card_monkey.webp"),
        OnitamaCard("mantis", "Mantis", listOf(Position(-1, -1), Position(-1, 1), Position(1, 0)), "onitama_card_mantis.webp"),
        OnitamaCard("horse", "Horse", listOf(Position(0, -1), Position(-1, 0), Position(1, 0))),
        OnitamaCard("ox", "Ox", listOf(Position(-1, 0), Position(0, 1), Position(1, 0))),
        OnitamaCard("crane", "Crane", listOf(Position(-1, 0), Position(1, -1), Position(1, 1))),
        OnitamaCard("boar", "Boar", listOf(Position(-1, 0), Position(0, -1), Position(0, 1))),
        OnitamaCard("cobra", "Cobra", listOf(Position(-1, 1), Position(0, -1), Position(1, 1))),
        OnitamaCard("eel", "Eel", listOf(Position(-1, -1), Position(0, 1), Position(1, -1)), "onitama_card_eel.webp"),
    )

    val byId: Map<String, OnitamaCard> = all.associateBy { it.id }
}

class OnitamaRuleEngine : RuleEngine {
    companion object {
        const val BOARD_SIZE = 5
        const val BOARD_CELLS = BOARD_SIZE * BOARD_SIZE
        const val WHITE_CARDS = "whiteCards"
        const val BLACK_CARDS = "blackCards"
        const val SIDE_CARD = "sideCard"
        val WHITE_TEMPLE = Position(4, 2)
        val BLACK_TEMPLE = Position(0, 2)

        // These are the supplied cards, plus one standard card to complete the
        // five-card starting pool required by the game rules.
        private val STARTING_WHITE_CARDS = listOf("eel", "monkey")
        private val STARTING_BLACK_CARDS = listOf("dragon", "mantis")
        private const val STARTING_SIDE_CARD = "tiger"
    }

    override fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_CELLS)
        listOf(0, 1, 3, 4).forEach { col ->
            board[col] = OnitamaPiece(PieceColor.BLACK, isMaster = false)
            board[4 * BOARD_SIZE + col] = OnitamaPiece(PieceColor.WHITE, isMaster = false)
        }
        board[2] = OnitamaPiece(PieceColor.BLACK, isMaster = true)
        board[4 * BOARD_SIZE + 2] = OnitamaPiece(PieceColor.WHITE, isMaster = true)
        return GameState(
            board = board,
            boardSize = BOARD_SIZE,
            currentTurn = PieceColor.WHITE,
            status = GameStatus.IN_PROGRESS,
            metadata = mapOf(
                WHITE_CARDS to STARTING_WHITE_CARDS,
                BLACK_CARDS to STARTING_BLACK_CARDS,
                SIDE_CARD to STARTING_SIDE_CARD,
            ),
        )
    }

    fun cards(state: GameState, color: PieceColor): List<OnitamaCard> =
        cardIds(state, color).mapNotNull(OnitamaCards.byId::get)

    fun sideCard(state: GameState): OnitamaCard =
        OnitamaCards.byId[state.metadata[SIDE_CARD] as? String] ?: OnitamaCards.byId.getValue(STARTING_SIDE_CARD)

    fun cardFor(id: String): OnitamaCard? = OnitamaCards.byId[id]

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> =
        allLegalMoves(state, state.currentTurn).filter { it.from == position }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return cards(state, color).flatMap { card ->
            (0 until BOARD_CELLS).flatMap { index ->
                val from = Position(index / BOARD_SIZE, index % BOARD_SIZE)
                val piece = state.get(from) as? OnitamaPiece
                if (piece?.color == color) {
                    card.moves.mapNotNull { offset ->
                        val oriented = orient(offset, color)
                        val to = from + oriented
                        if (!to.isValid(BOARD_SIZE) || state.get(to)?.color == color) {
                            null
                        } else {
                            Move(from, to, metadata = mapOf("card" to card.id))
                        }
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        if (state.status != GameStatus.IN_PROGRESS) return state
        val cardId = move.metadata["card"] as? String ?: return state
        val legal = allLegalMoves(state, state.currentTurn).firstOrNull {
            it.from == move.from && it.to == move.to && it.metadata["card"] == cardId
        } ?: return state

        val mover = state.currentTurn
        val board = state.board.copyOf()
        val movingPiece = board[indexOf(legal.from)] as? OnitamaPiece ?: return state
        board[indexOf(legal.from)] = null
        board[indexOf(legal.to)] = movingPiece

        val oldCards = cardIds(state, mover)
        val oldSide = state.metadata[SIDE_CARD] as? String ?: STARTING_SIDE_CARD
        val nextCards = oldCards.map { if (it == cardId) oldSide else it }
        val metadata = state.metadata.toMutableMap().apply {
            this[cardKey(mover)] = nextCards
            this[SIDE_CARD] = cardId
        }
        val next = state.copy(
            board = board,
            currentTurn = mover.opponent(),
            status = GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + legal,
            metadata = metadata,
        )
        return next.copy(status = gameStatus(next))
    }

    override fun gameStatus(state: GameState): GameStatus {
        val whiteMaster = masterPosition(state, PieceColor.WHITE)
        val blackMaster = masterPosition(state, PieceColor.BLACK)
        return when {
            whiteMaster == null -> GameStatus.BLACK_WINS
            blackMaster == null -> GameStatus.WHITE_WINS
            whiteMaster == BLACK_TEMPLE -> GameStatus.WHITE_WINS
            blackMaster == WHITE_TEMPLE -> GameStatus.BLACK_WINS
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
        val whiteMaster = masterPosition(state, PieceColor.WHITE)
        val blackMaster = masterPosition(state, PieceColor.BLACK)
        if (whiteMaster == null) return -100_000
        if (blackMaster == null) return 100_000
        val material = countPieces(state, PieceColor.WHITE) - countPieces(state, PieceColor.BLACK)
        val whiteTempleDistance = whiteMaster.row
        val blackTempleDistance = BOARD_SIZE - 1 - blackMaster.row
        val mobility = allLegalMoves(state, PieceColor.WHITE).size -
            allLegalMoves(state, PieceColor.BLACK).size
        return material * 45 + (blackTempleDistance - whiteTempleDistance) * 18 + mobility * 2
    }

    fun masterPosition(state: GameState, color: PieceColor): Position? =
        (0 until BOARD_CELLS)
            .map { Position(it / BOARD_SIZE, it % BOARD_SIZE) }
            .firstOrNull { (state.get(it) as? OnitamaPiece)?.let { piece -> piece.color == color && piece.isMaster } == true }

    fun countPieces(state: GameState, color: PieceColor): Int =
        state.board.count { (it as? OnitamaPiece)?.color == color }

    private fun cardIds(state: GameState, color: PieceColor): List<String> =
        (state.metadata[cardKey(color)] as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun cardKey(color: PieceColor): String =
        if (color == PieceColor.WHITE) WHITE_CARDS else BLACK_CARDS

    private fun orient(offset: Position, color: PieceColor): Position =
        if (color == PieceColor.WHITE) offset else Position(-offset.row, -offset.col)

    private fun indexOf(position: Position): Int = position.row * BOARD_SIZE + position.col
}

class OnitamaAIPlayer(
    private val engine: OnitamaRuleEngine,
    private val maxDepth: Int = 3,
) {
    fun bestMove(state: GameState): Move? {
        val moves = engine.allLegalMoves(state, state.currentTurn)
        if (moves.isEmpty()) return null
        val maximizing = state.currentTurn == PieceColor.WHITE
        var bestMove = moves.first()
        var bestScore = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE
        moves.forEach { move ->
            val score = search(engine.applyMove(state, move), maxDepth - 1, alpha, beta)
            if ((maximizing && score > bestScore) || (!maximizing && score < bestScore)) {
                bestMove = move
                bestScore = score
            }
            if (maximizing) alpha = maxOf(alpha, bestScore) else beta = minOf(beta, bestScore)
        }
        return bestMove
    }

    private fun search(state: GameState, depth: Int, alphaStart: Int, betaStart: Int): Int {
        if (depth <= 0 || state.status != GameStatus.IN_PROGRESS) return engine.evaluate(state)
        val moves = engine.allLegalMoves(state, state.currentTurn)
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
}