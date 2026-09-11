package com.mkdev.mkboardgames.challenges

data class ChessBeginnerChallenge(
    val level: Int,
    val title: String,
    val focus: String,
    val winCondition: String,
    val keyCoordinates: List<String>,
    val setup: String,
)

object ChessBeginnerChallenges {
    val all: List<ChessBeginnerChallenge> = listOf(
        challenge(
            1,
            "Scholar's Mate Challenge",
            "Build a direct attack on the exposed king.",
            "Deliver checkmate in 39 moves or fewer.",
            "e1, d1, c4, g1, e8, f7, g7, h7",
            "White: Ke1, Qd1, Bc4, Ng1, pawns e2/f2/g2/h2. Black: Ke8, pawns f7/g7/h7. White to move.",
        ),
        challenge(
            2,
            "No Queen Challenge",
            "Win a full game without using a queen at any point.",
            "Win the game without using a queen, including choosing a queen when a pawn promotes.",
            "e1, d1, e8, d8",
            "White: normal setup without the queen. Black: normal setup. White to move. A pawn promotion to a queen is not allowed.",
        ),
        challenge(
            3,
            "Fast Checkmate Challenge",
            "Find a forcing attack before the king can escape.",
            "Deliver checkmate in 25 moves or fewer.",
            "e1, d1, c4, g1, e8, f7, g7, h7",
            "White: Ke1, Qd1, Bc4, Ng1. Black: Ke8, pawns f7/g7/h7. White to move.",
        ),
        challenge(
            4,
            "Pawn Promotion Challenge",
            "Turn the advanced a-pawn into a winning piece.",
            "Promote the a7 pawn, then win the game.",
            "g1, a7, h2, g8, h7, a8",
            "White: Kg1, pawns a7/h2. Black: Kg8, pawn h7. White to move.",
        ),
        challenge(
            5,
            "Castle & Conquer Challenge",
            "Prepare the kingside and secure the king before attacking.",
            "Castle kingside, then win the game.",
            "e1, h1, g1, f1, f2, g2, h2",
            "White: Ke1, Rh1, Ng1, Bf1, pawns f2/g2/h2. Black: Ke8, Rh8, Ng8, Bf8, pawns f7/g7/h7. White to move.",
        ),
        challenge(
            6,
            "Bishop Pair Challenge",
            "Use both bishops without giving either one away.",
            "Win while keeping both white bishops alive.",
            "e1, c1, f1, d1, a2, h2",
            "White: Ke1, Qd1, bishops c1/f1, pawns a2-h2. Black: normal setup. White to move.",
        ),
        challenge(
            7,
            "Rook Checkmate Challenge",
            "Open a file for the rook to deliver the final blow.",
            "Deliver the final checkmate using a rook.",
            "g1, h1, d1, c4, g8, f7, g7, h7",
            "White: Kg1, Rh1, Qd1, Bc4. Black: Kg8, pawns f7/g7/h7. White to move.",
        ),
        challenge(
            8,
            "Material Comeback Challenge",
            "Convert a large starting material deficit into a win.",
            "Win despite starting at least 5 points down in material.",
            "e1, d1, a1, c1, b1, a2, h2",
            "White: Ke1, Qd1, Ra1, Bc1, Nb1, pawns a2-h2. Black: normal setup. White to move.",
        ),
        challenge(
            9,
            "One-Piece Army Challenge",
            "Let the knights do all the capturing.",
            "Only knights may capture pieces, then win the game.",
            "e1, b1, g1, a2, h2",
            "White: Ke1, knights b1/g1, pawns a2-h2. Black: normal setup. White to move.",
        ),
        challenge(
            10,
            "Knight Hunter Challenge",
            "Make the enemy knights your first targets.",
            "Capture both black knights before any other black piece, then win in 25 moves or fewer.",
            "b1, g1, b8, g8",
            "White and Black: normal setup. White to move.",
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
        setup: String,
    ) = ChessBeginnerChallenge(
        level = level,
        title = title,
        focus = focus,
        winCondition = winCondition,
        keyCoordinates = coordinates.split(", ").filter { it.isNotBlank() },
        setup = setup,
    )
}