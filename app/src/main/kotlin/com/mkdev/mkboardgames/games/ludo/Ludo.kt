package com.mkdev.mkboardgames.games.ludo

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Move
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
    const val PIECES_METADATA = "ludo_pieces"
    const val SIX_STREAK_METADATA = "ludo_six_streak"

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
     * The 52 common track cells in clockwise order, beginning at the yellow
     * starting square on the supplied 15x15 board. The four player starts are
     * exactly 13 cells apart: yellow (top), blue (right), red (bottom), and
     * green (left).
     */
    val PATH: List<Position> = buildList {
        addAll(listOf(Position(0, 7), Position(0, 8)))
        for (row in 1..5) add(Position(row, 8))
        for (col in 9..14) add(Position(6, col))
        addAll(listOf(Position(7, 14), Position(8, 14)))
        for (col in 13 downTo 9) add(Position(8, col))
        for (row in 9..14) add(Position(row, 8))
        addAll(listOf(Position(14, 7), Position(14, 6)))
        for (row in 13 downTo 9) add(Position(row, 6))
        for (col in 5 downTo 0) add(Position(8, col))
        addAll(listOf(Position(7, 0), Position(6, 0)))
        for (col in 1..5) add(Position(6, col))
        for (row in 5 downTo 1) add(Position(row, 6))
        add(Position(0, 6))
    }

    /**
     * The supplied board marks both the four coloured arrow/start cells and
     * the four star cells as safe. Tokens on these cells cannot be captured.
     */
    val SAFE_TRACK_INDICES: Set<Int> = setOf(0, 13, 16, 23, 26, 39, 42, 49)

    // The board artwork is green, yellow, red, blue clockwise from top-left.
    // The app's player order is red, blue, green, yellow.
    private val yardCells = arrayOf(
        arrayOf(Position(10, 1), Position(10, 3), Position(12, 1), Position(12, 3)),
        arrayOf(Position(10, 11), Position(10, 13), Position(12, 11), Position(12, 13)),
        arrayOf(Position(1, 1), Position(1, 3), Position(3, 1), Position(3, 3)),
        arrayOf(Position(1, 11), Position(1, 13), Position(3, 11), Position(3, 13))
    )

    fun yardPosition(player: Int, token: Int): Position = yardCells[player][token]

    fun startOffset(player: Int): Int = intArrayOf(26, 13, 39, 0)[player.coerceIn(0, PLAYER_COUNT - 1)]

    fun trackPosition(player: Int, progress: Int): Position =
        PATH[(startOffset(player) + progress) % PATH_LENGTH]

    fun isSafeTrackCell(position: Position): Boolean =
        PATH.withIndex().any { (index, pathPosition) ->
            index in SAFE_TRACK_INDICES && pathPosition == position
        }

    fun homeLanePosition(player: Int, progress: Int): Position {
        val lane = (progress - 52).coerceIn(0, 3)
        return when (player) {
            0 -> Position(13 - lane, 7)
            1 -> Position(7, 13 - lane)
            2 -> Position(7, 1 + lane)
            else -> Position(1 + lane, 7)
        }
    }

    fun finishPosition(player: Int, token: Int): Position = when (player) {
        0 -> Position(8 + token / 2, 6 + token % 2)
        1 -> Position(8 + token / 2, 8 + token % 2)
        2 -> Position(6 + token / 2, 6 + token % 2)
        else -> Position(6 + token / 2, 8 + token % 2)
    }

    fun positionOf(piece: LudoPiece): Position = when {
        piece.progress < 0 -> yardPosition(piece.player, piece.token)
        piece.progress < 52 -> trackPosition(piece.player, piece.progress)
        piece.progress < FINISH -> homeLanePosition(piece.player, piece.progress)
        else -> finishPosition(piece.player, piece.token)
    }

    /**
     * The generic engine board can hold one Piece per cell, while Ludo allows
     * same-colour tokens to share a track cell. Keep the complete token list
     * in metadata and use the board array as a backwards-compatible spatial
     * index/representative.
     */
    fun allPieces(state: GameState): List<LudoPiece> =
        (state.metadata[PIECES_METADATA] as? List<*>)
            ?.filterIsInstance<LudoPiece>()
            ?.takeIf { it.size == PLAYER_COUNT * TOKENS_PER_PLAYER }
            ?: state.board.filterIsInstance<LudoPiece>()

    fun piecesAt(state: GameState, position: Position): List<LudoPiece> =
        allPieces(state).filter { positionOf(it) == position }

    fun pieceForMove(state: GameState, move: Move): LudoPiece? {
        val player = move.metadata["player"] as? Int ?: return null
        val token = move.metadata["token"] as? Int ?: return null
        return allPieces(state).firstOrNull {
            it.player == player && it.token == token && positionOf(it) == move.from
        }
    }

    fun boardFor(pieces: List<LudoPiece>): Array<Piece?> {
        val board = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)
        for (piece in pieces) {
            board[indexOf(positionOf(piece))] = piece
        }
        return board
    }

    fun initialState(): GameState {
        val pieces = buildList {
            for (player in 0 until PLAYER_COUNT) {
                for (token in 0 until TOKENS_PER_PLAYER) {
                    add(LudoPiece(player, token, -1))
                }
            }
        }
        return GameState(
            board = boardFor(pieces),
            boardSize = BOARD_SIZE,
            currentTurn = PieceColor.WHITE,
            metadata = mapOf(
                "ludo_turn" to 0,
                "ludo_dice" to 0,
                SIX_STREAK_METADATA to 0,
                PIECES_METADATA to pieces,
                LudoEconomy.METADATA to LudoEconomy.initialPlayers(),
                "ludo_rerolled" to false,
            )
        )
    }

    fun indexOf(position: Position): Int = position.row * BOARD_SIZE + position.col

    fun playerFromState(state: GameState): Int =
        (state.metadata["ludo_turn"] as? Int ?: 0).coerceIn(0, PLAYER_COUNT - 1)

    fun colorForPlayer(player: Int): PieceColor =
        if (player % 2 == 0) PieceColor.WHITE else PieceColor.BLACK
}