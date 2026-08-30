package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Draughts board.
 *
 * Draughts keeps the generated board as its default and exposes the supplied
 * red-and-black board as its second toggle position.
 */
enum class DraughtsBoardStyle {
    CANVAS,
    RED_BLACK,
}