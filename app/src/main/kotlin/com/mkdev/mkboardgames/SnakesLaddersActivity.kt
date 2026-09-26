package com.mkdev.mkboardgames

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.FireworksView
import com.mkdev.mkboardgames.ui.GlbDiceView
import com.mkdev.mkboardgames.ui.LudoPlayerBadgeView
import com.mkdev.mkboardgames.ui.LudoPlayerControlView
import com.mkdev.mkboardgames.ui.MotionDiceDirection
import com.mkdev.mkboardgames.ui.SnakesLaddersBoardView
import com.mkdev.mkboardgames.ui.SnakesLaddersBoardSelectionView
import com.mkdev.mkboardgames.ui.SnakesLaddersEntryRule
import com.mkdev.mkboardgames.ui.StyledDialogs
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import kotlin.random.Random

class SnakesLaddersActivity : AppCompatActivity() {
    private lateinit var gameRoot: FrameLayout
    private lateinit var boardView: SnakesLaddersBoardView
    private lateinit var boardStage: FrameLayout
    private lateinit var screenRoot: FrameLayout
    private lateinit var boardBackdropView: ImageView
    private lateinit var fireworksView: FireworksView
    private lateinit var gameOverView: SnakesLaddersGameOverView
    private var modeMenuView: ChessMenuView? = null
    private var boardSelectionView: SnakesLaddersBoardSelectionView? = null
    private lateinit var playerDiceViews: Array<GlbDiceView>
    private lateinit var playerBadgeViews: Array<LudoPlayerBadgeView>
    private lateinit var playerControlViews: Array<LudoPlayerControlView>
    private val handler = Handler(Looper.getMainLooper())

    private var vsAI = true
    private var playerCount = 2
    private var matchStarted = false
    private var dialogOpen = false
    private var currentPlayer = 0
    private var positions = IntArray(2)
    private var gameOver = false
    private var winner = -1
    private var resultDialogVisible = false
    private var turnResolutionPending = false
    private var lifecycleActive = false
    private var exitPosted = false

    private var ladders = SnakesLaddersBoardView.Board.ONE.ladders
    private var snakes = SnakesLaddersBoardView.Board.ONE.snakes
    private var selectedBoard = SnakesLaddersBoardView.Board.ONE
    private var selectedEntryRule = SnakesLaddersEntryRule.REQUIRE_SIX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        SettingsManager.activateGameTheme(this, "snakes_ladders")
        SoundPlayer.init(this)
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        makeFullscreen()

