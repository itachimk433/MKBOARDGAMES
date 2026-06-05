package com.mkdev.nexboard.games.morabaraba

import com.mkdev.nexboard.engine.*

// ─── Piece ────────────────────────────────────────────────────────────────────

data class MorabarabaPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol() = "●"
    override fun value() = 100
}

// ─── Board topology ───────────────────────────────────────────────────────────

/**
 * The Morabaraba / Nine Men's Morris board mapped onto a 7×7 logical grid.
 *
 * 24 valid positions in 3 concentric rings connected by 4 spokes:
 *
 *   0 ─────── 1 ─────── 2
 *   │         │         │
 *   │  8 ──── 9 ──── 10 │
 *   │  │      │      │  │
 *   │  │  16─17─18   │  │
 *   │  │  │       │  │  │
 *   3  11 19      20 12  4
 *   │  │  │       │  │  │
 *   │  │  21─22─23   │  │
 *   │  │      │      │  │
 *   │  13 ── 14 ── 15 │
 *   │         │         │
 *   5 ─────── 6 ─────── 7
 *
 *  Indices 0-23 correspond to the positions array below.
 */
object MorabarabaBoard {

    /** The 24 valid board positions as (row, col) in a 7×7 logical grid. */
    val POSITIONS = listOf(
        // Outer ring (8)
        Position(0,0), Position(0,3), Position(0,6),   // 0  1  2
        Position(3,0), Position(3,6),                   // 3  4
        Position(6,0), Position(6,3), Position(6,6),   // 5  6  7
        // Middle ring (8)
        Position(1,1), Position(1,3), Position(1,5),   // 8  9 10
        Position(3,1), Position(3,5),                   // 11 12
        Position(5,1), Position(5,3), Position(5,5),   // 13 14 15
        // Inner ring (8)
        Position(2,2), Position(2,3), Position(2,4),   // 16 17 18
        Position(3,2), Position(3,4),                   // 19 20
        Position(4,2), Position(4,3), Position(4,4)    // 21 22 23
    )

    /** Adjacency list: each index maps to the connected node indices. */
    val ADJACENCY: Array<IntArray> = arrayOf(
        //  0         1             2
        intArrayOf(1,3), intArrayOf(0,2,9), intArrayOf(1,4),
        //  3               4               5
        intArrayOf(0,5,11), intArrayOf(2,7,12), intArrayOf(3,6),
        //  6               7
        intArrayOf(5,7,14), intArrayOf(4,6),
        //  8              9                  10
        intArrayOf(9,11), intArrayOf(8,10,1,17), intArrayOf(9,12),
        //  11                    12                  13
        intArrayOf(8,13,3,19), intArrayOf(10,15,4,20), intArrayOf(11,14),
        //  14                    15
        intArrayOf(13,15,6,22), intArrayOf(12,14),
        //  16             17                 18
        intArrayOf(17,19), intArrayOf(16,18,9), intArrayOf(17,20),
        //  19                   20                  21
        intArrayOf(16,21,11), intArrayOf(18,23,12), intArrayOf(19,22),
        //  22                   23
        intArrayOf(21,23,14), intArrayOf(20,22)
    )

    /** Every possible mill (line of 3 that wins a capture right). */
    val MILLS: List<IntArray> = listOf(
        // Outer ring
        intArrayOf(0,1,2), intArrayOf(5,6,7),
        intArrayOf(0,3,5), intArrayOf(2,4,7),
        // Middle ring
        intArrayOf(8,9,10), intArrayOf(13,14,15),
        intArrayOf(8,11,13), intArrayOf(10,12,15),
        // Inner ring
        intArrayOf(16,17,18), intArrayOf(21,22,23),
        intArrayOf(16,19,21), intArrayOf(18,20,23),
        // Spokes
        intArrayOf(1,9,17), intArrayOf(3,11,19),
        intArrayOf(4,12,20), intArrayOf(6,14,22)
    )

    // ── Position lookup helpers ───────────────────────────────────────────────

    private val posToIdx: Map<Position, Int> = POSITIONS.withIndex().associate { (i, p) -> p to i }

    fun indexOf(pos: Position): Int = posToIdx[pos] ?: -1
    fun posAt(idx: Int): Position    = POSITIONS[idx]

    fun boardAt(state: GameState, idx: Int): Piece? = state.get(POSITIONS[idx])

    /** All node indices occupied by [color]. */
    fun piecesOf(state: GameState, color: PieceColor): List<Int> =
        POSITIONS.indices.filter { boardAt(state, it)?.color == color }

    /** True if [idx] is in a mill of [color] on the given board. */
    fun inMill(board: Array<Piece?>, idx: Int, color: PieceColor): Boolean =
        MILLS.any { mill ->
            idx in mill && mill.all { i -> (board[POSITIONS[i].row * 7 + POSITIONS[i].col] as? MorabarabaPiece)?.color == color }
        }

