package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Color
import com.mkdev.mkboardgames.SettingsManager

object DifficultyChoices {
    private val symbols = listOf("I", "II", "III", "IV")
    private val accents = listOf("#8EC7B9", "#E3B86A", "#E58A7A", "#D8A7FF")
    private val descriptions = listOf(
        "A relaxed challenge",
        "A balanced challenge",
        "A sharper opponent",
        "An elite challenge",
    )

    fun create(
        context: Context,
        gameTag: String,
        current: Int,
        labels: List<String>,
    ): List<ChessChoiceView.Choice> {
        val highestUnlocked = SettingsManager.highestUnlockedDifficulty(context, gameTag)
            .coerceAtMost(labels.lastIndex)

        return labels.mapIndexed { index, label ->
            val locked = index > highestUnlocked
            val detail = when {
                locked && index == highestUnlocked + 1 ->
                    "Win 2 consecutive games on ${labels[highestUnlocked]} to unlock."
                locked -> "Unlock ${labels[index - 1]} first."
                index == current -> "Current setting"
                else -> descriptions.getOrElse(index) { "Computer strength" }
            }
            val displayLabel = when {
                locked -> "$label · LOCKED"
                index == current -> "$label  ✓"
                else -> label
            }
            ChessChoiceView.Choice(
                label = displayLabel,
                detail = detail,
                symbol = symbols.getOrElse(index) { (index + 1).toString() },
                accent = Color.parseColor(accents.getOrElse(index) { "#A9B6E8" }),
                enabled = !locked,
            )
        }
    }
}