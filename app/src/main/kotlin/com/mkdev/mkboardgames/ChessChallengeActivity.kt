package com.mkdev.mkboardgames

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.challenges.ChallengeObjective
import com.mkdev.mkboardgames.challenges.ChallengeCondition
import com.mkdev.mkboardgames.challenges.ChessChallengeAutoplayAi
import com.mkdev.mkboardgames.challenges.ChessChallengeAutoplayProgress
import com.mkdev.mkboardgames.challenges.ChessPuzzle
import com.mkdev.mkboardgames.challenges.ChessPuzzleData
import com.mkdev.mkboardgames.challenges.PromotionRequirement
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.chess.*
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.BoardStyleSwitchView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.ChallengeSection
import com.mkdev.mkboardgames.ui.ChallengeLevelGridView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.AutoplayButtonView
import com.mkdev.mkboardgames.ui.StandardGameHudView

class ChessChallengeActivity : AppCompatActivity() {

    private val engine = ChessRuleEngine()
    private val autoplayAi by lazy { ChessChallengeAutoplayAi(engine) }
    private val easyChessAi by lazy {
        val profile = SettingsManager.chessAiProfileForLevel(0)
        AIPlayer(
            engine = engine,
            maxDepth = profile.depth,
            timeLimitMs = profile.timeLimitMs,
            quiesceDepth = profile.quiesceDepth,
        )
    }
    private val handler = Handler(Looper.getMainLooper())
    private val puzzles = ChessPuzzleData.all
    private var selectedLevel = 1
    private var currentPuzzle: ChessPuzzle? = null
    private var puzzleState: GameState? = null
    private var playerStartState: GameState? = null
    private var solutionMoves: List<String> = emptyList()
    private var solutionLines: List<List<String>> = emptyList()
    private var expectedMoveIndex = 1
    private var playerColor = PieceColor.WHITE
    private var playerMovesMade = 0
    private var maxPlayerMoves = 1
    private var playerPiecesLost = 0
    private var opponentPiecesCaptured = 0
    private var opponentCapturedPlayerPiece = false
    private var playerPromotions = 0
    private val playerPromotionTypes = mutableListOf<String>()
    private var initialPlayerPiecesLost = 0
    private var initialOpponentPiecesCaptured = 0
    private var initialOpponentCapturedPlayerPiece = false
    private var opponentMoveAnimating = false
    private var pendingOpponentReply: Move? = null
    private var lastPlayerMove: Move? = null
    private var lastPlayerMoveBeforeState: GameState? = null
    private var lastPlayerMoveState: GameState? = null
    private var boardView: BoardView? = null
    private var gameHud: StandardGameHudView? = null
    private var moveHistoryView: TextView? = null
    private var capturedPiecesView: TextView? = null
    private var challengeStatusView: TextView? = null
    private var screenRoot: FrameLayout? = null
    private var activeOverlay: View? = null
    private var completed = false
    private var resetting = false
    private var hintActive = false
    private var hintUsed = false
    private var pauseOverlayOpen = false
    private var opponentReplyPending = false
    private var autoplayEnabled = false
    private var autoplayMoveInProgress = false
    private var autoplayButton: AutoplayButtonView? = null
    private val autoplayRunnable = Runnable { playAutoplayMove() }
    private var attemptStarted = false
    private var retryCount = 0
    private var attemptNumber = 0
    private var playerMovedQueen = false
    private var playerCastledKingside = false
    private var playerUsedNonKnightCapture = false
    private var opponentCapturedWhiteBishop = false
    private var promotedPawn = false
    private var startingMaterialDeficit = 0
    private var playerCapturedBlackKnights = 0
    private val playerCapturedSymbols = mutableListOf<String>()
    private val playerLostSymbols = mutableListOf<String>()
    private val initialPlayerCapturedSymbols = mutableListOf<String>()
    private val initialPlayerLostSymbols = mutableListOf<String>()

    private val progressPrefs by lazy {
        getSharedPreferences("chess_challenge_progress", Context.MODE_PRIVATE)
    }

    private val highestCompleted: Int
        get() = progressPrefs.getInt(KEY_HIGHEST_COMPLETED, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundPlayer.init(this)
        makeFullscreen()
        selectedLevel = intent.getIntExtra(EXTRA_LEVEL, 1).coerceIn(1, puzzles.size)
        showLevelList()
    }

