package com.mkdev.mkboardgames.games.xiangqi

import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XiangqiRuleEngineTest {

    private val engine = XiangqiRuleEngine()
    private val red = PieceColor.WHITE
    private val black = PieceColor.BLACK

    private data class Placed(
        val row: Int,
        val col: Int,
        val type: XiangqiPieceType,
        val color: PieceColor,
    )

    private fun red(type: XiangqiPieceType, row: Int, col: Int) =
        Placed(row, col, type, PieceColor.WHITE)

    private fun black(type: XiangqiPieceType, row: Int, col: Int) =
        Placed(row, col, type, PieceColor.BLACK)

    private fun position(
        turn: PieceColor = PieceColor.WHITE,
        vararg pieces: Placed,
    ): GameState {
        val size = XiangqiSetup.STORAGE_SIZE
        val board = arrayOfNulls<Piece>(size * size)
        for (p in pieces) {
            board[p.row * size + p.col] = XiangqiPiece(p.type, p.color)
        }
        return GameState(board = board, boardSize = size, currentTurn = turn)
    }

    private fun generals() = arrayOf(
        red(XiangqiPieceType.GENERAL, 9, 4),
        black(XiangqiPieceType.GENERAL, 0, 3),
    )

    private fun movesFrom(state: GameState, row: Int, col: Int): List<Move> =
        engine.legalMovesFrom(state, Position(row, col))

    private fun destinations(moves: List<Move>): Set<Position> =
        moves.map { it.to }.toSet()

    @Test
    fun generalMovesOnlyInsidePalace() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 7, 3),
            black(XiangqiPieceType.GENERAL, 0, 5),
        )
        val dests = destinations(movesFrom(state, 7, 3))

        assertFalse(Position(7, 2) in dests)
        assertFalse(Position(6, 3) in dests)
        assertTrue(Position(8, 3) in dests)
        assertTrue(Position(7, 4) in dests)
    }

    @Test
    fun generalsMayNotFaceThroughEmptyFile() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 4),
            black(XiangqiPieceType.GENERAL, 0, 4),
            red(XiangqiPieceType.CHARIOT, 5, 4),
        )
        val dests = destinations(movesFrom(state, 5, 4))

        assertFalse(dests.any { it.col != 4 })
        assertTrue(dests.all { it.col == 4 })
    }

    @Test
    fun generalMoveThatCreatesFacingGeneralsIsIllegal() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 3),
            black(XiangqiPieceType.GENERAL, 0, 4),
        )

        assertFalse(Position(9, 4) in destinations(movesFrom(state, 9, 3)))
    }

    @Test
    fun advisorMovesDiagonallyInsidePalaceOnly() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.ADVISOR, 8, 4),
        )
        val dests = destinations(movesFrom(state, 8, 4))

        assertEquals(
            setOf(Position(7, 3), Position(7, 5), Position(9, 3), Position(9, 5)),
            dests,
        )
    }

    @Test
    fun advisorCannotLeavePalace() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.ADVISOR, 7, 3),
        )
        val dests = destinations(movesFrom(state, 7, 3))

        assertFalse(Position(6, 2) in dests)
        assertFalse(Position(8, 2) in dests)
        assertTrue(Position(8, 4) in dests)
    }

    @Test
    fun elephantCannotCrossRiver() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.ELEPHANT, 5, 2),
        )
        val dests = destinations(movesFrom(state, 5, 2))

        assertTrue(dests.none { it.row < 5 })
        assertTrue(Position(7, 0) in dests)
        assertTrue(Position(7, 4) in dests)
    }

    @Test
    fun elephantBlockedWhenEyeIsOccupied() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.ELEPHANT, 9, 2),
            red(XiangqiPieceType.SOLDIER, 8, 3),
        )
        val dests = destinations(movesFrom(state, 9, 2))

        assertFalse(Position(7, 4) in dests)
        assertTrue(Position(7, 0) in dests)
    }

    @Test
    fun horseBlockedByLeg() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.HORSE, 6, 4),
            red(XiangqiPieceType.SOLDIER, 5, 4),
        )
        val dests = destinations(movesFrom(state, 6, 4))

        assertFalse(Position(4, 3) in dests)
        assertFalse(Position(4, 5) in dests)
        assertTrue(Position(8, 3) in dests || Position(8, 5) in dests)
    }

    @Test
    fun chariotCannotJumpOverPiece() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.CHARIOT, 5, 0),
            red(XiangqiPieceType.SOLDIER, 3, 0),
            black(XiangqiPieceType.SOLDIER, 1, 0),
        )
        val dests = destinations(movesFrom(state, 5, 0))

        assertTrue(Position(4, 0) in dests)
        assertFalse(Position(3, 0) in dests)
        assertFalse(Position(2, 0) in dests)
        assertFalse(Position(1, 0) in dests)
    }

    @Test
    fun cannonCannotCaptureWithoutScreen() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.CANNON, 7, 0),
            black(XiangqiPieceType.SOLDIER, 3, 0),
        )
        val moves = movesFrom(state, 7, 0)

        assertFalse(moves.any { it.to == Position(3, 0) })
        assertTrue(Position(4, 0) in destinations(moves))
    }

    @Test
    fun cannonCapturesWithExactlyOneScreen() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.CANNON, 7, 0),
            red(XiangqiPieceType.SOLDIER, 5, 0),
            black(XiangqiPieceType.SOLDIER, 3, 0),
        )
        val capture = movesFrom(state, 7, 0).firstOrNull { it.to == Position(3, 0) }

        assertTrue(capture != null && capture.isCapture)
    }

    @Test
    fun cannonCannotCaptureWithTwoScreens() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.CANNON, 8, 0),
            red(XiangqiPieceType.SOLDIER, 6, 0),
            black(XiangqiPieceType.SOLDIER, 5, 0),
            black(XiangqiPieceType.SOLDIER, 2, 0),
        )
        val moves = movesFrom(state, 8, 0)

        assertFalse(moves.any { it.to == Position(2, 0) })
    }

    @Test
    fun soldierMovesOnlyForwardBeforeCrossingRiver() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.SOLDIER, 6, 4),
        )
        assertEquals(setOf(Position(5, 4)), destinations(movesFrom(state, 6, 4)))
    }

    @Test
    fun soldierMaySidestepAfterCrossingRiver() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.SOLDIER, 4, 4),
        )
        assertEquals(
            setOf(Position(3, 4), Position(4, 3), Position(4, 5)),
            destinations(movesFrom(state, 4, 4)),
        )
    }

    @Test
    fun soldierMayNotMoveBackward() {
        val state = position(
            PieceColor.WHITE,
            *generals(),
            red(XiangqiPieceType.SOLDIER, 4, 4),
        )
        assertFalse(Position(5, 4) in destinations(movesFrom(state, 4, 4)))
    }

    @Test
    fun moveExposingOwnGeneralToCheckIsIllegal() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 4),
            black(XiangqiPieceType.GENERAL, 0, 3),
            red(XiangqiPieceType.CHARIOT, 7, 4),
            black(XiangqiPieceType.CHARIOT, 2, 4),
        )
        val dests = destinations(movesFrom(state, 7, 4))

        assertTrue(dests.all { it.col == 4 })
        assertFalse(Position(7, 0) in dests)
    }

    @Test
    fun moveThatGivesCheckIsLegal() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 3),
            black(XiangqiPieceType.GENERAL, 0, 4),
            red(XiangqiPieceType.CHARIOT, 5, 0),
        )
        val checking = movesFrom(state, 5, 0).firstOrNull { it.to == Position(5, 4) }

        assertTrue(checking != null)
        val next = engine.applyMove(state, checking!!)
        assertTrue(engine.isInCheck(next, black))
    }

    @Test
    fun capturingGeneralIsNeverLegal() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 3),
            black(XiangqiPieceType.GENERAL, 0, 4),
            red(XiangqiPieceType.CHARIOT, 5, 4),
        )
        val all = engine.allLegalMoves(state, red)

        assertTrue(all.none { it.to == Position(0, 4) })
        assertTrue(all.none { move ->
            move.captures.any {
                (state.get(it) as? XiangqiPiece)?.type == XiangqiPieceType.GENERAL
            }
        })
    }

    @Test
    fun stalemateIsALossEvenWhenTheGeneralIsNotInCheck() {
        val state = position(
            PieceColor.WHITE,
            red(XiangqiPieceType.GENERAL, 9, 4),
            red(XiangqiPieceType.ADVISOR, 9, 3),
            red(XiangqiPieceType.ADVISOR, 9, 5),
            red(XiangqiPieceType.SOLDIER, 8, 4),
            red(XiangqiPieceType.ADVISOR, 7, 4),
            red(XiangqiPieceType.ADVISOR, 8, 3),
            red(XiangqiPieceType.ADVISOR, 8, 5),
            black(XiangqiPieceType.GENERAL, 0, 3),
        )

        assertFalse(engine.isInCheck(state, red))
        assertTrue(engine.allLegalMoves(state, red).isEmpty())
        assertEquals(GameStatus.BLACK_WINS, engine.gameStatus(state))
    }

    @Test
    fun difficultyProfilesIncreaseInStrength() {
        val easy = SettingsManager.xiangqiAiProfileForLevel(0)
        val medium = SettingsManager.xiangqiAiProfileForLevel(1)
        val hard = SettingsManager.xiangqiAiProfileForLevel(2)

        assertTrue(easy.depth < medium.depth && medium.depth < hard.depth)
        assertTrue(easy.timeLimitMs < medium.timeLimitMs && medium.timeLimitMs < hard.timeLimitMs)
        assertTrue(easy.quiesceDepth < medium.quiesceDepth && medium.quiesceDepth < hard.quiesceDepth)
        assertTrue(easy.varietyWindow > medium.varietyWindow && medium.varietyWindow > hard.varietyWindow)
        assertEquals(0, hard.varietyWindow)
    }

    @Test
    fun levelThreeIsClampedToHardAndCreatesNoMasterProfile() {
        val hard = SettingsManager.xiangqiAiProfileForLevel(2)

        assertEquals(hard, SettingsManager.xiangqiAiProfileForLevel(3))
        assertEquals(hard, SettingsManager.xiangqiAiProfileForLevel(99))
        assertEquals(
            SettingsManager.xiangqiAiProfileForLevel(0),
            SettingsManager.xiangqiAiProfileForLevel(-5),
        )
    }
}