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
        assertTrue(objectives.getValue(98).objective.promotionRequirement == PromotionRequirement.QUEEN)
        assertTrue(objectives.getValue(99).objective.promotionRequirement == PromotionRequirement.ROOK)
        assertTrue(objectives.getValue(100).objective.promotionRequirement == PromotionRequirement.KNIGHT)
        assertTrue(objectives.getValue(101).objective.promotionRequirement == PromotionRequirement.QUEEN)
    }

    @Test
    fun catalogueIncludesAuthoredTrapAndLongMateObjectives() {
        val puzzles = ChessPuzzleData.all.associateBy { it.level }
        assertTrue(puzzles.getValue(39).condition == ChallengeCondition.SET_TRAP)
        assertTrue(puzzles.getValue(102).objective.targetPlayerMoves == 10)
        assertTrue(puzzles.getValue(103).objective.targetPlayerMoves == 11)
        assertTrue(puzzles.getValue(104).objective.targetPlayerMoves == 20)
        assertTrue(puzzles.getValue(104).mateIn == 20)
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