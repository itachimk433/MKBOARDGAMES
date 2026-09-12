package com.mkdev.mkboardgames.challenges

data class ChessPuzzle(
    val level: Int,
    val fen: String,
    val rating: Int,
    val sourceId: String,
    val objective: ChallengeObjective,
    val title: String,
    val setup: String,
    val winCondition: String,
    val recommendedMoves: List<String>,
    val missingPieces: Int,
) {
    val condition: ChallengeCondition
        get() = objective.condition
}

/**
 * The Missing Piece set is intentionally made from playable endgame
 * positions. White starts with one or more pieces missing and loses more
 * material as the level increases. A level is complete when the position ends
 * in checkmate or stalemate.
 */
object ChessPuzzleData {
    private val missingPiecePuzzles: List<ChessPuzzle> = listOf(
        missingPiecePuzzle(
            level = 1,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1",
            missingPieces = 1,
            setup = "White is missing the queen. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 2,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RN2KBNR w KQkq - 0 1",
            missingPieces = 2,
            setup = "White is missing the queen and the c1 bishop. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 3,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RN2KBN1 w KQkq - 0 1",
            missingPieces = 3,
            setup = "White is missing the queen, c1 bishop, and h1 rook. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 4,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RN2K1N1 w KQkq - 0 1",
            missingPieces = 4,
            setup = "White is missing the queen, both bishops, and the h1 rook. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 5,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K1N1 w KQkq - 0 1",
            missingPieces = 5,
            setup = "White is missing the queen, both bishops, the h1 rook, and the b1 knight. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 6,
            fen = "4k3/ppp5/8/8/8/8/PPPP4/4KRN1 w - - 0 1",
            missingPieces = 9,
            setup = "White has a king, rook, knight, and four pawns. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 7,
            fen = "4k3/ppp5/8/8/8/8/PP6/4KRN1 w - - 0 1",
            missingPieces = 11,
            setup = "White has a king, rook, knight, and two pawns. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 8,
            fen = "4k3/ppp5/8/8/8/8/8/4KRN1 w - - 0 1",
            missingPieces = 13,
            setup = "White has only a king, rook, and knight. White to move.",
            recommendedMoves = listOf("g1f3"),
        ),
        missingPiecePuzzle(
            level = 9,
            fen = "4k3/ppp5/8/8/8/8/8/4KQ2 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen. Black has a king and three pawns. White to move.",
            recommendedMoves = listOf("f1f8"),
        ),
        missingPiecePuzzle(
            level = 10,
            fen = "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen. Finish with checkmate or stalemate. White to move.",
            recommendedMoves = listOf("f7g7"),
        ),
        missingPiecePuzzle(
            level = 11,
            fen = "7k/6R1/6K1/8/8/8/8/8 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and rook. Finish with checkmate or stalemate. White to move.",
            recommendedMoves = listOf("g7h7"),
        ),
        missingPiecePuzzle(
            level = 12,
            fen = "7k/5Q2/5K2/8/8/8/8/8 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen. Force the black king to a final square. White to move.",
            recommendedMoves = listOf("f7g7"),
        ),
        missingPiecePuzzle(
            level = 13,
            fen = "7k/4Q3/5K2/8/8/8/8/8 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen. Leave no legal escape. White to move.",
            recommendedMoves = listOf("e7g7"),
        ),
        missingPiecePuzzle(
            level = 14,
            fen = "7k/8/5K2/8/8/8/8/4Q3 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen, far from the black king. White to move.",
            recommendedMoves = listOf("e1e8"),
        ),
        missingPiecePuzzle(
            level = 15,
            fen = "7k/8/6K1/8/8/8/8/3Q4 w - - 0 1",
            missingPieces = 14,
            setup = "White has only a king and queen. This is the final missing-piece test. White to move.",
            recommendedMoves = listOf("d1h5"),
        ),
    )

