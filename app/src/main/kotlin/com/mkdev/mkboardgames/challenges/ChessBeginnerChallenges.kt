package com.mkdev.mkboardgames.challenges

/**
 * The beginner curriculum shown by the first 25 chess challenges.
 *
 * The puzzle FEN remains the source of truth for the playable board. This
 * catalogue keeps the teaching brief and the important squares together with
 * the puzzle so the level list and in-game objective can describe what the
 * player is learning.
 */
data class ChessBeginnerChallenge(
    val level: Int,
    val title: String,
    val focus: String,
    val winCondition: String,
    val keyCoordinates: List<String>,
)

object ChessBeginnerChallenges {
    val standardInitialCoordinates: Map<String, List<String>> = mapOf(
        "white.rooks" to listOf("a1", "h1"),
        "white.knights" to listOf("b1", "g1"),
        "white.bishops" to listOf("c1", "f1"),
        "white.queen" to listOf("d1"),
        "white.king" to listOf("e1"),
        "white.pawns" to listOf("a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2"),
        "black.rooks" to listOf("a8", "h8"),
        "black.knights" to listOf("b8", "g8"),
        "black.bishops" to listOf("c8", "f8"),
        "black.queen" to listOf("d8"),
        "black.king" to listOf("e8"),
        "black.pawns" to listOf("a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7"),
    )

    const val standardInitialSetup =
        "White: rooks a1/h1, knights b1/g1, bishops c1/f1, queen d1, king e1, pawns a2-h2. " +
            "Black: rooks a8/h8, knights b8/g8, bishops c8/f8, queen d8, king e8, pawns a7-h7."

    const val startingPositionNote =
        "All 32 pieces begin on the standard initial squares before the level's opening moves."

    val all: List<ChessBeginnerChallenge> = listOf(
        challenge(
            1,
            "Mate in 1 (Back-Rank)",
            "Deliver checkmate with the rook on the opponent's back rank.",
            "Force checkmate in 1 move.",
            "e8, d7, f7, g7, h7",
        ),
        challenge(
            2,
            "Mate in 1 (Scholar's Mate pattern)",
            "Exploit an unprotected f7 pawn.",
            "Deliver checkmate targeting f7.",
            "f7, e8, h5, f3, c4",
        ),
        challenge(
            3,
            "The Simple Fork",
            "Attack two pieces simultaneously with a knight.",
            "Win the higher-value piece on the next turn.",
            "f3, d4, d5, e7, e4, c7",
        ),
        challenge(
            4,
            "Winning a Free Piece",
            "Capitalize on an undefended minor piece.",
            "Capture the hanging piece without compensation.",
            "c5, d4",
        ),
        challenge(
            5,
            "Back-Rank Weakness",
            "Exploit a lack of luft for the enemy king.",
            "Checkmate the king on the 8th or 1st rank.",
            "g8, h8, f7, g7, h7, e8, d8",
        ),
        challenge(
            6,
            "Simple Pin",
            "Immobilize an enemy piece that shields a more valuable piece.",
            "Capture the pinned piece after breaking support.",
            "b5, c6, e8",
        ),
        challenge(
            7,
            "Simple Skewer",
            "Attack a high-value piece and expose the piece behind it.",
            "Win the back piece after the high-value piece moves.",
            "e4, e8",
        ),
        challenge(
            8,
            "Removing the Defender",
            "Capture or deflect the sole piece guarding a key target.",
            "Capture the newly undefended target on the next move.",
            "c6, d4",
        ),
        challenge(
            9,
            "Simple Double Attack",
            "Use the queen to threaten the rook while taking the pawn.",
            "Secure a material advantage by taking one target.",
            "d4, a7, d7",
        ),
        challenge(
            10,
            "Discovered Attack",
            "Move a piece to unveil a direct rook, bishop, or queen threat.",
            "Win material from the secondary threat.",
            "d1, d4",
        ),
        challenge(
            11,
            "Mate in 2 (Smothered Mate setup)",
            "Trap a king surrounded by its own pieces using a knight check.",
            "Deliver checkmate with a knight.",
            "h8, g7, h7, g8, f7, f5",
        ),
        challenge(
            12,
            "Pawn Promotion Race",
            "Calculate the exact tempo needed to queen a passed pawn.",
            "Promote the pawn and convert the resulting endgame.",
            "e7, e8, c7",
        ),
        challenge(
            13,
            "Trapped Piece",
            "Cut off every flight square of an enemy bishop or knight.",
            "Capture the trapped piece on the subsequent move.",
            "a7, h7, b6, c5",
        ),
        challenge(
            14,
            "Overloaded Defender",
            "Force one piece to defend two critical threats.",
            "Capture the piece abandoned by the over-tasked defender.",
            "e7, c6, g5",
        ),
        challenge(
            15,
            "Simple X-Ray Attack",
            "Attack through an enemy piece to target a piece behind it.",
            "Win material or achieve a positional breakthrough.",
            "e1, e4, e8",
        ),
        challenge(
            16,
            "Intermediate Fork (Queen)",
            "Use diagonal and orthogonal range to fork a king and rook.",
            "Win the exchange or rook.",
            "c4, e8, a8",
        ),
        challenge(
            17,
            "Defensive Interposition",
            "Block an incoming check or dangerous line with a tactical sacrifice.",
            "Neutralize the attack and stabilize the position.",
            "g1, a7, a7-g1 diagonal",
        ),
        challenge(
            18,
            "Simple Clearance Sacrifice",
            "Sacrifice a piece to clear a crucial square or file for an ally.",
            "Follow up with a winning tactical blow.",
            "e4, d1, c1",
        ),
        challenge(
            19,
            "Breaking the King's Pawn Shield",
            "Exchange pieces to strip away the pawns around a castled king.",
            "Open lines for a subsequent mating attack.",
            "f7, g7, h7, g8, h7",
        ),
        challenge(
            20,
            "Basic Deflection",
            "Force an enemy piece away from a defensive post.",
            "Exploit the vacant defensive square.",
            "d8",
        ),
        challenge(
            21,
            "Interception",
            "Cut the communication line between two cooperating enemy pieces.",
            "Win material because their coordination is broken.",
            "a7, d4, 7th rank",
        ),
        challenge(
            22,
            "Seventh-Rank Rook Incursion",
            "Invade the opponent's seventh rank and harvest pawns.",
            "Win at least two pawns and create a mating net.",
            "c7, d7",
        ),
        challenge(
            23,
            "Centralizing the Knight",
            "Maneuver a knight to an outpost controlling key squares.",
            "Restrict enemy movement and win material.",
            "g1, d4, e5",
        ),
        challenge(
            24,
            "Simple Counter-Check",
            "Answer a check with an even stronger intermediate check.",
            "Turn the initiative and win the attacking piece.",
            "e1, b5",
        ),
        challenge(
            25,
            "Opposition Basics",
            "Use the king to out-flank the opposing king in a pawn ending.",
            "Promote a pawn to win the game.",
            "e4, e6",
        ),
    )

    fun forLevel(level: Int): ChessBeginnerChallenge? =
        all.firstOrNull { it.level == level }

    private fun challenge(
        level: Int,
        title: String,
        focus: String,
        winCondition: String,
        coordinates: String,
    ) = ChessBeginnerChallenge(
        level = level,
        title = title,
        focus = focus,
        winCondition = winCondition,
        keyCoordinates = coordinates.split(", ").filter { it.isNotBlank() },
    )
}