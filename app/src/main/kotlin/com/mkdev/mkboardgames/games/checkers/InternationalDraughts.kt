package com.mkdev.mkboardgames.games.checkers

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor

/**
 * International Draughts starts with twenty pieces per side on a 10×10 board.
 * Black occupies the top four rows and White occupies the bottom four rows.
 */
object InternationalDraughtsSetup {
    const val BOARD_SIZE = 10

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)
        for (row in 0 until 4) {
            for (col in 0 until BOARD_SIZE) {
                if ((row + col) % 2 == 1) {
                    board[row * BOARD_SIZE + col] =
                        CheckersPiece(CheckersPieceType.MAN, PieceColor.BLACK)
                }
            }
        }
        for (row in 6 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                if ((row + col) % 2 == 1) {
                    board[row * BOARD_SIZE + col] =
                        CheckersPiece(CheckersPieceType.MAN, PieceColor.WHITE)
                }
            }
        }
        return GameState(
            board = board,
            boardSize = BOARD_SIZE,
            currentTurn = PieceColor.WHITE
        )
    }
}