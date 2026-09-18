package com.mkdev.mkboardgames.games.amazons

import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmazonsRuleEngineTest {

    @Test
    fun `both variants use their expected board sizes and eight amazons`() {
        val small = AmazonsRuleEngine(8).initialState()
        val large = AmazonsRuleEngine(10).initialState()

        assertEquals(64, small.board.size)
        assertEquals(100, large.board.size)
        assertEquals(8, small.board.count { it is AmazonsPiece })
        assertEquals(8, large.board.count { it is AmazonsPiece })
        assertEquals(PieceColor.WHITE, small.currentTurn)
        assertEquals(PieceColor.WHITE, large.currentTurn)
    }

    @Test
    fun `a move includes a queen move and a clear arrow shot`() {
        val engine = AmazonsRuleEngine(8)
        val state = engine.initialState()
        val from = Position(7, 2)
        val move = assertNotNull(engine.legalMovesFrom(state, from).firstOrNull {
            it.to == Position(6, 2) &&
                it.metadata[AmazonsRuleEngine.ARROW_METADATA] == Position(4, 2)
        })

        val next = engine.applyMove(state, move)
        assertTrue(next.get(from) == null)
        assertTrue(next.get(move.to) is AmazonsPiece)
        assertEquals(
            AmazonsPieceType.ARROW,
            (next.get(Position(4, 2)) as AmazonsPiece).type,
        )
        assertEquals(PieceColor.BLACK, next.currentTurn)
    }

    @Test
    fun `an arrow may be fired back onto the amazon source square`() {
        val engine = AmazonsRuleEngine(8)
        val state = engine.initialState()
        val from = Position(7, 2)
        val move = assertNotNull(engine.legalMovesFrom(state, from).firstOrNull {
            it.to == Position(6, 2) &&
                it.metadata[AmazonsRuleEngine.ARROW_METADATA] == from
        })

        val next = engine.applyMove(state, move)
        assertEquals(AmazonsPieceType.ARROW, (next.get(from) as AmazonsPiece).type)
        assertEquals(PieceColor.WHITE, (next.get(move.to) as AmazonsPiece).color)
    }

    @Test
    fun `an arrow blocks later rays and cannot pass through a piece`() {
        val engine = AmazonsRuleEngine(8)
        val state = engine.initialState()
        val move = engine.legalMovesFrom(state, Position(7, 2)).first {
            it.to == Position(6, 2) &&
                it.metadata[AmazonsRuleEngine.ARROW_METADATA] == Position(4, 2)
        }
        val next = engine.applyMove(state, move)

        assertTrue(
            engine.legalMovesFrom(next, Position(6, 2)).none {
                it.to == Position(3, 2)
            },
        )
    }

    @Test
    fun `a side with no legal moves loses`() {
        val engine = AmazonsRuleEngine(8)
        val board = arrayOfNulls<com.mkdev.mkboardgames.engine.Piece>(64)
        board[0] = AmazonsPiece(AmazonsPieceType.AMAZON, PieceColor.WHITE)
        for (index in 1 until board.size) {
            board[index] = AmazonsPiece(AmazonsPieceType.ARROW, PieceColor.WHITE)
        }
        val state = com.mkdev.mkboardgames.engine.GameState(
            board = board,
            boardSize = 8,
            currentTurn = PieceColor.WHITE,
        )

        assertEquals(GameStatus.BLACK_WINS, engine.gameStatus(state))
    }
}