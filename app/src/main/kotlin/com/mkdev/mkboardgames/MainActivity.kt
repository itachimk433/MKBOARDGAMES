package com.mkdev.mkboardgames

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.ui.MenuView

class MainActivity : AppCompatActivity() {

    private var activeSettingsDialog: AlertDialog? = null
    private var menuView: MenuView? = null

    private companion object {
        const val SUPPORT_EMAIL = "mkdev4360@gmail.com"
        const val PRIVACY_POLICY_URL =
            "https://mkboard-games.pages.dev/privacy-policy"
        const val TERMS_OF_SERVICE_URL =
            "https://mkboard-games.pages.dev/terms-of-service"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val menu = MenuView(this)
        menuView = menu
        menu.onGameSelected = { type ->
            when (type) {
                MenuView.GameType.MORABARABA ->
                    startActivity(Intent(this, MorabarabaActivity::class.java))
                MenuView.GameType.TICTACTOE ->
                    startActivity(Intent(this, TicTacToeActivity::class.java))
                MenuView.GameType.CONNECT_FOUR ->
                    startActivity(Intent(this, ConnectFourActivity::class.java))
                MenuView.GameType.LUDO ->
                    startActivity(Intent(this, LudoActivity::class.java))
                MenuView.GameType.MANCALA ->
                    startActivity(Intent(this, MancalaActivity::class.java))
                MenuView.GameType.YOTE ->
                    startActivity(Intent(this, YoteActivity::class.java))
                else ->
                    startActivity(Intent(this, GameActivity::class.java).apply {
                        putExtra(GameActivity.EXTRA_GAME, type.name)
                    })
            }
        }
        menu.onSettingsClicked = { showSettings() }
        menu.onLogoTapped      = { showStatsDialog() }
        SoundPlayer.init(this)

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        root.addView(menu, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        setContentView(root)



        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }
    }

