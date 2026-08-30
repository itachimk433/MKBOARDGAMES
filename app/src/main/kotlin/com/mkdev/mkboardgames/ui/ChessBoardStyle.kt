package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Chess board.
 *
 * The order is also the order used by the compact style switch: tapping it
 * advances through each presentation and wraps back to the first.
 */
enum class ChessBoardStyle {
    CANVAS,
    CLASSIC_WOOD,
    SUPPLIED_WOOD,
    REALISTIC_BLACK_WHITE,
    BLACK_WHITE,
    RED_BLACK,
}