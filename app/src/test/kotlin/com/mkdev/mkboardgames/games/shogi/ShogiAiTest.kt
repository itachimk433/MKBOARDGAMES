package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.AIPlayer
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShogiAiTest {
    private val engine = ShogiRuleEngine()

    private fun stateOf(
        turn: PieceColor,
        hands: Map<PieceColor, List<Piece>> = emptyMap(),
        build: (Array<Piece?>) -> Unit,
    ): GameState {
        val board = arrayOfNulls<Piece>(81)
        build(board)
        return GameState(board = board, boardSize = 9, currentTurn = turn, hands = hands)
    }

    private fun put(
        board: Array<Piece?>,
        row: Int,
        col: Int,
        type: ShogiPieceType,
        color: PieceColor,
        promoted: Boolean = false,
    ) {
        board[row * 9 + col] = ShogiPiece(type, color, promoted)
    }

    private fun perft(state: GameState, depth: Int): Long {
        if (depth == 0) return 1
        return engine.allLegalMoves(state, state.currentTurn).sumOf { move ->
            perft(engine.applyForSearch(state, move), depth - 1)
        }
    }

    @Test
    fun perftStartPosition() {
        val state = engine.initialState()
        assertEquals(30L, perft(state, 1))
        assertEquals(900L, perft(state, 2))
        assertEquals(25_470L, perft(state, 3))
    }

    @Test
    fun fastAttackDetectionMatchesStartPosition() {
        val state = engine.initialState()
        assertFalse(engine.inCheck(state, PieceColor.WHITE))
        assertFalse(engine.inCheck(state, PieceColor.BLACK))
    }

    @Test
    fun detectsRookLanceAndKnightAttacks() {
        val rook = stateOf(PieceColor.WHITE) { board ->
            put(board, 8, 4, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 4, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 4, 4, ShogiPieceType.ROOK, PieceColor.BLACK)
        }
        assertTrue(engine.inCheck(rook, PieceColor.WHITE))

        val lance = stateOf(PieceColor.WHITE) { board ->
            put(board, 8, 4, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 0, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 3, 4, ShogiPieceType.LANCE, PieceColor.BLACK)
        }
        assertTrue(engine.inCheck(lance, PieceColor.WHITE))

        val knight = stateOf(PieceColor.WHITE) { board ->
            put(board, 4, 4, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 8, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 2, 3, ShogiPieceType.KNIGHT, PieceColor.BLACK)
        }
        assertTrue(engine.inCheck(knight, PieceColor.WHITE))
    }

    @Test
    fun nifuAndForcedPromotionAreEnforced() {
        val hands = mapOf(PieceColor.WHITE to listOf<Piece>(ShogiPiece(ShogiPieceType.PAWN, PieceColor.WHITE)))
        val nifu = stateOf(PieceColor.WHITE, hands) { board ->
            put(board, 8, 0, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 8, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 6, 3, ShogiPieceType.PAWN, PieceColor.WHITE)
        }
        val drops = engine.legalDropsFrom(nifu, ShogiPieceType.PAWN)
        assertTrue(drops.none { it.to.col == 3 })
        assertTrue(drops.isNotEmpty())

        val forced = stateOf(PieceColor.WHITE) { board ->
            put(board, 8, 0, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 8, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 1, 4, ShogiPieceType.PAWN, PieceColor.WHITE)
        }
        val moves = engine.legalMovesFrom(forced, Position(1, 4))
        assertEquals(1, moves.size)
        assertNotNull(moves[0].promotionType)
    }

    @Test
    fun capturesGoToCapturersHandUnpromoted() {
        val state = stateOf(PieceColor.WHITE) { board ->
            put(board, 8, 0, ShogiPieceType.KING, PieceColor.WHITE)
            put(board, 0, 8, ShogiPieceType.KING, PieceColor.BLACK)
            put(board, 5, 4, ShogiPieceType.ROOK, PieceColor.WHITE)
            put(board, 3, 4, ShogiPieceType.BISHOP, PieceColor.BLACK, promoted = true)
        }
        val move = engine.allLegalMoves(state, PieceColor.WHITE)
            .first { it.to == Position(3, 4) && it.promotionType == null }
        val after = engine.applyMove(state, move)
        val hand = after.hands[PieceColor.WHITE].orEmpty().filterIsInstance<ShogiPiece>()
        assertEquals(1, hand.size)
        assertEquals(ShogiPieceType.BISHOP, hand[0].type)
        assertFalse(hand[0].promoted)
        assertEquals(PieceColor.WHITE, hand[0].color)
    }

    @Test
    fun searchReturnsLegalMovesWithinBudget() {
        val start = System.currentTimeMillis()
        val ai = AIPlayer(engine, maxDepth = 20, timeLimitMs = 700, quiesceDepth = 1)
        var state = engine.initialState()
        repeat(10) {
            if (state.status != GameStatus.IN_PROGRESS) return
            val move = ai.bestMove(state) ?: return
            assertTrue(move in engine.allLegalMoves(state, state.currentTurn))
            state = engine.applyMove(state, move)
        }
        assertTrue(System.currentTimeMillis() - start < 1_500)
    }
}