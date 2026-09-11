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
    val beginnerChallenge: ChessBeginnerChallenge? = null,
) {
    val condition: ChallengeCondition
        get() = objective.condition
}

object ChessPuzzleData {
    private val challengeObjectives = listOf(
        ChallengeObjective(
            condition = ChallengeCondition.CHECKMATE_WITHIN_LIMIT,
            targetPlayerMoves = 39,
            difficulty = 1,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.NO_QUEEN_USE,
            difficulty = 2,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.CHECKMATE_WITHIN_LIMIT,
            targetPlayerMoves = 25,
            difficulty = 2,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.PROMOTE_AND_WIN,
            requiredPromotion = PromotionRequirement.ANY,
            difficulty = 1,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.CASTLE_AND_WIN,
            difficulty = 2,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.PRESERVE_BISHOPS,
            difficulty = 2,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.ROOK_CHECKMATE,
            difficulty = 3,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.MATERIAL_COMEBACK,
            difficulty = 4,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.KNIGHT_CAPTURE_ONLY,
            difficulty = 3,
        ),
        ChallengeObjective(
            condition = ChallengeCondition.KNIGHT_HUNTER,
            targetPlayerMoves = 25,
            difficulty = 2,
        ),
    )

    private val beginnerPuzzles = listOf(
        ChessPuzzle(
            1,
            "4k3/5ppp/8/8/2B5/8/4PPPP/3QK1N1 w - - 0 1",
            800,
            "custom-scholar-mate",
            challengeObjectives[0],
            ChessChallengeCatalogue.titleFor(1),
            ChessBeginnerChallenges.forLevel(1)!!.setup,
            ChessBeginnerChallenges.forLevel(1)!!.winCondition,
            listOf("c4f7", "d1h5", "d1h5"),
            ChessBeginnerChallenges.forLevel(1),
        ),
        ChessPuzzle(
            2,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1",
            1000,
            "custom-no-queen",
            challengeObjectives[1],
            ChessChallengeCatalogue.titleFor(2),
            ChessBeginnerChallenges.forLevel(2)!!.setup,
            ChessBeginnerChallenges.forLevel(2)!!.winCondition,
            listOf("e2e4", "g1f3", "f1e2"),
            ChessBeginnerChallenges.forLevel(2),
        ),
        ChessPuzzle(
            3,
            "4k3/5ppp/8/8/2B5/8/8/3QK1N1 w - - 0 1",
            1100,
            "custom-fast-checkmate",
            challengeObjectives[2],
            ChessChallengeCatalogue.titleFor(3),
            ChessBeginnerChallenges.forLevel(3)!!.setup,
            ChessBeginnerChallenges.forLevel(3)!!.winCondition,
            listOf("d1h5", "c4f7"),
            ChessBeginnerChallenges.forLevel(3),
        ),
        ChessPuzzle(
            4,
            "6k1/P6p/8/8/8/8/7P/6K1 w - - 0 1",
            900,
            "custom-pawn-promotion",
            challengeObjectives[3],
            ChessChallengeCatalogue.titleFor(4),
            ChessBeginnerChallenges.forLevel(4)!!.setup,
            ChessBeginnerChallenges.forLevel(4)!!.winCondition,
            listOf("a7a8Q", "h2h4"),
            ChessBeginnerChallenges.forLevel(4),
        ),
        ChessPuzzle(
            5,
            "4k1nr/5ppp/8/8/8/8/5PPP/4KBNR w K - 0 1",
            1200,
            "custom-castle-conquer",
            challengeObjectives[4],
            ChessChallengeCatalogue.titleFor(5),
            ChessBeginnerChallenges.forLevel(5)!!.setup,
            ChessBeginnerChallenges.forLevel(5)!!.winCondition,
            listOf("f1e2", "g1f3", "e1g1"),
            ChessBeginnerChallenges.forLevel(5),
        ),
        ChessPuzzle(
            6,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/2BQKB2 w KQkq - 0 1",
            1200,
            "custom-bishop-pair",
            challengeObjectives[5],
            ChessChallengeCatalogue.titleFor(6),
            ChessBeginnerChallenges.forLevel(6)!!.setup,
            ChessBeginnerChallenges.forLevel(6)!!.winCondition,
            listOf("e2e4", "c1g5", "f1e2"),
            ChessBeginnerChallenges.forLevel(6),
        ),
        ChessPuzzle(
            7,
            "6k1/5ppp/2B5/8/8/8/8/3Q2KR w - - 0 1",
            1300,
            "custom-rook-checkmate",
            challengeObjectives[6],
            ChessChallengeCatalogue.titleFor(7),
            ChessBeginnerChallenges.forLevel(7)!!.setup,
            ChessBeginnerChallenges.forLevel(7)!!.winCondition,
            listOf("d1h5", "h1h8", "c4f7"),
            ChessBeginnerChallenges.forLevel(7),
        ),
        ChessPuzzle(
            8,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK3 w KQkq - 0 1",
            1500,
            "custom-material-comeback",
            challengeObjectives[7],
            ChessChallengeCatalogue.titleFor(8),
            ChessBeginnerChallenges.forLevel(8)!!.setup,
            ChessBeginnerChallenges.forLevel(8)!!.winCondition,
            listOf("e2e4", "g1f3", "c1f4"),
            ChessBeginnerChallenges.forLevel(8),
        ),
        ChessPuzzle(
            9,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1N2K1N1 w KQkq - 0 1",
            1400,
            "custom-knight-army",
            challengeObjectives[8],
            ChessChallengeCatalogue.titleFor(9),
            ChessBeginnerChallenges.forLevel(9)!!.setup,
            ChessBeginnerChallenges.forLevel(9)!!.winCondition,
            listOf("b1c3", "g1f3", "e2e4"),
            ChessBeginnerChallenges.forLevel(9),
        ),
        ChessPuzzle(
            10,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            1100,
            "custom-knight-hunter",
            challengeObjectives[9],
            ChessChallengeCatalogue.titleFor(10),
            ChessBeginnerChallenges.forLevel(10)!!.setup,
            ChessBeginnerChallenges.forLevel(10)!!.winCondition,
            listOf("b1c3", "g1f3", "c3b5", "f3g5"),
            ChessBeginnerChallenges.forLevel(10),
        ),
    )

