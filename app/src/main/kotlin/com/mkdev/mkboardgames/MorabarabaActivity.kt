package com.mkdev.mkboardgames

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.morabaraba.MorabarabaBoard
import com.mkdev.mkboardgames.games.morabaraba.MorabarabaRuleEngine
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.CaptureStripView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.ChessMenuView
import com.mkdev.mkboardgames.ui.BoardStyleSwitchView
import com.mkdev.mkboardgames.ui.MorabaraBoardView
import com.mkdev.mkboardgames.ui.MorabarabaBoardStyle
import com.mkdev.mkboardgames.ui.StyledDialogs
import kotlinx.coroutines.*

class MorabarabaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "MORABARABA"
    }

    private lateinit var boardView:         MorabaraBoardView
    private lateinit var hudView:           MorabarabaHudView
    private lateinit var topCaptureView:    CaptureStripView
    private lateinit var bottomCaptureView: CaptureStripView
    private lateinit var boardStyleSwitch:  BoardStyleSwitchView
    private lateinit var autoplayButton:    AutoplayButtonView
    private lateinit var gameRoot: View
    private var engine:                     MorabarabaRuleEngine = MorabarabaRuleEngine()
    private var gameState:                  GameState = GameState(arrayOfNulls(49), boardSize = 7)
    private var vsAI                        = true
    private var playerColor                 = PieceColor.WHITE
    private var matchStarted                = false
    private var activityResumed             = false
    private var autoplayEnabled             = false
    private var exitPosted                  = false
    private var autoplayMoveInProgress      = false
    private var pieceCount                  = 12
    private var boardStyleSwitchEnabled    = false
    private val scope                       = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val moveHistory                 = ArrayDeque<GameState>()
    private var aiJob: Job?                 = null

    /** Stats recorded once per game. */
    private var resultRecorded = false
    private var interstitialAd: Any? = null

    private val redoGameStates = ArrayDeque<GameState>()
    private val redoCaptures   = ArrayDeque<Pair<List<Piece>, List<Piece>>>()
    private val redoMoves      = ArrayDeque<List<GameState>>()
    private val redoCapSnaps   = ArrayDeque<List<Pair<List<Piece>, List<Piece>>>>()

    private var capturedByWhite    = mutableListOf<Piece>()
    private var capturedByBlack    = mutableListOf<Piece>()
    private val captureSnapshots = ArrayDeque<Pair<List<Piece>, List<Piece>>>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0E0E"))
        }

        hudView          = MorabarabaHudView(this)
        topCaptureView   = CaptureStripView(this).also { it.dividerOnTop = false }
        boardView        = MorabaraBoardView(this)
        boardStyleSwitch = BoardStyleSwitchView(this)
        autoplayButton   = AutoplayButtonView(this)
        bottomCaptureView = CaptureStripView(this).also { it.dividerOnTop = true }

        val hudH = (82 * dp).toInt()
        val capH = (36 * dp).toInt()
        val boardStyleSwitchH = (44 * dp).toInt()
        val autoplayButtonH = (76 * dp).toInt()

        boardStyleSwitch.onStyleChanged = { styleIndex ->
            boardView.boardStyle = MorabarabaBoardStyle.entries
                .getOrElse(styleIndex) { MorabarabaBoardStyle.CANVAS }
        }
        autoplayButton.onAutoplayChanged = { enabled ->
            if (vsAI) {
                autoplayEnabled = enabled
                if (!enabled && autoplayMoveInProgress) {
                    autoplayMoveInProgress = false
                    boardView.cancelAnim()
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

        root.addView(hudView,          LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hudH))
        root.addView(topCaptureView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        root.addView(boardStyleSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, boardStyleSwitchH))
        root.addView(boardView,        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(autoplayButton,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, autoplayButtonH).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        autoplayButton.visibility = View.GONE
        root.addView(bottomCaptureView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, capH))
        boardStyleSwitch.visibility = View.GONE

        AdManager.attachBanner(root)
        gameRoot = root
        setContentView(root)
        hideBoardUntilMatchStarts()
        showModeDialog()
    }

    override fun onResume() {
        super.onResume(); makeFullscreen()
        activityResumed = true
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
        if (::boardView.isInitialized) {
            SettingsManager.activateGameTheme(this, "morabaraba")
            boardView.applyTheme()
        }
        resumeComputerTurnIfNeeded()
    }

    override fun onPause() {
        activityResumed = false
        stopAutomatedGameplay()
        SoundPlayer.stopAll()
        super.onPause()
    }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() {
        stopAutomatedGameplay()
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
        if (::gameRoot.isInitialized) gameRoot.visibility = View.GONE
        window.decorView.postDelayed({
            super.finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 16L)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (StyledDialogs.handleBackPressed()) return
        if (!matchStarted) {
            finish()
            return
        }
        if (gameState.status != GameStatus.IN_PROGRESS) {
            showResult()
            return
        }
        stopAutomatedGameplay()
        showLeaveMatchDialog()
    }

    private fun pauseMatchAndExit() {
        stopAutomatedGameplay()
        PausedMatchStore.save(
            this,
            gameType = "MORABARABA",
            vsAI = vsAI,
            playerColor = playerColor.name,
            pieceCount = pieceCount,
            moves = gameState.moveHistory,
        )
        finish()
    }

    private fun clearPausedMatch() = PausedMatchStore.clear(this, "MORABARABA")

    // ─── Game flow ────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        val menuView = ChessMenuView(
            this,
            PausedMatchStore.has(this, "MORABARABA"),
            gameLabel = "M O R A B A R A B A",
        )
        menuView.onVsAi = {
            StyledDialogs.dismiss()
            showVariantDialog(isVsAI = true)
        }
        menuView.onTwoPlayers = {
            StyledDialogs.dismiss()
            showVariantDialog(isVsAI = false)
        }
        menuView.onHowToPlay = {
            StyledDialogs.dismiss()
            showTutorial(showModeAfter = !matchStarted)
        }
        menuView.onResumeMatch = {
            StyledDialogs.dismiss()
            resumePausedMatch()
        }
        StyledDialogs.showFullScreenView(this, menuView) {
            if (!matchStarted) finish() else showBoardAfterDialog()
        }
    }

    private fun showVariantDialog(isVsAI: Boolean) {
        val choices = listOf(
            ChessChoiceView.Choice("6 Cows", "Simple variant", "VI", Color.parseColor("#8EC7B9")),
            ChessChoiceView.Choice("9 Cows", "Classic variant", "IX", Color.parseColor("#E3B86A")),
            ChessChoiceView.Choice("12 Cows", "Full variant", "XII", Color.parseColor("#E58A7A")),
        )
        showChoiceDialog("Choose Variant", "Select the number of cows in play.", choices, choices.mapIndexed { index, _ ->
            {
                pieceCount = when (index) { 0 -> 6; 1 -> 9; else -> 12 }
                vsAI = isVsAI
                if (isVsAI) showColorPickerDialog() else {
                    playerColor = PieceColor.WHITE
                    startGame()
                }
            }
        }, 520f) { showModeDialog() }
    }

    private fun showColorPickerDialog() {
        val choices = listOf(
            ChessChoiceView.Choice("White", "Moves first", "", Color.parseColor("#E3B86A")),
            ChessChoiceView.Choice("Black", "Moves second", "", Color.parseColor("#A9B6E8")),
        )
        showChoiceDialog("Play As", "Choose your side on the board.", choices, listOf(
            { playerColor = PieceColor.WHITE; startGame() },
            { playerColor = PieceColor.BLACK; startGame() },
        ), 420f) { showVariantDialog(isVsAI = true) }
    }

    private fun showChoiceDialog(
        title: String,
        subtitle: String,
        choices: List<ChessChoiceView.Choice>,
        actions: List<() -> Unit>,
        heightDp: Float,
        fullScreen: Boolean = true,
        onCancel: (() -> Unit)? = null,
    ) {
        hideBoardWhileDialogIsOpen()
        StyledDialogs.showChoices(
            this,
            title,
            subtitle,
            choices,
            heightDp,
            "M O R A B A R A B A",
            onCancel = onCancel ?: {
                if (matchStarted) showBoardAfterDialog() else finish()
            },
            fullScreen = fullScreen,
        ) { which, _ ->
            actions.getOrNull(which)?.invoke()
        }
    }

    private fun showLeaveMatchDialog() {
        showChoiceDialog(
            "Leave Match?",
            "Pause to resume later, or leave to forfeit this game.",
            listOf(
                ChessChoiceView.Choice("Pause & Exit", "Save and resume later", "Ⅱ", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Leave Match", "Forfeit this game", "⚑", Color.parseColor("#E58A7A")),
                ChessChoiceView.Choice("Keep Playing", "Return to the board", "↩", Color.parseColor("#A9B6E8")),
            ),
            listOf(
                { pauseMatchAndExit() },
                {
                    clearPausedMatch()
                    if (vsAI) SettingsManager.recordForfeit(this)
                    finish()
                },
                { showBoardAfterDialog() },
            ),
            520f,
            fullScreen = false,
        )
    }

    private fun startGame(restoring: PausedMatchStore.Match? = null) {
        showBoardAfterDialog()
        matchStarted = true
        resultRecorded = false
        interstitialAd = null
        redoGameStates.clear(); redoCaptures.clear(); redoMoves.clear(); redoCapSnaps.clear()
        AdManager.loadInterstitial(this) { interstitialAd = it }
        aiJob?.cancel(); aiJob = null
        autoplayEnabled = false
        autoplayMoveInProgress = false
        autoplayButton.setAutoplayEnabled(false, animate = false)
        autoplayButton.visibility = if (vsAI) View.VISIBLE else View.GONE
        moveHistory.clear()
        capturedByWhite.clear(); capturedByBlack.clear(); captureSnapshots.clear()
        if (restoring == null) clearPausedMatch()
        SettingsManager.activateGameTheme(this, "morabaraba")
        if (vsAI) SettingsManager.setActiveGame(this, "morabaraba")
        SoundPlayer.init(this)
        engine                 = MorabarabaRuleEngine(pieceCount)
        gameState              = engine.initialState()
        boardView.ruleEngine   = engine
        boardStyleSwitchEnabled = pieceCount != 6
        boardView.boardStyle = MorabarabaBoardStyle.CANVAS
        boardStyleSwitch.visibility = if (boardStyleSwitchEnabled) View.VISIBLE else View.GONE
        boardStyleSwitch.setStyleCount(MorabarabaBoardStyle.entries.size)
        boardStyleSwitch.setSelectedIndex(boardView.boardStyle.ordinal, animate = false)
        boardView.gameState    = gameState
        boardView.playerColor  = playerColor
        boardView.vsAI         = vsAI
        boardView.isLocked     = false
        boardView.onMoveMade   = ::handleMove
        boardView.applyTheme()
        // Tap after game over → re-show result dialog
        boardView.onGameOverTapped = { showResult() }

        topCaptureView.setLabel(
            if (!vsAI) "Black's captures" else "Black ⚔"
        )
        bottomCaptureView.setLabel(
            if (!vsAI) "White's captures" else "White ⚔"
        )

        topCaptureView.update(emptyList())
        bottomCaptureView.update(emptyList())
        SoundPlayer.play("game_start")
        updateHud()
        if (restoring != null) {
            restoreMoves(restoring.moves)
            clearPausedMatch()
            if (aiControlsCurrentTurn()) triggerAI()
        } else if (vsAI && gameState.currentTurn != playerColor) {
            triggerAI()
        }
    }

    private fun resumePausedMatch() {
        val paused = PausedMatchStore.load(this, "MORABARABA") ?: run {
            showModeDialog()
            return
        }
        pieceCount = paused.pieceCount ?: 12
        vsAI = paused.vsAI
        playerColor = runCatching { PieceColor.valueOf(paused.playerColor) }
            .getOrDefault(PieceColor.WHITE)
        startGame(paused)
    }

    private fun restoreMoves(moves: List<Move>) {
        for (move in moves) {
            val previous = gameState
            for (capture in move.captures) {
                val captured = previous.get(capture) ?: continue
                if (previous.currentTurn == PieceColor.WHITE) capturedByWhite.add(captured)
                else capturedByBlack.add(captured)
            }
            captureSnapshots.add(capturedByWhite.toList() to capturedByBlack.toList())
            moveHistory.add(previous)
            gameState = engine.applyMove(gameState, move)
        }
        boardView.gameState = gameState
        boardView.isLocked = false
        topCaptureView.update(capturedByBlack)
        bottomCaptureView.update(capturedByWhite)
        updateHud()
    }

    private fun handleMove(move: Move) {
        if (boardView.isLocked) return
        autoplayMoveInProgress = false
        try {
            val prev       = gameState
            val moverColor = prev.currentTurn

            for (capPos in move.captures) {
                val capPiece = prev.get(capPos) ?: continue
                if (moverColor == PieceColor.WHITE) capturedByWhite.add(capPiece)
                else capturedByBlack.add(capPiece)
            }
            captureSnapshots.add(capturedByWhite.toList() to capturedByBlack.toList())
            redoGameStates.clear(); redoCaptures.clear(); redoMoves.clear(); redoCapSnaps.clear()
            gameState           = engine.applyMove(gameState, move)
            moveHistory.add(prev)
            boardView.gameState = gameState
            topCaptureView.update(capturedByBlack)
            bottomCaptureView.update(capturedByWhite)
            updateHud()
            playMorabarabaSound(prev, move)
            if (gameState.status != GameStatus.IN_PROGRESS) {
                recordResult()
                val ad = interstitialAd; interstitialAd = null
                AdManager.showInterstitial(this, ad)
                AdManager.loadInterstitial(this) { interstitialAd = it }
                showResult()
                return
            }
            // Autoplay must also continue when the turn comes back to the
            // player. The enabled state makes both sides computer-controlled;
            // without it, the first AI move leaves the game waiting on the
            // player's turn after a single autoplay cycle.
            if (vsAI && aiControlsCurrentTurn()) triggerAI()
        } catch (e: Exception) {
            boardView.isLocked = false
        }
    }

    private fun playMorabarabaSound(prev: com.mkdev.mkboardgames.engine.GameState, move: Move) {
        when (gameState.status) {
            com.mkdev.mkboardgames.engine.GameStatus.WHITE_WINS, com.mkdev.mkboardgames.engine.GameStatus.BLACK_WINS -> {
                boardView.postDelayed({ if (activityResumed) SoundPlayer.play("game_end") }, 200)
                return
            }
            com.mkdev.mkboardgames.engine.GameStatus.DRAW -> { SoundPlayer.play("game_draw"); return }
            else -> {}
        }
        val wPlacedPrev = (prev.metadata[com.mkdev.mkboardgames.games.morabaraba.MorabarabaBoard.META_W] as? Int) ?: 0
        val bPlacedPrev = (prev.metadata[com.mkdev.mkboardgames.games.morabaraba.MorabarabaBoard.META_B] as? Int) ?: 0
        val isPlacementPhase = wPlacedPrev < engine.pieceCount || bPlacedPrev < engine.pieceCount
        when {
            move.captures.isNotEmpty() -> {
                SoundPlayer.playMovement("mora_capture")
                boardView.postDelayed({ if (activityResumed) SoundPlayer.playMovement("mora_mill") }, 250)
            }
            isPlacementPhase -> SoundPlayer.playMovement("mora_place")
            else             -> SoundPlayer.playMovement("mora_move")
        }
    }

    /** Record win/loss/draw once per game. */
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

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun stopAutomatedGameplay() {
        autoplayEnabled = false
        autoplayMoveInProgress = false
        aiJob?.cancel()
        aiJob = null
        scope.coroutineContext.cancelChildren()
        if (::autoplayButton.isInitialized) {
            autoplayButton.setAutoplayEnabled(false, animate = false)
        }
        if (::boardView.isInitialized) boardView.cancelAnim()
        if (::hudView.isInitialized) hudView.setThinking(false)
    }

    private fun triggerAI() {
        if (!activityResumed || !matchStarted || gameState.status != GameStatus.IN_PROGRESS) {
            boardView.isLocked = false
            hudView.setThinking(false)
            return
        }
        boardView.isLocked = true
        hudView.setThinking(true)
        aiJob?.cancel()
        val thinkingState = gameState
        val autoplayingPlayerTurn = thinkingState.currentTurn == playerColor
        aiJob = scope.launch {
            val depth  = SettingsManager.morabarabaAiDepth(this@MorabarabaActivity)
            val timeMs = SettingsManager.morabarabaAiTimeLimitMs(this@MorabarabaActivity)
            val ai = AIPlayer(engine, depth, timeMs)
            val move = withContext(Dispatchers.Default) {
                val legal = engine.allLegalMoves(thinkingState, thinkingState.currentTurn)
                try {
                    val best = ai.bestMove(thinkingState)
                    if (best != null && legal.any { it.from == best.from && it.to == best.to }) best
                    else legal.randomOrNull()
                } catch (e: Throwable) {
                    legal.randomOrNull()
                }
            }
            if (!isActive || !activityResumed) return@launch
            hudView.setThinking(false)
            val stateIsStillCurrent =
                gameState.currentTurn == thinkingState.currentTurn &&
                    gameState.moveHistory.size == thinkingState.moveHistory.size &&
                    gameState.status == thinkingState.status
            val playerAutoplayStillEnabled = !autoplayingPlayerTurn || autoplayEnabled
            if (move != null && stateIsStillCurrent && playerAutoplayStillEnabled) {
                autoplayMoveInProgress = autoplayingPlayerTurn
                boardView.animateExternalMove(move)
            } else {
                boardView.isLocked = false
            }
        }
    }

    private fun aiControlsCurrentTurn(): Boolean =
        vsAI && (gameState.currentTurn != playerColor || autoplayEnabled)

    private fun resumeComputerTurnIfNeeded() {
        if (!activityResumed ||
            !::boardView.isInitialized ||
            !matchStarted ||
            gameState.status != GameStatus.IN_PROGRESS ||
            !vsAI ||
            !aiControlsCurrentTurn()
        ) return
        triggerAI()
    }

    // ─── HUD helpers ─────────────────────────────────────────────────────────

    private fun updateHud() {
        val wPlaced = (gameState.metadata[MorabarabaBoard.META_W] as? Int) ?: 0
        val bPlaced = (gameState.metadata[MorabarabaBoard.META_B] as? Int) ?: 0
        val pc      = engine.pieceCount
        val wCount  = engine.activePositions.count { gameState.get(it)?.color == PieceColor.WHITE }
        val bCount  = engine.activePositions.count { gameState.get(it)?.color == PieceColor.BLACK }
        val inPlace = wPlaced < pc || bPlaced < pc

        val wFlying = !inPlace && wCount == 3 && wPlaced >= pc
        val bFlying = !inPlace && bCount == 3 && bPlaced >= pc
        val flyingMe = vsAI && (
            (gameState.currentTurn == playerColor && playerColor == PieceColor.WHITE && wFlying) ||
            (gameState.currentTurn == playerColor && playerColor == PieceColor.BLACK && bFlying)
        )
        val aiFlying = vsAI && (
            (playerColor == PieceColor.WHITE && bFlying) ||
            (playerColor == PieceColor.BLACK && wFlying)
        )

        val (sub1, sub2) = if (inPlace) {
            val wLeft = pc - wPlaced; val bLeft = pc - bPlaced
            "W: $wLeft to place  •  B: $bLeft to place" to "$wCount vs $bCount on board"
        } else {
            val flyTag = when {
                flyingMe  -> "✈ You can fly!"
                aiFlying  -> "✈ CPU is flying!"
                wFlying && !vsAI -> "✈ White is flying"
                bFlying && !vsAI -> "✈ Black is flying"
                else -> ""
            }
            "W: $wCount  •  B: $bCount" to flyTag
        }

        val label = if (vsAI && gameState.currentTurn == playerColor) "Your turn"
                    else "${if (gameState.currentTurn == PieceColor.WHITE) "White" else "Black"} to move"
        hudView.update(label, sub1, sub2, canUndo = moveHistory.isNotEmpty(), canRedo = redoGameStates.isNotEmpty())
    }

    @Suppress("DEPRECATION")
    fun onBack()  { onBackPressed() }
    fun onUndo()  { doUndo() }
    fun onRedo()  { doRedo() }
    fun onMenu()  {
        // In-app dialogs do not trigger onPause(), so stop AI and animations
        // before hiding the board behind the menu.
        stopAutomatedGameplay()
        showMenuDialog()
    }

    private fun showMenuDialog() {
        val inProgress = gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()
        val choices = mutableListOf(
            ChessChoiceView.Choice("New Game", if (inProgress) "Start over and forfeit" else "Begin a fresh match", "↻", Color.parseColor("#E3B86A")),
            ChessChoiceView.Choice("How to Play", "Review the essentials", "?", Color.parseColor("#A9B6E8")),
        )
        val actions = mutableListOf<() -> Unit>(
            { if (inProgress) showForfeitDialog() else showModeDialog() },
            { showTutorial(showModeAfter = false) },
        )
        if (vsAI) {
            choices += ChessChoiceView.Choice("CPU Difficulty", "Adjust the challenge", "●", Color.parseColor("#8EC7B9"))
            actions += { showDifficultyDialog() }
        }
        choices += ChessChoiceView.Choice("Main Menu", if (inProgress) "Leave this match" else "Choose another game", "⌂", Color.parseColor("#E58A7A"))
        actions += {
            if (inProgress) showLeaveMatchDialog()
            else {
                clearPausedMatch()
                finish()
            }
        }
        showChoiceDialog("Menu", "Choose what to do next.", choices, actions, 620f)
    }

    private fun showForfeitDialog() {
        showChoiceDialog(
            "Forfeit Match?",
            "Starting a new game counts as a forfeit.",
            listOf(
                ChessChoiceView.Choice("Cancel", "Keep playing this match", "↩", Color.parseColor("#A9B6E8")),
                ChessChoiceView.Choice("Forfeit & New Game", "Start a fresh match", "↻", Color.parseColor("#E58A7A")),
            ),
            listOf(
                { showBoardAfterDialog() },
                {
                    if (vsAI) SettingsManager.recordForfeit(this)
                    showModeDialog()
                },
            ),
            470f,
        )
    }

    private fun showDifficultyDialog() {
        val current = SettingsManager.getMorabarabaDifficulty(this)
        val choices = listOf(
            ChessChoiceView.Choice("Easy", if (current == 0) "Current setting" else "A relaxed challenge", "I", Color.parseColor("#8EC7B9")),
            ChessChoiceView.Choice("Medium", if (current == 1) "Current setting" else "A balanced challenge", "II", Color.parseColor("#E3B86A")),
            ChessChoiceView.Choice("Hard", if (current == 2) "Current setting" else "A serious challenge", "III", Color.parseColor("#E58A7A")),
        )
        showChoiceDialog("CPU Difficulty", "Choose the challenge for your next move.", choices, choices.indices.map { which ->
            {
                val changed = which != current
                SettingsManager.setMorabarabaDifficulty(this, which)
                if (changed && gameState.status == GameStatus.IN_PROGRESS && moveHistory.isNotEmpty()) {
                    showRestartDialog()
                } else showBoardAfterDialog()
            }
        }, 520f)
    }

    private fun showRestartDialog() {
        showChoiceDialog(
            "Restart Match?",
            "Difficulty changed. Restart now?",
            listOf(
                ChessChoiceView.Choice("Keep Playing", "Continue this match", "↩", Color.parseColor("#A9B6E8")),
                ChessChoiceView.Choice("Restart", "Start with the new difficulty", "↻", Color.parseColor("#E3B86A")),
            ),
            listOf({ showBoardAfterDialog() }, { showModeDialog() }),
            470f,
        )
    }

    private fun doUndo() {
        if (moveHistory.isEmpty()) return
        aiJob?.cancel(); aiJob = null
        boardView.cancelAnim()
        hudView.setThinking(false)
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
        topCaptureView.update(capturedByBlack)
        bottomCaptureView.update(capturedByWhite)
        boardView.gameState = gameState
        updateHud()
    }

    private fun doRedo() {
        if (redoGameStates.isEmpty()) return
        aiJob?.cancel(); aiJob = null
        boardView.cancelAnim()
        hudView.setThinking(false)
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
        topCaptureView.update(capturedByBlack)
        bottomCaptureView.update(capturedByWhite)
        boardView.gameState = gameState
        updateHud()
    }

    // ─── Results ─────────────────────────────────────────────────────────────

    private fun showResult() {
        if (gameState.status == GameStatus.IN_PROGRESS) return
        val msg = when (gameState.status) {
            GameStatus.WHITE_WINS -> if (vsAI && playerColor == PieceColor.WHITE) "You win! 🎉" else "White wins!"
            GameStatus.BLACK_WINS -> if (vsAI && playerColor == PieceColor.BLACK) "You win! 🎉" else "Black wins!"
            else -> "Draw!"
        }
        val resultLabel = when (gameState.status) {
            GameStatus.WHITE_WINS -> "White wins"
            GameStatus.BLACK_WINS -> "Black wins"
            else -> "Draw"
        }
        showChoiceDialog(
            "Game Over",
            msg,
            listOf(
                ChessChoiceView.Choice("Play Again", "Start a fresh game", "↻", Color.parseColor("#E3B86A")),
                ChessChoiceView.Choice("Main Menu", "Choose another match", "⌂", Color.parseColor("#E58A7A")),
                ChessChoiceView.Choice("Watch Replay", "Review the moves", "▶", Color.parseColor("#A9B6E8")),
            ),
            listOf(
                { showModeDialog() },
                { finish() },
                { launchReplay(resultLabel) },
            ),
            520f,
        )
    }

    // ─── Replay ───────────────────────────────────────────────────────────────

    private fun launchReplay(resultLabel: String) {
        showBoardAfterDialog()
        val movesJson = ReplayActivity.buildMovesJson(gameState.moveHistory)
        startActivity(Intent(this, ReplayActivity::class.java).apply {
            putExtra(ReplayActivity.EXTRA_GAME_TYPE,  "MORABARABA")
            putExtra(ReplayActivity.EXTRA_MOVES_JSON, movesJson)
            putExtra(ReplayActivity.EXTRA_RESULT,     resultLabel)
            putExtra(ReplayActivity.EXTRA_MORABARABA_PIECE_COUNT, pieceCount)
        })
    }

    // ─── Tutorial ────────────────────────────────────────────────────────────

    private fun showTutorial(showModeAfter: Boolean = false) {
        hideBoardWhileDialogIsOpen()
        val rulesText = """
MORABARABA — Rules

Overview
A traditional South African strategy game played on a board of three concentric squares connected by lines. Each player has cows (●).

─────────────────────────

Phase 1 — Placing Cows
Players alternate placing one cow per turn on any empty point.

─────────────────────────

Forming a Mill
When 3 of your cows line up along any marked line, you form a "mill". You immediately remove one of your opponent's cows.
• You cannot remove a cow that is already in a mill — unless those are the only cows left.

─────────────────────────

Phase 2 — Moving Cows
Once all cows are placed, players slide one cow at a time to an adjacent empty point along the lines.
Forming a new mill still wins you a capture.

─────────────────────────

Flying
When a player is reduced to exactly 3 cows, they may "fly" — jump to any empty point instead of sliding.

─────────────────────────

Winning
You win by either:
• Reducing your opponent to fewer than 3 cows, OR
• Leaving your opponent with no legal moves.
            """.trimIndent()
        StyledDialogs.showRules(
            this,
            "Morabaraba",
            rulesText,
            "M O R A B A R A B A",
            onDone = {
                if (!isFinishing) {
                    if (showModeAfter) showModeDialog() else showBoardAfterDialog()
                }
            },
        )
    }

    private fun hideBoardWhileDialogIsOpen() {
        // Keep the game surface mounted under the in-activity overlay.
        gameRoot.visibility = View.VISIBLE
    }

    private fun hideBoardUntilMatchStarts() {
        gameRoot.visibility = View.INVISIBLE
    }

    private fun showBoardAfterDialog(resumeAi: Boolean = true) {
        gameRoot.visibility = View.VISIBLE
        if (resumeAi) resumeComputerTurnIfNeeded()
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

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class MorabarabaHudView(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP   = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP  = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            textSize = 13f * sp.coerceAtMost(3f)
        }
        private val subP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val sub2P = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }
        private val dimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private var title    = "White to move"
        private var sub1     = ""
        private var sub2     = ""
        private var canUndo  = false
        private var canRedo  = false
        private var thinking = false

        private val backRect = RectF()
        private val undoRect = RectF()
        private val redoRect = RectF()
        private val menuRect = RectF()

        fun update(t: String, s1: String, s2: String = "", canUndo: Boolean, canRedo: Boolean) {
            title = t; sub1 = s1; sub2 = s2; this.canUndo = canUndo; this.canRedo = canRedo; invalidate()
        }
        fun setThinking(t: Boolean) { thinking = t; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 44f * dp; val bh = 28f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp,        by, 6f * dp + bw,   by + bh)
            undoRect.set(w - bw * 3.3f,  by, w - bw * 2.2f,  by + bh)
            redoRect.set(w - bw * 2.15f, by, w - bw * 1.1f,  by + bh)
            menuRect.set(w - bw * 1.05f, by, w - 4f * dp,    by + bh)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                when {
                    backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onBack() }
                    undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndo() }
                    redoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onRedo() }
                    menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenu() }
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, h - dp, w, h, divP)

            val rr = 5f * dp
            canvas.drawRoundRect(backRect, rr, rr, btnBgP)
            canvas.drawRoundRect(undoRect, rr, rr, btnBgP)
            canvas.drawRoundRect(redoRect, rr, rr, btnBgP)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgP)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnP.textSize * 0.36f, btnP)
            canvas.drawText("Undo",   undoRect.centerX(), undoRect.centerY() + btnP.textSize * 0.36f,
                if (canUndo) btnP else dimP)
            canvas.drawText("Redo",   redoRect.centerX(), redoRect.centerY() + btnP.textSize * 0.36f,
                if (canRedo) btnP else dimP)
            canvas.drawText("Menu",   menuRect.centerX(), menuRect.centerY() + btnP.textSize * 0.36f, btnP)

            val cx = w / 2f
            val titleStr = if (thinking) "Thinking…" else title
            canvas.drawText(titleStr, cx, h * 0.24f, txtP)
            canvas.drawText(sub1, cx, h * 0.70f, subP)
            if (sub2.isNotEmpty()) canvas.drawText(sub2, cx, h * 0.88f, sub2P)
        }
    }
}
