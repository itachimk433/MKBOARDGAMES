package com.mkdev.mkboardgames.challenges

/**
 * Offline chess challenge set.
 *
 * The first 25 levels are authored tactical lessons. Lichess or imported
 * challenge data is intentionally not included in this beginner-only set.
 */
data class ChessPuzzle(
    val level: Int,
    val fen: String,
    val moves: String,
    val rating: Int,
    val sourceId: String,
    val objective: ChallengeObjective = ChallengeObjective(),
    val alternateSolutions: List<String> = emptyList(),
    val title: String = "Chess challenge",
    val beginnerChallenge: ChessBeginnerChallenge? = null,
) {
    val solutionLines: List<String>
        get() = (listOf(moves) + alternateSolutions)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val mateIn: Int
        get() = solutionLines.first().split(Regex("\\s+")).size / 2

    val condition: ChallengeCondition
        get() = objective.condition

    val ratingLabel: String
        get() = when {
            rating < 900 -> "Beginner"
            rating < 1300 -> "Developing"
            rating < 1700 -> "Advanced"
            rating < 2050 -> "Expert"
            else -> "Master"
        }
}

object ChessPuzzleData {
    private val authoredObjectives = mapOf(
        1 to ChallengeObjective(ChallengeCondition.CLEAN_MATE, targetPlayerMoves = 1, allowedPiecesToLose = 0, difficulty = 1),
        2 to ChallengeObjective(ChallengeCondition.DIRECT_MATE, targetPlayerMoves = 1, difficulty = 1),
        3 to ChallengeObjective(ChallengeCondition.FORK, requiredCaptures = 1, targetPlayerMoves = 2, difficulty = 1),
        4 to ChallengeObjective(ChallengeCondition.WIN_FREE_PIECE, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 1),
        5 to ChallengeObjective(ChallengeCondition.DIRECT_MATE, targetPlayerMoves = 1, difficulty = 1),
        6 to ChallengeObjective(ChallengeCondition.PIN, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 1),
        7 to ChallengeObjective(ChallengeCondition.SKEWER, requiredCaptures = 2, targetPlayerMoves = 2, difficulty = 2),
        8 to ChallengeObjective(ChallengeCondition.REMOVE_DEFENDER, requiredCaptures = 2, targetPlayerMoves = 2, difficulty = 2),
        9 to ChallengeObjective(ChallengeCondition.DOUBLE_ATTACK, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 1),
        10 to ChallengeObjective(ChallengeCondition.DISCOVERED_ATTACK, requiredCaptures = 1, targetPlayerMoves = 2, difficulty = 2),
        11 to ChallengeObjective(ChallengeCondition.DIRECT_MATE, targetPlayerMoves = 2, difficulty = 2),
        12 to ChallengeObjective(
            condition = ChallengeCondition.PAWN_PROMOTION_RACE,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.QUEEN,
            targetPlayerMoves = 1,
            difficulty = 2,
        ),
        13 to ChallengeObjective(ChallengeCondition.TRAPPED_PIECE, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 1),
        14 to ChallengeObjective(ChallengeCondition.OVERLOADED_DEFENDER, requiredCaptures = 1, targetPlayerMoves = 2, difficulty = 2),
        15 to ChallengeObjective(ChallengeCondition.XRAY_ATTACK, requiredCaptures = 2, targetPlayerMoves = 2, difficulty = 2),
        16 to ChallengeObjective(ChallengeCondition.QUEEN_FORK, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 2),
        17 to ChallengeObjective(ChallengeCondition.INTERPOSITION, targetPlayerMoves = 1, difficulty = 2),
        18 to ChallengeObjective(ChallengeCondition.CLEARANCE_SACRIFICE, requiredCaptures = 1, targetPlayerMoves = 2, difficulty = 2),
        19 to ChallengeObjective(ChallengeCondition.PAWN_SHIELD_BREAK, requiredCaptures = 2, targetPlayerMoves = 2, difficulty = 2),
        20 to ChallengeObjective(ChallengeCondition.DEFLECTION, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 2),
        21 to ChallengeObjective(ChallengeCondition.INTERCEPTION, requiredCaptures = 1, targetPlayerMoves = 2, difficulty = 2),
        22 to ChallengeObjective(ChallengeCondition.ROOK_INCURSION, requiredCaptures = 2, targetPlayerMoves = 2, difficulty = 2),
        23 to ChallengeObjective(ChallengeCondition.KNIGHT_OUTPOST, targetPlayerMoves = 2, difficulty = 1),
        24 to ChallengeObjective(ChallengeCondition.COUNTER_CHECK, requiredCaptures = 1, targetPlayerMoves = 1, difficulty = 2),
        25 to ChallengeObjective(
            condition = ChallengeCondition.OPPOSITION,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.QUEEN,
            targetPlayerMoves = 2,
            difficulty = 2,
        ),
    )

    fun objectiveFor(puzzle: ChessPuzzle): ChallengeObjective =
        authoredObjectives[puzzle.level] ?: ChallengeObjective(
            targetPlayerMoves = puzzle.solutionLines.first().split(Regex("\\s+")).size / 2,
            difficulty = when {
                puzzle.rating < 900 -> 1
                puzzle.rating < 1400 -> 2
                puzzle.rating < 1900 -> 3
                puzzle.rating < 2200 -> 4
                else -> 5
            },
        )

    fun themeFor(level: Int): String =
        all.getOrNull(level - 1)?.title ?: ChessChallengeCatalogue.titleFor(level)

