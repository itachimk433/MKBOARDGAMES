package com.mkdev.mkboardgames.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChessPuzzleValidatorTest {
    @Test
    fun catalogueContainsFifteenMissingPieceChallenges() {
        assertEquals(15, ChessPuzzleData.all.size)
        assertEquals(15, ChessChallengeCatalogue.size)
        assertEquals((1..15).toList(), ChessPuzzleData.all.map { it.level })
        assertEquals("Three Pieces Short", ChessChallengeCatalogue.titleFor(3))
        assertEquals("Last Piece Standing", ChessChallengeCatalogue.titleFor(15))
    }

    @Test
    fun everyMissingPiecePositionStartsWithWhiteAndHasAValidHint() {
        val issues = ChessPuzzleValidator.validateAll()
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun missingPieceChallengesUseTheResolutionObjective() {
        val objectives = ChessPuzzleData.all.associateBy { it.level }.mapValues { it.value.objective }
        assertEquals(ChallengeCondition.CHECKMATE_WITHIN_LIMIT, objectives.getValue(1).condition)
        assertEquals(ChallengeCondition.CHECKMATE_WITHIN_LIMIT, objectives.getValue(3).condition)
        assertEquals(ChallengeCondition.CHECKMATE_WITHIN_LIMIT, objectives.getValue(15).condition)
        assertEquals(1, ChessPuzzleData.all.first().missingPieces)
        assertTrue(
            ChessPuzzleData.all.zipWithNext().all { (current, next) ->
                next.missingPieces >= current.missingPieces
            },
        )
    }

    @Test
    fun missingPieceChallengesKeepSetupAndWinConditionTogether() {
        ChessPuzzleData.all.forEach { puzzle ->
            assertTrue(puzzle.setup.isNotBlank())
            assertTrue(puzzle.winCondition.contains("checkmate or stalemate"))
            assertEquals(puzzle.title, ChessChallengeCatalogue.titleFor(puzzle.level))
        }
    }
}