    private val gettingStartedPuzzles = listOf(
        puzzle(11, "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", ChallengeCondition.CHECKMATE_WITHIN_LIMIT, 6, "f7g7"),
        puzzle(12, "7k/6R1/6K1/8/8/8/8/8 w - - 0 1", ChallengeCondition.CHECKMATE_WITHIN_LIMIT, 8, "g7h7"),
        puzzle(13, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", ChallengeCondition.NO_QUEEN_USE, null, "e2e4"),
        puzzle(14, "6k1/P6p/8/8/8/8/7P/6K1 w - - 0 1", ChallengeCondition.PROMOTE_AND_WIN, null, "a7a8R"),
        puzzle(15, "4k1nr/5ppp/8/8/8/8/5PPP/4KBNR w K - 0 1", ChallengeCondition.CASTLE_AND_WIN, null, "f1e2", "g1f3", "e1g1"),
        puzzle(16, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/2BQKB2 w KQkq - 0 1", ChallengeCondition.PRESERVE_BISHOPS, null, "e2e4"),
        puzzle(17, "6k1/5ppp/2B5/8/8/8/8/3Q2KR w - - 0 1", ChallengeCondition.ROOK_CHECKMATE, null, "d1h5"),
        puzzle(18, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK3 w KQkq - 0 1", ChallengeCondition.MATERIAL_COMEBACK, null, "e2e4"),
        puzzle(19, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1N2K1N1 w KQkq - 0 1", ChallengeCondition.KNIGHT_CAPTURE_ONLY, null, "b1c3"),
        puzzle(20, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", ChallengeCondition.KNIGHT_HUNTER, 25, "b1c3"),
    )

    private val easyPuzzles = listOf(
        puzzle(21, "4k3/5ppp/8/8/2B5/8/4PPPP/3QK1N1 w - - 0 1", ChallengeCondition.CHECKMATE_WITHIN_LIMIT, 15, "c4f7"),
        puzzle(22, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", ChallengeCondition.NO_QUEEN_USE, null, "e2e4"),
        puzzle(23, "6k1/P6p/8/8/8/8/7P/6K1 w - - 0 1", ChallengeCondition.PROMOTE_AND_WIN, null, "a7a8Q"),
        puzzle(24, "4k1nr/5ppp/8/8/8/8/5PPP/4KBNR w K - 0 1", ChallengeCondition.CASTLE_AND_WIN, null, "f1e2", "g1f3", "e1g1"),
        puzzle(25, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/2BQKB2 w KQkq - 0 1", ChallengeCondition.PRESERVE_BISHOPS, null, "e2e4"),
        puzzle(26, "6k1/5ppp/2B5/8/8/8/8/3Q2KR w - - 0 1", ChallengeCondition.ROOK_CHECKMATE, null, "d1h5"),
        puzzle(27, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK3 w KQkq - 0 1", ChallengeCondition.MATERIAL_COMEBACK, null, "e2e4"),
        puzzle(28, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1N2K1N1 w KQkq - 0 1", ChallengeCondition.KNIGHT_CAPTURE_ONLY, null, "b1c3"),
        puzzle(29, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", ChallengeCondition.KNIGHT_HUNTER, 25, "b1c3"),
        puzzle(30, "4k3/5ppp/8/8/2B5/8/8/3QK1N1 w - - 0 1", ChallengeCondition.CHECKMATE_WITHIN_LIMIT, 25, "d1h5"),
    )

    val all: List<ChessPuzzle> = beginnerPuzzles + gettingStartedPuzzles + easyPuzzles

    private fun puzzle(
        level: Int,
        fen: String,
        condition: ChallengeCondition,
        targetPlayerMoves: Int?,
        vararg recommendedMoves: String,
    ) = ChessPuzzle(
        level = level,
        fen = fen,
        rating = 900 + level * 25,
        sourceId = "custom-challenge-$level",
        objective = ChallengeObjective(
            condition = condition,
            targetPlayerMoves = targetPlayerMoves,
            difficulty = if (level < 21) 1 else 2,
        ),
        title = ChessChallengeCatalogue.titleFor(level),
        setup = "White to move. Complete the challenge objective.",
        winCondition = when (condition) {
            ChallengeCondition.CHECKMATE_WITHIN_LIMIT ->
                "Deliver checkmate in ${targetPlayerMoves ?: 25} moves or fewer."
            ChallengeCondition.NO_QUEEN_USE -> "Win without using a queen, including on pawn promotion."
            ChallengeCondition.PROMOTE_AND_WIN -> "Promote a pawn, then win the game."
            ChallengeCondition.CASTLE_AND_WIN -> "Castle kingside, then win the game."
            ChallengeCondition.PRESERVE_BISHOPS -> "Win while keeping both white bishops alive."
            ChallengeCondition.ROOK_CHECKMATE -> "Deliver the final checkmate using a rook."
            ChallengeCondition.MATERIAL_COMEBACK -> "Win despite starting at least 5 points down in material."
            ChallengeCondition.KNIGHT_CAPTURE_ONLY -> "Only knights may capture pieces, then win."
            ChallengeCondition.KNIGHT_HUNTER -> "Capture both black knights before any other black piece, then win."
        },
        recommendedMoves = recommendedMoves.toList(),
    )
}