package com.mkdev.mkboardgames.games.amazons

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

enum class AmazonsPieceType {
    AMAZON,
    ARROW,
}

data class AmazonsPiece(
    val type: AmazonsPieceType,
    override val color: PieceColor,
) : Piece(color) {
    override fun symbol(): String = when (type) {
        AmazonsPieceType.AMAZON -> if (color == PieceColor.WHITE) "♕" else "♛"
        AmazonsPieceType.ARROW -> "✕"
    }

    override fun value(): Int = when (type) {
        AmazonsPieceType.AMAZON -> 1_000
        AmazonsPieceType.ARROW -> 0
    }
}

object AmazonsSetup {
    /**
     * The standard 10×10 setup, scaled inward for the compact 8×8 board.
     * White moves first in both variants.
     */
    fun initialState(boardSize: Int): GameState {
        require(boardSize == 8 || boardSize == 10) {
            "Amazons supports only 8×8 and 10×10 boards"
        }

        val board = arrayOfNulls<Piece>(boardSize * boardSize)
        val positions = if (boardSize == 10) {
            listOf(
                PieceColor.BLACK to listOf(
                    Position(0, 3), Position(0, 6),
                    Position(3, 0), Position(3, 9),
                ),
                PieceColor.WHITE to listOf(
                    Position(6, 0), Position(6, 9),
                    Position(9, 3), Position(9, 6),
                ),
            )
        } else {
            listOf(
                PieceColor.BLACK to listOf(
                    Position(0, 2), Position(0, 5),
                    Position(2, 0), Position(2, 7),
                ),
                PieceColor.WHITE to listOf(
                    Position(5, 0), Position(5, 7),
                    Position(7, 2), Position(7, 5),
                ),
            )
        }

        positions.forEach { (color, pieces) ->
            pieces.forEach { position ->
                board[position.row * boardSize + position.col] =
                    AmazonsPiece(AmazonsPieceType.AMAZON, color)
            }
        }
        return GameState(board = board, boardSize = boardSize)
    }
}