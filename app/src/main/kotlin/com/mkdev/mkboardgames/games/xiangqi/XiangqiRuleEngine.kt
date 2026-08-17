package com.mkdev.mkboardgames.games.xiangqi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

class XiangqiRuleEngine : com.mkdev.mkboardgames.engine.RuleEngine {
    private val rows = XiangqiSetup.ROWS
    private val columns = XiangqiSetup.COLUMNS

    override fun initialState() = XiangqiSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = pieceAt(state, position) as? XiangqiPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()
        return pseudoMovesFrom(state, position, piece)
            // The raw move generator includes attacks on the opposing General
            // so that check detection remains accurate. Those attacks are not
            // legal moves: Xiangqi ends by checkmate, never by capturing a
            // General.
            .filter { !capturesGeneral(state, it) }
            .filter { !isInCheck(applyMoveInternal(state, it), piece.color) }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> =
        buildList {
            for (row in 0 until rows) for (col in 0 until columns) {
                val position = Position(row, col)
                if ((pieceAt(state, position) as? XiangqiPiece)?.color == color) {
                    addAll(legalMovesFrom(state.copy(currentTurn = color), position))
                }
            }
        }

    override fun applyMove(state: GameState, move: Move): GameState {
        require(!capturesGeneral(state, move)) {
            "Xiangqi Generals cannot be captured"
        }
        val next = applyMoveInternal(state, move)
        val status = gameStatus(next)
        return next.copy(status = status, moveHistory = state.moveHistory + move)
    }

    override fun gameStatus(state: GameState): GameStatus {
        val redKing = findPiece(state, PieceColor.WHITE, XiangqiPieceType.GENERAL)
        val blackKing = findPiece(state, PieceColor.BLACK, XiangqiPieceType.GENERAL)
        if (redKing == null) return GameStatus.BLACK_WINS
        if (blackKing == null) return GameStatus.WHITE_WINS
        if (allLegalMoves(state, state.currentTurn).isEmpty()) {
            return if (state.currentTurn == PieceColor.WHITE) GameStatus.BLACK_WINS
            else GameStatus.WHITE_WINS
        }
        return GameStatus.IN_PROGRESS
    }

    override fun evaluate(state: GameState): Int {
        var score = 0
        for (row in 0 until rows) for (col in 0 until columns) {
            val piece = pieceAt(state, Position(row, col)) as? XiangqiPiece ?: continue
            val mobility = pseudoMovesFrom(state, Position(row, col), piece)
                .count { !capturesGeneral(state, it) }
            score += if (piece.color == PieceColor.WHITE) {
                piece.value() + mobility * 3
            } else {
                -piece.value() - mobility * 3
            }
        }
        return score
    }

    private fun pseudoMovesFrom(
        state: GameState,
        from: Position,
        piece: XiangqiPiece,
    ): List<Move> = when (piece.type) {
        XiangqiPieceType.GENERAL -> generalMoves(state, from, piece.color)
        XiangqiPieceType.ADVISOR -> advisorMoves(state, from, piece.color)
        XiangqiPieceType.ELEPHANT -> elephantMoves(state, from, piece.color)
        XiangqiPieceType.HORSE -> horseMoves(state, from, piece.color)
        XiangqiPieceType.CHARIOT -> slidingMoves(state, from, piece.color, orthogonalDirections)
        XiangqiPieceType.CANNON -> cannonMoves(state, from, piece.color)
        XiangqiPieceType.SOLDIER -> soldierMoves(state, from, piece.color)
    }

