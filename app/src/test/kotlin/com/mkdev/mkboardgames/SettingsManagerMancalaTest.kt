package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsManagerMancalaTest {
    @Test
    fun mancalaDifficultyProfilesUseIncreasingSearchDepths() {
        val easyProfile = SettingsManager.mancalaAiProfileForLevel(0)
        val mediumProfile = SettingsManager.mancalaAiProfileForLevel(1)
        val hardProfile = SettingsManager.mancalaAiProfileForLevel(2)

        assertEquals(2, easyProfile.depth)
        assertTrue(mediumProfile.depth > easyProfile.depth)
        assertTrue(hardProfile.depth > mediumProfile.depth)
    }
}