package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.PieceColor

/**
 * Static Shogi evaluation, returned from WHITE's perspective.
 *
 * Terms (all in centipawn-like units where a pawn = 100):
 *  - board material with promoted bonuses
 *  - hand material (slightly above board value: drops are flexible)
 *  - advancement / piece-square bonuses
 *  - promotion-zone pressure and pawn advancement
 *  - king safety (adjacent defenders, king exposure)
 *  - cheap mobility for sliders
 *  - penalty for pieces with no future moves (dead pawn/lance/knight)
 */
object ShogiEvaluator {
    private const val SIZE = ShogiSetup.SIZE

    // Values for pieces in hand. Drops are stronger than the same piece
    // on the board for the minor pieces, so hands get a small bonus.
    private fun handValue(type: ShogiPieceType): Int = when (type) {
        ShogiPieceType.PAWN -> 115
        ShogiPieceType.LANCE -> 330
        ShogiPieceType.KNIGHT -> 340
        ShogiPieceType.SILVER -> 460
        ShogiPieceType.GOLD -> 560
        ShogiPieceType.BISHOP -> 920
        ShogiPieceType.ROOK -> 1_100
        ShogiPieceType.KING -> 0
    }

    private fun boardValue(p: ShogiPiece): Int = when {
        !p.promoted -> when (p.type) {
            ShogiPieceType.PAWN -> 100
            ShogiPieceType.LANCE -> 300
            ShogiPieceType.KNIGHT -> 320
            ShogiPieceType.SILVER -> 440
            ShogiPieceType.GOLD -> 520
            ShogiPieceType.BISHOP -> 830
            ShogiPieceType.ROOK -> 1_000
            ShogiPieceType.KING -> 0
        }
        else -> when (p.type) {
            ShogiPieceType.PAWN -> 420      // tokin
            ShogiPieceType.LANCE -> 500
            ShogiPieceType.KNIGHT -> 510
            ShogiPieceType.SILVER -> 520
            ShogiPieceType.GOLD -> 0
            ShogiPieceType.BISHOP -> 1_150  // horse
            ShogiPieceType.ROOK -> 1_300    // dragon
            ShogiPieceType.KING -> 0
        }
    }

    fun evaluate(state: GameState): Int {
        var white = 0
        var black = 0

        var whiteKingRow = -1
        var whiteKingCol = -1
        var blackKingRow = -1
        var blackKingCol = -1

        // First pass: locate kings.
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val p = state.board[r * SIZE + c] as? ShogiPiece ?: continue
            if (p.type == ShogiPieceType.KING) {
                if (p.color == PieceColor.WHITE) {
                    whiteKingRow = r
                    whiteKingCol = c
                } else {
                    blackKingRow = r
                    blackKingCol = c
                }
            }
        }

        val whitePawnFiles = BooleanArray(SIZE)
        val blackPawnFiles = BooleanArray(SIZE)

        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val p = state.board[r * SIZE + c] as? ShogiPiece ?: continue
            if (p.type == ShogiPieceType.KING) continue
            val isWhite = p.color == PieceColor.WHITE
            // advance: 0 at own back rank, 8 at enemy back rank.
            val advance = if (isWhite) 8 - r else r
            val centerFile = 4 - kotlin.math.abs(c - 4) // 0..4

            var s = boardValue(p)
            s += positional(p, advance, centerFile)

            // Dead-piece penalty: pieces that can never move again.
            if (!p.promoted) {
                if ((p.type == ShogiPieceType.PAWN || p.type == ShogiPieceType.LANCE) && advance == 8) s -= 200
                if (p.type == ShogiPieceType.KNIGHT && advance >= 7) s -= 200
            }

            // Promotion-zone pressure: pieces that can promote and sit in the zone.
            if (!p.promoted && advance >= 6) {
                s += when (p.type) {
                    ShogiPieceType.PAWN -> 25 + (advance - 6) * 15
                    ShogiPieceType.SILVER, ShogiPieceType.KNIGHT, ShogiPieceType.LANCE -> 30
                    ShogiPieceType.BISHOP, ShogiPieceType.ROOK -> 45
                    else -> 0
                }
            }

            // King attack pressure: friendly pieces near the enemy king.
            val ekr = if (isWhite) blackKingRow else whiteKingRow
            val ekc = if (isWhite) blackKingCol else whiteKingCol
            if (ekr >= 0) {
                val dist = maxOf(kotlin.math.abs(r - ekr), kotlin.math.abs(c - ekc))
                if (dist <= 2) {
                    s += when (p.type) {
                        ShogiPieceType.ROOK, ShogiPieceType.BISHOP -> 40 - dist * 10
                        ShogiPieceType.GOLD, ShogiPieceType.SILVER -> 30 - dist * 8
                        ShogiPieceType.KNIGHT -> 25 - dist * 6
                        else -> 12 - dist * 4
                    }
                }
            }