    /** Returns opponent pieces that may legally be removed (not in a mill if possible). */
    fun removableBy(board: Array<Piece?>, attacker: PieceColor): List<Int> {
        val opp = attacker.opponent()
        val oppNodes = POSITIONS.indices.filter {
            (board[POSITIONS[it].row * 7 + POSITIONS[it].col] as? MorabarabaPiece)?.color == opp
        }
        val notInMill = oppNodes.filter { !inMill(board, it, opp) }
        return if (notInMill.isNotEmpty()) notInMill else oppNodes
    }

    // ── Six-cow (Six Men's Morris) variant — outer + middle rings only ───────

    /** 16-position board for 6-cow variant (indices 0..15 of POSITIONS). */
    val SIX_POSITIONS: List<Position> = POSITIONS.take(16)

    /** Adjacency for 6-cow — same 2-ring topology, no inner-ring spokes. */
    val SIX_ADJACENCY: Array<IntArray> = arrayOf(
        intArrayOf(1, 3),       // 0
        intArrayOf(0, 2, 9),    // 1  spoke ↕ 9
        intArrayOf(1, 4),       // 2
        intArrayOf(0, 5, 11),   // 3  spoke ↔ 11
        intArrayOf(2, 7, 12),   // 4  spoke ↔ 12
        intArrayOf(3, 6),       // 5
        intArrayOf(5, 7, 14),   // 6  spoke ↕ 14
        intArrayOf(4, 6),       // 7
        intArrayOf(9, 11),      // 8
        intArrayOf(8, 10, 1),   // 9  spoke ↕ 1
        intArrayOf(9, 12),      // 10
        intArrayOf(8, 13, 3),   // 11 spoke ↔ 3
        intArrayOf(10, 15, 4),  // 12 spoke ↔ 4
        intArrayOf(11, 14),     // 13
        intArrayOf(13, 15, 6),  // 14 spoke ↕ 6
        intArrayOf(12, 14)      // 15
    )

    /** 8 mills for the 6-cow variant (4 outer sides + 4 inner sides). */
    val SIX_MILLS: List<IntArray> = listOf(
        intArrayOf(0, 1, 2), intArrayOf(5, 6, 7),
        intArrayOf(0, 3, 5), intArrayOf(2, 4, 7),
        intArrayOf(8, 9, 10), intArrayOf(13, 14, 15),
        intArrayOf(8, 11, 13), intArrayOf(10, 12, 15)
    )

    // ── Simple (single-square) board — for 6-cow variant ────────────────────
    //
    //  0 ─── 1 ─── 2          (TL, TM, TR)
    //  │  ╲  │  ╱  │
    //  3 ─── 4 ─── 5          (ML, C,  MR)
    //  │  ╱  │  ╲  │
    //  6 ─── 7 ─── 8          (BL, BM, BR)
    //
    //  4 corners connected to centre via diagonals.
    //  4 midpoints connected to centre via straight spokes.
    //  Outer square sides connect adjacent nodes.

    val SIMPLE_POSITIONS: List<Position> = listOf(
        Position(0, 0), Position(0, 3), Position(0, 6),   // 0 TL  1 TM  2 TR
        Position(3, 0), Position(3, 3), Position(3, 6),   // 3 ML  4 C   5 MR
        Position(6, 0), Position(6, 3), Position(6, 6)    // 6 BL  7 BM  8 BR
    )

    val SIMPLE_ADJACENCY: Array<IntArray> = arrayOf(
        intArrayOf(1, 3, 4),                          // 0 TL
        intArrayOf(0, 2, 4),                          // 1 TM
        intArrayOf(1, 5, 4),                          // 2 TR
        intArrayOf(0, 6, 4),                          // 3 ML
        intArrayOf(0, 1, 2, 3, 5, 6, 7, 8),           // 4 C  — connects to every other node
        intArrayOf(2, 8, 4),                          // 5 MR
        intArrayOf(3, 7, 4),                          // 6 BL
        intArrayOf(6, 8, 4),                          // 7 BM
        intArrayOf(5, 7, 4)                           // 8 BR
    )

    /** 8 mills: 4 outer sides + 2 midlines through centre + 2 diagonals. */
    val SIMPLE_MILLS: List<IntArray> = listOf(
        intArrayOf(0, 1, 2),  // top side
        intArrayOf(6, 7, 8),  // bottom side
        intArrayOf(0, 3, 6),  // left side
        intArrayOf(2, 5, 8),  // right side
        intArrayOf(3, 4, 5),  // horizontal midline
        intArrayOf(1, 4, 7),  // vertical midline
        intArrayOf(0, 4, 8),  // main diagonal
        intArrayOf(2, 4, 6)   // anti-diagonal
    )

    // Metadata keys
    const val META_W = "mora_w"   // white pieces placed
    const val META_B = "mora_b"   // black pieces placed
}
