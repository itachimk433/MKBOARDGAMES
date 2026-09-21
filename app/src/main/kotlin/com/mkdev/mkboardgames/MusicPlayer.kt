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
    private val trackPositionsMs = IntArray(tracks.size)
    private var activeUsesMatchVolume = false
    private var requestedUsesMatchVolume = false
    private var playerPrepared = false
    private var prepareGeneration = 0

    /** Begin the selected mode's music session from the beginning.
     *
     * Game-selection music intentionally starts fresh whenever the user enters
     * that screen. Its saved position is not reused, unlike the other tracks.
     */
    fun playForMode(ctx: Context, mode: GameMode) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        requestedTrack = if (mode == GameMode.IRREGULAR) IRREGULAR_TRACK else NORMAL_TRACK
        requestedUsesMatchVolume = false
        resetTrackPosition(requestedTrack)
        switchToRequestedTrack()
    }

    /** Switch to the standard match soundtrack for the current game. */
    fun enterMatch(ctx: Context) {
        switchToTrack(ctx, MATCH_TRACK, usesMatchVolume = true)
    }

    /** Switch to the dedicated Onitama match soundtrack. */
    fun enterOnitamaMatch(ctx: Context) {
        switchToTrack(ctx, MODE_SELECTION_ONITAMA_TRACK, usesMatchVolume = true)
    }

    /** Switch to the soundtrack used by an in-game pause overlay. */
    fun enterPausedMatch(ctx: Context) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        requestedTrack = PAUSED_MATCH_TRACK
        requestedUsesMatchVolume = true
        switchToRequestedTrack()
    }

    /** Restore the active match soundtrack after a pause overlay is dismissed. */
    fun resumeMatch(ctx: Context) {
        switchToTrack(ctx, matchTrack, usesMatchVolume = true)
    }

    private fun switchToTrack(ctx: Context, track: Int, usesMatchVolume: Boolean) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        if (track != PAUSED_MATCH_TRACK) matchTrack = track
        requestedTrack = track
        requestedUsesMatchVolume = usesMatchVolume
        switchToRequestedTrack()
    }

    private fun switchToRequestedTrack() {
        if (activeTrack != requestedTrack) {
            releasePlayer()
            activeTrack = requestedTrack
            activeUsesMatchVolume = requestedUsesMatchVolume
        } else if (activeUsesMatchVolume != requestedUsesMatchVolume) {
            activeUsesMatchVolume = requestedUsesMatchVolume
            applyVolume()
        }
        resumeIfAllowed()
    }

    /** Play the dedicated mode-selection soundtrack. */
    fun enterModeSelection(ctx: Context) {
        switchToTrack(ctx, MODE_SELECTION_ONITAMA_TRACK, usesMatchVolume = false)
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
        if (!activeUsesMatchVolume) applyVolume()
    }

    fun setMatchVolume(ctx: Context, volumePercent: Int) {
        val volume = volumePercent.coerceIn(0, 100)
        SettingsManager.setMatchMusicVolume(ctx, volume)
        if (activeUsesMatchVolume) applyVolume()
    }

    fun pause() {
        if (playerPrepared) {
            player?.takeIf { it.isPlaying }?.pause()
        }
    }

    private fun resumeIfAllowed() {
        val ctx = applicationContext ?: return
        if (!modeSessionActive || !appInForeground || !SettingsManager.isMusicEnabled(ctx)) return

        val existing = player
        if (existing != null) {
            applyVolume(existing)
            if (playerPrepared && !existing.isPlaying) existing.start()
            return
        }
        if (requestedTrack !in tracks.indices) return

        val track = requestedTrack
        val generation = ++prepareGeneration
        val newPlayer = MediaPlayer()
        val descriptor = ctx.resources.openRawResourceFd(tracks[track]) ?: run {
            newPlayer.release()
            return
        }
        playerPrepared = false
        try {
            descriptor.use {
                newPlayer.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            newPlayer.isLooping = true
        } catch (_: Exception) {
            newPlayer.release()
            return
        }
        newPlayer.setOnErrorListener { failedPlayer, _, _ ->
            failedPlayer.release()
            if (player === failedPlayer) player = null
            playerPrepared = false
            if (generation == prepareGeneration) resumeIfAllowed()
            true
        }
        newPlayer.setOnPreparedListener { preparedPlayer ->
            val stillRequested = generation == prepareGeneration &&
                player === preparedPlayer &&
                requestedTrack == track &&
                modeSessionActive &&
                appInForeground &&
                SettingsManager.isMusicEnabled(ctx)
            if (!stillRequested) {
                preparedPlayer.release()
                if (player === preparedPlayer) {
                    player = null
                    playerPrepared = false
                }
                return@setOnPreparedListener
            }
            playerPrepared = true
            applyVolume(preparedPlayer)
            val savedPosition = trackPositionsMs[track]
            if (savedPosition > 0 && savedPosition < preparedPlayer.duration) {
                preparedPlayer.seekTo(savedPosition)
            }
            preparedPlayer.start()
        }
        activeTrack = track
        player = newPlayer
        try {
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            newPlayer.release()
            if (player === newPlayer) player = null
            playerPrepared = false
        }
    }

    private fun applyVolume(target: MediaPlayer? = player) {
        val ctx = applicationContext ?: return
        val volumePercent = if (activeUsesMatchVolume) {
            SettingsManager.getMatchMusicVolume(ctx)
        } else {
            SettingsManager.getMusicVolume(ctx)
        }
        val scaledVolume = volumePercent / 100f
        target?.setVolume(scaledVolume, scaledVolume)
    }

    private fun resetTrackPosition(track: Int) {
        if (track !in trackPositionsMs.indices) return
        trackPositionsMs[track] = 0
        if (activeTrack == track) {
            player?.let { current ->
                runCatching { current.seekTo(0) }
            }
        }
    }

    private fun releasePlayer() {
        prepareGeneration++
        player?.let { current ->
            if (playerPrepared && activeTrack in trackPositionsMs.indices) {
                trackPositionsMs[activeTrack] = runCatching { current.currentPosition }.getOrDefault(0)
            }
            current.setOnCompletionListener(null)
            current.setOnErrorListener(null)
            current.release()
        }
        player = null
        playerPrepared = false
    }
}