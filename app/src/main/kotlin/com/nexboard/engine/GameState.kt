package com.nexboard.engine

enum class GameStatus { IN_PROGRESS, WHITE_WINS, BLACK_WINS, DRAW }

/**
 * Complete snapshot of a game at one point in time.
 * The board is a flat array [row * 8 + col] of nullable [Piece] references.
 * Immutable — every move produces a new [GameState].
 */
data class GameState(
    val board: Array<Piece?>,          // size = boardSize * boardSize
    val boardSize: Int = 8,
    val currentTurn: PieceColor = PieceColor.WHITE,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val moveHistory: List<Move> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()   // en-passant target, castle rights, etc.
) {
    fun get(pos: Position): Piece? = board[pos.row * boardSize + pos.col]
    fun get(row: Int, col: Int): Piece? = board[row * boardSize + col]

    fun set(pos: Position, piece: Piece?): Array<Piece?> {
        val copy = board.copyOf()
        copy[pos.row * boardSize + pos.col] = piece
        return copy
    }

    fun withBoard(newBoard: Array<Piece?>, turn: PieceColor, move: Move, status: GameStatus = GameStatus.IN_PROGRESS, meta: Map<String, Any> = emptyMap()) =
        copy(board = newBoard, currentTurn = turn, status = status, moveHistory = moveHistory + move, metadata = meta)

    val lastMove: Move? get() = moveHistory.lastOrNull()

    // equals/hashCode must consider board content
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false
        return board.contentEquals(other.board) && currentTurn == other.currentTurn
    }
    override fun hashCode() = 31 * board.contentHashCode() + currentTurn.hashCode()
}
