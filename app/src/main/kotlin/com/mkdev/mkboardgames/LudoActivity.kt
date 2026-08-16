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
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.games.ludo.LudoRuleEngine
import com.mkdev.mkboardgames.games.ludo.LudoAbility
import com.mkdev.mkboardgames.games.ludo.LudoEconomy
import com.mkdev.mkboardgames.games.ludo.LudoNotification
import com.mkdev.mkboardgames.games.ludo.LudoPlayerEconomy
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
    private lateinit var economyView: TextView
    private lateinit var storeView: TextView
    private lateinit var overlay: FrameLayout
    private lateinit var notificationHost: LinearLayout
    private val engine = LudoRuleEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val moves = mutableListOf<Move>()

    private var state: GameState = engine.initialState()
    private var vsAI = true
    private var humanPlayer = 0
    private var irregularMode = false
    private var economyEnabled = false
    private var aiDifficulty = 0
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
        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2A3035"), Color.parseColor("#0C1014")),
            )
        }
        overlay = FrameLayout(this)
        overlay.addView(contentRoot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        notificationHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
        }
        overlay.addView(
            notificationHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = (150 * dp).toInt()
                marginStart = (10 * dp).toInt()
                marginEnd = (10 * dp).toInt()
            },
        )
        turnView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(12, (6 * dp).toInt(), 12, (6 * dp).toInt())
            elevation = 4 * dp
        }
        val economyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 13f * dp
                setColor(Color.argb(205, 12, 17, 23))
                setStroke((1 * dp).toInt(), Color.argb(130, 232, 184, 74))
            }
        }
        economyView = TextView(this).apply {
            setTextColor(Color.rgb(240, 205, 112))
            setTextSize(14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        storeView = TextView(this).apply {
            text = "STORE"
            setTextColor(Color.WHITE)
            setTextSize(12f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 10f * dp
                setColor(Color.rgb(47, 84, 110))
            }
            setOnClickListener { if (economyEnabled && humanPlayerCanUseStore()) showStoreDialog() }
        }
        economyBar.addView(economyView, LinearLayout.LayoutParams(0, (38 * dp).toInt(), 1f))
        economyBar.addView(storeView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (32 * dp).toInt(),
        ))
        statusView = LudoStatusStripView(this)
        boardView = LudoBoardView(this)
        diceView = LudoDiceView(this)
        motionView = LudoControlTileView(this, LudoControlTileView.ControlType.MOTION)
        tapRollView = LudoControlTileView(this, LudoControlTileView.ControlType.TAP_TO_ROLL)

        contentRoot.addView(turnView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (54 * dp).toInt()
        ).apply {
            bottomMargin = (8 * dp).toInt()
        })
        contentRoot.addView(economyBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (42 * dp).toInt(),
        ).apply {
            bottomMargin = (6 * dp).toInt()
        })
        contentRoot.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (92 * dp).toInt(),
        ).apply {
            bottomMargin = (6 * dp).toInt()
        })
        contentRoot.addView(boardView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        controls.addView(motionView, LinearLayout.LayoutParams(0, (96 * dp).toInt(), 1f).apply {
            marginEnd = (6 * dp).toInt()
        })
        controls.addView(diceView, LinearLayout.LayoutParams((112 * dp).toInt(), (112 * dp).toInt()).apply {
            marginEnd = (6 * dp).toInt()
        })
        controls.addView(tapRollView, LinearLayout.LayoutParams(0, (96 * dp).toInt(), 1f))
        contentRoot.addView(controls, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (116 * dp).toInt()
        ))
        AdManager.attachBanner(contentRoot)
        setContentView(overlay)

        boardView.onMoveSelected = { move ->
            if (isHumanTurn()) playMove(move)
        }
        boardView.onGameOverTapped = { showResultDialog() }
        statusView.onPlayerProfileTapped = { player ->
            if (profilesEnabled()) showPlayerProfile(player)
        }
        statusView.profilesEnabled = false
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
                    rollDice(direction, false)
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
            .setItems(arrayOf(
                "vs AI · Normal",
                "vs AI · Irregular",
                "4 Players",
                "How to Play",
            )) { _, which ->
                when (which) {
                    0 -> {
                        vsAI = true
                        irregularMode = false
                        showPlayerPicker()
                    }
                    1 -> {
                        vsAI = true
                        irregularMode = true
                        showPlayerPicker()
                    }
                    2 -> {
                        vsAI = false
                        irregularMode = false
                        startGame()
                    }
                    3 -> showRules(true)
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

            Normal mode is classic Ludo. Irregular mode gives every colour ${LudoEconomy.STARTER_COINS} match-only coins. Captures, tokens reaching home, and final placement reward coins. STORE abilities last for the match: Invincibility blocks one capture, Extra Move adds two spaces, and Reroll replaces the current die. AI behaviour and profiles are visible only in Irregular mode.
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
        economyEnabled = irregularMode && vsAI
        aiDifficulty = SettingsManager.getLudoDifficulty(this)
        val initialState = engine.initialState()
        state = initialState.copy(
            metadata = initialState.metadata + mapOf(
                "ludo_economy_enabled" to economyEnabled,
            ),
        )
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
        rollDice(MotionDiceDirection.UP, false)
    }

    private fun rollDice(motionDirection: MotionDiceDirection, isReroll: Boolean) {
        if (state.status != GameStatus.IN_PROGRESS ||
            (rolledValue != 0 && !isReroll) ||
            boardView.isLocked
        ) return

        val player = LudoSetup.playerFromState(state)
        if (isAiTurn() && irregularMode && !isReroll) aiPrepareTurn(player)

        val nextValue = Random.nextInt(1, 7)
        diceView.rollTo(nextValue, motionDirection) {
            val previousRolledValue = rolledValue
            rolledValue = nextValue
            val storedSixStreak =
                (state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0).coerceAtLeast(0)
            val previousSixStreak = if (isReroll && previousRolledValue == 6) {
                (storedSixStreak - 1).coerceAtLeast(0)
            } else {
                storedSixStreak
            }
            val sixStreak = if (nextValue == 6) previousSixStreak + 1 else 0
            state = state.copy(
                metadata = state.metadata + mapOf(
                    "ludo_dice" to nextValue,
                    LudoSetup.SIX_STREAK_METADATA to sixStreak,
                    "ludo_rerolled" to isReroll,
                )
            )
            val legal = engine.legalMovesForDice(state, player, nextValue)
            boardView.gameState = state
            boardView.legalMoves = legal
            updateHud()
            if (isAiTurn() && irregularMode && aiShouldReroll(player, nextValue, legal)) {
                consumeAbility(player, LudoAbility.REROLL)
                showFloatingNotification(
                    "${LudoSetup.PLAYER_NAMES[player]} used 🎲 Reroll\nRolled $nextValue → next roll",
                    player,
                )
                handler.postDelayed({ rollDice(MotionDiceDirection.UP, true) }, 380L)
                return@rollTo
            }
            val preparedMoves = if (isAiTurn() && irregularMode) {
                aiMaybeUseExtraMove(player, nextValue, legal)
            } else {
                legal
            }
            if (preparedMoves.isEmpty()) {
                if (nextValue == 6 && sixStreak >= 3) {
                    showHudMessage("THREE SIXES — turn forfeited")
                    Toast.makeText(this, "Three sixes — turn forfeited", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ finishTurnAfterNoMove() }, 760L)
                } else {
                    Toast.makeText(this, "No move possible — turn skipped", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ finishTurnAfterNoMove() }, 520L)
                }
            } else if (isAiTurn()) {
                handler.postDelayed({ playMove(chooseAiMove(preparedMoves)) }, 420L)
            }
        }
    }

    private fun chooseAiMove(legal: List<Move>): Move =
        legal.maxByOrNull {
            val target = it.metadata["targetProgress"] as? Int ?: 0
            val finishBonus = if (target == LudoSetup.FINISH) 120 else 0
            val captureBonus = if (it.captures.isNotEmpty()) 70 else 0
            val launchBonus = if (target == 0) 18 else 0
            val noise = when (aiDifficulty) {
                0 -> Random.nextInt(0, 36)
                1 -> Random.nextInt(0, 10)
                else -> Random.nextInt(0, 3)
            }
            target * 10 + finishBonus + captureBonus + launchBonus + noise
        } ?: legal.first()

    private fun profilesEnabled(): Boolean = economyEnabled && irregularMode && vsAI

    private fun humanPlayerCanUseStore(): Boolean =
        economyEnabled && matchStarted && state.status == GameStatus.IN_PROGRESS && isHumanTurn()

    private fun aiPrepareTurn(player: Int) {
        if (!economyEnabled || !isAiTurn()) return
        var economy = LudoEconomy.player(state, player)
        val pieces = LudoSetup.allPieces(state).filter { it.player == player }
        val exposedPiece = pieces
            .filter { it.progress in 0 until 52 }
            .maxByOrNull { it.progress }
        val shouldProtect = exposedPiece != null &&
            economy.protectedToken == null &&
            economy.invincibility > 0 &&
            (aiDifficulty > 0 || exposedPiece.progress > 20)

        if (shouldProtect && exposedPiece != null) {
            economy = economy.copy(
                invincibility = (economy.invincibility - 1).coerceAtLeast(0),
                protectedToken = exposedPiece.token,
            )
            setPlayerEconomy(player, economy)
            showFloatingNotification(
                "${LudoSetup.PLAYER_NAMES[player]} used 🛡 Invincibility\nToken ${exposedPiece.token + 1} protected",
                player,
            )
        } else {
            val purchase = when {
                economy.coins >= LudoAbility.INVINCIBILITY.cost &&
                    economy.invincibility == 0 && exposedPiece != null &&
                    (aiDifficulty > 0 || Random.nextBoolean()) -> LudoAbility.INVINCIBILITY
                economy.coins >= LudoAbility.REROLL.cost &&
                    economy.reroll == 0 && aiDifficulty >= 1 -> LudoAbility.REROLL
                economy.coins >= LudoAbility.EXTRA_MOVE.cost &&
                    economy.extraMove == 0 && aiDifficulty >= 2 -> LudoAbility.EXTRA_MOVE
                else -> null
            }
            if (purchase != null) {
                LudoEconomy.purchase(economy, purchase)?.let {
                    setPlayerEconomy(player, it)
                    showFloatingNotification(
                        "${LudoSetup.PLAYER_NAMES[player]} bought ${purchase.icon} ${purchase.label}",
                        player,
                    )
                }
            }
        }
    }

    private fun aiShouldReroll(player: Int, dice: Int, legal: List<Move>): Boolean {
        if (!economyEnabled || state.metadata["ludo_rerolled"] == true) return false
        if (LudoEconomy.player(state, player).reroll <= 0) return false
        return when (aiDifficulty) {
            0 -> legal.isEmpty() && Random.nextBoolean()
            1 -> legal.isEmpty() || dice <= 2
            else -> legal.isEmpty() || dice <= 2
        }
    }

    private fun aiMaybeUseExtraMove(player: Int, dice: Int, legal: List<Move>): List<Move> {
        if (!economyEnabled || LudoEconomy.player(state, player).extraMove <= 0) return legal
        val sixStreak = state.metadata[LudoSetup.SIX_STREAK_METADATA] as? Int ?: 0
        if (dice == 6 && sixStreak >= 3) return legal
        val boosted = engine.legalMovesForDice(
            state,
            player,
            dice + 2,
            baseDice = dice,
            usedExtraMove = true,
        )
        val normalValue = legal.maxOfOrNull { (it.metadata["targetProgress"] as? Int ?: 0) } ?: -1
        val boostedValue = boosted.maxOfOrNull { (it.metadata["targetProgress"] as? Int ?: 0) } ?: -1
        val shouldUse = boosted.isNotEmpty() && (
            legal.isEmpty() ||
                boosted.any { it.captures.isNotEmpty() } ||
                boosted.any { (it.metadata["targetProgress"] as? Int) == LudoSetup.FINISH } ||
                (aiDifficulty >= 2 && boostedValue > normalValue + 8)
            )
        if (!shouldUse) return legal
        consumeAbility(player, LudoAbility.EXTRA_MOVE)
        boardView.legalMoves = boosted
        showFloatingNotification(
            "${LudoSetup.PLAYER_NAMES[player]} used ⚡ Extra Move\n+2 spaces",
            player,
        )
        return boosted
    }

    private fun setPlayerEconomy(player: Int, economy: LudoPlayerEconomy) {
        state = state.copy(metadata = LudoEconomy.withPlayer(state, player, economy))
        boardView.gameState = state
        updateHud()
    }

    private fun consumeAbility(player: Int, ability: LudoAbility) {
        setPlayerEconomy(player, LudoEconomy.consume(LudoEconomy.player(state, player), ability))
    }

    private fun showStoreDialog() {
        val player = humanPlayer
        val economy = LudoEconomy.player(state, player)
        val options = arrayOf(
            "Buy 🛡 Invincibility · ${LudoAbility.INVINCIBILITY.cost} coins",
            "Buy ⚡ Extra Move · ${LudoAbility.EXTRA_MOVE.cost} coins",
            "Buy 🎲 Reroll · ${LudoAbility.REROLL.cost} coins",
            "Use 🛡 Invincibility · ${economy.invincibility} available",
            "Use ⚡ Extra Move · ${economy.extraMove} available",
            "Use 🎲 Reroll · ${economy.reroll} available",
        )
        AlertDialog.Builder(this)
            .setTitle("Irregular Store · ${economy.coins} coins")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> buyAbility(player, LudoAbility.INVINCIBILITY)
                    1 -> buyAbility(player, LudoAbility.EXTRA_MOVE)
                    2 -> buyAbility(player, LudoAbility.REROLL)
                    3 -> chooseProtectionTarget(player)
                    4 -> useExtraMove(player, dialog)
                    5 -> useReroll(player, dialog)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun buyAbility(player: Int, ability: LudoAbility) {
        val economy = LudoEconomy.player(state, player)
        val purchased = LudoEconomy.purchase(economy, ability)
        if (purchased == null) {
            Toast.makeText(this, "Not enough coins", Toast.LENGTH_SHORT).show()
            return
        }
        setPlayerEconomy(player, purchased)
        showFloatingNotification(
            "YOU bought ${ability.icon} ${ability.label}",
            player,
        )
    }

    private fun chooseProtectionTarget(player: Int) {
        val economy = LudoEconomy.player(state, player)
        if (economy.invincibility <= 0) {
            Toast.makeText(this, "Buy Invincibility first", Toast.LENGTH_SHORT).show()
            return
        }
        val pieces = LudoSetup.allPieces(state)
            .filter { it.player == player && it.progress in 0 until LudoSetup.FINISH }
        if (pieces.isEmpty()) {
            Toast.makeText(this, "Move a token onto the track first", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = pieces.map { "Token ${it.token + 1} · ${progressLabel(it.progress)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Protect which token?")
            .setItems(labels) { _, index ->
                val piece = pieces[index]
                setPlayerEconomy(
                    player,
                    economy.copy(
                        invincibility = economy.invincibility - 1,
                        protectedToken = piece.token,
                    ),
                )
                showFloatingNotification(
                    "YOU used 🛡 Invincibility\nToken ${piece.token + 1} protected",
                    player,
                )
            }
            .show()
    }

    private fun useExtraMove(player: Int, dialog: android.content.DialogInterface) {
        if (rolledValue == 0) {
            Toast.makeText(this, "Roll the die first", Toast.LENGTH_SHORT).show()
            return
        }
        if (LudoEconomy.player(state, player).extraMove <= 0) {
            Toast.makeText(this, "Buy Extra Move first", Toast.LENGTH_SHORT).show()
            return
        }
        val boosted = engine.legalMovesForDice(
            state,
            player,
            rolledValue + 2,
            baseDice = rolledValue,
            usedExtraMove = true,
        )
        if (boosted.isEmpty()) {
            Toast.makeText(this, "Extra Move has no legal target", Toast.LENGTH_SHORT).show()
            return
        }
        consumeAbility(player, LudoAbility.EXTRA_MOVE)
        boardView.legalMoves = boosted
        showFloatingNotification("YOU used ⚡ Extra Move\n+2 spaces", player)
        dialog.dismiss()
    }

    private fun useReroll(player: Int, dialog: android.content.DialogInterface) {
        if (rolledValue == 0) {
            Toast.makeText(this, "Roll the die first", Toast.LENGTH_SHORT).show()
            return
        }
        if (state.metadata["ludo_rerolled"] == true) {
            Toast.makeText(this, "Only one reroll per turn", Toast.LENGTH_SHORT).show()
            return
        }
        if (LudoEconomy.player(state, player).reroll <= 0) {
            Toast.makeText(this, "Buy Reroll first", Toast.LENGTH_SHORT).show()
            return
        }
        consumeAbility(player, LudoAbility.REROLL)
        showFloatingNotification("YOU used 🎲 Reroll", player)
        dialog.dismiss()
        rollDice(MotionDiceDirection.UP, true)
    }

    private fun progressLabel(progress: Int): String = when {
        progress < 0 -> "BASE"
        progress >= LudoSetup.FINISH -> "HOME"
        else -> "$progress spaces"
    }

    private fun showPlayerProfile(player: Int) {
        if (!profilesEnabled()) return
        val economy = LudoEconomy.player(state, player)
        val pieces = LudoSetup.allPieces(state)
            .filter { it.player == player }
            .sortedBy { it.token }
        val tokenLines = pieces.joinToString("\n") {
            "Token ${it.token + 1} · ${progressLabel(it.progress)}"
        }
        val message = """
            Coins: ${economy.coins}

            Abilities
            🛡 Invincibility ×${economy.invincibility}${if (economy.protectedToken != null) " · Token ${economy.protectedToken + 1} protected" else ""}
            ⚡ Extra Move ×${economy.extraMove}
            🎲 Reroll ×${economy.reroll}

            Tokens
            $tokenLines
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("${LudoSetup.PLAYER_NAMES[player]} · AI profile")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showFloatingNotification(message: String, player: Int) {
        if (!::notificationHost.isInitialized) return
        val density = resources.displayMetrics.density
        val banner = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding((14 * density).toInt(), (9 * density).toInt(), (14 * density).toInt(), (9 * density).toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            setLineSpacing(0f, 1.05f)
            background = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(Color.argb(240, 16, 23, 31))
                setStroke((2 * density).toInt(), LudoSetup.PLAYER_COLORS[player])
            }
            elevation = 10 * density
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = (8 * density).toInt()
        }
        notificationHost.addView(banner, params)
        banner.post {
            banner.translationX = resources.displayMetrics.widthPixels.toFloat()
            banner.animate()
                .translationX(0f)
                .setDuration(360L)
                .withEndAction {
                    banner.postDelayed({
                        banner.animate()
                            .translationX(-resources.displayMetrics.widthPixels.toFloat())
                            .setDuration(420L)
                            .withEndAction { notificationHost.removeView(banner) }
                            .start()
                    }, 3200L)
                }
                .start()
        }
    }

    private fun playMove(move: Move) {
        if (state.status != GameStatus.IN_PROGRESS || rolledValue == 0 || boardView.isLocked) return
        boardView.legalMoves = emptyList()
        boardView.animateMove(move) {
            val movingPlayer = move.metadata["player"] as? Int
                ?: LudoSetup.playerFromState(state)
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
            if (economyEnabled) {
                (state.metadata[LudoEconomy.NOTIFICATION_METADATA] as? LudoNotification)?.let {
                    showFloatingNotification(
                        "${LudoSetup.PLAYER_NAMES[it.player]} ${it.message}",
                        it.player,
                    )
                }
                if (move.metadata["usedExtraMove"] == true) {
                    showFloatingNotification(
                        "${LudoSetup.PLAYER_NAMES[movingPlayer]} used ⚡ Extra Move\n+2 spaces",
                        movingPlayer,
                    )
                }
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
                "ludo_rerolled" to false,
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
        statusView.profilesEnabled = profilesEnabled()
        if (economyEnabled) {
            val economy = LudoEconomy.player(state, humanPlayer)
            economyView.text = "IRREGULAR  •  COINS ${economy.coins}"
            storeView.visibility = View.VISIBLE
        } else {
            economyView.text = "NORMAL  •  CLASSIC LUDO"
            storeView.visibility = View.GONE
        }
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