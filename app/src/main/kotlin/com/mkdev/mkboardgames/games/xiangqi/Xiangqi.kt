package com.mkdev.mkboardgames.games.xiangqi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor

enum class XiangqiPieceType(
    val redSymbol: String,
    val blackSymbol: String,
    val points: Int,
) {
    GENERAL("帥", "將", 10_000),
    ADVISOR("仕", "士", 200),
    ELEPHANT("相", "象", 250),
    HORSE("傌", "馬", 450),
    CHARIOT("俥", "車", 900),
    CANNON("炮", "砲", 450),
    SOLDIER("兵", "卒", 100),
}

data class XiangqiPiece(
    val type: XiangqiPieceType,
    override val color: PieceColor,
) : Piece(color) {
    override fun symbol() =
        if (color == PieceColor.WHITE) type.redSymbol else type.blackSymbol

    override fun value() = type.points
}

object XiangqiSetup {
    const val ROWS = 10
    const val COLUMNS = 9
    const val STORAGE_SIZE = ROWS

    private val backRank = listOf(
        XiangqiPieceType.CHARIOT,
        XiangqiPieceType.HORSE,
        XiangqiPieceType.ELEPHANT,
        XiangqiPieceType.ADVISOR,
        XiangqiPieceType.GENERAL,
        XiangqiPieceType.ADVISOR,
        XiangqiPieceType.ELEPHANT,
        XiangqiPieceType.HORSE,
        XiangqiPieceType.CHARIOT,
    )

    private fun index(row: Int, col: Int) = row * STORAGE_SIZE + col

    fun initialState(): GameState {
        // GameState is square-shaped for the other games. A ten-wide backing
        // array leaves one unused column while preserving its immutable API.
        val board = arrayOfNulls<Piece>(STORAGE_SIZE * STORAGE_SIZE)
        fun put(row: Int, col: Int, type: XiangqiPieceType, color: PieceColor) {
            board[index(row, col)] = XiangqiPiece(type, color)
        }

        backRank.forEachIndexed { col, type ->
            put(0, col, type, PieceColor.BLACK)
            put(9, col, type, PieceColor.WHITE)
        }
        put(2, 1, XiangqiPieceType.CANNON, PieceColor.BLACK)
        put(2, 7, XiangqiPieceType.CANNON, PieceColor.BLACK)
        put(7, 1, XiangqiPieceType.CANNON, PieceColor.WHITE)
        put(7, 7, XiangqiPieceType.CANNON, PieceColor.WHITE)
        for (col in listOf(0, 2, 4, 6, 8)) {
            put(3, col, XiangqiPieceType.SOLDIER, PieceColor.BLACK)
            put(6, col, XiangqiPieceType.SOLDIER, PieceColor.WHITE)
        }
        return GameState(board = board, boardSize = STORAGE_SIZE)
    }
}