package com.mkdev.mkboardgames.challenges

import com.mkdev.mkboardgames.engine.AIPlayer
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessPieceType
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine

/**
 * Challenge-aware move selection for autoplay.
 *
 * The normal chess AI optimizes material and position, but a challenge can
 * require a specific kind of win. Autoplay therefore filters illegal objective
 * moves first, follows authored recommendations when they are legal, and only
 * then uses a deterministic position search as a tie-breaker.
 */
data class ChessChallengeAutoplayProgress(
    val startingMaterialDeficit: Int = 0,
    val playerMovedQueen: Boolean = false,
    val playerCastledKingside: Boolean = false,
    val playerUsedNonKnightCapture: Boolean = false,
    val promotedPawn: Boolean = false,
    val playerPromotionTypes: List<String> = emptyList(),
    val playerCapturedBlackKnights: Int = 0,
    val opponentCapturedWhiteBishop: Boolean = false,
    val lastPlayerMoveWasRook: Boolean = false,
)

class ChessChallengeAutoplayAi(
    private val engine: ChessRuleEngine,
) {
    private val positionAi = AIPlayer(
        engine = engine,
        maxDepth = 4,
        timeLimitMs = 700L,
        quiesceDepth = 2,
        varietyWindowOverride = 0,
    )

    fun choosePlayerMove(
        state: GameState,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Move? {
        if (state.status != GameStatus.IN_PROGRESS || state.currentTurn != playerColor) return null

        val legal = engine.allLegalMoves(state, playerColor)
            .filterNot { violatesChallengeRule(state, it, puzzle, playerColor, progress) }
        if (legal.isEmpty()) return null

        val preferred = runCatching { positionAi.bestMove(state) }.getOrNull()
        val recommended = puzzle.recommendedMoves.asSequence()
            .mapNotNull { findMove(state, it) }
            .firstOrNull { candidate ->
                legal.any { it == candidate } && isSafeContinuation(state, candidate, puzzle, playerColor, progress)
            }
        if (recommended != null) return recommended

        val scored = legal.map { move ->
            val next = engine.applyMove(state, move)
            val nextProgress = progressAfterPlayerMove(state, move, playerColor, progress)
            ScoredMove(
                move = move,
                score = scorePlayerMove(
                    state = state,
                    next = next,
                    move = move,
                    puzzle = puzzle,
                    playerColor = playerColor,
                    progress = nextProgress,
                    preferred = move == preferred,
                ),
            )
        }
        return scored
            .sortedWith(compareByDescending<ScoredMove> { it.score }.thenBy { moveKey(it.move) })
            .firstOrNull()
            ?.move
    }

    /**
     * The opponent should keep autoplay solvable instead of accidentally
     * removing a required bishop or the only rook needed by a challenge.
     */
    fun chooseOpponentMove(
        state: GameState,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Move? {
        if (state.status != GameStatus.IN_PROGRESS || state.currentTurn == playerColor) return null

        val legal = engine.allLegalMoves(state, state.currentTurn)
        if (legal.isEmpty()) return null
        val safe = legal.filterNot { sabotagesObjective(state, it, puzzle, playerColor) }
        val candidates = safe.ifEmpty { legal }

        return candidates
            .map { move ->
                val next = engine.applyMove(state, move)
                ScoredMove(
                    move = move,
                    score = scoreOpponentMove(next, move, puzzle, playerColor, progress),
                )
            }
            .sortedWith(compareByDescending<ScoredMove> { it.score }.thenBy { moveKey(it.move) })
            .firstOrNull()
            ?.move
    }

    private fun scorePlayerMove(
        state: GameState,
        next: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
        preferred: Boolean,
    ): Int {
        val playerWin = playerWinStatus(playerColor)
        val opponentWin = playerWinStatus(playerColor.opponent())
        val objectiveWin = isSuccessfulResolution(next, playerColor, puzzle) &&
            objectiveSatisfied(next, move, puzzle, playerColor, progress)
        var score = when {
            objectiveWin -> 1_000_000
            next.status == playerWin -> -400_000
            next.status == opponentWin -> -1_000_000
            next.status == GameStatus.DRAW -> -300_000
            else -> 0
        }

        if (next.status == GameStatus.IN_PROGRESS) {
            val evaluation = engine.evaluate(next)
            score += if (playerColor == PieceColor.WHITE) evaluation else -evaluation
        }
        if (preferred) score += 4_000

        val movingPiece = state.get(move.from) as? ChessPiece
        when (puzzle.condition) {
            ChallengeCondition.CHECKMATE_WITHIN_LIMIT ->
                if (next.status == GameStatus.IN_PROGRESS &&
                    engine.isInCheck(next, playerColor.opponent())
                ) score += 8_000
            ChallengeCondition.PROMOTE_AND_WIN ->
                if (move.promotionType != null) score += 60_000
            ChallengeCondition.CASTLE_AND_WIN ->
                if (move.metadata["castle"] == "K") score += 60_000
            ChallengeCondition.PRESERVE_BISHOPS ->
                score += whiteBishopCount(next, playerColor) * 2_000
            ChallengeCondition.ROOK_CHECKMATE ->
                if (movingPiece?.type == ChessPieceType.ROOK) score += 40_000
            ChallengeCondition.KNIGHT_HUNTER ->
                score += move.captures.count { position ->
                    (state.get(position) as? ChessPiece)?.let {
                        it.color == playerColor.opponent() && it.type == ChessPieceType.KNIGHT
                    } == true
                } * 50_000
            ChallengeCondition.NO_QUEEN_USE,
            ChallengeCondition.MATERIAL_COMEBACK,
            ChallengeCondition.KNIGHT_CAPTURE_ONLY -> Unit
        }
        return score
    }

    private fun scoreOpponentMove(
        next: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Int {
        val playerWin = playerWinStatus(playerColor)
        var score = when (next.status) {
            GameStatus.WHITE_WINS ->
                if (playerWin == GameStatus.WHITE_WINS) 200_000 else -1_000_000
            GameStatus.BLACK_WINS ->
                if (playerWin == GameStatus.BLACK_WINS) 200_000 else -1_000_000
            GameStatus.DRAW -> -250_000
            GameStatus.IN_PROGRESS -> 0
        }
        if (next.status == GameStatus.IN_PROGRESS) {
            val evaluation = engine.evaluate(next)
            score += if (playerColor == PieceColor.WHITE) evaluation else -evaluation
        }
        // A quiet reply is more useful for an autoplay solver than a tactical
        // reply that closes the position before the objective can be met.
        score -= move.captures.size * 80
        if (puzzle.condition == ChallengeCondition.PRESERVE_BISHOPS &&
            progress.opponentCapturedWhiteBishop
        ) {
            score -= 100_000
        }
        return score
    }

    private fun isSafeContinuation(
        state: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Boolean {
        val next = engine.applyMove(state, move)
        return next.status == GameStatus.IN_PROGRESS ||
            (isSuccessfulResolution(next, playerColor, puzzle) &&
                objectiveSatisfied(
                    next,
                    move,
                    puzzle,
                    playerColor,
                    progressAfterPlayerMove(state, move, playerColor, progress),
                ))
    }

    private fun objectiveSatisfied(
        state: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Boolean {
        if (!isSuccessfulResolution(state, playerColor, puzzle)) return false
        return when (puzzle.condition) {
            ChallengeCondition.CHECKMATE_WITHIN_LIMIT -> true
            ChallengeCondition.NO_QUEEN_USE -> !progress.playerMovedQueen
            ChallengeCondition.KNIGHT_HUNTER -> progress.playerCapturedBlackKnights >= 2
            ChallengeCondition.PROMOTE_AND_WIN -> progress.promotedPawn &&
                promotionRequirementSatisfied(progress.playerPromotionTypes, puzzle.objective.requiredPromotion)
            ChallengeCondition.CASTLE_AND_WIN -> progress.playerCastledKingside
            ChallengeCondition.PRESERVE_BISHOPS ->
                !progress.opponentCapturedWhiteBishop && whiteBishopCount(state, playerColor) >= 2
            ChallengeCondition.ROOK_CHECKMATE ->
                progress.lastPlayerMoveWasRook || (state.get(move.from) as? ChessPiece)?.type == ChessPieceType.ROOK
            ChallengeCondition.MATERIAL_COMEBACK -> progress.startingMaterialDeficit >= 5
            ChallengeCondition.KNIGHT_CAPTURE_ONLY -> !progress.playerUsedNonKnightCapture
        }
    }

    private fun violatesChallengeRule(
        state: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): Boolean {
        val movingPiece = state.get(move.from) as? ChessPiece ?: return false
        return when (puzzle.condition) {
            ChallengeCondition.NO_QUEEN_USE ->
                movingPiece.type == ChessPieceType.QUEEN || move.promotionType == "QUEEN"
            ChallengeCondition.KNIGHT_HUNTER ->
                move.captures.any { position ->
                    (state.get(position) as? ChessPiece)?.let { captured ->
                        captured.color == playerColor.opponent() &&
                            progress.playerCapturedBlackKnights < 2 &&
                            captured.type != ChessPieceType.KNIGHT
                    } == true
                }
            ChallengeCondition.KNIGHT_CAPTURE_ONLY ->
                move.isCapture && movingPiece.type != ChessPieceType.KNIGHT
            else -> false
        }
    }

    private fun sabotagesObjective(
        state: GameState,
        move: Move,
        puzzle: ChessPuzzle,
        playerColor: PieceColor,
    ): Boolean {
        if (puzzle.condition == ChallengeCondition.PRESERVE_BISHOPS &&
            move.captures.any { position ->
                (state.get(position) as? ChessPiece)?.let {
                    it.color == playerColor && it.type == ChessPieceType.BISHOP
                } == true
            }
        ) {
            return true
        }
        if (puzzle.condition == ChallengeCondition.ROOK_CHECKMATE &&
            state.board.filterIsInstance<ChessPiece>()
                .count { it.color == playerColor && it.type == ChessPieceType.ROOK } <= 1 &&
            move.captures.any { position ->
                (state.get(position) as? ChessPiece)?.let {
                    it.color == playerColor && it.type == ChessPieceType.ROOK
                } == true
            }
        ) {
            return true
        }
        return false
    }

    private fun progressAfterPlayerMove(
        state: GameState,
        move: Move,
        playerColor: PieceColor,
        progress: ChessChallengeAutoplayProgress,
    ): ChessChallengeAutoplayProgress {
        val movingPiece = state.get(move.from) as? ChessPiece
        val capturedKnights = move.captures.count { position ->
            (state.get(position) as? ChessPiece)?.let {
                it.color == playerColor.opponent() && it.type == ChessPieceType.KNIGHT
            } == true
        }
        val promotionTypes = move.promotionType?.let { progress.playerPromotionTypes + it }
            ?: progress.playerPromotionTypes
        return progress.copy(
            playerMovedQueen = progress.playerMovedQueen ||
                movingPiece?.type == ChessPieceType.QUEEN ||
                move.promotionType == "QUEEN",
            playerCastledKingside = progress.playerCastledKingside ||
                move.metadata["castle"] == "K",
            playerUsedNonKnightCapture = progress.playerUsedNonKnightCapture ||
                (move.isCapture && movingPiece?.type != ChessPieceType.KNIGHT),
            promotedPawn = progress.promotedPawn || move.promotionType != null,
            playerPromotionTypes = promotionTypes,
            playerCapturedBlackKnights = progress.playerCapturedBlackKnights + capturedKnights,
            lastPlayerMoveWasRook = movingPiece?.type == ChessPieceType.ROOK,
        )
    }

    private fun promotionRequirementSatisfied(
        promotionTypes: List<String>,
        requirement: PromotionRequirement,
    ): Boolean {
        if (requirement == PromotionRequirement.ANY) return promotionTypes.isNotEmpty()
        return promotionTypes.isNotEmpty() && promotionTypes.all { it == requirement.name }
    }

    private fun whiteBishopCount(state: GameState, playerColor: PieceColor): Int =
        state.board.filterIsInstance<ChessPiece>()
            .count { it.color == playerColor && it.type == ChessPieceType.BISHOP }

    private fun playerWinStatus(color: PieceColor) =
        if (color == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS

    private fun isSuccessfulResolution(
        state: GameState,
        playerColor: PieceColor,
        puzzle: ChessPuzzle,
    ): Boolean =
        state.status == playerWinStatus(playerColor) ||
            (puzzle.condition == ChallengeCondition.CHECKMATE_WITHIN_LIMIT &&
                state.status == GameStatus.DRAW)

    private fun findMove(state: GameState, uci: String): Move? {
        if (uci.length < 4) return null
        val from = position(uci.substring(0, 2)) ?: return null
        val to = position(uci.substring(2, 4)) ?: return null
        val promotion = uci.getOrNull(4)?.uppercaseChar()?.let {
            when (it) {
                'Q' -> "QUEEN"
                'R' -> "ROOK"
                'B' -> "BISHOP"
                'N' -> "KNIGHT"
                else -> null
            }
        }
        return engine.legalMovesFrom(state, from).firstOrNull { move ->
            move.to == to && (promotion == null || move.promotionType == promotion)
        }
    }

    private fun position(square: String) =
        if (square.length == 2) {
            val col = square[0] - 'a'
            val row = 8 - (square[1] - '0')
            if (row in 0..7 && col in 0..7) {
                com.mkdev.mkboardgames.engine.Position(row, col)
            } else {
                null
            }
        } else {
            null
        }

    private fun moveKey(move: Move): String =
        "${move.from.row}:${move.from.col}:${move.to.row}:${move.to.col}:${move.promotionType.orEmpty()}"

    private data class ScoredMove(
        val move: Move,
        val score: Int,
    )
}