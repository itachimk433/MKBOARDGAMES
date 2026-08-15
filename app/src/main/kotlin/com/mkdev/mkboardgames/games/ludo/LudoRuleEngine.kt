package com.mkdev.mkboardgames.games.ludo

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.engine.RuleEngine

class LudoRuleEngine : RuleEngine {

    override fun initialState(): GameState = LudoSetup.initialState()

    fun legalMovesForDice(state: GameState, player: Int, dice: Int): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS || dice !in 1..6) return emptyList()
        val moves = mutableListOf<Move>()
        for (row in 0 until state.boardSize) {
            for (col in 0 until state.boardSize) {
                val from = Position(row, col)
                val piece = state.get(from) as? LudoPiece ?: continue
                if (piece.player != player || piece.progress >= LudoSetup.FINISH) continue

                val targetProgress = when {
                    piece.progress < 0 && dice == 6 -> 0
                    piece.progress < 0 -> null
                    piece.progress + dice <= LudoSetup.FINISH -> piece.progress + dice
                    else -> null
                } ?: continue

                val to = when {
                    targetProgress < 0 -> LudoSetup.yardPosition(player, piece.token)
                    targetProgress < 52 -> LudoSetup.trackPosition(player, targetProgress)
                    targetProgress < LudoSetup.FINISH ->
                        LudoSetup.homeLanePosition(player, targetProgress)
                    else -> LudoSetup.finishPosition(player, piece.token)
                }

                val captures = if (targetProgress in 0 until 52) {
                    val occupant = state.get(to) as? LudoPiece
                    if (occupant != null && occupant.player != player && !isSafeTrackCell(to)) {
                        listOf(to)
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                moves += Move(
                    from = from,
                    to = to,
                    captures = captures,
                    metadata = mapOf(
                        "dice" to dice,
                        "player" to player,
                        "token" to piece.token,
                        "targetProgress" to targetProgress
                    )
                )
            }
        }
        return moves
    }

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val player = LudoSetup.playerFromState(state)
        val dice = state.metadata["ludo_dice"] as? Int ?: 0
        return legalMovesForDice(state, player, dice).filter { it.from == position }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val player = LudoSetup.playerFromState(state)
        return if (LudoSetup.colorForPlayer(player) == color) {
            legalMovesForDice(state, player, state.metadata["ludo_dice"] as? Int ?: 0)
        } else {
            emptyList()
        }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        val movingPiece = state.get(move.from) as? LudoPiece ?: return state
        val player = (move.metadata["player"] as? Int) ?: movingPiece.player
        val dice = (move.metadata["dice"] as? Int) ?: 0
        val targetProgress = (move.metadata["targetProgress"] as? Int)
            ?: inferTargetProgress(movingPiece, move.to)

        val nextBoard = state.board.copyOf()
        nextBoard[LudoSetup.indexOf(move.from)] = null
        for (capture in move.captures) nextBoard[LudoSetup.indexOf(capture)] = null

        val movedPiece = movingPiece.copy(progress = targetProgress)
        nextBoard[LudoSetup.indexOf(move.to)] = movedPiece

        val hasWon = (0 until LudoSetup.TOKENS_PER_PLAYER).all { token ->
            findPiece(nextBoard, player, token)?.progress == LudoSetup.FINISH
        }
        val status = if (hasWon) {
            if (player % 2 == 0) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS
        } else {
            GameStatus.IN_PROGRESS
        }

        val nextPlayer = if (status != GameStatus.IN_PROGRESS || dice == 6) {
            player
        } else {
            (player + 1) % LudoSetup.PLAYER_COUNT
        }
        val metadata = mutableMapOf<String, Any>(
            "ludo_turn" to nextPlayer,
            "ludo_dice" to 0
        )
        if (hasWon) metadata["ludo_winner"] = player

        return state.copy(
            board = nextBoard,
            currentTurn = LudoSetup.colorForPlayer(nextPlayer),
            status = status,
            moveHistory = state.moveHistory + move,
            metadata = metadata
        )
    }

    override fun gameStatus(state: GameState): GameStatus = state.status

    override fun evaluate(state: GameState): Int {
        val winner = state.metadata["ludo_winner"] as? Int ?: return 0
        return if (winner % 2 == 0) 100_000 else -100_000
    }

    private fun inferTargetProgress(piece: LudoPiece, to: Position): Int {
        if (to == LudoSetup.finishPosition(piece.player, piece.token)) return LudoSetup.FINISH
        for (progress in 0 until 52) {
            if (to == LudoSetup.trackPosition(piece.player, progress)) return progress
        }
        for (progress in 52 until LudoSetup.FINISH) {
            if (to == LudoSetup.homeLanePosition(piece.player, progress)) return progress
        }
        return piece.progress
    }

    private fun findPiece(board: Array<Piece?>, player: Int, token: Int): LudoPiece? =
        board.filterIsInstance<LudoPiece>().firstOrNull { it.player == player && it.token == token }

    private fun isSafeTrackCell(position: Position): Boolean =
        position in LudoSetup.PATH.filterIndexed { index, _ -> index % 13 == 0 }
}