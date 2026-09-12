package com.mkdev.mkboardgames.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChessPuzzleValidatorTest {
    @Test
    fun catalogueContainsTenMissingPieceChallenges() {
        assertEquals(10, ChessPuzzleData.all.size)
        assertEquals(10, ChessChallengeCatalogue.size)
        assertEquals((1..10).toList(), ChessPuzzleData.all.map { it.level })
        assertEquals("Three Pieces Short", ChessChallengeCatalogue.titleFor(3))
        assertEquals("Queen's Final Test", ChessChallengeCatalogue.titleFor(10))
    }

    @Test
    fun everyMissingPiecePositionStartsWithWhiteAndHasAValidHint() {
        val issues = ChessPuzzleValidator.validateAll()
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun missingPieceChallengesUseTheResolutionObjective() {
        val objectives = ChessPuzzleData.all.associateBy { it.level }.mapValues { it.value.objective }
        assertTrue((1..10).all {
            objectives.getValue(it).condition == ChallengeCondition.CHECKMATE_OR_STALEMATE &&
                objectives.getValue(it).targetPlayerMoves == null
        })
        assertEquals(2, ChessPuzzleData.all.first().missingPieces)
        assertTrue(
            ChessPuzzleData.all.zipWithNext().all { (current, next) ->
                next.missingPieces >= current.missingPieces
            },
        )
    }

    @Test
    fun requestedStartingMaterialIsEncodedInEachPosition() {
        val fens = ChessPuzzleData.all.associateBy { it.level }.mapValues { it.value.fen }
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/RNB1KBNR w KQkq - 0 1",
            fens.getValue(1),
        )
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RN2K1NR w KQkq - 0 1",
            fens.getValue(2),
        )
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPP2PPP/R1BQKB1R w KQkq - 0 1",
            fens.getValue(3),
        )
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/R2QKBN1 w KQkq - 0 1",
            fens.getValue(4),
        )
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/P2PP2P/R3K1N1 w KQkq - 0 1",
            fens.getValue(5),
        )
        assertEquals(
            "4k3/ppp5/8/8/8/8/PPP5/2B1K1N1 w - - 0 1",
            fens.getValue(6),
        )
        assertEquals(
            "4k3/ppp5/8/8/8/8/PP6/1N2K1N1 w - - 0 1",
            fens.getValue(7),
        )
        assertEquals(
            "4k3/ppp5/8/8/8/8/PP6/R3K2R w - - 0 1",
            fens.getValue(8),
        )
        assertEquals(
            "4k3/ppp5/8/8/8/8/P7/R1B1K3 w - - 0 1",
            fens.getValue(9),
        )
        assertEquals(
            "4k3/ppp5/8/8/8/8/PP6/3QK3 w - - 0 1",
            fens.getValue(10),
        )
    }

    @Test
    fun allChallengesKeepSetupAndWinConditionTogether() {
        ChessPuzzleData.all.forEach { puzzle ->
            assertTrue(puzzle.setup.isNotBlank())
            assertTrue(
                puzzle.winCondition.contains("checkmate or stalemate"),
            )
            assertTrue(!puzzle.winCondition.contains("moves"))
            assertEquals(puzzle.title, ChessChallengeCatalogue.titleFor(puzzle.level))
        }
    }
}