    override fun onResume() {
        super.onResume()
        makeFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            makeFullscreen()
            window.decorView.postDelayed({ makeFullscreen() }, 200L)
        }
    }

    override fun onPause() {
        SoundPlayer.stopAll()
        stopAutoplay()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeOverlay != null) {
            val terminal = completed || resetting
            val wasPause = pauseOverlayOpen
            dismissOverlay()
            when {
                wasPause -> resumeFromPause()
                terminal -> {
                    MusicPlayer.resumeMatch(this)
                    showLevelList()
                }
                else -> {
                    boardView?.isLocked = false
                    MusicPlayer.resumeMatch(this)
                }
            }
        } else if (boardView != null) {
            showPauseScreen()
        } else {
            finish()
        }
    }

    private fun showLevelList() {
        handler.removeCallbacksAndMessages(null)
        dismissOverlay()
        stopAutoplay()
        boardView = null
        gameHud = null
        autoplayButton = null
        val root = verticalRoot()
        val challengeSummary = "15 Missing Piece + 15 Limited Moves chess challenges"
        root.addView(topBar("CHESS CHALLENGES", challengeSummary) {
            finish()
        })

        val summary = TextView(this).apply {
            val earnedStars = (1..puzzles.size).sumOf { progressPrefs.getInt(starsKey(it), 0) }
            val solved = (1..puzzles.size).count { progressPrefs.getInt(starsKey(it), 0) > 0 }
            val attempts = (1..puzzles.size).sumOf { progressPrefs.getInt(attemptsKey(it), 0) }
            text = "Checkmate, stalemate, or check within a move limit.\nCompleted: $solved / ${puzzles.size} • Highest completed: ${highestCompleted.coerceAtMost(puzzles.size)} • Stars: $earnedStars • Attempts: $attempts"
            setTextColor(Color.parseColor("#B7C9D1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(20), dp(13), dp(20), dp(12))
        }
        root.addView(summary)

        val levelGrid = ChallengeLevelGridView(
            this,
            puzzles.size,
            highestCompleted,
            IntArray(puzzles.size) { progressPrefs.getInt(starsKey(it + 1), 0) },
            subtitles = puzzles.map { it.title },
            lockFutureChallenges = false,
            sections = listOf(
                ChallengeSection(1, "Missing Piece"),
                ChallengeSection(16, "Limited Moves"),
            ),
        ).apply {
            onLevelSelected = { level ->
                selectedLevel = level
                showPuzzle(level)
            }
            onLockedLevelSelected = { level ->
                Toast.makeText(
                    this@ChessChallengeActivity,
                    "This challenge is available from the challenge list.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        root.addView(levelGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showPuzzle(level: Int) {
        handler.removeCallbacksAndMessages(null)
        opponentReplyPending = false
        dismissOverlay()
        stopAutoplay()
        selectedLevel = level.coerceIn(1, puzzles.size)
        currentPuzzle = puzzles[selectedLevel - 1]
        MusicPlayer.enterMatch(this)
        completed = false
        resetting = false
        hintActive = false
        hintUsed = false
        attemptStarted = false
        retryCount = 0
        opponentMoveAnimating = false
        moveHistoryView = null
        capturedPiecesView = null
        challengeStatusView = null
        val root = verticalRoot()
        val compactLayout = resources.displayMetrics.heightPixels /
            resources.displayMetrics.density < 680f

        val hud = StandardGameHudView(
            this,
            showHistoryControls = false,
            labelTextSizeSp = 15f,
            backLabel = "Back",
             sideLabel = "CHALLENGE ${selectedLevel.toString().padStart(2, '0')}",
            showHintControl = true,
            showMenuControl = false,
        ).apply {
            setInfo(
                value = "MOVES",
                undo = false,
                detail = "",
                accentColor = Color.parseColor("#F7D99B"),
            )
            onBack = { showPauseScreen() }
            onHint = {
                if (!completed && !resetting) toggleHint()
            }
        }
        gameHud = hud
        root.addView(hud, LinearLayout.LayoutParams(-1, dp(if (compactLayout) 52 else 56)))

        val styleRow = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            setBackgroundColor(Color.parseColor("#102C32"))
        }
        val styleSwitch = BoardStyleSwitchView(this).apply {
            setStyleCount(ChessBoardStyle.entries.size)
            setSelectedIndex(
                progressPrefs.getInt(KEY_BOARD_STYLE, ChessBoardStyle.CANVAS.ordinal)
                    .coerceIn(0, ChessBoardStyle.entries.lastIndex),
                animate = false,
            )
        }
        styleSwitch.onStyleChanged = { index ->
            boardView?.chessBoardStyle = ChessBoardStyle.entries[index]
            progressPrefs.edit().putInt(KEY_BOARD_STYLE, index).apply()
        }
        styleRow.addView(
            styleSwitch,
            LinearLayout.LayoutParams(
                dp(if (compactLayout) 108 else 118),
                dp(if (compactLayout) 38 else 44),
            ),
        )
        root.addView(styleRow, LinearLayout.LayoutParams(-1, dp(if (compactLayout) 38 else 44)))

        val historyView = TextView(this).apply {
            setTextColor(Color.parseColor("#AFC6CC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = android.view.Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(14), dp(2), dp(14), dp(2))
            setBackgroundColor(Color.parseColor("#081821"))
        }
        moveHistoryView = historyView
        root.addView(historyView, LinearLayout.LayoutParams(-1, dp(if (compactLayout) 28 else 34)))

        val capturesView = TextView(this).apply {
            setTextColor(Color.parseColor("#D7E4DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.parseColor("#0B2028"))
        }
        capturedPiecesView = capturesView
        root.addView(capturesView, LinearLayout.LayoutParams(-1, dp(if (compactLayout) 22 else 28)))

        val statusView = TextView(this).apply {
            setTextColor(Color.parseColor("#D7E4DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compactLayout) 10f else 11f)
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setBackgroundColor(Color.parseColor("#0B2028"))
        }
        challengeStatusView = statusView
        root.addView(statusView, LinearLayout.LayoutParams(-1, dp(if (compactLayout) 36 else 44)))

        val autoplay = AutoplayButtonView(this).apply {
            onAutoplayChanged = { enabled ->
                autoplayEnabled = enabled
                if (enabled) {
                    scheduleAutoplayMove(160L)
                } else {
                    handler.removeCallbacks(autoplayRunnable)
                    autoplayMoveInProgress = false
                    if (!completed && !resetting && !opponentMoveAnimating && !opponentReplyPending) {
                        boardView?.isLocked = false
                    }
                }
            }
        }
        autoplayButton = autoplay
        root.addView(
            autoplay,
            LinearLayout.LayoutParams(dp(104), dp(if (compactLayout) 58 else 66)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            },
        )

        val board = BoardView(this).apply {
            ruleEngine = engine
            chessBoardStyle = ChessBoardStyle.CANVAS
            isFlipped = false
            isFocusable = true
            contentDescription = "Chess challenge board. Complete the custom win condition."
            onMoveMade = ::handlePlayerMove
            onPromotionChoice = ::showPromotionChoice
            onGameOverTapped = {
                if (activeOverlay == null && (completed || resetting)) {
                    if (completed) showCompletionScreen() else showFailureScreen()
                }
            }
            isLocked = true
        }
        boardView = board
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))

        screenRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#102C32"))
            addView(root, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(screenRoot!!)
        val puzzle = currentPuzzle!!
        loadPuzzle(puzzle)
        renderHistory()
    }

    private fun setChallengeStatus(text: String) {
        renderHistory()
        challengeStatusView?.text = text.replace('\n', ' ').take(120)
        gameHud?.setInfo(
            value = moveCounterText(),
            undo = false,
            detail = "",
            accentColor = Color.parseColor("#F7D99B"),
        )
    }

    private fun loadPuzzle(puzzle: ChessPuzzle) {
        val initial = ChessFenParser.parse(puzzle.fen)
        solutionLines = emptyList()
        solutionMoves = emptyList()
        expectedMoveIndex = 0
        playerMovesMade = 0
        pendingOpponentReply = null
        lastPlayerMove = null
        lastPlayerMoveBeforeState = null
        lastPlayerMoveState = null
        maxPlayerMoves = puzzle.objective.targetPlayerMoves ?: Int.MAX_VALUE
        playerColor = initial.currentTurn
        startingMaterialDeficit = materialDeficit(initial)
        initialPlayerPiecesLost = 0
        initialOpponentPiecesCaptured = 0
        initialOpponentCapturedPlayerPiece = false
        playerCapturedSymbols.clear()
        playerLostSymbols.clear()
        initialPlayerCapturedSymbols.clear()
        initialPlayerLostSymbols.clear()
        resetConditionTracking()
        puzzleState = initial
        playerStartState = initial
        boardView?.gameState = initial
        boardView?.isFlipped = initial.currentTurn == PieceColor.BLACK
        boardView?.isLocked = false
        setChallengeStatus(objectiveText())
    }

    private fun scheduleAutoplayMove(delayMs: Long = 220L) {
        if (!autoplayEnabled || completed || resetting || opponentMoveAnimating ||
            opponentReplyPending || activeOverlay != null
        ) return
        handler.removeCallbacks(autoplayRunnable)
        handler.postDelayed(autoplayRunnable, delayMs)
    }

    private fun playAutoplayMove() {
        if (!autoplayEnabled || completed || resetting || opponentMoveAnimating ||
            opponentReplyPending || activeOverlay != null
        ) return
        val state = puzzleState ?: return
        if (state.status != GameStatus.IN_PROGRESS || state.currentTurn != playerColor) return

        if (!attemptStarted) {
            beginAttempt()
            attemptStarted = true
        }
        val puzzle = currentPuzzle ?: return
        val move = autoplayAi.choosePlayerMove(
            state = state,
            puzzle = puzzle,
            playerColor = playerColor,
            progress = autoplayProgress(),
        )
        if (move == null) {
            showWrongMove()
            return
        }
        autoplayMoveInProgress = true
        boardView?.isLocked = true
        setChallengeStatus("Autoplay is selecting an objective-safe move…")
        boardView?.animateExternalMove(move)
    }

    private fun handlePlayerMove(move: Move) {
        if (opponentMoveAnimating) {
            opponentMoveAnimating = false
            applyOpponentReply(move)
            return
        }
        if (completed || resetting) return
        val state = puzzleState ?: return
        if (!attemptStarted) {
            beginAttempt()
            attemptStarted = true
        }

        autoplayMoveInProgress = false
        hintActive = false
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        val acceptedMove = findMove(state, move.toUci())
        if (acceptedMove == null) {
            showWrongMove()
            return
        }
        if (violatesChallengeRule(state, acceptedMove)) {
            showConditionFailure()
            return
        }
        recordMoveEffects(state, acceptedMove, playerColor)
        val nextState = engine.applyMove(state, acceptedMove)
        if (nextState === state) {
            showWrongMove()
            return
        }
        puzzleState = nextState
        playerMovesMade++
        expectedMoveIndex++
        lastPlayerMove = acceptedMove
        lastPlayerMoveBeforeState = state
        lastPlayerMoveState = nextState
        boardView?.gameState = puzzleState!!

        if (nextState.status != GameStatus.IN_PROGRESS || isLimitedMovesSuccess(nextState)) {
            finishAfterFinalPosition(nextState)
        } else {
            boardView?.isLocked = true
            opponentReplyPending = true
            setChallengeStatus("Opponent is preparing a reply…")
            handler.postDelayed({ playOpponentReply() }, 380L)
        }
    }

    private fun playOpponentReply() {
        if (completed || resetting) return
        opponentReplyPending = false
        val state = puzzleState ?: return
        val reply = chooseOpponentReply(state)
        if (reply == null) {
            showWrongMove()
            return
        }
        pendingOpponentReply = reply
        opponentMoveAnimating = true
        boardView?.isLocked = true
        setChallengeStatus("Opponent is moving…")
        boardView?.animateExternalMove(reply)
    }

    private fun chooseOpponentReply(state: GameState): Move? {
        if (autoplayEnabled) {
            return autoplayAi.chooseOpponentMove(
                state = state,
                puzzle = currentPuzzle ?: return null,
                playerColor = playerColor,
                progress = autoplayProgress(),
            )
        }
        return easyChessAi.bestMove(state)
            ?: engine.allLegalMoves(state, state.currentTurn).firstOrNull()
    }

    private fun applyOpponentReply(move: Move) {
        val state = puzzleState ?: return
        val reply = pendingOpponentReply
        pendingOpponentReply = null
        if (reply == null || move.toUci() != reply.toUci()) {
            showWrongMove()
            return
        }
        recordMoveEffects(state, reply, playerColor.opponent())
        val nextState = engine.applyMove(state, reply)
        if (nextState === state) {
            showWrongMove()
            return
        }
        puzzleState = nextState
        expectedMoveIndex++
        boardView?.gameState = nextState
        boardView?.setHintMove(null)
        boardView?.isFlipped = nextState.currentTurn == PieceColor.BLACK
        if (nextState.status != GameStatus.IN_PROGRESS) {
            finishAfterFinalPosition(nextState)
        } else if (playerMovesMade >= maxPlayerMoves) {
            finishAfterFinalPosition(nextState)
        } else {
            opponentReplyPending = false
            boardView?.isLocked = false
            setChallengeStatus(objectiveText())
            scheduleAutoplayMove()
        }
    }

    private fun finishAfterFinalPosition(state: GameState) {
        if (state.status == GameStatus.IN_PROGRESS && playerMovesMade < maxPlayerMoves) {
            boardView?.isLocked = false
            setChallengeStatus(objectiveText())
            return
        }
        if (challengeSatisfied(state)) {
            completeLevel()
        } else {
            showConditionFailure()
        }
    }

    private fun challengeSatisfied(state: GameState): Boolean {
        if (playerMovesMade > maxPlayerMoves) return false
        if (currentPuzzle?.condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT) {
            return isLimitedMovesSuccess(state)
        }
        if (!isSuccessfulResolution(state)) {
            return false
        }
        return conditionSatisfied(state)
    }

    private fun completeLevel() {
        stopAutoplay()
        completed = true
        opponentMoveAnimating = false
        boardView?.isLocked = true
        gameHud?.controlsEnabled = false
        SoundPlayer.play("game_end")
        if (selectedLevel > highestCompleted) {
            progressPrefs.edit().putInt(KEY_HIGHEST_COMPLETED, selectedLevel).apply()
        }
        val stars = earnedStars()
        val previousStars = progressPrefs.getInt(starsKey(selectedLevel), 0)
        if (stars > previousStars) {
            progressPrefs.edit().putInt(starsKey(selectedLevel), stars).apply()
        }
        val resultLabel = when {
            currentPuzzle?.condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT &&
                puzzleState?.status == GameStatus.IN_PROGRESS -> "Check!"
            puzzleState?.status == GameStatus.DRAW -> "Stalemate!"
            else -> "Checkmate!"
        }
        setChallengeStatus("$resultLabel Challenge complete • ${"★".repeat(stars)} • Attempt $attemptNumber")
        markResultBoard(victory = true)
        showCompletionScreen()
    }

    private fun showWrongMove() {
        stopAutoplay()
        resetting = true
        opponentMoveAnimating = false
        pendingOpponentReply = null
        opponentReplyPending = false
        boardView?.isLocked = true
        gameHud?.controlsEnabled = false
        hintActive = false
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        setChallengeStatus(failureMessage())
        markResultBoard()
        showFailureScreen()
    }

    private fun resetPuzzle() {
        handler.removeCallbacksAndMessages(null)
        stopAutoplay()
        dismissOverlay()
        MusicPlayer.resumeMatch(this)
        val start = playerStartState ?: return
        puzzleState = start
        expectedMoveIndex = 0
        playerMovesMade = 0
        resetConditionTracking()
        completed = false
        resetting = false
        hintActive = false
        hintUsed = false
        pauseOverlayOpen = false
        attemptStarted = false
        retryCount++
        opponentMoveAnimating = false
        pendingOpponentReply = null
        opponentReplyPending = false
        lastPlayerMove = null
        lastPlayerMoveBeforeState = null
        lastPlayerMoveState = null
        boardView?.gameState = start
        boardView?.isFlipped = start.currentTurn == PieceColor.BLACK
        boardView?.isLocked = false
        gameHud?.controlsEnabled = true
        gameHud?.setHintActive(false, animate = false)
        boardView?.setHintMove(null)
        setChallengeStatus(objectiveText())
    }

    private fun showPromotionChoice(choices: List<Move>) {
        val available = choices.filter { it.promotionType != null }
        if (available.isEmpty()) return
        val view = ChessChoiceView(
            this,
            title = "Choose Promotion",
            subtitle = "Select the piece for your advancing pawn.",
            choices = available.map {
                ChessChoiceView.Choice(
                    it.promotionType!!.lowercase().replaceFirstChar(Char::uppercaseChar),
                    "Continue the challenge",
                    it.promotionType!!.first().toString(),
                    GOLD,
                )
            },
            gameLabel = "C H E S S",
            fullScreenOverride = true,
        )
        view.onChoiceSelected = { which ->
            dismissOverlay()
            boardView?.animateExternalMove(available[which])
        }
        showOverlay(view)
    }

    private fun toggleHint() {
        if (currentPuzzle == null || puzzleState == null) return
        hintUsed = true
        hintActive = !hintActive
        val move = expectedMoveForCurrentState()
        boardView?.setHintMove(if (hintActive) move else null)
        gameHud?.setHintActive(hintActive)
        setChallengeStatus(
            when {
                !hintActive -> objectiveText()
                move == null -> "No hint is available for this position."
                else -> "Hint: highlighted next move"
            },
        )
    }

    private fun expectedMoveForCurrentState(): Move? {
        val state = puzzleState ?: return null
        val recommended = currentPuzzle?.recommendedMoves.orEmpty()
            .asSequence()
            .mapNotNull { findMove(state, it) }
            .firstOrNull()
        return recommended ?: engine.allLegalMoves(state, state.currentTurn).firstOrNull()
    }

    private fun objectiveText(): String {
        return currentPuzzle?.winCondition ?: "Complete the challenge"
    }

    private fun moveCounterText(): String {
        if (maxPlayerMoves == Int.MAX_VALUE) return "OPEN"
        val remaining = (maxPlayerMoves - playerMovesMade).coerceAtLeast(0)
        return if (remaining == 1) "1 MOVE" else "$remaining MOVES"
    }

    private fun autoplayProgress() = ChessChallengeAutoplayProgress(
        startingMaterialDeficit = startingMaterialDeficit,
        playerMovedQueen = playerMovedQueen,
        playerCastledKingside = playerCastledKingside,
        playerUsedNonKnightCapture = playerUsedNonKnightCapture,
        promotedPawn = promotedPawn,
        playerPromotionTypes = playerPromotionTypes.toList(),
        playerCapturedBlackKnights = playerCapturedBlackKnights,
        opponentCapturedWhiteBishop = opponentCapturedWhiteBishop,
        lastPlayerMoveWasRook = lastPlayerMove?.let { move ->
            lastPlayerMoveBeforeState?.get(move.from) as? ChessPiece
        }?.type == ChessPieceType.ROOK,
    )

    private fun stopAutoplay() {
        handler.removeCallbacks(autoplayRunnable)
        autoplayEnabled = false
        autoplayMoveInProgress = false
        autoplayButton?.setAutoplayEnabled(false, animate = false)
    }

    private fun resetConditionTracking() {
        playerPiecesLost = initialPlayerPiecesLost
        opponentPiecesCaptured = initialOpponentPiecesCaptured
        opponentCapturedPlayerPiece = initialOpponentCapturedPlayerPiece
        playerPromotions = 0
        playerPromotionTypes.clear()
        playerCapturedSymbols.clear()
        playerCapturedSymbols += initialPlayerCapturedSymbols
        playerLostSymbols.clear()
        playerLostSymbols += initialPlayerLostSymbols
        playerMovedQueen = false
        playerCastledKingside = false
        playerUsedNonKnightCapture = false
        opponentCapturedWhiteBishop = false
        promotedPawn = false
        playerCapturedBlackKnights = 0
    }

    private fun recordMoveEffects(state: GameState, move: Move, mover: PieceColor) {
        val movingPiece = state.get(move.from) as? ChessPiece
        move.captures.forEach { capturePosition ->
            val capturedPiece = state.get(capturePosition) as? ChessPiece
            val capturedColor = capturedPiece?.color
            val capturedSymbol = capturedPiece?.symbol() ?: "?"
            if (capturedColor == playerColor) {
                playerPiecesLost++
                playerLostSymbols += capturedSymbol
                if (mover == playerColor.opponent()) {
                    opponentCapturedPlayerPiece = true
                    if (capturedPiece?.type == ChessPieceType.BISHOP) {
                        opponentCapturedWhiteBishop = true
                    }
                }
            } else if (capturedColor == playerColor.opponent()) {
                opponentPiecesCaptured++
                playerCapturedSymbols += capturedSymbol
                if (mover == playerColor) {
                    if (capturedPiece?.type == ChessPieceType.KNIGHT) {
                        playerCapturedBlackKnights++
                    }
                }
            }
        }
        if (mover == playerColor) {
            if (movingPiece?.type == ChessPieceType.QUEEN) playerMovedQueen = true
            if (move.metadata["castle"] == "K") playerCastledKingside = true
            if (move.isCapture && movingPiece?.type != ChessPieceType.KNIGHT) {
                playerUsedNonKnightCapture = true
            }
            if (move.promotionType != null) {
                playerPromotions++
                playerPromotionTypes += move.promotionType
                promotedPawn = true
            }
        }
    }

    private fun conditionSatisfied(state: GameState): Boolean {
        val puzzle = currentPuzzle ?: return false
        val condition = puzzle.condition
        if (condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT) {
            return isOpponentInCheck(state)
        }
        if (condition == ChallengeCondition.CHECKMATE_WITHIN_LIMIT) {
            return isSuccessfulResolution(state)
        }
        return when (condition) {
            ChallengeCondition.NO_QUEEN_USE -> !playerMovedQueen
            ChallengeCondition.KNIGHT_HUNTER ->
                playerCapturedBlackKnights >= 2
            ChallengeCondition.PROMOTE_AND_WIN ->
                promotedPawn && promotionRequirementSatisfied()
            ChallengeCondition.CASTLE_AND_WIN -> playerCastledKingside
            ChallengeCondition.PRESERVE_BISHOPS -> !opponentCapturedWhiteBishop &&
                whiteBishopsOnBoard(state) >= 2
            ChallengeCondition.ROOK_CHECKMATE -> {
                val before = lastPlayerMoveBeforeState ?: return false
                val move = lastPlayerMove ?: return false
                (before.get(move.from) as? ChessPiece)?.type == ChessPieceType.ROOK &&
                    isPlayerVictory(state)
            }
            ChallengeCondition.MATERIAL_COMEBACK -> startingMaterialDeficit >= 5
            ChallengeCondition.KNIGHT_CAPTURE_ONLY -> !playerUsedNonKnightCapture
            ChallengeCondition.CHECKMATE_WITHIN_LIMIT -> true
            ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT -> true
        }
    }

    private val objective: ChallengeObjective
        get() = currentPuzzle?.objective
            ?: ChallengeObjective(ChallengeCondition.CHECKMATE_WITHIN_LIMIT)

    private fun promotionRequirementSatisfied(): Boolean {
        val requirement = objective.requiredPromotion
        if (requirement == PromotionRequirement.ANY) return true
        return playerPromotionTypes.all { it == requirement.name } &&
            playerPromotionTypes.isNotEmpty()
    }

    private fun violatesChallengeRule(state: GameState, move: Move): Boolean {
        val movingPiece = state.get(move.from) as? ChessPiece ?: return false
        return when (currentPuzzle?.condition) {
            ChallengeCondition.NO_QUEEN_USE ->
                movingPiece.type == ChessPieceType.QUEEN || move.promotionType == "QUEEN"
            ChallengeCondition.KNIGHT_HUNTER ->
                move.captures
                    .mapNotNull { state.get(it) as? ChessPiece }
                    .any { captured ->
                        captured.color == playerColor.opponent() &&
                            playerCapturedBlackKnights < 2 &&
                            captured.type != ChessPieceType.KNIGHT
                    }
            ChallengeCondition.KNIGHT_CAPTURE_ONLY ->
                move.isCapture && movingPiece.type != ChessPieceType.KNIGHT
            else -> false
        }
    }

    private fun failureMessage(): String {
        val puzzle = currentPuzzle ?: return "The challenge was not completed."
        if (puzzle.objective.targetPlayerMoves != null &&
            playerMovesMade >= maxPlayerMoves &&
            puzzleState?.status == GameStatus.IN_PROGRESS
        ) {
            return if (puzzle.condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT) {
                "The check move limit was reached."
            } else {
                "The checkmate move limit was reached."
            }
        }
        return when (puzzle.condition) {
            ChallengeCondition.CHECKMATE_WITHIN_LIMIT ->
                "The position was not resolved by checkmate or stalemate within the move limit."
            ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT ->
                "The black king was not put in check within the move limit."
            ChallengeCondition.NO_QUEEN_USE ->
                "A queen was used. Retry without moving or promoting to a queen."
            ChallengeCondition.KNIGHT_HUNTER ->
                "Capture both black knights before taking any other black piece."
            ChallengeCondition.PROMOTE_AND_WIN ->
                "Promote the a-pawn before winning the game."
            ChallengeCondition.CASTLE_AND_WIN ->
                "Castle kingside before winning the game."
            ChallengeCondition.PRESERVE_BISHOPS ->
                "Both white bishops must survive the winning game."
            ChallengeCondition.ROOK_CHECKMATE ->
                "The final checkmate must be delivered by a rook."
            ChallengeCondition.MATERIAL_COMEBACK ->
                "The starting position must be converted into a winning comeback."
            ChallengeCondition.KNIGHT_CAPTURE_ONLY ->
                "Only a knight may capture pieces in this challenge."
        }
    }

    private fun earnedStars(): Int = when {
        retryCount > 0 -> 1
        hintUsed -> 2
        maxPlayerMoves == Int.MAX_VALUE || playerMovesMade <= maxPlayerMoves -> 3
        else -> 1
    }

    private fun renderHistory() {
        val state = puzzleState ?: return
        val moves = state.moveHistory.mapIndexed { index, move ->
            "${index + 1}.${if (index % 2 == 0) ".." else ""}${move.toNotation()}"
        }
        moveHistoryView?.text = if (moves.isEmpty()) {
            "Moves: —"
        } else {
            "Moves: ${moves.joinToString(" ")}"
        }
        val captured = playerCapturedSymbols.joinToString(" ").ifEmpty { "—" }
        val lost = playerLostSymbols.joinToString(" ").ifEmpty { "—" }
        capturedPiecesView?.text = "Captured: $captured   Lost: $lost"
    }

    private fun starsKey(level: Int) = "level_${level}_stars"

    private fun attemptsKey(level: Int) = "level_${level}_attempts"

    private fun beginAttempt() {
        attemptNumber = progressPrefs.getInt(attemptsKey(selectedLevel), 0) + 1
        progressPrefs.edit().putInt(attemptsKey(selectedLevel), attemptNumber).apply()
    }

    private fun showConditionFailure() {
        stopAutoplay()
        resetting = true
        opponentMoveAnimating = false
        boardView?.isLocked = true
        gameHud?.controlsEnabled = false
        hintActive = false
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        setChallengeStatus(failureMessage())
        markResultBoard()
        showFailureScreen()
    }

    private fun markResultBoard(victory: Boolean = false) {
        val state = puzzleState ?: return
        val resultStatus = when {
            victory && state.status == GameStatus.DRAW -> GameStatus.DRAW
            victory && playerColor == PieceColor.WHITE -> GameStatus.WHITE_WINS
            victory -> GameStatus.BLACK_WINS
            playerColor == PieceColor.WHITE -> GameStatus.BLACK_WINS
            else -> GameStatus.WHITE_WINS
        }
        boardView?.gameState = state.copy(status = resultStatus)
        boardView?.isLocked = true
    }

    private fun isPlayerVictory(state: GameState): Boolean {
        val playerWin = if (playerColor == PieceColor.WHITE) {
            GameStatus.WHITE_WINS
        } else {
            GameStatus.BLACK_WINS
        }
        return state.status == playerWin
    }

    private fun isSuccessfulResolution(state: GameState): Boolean =
        isPlayerVictory(state) || state.status == GameStatus.DRAW

    private fun isOpponentInCheck(state: GameState): Boolean =
        engine.isInCheck(state, playerColor.opponent())

    private fun isLimitedMovesSuccess(state: GameState): Boolean =
        currentPuzzle?.condition == ChallengeCondition.CHECK_OR_CHECKMATE_WITHIN_LIMIT &&
            isOpponentInCheck(state)

    private fun showPauseScreen() {
        if (activeOverlay != null || boardView == null) return
        stopAutoplay()
        boardView?.isLocked = true
        if (opponentMoveAnimating) {
            boardView?.cancelMoveAnimation()
            opponentMoveAnimating = false
            opponentReplyPending = true
        }
        if (opponentReplyPending) {
            handler.removeCallbacksAndMessages(null)
        }
        MusicPlayer.enterPausedMatch(this)
        showOverlay(
            ChessChoiceView(
                this,
                title = "Paused",
                subtitle = "The position is saved while you decide what to do next.",
                choices = listOf(
                    ChessChoiceView.Choice("Resume", "Return to the board", "▶", GOLD),
                    ChessChoiceView.Choice("Restart Challenge", "Try this position again", "↻", GOLD),
                    ChessChoiceView.Choice("Home", "Return to the challenge home", "⌂", GOLD),
                ),
                gameLabel = "C H E S S",
                fullScreenOverride = true,
            ).apply {
                onChoiceSelected = { which ->
                    when (which) {
                        0 -> {
                            resumeFromPause()
                        }
                        1 -> resetPuzzle()
                        else -> returnToLevelList()
                    }
                }
            },
        )
        pauseOverlayOpen = true
    }

    private fun resumeFromPause() {
        dismissOverlay()
        pauseOverlayOpen = false
        MusicPlayer.resumeMatch(this)
        if (completed || resetting) return
        if (opponentMoveAnimating) return
        if (opponentReplyPending) {
            handler.postDelayed({ playOpponentReply() }, 120L)
        } else {
            boardView?.isLocked = false
        }
    }

    private fun returnToLevelList() {
        handler.removeCallbacksAndMessages(null)
        stopAutoplay()
        dismissOverlay()
        MusicPlayer.resumeMatch(this)
        showLevelList()
    }

    private fun showFailureScreen() {
        pauseOverlayOpen = false
        showOverlay(
            ChessChoiceView(
                this,
                title = "Game Over",
                subtitle = failureMessage(),
                choices = listOf(
                    ChessChoiceView.Choice("Retry Challenge", "Try the position again", "↻", GOLD),
                    ChessChoiceView.Choice("Home", "Return to the challenge home", "⌂", GOLD),
                ),
                gameLabel = "C H E S S",
                fullScreenOverride = false,
                dismissOnEmptyTap = true,
            ).apply {
                onDismissRequested = { dismissOverlay() }
                onChoiceSelected = { which ->
                    when (which) {
                        0 -> resetPuzzle()
                        else -> returnToLevelList()
                    }
                }
            },
            cardOverlay = true,
        )
    }

    private fun showCompletionScreen() {
        val nextLevel = selectedLevel < puzzles.size
        pauseOverlayOpen = false
        showOverlay(
            ChessChoiceView(
                this,
                title = "Challenge Complete",
                subtitle = "${completionSummary()} • ${"★".repeat(earnedStars())}",
                choices = buildList {
                    if (nextLevel) add(ChessChoiceView.Choice("Next Challenge", "Continue the challenge", "→", GOLD))
                    add(ChessChoiceView.Choice("Replay Challenge", "Try this one again", "↻", GOLD))
                    add(ChessChoiceView.Choice("Home", "Return to the challenge home", "⌂", GOLD))
                },
                gameLabel = "C H E S S",
                fullScreenOverride = false,
                dismissOnEmptyTap = true,
            ).apply {
                onDismissRequested = { dismissOverlay() }
                onChoiceSelected = { which ->
                    if (nextLevel && which == 0) showPuzzle(selectedLevel + 1)
                    else if (which == if (nextLevel) 1 else 0) resetPuzzle()
                    else returnToLevelList()
                }
            },
            cardOverlay = true,
        )
    }

    private fun completionSummary(): String {
        val mateLabel = currentPuzzle?.title ?: "Challenge complete"
        val material = if (playerPiecesLost == 0) "No pieces lost" else "$playerPiecesLost piece(s) lost"
        val hint = if (hintUsed) "Hint used: Yes" else "Hint used: No"
        val best = progressPrefs.getInt(starsKey(selectedLevel), earnedStars())
        return "$mateLabel • $material • $hint • Attempt $attemptNumber • Best ${"★".repeat(best)}"
    }

    private fun showOverlay(view: View, cardOverlay: Boolean = false) {
        val root = screenRoot ?: return
        dismissOverlay()
        val overlay = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            if (cardOverlay) {
                addView(
                    View(this@ChessChallengeActivity).apply {
                        setBackgroundColor(Color.argb(184, 0, 0, 0))
                        isClickable = true
                        setOnClickListener {
                            (view as? ChessChoiceView)?.onDismissRequested?.invoke()
                                ?: dismissOverlay()
                        }
                    },
                    FrameLayout.LayoutParams(-1, -1),
                )
            }
            if (cardOverlay) {
                val width = minOf(resources.displayMetrics.widthPixels - dp(48), dp(420))
                addView(
                    view,
                    FrameLayout.LayoutParams(width, -2).apply {
                        gravity = android.view.Gravity.CENTER
                    },
                )
            } else {
                addView(view, FrameLayout.LayoutParams(-1, -1))
            }
        }
        activeOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.requestFocus()
    }

    private fun dismissOverlay() {
        val root = screenRoot
        activeOverlay?.let { root?.removeView(it) }
        activeOverlay = null
        pauseOverlayOpen = false
    }

    private fun findMove(state: GameState, uci: String): Move? {
        if (uci.length < 4) return null
        val from = ChessFenParser.position(uci.substring(0, 2)) ?: return null
        val to = ChessFenParser.position(uci.substring(2, 4)) ?: return null
        val promotion = uci.getOrNull(4)?.uppercaseChar()?.let {
            when (it) {
                'Q' -> "QUEEN"
                'R' -> "ROOK"
                'B' -> "BISHOP"
                'N' -> "KNIGHT"
                else -> null
            }
        }
        return engine.legalMovesFrom(state, from).firstOrNull { move ->
            move.to == to && (promotion == null || move.promotionType == promotion)
        }
    }

    private fun verticalRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#061321"))
    }

    private fun topBar(title: String, subtitle: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(actionButton("‹") { onBack() }, LinearLayout.LayoutParams(dp(48), dp(46)))
            addView(LinearLayout(this@ChessChallengeActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(TextView(this@ChessChallengeActivity).apply {
                    text = title
                    tag = "challenge-title"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                })
                addView(TextView(this@ChessChallengeActivity).apply {
                    text = subtitle
                    tag = "challenge-subtitle"
                    setTextColor(Color.parseColor("#AFC6CC"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = android.view.Gravity.CENTER
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Space(this@ChessChallengeActivity), LinearLayout.LayoutParams(dp(48), dp(46)))
        }

    private fun actionButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = android.view.Gravity.CENTER
        setTextColor(Color.parseColor("#F7D99B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label == "‹") 28f else 12f)
        typeface = Typeface.DEFAULT_BOLD
        background = roundedBackground("#34261B", "#D3A05F", 10f)
        setOnClickListener { action() }
    }

    private fun roundedBackground(fill: String, stroke: String, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(dp(1), Color.parseColor(stroke))
            cornerRadius = dp(radius).toFloat()
        }

    private fun materialDeficit(state: GameState): Int {
        fun score(color: PieceColor): Int =
            state.board.filterIsInstance<ChessPiece>()
                .filter { it.color == color && it.type != ChessPieceType.KING }
                .sumOf { it.type.points }

        return ((score(PieceColor.BLACK) - score(PieceColor.WHITE)) / 100).coerceAtLeast(0)
    }

    private fun whiteBishopsOnBoard(state: GameState): Int =
        state.board.filterIsInstance<ChessPiece>()
            .count { it.color == PieceColor.WHITE && it.type == ChessPieceType.BISHOP }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars(),
            )
            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_LEVEL = "challenge_level"
        private const val KEY_HIGHEST_COMPLETED = "highest_chess_level"
        private const val KEY_BOARD_STYLE = "chess_challenge_board_style"
        private val GOLD = Color.parseColor("#E3B86A")
    }
}

private object ChessFenParser {
    fun parse(fen: String): GameState {
        val fields = fen.trim().split(Regex("\\s+"))
        val board = arrayOfNulls<Piece>(64)
        val ranks = fields.firstOrNull()?.split("/") ?: emptyList()
        ranks.take(8).forEachIndexed { row, rank ->
            var col = 0
            rank.forEach { token ->
                if (token.isDigit()) col += token.digitToInt()
                else if (col < 8) {
                    val color = if (token.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (token.lowercaseChar()) {
                        'k' -> ChessPieceType.KING
                        'q' -> ChessPieceType.QUEEN
                        'r' -> ChessPieceType.ROOK
                        'b' -> ChessPieceType.BISHOP
                        'n' -> ChessPieceType.KNIGHT
                        'p' -> ChessPieceType.PAWN
                        else -> null
                    }
                    if (type != null) board[row * 8 + col] = ChessPiece(type, color)
                    col++
                }
            }
        }
        val castling = fields.getOrNull(2) ?: "-"
        val enPassant = fields.getOrNull(3)?.takeIf { it.length == 2 }?.let { it[0] - 'a' } ?: -1
        return GameState(
            board = board,
            currentTurn = if (fields.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE,
            metadata = mapOf(
                "castleWK" to castling.contains('K'),
                "castleWQ" to castling.contains('Q'),
                "castleBK" to castling.contains('k'),
                "castleBQ" to castling.contains('q'),
                "enPassant" to enPassant,
            ),
        )
    }

    fun position(square: String): Position? {
        if (square.length != 2) return null
        val col = square[0] - 'a'
        val row = 8 - (square[1] - '0')
        return if (row in 0..7 && col in 0..7) Position(row, col) else null
    }
}

private fun Move.toUci(): String {
    val promotion = promotionType?.firstOrNull()?.lowercaseChar()?.toString() ?: ""
    return "${from.colLetter()}${from.rowNumber()}${to.colLetter()}${to.rowNumber()}$promotion"
}

private fun Move.toNotation(): String {
    val capture = if (isCapture) "x" else "-"
    val promotion = promotionType?.let { "=${it.first()}" } ?: ""
    return "${from.colLetter()}${from.rowNumber()}$capture${to.colLetter()}${to.rowNumber()}$promotion"
}

private fun Position.colLetter(): Char = ('a'.code + col).toChar()

private fun Position.rowNumber(): Int = 8 - row
