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
        "Check in Three",
        "Check in Four",
        "Check in Five",
        "Check in Six",
        "Check in Seven",
        "Check in Eight",
        "Check in Ten",
        "Check in Twelve",
        "Check in Fourteen",
        "Check in Sixteen",
        "Check in Eighteen",
        "Check in Twenty",
        "Check in Twenty-Two",
        "Check in Twenty-Four",
        "Check in Twenty-Five",
    )

    val size: Int
        get() = titles.size

    fun titleFor(challenge: Int): String =
        titles.getOrNull(challenge - 1) ?: "Chess challenge"
}