package com.mkdev.mkboardgames

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsManagerDraughtsDifficultyTest {

    @Test
    fun checkersHasOnlyThreeSearchLevels() {
        assertEquals(2, SettingsManager.checkersAiDepthForLevel(0))
        assertEquals(5, SettingsManager.checkersAiDepthForLevel(1))
        assertEquals(8, SettingsManager.checkersAiDepthForLevel(2))
    }

    @Test
    fun checkersInvalidAndMasterValuesClampToHard() {
        assertEquals(8, SettingsManager.checkersAiDepthForLevel(3))
        assertEquals(8, SettingsManager.checkersAiDepthForLevel(99))
        assertEquals(2, SettingsManager.checkersAiDepthForLevel(-1))
    }

    @Test
    fun internationalDraughtsHasOnlyThreeSearchLevels() {
        assertEquals(2, SettingsManager.internationalDraughtsAiDepthForLevel(0))
        assertEquals(5, SettingsManager.internationalDraughtsAiDepthForLevel(1))
        assertEquals(8, SettingsManager.internationalDraughtsAiDepthForLevel(2))
    }

    @Test
    fun internationalDraughtsInvalidAndMasterValuesClampToHard() {
        assertEquals(8, SettingsManager.internationalDraughtsAiDepthForLevel(3))
        assertEquals(8, SettingsManager.internationalDraughtsAiDepthForLevel(99))
        assertEquals(2, SettingsManager.internationalDraughtsAiDepthForLevel(-1))
    }
}