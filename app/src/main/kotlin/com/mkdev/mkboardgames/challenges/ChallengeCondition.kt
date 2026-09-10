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
    val requiresCheckmate: Boolean = true,
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
    SET_TRAP(
        title = "Set a trap",
        objective = "Offer a piece to force a capture, then spring the mating finish.",
    ),
    MATERIAL_PRESSURE(
        title = "Material pressure",
        objective = "Checkmate after allowing at least two of your pieces to be captured.",
    ),
    LONG_MATE(
        title = "Long calculation",
        objective = "Stay on the authored route until the final move delivers checkmate.",
    ),
    PAWN_PROMOTION(
        title = "Pawn promotion",
        objective = "Promote a pawn before delivering checkmate.",
    ),
    PROMOTE_AND_MATE(
        title = "Promote and mate",
        objective = "Promote to the required piece and deliver checkmate.",
    ),
    FORK(
        title = "Knight fork",
        objective = "Fork two valuable pieces, then win the higher-value target.",
        requiresCheckmate = false,
    ),
    WIN_FREE_PIECE(
        title = "Free piece",
        objective = "Capture the undefended piece without giving material back.",
        requiresCheckmate = false,
    ),
    PIN(
        title = "Pin and win",
        objective = "Capture the pinned piece while its king remains exposed.",
        requiresCheckmate = false,
    ),
    SKEWER(
        title = "Skewer",
        objective = "Attack through the high-value piece and win what is behind it.",
        requiresCheckmate = false,
    ),
    REMOVE_DEFENDER(
        title = "Remove the defender",
        objective = "Remove the guard, then capture the newly undefended target.",
        requiresCheckmate = false,
    ),
    DOUBLE_ATTACK(
        title = "Double attack",
        objective = "Create two threats and secure one of the targets.",
        requiresCheckmate = false,
    ),
    DISCOVERED_ATTACK(
        title = "Discovered attack",
        objective = "Move the masking piece, then win the revealed target.",
        requiresCheckmate = false,
    ),
    PAWN_PROMOTION_RACE(
        title = "Promotion race",
        objective = "Promote the passed pawn before the opposing king can stop it.",
        requiresCheckmate = false,
    ),
    TRAPPED_PIECE(
        title = "Trapped piece",
        objective = "Cut off the minor piece and capture it.",
        requiresCheckmate = false,
    ),
    OVERLOADED_DEFENDER(
        title = "Overloaded defender",
        objective = "Overload the defender, then take the abandoned target.",
        requiresCheckmate = false,
    ),
    XRAY_ATTACK(
        title = "X-ray attack",
        objective = "Attack through the front piece and win the target behind it.",
        requiresCheckmate = false,
    ),
    QUEEN_FORK(
        title = "Queen fork",
        objective = "Use one queen move to check the king and attack a rook.",
        requiresCheckmate = false,
    ),
    INTERPOSITION(
        title = "Interposition",
        objective = "Block the line of attack and neutralize the check.",
        requiresCheckmate = false,
    ),
    CLEARANCE_SACRIFICE(
        title = "Clearance sacrifice",
        objective = "Clear the critical line, then follow with the winning blow.",
        requiresCheckmate = false,
    ),
    PAWN_SHIELD_BREAK(
        title = "Break the pawn shield",
        objective = "Exchange into the king's pawn shield and open the attack.",
        requiresCheckmate = false,
    ),
    DEFLECTION(
        title = "Deflection",
        objective = "Drive the defender away, then exploit the vacant post.",
        requiresCheckmate = false,
    ),
    INTERCEPTION(
        title = "Interception",
        objective = "Break the communication between the two defenders and win material.",
        requiresCheckmate = false,
    ),
    ROOK_INCURSION(
        title = "Seventh-rank incursion",
        objective = "Invade the seventh rank, win two pawns, and keep the rook active.",
        requiresCheckmate = false,
    ),
    KNIGHT_OUTPOST(
        title = "Knight outpost",
        objective = "Centralize the knight on the outpost and restrict the enemy.",
        requiresCheckmate = false,
    ),
    COUNTER_CHECK(
        title = "Counter-check",
        objective = "Answer check with a stronger check while winning the attacker.",
        requiresCheckmate = false,
    ),
    OPPOSITION(
        title = "Opposition",
        objective = "Use king opposition to escort the pawn to promotion.",
        requiresCheckmate = false,
    ),
}