    private fun generalMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in orthogonalDirections) {
            val to = Position(from.row + dr, from.col + dc)
            if (onBoard(to) && inPalace(to, color)) addIfAvailable(state, from, to, moves)
        }
        val enemy = findPiece(state, color.opponent(), XiangqiPieceType.GENERAL)
        if (enemy != null && enemy.col == from.col && clearBetween(state, from, enemy)) {
            moves += Move(from, enemy, listOf(enemy))
        }
        return moves
    }

    private fun advisorMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in diagonalDirections) {
            val to = Position(from.row + dr, from.col + dc)
            if (onBoard(to) && inPalace(to, color)) addIfAvailable(state, from, to, moves)
        }
        return moves
    }

    private fun elephantMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in elephantDirections) {
            val eye = Position(from.row + dr / 2, from.col + dc / 2)
            val to = Position(from.row + dr, from.col + dc)
            if (onBoard(to) && onOwnSideOfRiver(to, color) && pieceAt(state, eye) == null) {
                addIfAvailable(state, from, to, moves)
            }
        }
        return moves
    }

    private fun horseMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc, lr, lc) in horseDirections) {
            val leg = Position(from.row + lr, from.col + lc)
            val to = Position(from.row + dr, from.col + dc)
            if (onBoard(to) && pieceAt(state, leg) == null) addIfAvailable(state, from, to, moves)
        }
        return moves
    }

    private fun slidingMoves(
        state: GameState,
        from: Position,
        color: PieceColor,
        directions: List<Pair<Int, Int>>,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in directions) {
            var row = from.row + dr
            var col = from.col + dc
            while (onBoard(Position(row, col))) {
                val to = Position(row, col)
                val target = pieceAt(state, to)
                if (target == null) moves += Move(from, to)
                else {
                    if (target.color != color) moves += Move(from, to, listOf(to))
                    break
                }
                row += dr
                col += dc
            }
        }
        return moves
    }

    private fun cannonMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in orthogonalDirections) {
            var row = from.row + dr
            var col = from.col + dc
            var screenSeen = false
            while (onBoard(Position(row, col))) {
                val to = Position(row, col)
                val target = pieceAt(state, to)
                if (!screenSeen) {
                    if (target == null) moves += Move(from, to)
                    else screenSeen = true
                } else if (target != null) {
                    if (target.color != color) moves += Move(from, to, listOf(to))
                    break
                }
                row += dr
                col += dc
            }
        }
        return moves
    }

    private fun soldierMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        val forward = if (color == PieceColor.WHITE) -1 else 1
        addIfAvailable(state, from, Position(from.row + forward, from.col), moves)
        val crossed = if (color == PieceColor.WHITE) from.row <= 4 else from.row >= 5
        if (crossed) {
            addIfAvailable(state, from, Position(from.row, from.col - 1), moves)
            addIfAvailable(state, from, Position(from.row, from.col + 1), moves)
        }
        return moves
    }

    private fun addIfAvailable(
        state: GameState,
        from: Position,
        to: Position,
        moves: MutableList<Move>,
    ) {
        if (!onBoard(to)) return
        val source = pieceAt(state, from) ?: return
        val target = pieceAt(state, to)
        if (target == null) moves += Move(from, to)
        else if (target.color != source.color) moves += Move(from, to, listOf(to))
    }

    private fun isInCheck(state: GameState, color: PieceColor): Boolean {
        val king = findPiece(state, color, XiangqiPieceType.GENERAL) ?: return true
        for (row in 0 until rows) for (col in 0 until columns) {
            val from = Position(row, col)
            val piece = pieceAt(state, from) as? XiangqiPiece ?: continue
            if (piece.color == color) continue
            if (pseudoMovesFrom(state, from, piece).any { it.to == king }) return true
        }
        return false
    }

    private fun capturesGeneral(state: GameState, move: Move): Boolean {
        val destinationTarget = pieceAt(state, move.to) as? XiangqiPiece
        if (destinationTarget?.type == XiangqiPieceType.GENERAL) return true
        return move.captures.any {
            (pieceAt(state, it) as? XiangqiPiece)?.type == XiangqiPieceType.GENERAL
        }
    }

    private fun applyMoveInternal(state: GameState, move: Move): GameState {
        val board = state.board.copyOf()
        move.captures.forEach { board[index(it)] = null }
        board[index(move.to)] = board[index(move.from)]
        board[index(move.from)] = null
        return state.copy(board = board, currentTurn = state.currentTurn.opponent(), status = GameStatus.IN_PROGRESS)
    }

    private fun pieceAt(state: GameState, position: Position): Piece? =
        if (onBoard(position)) state.board[index(position)] else null

    private fun index(position: Position) = position.row * XiangqiSetup.STORAGE_SIZE + position.col

    private fun findPiece(state: GameState, color: PieceColor, type: XiangqiPieceType): Position? {
        for (row in 0 until rows) for (col in 0 until columns) {
            val piece = pieceAt(state, Position(row, col)) as? XiangqiPiece ?: continue
            if (piece.color == color && piece.type == type) return Position(row, col)
        }
        return null
    }

    private fun inPalace(position: Position, color: PieceColor): Boolean =
        position.col in 3..5 &&
            if (color == PieceColor.WHITE) position.row in 7..9 else position.row in 0..2

    private fun onOwnSideOfRiver(position: Position, color: PieceColor): Boolean =
        if (color == PieceColor.WHITE) position.row >= 5 else position.row <= 4

    private fun clearBetween(state: GameState, a: Position, b: Position): Boolean {
        val step = if (a.row < b.row) 1 else -1
        var row = a.row + step
        while (row != b.row) {
            if (pieceAt(state, Position(row, a.col)) != null) return false
            row += step
        }
        return true
    }

    private fun onBoard(position: Position) =
        position.row in 0 until rows && position.col in 0 until columns

    private val orthogonalDirections = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val diagonalDirections = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val elephantDirections = listOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)
    private val horseDirections = listOf(
        Quadruple(2, 1, 1, 0),
        Quadruple(2, -1, 1, 0),
        Quadruple(-2, 1, -1, 0),
        Quadruple(-2, -1, -1, 0),
        Quadruple(1, 2, 0, 1),
        Quadruple(-1, 2, 0, 1),
        Quadruple(1, -2, 0, -1),
        Quadruple(-1, -2, 0, -1),
    )

    private data class Quadruple(
        val dr: Int,
        val dc: Int,
        val lr: Int,
        val lc: Int,
    )
}