package com.mkdev.mkboardgames.ui

/**
 * The mathematical sans-serif bold italic glyphs used for selectable options.
 *
 * These are Unicode characters rather than a device-dependent font, so the
 * option labels keep the same visual treatment across Android installations.
 */
internal fun String.asOptionItalicText(): String = buildString(length) {
    for (character in this@asOptionItalicText) {
        when {
            character in 'A'..'Z' -> appendCodePoint(0x1D63C + (character - 'A'))
            character in 'a'..'z' -> appendCodePoint(0x1D656 + (character - 'a'))
            else -> append(character)
        }
    }
}