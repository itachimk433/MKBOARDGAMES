package com.mkdev.mkboardgames.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChessPuzzleValidatorTest {
    @Test
    fun catalogueContainsTwoFifteenChallengeSections() {
        assertEquals(30, ChessPuzzleData.all.size)
        assertEquals(30, ChessChallengeCatalogue.size)
        assertEquals((1..30).toList(), ChessPuzzleData.all.map { it.level })
        assertEquals("Three Pieces Short", ChessChallengeCatalogue.titleFor(3))
        assertEquals("Last Piece Standing", ChessChallengeCatalogue.titleFor(15))
        assertEquals("Check in Three", ChessChallengeCatalogue.titleFor(16))
        assertEquals("Check in Twenty-Five", ChessChallengeCatalogue.titleFor(30))
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
    fun limitedMoveChallengesUseCheckObjectivesCappedAtTwentyFive() {
        val limited = ChessPuzzleData.all.filter { it.level in 16..30 }
        assertEquals(15, limited.size)
        assertTrue(
            limited.all {
                val limit = it.objective.targetPlayerMoves
                it.condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT &&
                    limit != null &&
                    limit in 1..25 &&
                    it.winCondition.contains("check or checkmate")
            },
        )
        assertTrue(
            limited.zipWithNext().all { (current, next) ->
                current.objective.targetPlayerMoves!! < next.objective.targetPlayerMoves!!
            },
        )
    }

    @Test
    fun allChallengesKeepSetupAndWinConditionTogether() {
        ChessPuzzleData.all.forEach { puzzle ->
            assertTrue(puzzle.setup.isNotBlank())
            assertTrue(
                puzzle.winCondition.contains("checkmate or stalemate") ||
                    puzzle.winCondition.contains("check or checkmate"),
            )
            assertEquals(puzzle.title, ChessChallengeCatalogue.titleFor(puzzle.level))
        }
    }
}