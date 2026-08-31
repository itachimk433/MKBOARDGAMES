package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Draughts board.
 *
 * Draughts keeps the generated board as its default, then follows it with the
 * supplied red-and-black board, the copied Chess presentations, and the two
 * uploaded International Draughts wood boards.
 */
enum class DraughtsBoardStyle {
    CANVAS,
    RED_BLACK,
    CLASSIC_WOOD,
    SUPPLIED_WOOD,
    REALISTIC_BLACK_WHITE,
    BLACK_WHITE,
    INTERNATIONAL_DARK_WOOD,
    INTERNATIONAL_LIGHT_WOOD,
}