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
import com.mkdev.mkboardgames.challenges.ChessPuzzle
import com.mkdev.mkboardgames.challenges.ChessPuzzleData
import com.mkdev.mkboardgames.challenges.ChessPuzzleValidator
import com.mkdev.mkboardgames.challenges.PromotionRequirement
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.chess.*
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.BoardStyleSwitchView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.ChallengeLevelGridView
import com.mkdev.mkboardgames.ui.ChessChoiceView
import com.mkdev.mkboardgames.ui.StandardGameHudView

class ChessChallengeActivity : AppCompatActivity() {

    private val engine = ChessRuleEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val puzzles = ChessPuzzleData.all.filter { it.level in 1..25 }
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
    private var boardView: BoardView? = null
    private var gameHud: StandardGameHudView? = null
    private var challengeStatusView: TextView? = null
    private var moveHistoryView: TextView? = null
    private var capturedPiecesView: TextView? = null
    private var screenRoot: FrameLayout? = null
    private var activeOverlay: View? = null
    private var completed = false
    private var resetting = false
    private var hintActive = false
    private var hintStage = 0
    private var hintUsed = false
    private var retryCount = 0
    private var attemptNumber = 0
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
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeOverlay != null) {
            dismissOverlay()
            if (resetting || completed) {
                boardView?.isLocked = true
            } else {
                boardView?.isLocked = false
                MusicPlayer.resumeMatch(this)
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
        boardView = null
        gameHud = null
        challengeStatusView = null
        val root = verticalRoot()
        val levelSummary = if (puzzles.size == 1) "1 level" else "${puzzles.size} progressive levels"
        root.addView(topBar("CHESS CHALLENGES", levelSummary) {
            finish()
        })

        val summary = TextView(this).apply {
            val earnedStars = (1..puzzles.size).sumOf { progressPrefs.getInt(starsKey(it), 0) }
            val solved = (1..puzzles.size).count { progressPrefs.getInt(starsKey(it), 0) > 0 }
            val attempts = (1..puzzles.size).sumOf { progressPrefs.getInt(attemptsKey(it), 0) }
            text = "Progress from clean finishes to captures, traps, promotion, and long calculations.\nSolved: $solved / ${puzzles.size} • Highest: ${highestCompleted.coerceAtMost(puzzles.size)} • Stars: $earnedStars • Attempts: $attempts"
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
        ).apply {
            onLevelSelected = { level ->
                selectedLevel = level
                showPuzzle(level)
            }
        }
        root.addView(levelGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showPuzzle(level: Int) {
        handler.removeCallbacksAndMessages(null)
        dismissOverlay()
        selectedLevel = level.coerceIn(1, puzzles.size)
        currentPuzzle = puzzles[selectedLevel - 1]
        MusicPlayer.enterMatch(this)
        completed = false
        resetting = false
        hintActive = false
        hintStage = 0
        hintUsed = false
        retryCount = 0
        opponentMoveAnimating = false
        challengeStatusView = null
        moveHistoryView = null
        capturedPiecesView = null
        val root = verticalRoot()

        val hud = StandardGameHudView(
            this,
            showHistoryControls = false,
            labelTextSizeSp = 15f,
            backLabel = "Back",
            showHintControl = true,
            showMenuControl = false,
        ).apply {
            setInfo(
                value = "LEVEL ${selectedLevel.toString().padStart(2, '0')}",
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
        root.addView(hud, LinearLayout.LayoutParams(-1, dp(56)))

        val styleRow = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            setBackgroundColor(Color.parseColor("#102C32"))
        }
        val styleSwitch = BoardStyleSwitchView(this).apply {
            setStyleCount(ChessBoardStyle.entries.size)
            setSelectedIndex(ChessBoardStyle.CANVAS.ordinal, animate = false)
        }
        styleSwitch.onStyleChanged = { index ->
            boardView?.chessBoardStyle = ChessBoardStyle.entries[index]
        }
        styleRow.addView(styleSwitch, LinearLayout.LayoutParams(dp(118), dp(44)))
        root.addView(styleRow, LinearLayout.LayoutParams(-1, dp(44)))

        val statusView = TextView(this).apply {
            setTextColor(Color.parseColor("#D7E4DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            maxLines = 3
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundColor(Color.parseColor("#0B2028"))
        }
        challengeStatusView = statusView
        root.addView(statusView, LinearLayout.LayoutParams(-1, dp(58)))

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
        root.addView(historyView, LinearLayout.LayoutParams(-1, dp(34)))

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
        root.addView(capturesView, LinearLayout.LayoutParams(-1, dp(28)))

        val board = BoardView(this).apply {
            ruleEngine = engine
            chessBoardStyle = ChessBoardStyle.CANVAS
            isFlipped = false
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
        challengeStatusView?.text = text
        renderHistory()
        gameHud?.setInfo(
            value = "LEVEL ${selectedLevel.toString().padStart(2, '0')}",
            undo = false,
            detail = "",
            accentColor = Color.parseColor("#F7D99B"),
        )
    }

    private fun loadPuzzle(puzzle: ChessPuzzle) {
        val initial = ChessFenParser.parse(puzzle.fen)
        beginAttempt()
        solutionLines = ChessPuzzleValidator.playableSolutionLines(puzzle)
        solutionMoves = solutionLines.firstOrNull() ?: emptyList()
        expectedMoveIndex = 1
        playerMovesMade = 0
        maxPlayerMoves = puzzle.objective.targetPlayerMoves
            ?: (solutionMoves.size / 2).coerceAtLeast(1)
        playerColor = initial.currentTurn.opponent()
        initialPlayerPiecesLost = 0
        initialOpponentPiecesCaptured = 0
        initialOpponentCapturedPlayerPiece = false
        playerCapturedSymbols.clear()
        playerLostSymbols.clear()
        initialPlayerCapturedSymbols.clear()
        initialPlayerLostSymbols.clear()
        resetConditionTracking()
        if (solutionLines.isEmpty()) {
            puzzleState = initial
            playerStartState = initial
            boardView?.gameState = initial
            boardView?.isLocked = true
            setChallengeStatus("This puzzle has an incomplete solution.")
            return
        }
        val firstMove = solutionMoves.firstOrNull()?.let { findMove(initial, it) }
        if (firstMove == null) {
            puzzleState = initial
            playerStartState = initial
            boardView?.gameState = initial
            boardView?.isLocked = true
            setChallengeStatus("This puzzle could not be loaded.")
            return
        }
        val afterOpponent = engine.applyMove(initial, firstMove)
        recordMoveEffects(initial, firstMove, initial.currentTurn)
        initialPlayerPiecesLost = playerPiecesLost
        initialOpponentPiecesCaptured = opponentPiecesCaptured
        initialOpponentCapturedPlayerPiece = opponentCapturedPlayerPiece
        initialPlayerCapturedSymbols += playerCapturedSymbols
        initialPlayerLostSymbols += playerLostSymbols
        puzzleState = afterOpponent.copy(status = GameStatus.IN_PROGRESS)
        playerStartState = puzzleState
        playerColor = puzzleState!!.currentTurn
        boardView?.gameState = puzzleState!!
        boardView?.isFlipped = puzzleState!!.currentTurn == PieceColor.BLACK
        boardView?.isLocked = false
        setChallengeStatus(objectiveText())
    }

    private fun handlePlayerMove(move: Move) {
        if (opponentMoveAnimating) {
            opponentMoveAnimating = false
            applyOpponentReply(move)
            return
        }
        if (completed || resetting) return
        val state = puzzleState ?: return

        hintActive = false
        hintStage = 0
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        val expectedLine = solutionLines.firstOrNull { line ->
            line.getOrNull(expectedMoveIndex)?.let { findMove(state, it) == move } == true
        }
        val acceptsAnyMate = currentPuzzle?.condition?.requiresCheckmate == true &&
            playerMovesMade == 0 &&
            maxPlayerMoves == 1 &&
            isPlayerVictory(engine.applyMove(state, move))
        if (expectedLine == null && !acceptsAnyMate) {
            showWrongMove()
            return
        }
        if (expectedLine != null) {
            solutionMoves = expectedLine
        } else {
            solutionMoves = solutionMoves.take(expectedMoveIndex) + move.toUci()
        }
        recordMoveEffects(state, move, playerColor)
        val nextState = engine.applyMove(state, move)
        if (nextState === state) return
        puzzleState = nextState
        playerMovesMade++
        expectedMoveIndex++
        boardView?.gameState = puzzleState!!

        val authoredLineComplete = expectedMoveIndex >= solutionMoves.size
        if ((isPlayerVictory(nextState) || authoredLineComplete) && playerMovesMade <= maxPlayerMoves) {
            if (conditionSatisfied()) completeLevel() else showConditionFailure()
        } else if (nextState.status != GameStatus.IN_PROGRESS || playerMovesMade >= maxPlayerMoves) {
            showWrongMove()
        } else {
            boardView?.isLocked = true
            setChallengeStatus("Opponent is preparing a reply…")
            handler.postDelayed({ playOpponentReply() }, 380L)
        }
    }

    private fun playOpponentReply() {
        if (completed || resetting) return
        val state = puzzleState ?: return
        val reply = solutionMoves.getOrNull(expectedMoveIndex)?.let { findMove(state, it) }
        if (reply == null) {
            showWrongMove()
            return
        }
        opponentMoveAnimating = true
        boardView?.isLocked = true
        setChallengeStatus("Opponent is moving…")
        boardView?.animateExternalMove(reply)
    }

    private fun applyOpponentReply(move: Move) {
        val state = puzzleState ?: return
        val expectedMove = solutionMoves.getOrNull(expectedMoveIndex)?.let { findMove(state, it) }
        if (expectedMove == null || move != expectedMove) {
            showWrongMove()
            return
        }
        recordMoveEffects(state, move, playerColor.opponent())
        val nextState = engine.applyMove(state, move)
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
            showWrongMove()
        } else {
            boardView?.isLocked = false
            setChallengeStatus(objectiveText())
        }
    }

    private fun completeLevel() {
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
        val resultLabel = if (currentPuzzle?.condition?.requiresCheckmate == true) {
            "Checkmate!"
        } else {
            "Tactical line complete!"
        }
        setChallengeStatus("$resultLabel Challenge complete • ${"★".repeat(stars)} • Attempt $attemptNumber")
        markResultBoard(victory = true)
        showCompletionScreen()
    }

    private fun showWrongMove() {
        resetting = true
        opponentMoveAnimating = false
        boardView?.isLocked = true
        gameHud?.controlsEnabled = false
        hintActive = false
        hintStage = 0
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        setChallengeStatus(failureMessage())
        markResultBoard()
        showFailureScreen()
    }

    private fun resetPuzzle() {
        handler.removeCallbacksAndMessages(null)
        dismissOverlay()
        MusicPlayer.resumeMatch(this)
        val start = playerStartState ?: return
        puzzleState = start
        expectedMoveIndex = 1
        playerMovesMade = 0
        resetConditionTracking()
        completed = false
        resetting = false
        hintActive = false
        hintStage = 0
        retryCount++
        beginAttempt()
        opponentMoveAnimating = false
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
        if (puzzleState == null) return
        val move = expectedMoveForCurrentState()
        if (move == null) {
            hintActive = false
            boardView?.setHintMove(null)
            gameHud?.setHintActive(false)
            return
        }
        hintStage = if (!hintActive) 1 else (hintStage % 3) + 1
        hintActive = true
        hintUsed = true
        boardView?.setHintMove(move, hintStage)
        val hintLabel = when (hintStage) {
            1 -> "Hint 1/3: move the highlighted piece."
            2 -> "Hint 2/3: move it to the highlighted square."
            else -> "Hint 3/3: follow the complete highlighted move."
        }
        setChallengeStatus(
            hintLabel,
        )
    }

    private fun objectiveText(): String {
        val puzzle = currentPuzzle ?: return "Find the checkmate"
        val theme = ChessPuzzleData.themeFor(selectedLevel)
        val playerMoves = puzzle.objective.targetPlayerMoves ?: maxPlayerMoves
        val moveLabel = if (playerMoves == 1) "move" else "moves"
        val lineObjective = "$theme • Difficulty ${puzzle.objective.difficulty}/5 • $playerMoves $moveLabel"
        val beginnerBrief = puzzle.beginnerChallenge
        if (beginnerBrief != null) {
            return "${beginnerBrief.focus}\nWin condition: ${beginnerBrief.winCondition} • $lineObjective"
        }
        return if (puzzle.condition == ChallengeCondition.DIRECT_MATE) {
            lineObjective
        } else {
            "${puzzle.condition.title}: ${puzzle.condition.objective} • $lineObjective"
        }
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
    }

    private fun recordMoveEffects(state: GameState, move: Move, mover: PieceColor) {
        move.captures.forEach { capturePosition ->
            val capturedColor = state.get(capturePosition)?.color
            val capturedSymbol = state.get(capturePosition)?.symbol() ?: "?"
            if (capturedColor == playerColor) {
                playerPiecesLost++
                playerLostSymbols += capturedSymbol
                if (mover == playerColor.opponent()) {
                    opponentCapturedPlayerPiece = true
                }
            } else if (capturedColor == playerColor.opponent()) {
                opponentPiecesCaptured++
                playerCapturedSymbols += capturedSymbol
            }
        }
        if (mover == playerColor && move.promotionType != null) {
            playerPromotions++
            playerPromotionTypes += move.promotionType
        }
    }

    private fun conditionSatisfied(): Boolean {
        if (currentPuzzle == null) return false
        if (opponentPiecesCaptured < objective.requiredCaptures) return false
        if (objective.requiredPromotions > 0 && !promotionRequirementSatisfied()) return false
        return when (currentPuzzle?.condition) {
            ChallengeCondition.DIRECT_MATE -> true
            ChallengeCondition.CLEAN_MATE -> playerPiecesLost <= objective.allowedPiecesToLose
            ChallengeCondition.QUIET_MATE ->
                playerPiecesLost == 0 && opponentPiecesCaptured == 0
            ChallengeCondition.CAPTURE_MATE,
            ChallengeCondition.DOUBLE_CAPTURE_MATE -> true
            ChallengeCondition.SACRIFICE_TRAP,
            ChallengeCondition.SET_TRAP -> opponentCapturedPlayerPiece
            ChallengeCondition.MATERIAL_PRESSURE -> playerPiecesLost >= objective.allowedPiecesToLose
            ChallengeCondition.PAWN_PROMOTION,
            ChallengeCondition.PROMOTE_AND_MATE -> playerPromotions >= objective.requiredPromotions &&
                promotionRequirementSatisfied()
            ChallengeCondition.LONG_MATE -> true
            null -> false
            else -> true
        }
    }

    private val objective: ChallengeObjective
        get() = currentPuzzle?.objective ?: ChallengeObjective()

    private fun promotionRequirementSatisfied(): Boolean {
        val requirement = objective.promotionRequirement
        if (requirement == PromotionRequirement.ANY) return true
        return playerPromotionTypes.all { it == requirement.name } &&
            playerPromotionTypes.isNotEmpty()
    }

    private fun failureMessage(): String {
        val puzzle = currentPuzzle ?: return "The line was not completed."
        if (puzzleState?.status != GameStatus.IN_PROGRESS && playerMovesMade >= maxPlayerMoves) {
            return "Mate found, but the move limit was exceeded."
        }
        return when (puzzle.condition) {
            ChallengeCondition.CLEAN_MATE ->
                "You found mate, but one of your pieces was captured."
            ChallengeCondition.QUIET_MATE ->
                "You needed to finish without any captures."
            ChallengeCondition.CAPTURE_MATE ->
                "You found mate, but needed ${objective.requiredCaptures} capture."
            ChallengeCondition.DOUBLE_CAPTURE_MATE ->
                "You found mate, but needed ${objective.requiredCaptures} captures."
            ChallengeCondition.SACRIFICE_TRAP ->
                "You found mate, but the opponent had to capture one of your pieces."
            ChallengeCondition.SET_TRAP ->
                "You found mate, but the trap was not sprung by an opponent capture."
            ChallengeCondition.MATERIAL_PRESSURE ->
                "You found mate, but needed to lose ${objective.allowedPiecesToLose} pieces."
            ChallengeCondition.PAWN_PROMOTION,
            ChallengeCondition.PROMOTE_AND_MATE ->
                "You found mate, but the pawn must promote to ${objective.promotionRequirement.name.lowercase()} first."
            ChallengeCondition.DIRECT_MATE ->
                "That move did not complete the authored mating line."
            ChallengeCondition.LONG_MATE ->
                "The long mating route was not completed."
            else ->
                "The authored tactical line was not completed."
        }
    }

    private fun earnedStars(): Int = when {
        retryCount > 0 -> 1
        hintUsed -> 2
        playerMovesMade <= maxPlayerMoves -> 3
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
        resetting = true
        opponentMoveAnimating = false
        boardView?.isLocked = true
        gameHud?.controlsEnabled = false
        hintActive = false
        hintStage = 0
        gameHud?.setHintActive(false)
        boardView?.setHintMove(null)
        setChallengeStatus(failureMessage())
        markResultBoard()
        showFailureScreen()
    }

    private fun markResultBoard(victory: Boolean = false) {
        val state = puzzleState ?: return
        val resultStatus = if (victory) {
            if (playerColor == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS
        } else {
            GameStatus.WHITE_WINS
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

    private fun expectedMoveForCurrentState(): Move? {
        val state = puzzleState ?: return null
        return solutionMoves.getOrNull(expectedMoveIndex)?.let { findMove(state, it) }
    }

    private fun showPauseScreen() {
        if (activeOverlay != null || boardView == null) return
        boardView?.isLocked = true
        MusicPlayer.enterPausedMatch(this)
        showOverlay(
            ChessChoiceView(
                this,
                title = "Paused",
                subtitle = "The position is saved while you decide what to do next.",
                choices = listOf(
                    ChessChoiceView.Choice("Resume", "Return to the board", "▶", GOLD),
                    ChessChoiceView.Choice("Restart Level", "Try this puzzle again", "↻", GOLD),
                    ChessChoiceView.Choice("Home", "Return to the challenge home", "⌂", GOLD),
                ),
                gameLabel = "C H E S S",
                fullScreenOverride = true,
            ).apply {
                onChoiceSelected = { which ->
                    when (which) {
                        0 -> {
                            dismissOverlay()
                            boardView?.isLocked = false
                            MusicPlayer.resumeMatch(this@ChessChallengeActivity)
                        }
                        1 -> resetPuzzle()
                        else -> finish()
                    }
                }
            },
        )
    }

    private fun showFailureScreen() {
        showOverlay(
            ChessChoiceView(
                this,
                title = "Game Over",
                subtitle = failureMessage(),
                choices = listOf(
                    ChessChoiceView.Choice("Retry Level", "Calculate the line again", "↻", GOLD),
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
                        else -> finish()
                    }
                }
            },
            cardOverlay = true,
        )
    }

    private fun showCompletionScreen() {
        val nextLevel = selectedLevel < puzzles.size
        showOverlay(
            ChessChoiceView(
                this,
                title = "Challenge Complete",
                subtitle = "${completionSummary()} • ${"★".repeat(earnedStars())}",
                choices = buildList {
                    if (nextLevel) add(ChessChoiceView.Choice("Next Level", "Continue the challenge", "→", GOLD))
                    add(ChessChoiceView.Choice("Replay Level", "Solve this one again", "↻", GOLD))
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
                    else finish()
                }
            },
            cardOverlay = true,
        )
    }

    private fun completionSummary(): String {
        val mateLabel = if (maxPlayerMoves == 1) "Mate in 1" else "Mate in $maxPlayerMoves"
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
                        setOnClickListener { dismissOverlay() }
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
                    tag = "level-title"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                })
                addView(TextView(this@ChessChallengeActivity).apply {
                    text = subtitle
                    tag = "level-subtitle"
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