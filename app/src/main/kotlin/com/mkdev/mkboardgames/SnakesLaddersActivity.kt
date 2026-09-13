package com.mkdev.mkboardgames

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.GlbDiceView
import com.mkdev.mkboardgames.ui.MotionDiceDirection
import com.mkdev.mkboardgames.ui.SnakesLaddersBoardView
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlin.random.Random

class SnakesLaddersActivity : AppCompatActivity() {
    private lateinit var gameRoot: LinearLayout
    private lateinit var boardView: SnakesLaddersBoardView
    private lateinit var diceView: GlbDiceView
    private lateinit var turnView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var vsAI = true
    private var matchStarted = false
    private var dialogOpen = false
    private var currentPlayer = 0
    private var positions = intArrayOf(0, 0)
    private var gameOver = false
    private var winner = -1
    private var resultDialogVisible = false
    private var lifecycleActive = false
    private var exitPosted = false

    private val ladders = mapOf(
        4 to 14, 9 to 31, 20 to 38, 28 to 84,
        40 to 59, 51 to 67, 63 to 81, 71 to 91,
    )
    private val snakes = mapOf(
        17 to 7, 54 to 34, 62 to 19, 64 to 60,
        87 to 24, 93 to 73, 95 to 75, 99 to 78,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        SettingsManager.activateGameTheme(this, "snakes_ladders")
        SoundPlayer.init(this)
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        makeFullscreen()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(6))
            setBackgroundColor(Color.parseColor("#10151A"))
        }
        turnView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(17f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        boardView = SnakesLaddersBoardView(this).apply {
            onGameOverTapped = { showResultDialog() }
        }
        val boardStage = FrameLayout(this).apply {
            clipChildren = false
            addView(
                boardView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        diceView = GlbDiceView(this).apply {
            contentDescription = "Dice"
            onRoll = { rollDice() }
        }
        val diceRail = FrameLayout(this).apply {
            addView(
                diceView,
                FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER),
            )
        }
        gameRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(turnView, LinearLayout.LayoutParams(-1, dp(48)))
            addView(boardStage, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(diceRail, LinearLayout.LayoutParams(-1, dp(96)))
        }
        gameRoot.visibility = View.GONE

        val screenRoot = FrameLayout(this).apply {
            addView(
                gameRoot,
                FrameLayout.LayoutParams(-1, -1),
            )
        }
        setContentView(screenRoot)
        showModeDialog()
    }

    override fun onResume() {
        super.onResume()
        lifecycleActive = true
        makeFullscreen()
    }

    override fun onPause() {
        lifecycleActive = false
        handler.removeCallbacksAndMessages(null)
        diceView.cancelRoll()
        boardView.cancelAnimations()
        super.onPause()
    }

    override fun onDestroy() {
        lifecycleActive = false
        handler.removeCallbacksAndMessages(null)
        diceView.cancelRoll()
        boardView.cancelAnimations()
        super.onDestroy()
    }

    override fun finish() {
        if (exitPosted) return
        exitPosted = true
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
        if (gameOver) {
            showResultDialog()
        } else if (matchStarted) {
            showLeaveMatchDialog()
        } else {
            finish()
        }
    }

    private fun showModeDialog() {
        hideBoardWhileDialogIsOpen()
        val menu = ChessMenuView(this, false, gameLabel = "S N A K E S & L A D D E R S")
        menu.onVsAi = {
            StyledDialogs.dismiss()
            vsAI = true
            startGame()
        }
        menu.onTwoPlayers = {
            StyledDialogs.dismiss()
            vsAI = false
            startGame()
        }
        menu.onHowToPlay = {
            StyledDialogs.dismiss()
            showRules(showModeAfter = !matchStarted)
        }
        StyledDialogs.showFullScreenView(this, menu) {
            if (matchStarted) showBoardAfterDialog() else finish()
        }
    }

    private fun showRules(showModeAfter: Boolean) {
        hideBoardWhileDialogIsOpen()
        val rules = """
            SNAKES & LADDERS — Rules

            Roll the die and move your counter along the numbered board. Land on the bottom of a ladder to climb upward. Land on a snake's head and slide back down.

            Reach square 100 first to win. A roll that would pass 100 leaves your counter where it is. Rolling a six grants another turn.

            Play against the CPU or choose two players to take turns on the same board.
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
        diceView.cancelRoll()
        boardView.cancelAnimations()
        positions = intArrayOf(0, 0)
        currentPlayer = 0
        gameOver = false
        winner = -1
        resultDialogVisible = false
        matchStarted = true
        boardView.gameOver = false
        boardView.setPlayerPosition(0, 0)
        boardView.setPlayerPosition(1, 0)
        showBoardAfterDialog()
        updateHud()
        SoundPlayer.playMovement("ludo_start")
    }

    private fun rollDice(automated: Boolean = false) {
        if (!gameplayActive() || !matchStarted || gameOver || diceView.isRolling) return
        // A die tap is always a human action. CPU turns call this method with
        // automated=true so they are not blocked by the human-turn guard.
        if (!automated && vsAI && currentPlayer == 1) return
        val player = currentPlayer
        val value = Random.nextInt(1, 7)
        diceView.rollTo(value, MotionDiceDirection.UP) {
            if (gameplayActive()) applyRoll(player, value)
        }
        SoundPlayer.playMovement("ludo_dice")
    }

    private fun applyRoll(player: Int, value: Int) {
        if (player != currentPlayer || gameOver) return
        val from = positions[player]
        val stepped = if (from + value <= 100) from + value else from
        val destination = ladders[stepped] ?: snakes[stepped] ?: stepped
        animateMove(player, from, stepped, destination, value)
    }

    private fun animateMove(player: Int, from: Int, stepped: Int, destination: Int, roll: Int) {
        boardView.animateMove(
            player = player,
            from = from,
            to = stepped,
            onStep = { SoundPlayer.playMovement("ludo_move") },
        ) {
            positions[player] = stepped
            if (destination != stepped) {
                val isLadder = destination > stepped
                turnView.text = if (isLadder) {
                    "Ladder! $stepped → $destination"
                } else {
                    "Snake! $stepped → $destination"
                }
                handler.postDelayed({
                    if (!gameplayActive() || gameOver) return@postDelayed
                    boardView.animateMove(
                        player = player,
                        from = stepped,
                        to = destination,
                        onStep = { SoundPlayer.playMovement("ludo_move", 0.8f) },
                    ) {
                        positions[player] = destination
                        finishTurn(player, roll, destination)
                    }
                }, 120L)
            } else {
                finishTurn(player, roll, destination)
            }
        }
    }

    private fun finishTurn(player: Int, roll: Int, destination: Int) {
        if (destination >= 100) {
            winner = player
            gameOver = true
            boardView.gameOver = true
            turnView.text = "${playerName(player)} wins"
            handler.postDelayed({ if (lifecycleActive) showResultDialog() }, 300L)
            return
        }
        if (roll != 6) currentPlayer = 1 - player
        updateHud()
        if (vsAI && currentPlayer == 1) {
            handler.postDelayed({ if (gameplayActive()) rollDice(automated = true) }, 700L)
        }
    }

    private fun updateHud() {
        if (!::turnView.isInitialized) return
        val label = if (currentPlayer == 0) {
            if (vsAI) "Your turn · tap the die to roll" else "Player 1 · tap the die to roll"
        } else {
            if (vsAI) "CPU is thinking" else "Player 2 · tap the die to roll"
        }
        turnView.text = label
        val accent = SnakesLaddersBoardView.PLAYER_COLORS[currentPlayer]
        turnView.setTextColor(accent)
        turnView.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb(235, 13, 18, 24))
            setStroke(dp(2), Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
    }

    private fun showLeaveMatchDialog() {
        if (diceView.isRolling) {
            Toast.makeText(this, "Wait for the dice to stop rolling", Toast.LENGTH_SHORT).show()
            return
        }
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

    private fun showResultDialog() {
        if (!gameOver || resultDialogVisible) return
        resultDialogVisible = true
        hideBoardWhileDialogIsOpen()
        val message = if (winner == 0) {
            if (vsAI) "You win!" else "Player 1 wins!"
        } else {
            if (vsAI) "The CPU wins!" else "Player 2 wins!"
        }
        StyledDialogs.showChoices(
            this,
            "Game Over",
            message,
            listOf(
                StyledDialogs.choice("Play Again", "Start a fresh game", "↻", "#E3B86A"),
                StyledDialogs.choice("Main Menu", "Choose another match", "⌂", "#E58A7A"),
            ),
            420f,
            "S N A K E S & L A D D E R S",
            onCancel = {
                resultDialogVisible = false
                showBoardAfterDialog()
            },
            fullScreen = false,
        ) { which, dialog ->
            dialog.dismiss()
            resultDialogVisible = false
            if (which == 0) startGame() else finish()
        }
    }

    private fun hideBoardWhileDialogIsOpen() {
        dialogOpen = true
        handler.removeCallbacksAndMessages(null)
        diceView.cancelRoll()
        diceView.setGameplayVisible(false)
        boardView.cancelAnimations()
        gameRoot.visibility = View.GONE
    }

    private fun showBoardAfterDialog() {
        dialogOpen = false
        gameRoot.visibility = View.VISIBLE
        diceView.setGameplayVisible(true)
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
            if (vsAI) "CPU" else "Player 2"
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
}