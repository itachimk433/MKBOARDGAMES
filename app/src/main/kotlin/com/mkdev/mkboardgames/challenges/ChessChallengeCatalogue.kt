package com.mkdev.mkboardgames.challenges

/**
 * The challenge names are kept separately from the board positions so the
 * catalogue can be edited from the design brief without changing validation
 * data or progress keys.
 */
object ChessChallengeCatalogue {
    private val titles = listOf(
        "Mate in 1 (Back-Rank)",
        "Mate in 1 (Scholar's Mate pattern)",
        "The Simple Fork",
        "Winning a Free Piece",
        "Back-Rank Weakness",
        "Simple Pin",
        "Simple Skewer",
        "Removing the Defender",
        "Simple Double Attack",
        "Discovered Attack",
        "Mate in 2 (Smothered Mate setup)",
        "Pawn Promotion Race",
        "Trapped Piece",
        "Overloaded Defender",
        "Simple X-Ray Attack",
        "Intermediate Fork (Queen)",
        "Defensive Interposition",
        "Simple Clearance Sacrifice",
        "Breaking the King's Pawn Shield",
        "Basic Deflection",
        "Interception",
        "Seventh-Rank Rook Incursion",
        "Centralizing the Knight",
        "Simple Counter-Check",
        "Opposition Basics",
    )

    val size: Int
        get() = titles.size

    fun titleFor(level: Int): String = titles.getOrNull(level - 1) ?: "Chess challenge"
}