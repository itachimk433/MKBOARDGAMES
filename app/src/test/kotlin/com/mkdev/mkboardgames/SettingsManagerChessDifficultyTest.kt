package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsManagerChessDifficultyTest {
    @Test
    fun chessDifficultyProfilesIncludeMasterAndIncreaseStrength() {
        val easyDepth = SettingsManager.chessAiDepthForTest(0)
        val mediumDepth = SettingsManager.chessAiDepthForTest(1)
        val hardDepth = SettingsManager.chessAiDepthForTest(2)
        val masterDepth = SettingsManager.chessAiDepthForTest(3)

        assertEquals(2, easyDepth)
        assertTrue(mediumDepth >= 4)
        assertTrue(hardDepth > mediumDepth)
        assertTrue(masterDepth > hardDepth)

        val easyTimeMs = SettingsManager.chessAiTimeLimitMsForTest(0)
        val mediumTimeMs = SettingsManager.chessAiTimeLimitMsForTest(1)
        val hardTimeMs = SettingsManager.chessAiTimeLimitMsForTest(2)
        val masterTimeMs = SettingsManager.chessAiTimeLimitMsForTest(3)

        assertTrue(easyTimeMs < mediumTimeMs)
        assertTrue(mediumTimeMs < hardTimeMs)
        assertTrue(hardTimeMs < masterTimeMs)
    }
}
