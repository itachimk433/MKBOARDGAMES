package com.mkdev.mkboardgames.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChessPuzzleValidatorTest {
    @Test
    fun catalogueContainsThirtyChallengesAcrossThreeSections() {
        assertEquals(30, ChessPuzzleData.all.size)
        assertEquals(30, ChessChallengeCatalogue.size)
        assertEquals((1..30).toList(), ChessPuzzleData.all.map { it.level })
        assertEquals("Fast Checkmate Challenge", ChessChallengeCatalogue.titleFor(3))
        assertEquals("Knight Hunter Challenge", ChessChallengeCatalogue.titleFor(10))
    }

    @Test
    fun everyCustomPositionStartsWithWhiteAndHasAValidHint() {
        val issues = ChessPuzzleValidator.validateAll()
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun challengeRulesMatchTheRequestedWinConditions() {
        val objectives = ChessPuzzleData.all.associateBy { it.level }.mapValues { it.value.objective }
        assertEquals(ChallengeCondition.CHECKMATE_WITHIN_LIMIT, objectives.getValue(1).condition)
        assertEquals(39, objectives.getValue(1).targetPlayerMoves)
        assertEquals(ChallengeCondition.NO_QUEEN_USE, objectives.getValue(2).condition)
        assertEquals(ChallengeCondition.CHECKMATE_WITHIN_LIMIT, objectives.getValue(3).condition)
        assertEquals(25, objectives.getValue(3).targetPlayerMoves)
        assertEquals(ChallengeCondition.CASTLE_AND_WIN, objectives.getValue(5).condition)
        assertEquals(ChallengeCondition.ROOK_CHECKMATE, objectives.getValue(7).condition)
        assertEquals(ChallengeCondition.KNIGHT_HUNTER, objectives.getValue(10).condition)
        assertEquals(25, objectives.getValue(10).targetPlayerMoves)
    }

    @Test
    fun challengeBriefsKeepSetupAndWinConditionTogether() {
        ChessBeginnerChallenges.all.forEach { brief ->
            assertTrue(brief.setup.isNotBlank())
            assertTrue(brief.winCondition.isNotBlank())
            assertEquals(brief.title, ChessChallengeCatalogue.titleFor(brief.level))
        }
    }
}