        boardView = SnakesLaddersBoardView(this).apply {
            onGameOverTapped = { showGameOverOverlay() }
        }
        boardStage = FrameLayout(this).apply {
            clipChildren = false
            addView(
                boardView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    val railHeight = dp(LudoPlayerControlView.RAIL_HEIGHT)
                    topMargin = railHeight
                    bottomMargin = railHeight
                },
            )
        }
        boardBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(loadAssetBitmap("snakes_ladders_board_two_background.webp"))
            visibility = View.GONE
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        gameRoot = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(boardStage, FrameLayout.LayoutParams(-1, -1))
        }
        AdManager.attachBannerOverlay(gameRoot)
        gameRoot.visibility = View.GONE

        screenRoot = FrameLayout(this).apply {
            addView(
                boardBackdropView,
                FrameLayout.LayoutParams(-1, -1),
            )
            addView(
                gameRoot,
                FrameLayout.LayoutParams(-1, -1),
            )
            fireworksView = FireworksView(this@SnakesLaddersActivity)
            addView(
                fireworksView,
                FrameLayout.LayoutParams(-1, -1),
            )
            gameOverView = SnakesLaddersGameOverView(this@SnakesLaddersActivity).apply {
                onReplay = {
                    hideGameOverOverlay()
                    startGame()
                }
                onHome = {
                    hideGameOverOverlay()
                    finish()
                }
            }
            addView(
                gameOverView,
                FrameLayout.LayoutParams(-1, -1),
            )
        }
        setContentView(screenRoot)
        showModeDialog()
    }

    private fun mountPlayerControls() {
        playerControlViews.forEach { control ->
            boardStage.addView(
                control,
                FrameLayout.LayoutParams(
                    dp(LudoPlayerControlView.PAIR_WIDTH),
                    dp(LudoPlayerControlView.CONTROL_HEIGHT),
                ),
            )
        }
        // The board is transparent outside its artwork, so placing it above
        // the controls lets a start token cover the matching profile icon.
        boardView.bringToFront()
    }

    override fun onResume() {
        super.onResume()
        lifecycleActive = true
        makeFullscreen()
        if (::playerDiceViews.isInitialized) {
            playerDiceViews.forEach { it.onHostResume() }
            boardView.resumeAnimations()
            if (matchStarted && !dialogOpen && !gameOver) {
                updateHud()
                if (vsAI && currentPlayer == 1 &&
                    !turnResolutionPending &&
                    playerDiceViews.none { it.isRolling }
                ) {
                    handler.postDelayed(
                        { if (gameplayActive() && currentPlayer == 1) rollDice(automated = true) },
                        350L,
                    )
                }
            }
        }
    }

    override fun onPause() {
        if (::playerDiceViews.isInitialized) {
            playerDiceViews.forEach { it.onHostPause() }
        }
        SoundPlayer.stopAll()
        super.onPause()
    }

    override fun onDestroy() {
        lifecycleActive = false
        handler.removeCallbacksAndMessages(null)
        if (::playerDiceViews.isInitialized) {
            playerDiceViews.forEach { it.cancelRoll() }
        }
        turnResolutionPending = false
        boardView.cancelAnimations()
        SoundPlayer.stopAll()
        if (::fireworksView.isInitialized) fireworksView.cancel()
        super.onDestroy()
    }

    override fun finish() {
        if (exitPosted) return
        exitPosted = true
        boardBackdropView.visibility = View.GONE
        gameRoot.visibility = View.GONE
        handler.postDelayed({
            super.finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 16L)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (boardSelectionView != null) {
            boardSelectionView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            boardSelectionView = null
            showModeDialog()
        } else if (gameOver) {
            showGameOverOverlay()
        } else if (matchStarted) {
            if (canPauseMatch()) showLeaveMatchDialog()
            else Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
        } else {
            finish()
        }
    }

    private fun showModeDialog() {
        boardBackdropView.visibility = View.GONE
        // Keep the activity's game surface mounted while the mode menu is
        // shown. The menu is a child of the same root, so switching modes
        // only removes the menu instead of revealing a hidden/recreated
        // activity surface.
        hideBoardWhileDialogIsOpen(hideGameRoot = false)
        modeMenuView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
        }
        boardSelectionView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
        }
        boardSelectionView = null
        val menu = ChessMenuView(this, false, gameLabel = "S N A K E S & L A D D E R S")
        modeMenuView = menu
        menu.onVsAi = {
            showBoardSelection(cpuEnabled = true, players = 2)
        }
        menu.onTwoPlayers = {
            showBoardSelection(cpuEnabled = false, players = 2)
        }
        menu.onFourPlayers = {
            showBoardSelection(cpuEnabled = false, players = 4)
        }
        menu.onHowToPlay = {
            modeMenuView?.let { current ->
                (current.parent as? ViewGroup)?.removeView(current)
            }
            modeMenuView = null
            showRules(showModeAfter = !matchStarted)
        }
        screenRoot.addView(menu, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showBoardSelection(cpuEnabled: Boolean, players: Int) {
        boardBackdropView.visibility = View.GONE
        modeMenuView?.let { menu ->
            (menu.parent as? ViewGroup)?.removeView(menu)
        }
        modeMenuView = null
        hideBoardWhileDialogIsOpen(hideGameRoot = false)

        val label = if (cpuEnabled) "vs CPU" else "$players Players"
        val picker = SnakesLaddersBoardSelectionView(this, label)
        boardSelectionView = picker
        picker.onSelectionConfirmed = { board, entryRule ->
            setBoardRules(board)
            selectedEntryRule = entryRule
            boardView.setBoard(board)
            boardSelectionView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            boardSelectionView = null
            beginMatch(cpuEnabled, players)
        }
        picker.onBackClicked = {
            boardSelectionView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            boardSelectionView = null
            showModeDialog()
        }
        screenRoot.addView(picker, FrameLayout.LayoutParams(-1, -1))
    }

    private fun setBoardRules(board: SnakesLaddersBoardView.Board) {
        selectedBoard = board
        ladders = board.ladders
        snakes = board.snakes
        val backgroundAsset = board.matchBackgroundAssetName
        boardBackdropView.setImageBitmap(backgroundAsset?.let(::loadAssetBitmap))
        boardBackdropView.visibility = if (backgroundAsset == null) View.GONE else View.VISIBLE
    }

    private fun beginMatch(cpuEnabled: Boolean, players: Int) {
        vsAI = cpuEnabled
        playerCount = players
        // Mark the match before removing the selection overlay. This keeps
        // the overlay's exit callback from treating a valid mode selection
        // as an attempt to leave the activity.
        matchStarted = true
        if (!::playerControlViews.isInitialized || players > playerControlViews.size) {
            if (::playerControlViews.isInitialized) {
                cancelPlayerDiceRolls()
                playerControlViews.forEach { boardStage.removeView(it) }
            }
            createPlayerControls(players)
            mountPlayerControls()
        }
        modeMenuView?.let { menu ->
            (menu.parent as? ViewGroup)?.removeView(menu)
        }
        modeMenuView = null
        startGame()
    }

    private fun showRules(showModeAfter: Boolean) {
        hideBoardWhileDialogIsOpen()
        val rules = """
            SNAKES & LADDERS — Rules

            Roll the die and move your counter along the numbered board. ${selectedEntryRule.title}. Land on the bottom of a ladder to climb upward. Land on a snake's head and slide back down.

            Reach square 100 first to win. A roll that would pass 100 leaves your counter where it is. Rolling a six grants another turn.

            Play against the CPU, choose two players, or start a four-player match. Each board has its own ladder and snake layout.
        """.trimIndent()
        StyledDialogs.showRules(
            this,
            "Snakes & Ladders",
            rules,
            "S N A K E S & L A D D E R S",
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() },
        )
    }

    private fun startGame() {
        handler.removeCallbacksAndMessages(null)
        cancelPlayerDiceRolls()
        boardView.cancelAnimations()
        positions = IntArray(playerCount)
        currentPlayer = 0
        gameOver = false
        winner = -1
        resultDialogVisible = false
        turnResolutionPending = false
        matchStarted = true
        SettingsManager.recordRecentlyPlayed(this, "SNAKES_LADDERS")
        if (vsAI) SettingsManager.setActiveGame(this, "snakes_ladders")
        gameOverView.winnerLabel = ""
        fireworksView.cancel()
        SoundPlayer.stop("snakes_ladders_fireworks", "snakes_ladders_victory")
        hideGameOverOverlay()
        boardView.gameOver = false
        boardView.setActivePlayerCount(playerCount)
        if (playerCount == 4) {
            configurePlayerControls()
        }
        for (player in 0 until playerCount) {
            boardView.setPlayerPosition(player, 0)
        }
        MusicPlayer.enterMatch(this)
        showBoardAfterDialog()
        updateHud()
        SoundPlayer.playMovement("ludo_start")
    }

    private fun rollDice(automated: Boolean = false) {
        if (!gameplayActive() || !matchStarted || gameOver ||
            turnResolutionPending || playerDiceViews.any { it.isRolling }
        ) return
        // A die tap is always a human action. CPU turns call this method with
        // automated=true so they are not blocked by the human-turn guard.
        if (!automated && vsAI && currentPlayer == 1) return
        val player = currentPlayer
        val value = Random.nextInt(1, 7)
        turnResolutionPending = true
        playerDiceViews[player].rollTo(value, MotionDiceDirection.UP) {
            if (gameplayActive() && !gameOver) {
                applyRoll(player, value)
            } else {
                turnResolutionPending = false
            }
        }
    }

    private fun canPauseMatch(): Boolean =
        matchStarted &&
            !gameOver &&
            (!vsAI || currentPlayer != 1) &&
            !turnResolutionPending &&
            playerDiceViews.none { it.isRolling } &&
            !boardView.hasPendingMoveAnimation()

    private fun applyRoll(player: Int, value: Int) {
        if (player != currentPlayer || gameOver) return
        val from = positions[player]
        if (from == 0 &&
            selectedEntryRule == SnakesLaddersEntryRule.REQUIRE_SIX &&
            value != 6
        ) {
            Toast.makeText(
                this,
                "${playerName(player)} needs a 6 to enter the board",
                Toast.LENGTH_SHORT,
            ).show()
            finishTurn(player, value, 0)
            return
        }

        val stepped = if (from == 0) {
            if (selectedEntryRule == SnakesLaddersEntryRule.REQUIRE_SIX) 1 else value
        } else if (from + value <= 100) {
            from + value
        } else {
            from
        }
        val destination = ladders[stepped] ?: snakes[stepped] ?: stepped
        animateMove(
            player = player,
            from = from,
            stepped = stepped,
            destination = destination,
            roll = value,
            path = if (from == 0) {
                SnakesLaddersBoardView.MovePath.ENTER_BOARD
            } else {
                SnakesLaddersBoardView.MovePath.NUMBERED_SQUARES
            },
        )
    }

    private fun animateMove(
        player: Int,
        from: Int,
        stepped: Int,
        destination: Int,
        roll: Int,
        path: SnakesLaddersBoardView.MovePath,
    ) {
        boardView.animateMove(
            player = player,
            from = from,
            to = stepped,
            path = path,
            onStep = { SoundPlayer.playMovement("ludo_move", rate = 0.5f) },
        ) {
            positions[player] = stepped
            if (destination != stepped) {
                handler.postDelayed({
                    if (!gameplayActive() || gameOver) return@postDelayed
                    val transitionSound = if (destination > stepped) {
                        "snakes_ladders_ladder"
                    } else {
                        "snakes_ladders_snake"
                    }
                    SoundPlayer.playMovement(transitionSound)
                    boardView.animateMove(
                        player = player,
                        from = stepped,
                        to = destination,
                        path = SnakesLaddersBoardView.MovePath.DIRECT_TRANSITION,
                        onStep = {},
                    ) {
                        positions[player] = destination
                        finishAnimatedMove(player, roll, destination)
                    }
                }, 120L)
            } else {
                finishAnimatedMove(player, roll, destination)
            }
        }
    }

    private fun finishAnimatedMove(player: Int, roll: Int, destination: Int) {
        if (destination >= 100) {
            completeWin(player)
        } else {
            finishTurn(player, roll, destination)
        }
    }

    private fun finishTurn(player: Int, roll: Int, destination: Int) {
        handler.postDelayed({
            if (gameplayActive() && !gameOver) {
                completeTurn(player, roll, destination)
            } else {
                turnResolutionPending = false
            }
        }, TURN_RESOLUTION_DELAY_MS)
    }

    private fun completeTurn(player: Int, roll: Int, destination: Int) {
        turnResolutionPending = false
        if (destination >= 100) {
            completeWin(player)
            return
        }
        if (roll != 6) currentPlayer = (player + 1) % playerCount
        updateHud()
        if (vsAI && currentPlayer == 1) {
            handler.postDelayed({ if (gameplayActive()) rollDice(automated = true) }, 700L)
        }
    }

    private fun completeWin(player: Int) {
        if (gameOver) return
        turnResolutionPending = false
        winner = player
        gameOver = true
        if (vsAI) {
            if (player == 0) SettingsManager.recordWin(this)
            else SettingsManager.recordLoss(this)
        }
        boardView.gameOver = true
        gameOverView.winnerLabel = "WINNER: ${playerName(player)}"
        SoundPlayer.stop("ludo_move")
        SoundPlayer.playWhenReady("snakes_ladders_victory")
        SoundPlayer.playWhenReady("snakes_ladders_fireworks", volume = 0.82f)
        fireworksView.playOnce()
        updateHud()
        showGameOverOverlay()
    }

    private fun updateHud() {
        if (!::playerControlViews.isInitialized) return
        playerControlViews.forEachIndexed { player, control ->
            val active = matchStarted && !gameOver && player == currentPlayer
            control.label = playerName(player)
            control.isActive = active
            playerBadgeViews[player].label = playerName(player)
            playerBadgeViews[player].isActive = active
        }
        syncPlayerDiceVisibility()
    }

    private fun syncPlayerDiceVisibility() {
        if (!::playerControlViews.isInitialized) return
        val gameplayVisible = matchStarted && !dialogOpen && !gameOver
        playerControlViews.forEachIndexed { player, control ->
            control.setDieVisible(gameplayVisible && player == currentPlayer)
        }
    }

    private fun showLeaveMatchDialog() {
        if (playerDiceViews.any { it.isRolling }) {
            Toast.makeText(this, "Wait for the dice to stop rolling", Toast.LENGTH_SHORT).show()
            return
        }
        MusicPlayer.enterPausedMatch(this)
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(
            this,
            "Leave Match?",
            "Leaving counts as a forfeit.",
            listOf(
                StyledDialogs.choice("Leave Match", "Return to the home screen", "⚑", "#E58A7A"),
                StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
            ),
            420f,
            "S N A K E S & L A D D E R S",
            onCancel = { showBoardAfterDialog() },
        ) { which, dialog ->
            dialog.dismiss()
            if (which == 0) finish() else showBoardAfterDialog()
        }
    }

    private fun showGameOverOverlay() {
        if (!gameOver || resultDialogVisible) return
        resultDialogVisible = true
        gameOverView.visibility = View.VISIBLE
        gameOverView.bringToFront()
    }

    private fun hideGameOverOverlay() {
        resultDialogVisible = false
        if (::gameOverView.isInitialized) {
            gameOverView.visibility = View.GONE
        }
    }

    private fun hideBoardWhileDialogIsOpen(hideGameRoot: Boolean = true) {
        dialogOpen = true
        handler.removeCallbacksAndMessages(null)
        setPlayerControlsVisible(false)
        boardView.cancelAnimations()
        SoundPlayer.stopAll()
        hideGameOverOverlay()
        gameRoot.visibility = if (hideGameRoot) View.GONE else View.VISIBLE
    }

    private fun showBoardAfterDialog() {
        if (matchStarted && !gameOver) {
            MusicPlayer.resumeMatch(this)
        }
        dialogOpen = false
        boardBackdropView.visibility =
            if (selectedBoard.matchBackgroundAssetName == null) View.GONE else View.VISIBLE
        gameRoot.visibility = View.VISIBLE
        setPlayerControlsVisible(true)
        updateHud()
        if (vsAI && currentPlayer == 1 && !gameOver) {
            handler.postDelayed({ if (gameplayActive()) rollDice(automated = true) }, 500L)
        }
    }

    private fun gameplayActive(): Boolean =
        lifecycleActive && !dialogOpen && !isFinishing && !isDestroyed

    private fun playerName(player: Int): String =
        if (player == 0) {
            if (vsAI) "You" else "Player 1"
        } else {
            if (vsAI) "CPU" else "Player ${player + 1}"
        }

    private fun createPlayerControls(count: Int) {
        playerDiceViews = Array(count) { player ->
            GlbDiceView(this).apply {
                accentColor = SnakesLaddersBoardView.PLAYER_COLORS[player]
                contentDescription = "Player ${player + 1} dice"
                onRoll = {
                    if (player == currentPlayer &&
                        gameplayActive() &&
                        matchStarted &&
                        !gameOver &&
                        (!vsAI || player == 0)
                    ) {
                        rollDice()
                    }
                }
            }
        }
        playerBadgeViews = Array(count) { player ->
            LudoPlayerBadgeView(this).apply {
                accentColor = SnakesLaddersBoardView.PLAYER_COLORS[player]
                avatarScale = selectedBoard.profileScale
                ringAndGlowEnabled = selectedBoard != SnakesLaddersBoardView.Board.TWO
                label = playerName(player)
            }
        }
        playerControlViews = Array(count) { player ->
            LudoPlayerControlView(this).apply {
                accentColor = SnakesLaddersBoardView.PLAYER_COLORS[player]
                frameAssetName = selectedBoard.playerFrameAssetName
                dieScale = selectedBoard.dieScale
                label = playerName(player)
                labelBelow = if (count == 2) player == 0 else player < 2
                labelUpsideDown = false
                dieHorizontalShiftFraction = if (player == 0 || player == 2) {
                    PLAYER_ONE_THREE_DIE_SHIFT_FRACTION
                } else {
                    0f
                }
                bind(
                    playerBadgeViews[player],
                    playerDiceViews[player],
                    profileOnEnd = player % 2 == 1,
                )
                playerBadgeViews[player].facesOppositeSide = false
            }
        }
        boardStage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionPlayerControls()
        }
    }

    private fun configurePlayerControls() {
        playerControlViews.forEachIndexed { player, control ->
            val isBottomPlayer = if (playerCount == 2) player == 0 else player < 2
            control.labelBelow = isBottomPlayer
            control.labelUpsideDown = false
            control.frameAssetName = selectedBoard.playerFrameAssetName
            control.dieScale = selectedBoard.dieScale
            control.dieHorizontalShiftFraction = if (player == 0 || player == 2) {
                PLAYER_ONE_THREE_DIE_SHIFT_FRACTION
            } else {
                0f
            }
            playerBadgeViews[player].avatarScale = selectedBoard.profileScale
            playerBadgeViews[player].ringAndGlowEnabled =
                selectedBoard != SnakesLaddersBoardView.Board.TWO
            playerBadgeViews[player].facesOppositeSide = false
            control.bind(
                playerBadgeViews[player],
                playerDiceViews[player],
                profileOnEnd = player % 2 == 1,
            )
        }
    }

    private fun positionPlayerControls() {
        if (!::boardStage.isInitialized || boardStage.width <= 0 || boardStage.height <= 0) return
        val boardLeft = boardView.left + boardView.boardArtworkLeftPixels()
        val boardTop = boardView.top + boardView.boardArtworkTopPixels()
        val boardWidth = boardView.boardArtworkWidthPixels()
        val boardBottom = boardView.top + boardView.boardArtworkBottomPixels()
        val controlWidth = dp(LudoPlayerControlView.PAIR_WIDTH)
        val controlHeight = dp(LudoPlayerControlView.CONTROL_HEIGHT)
        val gap = dp(LudoPlayerControlView.CONTROL_GAP)
        val leftX = boardLeft + dp(4)
        val rightX = boardLeft + boardWidth - controlWidth - dp(4)
        val topY = boardTop - controlHeight - gap
        val bottomY = boardBottom + gap
        val positions = if (playerCount == 2) {
            arrayOf(leftX to bottomY, rightX to topY)
        } else {
            arrayOf(
                leftX to bottomY,
                rightX to bottomY,
                leftX to topY,
                rightX to topY,
            )
        }
        playerControlViews.forEachIndexed { player, control ->
            val (left, top) = positions[player]
            control.layoutParams = (control.layoutParams as FrameLayout.LayoutParams).apply {
                width = controlWidth
                height = controlHeight
                leftMargin = left
                topMargin = top
            }
            val profileCenter = control.profileCenterInParent()
            boardView.setPlayerStartAnchor(
                player = player,
                centerX = left + profileCenter.x - boardView.left,
                centerY = top + profileCenter.y - boardView.top,
                radius = control.profileRadius(),
            )
            control.visibility = if (player < playerCount && !dialogOpen) View.VISIBLE else View.GONE
        }
    }

    private fun setPlayerControlsVisible(visible: Boolean) {
        if (!::playerControlViews.isInitialized) return
        playerControlViews.forEachIndexed { player, control ->
            val shown = visible && player < playerCount
            control.visibility = if (shown) View.VISIBLE else View.INVISIBLE
            control.setDieVisible(shown && player == currentPlayer && matchStarted && !gameOver)
            playerBadgeViews[player].visibility = if (shown) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun cancelPlayerDiceRolls() {
        if (!::playerDiceViews.isInitialized) return
        playerDiceViews.forEach { it.cancelRoll() }
    }

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadAssetBitmap(assetName: String): Bitmap? = runCatching {
        assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private companion object {
        const val TURN_RESOLUTION_DELAY_MS = 500L
        const val PLAYER_ONE_THREE_DIE_SHIFT_FRACTION = 0.015f
    }
}