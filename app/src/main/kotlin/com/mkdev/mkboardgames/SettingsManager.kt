package com.mkdev.mkboardgames

import android.content.Context

object SettingsManager {

    private const val PREFS = "nexboard_prefs"
    private const val IRREGULAR_PREFS = "nexboard_irregular_prefs"
    private const val KEY_GAME_MODE = "game_mode"

    // ── Difficulty keys ──────────────────────────────────────────────────────
    private const val KEY_CHESS_DIFFICULTY      = "chess_ai_difficulty"
    private const val KEY_CHECKERS_DIFFICULTY   = "checkers_ai_difficulty"
    private const val KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY = "international_draughts_ai_difficulty"
    private const val KEY_MORABARABA_DIFFICULTY = "morabaraba_ai_difficulty"
    private const val KEY_CONNECT_FOUR_DIFFICULTY = "connect_four_ai_difficulty"
    private const val KEY_FOX_AND_GEESE_DIFFICULTY = "fox_and_geese_ai_difficulty"
    private const val KEY_LUDO_DIFFICULTY = "ludo_ai_difficulty"
    private const val KEY_SHOGI_DIFFICULTY = "shogi_ai_difficulty"
    private const val KEY_GO_DIFFICULTY = "go_ai_difficulty"
    private const val KEY_MANCALA_DIFFICULTY = "mancala_ai_difficulty"
    private const val KEY_YOTE_DIFFICULTY = "yote_ai_difficulty"
    private const val KEY_FIVE_FIELD_KONO_DIFFICULTY = "five_field_kono_ai_difficulty"
    private const val KEY_ONITAMA_DIFFICULTY = "onitama_ai_difficulty"
    private const val KEY_AMAZONS_DIFFICULTY = "amazons_ai_difficulty"
    private const val KEY_XIANGQI_DIFFICULTY = "xiangqi_ai_difficulty"
    private const val KEY_OTHELLO_DIFFICULTY = "othello_ai_difficulty"
    private const val KEY_MANCALA_MOVEMENT_SPEED = "mancala_movement_speed"

    // ── Hints keys ───────────────────────────────────────────────────────────
    private const val KEY_CHESS_HINTS    = "chess_show_hints"
    private const val KEY_CHECKERS_HINTS = "checkers_show_hints"
    private const val KEY_HELPER         = "helper_enabled"

    // ── Global + per-game theme keys ─────────────────────────────────────────
    private const val KEY_THEME            = "board_theme"
    private const val KEY_CHESS_THEME      = "chess_theme"
    private const val KEY_CHECKERS_THEME   = "checkers_theme"
    private const val KEY_INTERNATIONAL_DRAUGHTS_THEME = "international_draughts_theme"
    private const val KEY_OTHELLO_THEME    = "othello_theme"
    private const val KEY_MORABARABA_THEME = "morabaraba_theme"
    private const val KEY_TTT_THEME        = "ttt_theme"
    private const val KEY_CONNECT_FOUR_THEME = "connect_four_theme"
    private const val KEY_FOX_AND_GEESE_THEME = "fox_and_geese_theme"
    private const val KEY_SHOGI_THEME = "shogi_theme"

    // ── Global stats keys (legacy / overall) ─────────────────────────────────
    private const val KEY_STATS_WINS     = "stats_wins"
    private const val KEY_STATS_LOSSES   = "stats_losses"
    private const val KEY_STATS_DRAWS    = "stats_draws"
    private const val KEY_RECENTLY_PLAYED = "recently_played_games"
    private const val KEY_RECENTLY_PLAYED_VERSION = "recently_played_games_version"
    private const val RECENTLY_PLAYED_VERSION = 2
    private const val KEY_DAILY_REMINDERS_ENABLED = "daily_reminders_enabled"
    private const val KEY_LAST_APP_OPENED_AT = "last_app_opened_at"
    private const val KEY_DAILY_TEST_CLAIMED_DAY = "daily_test_claimed_day"
    private const val KEY_DAILY_TEST_CLAIM_DATE = "daily_test_claim_date"
    private const val KEY_USE_3D_DICE = "use_3d_dice"
    const val DAILY_TEST_DAYS = 14

    // ── Per-game stats keys ───────────────────────────────────────────────────
    private fun winKey(game: String)     = "stats_${game}_wins"
    private fun lossKey(game: String)    = "stats_${game}_losses"
    private fun drawKey(game: String)    = "stats_${game}_draws"

    // active game tag — set at the start of every vs-AI game
    private const val KEY_ACTIVE_GAME = "active_game_tag"
    private const val KEY_DIFFICULTY_UNLOCKED_LEVEL = "difficulty_unlocked_level"
    private const val KEY_DIFFICULTY_WIN_STREAK = "difficulty_win_streak"
    private const val KEY_DIFFICULTY_STREAK_LEVEL = "difficulty_streak_level"
    private const val KEY_UNDO_CREDITS = "undo_credits"
    private const val KEY_UNDO_CREDITS_MIGRATED = "undo_credits_migrated"
    private const val UNLIMITED_UNDO_CREDITS = Int.MAX_VALUE
    const val MAX_UNDO_CREDITS = 10

    private fun undoKey(gameTag: String) = KEY_UNDO_CREDITS + "_" + gameTag

    private fun sharedPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prefsForMode(ctx: Context, mode: GameMode) =
        ctx.getSharedPreferences(
            if (mode == GameMode.IRREGULAR) IRREGULAR_PREFS else PREFS,
            Context.MODE_PRIVATE,
        )

    private fun prefs(ctx: Context) = prefsForMode(ctx, currentMode(ctx))

    fun currentMode(ctx: Context): GameMode =
        GameMode.fromName(sharedPrefs(ctx).getString(KEY_GAME_MODE, GameMode.NORMAL.name))

    fun setCurrentMode(ctx: Context, mode: GameMode) {
        sharedPrefs(ctx).edit().putString(KEY_GAME_MODE, mode.name).apply()
    }

