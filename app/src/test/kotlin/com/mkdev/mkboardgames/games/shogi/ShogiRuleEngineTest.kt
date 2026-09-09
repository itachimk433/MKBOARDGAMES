package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShogiRuleEngineTest {
    private val engine = ShogiRuleEngine()

    @Test
    fun dropThatLeavesKingInRookCheckIsNotLegal() {
        val state = sparseState(
            boardPieces = listOf(
                Position(8, 4) to ShogiPiece(ShogiPieceType.KING, PieceColor.WHITE),
                Position(0, 4) to ShogiPiece(ShogiPieceType.KING, PieceColor.BLACK),
                Position(8, 0) to ShogiPiece(ShogiPieceType.ROOK, PieceColor.BLACK),
            ),
            hands = mapOf(
                PieceColor.WHITE to listOf(ShogiPiece(ShogiPieceType.PAWN, PieceColor.WHITE)),
                PieceColor.BLACK to emptyList(),
            ),
            turn = PieceColor.WHITE,
        )

        val drops = engine.legalDropsFrom(state, ShogiPieceType.PAWN)

        assertFalse(drops.any { it.to == Position(0, 0) })
        assertTrue(drops.any { it.to == Position(8, 2) })
    }

    @Test
    fun checkedKingCanOnlyUseMovesThatResolveTheCheck() {
        val state = sparseState(
            boardPieces = listOf(
                Position(8, 4) to ShogiPiece(ShogiPieceType.KING, PieceColor.WHITE),
                Position(0, 4) to ShogiPiece(ShogiPieceType.KING, PieceColor.BLACK),
                Position(8, 0) to ShogiPiece(ShogiPieceType.ROOK, PieceColor.BLACK),
            ),
            turn = PieceColor.WHITE,
        )

        val moves = engine.allLegalMoves(state, PieceColor.WHITE)

        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { move ->
            val next = engine.applyMove(state, move)
            next.status == GameStatus.IN_PROGRESS || next.currentTurn == PieceColor.BLACK
        })
        assertFalse(moves.any { it.to == Position(8, 3) })
    }

    private fun sparseState(
        boardPieces: List<Pair<Position, ShogiPiece>>,
        hands: Map<PieceColor, List<Piece>> = emptyMap(),
        turn: PieceColor,
    ): GameState {
        val board = arrayOfNulls<Piece>(ShogiSetup.SIZE * ShogiSetup.SIZE)
        boardPieces.forEach { (position, piece) ->
            board[position.row * ShogiSetup.SIZE + position.col] = piece
        }
        return GameState(
            board = board,
            boardSize = ShogiSetup.SIZE,
            currentTurn = turn,
            status = GameStatus.IN_PROGRESS,
            hands = hands,
        )
    }
}