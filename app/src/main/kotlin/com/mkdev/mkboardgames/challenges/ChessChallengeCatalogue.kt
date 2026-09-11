package com.mkdev.mkboardgames.challenges

object ChessChallengeCatalogue {
    val titles = listOf(
        "Scholar's Mate Challenge",
        "No Queen Challenge",
        "Knight Hunter Challenge",
        "Pawn Promotion Challenge",
        "Castle & Conquer Challenge",
        "Bishop Pair Challenge",
        "Rook Checkmate Challenge",
        "Material Comeback Challenge",
        "One-Piece Army Challenge",
        "Fast Checkmate Challenge",
    )

    val size: Int
        get() = titles.size

    fun titleFor(challenge: Int): String =
        titles.getOrNull(challenge - 1) ?: "Chess challenge"
}