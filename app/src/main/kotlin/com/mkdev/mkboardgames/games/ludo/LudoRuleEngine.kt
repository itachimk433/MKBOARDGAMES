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

    fun legalMovesForDice(
        state: GameState,
        player: Int,
        dice: Int,
        baseDice: Int = dice,
        usedExtraMove: Boolean = false,
    ): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS || dice !in 1..8) return emptyList()
        val sixStreak = state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0
        if (dice == 6 && sixStreak >= 3) return emptyList()

        val moves = mutableListOf<Move>()
        for (piece in LudoSetup.allPieces(state)) {
            if (piece.player != player || piece.progress >= LudoSetup.FINISH) continue
            val from = LudoSetup.positionOf(piece)

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

            val occupants = if (targetProgress in 0 until 52) {
                LudoSetup.piecesAt(state, to)
            } else {
                emptyList()
            }
            val protectedTarget = occupants.singleOrNull()?.let { occupant ->
                val protection = LudoEconomy.player(state, occupant.player)
                if (protection.protectedToken == occupant.token && occupant.player != player) {
                    occupant
                } else {
                    null
                }
            }
            val captures = if (targetProgress in 0 until 52) {
                if (occupants.size == 1 && occupants.single().player != player &&
                    !isSafeTrackCell(to) && protectedTarget == null
                ) {
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
                    "baseDice" to baseDice,
                    "effectiveDice" to dice,
                    "usedExtraMove" to usedExtraMove,
                    "player" to player,
                    "token" to piece.token,
                    "targetProgress" to targetProgress,
                    "blockedProtection" to (protectedTarget != null),
                )
            )
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
        val movingPiece = LudoSetup.pieceForMove(state, move)
            ?: (state.get(move.from) as? LudoPiece)
            ?: return state
        val player = (move.metadata["player"] as? Int) ?: movingPiece.player
        val dice = (move.metadata["baseDice"] as? Int)
            ?: (move.metadata["dice"] as? Int)
            ?: 0
        val previousSixStreak =
            (state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0).coerceAtLeast(0)
        val targetProgress = (move.metadata["targetProgress"] as? Int)
            ?: inferTargetProgress(movingPiece, move.to)

        val blockedProtection = move.metadata["blockedProtection"] as? Boolean ?: false
        val nextPieces = LudoSetup.allPieces(state).map { piece ->
            when {
                piece.player == movingPiece.player && piece.token == movingPiece.token ->
                    movingPiece.copy(progress = targetProgress)
                move.captures.any { capture -> LudoSetup.positionOf(piece) == capture } &&
                    piece.player != player ->
                    piece.copy(progress = -1)
                else -> piece
            }
        }
        val nextBoard = LudoSetup.boardFor(nextPieces)

        val hasWon = (0 until LudoSetup.TOKENS_PER_PLAYER).all { token ->
            nextPieces.firstOrNull { it.player == player && it.token == token }?.progress == LudoSetup.FINISH
        }
        val status = if (hasWon) {
            if (player % 2 == 0) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS
        } else {
            GameStatus.IN_PROGRESS
        }

        val rolledSixStreak = if (dice == 6) {
            previousSixStreak.coerceAtLeast(1)
        } else {
            0
        }
        val forfeitsAfterThreeSixes = dice == 6 && rolledSixStreak >= 3
        val nextPlayer = if (status != GameStatus.IN_PROGRESS) {
            player
        } else if (dice == 6 && !forfeitsAfterThreeSixes) {
            player
        } else {
            (player + 1) % LudoSetup.PLAYER_COUNT
        }
        val nextSixStreak = if (nextPlayer == player) rolledSixStreak else 0
        val metadata = state.metadata.toMutableMap()
        metadata["ludo_turn"] = nextPlayer
        metadata["ludo_dice"] = 0
        metadata[LudoSetup.SIX_STREAK_METADATA] = nextSixStreak
        metadata[LudoSetup.PIECES_METADATA] = nextPieces
        metadata["ludo_rerolled"] = false

        if (metadata["ludo_economy_enabled"] as? Boolean == true) {
            var economies = LudoEconomy.players(state).toMutableList()
            var playerEconomy = economies[player]
            val reward = (if (move.captures.isNotEmpty()) LudoEconomy.CAPTURE_REWARD else 0) +
                (if (targetProgress == LudoSetup.FINISH) LudoEconomy.HOME_REWARD else 0) +
                (if (hasWon) LudoEconomy.FINAL_PLACEMENT_REWARD else 0)
            if (reward > 0) {
                playerEconomy = LudoEconomy.addCoins(playerEconomy, reward)
            }
            economies[player] = playerEconomy

            if (blockedProtection) {
                val protectedPiece = LudoSetup.piecesAt(state, move.to).singleOrNull()
                if (protectedPiece != null) {
                    val protectedEconomy = economies[protectedPiece.player]
                    economies[protectedPiece.player] = protectedEconomy.copy(protectedToken = null)
                }
            }
            metadata[LudoEconomy.METADATA] = economies
            val notification = when {
                move.captures.isNotEmpty() -> LudoNotification(
                    player,
                    "+${LudoEconomy.CAPTURE_REWARD} coins for a capture",
                )
                targetProgress == LudoSetup.FINISH -> LudoNotification(
                    player,
                    "+${LudoEconomy.HOME_REWARD} coins — token home",
                )
                hasWon -> LudoNotification(
                    player,
                    "+${LudoEconomy.FINAL_PLACEMENT_REWARD} coins — final placement",
                )
                blockedProtection -> LudoNotification(
                    player,
                    "Invincibility blocked a capture",
                )
                else -> null
            }
            if (notification != null) {
                metadata[LudoEconomy.NOTIFICATION_METADATA] = notification
            } else {
                metadata.remove(LudoEconomy.NOTIFICATION_METADATA)
            }
        }
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

    private fun isSafeTrackCell(position: Position): Boolean =
        LudoSetup.isSafeTrackCell(position)
}