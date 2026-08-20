package com.mkdev.mkboardgames.games.othello

import com.mkdev.mkboardgames.engine.*

class OthelloRuleEngine : RuleEngine {

    override fun initialState() = OthelloSetup.initialState()

    // ─── Move generation ──────────────────────────────────────────────────────

    /**
     * Othello selects destinations rather than source pieces, so a query for
     * an empty square returns the placement move for the active player.
     */
    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (!position.isValid() || state.get(position) != null) return emptyList()
        return allLegalMoves(state, state.currentTurn).filter { it.to == position }
    }

    /**
     * Every empty square that would flip at least one opponent disc.
     * move.from == move.to == placement square; move.captures = flipped positions.
     */
    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0..7) for (col in 0..7) {
            val pos = Position(row, col)
            if (state.get(pos) != null) continue
            val flips = getFlips(state, pos, color)
            if (flips.isNotEmpty()) moves += Move(pos, pos, flips)
        }
        return moves
    }

    private fun getFlips(state: GameState, pos: Position, color: PieceColor): List<Position> {
        val opp   = color.opponent()
        val flips = mutableListOf<Position>()
        for (dir in ALL_DIRS) {
            val line = mutableListOf<Position>()
            var cur  = pos + dir
            while (cur.isValid() && state.get(cur)?.color == opp) {
                line += cur; cur = cur + dir
            }
            if (line.isNotEmpty() && cur.isValid() && state.get(cur)?.color == color)
                flips += line
        }
        return flips
    }

    // ─── Apply move ───────────────────────────────────────────────────────────

    override fun applyMove(state: GameState, move: Move): GameState {
        val color    = state.currentTurn
        val legal = allLegalMoves(state, color).firstOrNull {
            it.to == move.to && it.captures.toSet() == move.captures.toSet()
        } ?: return state
        val newBoard = state.board.copyOf()

        // Place new disc
        newBoard[legal.to.row * 8 + legal.to.col] = OthelloPiece(color)
        // Flip captured discs
        for (pos in legal.captures)
            newBoard[pos.row * 8 + pos.col] = OthelloPiece(color)

        val next = state.withBoard(newBoard, color.opponent(), legal)

        // If opponent has no moves, give the turn back; if neither can move, end game
        return when {
            allLegalMoves(next, next.currentTurn).isNotEmpty() ->
                next.copy(status = GameStatus.IN_PROGRESS)

            allLegalMoves(next, color).isNotEmpty() ->
                // Opponent passes — current player goes again
                next.copy(currentTurn = color, status = GameStatus.IN_PROGRESS)

            else ->
                next.copy(status = gameStatus(next))
        }
    }

    // ─── Game status ─────────────────────────────────────────────────────────

    override fun gameStatus(state: GameState): GameStatus {
        val wMoves = allLegalMoves(state, PieceColor.WHITE).isNotEmpty()
        val bMoves = allLegalMoves(state, PieceColor.BLACK).isNotEmpty()
        if (wMoves || bMoves) return GameStatus.IN_PROGRESS

        var white = 0; var black = 0
        for (p in state.board) when ((p as? OthelloPiece)?.color) {
            PieceColor.WHITE -> white++
            PieceColor.BLACK -> black++
            else -> {}
        }
        return when {
            white > black -> GameStatus.WHITE_WINS
            black > white -> GameStatus.BLACK_WINS
            else          -> GameStatus.DRAW
        }
    }

    // ─── Evaluation ──────────────────────────────────────────────────────────

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.WHITE_WINS) return  100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW)       return 0

        var score = 0; var white = 0; var black = 0
        var whiteFrontier = 0; var blackFrontier = 0
        for (idx in 0 until 64) {
            val p = state.board[idx] as? OthelloPiece ?: continue
            val bonus = POS_WEIGHTS[idx / 8][idx % 8]
            if (p.color == PieceColor.WHITE) { score += bonus; white++ }
            else                             { score -= bonus; black++ }
            val row = idx / 8; val col = idx % 8
            if (ALL_DIRS.any { direction ->
                    val adjacent = Position(row + direction.row, col + direction.col)
                    adjacent.isValid() && state.get(adjacent) == null
                }) {
                if (p.color == PieceColor.WHITE) whiteFrontier++ else blackFrontier++
            }
        }

        val totalDiscs = white + black
        // Late-game (>48 discs placed): disc count is decisive
        val discWeight = if (totalDiscs > 48) 50 else 5
        score += (white - black) * discWeight

        // Mobility: reward having more moves than opponent (skip in endgame — expensive)
        if (totalDiscs <= 52) {
            val wMob = allLegalMoves(state.copy(currentTurn = PieceColor.WHITE), PieceColor.WHITE).size
            val bMob = allLegalMoves(state.copy(currentTurn = PieceColor.BLACK), PieceColor.BLACK).size
            // Mobility weight declines as board fills up (position dominates early, disc count late)
            val mobWeight = if (totalDiscs < 20) 12 else if (totalDiscs < 40) 8 else 4
            score += (wMob - bMob) * mobWeight
        }

        // Corners cannot be flipped and frontier discs are exposed to attack.
        val corners = listOf(0, 7, 56, 63)
        for (corner in corners) {
            when ((state.board[corner] as? OthelloPiece)?.color) {
                PieceColor.WHITE -> score += 180
                PieceColor.BLACK -> score -= 180
                else -> {}
            }
        }
        score += (blackFrontier - whiteFrontier) * 5

        return score
    }

    companion object {
        val ALL_DIRS = listOf(
            Position(-1,-1), Position(-1, 0), Position(-1, 1),
            Position( 0,-1),                  Position( 0, 1),
            Position( 1,-1), Position( 1, 0), Position( 1, 1)
        )

        // Classic Othello positional weights
        private val POS_WEIGHTS = arrayOf(
            intArrayOf(120,-20, 20,  5,  5, 20,-20,120),
            intArrayOf(-20,-40, -5, -5, -5, -5,-40,-20),
            intArrayOf( 20, -5, 15,  3,  3, 15, -5, 20),
            intArrayOf(  5, -5,  3,  3,  3,  3, -5,  5),
            intArrayOf(  5, -5,  3,  3,  3,  3, -5,  5),
            intArrayOf( 20, -5, 15,  3,  3, 15, -5, 20),
            intArrayOf(-20,-40, -5, -5, -5, -5,-40,-20),
            intArrayOf(120,-20, 20,  5,  5, 20,-20,120)
        )
    }
}
