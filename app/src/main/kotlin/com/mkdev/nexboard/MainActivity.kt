package com.mkdev.nexboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.nexboard.ui.MenuView

class MainActivity : AppCompatActivity() {

    private var activeSettingsDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val menu = MenuView(this)
        menu.onGameSelected = { type ->
            when (type) {
                MenuView.GameType.MORABARABA ->
                    startActivity(Intent(this, MorabarabaActivity::class.java))
                MenuView.GameType.TICTACTOE ->
                    startActivity(Intent(this, TicTacToeActivity::class.java))
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
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
            val adView = com.google.android.gms.ads.AdView(this).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
            root.addView(adView, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } catch (_: Exception) {}
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
        val diffs  = arrayOf("Easy", "Medium", "Hard")
        val themes = SettingsManager.THEMES.map { it.name }.toTypedArray()

        // Wrapper: sticky title on top, scrollable rows below
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        wrapper.addView(TextView(ctx).apply {
            text = "⚙  Settings"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding((8*dp).toInt(), (14*dp).toInt(), (8*dp).toInt(), (4*dp).toInt())
        })
        wrapper.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
        })
        val scroll = ScrollView(ctx)
        val root   = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8*dp).toInt(), 0, (8*dp).toInt(), (16*dp).toInt())
        }
        scroll.addView(root)
        wrapper.addView(scroll)

        fun sectionHeader(text: String) = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#7FC8F8"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding((12*dp).toInt(), (12*dp).toInt(), (12*dp).toInt(), (6*dp).toInt())
            letterSpacing = 0.12f
        }

        fun divider() = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
                .also { it.setMargins((16*dp).toInt(), 0, (16*dp).toInt(), 0) }
        }

        fun settingRow(icon: String, label: String, value: String): Pair<LinearLayout, TextView> {
            val valueView = TextView(ctx).apply {
                text = value; setTextColor(Color.parseColor("#7FC8F8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#222222")); cornerRadius = 12*dp
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.setMargins((4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt()) }
                setPadding((12*dp).toInt(), (14*dp).toInt(), (16*dp).toInt(), (14*dp).toInt())
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
                val iconView  = TextView(ctx).apply { this.text = icon; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setPadding(0,0,(12*dp).toInt(),0) }
                val labelView = TextView(ctx).apply { this.text = label; setTextColor(Color.parseColor("#EEEEEE")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
                leftGroup.addView(iconView); leftGroup.addView(labelView)
                addView(leftGroup); addView(valueView)
            }
            return row to valueView
        }

        fun themeRow(game: String, getT: () -> Int, setT: (Int) -> Unit): Pair<LinearLayout, TextView> {
            var t = getT()
            val (row, valV) = settingRow("🖌", "Board Theme", themes[t])
            row.setOnClickListener {
                AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                    .setTitle("$game · Theme")
                    .setSingleChoiceItems(themes, t) { d, i ->
                        setT(i); t = i; valV.text = themes[i]; d.dismiss()
                    }.show()
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

        // ── Chess ──
        root.addView(sectionHeader("♟  CHESS"))
        var chessDiff  = SettingsManager.getChessDifficulty(ctx)
        var chessHints = SettingsManager.getChessHints(ctx)
        val (chessDiffRow, chessDiffVal) = settingRow("🎯", "AI Difficulty", diffs[chessDiff])
        chessDiffRow.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setTitle("Chess · AI Difficulty")
                .setSingleChoiceItems(diffs, chessDiff) { d, i ->
                    SettingsManager.setChessDifficulty(ctx, i); chessDiff = i; chessDiffVal.text = diffs[i]; d.dismiss()
                }.show()
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

        // ── Checkers ──
        root.addView(sectionHeader("⬤  CHECKERS"))
        var checkersDiff  = SettingsManager.getCheckersDifficulty(ctx)
        var checkersHints = SettingsManager.getCheckersHints(ctx)
        val (checkersDiffRow, checkersDiffVal) = settingRow("🎯", "AI Difficulty", diffs[checkersDiff])
        checkersDiffRow.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setTitle("Checkers · AI Difficulty")
                .setSingleChoiceItems(diffs, checkersDiff) { d, i ->
                    SettingsManager.setCheckersDifficulty(ctx, i); checkersDiff = i; checkersDiffVal.text = diffs[i]; d.dismiss()
                }.show()
        }
        root.addView(checkersDiffRow); root.addView(divider())
        val (checkersHintsRow, checkersHintsVal) = settingRow("💡", "Move Hints", if (checkersHints) "On" else "Off")
        checkersHintsRow.setOnClickListener {
            checkersHints = !checkersHints; SettingsManager.setCheckersHints(ctx, checkersHints)
            checkersHintsVal.text = if (checkersHints) "On" else "Off"
        }
        root.addView(checkersHintsRow); root.addView(divider())
        root.addView(themeRow("Checkers",
            { SettingsManager.getCheckersTheme(ctx) },
            { v -> SettingsManager.setCheckersTheme(ctx, v) }).first)

        // ── Othello — difficulty fixed at hard; only theme is configurable ──
        root.addView(sectionHeader("◉  OTHELLO"))
        root.addView(themeRow("Othello",
            { SettingsManager.getOthelloTheme(ctx) },
            { v -> SettingsManager.setOthelloTheme(ctx, v) }).first)

        // ── Morabaraba ──
        root.addView(sectionHeader("⬡  MORABARABA"))
        var morabaraDiff = SettingsManager.getMorabarabaDifficulty(ctx)
        val (morabaraDiffRow, morabaraDiffVal) = settingRow("🎯", "AI Difficulty", diffs[morabaraDiff])
        morabaraDiffRow.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setTitle("Morabaraba · AI Difficulty")
                .setSingleChoiceItems(diffs, morabaraDiff) { d, i ->
                    SettingsManager.setMorabarabaDifficulty(ctx, i); morabaraDiff = i; morabaraDiffVal.text = diffs[i]; d.dismiss()
                }.show()
        }
        root.addView(morabaraDiffRow); root.addView(divider())
        root.addView(themeRow("Morabaraba",
            { SettingsManager.getMorabarabaTheme(ctx) },
            { v -> SettingsManager.setMorabarabaTheme(ctx, v) }).first)

        // ── Tic-Tac-Toe ──
        root.addView(sectionHeader("✕  TIC-TAC-TOE"))
        var tttDiff = SettingsManager.getTttDifficulty(ctx)
        val (tttDiffRow, tttDiffVal) = settingRow("🎯", "AI Difficulty", diffs[tttDiff])
        tttDiffRow.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
                .setTitle("Tic-Tac-Toe · AI Difficulty")
                .setSingleChoiceItems(diffs, tttDiff) { d, i ->
                    SettingsManager.setTttDifficulty(ctx, i); tttDiff = i; tttDiffVal.text = diffs[i]; d.dismiss()
                }.show()
        }
        root.addView(tttDiffRow); root.addView(divider())
        root.addView(themeRow("Tic-Tac-Toe",
            { SettingsManager.getTttTheme(ctx) },
            { v -> SettingsManager.setTttTheme(ctx, v) }).first)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setView(wrapper).setPositiveButton("Done", null).create()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
        dialog.setOnDismissListener { if (activeSettingsDialog === dialog) activeSettingsDialog = null }
        dialog.show()
        activeSettingsDialog = dialog
    }

    // ─── Stats Dialog — swipeable per-game pages ──────────────────────────────

    private fun showStatsDialog() {
        val dp  = resources.displayMetrics.density
        val ctx = this

        data class Page(val title: String, val icon: String, val gameTag: String)
        val pages = listOf(
            Page("Overall",    "★",  "overall"),
            Page("Chess",      "♟",  "chess"),
            Page("Checkers",   "⬤",  "checkers"),
            Page("Othello",    "◉",  "othello"),
            Page("Morabaraba", "⬡",  "morabaraba"),
            Page("Tic-Tac-Toe","✕",  "ttt")
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
            .setTitle("Your Stats (vs AI)")
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
