package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertTrue

class SettingsManagerAmazonsDifficultyTest {
    @Test
    fun amazonsDifficultyProfilesScaleSearchStrength() {
        val easy = SettingsManager.amazonsAiProfileForLevel(0)
        val medium = SettingsManager.amazonsAiProfileForLevel(1)
        val hard = SettingsManager.amazonsAiProfileForLevel(2)

        assertTrue(easy.depth >= 2)
        assertTrue(easy.depth < medium.depth)
        assertTrue(medium.depth < hard.depth)
        assertTrue(easy.timeLimitMs < medium.timeLimitMs)
        assertTrue(medium.timeLimitMs < hard.timeLimitMs)
    }
}