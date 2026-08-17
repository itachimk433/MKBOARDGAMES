package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

/**
 * Shogi's board movement and promotion rules.
 *
 * Captured pieces are shown in the shared capture strips. This first version
 * keeps the same board-only interaction model as the other games in the app;
 * captured-piece drops are intentionally not exposed as a separate touch mode.
 */
class ShogiRuleEngine : com.mkdev.mkboardgames.engine.RuleEngine {
    override fun initialState() = ShogiSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = pieceAt(state, position) as? ShogiPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()

        return rawMovesFrom(state, position, piece)
            .filter { (pieceAt(state, it.to) as? ShogiPiece)?.type != ShogiPieceType.KING }
            .filter { move ->
                !isKingInCheck(applyMoveInternal(state, promoteMove(piece, move)), piece.color)
            }
            .map { promoteMove(piece, it) }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val turnState = state.copy(currentTurn = color)
        return buildList {
            for (row in 0 until ShogiSetup.SIZE) {
                for (col in 0 until ShogiSetup.SIZE) {
                    addAll(legalMovesFrom(turnState, Position(row, col)))
                }
            }
        }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        val mover = state.get(move.from) as? ShogiPiece
            ?: error("No Shogi piece at ${move.from}")
        val applied = applyMoveInternal(state, promoteMove(mover, move))
        return applied.copy(
            moveHistory = state.moveHistory + move,
            status = gameStatus(applied.copy(moveHistory = state.moveHistory + move)),
        )
    }

    override fun gameStatus(state: GameState): GameStatus {
        val whiteKing = findKing(state, PieceColor.WHITE)
        val blackKing = findKing(state, PieceColor.BLACK)
        if (whiteKing == null) return GameStatus.BLACK_WINS
        if (blackKing == null) return GameStatus.WHITE_WINS

        if (allLegalMoves(state, state.currentTurn).isNotEmpty()) {
            return GameStatus.IN_PROGRESS
        }
        return if (isKingInCheck(state, state.currentTurn)) {
            winnerFor(state.currentTurn.opponent())
        } else {
            GameStatus.DRAW
        }
    }

    override fun evaluate(state: GameState): Int {
        var score = 0
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val piece = pieceAt(state, Position(row, col)) ?: continue
                val advance = if (piece.color == PieceColor.WHITE) 8 - row else row
                val positional = advance * 3
                score += if (piece.color == PieceColor.WHITE) {
                    piece.value() + positional
                } else {
                    -piece.value() - positional
                }
            }
        }
        return score
    }

    private fun rawMovesFrom(
        state: GameState,
        from: Position,
        piece: ShogiPiece,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        fun add(to: Position) {
            if (!onBoard(to)) return
            val target = pieceAt(state, to)
            if (target?.color == piece.color) return
            moves += Move(
                from = from,
                to = to,
                captures = if (target == null) emptyList() else listOf(to),
            )
        }
        fun slide(directions: List<Pair<Int, Int>>) {
            for ((dr, dc) in directions) {
                var row = from.row + dr
                var col = from.col + dc
                while (onBoard(Position(row, col))) {
                    val target = pieceAt(state, Position(row, col))
                    if (target == null) {
                        moves += Move(from, Position(row, col))
                    } else {
                        if (target.color != piece.color) moves += Move(from, Position(row, col), listOf(Position(row, col)))
                        break
                    }
                    row += dr
                    col += dc
                }
            }
        }

        val forward = if (piece.color == PieceColor.WHITE) -1 else 1
        when (piece.type) {
            ShogiPieceType.PAWN -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else add(Position(from.row + forward, from.col))
            }
            ShogiPieceType.LANCE -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else slide(listOf(forward to 0))
            }
            ShogiPieceType.KNIGHT -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else {
                    add(Position(from.row + forward * 2, from.col - 1))
                    add(Position(from.row + forward * 2, from.col + 1))
                }
            }
            ShogiPieceType.SILVER -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else {
                    add(Position(from.row + forward, from.col))
                    add(Position(from.row + forward, from.col - 1))
                    add(Position(from.row + forward, from.col + 1))
                    add(Position(from.row - forward, from.col - 1))
                    add(Position(from.row - forward, from.col + 1))
                }
            }
            ShogiPieceType.GOLD -> goldSteps(forward, from, ::add)
            ShogiPieceType.KING -> {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr != 0 || dc != 0) add(Position(from.row + dr, from.col + dc))
                }
            }
            ShogiPieceType.BISHOP -> {
                slide(diagonalDirections)
                if (piece.promoted) orthogonalDirections.forEach { (dr, dc) ->
                    add(Position(from.row + dr, from.col + dc))
                }
            }
            ShogiPieceType.ROOK -> {
                slide(orthogonalDirections)
                if (piece.promoted) diagonalDirections.forEach { (dr, dc) ->
                    add(Position(from.row + dr, from.col + dc))
                }
            }
        }
        return moves
    }

    private fun goldSteps(
        forward: Int,
        from: Position,
        add: (Position) -> Unit,
    ) {
        listOf(
            forward to 0,
            forward to -1,
            forward to 1,
            0 to -1,
            0 to 1,
            -forward to 0,
        ).forEach { (dr, dc) -> add(Position(from.row + dr, from.col + dc)) }
    }

    private fun promoteMove(piece: ShogiPiece, move: Move): Move {
        if (piece.promoted || !canPromote(piece.type)) return move
        val forced = when (piece.type) {
            ShogiPieceType.PAWN, ShogiPieceType.LANCE ->
                move.to.row == promotionLastRank(piece.color)
            ShogiPieceType.KNIGHT ->
                move.to.row == promotionLastRank(piece.color) ||
                    move.to.row == promotionLastRank(piece.color) + if (piece.color == PieceColor.WHITE) 1 else -1
            else -> false
        }
        val entersZone = inPromotionZone(move.to, piece.color)
        if (forced || entersZone) {
            return move.copy(
                promotionType = piece.type.name,
                metadata = move.metadata + ("promote" to true),
            )
        }
        return move
    }

    private fun applyMoveInternal(state: GameState, move: Move): GameState {
        val board = state.board.copyOf()
        move.captures.forEach { board[index(it)] = null }
        val piece = board[index(move.from)] as? ShogiPiece ?: return state
        val promoted = move.promotionType != null || move.metadata["promote"] == true
        board[index(move.to)] = if (promoted) piece.copy(promoted = true) else piece
        board[index(move.from)] = null
        return state.copy(
            board = board,
            currentTurn = state.currentTurn.opponent(),
            status = GameStatus.IN_PROGRESS,
        )
    }

    private fun isKingInCheck(state: GameState, color: PieceColor): Boolean {
        val king = findKing(state, color) ?: return true
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val from = Position(row, col)
                val piece = pieceAt(state, from) as? ShogiPiece ?: continue
                if (piece.color == color) continue
                if (rawMovesFrom(state, from, piece).any { it.to == king }) return true
            }
        }
        return false
    }

    private fun findKing(state: GameState, color: PieceColor): Position? {
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val piece = pieceAt(state, Position(row, col)) as? ShogiPiece ?: continue
                if (piece.color == color && piece.type == ShogiPieceType.KING) {
                    return Position(row, col)
                }
            }
        }
        return null
    }

    private fun pieceAt(state: GameState, position: Position): ShogiPiece? =
        if (onBoard(position)) state.board[index(position)] as? ShogiPiece else null

    private fun index(position: Position) = position.row * ShogiSetup.SIZE + position.col
    private fun onBoard(position: Position) =
        position.row in 0 until ShogiSetup.SIZE && position.col in 0 until ShogiSetup.SIZE

    private fun canPromote(type: ShogiPieceType) =
        type != ShogiPieceType.KING && type != ShogiPieceType.GOLD

    private fun promotionLastRank(color: PieceColor) =
        if (color == PieceColor.WHITE) 0 else ShogiSetup.SIZE - 1

    private fun inPromotionZone(position: Position, color: PieceColor) =
        if (color == PieceColor.WHITE) position.row <= 2 else position.row >= 6

    private fun winnerFor(color: PieceColor) =
        if (color == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS

    private val orthogonalDirections = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
    )
    private val diagonalDirections = listOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
    )
}