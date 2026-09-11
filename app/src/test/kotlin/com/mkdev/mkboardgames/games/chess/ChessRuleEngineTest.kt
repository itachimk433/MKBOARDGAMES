package com.mkdev.mkboardgames.games.chess

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChessRuleEngineTest {
    private val engine = ChessRuleEngine()

    @Test
    fun aKingCannotBeCaptured() {
        val board = arrayOfNulls<Piece>(64).apply {
            this[Position(7, 0).index()] = ChessPiece(ChessPieceType.KING, PieceColor.WHITE)
            this[Position(1, 4).index()] = ChessPiece(ChessPieceType.QUEEN, PieceColor.WHITE)
            this[Position(0, 4).index()] = ChessPiece(ChessPieceType.KING, PieceColor.BLACK)
        }
        val state = GameState(board = board, currentTurn = PieceColor.WHITE)

        assertFalse(
            engine.allLegalMoves(state, PieceColor.WHITE).any { move ->
                move.to == Position(0, 4)
            },
        )
        assertEquals(
            state,
            engine.applyMove(
                state,
                Move(from = Position(1, 4), to = Position(0, 4), captures = listOf(Position(0, 4))),
            ),
        )
    }

    @Test
    fun checkmateIsAWinForTheCheckingSide() {
        val board = arrayOfNulls<Piece>(64).apply {
            this[Position(0, 7).index()] = ChessPiece(ChessPieceType.KING, PieceColor.BLACK)
            this[Position(1, 6).index()] = ChessPiece(ChessPieceType.QUEEN, PieceColor.WHITE)
            this[Position(2, 5).index()] = ChessPiece(ChessPieceType.KING, PieceColor.WHITE)
        }
        val state = GameState(board = board, currentTurn = PieceColor.BLACK)

        assertEquals(GameStatus.WHITE_WINS, engine.gameStatus(state))
    }

    private fun Position.index(): Int = row * 8 + col
}