    private val beginnerPuzzles = listOf(
        ChessPuzzle(1, "R2qk3/3ppppp/p3N2B/8/8/8/8/4K3 b - - 0 1", "a6a5 a8d8", 650, "authored-beginner-back-rank"),
        ChessPuzzle(2, "rnbqkbnr/pppppppp/8/7Q/2B5/8/PPPPPPPP/RNB1K1NR b KQkq - 0 1", "a7a6 h5f7", 650, "authored-beginner-scholar"),
        ChessPuzzle(3, "4k3/p3p2p/2r1q3/3p4/8/5N2/8/6K1 b - - 0 1", "a7a6 f3d4 h7h6 d4e6", 700, "authored-beginner-fork"),
        ChessPuzzle(4, "4k3/8/8/2b5/3Q4/8/8/4K3 b - - 0 1", "e8e7 d4c5", 650, "authored-beginner-free-piece"),
        ChessPuzzle(5, "6k1/p4ppp/8/8/8/8/8/4R1K1 b - - 0 1", "a7a6 e1e8", 700, "authored-beginner-back-rank-weakness"),
        ChessPuzzle(6, "4k3/p7/2n5/1B6/8/8/8/4K3 b - - 0 1", "a7a6 b5c6", 750, "authored-beginner-pin"),
        ChessPuzzle(7, "4r1k1/p7/8/8/4q3/8/8/4R1K1 b - - 0 1", "a7a6 e1e4 a6a5 e4e8", 800, "authored-beginner-skewer"),
        ChessPuzzle(8, "4k3/p6p/2b5/4N3/3r4/8/8/4K3 b - - 0 1", "a7a6 e5c6 h7h6 c6d4", 800, "authored-beginner-remove-defender"),
        ChessPuzzle(9, "4k3/p2r3p/8/8/3Q4/8/8/4K3 b - - 0 1", "h7h6 d4a7", 750, "authored-beginner-double-attack"),
        ChessPuzzle(10, "3qk3/p6p/8/6B1/3N4/8/8/3R2K1 b - - 0 1", "a7a6 d4b5 h7h6 d1d8", 850, "authored-beginner-discovered-attack"),
        ChessPuzzle(11, "6nk/p5pp/7N/8/8/8/8/R3K3 b - - 0 1", "a7a6 a1a2 a6a5 h6f7", 850, "authored-beginner-smothered-mate"),
        ChessPuzzle(12, "8/p1k1P3/8/4K3/8/8/8/8 b - - 0 1", "a7a6 e7e8Q", 850, "authored-beginner-promotion-race"),
        ChessPuzzle(13, "4k3/b6p/1p6/2p5/8/8/8/R3K3 b - - 0 1", "h7h6 a1a7", 700, "authored-beginner-trapped-piece"),
        ChessPuzzle(14, "4k3/4q3/2n5/6b1/2Q5/8/8/6K1 b - - 0 1", "e7e6 c4g4 e6e5 g4g5", 850, "authored-beginner-overloaded-defender"),
        ChessPuzzle(15, "4r1k1/p7/8/8/4b3/8/8/4R1K1 b - - 0 1", "a7a6 e1e4 a6a5 e4e8", 850, "authored-beginner-xray"),
        ChessPuzzle(16, "r3k3/p7/4r3/8/2Q5/8/8/4K3 b - - 0 1", "a7a6 c4e6", 900, "authored-beginner-queen-fork"),
        ChessPuzzle(17, "4k3/b6p/8/8/8/8/4P3/6K1 b - - 0 1", "h7h6 e2e3", 850, "authored-beginner-interposition"),
        ChessPuzzle(18, "3qk3/p6p/8/8/4B3/8/8/3R1K2 b - - 0 1", "a7a6 e4f5 h7h6 d1d8", 900, "authored-beginner-clearance"),
        ChessPuzzle(19, "6k1/p4ppp/8/7Q/8/3B4/8/4K3 b - - 0 1", "a7a6 d3h7 g8f8 h5f7", 900, "authored-beginner-pawn-shield"),
        ChessPuzzle(20, "3qk3/p7/8/8/8/8/8/3R2K1 b - - 0 1", "d8d7 d1d7", 850, "authored-beginner-deflection"),
        ChessPuzzle(21, "4k3/r2p4/8/8/3b4/2Q5/8/R3K3 b - - 0 1", "d7d6 c3b4 d6d5 b4d4", 900, "authored-beginner-interception"),
        ChessPuzzle(22, "4k3/p1pp3p/8/8/8/8/8/2R1K3 b - - 0 1", "a7a6 c1c7 h7h6 c7d7", 900, "authored-beginner-rook-incursion"),
        ChessPuzzle(23, "4k3/p6p/8/8/8/8/8/4K1N1 b - - 0 1", "a7a6 g1f3 h7h6 f3d4", 700, "authored-beginner-knight-outpost"),
        ChessPuzzle(24, "4r1k1/p7/8/1Q6/8/8/8/4K3 b - - 0 1", "a7a6 b5e8", 900, "authored-beginner-counter-check"),
        ChessPuzzle(25, "8/p7/3Pk3/8/4K3/8/8/8 b - - 0 1", "a7a6 d6d7 e6f6 d7d8Q", 900, "authored-beginner-opposition"),
    )

    val all: List<ChessPuzzle> = beginnerPuzzles.map { puzzle ->
        puzzle.copy(
            objective = objectiveFor(puzzle),
            title = ChessChallengeCatalogue.titleFor(puzzle.level),
            beginnerChallenge = ChessBeginnerChallenges.forLevel(puzzle.level),
        )
    }
}