package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor

/**
 * Dedicated Shogi search. Used by AIPlayer when the engine is a
 * [ShogiRuleEngine]; all other games keep the generic search.
 *
 * Features: iterative deepening, PVS, aspiration windows, transposition
 * table with correct Zobrist hashing (board + hands), killer/history move
 * ordering, check extension, late-move reductions, mate-distance scoring,
 * path-based repetition detection, and a capture/promotion/check quiescence.
 */
class ShogiSearch(
    private val engine: ShogiRuleEngine,
    private val maxDepth: Int,
    private val timeLimitMs: Long,
    private val quiesceDepth: Int,
    private val varietyWindow: Int,
) {
    companion object {
        private const val INF = 1_000_000
        private const val MATE = 100_000
        private const val MATE_BOUND = MATE - 1_000
        private const val MAX_PLY = 96
        private const val TT_MAX_SIZE = 1 shl 20
    }

    private enum class Flag { EXACT, LOWER, UPPER }
    private class Entry(val depth: Int, val score: Int, val flag: Flag, val bestKey: Int)

    private val tt = HashMap<Long, Entry>(1 shl 16)
    private val killers = Array(MAX_PLY) { IntArray(2) { -1 } }
    private val history = IntArray(2 * 82 * 82)
    private val pathKeys = LongArray(MAX_PLY + 2)

    private var deadline = Long.MAX_VALUE
    private var aborted = false
    private var nodes = 0L

    // ------------------------------------------------------------------
    // Zobrist
    // ------------------------------------------------------------------

    private val rnd = java.util.Random(0x5EEDBEEFL)
    // [square][color(2)][type(8)][promoted(2)]
    private val pieceKeys = Array(81) { Array(2) { Array(8) { LongArray(2) { rnd.nextLong() } } } }
    // [color][type][count 0..18]
    private val handKeys = Array(2) { Array(8) { LongArray(19) { rnd.nextLong() } } }
    private val sideKey = rnd.nextLong()

    private fun hash(state: GameState): Long {
        var h = if (state.currentTurn == PieceColor.BLACK) sideKey else 0L
        for (i in 0 until 81) {
            val p = state.board[i] as? ShogiPiece ?: continue
            h = h xor pieceKeys[i][p.color.ordinal][p.type.ordinal][if (p.promoted) 1 else 0]
        }
        for (color in PieceColor.entries) {
            val counts = IntArray(8)
            for (piece in state.hands[color].orEmpty()) {
                val sp = piece as? ShogiPiece ?: continue
                counts[sp.type.ordinal]++
            }
            for (t in 0 until 8) {
                val n = counts[t].coerceAtMost(18)
                if (n > 0) h = h xor handKeys[color.ordinal][t][n]
            }
        }
        return h
    }

    // ------------------------------------------------------------------
    // Move keys
    // ------------------------------------------------------------------

    private fun sq(r: Int, c: Int): Int = if (r < 0) 81 else r * 9 + c

    private fun moveKey(m: Move): Int {
        val from = if (m.metadata["drop"] != null) 81 + (m.from.col.coerceIn(0, 7)) else sq(m.from.row, m.from.col)
        val to = sq(m.to.row, m.to.col)
        val promo = if (m.promotionType != null || m.metadata["promote"] == true) 1 else 0
        return (from * 81 + to) * 2 + promo
    }

    private fun histIndex(m: Move, color: PieceColor): Int {
        val from = if (m.metadata["drop"] != null) 81 else sq(m.from.row, m.from.col)
        val to = sq(m.to.row, m.to.col)
        return color.ordinal * 82 * 82 + from.coerceAtMost(81) * 82 + to
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    fun bestMove(state: GameState): Move? {
        deadline = System.currentTimeMillis() + timeLimitMs.coerceAtLeast(1L)
        aborted = false
        nodes = 0L
        if (tt.size > TT_MAX_SIZE) tt.clear()
        history.fill(0)
        for (k in killers) { k[0] = -1; k[1] = -1 }

        val root = state.copy(moveHistory = emptyList())
        val rootMoves = orderMoves(root, engine.searchMoves(root), 0, -1)
        if (rootMoves.isEmpty()) return null
        if (rootMoves.size == 1) return rootMoves[0]

        // Immediate mate / king capture.
        for (m in rootMoves) {
            val child = engine.applyForSearch(root, m)
            if (engine.winnerStatusIfKingMissing(child) != null) return m
            if (engine.searchMoves(child).isEmpty()) return m // checkmate
        }

        pathKeys[0] = hash(root)
        var best = rootMoves.first()
        var bestScore = -INF
        var lastScores: List<Pair<Move, Int>> = emptyList()
        var prev = 0
        val cap = maxDepth.coerceIn(1, MAX_PLY - 8)

        for (depth in 1..cap) {
            if (System.currentTimeMillis() > deadline) break
            aborted = false
            var a = if (depth >= 4) prev - 60 else -INF
            var b = if (depth >= 4) prev + 60 else INF
            var res: Pair<Move, List<Pair<Move, Int>>>
            while (true) {
                res = searchRoot(root, rootMoves, depth, a, b, best)
                if (aborted) break
                val s = res.second.maxOfOrNull { it.second } ?: 0
                if (s <= a && a > -INF) { a = -INF; continue }
                if (s >= b && b < INF) { b = INF; continue }
                break
            }
            if (aborted) break
            best = res.first
            lastScores = res.second
            bestScore = lastScores.maxOfOrNull { it.second } ?: 0
            prev = bestScore
            if (bestScore >= MATE_BOUND) break
        }

        return pickWithVariety(root, best, bestScore, lastScores)
    }

    private fun searchRoot(
        state: GameState,
        moves: List<Move>,
        depth: Int,
        alphaIn: Int,
        beta: Int,
        prevBest: Move,
    ): Pair<Move, List<Pair<Move, Int>>> {
        var alpha = alphaIn
        var best = moves.first()
        var bestScore = -INF
        val scored = ArrayList<Pair<Move, Int>>(moves.size)
        val prevKey = moveKey(prevBest)
        val ordered = moves.sortedByDescending { if (moveKey(it) == prevKey) 1 else 0 }
        val wantExact = varietyWindow > 0

        for ((i, m) in ordered.withIndex()) {
            if (System.currentTimeMillis() > deadline) { aborted = true; break }
            val child = engine.applyForSearch(state, m)
            val score: Int
            if (i == 0 || wantExact) {
                val lo = if (wantExact) minOf(alpha, bestScore - varietyWindow - 1).coerceAtLeast(-INF) else alpha
                score = -negamax(child, depth - 1, -beta, -lo, 1, true)
            } else {
                var s = -negamax(child, depth - 1, -alpha - 1, -alpha, 1, true)
                if (!aborted && s > alpha && s < beta) {
                    s = -negamax(child, depth - 1, -beta, -alpha, 1, true)
                }
                score = s
            }
            if (aborted) break
            scored += m to score
            if (score > bestScore) { bestScore = score; best = m }
            if (score > alpha) alpha = score
        }
        if (aborted || scored.size != ordered.size) aborted = true
        return best to scored
    }

    private fun pickWithVariety(
        state: GameState,
        best: Move,
        bestScore: Int,
        scored: List<Pair<Move, Int>>,
    ): Move {
        if (varietyWindow == 0 || scored.isEmpty()) return best
        if (bestScore >= MATE_BOUND || bestScore <= -MATE_BOUND) return best
        val pool = scored.filter { it.second >= bestScore - varietyWindow }.map { it.first }
        return if (pool.isNotEmpty()) pool.random() else best
    }

    // ------------------------------------------------------------------
    // Core search
    // ------------------------------------------------------------------

    private fun evalFor(state: GameState, color: PieceColor): Int {
        val w = ShogiEvaluator.evaluate(state)
        return if (color == PieceColor.WHITE) w else -w
    }

    private fun negamax(
        state: GameState,
        depthIn: Int,
        alphaIn: Int,
        betaIn: Int,
        ply: Int,
        allowExt: Boolean,
    ): Int {
        if ((++nodes and 511L) == 0L && System.currentTimeMillis() > deadline) aborted = true
        if (aborted) return 0

        val color = state.currentTurn
        if (ply >= MAX_PLY - 2) return evalFor(state, color)

        // A missing king means the previous mover captured it (pseudo-terminal).
        engine.winnerStatusIfKingMissing(state)?.let { st ->
            val won = (st == GameStatus.WHITE_WINS && color == PieceColor.WHITE) ||
                (st == GameStatus.BLACK_WINS && color == PieceColor.BLACK)
            return if (won) MATE - ply else -(MATE - ply)
        }

        val key = hash(state)
        for (i in 0 until ply) if (pathKeys[i] == key) return 0 // repetition
        pathKeys[ply] = key

        var alpha = maxOf(alphaIn, -MATE + ply)
        val beta = minOf(betaIn, MATE - ply - 1)
        if (alpha >= beta) return alpha

        val inCheck = engine.inCheck(state, color)
        var depth = depthIn
        if (inCheck && allowExt) depth += 1

        if (depth <= 0) {
            return if (quiesceDepth > 0) quiescence(state, alpha, beta, ply, quiesceDepth)
            else evalFor(state, color)
        }

        val entry = tt[key]
        var ttKey = -1
        if (entry != null) {
            ttKey = entry.bestKey
            if (entry.depth >= depth) {
                val s = fromTT(entry.score, ply)
                when (entry.flag) {
                    Flag.EXACT -> return s
                    Flag.LOWER -> if (s >= beta) return s
                    Flag.UPPER -> if (s <= alpha) return s
                }
            }
        }

        val moves = orderMoves(state, engine.searchMoves(state), ply, ttKey)
        // Shogi: no legal moves means the side to move loses (checkmated or
        // stalemated alike).
        if (moves.isEmpty()) return -MATE + ply

        var bestScore = -INF
        var bestKey = -1
        val origAlpha = alpha

        for ((i, m) in moves.withIndex()) {
            val child = engine.applyForSearch(state, m)
            val quiet = !m.isCapture && m.promotionType == null && m.metadata["promote"] != true
            var score: Int
            if (i == 0) {
                score = -negamax(child, depth - 1, -beta, -alpha, ply + 1, true)
            } else {
                var red = 0
                if (depth >= 3 && i >= 5 && quiet && !inCheck) {
                    red = 1 + (if (i >= 10) 1 else 0)
                    red = minOf(red, depth - 2)
                }
                score = -negamax(child, depth - 1 - red, -alpha - 1, -alpha, ply + 1, true)
                if (!aborted && score > alpha && red > 0) {
                    score = -negamax(child, depth - 1, -alpha - 1, -alpha, ply + 1, true)
                }
                if (!aborted && score > alpha && score < beta) {
                    score = -negamax(child, depth - 1, -beta, -alpha, ply + 1, true)
                }
            }
            if (aborted) return 0
            if (score > bestScore) { bestScore = score; bestKey = moveKey(m) }
            if (score > alpha) alpha = score
            if (alpha >= beta) {
                if (quiet) recordCutoff(m, color, ply, depth)
                break
            }
        }

        val flag = when {
            bestScore <= origAlpha -> Flag.UPPER
            bestScore >= beta -> Flag.LOWER
            else -> Flag.EXACT
        }
        tt[key] = Entry(depth, toTT(bestScore, ply), flag, bestKey)
        return bestScore
    }

    private fun quiescence(state: GameState, alphaIn: Int, beta: Int, ply: Int, left: Int): Int {
        if ((++nodes and 511L) == 0L && System.currentTimeMillis() > deadline) aborted = true
        if (aborted) return 0

        val color = state.currentTurn
        engine.winnerStatusIfKingMissing(state)?.let { st ->
            val won = (st == GameStatus.WHITE_WINS && color == PieceColor.WHITE) ||
                (st == GameStatus.BLACK_WINS && color == PieceColor.BLACK)
            return if (won) MATE - ply else -(MATE - ply)
        }

        var alpha = alphaIn
        val inCheck = engine.inCheck(state, color)
        val standPat = evalFor(state, color)
        if (left <= 0 || ply >= MAX_PLY - 2) return standPat

        var best: Int
        if (inCheck) {
            best = -INF
        } else {
            if (standPat >= beta) return standPat
            best = standPat
            if (standPat > alpha) alpha = standPat
        }

        val all = engine.searchMoves(state)
        if (all.isEmpty()) return -MATE + ply

        // In check: search all evasions. Otherwise: captures and promotions.
        val tactical = if (inCheck) all else all.filter {
            it.isCapture || it.promotionType != null || it.metadata["promote"] == true
        }
        if (tactical.isEmpty()) return standPat

        val ordered = tactical.sortedByDescending { mvvLva(state, it) }
        for (m in ordered) {
            val s = -quiescence(engine.applyForSearch(state, m), -beta, -alpha, ply + 1, left - 1)
            if (aborted) return 0
            if (s > best) best = s
            if (s > alpha) alpha = s
            if (alpha >= beta) break
        }
        return best
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    private fun pieceScore(p: ShogiPiece?): Int = when {
        p == null -> 0
        p.promoted -> when (p.type) {
            ShogiPieceType.ROOK -> 1300
            ShogiPieceType.BISHOP -> 1150
            else -> 500
        }
        else -> p.type.points
    }

    private fun mvvLva(state: GameState, m: Move): Int {
        val victim = m.captures.sumOf { c -> pieceScore(state.get(c) as? ShogiPiece) }
        val attacker = if (m.metadata["drop"] != null) 0
        else pieceScore(state.get(m.from) as? ShogiPiece)
        val promo = if (m.promotionType != null || m.metadata["promote"] == true) 400 else 0
        return victim * 16 - attacker + promo
    }

    private fun orderMoves(state: GameState, legal: List<Move>, ply: Int, ttKey: Int): List<Move> {
        if (legal.size <= 1) return legal
        val color = state.currentTurn
        val k0 = if (ply < MAX_PLY) killers[ply][0] else -1
        val k1 = if (ply < MAX_PLY) killers[ply][1] else -1
        val enemyKing = findKing(state, color.opponent())

        return legal.map { m ->
            val key = moveKey(m)
            var s = 0
            if (key == ttKey) s += 2_000_000
            if (m.isCapture) s += 100_000 + mvvLva(state, m)
            if (m.promotionType != null || m.metadata["promote"] == true) s += 60_000
            if (!m.isCapture) {
                if (key == k0) s += 30_000 else if (key == k1) s += 20_000
                s += history[histIndex(m, color)]
            }
            // Cheap proximity-to-enemy-king bonus (helps checks and drops
            // near the king surface early without a full givesCheck call).
            if (enemyKing != null) {
                val d = maxOf(kotlin.math.abs(m.to.row - enemyKing.first), kotlin.math.abs(m.to.col - enemyKing.second))
                if (d <= 2) s += (3 - d) * 400
            }
            if (m.metadata["drop"] != null) s += 200
            m to s
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun findKing(state: GameState, color: PieceColor): Pair<Int, Int>? {
        for (i in 0 until 81) {
            val p = state.board[i] as? ShogiPiece ?: continue
            if (p.type == ShogiPieceType.KING && p.color == color) return (i / 9) to (i % 9)
        }
        return null
    }

    private fun recordCutoff(m: Move, color: PieceColor, ply: Int, depth: Int) {
        val key = moveKey(m)
        if (ply < MAX_PLY && killers[ply][0] != key) {
            killers[ply][1] = killers[ply][0]
            killers[ply][0] = key
        }
        val idx = histIndex(m, color)
        history[idx] = (history[idx] + depth * depth).coerceAtMost(50_000)
    }

    private fun toTT(s: Int, ply: Int) = when {
        s >= MATE_BOUND -> s + ply
        s <= -MATE_BOUND -> s - ply
        else -> s
    }

    private fun fromTT(s: Int, ply: Int) = when {
        s >= MATE_BOUND -> s - ply
        s <= -MATE_BOUND -> s + ply
        else -> s
    }
}