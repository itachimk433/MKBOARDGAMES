package com.mkdev.mkboardgames.games.ludo

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

/**
 * A Ludo token keeps its progress in the piece itself.  -1 is a home-yard
 * token, 0..51 is the shared track, 52..55 is the player's home lane and 56
 * is the finished position.
 *
 * PieceColor remains binary for compatibility with the older two-sided games;
 * Ludo uses [player] for the four distinct colours and only uses PieceColor
 * when it has to cross the shared engine interface.
 */
data class LudoPiece(
    val player: Int,
    val token: Int,
    val progress: Int,
    override val color: PieceColor = if (player % 2 == 0) PieceColor.WHITE else PieceColor.BLACK
) : Piece(color) {
    override fun symbol(): String = (token + 1).toString()
    override fun value(): Int = progress.coerceAtLeast(0)
}

object LudoSetup {
    const val BOARD_SIZE = 15
    const val PATH_LENGTH = 52
    const val FINISH = 56
    const val PLAYER_COUNT = 4
    const val TOKENS_PER_PLAYER = 4

    val PLAYER_NAMES = arrayOf("Red", "Blue", "Green", "Yellow")
    val PLAYER_COLORS = intArrayOf(
        0xFFE4574F.toInt(),
        0xFF4F8DDA.toInt(),
        0xFF42A778.toInt(),
        0xFFE8B84A.toInt()
    )
    val PLAYER_SOFT_COLORS = intArrayOf(
        0xFF6E2728.toInt(),
        0xFF203E68.toInt(),
        0xFF1E513B.toInt(),
        0xFF5F4A20.toInt()
    )

    /**
     * The 52 playable track cells are the perimeter of the 15x15 board with
     * the four corner cells removed. It leaves the central 5x5 home area
     * available for the coloured approach lanes.
     */
    val PATH: List<Position> = buildList {
        for (col in 1..13) add(Position(0, col))
        for (row in 1..13) add(Position(row, 14))
        for (col in 13 downTo 1) add(Position(14, col))
        for (row in 13 downTo 1) add(Position(row, 0))
    }

    private val yardCells = arrayOf(
        arrayOf(Position(10, 1), Position(10, 3), Position(12, 1), Position(12, 3)),
        arrayOf(Position(1, 1), Position(1, 3), Position(3, 1), Position(3, 3)),
        arrayOf(Position(1, 11), Position(1, 13), Position(3, 11), Position(3, 13)),
        arrayOf(Position(10, 11), Position(10, 13), Position(12, 11), Position(12, 13))
    )

    fun yardPosition(player: Int, token: Int): Position = yardCells[player][token]

    fun startOffset(player: Int): Int = player * 13

    fun trackPosition(player: Int, progress: Int): Position =
        PATH[(startOffset(player) + progress) % PATH_LENGTH]

    fun homeLanePosition(player: Int, progress: Int): Position {
        val lane = (progress - 52).coerceIn(0, 3)
        return when (player) {
            0 -> Position(7, 1 + lane)
            1 -> Position(1 + lane, 7)
            2 -> Position(7, 13 - lane)
            else -> Position(13 - lane, 7)
        }
    }

    fun finishPosition(player: Int, token: Int): Position = when (player) {
        0 -> Position(6 + token / 2, 6 + token % 2)
        1 -> Position(6 + token / 2, 8 + token % 2)
        2 -> Position(8 + token / 2, 8 + token % 2)
        else -> Position(8 + token / 2, 6 + token % 2)
    }

    fun positionOf(piece: LudoPiece): Position = when {
        piece.progress < 0 -> yardPosition(piece.player, piece.token)
        piece.progress < 52 -> trackPosition(piece.player, piece.progress)
        piece.progress < FINISH -> homeLanePosition(piece.player, piece.progress)
        else -> finishPosition(piece.player, piece.token)
    }

    fun initialState(): GameState {
        val board = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)
        for (player in 0 until PLAYER_COUNT) {
            for (token in 0 until TOKENS_PER_PLAYER) {
                val piece = LudoPiece(player, token, -1)
                board[indexOf(yardPosition(player, token))] = piece
            }
        }
        return GameState(
            board = board,
            boardSize = BOARD_SIZE,
            currentTurn = PieceColor.WHITE,
            metadata = mapOf("ludo_turn" to 0, "ludo_dice" to 0)
        )
    }

    fun indexOf(position: Position): Int = position.row * BOARD_SIZE + position.col

    fun playerFromState(state: GameState): Int =
        (state.metadata["ludo_turn"] as? Int ?: 0).coerceIn(0, PLAYER_COUNT - 1)

    fun colorForPlayer(player: Int): PieceColor =
        if (player % 2 == 0) PieceColor.WHITE else PieceColor.BLACK
}