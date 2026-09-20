package com.mkdev.mkboardgames.engine

import com.mkdev.mkboardgames.games.amazons.AmazonsRuleEngine
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessPieceType
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine

/**
 * Negamax + PVS AI with iterative deepening, aspiration windows, a
 * transposition table, null-move pruning for chess, late-move reductions,
 * check extensions, quiescence, killer/history ordering, and mate-distance
 * scoring.
 *
 * [maxDepth]     - depth ceiling; use a large value and let time decide
 * [timeLimitMs]  - wall-clock budget
 * [quiesceDepth] - extra tactical plies at a leaf
 * [varietyWindowOverride] - 0 = deterministic strongest move
 */
class AIPlayer(
    private val engine: RuleEngine,
    val maxDepth: Int = 3,
    private val timeLimitMs: Long = 1000L,
    private val quiesceDepth: Int = 0,
    private val varietyWindowOverride: Int = -1
) {

    companion object {
        private const val INF = 1_000_000
        private const val MATE = 100_000
        private const val MATE_BOUND = MATE - 1_000
        private const val MAX_PLY = 128
        private const val TT_MAX_SIZE = 1 shl 20
        private const val MOVE_COORDINATE_BITS = 4
        private const val MOVE_COORDINATE_SIZE = 1 shl MOVE_COORDINATE_BITS
        private const val MOVE_COORDINATE_MASK = MOVE_COORDINATE_SIZE - 1
        private const val MOVE_SQUARE_COUNT = MOVE_COORDINATE_SIZE * MOVE_COORDINATE_SIZE
        private const val MOVE_PROMOTION_BUCKETS = 16
    }

    @Volatile private var deadline = Long.MAX_VALUE
    @Volatile private var searchAborted = false
    private var nodeCounter = 0L

    private enum class TTFlag { EXACT, LOWER, UPPER }

    private class TTEntry(
        val depth: Int,
        val score: Int,
        val flag: TTFlag,
        val bestKey: Int
    )

    private val tt = HashMap<Long, TTEntry>(1 shl 16)
    private val historyScores = IntArray(MOVE_SQUARE_COUNT * MOVE_SQUARE_COUNT * 2)
    private val killers = Array(MAX_PLY) { IntArray(2) { -1 } }
    private val pathKeys = LongArray(MAX_PLY)

    private val isChess = engine is ChessRuleEngine
    private val chessEngine = engine as? ChessRuleEngine
    private val isAmazons = engine is AmazonsRuleEngine

    // A fixed seed makes ordering and search behaviour reproducible between turns.
    private val zobristTable: Array<LongArray> = run {
        val random = java.util.Random(0x5EED_CAFEL)
        Array(100 * 8) { LongArray(16) { random.nextLong() } }
    }

    private fun boardKey(state: GameState): Long {
        var key = if (state.currentTurn == PieceColor.WHITE) 0L else -0x5DEECE66DL

        for (i in state.board.indices) {
            val piece = state.board[i] ?: continue
            val colorOffset = if (piece.color == PieceColor.WHITE) 0 else 8
            val typeOffset = if (piece is ChessPiece) {
                piece.type.ordinal
            } else {
                (piece.value() / 100).coerceIn(0, 7)
            }
            key = key xor zobristTable[i % zobristTable.size][
                (colorOffset + typeOffset).coerceIn(0, 15)
            ]
        }

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

        if (state.metadata["castleWK"] == true) key = key xor 0x51A3D29BL
        if (state.metadata["castleWQ"] == true) key = key xor 0x8F12C4D6L
        if (state.metadata["castleBK"] == true) key = key xor 0x2D7E91A4L
        if (state.metadata["castleBQ"] == true) key = key xor 0xB6043FA1L

        val enPassant = (state.metadata["enPassant"] as? Int)?.coerceIn(-1, 7) ?: -1
        key = key xor ((enPassant + 1).toLong() * 0x9E3779B9L)
        return key
    }

    private fun squareKey(row: Int, col: Int): Int =
        ((row and MOVE_COORDINATE_MASK) * MOVE_COORDINATE_SIZE) +
            (col and MOVE_COORDINATE_MASK)

    private fun moveKey(move: Move): Int {
        val from = squareKey(move.from.row, move.from.col)
        val to = squareKey(move.to.row, move.to.col)
        val promotion = if (move.promotionType != null) {
            1 + (move.promotionType.hashCode() and (MOVE_PROMOTION_BUCKETS - 2))
        } else {
            0
        }
        return (from * MOVE_SQUARE_COUNT + to) * MOVE_PROMOTION_BUCKETS + promotion
    }

    private fun historyIndex(move: Move, color: PieceColor): Int {
        val from = squareKey(move.from.row, move.from.col)
        val to = squareKey(move.to.row, move.to.col)
        return (from * MOVE_SQUARE_COUNT + to) * 2 +
            if (color == PieceColor.WHITE) 0 else 1
    }

    fun bestMove(state: GameState): Move? {
        deadline = System.currentTimeMillis() + timeLimitMs.coerceAtLeast(1L)
        searchAborted = false
        nodeCounter = 0L

        if (tt.size > TT_MAX_SIZE) tt.clear()
        historyScores.fill(0)
        for (killer in killers) {
            killer[0] = -1
            killer[1] = -1
        }

        val rootColor = state.currentTurn
        val rootMoves = orderedMoves(state, rootColor, 0, -1)
        if (rootMoves.isEmpty()) return null
        if (rootMoves.size == 1) return rootMoves[0]

        // Resolve immediate wins before the timed search. Amazons is excluded
        // because applying every candidate also has a large mobility cost there.
        if (!isAmazons) {
            for (move in rootMoves) {
                if (engine.applyMove(state, move).status == winStatus(rootColor)) {
                    return move
                }
            }
        }

        var bestMove = rootMoves.first()
        var bestScore = -INF
        var lastScores: List<Pair<Move, Int>> = emptyList()
        var previousScore = 0
        val depthCap = maxDepth.coerceAtMost(MAX_PLY - 8)
        pathKeys[0] = boardKey(state)

        // Preserve the generic-game safety net from the previous search:
        // games with an immediate one-move threat should block it even when a
        // short time budget prevents a deeper completed iteration.
        if (!isChess) {
            immediateBlockingMove(state)?.let { return it }
        }

        for (depth in 1..depthCap) {
            if (System.currentTimeMillis() > deadline) break
            searchAborted = false

            var alphaWindow = if (depth >= 4) previousScore - 50 else -INF
            var betaWindow = if (depth >= 4) previousScore + 50 else INF
            var iterationResult: Pair<Move, List<Pair<Move, Int>>>? = null

            while (true) {
                iterationResult = searchRoot(
                    state,
                    rootMoves,
                    depth,
                    alphaWindow,
                    betaWindow,
                    bestMove
                )
                if (searchAborted) break

                val score = iterationResult.second.maxOfOrNull { it.second } ?: 0
                if (score <= alphaWindow && alphaWindow > -INF) {
                    alphaWindow = -INF
                    continue
                }
                if (score >= betaWindow && betaWindow < INF) {
                    betaWindow = INF
                    continue
                }
                break
            }

            if (searchAborted || iterationResult == null) break
            bestMove = iterationResult.first
            lastScores = iterationResult.second
            bestScore = lastScores.maxOfOrNull { it.second } ?: 0
            previousScore = bestScore

            if (bestScore >= MATE_BOUND) break
        }

        return pickWithVariety(state, bestMove, bestScore, lastScores)
    }

    private fun immediateBlockingMove(state: GameState): Move? {
        val player = state.currentTurn
        val opponent = player.opponent()
        val opponentState = state.copy(currentTurn = opponent)
        val threats = engine.allLegalMoves(opponentState, opponent).filter { move ->
            engine.applyMove(opponentState, move).status == winStatus(opponent)
        }
        if (threats.size != 1) return null

        val threatenedSquare = threats.first().to
        return engine.allLegalMoves(state, player).firstOrNull { move ->
            move.to == threatenedSquare
        }
    }

    private fun searchRoot(
        state: GameState,
        moves: List<Move>,
        depth: Int,
        alphaIn: Int,
        betaIn: Int,
        previousBest: Move
    ): Pair<Move, List<Pair<Move, Int>>> {
        var alpha = alphaIn
        val beta = betaIn
        var best = moves.first()
        var bestScore = -INF
        val scored = ArrayList<Pair<Move, Int>>(moves.size)
        val previousKey = moveKey(previousBest)
        val ordered = moves.sortedByDescending { if (moveKey(it) == previousKey) 1 else 0 }
        val wantExactScores = varietyWindow() > 0

        for ((index, move) in ordered.withIndex()) {
            if (System.currentTimeMillis() > deadline) {
                searchAborted = true
                break
            }

            val child = applyForSearch(state, move)
            val score = if (index == 0 || wantExactScores) {
                val lowerBound = if (wantExactScores) {
                    minOf(alpha, bestScore - varietyWindow() - 1).coerceAtLeast(-INF)
                } else {
                    alpha
                }
                -negamax(child, depth - 1, -beta, -lowerBound, 1, true)
            } else {
                var candidate = -negamax(child, depth - 1, -alpha - 1, -alpha, 1, true)
                if (!searchAborted && candidate > alpha && candidate < beta) {
                    candidate = -negamax(child, depth - 1, -beta, -alpha, 1, true)
                }
                candidate
            }

            if (searchAborted) break
            scored += move to score
            if (score > bestScore) {
                bestScore = score
                best = move
            }
            if (score > alpha) alpha = score
        }

        if (searchAborted || scored.size != ordered.size) searchAborted = true
        return best to scored
    }

    private fun varietyWindow(): Int = when {
        varietyWindowOverride >= 0 -> varietyWindowOverride
        maxDepth <= 3 -> 60
        maxDepth <= 5 -> 30
        else -> 0
    }

    private fun pickWithVariety(
        state: GameState,
        best: Move,
        bestScore: Int,
        scored: List<Pair<Move, Int>>
    ): Move {
        val window = varietyWindow()
        if (window == 0 || scored.isEmpty()) return best
        if (bestScore >= MATE_BOUND || bestScore <= -MATE_BOUND) return best

        val myPrior = if (state.moveHistory.size >= 2) {
            state.moveHistory[state.moveHistory.size - 2]
        } else {
            null
        }
        fun isRepeat(move: Move) =
            myPrior != null && move.from == myPrior.to && move.to == myPrior.from

        val pool = scored
            .filter { (move, score) -> score >= bestScore - window && !isRepeat(move) }
            .map { it.first }
        return if (pool.isNotEmpty()) pool.random() else best
    }

    private fun negamax(
        state: GameState,
        depthIn: Int,
        alphaIn: Int,
        betaIn: Int,
        ply: Int,
        allowNull: Boolean
    ): Int {
        if ((++nodeCounter and 2047L) == 0L &&
            System.currentTimeMillis() > deadline
        ) {
            searchAborted = true
        }
        if (searchAborted) return 0

        val color = state.currentTurn
        if (state.status != GameStatus.IN_PROGRESS) {
            return terminalScore(state, color, ply)
        }
        if (ply >= MAX_PLY - 2) return evalFor(state, color)

        // Repetition is a path property. The fixed-depth stack avoids treating
        // every position with old move history as a draw in the TT.
        val key = boardKey(state)
        if ((0 until ply).any { pathKeys[it] == key }) return 0
        pathKeys[ply] = key

        var alpha = maxOf(alphaIn, -MATE + ply)
        var beta = minOf(betaIn, MATE - ply - 1)
        if (alpha >= beta) return alpha

        val inCheck = chessEngine?.isInCheck(state, color) == true
        var depth = depthIn
        if (inCheck) depth += 1

        if (depth <= 0) {
            return if (quiesceDepth > 0) {
                quiescence(state, alpha, beta, ply, quiesceDepth)
            } else {
                evalFor(state, color)
            }
        }

        val entry = tt[key]
        var ttMoveKey = -1
        if (entry != null) {
            ttMoveKey = entry.bestKey
            if (entry.depth >= depth) {
                val score = fromTT(entry.score, ply)
                when (entry.flag) {
                    TTFlag.EXACT -> return score
                    TTFlag.LOWER -> if (score >= beta) return score
                    TTFlag.UPPER -> if (score <= alpha) return score
                }
            }
        }

        val isPvNode = beta - alpha > 1
        if (isChess && allowNull && !isPvNode && !inCheck && depth >= 3 &&
            hasNonPawnMaterial(state, color)
        ) {
            val staticEval = evalFor(state, color)
            if (staticEval >= beta) {
                val reduction = if (depth >= 6) 3 else 2
                val nullState = state.copy(
                    currentTurn = color.opponent(),
                    metadata = state.metadata.toMutableMap().apply { put("enPassant", -1) }
                )
                val nullScore = -negamax(
                    nullState,
                    depth - 1 - reduction,
                    -beta,
                    -beta + 1,
                    ply + 1,
                    false
                )
                if (searchAborted) return 0
                if (nullScore >= beta) return if (nullScore >= MATE_BOUND) beta else nullScore
            }
        }

        val moves = orderedMoves(state, color, ply, ttMoveKey)
        if (moves.isEmpty()) return terminalScoreNoMoves(state, color, inCheck, ply)

        var bestScore = -INF
        var bestKey = -1
        val originalAlpha = alpha

        for ((index, move) in moves.withIndex()) {
            val child = applyForSearch(state, move)
            val quiet = !move.isCapture && move.promotionType == null
            var score: Int

            if (index == 0) {
                score = -negamax(child, depth - 1, -beta, -alpha, ply + 1, true)
            } else {
                var reduction = 0
                if (depth >= 3 && index >= 4 && quiet && !inCheck) {
                    reduction = 1 +
                        (if (index >= 8) 1 else 0) +
                        (if (depth >= 6) 1 else 0)
                    reduction = minOf(reduction, depth - 2)
                }

                score = -negamax(
                    child,
                    depth - 1 - reduction,
                    -alpha - 1,
                    -alpha,
                    ply + 1,
                    true
                )
                if (!searchAborted && score > alpha && reduction > 0) {
                    score = -negamax(child, depth - 1, -alpha - 1, -alpha, ply + 1, true)
                }
                if (!searchAborted && score > alpha && score < beta) {
                    score = -negamax(child, depth - 1, -beta, -alpha, ply + 1, true)
                }
            }

            if (searchAborted) return 0
            if (score > bestScore) {
                bestScore = score
                bestKey = moveKey(move)
            }
            if (score > alpha) alpha = score

            if (alpha >= beta) {
                if (quiet) recordCutoff(move, color, ply, depth)
                break
            }
        }

        val flag = when {
            bestScore <= originalAlpha -> TTFlag.UPPER
            bestScore >= beta -> TTFlag.LOWER
            else -> TTFlag.EXACT
        }
        tt[key] = TTEntry(depth, toTT(bestScore, ply), flag, bestKey)
        return bestScore
    }

    private fun quiescence(
        state: GameState,
        alphaIn: Int,
        beta: Int,
        ply: Int,
        depthLeft: Int
    ): Int {
        if ((++nodeCounter and 2047L) == 0L &&
            System.currentTimeMillis() > deadline
        ) {
            searchAborted = true
        }
        if (searchAborted) return 0

        val color = state.currentTurn
        if (state.status != GameStatus.IN_PROGRESS) return terminalScore(state, color, ply)

        var alpha = alphaIn
        val inCheck = chessEngine?.isInCheck(state, color) == true
        val standPat = evalFor(state, color)
        if (depthLeft <= 0 || ply >= MAX_PLY - 2) {
            if (!inCheck) return standPat
            val evasions = engine.allLegalMoves(state, color)
            if (evasions.isEmpty()) return -MATE + ply
            return evasions.maxOf { move ->
                -evalFor(applyForSearch(state, move), color.opponent())
            }
        }

        var best: Int
        if (inCheck) {
            best = -INF
        } else {
            if (standPat >= beta) return standPat
            best = standPat
            if (standPat > alpha) alpha = standPat
        }

        val moves = tacticalMoves(state, color, inCheck)
        if (moves.isEmpty()) return if (inCheck) -MATE + ply else standPat

        for (move in moves) {
            val score = -quiescence(
                applyForSearch(state, move),
                -beta,
                -alpha,
                ply + 1,
                depthLeft - 1
            )
            if (searchAborted) return 0
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return best
    }

    private fun orderedMoves(
        state: GameState,
        color: PieceColor,
        ply: Int,
        ttMoveKey: Int = -1
    ): List<Move> {
        val killer0 = if (ply < MAX_PLY) killers[ply][0] else -1
        val killer1 = if (ply < MAX_PLY) killers[ply][1] else -1
        val legal = engine.allLegalMoves(state, color)
        if (legal.size <= 1) return legal

        return legal.map { move ->
            val key = moveKey(move)
            var score = orderingScore(state, move)
            if (key == ttMoveKey) {
                score += 1_000_000
            } else if (!move.isCapture) {
                if (key == killer0) score += 18_000
                else if (key == killer1) score += 12_000
                score += historyScores[historyIndex(move, color)]
            }
            move to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun recordCutoff(move: Move, color: PieceColor, ply: Int, depth: Int) {
        val key = moveKey(move)
        if (ply < MAX_PLY && killers[ply][0] != key) {
            killers[ply][1] = killers[ply][0]
            killers[ply][0] = key
        }
        val index = historyIndex(move, color)
        historyScores[index] =
            (historyScores[index] + depth * depth).coerceAtMost(100_000)
    }

    private fun orderingScore(state: GameState, move: Move): Int {
        val piece = state.get(move.from) ?: return 0
        var score = 0
        if (move.isCapture) {
            val victim = move.captures.maxOfOrNull { state.get(it)?.value() ?: 0 } ?: 0
            score += 10_000 + victim * 16 - piece.value()
        }
        if (move.promotionType != null) score += 9_000
        if (move.to.row in 2..5 && move.to.col in 2..5) score += 35

        if (piece is ChessPiece) {
            val central = move.to.row in 2..5 && move.to.col in 2..5
            score += when (piece.type) {
                ChessPieceType.PAWN ->
                    if (piece.color == PieceColor.WHITE) move.to.row else 7 - move.to.row
                ChessPieceType.KNIGHT -> 12 + if (central) 12 else 0
                ChessPieceType.BISHOP -> 15 + if (central) 10 else 0
                ChessPieceType.ROOK -> 12 + if (move.to.row in 2..5 || move.to.col in 2..5) 8 else 0
                ChessPieceType.QUEEN -> 18
                ChessPieceType.KING -> 10
            }
        }
        return score
    }

    private fun mvvLva(state: GameState, move: Move): Int {
        val victim = move.captures.sumOf { state.get(it)?.value() ?: 100 }
        val attacker = state.get(move.from)?.value() ?: 1000
        return victim * 16 - attacker
    }

    private fun tacticalMoves(
        state: GameState,
        color: PieceColor,
        inCheck: Boolean
    ): List<Move> {
        val legal = engine.allLegalMoves(state, color)
        if (!isChess) {
            return legal.filter { it.isCapture }.sortedByDescending { mvvLva(state, it) }
        }
        if (inCheck) return legal.sortedByDescending { mvvLva(state, it) }
        return legal
            .filter { it.isCapture || it.promotionType != null }
            .sortedByDescending {
                mvvLva(state, it) + if (it.promotionType != null) 6_000 else 0
            }
    }

    private fun evalFor(state: GameState, color: PieceColor): Int {
        val whiteScore = if (isAmazons) {
            (engine as AmazonsRuleEngine).evaluateForSearch(state)
        } else {
            engine.evaluate(state)
        }
        return if (color == PieceColor.WHITE) whiteScore else -whiteScore
    }

    private fun terminalScore(state: GameState, color: PieceColor, ply: Int): Int =
        when (state.status) {
            GameStatus.WHITE_WINS ->
                if (color == PieceColor.WHITE) MATE - ply else -(MATE - ply)
            GameStatus.BLACK_WINS ->
                if (color == PieceColor.BLACK) MATE - ply else -(MATE - ply)
            else -> evalFor(state, color)
        }

    private fun terminalScoreNoMoves(
        state: GameState,
        color: PieceColor,
        inCheck: Boolean,
        ply: Int
    ): Int = if (isChess) {
        if (inCheck) -MATE + ply else 0
    } else {
        evalFor(state, color)
    }

    private fun hasNonPawnMaterial(state: GameState, color: PieceColor): Boolean =
        state.board.any { piece ->
            piece != null &&
                piece.color == color &&
                piece is ChessPiece &&
                piece.type != ChessPieceType.PAWN &&
                piece.type != ChessPieceType.KING
        }

    private fun toTT(score: Int, ply: Int): Int = when {
        score >= MATE_BOUND -> score + ply
        score <= -MATE_BOUND -> score - ply
        else -> score
    }

    private fun fromTT(score: Int, ply: Int): Int = when {
        score >= MATE_BOUND -> score - ply
        score <= -MATE_BOUND -> score + ply
        else -> score
    }

    private fun winStatus(color: PieceColor): GameStatus =
        if (color == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS

    private fun applyForSearch(state: GameState, move: Move): GameState =
        if (isAmazons) {
            (engine as AmazonsRuleEngine).applyMoveForSearch(state, move)
        } else {
            engine.applyMove(state, move)
        }
}