            if (p.type == ShogiPieceType.PAWN && !p.promoted) {
                if (isWhite) whitePawnFiles[c] = true else blackPawnFiles[c] = true
            }
            if (isWhite) white += s else black += s
        }

        // Pieces in hand.
        for ((color, pieces) in state.hands) {
            var h = 0
            for (piece in pieces) {
                val sp = piece as? ShogiPiece ?: continue
                h += handValue(sp.type)
            }
            if (color == PieceColor.WHITE) white += h else black += h
        }

        // King safety.
        white += kingSafety(state, whiteKingRow, whiteKingCol, PieceColor.WHITE)
        black += kingSafety(state, blackKingRow, blackKingCol, PieceColor.BLACK)

        // Passed pawns: no enemy unpromoted pawn on the file ahead is not
        // meaningful in shogi (drops), so reward open files for rooks instead.
        for (c in 0 until SIZE) {
            if (!whitePawnFiles[c] && !blackPawnFiles[c]) {
                for (r in 0 until SIZE) {
                    val p = state.board[r * SIZE + c] as? ShogiPiece ?: continue
                    if (p.type == ShogiPieceType.ROOK) {
                        if (p.color == PieceColor.WHITE) white += 30 else black += 30
                    }
                }
            }
        }

        // Mobility (cheap): count slider reach for rooks/bishops/lances.
        white += sliderMobility(state, PieceColor.WHITE)
        black += sliderMobility(state, PieceColor.BLACK)

        return white - black
    }

    private fun positional(p: ShogiPiece, advance: Int, centerFile: Int): Int = when {
        p.promoted -> advance * 2 + centerFile * 2
        else -> when (p.type) {
            ShogiPieceType.PAWN -> advance * 6 + (if (advance in 3..5) centerFile * 2 else 0)
            ShogiPieceType.LANCE -> advance * 3
            ShogiPieceType.KNIGHT -> advance * 4 + centerFile * 2
            ShogiPieceType.SILVER -> advance * 5 + centerFile * 3
            ShogiPieceType.GOLD -> advance * 2 + centerFile
            ShogiPieceType.BISHOP -> centerFile * 4 + advance
            ShogiPieceType.ROOK -> advance * 3
            ShogiPieceType.KING -> 0
        }
    }

    private fun kingSafety(state: GameState, kr: Int, kc: Int, color: PieceColor): Int {
        if (kr < 0) return 0
        var s = 0
        val ownHomeRow = if (color == PieceColor.WHITE) 8 else 0
        val advanceFromHome = kotlin.math.abs(kr - ownHomeRow)

        // King should stay near home early; heavy penalty for wandering out.
        s -= advanceFromHome * 12
        // Prefer flank (castled) over center file.
        val distFromCenter = kotlin.math.abs(kc - 4)
        s += minOf(distFromCenter, 3) * 6

        // Defenders in the 3x3 around the king, weighted toward gold/silver.
        var defenders = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = kr + dr
            val c = kc + dc
            if (r !in 0 until SIZE || c !in 0 until SIZE) continue
            val p = state.board[r * SIZE + c] as? ShogiPiece ?: continue
            if (p.color != color) {
                // Enemy piece adjacent to our king is a threat.
                s -= 25
                continue
            }
            defenders += when (p.type) {
                ShogiPieceType.GOLD -> 3
                ShogiPieceType.SILVER -> 2
                ShogiPieceType.KNIGHT, ShogiPieceType.LANCE -> 1
                ShogiPieceType.PAWN -> 1
                else -> if (p.promoted) 2 else 1
            }
        }
        s += minOf(defenders, 8) * 14

        // Enemy pieces in hand are a drop threat against a weakly defended king.
        val enemy = color.opponent()
        val enemyHand = state.hands[enemy].orEmpty()
        if (enemyHand.isNotEmpty() && defenders < 4) {
            s -= (4 - defenders) * enemyHand.size.coerceAtMost(4) * 4
        }
        return s
    }

    private fun sliderMobility(state: GameState, color: PieceColor): Int {
        var total = 0
        val forward = if (color == PieceColor.WHITE) -1 else 1
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val p = state.board[r * SIZE + c] as? ShogiPiece ?: continue
            if (p.color != color) continue
            when {
                p.type == ShogiPieceType.ROOK -> total += 3 * ray(state, r, c, 1, 0) +
                    3 * ray(state, r, c, -1, 0) + 3 * ray(state, r, c, 0, 1) + 3 * ray(state, r, c, 0, -1)
                p.type == ShogiPieceType.BISHOP -> total += 3 * ray(state, r, c, 1, 1) +
                    3 * ray(state, r, c, 1, -1) + 3 * ray(state, r, c, -1, 1) + 3 * ray(state, r, c, -1, -1)
                p.type == ShogiPieceType.LANCE && !p.promoted -> total += 2 * ray(state, r, c, forward, 0)
            }
        }
        return total
    }

    private fun ray(state: GameState, r0: Int, c0: Int, dr: Int, dc: Int): Int {
        var r = r0 + dr
        var c = c0 + dc
        var n = 0
        while (r in 0 until SIZE && c in 0 until SIZE) {
            n++
            if (state.board[r * SIZE + c] != null) break
            r += dr
            c += dc
        }
        return n
    }
}