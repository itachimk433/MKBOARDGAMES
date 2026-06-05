package com.nexboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Lightweight board game sound player backed by [SoundPool].
 * Call [init] once in Activity.onCreate and [play] to fire a sound.
 */
object SoundPlayer {

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        val p = pool ?: return

        // Chess sounds (unchanged)
        ids["game_start"]       = p.load(ctx, R.raw.game_start,       1)
        ids["game_end"]         = p.load(ctx, R.raw.game_end,         1)
        ids["capture"]          = p.load(ctx, R.raw.capture,          1)
        ids["castle"]           = p.load(ctx, R.raw.castle,           1)
        ids["move_self"]        = p.load(ctx, R.raw.move_self,        1)
        ids["move_opponent"]    = p.load(ctx, R.raw.move_opponent,    1)
        ids["move_check"]       = p.load(ctx, R.raw.move_check,       1)
        ids["promote"]          = p.load(ctx, R.raw.promote,          1)
        ids["illegal"]          = p.load(ctx, R.raw.illegal,          1)
        ids["game_draw"]        = p.load(ctx, R.raw.game_draw,        1)

        // Checkers sounds (upgraded)
        ids["checkers_move"]    = p.load(ctx, R.raw.checkers_move,    1)
        ids["checkers_capture"] = p.load(ctx, R.raw.checkers_capture, 1)
        ids["checkers_king"]    = p.load(ctx, R.raw.checkers_king,    1)

        // Othello sounds (new)
        ids["othello_place"]    = p.load(ctx, R.raw.othello_place,    1)
        ids["othello_flip"]     = p.load(ctx, R.raw.othello_flip,     1)

        // Morabaraba sounds (new)
        ids["mora_place"]       = p.load(ctx, R.raw.mora_place,       1)
        ids["mora_move"]        = p.load(ctx, R.raw.mora_move,        1)
        ids["mora_capture"]     = p.load(ctx, R.raw.mora_capture,     1)
        ids["mora_mill"]        = p.load(ctx, R.raw.mora_mill,        1)

        // Tic Tac Toe sounds (new)
        ids["ttt_x"]            = p.load(ctx, R.raw.ttt_x,            1)
        ids["ttt_o"]            = p.load(ctx, R.raw.ttt_o,            1)

        // UI navigation click (reuses mora_place — short, clicky)
        ids["ui_click"]         = p.load(ctx, R.raw.mora_place,       1)

        ready = true
    }

    /** Set to false to silence movement/game sounds while keeping UI click sounds audible. */
    var movementSoundsEnabled: Boolean = true

    fun play(key: String, volume: Float = 1f) {
        ids[key]?.let { pool?.play(it, volume, volume, 0, 0, 1f) }
    }

    /** Play a movement/game sound only when movement sounds are enabled. */
    fun playMovement(key: String, volume: Float = 1f) {
        if (movementSoundsEnabled) play(key, volume)
    }
}
