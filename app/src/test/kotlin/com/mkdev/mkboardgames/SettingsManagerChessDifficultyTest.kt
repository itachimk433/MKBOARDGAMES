package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsManagerChessDifficultyTest {
    @Test
    fun chessDifficultyProfilesIncludeMasterAndIncreaseStrength() {
        val easyProfile = SettingsManager.chessAiProfileForLevel(0)
        val mediumProfile = SettingsManager.chessAiProfileForLevel(1)
        val hardProfile = SettingsManager.chessAiProfileForLevel(2)
        val masterProfile = SettingsManager.chessAiProfileForLevel(3)

        assertEquals(2, easyProfile.depth)
        assertTrue(mediumProfile.depth >= 4)
        assertTrue(hardProfile.depth > mediumProfile.depth)
        assertTrue(masterProfile.depth > hardProfile.depth)

        assertTrue(easyProfile.timeLimitMs < mediumProfile.timeLimitMs)
        assertTrue(mediumProfile.timeLimitMs < hardProfile.timeLimitMs)
        assertTrue(hardProfile.timeLimitMs < masterProfile.timeLimitMs)
    }
}
