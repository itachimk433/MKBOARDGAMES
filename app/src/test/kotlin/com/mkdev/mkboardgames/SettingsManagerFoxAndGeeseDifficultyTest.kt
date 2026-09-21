package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsManagerFoxAndGeeseDifficultyTest {

    @Test
    fun foxAndGeeseHasThreeProgressivelyStrongerSearchLevels() {
        val easy = SettingsManager.foxAndGeeseAiProfileForLevel(0)
        val medium = SettingsManager.foxAndGeeseAiProfileForLevel(1)
        val hard = SettingsManager.foxAndGeeseAiProfileForLevel(2)

        assertTrue(easy.depth < medium.depth && medium.depth < hard.depth)
        assertTrue(
            easy.timeLimitMs < medium.timeLimitMs &&
                medium.timeLimitMs < hard.timeLimitMs,
        )
        assertTrue(
            easy.varietyWindow > medium.varietyWindow &&
                medium.varietyWindow > hard.varietyWindow,
        )
        assertEquals(0, hard.varietyWindow)
    }

    @Test
    fun removedMasterValueClampsToHard() {
        val hard = SettingsManager.foxAndGeeseAiProfileForLevel(2)

        assertEquals(hard, SettingsManager.foxAndGeeseAiProfileForLevel(3))
        assertEquals(hard, SettingsManager.foxAndGeeseAiProfileForLevel(99))
        assertEquals(
            SettingsManager.foxAndGeeseAiProfileForLevel(0),
            SettingsManager.foxAndGeeseAiProfileForLevel(-1),
        )
    }
}