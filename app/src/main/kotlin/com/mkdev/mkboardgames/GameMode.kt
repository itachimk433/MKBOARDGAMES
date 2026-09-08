package com.mkdev.mkboardgames

/**
 * The ruleset context selected before opening the game catalogue.
 *
 * Both modes intentionally use the same game catalogue and activities. The
 * extra gives each match a stable context for future irregular-mode systems
 * such as coins and abilities without changing normal gameplay.
 */
enum class GameMode {
    NORMAL,
    IRREGULAR;

    companion object {
        const val EXTRA_MODE = "com.mkdev.mkboardgames.GAME_MODE"

        fun fromName(value: String?): GameMode =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}