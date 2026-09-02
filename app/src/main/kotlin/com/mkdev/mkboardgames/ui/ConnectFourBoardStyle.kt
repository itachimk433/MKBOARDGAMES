package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Connect Four board.
 *
 * Canvas remains first so existing matches open with the original rendering.
 * Tapping the board-style switch advances through the three supplied boards.
 */
enum class ConnectFourBoardStyle {
    CANVAS,
    RED,
    BLUE,
    GREEN,
}