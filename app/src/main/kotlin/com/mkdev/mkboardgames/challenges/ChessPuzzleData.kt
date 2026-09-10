package com.mkdev.mkboardgames.challenges

/**
 * Offline checkmate challenge set.
 *
 * Every entry is a real mate puzzle from the Lichess open puzzle database
 * (CC0: https://database.lichess.org/#puzzles). Lichess defines the FEN as
 * the position before the opponent's first move, so [moves] always starts
 * with that opponent move and ends with the player's checkmate.
 *
 * The imported entries are curated and sorted by puzzle rating, followed by
 * a small set of authored condition puzzles. This keeps the
 * first levels approachable while gradually introducing longer mating lines
 * and master-level calculation.
 */
data class ChessPuzzle(
    val level: Int,
    val fen: String,
    val moves: String,
    val rating: Int,
    val sourceId: String,
    val objective: ChallengeObjective = ChallengeObjective(),
    val alternateSolutions: List<String> = emptyList(),
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
        1 to ChallengeObjective(ChallengeCondition.CLEAN_MATE, allowedPiecesToLose = 0, difficulty = 1),
        9 to ChallengeObjective(ChallengeCondition.CAPTURE_MATE, requiredCaptures = 1, difficulty = 2),
        10 to ChallengeObjective(ChallengeCondition.QUIET_MATE, allowedPiecesToLose = 0, difficulty = 2),
        27 to ChallengeObjective(ChallengeCondition.CAPTURE_MATE, requiredCaptures = 1, difficulty = 2),
        39 to ChallengeObjective(ChallengeCondition.SACRIFICE_TRAP, difficulty = 3),
        55 to ChallengeObjective(ChallengeCondition.DOUBLE_CAPTURE_MATE, requiredCaptures = 2, difficulty = 3),
        67 to ChallengeObjective(ChallengeCondition.SACRIFICE_TRAP, difficulty = 4),
        76 to ChallengeObjective(ChallengeCondition.MATERIAL_PRESSURE, allowedPiecesToLose = 2, difficulty = 4),
        91 to ChallengeObjective(ChallengeCondition.CAPTURE_MATE, requiredCaptures = 1, difficulty = 5),
        98 to ChallengeObjective(
            condition = ChallengeCondition.PROMOTE_AND_MATE,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.QUEEN,
            difficulty = 4,
        ),
        99 to ChallengeObjective(
            condition = ChallengeCondition.PROMOTE_AND_MATE,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.ROOK,
            difficulty = 5,
        ),
        100 to ChallengeObjective(
            condition = ChallengeCondition.PROMOTE_AND_MATE,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.KNIGHT,
            difficulty = 5,
        ),
        101 to ChallengeObjective(
            condition = ChallengeCondition.PAWN_PROMOTION,
            requiredPromotions = 1,
            promotionRequirement = PromotionRequirement.QUEEN,
            difficulty = 5,
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

    fun themeFor(level: Int): String {
        val mateIn = all.getOrNull(level - 1)?.mateIn ?: 1
        return when (mateIn) {
            1 -> "Mate in one"
            2 -> "Mate in two"
            3 -> "Mate in three"
            4 -> "Mate in four"
            else -> "Mate in $mateIn"
        }
    }

    val all: List<ChessPuzzle> = listOf(
            ChessPuzzle(1, "2r2rk1/6pp/p1q5/1pn2p2/1B1pPP2/3Pn1QB/1PP2R1P/6RK b - - 3 24", "f8f6 g3g7", 400, "00c89"),
            ChessPuzzle(2, "1k2r3/p2r1R2/2Q5/1p5p/P1P3p1/8/6PP/7K w - - 2 44", "c6d7 e8e1 f7f1 e1f1", 421, "00VC1"),
            ChessPuzzle(3, "6k1/ppp2ppp/8/3p1q2/2Q5/8/P3r3/R1KR4 b - - 0 33", "d5c4 d1d8 e2e8 d8e8", 442, "0J0jI"),
            ChessPuzzle(4, "r2q1k1r/pbp1bppp/1p2pn2/1B2N3/3P1Q2/8/PPP2PPP/R1B2RK1 b - - 5 12", "f6d5 f4f7", 470, "00dOP"),
            ChessPuzzle(5, "r2q1r2/pppb1R1k/3p3p/2P3pP/2QBP3/8/P3B1P1/6K1 b - - 3 23", "f8f7 c4f7", 489, "0xzqb"),
            ChessPuzzle(6, "1r2kbnr/3b1ppp/pq1pp3/8/2pNPPP1/P1N1B3/1PPQ3P/2KR3R w k - 1 14", "f4f5 b6b2", 503, "00tsu"),
            ChessPuzzle(7, "1k2R2r/pp4bp/1B2Q1p1/2pn4/P2r4/5P2/2P2P1P/6K1 b - - 9 23", "h8e8 e6e8", 522, "0iMU5"),
            ChessPuzzle(8, "r2q1b1r/1pp2kpp/2n1bn2/p3p1N1/1P2p3/1QP5/P2P1PPP/RNB1K2R b KQ - 3 10", "f7e7 b3e6", 540, "0roxk"),
            ChessPuzzle(9, "3r2k1/4nppp/pq1p1b2/1p2P3/2r2P2/2P1NR2/PP1Q2BP/3R2K1 b - - 0 24", "d6e5 d2d8 b6d8 d1d8", 551, "0042j"),
            ChessPuzzle(10, "8/6k1/2R4p/5p1P/5P1K/6P1/8/r7 w - - 2 58", "c6b6 a1h1", 564, "002vV"),
            ChessPuzzle(11, "R5k1/3r2pp/3N4/1nP5/6P1/1P3P2/P2K2n1/8 b - - 1 30", "d7d8 a8d8", 576, "00NR5"),
            ChessPuzzle(12, "2k2r2/pp5p/3p4/3Nb1p1/8/1P1P3P/P1Pn4/1K1R1R2 w - - 3 28", "d1d2 f8f1 d2d1 f1d1", 588, "00KVb"),
            ChessPuzzle(13, "r1b2r1k/1pp1b1pp/p1np2q1/3Qp3/1PB1P3/P3BN1P/2P2PP1/R2R3K b - - 6 17", "f8f3 d5g8", 599, "00teH"),
            ChessPuzzle(14, "3r4/2QP1pkp/p7/3np1q1/2P5/5pP1/PP3P2/3R1K2 w - - 0 42", "d1d5 g5c1 d5d1 c1d1", 608, "1198E"),
            ChessPuzzle(15, "r1b5/pppnppkP/8/q7/6Q1/1P1B4/P1Pn1PP1/R5K1 b - - 4 18", "g7h8 g4g8", 630, "0y0bi"),
            ChessPuzzle(16, "r1b2q1k/1pp3p1/1b1p3p/1p5Q/P1P1Bp2/3P4/6PP/5R1K b - - 1 23", "f8f6 h5e8 f6f8 e8f8", 638, "00ax2"),
            ChessPuzzle(17, "2kr3r/ppp2p2/2nb1n1p/4q1p1/Q7/N1P1B3/PP2NPPP/R4RK1 w - - 2 13", "a3c4 e5h2", 653, "00ad3"),
            ChessPuzzle(18, "r2q1rk1/pppb1pp1/3p1n1B/4p3/2BbP3/2NP2QP/PPP2PP1/R4RK1 b - - 0 13", "d4c3 g3g7", 661, "0M9xW"),
            ChessPuzzle(19, "1k6/1pp3pB/8/p3P3/7P/bP5r/P1P5/1K1R4 w - - 1 32", "d1d4 h3h1 d4d1 h1d1", 674, "0lUNL"),
            ChessPuzzle(20, "2r3nr/ppq1kb2/4pnBp/3p4/3P1N2/5RQ1/PPP2PPP/2K1R3 w - - 14 23", "g6f7 c7c2", 688, "0bxYF"),
            ChessPuzzle(21, "N4rk1/pp3ppp/5n2/3pq3/8/2P5/PP3PPP/R1BQ2K1 w - - 0 18", "d1f3 e5e1", 699, "00ud8"),
            ChessPuzzle(22, "4R3/p5pk/7p/5r2/4Q3/P7/1P3q1P/6K1 w - - 0 42", "g1h1 f2f1", 706, "0M98x"),
            ChessPuzzle(23, "2kr3r/Bpp2ppp/8/3qb3/3P4/6Pb/PP2BP1P/R2QR1K1 w - - 0 18", "d4e5 d5g2", 725, "1175r"),
            ChessPuzzle(24, "3r3k/ppb2Qp1/2p5/2P2pp1/1P1Pp3/P4B1P/3R1PPq/5K2 w - - 0 30", "f3e2 h2h1", 739, "0PJ7t"),
            ChessPuzzle(25, "8/6pp/3k1p2/3Pp3/1r2KP2/8/6PP/3R4 w - - 1 41", "e4f5 b4f4", 750, "0y1jJ"),
            ChessPuzzle(26, "5kr1/ppR3p1/3R3p/8/1r1n4/8/1P3PPP/2K5 b - - 4 31", "d4b5 d6d8", 752, "005Ep"),
            ChessPuzzle(27, "3r3k/5Qpp/2pq4/p7/P3N3/8/1rP3PP/5R1K b - - 2 32", "d6g6 f7f8 d8f8 f1f8", 777, "0uyvt"),
            ChessPuzzle(28, "r1bq2kr/1p2b1pp/p2pN3/4p3/2B1P3/5Q2/PP3PPP/2RR2K1 b - - 0 17", "c8e6 c4e6", 805, "00f1D"),
            ChessPuzzle(29, "r2qk2r/pp2bpp1/4pn1p/1B6/3p4/2P2QNP/PP4P1/R4RK1 b kq - 1 15", "f6d7 f3f7", 818, "0M8lm"),
            ChessPuzzle(30, "rnbqk1nr/ppp2ppp/3p1b2/6B1/2BpP3/5Q2/PPP2PP1/RN2K2R b KQkq - 3 8", "f6g5 f3f7", 833, "0y1sh"),
            ChessPuzzle(31, "3r4/1R2pr1k/1p4pp/2p5/2P1P3/1P4N1/P3n1PP/3R2K1 w - - 1 38", "g3e2 d8d1", 851, "0iNZf"),
            ChessPuzzle(32, "r5k1/ppq2p2/2n5/3p4/B3p3/2P1BbrN/PP3P2/RN3RK1 w - - 0 28", "f2g3 c7g3", 861, "0J1gQ"),
            ChessPuzzle(33, "r4b1r/pppqpkpp/6B1/7Q/2PP4/2N1P2P/PP4P1/R5K1 b - - 0 15", "f7g8 g6f7", 874, "00TTm"),
            ChessPuzzle(34, "6k1/1Q4p1/p1p4p/3pP3/P3bq2/2N4P/1P4PK/5B2 w - - 1 26", "h2h1 f4f1 h1h2 f1g2", 881, "00dzT"),
            ChessPuzzle(35, "rn2k2Q/5p2/2p1p1r1/1q4p1/8/8/4NPPP/3R1K1R b q - 5 23", "e8e7 h8d8", 898, "00AGs"),
            ChessPuzzle(36, "6rk/1p6/p3q1p1/5nQ1/2pR4/2P4P/PP4PK/8 b - - 0 38", "f5d4 g5h6", 908, "0VZzF"),
            ChessPuzzle(37, "2r3k1/7p/6q1/p1Np4/Qp2pr2/P4P2/1PR2P1K/5R2 w - - 0 36", "f1g1 f4h4", 922, "009eX"),
            ChessPuzzle(38, "r1b3k1/pp2N1pp/n7/2p5/2PQ4/P5P1/1P5P/4rRK1 b - - 1 25", "e1e7 d4d8 e7e8 d8e8", 931, "0bvMG"),
            ChessPuzzle(39, "3rk2r/4bppb/2B4p/2p2P2/2n5/2P3BP/P7/3RR1K1 b - - 6 29", "e8f8 d1d8 e7d8 e1e8", 946, "0YkSI"),
            ChessPuzzle(40, "8/4k3/R4pp1/3Pp2p/2P1NnK1/1r5P/5PP1/8 w - - 0 48", "g4h4 f4g2", 960, "0YjFP"),
            ChessPuzzle(41, "r4knb/1bpp4/p1n1pq2/1p4NQ/3PP3/2N5/PPP2PP1/2KR1B2 b - - 3 15", "f6h6 h5f7", 970, "00Lnf"),
            ChessPuzzle(42, "2r2rQk/6pp/p6N/1p1p4/2pq4/P6P/1P3PP1/4R1K1 b - - 9 36", "f8g8 h6f7", 979, "00Tmr"),
            ChessPuzzle(43, "2r3k1/2qR1ppp/p7/2p2Q2/P7/7P/5PP1/6K1 b - - 3 26", "c7c6 f5f7 g8h8 f7g7", 991, "0092z"),
            ChessPuzzle(44, "8/3R2p1/4pbk1/6N1/7P/5P1R/1r6/r3K3 w - - 15 31", "d7d1 f6c3 e1f1 a1d1", 1005, "0bw5Q"),
            ChessPuzzle(45, "r7/2p1b3/pp2bk1q/3p1p2/3P1Pp1/P6p/1P1BQP2/R4RKB w - - 4 31", "a1e1 h3h2 g1g2 h6h3", 1025, "0lVGB"),
            ChessPuzzle(46, "2r3k1/R4pp1/1p2p2p/1q2P3/3Pp3/1p2P2P/5QP1/6K1 b - - 1 34", "b3b2 f2f7 g8h8 f7g7", 1039, "00rYm"),
            ChessPuzzle(47, "r7/pp4kp/6p1/2P2q1n/1PBb1p2/P4P2/3B2PP/2Q1R1K1 w - - 1 28", "g1h1 h5g3 h2g3 f5h5", 1049, "00Yy4"),
            ChessPuzzle(48, "r4rk1/1pp2ppp/p2p4/2bPp3/2P1Pn1q/P1N2B2/1P3P2/R1BQK1R1 w Q - 1 15", "c1f4 h4f2", 1061, "003YF"),
            ChessPuzzle(49, "rnb1k2r/p1p2ppp/1p3n2/2b1N1B1/3qP3/3P4/PPP3PP/RN1QKB1R w KQkq - 1 8", "e5f3 d4f2", 1080, "00eNa"),
            ChessPuzzle(50, "1R6/3k1Q2/p2b1p2/2r1p3/3n4/P6P/5PP1/4qBK1 b - - 1 40", "d7c6 f7b7", 1100, "00MYL"),
            ChessPuzzle(51, "2r5/3k4/4p3/3pPp2/3P3P/3bP1P1/1P5R/1K1B4 w - - 4 37", "b1a1 c8a8 d1a4 a8a4", 1101, "0uxHd"),
            ChessPuzzle(52, "4r1k1/1b3p2/pq1prBp1/2p4R/8/3P3Q/PP4PP/1R3K2 b - - 0 30", "g6h5 h3g3 g8f8 g3g7", 1114, "116WH"),
            ChessPuzzle(53, "8/ppk1pR2/3p1b2/3B4/8/1P2PpP1/P1PK1P1r/8 w - - 3 32", "d2e1 f6c3 e1d1 h2h1", 1122, "0CaJd"),
            ChessPuzzle(54, "6k1/1p3p1p/p5p1/8/6Q1/2B2PRK/PPq1r3/8 w - - 0 39", "g4d4 e2h2 h3g4 c2f5", 1133, "0VaGx"),
            ChessPuzzle(55, "2kr3r/1pq2pp1/p1pbb3/7p/Q2P2n1/5N2/PP1B1PPP/2R1RBK1 w - - 1 20", "d2a5 d6h2 f3h2 c7h2", 1148, "00gnK"),
            ChessPuzzle(56, "k2r3r/p2q2pp/P1pB4/1BQpPb2/4p1n1/2P5/2P2PPP/R4RK1 b - - 0 19", "c6b5 c5d5 d7c6 d5c6", 1152, "0VXb1"),
            ChessPuzzle(57, "8/p4Rpk/7p/5p2/7r/1P2q1PK/P5Q1/8 w - - 0 38", "h3h4 e3g5 h4h3 g5h5", 1163, "0PGOQ"),
            ChessPuzzle(58, "4Q1bk/2p3p1/1p1p4/5Bp1/p1q5/7P/5P2/6K1 b - - 1 34", "a4a3 e8h5 g8h7 h5h7", 1180, "00wAT"),
            ChessPuzzle(59, "r3kb1r/ppp2pp1/3p4/1P2p3/2P3pn/2N1P2q/PB1P1P1N/R2Q1RK1 w kq - 0 15", "d1g4 h4f3 h2f3 h3g4", 1196, "00Zit"),
            ChessPuzzle(60, "1kR1r3/4Q1p1/pr2Nn2/1q1P4/4P2p/5P2/6PP/2R3K1 b - - 9 32", "e8c8 c1c8 b8c8 e7c7", 1217, "0J1BO"),
            ChessPuzzle(61, "r1bq3r/ppppnkpp/2n5/b5N1/4P3/B1P5/P4PPP/RN1QK2R b KQ - 1 9", "f7f8 d1f3 f8e8 f3f7", 1232, "00QZ3"),
            ChessPuzzle(62, "4r2k/2q1r3/2p4Q/p5P1/1p1Pp1P1/2P1N2P/PP6/4bRK1 b - - 0 28", "e7h7 f1f8 e8f8 h6f8", 1270, "00mFI"),
            ChessPuzzle(63, "3r3k/p5pp/2r2q2/2p4Q/8/8/P5PP/3R1R1K b - - 5 29", "d8d1 h5e8 f6f8 e8f8", 1294, "00MFe"),
            ChessPuzzle(64, "rn1qk2r/4b1p1/p4pp1/8/3pQ3/1B1P4/PPP2PPP/R3K2R b KQkq - 0 16", "b8d7 e4g6 e8f8 g6f7", 1322, "0PGJq"),
            ChessPuzzle(65, "1k6/p1p2p2/1pq5/8/1P6/2KN2Q1/4r2B/1R5R w - - 8 41", "c3b3 c6c2 b3a3 c2a2", 1339, "0Yjq0"),
            ChessPuzzle(66, "r4rk1/pp4p1/1np1p1P1/4q1PR/5p2/4BP2/PPQ5/R4K2 b - - 1 25", "e5e3 h5h8 g8h8 c2h2 h8g8 h2h7", 1362, "0Yiuu"),
            ChessPuzzle(67, "r6r/pp2kb2/3p1p1Q/1N1Pp3/3bP3/P2B2P1/1P4PP/7K w - - 6 28", "h6d2 h8h2 h1h2 a8h8 d2h6 h8h6", 1380, "00BrZ"),
            ChessPuzzle(68, "rn2r2Q/pp1k1pp1/2pbq1P1/6B1/3P4/8/PPP2PP1/R4K1R w - - 5 18", "h8g7 e6e2 f1g1 e2e1 a1e1 e8e1", 1402, "0y0Jo"),
            ChessPuzzle(69, "rn1Q1b1r/1qN2kpp/p7/1p4B1/4p3/P7/1PP2PPP/R5K1 b - - 3 17", "a8a7 d8e8 f7g8 e8e6", 1436, "09TAi"),
            ChessPuzzle(70, "3r2k1/p5pp/8/3rp1q1/1RQp4/PP6/6PP/5R1K b - - 5 30", "g5e3 c4d5 d8d5 b4b8 d5d8 b8d8", 1460, "0J0GM"),
            ChessPuzzle(71, "5rk1/3Q1ppp/p4q2/1p6/4pn2/1B6/PP3P1P/3R1K2 b - - 5 27", "f6b2 d7f7 f8f7 d1d8", 1480, "0ojP3"),
            ChessPuzzle(72, "1r6/2r2p1R/7R/4pPk1/2B2n2/1P3P1P/P4K2/8 b - - 0 43", "c7c4 h3h4 g5f5 h7f7", 1524, "00voi"),
            ChessPuzzle(73, "5k2/p1R4R/1p4p1/3r3q/3P4/2P2rQp/PP5K/8 b - - 4 36", "f3g3 c7c8 d5d8 c8d8", 1537, "00h8Z"),
            ChessPuzzle(74, "r2q1rk1/4N1bp/p2p2p1/2p3N1/Pp4P1/1Q5P/1P1n1P2/5RK1 b - - 0 21", "g8h8 b3g8 f8g8 g5f7", 1564, "00pER"),
            ChessPuzzle(75, "r2qkb1r/ppp2ppp/3p1n2/4N1B1/4P1b1/1BN5/PPP2PPP/R2QK2R b KQkq - 0 9", "g4d1 b3f7 e8e7 c3d5", 1590, "0M90o"),
            ChessPuzzle(76, "2r2r2/2p1kpp1/2QBbn1p/q7/2P5/1P6/P4PPP/3RR1K1 b - - 4 28", "c7d6 c6d6 e7e8 e1e6 f7e6 d6e6", 1607, "0iMCD"),
            ChessPuzzle(77, "8/6kp/3Q2p1/2p1p3/1PP1q1P1/4b3/6KP/3R4 w - - 0 34", "g2f1 e4f3 f1e1 f3f2", 1626, "0VaT1"),
            ChessPuzzle(78, "5r1k/5prp/1p1N1Q2/p7/1P2p3/P2n2q1/2B4R/5R1K w - - 3 32", "d6f5 d3f2 h2f2 g3h3 f2h2 h3f1", 1644, "0Iyf7"),
            ChessPuzzle(79, "3k2q1/pb1p3p/1p1P4/2p5/2P2Q1K/8/P7/5R2 b - - 2 36", "b7g2 f4f8 g8f8 f1f8", 1665, "00EEp"),
            ChessPuzzle(80, "r2q3k/5Pb1/2n3Bp/3p2pP/pp1P2Q1/6B1/1PP5/6K1 b - - 0 39", "g7d4 g4d4 c6d4 g3e5 d8f6 e5f6", 1674, "00rw0"),
            ChessPuzzle(81, "3R2k1/2q5/2b3pb/1pp5/p3r2Q/P7/1PP3PP/5RK1 b - - 1 27", "g8g7 h4f6 g7h7 d8h8", 1701, "0SSgk"),
            ChessPuzzle(82, "5bk1/1R3ppp/1Q1p1n2/5N2/2P2BP1/3P3P/5PK1/r3q3 w - - 5 33", "b7b8 e1h1 g2g3 a1g1 g3h4 g1g4", 1727, "0ojQb"),
            ChessPuzzle(83, "2k5/pp3p2/5q2/2b1p1p1/4P1Pr/1nN2BK1/PPP3P1/R2Q4 w - - 3 23", "c3d5 f6f4 d5f4 e5f4", 1758, "00he6"),
            ChessPuzzle(84, "k1r5/pp1R1p2/8/4Q3/6p1/P1P5/KPP2P2/5q2 b - - 4 32", "f1f2 e5c7 c8c7 d7d8 c7c8 d8c8", 1780, "0PGoz"),
            ChessPuzzle(85, "1rb4r/pp1nN1pk/1b1pNn1p/4pP2/3PP3/2P2R2/PP4PP/R1B3K1 b - - 4 18", "e5d4 e6g5 h6g5 f3h3 f6h5 h3h5", 1803, "0MAkP"),
            ChessPuzzle(86, "8/5p2/pq5p/1p5k/6B1/6P1/P6P/2Q4K b - - 0 36", "h5g4 c1f4 g4h5 f4f5", 1842, "00bpH"),
            ChessPuzzle(87, "2b2rk1/3p1ppp/p3p2B/2Q1P3/Pn2N1q1/3B2P1/1rP4P/5RK1 b - - 3 28", "b4d3 e4f6 g8h8 c5f8", 1892, "0xzky"),
            ChessPuzzle(88, "r1b2rk1/1p1p2pp/p1n1n3/5N2/1P2Q2P/P2BB3/2PK1PP1/q6R b - - 0 19", "a1a3 f5h6 g7h6 e4h7", 1915, "0fDzR"),
            ChessPuzzle(89, "2q2rk1/1p2p3/p2pb1pB/8/1n2P1P1/1Nr2P2/P1P4Q/1K1R3R w - - 0 23", "h6d2 c3b3 a2b3 c8c2 b1a1 c2a2", 1953, "0CYah"),
            ChessPuzzle(90, "r1b1k1nr/pp1np2p/2q1Npp1/2P1p3/2B5/2N3B1/PPPR1PPP/2K4R b kq - 2 14", "d7c5 d2d8 e8f7 d8f8", 1990, "00JsQ"),
            ChessPuzzle(91, "8/p4ppk/Pp2p3/2b5/2K4Q/2P3P1/1q6/3R4 b - - 1 34", "h7g8 d1d8 c5f8 d8f8 g8f8 h4d8", 2003, "09UOO"),
            ChessPuzzle(92, "8/p1Q4k/3p1Bb1/2p5/4r3/1P5r/P1P1q3/1K3R2 b - - 1 37", "h7h6 c7g7 h6h5 f1f5 g6f5 g7g5", 2034, "0fFBZ"),
            ChessPuzzle(93, "3r1rk1/Q3qppp/8/1ppb4/2Pn1B1n/2N3P1/PP3P2/R2R1K2 w - - 0 21", "a7e7 d5g2 f1e1 h4f3", 2088, "00Pbs"),
            ChessPuzzle(94, "5r2/pp3pkp/4qNp1/3p4/3Q4/rP5P/6P1/5R1K b - - 2 30", "a3b3 f6e8 g7g8 d4g7", 2106, "0Ca27"),
            ChessPuzzle(95, "b3r3/R6p/5ppk/N7/1nN2P1P/1P1P2P1/P2R1K1r/8 w - - 1 35", "f2g1 e8e1 g1h2 e1h1", 2120, "0M9nl"),
            ChessPuzzle(96, "8/3Bkpp1/4b3/2P5/PP4n1/4P1qQ/R4pP1/2R3K1 w - - 0 27", "g1h1 g3h3 g2h3 e6d5 e3e4 d5e4", 2205, "0y20P"),
            ChessPuzzle(97, "Q7/8/3B4/2p5/Krkn4/8/8/8 w - - 1 54", "a4a3 d4b5 a3a2 b5c3 a2a1 b4b1", 2226, "00dt1"),
            // A compact promotion module: the black rook gives the required
            // waiting move, while the white pieces cover every escape square
            // around the promoted piece. Each entry exercises a different
            // promotion requirement.
            ChessPuzzle(
                98,
                "7k/5PK1/8/8/8/8/8/7r b - - 0 1",
                "h1h2 f7f8Q",
                2226,
                "local-queen-promotion",
                alternateSolutions = listOf("h1h3 f7f8Q"),
            ),
            ChessPuzzle(
                99,
                "7k/5PK1/8/8/8/8/8/7r b - - 0 1",
                "h1h2 f7f8R",
                2300,
                "local-rook-promotion",
                alternateSolutions = listOf("h1h3 f7f8R"),
            ),
            ChessPuzzle(
                100,
                "R7/4NP1k/6K1/4B3/8/8/8/7r b - - 0 1",
                "h1h2 f7f8N",
                2400,
                "local-knight-promotion",
                alternateSolutions = listOf("h1h3 f7f8N"),
            ),
            ChessPuzzle(
                101,
                "8/5KPk/8/6P1/8/8/8/7r b - - 0 1",
                "h1h2 g7g8Q",
                2450,
                "local-pawn-promotion-final",
                alternateSolutions = listOf("h1h3 g7g8Q"),
            ),
    ).map { it.copy(objective = objectiveFor(it)) }
}