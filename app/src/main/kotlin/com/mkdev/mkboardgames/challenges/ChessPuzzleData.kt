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
 * The ten Missing Piece challenges use authored starting positions so each
 * level has the exact material described in its setup text. A level is
 * complete when the position ends in checkmate or stalemate.
 */
object ChessPuzzleData {
    private val missingPiecePuzzles: List<ChessPuzzle> = listOf(
        missingPiecePuzzle(
            level = 1,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/RNB1KBNR w KQkq - 0 1",
            missingPieces = 2,
            setup = "White is missing the queen and the d2 middle pawn. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 2,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RN2K1NR w KQkq - 0 1",
            missingPieces = 3,
            setup = "White is missing the queen and both bishops. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 3,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPP2PPP/R1BQKB1R w KQkq - 0 1",
            missingPieces = 4,
            setup = "White has the queen but no knights and is missing two middle pawns. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 4,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/R2QKBN1 w KQkq - 0 1",
            missingPieces = 4,
            setup = "White is missing the h1 rook, b1 knight, c1 bishop, and d2 pawn. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 5,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/P2PP2P/R3K1N1 w KQkq - 0 1",
            missingPieces = 9,
            setup = "White is missing the queen, both bishops, the h1 rook, the b1 knight, and the b2, c2, f2, and g2 pawns. White to move.",
            recommendedMoves = listOf("e2e4"),
        ),
        missingPiecePuzzle(
            level = 6,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPP5/2B1K1N1 w kq - 0 1",
            missingPieces = 10,
            setup = "White has only a king, bishop, knight, and three pawns. Black has a full army. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 7,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PP6/1N2K1N1 w kq - 0 1",
            missingPieces = 11,
            setup = "White has only a king, two knights, and two pawns. Black has a full army. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 8,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PP6/R3K2R w kq - 0 1",
            missingPieces = 11,
            setup = "White has only a king, two rooks, and two pawns. Black has a full army. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 9,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/P7/R1B1K3 w kq - 0 1",
            missingPieces = 12,
            setup = "White has only a king, rook, bishop, and one pawn. Black has a full army. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
        missingPiecePuzzle(
            level = 10,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PP6/3QK3 w kq - 0 1",
            missingPieces = 12,
            setup = "White has only a king, queen, and two pawns. Black has a full army. White to move.",
            recommendedMoves = listOf("a2a3"),
        ),
    )

    val all: List<ChessPuzzle> = missingPiecePuzzles

    private fun missingPiecePuzzle(
        level: Int,
        fen: String,
        missingPieces: Int,
        setup: String,
        recommendedMoves: List<String>,
    ) = ChessPuzzle(
        level = level,
        fen = fen,
            rating = 900 + level * 45,
        sourceId = "missing-piece-$level",
        objective = ChallengeObjective(
                condition = ChallengeCondition.CHECKMATE_OR_STALEMATE,
            difficulty = ((level + 2) / 3).coerceIn(1, 5),
        ),
        title = ChessChallengeCatalogue.titleFor(level),
        setup = setup,
        winCondition = "Deliver checkmate or stalemate.",
        recommendedMoves = recommendedMoves,
        missingPieces = missingPieces,
    )
}