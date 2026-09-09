package com.mkdev.mkboardgames

import android.content.Context
import android.media.MediaPlayer

/**
 * App-wide background music player.
 *
 * The player is paused, rather than released, when music is disabled or the
 * app is backgrounded. Mode selection, active matches, and in-game pause
 * screens each have their own looping soundtrack.
 */
object MusicPlayer {

    private const val NORMAL_TRACK = 0
    private const val IRREGULAR_TRACK = 1
    private const val MATCH_TRACK = 2
    private const val MODE_SELECTION_ONITAMA_TRACK = 3
    private const val PAUSED_MATCH_TRACK = 4

    private val tracks = intArrayOf(
        R.raw.in_game_selection_screen1,
        R.raw.in_game_selection_screen2,
        R.raw.in_match,
        R.raw.mode_selection_screen_and_onitama_in_match_only,
        R.raw.paused_match,
    )

    private var player: MediaPlayer? = null
    private var activeTrack = -1
    private var requestedTrack = -1
    private var modeSessionActive = false
    private var appInForeground = true
    private var applicationContext: Context? = null
    private var matchTrack = MATCH_TRACK

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

    /** Switch to the standard match soundtrack for the current game. */
    fun enterMatch(ctx: Context) {
        switchToTrack(ctx, MATCH_TRACK)
    }

    /** Switch to the dedicated Onitama match soundtrack. */
    fun enterOnitamaMatch(ctx: Context) {
        switchToTrack(ctx, MODE_SELECTION_ONITAMA_TRACK)
    }

    /** Switch to the soundtrack used by an in-game pause overlay. */
    fun enterPausedMatch(ctx: Context) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        requestedTrack = PAUSED_MATCH_TRACK
        switchToRequestedTrack()
    }

    /** Restore the active match soundtrack after a pause overlay is dismissed. */
    fun resumeMatch(ctx: Context) {
        switchToTrack(ctx, matchTrack)
    }

    private fun switchToTrack(ctx: Context, track: Int) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        if (track != PAUSED_MATCH_TRACK) matchTrack = track
        requestedTrack = track
        switchToRequestedTrack()
    }

    private fun switchToRequestedTrack() {
        if (activeTrack != requestedTrack) {
            releasePlayer()
            activeTrack = requestedTrack
        }
        resumeIfAllowed()
    }

    /** Play the dedicated mode-selection soundtrack. */
    fun enterModeSelection(ctx: Context) {
        switchToTrack(ctx, MODE_SELECTION_ONITAMA_TRACK)
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
        newPlayer.isLooping = true
        newPlayer.setOnErrorListener { failedPlayer, _, _ ->
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