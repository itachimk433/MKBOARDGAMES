package com.mkdev.mkboardgames.ui

enum class FiveFieldKonoBoardStyle(
    val assetName: String,
    val title: String,
    val detail: String,
) {
    WOOD(
        assetName = "five_field_kono_board_wood.png",
        title = "Classic wood",
        detail = "Warm natural grain",
    ),
    BLACK_AND_WHITE(
        assetName = "five_field_kono_board_light.png",
        title = "Black & white",
        detail = "Clean monochrome",
    ),
}
