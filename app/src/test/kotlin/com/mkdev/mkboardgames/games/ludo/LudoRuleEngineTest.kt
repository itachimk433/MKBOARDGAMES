package com.mkdev.mkboardgames.games.ludo

import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.PieceColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LudoRuleEngineTest {
    private val engine = LudoRuleEngine()

    @Test
    fun extraMoveUsesEffectiveDiceWhenTokenReachesHome() {
        val state = stateWithPiece(progress = LudoSetup.FINISH - 4).copy(
            metadata = stateWithPiece(progress = LudoSetup.FINISH - 4).metadata +
                ("ludo_dice" to 2),
        )
        val move = engine.legalMovesForDice(
            state = state,
            player = 0,
            dice = 4,
            baseDice = 2,
            usedExtraMove = true,
        ).single()

        val next = engine.applyMove(state, move)

        assertEquals(LudoSetup.FINISH, LudoSetup.allPieces(next)
            .first { it.player == 0 && it.token == 0 }.progress)
        assertEquals(PieceColor.BLACK, next.currentTurn)
        assertEquals(GameStatus.IN_PROGRESS, next.status)
    }

    @Test
    fun extraMoveDoesNotAcceptAStaleOrMismatchedEffectiveRoll() {
        val state = stateWithPiece(progress = 10).copy(
            metadata = stateWithPiece(progress = 10).metadata + ("ludo_dice" to 2),
        )
        val move = engine.legalMovesForDice(
            state = state,
            player = 0,
            dice = 4,
            baseDice = 3,
            usedExtraMove = true,
        ).single()

        val rejected = runCatching { engine.applyMove(state, move) }

        assertEquals(IllegalArgumentException::class, rejected.exceptionOrNull()!!::class)
        assertNotNull(rejected.exceptionOrNull())
    }

    private fun stateWithPiece(progress: Int) =
        LudoSetup.initialState().let { initial ->
            val pieces = LudoSetup.allPieces(initial).map { piece ->
                if (piece.player == 0 && piece.token == 0) piece.copy(progress = progress) else piece
            }
            initial.copy(
                board = LudoSetup.boardFor(pieces),
                metadata = initial.metadata + (LudoSetup.PIECES_METADATA to pieces),
            )
        }
}