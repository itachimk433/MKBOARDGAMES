package com.mkdev.mkboardgames.ui

/**
 * The three visual presentations available for a Chess board.
 *
 * The order is also the order used by the compact style switch: tapping it
 * advances from left to middle to right, then wraps back to the left.
 */
enum class ChessBoardStyle {
    CANVAS,
    CLASSIC_WOOD,
    SUPPLIED_WOOD,
}