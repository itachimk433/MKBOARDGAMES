package com.mkdev.mkboardgames

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.ui.MenuView
import com.mkdev.mkboardgames.ui.ModeSelectionView

class MainActivity : AppCompatActivity() {

    private var activeSettingsDialog: AlertDialog? = null
    private var menuView: MenuView? = null
    private lateinit var screenRoot: FrameLayout
    private var showingGameMenu = false
    private var selectedGameMode = GameMode.NORMAL

    private companion object {
        const val SUPPORT_EMAIL = "mkdev4360@gmail.com"
        const val PRIVACY_POLICY_URL =
            "https://mkboardgames-terms.pages.dev/privacy-policy"
        const val TERMS_OF_SERVICE_URL =
            "https://mkboardgames-terms.pages.dev/terms-of-service"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)

        screenRoot = FrameLayout(this)
        setContentView(screenRoot)
        showModeSelection()
        window.decorView.post { showDailyTestClaim() }

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }
    }

    private fun showModeSelection() {
        activeSettingsDialog?.dismiss()
        activeSettingsDialog = null
        showingGameMenu = false
        menuView = null
        MusicPlayer.enterModeSelection(this)

        val modeSelection = ModeSelectionView(this)
        modeSelection.onModeSelected = { mode ->
            selectedGameMode = mode
            SettingsManager.setCurrentMode(this@MainActivity, mode)
            MusicPlayer.playForMode(this@MainActivity, mode)
            showGameMenu()
        }
        modeSelection.onAboutClicked = { showAbout() }
        modeSelection.onStatsClicked = { showStatsDialog() }
        modeSelection.onRemoveAdsClicked = {
            RemoveAdsManager.purchase(
                activity = this@MainActivity,
                onLoadingChanged = { loading ->
                    runOnUiThread { modeSelection.setRemoveAdsLoading(loading) }
                },
                onResult = { error ->
                    runOnUiThread {
                        modeSelection.setRemoveAdsLoading(false)
                        if (error == null) {
                            modeSelection.setAdsRemoved()
                            Toast.makeText(
                                this@MainActivity,
                                "Ads removed. Thank you!",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else if (error != "Purchase canceled.") {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Remove Ads")
                                .setMessage(error)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                },
            )
        }
        RemoveAdsManager.prepare(this) { price ->
            runOnUiThread { modeSelection.setRemoveAdsPrice(price) }
        }
        screenRoot.removeAllViews()
        screenRoot.addView(modeSelection, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showDailyTestClaim() {
        if (isFinishing || activeSettingsDialog?.isShowing == true) return

        val ctx = this
        val dp = resources.displayMetrics.density
        val palette = intArrayOf(
            Color.parseColor("#E86A5B"),
            Color.parseColor("#EF8B5B"),
            Color.parseColor("#F2B35D"),
            Color.parseColor("#D7C45C"),
            Color.parseColor("#9CCB6B"),
            Color.parseColor("#5FC18A"),
            Color.parseColor("#58C0B2"),
            Color.parseColor("#5CA9C8"),
            Color.parseColor("#6C8FD1"),
            Color.parseColor("#897AC7"),
            Color.parseColor("#A879C1"),
            Color.parseColor("#C27BA7"),
            Color.parseColor("#D97886"),
            Color.parseColor("#E05E6F"),
        )
        val claimedDay = SettingsManager.dailyTestClaimedDay(ctx)
        val currentDay = SettingsManager.currentDailyTestDay(ctx)
        val hasClaimedToday = claimedDay > 0 && currentDay == claimedDay

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * dp).toInt(),
                (8 * dp).toInt(),
                (20 * dp).toInt(),
                (4 * dp).toInt(),
            )
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#173B43"),
                    Color.parseColor("#0C222C"),
                    Color.parseColor("#071723"),
                ),
            )
        }
        val title = TextView(ctx).apply {
            text = "14-DAY TEST RUN"
            setTextColor(Color.parseColor("#F7D99B"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.12f
        }
        wrapper.addView(title)

        val headline = TextView(ctx).apply {
            text = if (claimedDay >= SettingsManager.DAILY_TEST_DAYS) {
                "Test run complete"
            } else {
                "Your daily check-in is ready"
            }
            setTextColor(Color.parseColor("#FFF8E8"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, (5 * dp).toInt(), 0, 0)
        }
        wrapper.addView(headline)

        val subhead = TextView(ctx).apply {
            text = if (claimedDay >= SettingsManager.DAILY_TEST_DAYS) {
                "Thanks for helping test MK Board Games for 14 days."
            } else if (hasClaimedToday) {
                "Day $claimedDay is complete. Come back tomorrow for the next check-in."
            } else {
                "Try one game or feature, then claim today’s test pass."
            }
            setTextColor(Color.parseColor("#B8D0CF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        }
        wrapper.addView(subhead)

        val progress = TextView(ctx).apply {
            text = "$claimedDay of ${SettingsManager.DAILY_TEST_DAYS} days claimed"
            setTextColor(Color.parseColor("#F7D99B"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        wrapper.addView(progress)

        val tiles = mutableListOf<TextView>()
        val tileGrid = GridLayout(ctx).apply {
            columnCount = 7
            rowCount = 2
            useDefaultMargins = false
        }
        repeat(SettingsManager.DAILY_TEST_DAYS) { index ->
            val day = index + 1
            val isClaimed = day <= claimedDay
            val isCurrent = day == currentDay && !isClaimed
            val tile = TextView(ctx).apply {
                gravity = Gravity.CENTER
                text = if (isClaimed) "$day\nDONE" else "$day\n${if (isCurrent) "CLAIM" else "NEXT"}"
                setTextColor(if (isClaimed || isCurrent) Color.parseColor("#071723") else Color.parseColor("#8FA8A8"))
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 10f * dp
                    setColor(
                        when {
                            isClaimed -> Color.argb(170, Color.red(palette[index]), Color.green(palette[index]), Color.blue(palette[index]))
                            isCurrent -> palette[index]
                            else -> Color.parseColor("#1C3038")
                        },
                    )
                    setStroke(
                        (1 * dp).toInt(),
                        if (isCurrent) Color.parseColor("#FFF8E8") else Color.TRANSPARENT,
                    )
                }
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
            tileGrid.addView(
                tile,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = (48 * dp).toInt()
                    columnSpec = GridLayout.spec(index % 7, 1f)
                    rowSpec = GridLayout.spec(index / 7)
                    setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
                },
            )
            tiles += tile
        }
        wrapper.addView(
            tileGrid,
            LinearLayout.LayoutParams(-1, (108 * dp).toInt()),
        )

        val action = TextView(ctx).apply {
            text = when {
                claimedDay >= SettingsManager.DAILY_TEST_DAYS -> "14-DAY TEST COMPLETE"
                hasClaimedToday -> "DAY $claimedDay TEST: CLAIMED"
                else -> "CLAIM DAY $currentDay TEST"
            }
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#071723"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * dp
                setColor(if (hasClaimedToday || claimedDay >= SettingsManager.DAILY_TEST_DAYS) Color.parseColor("#78908F") else Color.parseColor("#F7D99B"))
            }
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
            isEnabled = !hasClaimedToday && claimedDay < SettingsManager.DAILY_TEST_DAYS
        }
        wrapper.addView(
            action,
            LinearLayout.LayoutParams(-1, (50 * dp).toInt()).apply {
                topMargin = (12 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            },
        )

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setView(wrapper)
            .setNegativeButton("Later", null)
            .create()
        action.setOnClickListener {
            if (!SettingsManager.claimDailyTestDay(ctx)) return@setOnClickListener

            val claimed = SettingsManager.dailyTestClaimedDay(ctx)
            action.text = "DAY $claimed TEST: CLAIMED"
            action.isEnabled = false
            action.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * dp
                setColor(Color.parseColor("#78908F"))
            }
            headline.text = if (claimed >= SettingsManager.DAILY_TEST_DAYS) {
                "Test run complete"
            } else {
                "Day $claimed is in the book"
            }
            subhead.text = if (claimed >= SettingsManager.DAILY_TEST_DAYS) {
                "Thanks for helping test MK Board Games for 14 days."
            } else {
                "Nice work. Come back tomorrow for Day ${claimed + 1}."
            }
            progress.text = "$claimed of ${SettingsManager.DAILY_TEST_DAYS} days claimed"
            tiles[claimed - 1].apply {
                text = "$claimed\nDONE"
                setTextColor(Color.parseColor("#071723"))
                (background as? android.graphics.drawable.GradientDrawable)?.setColor(
                    Color.argb(170, Color.red(palette[claimed - 1]), Color.green(palette[claimed - 1]), Color.blue(palette[claimed - 1])),
                )
            }
            Toast.makeText(ctx, "Day $claimed test claimed.", Toast.LENGTH_SHORT).show()
        }
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#071723")))
        dialog.setOnDismissListener { if (activeSettingsDialog === dialog) activeSettingsDialog = null }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#F7D99B"))
        activeSettingsDialog = dialog
    }

    private fun showGameMenu() {
        showingGameMenu = true
        val menu = MenuView(
            this,
            isChallengeMenu = selectedGameMode == GameMode.CHALLENGES,
        )
        menuView = menu
        menu.onGameSelected = { type ->
            if (selectedGameMode == GameMode.CHALLENGES) {
                if (type == MenuView.GameType.CHESS) {
                    startActivity(Intent(this@MainActivity, ChessChallengeActivity::class.java))
                }
            } else {
                launchGame(type)
            }
        }
        menu.onSettingsClicked = { showSettings() }
        menu.onBackClicked = { showModeSelection() }
        screenRoot.removeAllViews()
        screenRoot.addView(menu, FrameLayout.LayoutParams(-1, -1))
    }

    private fun launchGame(type: MenuView.GameType) {
        val modeExtra = selectedGameMode.name
        when (type) {
            MenuView.GameType.MORABARABA ->
                startActivity(Intent(this, MorabarabaActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.TICTACTOE ->
                startActivity(Intent(this, TicTacToeActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.CONNECT_FOUR ->
                startActivity(Intent(this, ConnectFourActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.LUDO ->
                startActivity(Intent(this, LudoActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.SNAKES_LADDERS ->
                startActivity(Intent(this, SnakesLaddersActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.MANCALA ->
                startActivity(Intent(this, MancalaActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.YOTE ->
                startActivity(Intent(this, YoteActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.ONITAMA ->
                startActivity(Intent(this, OnitamaActivity::class.java).withGameMode(modeExtra))
            MenuView.GameType.FIVE_FIELD_KONO ->
                startActivity(Intent(this, FiveFieldKonoActivity::class.java).withGameMode(modeExtra))
            else ->
                startActivity(Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_GAME, type.name)
                    putExtra(GameMode.EXTRA_MODE, modeExtra)
                })
        }
    }

    private fun Intent.withGameMode(mode: String): Intent =
        apply { putExtra(GameMode.EXTRA_MODE, mode) }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeSettingsDialog?.isShowing == true) {
            activeSettingsDialog?.dismiss()
            return
        }
        if (showingGameMenu) {
            showModeSelection()
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (showingGameMenu) {
            menuView?.resetLoadingState()
            menuView?.refreshRecentlyPlayed()
            MusicPlayer.playForMode(this, selectedGameMode)
        } else {
            MusicPlayer.enterModeSelection(this)
        }
        makeFullscreen()
    }

    private var pendingDailyReminderEnable = false

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != DailyReminderManager.NOTIFICATION_PERMISSION_REQUEST_CODE) return

        val granted = grantResults.firstOrNull() ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            DailyReminderManager.setEnabled(this, true)
            if (pendingDailyReminderEnable) {
                Toast.makeText(this, "Daily game reminders are on.", Toast.LENGTH_SHORT).show()
            }
        } else {
            SettingsManager.setDailyRemindersEnabled(this, false)
            if (pendingDailyReminderEnable) {
                Toast.makeText(this, "Daily game reminders remain off.", Toast.LENGTH_SHORT).show()
            }
        }
        pendingDailyReminderEnable = false
        if (activeSettingsDialog?.isShowing == true) {
            activeSettingsDialog?.dismiss()
            showSettings()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    // ─── About ────────────────────────────────────────────────────────────────

    private fun showAbout() {
        activeSettingsDialog?.dismiss()
        activeSettingsDialog = null

        val ctx = this
        val dp = resources.displayMetrics.density
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
            text = "About"
            setTextColor(Color.parseColor("#F7D99B"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            letterSpacing = 0.08f
            setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(180, 20, 4, 3))
        })
        header.addView(TextView(ctx).apply {
            text = "MK BOARD GAMES · Privacy, terms and support"
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

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt())
        }
        scroll.addView(root)
        wrapper.addView(scroll)

        fun divider() = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#85502D"), Color.TRANSPARENT),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt(),
            ).also {
                it.setMargins((12 * dp).toInt(), (2 * dp).toInt(), (12 * dp).toInt(), (2 * dp).toInt())
            }
        }

        fun legalRow(icon: String, label: String): LinearLayout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
                    it.setMargins((2 * dp).toInt(), (5 * dp).toInt(), (2 * dp).toInt(), (5 * dp).toInt())
                }
                minimumHeight = (58 * dp).toInt()
                setPadding((10 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnTouchListener { v, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN ->
                            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60L).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                    }
                    false
                }
                addView(TextView(ctx).apply {
                    text = icon
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
                })
                addView(TextView(ctx).apply {
                    text = label
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(ctx).apply {
                    text = "›"
                    setTextColor(Color.parseColor("#F7D99B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                    gravity = Gravity.CENTER
                })
            }

        val ppRow = legalRow("🔒", "Privacy Policy")
        ppRow.setOnClickListener {
            val ppContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            }
            fun pTxt(t: String, bold: Boolean = false, accent: Boolean = false) = TextView(ctx).apply {
                text = t
                setTextColor(if (accent) Color.parseColor("#E3B86A") else Color.parseColor("#CCCCCC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            ppContent.addView(pTxt("Privacy Policy — MK BOARD GAMES", bold = true, accent = true))
            ppContent.addView(pTxt("Effective date: September 24, 2026"))
            ppContent.addView(pTxt("\nDATA AND GAME INFORMATION\nNo account is required. Game statistics, app settings, and active game state are stored on your device. MKDEV does not receive or store your game records on its own servers."))
            ppContent.addView(pTxt("\nADVERTISING\nThe app may display banner and rewarded ads through Google AdMob. When ads are requested, Google and its advertising partners may process advertising identifiers, device and operating-system information, IP address, approximate location inferred from network information, ad views and interactions, diagnostics, and information used to prevent fraud and abuse. Google's Privacy Policy: https://policies.google.com/privacy"))
            ppContent.addView(pTxt("\nPURCHASES\nGoogle Play processes the optional one-time Remove Ads purchase. MKDEV receives purchase status, not your payment-card details."))
            ppContent.addView(pTxt("\nPERMISSIONS\n• Internet — loads ads and communicates with Google Play services\n• Advertising ID — supports advertising services\n• Notifications — optional daily game reminders\n• Vibrate — haptic feedback during gameplay"))
            ppContent.addView(pTxt("\nDATA DELETION\nGame information saved on this device can be deleted by clearing app data or uninstalling the app."))
            ppContent.addView(pTxt("\nCONTACT\n$SUPPORT_EMAIL"))
            val ppScroll = ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320 * dp).toInt())
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
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) } catch (_: Exception) {}
                }
                .create()
                .apply {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
                    show()
                }
        }
        root.addView(ppRow)
        root.addView(divider())

        val tosRow = legalRow("📄", "Terms of Service")
        tosRow.setOnClickListener {
            val tosContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            }
            fun tTxt(t: String, bold: Boolean = false, accent: Boolean = false) = TextView(ctx).apply {
                text = t
                setTextColor(if (accent) Color.parseColor("#E3B86A") else Color.parseColor("#CCCCCC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            tosContent.addView(tTxt("Terms of Service — MK BOARD GAMES", bold = true, accent = true))
            tosContent.addView(tTxt("Effective date: September 24, 2026"))
            tosContent.addView(tTxt("\n1. ACCEPTANCE\nBy installing or using MK BOARD GAMES you agree to these terms. If you do not agree, uninstall the app."))
            tosContent.addView(tTxt("\n2. LICENCE\nMK BOARD GAMES is available without charge for personal, non-commercial use. Optional in-app purchases may be offered through Google Play. You may not reverse-engineer, redistribute, or sell the app or any part of it."))
            tosContent.addView(tTxt("\n3. ADVERTISING\nThe app may display banner and rewarded advertisements through Google AdMob. Google's terms and privacy policy apply to information processed by its advertising services."))
            tosContent.addView(tTxt("\n4. PURCHASES\nGoogle Play processes the optional one-time Remove Ads purchase, including payment and refund handling under Google Play's terms. The current price is shown by Google Play before purchase."))
            tosContent.addView(tTxt("\n5. DISCLAIMER\nMK BOARD GAMES is provided \"as is\" without warranties of any kind. MKDEV is not liable for any loss or damage arising from use of the app."))
            tosContent.addView(tTxt("\n6. CHANGES\nThese terms may be updated when app features or legal requirements change. Continued use after an update constitutes acceptance of the revised terms."))
            tosContent.addView(tTxt("\nCONTACT\n$SUPPORT_EMAIL"))
            val tosScroll = ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320 * dp).toInt())
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
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_OF_SERVICE_URL))) } catch (_: Exception) {}
                }
                .create()
                .apply {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
                    show()
                }
        }
        root.addView(tosRow)
        root.addView(divider())

        val contactRow = legalRow("", "Contact Us").apply {
            val emailIcon = getChildAt(0) as TextView
            emailIcon.text = ""
            emailIcon.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#321718"))
                setStroke((1f * dp).toInt(), Color.parseColor("#C8894C"))
            }
            emailIcon.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_email, 0, 0, 0)
            emailIcon.compoundDrawablePadding = 0
            emailIcon.setPadding((7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt())
        }
        contactRow.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "MK BOARD GAMES Support")
                })
            } catch (_: Exception) {
                Toast.makeText(ctx, SUPPORT_EMAIL, Toast.LENGTH_LONG).show()
            }
        }
        root.addView(contactRow)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setView(wrapper)
            .setPositiveButton("Done", null)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#061321")))
        dialog.setOnDismissListener { if (activeSettingsDialog === dialog) activeSettingsDialog = null }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#F7D99B"))
        activeSettingsDialog = dialog
    }

    private fun showSettings() {
        activeSettingsDialog?.dismiss()
        activeSettingsDialog = null

        val ctx    = this
        val dp     = resources.displayMetrics.density

        // A compact board-room surface for the app-wide controls.
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

        // ── General ──
        root.addView(sectionHeader("⚙  GENERAL"))
        var dailyReminders = SettingsManager.isDailyRemindersEnabled(ctx)
        val (reminderRow, reminderVal) = settingRow(
            "◷",
            "Daily Game Reminders",
            if (dailyReminders) "On" else "Off",
        )
        reminderRow.setOnClickListener {
            if (dailyReminders) {
                dailyReminders = false
                DailyReminderManager.setEnabled(ctx, false)
                reminderVal.text = "Off"
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingDailyReminderEnable = true
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    DailyReminderManager.NOTIFICATION_PERMISSION_REQUEST_CODE,
                )
            } else {
                dailyReminders = true
                DailyReminderManager.setEnabled(ctx, true)
                reminderVal.text = "On"
            }
        }
        root.addView(reminderRow)

        root.addView(sectionHeader("♫  MUSIC"))
        var musicEnabled = SettingsManager.isMusicEnabled(ctx)
        val (musicRow, musicVal) =
            settingRow("♫", "Background Music", if (musicEnabled) "On" else "Off")
        musicRow.setOnClickListener {
            musicEnabled = !musicEnabled
            MusicPlayer.setEnabled(ctx, musicEnabled)
            musicVal.text = if (musicEnabled) "On" else "Off"
        }
        root.addView(musicRow)

        var musicVolume = SettingsManager.getMusicVolume(ctx)
        val volumePanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
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
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (5 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.setMargins((2 * dp).toInt(), (5 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt())
            }
        }
        val volumeLabel = TextView(ctx).apply {
            text = "Background Music Volume  •  $musicVolume%"
            setTextColor(Color.parseColor("#FFF8E8"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        volumePanel.addView(volumeLabel)
        volumePanel.addView(SeekBar(ctx).apply {
            max = 100
            progress = musicVolume
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C8894C"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F7D99B"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    musicVolume = progress
                    MusicPlayer.setVolume(ctx, musicVolume)
                    volumeLabel.text = "Background Music Volume  •  $musicVolume%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        root.addView(volumePanel)

        var matchMusicVolume = SettingsManager.getMatchMusicVolume(ctx)
        val matchVolumePanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
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
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (5 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.setMargins((2 * dp).toInt(), (0 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt())
            }
        }
        val matchVolumeLabel = TextView(ctx).apply {
            text = "In-match Music Volume  •  $matchMusicVolume%"
            setTextColor(Color.parseColor("#FFF8E8"))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        matchVolumePanel.addView(matchVolumeLabel)
        matchVolumePanel.addView(SeekBar(ctx).apply {
            max = 100
            progress = matchMusicVolume
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C8894C"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F7D99B"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    matchMusicVolume = progress
                    MusicPlayer.setMatchVolume(ctx, matchMusicVolume)
                    matchVolumeLabel.text = "In-match Music Volume  •  $matchMusicVolume%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        root.addView(matchVolumePanel)

        var movementSounds = SettingsManager.isMovementSoundsEnabled(ctx)
        val (movSoundRow, movSoundVal) = settingRow("🔊", "Movement Sounds", if (movementSounds) "On" else "Off")
        movSoundRow.setOnClickListener {
            movementSounds = !movementSounds
            SettingsManager.setMovementSoundsEnabled(ctx, movementSounds)
            SoundPlayer.movementSoundsEnabled = movementSounds
            movSoundVal.text = if (movementSounds) "On" else "Off"
        }
        root.addView(movSoundRow)

        var helperEnabled = SettingsManager.getHelper(ctx)
        val (helperRow, helperVal) =
            settingRow("💡", "Helper", if (helperEnabled) "On" else "Off")
        helperRow.setOnClickListener {
            helperEnabled = !helperEnabled
            SettingsManager.setHelper(ctx, helperEnabled)
            helperVal.text = if (helperEnabled) "On" else "Off"
        }
        root.addView(helperRow)

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
            Page("Amazons",    "♛",  "amazons"),
            Page("Draughts",   "⬤",  "checkers"),
            Page("International Draughts", "⬤", "international_draughts"),
            Page("Othello",    "◉",  "othello"),
            Page("Morabaraba", "⬡",  "morabaraba"),
            Page("Tic-Tac-Toe","✕",  "ttt"),
            Page("Connect Four", "●", "connect_four"),
            Page("Fox & Geese", "🦊", "fox_and_geese"),
            Page("Ludo", "●", "ludo"),
            Page("Snakes & Ladders", "🎲", "snakes_ladders"),
            Page("Xiangqi", "象", "xiangqi"),
            Page("Shogi", "将", "shogi"),
            Page("Go", "⚫", "go"),
            Page("Mancala", "●", "mancala"),
            Page("Yote", "⬡", "yote"),
            Page("Onitama", "♞", "onitama"),
            Page("Five Field Kono", "⬟", "five_field_kono"),
        )

        var currentPage = 0
        var statsMode = SettingsManager.currentMode(ctx).let { mode ->
            if (mode == GameMode.IRREGULAR) GameMode.NORMAL else mode
        }

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
        val dotsScroll = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(dotRow, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
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
            val s = if (gameTag == "overall") SettingsManager.getStats(ctx, statsMode)
                    else SettingsManager.getGameStats(ctx, gameTag, statsMode)

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
            dotsScroll.post {
                val dot = dots[currentPage]
                dotsScroll.smoothScrollTo(
                    (dot.left + dot.width / 2 - dotsScroll.width / 2).coerceAtLeast(0),
                    0,
                )
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

        wrapper.addView(dotsScroll, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        wrapper.addView(navRow)
        wrapper.addView(android.view.View(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
        })
        wrapper.addView(statCard)

        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_MinWidth)
            .setTitle("Your Stats")
            .setIcon(R.drawable.ic_app_logo)
            .setView(wrapper)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset") { _, _ ->
                AlertDialog.Builder(ctx).setTitle("Reset Stats?")
                    .setMessage("This clears all wins, losses and draws for the selected mode.")
                    .setPositiveButton("Reset") { _, _ -> SettingsManager.resetStats(ctx, statsMode) }
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
