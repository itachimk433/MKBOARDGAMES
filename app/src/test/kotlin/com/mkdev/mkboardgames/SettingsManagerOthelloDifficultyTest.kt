package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsManagerOthelloDifficultyTest {

    @Test
    fun othelloProfilesIncreaseInStrength() {
        val easy = SettingsManager.othelloAiProfileForLevel(0)
        val medium = SettingsManager.othelloAiProfileForLevel(1)
        val hard = SettingsManager.othelloAiProfileForLevel(2)

        assertTrue(easy.depth < medium.depth && medium.depth < hard.depth)
        assertTrue(easy.timeLimitMs < medium.timeLimitMs && medium.timeLimitMs < hard.timeLimitMs)
        assertTrue(easy.varietyWindow > medium.varietyWindow && medium.varietyWindow > hard.varietyWindow)
        assertEquals(0, hard.varietyWindow)
    }

    @Test
    fun othelloInvalidAndMasterValuesClampToHard() {
        val hard = SettingsManager.othelloAiProfileForLevel(2)

        assertEquals(hard, SettingsManager.othelloAiProfileForLevel(3))
        assertEquals(hard, SettingsManager.othelloAiProfileForLevel(99))
        assertEquals(
            SettingsManager.othelloAiProfileForLevel(0),
            SettingsManager.othelloAiProfileForLevel(-1),
        )
    }

    @Test
    fun hardProfilePreservesPreviousOthelloSearchBudget() {
        val hard = SettingsManager.othelloAiProfileForLevel(2)

        assertEquals(7, hard.depth)
        assertEquals(3000L, hard.timeLimitMs)
    }
}