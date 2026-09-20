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
        assertEquals(-1, easyProfile.varietyWindowOverride)
        assertEquals(-1, mediumProfile.varietyWindowOverride)
        assertEquals(0, hardProfile.varietyWindowOverride)
        assertEquals(0, masterProfile.varietyWindowOverride)

        val hardEthereal = SettingsManager.chessEtherealProfileForLevel(2)
        val masterEthereal = SettingsManager.chessEtherealProfileForLevel(3)
        assertTrue(hardEthereal.timeLimitMs < masterEthereal.timeLimitMs)
        assertTrue(hardEthereal.hashMb < masterEthereal.hashMb)

        val masterStockfish = SettingsManager.chessStockfishProfileForLevel(3)
        assertEquals(5_000L, masterStockfish.timeLimitMs)
        assertEquals(64, masterStockfish.hashMb)
    }
}
