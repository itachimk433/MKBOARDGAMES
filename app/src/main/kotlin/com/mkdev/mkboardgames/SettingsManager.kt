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
    private const val KEY_MANCALA_MOVEMENT_SPEED = "mancala_movement_speed"

    // ── Hints keys ───────────────────────────────────────────────────────────
    private const val KEY_CHESS_HINTS    = "chess_show_hints"
    private const val KEY_CHECKERS_HINTS = "checkers_show_hints"

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
    private const val KEY_STATS_FORFEITS = "stats_forfeits"

    // ── Per-game stats keys ───────────────────────────────────────────────────
    private fun winKey(game: String)     = "stats_${game}_wins"
    private fun lossKey(game: String)    = "stats_${game}_losses"
    private fun drawKey(game: String)    = "stats_${game}_draws"
    private fun forfeitKey(game: String) = "stats_${game}_forfeits"

    // active game tag — set at the start of every vs-AI game
    private const val KEY_ACTIVE_GAME = "active_game_tag"

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

    // ── Active game tag ───────────────────────────────────────────────────────
    fun setActiveGame(ctx: Context, gameTag: String) =
        prefs(ctx).edit().putString(KEY_ACTIVE_GAME, gameTag).apply()

    private fun activeGame(ctx: Context) =
        prefs(ctx).getString(KEY_ACTIVE_GAME, "overall") ?: "overall"

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

    fun getChessDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_CHESS_DIFFICULTY, 1).coerceIn(0, 3)
    fun setChessDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_CHESS_DIFFICULTY, v.coerceIn(0, 3)).apply()
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
        prefs(ctx).getInt(KEY_CHECKERS_DIFFICULTY, 0).coerceIn(0, 2)

    fun setCheckersDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_CHECKERS_DIFFICULTY, v.coerceIn(0, 2)).apply()

    fun checkersAiDepthForLevel(level: Int) = when (level.coerceIn(0, 2)) {
        0 -> 2
        2 -> 8
        else -> 5
    }

    fun checkersAiDepth(ctx: Context) =
        checkersAiDepthForLevel(getCheckersDifficulty(ctx))

    // ── International Draughts ──────────────────────────────────────────────
    fun getInternationalDraughtsDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY, 0).coerceIn(0, 2)

    fun setInternationalDraughtsDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit()
            .putInt(KEY_INTERNATIONAL_DRAUGHTS_DIFFICULTY, v.coerceIn(0, 2))
            .apply()

    fun internationalDraughtsAiDepthForLevel(level: Int) = when (level.coerceIn(0, 2)) {
        0 -> 2
        2 -> 8
        else -> 5
    }

    fun internationalDraughtsAiDepth(ctx: Context) =
        internationalDraughtsAiDepthForLevel(getInternationalDraughtsDifficulty(ctx))

    // ── Othello — always hard (no user-facing difficulty) ────────────────────
    fun othelloAiDepth(@Suppress("UNUSED_PARAMETER") ctx: Context) = 7

    // ── Morabaraba ───────────────────────────────────────────────────────────
    fun getMorabarabaDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_MORABARABA_DIFFICULTY, 0)
    fun setMorabarabaDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_MORABARABA_DIFFICULTY, v).apply()
    fun morabarabaAiDepth(ctx: Context) = when (getMorabarabaDifficulty(ctx)) { 0 -> 3; 2 -> 7; else -> 5 }
    fun morabarabaAiTimeLimitMs(ctx: Context): Long = when (getMorabarabaDifficulty(ctx)) { 0 -> 600L; 2 -> 2500L; else -> 1200L }

    // ── Connect Four ─────────────────────────────────────────────────────────
    fun getConnectFourDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_CONNECT_FOUR_DIFFICULTY, 0)
    fun setConnectFourDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_CONNECT_FOUR_DIFFICULTY, v).apply()
    fun connectFourAiDepth(ctx: Context) = when (getConnectFourDifficulty(ctx)) {
        0 -> 3
        2 -> 7
        else -> 5
    }
    fun connectFourAiTimeLimitMs(ctx: Context): Long =
        when (getConnectFourDifficulty(ctx)) { 0 -> 700L; 2 -> 2500L; else -> 1400L }

    // ── Fox and Geese ────────────────────────────────────────────────────────
    fun getFoxAndGeeseDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_FOX_AND_GEESE_DIFFICULTY, 0)
    fun setFoxAndGeeseDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_FOX_AND_GEESE_DIFFICULTY, v).apply()
    fun foxAndGeeseAiDepth(ctx: Context) =
        when (getFoxAndGeeseDifficulty(ctx)) { 0 -> 2; 2 -> 6; else -> 4 }
    fun foxAndGeeseAiTimeLimitMs(ctx: Context): Long =
        when (getFoxAndGeeseDifficulty(ctx)) { 0 -> 700L; 2 -> 2400L; else -> 1300L }

    // ── Ludo ─────────────────────────────────────────────────────────────────
    fun getLudoDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_LUDO_DIFFICULTY, 0)
    fun setLudoDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_LUDO_DIFFICULTY, v.coerceIn(0, 2)).apply()

    // ── Xiangqi ───────────────────────────────────────────────────────────────
    data class XiangqiAiProfile(
        val depth: Int,
        val timeLimitMs: Long,
        val quiesceDepth: Int,
        val varietyWindow: Int,
    )

    fun getXiangqiDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_XIANGQI_DIFFICULTY, 1).coerceIn(0, 2)

    fun setXiangqiDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit()
            .putInt(KEY_XIANGQI_DIFFICULTY, v.coerceIn(0, 2))
            .apply()

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
        prefs(ctx).getInt(KEY_SHOGI_DIFFICULTY, 0).coerceIn(0, 3)
    fun setShogiDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_SHOGI_DIFFICULTY, v.coerceIn(0, 3)).apply()
    fun shogiAiDepth(ctx: Context) = when (getShogiDifficulty(ctx)) {
        0 -> 1
        1 -> 2
        2 -> 3
        else -> 4
    }
    fun shogiAiTimeLimitMs(ctx: Context): Long = when (getShogiDifficulty(ctx)) {
        0 -> 700L
        1 -> 1200L
        2 -> 2200L
        else -> 4200L
    }

    // ── Go ────────────────────────────────────────────────────────────────────
    fun getGoDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_GO_DIFFICULTY, 0)
    fun setGoDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_GO_DIFFICULTY, v.coerceIn(0, 2)).apply()

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
        prefs(ctx).getInt(KEY_MANCALA_DIFFICULTY, 1).coerceIn(0, 2)

    fun setMancalaDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_MANCALA_DIFFICULTY, v.coerceIn(0, 2)).apply()

    fun mancalaAiDepth(ctx: Context) = mancalaAiProfileForLevel(getMancalaDifficulty(ctx)).depth

    // ── Yoté ──────────────────────────────────────────────────────────────────
    fun getYoteDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_YOTE_DIFFICULTY, 1).coerceIn(0, 2)

    fun setYoteDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_YOTE_DIFFICULTY, v.coerceIn(0, 2)).apply()

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
        prefs(ctx).getInt(KEY_FIVE_FIELD_KONO_DIFFICULTY, 1).coerceIn(0, 2)

    fun setFiveFieldKonoDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_FIVE_FIELD_KONO_DIFFICULTY, v.coerceIn(0, 2)).apply()

    fun fiveFieldKonoAiDepth(ctx: Context) =
        fiveFieldKonoAiProfileForLevel(getFiveFieldKonoDifficulty(ctx)).depth

    fun fiveFieldKonoAiTimeLimitMs(ctx: Context): Long =
        fiveFieldKonoAiProfileForLevel(getFiveFieldKonoDifficulty(ctx)).timeLimitMs

    // ── Onitama ──────────────────────────────────────────────────────────────
    fun getOnitamaDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_ONITAMA_DIFFICULTY, 1).coerceIn(0, 2)

    fun setOnitamaDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_ONITAMA_DIFFICULTY, v.coerceIn(0, 2)).apply()

    fun onitamaAiDepth(ctx: Context) = when (getOnitamaDifficulty(ctx)) {
        0 -> 2
        2 -> 4
        else -> 3
    }

    // ── Amazons ───────────────────────────────────────────────────────────────
    data class AmazonsAiProfile(val depth: Int, val timeLimitMs: Long)

    fun amazonsAiProfileForLevel(level: Int): AmazonsAiProfile =
        when (level.coerceIn(0, 2)) {
            0 -> AmazonsAiProfile(depth = 1, timeLimitMs = 900L)
            1 -> AmazonsAiProfile(depth = 2, timeLimitMs = 1600L)
            else -> AmazonsAiProfile(depth = 3, timeLimitMs = 2600L)
        }

    fun getAmazonsDifficulty(ctx: Context) =
        prefs(ctx).getInt(KEY_AMAZONS_DIFFICULTY, 1).coerceIn(0, 2)

    fun setAmazonsDifficulty(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_AMAZONS_DIFFICULTY, v.coerceIn(0, 2)).apply()

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
    fun getTttDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_TTT_DIFFICULTY, 0)
    fun setTttDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_TTT_DIFFICULTY, v).apply()
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

    // ── Motion dice ───────────────────────────────────────────────────────────
    private const val KEY_MOTION_DICE = "motion_dice"
    fun isMotionDiceEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_MOTION_DICE, false)
    fun setMotionDiceEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MOTION_DICE, v).apply()

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
        CLASSIC("Classic"),
        BROWN("Brown"),
    }

    fun getHomeStyle(ctx: Context): HomeStyle {
        return when (prefs(ctx).getString(KEY_HOME_STYLE, null)) {
            HomeStyle.BROWN.name -> HomeStyle.BROWN
            HomeStyle.CLASSIC.name -> HomeStyle.CLASSIC
            else -> if (isBrownHomeStyleEnabled(ctx)) HomeStyle.BROWN else HomeStyle.CLASSIC
        }
    }

    fun setHomeStyle(ctx: Context, style: HomeStyle) {
        prefs(ctx).edit()
            .putString(KEY_HOME_STYLE, style.name)
            .putBoolean(KEY_HOME_BACKGROUND, style == HomeStyle.BROWN)
            .putBoolean(KEY_WOOD_GAME_CARDS, style == HomeStyle.BROWN)
            .apply()
    }

    fun isWoodGameCardStyleEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_WOOD_GAME_CARDS, true)
    fun setWoodGameCardStyleEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_WOOD_GAME_CARDS, v).apply()
    fun isBrownHomeStyleEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_HOME_BACKGROUND, true) &&
            prefs(ctx).getBoolean(KEY_WOOD_GAME_CARDS, true)
    fun setBrownHomeStyleEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit()
            .putString(KEY_HOME_STYLE, if (v) HomeStyle.BROWN.name else HomeStyle.CLASSIC.name)
            .putBoolean(KEY_HOME_BACKGROUND, v)
            .putBoolean(KEY_WOOD_GAME_CARDS, v)
            .apply()

    // ── Hints ────────────────────────────────────────────────────────────────
    fun getChessHints(ctx: Context) = prefs(ctx).getBoolean(KEY_CHESS_HINTS, true)
    fun setChessHints(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_CHESS_HINTS, v).apply()
    fun getCheckersHints(ctx: Context) = prefs(ctx).getBoolean(KEY_CHECKERS_HINTS, true)
    fun setCheckersHints(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_CHECKERS_HINTS, v).apply()
    fun getShowHints(ctx: Context) = getChessHints(ctx)
    fun setShowHints(ctx: Context, v: Boolean) = setChessHints(ctx, v)

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
    data class Stats(val wins: Int, val losses: Int, val draws: Int, val forfeits: Int)

    fun getStats(ctx: Context, mode: GameMode = currentMode(ctx)) = Stats(
        wins     = prefsForMode(ctx, mode).getInt(KEY_STATS_WINS,     0),
        losses   = prefsForMode(ctx, mode).getInt(KEY_STATS_LOSSES,   0),
        draws    = prefsForMode(ctx, mode).getInt(KEY_STATS_DRAWS,    0),
        forfeits = prefsForMode(ctx, mode).getInt(KEY_STATS_FORFEITS, 0)
    )

    fun getGameStats(
        ctx: Context,
        game: String,
        mode: GameMode = currentMode(ctx),
    ) = Stats(
        wins     = prefsForMode(ctx, mode).getInt(winKey(game),     0),
        losses   = prefsForMode(ctx, mode).getInt(lossKey(game),    0),
        draws    = prefsForMode(ctx, mode).getInt(drawKey(game),    0),
        forfeits = prefsForMode(ctx, mode).getInt(forfeitKey(game), 0)
    )

    // ── Record outcomes — updates both global AND per-game counters ───────────

    fun recordWin(ctx: Context) {
        val game = activeGame(ctx)
        prefs(ctx).edit()
            .putInt(KEY_STATS_WINS,    prefs(ctx).getInt(KEY_STATS_WINS,    0) + 1)
            .putInt(winKey(game),      prefs(ctx).getInt(winKey(game),      0) + 1)
            .apply()
    }

    fun recordLoss(ctx: Context) {
        val game = activeGame(ctx)
        prefs(ctx).edit()
            .putInt(KEY_STATS_LOSSES,  prefs(ctx).getInt(KEY_STATS_LOSSES,  0) + 1)
            .putInt(lossKey(game),     prefs(ctx).getInt(lossKey(game),     0) + 1)
            .apply()
    }

    fun recordDraw(ctx: Context) {
        val game = activeGame(ctx)
        prefs(ctx).edit()
            .putInt(KEY_STATS_DRAWS,   prefs(ctx).getInt(KEY_STATS_DRAWS,   0) + 1)
            .putInt(drawKey(game),     prefs(ctx).getInt(drawKey(game),     0) + 1)
            .apply()
    }

    fun recordForfeit(ctx: Context) {
        val game = activeGame(ctx)
        prefs(ctx).edit()
            .putInt(KEY_STATS_FORFEITS, prefs(ctx).getInt(KEY_STATS_FORFEITS, 0) + 1)
            .putInt(forfeitKey(game),   prefs(ctx).getInt(forfeitKey(game),   0) + 1)
            .apply()
    }

    fun recordLudoResult(ctx: Context, won: Boolean) {
        setActiveGame(ctx, "ludo")
        if (won) recordWin(ctx) else recordLoss(ctx)
    }

    fun resetStats(ctx: Context, mode: GameMode = currentMode(ctx)) {
        val edit = prefsForMode(ctx, mode).edit()
        edit.putInt(KEY_STATS_WINS, 0).putInt(KEY_STATS_LOSSES, 0)
            .putInt(KEY_STATS_DRAWS, 0).putInt(KEY_STATS_FORFEITS, 0)
        for (g in listOf(
            "chess", "amazons", "checkers", "international_draughts",
            "othello", "morabaraba", "ttt", "connect_four", "overall",
            "fox_and_geese", "ludo", "shogi", "go"
        )) {
            edit.putInt(winKey(g), 0).putInt(lossKey(g), 0)
                .putInt(drawKey(g), 0).putInt(forfeitKey(g), 0)
        }
        edit.apply()
    }

    // ── Privacy consent ───────────────────────────────────────────────────────
    private const val KEY_CONSENT = "privacy_policy_accepted"
    fun hasConsentAccepted(ctx: Context) = sharedPrefs(ctx).getBoolean(KEY_CONSENT, false)
    fun setConsentAccepted(ctx: Context) =
        sharedPrefs(ctx).edit().putBoolean(KEY_CONSENT, true).apply()
}
