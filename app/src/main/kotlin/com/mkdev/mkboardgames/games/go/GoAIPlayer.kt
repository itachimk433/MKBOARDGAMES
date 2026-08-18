package com.mkdev.mkboardgames.games.go

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import java.util.Random
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A small, self-contained Go searcher for the 13x13 board.
 *
 * It uses Monte Carlo Tree Search rather than the generic chess-style minimax
 * player. The rollout policy is intentionally informed by Go signals: captures,
 * liberties, connection, influence, star points, and territory. Difficulty only
 * changes the search budget, so Medium and Hard use the same strategy instead of
 * switching to a different collection of capture rules.
 */
class GoAIPlayer(
    private val engine: GoRuleEngine,
    private val maxIterations: Int,
    private val timeLimitMs: Long,
    private val deterministic: Boolean = false,
) {
    private val random = Random()

    private class Node(
        val state: GameState,
        val move: Move?,
        val parent: Node?,
        val prior: Double,
    ) {
        val children = mutableListOf<Node>()
        val untriedMoves = mutableListOf<Move>()
        var visits = 0
        var value = 0.0
    }

    fun bestMove(state: GameState): Move? {
        if (state.status != GameStatus.IN_PROGRESS) return null

        val placements = engine.allLegalMoves(state, state.currentTurn)
            .filterNot { it.metadata[GoRuleEngine.PASS_METADATA] == true }
        if (placements.isEmpty()) return GoRuleEngine.passMove()

        // Keeping the most promising candidates makes the search spend its
        // budget reading positions rather than giving one visit to every
        // empty intersection on an otherwise open 13x13 board.
        val candidates = placements
            .sortedByDescending { movePrior(state, it) }
            .take(ROOT_CANDIDATE_LIMIT)

        val root = Node(state, null, null, 0.0)
        root.untriedMoves += candidates

        val deadline = System.currentTimeMillis() + timeLimitMs
        var iterations = 0
        while (
            iterations < maxIterations &&
            System.currentTimeMillis() < deadline
        ) {
            var node = root

            while (
                node.untriedMoves.isEmpty() &&
                node.children.isNotEmpty() &&
                node.state.status == GameStatus.IN_PROGRESS
            ) {
                node = selectChild(node)
            }

            if (node.untriedMoves.isNotEmpty()) {
                val move = chooseExpansionMove(node.state, node.untriedMoves)
                node.untriedMoves.remove(move)
                val childState = engine.applyMove(node.state, move)
                val child = Node(
                    state = childState,
                    move = move,
                    parent = node,
                    prior = movePrior(node.state, move),
                )
                child.untriedMoves += candidateMoves(childState)
                node.children += child
                node = child
            }

            val result = rollout(node.state, state.currentTurn, deadline)
            var visited: Node? = node
            while (visited != null) {
                visited.visits++
                visited.value += result
                visited = visited.parent
            }
            iterations++
        }

        return root.children
            .filter { it.move != null }
            .maxWithOrNull(
                compareBy<Node> { it.visits }
                    .thenBy { rootValue(it) }
                    .thenByDescending { it.prior },
            )
            ?.move
            ?: candidates.firstOrNull()
    }

    private fun selectChild(node: Node): Node =
        node.children.maxWithOrNull(
            compareBy<Node> {
                val exploitation = it.value / it.visits.coerceAtLeast(1)
                val exploration = EXPLORATION * sqrt(
                    ln(node.visits.coerceAtLeast(2).toDouble()) /
                        it.visits.coerceAtLeast(1).toDouble(),
                )
                exploitation + exploration + it.prior / it.visits.coerceAtLeast(1)
            }.thenBy { it.prior },
        ) ?: node.children.first()

    private fun chooseExpansionMove(state: GameState, moves: List<Move>): Move {
        if (deterministic) return moves.maxWithOrNull(compareBy<Move> { movePrior(state, it) })
            ?: moves.first()

        val top = moves.sortedByDescending { movePrior(state, it) }
            .take(EXPANSION_POOL_SIZE)
        return top[random.nextInt(top.size)]
    }

    private fun candidateMoves(state: GameState): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        return engine.allLegalMoves(state, state.currentTurn)
            .filterNot { it.metadata[GoRuleEngine.PASS_METADATA] == true }
            .sortedByDescending { movePrior(state, it) }
            .take(NODE_CANDIDATE_LIMIT)
    }

    private fun rollout(
        startingState: GameState,
        rootColor: PieceColor,
        deadline: Long,
    ): Double {
        var state = startingState
        var plies = 0
        while (
            state.status == GameStatus.IN_PROGRESS &&
            plies < ROLLOUT_PLIES &&
            System.currentTimeMillis() < deadline
        ) {
            val moves = engine.allLegalMoves(state, state.currentTurn)
            val placements = moves.filterNot {
                it.metadata[GoRuleEngine.PASS_METADATA] == true
            }
            val move = if (placements.isEmpty()) {
                GoRuleEngine.passMove()
            } else {
                chooseRolloutMove(state, placements)
            }
            state = engine.applyMove(state, move)
            plies++
        }
        return positionValue(state, rootColor)
    }

    private fun chooseRolloutMove(state: GameState, moves: List<Move>): Move {
        val ranked = moves
            .map { it to movePrior(state, it) }
            .sortedByDescending { it.second }
            .take(ROLLOUT_POOL_SIZE)
        if (deterministic) return ranked.first().first

        // A little noise keeps rollouts diverse, while the top of the list
        // still favours saving groups and taking valuable tactical moves.
        val total = ranked.sumOf { (1.0 + (it.second - ranked.last().second) / 8.0).coerceAtLeast(0.1) }
        var target = random.nextDouble() * total
        for ((move, score) in ranked) {
            target -= (1.0 + (score - ranked.last().second) / 8.0).coerceAtLeast(0.1)
            if (target <= 0.0) return move
        }
        return ranked.last().first
    }

    private fun rootValue(node: Node): Double = node.value / node.visits.coerceAtLeast(1)

    private fun positionValue(state: GameState, rootColor: PieceColor): Double {
        val summary = engine.scoreSummary(state)
        val scoreDiff = summary.black.total - summary.white.total
        val captureDiff = (summary.black.captures - summary.white.captures) * CAPTURE_TIE_BREAK
        val influenceDiff = influence(state)
        val whitePerspective = scoreDiff + captureDiff + influenceDiff
        val rootPerspective = if (rootColor == PieceColor.BLACK) whitePerspective else -whitePerspective

        // Terminal results should dominate a rollout's small positional
        // differences, while in-progress positions still receive a useful
        // territory/influence estimate.
        val terminalBonus = when (state.status) {
            GameStatus.BLACK_WINS -> if (rootColor == PieceColor.BLACK) 1.0 else -1.0
            GameStatus.WHITE_WINS -> if (rootColor == PieceColor.WHITE) 1.0 else -1.0
            GameStatus.DRAW -> 0.0
            GameStatus.IN_PROGRESS -> 0.0
        }
        return if (terminalBonus != 0.0) {
            terminalBonus
        } else {
            tanh(rootPerspective / VALUE_SCALE)
        }
    }

    private fun movePrior(state: GameState, move: Move): Double {
        if (move.metadata[GoRuleEngine.PASS_METADATA] == true) return -100.0

        val size = state.boardSize
        val row = move.to.row
        val col = move.to.col
        val centre = (size - 1) / 2.0
        val centreDistance = kotlin.math.abs(row - centre) + kotlin.math.abs(col - centre)
        val centreScore = (size - centreDistance) / size.toDouble()
        val starPoint = if (
            row == 3 || row == size - 4
        ) {
            if (col == 3 || col == size - 4) 2.4 else 0.0
        } else if (row == size / 2 && col == size / 2) {
            2.0
        } else {
            0.0
        }
        val edgePenalty = if (row == 0 || col == 0 || row == size - 1 || col == size - 1) 1.8 else 0.0

        var friendly = 0
        var enemy = 0
        var empty = 0
        for (neighbor in neighbors(move.to, size)) {
            when (state.get(neighbor)?.color) {
                null -> empty++
                state.currentTurn -> friendly++
                else -> enemy++
            }
        }

        return move.captures.size * 10.0 +
            friendly * 1.45 +
            enemy * 1.15 +
            empty * 0.35 +
            centreScore * 1.8 +
            starPoint -
            edgePenalty
    }

    private fun influence(state: GameState): Double {
        var black = 0.0
        var white = 0.0
        for (row in 0 until state.boardSize) {
            for (col in 0 until state.boardSize) {
                val position = Position(row, col)
                val piece = state.get(position)
                if (piece != null) {
                    if (piece.color == PieceColor.BLACK) black += 1.0 else white += 1.0
                    continue
                }
                var blackNeighbours = 0
                var whiteNeighbours = 0
                for (neighbor in neighbors(position, state.boardSize)) {
                    when (state.get(neighbor)?.color) {
                        PieceColor.BLACK -> blackNeighbours++
                        PieceColor.WHITE -> whiteNeighbours++
                        null -> {}
                    }
                }
                if (blackNeighbours > whiteNeighbours) black += 0.18
                if (whiteNeighbours > blackNeighbours) white += 0.18
            }
        }
        return white - black
    }

    private fun neighbors(position: Position, size: Int): List<Position> =
        listOf(
            Position(position.row - 1, position.col),
            Position(position.row + 1, position.col),
            Position(position.row, position.col - 1),
            Position(position.row, position.col + 1),
        ).filter { it.isValid(size) }

    private companion object {
        const val ROOT_CANDIDATE_LIMIT = 96
        const val NODE_CANDIDATE_LIMIT = 36
        const val EXPANSION_POOL_SIZE = 8
        const val ROLLOUT_POOL_SIZE = 10
        const val ROLLOUT_PLIES = 72
        const val EXPLORATION = 0.95
        const val CAPTURE_TIE_BREAK = 0.08
        const val VALUE_SCALE = 18.0
    }
}