    fun setCurrentModeFromIntent(ctx: Context, intent: android.content.Intent) {
        setCurrentMode(ctx, GameMode.fromName(intent.getStringExtra(GameMode.EXTRA_MODE)))
    }

    // Recently played is shared between the normal and irregular catalogues:
    // both modes expose the same games, so opening a game in either mode should
    // keep it available as a useful shortcut when the catalogue is revisited.
    fun recentlyPlayedGames(ctx: Context): List<String> {
        val settings = sharedPrefs(ctx)
        if (settings.getInt(KEY_RECENTLY_PLAYED_VERSION, 0) < RECENTLY_PLAYED_VERSION) {
            settings.edit()
                .remove(KEY_RECENTLY_PLAYED)
                .putInt(KEY_RECENTLY_PLAYED_VERSION, RECENTLY_PLAYED_VERSION)
                .apply()
            return emptyList()
        }
        return settings
            .getString(KEY_RECENTLY_PLAYED, null)
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.take(3)
            .orEmpty()
    }

    fun recordRecentlyPlayed(ctx: Context, gameType: String) {
        val normalizedGameType = gameType.trim().uppercase()
        val updated = (listOf(normalizedGameType) + recentlyPlayedGames(ctx))
            .distinct()
            .take(3)
        sharedPrefs(ctx).edit()
            .putString(KEY_RECENTLY_PLAYED, updated.joinToString(","))
            .apply()
    }

    fun isDailyRemindersEnabled(ctx: Context): Boolean =
        sharedPrefs(ctx).getBoolean(KEY_DAILY_REMINDERS_ENABLED, false)

    fun setDailyRemindersEnabled(ctx: Context, enabled: Boolean) {
        sharedPrefs(ctx).edit().putBoolean(KEY_DAILY_REMINDERS_ENABLED, enabled).apply()
    }

    fun is3DDiceEnabled(ctx: Context): Boolean =
        sharedPrefs(ctx).getBoolean(KEY_USE_3D_DICE, true)

    fun set3DDiceEnabled(ctx: Context, enabled: Boolean) {
        sharedPrefs(ctx).edit().putBoolean(KEY_USE_3D_DICE, enabled).apply()
    }

    fun recordAppOpened(ctx: Context, openedAt: Long = System.currentTimeMillis()) {
        sharedPrefs(ctx).edit().putLong(KEY_LAST_APP_OPENED_AT, openedAt).apply()
    }

    fun lastAppOpenedAt(ctx: Context): Long =
        sharedPrefs(ctx).getLong(KEY_LAST_APP_OPENED_AT, 0L)

    fun dailyTestClaimedDay(ctx: Context): Int =
        sharedPrefs(ctx).getInt(KEY_DAILY_TEST_CLAIMED_DAY, 0).coerceIn(0, DAILY_TEST_DAYS)

    fun currentDailyTestDay(ctx: Context): Int {
        val claimedDay = dailyTestClaimedDay(ctx)
        if (claimedDay >= DAILY_TEST_DAYS) return DAILY_TEST_DAYS

        val lastClaimDate = sharedPrefs(ctx).getString(KEY_DAILY_TEST_CLAIM_DATE, null)
        return if (lastClaimDate == dailyTestDateKey()) {
            claimedDay.coerceAtLeast(1)
        } else {
            (claimedDay + 1).coerceAtMost(DAILY_TEST_DAYS)
        }
    }

    fun claimDailyTestDay(ctx: Context): Boolean {
        val claimedDay = dailyTestClaimedDay(ctx)
        if (claimedDay >= DAILY_TEST_DAYS) return false

        val today = dailyTestDateKey()
        val currentDay = currentDailyTestDay(ctx)
        val lastClaimDate = sharedPrefs(ctx).getString(KEY_DAILY_TEST_CLAIM_DATE, null)
        if (lastClaimDate == today || currentDay <= claimedDay) return false

        sharedPrefs(ctx).edit()
            .putInt(KEY_DAILY_TEST_CLAIMED_DAY, currentDay)
            .putString(KEY_DAILY_TEST_CLAIM_DATE, today)
            .apply()
        return true
    }

    private fun dailyTestDateKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    // ── Active game tag ───────────────────────────────────────────────────────
    fun setActiveGame(ctx: Context, gameTag: String) =
        prefs(ctx).edit().putString(KEY_ACTIVE_GAME, gameTag).apply()

    private fun activeGame(ctx: Context) =
        prefs(ctx).getString(KEY_ACTIVE_GAME, "overall") ?: "overall"

    private fun difficultyPreferenceKey(gameTag: String): String? = when (gameTag.lowercase()) {
        "chess" -> KEY_CHESS_DIFFICULTY
        "checkers" -> KEY_CHECKERS_DIFFICULTY
        "international_draughts" -> KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY
        "morabaraba" -> KEY_MORABARABA_DIFFICULTY
        "connect_four" -> KEY_CONNECT_FOUR_DIFFICULTY
        "fox_and_geese" -> KEY_FOX_AND_GEESE_DIFFICULTY
        "ludo" -> KEY_LUDO_DIFFICULTY
        "shogi" -> KEY_SHOGI_DIFFICULTY
        "go" -> KEY_GO_DIFFICULTY
        "mancala" -> KEY_MANCALA_DIFFICULTY
        "yote" -> KEY_YOTE_DIFFICULTY
        "five_field_kono" -> KEY_FIVE_FIELD_KONO_DIFFICULTY
        "onitama" -> KEY_ONITAMA_DIFFICULTY
        "amazons" -> KEY_AMAZONS_DIFFICULTY
        "xiangqi" -> KEY_XIANGQI_DIFFICULTY
        "othello" -> KEY_OTHELLO_DIFFICULTY
        "ttt" -> KEY_TTT_DIFFICULTY
        else -> null
    }

