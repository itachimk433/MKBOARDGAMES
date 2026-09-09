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
            // Two opposing tokens form a block in standard Ludo. A token may
            // not land on or pass through the occupied destination square.
            if (occupants.size >= 2 && occupants.all { it.player != player }) {
                continue
            }
            val protectedTarget = occupants.singleOrNull()?.let { occupant ->
                val protection = LudoEconomy.player(state, occupant.player)
                if (protection.protectedToken == occupant.token &&
                    occupant.player != player &&
                    !isSafeTrackCell(to)
                ) {
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
        require(state.status == GameStatus.IN_PROGRESS) {
            "Cannot apply a move after the game has ended"
        }
        // An Extra Move is still one turn, but it deliberately uses a
        // different effective dice value. Validate it against that same
        // effective value instead of only against the value stored for the
        // original roll. Without this, the animated move is rejected after
        // the token has already been selected.
        val player = LudoSetup.playerFromState(state)
        val usedExtraMove = move.metadata["usedExtraMove"] == true
        val baseDice = move.metadata["baseDice"] as? Int
        val effectiveDice = move.metadata["effectiveDice"] as? Int
        val legalMoves = if (
            usedExtraMove &&
            move.metadata["player"] == player &&
            baseDice == (state.metadata["ludo_dice"] as? Int) &&
            effectiveDice != null
        ) {
            legalMovesForDice(
                state = state,
                player = player,
                dice = effectiveDice,
                baseDice = baseDice,
                usedExtraMove = true,
            )
        } else {
            allLegalMoves(state, state.currentTurn)
        }
        require(move in legalMoves) {
            "Illegal Ludo move"
        }
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
        val protectedPiece = if (blockedProtection) {
            LudoSetup.piecesAt(state, move.to).singleOrNull()
        } else {
            null
        }
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
        metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] =
            if (nextPlayer == player) {
                state.metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] == true
            } else {
                false
            }
        metadata[LudoEconomy.USED_ABILITY_METADATA] =
            if (nextPlayer == player) {
                state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true
            } else {
                false
            }

        if (metadata["ludo_economy_enabled"] as? Boolean == true) {
            var economies = LudoEconomy.players(state).toMutableList()
            var playerEconomy = economies[player]
            val captureReward = if (move.captures.isNotEmpty()) LudoEconomy.CAPTURE_REWARD else 0
            val homeReward = if (targetProgress == LudoSetup.FINISH) LudoEconomy.HOME_REWARD else 0
            val moveReward = captureReward + homeReward
            if (moveReward > 0) {
                playerEconomy = LudoEconomy.addEarnedCoins(playerEconomy, moveReward)
            }
            // Invincibility belongs to the token it protects. Once that token
            // reaches the centre home, the protection is spent and another
            // token may be protected only after buying a new ability.
            if (targetProgress == LudoSetup.FINISH &&
                playerEconomy.protectedToken == movingPiece.token
            ) {
                playerEconomy = playerEconomy.copy(protectedToken = null)
            }
            economies[player] = playerEconomy

            if (blockedProtection) {
                if (protectedPiece != null) {
                    val protectedEconomy = economies[protectedPiece.player]
                    economies[protectedPiece.player] = protectedEconomy.copy(protectedToken = null)
                }
            }
            if (hasWon) {
                // The game ends as soon as the first player finishes, so award
                // the complete 1st–4th placement table in this final state.
                LudoEconomy.standings(state.copy(
                    board = nextBoard,
                    metadata = metadata + (LudoSetup.PIECES_METADATA to nextPieces),
                )).forEach { standing ->
                    if (standing.placementReward > 0) {
                        economies[standing.player] = LudoEconomy.addEarnedCoins(
                            economies[standing.player],
                            standing.placementReward,
                        )
                    }
                }
            }
            metadata[LudoEconomy.METADATA] = economies
            val notification = when {
                move.captures.isNotEmpty() -> LudoNotification(
                    player,
                    "sent ${
                        capturedPlayerName(
                            LudoSetup.piecesAt(state, move.to).singleOrNull(),
                        )
                    } home · +${LudoEconomy.CAPTURE_REWARD} coins",
                )
                hasWon -> LudoNotification(
                    player,
                    "finished the match · +${
                        LudoEconomy.HOME_REWARD + LudoEconomy.placementReward(1)
                    } coins",
                )
                targetProgress == LudoSetup.FINISH -> LudoNotification(
                    player,
                    "Token ${(move.metadata["token"] as? Int ?: 0) + 1} reached HOME · +${LudoEconomy.HOME_REWARD} coins",
                )
                blockedProtection -> LudoNotification(
                    player,
                    "🛡 BLOCKED! Invincibility protected Token ${(protectedPiece?.token ?: 0) + 1}",
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

    private fun capturedPlayerName(piece: LudoPiece?): String =
        piece?.let { LudoSetup.PLAYER_NAMES[it.player] } ?: "an opponent"

    private fun isSafeTrackCell(position: Position): Boolean =
        LudoSetup.isSafeTrackCell(position)
}