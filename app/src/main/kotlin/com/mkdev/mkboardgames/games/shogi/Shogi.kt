package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor

enum class ShogiPieceType(
    val symbol: String,
    val promotedSymbol: String?,
    val points: Int,
) {
    PAWN("歩", "と", 100),
    LANCE("香", "杏", 300),
    KNIGHT("桂", "圭", 300),
    SILVER("銀", "全", 400),
    GOLD("金", null, 500),
    BISHOP("角", "馬", 800),
    ROOK("飛", "龍", 1_000),
    KING("玉", null, 10_000),
}

data class ShogiPiece(
    val type: ShogiPieceType,
    override val color: PieceColor,
    val promoted: Boolean = false,
) : Piece(color) {
    override fun symbol(): String =
        if (promoted) type.promotedSymbol ?: type.symbol else type.symbol

    override fun value(): Int =
        if (promoted) type.points + when (type) {
            ShogiPieceType.BISHOP, ShogiPieceType.ROOK -> 300
            ShogiPieceType.PAWN, ShogiPieceType.LANCE, ShogiPieceType.KNIGHT,
            ShogiPieceType.SILVER -> 200
            else -> 0
        } else type.points
}

object ShogiSetup {
    const val SIZE = 9

    private fun index(row: Int, col: Int) = row * SIZE + col

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(SIZE * SIZE)
        fun put(row: Int, col: Int, type: ShogiPieceType, color: PieceColor) {
            board[index(row, col)] = ShogiPiece(type, color)
        }

        val backRank = listOf(
            ShogiPieceType.LANCE,
            ShogiPieceType.KNIGHT,
            ShogiPieceType.SILVER,
            ShogiPieceType.GOLD,
            ShogiPieceType.KING,
            ShogiPieceType.GOLD,
            ShogiPieceType.SILVER,
            ShogiPieceType.KNIGHT,
            ShogiPieceType.LANCE,
        )
        backRank.forEachIndexed { col, type ->
            put(0, col, type, PieceColor.BLACK)
            put(8, col, type, PieceColor.WHITE)
        }
        put(1, 1, ShogiPieceType.ROOK, PieceColor.BLACK)
        put(1, 7, ShogiPieceType.BISHOP, PieceColor.BLACK)
        put(7, 1, ShogiPieceType.BISHOP, PieceColor.WHITE)
        put(7, 7, ShogiPieceType.ROOK, PieceColor.WHITE)
        for (col in 0 until SIZE) {
            put(2, col, ShogiPieceType.PAWN, PieceColor.BLACK)
            put(6, col, ShogiPieceType.PAWN, PieceColor.WHITE)
        }
        // In standard Shogi, Sente is traditionally represented by Black and
        // has the first move. The app keeps that mapping consistently.
        return GameState(board = board, boardSize = SIZE, currentTurn = PieceColor.BLACK)
    }
}