    private fun maximumDifficultyLevel(gameTag: String): Int? =
        when (gameTag.lowercase()) {
            "chess" -> 3
            "checkers", "international_draughts", "morabaraba", "connect_four",
            "fox_and_geese", "ludo", "shogi", "go", "mancala", "yote",
            "five_field_kono", "onitama", "amazons", "xiangqi", "othello", "ttt" -> 2
            else -> null
        }

    private fun unlockedDifficultyKey(gameTag: String) =
        "${KEY_DIFFICULTY_UNLOCKED_LEVEL}_${gameTag.lowercase()}"

    private fun difficultyWinStreakKey(gameTag: String) =
        "${KEY_DIFFICULTY_WIN_STREAK}_${gameTag.lowercase()}"

    private fun difficultyStreakLevelKey(gameTag: String) =
        "${KEY_DIFFICULTY_STREAK_LEVEL}_${gameTag.lowercase()}"

    /** Easy is available by default; each harder level is earned separately per game. */
    fun highestUnlockedDifficulty(ctx: Context, gameTag: String): Int {
        val maximum = maximumDifficultyLevel(gameTag) ?: return 0
        return prefs(ctx).getInt(unlockedDifficultyKey(gameTag), 0).coerceIn(0, maximum)
    }

    private fun getDifficulty(ctx: Context, gameTag: String, preferenceKey: String, maximum: Int): Int {
        val allowedMaximum = minOf(maximum, highestUnlockedDifficulty(ctx, gameTag))
        return prefs(ctx).getInt(preferenceKey, 0).coerceIn(0, allowedMaximum)
    }

    private fun setDifficulty(
        ctx: Context,
        gameTag: String,
        preferenceKey: String,
        maximum: Int,
        level: Int,
    ) {
        val allowedMaximum = minOf(maximum, highestUnlockedDifficulty(ctx, gameTag))
        prefs(ctx).edit().putInt(preferenceKey, level.coerceIn(0, allowedMaximum)).apply()
    }

    private fun recordDifficultyWin(ctx: Context, gameTag: String) {
        val normalizedGameTag = gameTag.lowercase()
        val preferenceKey = difficultyPreferenceKey(normalizedGameTag) ?: return
        val maximum = maximumDifficultyLevel(normalizedGameTag) ?: return
        val settings = prefs(ctx)
        val unlocked = settings.getInt(unlockedDifficultyKey(normalizedGameTag), 0)
            .coerceIn(0, maximum)
        val currentLevel = settings.getInt(preferenceKey, 0).coerceIn(0, unlocked)
        val streakLevelKey = difficultyStreakLevelKey(normalizedGameTag)
        val streakKey = difficultyWinStreakKey(normalizedGameTag)
        val previousStreakLevel = settings.getInt(streakLevelKey, currentLevel)
        val streak = if (previousStreakLevel == currentLevel) {
            settings.getInt(streakKey, 0) + 1
        } else {
            1
        }
        val nextUnlocked = if (currentLevel == unlocked && streak >= 2) {
            (unlocked + 1).coerceAtMost(maximum)
        } else {
            unlocked
        }
        settings.edit()
            .putInt(unlockedDifficultyKey(normalizedGameTag), nextUnlocked)
            .putInt(streakKey, if (nextUnlocked > unlocked) 0 else streak)
            .putInt(streakLevelKey, currentLevel)
            .apply()
    }

    private fun resetDifficultyWinStreak(ctx: Context, gameTag: String) {
        if (difficultyPreferenceKey(gameTag) == null) return
        prefs(ctx).edit().putInt(difficultyWinStreakKey(gameTag), 0).apply()
    }

    // Undo credits are independent for every game. A one-time migration keeps
    // the old shared balance for the first game opened after upgrading, while
    // every other game starts with its own full balance.
    fun hasUnlimitedUndos(ctx: Context, vsAI: Boolean): Boolean =
        !vsAI || isAdsRemoved(ctx)

    fun undoCredits(ctx: Context, gameTag: String, vsAI: Boolean = true): Int {
        if (hasUnlimitedUndos(ctx, vsAI)) return UNLIMITED_UNDO_CREDITS

        val settings = sharedPrefs(ctx)
        val key = undoKey(gameTag)
        if (settings.contains(key)) {
            return settings.getInt(key, 3).coerceIn(0, MAX_UNDO_CREDITS)
        }

        if (!settings.getBoolean(KEY_UNDO_CREDITS_MIGRATED, false) &&
            settings.contains(KEY_UNDO_CREDITS)
        ) {
            val legacyValue = settings.getInt(KEY_UNDO_CREDITS, 3)
                .coerceIn(0, MAX_UNDO_CREDITS)
            settings.edit()
                .putInt(key, legacyValue)
                .putBoolean(KEY_UNDO_CREDITS_MIGRATED, true)
                .apply()
            return legacyValue
        }
        return 3
    }

    fun consumeUndoCredit(ctx: Context, gameTag: String, current: Int, vsAI: Boolean): Int {
        if (hasUnlimitedUndos(ctx, vsAI)) return current
        val remaining = (current - 1).coerceAtLeast(0)
        setUndoCredits(ctx, gameTag, remaining)
        return remaining
    }

    fun refundUndoCredit(ctx: Context, gameTag: String, current: Int, vsAI: Boolean): Int {
        if (hasUnlimitedUndos(ctx, vsAI)) return current
        val restored = (current.coerceIn(0, MAX_UNDO_CREDITS) + 1)
            .coerceAtMost(MAX_UNDO_CREDITS)
        setUndoCredits(ctx, gameTag, restored)
        return restored
    }

    fun grantUndoCredits(
        ctx: Context,
        gameTag: String,
        current: Int,
        amount: Int,
        vsAI: Boolean,
    ): Int {
        if (hasUnlimitedUndos(ctx, vsAI)) return current
        val updated = (
            current.coerceIn(0, MAX_UNDO_CREDITS).toLong() +
                amount.coerceAtLeast(0).toLong()
        ).coerceAtMost(MAX_UNDO_CREDITS.toLong()).toInt()
        setUndoCredits(ctx, gameTag, updated)
        return updated
    }

