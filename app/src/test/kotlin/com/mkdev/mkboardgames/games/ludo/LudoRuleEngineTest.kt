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

    @Test
    fun sixLaunchesAnyTokenWhenThePlayerHasOnlyYardTokens() {
        val state = LudoSetup.initialState()

        val moves = engine.legalMovesForDice(
            state = state,
            player = 3,
            dice = 6,
        )

        assertEquals(LudoSetup.TOKENS_PER_PLAYER, moves.size)
        assertEquals(setOf(0, 1, 2, 3), moves.map { it.metadata["token"] }.toSet())
        assertEquals(setOf(0), moves.map { it.metadata["targetProgress"] }.toSet())
    }

    @Test
    fun p4SixLaunchesATokenAndKeepsTheTurn() {
        val initial = LudoSetup.initialState()
        val state = initial.copy(
            currentTurn = LudoSetup.colorForPlayer(3),
            metadata = initial.metadata + mapOf(
                "ludo_turn" to 3,
                "ludo_dice" to 6,
                LudoSetup.SIX_STREAK_METADATA to 1,
            ),
        )
        val move = engine.legalMovesForDice(state, player = 3, dice = 6).first()

        val next = engine.applyMove(state, move)

        assertEquals(0, LudoSetup.allPieces(next)
            .first { it.player == 3 && it.token == move.metadata["token"] }.progress)
        assertEquals(3, LudoSetup.playerFromState(next))
        assertEquals(1, next.metadata[LudoSetup.SIX_STREAK_METADATA])
        assertEquals(LudoSetup.colorForPlayer(3), next.currentTurn)
    }

    @Test
    fun p3AndP4CanLaunchOnSixEvenWhenTheirStartHasAnOpposingBlock() {
        listOf(2, 3).forEach { player ->
            val initial = LudoSetup.initialState()
            val start = LudoSetup.trackPosition(player, 0)
            val opponents = listOf(0, 1)
            val pieces = LudoSetup.allPieces(initial).map { piece ->
                when {
                    piece.player == opponents[0] && piece.token == 0 ->
                        piece.copy(progress = progressAt(start, opponents[0]))
                    piece.player == opponents[1] && piece.token == 0 ->
                        piece.copy(progress = progressAt(start, opponents[1]))
                    else -> piece
                }
            }
            val state = initial.copy(
                board = LudoSetup.boardFor(pieces),
                currentTurn = LudoSetup.colorForPlayer(player),
                metadata = initial.metadata + mapOf(
                    "ludo_turn" to player,
                    LudoSetup.PIECES_METADATA to pieces,
                    LudoSetup.SIX_STREAK_METADATA to 1,
                ),
            )

            val moves = engine.legalMovesForDice(state, player, 6)

            assertEquals(LudoSetup.TOKENS_PER_PLAYER, moves.size)
        }
    }

    private fun progressAt(position: com.mkdev.mkboardgames.engine.Position, player: Int): Int =
        (LudoSetup.PATH.indexOf(position) - LudoSetup.startOffset(player) + LudoSetup.PATH_LENGTH) %
            LudoSetup.PATH_LENGTH

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