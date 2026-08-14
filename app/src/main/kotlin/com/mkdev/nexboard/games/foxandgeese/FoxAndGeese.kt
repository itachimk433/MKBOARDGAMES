package com.mkdev.nexboard.games.foxandgeese

import com.mkdev.nexboard.engine.GameState
import com.mkdev.nexboard.engine.Piece
import com.mkdev.nexboard.engine.PieceColor
import com.mkdev.nexboard.engine.Position

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
    const val BOARD_SIZE = 8

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)

        // The fox starts on a dark square at the top of the board.
        board[indexOf(Position(0, 3))] =
            FoxAndGeesePiece(FoxAndGeesePieceType.FOX, PieceColor.WHITE)

        // Thirteen geese form the opposing flock on the lower three rows,
        // with one extra goose on the row immediately above them.
        val geese = buildList {
            add(Position(4, 1))
            for (row in 5..7) {
                for (col in 0..7) {
                    if ((row + col) % 2 == 1) add(Position(row, col))
                }
            }
        }
        for (pos in geese) {
            board[indexOf(pos)] =
                FoxAndGeesePiece(FoxAndGeesePieceType.GOOSE, PieceColor.BLACK)
        }

        return GameState(board = board, boardSize = BOARD_SIZE, currentTurn = PieceColor.WHITE)
    }

    fun indexOf(pos: Position): Int = pos.row * BOARD_SIZE + pos.col
}