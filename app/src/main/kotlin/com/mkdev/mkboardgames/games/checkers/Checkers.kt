package com.mkdev.mkboardgames.games.checkers

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

enum class CheckersPieceType { MAN, KING }

data class CheckersPiece(val type: CheckersPieceType, override val color: PieceColor) : Piece(color) {
    // Draughts are discs, not chess pieces. Rendering supplies the layered
    // material treatment; this fallback is used in capture strips and logs.
    override fun symbol() = if (type == CheckersPieceType.KING) KING_SYMBOL else "●"
    override fun value() = if (type == CheckersPieceType.MAN) 100 else 175
    val isKing get() = type == CheckersPieceType.KING

    companion object {
        const val KING_SYMBOL = "♛"
    }
}

object CheckersSetup {
    /** Standard 8×8 board: Black on rows 0–2, White on rows 5–7, dark squares only. */
    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(64)
        for (row in 0..2) for (col in 0..7)
            if ((row + col) % 2 == 1) board[row * 8 + col] = CheckersPiece(CheckersPieceType.MAN, PieceColor.BLACK)
        for (row in 5..7) for (col in 0..7)
            if ((row + col) % 2 == 1) board[row * 8 + col] = CheckersPiece(CheckersPieceType.MAN, PieceColor.WHITE)
        return GameState(board = board, currentTurn = PieceColor.WHITE)
    }
}
