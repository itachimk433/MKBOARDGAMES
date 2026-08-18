package com.mkdev.mkboardgames.games.go

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

/**
 * The Go board is added in stages. This first stage owns the 13×13 empty
 * position so the game can use the same mode dialog and board shell as Chess.
 */
object GoSetup {
    const val BOARD_SIZE = 13

    fun initialState(): GameState = GameState(
        board = arrayOfNulls(BOARD_SIZE * BOARD_SIZE),
        boardSize = BOARD_SIZE,
        currentTurn = PieceColor.WHITE,
    )
}

data class GoPiece(override val color: PieceColor) : Piece(color) {
    override fun symbol(): String = "●"
    override fun value(): Int = 1
}

class GoRuleEngine : RuleEngine {
    override fun initialState(): GameState = GoSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> = emptyList()

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> = emptyList()

    override fun applyMove(state: GameState, move: Move): GameState = state

    override fun gameStatus(state: GameState): GameStatus = GameStatus.IN_PROGRESS

    override fun evaluate(state: GameState): Int = 0
}