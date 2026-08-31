package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for an Othello board.
 *
 * The generated Canvas board stays first so existing games open unchanged;
 * the supplied green-felt photograph is the second toggle state.
 */
enum class OthelloBoardStyle {
    CANVAS,
    GREEN_FELT,
}