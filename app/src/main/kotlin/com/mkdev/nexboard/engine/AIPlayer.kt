package com.mkdev.nexboard.engine

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
    private val quiesceDepth: Int = 0
) {

    @Volatile private var deadline = Long.MAX_VALUE

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
        return key
    }

    // ─── Root search ─────────────────────────────────────────────────────────

    fun bestMove(state: GameState): Move? {
        deadline = System.currentTimeMillis() + timeLimitMs
        tt.clear()
        val moves = orderedMoves(state, state.currentTurn)
        if (moves.isEmpty()) return null

        val maximising = state.currentTurn == PieceColor.WHITE
        var bestMove: Move? = null
        var bestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE

        // Iterative deepening — start shallow, go deeper within time limit
        val allScored = mutableListOf<Pair<Move, Int>>()
        for (depth in 1..maxDepth) {
            if (System.currentTimeMillis() > deadline) break
            allScored.clear()
            var depthBest = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE

            for (move in moves) {
                if (System.currentTimeMillis() > deadline) break
                val next  = engine.applyMove(state, move)
                val score = minimax(next, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, !maximising)
                allScored.add(move to score)
                when {
                    maximising  && score > depthBest -> { depthBest = score; bestMove = move }
                    !maximising && score < depthBest -> { depthBest = score; bestMove = move }
                }
            }
            bestScore = depthBest
        }

        if (allScored.isEmpty()) return bestMove

        // Repetition guard: never immediately undo this player's last move
        val myPrior = if (state.moveHistory.size >= 2)
            state.moveHistory[state.moveHistory.size - 2] else null
        fun isRepeat(m: Move) = myPrior != null && m.from == myPrior.to && m.to == myPrior.from

        // Variety window — wider at low depth so the AI never plays the same game twice
        val window = when {
            maxDepth <= 3 -> 60
            maxDepth <= 5 -> 30
            else          -> 15
        }

        val pool = allScored.filter { (m, s) ->
            val inRange = if (maximising) s >= bestScore - window else s <= bestScore + window
            inRange && !isRepeat(m)
        }.map { it.first }

        if (pool.isNotEmpty()) return pool.random()

        val strict = allScored.filter { (_, s) -> s == bestScore }.map { it.first }
        return strict.randomOrNull() ?: bestMove ?: allScored.first().first
    }

    // ─── Main search ──────────────────────────────────────────────────────────

    private fun minimax(
        state: GameState, depth: Int, alpha: Int, beta: Int, maximising: Boolean
    ): Int {
        if (System.currentTimeMillis() > deadline) return engine.evaluate(state)
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
                best = maxOf(best, minimax(engine.applyMove(state, move), depth - 1, a, b, false))
                a = maxOf(a, best)
                if (b <= a) break
            }
            score = best
        } else {
            var best = Int.MAX_VALUE
            for (move in moves) {
                best = minOf(best, minimax(engine.applyMove(state, move), depth - 1, a, b, true))
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
        if (System.currentTimeMillis() > deadline) return engine.evaluate(state)
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
                if (System.currentTimeMillis() > deadline) break
                val score = quiescence(engine.applyMove(state, move), a, beta, false, depthLeft - 1)
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
                if (System.currentTimeMillis() > deadline) break
                val score = quiescence(engine.applyMove(state, move), alpha, b, true, depthLeft - 1)
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

    private fun orderedMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = engine.allLegalMoves(state, color)
        val (captures, quiet) = moves.partition { it.isCapture }
        val sortedCaptures = captures.sortedByDescending { mvvLva(state, it) }
        return sortedCaptures + quiet
    }
}
