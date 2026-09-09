package com.mkdev.mkboardgames.challenges

/**
 * Offline chess challenge set.
 *
 * Positions and principal variations are sourced from the Lichess open puzzle
 * database (CC0): https://database.lichess.org/#puzzles
 * The set is ordered by puzzle rating so the 100 levels climb from beginner
 * tactics to master-level calculation.
 */
data class ChessPuzzle(
    val level: Int,
    val fen: String,
    val moves: String,
    val rating: Int,
    val sourceId: String,
) {
    val ratingLabel: String
        get() = when {
            rating < 1100 -> "Beginner"
            rating < 1500 -> "Developing"
            rating < 1900 -> "Advanced"
            rating < 2300 -> "Expert"
            else -> "Master"
        }
}

object ChessPuzzleData {
    private val featuredThemes = mapOf(
        1 to "Mate in one",
        6 to "Back-rank mate",
        18 to "Endgame",
        34 to "Attraction",
        40 to "Attraction",
        56 to "Endgame",
        89 to "Bishop endgame",
        96 to "Pawn promotion",
    )

    fun themeFor(level: Int): String = featuredThemes[level] ?: "Tactical puzzle"

    val all: List<ChessPuzzle> = listOf(
            ChessPuzzle(1, "rn1qkb1r/ppp2ppp/8/3pp3/4n3/1PN2P1P/PBPPP2P/R2QKB1R w KQkq - 1 7", "f3e4 d8h4", 751, "GyvFB"),
            ChessPuzzle(2, "8/5N2/8/8/2pk1K2/8/6bP/8 w - - 2 47", "f7e5 c4c3 e5g4 c3c2", 773, "34koa"),
            ChessPuzzle(3, "6k1/6pp/ppqp2p1/2p5/5P2/1P3b1P/PBP1r2Q/1R3RK1 w - - 9 35", "h2g3 e2g2 g3g2 f3g2", 795, "A6QZg"),
            ChessPuzzle(4, "8/8/7P/7q/4pR2/4P1Pk/2K2P2/8 b - - 2 69", "h5h6 f4h4 h6h4 g3h4", 818, "KU9pO"),
            ChessPuzzle(5, "3r3k/p1q2pp1/1p2p2p/8/Q7/P1P4P/1P2RPP1/6K1 w - - 1 27", "a4g4 d8d1 e2e1 d1e1", 841, "FUwgh"),
            ChessPuzzle(6, "5k2/5ppp/pN6/1p6/1n6/1P4P1/P1r2P1P/4R1K1 b - - 1 27", "b4a2 b6d7 f8g8 e1e8", 864, "t4CnV"),
            ChessPuzzle(7, "6k1/p1b1r2p/4p1p1/1B6/1P6/P1r1P2P/4R1P1/3R2K1 w - - 7 33", "d1d3 c3c1 e2e1 c1e1", 886, "TjL9S"),
            ChessPuzzle(8, "8/8/2k2p1p/4pP2/2P1P2r/2K5/4B3/8 b - - 1 51", "h4e4 e2f3 c6b6 f3e4", 909, "7uez3"),
            ChessPuzzle(9, "r1bq2kr/pppp2pp/2n5/2bQP1N1/5Bn1/2P5/P4PPP/RN2K2R b KQ - 3 12", "g8f8 d5f7", 932, "8jacf"),
            ChessPuzzle(10, "2r3k1/p1qbQ1p1/1p2p2r/nP2P2P/4B1P1/N1P5/P7/5R1K b - - 4 27", "d7e8 f1f8", 955, "MfKEh"),
            ChessPuzzle(11, "5r2/5pk1/N1p1n1pp/Pp2P3/1Pq1B2P/2P2Q2/6P1/4R2K w - - 1 38", "e4c6 c4h4 h1g1 h4e1", 977, "w2aSl"),
            ChessPuzzle(12, "2k5/pp2qb2/2n3p1/3P4/N5n1/2PB4/PPQ3P1/R1B2RKr w - - 1 24", "g1h1 e7h4 h1g1 h4h2", 1000, "Gyu7W"),
            ChessPuzzle(13, "4r1k1/ppR2pp1/1n1P3p/8/P7/6PP/1PP2P1K/8 b - - 2 32", "b6a4 d6d7 e8d8 c7c8 g8f8 c8d8", 1023, "w2aFv"),
            ChessPuzzle(14, "5rk1/1Q3ppp/6r1/8/5qn1/1B5P/PPP2PP1/4RR1K w - - 1 28", "h3g4 g6h6 h1g1 f4h2", 1045, "F2IHk"),
            ChessPuzzle(15, "2r2r1k/4Q2p/pq2Npp1/1p6/1P2P3/3n1P2/P5PP/5R1K b - - 1 28", "f8g8 e7f6 g8g7 f6g7", 1068, "mrHEy"),
            ChessPuzzle(16, "2kr3r/1bpp1R2/p1n3qp/1p1BpNp1/4P3/3PP3/PPP3PP/R2Q2K1 w - - 2 15", "f5e7 c6e7 f7e7 b7d5", 1091, "uiCzS"),
            ChessPuzzle(17, "8/p5kp/bp4p1/8/4N2B/5PK1/PPr3PP/8 b - - 1 33", "c2b2 h4f6 g7f7 f6b2", 1114, "oWk7X"),
            ChessPuzzle(18, "2R2k2/1p4b1/5NQp/q2Ppb2/p7/5r2/PP3PP1/6K1 b - - 0 27", "f5c8 g6e8", 1136, "oWk38"),
            ChessPuzzle(19, "6r1/1pq1pp1k/p1p2b2/5P1p/4P3/2PPQNr1/PP5R/R6K b - - 0 28", "f6e5 h2h5 h7g7 e3h6", 1159, "p3Qlk"),
            ChessPuzzle(20, "2rqr1k1/1bp1b1nB/pp1p1n1Q/4p1N1/2PP4/2N4P/PP3PP1/2RR2K1 b - - 1 19", "f6h7 h6h7 g8f8 h7h8", 1181, "A6QYQ"),
            ChessPuzzle(21, "8/1p6/p1pN4/2P1k1P1/1P2BpK1/r7/7r/2R5 w - - 3 43", "g5g6 a3g3", 1205, "xV1Rx"),
            ChessPuzzle(22, "4r1k1/pp3p2/4qppP/8/3R4/2P1BQ2/bP3PP1/6K1 w - - 0 26", "d4d7 e6d7 f3f6 d7d1 g1h2 d1h5", 1227, "HdvsR"),
            ChessPuzzle(23, "r1b1k2r/ppp1bppp/5n2/n2q2N1/3P4/2NB4/P4PPP/R1BQK2R b KQkq - 1 11", "d5d4 d3b5 c7c6 d1d4", 1249, "807Vi"),
            ChessPuzzle(24, "r1b3k1/2Q3pp/p1p2q2/3p4/8/3P4/P1P3PP/5RK1 b - - 1 26", "f6e6 c7d8 e6e8 d8e8", 1273, "uiC1K"),
            ChessPuzzle(25, "8/1pk1rR2/p1p4p/1n1p4/1P5P/3B2PK/4r3/5R2 b - - 8 36", "b5c3 d3e2 e7f7 f1f7", 1295, "NJULJ"),
            ChessPuzzle(26, "7k/3q2p1/2p2p1p/3r4/5P2/B4RP1/P3Q3/6K1 w - - 2 34", "f3e3 d5d1 g1f2 d1d2 a3b4 d2e2", 1318, "ZUaI4"),
            ChessPuzzle(27, "1q4k1/1r4pp/3bp3/5p2/1pQBp3/6P1/PP3P1P/4R1K1 b - - 1 35", "b8c7 c4e6 c7f7 e6d6", 1341, "Q5DMA"),
            ChessPuzzle(28, "8/2k5/5p1p/1B2nK2/P1p5/7P/6P1/8 w - - 0 46", "f5f6 c4c3 f6e5 c3c2", 1364, "34nMb"),
            ChessPuzzle(29, "r6k/p5R1/2Q2n1p/8/8/8/PP1q2PP/4rR1K w - - 1 27", "c6f6 e1f1 f6f1 h8g7", 1386, "Gytx1"),
            ChessPuzzle(30, "6k1/pp2bp2/1q4p1/5p1p/2BPr3/1P4Q1/PB3PKP/R7 w - - 1 30", "a1d1 e4g4 g2f1 g4g3", 1409, "dflF5"),
            ChessPuzzle(31, "2r4k/p2p2pp/qp6/P2Q4/2R5/5P2/6PP/R5K1 b - - 0 25", "a6c4 d5c4 c8c4 a5b6", 1432, "uiBpb"),
            ChessPuzzle(32, "3r2k1/5pp1/4b2p/1B1p4/n4q2/5N1P/P1Q2PP1/3R2K1 b - - 0 26", "d8c8 c2a4 f4a4 b5a4", 1455, "t4BuI"),
            ChessPuzzle(33, "b3r2k/6p1/7p/1pP2p1B/1P1R3P/PQ2rpP1/8/6K1 w - - 0 42", "b3f7 e3e1 g1h2 e8e2 h2h3 e1h1", 1477, "p3Pfr"),
            ChessPuzzle(34, "2R2rk1/1b4pp/p4q2/8/4pN2/4Q1P1/PP5P/3R2K1 b - - 0 30", "f8c8 e3b3 f6f7 b3f7 g8f7 d1d7", 1500, "8jXC8"),
            ChessPuzzle(35, "8/3R2k1/2r1P1p1/1pN5/1P5p/3K3P/2P3r1/8 b - - 1 50", "g7f6 d7f7 f6e5 e6e7 c6c8 f7f8", 1522, "zs4uj"),
            ChessPuzzle(36, "2brr1k1/5pp1/p6p/q7/Pb2p3/1P2B1PP/2Q2PB1/1RR3K1 w - - 0 25", "c2c7 d8d1 c1d1 a5c7", 1545, "MfMHp"),
            ChessPuzzle(37, "8/8/5p1p/1Q2p1k1/8/1R3P1P/4r3/3q1N1K w - - 5 43", "h1g1 d1d4 g1h1 d4f2 b5e2 f2e2", 1568, "cVLxH"),
            ChessPuzzle(38, "r1b2rk1/5pp1/p7/1p1pn1p1/2p5/2P2NP1/PPB3PP/R4RK1 b - - 1 23", "f7f6 f3e5 f6e5 c2h7 g8h7 f1f8", 1591, "7ug2E"),
            ChessPuzzle(39, "r1b1k1nr/p2pppbp/2p3p1/q7/4P3/2PBB3/PP3PPP/RN1QK2R b KQkq - 1 8", "c8a6 b2b4 a5c7 d3a6", 1615, "805s9"),
            ChessPuzzle(40, "2qr2k1/1b2bppp/p3pn2/np2N3/3P4/4B2P/PPBN1PP1/3Q1RK1 b - - 0 17", "f6d5 c2h7 g8h7 d1h5 h7g8 h5f7", 1636, "p3PHr"),
            ChessPuzzle(41, "4R3/1p3r1p/4p2k/3pP1q1/1Pb2Q2/5P2/6PP/6K1 w - - 0 47", "f4f7 g5e3 g1h1 e3c1", 1659, "zs3s4"),
            ChessPuzzle(42, "r1b3k1/p1p2p1p/2p3pQ/3r4/3q4/3B3P/P1P2PP1/3R1R1K b - - 4 18", "d5h5 h6h5 g6h5 d3h7 g8h7 d1d4", 1682, "7ufa5"),
            ChessPuzzle(43, "r4rk1/p1p2ppp/1pnq2b1/3p2P1/3Pn3/1QN1PN1P/PP2BP2/R4RK1 w - - 1 15", "c3d5 c6a5 b3d1 d6d5", 1705, "A6PQ0"),
            ChessPuzzle(44, "3R4/4Qpbk/p5p1/1p2pqBp/7P/1P3P2/2rp1PK1/8 b - - 3 41", "c2c1 e7e8 f5f3 g2f3 d2d1q d8d1", 1727, "zs6EY"),
            ChessPuzzle(45, "2r3k1/5p2/b3PP1p/3p2pq/p2N4/Pp1PQ3/1P6/1K4R1 b - - 0 32", "f7e6 e3e6 h5f7 e6a6", 1750, "S90Ja"),
            ChessPuzzle(46, "3r4/3rq2k/1p4p1/2pNPp1p/2Q2P1P/4K1P1/P7/3R4 b - - 0 37", "e7f7 d5f6 h7g7 c4f7 d7f7 d1d8", 1773, "mrJej"),
            ChessPuzzle(47, "r4rk1/ppqb1ppp/2nb1n2/1B1p4/8/2N2N1P/PPP2PP1/R1BQR1K1 w - - 3 12", "c3d5 f6d5 d1d5 c6b4", 1795, "S8zdH"),
            ChessPuzzle(48, "3r2k1/p1q1b1pp/2p1p3/2p1pp2/2Pr4/1P1RQ3/P1P1NPPP/3R2K1 b - - 3 22", "e5e4 e2d4 c5d4 d3d4", 1818, "p3OZm"),
            ChessPuzzle(49, "4kr2/1p2bpQ1/p2p4/1q1Pp3/5rPp/2P4P/1P2RPB1/4R1K1 b - - 6 26", "b5d3 e2e5 d6e5 g7e5", 1841, "F2JLL"),
            ChessPuzzle(50, "5r2/2p1b3/1kp1b3/1p2P2r/pP1P2p1/1q1N1p2/3R1QPP/K1R5 b - - 3 33", "e7g5 d4d5 b6b7 d3c5 b7b8 c5b3", 1864, "JW5KO"),
            ChessPuzzle(51, "4r2k/1p3PRb/7p/p6P/1P1Qr1PK/6B1/1P6/5q2 b - - 1 34", "e4d4 f7e8q h8g7 g3e5 f1f6 e5f6", 1886, "ui9eX"),
            ChessPuzzle(52, "8/8/2p1kp1p/2Pp1p2/3P1PP1/5K1P/8/8 b - - 0 39", "f5g4 f3g4 e6f7 g4f5", 1909, "NJSea"),
            ChessPuzzle(53, "2r1k2r/pp1b1p2/4p2p/6N1/3qp3/2P3P1/PPQ2PP1/R3K2R w KQk - 0 18", "c2e4 h6g5 e4d4 h8h1 e1d2 h1a1", 1933, "GyuQe"),
            ChessPuzzle(54, "8/3k3p/1p1p2p1/1Pp1p1P1/P3r3/2R4P/4nPK1/1R6 w - - 2 33", "c3e3 e2f4 g2f1 e4a4", 1955, "Q5Ana"),
            ChessPuzzle(55, "3rr3/1p2pB1p/p1p3p1/1nk3B1/2Nb4/8/P4PPP/1R4K1 b - - 9 27", "e8f8 g5e7 b5d6 c4d6 d8d6 e7f8", 1977, "46Yum"),
            ChessPuzzle(56, "3k4/6p1/bp5p/p1bp1Q2/P2R4/2PK3P/1PB3P1/4q3 w - - 3 36", "c3c4 a6c4 d4c4 e1e3", 2000, "7uhTo"),
            ChessPuzzle(57, "5rk1/pp5p/6p1/2p5/3P3q/4PB2/PP3bP1/R2Q1K2 b - - 0 28", "f2e3 d1b3 c5c4 b3e3 f8f3 g2f3 h4h1 e3g1", 2023, "Q5CiZ"),
            ChessPuzzle(58, "r1b2rk1/1pq1bpnp/p3p1pQ/4n3/3NN3/1P1B4/PBP3PP/R3R1K1 b - - 1 16", "e5g4 h6g7 g8g7 d4e6 g7g8 e6c7", 2045, "Q5DSO"),
            ChessPuzzle(59, "r1bq3r/p1N1kppp/1p2pn2/2bp4/2PQ1B2/P7/1P2PPPP/R3KB1R w KQ - 4 11", "d4e5 c5f2 e1f2 f6g4 f2e1 g4e5", 2067, "MfKCx"),
            ChessPuzzle(60, "r2q4/p4NBk/2p1rPp1/1p1pb2p/7Q/7P/P2n2P1/5R1K b - - 0 31", "d8e8 f7g5 h7g8 f6f7 e8f7 f1f7", 2091, "M9mZd"),
            ChessPuzzle(61, "r1b1kbnr/ppp2ppp/2n5/4p3/2B5/1PN2N2/P1qPQPPP/R1B1K2R b KQkq - 1 7", "c8g4 c4d3 c2d3 e2d3", 2114, "dflbe"),
            ChessPuzzle(62, "8/4Bp1p/4pKp1/1k2P3/p3bPP1/P7/7P/8 b - - 0 45", "h7h5 f6f7 h5g4 f7e6", 2136, "M9kMt"),
            ChessPuzzle(63, "2r5/5p1k/Q2p2pb/2q1p3/pN2P2p/P4P1P/1P4PK/5R2 w - - 0 35", "a6a4 h6f4 h2h1 c5f2 a4a6 f2g3", 2157, "w2Zxh"),
            ChessPuzzle(64, "8/8/8/2pk1P2/8/3q1K2/8/4Q3 w - - 10 66", "e1e3 d3f5 f3e2 f5e4 e3e4 d5e4 e2d2 e4d4 d2c2 d4c4", 2182, "Q5DLm"),
            ChessPuzzle(65, "2r1k3/R2n1p2/4p2p/1Rp1P3/2Np1r2/3K4/2P4P/8 b - - 2 32", "e8d8 b5b7 d7e5 c4e5", 2205, "A6QgD"),
            ChessPuzzle(66, "N5nr/b2k1pp1/p1np3p/1p6/2BPb1P1/1Q2Bq1P/PP3P2/2KR3R w - - 0 17", "c4b5 a6b5 b3b5 g8e7 b5b7 d7e6", 2227, "w2amL"),
            ChessPuzzle(67, "5rk1/1p4pp/pr1P4/4pp2/7b/1qPB4/1P2Q1R1/R6K b - - 0 28", "g8h8 d3c4 b3b2 e2b2 b6b2 g2b2", 2251, "Q5EKZ"),
            ChessPuzzle(68, "r3k1nr/pp1n1p1p/4p1p1/q1bpP3/8/2N1B3/PPPQ1PPP/2KR2NR w kq - 0 11", "c3d5 a5d2 d1d2 e6d5 e3c5 d7c5", 2273, "A6Pi0"),
            ChessPuzzle(69, "5rk1/pp3bp1/2p5/6R1/3Pq3/4Br1P/PPPQ1P1K/7R w - - 8 28", "h1g1 f3h3 h2h3 f7e6 g5g4 e6g4", 2294, "t4C1M"),
            ChessPuzzle(70, "r3r1k1/3q2p1/p2bp1Qp/1p1p4/1P1PnN2/P3P2P/6P1/2R1BRK1 b - - 5 24", "e6e5 f4d5 e5d4 d5b6 d7e7 b6a8", 2318, "TjJEe"),
            ChessPuzzle(71, "r3k2r/1bq1bppp/n3p1P1/p2p3P/PpnNP3/2P2P2/NP2B3/R1BQK1R1 b Qkq - 0 18", "d5e4 g6f7 e8f7 g1g7 f7e8 d4e6", 2340, "F2JVX"),
            ChessPuzzle(72, "r1b3k1/pp3r1p/1q4p1/3Bb3/8/P1RQ1P2/1P2R1PP/7K b - - 2 30", "e5c3 e2e8 g8g7 d3c3 b6f6 e8g8 g7h6 d5f7 f6c3 b2c3", 2364, "mrJW8"),
            ChessPuzzle(73, "r3k2r/1b1p1pp1/p2bp1n1/5q2/P3PPPp/2BB4/1P5P/R2Q1RNK b kq - 0 19", "b7e4 g1f3 f5f4 d3e4 f4e4 d1d6", 2387, "NJSDy"),
            ChessPuzzle(74, "rn2kb1r/pp3ppp/3qp3/2pn4/2B3b1/2P2N2/PP1P1PPP/RNBQR1K1 b kq - 1 8", "f8e7 d1a4 b8c6 c4d5 d6d5 a4g4", 2409, "JW5Xq"),
            ChessPuzzle(75, "1Rr4r/2Bk1ppp/B3p3/2bpP3/8/P7/5qPP/1Q5K w - - 2 27", "a6c8 h8c8 c7d6 c8b8", 2428, "XATfF"),
            ChessPuzzle(76, "8/8/6k1/1K1p4/3Pp1n1/P3P3/6p1/6B1 b - - 3 56", "g4e3 g1e3 g6f5 b5b4 f5g4 b4c3", 2456, "TjKFb"),
            ChessPuzzle(77, "8/5p2/5p1p/3kpP2/6PP/4KP2/8/8 b - - 0 43", "d5c4 e3e4 c4c3 g4g5 f6g5 h4g5 h6g5 e4e5 c3d3 e5f6 d3e2 f6f7", 2476, "cVPlO"),
            ChessPuzzle(78, "5k1b/p4p1p/3Np1pn/3pP1q1/Q2P2P1/7P/PP1n1P2/5RK1 b - - 6 23", "h6g4 a4e8 f8g7 e8h8 g7h8 d6f7 h8g8 f7g5", 2500, "XAQb3"),
            ChessPuzzle(79, "5rkb/p1q1pp2/6NP/2pP4/4p3/1QP4P/1K3P2/6R1 b - - 0 27", "f7g6 d5d6 c5c4 d6c7 c4b3 g1d1 h8e5 d1d8 e5c7 h6h7 g8h7 d8f8", 2523, "p3OUH"),
            ChessPuzzle(80, "r3rbk1/5p2/1qp3np/pp1pNQ1N/3P3P/2P3P1/PP2B3/2K4R b - - 7 24", "g6e5 h5f6 g8g7 h1f1", 2545, "uiCLQ"),
            ChessPuzzle(81, "r1bq2kr/pp2N1p1/2pp1P1p/2b1n2Q/4P3/3P4/PP4PP/n1B2R1K b - - 1 15", "g8f8 e7g6 e5g6 h5g6 g7f6 f1f6 d8f6 g6f6", 2567, "ZUYJn"),
            ChessPuzzle(82, "2b1r1k1/5ppp/p2b4/2pPn1q1/4p3/PP2P2P/1BQN1PP1/R4RK1 w - - 1 27", "d2e4 e5f3 g1h1 g5h4", 2592, "HdzT4"),
            ChessPuzzle(83, "7Q/1p6/1p3pp1/4p1kp/4Pn2/2P1N1P1/qP3P2/6K1 b - - 0 35", "f4d3 e3d5 a2b1 g1g2 b1c2 h8f6", 2614, "oWm5a"),
            ChessPuzzle(84, "4rr1k/ppp3p1/6q1/5n2/5PK1/2N3P1/PP3Q1P/R3R3 w - - 3 34", "g4f3 f5h4 g3h4 f8f4 f3f4 e8f8", 2641, "34mQ5"),
            ChessPuzzle(85, "8/8/8/1pp5/8/2P1k3/PK6/8 w - - 2 59", "b2b3 e3d2 c3c4 b5b4 b3b2 d2d3", 2659, "p3PzO"),
            ChessPuzzle(86, "8/5k2/r1P2p2/3P4/1p1KNpp1/8/P5P1/8 b - - 0 44", "f7e8 c6c7 a6a8 e4f6", 2683, "mrHSG"),
            ChessPuzzle(87, "r1bqk2r/1p2bpp1/p4n1p/3B4/3Q3B/2N5/PPP2PPP/R4RK1 b kq - 0 12", "f6d5 c3d5 e7h4 d4g7", 2706, "JmeeC"),
            ChessPuzzle(88, "r7/1k3K2/5R1p/4R1p1/6P1/7r/8/8 b - - 0 50", "a8a7 f7g6 b7c8 e5e8 c8d7 e8h8", 2727, "34nNw"),
            ChessPuzzle(89, "8/8/6p1/1p3p1p/5P1P/P1p1B3/1k3K2/8 w - - 6 72", "f2e2 b2a3 e2d3 a3b3 e3d4 b5b4 d4e5 b3b2", 2751, "7uewB"),
            ChessPuzzle(90, "r3k2r/1R1bbppp/p1p1pn2/7q/3NNB2/3Q4/P1P3PP/5R1K b kq - 7 20", "h5d5 e4c3 d5c5 c3a4", 2773, "uiBKY"),
            ChessPuzzle(91, "8/6k1/1p1p3p/p1p2RP1/P1P1r3/1q2p1KP/4Q3/8 b - - 0 38", "b3c4 e2h5 c4e6 f5f6 e6f6 g5f6", 2798, "XAQZk"),
            ChessPuzzle(92, "5Q2/1p3pk1/p3b1pp/8/1Br1P3/P5NP/2q3P1/2b4K b - - 2 31", "g7f6 f8h8 f6g5 h8e5 g5h4 b4e1", 2825, "uiAPx"),
            ChessPuzzle(93, "5rk1/1b3pp1/p1p3rp/1pqP3Q/4N2P/5R2/PP4P1/5R1K b - - 1 29", "c5d5 h5g6 f7g6 f3f8 g8h7 f1e1", 2839, "t4E2u"),
            ChessPuzzle(94, "r4r1k/2p3Rn/2npq2p/4pN2/p3Pb2/1PBP1P2/P1K1QN2/7R w - - 0 24", "g7c7 a4b3 c2b1 b3b2 a2a4 a8a4 e2b2 f8b8", 2870, "JW2R6"),
            ChessPuzzle(95, "5k2/4pn2/p2b3p/5Pp1/2QB2P1/1PP2q2/KP2R3/7r b - - 9 41", "h1f1 c4c8 f7d8 c8d8 f8f7 d4g7", 2890, "46YI8"),
            ChessPuzzle(96, "8/8/4r3/5p1P/8/3kpKP1/1R6/8 w - - 8 68", "h5h6 e3e2 b2b1 e2e1q b1e1 e6e1 f3f4 e1f1 f4e5 d3c4", 2905, "XASwz"),
            ChessPuzzle(97, "8/6p1/4p2p/4p2P/P3k3/1p4P1/3K4/8 b - - 0 44", "b3b2 d2c2 e4f5 c2b2 f5g4 b2c3 e5e4 c3d2 g4f3 d2e1", 2952, "w2ZhK"),
            ChessPuzzle(98, "6k1/4P3/3K1Pp1/8/8/8/8/2q5 b - - 4 67", "c1c2 e7e8q g8h7 e8d7 h7h6 d7h3 h6g5 h3f3", 2905, "A6Sbc"),
            ChessPuzzle(99, "6b1/P6k/q6p/8/4Q2P/4p3/1P6/1K6 b - - 2 48", "h7g7 e4g2 g7f6 g2f3 f6e5 a7a8q g8h7 b1c1 a6c4 c1d1 h7c2 d1e1", 2856, "uiAou"),
            ChessPuzzle(100, "r1b5/1pp4p/p2p2pk/4b3/1PBnP1q1/P1NQ2P1/2P2P1P/R4RK1 w - - 1 21", "a1e1 d4f3 g1g2 g4h5 h2h4 f3h4 g3h4 h5g4 d3g3 e5g3", 2831, "XATBG"),
    ).sortedBy { it.rating }.mapIndexed { index, puzzle ->
        puzzle.copy(level = index + 1)
    }
}