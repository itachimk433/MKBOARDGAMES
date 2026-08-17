package com.mkdev.mkboardgames.games.ludo

import com.mkdev.mkboardgames.engine.GameState

enum class LudoAbility(val label: String, val icon: String, val cost: Int) {
    INVINCIBILITY("Invincibility", "🛡", 15),
    EXTRA_MOVE("Extra Move", "⚡", 10),
    REROLL("Reroll", "🎲", 8),
}

data class LudoPlayerEconomy(
    val coins: Int = LudoEconomy.STARTER_COINS,
    val invincibility: Int = 0,
    val extraMove: Int = 0,
    val reroll: Int = 0,
    val protectedToken: Int? = null,
    val tokenName: String = "Tokens",
    val totalEarned: Int = 0,
)

data class LudoNotification(
    val player: Int,
    val message: String,
)

data class LudoStanding(
    val player: Int,
    val place: Int,
    val completedTokens: Int,
    val totalProgress: Int,
    val placementReward: Int,
    val totalEarned: Int,
    val balance: Int,
)

object LudoEconomy {
    const val METADATA = "ludo_economy"
    const val NOTIFICATION_METADATA = "ludo_notification"
    const val PURCHASED_ABILITY_METADATA = "ludo_purchased_ability"
    const val USED_ABILITY_METADATA = "ludo_used_ability"
    const val STARTER_COINS = 30
    const val CAPTURE_REWARD = 2
    const val HOME_REWARD = 3
    const val FINAL_PLACEMENT_REWARD = 8
    val PLACEMENT_REWARDS = intArrayOf(8, 5, 2, 0)

    fun initialPlayers(): List<LudoPlayerEconomy> =
        List(LudoSetup.PLAYER_COUNT) { LudoPlayerEconomy() }

    fun players(state: GameState): List<LudoPlayerEconomy> {
        val stored = (state.metadata[METADATA] as? List<*>)
            ?.map { it as? LudoPlayerEconomy ?: LudoPlayerEconomy() }
            .orEmpty()
        return List(LudoSetup.PLAYER_COUNT) { stored.getOrNull(it) ?: LudoPlayerEconomy() }
    }

    fun player(state: GameState, player: Int): LudoPlayerEconomy =
        players(state)[player.coerceIn(0, LudoSetup.PLAYER_COUNT - 1)]

    fun withPlayer(
        state: GameState,
        player: Int,
        economy: LudoPlayerEconomy,
    ): Map<String, Any> = state.metadata + mapOf(
        METADATA to players(state).toMutableList().also { it[player] = economy },
    )

    fun withPlayers(
        state: GameState,
        players: List<LudoPlayerEconomy>,
    ): Map<String, Any> = state.metadata + mapOf(METADATA to players)

    fun abilityCount(economy: LudoPlayerEconomy, ability: LudoAbility): Int =
        when (ability) {
            LudoAbility.INVINCIBILITY -> economy.invincibility
            LudoAbility.EXTRA_MOVE -> economy.extraMove
            LudoAbility.REROLL -> economy.reroll
        }

    fun purchase(economy: LudoPlayerEconomy, ability: LudoAbility): LudoPlayerEconomy? {
        if (economy.coins < ability.cost) return null
        val updated = economy.copy(coins = economy.coins - ability.cost)
        return when (ability) {
            LudoAbility.INVINCIBILITY -> updated.copy(invincibility = updated.invincibility + 1)
            LudoAbility.EXTRA_MOVE -> updated.copy(extraMove = updated.extraMove + 1)
            LudoAbility.REROLL -> updated.copy(reroll = updated.reroll + 1)
        }
    }

    fun consume(economy: LudoPlayerEconomy, ability: LudoAbility): LudoPlayerEconomy =
        when (ability) {
            LudoAbility.INVINCIBILITY -> economy.copy(invincibility = (economy.invincibility - 1).coerceAtLeast(0))
            LudoAbility.EXTRA_MOVE -> economy.copy(extraMove = (economy.extraMove - 1).coerceAtLeast(0))
            LudoAbility.REROLL -> economy.copy(reroll = (economy.reroll - 1).coerceAtLeast(0))
        }

    fun addCoins(economy: LudoPlayerEconomy, amount: Int): LudoPlayerEconomy =
        economy.copy(coins = (economy.coins + amount).coerceAtLeast(0))

    fun addEarnedCoins(economy: LudoPlayerEconomy, amount: Int): LudoPlayerEconomy =
        economy.copy(
            coins = (economy.coins + amount).coerceAtLeast(0),
            totalEarned = (economy.totalEarned + amount).coerceAtLeast(0),
        )

    fun rename(economy: LudoPlayerEconomy, requestedName: String): LudoPlayerEconomy =
        economy.copy(tokenName = requestedName.filter { it.isLetter() }.take(5).ifBlank { "Tokens" })

    fun placementReward(place: Int): Int =
        PLACEMENT_REWARDS.getOrElse((place - 1).coerceIn(0, PLACEMENT_REWARDS.lastIndex)) { 0 }

    /**
     * The final board is the source of truth for ordering. Completed tokens are
     * the primary score, followed by total token progress, then player order as
     * a stable final tie-breaker. This keeps the result deterministic even when
     * multiple players have the same visible progress.
     */
    fun standings(state: GameState): List<LudoStanding> {
        val pieces = LudoSetup.allPieces(state)
        val economies = players(state)
        val rewardsEnabled = state.metadata["ludo_economy_enabled"] as? Boolean == true
        val orderedPlayers = (0 until LudoSetup.PLAYER_COUNT).sortedWith(
            compareByDescending<Int> { player ->
                pieces.count { it.player == player && it.progress >= LudoSetup.FINISH }
            }.thenByDescending { player ->
                pieces.filter { it.player == player }.sumOf { it.progress.coerceAtLeast(0) }
            }.thenBy { it },
        )
        return orderedPlayers.mapIndexed { index, player ->
            val playerPieces = pieces.filter { it.player == player }
            val economy = economies[player]
            LudoStanding(
                player = player,
                place = index + 1,
                completedTokens = playerPieces.count { it.progress >= LudoSetup.FINISH },
                totalProgress = playerPieces.sumOf { it.progress.coerceAtLeast(0) },
                placementReward = if (rewardsEnabled) placementReward(index + 1) else 0,
                totalEarned = economy.totalEarned,
                balance = economy.coins,
            )
        }
    }
}