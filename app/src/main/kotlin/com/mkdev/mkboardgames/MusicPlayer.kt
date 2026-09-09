package com.mkdev.mkboardgames

import android.content.Context
import android.media.MediaPlayer
import kotlin.random.Random

/**
 * App-wide background music player.
 *
 * Music is deliberately separate from [SoundPlayer], so short game effects
 * can continue to use SoundPool without interrupting the current track.
 */
object MusicPlayer {

    private val tracks = intArrayOf(
        R.raw.nebulite_summer_time,
        R.raw.pufino_enlivening,
    )

    private var player: MediaPlayer? = null
    private var currentTrack = -1
    private var applicationContext: Context? = null

    fun isEnabled(ctx: Context): Boolean = SettingsManager.isMusicEnabled(ctx)

    /** Starts or resumes music when the user has enabled it. */
    fun start(ctx: Context) {
        applicationContext = ctx.applicationContext
        if (!SettingsManager.isMusicEnabled(ctx)) return

        val existing = player
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        playRandomTrack()
    }

    /** Toggles background music and returns the new enabled state. */
    fun toggle(ctx: Context): Boolean {
        val enabled = !SettingsManager.isMusicEnabled(ctx)
        SettingsManager.setMusicEnabled(ctx, enabled)
        if (enabled) start(ctx) else stop()
        return enabled
    }

    fun stop() {
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
    }

    private fun playRandomTrack() {
        val ctx = applicationContext ?: return
        if (!SettingsManager.isMusicEnabled(ctx)) return

        val nextTrack = if (tracks.size == 1) {
            0
        } else {
            var candidate: Int
            do {
                candidate = Random.nextInt(tracks.size)
            } while (candidate == currentTrack)
            candidate
        }
        currentTrack = nextTrack

        val nextPlayer = MediaPlayer.create(ctx, tracks[nextTrack]) ?: return
        nextPlayer.setOnCompletionListener {
            player?.release()
            player = null
            playRandomTrack()
        }
        nextPlayer.setOnErrorListener { failedPlayer, _, _ ->
            failedPlayer.release()
            if (player === failedPlayer) player = null
            playRandomTrack()
            true
        }
        nextPlayer.setVolume(0.7f, 0.7f)
        player = nextPlayer
        nextPlayer.start()
    }
}