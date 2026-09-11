package com.mkdev.mkboardgames.challenges

object ChessChallengeCatalogue {
    val titles = listOf(
        "One Piece Short",
        "Two Pieces Short",
        "Three Pieces Short",
        "Four Pieces Short",
        "Five Pieces Short",
        "The Small Army",
        "The Smaller Army",
        "Rook and Knight",
        "Queen Against the Odds",
        "Queen's Final Test",
        "Rook's Final Test",
        "Stalemate Setup",
        "Stalemate Pressure",
        "Endgame Squeeze",
        "Last Piece Standing",
    )

    val size: Int
        get() = titles.size

    fun titleFor(challenge: Int): String =
        titles.getOrNull(challenge - 1) ?: "Chess challenge"
}