    fun setUndoCredits(ctx: Context, gameTag: String, value: Int) {
        sharedPrefs(ctx).edit()
            .putInt(undoKey(gameTag), value.coerceIn(0, MAX_UNDO_CREDITS))
            .apply()
    }

    // ── Chess ────────────────────────────────────────────────────────────────
    data class ChessAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val quiesceDepth: Int,
        val varietyWindowOverride: Int = -1,
    )
    data class ChessEtherealProfile(val timeLimitMs: Long, val hashMb: Int)
    data class ChessStockfishProfile(val timeLimitMs: Long, val hashMb: Int)

    fun chessDifficultyLabels() = arrayOf("Easy", "Medium", "Hard", "Master")

    fun chessAiProfileForLevel(level: Int): ChessAiProfile = when (level.coerceIn(0, 3)) {
        0 -> ChessAiProfile(2, 600L, 0)  // Easy
        1 -> ChessAiProfile(5, 1200L, 2) // Medium — stronger but still snappy
        2 -> ChessAiProfile(8, 2600L, 5, 0) // Hard — deterministic tactical search
        3 -> ChessAiProfile(10, 6000L, 6, 0) // Master — deepest deterministic search
        else -> ChessAiProfile(5, 1200L, 2)
    }

    fun getChessDifficulty(ctx: Context) = getDifficulty(ctx, "chess", KEY_CHESS_DIFFICULTY, 3)
    fun setChessDifficulty(ctx: Context, v: Int) = setDifficulty(ctx, "chess", KEY_CHESS_DIFFICULTY, 3, v)
    fun chessAiDepth(ctx: Context) = chessAiProfileForLevel(getChessDifficulty(ctx)).depth
    fun chessAiTimeLimitMs(ctx: Context): Long = chessAiProfileForLevel(getChessDifficulty(ctx)).timeLimitMs
    fun chessAiQuiesceDepth(ctx: Context): Int = chessAiProfileForLevel(getChessDifficulty(ctx)).quiesceDepth

    /**
     * Ethereal is used for Hard. Master uses Stockfish and keeps Ethereal as
     * its native fallback, while Easy/Medium stay on the lightweight Kotlin
     * player for quick, intentionally fallible moves.
     */
    fun chessEtherealProfileForLevel(level: Int): ChessEtherealProfile = when (level.coerceIn(0, 3)) {
        2 -> ChessEtherealProfile(timeLimitMs = 2_000L, hashMb = 32)
        3 -> ChessEtherealProfile(timeLimitMs = 5_000L, hashMb = 64)
        else -> ChessEtherealProfile(timeLimitMs = 1_200L, hashMb = 16)
    }

    fun chessStockfishProfileForLevel(level: Int): ChessStockfishProfile = when (level.coerceIn(0, 3)) {
        3 -> ChessStockfishProfile(timeLimitMs = 5_000L, hashMb = 64)
        else -> ChessStockfishProfile(timeLimitMs = 1_200L, hashMb = 16)
    }

    // ── Checkers ─────────────────────────────────────────────────────────────
    fun getCheckersDifficulty(ctx: Context) =
        getDifficulty(ctx, "checkers", KEY_CHECKERS_DIFFICULTY, 2)

    fun setCheckersDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "checkers", KEY_CHECKERS_DIFFICULTY, 2, v)

    fun checkersAiDepthForLevel(level: Int) = when (level.coerceIn(0, 2)) {
        0 -> 2
        2 -> 8
        else -> 5
    }

    fun checkersAiDepth(ctx: Context) =
        checkersAiDepthForLevel(getCheckersDifficulty(ctx))

    // ── International Draughts ──────────────────────────────────────────────
    fun getInternationalDraughtsDifficulty(ctx: Context) =
        getDifficulty(ctx, "international_draughts", KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY, 2)

    fun setInternationalDraughtsDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "international_draughts", KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY, 2, v)

    fun internationalDraughtsAiDepthForLevel(level: Int) = when (level.coerceIn(0, 2)) {
        0 -> 2
        2 -> 8
        else -> 5
    }

    fun internationalDraughtsAiDepth(ctx: Context) =
        internationalDraughtsAiDepthForLevel(getInternationalDraughtsDifficulty(ctx))

    // ── Othello ────────────────────────────────────────────────────────────────
    data class OthelloAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val varietyWindow: Int,
    )

    fun getOthelloDifficulty(ctx: Context) =
        getDifficulty(ctx, "othello", KEY_OTHELLO_DIFFICULTY, 2)

    fun setOthelloDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "othello", KEY_OTHELLO_DIFFICULTY, 2, v)

    fun othelloAiProfileForLevel(level: Int): OthelloAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> OthelloAiProfile(
                depth = 2,
                timeLimitMs = 800L,
                varietyWindow = 80,
            )
            1 -> OthelloAiProfile(
                depth = 4,
                timeLimitMs = 1600L,
                varietyWindow = 30,
            )
            else -> OthelloAiProfile(
                depth = 7,
                timeLimitMs = 3000L,
                varietyWindow = 0,
            )
        }

    fun othelloAiProfile(ctx: Context) =
        othelloAiProfileForLevel(getOthelloDifficulty(ctx))

    fun othelloAiDepth(ctx: Context) = othelloAiProfile(ctx).depth

    // ── Morabaraba ───────────────────────────────────────────────────────────
    fun getMorabarabaDifficulty(ctx: Context) =
        getDifficulty(ctx, "morabaraba", KEY_MORABARABA_DIFFICULTY, 2)
    fun setMorabarabaDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "morabaraba", KEY_MORABARABA_DIFFICULTY, 2, v)
    fun morabarabaAiDepth(ctx: Context) = when (getMorabarabaDifficulty(ctx)) { 0 -> 3; 2 -> 7; else -> 5 }
    fun morabarabaAiTimeLimitMs(ctx: Context): Long = when (getMorabarabaDifficulty(ctx)) { 0 -> 600L; 2 -> 2500L; else -> 1200L }

    // ── Connect Four ─────────────────────────────────────────────────────────
    fun getConnectFourDifficulty(ctx: Context) =
        getDifficulty(ctx, "connect_four", KEY_CONNECT_FOUR_DIFFICULTY, 2)
    fun setConnectFourDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "connect_four", KEY_CONNECT_FOUR_DIFFICULTY, 2, v)
    fun connectFourAiDepth(ctx: Context) = when (getConnectFourDifficulty(ctx)) {
        0 -> 3
        2 -> 7
        else -> 5
    }
    fun connectFourAiTimeLimitMs(ctx: Context): Long =
        when (getConnectFourDifficulty(ctx)) { 0 -> 700L; 2 -> 2500L; else -> 1400L }

    // ── Fox and Geese ────────────────────────────────────────────────────────
    data class FoxAndGeeseAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val varietyWindow: Int,
    )

    fun getFoxAndGeeseDifficulty(ctx: Context) =
        getDifficulty(ctx, "fox_and_geese", KEY_FOX_AND_GEESE_DIFFICULTY, 2)

    fun setFoxAndGeeseDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "fox_and_geese", KEY_FOX_AND_GEESE_DIFFICULTY, 2, v)

    fun foxAndGeeseAiProfileForLevel(level: Int): FoxAndGeeseAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> FoxAndGeeseAiProfile(
                depth = 2,
                timeLimitMs = 700L,
                varietyWindow = 60,
            )
            1 -> FoxAndGeeseAiProfile(
                depth = 4,
                timeLimitMs = 1300L,
                varietyWindow = 30,
            )
            else -> FoxAndGeeseAiProfile(
                depth = 6,
                timeLimitMs = 2400L,
                varietyWindow = 0,
            )
        }

    fun foxAndGeeseAiProfile(ctx: Context) =
        foxAndGeeseAiProfileForLevel(getFoxAndGeeseDifficulty(ctx))

    fun foxAndGeeseAiDepth(ctx: Context) = foxAndGeeseAiProfile(ctx).depth

    fun foxAndGeeseAiTimeLimitMs(ctx: Context): Long =
        foxAndGeeseAiProfile(ctx).timeLimitMs

    fun foxAndGeeseAiVarietyWindow(ctx: Context): Int =
        foxAndGeeseAiProfile(ctx).varietyWindow

    // ── Ludo ─────────────────────────────────────────────────────────────────
    fun getLudoDifficulty(ctx: Context) =
        getDifficulty(ctx, "ludo", KEY_LUDO_DIFFICULTY, 2)
    fun setLudoDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "ludo", KEY_LUDO_DIFFICULTY, 2, v)

    // ── Xiangqi ───────────────────────────────────────────────────────────────
    data class XiangqiAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val quiesceDepth: Int,
        val varietyWindow: Int,
    )

    fun getXiangqiDifficulty(ctx: Context) =
        getDifficulty(ctx, "xiangqi", KEY_XIANGQI_DIFFICULTY, 2)

    fun setXiangqiDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "xiangqi", KEY_XIANGQI_DIFFICULTY, 2, v)

    fun xiangqiAiProfileForLevel(level: Int): XiangqiAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> XiangqiAiProfile(
                depth = 1,
                timeLimitMs = 600L,
                quiesceDepth = 0,
                varietyWindow = 100,
            )
            1 -> XiangqiAiProfile(
                depth = 2,
                timeLimitMs = 1400L,
                quiesceDepth = 1,
                varietyWindow = 50,
            )
            else -> XiangqiAiProfile(
                depth = 4,
                timeLimitMs = 3200L,
                quiesceDepth = 2,
                varietyWindow = 0,
            )
        }

    // ── Shogi ─────────────────────────────────────────────────────────────────
    fun getShogiDifficulty(ctx: Context) =
        getDifficulty(ctx, "shogi", KEY_SHOGI_DIFFICULTY, 2)
    fun setShogiDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "shogi", KEY_SHOGI_DIFFICULTY, 2, v)
    fun shogiAiDepth(ctx: Context) = when (getShogiDifficulty(ctx)) {
        0 -> 1
        1 -> 2
        2 -> 3
        else -> 3
    }
    fun shogiAiTimeLimitMs(ctx: Context): Long = when (getShogiDifficulty(ctx)) {
        0 -> 700L
        1 -> 1200L
        2 -> 2200L
        else -> 2200L
    }

    // ── Go ────────────────────────────────────────────────────────────────────
    fun getGoDifficulty(ctx: Context) =
        getDifficulty(ctx, "go", KEY_GO_DIFFICULTY, 2)
    fun setGoDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "go", KEY_GO_DIFFICULTY, 2, v)

    fun goAiIterations(ctx: Context) = when (getGoDifficulty(ctx)) {
        0 -> 260
        2 -> 1500
        else -> 720
    }

    fun goAiTimeLimitMs(ctx: Context): Long = when (getGoDifficulty(ctx)) {
        0 -> 550L
        2 -> 2600L
        else -> 1300L
    }

    fun goAiThinkingDelayMs(ctx: Context): Long = when (getGoDifficulty(ctx)) {
        0 -> 650L
        2 -> 1350L
        else -> 950L
    }

    // ── Mancala ───────────────────────────────────────────────────────────────
    data class MancalaAiProfile(val depth: Int)

    fun mancalaAiProfileForLevel(level: Int): MancalaAiProfile = when (level.coerceIn(0, 2)) {
        0 -> MancalaAiProfile(depth = 2) // Easy
        1 -> MancalaAiProfile(depth = 5) // Medium
        else -> MancalaAiProfile(depth = 7) // Hard
    }

    fun getMancalaDifficulty(ctx: Context) =
        getDifficulty(ctx, "mancala", KEY_MANCALA_DIFFICULTY, 2)

    fun setMancalaDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "mancala", KEY_MANCALA_DIFFICULTY, 2, v)

    fun mancalaAiDepth(ctx: Context) = mancalaAiProfileForLevel(getMancalaDifficulty(ctx)).depth

    // ── Yoté ──────────────────────────────────────────────────────────────────
    fun getYoteDifficulty(ctx: Context) =
        getDifficulty(ctx, "yote", KEY_YOTE_DIFFICULTY, 2)

    fun setYoteDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "yote", KEY_YOTE_DIFFICULTY, 2, v)

    fun yoteAiDepth(ctx: Context) = when (getYoteDifficulty(ctx)) {
        0 -> 2
        2 -> 4
        else -> 3
    }

    // ── Five Field Kono ──────────────────────────────────────────────────────
    data class FiveFieldKonoAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val choiceWindow: Int,
    )

    fun fiveFieldKonoAiProfileForLevel(level: Int): FiveFieldKonoAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> FiveFieldKonoAiProfile(depth = 1, timeLimitMs = 300L, choiceWindow = 120)
            1 -> FiveFieldKonoAiProfile(depth = 3, timeLimitMs = 900L, choiceWindow = 35)
            else -> FiveFieldKonoAiProfile(depth = 5, timeLimitMs = 2200L, choiceWindow = 0)
        }

    fun getFiveFieldKonoDifficulty(ctx: Context) =
        getDifficulty(ctx, "five_field_kono", KEY_FIVE_FIELD_KONO_DIFFICULTY, 2)

    fun setFiveFieldKonoDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "five_field_kono", KEY_FIVE_FIELD_KONO_DIFFICULTY, 2, v)

    fun fiveFieldKonoAiDepth(ctx: Context) =
        fiveFieldKonoAiProfileForLevel(getFiveFieldKonoDifficulty(ctx)).depth

    fun fiveFieldKonoAiTimeLimitMs(ctx: Context): Long =
        fiveFieldKonoAiProfileForLevel(getFiveFieldKonoDifficulty(ctx)).timeLimitMs

    // ── Onitama ──────────────────────────────────────────────────────────────
    fun getOnitamaDifficulty(ctx: Context) =
        getDifficulty(ctx, "onitama", KEY_ONITAMA_DIFFICULTY, 2)

    fun setOnitamaDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "onitama", KEY_ONITAMA_DIFFICULTY, 2, v)

    fun onitamaAiDepth(ctx: Context) = when (getOnitamaDifficulty(ctx)) {
        0 -> 2
        2 -> 4
        else -> 3
    }

    // ── Amazons ───────────────────────────────────────────────────────────────
    data class AmazonsAiProfile(val depth: Int, val timeLimitMs: Long)

    fun amazonsAiProfileForLevel(level: Int): AmazonsAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> AmazonsAiProfile(depth = 2, timeLimitMs = 1800L)
            1 -> AmazonsAiProfile(depth = 3, timeLimitMs = 4000L)
            else -> AmazonsAiProfile(depth = 5, timeLimitMs = 7000L)
        }

    fun getAmazonsDifficulty(ctx: Context) =
        getDifficulty(ctx, "amazons", KEY_AMAZONS_DIFFICULTY, 2)

    fun setAmazonsDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "amazons", KEY_AMAZONS_DIFFICULTY, 2, v)

    fun amazonsAiDepth(ctx: Context) =
        amazonsAiProfileForLevel(getAmazonsDifficulty(ctx)).depth

    fun amazonsAiTimeLimitMs(ctx: Context): Long =
        amazonsAiProfileForLevel(getAmazonsDifficulty(ctx)).timeLimitMs

    fun getMancalaMovementSpeed(ctx: Context) =
        prefs(ctx).getInt(KEY_MANCALA_MOVEMENT_SPEED, 1).coerceIn(1, 4)

    fun setMancalaMovementSpeed(ctx: Context, multiplier: Int) =
        prefs(ctx).edit().putInt(KEY_MANCALA_MOVEMENT_SPEED, multiplier.coerceIn(1, 4)).apply()

    // ── Tic-Tac-Toe ──────────────────────────────────────────────────────────
    private const val KEY_TTT_DIFFICULTY = "ttt_ai_difficulty"
    fun getTttDifficulty(ctx: Context) =
        getDifficulty(ctx, "ttt", KEY_TTT_DIFFICULTY, 2)
    fun setTttDifficulty(ctx: Context, v: Int) =
        setDifficulty(ctx, "ttt", KEY_TTT_DIFFICULTY, 2, v)
    /** Depth scales with both difficulty and board size so the AI always responds fast. */
    fun tttAiDepth(ctx: Context, boardSize: Int): Int {
        val hardCap = when (boardSize) { 3 -> 9; 4 -> 7; else -> 6 }  // 5×5 max
        return when (getTttDifficulty(ctx)) {
            0    -> minOf(3, hardCap)  // Easy — makes mistakes, beatable
            2    -> hardCap             // Hard — near-perfect
            else -> minOf(6, hardCap)  // Medium
        }
    }

    // ── Movement sounds ──────────────────────────────────────────────────────
    private const val KEY_MOVEMENT_SOUNDS = "movement_sounds"
    fun isMovementSoundsEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_MOVEMENT_SOUNDS, true)
    fun setMovementSoundsEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MOVEMENT_SOUNDS, v).apply()

    // ── Background music ────────────────────────────────────────────────────
    private const val KEY_MUSIC_ENABLED = "background_music_enabled"
    private const val KEY_MUSIC_VOLUME = "background_music_volume"
    private const val KEY_MATCH_MUSIC_VOLUME = "in_match_music_volume"
    fun isMusicEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_MUSIC_ENABLED, true)

    fun setMusicEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MUSIC_ENABLED, v).apply()

    fun getMusicVolume(ctx: Context) =
        prefs(ctx).getInt(KEY_MUSIC_VOLUME, 45).coerceIn(0, 100)

    fun setMusicVolume(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_MUSIC_VOLUME, v.coerceIn(0, 100)).apply()

    fun getMatchMusicVolume(ctx: Context) =
        prefs(ctx).getInt(KEY_MATCH_MUSIC_VOLUME, 30).coerceIn(0, 100)

    fun setMatchMusicVolume(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_MATCH_MUSIC_VOLUME, v.coerceIn(0, 100)).apply()

    // ── Light mode ───────────────────────────────────────────────────────────
    private const val KEY_LIGHT_MODE = "light_mode"
    fun isLightMode(ctx: Context) = prefs(ctx).getBoolean(KEY_LIGHT_MODE, false)
    fun setLightMode(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_LIGHT_MODE, v).apply()

    // ── Home background ──────────────────────────────────────────────────────
    private const val KEY_HOME_BACKGROUND = "home_background"
    private const val KEY_HOME_STYLE = "home_style"
    fun isHomeBackgroundEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_HOME_BACKGROUND, true)
    fun setHomeBackgroundEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_HOME_BACKGROUND, v).apply()

    // ── Home game card style ─────────────────────────────────────────────────
    private const val KEY_WOOD_GAME_CARDS = "wood_game_cards"
    enum class HomeStyle(val label: String) {
        BROWN("Brown"),
    }

    fun getHomeStyle(ctx: Context): HomeStyle = HomeStyle.BROWN

    fun setHomeStyle(ctx: Context, style: HomeStyle) {
        prefs(ctx).edit()
            .putString(KEY_HOME_STYLE, HomeStyle.BROWN.name)
            .putBoolean(KEY_HOME_BACKGROUND, true)
            .putBoolean(KEY_WOOD_GAME_CARDS, true)
            .apply()
    }

    fun isWoodGameCardStyleEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_WOOD_GAME_CARDS, true)
    fun setWoodGameCardStyleEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_WOOD_GAME_CARDS, v).apply()
    fun isBrownHomeStyleEnabled(ctx: Context) =
        true
    fun setBrownHomeStyleEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit()
            .putString(KEY_HOME_STYLE, HomeStyle.BROWN.name)
            .putBoolean(KEY_HOME_BACKGROUND, true)
            .putBoolean(KEY_WOOD_GAME_CARDS, true)
            .apply()

    // ── Hints ────────────────────────────────────────────────────────────────
    fun getHelper(ctx: Context): Boolean {
        val settings = prefs(ctx)
        return if (settings.contains(KEY_HELPER)) {
            settings.getBoolean(KEY_HELPER, true)
        } else {
            // Keep the existing Chess preference when upgrading from a
            // version that exposed hints per game.
            settings.getBoolean(KEY_CHESS_HINTS, true)
        }
    }

    fun setHelper(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_HELPER, enabled).apply()

    fun getChessHints(ctx: Context) = getHelper(ctx)
    fun setChessHints(ctx: Context, v: Boolean) = setHelper(ctx, v)
    fun getCheckersHints(ctx: Context) = getHelper(ctx)
    fun setCheckersHints(ctx: Context, v: Boolean) = setHelper(ctx, v)
    fun getShowHints(ctx: Context) = getHelper(ctx)
    fun setShowHints(ctx: Context, v: Boolean) = setHelper(ctx, v)

    // ── Themes ───────────────────────────────────────────────────────────────
    data class BoardTheme(val light: Int, val dark: Int, val accent: Int, val name: String)

    val THEMES = listOf(
        BoardTheme(0xFFF0D9B5.toInt(), 0xFFB58863.toInt(), 0xFF7FC8F8.toInt(), "Classic"),
        BoardTheme(0xFF4A4A4A.toInt(), 0xFF2D2D2D.toInt(), 0xFF90CAF9.toInt(), "Dark Mode"),
        BoardTheme(0xFF9BB7D4.toInt(), 0xFF4A7FA5.toInt(), 0xFFFFD54F.toInt(), "Ocean"),
        BoardTheme(0xFF9DB888.toInt(), 0xFF5A7A4A.toInt(), 0xFFFFCC80.toInt(), "Forest"),
    )

    fun getTheme(ctx: Context) = prefs(ctx).getInt(KEY_THEME, 0)
    fun setTheme(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_THEME, v).apply()
    fun currentTheme(ctx: Context) = THEMES[getTheme(ctx).coerceIn(0, THEMES.lastIndex)]

    fun getChessTheme(ctx: Context)          = prefs(ctx).getInt(KEY_CHESS_THEME, 0)
    fun setChessTheme(ctx: Context, v: Int)  = prefs(ctx).edit().putInt(KEY_CHESS_THEME, v).apply()
    fun getCheckersTheme(ctx: Context)       = prefs(ctx).getInt(KEY_CHECKERS_THEME, 0)
    fun setCheckersTheme(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_CHECKERS_THEME, v).apply()
    fun getInternationalDraughtsTheme(ctx: Context) =
        prefs(ctx).getInt(KEY_INTERNATIONAL_DRAUGHTS_THEME, 0)
    fun setInternationalDraughtsTheme(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_INTERNATIONAL_DRAUGHTS_THEME, v).apply()
    fun getOthelloTheme(ctx: Context)        = prefs(ctx).getInt(KEY_OTHELLO_THEME, 0)
    fun setOthelloTheme(ctx: Context, v: Int)  = prefs(ctx).edit().putInt(KEY_OTHELLO_THEME, v).apply()
    fun getMorabarabaTheme(ctx: Context)     = prefs(ctx).getInt(KEY_MORABARABA_THEME, 0)
    fun setMorabarabaTheme(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_MORABARABA_THEME, v).apply()
    fun getTttTheme(ctx: Context)            = prefs(ctx).getInt(KEY_TTT_THEME, 0)
    fun setTttTheme(ctx: Context, v: Int)    = prefs(ctx).edit().putInt(KEY_TTT_THEME, v).apply()
    fun getConnectFourTheme(ctx: Context)    = prefs(ctx).getInt(KEY_CONNECT_FOUR_THEME, 0)
    fun setConnectFourTheme(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_CONNECT_FOUR_THEME, v).apply()
    fun getFoxAndGeeseTheme(ctx: Context) = prefs(ctx).getInt(KEY_FOX_AND_GEESE_THEME, 0)
    fun setFoxAndGeeseTheme(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_FOX_AND_GEESE_THEME, v).apply()
    fun getShogiTheme(ctx: Context) = prefs(ctx).getInt(KEY_SHOGI_THEME, 0)
    fun setShogiTheme(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_SHOGI_THEME, v).apply()

    fun activateGameTheme(ctx: Context, gameKey: String) {
        val idx = when (gameKey) {
            "chess"      -> getChessTheme(ctx)
            "checkers"   -> getCheckersTheme(ctx)
            "international_draughts" -> getInternationalDraughtsTheme(ctx)
            "othello"    -> getOthelloTheme(ctx)
            "morabaraba" -> getMorabarabaTheme(ctx)
            "ttt"        -> getTttTheme(ctx)
            "connect_four" -> getConnectFourTheme(ctx)
            "fox_and_geese" -> getFoxAndGeeseTheme(ctx)
            "shogi"        -> getShogiTheme(ctx)
            else         -> getTheme(ctx)
        }
        setTheme(ctx, idx)
    }

    // ── Stats data ────────────────────────────────────────────────────────────
    data class Stats(val wins: Int, val losses: Int, val draws: Int)

    fun getStats(ctx: Context, mode: GameMode = currentMode(ctx)) = Stats(
        wins     = prefsForMode(ctx, mode).getInt(KEY_STATS_WINS,     0),
        losses   = prefsForMode(ctx, mode).getInt(KEY_STATS_LOSSES,   0),
        draws    = prefsForMode(ctx, mode).getInt(KEY_STATS_DRAWS,    0)
    )

    fun getGameStats(
        ctx: Context,
        game: String,
        mode: GameMode = currentMode(ctx),
    ) = Stats(
        wins     = prefsForMode(ctx, mode).getInt(winKey(game),     0),
        losses   = prefsForMode(ctx, mode).getInt(lossKey(game),    0),
        draws    = prefsForMode(ctx, mode).getInt(drawKey(game),    0)
    )

    // ── Record outcomes — updates both global AND per-game counters ───────────

    fun recordWin(ctx: Context) {
        val game = activeGame(ctx)
        recordDifficultyWin(ctx, game)
        prefs(ctx).edit()
            .putInt(KEY_STATS_WINS,    prefs(ctx).getInt(KEY_STATS_WINS,    0) + 1)
            .putInt(winKey(game),      prefs(ctx).getInt(winKey(game),      0) + 1)
            .apply()
    }

    fun recordLoss(ctx: Context) {
        val game = activeGame(ctx)
        resetDifficultyWinStreak(ctx, game)
        prefs(ctx).edit()
            .putInt(KEY_STATS_LOSSES,  prefs(ctx).getInt(KEY_STATS_LOSSES,  0) + 1)
            .putInt(lossKey(game),     prefs(ctx).getInt(lossKey(game),     0) + 1)
            .apply()
    }

    fun recordDraw(ctx: Context) {
        val game = activeGame(ctx)
        resetDifficultyWinStreak(ctx, game)
        prefs(ctx).edit()
            .putInt(KEY_STATS_DRAWS,   prefs(ctx).getInt(KEY_STATS_DRAWS,   0) + 1)
            .putInt(drawKey(game),     prefs(ctx).getInt(drawKey(game),     0) + 1)
            .apply()
    }

    fun recordLudoResult(ctx: Context, won: Boolean) {
        setActiveGame(ctx, "ludo")
        if (won) recordWin(ctx) else recordLoss(ctx)
    }

    fun resetStats(ctx: Context, mode: GameMode = currentMode(ctx)) {
        val edit = prefsForMode(ctx, mode).edit()
        edit.putInt(KEY_STATS_WINS, 0).putInt(KEY_STATS_LOSSES, 0)
            .putInt(KEY_STATS_DRAWS, 0)
        for (g in listOf(
            "chess", "amazons", "checkers", "international_draughts",
            "othello", "morabaraba", "ttt", "connect_four", "overall",
            "fox_and_geese", "ludo", "shogi", "go", "snakes_ladders",
            "xiangqi", "mancala", "yote", "onitama", "five_field_kono",
        )) {
            edit.putInt(winKey(g), 0).putInt(lossKey(g), 0)
                .putInt(drawKey(g), 0)
        }
        edit.apply()
    }

    // ── Privacy consent ───────────────────────────────────────────────────────
    private const val KEY_CONSENT = "privacy_policy_accepted"
    fun hasConsentAccepted(ctx: Context) = sharedPrefs(ctx).getBoolean(KEY_CONSENT, false)
    fun setConsentAccepted(ctx: Context) =
        sharedPrefs(ctx).edit().putBoolean(KEY_CONSENT, true).apply()

    // ── Purchases ─────────────────────────────────────────────────────────────
    private const val KEY_ADS_REMOVED = "ads_removed"

    fun isAdsRemoved(ctx: Context): Boolean =
        sharedPrefs(ctx).getBoolean(KEY_ADS_REMOVED, false)

    fun setAdsRemoved(ctx: Context, removed: Boolean = true) {
        sharedPrefs(ctx).edit().putBoolean(KEY_ADS_REMOVED, removed).apply()
    }
}
