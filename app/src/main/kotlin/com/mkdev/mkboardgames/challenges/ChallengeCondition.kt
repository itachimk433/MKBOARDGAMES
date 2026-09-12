package com.mkdev.mkboardgames.challenges

enum class PromotionRequirement {
    ANY,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
}

/**
 * Challenge rules stay in data so the catalogue can change without tying
 * behavior to a numeric challenge id.
 */
enum class ChallengeCondition(
    val title: String,
    val requiresCheckmate: Boolean = false,
) {
    CHECKMATE_WITHIN_LIMIT("Checkmate or stalemate within the move limit", requiresCheckmate = true),
    CHECK_OR_CHECKMATE_WITHIN_LIMIT("Check or checkmate within the move limit", requiresCheckmate = true),
    NO_QUEEN_USE("Win without using a queen"),
    KNIGHT_HUNTER("Capture both black knights first"),
    PROMOTE_AND_WIN("Promote, then win"),
    CASTLE_AND_WIN("Castle kingside, then win"),
    PRESERVE_BISHOPS("Keep both bishops, then win"),
    ROOK_CHECKMATE("Finish with a rook checkmate", requiresCheckmate = true),
    MATERIAL_COMEBACK("Win from a material deficit"),
    KNIGHT_CAPTURE_ONLY("Only knights may capture");
}

data class ChallengeObjective(
    val condition: ChallengeCondition,
    val targetPlayerMoves: Int? = null,
    val requiredPromotion: PromotionRequirement = PromotionRequirement.ANY,
    val difficulty: Int = 1,
) {
    init {
        require(targetPlayerMoves == null || targetPlayerMoves > 0)
        require(difficulty in 1..5)
    }
}