package com.mkdev.mkboardgames.challenges

/**
 * Extra rules layered on top of a checkmate line.
 *
 * These are deliberately small, offline-verifiable objectives. A puzzle still
 * has one authored solution line, but the line can now teach more than
 * "deliver mate": keep your material, accept a sacrifice, or promote a pawn.
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
}