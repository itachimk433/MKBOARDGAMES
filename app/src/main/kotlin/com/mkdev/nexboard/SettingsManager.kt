package com.mkdev.nexboard

import android.content.Context

object SettingsManager {

    private const val PREFS = "nexboard_prefs"

    // ── Difficulty keys ──────────────────────────────────────────────────────
    private const val KEY_CHESS_DIFFICULTY      = "chess_ai_difficulty"
    private const val KEY_CHECKERS_DIFFICULTY   = "checkers_ai_difficulty"
    private const val KEY_MORABARABA_DIFFICULTY = "morabaraba_ai_difficulty"

    // ── Hints keys ───────────────────────────────────────────────────────────
    private const val KEY_CHESS_HINTS    = "chess_show_hints"
    private const val KEY_CHECKERS_HINTS = "checkers_show_hints"

    // ── Global + per-game theme keys ─────────────────────────────────────────
    private const val KEY_THEME            = "board_theme"
    private const val KEY_CHESS_THEME      = "chess_theme"
    private const val KEY_CHECKERS_THEME   = "checkers_theme"
    private const val KEY_OTHELLO_THEME    = "othello_theme"
    private const val KEY_MORABARABA_THEME = "morabaraba_theme"
    private const val KEY_TTT_THEME        = "ttt_theme"

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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Active game tag ───────────────────────────────────────────────────────
    fun setActiveGame(ctx: Context, gameTag: String) =
        prefs(ctx).edit().putString(KEY_ACTIVE_GAME, gameTag).apply()

    private fun activeGame(ctx: Context) =
        prefs(ctx).getString(KEY_ACTIVE_GAME, "overall") ?: "overall"

    // ── Chess ────────────────────────────────────────────────────────────────
    fun getChessDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_CHESS_DIFFICULTY, 0)
    fun setChessDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_CHESS_DIFFICULTY, v).apply()
    fun chessAiDepth(ctx: Context) = when (getChessDifficulty(ctx)) {
        0    -> 2   // Easy
        2    -> 6   // Hard  — deep search + quiescence
        else -> 4   // Medium
    }
    fun chessAiTimeLimitMs(ctx: Context): Long = when (getChessDifficulty(ctx)) {
        0    -> 600L
        2    -> 2500L
        else -> 1400L
    }
    fun chessAiQuiesceDepth(ctx: Context): Int = when (getChessDifficulty(ctx)) {
        0    -> 0
        2    -> 3
        else -> 2
    }

    // ── Checkers ─────────────────────────────────────────────────────────────
    fun getCheckersDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_CHECKERS_DIFFICULTY, 0)
    fun setCheckersDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_CHECKERS_DIFFICULTY, v).apply()
    fun checkersAiDepth(ctx: Context) = when (getCheckersDifficulty(ctx)) { 0 -> 2; 2 -> 8; else -> 5 }

    // ── Othello — always hard (no user-facing difficulty) ────────────────────
    fun othelloAiDepth(@Suppress("UNUSED_PARAMETER") ctx: Context) = 7

    // ── Morabaraba ───────────────────────────────────────────────────────────
    fun getMorabarabaDifficulty(ctx: Context) = prefs(ctx).getInt(KEY_MORABARABA_DIFFICULTY, 0)
    fun setMorabarabaDifficulty(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_MORABARABA_DIFFICULTY, v).apply()
    fun morabarabaAiDepth(ctx: Context) = when (getMorabarabaDifficulty(ctx)) { 0 -> 3; 2 -> 7; else -> 5 }
    fun morabarabaAiTimeLimitMs(ctx: Context): Long = when (getMorabarabaDifficulty(ctx)) { 0 -> 600L; 2 -> 2500L; else -> 1200L }

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

    // ── Light mode ───────────────────────────────────────────────────────────
    private const val KEY_LIGHT_MODE = "light_mode"
    fun isLightMode(ctx: Context) = prefs(ctx).getBoolean(KEY_LIGHT_MODE, false)
    fun setLightMode(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_LIGHT_MODE, v).apply()

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
    fun getOthelloTheme(ctx: Context)        = prefs(ctx).getInt(KEY_OTHELLO_THEME, 0)
    fun setOthelloTheme(ctx: Context, v: Int)  = prefs(ctx).edit().putInt(KEY_OTHELLO_THEME, v).apply()
    fun getMorabarabaTheme(ctx: Context)     = prefs(ctx).getInt(KEY_MORABARABA_THEME, 0)
    fun setMorabarabaTheme(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_MORABARABA_THEME, v).apply()
    fun getTttTheme(ctx: Context)            = prefs(ctx).getInt(KEY_TTT_THEME, 0)
    fun setTttTheme(ctx: Context, v: Int)    = prefs(ctx).edit().putInt(KEY_TTT_THEME, v).apply()

    fun activateGameTheme(ctx: Context, gameKey: String) {
        val idx = when (gameKey) {
            "chess"      -> getChessTheme(ctx)
            "checkers"   -> getCheckersTheme(ctx)
            "international_draughts" -> getCheckersTheme(ctx)
            "othello"    -> getOthelloTheme(ctx)
            "morabaraba" -> getMorabarabaTheme(ctx)
            "ttt"        -> getTttTheme(ctx)
            else         -> getTheme(ctx)
        }
        setTheme(ctx, idx)
    }

    // ── Stats data ────────────────────────────────────────────────────────────
    data class Stats(val wins: Int, val losses: Int, val draws: Int, val forfeits: Int)

    fun getStats(ctx: Context) = Stats(
        wins     = prefs(ctx).getInt(KEY_STATS_WINS,     0),
        losses   = prefs(ctx).getInt(KEY_STATS_LOSSES,   0),
        draws    = prefs(ctx).getInt(KEY_STATS_DRAWS,    0),
        forfeits = prefs(ctx).getInt(KEY_STATS_FORFEITS, 0)
    )

    fun getGameStats(ctx: Context, game: String) = Stats(
        wins     = prefs(ctx).getInt(winKey(game),     0),
        losses   = prefs(ctx).getInt(lossKey(game),    0),
        draws    = prefs(ctx).getInt(drawKey(game),    0),
        forfeits = prefs(ctx).getInt(forfeitKey(game), 0)
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

    fun resetStats(ctx: Context) {
        val edit = prefs(ctx).edit()
        edit.putInt(KEY_STATS_WINS, 0).putInt(KEY_STATS_LOSSES, 0)
            .putInt(KEY_STATS_DRAWS, 0).putInt(KEY_STATS_FORFEITS, 0)
        for (g in listOf(
            "chess", "checkers", "international_draughts",
            "othello", "morabaraba", "ttt", "overall"
        )) {
            edit.putInt(winKey(g), 0).putInt(lossKey(g), 0)
                .putInt(drawKey(g), 0).putInt(forfeitKey(g), 0)
        }
        edit.apply()
    }

    // ── Privacy consent ───────────────────────────────────────────────────────
    private const val KEY_CONSENT = "privacy_policy_accepted"
    fun hasConsentAccepted(ctx: Context) = prefs(ctx).getBoolean(KEY_CONSENT, false)
    fun setConsentAccepted(ctx: Context) = prefs(ctx).edit().putBoolean(KEY_CONSENT, true).apply()
}
