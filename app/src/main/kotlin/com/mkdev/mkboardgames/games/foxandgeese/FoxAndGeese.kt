package com.mkdev.mkboardgames.games.foxandgeese

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

enum class FoxAndGeesePieceType {
    FOX, GOOSE
}

/**
 * Fox and Geese uses WHITE for the fox and BLACK for the geese.  The colour
 * mapping lets the shared board, AI and result handling work exactly like the
 * other asymmetric games without pretending the sides have equal material.
 */
data class FoxAndGeesePiece(
    val type: FoxAndGeesePieceType,
    override val color: PieceColor
) : Piece(color) {
    override fun symbol() = if (type == FoxAndGeesePieceType.FOX) "F" else "G"
    override fun value() = if (type == FoxAndGeesePieceType.FOX) 1_000 else 100
}

object FoxAndGeeseSetup {
    /**
     * The traditional board is a 7x7 lattice with a three-square-wide cross.
     * That gives 33 playable points: 3 + 3 + 7 + 7 + 7 + 3 + 3.
     */
    const val BOARD_SIZE = 7

    fun isPlayable(position: Position): Boolean =
        position.row in 0 until BOARD_SIZE &&
            position.col in 0 until BOARD_SIZE &&
            (position.row in 2..4 || position.col in 2..4)

    /**
     * Returns whether two adjacent points share a line on the traditional
     * cross board. Diagonals stay inside one of the three board sections;
     * the four diagonals that cut across a cross-arm junction are not board
     * lines and must not be drawable or playable moves.
     */
    fun isConnected(from: Position, to: Position): Boolean {
        if (!isPlayable(from) || !isPlayable(to)) return false
        val rowDistance = kotlin.math.abs(from.row - to.row)
        val colDistance = kotlin.math.abs(from.col - to.col)
        if (rowDistance > 1 || colDistance > 1 || (rowDistance == 0 && colDistance == 0)) {
            return false
        }
        if (rowDistance == 0 || colDistance == 0) return true

        val staysInVerticalArm = from.col in 2..4 && to.col in 2..4
        val staysInHorizontalArm = from.row in 2..4 && to.row in 2..4
        return staysInVerticalArm || staysInHorizontalArm
    }

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)

        // The fox starts at the centre point of the upper half of the cross.
        board[indexOf(Position(2, 3))] =
            FoxAndGeesePiece(FoxAndGeesePieceType.FOX, PieceColor.WHITE)

        // Thirteen geese fill the lower arm: the full middle row plus the
        // two three-point rows beneath it.
        val geese = buildList {
            for (col in 0 until BOARD_SIZE) add(Position(4, col))
            for (row in 5..6) for (col in 2..4) add(Position(row, col))
        }
        for (pos in geese) {
            board[indexOf(pos)] =
                FoxAndGeesePiece(FoxAndGeesePieceType.GOOSE, PieceColor.BLACK)
        }

        return GameState(board = board, boardSize = BOARD_SIZE, currentTurn = PieceColor.WHITE)
    }

    fun indexOf(pos: Position): Int = pos.row * BOARD_SIZE + pos.col
}