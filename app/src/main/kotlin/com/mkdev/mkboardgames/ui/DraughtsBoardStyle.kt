package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Draughts board.
 *
 * Draughts keeps the generated board as its default, then follows it with the
 * supplied red-and-black board and the four copied Chess presentations.
 */
enum class DraughtsBoardStyle {
    CANVAS,
    RED_BLACK,
    CLASSIC_WOOD,
    SUPPLIED_WOOD,
    REALISTIC_BLACK_WHITE,
    BLACK_WHITE,
}