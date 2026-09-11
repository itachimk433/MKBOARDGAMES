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

    @Test
    fun kingsideCastlingMovesTheRookAndConsumesCastlingRights() {
        val board = arrayOfNulls<Piece>(64).apply {
            this[Position(7, 4).index()] = ChessPiece(ChessPieceType.KING, PieceColor.WHITE)
            this[Position(7, 7).index()] = ChessPiece(ChessPieceType.ROOK, PieceColor.WHITE)
            this[Position(0, 4).index()] = ChessPiece(ChessPieceType.KING, PieceColor.BLACK)
        }
        val state = GameState(
            board = board,
            currentTurn = PieceColor.WHITE,
            metadata = mapOf(
                "castleWK" to true,
                "castleWQ" to false,
                "castleBK" to false,
                "castleBQ" to false,
                "enPassant" to -1,
            ),
        )

        val castle = engine.legalMovesFrom(state, Position(7, 4))
            .firstOrNull { it.to == Position(7, 6) }
            ?: error("Expected kingside castling to be legal")
        assertEquals("K", castle.metadata["castle"])
        val next = engine.applyMove(state, castle)

        assertEquals(ChessPiece(ChessPieceType.KING, PieceColor.WHITE), next.get(Position(7, 6)))
        assertEquals(ChessPiece(ChessPieceType.ROOK, PieceColor.WHITE), next.get(Position(7, 5)))
        assertEquals(null, next.get(Position(7, 7)))
        assertEquals(false, next.metadata["castleWK"])
        assertEquals(PieceColor.BLACK, next.currentTurn)
    }

    private fun Position.index(): Int = row * 8 + col
}