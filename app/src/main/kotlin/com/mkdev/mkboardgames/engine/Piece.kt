package com.mkdev.mkboardgames.engine

enum class PieceColor {
    WHITE, BLACK;
    fun opponent() = if (this == WHITE) BLACK else WHITE
}

/** Base class for all board game pieces. */
abstract class Piece(open val color: PieceColor) {
    abstract fun symbol(): String   // Unicode glyph or text for Canvas rendering
    abstract fun value(): Int       // Material value for AI evaluation
}
