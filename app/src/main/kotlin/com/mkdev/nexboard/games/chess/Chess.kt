package com.mkdev.nexboard.games.chess

import com.mkdev.nexboard.engine.GameState
import com.mkdev.nexboard.engine.Piece
import com.mkdev.nexboard.engine.PieceColor
import com.mkdev.nexboard.engine.Position

enum class ChessPieceType(val whiteSymbol: String, val blackSymbol: String, val points: Int) {
    KING  ("♔", "♚", 20000),
    QUEEN ("♕", "♛", 900),
    ROOK  ("♖", "♜", 500),
    BISHOP("♗", "♝", 330),
    KNIGHT("♘", "♞", 320),
    PAWN  ("♙", "♟", 100)
}

data class ChessPiece(val type: ChessPieceType, override val color: PieceColor) : Piece(color) {
    override fun symbol() = if (color == PieceColor.WHITE) type.whiteSymbol else type.blackSymbol
    override fun value() = type.points
}

object ChessSetup {

    private val BACK_ROW = listOf(
        ChessPieceType.ROOK, ChessPieceType.KNIGHT, ChessPieceType.BISHOP,
        ChessPieceType.QUEEN, ChessPieceType.KING,
        ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.ROOK
    )

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(64)

        // Black back row (row 0) + pawns (row 1)
        for (col in 0..7) {
            board[0 * 8 + col] = ChessPiece(BACK_ROW[col], PieceColor.BLACK)
            board[1 * 8 + col] = ChessPiece(ChessPieceType.PAWN, PieceColor.BLACK)
        }
        // White back row (row 7) + pawns (row 6)
        for (col in 0..7) {
            board[7 * 8 + col] = ChessPiece(BACK_ROW[col], PieceColor.WHITE)
            board[6 * 8 + col] = ChessPiece(ChessPieceType.PAWN, PieceColor.WHITE)
        }

        return GameState(
            board = board,
            metadata = mapOf(
                // Castle rights: wK, wQ, bK, bQ sides
                "castleWK" to true, "castleWQ" to true,
                "castleBK" to true, "castleBQ" to true,
                "enPassant" to -1   // target column, -1 = none
            )
        )
    }
}
