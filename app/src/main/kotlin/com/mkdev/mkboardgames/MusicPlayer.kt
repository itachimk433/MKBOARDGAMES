package com.mkdev.mkboardgames

import android.content.Context
import android.media.MediaPlayer

/**
 * App-wide background music player.
 *
 * The player is paused, rather than released, when music is disabled or the
 * app is backgrounded. Mode selection, active matches, and in-game pause
 * screens share a randomized playlist; Onitama and paused matches keep their
 * dedicated tracks.
 */
object MusicPlayer {

    private const val MUSIC_1_TRACK = 0
    private const val MUSIC_2_TRACK = 1
    private const val MUSIC_3_TRACK = 2
    private const val ONITAMA_TRACK = 3
    private const val PAUSED_MATCH_TRACK = 4
    private const val GLOBAL_TRACK_COUNT = 3

    private val tracks = intArrayOf(
        R.raw.music1,
        R.raw.music2,
        R.raw.music3,
        R.raw.mode_selection_screen_and_onitama_in_match_only,
        R.raw.paused_match,
    )

    private var player: MediaPlayer? = null
    private var activeTrack = -1
    private var requestedTrack = -1
    private var modeSessionActive = false
    private var appInForeground = true
    private var applicationContext: Context? = null
    private var globalTrack = -1
    private var matchTrack = MUSIC_1_TRACK
    private val trackPositionsMs = IntArray(tracks.size)
    private var activeUsesMatchVolume = false
    private var requestedUsesMatchVolume = false
    private var playerPrepared = false
    private var prepareGeneration = 0

    /** Keep the shared playlist active while the user chooses a game. */
    fun playForMode(ctx: Context, mode: GameMode) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        ensureGlobalTrackSelected()
        requestedTrack = globalTrack
        requestedUsesMatchVolume = false
        switchToRequestedTrack()
    }

    /** Switch to the standard match soundtrack for the current game. */
    fun enterMatch(ctx: Context) {
        ensureGlobalTrackSelected()
        switchToTrack(ctx, globalTrack, usesMatchVolume = true)
    }

    /** Switch to the dedicated Onitama match soundtrack. */
    fun enterOnitamaMatch(ctx: Context) {
        switchToTrack(ctx, ONITAMA_TRACK, usesMatchVolume = true)
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

    /** Play the shared playlist on the mode-selection screen. */
    fun enterModeSelection(ctx: Context) {
        applicationContext = ctx.applicationContext
        modeSessionActive = true
        ensureGlobalTrackSelected()
        requestedTrack = globalTrack
        requestedUsesMatchVolume = false
        switchToRequestedTrack()
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
            newPlayer.isLooping = track >= GLOBAL_TRACK_COUNT
        } catch (_: Exception) {
            newPlayer.release()
            return
        }
        newPlayer.setOnCompletionListener { completedPlayer ->
            if (track < GLOBAL_TRACK_COUNT && player === completedPlayer) {
                globalTrack = nextGlobalTrack()
                matchTrack = globalTrack
                resetTrackPosition(globalTrack)
                requestedTrack = globalTrack
                switchToRequestedTrack()
            }
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

    private fun ensureGlobalTrackSelected() {
        if (globalTrack !in 0 until GLOBAL_TRACK_COUNT) {
            globalTrack = MUSIC_1_TRACK
        }
    }

    private fun nextGlobalTrack(): Int {
        val candidates = intArrayOf(MUSIC_1_TRACK, MUSIC_2_TRACK, MUSIC_3_TRACK)
        return candidates.filter { it != globalTrack }.random()
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