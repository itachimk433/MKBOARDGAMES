package com.mkdev.mkboardgames.engine

/**
 * Contract every board game rule set must satisfy.
 * Each game (Chess, Checkers, …) provides its own implementation.
 */
interface RuleEngine {
    /** All legal moves for the piece at [position] in the current [state]. */
    fun legalMovesFrom(state: GameState, position: Position): List<Move>

    /** All legal moves for [color] across the whole board. */
    fun allLegalMoves(state: GameState, color: PieceColor): List<Move>

    /** Apply [move] to [state] and return the resulting state. */
    fun applyMove(state: GameState, move: Move): GameState

    /** Compute game status (win / draw / in-progress) for [state]. */
    fun gameStatus(state: GameState): GameStatus

    /**
     * Static evaluation score from WHITE's perspective.
     * Positive = White advantage, negative = Black advantage.
     * Used by the AI minimax search.
     */
    fun evaluate(state: GameState): Int

    /** Return a fresh starting [GameState] for this game. */
    fun initialState(): GameState
}
