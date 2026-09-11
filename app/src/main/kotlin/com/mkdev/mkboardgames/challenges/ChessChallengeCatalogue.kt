package com.mkdev.mkboardgames.challenges

object ChessChallengeCatalogue {
    val titles = listOf(
        "Scholar's Mate Challenge",
        "No Queen Challenge",
        "Fast Checkmate Challenge",
        "Pawn Promotion Challenge",
        "Castle & Conquer Challenge",
        "Bishop Pair Challenge",
        "Rook Checkmate Challenge",
        "Material Comeback Challenge",
        "One-Piece Army Challenge",
        "Knight Hunter Challenge",
        "First Checkmate",
        "Opening Checkmate",
        "Promotion Without a Queen",
        "Castle Basics",
        "Bishop Safety",
        "Rook Finish",
        "Comeback Practice",
        "Knight Capture Practice",
        "Knight Hunt",
        "Fast Finish",
        "Checkmate Builder",
        "Queenless Victory",
        "Rook Promotion",
        "Castle Under Pressure",
        "Protect the Bishops",
        "Rook Checkmate Route",
        "Material Recovery",
        "Knight-Only Capture",
        "Advanced Knight Hunt",
        "Long Checkmate",
    )

    val size: Int
        get() = titles.size

    fun titleFor(challenge: Int): String =
        titles.getOrNull(challenge - 1) ?: "Chess challenge"
}