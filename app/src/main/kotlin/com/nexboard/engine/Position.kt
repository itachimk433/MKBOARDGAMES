package com.nexboard.engine

/** Zero-indexed board coordinate. Row 0 = top, Col 0 = left. */
data class Position(val row: Int, val col: Int) {
    operator fun plus(other: Position) = Position(row + other.row, col + other.col)
    fun isValid(size: Int = 8) = row in 0 until size && col in 0 until size
    override fun toString() = "${'A' + col}${8 - row}"
}
