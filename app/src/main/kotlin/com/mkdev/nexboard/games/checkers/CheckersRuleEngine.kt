package com.mkdev.nexboard.games.checkers

import com.mkdev.nexboard.engine.*

class CheckersRuleEngine : RuleEngine {

    override fun initialState() = CheckersSetup.initialState()

    // ─── Move generation ──────────────────────────────────────────────────────

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = state.get(position) as? CheckersPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()
        val hasCaptures = anyCaptures(state, state.currentTurn)
        return if (hasCaptures) jumpMovesFrom(state.board, position, piece)
        else quietMovesFrom(state, position, piece)
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val hasCaptures = anyCaptures(state, color)
        val moves = mutableListOf<Move>()
        for (row in 0..7) for (col in 0..7) {
            val pos = Position(row, col)
            val p = state.get(pos) as? CheckersPiece ?: continue
            if (p.color != color) continue
            if (hasCaptures) moves += jumpMovesFrom(state.board, pos, p)
            else moves += quietMovesFrom(state, pos, p)
        }
        // Move ordering: captures first, then multi-captures before single
        return moves.sortedByDescending { it.captures.size }
    }

    private fun anyCaptures(state: GameState, color: PieceColor): Boolean {
        for (row in 0..7) for (col in 0..7) {
            val p = state.get(row, col) as? CheckersPiece ?: continue
            if (p.color == color && jumpMovesFrom(state.board, Position(row, col), p).isNotEmpty())
                return true
        }
        return false
    }

    // ─── Quiet moves ─────────────────────────────────────────────────────────

    private fun quietMovesFrom(state: GameState, from: Position, piece: CheckersPiece): List<Move> =
        moveDirs(piece).mapNotNull { dir ->
            val to = from + dir
            if (to.isValid() && state.get(to) == null) Move(from, to) else null
        }

    // ─── Jump / capture chains ────────────────────────────────────────────────

    /**
     * Returns all complete capture chains starting from [from].
     *
     * Men can capture in ALL 4 diagonal directions (forward AND backward).
     * Movement (quiet moves) remains forward-only.
     * Kings capture and move in all 4 diagonal directions.
     *
     * Allowing backward captures for men eliminates the "freeze" where the only
     * available capture is behind a piece but mandatory capture prevents all moves.
     */
    fun jumpMovesFrom(
        board: Array<Piece?>,
        from: Position,
        piece: CheckersPiece,
        alreadyCaptured: Set<Position> = emptySet()
    ): List<Move> {
        val results = mutableListOf<Move>()

        for (dir in allDiagonals()) {
            val over = from + dir
            val land = Position(from.row + dir.row * 2, from.col + dir.col * 2)
            if (!over.isValid() || !land.isValid()) continue

            val victim = board[over.row * 8 + over.col] as? CheckersPiece ?: continue
            if (victim.color == piece.color) continue
            if (over in alreadyCaptured) continue
            if (board[land.row * 8 + land.col] != null) continue

            val tempBoard = board.copyOf()
            tempBoard[from.row * 8 + from.col] = null
            tempBoard[over.row * 8 + over.col] = null
            val promoted = promote(piece, land)
            tempBoard[land.row * 8 + land.col] = promoted

            val newCaptured = alreadyCaptured + over

            // Promotion ends the multi-jump for men (standard English draughts)
            val continuations = if (promoted.isKing && !piece.isKing) emptyList()
            else jumpMovesFrom(tempBoard, land, promoted, newCaptured)

            if (continuations.isEmpty()) {
                results += Move(from, land, captures = newCaptured.toList())
            } else {
                for (cont in continuations) {
                    results += Move(from, cont.to, captures = newCaptured.toList() + cont.captures)
                }
            }
        }
        return results
    }

    // ─── Apply move ───────────────────────────────────────────────────────────

    override fun applyMove(state: GameState, move: Move): GameState {
        val piece = state.get(move.from) as? CheckersPiece ?: return state
        val newBoard = state.board.copyOf()

        for (cap in move.captures) newBoard[cap.row * 8 + cap.col] = null
        newBoard[move.from.row * 8 + move.from.col] = null
        newBoard[move.to.row * 8 + move.to.col] = promote(piece, move.to)

        val next = state.withBoard(newBoard, piece.color.opponent(), move)
        return next.copy(status = gameStatus(next))
    }

    // ─── Game status ─────────────────────────────────────────────────────────

    override fun gameStatus(state: GameState): GameStatus {
        val hasWhite = state.board.any { (it as? CheckersPiece)?.color == PieceColor.WHITE }
        val hasBlack = state.board.any { (it as? CheckersPiece)?.color == PieceColor.BLACK }
        if (!hasWhite) return GameStatus.BLACK_WINS
        if (!hasBlack) return GameStatus.WHITE_WINS
        if (allLegalMoves(state, state.currentTurn).isEmpty())
            return if (state.currentTurn == PieceColor.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS
        return GameStatus.IN_PROGRESS
    }

    // ─── Evaluation ──────────────────────────────────────────────────────────

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.WHITE_WINS) return  100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW)       return 0

        var score = 0
        var whitePieces = 0; var blackPieces = 0

        for (idx in 0 until 64) {
            val p = state.board[idx] as? CheckersPiece ?: continue
            val row = idx / 8; val col = idx % 8

            // Material: king worth 3x a man
            val material = if (p.isKing) 300 else 100

            val positional = when {
                p.isKing -> {
                    // Kings: centralise and stay active
                    val centerDist = Math.abs(3 - row) + Math.abs(3 - col)
                    (6 - centerDist) * 20
                }
                p.color == PieceColor.WHITE -> {
                    // White men advance toward row 0; protect row 7 from black promotion
                    val advancement   = (7 - row) * 12
                    val backRowGuard  = if (row == 7) 30 else 0
                    val edgePenalty   = if (col == 0 || col == 7) -15 else 0
                    val triangleBonus = if (row <= 2 && (col == 0 || col == 7)) -10 else 0
                    advancement + backRowGuard + edgePenalty + triangleBonus
                }
                else -> {
                    // Black men advance toward row 7; protect row 0 from white promotion
                    val advancement   = row * 12
                    val backRowGuard  = if (row == 0) 30 else 0
                    val edgePenalty   = if (col == 0 || col == 7) -15 else 0
                    val triangleBonus = if (row >= 5 && (col == 0 || col == 7)) -10 else 0
                    advancement + backRowGuard + edgePenalty + triangleBonus
                }
            }

            val pieceScore = material + positional
            if (p.color == PieceColor.WHITE) { score += pieceScore; whitePieces++ }
            else { score -= pieceScore; blackPieces++ }
        }

        // Encourage the winning side to trade pieces (speeds up win)
        val pieceDiff = whitePieces - blackPieces
        if (pieceDiff > 0) score += pieceDiff * 15       // white ahead: trades good
        else if (pieceDiff < 0) score += pieceDiff * 15  // black ahead: same (negative)

        // Tempo: reward the side to move having more captures available
        val currentCaptures = allLegalMoves(state, state.currentTurn).count { it.isCapture }
        val opponentCaptures = allLegalMoves(state, state.currentTurn.opponent()).count { it.isCapture }
        val captureBonus = (currentCaptures - opponentCaptures) * 8
        score += if (state.currentTurn == PieceColor.WHITE) captureBonus else -captureBonus

        return score
    }

    // ─── Direction helpers ────────────────────────────────────────────────────

    /** Directions a piece can MOVE (quiet). Kings: all 4. Men: forward 2. */
    private fun moveDirs(piece: CheckersPiece): List<Position> = when {
        piece.isKing -> allDiagonals()
        piece.color == PieceColor.WHITE -> listOf(Position(-1,-1), Position(-1,1))
        else -> listOf(Position(1,-1), Position(1,1))
    }

    private fun allDiagonals() =
        listOf(Position(-1,-1), Position(-1,1), Position(1,-1), Position(1,1))

    private fun promote(piece: CheckersPiece, landing: Position): CheckersPiece {
        if (piece.isKing) return piece
        val reached = (piece.color == PieceColor.WHITE && landing.row == 0) ||
                      (piece.color == PieceColor.BLACK && landing.row == 7)
        return if (reached) CheckersPiece(CheckersPieceType.KING, piece.color) else piece
    }
}
