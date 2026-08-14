package com.mkdev.mkboardgames.games.othello

import com.mkdev.mkboardgames.engine.*

data class OthelloPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 10
}

object OthelloSetup {
    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(64)
        board[3 * 8 + 3] = OthelloPiece(PieceColor.WHITE)
        board[3 * 8 + 4] = OthelloPiece(PieceColor.BLACK)
        board[4 * 8 + 3] = OthelloPiece(PieceColor.BLACK)
        board[4 * 8 + 4] = OthelloPiece(PieceColor.WHITE)
        return GameState(board = board, currentTurn = PieceColor.BLACK)
    }
}
