package com.mkdev.mkboardgames.ui

/**
 * The visual presentations available for a Connect Four board.
 *
 * Canvas remains first so existing matches open with the original rendering.
 * The supplied blue board is the only alternate presentation; the red and
 * green photographs are used neither as boards nor as board-style options.
 */
enum class ConnectFourBoardStyle {
    CANVAS,
    BLUE,
}