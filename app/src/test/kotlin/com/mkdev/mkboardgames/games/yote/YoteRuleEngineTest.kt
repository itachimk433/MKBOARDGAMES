package com.mkdev.mkboardgames.games.yote

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class YoteRuleEngineTest {
    private val engine = YoteRuleEngine()

    @Test
    fun captureRequiresSecondRemovalWhenAnOpponentPieceRemains() {
        val state = captureState(extraBlack = Position(4, 5))
        val capture = captureMove(state)

        val rejected = engine.applyMove(state, capture)

        assertSame(state, rejected)
    }

    @Test
    fun captureRemovesBothPiecesAndPassesTheTurn() {
        val extraBlack = Position(4, 5)
        val state = captureState(extraBlack = extraBlack)
        val capture = engine.completeBonusCapture(state, captureMove(state), extraBlack)

        val next = engine.applyMove(state, capture)

        assertNotSame(state, next)
        assertEquals(PieceColor.BLACK, next.currentTurn)
        assertEquals(PieceColor.WHITE, next.get(Position(0, 2))?.color)
        assertNull(next.get(Position(0, 1)))
        assertNull(next.get(extraBlack))
        assertEquals(
            listOf(Position(0, 1), extraBlack),
            next.lastMove?.captures,
        )
    }

    @Test
    fun finalCaptureDoesNotRequireAnUnavailableSecondRemoval() {
        val state = captureState(extraBlack = null)
        val next = engine.applyMove(state, captureMove(state))

        assertEquals(GameStatus.WHITE_WINS, next.status)
        assertEquals(PieceColor.BLACK, next.currentTurn)
        assertNull(next.get(Position(0, 1)))
    }

    @Test
    fun havingNoLegalMoveDoesNotDeclareAWin() {
        val board = Array<Piece?>(YoteRuleEngine.BOARD_CELLS) { index ->
            YotePiece(if (index % 2 == 0) PieceColor.WHITE else PieceColor.BLACK)
        }
        val state = GameState(
            board = board,
            boardSize = YoteRuleEngine.COLUMNS,
            currentTurn = PieceColor.WHITE,
            status = GameStatus.IN_PROGRESS,
            hands = mapOf(
                PieceColor.WHITE to emptyList(),
                PieceColor.BLACK to emptyList(),
            ),
        )

        assertEquals(GameStatus.IN_PROGRESS, engine.gameStatus(state))
    }

    private fun captureState(extraBlack: Position?): GameState {
        val board = arrayOfNulls<Piece>(YoteRuleEngine.BOARD_CELLS)
        board[YoteRuleEngine.indexOf(Position(0, 0))] = YotePiece(PieceColor.WHITE)
        board[YoteRuleEngine.indexOf(Position(0, 1))] = YotePiece(PieceColor.BLACK)
        extraBlack?.let { board[YoteRuleEngine.indexOf(it)] = YotePiece(PieceColor.BLACK) }
        return GameState(
            board = board,
            boardSize = YoteRuleEngine.COLUMNS,
            currentTurn = PieceColor.WHITE,
            status = GameStatus.IN_PROGRESS,
            hands = mapOf(
                PieceColor.WHITE to emptyList(),
                PieceColor.BLACK to emptyList(),
            ),
        )
    }

    private fun captureMove(state: GameState) =
        engine.legalMovesFrom(state, Position(0, 0))
            .single { it.to == Position(0, 2) }
}