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
    fun authoredObjectivesIncludeThePromotionVariants() {
        val objectives = ChessPuzzleData.all.associateBy { it.level }
        assertTrue(objectives.getValue(31).objective.promotionRequirement == PromotionRequirement.KNIGHT)
        assertTrue(objectives.getValue(98).objective.promotionRequirement == PromotionRequirement.KNIGHT)
    }

    @Test
    fun catalogueIncludesAuthoredTrapAndLongMateObjectives() {
        val puzzles = ChessPuzzleData.all.associateBy { it.level }
        assertTrue(puzzles.getValue(39).condition == ChallengeCondition.SET_TRAP)
        assertTrue(puzzles.getValue(100).objective.targetPlayerMoves == 10)
        assertTrue(puzzles.getValue(100).mateIn == 10)
    }

    @Test
    fun catalogueMatchesTheAuthoredChallengeCount() {
        assertTrue(ChessChallengeCatalogue.size == 100)
        assertTrue(ChessChallengeCatalogue.titleFor(100) == "The Grandmaster Immortal Test")
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