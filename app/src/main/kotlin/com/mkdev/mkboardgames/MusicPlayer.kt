package com.mkdev.mkboardgames

import android.content.Context
import android.media.MediaPlayer

/**
 * App-wide background music player.
 *
 * The player is paused, rather than released, when music is disabled, the app
 * is backgrounded, or the user returns to mode selection. This preserves the
 * current position when music is enabled again.
 */
object MusicPlayer {

    private const val NORMAL_TRACK = 0
    private const val IRREGULAR_TRACK = 1

    private val tracks = intArrayOf(
        R.raw.pufino_enlivening,
        R.raw.nebulite_summer_time,
    )

    private var player: MediaPlayer? = null
    private var activeTrack = -1
    private var requestedTrack = -1
    private var modeSessionActive = false
    private var appInForeground = true
    private var applicationContext: Context? = null

    /** Begin the selected mode's music session and resume from its saved position. */
    fun playForMode(ctx: Context, mode: GameMode) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        requestedTrack = if (mode == GameMode.IRREGULAR) IRREGULAR_TRACK else NORMAL_TRACK

        if (activeTrack != requestedTrack) {
            releasePlayer()
            activeTrack = requestedTrack
        }
        resumeIfAllowed()
    }

    /** Leaves the music paused while the mode-selection screen is visible. */
    fun enterModeSelection() {
        modeSessionActive = false
        pause()
    }

    /** Called by the application when no app activity is visible. */
    fun onAppBackground() {
        appInForeground = false
        pause()
    }

    /** Called by the application when the app becomes visible again. */
    fun onAppForeground() {
        appInForeground = true
        resumeIfAllowed()
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        applicationContext = ctx.applicationContext
        SettingsManager.setMusicEnabled(ctx, enabled)
        if (enabled) resumeIfAllowed() else pause()
    }

    fun setVolume(ctx: Context, volumePercent: Int) {
        val volume = volumePercent.coerceIn(0, 100)
        SettingsManager.setMusicVolume(ctx, volume)
        val scaledVolume = volume / 100f
        player?.setVolume(scaledVolume, scaledVolume)
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    private fun resumeIfAllowed() {
        val ctx = applicationContext ?: return
        if (!modeSessionActive || !appInForeground || !SettingsManager.isMusicEnabled(ctx)) return

        val existing = player
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        if (requestedTrack !in tracks.indices) return

        val newPlayer = MediaPlayer.create(ctx, tracks[requestedTrack]) ?: return
        val volume = SettingsManager.getMusicVolume(ctx) / 100f
        newPlayer.setVolume(volume, volume)
        newPlayer.setOnCompletionListener { completedPlayer ->
            completedPlayer.setOnCompletionListener(null)
            completedPlayer.setOnErrorListener(null)
            completedPlayer.release()
            if (player === completedPlayer) player = null
            resumeIfAllowed()
        }
        newPlayer.setOnErrorListener { failedPlayer, _, _ ->
            failedPlayer.setOnCompletionListener(null)
            failedPlayer.release()
            if (player === failedPlayer) player = null
            resumeIfAllowed()
            true
        }
        activeTrack = requestedTrack
        player = newPlayer
        newPlayer.start()
    }

    private fun releasePlayer() {
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
    }
}