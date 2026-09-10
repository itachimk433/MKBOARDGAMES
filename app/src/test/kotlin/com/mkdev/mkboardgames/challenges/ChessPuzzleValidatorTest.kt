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
}