package com.mkdev.mkboardgames

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.amazons.AmazonsRuleEngine
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.checkers.CheckersRuleEngine
import com.mkdev.mkboardgames.games.checkers.InternationalDraughtsRuleEngine
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine
import com.mkdev.mkboardgames.games.chess.EtherealPlayer
import com.mkdev.mkboardgames.games.chess.StockfishPlayer
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseRuleEngine
import com.mkdev.mkboardgames.games.go.GoRuleEngine
import com.mkdev.mkboardgames.games.go.GoAIPlayer
import com.mkdev.mkboardgames.games.othello.OthelloRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import com.mkdev.mkboardgames.games.shogi.ShogiRuleEngine
import com.mkdev.mkboardgames.games.xiangqi.XiangqiRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.BoardSelectionOption
import com.mkdev.mkboardgames.ui.BoardSelectionPreview
import com.mkdev.mkboardgames.ui.BoardSelectionView
import com.mkdev.mkboardgames.ui.CaptureStripView
import com.mkdev.mkboardgames.ui.ChessBoardSelectionView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.ChessRulesView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.DraughtsBoardStyle
import com.mkdev.mkboardgames.ui.FoxAndGeeseBoardStyle
import com.mkdev.mkboardgames.ui.OthelloBoardStyle
import com.mkdev.mkboardgames.ui.ShogiBoardStyle
import com.mkdev.mkboardgames.ui.SnakesLaddersGameOverView
import com.mkdev.mkboardgames.ui.XiangqiBoardStyle
import kotlinx.coroutines.*

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "game_type"
    }

    private lateinit var boardView:        BoardView
    private lateinit var autoplayButton:   AutoplayButtonView
    private lateinit var hudView:          HudView
    private lateinit var topCaptureView:   CaptureStripView
    private lateinit var goNoticeView:    android.widget.TextView
    private lateinit var bottomCaptureView: CaptureStripView
    private lateinit var chessGameOverView: SnakesLaddersGameOverView
    private lateinit var engine:           RuleEngine
    private lateinit var gameType:         String
    private lateinit var gameContainer:    View
    private lateinit var screenRoot:       FrameLayout
    private lateinit var styledOverlayHost: FrameLayout
    private var amazonsBoardSize: Int = 8
    private val internationalDraughtsStyles = arrayOf(
        DraughtsBoardStyle.CANVAS,
        DraughtsBoardStyle.INTERNATIONAL_DARK_WOOD,
        DraughtsBoardStyle.INTERNATIONAL_LIGHT_WOOD,
    )
    private val foxAndGeeseStyles = arrayOf(
        FoxAndGeeseBoardStyle.CANVAS,
        FoxAndGeeseBoardStyle.LIGHT_WOOD,
        FoxAndGeeseBoardStyle.CROSS_WOOD,
    )
    private val xiangqiStyles = arrayOf(
        XiangqiBoardStyle.CLASSIC,
        XiangqiBoardStyle.CHINESE,
        XiangqiBoardStyle.ENGLISH,
    )

    private var gameState: GameState = GameState(arrayOfNulls(64))
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var autoplayEnabled = false
    private var autoplayMoveInProgress = false
    private val autoplayAllowed: Boolean
        get() = SettingsManager.currentMode(this) == GameMode.IRREGULAR
    private val moveHistory      = ArrayDeque<GameState>()
    private var undosRemaining = 3
    private val scope            = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Result guard: stats recorded exactly once per game
    private var resultRecorded = false
    private var interstitialAd: Any? = null
    private var exitPosted = false
    private val redoGameStates = ArrayDeque<GameState>()
    private val redoCaptures   = ArrayDeque<Pair<List<Piece>, List<Piece>>>()
    private val redoMoves      = ArrayDeque<List<GameState>>()
    private val redoCapSnaps   = ArrayDeque<List<Pair<List<Piece>, List<Piece>>>>()
    private var autoPassJob: Job? = null

    private var capturedByWhite = mutableListOf<Piece>()
    private var capturedByBlack = mutableListOf<Piece>()
    private val captureSnapshots = ArrayDeque<Pair<List<Piece>, List<Piece>>>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.setCurrentModeFromIntent(this, intent)
        makeFullscreen()
        SoundPlayer.init(this)

        gameType = intent.getStringExtra(EXTRA_GAME) ?: "CHESS"

        engine   = when (gameType) {
            "AMAZONS" -> AmazonsRuleEngine(amazonsBoardSize)
            "OTHELLO"   -> OthelloRuleEngine()
            "CHECKERS"  -> CheckersRuleEngine()
            "INTERNATIONAL_DRAUGHTS" -> InternationalDraughtsRuleEngine()
            "FOX_AND_GEESE" -> FoxAndGeeseRuleEngine()
            "GO" -> GoRuleEngine()
            "SHOGI"       -> ShogiRuleEngine()
            "XIANGQI"   -> XiangqiRuleEngine()
            else        -> ChessRuleEngine()   // covers "CHESS" and any future alias
        }

        val dp    = resources.displayMetrics.density
        val hudH  = (56 * dp).toInt()
        val capH  = if (gameType == "GO") (58 * dp).toInt() else (36 * dp).toInt()
        val autoplayButtonH = (76 * dp).toInt()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView          = HudView(this)
        topCaptureView   = CaptureStripView(this).also { it.dividerOnTop = false }
        goNoticeView     = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#FFE0A3"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
            setLineSpacing(1f, 1.05f)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            visibility = View.GONE
        }
        boardView        = BoardView(this)
        autoplayButton   = AutoplayButtonView(this)
        bottomCaptureView = CaptureStripView(this).also { it.dividerOnTop = true }
        chessGameOverView = SnakesLaddersGameOverView(this).apply {
            winnerBaselineDp = 112f
            bottomCaptureTopPxProvider = { bottomCaptureView.top.toFloat() }
            onReplay = {
                visibility = View.GONE
                startGame()
            }
            onWatchReplay = {
                visibility = View.GONE
                launchReplay(currentResultLabel(), lockBoardStyle = true)
            }
            onHome = {
                visibility = View.GONE
                clearPausedMatch()
                finish()
            }
        }
        boardView.onEmptySpaceTapped = null
        autoplayButton.onAutoplayChanged = { enabled ->
            if (autoplayAllowed && gameType != "LUDO" && vsAI) {
                autoplayEnabled = enabled
                if (!enabled && autoplayMoveInProgress) {
                    autoplayMoveInProgress = false
                    boardView.cancelMoveAnimation()
                    hudView.setThinking(false)
                }
                if (enabled &&
                    matchStarted &&
                    gameState.status == GameStatus.IN_PROGRESS &&
                    !boardView.isLocked &&
                    aiControlsCurrentTurn()
                ) {
                    triggerAI()
                }
            }
        }
        topCaptureView.onPieceSelected = ::handleShogiHandTap
        bottomCaptureView.onPieceSelected = ::handleShogiHandTap

        // Render the selected game's real starting position before the first
        // match. The game container stays hidden until startGame() so the
        // board cannot appear behind the initial setup choices.
        SettingsManager.activateGameTheme(this, gameType.lowercase())
        gameState = engine.initialState()
        boardView.ruleEngine = engine
        boardView.gameState = gameState
        val isInternationalDraughts = gameType == "INTERNATIONAL_DRAUGHTS"
        val isDraughtsGame =
            gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS"
        if (isInternationalDraughts) {
            boardView.draughtsBoardStyle = internationalDraughtsStyles.first()
        }
        autoplayButton.setAutoplayEnabled(false, animate = false)

        container.addView(hudView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hudH))
        container.addView(topCaptureView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        container.addView(goNoticeView,
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (58 * dp).toInt(),
            ))
        container.addView(boardView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                .apply { weight = 1f })
        container.addView(autoplayButton,
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                autoplayButtonH,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                autoplayButton.visibility =
                    if (autoplayAllowed && gameType == "CHESS" && vsAI) View.VISIBLE else View.GONE
            })
        container.addView(bottomCaptureView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        val showCaptures = gameType != "OTHELLO" && gameType != "AMAZONS"
        topCaptureView.visibility    = if (showCaptures) View.VISIBLE else View.GONE
        bottomCaptureView.visibility = if (showCaptures) View.VISIBLE else View.GONE

        AdManager.attachBanner(container)
        gameContainer = container
        screenRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        styledOverlayHost = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }
        screenRoot.addView(
            gameContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        screenRoot.addView(
            styledOverlayHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        screenRoot.addView(
            chessGameOverView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(screenRoot)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        gameContainer.visibility = View.INVISIBLE
        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        activityResumed = true
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) boardView.resumeMoveAnimation()
        if (!::boardView.isInitialized || !boardView.hasPendingMoveAnimation()) {
            resumeComputerTurnIfNeeded()
        }
    }

    private fun stopAutoplayAndAiThinking() {
        autoplayEnabled = false
        autoplayMoveInProgress = false
        autoPassJob?.cancel()
        autoPassJob = null
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
        }
        if (::boardView.isInitialized) {
            boardView.isLocked = false
            boardView.cancelMoveAnimation()
        }
        if (::hudView.isInitialized) {
            hudView.setThinking(false)
        }
        scope.coroutineContext.cancelChildren()
    }

    override fun onPause() {
        activityResumed = false
        pauseAutomatedGameplayForLifecycle()
        SoundPlayer.stopAll()
        if (!isFinishing) savePausedMatch()
        super.onPause()
    }

    private fun pauseAutomatedGameplayForLifecycle() {
        autoPassJob?.cancel()
        autoPassJob = null
        scope.coroutineContext.cancelChildren()
        if (::boardView.isInitialized) {
            if (boardView.hasPendingMoveAnimation()) {
                boardView.pauseMoveAnimation()
            } else {
                boardView.isLocked = false
            }
        }
        if (::hudView.isInitialized) hudView.setThinking(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) makeFullscreen()
    }

    override fun onDestroy() {
        stopAutoplayAndAiThinking()
        super.onDestroy()
        scope.cancel()
    }

    override fun finish() {
        finishToHome()
    }

    override fun finishAfterTransition() {
        finishToHome()
    }

    private fun finishToHome() {
        if (exitPosted) return
        exitPosted = true
        if (::gameContainer.isInitialized) gameContainer.visibility = View.GONE
        window.decorView.postDelayed({
            super.finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 16L)
    }

    private fun applyBoardStyleIndex(styleIndex: Int) {
        when (gameType) {
            "CHESS" -> boardView.chessBoardStyle =
                ChessBoardStyle.entries.getOrElse(styleIndex) { ChessBoardStyle.CANVAS }
            "AMAZONS" -> if (amazonsBoardSize == 8) {
                boardView.chessBoardStyle =
                    ChessBoardStyle.entries.getOrElse(styleIndex) { ChessBoardStyle.CANVAS }
            } else {
                boardView.draughtsBoardStyle =
                    internationalDraughtsStyles.getOrElse(styleIndex) {
                        internationalDraughtsStyles.first()
                    }
            }
            "CHECKERS" -> boardView.draughtsBoardStyle =
                DraughtsBoardStyle.entries.getOrElse(styleIndex) { DraughtsBoardStyle.CANVAS }
            "INTERNATIONAL_DRAUGHTS" ->
                boardView.draughtsBoardStyle =
                    internationalDraughtsStyles.getOrElse(styleIndex) {
                        internationalDraughtsStyles.first()
                    }
            "OTHELLO" -> boardView.othelloBoardStyle =
                OthelloBoardStyle.entries.getOrElse(styleIndex) { OthelloBoardStyle.CANVAS }
            "FOX_AND_GEESE" ->
                boardView.foxAndGeeseBoardStyle =
                    foxAndGeeseStyles.getOrElse(styleIndex) { foxAndGeeseStyles.first() }
            "XIANGQI" -> boardView.xiangqiBoardStyle =
                xiangqiStyles.getOrElse(styleIndex) { XiangqiBoardStyle.CLASSIC }
            "SHOGI" -> boardView.shogiBoardStyle =
                ShogiBoardStyle.entries.getOrElse(styleIndex) { ShogiBoardStyle.CLASSIC }
        }
    }

    private fun currentBoardStyleIndex(): Int = when {
        gameType == "CHESS" -> boardView.chessBoardStyle.ordinal
        gameType == "AMAZONS" && amazonsBoardSize == 8 -> boardView.chessBoardStyle.ordinal
        gameType == "AMAZONS" ->
            internationalDraughtsStyles.indexOf(boardView.draughtsBoardStyle).coerceAtLeast(0)
        gameType == "CHECKERS" -> boardView.draughtsBoardStyle.ordinal
        gameType == "INTERNATIONAL_DRAUGHTS" ->
            internationalDraughtsStyles.indexOf(boardView.draughtsBoardStyle).coerceAtLeast(0)
        gameType == "OTHELLO" -> boardView.othelloBoardStyle.ordinal
        gameType == "FOX_AND_GEESE" ->
            foxAndGeeseStyles.indexOf(boardView.foxAndGeeseBoardStyle).coerceAtLeast(0)
        gameType == "XIANGQI" ->
            xiangqiStyles.indexOf(boardView.xiangqiBoardStyle).coerceAtLeast(0)
        gameType == "SHOGI" -> boardView.shogiBoardStyle.ordinal
        else -> 0
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeStyledOverlay != null) {
            dismissStyledOverlay(invokeCancel = true)
            return
        }
        if (matchStarted &&
            gameType != "LUDO" &&
            gameType != "ONITAMA" &&
            gameState.status != GameStatus.IN_PROGRESS
        ) {
            // Every board game handled here keeps its game-over actions on the
            // board. Keep repeated system-back presses from falling through
            // to the activity finish path and returning to mode selection.
            return
        }
        if (matchStarted && gameState.status != GameStatus.IN_PROGRESS) {
            showResultDialog()
            return
        }
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            finish()
            return
        }
        if (!canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        stopAutoplayAndAiThinking()
        MusicPlayer.enterPausedMatch(this)
        if (isStyledBoardGame()) {
            showChessLeaveMatchDialog()
            return
        }
        AlertDialog.Builder(this).setTitle("Leave Match?")
            .setMessage("Pause to keep this match and resume it later, or leave to forfeit.")
            .setPositiveButton("Pause & Exit") { _, _ ->
                pauseMatchAndExit()
            }
            .setNeutralButton("Leave Match") { _, _ ->
                clearPausedMatch()
                finish()
            }
            .setNegativeButton("Keep Playing") { _, _ ->
                MusicPlayer.resumeMatch(this)
            }
            .setOnCancelListener { MusicPlayer.resumeMatch(this) }
            .show()
    }

    private fun showChessLeaveMatchDialog() {
        // The overlay does not pause the Activity, so stop automated gameplay
        // before covering the board with the leave-match screen.
        MusicPlayer.enterPausedMatch(this)
        stopAutoplayAndAiThinking()
        val view = ChessChoiceView(
            this,
            title = "Leave Match?",
            subtitle = "Pause to resume later, or leave to forfeit this game.",
            choices = listOf(
                ChessChoiceView.Choice(
                    "Pause & Exit",
                    "Save and resume later",
                    "Ⅱ",
                    Color.parseColor("#E3B86A"),
                ),
                ChessChoiceView.Choice(
                    "Leave Match",
                    "Forfeit this game",
                    "⚑",
                    Color.parseColor("#E58A7A"),
                ),
                ChessChoiceView.Choice(
                    "Keep Playing",
                    "Return to the board",
                    "↩",
                    Color.parseColor("#A9B6E8"),
                ),
            ),
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            when (which) {
                0 -> {
                    dismissStyledOverlay()
                    pauseMatchAndExit()
                }
                1 -> {
                    dismissStyledOverlay()
                    clearPausedMatch()
                    finish()
                }
                else -> {
                    dismissStyledOverlay()
                    showChessBoardAfterDialog()
                }
            }
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun pauseMatchAndExit() {
        stopAutoplayAndAiThinking()
        savePausedMatch()
        finish()
    }

    private fun savePausedMatch() {
        if (gameState.status != GameStatus.IN_PROGRESS || gameState.moveHistory.isEmpty()) return
        PausedMatchStore.save(
            this,
            gameType = gameType,
            vsAI = vsAI,
            playerColor = playerColor.name,
            moves = gameState.moveHistory,
            boardSize = if (gameType == "AMAZONS") amazonsBoardSize else null,
            boardStyle = currentBoardStyleIndex(),
        )
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, gameType)

    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun isStyledBoardGame(): Boolean =
        gameType == "CHESS" ||
            gameType == "AMAZONS" ||
            gameType == "CHECKERS" ||
            gameType == "INTERNATIONAL_DRAUGHTS" ||
            gameType == "OTHELLO" ||
            gameType == "FOX_AND_GEESE" ||
            gameType == "GO" ||
            gameType == "SHOGI" ||
            gameType == "XIANGQI"

    private fun showModeDialog() {
        if (isStyledBoardGame()) {
            showChessMenu()
            return
        }
        val gameName = when (gameType) {
            "OTHELLO"  -> "Othello"
            "CHECKERS" -> "Draughts"
            "INTERNATIONAL_DRAUGHTS" -> "International Draughts"
            "FOX_AND_GEESE" -> "Fox and Geese"
            "SHOGI" -> "Shogi 将棋"
            "XIANGQI" -> "Xiangqi 象棋"
            "GO" -> "Go 围棋"
            else       -> "Chess"
        }
        val paused = PausedMatchStore.has(this, gameType)
        val options = buildList {
            if (paused) add("Resume Match")
            add("vs CPU")
            add("2 Players")
            add("How to Play")
        }
        AlertDialog.Builder(this).setTitle(gameName)
            .setItems(options.toTypedArray()) { _, which ->
                if (paused && which == 0) {
                    resumePausedMatch()
                    return@setItems
                }
                when (options[which]) {
                    "vs CPU" -> { vsAI = true; showColorPickerDialog() }
                    "2 Players" -> { vsAI = false; playerColor = PieceColor.WHITE; startGame() }
                    "How to Play" -> showRules(showModeAfter = !matchStarted)
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (!matchStarted) finish() }
            .show()
    }

    private fun showChessMenu() {
        val menuView = ChessMenuView(
            this,
            PausedMatchStore.has(this, gameType),
            gameLabel = styledGameLabel(),
        )
        menuView.onVsAi = {
            vsAI = true
            if (gameType == "AMAZONS") {
                showAmazonsVariantPicker()
            } else if (gameType == "CHESS") {
                showChessBoardSelection()
            } else if (boardSelectionOptions() != null) {
                showBoardSelection()
            } else {
                showChessSidePicker()
            }
        }
        menuView.onTwoPlayers = {
            vsAI = false
            if (gameType == "AMAZONS") {
                showAmazonsVariantPicker()
            } else if (gameType == "CHESS") {
                showChessBoardSelection()
            } else if (boardSelectionOptions() != null) {
                showBoardSelection()
            } else {
                playerColor = PieceColor.WHITE
                dismissStyledOverlay()
                startGame()
            }
        }
        menuView.onHowToPlay = {
            showRules(showModeAfter = !matchStarted)
        }
        menuView.onResumeMatch = {
            resumePausedMatch()
        }

        showStyledOverlay(
            view = menuView,
            fullScreen = true,
            onCancel = { if (!matchStarted) finish() else showChessBoardAfterDialog() },
        )
    }

    private fun showAmazonsVariantPicker() {
        val view = ChessChoiceView(
            this,
            title = "Amazons Board",
            subtitle = "Choose the board size for this match.",
            choices = listOf(
                ChessChoiceView.Choice(
                    "8 × 8",
                    "Chess board presentation",
                    "8",
                    Color.parseColor("#E3B86A"),
                ),
                ChessChoiceView.Choice(
                    "10 × 10",
                    "International Draughts presentation",
                    "10",
                    Color.parseColor("#8EC7B9"),
                ),
            ),
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            amazonsBoardSize = if (which == 0) 8 else 10
            dismissStyledOverlay()
            engine = AmazonsRuleEngine(amazonsBoardSize)
            gameState = engine.initialState()
            boardView.ruleEngine = engine
            boardView.gameState = gameState
            showBoardSelection()
        }
        view.onBackRequested = { showChessMenu() }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessMenu() },
        )
    }

    private fun showColorPickerDialog() {
        if (gameType != "CHESS" && isStyledBoardGame()) {
            showChessSidePicker()
            return
        }
        val sides = if (gameType == "FOX_AND_GEESE")
            arrayOf("Fox (moves second)", "Geese (moves first)")
        else if (gameType == "XIANGQI")
            arrayOf("Red (moves first)", "Black (moves second)")
        else if (gameType == "SHOGI")
            arrayOf("Sente / Black (moves first)", "Gote / White (moves second)")
        else if (gameType == "GO")
            arrayOf("Black (moves first)", "White (moves second)")
        else
            arrayOf("White (moves first)", "Black (moves second)")
        AlertDialog.Builder(this)
            .setTitle(if (gameType == "FOX_AND_GEESE") "Choose your side" else "Play as")
            .setItems(sides) { _, which ->
                playerColor = if (gameType == "SHOGI") {
                    if (which == 0) PieceColor.BLACK else PieceColor.WHITE
                } else if (gameType == "GO") {
                    if (which == 0) PieceColor.BLACK else PieceColor.WHITE
                } else {
                    if (which == 0) PieceColor.WHITE else PieceColor.BLACK
                }
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showChessSidePicker() {
        val firstChoice = when (gameType) {
            "FOX_AND_GEESE" -> "Fox"
            "XIANGQI" -> "Red"
            "SHOGI" -> "Sente / Black"
            "GO" -> "Black"
            else -> "White"
        }
        val secondChoice = when (gameType) {
            "FOX_AND_GEESE" -> "Geese"
            "XIANGQI" -> "Black"
            "SHOGI" -> "Gote / White"
            "GO" -> "White"
            else -> "Black"
        }
        val firstDetail = if (gameType == "FOX_AND_GEESE") {
            "Moves second"
        } else {
            "Moves first"
        }
        val secondDetail = if (gameType == "FOX_AND_GEESE") {
            "Moves first"
        } else {
            "Moves second"
        }
        val firstSymbol = when (gameType) {
            "CHESS" -> "♔"
            "SHOGI" -> "▲"
            "XIANGQI" -> "红"
            "GO" -> "●"
            "FOX_AND_GEESE" -> "F"
            else -> "●"
        }
        val secondSymbol = when (gameType) {
            "CHESS" -> "♚"
            "SHOGI" -> "▽"
            "XIANGQI" -> "黑"
            "GO" -> "○"
            "FOX_AND_GEESE" -> "G"
            else -> "○"
        }
        val firstColor = if (gameType == "SHOGI" || gameType == "GO") {
            PieceColor.BLACK
        } else {
            PieceColor.WHITE
        }
        val view = ChessChoiceView(
            this,
            title = "Play As",
            subtitle = "Choose your colour before the first move.",
            choices = listOf(
                ChessChoiceView.Choice(
                    firstChoice,
                    firstDetail,
                    firstSymbol,
                    Color.parseColor("#E3B86A"),
                ),
                ChessChoiceView.Choice(
                    secondChoice,
                    secondDetail,
                    secondSymbol,
                    Color.parseColor("#A9B6E8"),
                ),
            ),
            gameLabel = styledGameLabel(),
            headerSymbol = "●",
        )
        view.onChoiceSelected = { which ->
            playerColor = if (which == 0) firstColor else firstColor.opponent()
            dismissStyledOverlay()
            startGame()
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showModeDialog() },
        )
    }

    private fun showChessBoardSelection() {
        val view = ChessBoardSelectionView(
            this,
            matchLabel = if (vsAI) "vs CPU" else "2 Players",
        )
        view.onSelectionConfirmed = { style ->
            boardView.chessBoardStyle = style
            dismissStyledOverlay()
            if (vsAI) {
                showChessSidePicker()
            } else {
                playerColor = PieceColor.WHITE
                startGame()
            }
        }
        view.onBackClicked = {
            showChessMenu()
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessMenu() },
        )
    }

    private fun boardSelectionOptions(): List<BoardSelectionOption>? = when (gameType) {
        "AMAZONS" -> if (amazonsBoardSize == 8) {
            listOf(
                BoardSelectionOption("Canvas Board", "Clean and modern"),
                BoardSelectionOption("Classic Wood", "Warm tournament feel", "chess_board.jpg"),
                BoardSelectionOption("Supplied Wood", "Rich natural grain", "chess_board_wood.jpg"),
                BoardSelectionOption(
                    "Realistic Dark",
                    "High-contrast frame",
                    preview = BoardSelectionPreview.REALISTIC_CHESS,
                ),
                BoardSelectionOption("Black & White", "Bold monochrome", "chess_board_black_white.png"),
            )
        } else {
            listOf(
                BoardSelectionOption("Canvas Board", "Clean and modern", preview = BoardSelectionPreview.CHECKERS),
                BoardSelectionOption("Dark Wood", "Deep natural grain", "international_draughts_board_dark.jpg", BoardSelectionPreview.CHECKERS),
                BoardSelectionOption("Light Wood", "Bright natural grain", "international_draughts_board_light.jpg", BoardSelectionPreview.CHECKERS),
            )
        }
        "CHECKERS" -> listOf(
            BoardSelectionOption("Canvas Board", "Clean and modern", preview = BoardSelectionPreview.CHECKERS),
            BoardSelectionOption("Red & Black", "Bold contrast", "draughts_board_red_black.png", BoardSelectionPreview.CHECKERS),
            BoardSelectionOption("Classic Wood", "Warm tournament feel", "chess_board.jpg", BoardSelectionPreview.CHECKERS),
            BoardSelectionOption("Supplied Wood", "Rich natural grain", "chess_board_wood.jpg", BoardSelectionPreview.CHECKERS),
            BoardSelectionOption(
                "Realistic Dark",
                "High-contrast frame",
                preview = BoardSelectionPreview.REALISTIC_CHESS,
            ),
            BoardSelectionOption("Black & White", "Bold monochrome", "chess_board_black_white.png", BoardSelectionPreview.CHECKERS),
        )
        "INTERNATIONAL_DRAUGHTS" -> listOf(
            BoardSelectionOption("Canvas Board", "Clean and modern", preview = BoardSelectionPreview.CHECKERS),
            BoardSelectionOption("Dark Wood", "Deep natural grain", "international_draughts_board_dark.jpg", BoardSelectionPreview.CHECKERS),
            BoardSelectionOption("Light Wood", "Bright natural grain", "international_draughts_board_light.jpg", BoardSelectionPreview.CHECKERS),
        )
        "OTHELLO" -> listOf(
            BoardSelectionOption("Canvas Board", "Clean and modern", preview = BoardSelectionPreview.OTHELLO),
            BoardSelectionOption("Green Felt", "Classic table feel", "othello_board_green.webp", BoardSelectionPreview.OTHELLO),
        )
        "FOX_AND_GEESE" -> listOf(
            BoardSelectionOption("Canvas Board", "Clean and modern", preview = BoardSelectionPreview.FOX_AND_GEESE),
            BoardSelectionOption("Light Wood", "Warm natural grain", "fox_and_geese_board_light.webp", BoardSelectionPreview.FOX_AND_GEESE),
            BoardSelectionOption("Cross Wood", "Rich crafted frame", "fox_and_geese_board_cross.webp", BoardSelectionPreview.FOX_AND_GEESE),
        )
        "XIANGQI" -> listOf(
            BoardSelectionOption("Classic", "Traditional lines", "xiangqi_board.webp", BoardSelectionPreview.XIANGQI),
            BoardSelectionOption("Chinese", "Chinese labels", "xiangqi_board_chinese.webp", BoardSelectionPreview.XIANGQI),
            BoardSelectionOption("English", "English labels", "xiangqi_board_english.webp", BoardSelectionPreview.XIANGQI),
        )
        "SHOGI" -> listOf(
            BoardSelectionOption("Wood Board", "Traditional board", "shogi_board.webp", BoardSelectionPreview.SHOGI),
            BoardSelectionOption("Polished Wood", "Warm natural grain", "shogi_board_wood.webp", BoardSelectionPreview.SHOGI),
        )
        else -> null
    }

    private fun showBoardSelection() {
        val options = boardSelectionOptions() ?: run {
            if (vsAI) {
                showChessSidePicker()
            } else {
                playerColor = PieceColor.WHITE
                dismissStyledOverlay()
                startGame()
            }
            return
        }
        val view = BoardSelectionView(
            this,
            matchLabel = if (vsAI) "vs CPU" else "2 Players",
            options = options,
        )
        view.onSelectionConfirmed = { styleIndex ->
            applyBoardStyleIndex(styleIndex)
            dismissStyledOverlay()
            if (vsAI) {
                showChessSidePicker()
            } else {
                playerColor = PieceColor.WHITE
                startGame()
            }
        }
        view.onBackClicked = { showChessMenu() }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessMenu() },
        )
    }

    private fun showRules(showModeAfter: Boolean = false) {
        val gameName = when (gameType) {
            "AMAZONS" -> "Amazons"
            "OTHELLO" -> "Othello"
            "CHECKERS" -> "Draughts"
            "INTERNATIONAL_DRAUGHTS" -> "International Draughts"
            "FOX_AND_GEESE" -> "Fox and Geese"
            "SHOGI" -> "Shogi 将棋"
            "XIANGQI" -> "Xiangqi 象棋"
            "GO" -> "Go 围棋"
            else -> "Chess"
        }
        val rulesText = when (gameType) {
            "AMAZONS" -> """
AMAZONS — Rules

Overview
Amazons is played on either an 8×8 or 10×10 board. The 8×8 variant uses Chess board presentations; the 10×10 variant uses International Draughts board presentations. White moves first.

Moving an Amazon
On your turn, move one of your four amazons any distance horizontally, vertically, or diagonally, like a chess queen. The path must be clear, and the destination must be empty.

Firing an Arrow
After moving, fire an arrow from the amazon's new square. The arrow travels in any queen direction through clear squares and permanently blocks the square where it lands. The amazon may not fire through another amazon or arrow.

Winning
The player who can make the last legal move wins. If you cannot move any amazon and fire an arrow, you lose.
            """.trimIndent()
            "GO" -> """
GO 围棋 — Rules

Overview
Go is played on the 13×13 intersections of the wooden board. Black moves first. Players place one stone at a time and surround territory while trying to capture opposing groups.

─────────────────────────

Placing Stones
Tap an empty intersection to place your stone. A connected group shares liberties with its orthogonally adjacent stones. When a group has no liberties, all of its stones are captured and removed.

Suicide and Ko
You may not place a stone where your own group would have no liberties, unless the move captures an opposing group. Positional superko prevents a move from recreating any earlier board position.

Passing and Winning
Tap Pass when you want to pass despite having a legal placement. If no legal placement remains, that turn passes automatically after a short notice. Two consecutive passes end the game. The winner is decided by Chinese area scoring: stones on the board plus surrounded territory, with 6.5 komi added to White's score.
            """.trimIndent()
            "XIANGQI" -> """
XIANGQI 象棋 — Rules

Overview
Xiangqi is played on a 9×10 board. Red moves first. The pieces sit on the intersections of the lines, and the river divides the two armies.

─────────────────────────

Pieces
帥 / 將 — the General moves one point inside the palace. The two Generals may not face each other along an open file.
仕 / 士 — the Advisor moves one point diagonally inside the palace.
相 / 象 — the Elephant moves two points diagonally and may not cross the river. Its eye must be clear.
傌 / 馬 — the Horse moves in an L shape. Its leg must be clear.
俥 / 車 — the Chariot moves any distance horizontally or vertically.
炮 / 砲 — the Cannon moves like a Chariot, but captures by jumping over exactly one piece.
兵 / 卒 — the Soldier moves forward; after crossing the river it may also move sideways.

─────────────────────────

Winning
Checkmate the opposing General by leaving it in check with no legal move. Stalemate is also a loss. A move that leaves your own General in check is illegal.

Repetition and Perpetual Play
This version uses a fixed platform ruleset. Repeating the same position, with the same side to move, three times is a draw. If one side gives check on every move in that repeated cycle, that side loses. Repeatedly attacking the same opposing piece while it moves away after each attack is treated the same way.
            """.trimIndent()
            "SHOGI" -> """
SHOGI 将棋 — Rules

Overview
Shogi is played on a 9×9 board. Black is Sente and moves first; White is Gote. Captured pieces stay in the capturing player's hand and can be dropped back onto the board.

─────────────────────────

Pieces
歩 Pawn — moves one point forward.
香 Lance — moves any distance forward.
桂 Knight — jumps two points forward and one to either side.
銀 Silver — moves one point forward or diagonally.
金 Gold — moves one point forward, sideways, backward, or diagonally forward.
角 Bishop — slides diagonally. Promoted bishops also move one point orthogonally.
飛 Rook — slides horizontally or vertically. Promoted rooks also move one point diagonally.
玉 King — moves one point in any direction.

─────────────────────────

Promotion
Pieces may promote when they move into, within, or out of the three-rank promotion zone. Promotion is optional except when a Pawn or Lance reaches the last rank, or a Knight reaches either of the last two ranks. A promoted pawn, lance, knight, or silver moves like a Gold; a promoted bishop becomes a Horse (馬), and a promoted rook becomes a Dragon (龍).

Drops
Captured pieces are reusable. A drop is always unpromoted. A Pawn or Lance may not be dropped on the last rank, and a Knight may not be dropped on either of the last two ranks. A Pawn may not be dropped on a file containing your unpromoted Pawn (二歩), and a Pawn drop may not give immediate checkmate.

Winning
Checkmate wins the game. A player with no legal move loses, even if their King is not in check.
            """.trimIndent()
            "OTHELLO" -> """
OTHELLO — Rules

Overview
Othello (Reversi) is played on an 8×8 board. Each player has discs that are white on one side and black on the other. You choose your colour when starting a game vs CPU.

─────────────────────────

Placing a Disc
On your turn, place a disc on any empty square that traps one or more of your opponent's discs between your new disc and another of your own.

─────────────────────────

Flipping
All opponent discs trapped in a straight line (horizontal, vertical, or diagonal) between your new disc and another of your own are flipped to your colour.

─────────────────────────

Passing
If you have no legal move, your turn is skipped. If neither player can move, the game ends.

─────────────────────────

Winning
When the board is full (or no legal moves remain), the player with more discs wins. If counts are equal, it's a draw.
            """.trimIndent()

            "CHECKERS" -> """
DRAUGHTS — Rules

Overview
Played on the dark squares of an 8×8 board. You choose your colour when starting a game vs CPU. Pieces start on the first 3 rows of each side.

─────────────────────────

Moving
Pieces move diagonally forward one square at a time to an empty dark square.

─────────────────────────

Jumping
If an opponent's piece is diagonally adjacent and the square beyond it is empty, you must jump over it — capturing it. Multiple jumps in one turn are mandatory if available.

─────────────────────────

Kinging
When a piece reaches the far end of the board it becomes a King. Kings may move and jump diagonally in any direction.

─────────────────────────

Winning
Capture all of your opponent's pieces, or leave them with no legal moves.
            """.trimIndent()

            "INTERNATIONAL_DRAUGHTS" -> """
INTERNATIONAL DRAUGHTS — Rules

Overview
Played on the dark squares of a 10×10 board with twenty pieces per side. You choose your colour when starting a game vs CPU. White moves first.

Moving
Men move one square diagonally forward to an empty dark square. Men may capture forwards or backwards.

Capturing
Captures are compulsory. A capture sequence must take the greatest possible number of pieces. If two sequences take the same number, the one taking more kings is required.

Flying Kings
When a man reaches the far end it becomes a King. Kings move any distance diagonally and can capture a piece from any distance, landing on any empty square beyond it. A multi-capture continues after promotion.

Winning
Capture all of your opponent's pieces, or leave them with no legal moves.
            """.trimIndent()

            "FOX_AND_GEESE" -> """
FOX AND GEESE — Rules

Overview
An asymmetric hunt game on a 33-point cross board. One player controls a fox; the other controls thirteen geese. The geese move first, and work together to trap the more mobile fox.

─────────────────────────

The Fox
The fox moves to any adjacent connected point horizontally, vertically, or diagonally. It may jump over an adjacent goose into an empty point to capture it. If another jump is available from the landing point, the fox may continue jumping in the same turn.

─────────────────────────

The Geese
The geese move one adjacent connected point in any direction, including forwards, backwards, sideways, or diagonally where the board has a line. They do not capture, so their strength comes from surrounding and blocking the fox.

─────────────────────────

Winning
The fox wins by capturing all but one of the geese, leaving the flock with no legal move, or capturing all the geese. A single goose can never trap the fox, so the game ends immediately when only one remains. The geese win by trapping the fox so it has no legal move.
            """.trimIndent()

            else -> """
CHESS — Rules

Overview
Two players command 16 pieces each (White and Black) on an 8×8 board. You choose your colour when starting a game vs CPU.

─────────────────────────

Pieces & How They Move
♙ Pawn — forward one square; captures diagonally. On first move it may advance two squares. En passant applies.
♖ Rook — any number of squares horizontally or vertically.
♘ Knight — L-shaped: 2 squares in one direction then 1 sideways. Jumps over pieces.
♗ Bishop — any number of squares diagonally.
♕ Queen — combines Rook + Bishop.
♔ King — one square in any direction.

─────────────────────────

Castling
A special King-side or Queen-side move: the King slides 2 squares toward a Rook, and that Rook jumps to the other side of the King. Neither piece may have moved before, and the squares between them must be clear and not under attack.

─────────────────────────

Check & Checkmate
If your King is under attack you are in check — you must escape immediately. If you cannot escape, it is checkmate and you lose.

─────────────────────────

Promotion
A Pawn reaching the far rank is promoted — usually to a Queen.

─────────────────────────

Winning
Checkmate your opponent's King.
            """.trimIndent()
        }

        if (isStyledBoardGame()) {
            showChessRulesDialog(
                gameName = gameName,
                rulesText = rulesText,
                showModeAfter = showModeAfter,
            )
            return
        }

        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = rulesText
        }
        val sv = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle("How to Play $gameName")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun showChessRulesDialog(gameName: String, rulesText: String, showModeAfter: Boolean) {
        val view = ChessRulesView(this, gameName, rulesText, styledGameLabel())
        view.onDone = {
            dismissStyledOverlay()
            if (showModeAfter) showModeDialog()
            else showChessBoardAfterDialog()
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = {
                if (showModeAfter) showModeDialog()
                else showChessBoardAfterDialog()
            },
        )
    }

    private fun showStyledOverlay(
        view: View,
        fullScreen: Boolean,
        onCancel: () -> Unit,
        dimBackground: Boolean = !fullScreen,
    ) {
        dismissStyledOverlay()

        val overlay = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            if (dimBackground) {
                setBackgroundColor(Color.argb(184, 0, 0, 0))
            }
            setOnTouchListener { _, event ->
                if (!fullScreen && event.actionMasked == MotionEvent.ACTION_UP) {
                    dismissStyledOverlay(invokeCancel = true)
                }
                true
            }
        }
        val viewParams = if (fullScreen) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        } else {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }
        overlay.addView(view, viewParams)
        overlay.tag = onCancel
        styledOverlayHost.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        styledOverlayHost.visibility = View.VISIBLE
        activeStyledOverlay = overlay
        overlay.requestFocus()
    }

    private var activeStyledOverlay: View? = null

    private fun dismissStyledOverlay(invokeCancel: Boolean = false) {
        val overlay = activeStyledOverlay ?: return
        val onCancel = overlay.tag as? (() -> Unit)
        styledOverlayHost.removeView(overlay)
        activeStyledOverlay = null
        styledOverlayHost.visibility = View.GONE
        if (invokeCancel) onCancel?.invoke()
    }

    private fun showChessBoardAfterDialog() {
        showChessBoardAfterDialog(resumeAi = true)
    }

    private fun showChessBoardAfterDialog(resumeAi: Boolean) {
        gameContainer.visibility = View.VISIBLE
        dismissStyledOverlay()
        chessGameOverView.visibility = View.GONE
        if (matchStarted && gameState.status == GameStatus.IN_PROGRESS) {
            MusicPlayer.resumeMatch(this)
        }
        boardView.isLocked = gameState.status != GameStatus.IN_PROGRESS
        if (resumeAi) resumeComputerTurnIfNeeded()
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        showChessBoardAfterDialog(resumeAi = false)
        matchStarted = true
        SettingsManager.recordRecentlyPlayed(this, gameType)
        MusicPlayer.enterMatch(this)
        resultRecorded = false
        interstitialAd = null
        autoPassJob?.cancel()
        autoPassJob = null
        autoplayEnabled = false
        autoplayMoveInProgress = false
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
            autoplayButton.visibility =
                if (autoplayAllowed && gameType != "LUDO" && vsAI) View.VISIBLE else View.GONE
        }
        redoGameStates.clear(); redoCaptures.clear(); redoMoves.clear(); redoCapSnaps.clear()
        undosRemaining = SettingsManager.undoCredits(this, gameType, vsAI)
        AdManager.loadInterstitial(this) { interstitialAd = it }
        AdManager.loadRewarded(this)
        moveHistory.clear(); capturedByWhite.clear(); capturedByBlack.clear(); captureSnapshots.clear()
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, gameType.lowercase())
        if (vsAI) SettingsManager.setActiveGame(this, gameType.lowercase())
        restoring?.boardStyle?.let {
            applyBoardStyleIndex(it)
        }

        gameState = engine.initialState()
        boardView.ruleEngine           = engine
        boardView.showMustCaptureHints =
            gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS"
        boardView.directMoveMode       = (gameType == "OTHELLO" || gameType == "GO")
        boardView.gameState            = gameState
        boardView.isFlipped            = false
        boardView.isLocked             = false
        boardView.rotateBlackPieces    =
            gameType == "AMAZONS" || (!vsAI && gameType == "CHESS")
        boardView.onMoveMade           = { move -> handleMove(move) }
        boardView.onPromotionChoice   = ::showPromotionChoice
        // Tap after game ends → re-show result dialog without double-recording stats
        boardView.onGameOverTapped     = { showResultDialog() }
        boardView.refreshTheme()
        hudView.setGoMode(gameType == "GO")
        hudView.setGameOver(false)
        hideGoNotice()
        chessGameOverView.visibility = View.GONE

        topCaptureView.setLabel(
            when {
                gameType == "FOX_AND_GEESE" -> ""
                gameType == "SHOGI" -> "Gote hand"
                !vsAI && gameType == "CHESS" -> "Black's captures"
                else -> "Black ⚔"
            }
        )
        bottomCaptureView.setLabel(
            when {
                gameType == "FOX_AND_GEESE" -> "Geese captured"
                gameType == "SHOGI" -> "Sente hand"
                !vsAI && gameType == "CHESS" -> "White's captures"
                else -> "White ⚔"
            }
        )

        topCaptureView.setSelectable(false)
        bottomCaptureView.setSelectable(false)
        refreshCaptureViews()
        if (gameType == "CHESS" || gameType == "CHECKERS" ||
            gameType == "INTERNATIONAL_DRAUGHTS" || gameType == "OTHELLO" ||
            gameType == "FOX_AND_GEESE" || gameType == "AMAZONS"
        ) SoundPlayer.play("game_start")
        updateHud()
        if (restoring != null) {
            restoreMoves(restoring.moves)
            clearPausedMatch()
            if (!scheduleGoAutoPassIfNeeded() && aiControlsCurrentTurn()) triggerAI()
        } else if (!scheduleGoAutoPassIfNeeded() &&
            aiControlsCurrentTurn()
        ) {
            triggerAI()
        }
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, gameType) ?: run {
            showModeDialog()
            return
        }
        if (gameType == "AMAZONS") {
            amazonsBoardSize = paused.boardSize?.takeIf { it == 8 || it == 10 } ?: 8
            engine = AmazonsRuleEngine(amazonsBoardSize)
            boardView.ruleEngine = engine
            boardView.gameState = engine.initialState()
        }
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }
            .getOrDefault(PieceColor.WHITE)
        startGame(paused)
    }

    private fun restoreMoves(moves: List<Move>) {
        for (move in moves) {
            val previous = gameState
            val moverColor = previous.get(move.from)?.color ?: previous.currentTurn
            for (capture in move.captures) {
                val captured = previous.get(capture) ?: continue
                if (moverColor == PieceColor.WHITE) capturedByWhite.add(captured)
                else capturedByBlack.add(captured)
            }
            captureSnapshots.add(capturedByWhite.toList() to capturedByBlack.toList())
            moveHistory.add(previous)
            gameState = engine.applyMove(gameState, move)
        }
        boardView.gameState = gameState
        boardView.isLocked = false
        refreshCaptureViews()
        updateHud()
    }

    private fun refreshCaptureViews() {
        if (gameType == "GO") {
            val goEngine = engine as? GoRuleEngine ?: return
            val scores = goEngine.scoreSummary(gameState)
            topCaptureView.setSummary(
                player = "Black",
                playerColor = PieceColor.BLACK,
                score = scores.black.total,
                captures = scores.black.captures,
                active = gameState.status == GameStatus.IN_PROGRESS &&
                    gameState.currentTurn == PieceColor.BLACK,
            )
            bottomCaptureView.setSummary(
                player = "White",
                playerColor = PieceColor.WHITE,
                score = scores.white.total,
                captures = scores.white.captures,
                active = gameState.status == GameStatus.IN_PROGRESS &&
                    gameState.currentTurn == PieceColor.WHITE,
            )
            topCaptureView.setSelectable(false)
            bottomCaptureView.setSelectable(false)
            return
        }
        if (gameType == "SHOGI") {
            val goteHand = gameState.hands[PieceColor.WHITE].orEmpty()
            val senteHand = gameState.hands[PieceColor.BLACK].orEmpty()
            topCaptureView.update(goteHand)
            bottomCaptureView.update(senteHand)
            topCaptureView.setSelectable(
                !boardView.isLocked &&
                    gameState.currentTurn == PieceColor.WHITE &&
                    (!vsAI || playerColor == PieceColor.WHITE)
            )
            bottomCaptureView.setSelectable(
                !boardView.isLocked &&
                    gameState.currentTurn == PieceColor.BLACK &&
                    (!vsAI || playerColor == PieceColor.BLACK)
            )
        } else {
            topCaptureView.update(capturedByBlack)
            bottomCaptureView.update(capturedByWhite)
            topCaptureView.setSelectable(false)
            bottomCaptureView.setSelectable(false)
        }
    }

    private fun handleShogiHandTap(piece: Piece) {
        val shogiPiece = piece as? ShogiPiece ?: return
        if (gameType != "SHOGI" || boardView.isLocked ||
            gameState.currentTurn != shogiPiece.color ||
            (vsAI && gameState.currentTurn != playerColor)
        ) return
        boardView.beginShogiDrop(shogiPiece.type)
    }

    private fun showPromotionChoice(choices: List<Move>) {
        val promotionChoices = choices.filter { it.promotionType != null }
        if (promotionChoices.isEmpty()) return
        val details = mapOf(
            "QUEEN" to ("Queen" to "Most powerful piece"),
            "KNIGHT" to ("Knight" to "The only piece that jumps"),
            "ROOK" to ("Rook" to "Controls ranks and files"),
            "BISHOP" to ("Bishop" to "Controls diagonals"),
        )
        val symbols = mapOf(
            "QUEEN" to "♕",
            "KNIGHT" to "♘",
            "ROOK" to "♖",
            "BISHOP" to "♗",
        )
        val accents = mapOf(
            "QUEEN" to "#E3B86A",
            "KNIGHT" to "#8EC7B9",
            "ROOK" to "#A9B6E8",
            "BISHOP" to "#E58A7A",
        )
        val ordered = listOf("QUEEN", "KNIGHT", "ROOK", "BISHOP")
        val available = ordered.mapNotNull { type ->
            val move = promotionChoices.firstOrNull { it.promotionType == type } ?: return@mapNotNull null
            val (label, detail) = details.getValue(type)
            ChessChoiceView.Choice(label, detail, symbols.getValue(type), Color.parseColor(accents.getValue(type))) to move
        }
        val view = ChessChoiceView(
            context = this,
            title = "Choose a promotion",
            subtitle = "Your pawn reached the far rank. Select its new piece.",
            choices = available.map { it.first },
            gameLabel = styledGameLabel(),
            headerSymbol = "●",
        )
        view.onChoiceSelected = { index ->
            dismissStyledOverlay()
            showChessBoardAfterDialog()
            boardView.animateExternalMove(available[index].second)
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun handleMove(move: Move, automaticPass: Boolean = false) {
        if (boardView.isLocked) return
        autoplayMoveInProgress = false
        try {
            val moverColor = gameState.get(move.from)?.color
                ?: gameState.currentTurn
            if (gameType == "GO" && !automaticPass) hideGoNotice()
            for (cap in move.captures) {
                val capPiece = gameState.get(cap) ?: continue
                if (gameType == "OTHELLO" || gameType == "SHOGI") continue
                if (moverColor == PieceColor.WHITE) capturedByWhite.add(capPiece)
                else capturedByBlack.add(capPiece)
            }
            captureSnapshots.add(capturedByWhite.toList() to capturedByBlack.toList())
            redoGameStates.clear(); redoCaptures.clear(); redoMoves.clear(); redoCapSnaps.clear()
            moveHistory.add(gameState)
            gameState = engine.applyMove(gameState, move)
            boardView.gameState = gameState
            // Othello: pop-animate placed disc + all flipped discs
            if (gameType == "OTHELLO") {
                val popSet = (move.captures + listOf(move.to)).toSet()
                if (popSet.isNotEmpty()) boardView.playOthelloPopAnim(popSet)
            }
            refreshCaptureViews()
            updateHud()
            if (gameType == "CHESS") playChessSound(move)
            if (gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS")
                playCheckersSound(move)
            if (gameType == "OTHELLO") playOthelloSound(move)
            if ((gameType == "FOX_AND_GEESE" ||
                    gameType == "XIANGQI" ||
                    gameType == "SHOGI" ||
                    gameType == "AMAZONS" ||
                    gameType == "GO") &&
                move.metadata[GoRuleEngine.PASS_METADATA] != true
            ) SoundPlayer.playMovement("board_piece_move")
            if (gameState.status != GameStatus.IN_PROGRESS) {
                recordResult()
                val ad = interstitialAd
                interstitialAd = null
                AdManager.showInterstitial(this, ad)
                AdManager.loadInterstitial(this) { interstitialAd = it }
                showResultDialog()
                return
            }
            if (gameType == "GO" && automaticPass) {
                if (moverColor != playerColor) {
                    showGoNotice(
                        "Your opponent no longer has available moves, " +
                            "click on Pass to end the game, or continue taking more territory.",
                    )
                } else {
                    showGoNotice("You no longer have available moves. Passing automatically…")
                }
            }
            if (!scheduleGoAutoPassIfNeeded() && aiControlsCurrentTurn()) triggerAI()
        } catch (e: Exception) {
            Toast.makeText(this, "Move error — please try again", Toast.LENGTH_SHORT).show()
            if (moveHistory.isNotEmpty()) {
                gameState = moveHistory.removeLast(); captureSnapshots.removeLastOrNull()
                val (cw, cb) = captureSnapshots.lastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())
                capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
                boardView.gameState = gameState; boardView.isLocked = false
                refreshCaptureViews()
            }
        }
    }

    private fun onPassClicked() {
        if (gameType != "GO" ||
            boardView.isLocked ||
            gameState.status != GameStatus.IN_PROGRESS
        ) return
        handleMove(GoRuleEngine.passMove())
    }

    /**
     * A Go pass is automatic only when there are no legal stone placements.
     * Manual Pass remains available for any position where the player can move.
     */
    private fun scheduleGoAutoPassIfNeeded(): Boolean {
        if (gameType != "GO" || gameState.status != GameStatus.IN_PROGRESS) return false
        val goEngine = engine as? GoRuleEngine ?: return false
        if (goEngine.hasLegalPlacement(gameState)) return false

        autoPassJob?.cancel()
        val passingColor = gameState.currentTurn
        val passingPlayer = if (passingColor == PieceColor.WHITE) "White" else "Black"
        boardView.isLocked = true
        showGoNotice("$passingPlayer has no legal placements.\nPassing automatically…")
        updateHud()
        autoPassJob = scope.launch {
            delay(900L)
            if (!isActive ||
                gameState.status != GameStatus.IN_PROGRESS ||
                gameState.currentTurn != passingColor ||
                goEngine.hasLegalPlacement(gameState)
            ) return@launch
            autoPassJob = null
            boardView.isLocked = false
            handleMove(GoRuleEngine.passMove(), automaticPass = true)
        }
        return true
    }

    /** Record win/loss/draw once per game. Safe to call multiple times. */
    private fun recordResult() {
        if (resultRecorded || !vsAI) return
        resultRecorded = true
        when (gameState.status) {
            GameStatus.WHITE_WINS -> if (playerColor == PieceColor.WHITE) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.BLACK_WINS -> if (playerColor == PieceColor.BLACK) SettingsManager.recordWin(this) else SettingsManager.recordLoss(this)
            GameStatus.DRAW       -> SettingsManager.recordDraw(this)
            else -> {}
        }
    }

    // ─── Chess sounds ─────────────────────────────────────────────────────────

    private fun playChessSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                SoundPlayer.play("move_check")
                boardView.postDelayed({ if (activityResumed) SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        // Primary move sound
        when {
            move.promotionType != null      -> SoundPlayer.playMovement("promote")
            move.metadata["castle"] != null -> SoundPlayer.playMovement("castle")
            move.captures.isNotEmpty()      -> SoundPlayer.playMovement("capture")
            vsAI && gameState.currentTurn == playerColor ->
                SoundPlayer.playMovement("move_opponent")
            else -> SoundPlayer.playMovement("move_self")
        }
        // Check sound plays on top (SoundPool supports simultaneous streams)
        val isCheck = (engine as? com.mkdev.mkboardgames.games.chess.ChessRuleEngine)
            ?.isInCheck(gameState, gameState.currentTurn) == true
        if (isCheck) SoundPlayer.playMovement("move_check")
    }

    // ─── Checkers sounds ──────────────────────────────────────────────────────

    private fun playCheckersSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ if (activityResumed) SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        when {
            move.captures.isNotEmpty() -> SoundPlayer.playMovement("checkers_capture")
            else -> {
                val prevState = moveHistory.lastOrNull()
                val wasKing = prevState?.get(move.from)?.let { (it as? com.mkdev.mkboardgames.games.checkers.CheckersPiece)?.isKing } ?: false
                val isNowKing = (gameState.get(move.to) as? com.mkdev.mkboardgames.games.checkers.CheckersPiece)?.isKing ?: false
                if (!wasKing && isNowKing) SoundPlayer.playMovement("checkers_king")
                else SoundPlayer.playMovement("checkers_move")
            }
        }
    }

    // ─── Othello sounds ───────────────────────────────────────────────────────

    private fun playOthelloSound(move: Move) {
        when (gameState.status) {
            GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ if (activityResumed) SoundPlayer.play("game_end") }, 200)
                return
            }
            GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        SoundPlayer.playMovement("othello_place")
        if (move.captures.isNotEmpty()) {
            boardView.postDelayed({ if (activityResumed) SoundPlayer.playMovement("othello_flip") }, 150)
        }
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && gameType != "LUDO" && (
            gameState.currentTurn != playerColor ||
                (autoplayAllowed && autoplayEnabled)
            )

    private fun canPauseMatch(): Boolean =
        matchStarted &&
            gameState.status == GameStatus.IN_PROGRESS &&
            !aiControlsCurrentTurn() &&
            !boardView.isLocked &&
            !boardView.hasPendingMoveAnimation()

    private fun resumeComputerTurnIfNeeded() {
        if (!activityResumed ||
            !::boardView.isInitialized ||
            !matchStarted ||
            gameState.status != GameStatus.IN_PROGRESS ||
            !aiControlsCurrentTurn()
        ) return
        if (!scheduleGoAutoPassIfNeeded()) triggerAI()
    }

    private fun triggerAI() {
        if (!activityResumed || !matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            boardView.isLocked = false
            hudView.setThinking(false)
            return
        }
        boardView.isLocked = true; hudView.setThinking(true)
        scope.launch {
            if (gameType == "GO") {
                delay(SettingsManager.goAiThinkingDelayMs(this@GameActivity))
            }
            if (!isActive || !activityResumed || gameState.status != GameStatus.IN_PROGRESS) {
                boardView.isLocked = false
                hudView.setThinking(false)
                return@launch
            }
            val thinkingState = gameState
            val autoplayingPlayerTurn =
                gameType != "LUDO" && thinkingState.currentTurn == playerColor
            val move = withContext(Dispatchers.Default) {
                try {
                    val preferredMove = when (gameType) {
                        "XIANGQI" -> {
                            val profile = SettingsManager.xiangqiAiProfileForLevel(
                                SettingsManager.getXiangqiDifficulty(this@GameActivity),
                            )
                            AIPlayer(
                                engine = engine,
                                maxDepth = profile.depth,
                                timeLimitMs = profile.timeLimitMs,
                                quiesceDepth = profile.quiesceDepth,
                                varietyWindowOverride = profile.varietyWindow,
                            ).bestMove(thinkingState)
                        }
                        "SHOGI" -> {
                            val shogiDifficulty =
                                SettingsManager.getShogiDifficulty(this@GameActivity)
                            AIPlayer(
                                engine,
                                maxDepth = SettingsManager.shogiAiDepth(this@GameActivity),
                                timeLimitMs = SettingsManager.shogiAiTimeLimitMs(this@GameActivity),
                                quiesceDepth = 1,
                                // Easy through Hard retain a little move variety;
                                // Master must always choose the strongest completed
                                // search result rather than a near-best alternative.
                                varietyWindowOverride = if (shogiDifficulty >= 3) 0 else -1,
                            ).bestMove(thinkingState)
                        }
                        "OTHELLO"  -> {
                            val profile = SettingsManager.othelloAiProfile(this@GameActivity)
                            AIPlayer(
                                engine = engine,
                                maxDepth = profile.depth,
                                timeLimitMs = profile.timeLimitMs,
                                varietyWindowOverride = profile.varietyWindow,
                            ).bestMove(thinkingState)
                                ?: engine.allLegalMoves(thinkingState, thinkingState.currentTurn).randomOrNull()
                        }
                        "GO" -> {
                            GoAIPlayer(
                                engine = engine as GoRuleEngine,
                                maxIterations = SettingsManager.goAiIterations(this@GameActivity),
                                timeLimitMs = SettingsManager.goAiTimeLimitMs(this@GameActivity),
                                deterministic = SettingsManager.getGoDifficulty(this@GameActivity) == 2,
                            ).bestMove(thinkingState)
                        }
                        "CHECKERS" -> {
                            val ai = AIPlayer(engine, maxDepth = SettingsManager.checkersAiDepth(this@GameActivity))
                            ai.bestMove(thinkingState)
                        }
                        "INTERNATIONAL_DRAUGHTS" -> {
                            val ai = AIPlayer(
                                engine,
                                maxDepth = SettingsManager.internationalDraughtsAiDepth(this@GameActivity)
                            )
                            ai.bestMove(thinkingState)
                        }
                        "FOX_AND_GEESE" -> {
                            val ai = AIPlayer(
                                engine,
                                maxDepth = SettingsManager.foxAndGeeseAiDepth(this@GameActivity),
                                timeLimitMs = SettingsManager.foxAndGeeseAiTimeLimitMs(this@GameActivity),
                                varietyWindowOverride =
                                    SettingsManager.foxAndGeeseAiVarietyWindow(this@GameActivity),
                            )
                            ai.bestMove(thinkingState)
                        }
                        "AMAZONS" -> {
                            val profile = SettingsManager.amazonsAiProfileForLevel(
                                SettingsManager.getAmazonsDifficulty(this@GameActivity),
                            )
                            AIPlayer(
                                engine,
                                maxDepth = profile.depth,
                                timeLimitMs = profile.timeLimitMs,
                            ).bestMove(thinkingState)
                        }
                        "CHESS" -> {
                            val level = SettingsManager.getChessDifficulty(this@GameActivity)
                            if (level == 3) {
                                val stockfishProfile =
                                    SettingsManager.chessStockfishProfileForLevel(level)
                                StockfishPlayer(
                                    context = this@GameActivity,
                                    profile = stockfishProfile,
                                ).bestMove(thinkingState)
                                    ?: run {
                                        val etherealProfile =
                                            SettingsManager.chessEtherealProfileForLevel(level)
                                        EtherealPlayer(
                                            context = this@GameActivity,
                                            profile = etherealProfile,
                                        ).bestMove(thinkingState)
                                    }
                                    ?: run {
                                        val fallback =
                                            SettingsManager.chessAiProfileForLevel(level)
                                        AIPlayer(
                                            engine,
                                            maxDepth = fallback.depth,
                                            timeLimitMs = fallback.timeLimitMs,
                                            quiesceDepth = fallback.quiesceDepth,
                                            varietyWindowOverride = fallback.varietyWindowOverride,
                                        ).bestMove(thinkingState)
                                    }
                            } else if (level == 2) {
                                val profile = SettingsManager.chessEtherealProfileForLevel(level)
                                EtherealPlayer(
                                    context = this@GameActivity,
                                    profile = profile,
                                ).bestMove(thinkingState)
                                    ?: run {
                                        val fallback = SettingsManager.chessAiProfileForLevel(level)
                                        AIPlayer(
                                            engine,
                                            maxDepth = fallback.depth,
                                            timeLimitMs = fallback.timeLimitMs,
                                            quiesceDepth = fallback.quiesceDepth,
                                            varietyWindowOverride = fallback.varietyWindowOverride,
                                        ).bestMove(thinkingState)
                                    }
                            } else {
                                val profile = SettingsManager.chessAiProfileForLevel(level)
                                AIPlayer(
                                    engine,
                                    maxDepth = profile.depth,
                                    timeLimitMs = profile.timeLimitMs,
                                    quiesceDepth = profile.quiesceDepth,
                                    varietyWindowOverride = profile.varietyWindowOverride,
                                ).bestMove(thinkingState)
                            }
                        }
                        else -> {
                            // Chess — deeper search with quiescence and time limit
                            val profile = SettingsManager.chessAiProfileForLevel(
                                SettingsManager.getChessDifficulty(this@GameActivity),
                            )
                            val ai = AIPlayer(
                                engine,
                                maxDepth = profile.depth,
                                timeLimitMs = profile.timeLimitMs,
                                quiesceDepth = profile.quiesceDepth,
                                varietyWindowOverride = profile.varietyWindowOverride,
                            )
                            ai.bestMove(thinkingState)
                        }
                    }
                    // Never leave a turn locked because a bounded search
                    // timed out or returned no move. The rule engine remains
                    // authoritative; this is only a safe recovery move.
                    preferredMove ?: engine
                        .allLegalMoves(thinkingState, thinkingState.currentTurn)
                        .firstOrNull()
                } catch (e: Throwable) { null }
            }
            hudView.setThinking(false)
            val stateIsStillCurrent =
                gameState.currentTurn == thinkingState.currentTurn &&
                    gameState.moveHistory.size == thinkingState.moveHistory.size &&
                    gameState.status == thinkingState.status
            val playerAutoplayStillEnabled =
                !autoplayingPlayerTurn || (autoplayAllowed && autoplayEnabled)
            if (move != null &&
                isActive &&
                activityResumed &&
                stateIsStillCurrent &&
                playerAutoplayStillEnabled
            ) {
                autoplayMoveInProgress = autoplayingPlayerTurn
                boardView.animateExternalMove(move)
            } else {
                boardView.isLocked = false
            }
        }
    }

    // ─── HUD helpers ─────────────────────────────────────────────────────────

    private fun updateHud() {
        val turn = if (gameType == "FOX_AND_GEESE") {
            if (gameState.currentTurn == PieceColor.WHITE) "Fox" else "Geese"
        } else if (gameType == "XIANGQI") {
            if (gameState.currentTurn == PieceColor.WHITE) "Red" else "Black"
        } else if (gameType == "SHOGI") {
            if (gameState.currentTurn == PieceColor.BLACK) "Sente" else "Gote"
        } else {
            if (gameState.currentTurn == PieceColor.WHITE) "White" else "Black"
        }
        val label = when {
            gameState.status != GameStatus.IN_PROGRESS -> "Game over"
            vsAI && gameState.currentTurn == playerColor -> "Your turn"
            vsAI -> "CPU Turn"
            else -> "$turn to move"
        }
        hudView.setInfo(
            label,
            canUndo = moveHistory.isNotEmpty(),
            canRedo = redoGameStates.isNotEmpty(),
            undoCount = undosRemaining,
        )
    }

    private fun showGoNotice(message: String) {
        if (gameType != "GO") return
        goNoticeView.text = message
        goNoticeView.visibility = View.VISIBLE
    }

    private fun hideGoNotice() {
        if (::goNoticeView.isInitialized) {
            goNoticeView.text = ""
            goNoticeView.visibility = View.GONE
        }
    }

    fun onUndoClicked() {
        if (moveHistory.isEmpty() || boardView.isLocked) return
        if (undosRemaining == 0) {
            UndoRewardDialog.show(this, undosRemaining) {
                undosRemaining = SettingsManager.grantUndoCredits(
                    this,
                    gameType,
                    undosRemaining,
                    2,
                    vsAI,
                )
                updateHud()
                undosRemaining
            }
            return
        }
        if (gameType == "GO") hideGoNotice()
        val prevState = gameState
        val prevCap   = capturedByWhite.toList() to capturedByBlack.toList()
        val rMoves    = mutableListOf<GameState>()
        val rSnaps    = mutableListOf<Pair<List<Piece>, List<Piece>>>()
        if (vsAI && moveHistory.size >= 2) rMoves.add(moveHistory.removeLast())
        val restored  = moveHistory.removeLastOrNull() ?: return
        rMoves.add(restored)
        repeat(rMoves.size) { rSnaps.add(captureSnapshots.removeLastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())) }
        gameState = restored
        val (cw, cb) = captureSnapshots.lastOrNull() ?: (emptyList<Piece>() to emptyList<Piece>())
        capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
        redoGameStates.add(prevState); redoCaptures.add(prevCap)
        redoMoves.add(rMoves);         redoCapSnaps.add(rSnaps)
        undosRemaining = SettingsManager.consumeUndoCredit(
            this,
            gameType,
            undosRemaining,
            vsAI,
        )
        refreshCaptureViews()
        boardView.isLocked = false; boardView.gameState = gameState; updateHud()
    }

    fun onRedoClicked() {
        if (redoGameStates.isEmpty() || boardView.isLocked) return
        if (gameType == "GO") hideGoNotice()
        val nextState = redoGameStates.removeLast()
        val nextCap   = redoCaptures.removeLast()
        val rMoves    = redoMoves.removeLast()
        val rSnaps    = redoCapSnaps.removeLast()
        for (i in rMoves.indices.reversed()) {
            captureSnapshots.add(rSnaps[i])
            moveHistory.add(rMoves[i])
        }
        undosRemaining = SettingsManager.refundUndoCredit(
            this,
            gameType,
            undosRemaining,
            vsAI,
        )
        gameState = nextState
        val (cw, cb) = nextCap
        capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
        refreshCaptureViews()
        boardView.isLocked = false; boardView.gameState = gameState; updateHud()
    }

    fun onMenuClicked() {
        val matchActive = matchStarted && gameState.status == GameStatus.IN_PROGRESS
        if (matchActive && !canPauseMatch()) {
            Toast.makeText(this, "Pause is available before your turn starts.", Toast.LENGTH_SHORT).show()
            return
        }
        // The menu is an in-app dialog and does not trigger onPause().
        stopAutoplayAndAiThinking()
        val inProgress = matchActive && moveHistory.isNotEmpty()
        if (matchActive) MusicPlayer.enterPausedMatch(this)
        if (isStyledBoardGame()) {
            showChessGameplayMenu(inProgress)
            return
        }
        val items = mutableListOf("New Game", "How to Play")
        // CPU games expose their difficulty because their search depth can be
        // tuned safely.
        if (vsAI) items.add("CPU Difficulty")
        items.add("Main Menu")
        val arr = items.toTypedArray()
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(arr) { _, which ->
                when (arr[which]) {
                    "New Game" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Forfeit Match?")
                            .setMessage("Starting a new game counts as a forfeit.")
                            .setPositiveButton("Forfeit & New Game") { _, _ ->
                                showModeDialog()
                            }.setNegativeButton("Cancel", null).show()
                    } else showModeDialog()
                    "How to Play"   -> showRules(showModeAfter = false)
                    "CPU Difficulty" -> showDifficultyDialog()
                    "Main Menu" -> if (inProgress) {
                        if (isStyledBoardGame()) {
                            showChessLeaveMatchDialog()
                        } else {
                            AlertDialog.Builder(this).setTitle("Leave Match?")
                                .setMessage("Pause to resume later, or leave to forfeit.")
                                .setPositiveButton("Pause & Exit") { _, _ -> pauseMatchAndExit() }
                                .setNeutralButton("Leave Match") { _, _ ->
                                    clearPausedMatch()
                                    finish()
                                }.setNegativeButton("Cancel", null).show()
                        }
                } else {
                    clearPausedMatch()
                    finish()
                }
                }
            }.show()
    }

    private fun showChessGameplayMenu(inProgress: Boolean) {
        val choices = mutableListOf(
            ChessChoiceView.Choice(
                "New Game",
                if (inProgress) "Start over and forfeit" else "Begin a fresh match",
                "↻",
                Color.parseColor("#E3B86A"),
            ),
            ChessChoiceView.Choice(
                "How to Play",
                "Review the essentials",
                "?",
                Color.parseColor("#A9B6E8"),
            ),
        )
        val actions = mutableListOf<() -> Unit>(
            { if (inProgress) showChessForfeitDialog() else showModeDialog() },
            { showRules(showModeAfter = false) },
        )
        if (vsAI) {
            choices += ChessChoiceView.Choice(
                "CPU Difficulty",
                "Adjust the challenge",
                when {
                    gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS" -> "◉"
                    gameType == "FOX_AND_GEESE" -> "🦊"
                    else -> "♞"
                },
                Color.parseColor("#8EC7B9"),
            )
            actions += { showDifficultyDialog() }
        }
        choices += ChessChoiceView.Choice(
            "Main Menu",
            if (inProgress) "Leave this match" else "Choose another game",
            "⌂",
            Color.parseColor("#E58A7A"),
        )
        actions += {
            if (inProgress) {
                showChessLeaveMatchDialog()
            } else {
                clearPausedMatch()
                finish()
            }
        }

        val view = ChessChoiceView(
            this,
            title = "Menu",
            subtitle = "Choose what to do next.",
            choices = choices,
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            actions.getOrNull(which)?.invoke()
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun showChessForfeitDialog() {
        val view = ChessChoiceView(
            this,
            title = "Forfeit Match?",
            subtitle = "Starting a new game counts as a forfeit.",
            choices = listOf(
                ChessChoiceView.Choice(
                    "Cancel",
                    "Keep playing this match",
                    "↩",
                    Color.parseColor("#A9B6E8"),
                ),
                ChessChoiceView.Choice(
                    "Forfeit & New Game",
                    "Start a fresh match",
                    "↻",
                    Color.parseColor("#E58A7A"),
                ),
            ),
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            dismissStyledOverlay()
            if (which == 1) {
                showModeDialog()
            } else {
                showChessBoardAfterDialog()
            }
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun showDifficultyDialog() {
        val getDiff: () -> Int
        val setDiff: (Int) -> Unit
        when (gameType) {
            "GO" -> {
                getDiff = { SettingsManager.getGoDifficulty(this) }
                setDiff = { v -> SettingsManager.setGoDifficulty(this, v) }
            }
            "CHECKERS" -> {
                getDiff = { SettingsManager.getCheckersDifficulty(this) }
                setDiff = { v -> SettingsManager.setCheckersDifficulty(this, v) }
            }
            "INTERNATIONAL_DRAUGHTS" -> {
                getDiff = { SettingsManager.getInternationalDraughtsDifficulty(this) }
                setDiff = { v -> SettingsManager.setInternationalDraughtsDifficulty(this, v) }
            }
            "FOX_AND_GEESE" -> {
                getDiff = { SettingsManager.getFoxAndGeeseDifficulty(this) }
                setDiff = { v -> SettingsManager.setFoxAndGeeseDifficulty(this, v) }
            }
            "SHOGI" -> {
                getDiff = { SettingsManager.getShogiDifficulty(this) }
                setDiff = { v -> SettingsManager.setShogiDifficulty(this, v) }
            }
            "AMAZONS" -> {
                getDiff = { SettingsManager.getAmazonsDifficulty(this) }
                setDiff = { v -> SettingsManager.setAmazonsDifficulty(this, v) }
            }
            "OTHELLO" -> {
                getDiff = { SettingsManager.getOthelloDifficulty(this) }
                setDiff = { v -> SettingsManager.setOthelloDifficulty(this, v) }
            }
            "XIANGQI" -> {
                getDiff = { SettingsManager.getXiangqiDifficulty(this) }
                setDiff = { v -> SettingsManager.setXiangqiDifficulty(this, v) }
            }
            else       -> { getDiff = { SettingsManager.getChessDifficulty(this) };    setDiff = { v -> SettingsManager.setChessDifficulty(this, v) } }
        }
        val current = getDiff()
        showStyledDifficultyDialog(current, setDiff)
    }

    private fun showStyledDifficultyDialog(current: Int, setDiff: (Int) -> Unit) {
        val labels = if (gameType == "CHESS") {
            listOf("Easy", "Medium", "Hard", "Master")
        } else {
            listOf("Easy", "Medium", "Hard")
        }
        val levels = com.mkdev.mkboardgames.ui.DifficultyChoices.create(
            context = this,
            gameTag = gameType.lowercase(),
            current = current,
            labels = labels,
        )
        val view = ChessChoiceView(
            this,
            title = "CPU Difficulty",
            subtitle = "Win 2 consecutive games at each level to unlock the next.",
            choices = levels,
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            val changed = which != current
            if (changed) {
                setDiff(which)
                if (gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    dismissStyledOverlay()
                    showChessRestartDialog()
                } else {
                    // Before the first move, keep the difficulty picker open so
                    // the player can choose and verify a setting without being
                    // sent back to the board after every tap.
                    showStyledDifficultyDialog(which, setDiff)
                }
            }
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun styledGameLabel(): String = when (gameType) {
        "AMAZONS" -> "A M A Z O N S"
        "CHECKERS" -> "D R A U G H T S"
        "INTERNATIONAL_DRAUGHTS" -> "I N T L  D R A U G H T S"
        "OTHELLO" -> "O T H E L L O"
        "FOX_AND_GEESE" -> "F O X  &  G E E S E"
        "GO" -> "G O"
        "SHOGI" -> "S H O G I"
        "XIANGQI" -> "X I A N G Q I"
        else -> "C H E S S"
    }

    private fun showChessRestartDialog() {
        val view = ChessChoiceView(
            this,
            title = "Restart Match?",
            subtitle = "Difficulty changed. Restart now?",
            choices = listOf(
                ChessChoiceView.Choice(
                    "Keep Playing",
                    "Continue this match",
                    "↩",
                    Color.parseColor("#A9B6E8"),
                ),
                ChessChoiceView.Choice(
                    "Restart",
                    "Start with the new difficulty",
                    "↻",
                    Color.parseColor("#E3B86A"),
                ),
            ),
            gameLabel = styledGameLabel(),
        )
        view.onChoiceSelected = { which ->
            dismissStyledOverlay()
            if (which == 1) startGame() else showChessBoardAfterDialog()
        }
        showStyledOverlay(
            view = view,
            fullScreen = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    // ─── Result dialog ────────────────────────────────────────────────────────

    private fun showResultDialog() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val outcome = when (gameState.status) {
            GameStatus.WHITE_WINS ->
                when {
                    gameType == "FOX_AND_GEESE" && vsAI && playerColor == PieceColor.WHITE -> "You win!"
                    gameType == "FOX_AND_GEESE" -> "The fox wins!"
                    gameType == "XIANGQI" && vsAI && playerColor == PieceColor.WHITE -> "You win!"
                    gameType == "XIANGQI" -> "Red wins!"
                    gameType == "SHOGI" && vsAI && playerColor == PieceColor.WHITE -> "You win!"
                    gameType == "SHOGI" -> "Gote wins!"
                    vsAI && playerColor == PieceColor.WHITE -> "You win! 🎉"
                    else -> "White wins!"
                }
            GameStatus.BLACK_WINS ->
                when {
                    gameType == "FOX_AND_GEESE" && vsAI && playerColor == PieceColor.BLACK -> "You win!"
                    gameType == "FOX_AND_GEESE" -> "The geese win!"
                    gameType == "XIANGQI" && vsAI && playerColor == PieceColor.BLACK -> "You win!"
                    gameType == "XIANGQI" -> "Black wins!"
                    gameType == "SHOGI" && vsAI && playerColor == PieceColor.BLACK -> "You win!"
                    gameType == "SHOGI" -> "Sente wins!"
                    vsAI && playerColor == PieceColor.BLACK -> "You win! 🎉"
                    else -> "Black wins!"
                }
            GameStatus.DRAW -> "Draw! Well played."
            else -> ""
        }
        val msg = if (gameType == "GO") {
            buildGoResultMessage(outcome)
        } else {
            outcome
        }
        val resultLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> when (gameType) {
                "FOX_AND_GEESE" -> "Fox wins"
                "XIANGQI" -> "Red wins"
                "SHOGI" -> "Gote wins"
                else -> "White wins"
            }
            GameStatus.BLACK_WINS -> when (gameType) {
                "FOX_AND_GEESE" -> "Geese win"
                "XIANGQI" -> "Black wins"
                "SHOGI" -> "Sente wins"
                else -> "Black wins"
            }
            GameStatus.DRAW       -> "Draw"
            else -> ""
        }
        if (gameType != "LUDO" && gameType != "ONITAMA") {
            showBoardGameResultDialog()
            return
        }
        if (isStyledBoardGame()) {
            showStyledResultDialog(msg, resultLabel)
            return
        }

        val builder = AlertDialog.Builder(this).setTitle("Game Over").setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> startGame() }
            .setNegativeButton("Main Menu")  { _, _ -> finish() }
            .setCancelable(true)

        if (gameType != "OTHELLO") {
            builder.setNeutralButton("Watch Replay") { _, _ -> launchReplay(resultLabel) }
        }
        builder.show()
    }

    private fun showBoardGameResultDialog() {
        hudView.setInfo("Game Over", canUndo = false, canRedo = false)
        hudView.setGameOver(true)
        chessGameOverView.winnerLabel = boardGameWinnerLabel()
        gameContainer.visibility = View.VISIBLE
        styledOverlayHost.visibility = View.GONE
        activeStyledOverlay = null
        chessGameOverView.visibility = View.VISIBLE
        chessGameOverView.bringToFront()
    }

    private fun boardGameWinnerLabel(): String {
        val winner = when (gameState.status) {
            GameStatus.WHITE_WINS -> PieceColor.WHITE
            GameStatus.BLACK_WINS -> PieceColor.BLACK
            else -> null
        }
        val label = when {
            winner == null -> "Draw"
            vsAI && winner == playerColor -> "You"
            vsAI -> "CPU"
            winner == PieceColor.WHITE -> "White"
            else -> "Black"
        }
        return "Winner: $label"
    }

    private fun currentResultLabel(): String = when (gameState.status) {
        GameStatus.WHITE_WINS -> "White wins"
        GameStatus.BLACK_WINS -> "Black wins"
        GameStatus.DRAW -> "Draw"
        else -> ""
    }

    private fun showStyledResultDialog(message: String, resultLabel: String) {
        val choices = mutableListOf(
            ChessChoiceView.Choice(
                "Play Again",
                "Start a fresh game",
                "↻",
                Color.parseColor("#E3B86A"),
            ),
            ChessChoiceView.Choice(
                "Main Menu",
                "Choose another match",
                "⌂",
                Color.parseColor("#E58A7A"),
            ),
        )
        if (gameType != "OTHELLO") {
            choices += ChessChoiceView.Choice(
                "Watch Replay",
                "Review the moves",
                "▶",
                Color.parseColor("#A9B6E8"),
            )
        }
        val view = ChessChoiceView(
            this,
            title = "Game Over",
            subtitle = message,
            choices = choices,
            gameLabel = styledGameLabel(),
            fullScreenOverride = false,
        )
        view.onChoiceSelected = { which ->
            dismissStyledOverlay()
            when (which) {
                0 -> startGame()
                1 -> finish()
                2 -> launchReplay(resultLabel)
            }
        }
        showStyledOverlay(
            view = view,
            fullScreen = false,
            dimBackground = true,
            onCancel = { showChessBoardAfterDialog() },
        )
    }

    private fun buildGoResultMessage(outcome: String): String {
        val scores = (engine as? GoRuleEngine)?.scoreSummary(gameState) ?: return outcome
        fun formatScore(score: Double): String =
            if (score == score.toInt().toDouble()) score.toInt().toString()
            else String.format(java.util.Locale.US, "%.1f", score)

        fun playerDetails(name: String, score: com.mkdev.mkboardgames.games.go.GoScoreBreakdown): String =
            "$name\n" +
                "Territory  ${score.territory}\n" +
                "Stones     ${score.stones}\n" +
                "Captures   ${score.captures}\n" +
                "Komi       ${formatScore(score.komi)}\n" +
                "Final score ${formatScore(score.total)}"

        return "$outcome\n\n${playerDetails("Black", scores.black)}\n\n${playerDetails("White", scores.white)}"
    }

    private fun launchReplay(resultLabel: String, lockBoardStyle: Boolean = false) {
        showChessBoardAfterDialog()
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  gameType)
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
            putExtra(ReplayActivity.EXTRA_BOARD_STYLE_INDEX, currentBoardStyleIndex())
            if (gameType == "AMAZONS") {
                putExtra(ReplayActivity.EXTRA_AMAZONS_BOARD_SIZE, amazonsBoardSize)
            }
            if (lockBoardStyle) {
                putExtra(ReplayActivity.EXTRA_LOCK_BOARD_STYLE, true)
            }
        })
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class HudView(ctx: Context) : View(ctx) {
        private var title    = "White to move"
        private var canUndo  = false
        private var undoCount = 0
        private var canRedo  = false
        private var thinking = false
        private var goMode   = false
        private var detail   = ""

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity

        private val bgPaint    = Paint().apply { color = Color.parseColor("#102C32") }
        private val divPaint   = Paint().apply { color = Color.parseColor("#203E42") }
        private val txtPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.LEFT; isFakeBoldText = true
            textSize = 15f * sp.coerceAtMost(3f)
        }
        private val subPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BFD0C6"); textAlign = Paint.Align.LEFT
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#34261B") }
        private val btnEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D3A05F")
            style = Paint.Style.STROKE
            strokeWidth = dp
        }
        private val disabledBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#211F1B")
        }
        private val disabledBtnEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#665A4A")
            style = Paint.Style.STROKE
            strokeWidth = dp
        }
        private val btnPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7D99B"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val dimPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7D776C"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private val backRect = RectF()
        private val passRect = RectF()
        private val undoRect = RectF()
        private val redoRect = RectF()
        private val menuRect = RectF()
        private var gameOver = false

        fun setInfo(
            label: String,
            canUndo: Boolean,
            canRedo: Boolean,
            detail: String = "",
            undoCount: Int = 0,
        ) {
            title = label
            this.canUndo = canUndo
            this.undoCount = undoCount.coerceAtLeast(0)
            this.canRedo = canRedo
            this.detail = detail
            invalidate()
        }
        fun setGameOver(value: Boolean) {
            gameOver = value
            invalidate()
        }
        fun setThinking(t: Boolean) {
            thinking = t
            if (t) detail = ""
            invalidate()
        }
        fun setGoMode(enabled: Boolean) {
            goMode = enabled
            if (width > 0 && height > 0) onSizeChanged(width, height, width, height)
            invalidate()
        }
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 50f * dp; val bh = 28f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp,        by, 6f * dp + bw,   by + bh)
            if (goMode) {
                passRect.set(w - bw * 4.4f, by, w - bw * 3.35f, by + bh)
            } else {
                passRect.set(0f, 0f, 0f, 0f)
            }
            undoRect.set(w - bw * 3.3f,  by, w - bw * 2.2f,  by + bh)
            redoRect.set(w - bw * 2.15f, by, w - bw * 1.1f,  by + bh)
            menuRect.set(w - bw * 1.05f, by, w - 4f * dp,    by + bh)
        }

        @Suppress("DEPRECATION")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (gameOver) return true
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); this@GameActivity.onBackPressed() }
                    goMode && passRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onPassClicked() }
                    undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndoClicked() }
                    redoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onRedoClicked() }
                    menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenuClicked() }
                    else -> Unit
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            canvas.drawRect(0f, h - dp, w, h, divPaint)

            val rr = 5f * dp
            val backgroundPaint = if (gameOver) disabledBtnBgPaint else btnBgPaint
            val edgePaint = if (gameOver) disabledBtnEdgePaint else btnEdgePaint
            val actionPaint = if (gameOver) dimPaint else btnPaint
            canvas.drawRoundRect(backRect, rr, rr, backgroundPaint)
            if (goMode) canvas.drawRoundRect(passRect, rr, rr, backgroundPaint)
            canvas.drawRoundRect(undoRect, rr, rr, backgroundPaint)
            canvas.drawRoundRect(redoRect, rr, rr, backgroundPaint)
            canvas.drawRoundRect(menuRect, rr, rr, backgroundPaint)
            canvas.drawRoundRect(backRect, rr, rr, edgePaint)
            if (goMode) canvas.drawRoundRect(passRect, rr, rr, edgePaint)
            canvas.drawRoundRect(undoRect, rr, rr, edgePaint)
            canvas.drawRoundRect(redoRect, rr, rr, edgePaint)
            canvas.drawRoundRect(menuRect, rr, rr, edgePaint)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnPaint.textSize * 0.36f, actionPaint)
            if (goMode) canvas.drawText("Pass", passRect.centerX(), passRect.centerY() + btnPaint.textSize * 0.36f, actionPaint)
            canvas.drawText(
                if (undoCount == Int.MAX_VALUE) "Undo(∞)"
                else if (undoCount > 0) "Undo($undoCount)"
                else "Undo",
                undoRect.centerX(),
                undoRect.centerY() + btnPaint.textSize * 0.36f,
                if (gameOver) dimPaint else if (canUndo) btnPaint else dimPaint)
            canvas.drawText("Redo", redoRect.centerX(), redoRect.centerY() + btnPaint.textSize * 0.36f,
                if (gameOver) dimPaint else if (canRedo) btnPaint else dimPaint)
            canvas.drawText("Menu", menuRect.centerX(), menuRect.centerY() + btnPaint.textSize * 0.36f, actionPaint)

            val cx = (backRect.right + if (goMode) passRect.left else undoRect.left) / 2f
            val sub = if (thinking) "Thinking…" else detail
            canvas.drawText(title, cx, h / 2f - txtPaint.textSize * 0.15f, txtPaint.also { it.textAlign = Paint.Align.CENTER })
            if (sub.isNotEmpty()) canvas.drawText(sub, cx, h / 2f + subPaint.textSize * 1.1f, subPaint.also { it.textAlign = Paint.Align.CENTER })
            txtPaint.textAlign = Paint.Align.LEFT
            subPaint.textAlign = Paint.Align.LEFT
        }
    }
}
