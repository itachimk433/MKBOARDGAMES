package com.mkdev.mkboardgames.challenges

import kotlin.test.Test
import kotlin.test.assertTrue

class ChessPuzzleValidatorTest {
    @Test
    fun everyChallengeHasCoverageAndAValidCheckmateLine() {
        val issues = ChessPuzzleValidator.validateAll()
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun authoredObjectivesIncludeThePromotionLessons() {
        val objectives = ChessPuzzleData.all.associateBy { it.level }
        assertTrue(objectives.getValue(12).objective.promotionRequirement == PromotionRequirement.QUEEN)
        assertTrue(objectives.getValue(25).objective.promotionRequirement == PromotionRequirement.QUEEN)
    }

    @Test
    fun catalogueIncludesAuthoredTacticalObjectives() {
        val puzzles = ChessPuzzleData.all.associateBy { it.level }
        assertTrue(puzzles.getValue(3).condition == ChallengeCondition.FORK)
        assertTrue(puzzles.getValue(25).objective.targetPlayerMoves == 2)
        assertTrue(puzzles.getValue(25).mateIn == 2)
    }

    @Test
    fun catalogueMatchesTheAuthoredChallengeCount() {
        assertTrue(ChessChallengeCatalogue.size == 25)
        assertTrue(ChessChallengeCatalogue.titleFor(25) == "Opposition Basics")
    }

    @Test
    fun beginnerCurriculumContainsAllTwentyFiveLevelsAndStartingSetup() {
        assertTrue(ChessBeginnerChallenges.all.size == 25)
        assertTrue(ChessBeginnerChallenges.all.map { it.level } == (1..25).toList())
        assertTrue(ChessBeginnerChallenges.standardInitialSetup.contains("rooks a1/h1"))
        assertTrue(ChessBeginnerChallenges.standardInitialSetup.contains("king e8"))
        assertTrue(ChessBeginnerChallenges.standardInitialCoordinates["white.pawns"]?.size == 8)
        assertTrue(ChessBeginnerChallenges.standardInitialCoordinates["black.pawns"]?.contains("h7") == true)
        assertTrue(ChessPuzzleData.all.take(25).all { it.beginnerChallenge != null })
    }

    @Test
    fun everyPuzzleHasAtLeastOnePlayableAuthoredLine() {
        ChessPuzzleData.all.forEach { puzzle ->
            assertTrue(
                ChessPuzzleValidator.playableSolutionLines(puzzle).isNotEmpty(),
                "level ${puzzle.level} has no playable authored line",
            )
        }
    }
}