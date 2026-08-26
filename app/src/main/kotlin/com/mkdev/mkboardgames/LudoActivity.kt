package com.mkdev.mkboardgames

import android.app.Dialog
import android.content.Intent
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
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.mkdev.mkboardgames.games.ludo.LudoPiece
import com.mkdev.mkboardgames.games.ludo.LudoPlayerEconomy
import com.mkdev.mkboardgames.games.ludo.LudoSetup
import com.mkdev.mkboardgames.ui.LudoBoardView
import com.mkdev.mkboardgames.ui.LudoControlTileView
import com.mkdev.mkboardgames.ui.GlbDiceView
import com.mkdev.mkboardgames.ui.LudoStatusStripView
import com.mkdev.mkboardgames.ui.MotionDiceDirection
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

class LudoActivity : AppCompatActivity() {
    private lateinit var boardView: LudoBoardView
    private lateinit var diceView: GlbDiceView
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
    private var ludoResultRecorded = false
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
        boardView.onMoveStep = { SoundPlayer.playMovement("ludo_move") }
        diceView = GlbDiceView(this)
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
        boardView.onTokenLongPressed = { piece -> showTokenAbilityDialog(piece) }
        boardView.onGameOverTapped = { showResultDialog() }
        statusView.onPlayerProfileTapped = { player ->
            if (profilesEnabled()) showPlayerProfile(player) else showRenameDialog(player)
        }
        statusView.profilesEnabled = true
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
        hideBoardWhileDialogIsOpen()
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
            hideBoardWhileDialogIsOpen()
            StyledDialogs.showChoices(this, "Leave Match?", "Leaving counts as a forfeit.",
                listOf(
                    StyledDialogs.choice("Leave Match", "Forfeit this game", "⚑", "#E58A7A"),
                    StyledDialogs.choice("Keep Playing", "Return to the board", "↩", "#A9B6E8"),
                ), 420f, "L U D O", onCancel = { showBoardAfterDialog() }) { which, dialog ->
                    dialog.dismiss()
                    if (which == 0) super.onBackPressed() else showBoardAfterDialog()
                }
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
        hideBoardWhileDialogIsOpen()
        val options = listOf("vs AI · Normal", "vs AI · Irregular", "4 Players", "How to Play")
        StyledDialogs.showChoices(this, "Ludo", "Choose how to begin.",
            listOf(
                StyledDialogs.choice(options[0], "Classic computer match", "⚄", "#8EC7B9"),
                StyledDialogs.choice(options[1], "Match with abilities and coins", "⚄", "#E3B86A"),
                StyledDialogs.choice(options[2], "Play locally with four colours", "⚄", "#A9B6E8"),
                StyledDialogs.choice(options[3], "Review the essentials", "⚄", "#E58A7A"),
            ), 520f, "L U D O", headerSymbol = "⚄", onCancel = {
                if (!matchStarted) finish() else showBoardAfterDialog()
            }) { which, dialog ->
                when (which) {
                    0 -> {
                        vsAI = true
                        irregularMode = false
                        dialog.dismiss()
                        showPlayerPicker()
                    }
                    1 -> {
                        vsAI = true
                        irregularMode = true
                        dialog.dismiss()
                        showPlayerPicker()
                    }
                    2 -> {
                        vsAI = false
                        irregularMode = false
                        dialog.dismiss()
                        startGame()
                    }
                    3 -> {
                        dialog.dismiss()
                        showRules(true)
                    }
                }
            }
    }

    private fun showPlayerPicker() {
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(this, "Play As", "Choose your colour before the first roll.",
            LudoSetup.PLAYER_NAMES.mapIndexed { index, name ->
                StyledDialogs.choice(name, if (index == 0) "Moves first" else "Joins the match", listOf("●", "●", "●", "●")[index], listOf("#E3B86A", "#E58A7A", "#8EC7B9", "#A9B6E8")[index])
            }, 520f, "L U D O", headerSymbol = "⚄", onCancel = { showModeDialog() }) { which, dialog ->
                humanPlayer = which
                dialog.dismiss()
                startGame()
            }
    }

    private fun showRules(showModeAfter: Boolean) {
        hideBoardWhileDialogIsOpen()
        val message = """
            LUDO — Rules

            Roll the die and move one of your four tokens around the track. A six brings a token out of your yard and gives you another roll. Land on an opponent's token to send it home. Bring all four tokens into your home area first to win.

            Three sixes in a row forfeit the turn. An exact roll is required to reach the center. In a four-player match, every colour takes a turn clockwise. During a match against the AI, you control one colour and the other three are automated.

            Normal mode is classic Ludo. Irregular mode gives every colour ${LudoEconomy.STARTER_COINS} match-only coins. Captures, tokens reaching home, and final placement reward coins. STORE abilities last for the match: Invincibility blocks one capture, Extra Move adds two spaces, and Reroll replaces the current die. AI behaviour and profiles are visible only in Irregular mode.
        """.trimIndent()
        StyledDialogs.showRules(this, "Ludo", message, "L U D O",
            onDone = { if (showModeAfter) showModeDialog() else showBoardAfterDialog() })
    }

    private fun startGame() {
        showBoardAfterDialog()
        moves.clear()
        resultDialogVisible = false
        ludoResultRecorded = false
        rolledValue = 0
        celebrationMessage = null
        celebrationGeneration++
        matchStarted = true
        SoundPlayer.playMovement("ludo_start")
        economyEnabled = irregularMode && vsAI
        if (vsAI) SettingsManager.setActiveGame(this, "ludo")
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
        SoundPlayer.playMovement("ludo_dice")
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
        val canPurchase = state.metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] != true
        val canUseAbility = state.metadata[LudoEconomy.USED_ABILITY_METADATA] != true
        val pieces = LudoSetup.allPieces(state).filter { it.player == player }
        val exposedPiece = pieces
            .filter { it.progress in 0 until 52 }
            .maxByOrNull { it.progress }
        val shouldProtect = exposedPiece != null &&
            economy.protectedToken == null &&
            economy.invincibility > 0 &&
            canUseAbility &&
            (aiDifficulty > 0 || exposedPiece.progress > 20)

        if (shouldProtect && exposedPiece != null) {
            economy = economy.copy(
                invincibility = (economy.invincibility - 1).coerceAtLeast(0),
                protectedToken = exposedPiece.token,
            )
            setPlayerEconomy(player, economy, usedThisTurn = true)
            showFloatingNotification(
                "${LudoSetup.PLAYER_NAMES[player]} used 🛡 Invincibility\nToken ${exposedPiece.token + 1} protected",
                player,
            )
        } else if (canPurchase) {
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
                    setPlayerEconomy(player, it, purchasedThisTurn = true)
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
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) return false
        if (LudoEconomy.player(state, player).reroll <= 0) return false
        return when (aiDifficulty) {
            0 -> legal.isEmpty() && Random.nextBoolean()
            1 -> legal.isEmpty() || dice <= 2
            else -> legal.isEmpty() || dice <= 2
        }
    }

    private fun aiMaybeUseExtraMove(player: Int, dice: Int, legal: List<Move>): List<Move> {
        if (!economyEnabled || LudoEconomy.player(state, player).extraMove <= 0) return legal
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) return legal
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

    private fun setPlayerEconomy(
        player: Int,
        economy: LudoPlayerEconomy,
        purchasedThisTurn: Boolean? = null,
        usedThisTurn: Boolean? = null,
    ) {
        val existingLegalMoves = boardView.legalMoves
        val metadata = LudoEconomy.withPlayer(state, player, economy).toMutableMap()
        if (purchasedThisTurn != null) {
            metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] = purchasedThisTurn
        }
        if (usedThisTurn != null) {
            metadata[LudoEconomy.USED_ABILITY_METADATA] = usedThisTurn
        }
        state = state.copy(
            metadata = metadata,
        )
        boardView.gameState = state
        boardView.legalMoves = if (existingLegalMoves.isNotEmpty()) {
            existingLegalMoves
        } else if (rolledValue != 0 && isHumanTurn()) {
            engine.legalMovesForDice(state, humanPlayer, rolledValue)
        } else {
            emptyList()
        }
        updateHud()
    }

    private fun consumeAbility(player: Int, ability: LudoAbility) {
        setPlayerEconomy(
            player,
            LudoEconomy.consume(LudoEconomy.player(state, player), ability),
            usedThisTurn = true,
        )
    }

    private fun showTokenAbilityDialog(piece: LudoPiece) {
        if (!economyEnabled || !matchStarted || state.status != GameStatus.IN_PROGRESS ||
            !isHumanTurn() || piece.player != humanPlayer
        ) {
            return
        }
        val economy = LudoEconomy.player(state, humanPlayer)
        val ownedAbilities = LudoAbility.values().filter { ability ->
            LudoEconomy.abilityCount(economy, ability) > 0
        }
        if (ownedAbilities.isEmpty()) return

        val labels = ownedAbilities.map { ability ->
            "${ability.label} · ${abilityDescription(ability)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Apply to ${economy.tokenName} ${piece.token + 1}")
            .setItems(labels) { dialog, index ->
                when (ownedAbilities[index]) {
                    LudoAbility.INVINCIBILITY -> applyInvincibilityToToken(piece, dialog)
                    LudoAbility.EXTRA_MOVE -> useExtraMoveOnToken(piece, dialog)
                    LudoAbility.REROLL -> useReroll(humanPlayer, dialog)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun abilityDescription(ability: LudoAbility): String = when (ability) {
        LudoAbility.INVINCIBILITY -> "protect this token"
        LudoAbility.EXTRA_MOVE -> "move this token +2 spaces"
        LudoAbility.REROLL -> "roll again before moving"
    }

    private fun applyInvincibilityToToken(piece: LudoPiece, dialog: android.content.DialogInterface) {
        val player = humanPlayer
        val economy = LudoEconomy.player(state, player)
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability use per turn", Toast.LENGTH_SHORT).show()
            return
        }
        if (economy.invincibility <= 0) {
            Toast.makeText(this, "Buy Invincibility first", Toast.LENGTH_SHORT).show()
            return
        }
        val currentPiece = LudoSetup.allPieces(state).firstOrNull {
            it.player == piece.player && it.token == piece.token
        } ?: return
        if (currentPiece.progress !in 0 until LudoSetup.FINISH) {
            Toast.makeText(this, "Move this token onto the track first", Toast.LENGTH_SHORT).show()
            return
        }
        if (economy.protectedToken != null) {
            Toast.makeText(this, "Only one token can be protected at a time", Toast.LENGTH_SHORT).show()
            return
        }
        setPlayerEconomy(
            player,
            economy.copy(
                invincibility = economy.invincibility - 1,
                protectedToken = currentPiece.token,
            ),
            usedThisTurn = true,
        )
        dialog.dismiss()
        showFloatingNotification(
            "YOU used 🛡 Invincibility\n${economy.tokenName} ${currentPiece.token + 1} protected",
            player,
        )
    }

    private fun useExtraMoveOnToken(piece: LudoPiece, dialog: android.content.DialogInterface) {
        val player = humanPlayer
        if (rolledValue == 0) {
            Toast.makeText(this, "Roll the die first", Toast.LENGTH_SHORT).show()
            return
        }
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability use per turn", Toast.LENGTH_SHORT).show()
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
        ).filter { move -> (move.metadata["token"] as? Int) == piece.token }
        if (boosted.isEmpty()) {
            Toast.makeText(this, "Extra Move cannot move this token", Toast.LENGTH_SHORT).show()
            return
        }
        consumeAbility(player, LudoAbility.EXTRA_MOVE)
        boardView.legalMoves = boosted
        dialog.dismiss()
        showFloatingNotification(
            "YOU used ⚡ Extra Move\n${LudoEconomy.player(state, player).tokenName} ${piece.token + 1} · +2 spaces",
            player,
        )
    }

    private fun showStoreDialog() {
        val player = humanPlayer
        val density = resources.displayMetrics.density
        val dialog = Dialog(this)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = 0f
                setColor(Color.rgb(9, 18, 29))
                setStroke(dp(2), Color.rgb(72, 151, 235))
            }
            elevation = 18f * density
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        dialog.setContentView(shell)
        dialog.setCanceledOnTouchOutside(true)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cart = storeText("🛒", 28f, Color.WHITE, Gravity.CENTER).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = "Store"
            background = storePanel(Color.rgb(42, 20, 70), Color.rgb(170, 86, 245), 20f)
        }
        header.addView(cart, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            marginEnd = dp(12)
        })
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleColumn.addView(storeText("IRREGULAR STORE", 21f, Color.WHITE, Gravity.START))
        titleColumn.addView(storeText("Spend your coins wisely", 13f, Color.rgb(183, 192, 207), Gravity.START))
        header.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = storeText("×", 34f, Color.rgb(190, 219, 255), Gravity.CENTER).apply {
            isClickable = true
            background = storePanel(Color.TRANSPARENT, Color.rgb(56, 141, 235), 30f)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(54), dp(54)))
        content.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) })

        val economyBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = storePanel(Color.rgb(17, 29, 44), Color.rgb(46, 93, 139), 15f)
        }
        val coin = storeText("◉", 21f, Color.rgb(255, 213, 72), Gravity.CENTER)
        economyBar.addView(coin, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            marginEnd = dp(8)
        })
        val coinText = storeText("", 16f, Color.WHITE, Gravity.START)
        economyBar.addView(coinText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val turnText = storeText("", 11f, Color.rgb(137, 214, 255), Gravity.END)
        economyBar.addView(turnText)
        content.addView(economyBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) })

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(storeText("BOOSTS", 13f, Color.rgb(110, 214, 255), Gravity.CENTER).apply {
            letterSpacing = 0.18f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(7) })
        content.addView(cards)

        val activeTitle = storeText("ACTIVE BOOSTS", 13f, Color.rgb(42, 224, 226), Gravity.CENTER).apply {
            letterSpacing = 0.12f
        }
        content.addView(activeTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(15)
            bottomMargin = dp(7)
        })
        val active = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = storePanel(Color.rgb(11, 27, 40), Color.rgb(28, 74, 102), 16f)
        }
        content.addView(active)

        lateinit var render: () -> Unit
        render = {
            val economy = LudoEconomy.player(state, player)
            val purchaseUsed = state.metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] == true
            coinText.text = "${economy.coins} coins available"
            turnText.text = if (purchaseUsed) "PURCHASE USED" else "1 PURCHASE LEFT"
            cards.removeAllViews()
            LudoAbility.values().forEachIndexed { index, ability ->
                val card = buildStoreAbilityCard(
                    ability = ability,
                    economy = economy,
                    purchaseUsed = purchaseUsed,
                    onBuy = {
                        buyAbility(player, ability)
                        render()
                    },
                )
                cards.addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = if (index == LudoAbility.values().lastIndex) 0 else dp(8) })
                card.alpha = 0f
                card.translationY = dp(12).toFloat()
                card.postDelayed({
                    card.animate().alpha(1f).translationY(0f).setDuration(260L).start()
                }, index * 70L)
            }
            active.removeAllViews()
            LudoAbility.values().forEachIndexed { index, ability ->
                val count = LudoEconomy.abilityCount(economy, ability)
                val row = buildActiveBoostRow(ability, count) {
                    when (ability) {
                        LudoAbility.INVINCIBILITY -> {
                            dialog.dismiss()
                            chooseProtectionTarget(player)
                        }
                        LudoAbility.EXTRA_MOVE -> useExtraMove(player, dialog)
                        LudoAbility.REROLL -> useReroll(player, dialog)
                    }
                }
                active.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52),
                ).apply { bottomMargin = if (index == LudoAbility.values().lastIndex) 0 else dp(1) })
            }
        }
        render()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.78f)
            setLayout(
                (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(280)),
                (resources.displayMetrics.heightPixels * 0.86f).roundToInt(),
            )
        }
        shell.alpha = 0f
        shell.scaleX = 0.92f
        shell.scaleY = 0.92f
        shell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280L).start()
    }

    private fun buildStoreAbilityCard(
        ability: LudoAbility,
        economy: LudoPlayerEconomy,
        purchaseUsed: Boolean,
        onBuy: () -> Unit,
    ): LinearLayout {
        val accent = when (ability) {
            LudoAbility.INVINCIBILITY -> Color.rgb(255, 201, 71)
            LudoAbility.EXTRA_MOVE -> Color.rgb(45, 181, 255)
            LudoAbility.REROLL -> Color.rgb(196, 95, 255)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = storePanel(
                Color.argb(210, Color.red(accent) / 10, Color.green(accent) / 10, Color.blue(accent) / 10),
                Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent)),
                17f,
            )
        }
        val icon = storeText(abilityIcon(ability), 30f, accent, Gravity.CENTER).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
            contentDescription = "${ability.label} icon"
            background = storePanel(Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent)), accent, 15f)
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(11) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(storeText(ability.label.uppercase(), 16f, accent, Gravity.START))
        details.addView(storeText(
            when (ability) {
                LudoAbility.INVINCIBILITY -> "Protect a token from one capture."
                LudoAbility.EXTRA_MOVE -> "Move two extra spaces this turn."
                LudoAbility.REROLL -> "Roll the die one more time."
            },
            12f,
            Color.rgb(192, 204, 218),
            Gravity.START,
        ))
        val owned = LudoEconomy.abilityCount(economy, ability)
        details.addView(storeText("OWNED  $owned", 10f, Color.rgb(143, 232, 192), Gravity.START))
        card.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val buy = storeText(
            if (purchaseUsed) "USED" else if (economy.coins >= ability.cost) "BUY\n${ability.cost} ◉" else "NEED\n${ability.cost}",
            12f,
            if (!purchaseUsed && economy.coins >= ability.cost) Color.WHITE else Color.rgb(138, 151, 168),
            Gravity.CENTER,
        ).apply {
            isClickable = !purchaseUsed && economy.coins >= ability.cost
            background = storePanel(
                if (isClickable) Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.rgb(24, 35, 48),
                if (isClickable) accent else Color.rgb(56, 72, 92),
                13f,
            )
            if (isClickable) {
                setOnClickListener {
                    animate().scaleX(0.92f).scaleY(0.92f).setDuration(70L).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        onBuy()
                    }.start()
                }
            }
        }
        card.addView(buy, LinearLayout.LayoutParams(dp(82), dp(58)).apply { marginStart = dp(8) })
        return card
    }

    private fun buildActiveBoostRow(
        ability: LudoAbility,
        count: Int,
        onUse: () -> Unit,
    ): LinearLayout {
        val accent = when (ability) {
            LudoAbility.INVINCIBILITY -> Color.rgb(255, 201, 71)
            LudoAbility.EXTRA_MOVE -> Color.rgb(45, 181, 255)
            LudoAbility.REROLL -> Color.rgb(196, 95, 255)
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(12), dp(5))
            val icon = storeText(abilityIcon(ability), 21f, accent, Gravity.CENTER).apply {
                setPadding(dp(3), dp(3), dp(3), dp(3))
                contentDescription = "${ability.label} icon"
                alpha = if (count > 0) 1f else 0.5f
                background = storePanel(
                    Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(if (count > 0) 200 else 90, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    10f,
                )
            }
            addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginEnd = dp(10)
            })
            addView(storeText(
                ability.label.uppercase(),
                12f,
                if (count > 0) accent else Color.rgb(148, 160, 176),
                Gravity.CENTER_VERTICAL,
            ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(storeText(
                "$count available",
                11f,
                if (count > 0) Color.rgb(207, 220, 235) else Color.rgb(122, 135, 151),
                Gravity.CENTER_VERTICAL or Gravity.END,
            ))
            isClickable = count > 0
            if (count > 0) setOnClickListener { onUse() }
        }
    }

    private fun abilityIcon(ability: LudoAbility): String = when (ability) {
        LudoAbility.INVINCIBILITY -> "🛡"
        LudoAbility.EXTRA_MOVE -> "⚡"
        LudoAbility.REROLL -> "↻"
    }

    private fun storeText(text: String, size: Float, color: Int, gravity: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(size)
            this.gravity = gravity
            setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(0f, 1.04f)
        }

    private fun storePanel(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius * resources.displayMetrics.density
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun buyAbility(player: Int, ability: LudoAbility) {
        if (state.metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability purchase per turn", Toast.LENGTH_SHORT).show()
            return
        }
        val economy = LudoEconomy.player(state, player)
        val purchased = LudoEconomy.purchase(economy, ability)
        if (purchased == null) {
            Toast.makeText(this, "Not enough coins", Toast.LENGTH_SHORT).show()
            return
        }
        setPlayerEconomy(player, purchased, purchasedThisTurn = true)
        showFloatingNotification(
            "YOU bought ${ability.icon} ${ability.label}",
            player,
        )
    }

    private fun chooseProtectionTarget(player: Int) {
        val economy = LudoEconomy.player(state, player)
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability use per turn", Toast.LENGTH_SHORT).show()
            return
        }
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
                    usedThisTurn = true,
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
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability use per turn", Toast.LENGTH_SHORT).show()
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
        if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
            Toast.makeText(this, "Only one ability use per turn", Toast.LENGTH_SHORT).show()
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
        val color = LudoSetup.PLAYER_COLORS[player]
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = storePanel(Color.rgb(9, 18, 29), color, 24f)
            elevation = 18f * resources.displayMetrics.density
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(body)
        content.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = profileText(
            LudoSetup.PLAYER_NAMES[player].take(1),
            24f,
            Color.WHITE,
            Gravity.CENTER,
        ).apply {
            background = storePanel(color, Color.argb(220, 255, 255, 255), 20f)
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            marginEnd = dp(12)
        })
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        title.addView(profileText(
            "${LudoSetup.PLAYER_NAMES[player]} profile",
            21f,
            Color.WHITE,
        ))
        title.addView(profileText(
            "${economy.tokenName} · AI player",
            13f,
            Color.rgb(177, 194, 211),
        ))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(profileText("×", 32f, Color.rgb(204, 225, 244), Gravity.CENTER).apply {
            isClickable = true
            background = storePanel(Color.TRANSPARENT, Color.argb(220, 120, 178, 228), 18f)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) })

        val summary = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = storePanel(Color.rgb(17, 30, 44), Color.rgb(45, 86, 125), 15f)
        }
        summary.addView(profileText("COINS", 11f, Color.rgb(169, 190, 211)))
        summary.addView(profileText(
            economy.coins.toString(),
            20f,
            Color.rgb(255, 213, 72),
            Gravity.END,
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val protected = economy.protectedToken?.let { "Token ${it + 1} protected" } ?: "No token protected"
        summary.addView(profileText(protected, 10f, Color.rgb(147, 221, 193), Gravity.END))
        body.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(14) })

        body.addView(profileText("TOKENS", 12f, color, Gravity.START).apply {
            letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(7) })
        pieces.forEachIndexed { index, piece ->
            body.addView(buildProfileTokenRow(piece, color), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = if (index == pieces.lastIndex) dp(14) else dp(6) })
        }

        body.addView(profileText("ABILITIES", 12f, color, Gravity.START).apply {
            letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(7) })
        val abilities = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = storePanel(Color.rgb(11, 27, 40), Color.rgb(28, 74, 102), 16f)
        }
        LudoAbility.values().forEachIndexed { index, ability ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), dp(9), dp(12), dp(9))
            }
            row.addView(profileText(ability.label, 13f, Color.rgb(221, 231, 242)))
            row.addView(profileText(
                "×${LudoEconomy.abilityCount(economy, ability)}",
                14f,
                color,
                Gravity.END,
            ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (index < LudoAbility.values().lastIndex) {
                row.background = storePanel(Color.TRANSPARENT, Color.argb(55, 102, 151, 193), 0f)
            }
            abilities.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        body.addView(abilities)

        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(profileText(
            if (state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true) {
                "Ability used this turn"
            } else {
                "Ability available this turn"
            },
            11f,
            Color.rgb(157, 181, 203),
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(profileText("RENAME", 12f, Color.WHITE, Gravity.CENTER).apply {
            isClickable = true
            background = storePanel(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)), color, 12f)
            setOnClickListener {
                dialog.dismiss()
                showRenameDialog(player)
            }
        }, LinearLayout.LayoutParams(dp(84), dp(42)).apply { marginStart = dp(8) })
        body.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(2)
        })

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.78f)
            setLayout(
                (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(280)),
                (resources.displayMetrics.heightPixels * 0.78f).roundToInt(),
            )
        }
        content.alpha = 0f
        content.scaleX = 0.94f
        content.scaleY = 0.94f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240L).start()
    }

    private fun buildProfileTokenRow(piece: LudoPiece, color: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = storePanel(Color.rgb(17, 29, 42), Color.rgb(43, 77, 108), 14f)
        }
        row.addView(profileText(
            "${piece.token + 1}",
            15f,
            Color.WHITE,
            Gravity.CENTER,
        ).apply {
            background = storePanel(color, Color.argb(220, 255, 255, 255), 16f)
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        details.addView(profileText(
            "Token ${piece.token + 1}",
            13f,
            Color.WHITE,
        ))
        details.addView(profileText(
            when {
                piece.progress < 0 -> "In yard"
                piece.progress >= LudoSetup.FINISH -> "Finished in ${LudoSetup.PLAYER_NAMES[piece.player]} home"
                else -> "On track · ${piece.progress} spaces"
            },
            11f,
            Color.rgb(164, 186, 207),
        ))
        row.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(profileText(
            if (piece.progress >= LudoSetup.FINISH) "HOME" else if (piece.progress < 0) "YARD" else "ACTIVE",
            10f,
            if (piece.progress >= LudoSetup.FINISH) color else Color.rgb(153, 170, 188),
            Gravity.CENTER,
        ).apply {
            background = storePanel(
                if (piece.progress >= LudoSetup.FINISH) {
                    Color.argb(55, Color.red(color), Color.green(color), Color.blue(color))
                } else {
                    Color.rgb(25, 39, 53)
                },
                if (piece.progress >= LudoSetup.FINISH) color else Color.rgb(66, 91, 115),
                10f,
            )
        }, LinearLayout.LayoutParams(dp(66), dp(30)))
        return row
    }

    private fun profileText(
        text: String,
        size: Float,
        color: Int,
        gravity: Int = Gravity.START,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(size)
        this.gravity = gravity
        setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.04f)
    }

    private fun showRenameDialog(player: Int) {
        val economy = LudoEconomy.player(state, player)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(
                InputFilter.LengthFilter(5),
                InputFilter { source, _, _, _, _, _ ->
                    (source ?: "").filter { it.isLetter() }
                },
            )
            setText(economy.tokenName.take(5))
            setSelection(text.length)
            hint = "Up to 5 letters"
            isSingleLine = true
        }
        val padding = (22 * resources.displayMetrics.density).roundToInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        AlertDialog.Builder(this)
            .setTitle("Rename ${LudoSetup.PLAYER_NAMES[player]} tokens")
            .setMessage("Use up to 5 letters.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val renamed = LudoEconomy.rename(economy, input.text.toString())
                setPlayerEconomy(player, renamed)
                showFloatingNotification(
                    "${LudoSetup.PLAYER_NAMES[player]} tokens are now ${renamed.tokenName}",
                    player,
                )
            }
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
                    if (it.message.contains("BLOCKED")) {
                        showHudMessage(it.message)
                    }
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
                SoundPlayer.playMovement("ludo_win")
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
                LudoEconomy.PURCHASED_ABILITY_METADATA to (nextPlayer == player &&
                    state.metadata[LudoEconomy.PURCHASED_ABILITY_METADATA] == true),
                LudoEconomy.USED_ABILITY_METADATA to (nextPlayer == player &&
                    state.metadata[LudoEconomy.USED_ABILITY_METADATA] == true),
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
        statusView.profilesEnabled = true
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
        hideBoardWhileDialogIsOpen()
        resultDialogVisible = true
        val winner = state.metadata["ludo_winner"] as? Int ?: 0
        if (vsAI && !ludoResultRecorded) {
            ludoResultRecorded = true
            SettingsManager.recordLudoResult(this, winner == humanPlayer)
        }
        val standings = LudoEconomy.standings(state)
        val result = buildString {
            append("${LudoSetup.PLAYER_NAMES[winner]} wins\n\n")
            standings.forEachIndexed { index, standing ->
                val economy = LudoEconomy.player(state, standing.player)
                val ordinal = when (standing.place) {
                    1 -> "1st"
                    2 -> "2nd"
                    3 -> "3rd"
                    else -> "${standing.place}th"
                }
                append("$ordinal  ${LudoSetup.PLAYER_NAMES[standing.player]}")
                if (standing.player == humanPlayer && vsAI) append(" (You)")
                append("\n")
                append("  ${standing.completedTokens}/4 home · ")
                append("Placement reward +${standing.placementReward}\n")
                if (economyEnabled) {
                    append("  Earned +${standing.totalEarned} total · Balance ${standing.balance}")
                } else {
                    append("  Classic match · coins disabled")
                }
                if (index < standings.lastIndex) append("\n\n")
            }
        }
        StyledDialogs.showChoices(this, "Ludo Standings", result,
            listOf(
                StyledDialogs.choice("New Match", "Roll into another game", "↻", "#E3B86A"),
                StyledDialogs.choice("Main Menu", "Choose another game", "⌂", "#E58A7A"),
                StyledDialogs.choice("Watch Replay", "Review the moves", "▶", "#A9B6E8"),
            ), 620f, "L U D O", onCancel = { showBoardAfterDialog() }) { which, dialog ->
                dialog.dismiss()
                resultDialogVisible = false
                when (which) {
                    0 -> startGame()
                    1 -> finish()
                    2 -> launchReplay(result)
                }
            }.setOnDismissListener { resultDialogVisible = false }
    }

    private fun hideBoardWhileDialogIsOpen() {
        boardView.visibility = View.INVISIBLE
    }

    private fun showBoardAfterDialog() {
        boardView.visibility = View.VISIBLE
    }

    private fun launchReplay(result: String) {
        showBoardAfterDialog()
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE, "LUDO")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, ReplayActivity.buildMovesJson(moves))
            putExtra(ReplayActivity.EXTRA_RESULT, result)
        })
    }
}