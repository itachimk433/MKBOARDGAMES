package com.mkdev.mkboardgames

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.checkers.CheckersRuleEngine
import com.mkdev.mkboardgames.games.checkers.InternationalDraughtsRuleEngine
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseRuleEngine
import com.mkdev.mkboardgames.games.go.GoRuleEngine
import com.mkdev.mkboardgames.games.go.GoAIPlayer
import com.mkdev.mkboardgames.games.othello.OthelloRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import com.mkdev.mkboardgames.games.shogi.ShogiRuleEngine
import com.mkdev.mkboardgames.games.xiangqi.XiangqiRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.BoardStyleSwitchView
import com.mkdev.mkboardgames.ui.CaptureStripView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.ChessRulesView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.DraughtsBoardStyle
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "game_type"
    }

    private lateinit var boardView:        BoardView
    private lateinit var boardStyleSwitch: BoardStyleSwitchView
    private lateinit var autoplayButton:   AutoplayButtonView
    private lateinit var hudView:          HudView
    private lateinit var topCaptureView:   CaptureStripView
    private lateinit var goNoticeView:    android.widget.TextView
    private lateinit var bottomCaptureView: CaptureStripView
    private lateinit var engine:           RuleEngine
    private lateinit var gameType:         String
    private lateinit var gameContainer:    View

    private var gameState: GameState = GameState(arrayOfNulls(64))
    private var vsAI = true
    private var playerColor = PieceColor.WHITE
    private var matchStarted = false
    private var activityResumed = false
    private var autoplayEnabled = false
    private var autoplayMoveInProgress = false
    private val moveHistory      = ArrayDeque<GameState>()
    private val scope            = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Result guard: stats recorded exactly once per game
    private var resultRecorded = false
    private var interstitialAd: Any? = null
    private var chessMenuDialog: Dialog? = null
    private var boardStyleSwitchEnabled = false
    private val boardStyleSwitchFadeRunnable = Runnable {
        if (!boardStyleSwitchEnabled || !::boardStyleSwitch.isInitialized) return@Runnable
        boardStyleSwitch.animate()
            .alpha(0f)
            .setDuration(900L)
            .withEndAction {
                if (boardStyleSwitch.alpha <= 0.01f) {
                    boardStyleSwitch.visibility = View.INVISIBLE
                }
            }
            .start()
    }
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
        makeFullscreen()
        SoundPlayer.init(this)

        gameType = intent.getStringExtra(EXTRA_GAME) ?: "CHESS"

        engine   = when (gameType) {
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
        val boardStyleSwitchH = (44 * dp).toInt()
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
        boardStyleSwitch = BoardStyleSwitchView(this)
        autoplayButton   = AutoplayButtonView(this)
        bottomCaptureView = CaptureStripView(this).also { it.dividerOnTop = true }
        val boardStyleRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding((10 * dp).toInt(), 0, 0, 0)
            isClickable = true
        }
        boardStyleRow.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                revealBoardStyleSwitch()
            }
            true
        }
        boardStyleSwitch.onStyleChanged = { styleIndex ->
            when (gameType) {
                "CHESS" -> boardView.chessBoardStyle = ChessBoardStyle.entries[styleIndex]
                "CHECKERS" -> boardView.draughtsBoardStyle = DraughtsBoardStyle.entries[styleIndex]
            }
        }
        boardView.onEmptySpaceTapped = ::revealBoardStyleSwitch
        autoplayButton.onAutoplayChanged = { enabled ->
            if (gameType != "LUDO" && vsAI) {
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

        // Render the selected game's real starting position behind the mode
        // dialog. BoardView defaults to an empty 8x8 chess-sized state, which
        // made Fox & Geese briefly show a chess board before startGame().
        SettingsManager.activateGameTheme(this, gameType.lowercase())
        gameState = engine.initialState()
        boardView.ruleEngine = engine
        boardView.gameState = gameState
        boardStyleSwitchEnabled = gameType == "CHESS" || gameType == "CHECKERS"
        boardStyleSwitch.setStyleCount(
            if (gameType == "CHECKERS") DraughtsBoardStyle.entries.size
            else ChessBoardStyle.entries.size,
        )
        boardStyleSwitch.setSelectedIndex(
            if (gameType == "CHECKERS") boardView.draughtsBoardStyle.ordinal
            else boardView.chessBoardStyle.ordinal,
            animate = false,
        )
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
        boardStyleRow.addView(boardStyleSwitch,
            android.widget.LinearLayout.LayoutParams((118 * dp).toInt(), boardStyleSwitchH))
        boardStyleSwitch.visibility = if (boardStyleSwitchEnabled) View.VISIBLE else View.GONE
        container.addView(boardStyleRow,
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                boardStyleSwitchH,
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
                    if (gameType == "CHESS" && vsAI) View.VISIBLE else View.GONE
            })
        container.addView(bottomCaptureView,
            android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))

        val showCaptures = gameType != "OTHELLO"
        topCaptureView.visibility    = if (showCaptures) View.VISIBLE else View.GONE
        bottomCaptureView.visibility = if (showCaptures) View.VISIBLE else View.GONE

        AdManager.attachBanner(container)
        gameContainer = container
        setContentView(gameContainer)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        activityResumed = true
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        resumeComputerTurnIfNeeded()
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
        stopAutoplayAndAiThinking()
        SoundPlayer.stopAll()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) makeFullscreen()
    }

    override fun onDestroy() {
        stopAutoplayAndAiThinking()
        super.onDestroy()
        scope.cancel()
    }

    private fun scheduleBoardStyleSwitchFade() {
        if (boardStyleSwitchEnabled && ::boardStyleSwitch.isInitialized) {
            boardStyleSwitch.removeCallbacks(boardStyleSwitchFadeRunnable)
            boardStyleSwitch.animate().cancel()
            boardStyleSwitch.alpha = 1f
            boardStyleSwitch.visibility = View.VISIBLE
            boardStyleSwitch.postDelayed(boardStyleSwitchFadeRunnable, 5_000L)
        }
    }

    private fun revealBoardStyleSwitch() {
        if (boardStyleSwitchEnabled && ::boardStyleSwitch.isInitialized) {
            boardStyleSwitch.removeCallbacks(boardStyleSwitchFadeRunnable)
            boardStyleSwitch.animate().cancel()
            if (boardStyleSwitch.visibility != View.VISIBLE) {
                boardStyleSwitch.visibility = View.VISIBLE
                boardStyleSwitch.alpha = 0f
            }
            boardStyleSwitch.animate()
                .alpha(1f)
                .setDuration(220L)
                .start()
            boardStyleSwitch.postDelayed(boardStyleSwitchFadeRunnable, 5_000L)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (matchStarted && gameState.status != GameStatus.IN_PROGRESS) {
            showResultDialog()
            return
        }
        if (!matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            @Suppress("DEPRECATION") super.onBackPressed(); return
        }
        stopAutoplayAndAiThinking()
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
                if (vsAI) SettingsManager.recordForfeit(this)
                @Suppress("DEPRECATION") super.onBackPressed()
            }
            .setNegativeButton("Keep Playing", null).show()
    }

    private fun showChessLeaveMatchDialog() {
        // Dialogs do not pause the Activity, so stop automated gameplay before
        // hiding the board behind the leave-match screen.
        stopAutoplayAndAiThinking()
        hideChessBoardWhileDialogIsOpen()
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
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            when (which) {
                0 -> {
                    dialog.dismiss()
                    pauseMatchAndExit()
                }
                1 -> {
                    dialog.dismiss()
                    clearPausedMatch()
                    if (vsAI) SettingsManager.recordForfeit(this)
                    @Suppress("DEPRECATION") super.onBackPressed()
                }
                else -> {
                    dialog.dismiss()
                    showChessBoardAfterDialog()
                }
            }
        }
        dialog.show()
        styleChessDialog(dialog, 520f)
    }

    private fun pauseMatchAndExit() {
        stopAutoplayAndAiThinking()
        PausedMatchStore.save(
            this,
            gameType = gameType,
            vsAI = vsAI,
            playerColor = playerColor.name,
            moves = gameState.moveHistory,
        )
        finish()
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
            add("vs AI")
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
                    "vs AI" -> { vsAI = true; showColorPickerDialog() }
                    "2 Players" -> { vsAI = false; playerColor = PieceColor.WHITE; startGame() }
                    "How to Play" -> showRules(showModeAfter = !matchStarted)
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (!matchStarted) finish() }
            .show()
    }

    private fun showChessMenu() {
        chessMenuDialog?.dismiss()
        hideChessBoardWhileDialogIsOpen()

        val menuView = ChessMenuView(
            this,
            PausedMatchStore.has(this, gameType),
            gameLabel = styledGameLabel(),
        )
        val dialog = Dialog(this)
        chessMenuDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(menuView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            chessMenuDialog = null
            clearChessDialogBlur()
            if (!matchStarted) finish() else showChessBoardAfterDialog()
        }
        dialog.setOnDismissListener {
            clearChessDialogBlur()
            if (chessMenuDialog === dialog) chessMenuDialog = null
        }

        menuView.onVsAi = {
            dialog.dismiss()
            vsAI = true
            showColorPickerDialog()
        }
        menuView.onTwoPlayers = {
            dialog.dismiss()
            vsAI = false
            playerColor = PieceColor.WHITE
            startGame()
        }
        menuView.onHowToPlay = {
            dialog.dismiss()
            showRules(showModeAfter = !matchStarted)
        }
        menuView.onResumeMatch = {
            dialog.dismiss()
            resumePausedMatch()
        }

        dialog.show()
        if (isStyledBoardGame()) {
            styleChessDialog(dialog, 0f)
        } else {
            dialog.window?.let { window ->
                val metrics = resources.displayMetrics
                val horizontalMargin = (24f * metrics.density).toInt()
                val maxWidth = (420f * metrics.density).toInt()
                val width = minOf(metrics.widthPixels - horizontalMargin * 2, maxWidth)
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setWindowAnimations(0)
                window.attributes = window.attributes.apply { dimAmount = 0.72f }
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                enableChessWindowBlur(window)
            }
            applyChessDialogBlur()
        }
    }

    private fun showColorPickerDialog() {
        if (isStyledBoardGame()) {
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
        hideChessBoardWhileDialogIsOpen()
        val isDraughts = gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS"
        val isFoxAndGeese = gameType == "FOX_AND_GEESE"
        val view = ChessChoiceView(
            this,
            title = "Play As",
            subtitle = "Choose your colour before the first move.",
            choices = listOf(
                ChessChoiceView.Choice(
                    if (isFoxAndGeese) "Fox" else "White",
                    if (isFoxAndGeese) "Moves second" else "Moves first",
                    if (isFoxAndGeese) "🦊" else if (isDraughts) "◉" else "♔",
                    Color.parseColor("#E3B86A"),
                ),
                ChessChoiceView.Choice(
                    if (isFoxAndGeese) "Geese" else "Black",
                    if (isFoxAndGeese) "Moves first" else "Moves second",
                    if (isFoxAndGeese) "🪿" else if (isDraughts) "●" else "♚",
                    Color.parseColor("#A9B6E8"),
                ),
            ),
            gameLabel = styledGameLabel(),
            headerSymbol = "●",
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showModeDialog()
        }
        dialog.setOnDismissListener { clearChessDialogBlur() }
        view.onChoiceSelected = { which ->
            playerColor = if (which == 0) PieceColor.WHITE else PieceColor.BLACK
            dialog.dismiss()
            startGame()
        }
        dialog.show()
        styleChessDialog(dialog, 420f)
    }

    private fun showRules(showModeAfter: Boolean = false) {
        val gameName = when (gameType) {
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
Othello (Reversi) is played on an 8×8 board. Each player has discs that are white on one side and black on the other. You choose your colour when starting a game vs AI.

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
Played on the dark squares of an 8×8 board. You choose your colour when starting a game vs AI. Pieces start on the first 3 rows of each side.

─────────────────────────

Moving
Pieces move diagonally forward one square at a time to an empty dark square.

─────────────────────────

Jumping
If an opponent's piece is diagonally adjacent and the square beyond it is empty, you must jump over it — capturing it. Multiple jumps in one turn are mandatory if available.

─────────────────────────

Kinging
When a piece reaches the far end of the board it becomes a King (marked ♔). Kings may move and jump diagonally in any direction.

─────────────────────────

Winning
Capture all of your opponent's pieces, or leave them with no legal moves.
            """.trimIndent()

            "INTERNATIONAL_DRAUGHTS" -> """
INTERNATIONAL DRAUGHTS — Rules

Overview
Played on the dark squares of a 10×10 board with twenty pieces per side. You choose your colour when starting a game vs AI. White moves first.

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
Two players command 16 pieces each (White and Black) on an 8×8 board. You choose your colour when starting a game vs AI.

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
        hideChessBoardWhileDialogIsOpen()
        val view = ChessRulesView(this, gameName, rulesText, styledGameLabel())
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            if (showModeAfter) showModeDialog()
            else showChessBoardAfterDialog()
        }
        dialog.setOnDismissListener { clearChessDialogBlur() }
        view.onDone = {
            dialog.dismiss()
            if (showModeAfter) showModeDialog()
            else showChessBoardAfterDialog()
        }
        dialog.show()
        styleChessDialog(dialog, 620f)
    }

    private fun styleChessDialog(
        dialog: Dialog,
        heightDp: Float,
        fullScreen: Boolean = isStyledBoardGame(),
        blurBackground: Boolean = true,
    ) {
        val isChessFullScreen = fullScreen
        val metrics = resources.displayMetrics
        if (isChessFullScreen) {
            hideChessBoardWhileDialogIsOpen()
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0B1D25")))
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setWindowAnimations(0)
                window.decorView.setPadding(0, 0, 0, 0)
                window.setGravity(Gravity.CENTER)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
            clearChessDialogBlur()
            return
        }
        val horizontalMargin = (24f * metrics.density).toInt()
        val maxWidth = (420f * metrics.density).toInt()
        val width = minOf(metrics.widthPixels - horizontalMargin * 2, maxWidth)
        val maxHeight = (metrics.heightPixels * 0.84f).toInt()
        val height = minOf((heightDp * metrics.density).toInt(), maxHeight)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.72f }
            window.setLayout(width, height)
            enableChessWindowBlur(window)
        }
        if (blurBackground) applyChessDialogBlur()
    }

    private fun enableChessWindowBlur(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = (28f * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun applyChessDialogBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP)
            gameContainer.setRenderEffect(blur)
            (gameContainer as? ViewGroup)?.let { container ->
                for (index in 0 until container.childCount) {
                    container.getChildAt(index).setRenderEffect(blur)
                }
            }
        }
    }

    private fun clearChessDialogBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            gameContainer.setRenderEffect(null)
            (gameContainer as? ViewGroup)?.let { container ->
                for (index in 0 until container.childCount) {
                    container.getChildAt(index).setRenderEffect(null)
                }
            }
        }
    }

    private fun hideChessBoardWhileDialogIsOpen() {
        if (isStyledBoardGame()) {
            gameContainer.visibility = View.INVISIBLE
        }
    }

    private fun showChessBoardAfterDialog() {
        showChessBoardAfterDialog(resumeAi = true)
    }

    private fun showChessBoardAfterDialog(resumeAi: Boolean) {
        if (isStyledBoardGame()) {
            gameContainer.visibility = View.VISIBLE
            if (resumeAi) resumeComputerTurnIfNeeded()
        }
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        showChessBoardAfterDialog(resumeAi = false)
        matchStarted = true
        resultRecorded = false
        interstitialAd = null
        autoPassJob?.cancel()
        autoPassJob = null
        autoplayEnabled = false
        autoplayMoveInProgress = false
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
            autoplayButton.visibility =
                if (gameType != "LUDO" && vsAI) View.VISIBLE else View.GONE
        }
        redoGameStates.clear(); redoCaptures.clear(); redoMoves.clear(); redoCapSnaps.clear()
        AdManager.loadInterstitial(this) { interstitialAd = it }
        moveHistory.clear(); capturedByWhite.clear(); capturedByBlack.clear(); captureSnapshots.clear()
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, gameType.lowercase())
        if (vsAI) SettingsManager.setActiveGame(this, gameType.lowercase())

        gameState = engine.initialState()
        boardView.ruleEngine           = engine
        boardView.showMustCaptureHints =
            gameType == "CHECKERS" || gameType == "INTERNATIONAL_DRAUGHTS"
        boardView.directMoveMode       = (gameType == "OTHELLO" || gameType == "GO")
        boardView.gameState            = gameState
        boardView.isFlipped            = false
        boardView.isLocked             = false
        boardView.rotateBlackPieces    = (!vsAI && gameType == "CHESS")
        boardView.onMoveMade           = { move -> handleMove(move) }
        boardView.onPromotionChoice   = ::showPromotionChoice
        // Tap after game ends → re-show result dialog without double-recording stats
        boardView.onGameOverTapped     = { showResultDialog() }
        boardView.refreshTheme()
        hudView.setGoMode(gameType == "GO")
        hideGoNotice()

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
            gameType == "FOX_AND_GEESE"
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
        scheduleBoardStyleSwitchFade()
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, gameType) ?: run {
            showModeDialog()
            return
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
        hideChessBoardWhileDialogIsOpen()
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
        StyledDialogs.showChoices(
            context = this,
            title = "Choose a promotion",
            subtitle = "Your pawn reached the far rank. Select its new piece.",
            choices = available.map { it.first },
            heightDp = 560f,
            gameLabel = styledGameLabel(),
            headerSymbol = "●",
            onCancel = { showChessBoardAfterDialog() },
            onChoice = { index, dialog ->
                dialog.dismiss()
                showChessBoardAfterDialog()
                boardView.animateExternalMove(available[index].second)
            },
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
                autoplayEnabled
            )

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
                    when (gameType) {
                        "XIANGQI" -> {
                            AIPlayer(
                                engine,
                                maxDepth = 2,
                                timeLimitMs = 1800L,
                                quiesceDepth = 1,
                            ).bestMove(thinkingState)
                        }
                        "SHOGI" -> {
                            AIPlayer(
                                engine,
                                maxDepth = SettingsManager.shogiAiDepth(this@GameActivity),
                                timeLimitMs = SettingsManager.shogiAiTimeLimitMs(this@GameActivity),
                                quiesceDepth = 1,
                            ).bestMove(thinkingState)
                        }
                        "OTHELLO"  -> {
                            val ai = AIPlayer(engine, maxDepth = SettingsManager.othelloAiDepth(this@GameActivity), timeLimitMs = 3000L)
                            ai.bestMove(thinkingState)
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
                                timeLimitMs = SettingsManager.foxAndGeeseAiTimeLimitMs(this@GameActivity)
                            )
                            ai.bestMove(thinkingState)
                        }
                        else -> {
                            // Chess — deeper search with quiescence and time limit
                            val depth    = SettingsManager.chessAiDepth(this@GameActivity)
                            val timeMs   = SettingsManager.chessAiTimeLimitMs(this@GameActivity)
                            val quiesce  = SettingsManager.chessAiQuiesceDepth(this@GameActivity)
                            val ai = AIPlayer(engine, maxDepth = depth, timeLimitMs = timeMs, quiesceDepth = quiesce)
                            ai.bestMove(thinkingState)
                        }
                    }
                } catch (e: Throwable) { null }
            }
            hudView.setThinking(false)
            val stateIsStillCurrent =
                gameState.currentTurn == thinkingState.currentTurn &&
                    gameState.moveHistory.size == thinkingState.moveHistory.size &&
                    gameState.status == thinkingState.status
            val playerAutoplayStillEnabled = !autoplayingPlayerTurn || autoplayEnabled
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
        val label = if (vsAI && gameState.currentTurn == playerColor) "Your turn" else "$turn to move"
        hudView.setInfo(label, canUndo = moveHistory.isNotEmpty(), canRedo = redoGameStates.isNotEmpty())
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
        gameState = nextState
        val (cw, cb) = nextCap
        capturedByWhite = cw.toMutableList(); capturedByBlack = cb.toMutableList()
        refreshCaptureViews()
        boardView.isLocked = false; boardView.gameState = gameState; updateHud()
    }

    fun onMenuClicked() {
        // The menu is an in-app dialog and does not trigger onPause().
        stopAutoplayAndAiThinking()
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        if (isStyledBoardGame()) {
            showChessGameplayMenu(inProgress)
            return
        }
        val items = mutableListOf("New Game", "How to Play")
        // Othello uses a fixed AI setting; the other games expose their
        // difficulty because their search depth can be tuned safely.
        if (vsAI && gameType != "OTHELLO") items.add("AI Difficulty")
        items.add("Main Menu")
        val arr = items.toTypedArray()
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(arr) { _, which ->
                when (arr[which]) {
                    "New Game" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Forfeit Match?")
                            .setMessage("Starting a new game counts as a forfeit.")
                            .setPositiveButton("Forfeit & New Game") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            }.setNegativeButton("Cancel", null).show()
                    } else showModeDialog()
                    "How to Play"   -> showRules(showModeAfter = false)
                    "AI Difficulty" -> showDifficultyDialog()
                    "Main Menu" -> if (inProgress) {
                        if (isStyledBoardGame()) {
                            showChessLeaveMatchDialog()
                        } else {
                            AlertDialog.Builder(this).setTitle("Leave Match?")
                                .setMessage("Pause to resume later, or leave to forfeit.")
                                .setPositiveButton("Pause & Exit") { _, _ -> pauseMatchAndExit() }
                                .setNeutralButton("Leave Match") { _, _ ->
                                    clearPausedMatch()
                                    if (vsAI) SettingsManager.recordForfeit(this)
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
        hideChessBoardWhileDialogIsOpen()
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
        if (vsAI && gameType != "OTHELLO") {
            choices += ChessChoiceView.Choice(
                "AI Difficulty",
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
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            dialog.dismiss()
            actions.getOrNull(which)?.invoke()
        }
        dialog.show()
        styleChessDialog(dialog, 620f)
    }

    private fun showChessForfeitDialog() {
        hideChessBoardWhileDialogIsOpen()
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
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            dialog.dismiss()
            if (which == 1) {
                if (vsAI) SettingsManager.recordForfeit(this)
                showModeDialog()
            } else {
                showChessBoardAfterDialog()
            }
        }
        dialog.show()
        styleChessDialog(dialog, 470f)
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
            else       -> { getDiff = { SettingsManager.getChessDifficulty(this) };    setDiff = { v -> SettingsManager.setChessDifficulty(this, v) } }
        }
        val current = getDiff()
        showStyledDifficultyDialog(current, setDiff)
    }

    private fun showStyledDifficultyDialog(current: Int, setDiff: (Int) -> Unit) {
        hideChessBoardWhileDialogIsOpen()
        val levels = listOf(
            ChessChoiceView.Choice(
                "Easy",
                if (current == 0) "Current setting" else "A relaxed challenge",
                "I",
                Color.parseColor("#8EC7B9"),
            ),
            ChessChoiceView.Choice(
                "Medium",
                if (current == 1) "Current setting" else "A balanced challenge",
                "II",
                Color.parseColor("#E3B86A"),
            ),
            ChessChoiceView.Choice(
                "Hard",
                if (current == 2) "Current setting" else "A serious challenge",
                "III",
                Color.parseColor("#E58A7A"),
            ),
            ChessChoiceView.Choice(
                "Master",
                if (current == 3) "Current setting" else "Elite-level challenge",
                "IV",
                Color.parseColor("#D8A7FF"),
            ),
        )
        val view = ChessChoiceView(
            this,
            title = "AI Difficulty",
            subtitle = "Choose the challenge for your next move.",
            choices = levels,
            gameLabel = styledGameLabel(),
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            val changed = which != current
            setDiff(which)
            dialog.dismiss()
            if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                showChessRestartDialog()
            } else {
                showChessBoardAfterDialog()
            }
        }
        dialog.show()
        styleChessDialog(dialog, 520f)
    }

    private fun styledGameLabel(): String = when (gameType) {
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
        hideChessBoardWhileDialogIsOpen()
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
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            dialog.dismiss()
            if (which == 1) startGame() else showChessBoardAfterDialog()
        }
        dialog.show()
        styleChessDialog(dialog, 470f)
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
        if (isStyledBoardGame()) {
            showChessResultDialog(msg, resultLabel)
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

    private fun showChessResultDialog(message: String, resultLabel: String) {
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
        )
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { clearChessDialogBlur() }
        dialog.setOnCancelListener {
            clearChessDialogBlur()
            showChessBoardAfterDialog()
        }
        view.onChoiceSelected = { which ->
            dialog.dismiss()
            when (which) {
                0 -> startGame()
                1 -> finish()
                2 -> launchReplay(resultLabel)
            }
        }
        dialog.show()
        styleChessDialog(dialog, 520f, fullScreen = false, blurBackground = false)
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

    private fun launchReplay(resultLabel: String) {
        showChessBoardAfterDialog()
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  gameType)
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
        })
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class HudView(ctx: Context) : View(ctx) {
        private var title    = "White to move"
        private var canUndo  = false
        private var canRedo  = false
        private var thinking = false
        private var goMode   = false
        private var detail   = ""

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity

        private val bgPaint    = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divPaint   = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.LEFT; isFakeBoldText = true
            textSize = 15f * sp.coerceAtMost(3f)
        }
        private val subPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.LEFT
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val dimPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private val backRect = RectF()
        private val passRect = RectF()
        private val undoRect = RectF()
        private val redoRect = RectF()
        private val menuRect = RectF()

        fun setInfo(label: String, canUndo: Boolean, canRedo: Boolean, detail: String = "") {
            title = label
            this.canUndo = canUndo
            this.canRedo = canRedo
            this.detail = detail
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
            val bw = 44f * dp; val bh = 28f * dp; val by = (h - bh) / 2f
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
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); this@GameActivity.onBackPressed() }
                    goMode && passRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onPassClicked() }
                    undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndoClicked() }
                    redoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onRedoClicked() }
                    menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenuClicked() }
                    else -> revealBoardStyleSwitch()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            canvas.drawRect(0f, h - dp, w, h, divPaint)

            val rr = 5f * dp
            canvas.drawRoundRect(backRect, rr, rr, btnBgPaint)
            if (goMode) canvas.drawRoundRect(passRect, rr, rr, btnBgPaint)
            canvas.drawRoundRect(undoRect, rr, rr, btnBgPaint)
            canvas.drawRoundRect(redoRect, rr, rr, btnBgPaint)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgPaint)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnPaint.textSize * 0.36f, btnPaint)
            if (goMode) canvas.drawText("Pass", passRect.centerX(), passRect.centerY() + btnPaint.textSize * 0.36f, btnPaint)
            canvas.drawText("Undo", undoRect.centerX(), undoRect.centerY() + btnPaint.textSize * 0.36f,
                if (canUndo) btnPaint else dimPaint)
            canvas.drawText("Redo", redoRect.centerX(), redoRect.centerY() + btnPaint.textSize * 0.36f,
                if (canRedo) btnPaint else dimPaint)
            canvas.drawText("Menu", menuRect.centerX(), menuRect.centerY() + btnPaint.textSize * 0.36f, btnPaint)

            val cx = (backRect.right + if (goMode) passRect.left else undoRect.left) / 2f
            val sub = if (thinking) "Thinking…" else detail
            canvas.drawText(title, cx, h / 2f - txtPaint.textSize * 0.15f, txtPaint.also { it.textAlign = Paint.Align.CENTER })
            if (sub.isNotEmpty()) canvas.drawText(sub, cx, h / 2f + subPaint.textSize * 1.1f, subPaint.also { it.textAlign = Paint.Align.CENTER })
            txtPaint.textAlign = Paint.Align.LEFT
            subPaint.textAlign = Paint.Align.LEFT
        }
    }
}
