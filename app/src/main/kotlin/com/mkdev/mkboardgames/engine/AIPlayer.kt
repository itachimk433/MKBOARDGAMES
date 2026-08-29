package com.mkdev.mkboardgames.engine

/**
 * Minimax AI with alpha-beta pruning, iterative deepening, Zobrist hashing,
 * transposition table, quiescence search, and MVV-LVA capture ordering.
 *
 * [maxDepth]       — main search depth ceiling
 * [timeLimitMs]    — wall-clock budget; search aborts gracefully when exceeded
 * [quiesceDepth]   — extra plies of capture-only search after depth 0
 *                    (set > 0 for Chess to avoid horizon effect)
 */
class AIPlayer(
    private val engine: RuleEngine,
    val maxDepth: Int = 3,
    private val timeLimitMs: Long = 1000L,
    private val quiesceDepth: Int = 0,
    private val varietyWindowOverride: Int = -1   // -1 = auto; 0 = always deterministic best
) {

    @Volatile private var deadline = Long.MAX_VALUE
    @Volatile private var searchAborted = false

    // ─── Transposition table ──────────────────────────────────────────────────

    private enum class TTFlag { EXACT, LOWER, UPPER }
    private data class TTEntry(val depth: Int, val score: Int, val flag: TTFlag)

    private val tt = HashMap<Long, TTEntry>(1 shl 16)

    /** Simple Zobrist key: XOR of random longs indexed by (cell, pieceType). */
    private val zobristTable: Array<LongArray> = Array(64 * 8) { LongArray(16) { java.util.Random().nextLong() } }

    private fun boardKey(state: GameState): Long {
        var key = if (state.currentTurn == PieceColor.WHITE) 0L else -1L
        for (i in state.board.indices) {
            val p = state.board[i] ?: continue
            val colorOff = if (p.color == PieceColor.WHITE) 0 else 8
            val typeOff  = (p.value() / 100).coerceIn(0, 7)
            key = key xor zobristTable[i % (64 * 8)][(colorOff + typeOff).coerceIn(0, 15)]
        }
        // A Shogi position is not defined by the board alone: the pieces in
        // each player's hand are also part of the position.
        for ((color, pieces) in state.hands) {
            var handKey = if (color == PieceColor.WHITE) 0x13579BDFL else 0x2468ACE0L
            pieces.groupingBy { it.symbol() }
                .eachCount()
                .toSortedMap()
                .forEach { (symbol, count) ->
                    handKey = handKey * 31 + symbol.hashCode() * 17 + count
                }
            key = key xor handKey
        }
        return key
    }

    // ─── Root search ─────────────────────────────────────────────────────────

    fun bestMove(state: GameState): Move? {
        deadline = System.currentTimeMillis() + timeLimitMs
        searchAborted = false
        tt.clear()
        val moves = orderedMoves(state, state.currentTurn)
        if (moves.isEmpty()) return null

        // Tactical moves must never be lost to search depth or a short time
        // budget. This is especially important for gravity games such as
        // Connect Four, where a playable three-in-a-row is an immediate threat.
        immediateTacticalMove(state)?.let { return it }

        val maximising = state.currentTurn == PieceColor.WHITE
        var completedBestMove: Move? = moves.first()
        var completedBestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
        var completedScores = emptyList<Pair<Move, Int>>()

        // Iterative deepening — start shallow, go deeper within time limit
        for (depth in 1..maxDepth) {
            if (System.currentTimeMillis() > deadline) break
            searchAborted = false
            val depthScored = mutableListOf<Pair<Move, Int>>()
            var depthBest = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
            var depthBestMove: Move? = null

            for (move in moves) {
                if (System.currentTimeMillis() > deadline) {
                    searchAborted = true
                    break
                }
                val next  = engine.applyMove(state, move)
                val score = minimax(next, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, !maximising)
                if (searchAborted) break
                depthScored.add(move to score)
                when {
                    maximising  && score > depthBest -> { depthBest = score; depthBestMove = move }
                    !maximising && score < depthBest -> { depthBest = score; depthBestMove = move }
                }
            }

            // Never publish a result from a partially searched iteration.
            // The old implementation could replace a good shallow result with
            // whichever move happened to be visited before the timeout.
            if (searchAborted || depthScored.size != moves.size || depthBestMove == null) break
            completedScores = depthScored
            completedBestMove = depthBestMove
            completedBestScore = depthBest
        }

        if (completedScores.isEmpty()) return completedBestMove

        // Repetition guard: never immediately undo this player's last move
        val myPrior = if (state.moveHistory.size >= 2)
            state.moveHistory[state.moveHistory.size - 2] else null
        fun isRepeat(m: Move) = myPrior != null && m.from == myPrior.to && m.to == myPrior.from

        // Variety window — wider at low depth so the AI never plays the same game twice.
        // Narrower at high depth for stronger, more consistent play.
        // varietyWindowOverride >= 0 forces a specific window (0 = always deterministic best).
        val window = when {
            varietyWindowOverride >= 0 -> varietyWindowOverride
            maxDepth <= 3 -> 60
            maxDepth <= 5 -> 30
            else          -> 15
        }

        // Window = 0 (Hard mode): always return the deterministic best move (first in ordered list).
        // This prevents the "all moves score 0 → random" failure on solved games like 3×3 TicTacToe.
        if (window == 0) {
            val strict = completedScores.filter { (_, s) -> s == completedBestScore }.map { it.first }
            return strict.firstOrNull() ?: completedBestMove ?: completedScores.first().first
        }

        val pool = completedScores.filter { (m, s) ->
            val inRange = if (maximising) s >= completedBestScore - window else s <= completedBestScore + window
            inRange && !isRepeat(m)
        }.map { it.first }

        if (pool.isNotEmpty()) return pool.random()

        val strict = completedScores.filter { (_, s) -> s == completedBestScore }.map { it.first }
        return strict.randomOrNull() ?: completedBestMove ?: completedScores.first().first
    }

    /**
     * Return a move that wins immediately, or the only move that prevents an
     * opponent win on their next turn. The opponent state is copied with its
     * turn changed because RuleEngine.applyMove uses state.currentTurn.
     */
    private fun immediateTacticalMove(state: GameState): Move? {
        val player = state.currentTurn
        val opponent = player.opponent()

        val winningMove = engine.allLegalMoves(state, player).firstOrNull { move ->
            engine.applyMove(state, move).status == winStatus(player)
        }
        if (winningMove != null) return winningMove

        val opponentState = state.copy(currentTurn = opponent)
        val threats = engine.allLegalMoves(opponentState, opponent).filter { move ->
            engine.applyMove(opponentState, move).status == winStatus(opponent)
        }
        if (threats.size != 1) return null

        val threat = threats.first().to
        return engine.allLegalMoves(state, player).firstOrNull { move ->
            move.to == threat
        }
    }

    private fun winStatus(color: PieceColor) =
        if (color == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS

    // ─── Main search ──────────────────────────────────────────────────────────

    private fun minimax(
        state: GameState, depth: Int, alpha: Int, beta: Int, maximising: Boolean
    ): Int {
        if (System.currentTimeMillis() > deadline) {
            searchAborted = true
            return engine.evaluate(state)
        }
        if (state.status != GameStatus.IN_PROGRESS)  return engine.evaluate(state)

        // Transposition table lookup
        val key = boardKey(state)
        val cached = tt[key]
        if (cached != null && cached.depth >= depth) {
            when (cached.flag) {
                TTFlag.EXACT -> return cached.score
                TTFlag.LOWER -> if (cached.score >= beta)  return cached.score
                TTFlag.UPPER -> if (cached.score <= alpha) return cached.score
            }
        }

        if (depth == 0) {
            return if (quiesceDepth > 0)
                quiescence(state, alpha, beta, maximising, quiesceDepth)
            else
                engine.evaluate(state)
        }

        val color = if (maximising) PieceColor.WHITE else PieceColor.BLACK
        val moves = orderedMoves(state, color)
        if (moves.isEmpty()) return engine.evaluate(state)

        var a = alpha; var b = beta
        val score: Int
        if (maximising) {
            var best = Int.MIN_VALUE
            for (move in moves) {
                val childScore = minimax(engine.applyMove(state, move), depth - 1, a, b, false)
                if (searchAborted) return engine.evaluate(state)
                best = maxOf(best, childScore)
                a = maxOf(a, best)
                if (b <= a) break
            }
            score = best
        } else {
            var best = Int.MAX_VALUE
            for (move in moves) {
                val childScore = minimax(engine.applyMove(state, move), depth - 1, a, b, true)
                if (searchAborted) return engine.evaluate(state)
                best = minOf(best, childScore)
                b = minOf(b, best)
                if (b <= a) break
            }
            score = best
        }

        // Store in TT
        val flag = when {
            score <= alpha -> TTFlag.UPPER
            score >= beta  -> TTFlag.LOWER
            else           -> TTFlag.EXACT
        }
        tt[key] = TTEntry(depth, score, flag)
        return score
    }

    // ─── Quiescence search ───────────────────────────────────────────────────

    private fun quiescence(
        state: GameState, alpha: Int, beta: Int, maximising: Boolean, depthLeft: Int
    ): Int {
        if (System.currentTimeMillis() > deadline) {
            searchAborted = true
            return engine.evaluate(state)
        }
        if (state.status != GameStatus.IN_PROGRESS)  return engine.evaluate(state)

        val standPat = engine.evaluate(state)

        if (maximising) {
            if (standPat >= beta) return standPat
            if (depthLeft == 0)  return standPat
            var best = standPat
            var a    = maxOf(alpha, standPat)
            val captures = engine.allLegalMoves(state, PieceColor.WHITE)
                .filter { it.isCapture }
                .sortedByDescending { mvvLva(state, it) }
            for (move in captures) {
                if (System.currentTimeMillis() > deadline) {
                    searchAborted = true
                    break
                }
                val score = quiescence(engine.applyMove(state, move), a, beta, false, depthLeft - 1)
                if (searchAborted) return engine.evaluate(state)
                best = maxOf(best, score)
                a    = maxOf(a, best)
                if (beta <= a) break
            }
            return best
        } else {
            if (standPat <= alpha) return standPat
            if (depthLeft == 0)   return standPat
            var best = standPat
            var b    = minOf(beta, standPat)
            val captures = engine.allLegalMoves(state, PieceColor.BLACK)
                .filter { it.isCapture }
                .sortedByDescending { mvvLva(state, it) }
            for (move in captures) {
                if (System.currentTimeMillis() > deadline) {
                    searchAborted = true
                    break
                }
                val score = quiescence(engine.applyMove(state, move), alpha, b, true, depthLeft - 1)
                if (searchAborted) return engine.evaluate(state)
                best = minOf(best, score)
                b    = minOf(b, best)
                if (b <= alpha) break
            }
            return best
        }
    }

    // ─── Move ordering ────────────────────────────────────────────────────────

    private fun mvvLva(state: GameState, move: Move): Int {
        val victimValue   = move.captures.sumOf { pos -> state.get(pos)?.value() ?: 100 }
        val attackerValue = state.get(move.from)?.value() ?: 1000
        return victimValue * 16 - attackerValue
    }

    private fun orderingScore(state: GameState, move: Move): Int {
        val piece = state.get(move.from) ?: return 0
        val captureBonus = if (move.isCapture) {
            val victim = move.captures.maxOfOrNull { pos -> state.get(pos)?.value() ?: 0 } ?: 0
            val attacker = piece.value()
            1000 + victim * 16 - attacker
        } else 0
        val promotionBonus = if (move.promotionType != null) 600 else 0
        val centerBonus = if (move.to.row in 2..5 && move.to.col in 2..5) 35 else 0
        val pieceSquareBonus = when (piece) {
            is com.mkdev.mkboardgames.games.chess.ChessPiece -> {
                val row = move.to.row
                val col = move.to.col
                when (piece.type) {
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.PAWN -> if (piece.color == PieceColor.WHITE) row else 7 - row
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.KNIGHT -> 12 + (if (row in 2..5 && col in 2..5) 12 else 0)
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.BISHOP -> 15 + (if (row in 2..5 && col in 2..5) 10 else 0)
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.ROOK -> 12 + (if (row in 2..5 || col in 2..5) 8 else 0)
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.QUEEN -> 18
                    com.mkdev.mkboardgames.games.chess.ChessPieceType.KING -> 10
                }
            }
            else -> 0
        }
        return captureBonus + promotionBonus + centerBonus + pieceSquareBonus
    }

    private fun orderedMoves(state: GameState, color: PieceColor): List<Move> {
        return engine.allLegalMoves(state, color)
            .sortedByDescending { orderingScore(state, it) }
    }
}
