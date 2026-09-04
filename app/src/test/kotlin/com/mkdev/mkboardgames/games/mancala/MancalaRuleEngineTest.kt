package com.mkdev.mkboardgames.games.mancala

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

class MancalaRuleEngineTest {
    private val engine = MancalaRuleEngine()

    @Test
    fun initialPositionHasFourStonesInEveryPit() {
        val state = engine.initialState()

        assertEquals(PieceColor.WHITE, state.currentTurn)
        assertEquals((0 until 14).sumOf { if (it == 6 || it == 13) 0 else 4 },
            (0 until 14).sumOf { engine.stones(state, it) })
        assertEquals(0, engine.stones(state, 6))
        assertEquals(0, engine.stones(state, 13))
        (0 until 6).forEach { assertEquals(4, engine.stones(state, it)) }
        (7 until 13).forEach { assertEquals(4, engine.stones(state, it)) }
    }

    @Test
    fun eachSideSkipsTheOpponentsStoreWhenSowing() {
        val southState = stateWith(intArrayOf(0, 0, 0, 0, 0, 7, 0, 1, 0, 0, 0, 0, 0, 0), PieceColor.WHITE)
        val southResult = engine.applyMove(southState, moveFrom(5))

        assertEquals(1, engine.stones(southResult, 6))
        assertEquals(1, engine.stones(southResult, 0))
        assertEquals(0, engine.stones(southResult, 13))

        val northState = stateWith(intArrayOf(1, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0), PieceColor.BLACK)
        val northResult = engine.applyMove(northState, moveFrom(7))

        assertEquals(1, engine.stones(northResult, 13))
        assertEquals(1, engine.stones(northResult, 0))
        assertEquals(0, engine.stones(northResult, 6))
    }

    @Test
    fun landingInOwnEmptyPitCapturesTheOppositePit() {
        val state = stateWith(
            intArrayOf(1, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0, 3, 0, 0),
            PieceColor.WHITE,
        )

        val result = engine.applyMove(state, moveFrom(0))

        assertEquals(4, engine.stones(result, 6))
        assertEquals(0, engine.stones(result, 1))
        assertEquals(0, engine.stones(result, 11))
        assertEquals(1, engine.stones(result, 5))
        assertEquals(1, result.metadata["lastLanding"])
        assertEquals(4, result.metadata["captured"])
        assertEquals(11, result.metadata["capturedFrom"])
        assertEquals(6, result.metadata["captureStore"])
    }

    @Test
    fun captureDoesNotHappenWhenOppositePitIsEmpty() {
        val state = stateWith(
            intArrayOf(1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0),
            PieceColor.WHITE,
        )

        val result = engine.applyMove(state, moveFrom(0))

        assertEquals(0, engine.stones(result, 6))
        assertEquals(1, engine.stones(result, 1))
        assertEquals(0, result.metadata["captured"])
    }

    @Test
    fun emptySideSweepsTheRemainingStonesAndEndsTheGame() {
        val state = stateWith(
            intArrayOf(0, 0, 0, 0, 0, 1, 0, 2, 0, 0, 0, 0, 0, 0),
            PieceColor.WHITE,
        )

        val result = engine.applyMove(state, moveFrom(5))

        assertEquals(1, engine.stones(result, 6))
        assertEquals(2, engine.stones(result, 13))
        assertEquals(GameStatus.BLACK_WINS, result.status)
        (0 until 6).forEach { assertEquals(0, engine.stones(result, it)) }
        (7 until 13).forEach { assertEquals(0, engine.stones(result, it)) }
    }

    @Test
    fun forwardStoreRouteSkipsOnlyTheOpponentsStore() {
        val southRoute = engine.forwardPath(11, MancalaRuleEngine.SOUTH_STORE, PieceColor.WHITE)
        val northRoute = engine.forwardPath(1, MancalaRuleEngine.NORTH_STORE, PieceColor.BLACK)

        assertEquals(11, southRoute.first())
        assertEquals(6, southRoute.last())
        assertFalse(MancalaRuleEngine.NORTH_STORE in southRoute)
        assertTrue(southRoute.indexOf(6) > southRoute.indexOf(11))

        assertEquals(1, northRoute.first())
        assertEquals(13, northRoute.last())
        assertFalse(MancalaRuleEngine.SOUTH_STORE in northRoute)
        assertTrue(northRoute.indexOf(13) > northRoute.indexOf(1))
    }

    @Test
    fun capturedStonesTravelDirectlyToTheMoverStore() {
        assertEquals(
            listOf(11, MancalaRuleEngine.SOUTH_STORE),
            engine.capturePath(11, MancalaRuleEngine.SOUTH_STORE, PieceColor.WHITE),
        )
        assertEquals(
            listOf(1, MancalaRuleEngine.NORTH_STORE),
            engine.capturePath(1, MancalaRuleEngine.NORTH_STORE, PieceColor.BLACK),
        )
    }

    @Test
    fun fullForwardRouteVisitsEachCellBeforeItsDestination() {
        assertEquals(
            listOf(3, 4, 5, MancalaRuleEngine.SOUTH_STORE),
            engine.pathBetween(3, MancalaRuleEngine.SOUTH_STORE, PieceColor.WHITE),
        )
        assertEquals(
            listOf(10, 11, 12, MancalaRuleEngine.NORTH_STORE, 0, 1, 2),
            engine.pathBetween(10, 2, PieceColor.BLACK),
        )
        assertEquals(
            listOf(5, 6, 7, 8, 9, 10, 11, 12, 0, 1, 2, 3, 4, 5),
            engine.pathBetween(5, 5, PieceColor.WHITE, minimumSteps = 13),
        )
    }

    private fun moveFrom(index: Int) = Move(Position(0, index), Position(0, index))

    private fun stateWith(counts: IntArray, turn: PieceColor): GameState {
        require(counts.size == MancalaRuleEngine.BOARD_CELLS)
        return GameState(
            board = Array<Piece?>(MancalaRuleEngine.BOARD_CELLS) { index ->
                val owner = if (index <= MancalaRuleEngine.SOUTH_STORE) {
                    PieceColor.WHITE
                } else {
                    PieceColor.BLACK
                }
                MancalaPitPiece(
                    color = owner,
                    stones = counts[index],
                    store = index == MancalaRuleEngine.SOUTH_STORE ||
                        index == MancalaRuleEngine.NORTH_STORE,
                )
            },
            boardSize = MancalaRuleEngine.BOARD_CELLS,
            currentTurn = turn,
        )
    }
}