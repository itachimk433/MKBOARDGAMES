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
    val all: List<ChessPuzzle> = listOf(
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
}