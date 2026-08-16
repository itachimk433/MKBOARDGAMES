package com.mkdev.mkboardgames

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.games.ludo.LudoRuleEngine
import com.mkdev.mkboardgames.games.ludo.LudoSetup
import com.mkdev.mkboardgames.ui.LudoBoardView
import com.mkdev.mkboardgames.ui.LudoDiceView
import kotlin.random.Random

class LudoActivity : AppCompatActivity() {
    private lateinit var boardView: LudoBoardView
    private lateinit var diceView: LudoDiceView
    private lateinit var turnView: TextView
    private val engine = LudoRuleEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val moves = mutableListOf<Move>()

    private var state: GameState = engine.initialState()
    private var vsAI = true
    private var humanPlayer = 0
    private var rolledValue = 0
    private var resultDialogVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)
        SettingsManager.activateGameTheme(this, "ludo")

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#10151A"))
        }
        turnView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(12, (10 * dp).toInt(), 12, (10 * dp).toInt())
        }
        boardView = LudoBoardView(this)
        diceView = LudoDiceView(this)

        root.addView(turnView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (52 * dp).toInt()
        ))
        root.addView(boardView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
        root.addView(diceView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (148 * dp).toInt()
        ))
        AdManager.attachBanner(root)
        setContentView(root)

        boardView.onMoveSelected = { move ->
            if (isHumanTurn()) playMove(move)
        }
        boardView.onGameOverTapped = { showResultDialog() }
        diceView.onRoll = { if (isHumanTurn()) rollDice() }
        showModeDialog()
    }

    override fun onResume() {
        super.onResume()
        makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (state.status == GameStatus.IN_PROGRESS && moves.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Leave Match?")
                .setMessage("Leaving counts as a forfeit.")
                .setPositiveButton("Leave") { _, _ -> super.onBackPressed() }
                .setNegativeButton("Keep Playing", null)
                .show()
        } else {
            super.onBackPressed()
        }
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

    private fun showModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Ludo")
            .setItems(arrayOf("vs AI", "4 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> {
                        vsAI = true
                        showPlayerPicker()
                    }
                    1 -> {
                        vsAI = false
                        startGame()
                    }
                    2 -> showRules(true)
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (moves.isEmpty()) finish() }
            .show()
    }

    private fun showPlayerPicker() {
        AlertDialog.Builder(this)
            .setTitle("Choose your colour")
            .setItems(LudoSetup.PLAYER_NAMES) { _, which ->
                humanPlayer = which
                startGame()
            }
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showRules(showModeAfter: Boolean) {
        val message = """
            LUDO — Rules

            Roll the die and move one of your four tokens around the track. A six brings a token out of your yard and gives you another roll. Land on an opponent's token to send it home. Bring all four tokens into your home area first to win.

            In a four-player match, every colour takes a turn clockwise. During a match against the AI, you control one colour and the other three are automated.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("How to Play Ludo")
            .setMessage(message)
            .setPositiveButton("Got it") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
    }

    private fun startGame() {
        moves.clear()
        resultDialogVisible = false
        rolledValue = 0
        state = engine.initialState()
        boardView.gameState = state
        boardView.legalMoves = emptyList()
        boardView.isLocked = false
        diceView.value = 1
        updateHud()
        if (isAiTurn()) handler.postDelayed({ rollDice() }, 650L)
    }

    private fun rollDice() {
        if (state.status != GameStatus.IN_PROGRESS || rolledValue != 0 || boardView.isLocked) return

        val nextValue = Random.nextInt(1, 7)
        diceView.rollTo(nextValue) {
            rolledValue = nextValue
            state = state.copy(metadata = state.metadata + ("ludo_dice" to nextValue))
            val player = LudoSetup.playerFromState(state)
            val legal = engine.legalMovesForDice(state, player, nextValue)
            boardView.gameState = state
            boardView.legalMoves = legal
            updateHud()
            if (legal.isEmpty()) {
                Toast.makeText(this, "No move possible — turn skipped", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ finishTurnAfterNoMove() }, 520L)
            } else if (isAiTurn()) {
                handler.postDelayed({ playMove(chooseAiMove(legal)) }, 420L)
            }
        }
    }

    private fun chooseAiMove(legal: List<Move>): Move =
        legal.maxByOrNull { (it.metadata["targetProgress"] as? Int ?: 0) * 10 +
            (if (it.captures.isNotEmpty()) 8 else 0) + Random.nextInt(0, 4)
        } ?: legal.first()

    private fun playMove(move: Move) {
        if (state.status != GameStatus.IN_PROGRESS || rolledValue == 0 || boardView.isLocked) return
        boardView.legalMoves = emptyList()
        boardView.animateMove(move) {
            state = engine.applyMove(state, move)
            moves += move
            rolledValue = 0
            boardView.gameState = state
            boardView.legalMoves = emptyList()
            if (state.status == GameStatus.IN_PROGRESS) {
                updateHud()
                if (isAiTurn()) handler.postDelayed({ rollDice() }, 420L)
            } else {
                updateHud()
                handler.postDelayed({ showResultDialog() }, 220L)
            }
        }
    }

    private fun finishTurnAfterNoMove() {
        if (state.status != GameStatus.IN_PROGRESS || rolledValue == 0) return
        val player = LudoSetup.playerFromState(state)
        val nextPlayer = if (rolledValue == 6) player else (player + 1) % LudoSetup.PLAYER_COUNT
        state = state.copy(
            currentTurn = LudoSetup.colorForPlayer(nextPlayer),
            metadata = mapOf("ludo_turn" to nextPlayer, "ludo_dice" to 0)
        )
        rolledValue = 0
        boardView.gameState = state
        boardView.legalMoves = emptyList()
        updateHud()
        if (isAiTurn()) handler.postDelayed({ rollDice() }, 420L)
    }

    private fun isHumanTurn(): Boolean = !vsAI || LudoSetup.playerFromState(state) == humanPlayer
    private fun isAiTurn(): Boolean = vsAI && !isHumanTurn()

    private fun updateHud() {
        val player = LudoSetup.playerFromState(state)
        val text = when {
            state.status != GameStatus.IN_PROGRESS -> "${LudoSetup.PLAYER_NAMES[state.metadata["ludo_winner"] as? Int ?: player]} wins"
            isAiTurn() -> "${LudoSetup.PLAYER_NAMES[player]} is thinking"
            rolledValue != 0 -> "${LudoSetup.PLAYER_NAMES[player]}: choose a token"
            else -> "${LudoSetup.PLAYER_NAMES[player]}: roll the die"
        }
        turnView.text = text
        turnView.setTextColor(Color.rgb(
            Color.red(LudoSetup.PLAYER_COLORS[player]),
            Color.green(LudoSetup.PLAYER_COLORS[player]),
            Color.blue(LudoSetup.PLAYER_COLORS[player])
        ))
    }

    private fun showResultDialog() {
        if (state.status == GameStatus.IN_PROGRESS || resultDialogVisible) return
        resultDialogVisible = true
        val winner = state.metadata["ludo_winner"] as? Int ?: 0
        val result = "${LudoSetup.PLAYER_NAMES[winner]} wins!"
        AlertDialog.Builder(this)
            .setTitle("Ludo")
            .setMessage(result)
            .setPositiveButton("New Match") { _, _ -> startGame() }
            .setNegativeButton("Main Menu") { _, _ -> finish() }
            .setOnDismissListener { resultDialogVisible = false }
            .show()
    }
}