    private val limitedMovePuzzles: List<ChessPuzzle> = listOf(
        limitedMovePuzzle(
            level = 16,
            fen = "4k3/5ppp/8/8/2B5/8/4PPPP/3QK1N1 w - - 0 1",
            moveLimit = 3,
            setup = "White to move. Put the black king in check before the limit.",
            recommendedMoves = listOf("c4f7"),
        ),
        limitedMovePuzzle(
            level = 17,
            fen = "7k/8/8/8/8/8/8/3QK3 w - - 0 1",
            moveLimit = 4,
            setup = "White to move. Find a direct queen check before the limit.",
            recommendedMoves = listOf("d1h5"),
        ),
        limitedMovePuzzle(
            level = 18,
            fen = "6k1/8/8/8/8/8/8/3QK3 w - - 0 1",
            moveLimit = 5,
            setup = "White to move. Use the open board to check the king.",
            recommendedMoves = listOf("d1d8"),
        ),
        limitedMovePuzzle(
            level = 19,
            fen = "5k2/8/8/8/8/8/8/3QK3 w - - 0 1",
            moveLimit = 6,
            setup = "White to move. Deliver a queen check within six moves.",
            recommendedMoves = listOf("d1f3"),
        ),
        limitedMovePuzzle(
            level = 20,
            fen = "4k3/8/8/8/8/8/8/3RK3 w - - 0 1",
            moveLimit = 7,
            setup = "White to move. Use the rook to check the black king.",
            recommendedMoves = listOf("d1d8"),
        ),
        limitedMovePuzzle(
            level = 21,
            fen = "7k/8/8/8/8/8/8/3RK3 w - - 0 1",
            moveLimit = 8,
            setup = "White to move. Find a rook check along the open files.",
            recommendedMoves = listOf("d1d8"),
        ),
        limitedMovePuzzle(
            level = 22,
            fen = "7k/8/8/8/8/8/8/R3K3 w - - 0 1",
            moveLimit = 10,
            setup = "White to move. Slide the rook into a checking file.",
            recommendedMoves = listOf("a1a8"),
        ),
        limitedMovePuzzle(
            level = 23,
            fen = "7k/8/8/8/8/8/8/4KR2 w - - 0 1",
            moveLimit = 12,
            setup = "White to move. Put the black king in check with the rook.",
            recommendedMoves = listOf("f1h1"),
        ),
        limitedMovePuzzle(
            level = 24,
            fen = "6k1/8/8/8/8/8/8/R3K3 w - - 0 1",
            moveLimit = 14,
            setup = "White to move. Check the king from the open g-file.",
            recommendedMoves = listOf("a1a8"),
        ),
        limitedMovePuzzle(
            level = 25,
            fen = "5k2/8/8/8/8/8/8/R3K3 w - - 0 1",
            moveLimit = 16,
            setup = "White to move. Check the king from the open f-file.",
            recommendedMoves = listOf("a1a8"),
        ),
        limitedMovePuzzle(
            level = 26,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            moveLimit = 18,
            setup = "White to move from the full starting position. Check before the limit.",
            recommendedMoves = listOf("e2e4"),
        ),
        limitedMovePuzzle(
            level = 27,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            moveLimit = 20,
            setup = "White to move from the full starting position. Check before the limit.",
            recommendedMoves = listOf("e2e4"),
        ),
        limitedMovePuzzle(
            level = 28,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            moveLimit = 22,
            setup = "White to move from the full starting position. Check before the limit.",
            recommendedMoves = listOf("e2e4"),
        ),
        limitedMovePuzzle(
            level = 29,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            moveLimit = 24,
            setup = "White to move from the full starting position. Check before the limit.",
            recommendedMoves = listOf("e2e4"),
        ),
        limitedMovePuzzle(
            level = 30,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            moveLimit = 25,
            setup = "White to move from the full starting position. Check before the limit.",
            recommendedMoves = listOf("e2e4"),
        ),
    )

    val all: List<ChessPuzzle> = missingPiecePuzzles + limitedMovePuzzles

    private fun missingPiecePuzzle(
        level: Int,
        fen: String,
        missingPieces: Int,
        setup: String,
        recommendedMoves: List<String>,
    ) = ChessPuzzle(
        level = level,
        fen = fen,
        rating = 900 + level * 35,
        sourceId = "missing-piece-$level",
        objective = ChallengeObjective(
            condition = ChallengeCondition.CHECKMATE_WITHIN_LIMIT,
            targetPlayerMoves = (20 + level * 2).coerceAtMost(50),
            difficulty = ((level + 2) / 3).coerceIn(1, 5),
        ),
        title = ChessChallengeCatalogue.titleFor(level),
        setup = setup,
        winCondition = "Deliver checkmate or stalemate in ${20 + level * 2} moves or fewer.",
        recommendedMoves = recommendedMoves,
        missingPieces = missingPieces,
    )

    private fun limitedMovePuzzle(
        level: Int,
        fen: String,
        moveLimit: Int,
        setup: String,
        recommendedMoves: List<String>,
    ) = ChessPuzzle(
        level = level,
        fen = fen,
        rating = 950 + level * 30,
        sourceId = "limited-moves-$level",
        objective = ChallengeObjective(
            condition = ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT,
            targetPlayerMoves = moveLimit,
            difficulty = ((level - 13) / 4).coerceIn(1, 5),
        ),
        title = ChessChallengeCatalogue.titleFor(level),
        setup = setup,
        winCondition = "Put the black king in check or checkmate in $moveLimit moves or fewer.",
        recommendedMoves = recommendedMoves,
        missingPieces = 0,
    )
}