    override fun onResume() { super.onResume(); makeFullscreen() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    private fun showSettings() {
        activeSettingsDialog?.dismiss()
        activeSettingsDialog = null

        val ctx    = this
        val dp     = resources.displayMetrics.density
        val diffs  = SettingsManager.chessDifficultyLabels()
        val themes = SettingsManager.THEMES.map { it.name }.toTypedArray()

        // A board-room surface: deep teal like Chess/Draughts, with Mancala's
        // warm wood and gold controls layered over it.
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#102C32"),
                    Color.parseColor("#0B1D25"),
                    Color.parseColor("#061321"),
                ),
            )
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#4A1714"),
                    Color.parseColor("#6A2D2B"),
                    Color.parseColor("#321718"),
                ),
            ).apply {
                cornerRadius = 16f * dp
                setStroke((1.2f * dp).toInt(), Color.parseColor("#C8894C"))
            }
            elevation = 6f * dp
            setPadding((18 * dp).toInt(), (15 * dp).toInt(), (18 * dp).toInt(), (13 * dp).toInt())
        }
        header.addView(TextView(ctx).apply {
            text = "⚙  SETTINGS"
            setTextColor(Color.parseColor("#F7D99B"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            letterSpacing = 0.08f
            setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(180, 20, 4, 3))
        })
        header.addView(TextView(ctx).apply {
            text = "Tune your board room"
            setTextColor(Color.parseColor("#F5DCC0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        })
        wrapper.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also {
            it.setMargins((10 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        })
        wrapper.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#C8894C"),
                    Color.TRANSPARENT,
                ),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt(),
            ).also {
                it.setMargins((18 * dp).toInt(), 0, (18 * dp).toInt(), (4 * dp).toInt())
            }
        })
        val scroll = ScrollView(ctx)
        val root   = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), (20 * dp).toInt())
        }
        scroll.addView(root)
        wrapper.addView(scroll)

        fun sectionHeader(text: String) = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#E3B86A"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setShadowLayer(1.5f * dp, 0f, 1f * dp, Color.argb(180, 25, 9, 5))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#21454A"),
                    Color.parseColor("#16353B"),
                ),
            ).apply {
                cornerRadius = 10f * dp
                setStroke((1f * dp).toInt(), Color.parseColor("#2C5960"))
            }
            elevation = 2f * dp
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (9 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.setMargins((2 * dp).toInt(), (14 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt())
            }
        }

        fun divider() = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#85502D"),
                    Color.TRANSPARENT,
                ),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt(),
            ).also {
                it.setMargins((12 * dp).toInt(), (2 * dp).toInt(), (12 * dp).toInt(), (2 * dp).toInt())
            }
        }

        fun settingRow(
            icon: String,
            label: String,
            value: String,
            iconRes: Int? = null
        ): Pair<LinearLayout, TextView> {
            val valueView = TextView(ctx).apply {
                text = value
                setTextColor(Color.parseColor("#4A1714"))
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                minWidth = (54 * dp).toInt()
                setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#F7D99B"),
                        Color.parseColor("#C8894C"),
                    ),
                ).apply {
                    cornerRadius = 8f * dp
                    setStroke((1f * dp).toInt(), Color.parseColor("#85502D"))
                }
                elevation = 2f * dp
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#21454A"),
                        Color.parseColor("#16353B"),
                        Color.parseColor("#102C32"),
                    ),
                ).apply {
                    cornerRadius = 12f * dp
                    setStroke((1f * dp).toInt(), Color.parseColor("#2C5960"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also {
                    it.setMargins((2 * dp).toInt(), (3 * dp).toInt(), (2 * dp).toInt(), (3 * dp).toInt())
                }
                minimumHeight = (56 * dp).toInt()
                setPadding((10 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
                elevation = 3f * dp
                isClickable = true; isFocusable = true
                setOnTouchListener { v, e ->
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN ->
                            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60L).start()
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                    }
                    false
                }
                val leftGroup = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val iconView: View = if (iconRes != null) {
                    ImageView(ctx).apply {
                        setImageResource(iconRes)
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#F7D99B")
                        )
                        contentDescription = "$label icon"
                        layoutParams = LinearLayout.LayoutParams(
                            (36 * dp).toInt(),
                            (36 * dp).toInt(),
                        ).also { it.marginEnd = (11 * dp).toInt() }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(Color.parseColor("#321718"))
                            setStroke((1f * dp).toInt(), Color.parseColor("#C8894C"))
                        }
                        setPadding((7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt())
                    }
                } else {
                    TextView(ctx).apply {
                        this.text = icon
                        setTextColor(Color.parseColor("#F7D99B"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            (36 * dp).toInt(),
                            (36 * dp).toInt(),
                        ).also { it.marginEnd = (11 * dp).toInt() }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(Color.parseColor("#321718"))
                            setStroke((1f * dp).toInt(), Color.parseColor("#C8894C"))
                        }
                    }
                }
                val labelView = TextView(ctx).apply {
                    this.text = label
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, Color.argb(160, 0, 0, 0))
                }
                leftGroup.addView(iconView); leftGroup.addView(labelView)
                addView(leftGroup); addView(valueView)
            }
            return row to valueView
        }

        fun showSettingsChoiceDialog(
            title: String,
            options: Array<String>,
            selectedIndex: Int,
            onSelected: (Int) -> Unit,
        ) {
            fun panelBackground(selected: Boolean) =
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    if (selected) {
                        intArrayOf(
                            Color.parseColor("#6A2D2B"),
                            Color.parseColor("#4A1714"),
                        )
                    } else {
                        intArrayOf(
                            Color.parseColor("#21454A"),
                            Color.parseColor("#16353B"),
                        )
                    },
                ).apply {
                    cornerRadius = 10f * dp
                    setStroke(
                        (1f * dp).toInt(),
                        Color.parseColor(if (selected) "#C8894C" else "#2C5960"),
                    )
                }

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#102C32"),
                        Color.parseColor("#061321"),
                    ),
                )
                setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            }
            content.addView(TextView(ctx).apply {
                text = title
                setTextColor(Color.parseColor("#F7D99B"))
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                gravity = Gravity.CENTER
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
            })

            lateinit var choiceDialog: AlertDialog
            options.forEachIndexed { index, option ->
                val optionRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = panelBackground(index == selectedIndex)
                    setPadding((12 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also {
                        it.setMargins(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
                    }
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener { v, e ->
                        when (e.action) {
                            android.view.MotionEvent.ACTION_DOWN ->
                                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60L).start()
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL ->
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        }
                        false
                    }
                    setOnClickListener {
                        onSelected(index)
                        choiceDialog.dismiss()
                    }
                }
                optionRow.addView(TextView(ctx).apply {
                    text = if (index == selectedIndex) "◉" else "○"
                    setTextColor(
                        Color.parseColor(if (index == selectedIndex) "#F7D99B" else "#9FB5B8"),
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        (30 * dp).toInt(),
                        (30 * dp).toInt(),
                    )
                })
                optionRow.addView(TextView(ctx).apply {
                    text = option
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                })
                content.addView(optionRow)
            }

            choiceDialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setView(content)
                .create()
            choiceDialog.window?.setBackgroundDrawable(
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#102C32"),
                        Color.parseColor("#061321"),
                    ),
                ).apply {
                    cornerRadius = 18f * dp
                    setStroke((1.2f * dp).toInt(), Color.parseColor("#85502D"))
                },
            )
            choiceDialog.show()
        }

        fun themeRow(game: String, getT: () -> Int, setT: (Int) -> Unit): Pair<LinearLayout, TextView> {
            var t = getT()
            val (row, valV) = settingRow("🖌", "Board Theme", themes[t])
            row.setOnClickListener {
                showSettingsChoiceDialog("$game · Theme", themes, t) { i ->
                    setT(i)
                    t = i
                    valV.text = themes[i]
                }
            }
            return row to valV
        }

        // ── General ──
        root.addView(sectionHeader("⚙  GENERAL"))
        var movementSounds = SettingsManager.isMovementSoundsEnabled(ctx)
        val (movSoundRow, movSoundVal) = settingRow("🔊", "Movement Sounds", if (movementSounds) "On" else "Off")
        movSoundRow.setOnClickListener {
            movementSounds = !movementSounds
            SettingsManager.setMovementSoundsEnabled(ctx, movementSounds)
            SoundPlayer.movementSoundsEnabled = movementSounds
            movSoundVal.text = if (movementSounds) "On" else "Off"
        }
        root.addView(movSoundRow)

        var brownHomeStyle = SettingsManager.isBrownHomeStyleEnabled(ctx)
        val (homeStyleRow, homeStyleVal) =
            settingRow("🖌️", "Home Style", if (brownHomeStyle) "Brown" else "Classic")
        homeStyleRow.setOnClickListener {
            brownHomeStyle = !brownHomeStyle
            SettingsManager.setBrownHomeStyleEnabled(ctx, brownHomeStyle)
            menuView?.isHomeBackgroundEnabled = brownHomeStyle
            menuView?.isWoodGameCardStyleEnabled = brownHomeStyle
            homeStyleVal.text = if (brownHomeStyle) "Brown" else "Classic"
        }
        root.addView(homeStyleRow)

        var motionDice = SettingsManager.isMotionDiceEnabled(ctx)
        val (motionDiceRow, motionDiceVal) =
            settingRow("◈", "Motion Dice", if (motionDice) "On" else "Off")
        motionDiceRow.setOnClickListener {
            motionDice = !motionDice
            SettingsManager.setMotionDiceEnabled(ctx, motionDice)
            motionDiceVal.text = if (motionDice) "On" else "Off"
        }
        root.addView(motionDiceRow)

        // ── Chess ──
        root.addView(sectionHeader("♟  CHESS"))
        var chessDiff  = SettingsManager.getChessDifficulty(ctx)
        var chessHints = SettingsManager.getChessHints(ctx)
        val (chessDiffRow, chessDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[chessDiff])
        chessDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Chess · CPU Difficulty", diffs, chessDiff) { i ->
                SettingsManager.setChessDifficulty(ctx, i)
                chessDiff = i
                chessDiffVal.text = diffs[i]
            }
        }
        root.addView(chessDiffRow); root.addView(divider())
        val (chessHintsRow, chessHintsVal) = settingRow("💡", "Move Hints", if (chessHints) "On" else "Off")
        chessHintsRow.setOnClickListener {
            chessHints = !chessHints; SettingsManager.setChessHints(ctx, chessHints)
            chessHintsVal.text = if (chessHints) "On" else "Off"
        }
        root.addView(chessHintsRow); root.addView(divider())
        root.addView(themeRow("Chess",
            { SettingsManager.getChessTheme(ctx) },
            { v -> SettingsManager.setChessTheme(ctx, v) }).first)

        // ── Draughts ──
        root.addView(sectionHeader("⬤  DRAUGHTS"))
        var checkersDiff  = SettingsManager.getCheckersDifficulty(ctx)
        var checkersHints = SettingsManager.getCheckersHints(ctx)
        val (checkersDiffRow, checkersDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[checkersDiff])
        checkersDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Draughts · CPU Difficulty", diffs, checkersDiff) { i ->
                SettingsManager.setCheckersDifficulty(ctx, i)
                checkersDiff = i
                checkersDiffVal.text = diffs[i]
            }
        }
        root.addView(checkersDiffRow); root.addView(divider())
        val (checkersHintsRow, checkersHintsVal) = settingRow("💡", "Move Hints", if (checkersHints) "On" else "Off")
        checkersHintsRow.setOnClickListener {
            checkersHints = !checkersHints; SettingsManager.setCheckersHints(ctx, checkersHints)
            checkersHintsVal.text = if (checkersHints) "On" else "Off"
        }
        root.addView(checkersHintsRow); root.addView(divider())
        root.addView(themeRow("Draughts",
            { SettingsManager.getCheckersTheme(ctx) },
            { v -> SettingsManager.setCheckersTheme(ctx, v) }).first)

        // ── International Draughts ──
        root.addView(sectionHeader("◉  INTERNATIONAL DRAUGHTS"))
        var internationalDraughtsDiff = SettingsManager.getInternationalDraughtsDifficulty(ctx)
        val (internationalDraughtsDiffRow, internationalDraughtsDiffVal) =
            settingRow("🎯", "CPU Difficulty", diffs[internationalDraughtsDiff])
        internationalDraughtsDiffRow.setOnClickListener {
            showSettingsChoiceDialog(
                "International Draughts · CPU Difficulty",
                diffs,
                internationalDraughtsDiff,
            ) { i ->
                SettingsManager.setInternationalDraughtsDifficulty(ctx, i)
                internationalDraughtsDiff = i
                internationalDraughtsDiffVal.text = diffs[i]
            }
        }
        root.addView(internationalDraughtsDiffRow); root.addView(divider())
        root.addView(themeRow("International Draughts",
            { SettingsManager.getInternationalDraughtsTheme(ctx) },
            { v -> SettingsManager.setInternationalDraughtsTheme(ctx, v) }).first)

        // ── Othello — difficulty fixed at hard; only theme is configurable ──
        root.addView(sectionHeader("◉  OTHELLO"))
        root.addView(themeRow("Othello",
            { SettingsManager.getOthelloTheme(ctx) },
            { v -> SettingsManager.setOthelloTheme(ctx, v) }).first)

        // ── Morabaraba ──
        root.addView(sectionHeader("⬡  MORABARABA"))
        var morabaraDiff = SettingsManager.getMorabarabaDifficulty(ctx)
        val (morabaraDiffRow, morabaraDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[morabaraDiff])
        morabaraDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Morabaraba · CPU Difficulty", diffs, morabaraDiff) { i ->
                SettingsManager.setMorabarabaDifficulty(ctx, i)
                morabaraDiff = i
                morabaraDiffVal.text = diffs[i]
            }
        }
        root.addView(morabaraDiffRow); root.addView(divider())
        root.addView(themeRow("Morabaraba",
            { SettingsManager.getMorabarabaTheme(ctx) },
            { v -> SettingsManager.setMorabarabaTheme(ctx, v) }).first)

        // ── Tic-Tac-Toe ──
        root.addView(sectionHeader("✕  TIC-TAC-TOE"))
        var tttDiff = SettingsManager.getTttDifficulty(ctx)
        val (tttDiffRow, tttDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[tttDiff])
        tttDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Tic-Tac-Toe · CPU Difficulty", diffs, tttDiff) { i ->
                SettingsManager.setTttDifficulty(ctx, i)
                tttDiff = i
                tttDiffVal.text = diffs[i]
            }
        }
        root.addView(tttDiffRow); root.addView(divider())
        root.addView(themeRow("Tic-Tac-Toe",
            { SettingsManager.getTttTheme(ctx) },
            { v -> SettingsManager.setTttTheme(ctx, v) }).first)

        // ── Connect Four ──
        root.addView(sectionHeader("●  CONNECT FOUR"))
        var connectFourDiff = SettingsManager.getConnectFourDifficulty(ctx)
        val (connectFourDiffRow, connectFourDiffVal) =
            settingRow("🎯", "CPU Difficulty", diffs[connectFourDiff])
        connectFourDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Connect Four · CPU Difficulty", diffs, connectFourDiff) { i ->
                SettingsManager.setConnectFourDifficulty(ctx, i)
                connectFourDiff = i
                connectFourDiffVal.text = diffs[i]
            }
        }
        root.addView(connectFourDiffRow); root.addView(divider())
        root.addView(themeRow("Connect Four",
            { SettingsManager.getConnectFourTheme(ctx) },
            { v -> SettingsManager.setConnectFourTheme(ctx, v) }).first)

        // ── Fox and Geese ──
        root.addView(sectionHeader("🦊  FOX & GEESE"))
        var foxAndGeeseDiff = SettingsManager.getFoxAndGeeseDifficulty(ctx)
        val (foxAndGeeseDiffRow, foxAndGeeseDiffVal) =
            settingRow("🎯", "CPU Difficulty", diffs[foxAndGeeseDiff])
        foxAndGeeseDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Fox & Geese · CPU Difficulty", diffs, foxAndGeeseDiff) { i ->
                SettingsManager.setFoxAndGeeseDifficulty(ctx, i)
                foxAndGeeseDiff = i
                foxAndGeeseDiffVal.text = diffs[i]
            }
        }
        root.addView(foxAndGeeseDiffRow); root.addView(divider())
        root.addView(themeRow("Fox & Geese",
            { SettingsManager.getFoxAndGeeseTheme(ctx) },
            { v -> SettingsManager.setFoxAndGeeseTheme(ctx, v) }).first)

        // ── Ludo ──
        root.addView(sectionHeader("●  LUDO"))
        var ludoDiff = SettingsManager.getLudoDifficulty(ctx)
        val (ludoDiffRow, ludoDiffVal) =
            settingRow("🎯", "CPU Difficulty", diffs[ludoDiff])
        ludoDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Ludo · CPU Difficulty", diffs, ludoDiff) { i ->
                SettingsManager.setLudoDifficulty(ctx, i)
                ludoDiff = i
                ludoDiffVal.text = diffs[i]
            }
        }
        root.addView(ludoDiffRow)
        root.addView(divider())

        // ── Go ──
        root.addView(sectionHeader("⚫  GO"))
        var goDiff = SettingsManager.getGoDifficulty(ctx)
        val (goDiffRow, goDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[goDiff])
        goDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Go · CPU Difficulty", diffs, goDiff) { i ->
                SettingsManager.setGoDifficulty(ctx, i)
                goDiff = i
                goDiffVal.text = diffs[i]
            }
        }
        root.addView(goDiffRow)
        root.addView(divider())

        // ── Shogi ──
        root.addView(sectionHeader("将  SHOGI"))
        var shogiDiff = SettingsManager.getShogiDifficulty(ctx)
        val (shogiDiffRow, shogiDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[shogiDiff])
        shogiDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Shogi · CPU Difficulty", diffs, shogiDiff) { i ->
                SettingsManager.setShogiDifficulty(ctx, i)
                shogiDiff = i
                shogiDiffVal.text = diffs[i]
            }
        }
        root.addView(shogiDiffRow)
        root.addView(divider())

        // ── Yoté ──
        root.addView(sectionHeader("●  YOTÉ"))
        var yoteDiff = SettingsManager.getYoteDifficulty(ctx)
        val (yoteDiffRow, yoteDiffVal) = settingRow("🎯", "CPU Difficulty", diffs[yoteDiff])
        yoteDiffRow.setOnClickListener {
            showSettingsChoiceDialog("Yoté · CPU Difficulty", diffs.take(3).toTypedArray(), yoteDiff) { i ->
                SettingsManager.setYoteDifficulty(ctx, i)
                yoteDiff = i
                yoteDiffVal.text = diffs[i]
            }
        }
        root.addView(yoteDiffRow)
        root.addView(divider())

        // ── Legal ──
        root.addView(sectionHeader("📋  LEGAL"))
        val ppUrl  = PRIVACY_POLICY_URL
        val tosUrl = TERMS_OF_SERVICE_URL
        val (ppRow, _) = settingRow("🔒", "Privacy Policy", "›")
        ppRow.setOnClickListener {
            val ppContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
            }
            fun pTxt(t: String, bold: Boolean = false, accent: Boolean = false) = TextView(ctx).apply {
                text = t
                setTextColor(if (accent) Color.parseColor("#E3B86A") else Color.parseColor("#CCCCCC"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (4*dp).toInt(), 0, (4*dp).toInt())
            }
            ppContent.addView(pTxt("Privacy Policy — MK BOARD GAMES v1.2", bold = true, accent = true))
            ppContent.addView(pTxt("Effective date: June 2026", bold = false))
            ppContent.addView(pTxt("\nDATA COLLECTION\nMK BOARD GAMES does not collect or transmit personal data. Game statistics (wins, losses, draws) are stored only on your device."))
            ppContent.addView(pTxt("\nADVERTISING\nThis version of MK BOARD GAMES contains no advertising. Ads may be introduced in a future update via Google AdMob, in which case this policy will be updated accordingly."))
            ppContent.addView(pTxt("\nPERMISSIONS\n• Vibrate — in-game haptic feedback"))
            ppContent.addView(pTxt("\nCONTACT\n$SUPPORT_EMAIL", bold = false))
            val ppScroll = ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320*dp).toInt())
            }
            ppScroll.addView(ppContent)
            val ppWrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#061321"))
            }
            ppWrapper.addView(ppScroll)
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setView(ppWrapper)
                .setPositiveButton("Close", null)
                .setNeutralButton("Open in Browser") { _, _ ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ppUrl))) } catch (_: Exception) {}
                }
                .create()
                .apply {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
                    show()
                }
        }
        root.addView(ppRow); root.addView(divider())
        val (tosRow, _) = settingRow("📄", "Terms of Service", "›")
        tosRow.setOnClickListener {
            val tosContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
            }
            fun tTxt(t: String, bold: Boolean = false, accent: Boolean = false) = TextView(ctx).apply {
                text = t
                setTextColor(if (accent) Color.parseColor("#E3B86A") else Color.parseColor("#CCCCCC"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (4*dp).toInt(), 0, (4*dp).toInt())
            }
            tosContent.addView(tTxt("Terms of Service — MK BOARD GAMES v1.2", bold = true, accent = true))
            tosContent.addView(tTxt("Effective date: June 2026", bold = false))
            tosContent.addView(tTxt("\n1. ACCEPTANCE\nBy installing or using MK BOARD GAMES you agree to these terms. If you do not agree, uninstall the app."))
            tosContent.addView(tTxt("\n2. LICENCE\nMK BOARD GAMES is provided free of charge for personal, non-commercial use. You may not reverse-engineer, redistribute, or sell the app or any part of it."))
            tosContent.addView(tTxt("\n3. ADVERTISING\nThis version of MK BOARD GAMES is ad-free. Ads may be introduced in a future release. If advertising is added, the relevant ad networks will operate under their own terms and privacy policies and this section will be updated."))
            tosContent.addView(tTxt("\n4. DISCLAIMER\nMK BOARD GAMES is provided \"as is\" without warranties of any kind. MKDEV is not liable for any loss or damage arising from use of the app."))
            tosContent.addView(tTxt("\n5. CHANGES\nThese terms may be updated at any time. Continued use after an update constitutes acceptance of the revised terms."))
            tosContent.addView(tTxt("\nCONTACT\n$SUPPORT_EMAIL", bold = false))
            val tosScroll = ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320*dp).toInt())
            }
            tosScroll.addView(tosContent)
            val tosWrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#061321"))
            }
            tosWrapper.addView(tosScroll)
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setView(tosWrapper)
                .setPositiveButton("Close", null)
                .setNeutralButton("Open in Browser") { _, _ ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tosUrl))) } catch (_: Exception) {}
                }
                .create()
                .apply {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
                    show()
                }
        }
        root.addView(tosRow); root.addView(divider())

        // ── Contact ──
        val (contactRow, _) = settingRow("", "Contact Us", "›", R.drawable.ic_email)
        contactRow.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "MK BOARD GAMES Support")
                })
            } catch (_: Exception) {
                android.widget.Toast.makeText(ctx, SUPPORT_EMAIL, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        root.addView(contactRow)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setView(wrapper).setPositiveButton("Done", null).create()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
        dialog.setOnDismissListener { if (activeSettingsDialog === dialog) activeSettingsDialog = null }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#F7D99B"))
        activeSettingsDialog = dialog
    }

    // ─── First-run consent dialog (Huawei AppGallery requirement) ────────────


    // ─── Stats Dialog — swipeable per-game pages ──────────────────────────────

    private fun showStatsDialog() {
        val dp  = resources.displayMetrics.density
        val ctx = this

        data class Page(val title: String, val icon: String, val gameTag: String)
        val pages = listOf(
            Page("Overall",    "★",  "overall"),
            Page("Chess",      "♟",  "chess"),
            Page("Draughts",   "⬤",  "checkers"),
            Page("International Draughts", "⬤", "international_draughts"),
            Page("Othello",    "◉",  "othello"),
            Page("Morabaraba", "⬡",  "morabaraba"),
            Page("Tic-Tac-Toe","✕",  "ttt"),
            Page("Connect Four", "●", "connect_four"),
            Page("Fox & Geese", "🦊", "fox_and_geese"),
            Page("Ludo", "●", "ludo"),
            Page("Shogi", "将", "shogi"),
            Page("Go", "⚫", "go"),
        )

        var currentPage = 0

        val wrapper = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }

        val dotRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (4*dp).toInt(), 0, (2*dp).toInt())
        }
        val dots = pages.mapIndexed { i, _ ->
            android.widget.TextView(ctx).apply {
                text = "●"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 8f)
                setPadding((4*dp).toInt(), 0, (4*dp).toInt(), 0)
                setTextColor(if (i == 0) android.graphics.Color.parseColor("#7FC8F8")
                             else        android.graphics.Color.parseColor("#444444"))
                dotRow.addView(this)
            }
        }

        val navRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((8*dp).toInt(), (4*dp).toInt(), (8*dp).toInt(), (4*dp).toInt())
        }
        val btnPrev = android.widget.TextView(ctx).apply {
            text = "‹"; setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(android.graphics.Color.parseColor("#7FC8F8"))
            setPadding((12*dp).toInt(), 0, (12*dp).toInt(), 0)
            isClickable = true; isFocusable = true
        }
        val pageLabel = android.widget.TextView(ctx).apply {
            text = "${pages[0].icon}  ${pages[0].title}"
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnNext = android.widget.TextView(ctx).apply {
            text = "›"; setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(android.graphics.Color.parseColor("#7FC8F8"))
            setPadding((12*dp).toInt(), 0, (12*dp).toInt(), 0)
            isClickable = true; isFocusable = true
        }
        navRow.addView(btnPrev); navRow.addView(pageLabel); navRow.addView(btnNext)

        val statCard = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
        }

        fun buildStatCard(gameTag: String) {
            statCard.removeAllViews()
            val s = if (gameTag == "overall") SettingsManager.getStats(ctx)
                    else SettingsManager.getGameStats(ctx, gameTag)

            fun statLine(label: String, value: Int, color: String) {
                val row = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(0, (5*dp).toInt(), 0, (5*dp).toInt())
                }
                val lbl = android.widget.TextView(ctx).apply {
                    text = label
                    setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val val_ = android.widget.TextView(ctx).apply {
                    text = value.toString()
                    setTextColor(android.graphics.Color.parseColor(color))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_END
                }
                row.addView(lbl); row.addView(val_); statCard.addView(row)
                statCard.addView(android.view.View(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#222222"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
                })
            }

            statLine("Wins",     s.wins,     "#4CAF50")
            statLine("Losses",   s.losses,   "#EF5350")
            statLine("Draws",    s.draws,    "#7FC8F8")
            statLine("Forfeits", s.forfeits, "#FFA726")

            val total = s.wins + s.losses + s.draws
            if (total > 0) {
                val winPct = (s.wins * 100f / total).toInt()
                statCard.addView(android.widget.TextView(ctx).apply {
                    text = "Win rate: $winPct%"
                    setTextColor(android.graphics.Color.parseColor("#EEEEEE"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, (6*dp).toInt(), 0, 0)
                })
            } else {
                statCard.addView(android.widget.TextView(ctx).apply {
                    text = "No games played yet"
                    setTextColor(android.graphics.Color.parseColor("#555555"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, (6*dp).toInt(), 0, 0)
                })
            }
        }

        fun navigateTo(idx: Int) {
            currentPage = idx.coerceIn(0, pages.lastIndex)
            val page = pages[currentPage]
            pageLabel.text = "${page.icon}  ${page.title}"
            dots.forEachIndexed { i, d ->
                d.setTextColor(if (i == currentPage) android.graphics.Color.parseColor("#7FC8F8")
                               else                  android.graphics.Color.parseColor("#444444"))
            }
            buildStatCard(page.gameTag)
        }

        btnPrev.setOnClickListener { navigateTo(currentPage - 1) }
        btnNext.setOnClickListener { navigateTo(currentPage + 1) }

        // Pop animation on nav arrows
        fun addNavBounce(v: android.widget.TextView) = v.setOnTouchListener { view, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.75f).scaleY(0.75f).setDuration(70L).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
        addNavBounce(btnPrev); addNavBounce(btnNext)

        statCard.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var startX = 0f
            override fun onTouch(v: android.view.View, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { startX = e.x; return true }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = e.x - startX
                        if (kotlin.math.abs(dx) > 40 * dp) {
                            if (dx < 0) navigateTo(currentPage + 1) else navigateTo(currentPage - 1)
                        }
                        return true
                    }
                }
                return false
            }
        })

        buildStatCard("overall")

        wrapper.addView(dotRow)
        wrapper.addView(navRow)
        wrapper.addView(android.view.View(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
        })
        wrapper.addView(statCard)

        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setTitle("Your Stats (vs CPU)")
            .setIcon(R.drawable.ic_app_logo)
            .setView(wrapper)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset") { _, _ ->
                AlertDialog.Builder(ctx).setTitle("Reset Stats?")
                    .setMessage("This clears all wins, losses, draws and forfeits.")
                    .setPositiveButton("Reset") { _, _ -> SettingsManager.resetStats(ctx) }
                    .setNegativeButton("Cancel", null).show()
            }
            .show()
            .window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1A1A1A")))
    }

    // ─── Fullscreen ───────────────────────────────────────────────────────────

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}
