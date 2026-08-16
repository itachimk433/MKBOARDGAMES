package com.mkdev.mkboardgames

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
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
import com.mkdev.mkboardgames.ui.LudoControlTileView
import com.mkdev.mkboardgames.ui.LudoDiceView
import com.mkdev.mkboardgames.ui.LudoStatusStripView
import com.mkdev.mkboardgames.ui.MotionDiceDirection
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class LudoActivity : AppCompatActivity() {
    private lateinit var boardView: LudoBoardView
    private lateinit var diceView: LudoDiceView
    private lateinit var statusView: LudoStatusStripView
    private lateinit var motionView: LudoControlTileView
    private lateinit var tapRollView: LudoControlTileView
    private lateinit var turnView: TextView
    private val engine = LudoRuleEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val moves = mutableListOf<Move>()

    private var state: GameState = engine.initialState()
    private var vsAI = true
    private var humanPlayer = 0
    private var matchStarted = false
    private var rolledValue = 0
    private var resultDialogVisible = false
    private var celebrationMessage: String? = null
    private var celebrationGeneration = 0
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var usingRawAccelerometer = false
    private var lastMotionAt = 0L
    private val gravity = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)
        SettingsManager.activateGameTheme(this, "ludo")

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2A3035"), Color.parseColor("#0C1014")),
            )
        }
        turnView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(12, (6 * dp).toInt(), 12, (6 * dp).toInt())
            elevation = 4 * dp
        }
        statusView = LudoStatusStripView(this)
        boardView = LudoBoardView(this)
        diceView = LudoDiceView(this)
        motionView = LudoControlTileView(this, LudoControlTileView.ControlType.MOTION)
        tapRollView = LudoControlTileView(this, LudoControlTileView.ControlType.TAP_TO_ROLL)

        root.addView(turnView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (54 * dp).toInt()
        ).apply {
            bottomMargin = (8 * dp).toInt()
        })
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (92 * dp).toInt(),
        ).apply {
            bottomMargin = (6 * dp).toInt()
        })
        root.addView(boardView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        controls.addView(motionView, LinearLayout.LayoutParams(0, (112 * dp).toInt(), 1f).apply {
            marginEnd = (6 * dp).toInt()
        })
        controls.addView(diceView, LinearLayout.LayoutParams((112 * dp).toInt(), (112 * dp).toInt()).apply {
            marginEnd = (6 * dp).toInt()
        })
        controls.addView(tapRollView, LinearLayout.LayoutParams(0, (112 * dp).toInt(), 1f))
        root.addView(controls, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (116 * dp).toInt()
        ))
        AdManager.attachBanner(root)
        setContentView(root)

        boardView.onMoveSelected = { move ->
            if (isHumanTurn()) playMove(move)
        }
        boardView.onGameOverTapped = { showResultDialog() }
        diceView.onRoll = { if (matchStarted && isHumanTurn()) rollDice() }
        tapRollView.onTap = { if (matchStarted && isHumanTurn()) rollDice() }
        motionView.motionEnabled = SettingsManager.isMotionDiceEnabled(this)
        motionView.onMotionToggle = { enabled ->
            SettingsManager.setMotionDiceEnabled(this, enabled)
            syncMotionSensor()
            Toast.makeText(
                this,
                if (enabled) "Motion dice enabled" else "Motion dice disabled",
                Toast.LENGTH_SHORT,
            ).show()
        }
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                usingRawAccelerometer = true
            }
        showModeDialog()
    }

    override fun onResume() {
        super.onResume()
        makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        motionView.motionEnabled = SettingsManager.isMotionDiceEnabled(this)
        syncMotionSensor()
    }

    override fun onPause() {
        sensorManager.unregisterListener(motionListener)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(motionListener)
        super.onDestroy()
    }

    private val motionListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            val raw = event.values
            val values = FloatArray(3)
            if (usingRawAccelerometer) {
                for (i in 0..2) {
                    gravity[i] = 0.88f * gravity[i] + 0.12f * raw[i]
                    values[i] = raw[i] - gravity[i]
                }
            } else {
                for (i in 0..2) values[i] = raw[i]
            }

            val x = values[0]
            val y = values[1]
            val horizontal = abs(x)
            val vertical = abs(y)
            val magnitude = sqrt(x * x + y * y + values[2] * values[2])
            if (magnitude < 3.0f || vertical < 1.8f && horizontal < 2.6f) return

            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastMotionAt < 900L) return
            if (!matchStarted || !isHumanTurn() || state.status != GameStatus.IN_PROGRESS ||
                rolledValue != 0 || boardView.isLocked
            ) return

            val direction = when {
                vertical > 2.2f && horizontal > 1.8f ->
                    if (x < 0f) MotionDiceDirection.TOP_LEFT
                    else MotionDiceDirection.TOP_RIGHT
                horizontal > vertical * 1.15f ->
                    if (x < 0f) MotionDiceDirection.LEFT
                    else MotionDiceDirection.RIGHT
                vertical >= horizontal -> MotionDiceDirection.UP
                else -> null
            } ?: return

            lastMotionAt = now
            runOnUiThread {
                if (matchStarted && SettingsManager.isMotionDiceEnabled(this@LudoActivity)) {
                    rollDice(direction)
                }
            }
        }
    }

    private fun syncMotionSensor() {
        sensorManager.unregisterListener(motionListener)
        if (matchStarted && SettingsManager.isMotionDiceEnabled(this)) {
            motionSensor?.let { sensor ->
                sensorManager.registerListener(
                    motionListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }
        }
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

            Three sixes in a row forfeit the turn. An exact roll is required to reach the center. In a four-player match, every colour takes a turn clockwise. During a match against the AI, you control one colour and the other three are automated.
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
        celebrationMessage = null
        celebrationGeneration++
        matchStarted = true
        state = engine.initialState()
        boardView.gameState = state
        boardView.legalMoves = emptyList()
        boardView.isLocked = false
        diceView.value = 1
        motionView.motionEnabled = SettingsManager.isMotionDiceEnabled(this)
        syncMotionSensor()
        updateHud()
        if (isAiTurn()) handler.postDelayed({ rollDice() }, 650L)
    }

    private fun rollDice() {
        rollDice(MotionDiceDirection.UP)
    }

    private fun rollDice(motionDirection: MotionDiceDirection) {
        if (state.status != GameStatus.IN_PROGRESS || rolledValue != 0 || boardView.isLocked) return

        val nextValue = Random.nextInt(1, 7)
        diceView.rollTo(nextValue, motionDirection) {
            rolledValue = nextValue
            val previousSixStreak =
                (state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0).coerceAtLeast(0)
            val sixStreak = if (nextValue == 6) previousSixStreak + 1 else 0
            state = state.copy(
                metadata = state.metadata + mapOf(
                    "ludo_dice" to nextValue,
                    LudoSetup.SIX_STREAK_METADATA to sixStreak,
                )
            )
            val player = LudoSetup.playerFromState(state)
            val legal = engine.legalMovesForDice(state, player, nextValue)
            boardView.gameState = state
            boardView.legalMoves = legal
            updateHud()
            if (legal.isEmpty()) {
                if (nextValue == 6 && sixStreak >= 3) {
                    showHudMessage("THREE SIXES — turn forfeited")
                    Toast.makeText(this, "Three sixes — turn forfeited", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ finishTurnAfterNoMove() }, 760L)
                } else {
                    Toast.makeText(this, "No move possible — turn skipped", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ finishTurnAfterNoMove() }, 520L)
                }
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
            val targetProgress = move.metadata["targetProgress"] as? Int
            if (targetProgress == LudoSetup.FINISH) {
                val player = move.metadata["player"] as? Int ?: LudoSetup.playerFromState(state)
                val token = (move.metadata["token"] as? Int ?: 0) + 1
                val message = "TOKEN HOME! ${LudoSetup.PLAYER_NAMES[player]} token $token"
                showHudMessage(message)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
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
        val sixStreak = state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0
        val forfeitsAfterThreeSixes = rolledValue == 6 && sixStreak >= 3
        val nextPlayer = if (rolledValue == 6 && !forfeitsAfterThreeSixes) {
            player
        } else {
            (player + 1) % LudoSetup.PLAYER_COUNT
        }
        state = state.copy(
            currentTurn = LudoSetup.colorForPlayer(nextPlayer),
            metadata = state.metadata + mapOf(
                "ludo_turn" to nextPlayer,
                "ludo_dice" to 0,
                LudoSetup.SIX_STREAK_METADATA to if (nextPlayer == player) sixStreak else 0,
            )
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
        val text = celebrationMessage ?: when {
            state.status != GameStatus.IN_PROGRESS -> "${LudoSetup.PLAYER_NAMES[state.metadata["ludo_winner"] as? Int ?: player]} wins"
            isAiTurn() -> "${LudoSetup.PLAYER_NAMES[player]} is thinking"
            rolledValue != 0 -> "${LudoSetup.PLAYER_NAMES[player]}: choose a token • move $rolledValue spaces"
            else -> "${LudoSetup.PLAYER_NAMES[player]}: roll the die"
        }
        turnView.text = text
        val accent = LudoSetup.PLAYER_COLORS[player]
        turnView.setTextColor(accent)
        turnView.background = GradientDrawable().apply {
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(Color.argb(235, 13, 18, 24))
            setStroke((2 * resources.displayMetrics.density).toInt(), Color.argb(
                220,
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent),
            ))
        }
        statusView.gameState = state
        statusView.activePlayer = player
        statusView.rolledValue = rolledValue
        motionView.motionEnabled = SettingsManager.isMotionDiceEnabled(this)
    }

    private fun showHudMessage(message: String) {
        celebrationGeneration++
        val generation = celebrationGeneration
        celebrationMessage = message
        updateHud()
        handler.postDelayed({
            if (celebrationGeneration == generation) {
                celebrationMessage = null
                updateHud()
            }
        }, 1800L)
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