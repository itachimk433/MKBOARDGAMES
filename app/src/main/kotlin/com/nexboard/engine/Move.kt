package com.nexboard.engine

/**
 * A single game move.
 *
 * [captures] lists every position whose piece is removed when this move is played
 * (supports multi-jump sequences in Checkers).
 * [promotionType] is set for Chess pawn promotion — the string is game-specific
 * (e.g. "QUEEN" for Chess).
 * [metadata] is an open bag for game-specific flags (castle side, en-passant square, etc.).
 */
data class Move(
    val from: Position,
    val to: Position,
    val captures: List<Position> = emptyList(),
    val promotionType: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    val isCapture: Boolean get() = captures.isNotEmpty()
}
