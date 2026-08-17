package com.mkdev.mkboardgames.engine

enum class GameStatus { IN_PROGRESS, WHITE_WINS, BLACK_WINS, DRAW }

/**
 * Complete snapshot of a game at one point in time.
 * The board is a flat array [row * boardSize + col] of nullable [Piece] references.
 * Immutable — every move produces a new [GameState].
 */
data class GameState(
    val board: Array<Piece?>,          // size = boardSize * boardSize
    val boardSize: Int = 8,
    val currentTurn: PieceColor = PieceColor.WHITE,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val moveHistory: List<Move> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),   // en-passant target, castle rights, etc.
    /**
     * Pieces held off the board by each player. Shogi uses this for captured
     * pieces that may be dropped back onto the board; other games leave it
     * empty.
     */
    val hands: Map<PieceColor, List<Piece>> = emptyMap(),
) {
    fun get(pos: Position): Piece? =
        if (pos.row in 0 until boardSize && pos.col in 0 until boardSize) {
            board[pos.row * boardSize + pos.col]
        } else {
            null
        }

    fun get(row: Int, col: Int): Piece? =
        if (row in 0 until boardSize && col in 0 until boardSize) board[row * boardSize + col] else null

    fun set(pos: Position, piece: Piece?): Array<Piece?> {
        val copy = board.copyOf()
        copy[pos.row * boardSize + pos.col] = piece
        return copy
    }

    fun withBoard(
        newBoard: Array<Piece?>,
        turn: PieceColor,
        move: Move,
        status: GameStatus = GameStatus.IN_PROGRESS,
        meta: Map<String, Any> = metadata,
    ) = copy(
        board = newBoard,
        currentTurn = turn,
        status = status,
        moveHistory = moveHistory + move,
        metadata = meta,
        hands = hands,
    )

    val lastMove: Move? get() = moveHistory.lastOrNull()

    // equals/hashCode must consider board content
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false
        return board.contentEquals(other.board) &&
            currentTurn == other.currentTurn &&
            hands == other.hands
    }
    override fun hashCode() =
        ((31 * board.contentHashCode()) + currentTurn.hashCode()) * 31 + hands.hashCode()
}
