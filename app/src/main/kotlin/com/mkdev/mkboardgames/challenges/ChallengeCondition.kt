package com.mkdev.mkboardgames.challenges

enum class PromotionRequirement {
    ANY,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
}

/**
 * Everything that makes a challenge different from a normal mate puzzle.
 *
 * This is deliberately data, rather than logic derived from the level number.
 * A puzzle can therefore be moved, replaced, or re-ordered without silently
 * changing the rules that make its solution valid.
 */
data class ChallengeObjective(
    val condition: ChallengeCondition = ChallengeCondition.DIRECT_MATE,
    val targetPlayerMoves: Int? = null,
    val requiredCaptures: Int = 0,
    val allowedPiecesToLose: Int = Int.MAX_VALUE,
    val requiredPromotions: Int = 0,
    val promotionRequirement: PromotionRequirement = PromotionRequirement.ANY,
    val difficulty: Int = 1,
) {
    init {
        require(targetPlayerMoves == null || targetPlayerMoves > 0)
        require(requiredCaptures >= 0)
        require(allowedPiecesToLose >= 0)
        require(requiredPromotions >= 0)
        require(difficulty in 1..5)
    }
}

/**
 * The presentation label for an authored objective.
 */
enum class ChallengeCondition(
    val title: String,
    val objective: String,
) {
    DIRECT_MATE(
        title = "Direct mate",
        objective = "Follow the line and checkmate the king.",
    ),
    CLEAN_MATE(
        title = "Clean finish",
        objective = "Checkmate without losing any of your pieces.",
    ),
    QUIET_MATE(
        title = "Quiet finish",
        objective = "Deliver checkmate without any captures on either side.",
    ),
    CAPTURE_MATE(
        title = "Winning capture",
        objective = "Capture at least one defending piece before delivering mate.",
    ),
    DOUBLE_CAPTURE_MATE(
        title = "Double capture",
        objective = "Capture at least two defending pieces before delivering mate.",
    ),
    SACRIFICE_TRAP(
        title = "Sacrifice trap",
        objective = "Let the opponent capture one of your pieces, then deliver mate.",
    ),
    MATERIAL_PRESSURE(
        title = "Material pressure",
        objective = "Checkmate after allowing at least two of your pieces to be captured.",
    ),
    PAWN_PROMOTION(
        title = "Pawn promotion",
        objective = "Promote a pawn before delivering checkmate.",
    ),
    PROMOTE_AND_MATE(
        title = "Promote and mate",
        objective = "Promote to the required piece and deliver checkmate.",
    ),
}