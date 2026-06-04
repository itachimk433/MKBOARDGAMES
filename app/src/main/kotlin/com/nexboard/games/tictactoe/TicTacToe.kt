package com.nexboard.games.tictactoe

import com.nexboard.engine.*

data class TicTacToePiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = if (color == PieceColor.WHITE) "✕" else "○"
    override fun value() = 1
}

/**
 * Generic N-in-a-row rule engine.
 *
 * winLength always equals boardSize (standard rules — no custom win length).
 * Adds early-draw detection: if no win line can still be completed by either
 * player (because a line contains pieces of both colors), the game ends as a
 * draw immediately instead of waiting for the board to fill up.
 */
class TicTacToeRuleEngine(
    val boardSize: Int = 3,
    val winLength: Int = boardSize
) : RuleEngine {

    companion object {
        val PLACE = Position(-1, -1)   // sentinel "from" for placements

        fun generateWinLines(n: Int, k: Int): List<List<Int>> {
            val lines = mutableListOf<List<Int>>()
            // Rows
            for (r in 0 until n) for (c in 0..n - k) {
                lines += (0 until k).map { r * n + (c + it) }
            }
            // Columns
            for (c in 0 until n) for (r in 0..n - k) {
                lines += (0 until k).map { (r + it) * n + c }
            }
            // Diagonals ↘
            for (r in 0..n - k) for (c in 0..n - k) {
                lines += (0 until k).map { (r + it) * n + (c + it) }
            }
            // Diagonals ↙
            for (r in 0..n - k) for (c in k - 1 until n) {
                lines += (0 until k).map { (r + it) * n + (c - it) }
            }
            return lines
        }
    }

    val winLines: List<List<Int>> = generateWinLines(boardSize, winLength)

    override fun initialState() = GameState(
        board       = arrayOfNulls(boardSize * boardSize),
        boardSize   = boardSize,
        currentTurn = PieceColor.WHITE,
        status      = GameStatus.IN_PROGRESS
    )

    override fun legalMovesFrom(state: GameState, position: Position) = emptyList<Move>()

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return (0 until boardSize * boardSize)
            .filter { state.board[it] == null }
            .map { Move(PLACE, Position(it / boardSize, it % boardSize)) }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        val newBoard = state.board.copyOf()
        newBoard[move.to.row * boardSize + move.to.col] = TicTacToePiece(state.currentTurn)
        val next = state.currentTurn.opponent()
        val candidate = state.copy(
            board       = newBoard,
            currentTurn = next,
            moveHistory = state.moveHistory + move
        )
        return candidate.copy(status = gameStatus(candidate))
    }

    override fun gameStatus(state: GameState): GameStatus {
        // Check for a winner first
        for (line in winLines) {
            val ps = line.map { state.board[it] as? TicTacToePiece }
            if (ps.all { it?.color == PieceColor.WHITE }) return GameStatus.WHITE_WINS
            if (ps.all { it?.color == PieceColor.BLACK }) return GameStatus.BLACK_WINS
        }
        // Board full → draw
        if (state.board.none { it == null }) return GameStatus.DRAW
        // Early draw: no win line is still achievable by either player.
        // A line is "live" if it contains at most one color (not mixed).
        val anyLiveForWhite = winLines.any { line ->
            val ps = line.map { state.board[it] as? TicTacToePiece }
            ps.none { it?.color == PieceColor.BLACK }   // no black piece blocks white
        }
        val anyLiveForBlack = winLines.any { line ->
            val ps = line.map { state.board[it] as? TicTacToePiece }
            ps.none { it?.color == PieceColor.WHITE }   // no white piece blocks black
        }
        if (!anyLiveForWhite && !anyLiveForBlack) return GameStatus.DRAW
        return GameStatus.IN_PROGRESS
    }

    override fun evaluate(state: GameState): Int {
        return when (gameStatus(state)) {
            // Scale by moves used: AI prefers to win (or lose) as quickly as possible
            GameStatus.WHITE_WINS -> 1_000_000 - state.moveHistory.size * 10
            GameStatus.BLACK_WINS -> -(1_000_000 - state.moveHistory.size * 10)
            GameStatus.DRAW       -> 0
            else -> {
                var score = 0
                for (line in winLines) {
                    val ps = line.map { state.board[it] as? TicTacToePiece }
                    val w  = ps.count { it?.color == PieceColor.WHITE }
                    val b  = ps.count { it?.color == PieceColor.BLACK }
                    // Urgently weight threats one step from winning/losing
                    if (b == 0 && w > 0) score += when (w) {
                        winLength - 1 -> 800   // one move from winning — seize/block immediately
                        winLength - 2 -> 80    // building toward win
                        else          -> w * w
                    }
                    if (w == 0 && b > 0) score -= when (b) {
                        winLength - 1 -> 800   // must block — opponent one move from winning
                        winLength - 2 -> 80
                        else          -> b * b
                    }
                }
                score
            }
        }
    }

    /** Returns the indices in [GameState.board] that form the winning line, or null. */
    fun winningLine(state: GameState): List<Int>? {
        for (line in winLines) {
            val ps = line.map { state.board[it] as? TicTacToePiece }
            if (ps.all { it?.color == PieceColor.WHITE } || ps.all { it?.color == PieceColor.BLACK })
                return line
        }
        return null
    }
}
