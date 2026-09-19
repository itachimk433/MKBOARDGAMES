package com.mkdev.mkboardgames.games.fivefieldkono

import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiveFieldKonoRuleEngineTest {
    private val engine = FiveFieldKonoRuleEngine()

    @Test
    fun initialPositionHasFivePiecesPerSideAndWhiteMovesFirst() {
        val state = engine.initialState()

        assertEquals(PieceColor.WHITE, state.currentTurn)
        assertEquals(5, state.board.count { it?.color == PieceColor.WHITE })
        assertEquals(5, state.board.count { it?.color == PieceColor.BLACK })
        assertEquals(GameStatus.IN_PROGRESS, state.status)
    }

    @Test
    fun whiteCanMoveDiagonallyFromTheHomeRow() {
        val state = engine.initialState()

        assertTrue(
            engine.legalMovesFrom(state, Position(4, 1)).contains(Move(Position(4, 1), Position(3, 0))),
        )
        assertTrue(
            engine.legalMovesFrom(state, Position(4, 1)).contains(Move(Position(4, 1), Position(3, 2))),
        )
    }

    @Test
    fun occupiedAdjacentPointCanBeJumpedWithoutCapture() {
        val initial = engine.initialState()
        val board = initial.board.copyOf().apply {
            this[FiveFieldKonoRuleEngine.indexOf(Position(0, 2))] = null
            this[FiveFieldKonoRuleEngine.indexOf(Position(3, 2))] =
                FiveFieldKonoPiece(PieceColor.BLACK)
        }
        val second = initial.copy(board = board)

        val jumps = engine.legalMovesFrom(second, Position(4, 1))
        assertTrue(jumps.any { it.to == Position(2, 3) })
        assertEquals(5, second.board.count { it?.color == PieceColor.WHITE })
        assertEquals(5, second.board.count { it?.color == PieceColor.BLACK })
    }
}