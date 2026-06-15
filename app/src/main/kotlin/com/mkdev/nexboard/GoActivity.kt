package com.mkdev.nexboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class GoActivity : AppCompatActivity() {

    // ── Stone colours ─────────────────────────────────────────────────────────
    private val BLACK = 1
    private val WHITE = 2
    private val EMPTY = 0
    private val KOMI  = 6.5   // compensation for White

    // ── Game state ────────────────────────────────────────────────────────────
    private var boardSize         = 9
    private var board             = IntArray(9 * 9)
    private var currentColor      = BLACK
    private var capturedByBlack   = 0   // white stones captured by Black
    private var capturedByWhite   = 0   // black stones captured by White
    private var koPoint: Int?     = null
    private var lastBoard: IntArray? = null
    private var consecutivePasses = 0
    private var gameOver          = false
    private var lastMovePt: Int?  = null

    // ── Mode ──────────────────────────────────────────────────────────────────
    private var vsAI        = true
    private var playerColor = BLACK
    private var difficulty  = 0   // 0 Easy 1 Med 2 Hard

    // ── History ───────────────────────────────────────────────────────────────
    private data class Snapshot(
        val board: IntArray,
        val color: Int,
        val capB: Int,
        val capW: Int,
        val ko: Int?,
        val lastBoard: IntArray?,
        val passes: Int,
        val lastPt: Int?
    )
    private val history = ArrayDeque<Snapshot>()

    // ── Coroutines / ads ──────────────────────────────────────────────────────
    private val scope          = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var aiJob: Job?    = null
    private var interstitialAd: Any? = null
    private var resultRecorded = false

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var hudView:   HudView
    private lateinit var boardView: GoBoardView
    private lateinit var scoreBar:  ScoreBarView

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)

        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        hudView   = HudView(this)
        boardView = GoBoardView(this)
        scoreBar  = ScoreBarView(this)

        root.addView(hudView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        root.addView(scoreBar,  LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * dp).toInt()))

        AdManager.attachBanner(root)
        setContentView(root)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                window.decorView.postDelayed({ makeFullscreen() }, 200)
        }

        showModeDialog()
    }

    override fun onResume() {
        super.onResume()
        makeFullscreen()
        SoundPlayer.movementSoundsEnabled = SettingsManager.isMovementSoundsEnabled(this)
    }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameOver && history.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Leave Match?")
                .setMessage("Leaving counts as a forfeit.")
                .setPositiveButton("Leave") { _, _ ->
                    if (vsAI) SettingsManager.recordForfeit(this)
                    @Suppress("DEPRECATION") super.onBackPressed()
                }
                .setNegativeButton("Keep Playing", null).show()
        } else {
            @Suppress("DEPRECATION") super.onBackPressed()
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

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this).setTitle("Go")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, which ->
                when (which) {
                    0 -> { vsAI = true;  showBoardSizeDialog() }
                    1 -> { vsAI = false; playerColor = BLACK; showBoardSizeDialog() }
                    2 -> showRules(showModeAfter = history.isEmpty())
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (history.isEmpty()) finish() }
            .show()
    }

    private fun showBoardSizeDialog() {
        AlertDialog.Builder(this).setTitle("Board Size")
            .setItems(arrayOf(
                "9×9   — Quick  (~20 min)",
                "13×13 — Medium (~60 min)",
                "19×19 — Full   (~90 min)"
            )) { _, which ->
                boardSize = when (which) { 0 -> 9; 1 -> 13; else -> 19 }
                boardView.updateBoardSize(boardSize)
                if (vsAI) showColorPickerDialog() else startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showModeDialog() }
            .show()
    }

    private fun showColorPickerDialog() {
        AlertDialog.Builder(this).setTitle("Play as")
            .setItems(arrayOf("Black ● (goes first)", "White ○ (goes second)")) { _, which ->
                playerColor = if (which == 0) BLACK else WHITE
                startGame()
            }
            .setCancelable(true)
            .setOnCancelListener { showBoardSizeDialog() }
            .show()
    }

    private fun showRules(showModeAfter: Boolean = false) {
        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setLineSpacing(4f * dp, 1f)
            text = """
GO — Rules

Overview
Go is a 2-player strategy game. Players place black and white stones on intersections. Black goes first.

─────────────────────────

Placing Stones
Tap any empty intersection to place your stone. Stones do not move once placed.

─────────────────────────

Capturing
A stone (or connected group) is captured and removed when all of its adjacent empty intersections (liberties) are filled by the opponent.

─────────────────────────

Ko Rule
You may not place a stone that recreates the exact board position from the previous turn.

─────────────────────────

Passing
Tap Pass to skip your turn. Two consecutive passes end the game.

─────────────────────────

Scoring (Chinese Rules)
Each player scores:
  • 1 point per stone on the board
  • 1 point per empty intersection fully enclosed by their stones
  • White receives 6.5 Komi points (for moving second)

Territory is shown on the board when the game ends.

─────────────────────────

Tips
• Secure corners first — they're easiest to hold.
• Build groups with two or more internal eyes — they cannot be captured.
• Reduce opponent liberties to capture their stones.
            """.trimIndent()
        }
        val sv = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv)
        }
        AlertDialog.Builder(this).setTitle("How to Play Go")
            .setView(sv)
            .setPositiveButton("Got it!") { _, _ -> if (showModeAfter) showModeDialog() }
            .show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    // ── Game flow ─────────────────────────────────────────────────────────────

    private fun startGame() {
        resultRecorded = false
        interstitialAd = null
        aiJob?.cancel(); aiJob = null
        AdManager.loadInterstitial(this) { interstitialAd = it }
        if (vsAI) SettingsManager.setActiveGame(this, "go")
        SoundPlayer.init(this)

        board             = IntArray(boardSize * boardSize)
        currentColor      = BLACK
        capturedByBlack   = 0
        capturedByWhite   = 0
        koPoint           = null
        lastBoard         = null
        consecutivePasses = 0
        gameOver          = false
        lastMovePt        = null
        history.clear()

        boardView.reset(board, boardSize, null)
        scoreBar.update(capturedByBlack, capturedByWhite, BLACK)
        updateHud()
        SoundPlayer.play("game_start")

        if (vsAI && currentColor != playerColor) triggerAI()
    }

    internal fun handlePlacement(idx: Int) {
        if (gameOver || boardView.isLocked) return
        if (board[idx] != EMPTY) return

        val newBoard = applyStone(board, idx, currentColor, koPoint, boardSize) ?: return
        val captured = countCaptures(board, newBoard, currentColor)

        history.addLast(Snapshot(board.copyOf(), currentColor, capturedByBlack, capturedByWhite, koPoint, lastBoard?.copyOf(), consecutivePasses, lastMovePt))

        val newKo = computeKo(board, newBoard, idx, currentColor, boardSize)

        lastBoard = board.copyOf()
        board     = newBoard
        if (currentColor == BLACK) capturedByBlack += captured
        else                       capturedByWhite += captured
        koPoint           = newKo
        consecutivePasses = 0
        lastMovePt        = idx
        currentColor      = opp(currentColor)

        boardView.setState(board, lastMovePt)
        scoreBar.update(capturedByBlack, capturedByWhite, currentColor)
        updateHud()
        SoundPlayer.playMovement(if (currentColor == WHITE) "ttt_x" else "ttt_o")

        if (vsAI && !gameOver && currentColor != playerColor) triggerAI()
    }

    internal fun handlePass() {
        if (gameOver || boardView.isLocked) return

        history.addLast(Snapshot(board.copyOf(), currentColor, capturedByBlack, capturedByWhite, koPoint, lastBoard?.copyOf(), consecutivePasses, lastMovePt))

        consecutivePasses++
        lastBoard    = board.copyOf()
        lastMovePt   = null
        koPoint      = null
        currentColor = opp(currentColor)

        boardView.setState(board, null)
        scoreBar.update(capturedByBlack, capturedByWhite, currentColor)
        updateHud()
        SoundPlayer.play("ui_click")

        if (consecutivePasses >= 2) { endGame(); return }
        if (vsAI && !gameOver && currentColor != playerColor) triggerAI()
    }

    private fun endGame() {
        gameOver = true
        boardView.isLocked = true

        val (blackT, whiteT) = countTerritory(board, boardSize)
        val blackOnBoard     = board.count { it == BLACK }
        val whiteOnBoard     = board.count { it == WHITE }
        val blackFinal       = (blackOnBoard + blackT + capturedByBlack).toDouble()
        val whiteFinal       = whiteOnBoard + whiteT + capturedByWhite + KOMI

        boardView.setTerritoryOverlay(buildTerritoryMap(board, boardSize))
        scoreBar.update(capturedByBlack, capturedByWhite, currentColor)
        updateHud()

        if (vsAI) recordResult(blackFinal, whiteFinal)
        val ad = interstitialAd; interstitialAd = null
        AdManager.showInterstitial(this, ad)
        AdManager.loadInterstitial(this) { interstitialAd = it }

        scope.launch { delay(500L); showResultDialog(blackFinal, whiteFinal) }
    }

    private fun recordResult(b: Double, w: Double) {
        if (resultRecorded) return
        resultRecorded = true
        val playerWon = (playerColor == BLACK && b > w) || (playerColor == WHITE && w > b)
        when {
            b == w     -> SettingsManager.recordDraw(this)
            playerWon  -> SettingsManager.recordWin(this)
            else       -> SettingsManager.recordLoss(this)
        }
    }

    private fun showResultDialog(blackScore: Double, whiteScore: Double) {
        val winner    = if (blackScore > whiteScore) "Black" else "White"
        val margin    = "%.1f".format(kotlin.math.abs(blackScore - whiteScore))
        val resultMsg = if (vsAI) {
            val youWon = (playerColor == BLACK && blackScore > whiteScore) ||
                         (playerColor == WHITE && whiteScore > blackScore)
            if (youWon) "You win! 🎉" else "AI wins."
        } else "$winner wins by $margin pts!"

        val msg = "Black: ${"%.0f".format(blackScore)} pts" +
                  "   White: ${"%.1f".format(whiteScore)} pts\n\n" +
                  resultMsg +
                  "\n\n(Territory shown on board  •  Komi 6.5 for White)"

        AlertDialog.Builder(this).setTitle("Game Over")
            .setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> showModeDialog() }
            .setNegativeButton("Main Menu")  { _, _ -> finish() }
            .setCancelable(true).show()
    }

    // ── Undo ──────────────────────────────────────────────────────────────────

    fun onUndoClicked() {
        if (history.isEmpty() || boardView.isLocked) return
        aiJob?.cancel(); aiJob = null
        hudView.setThinking(false)
        boardView.isLocked = false

        if (vsAI && history.size >= 2) history.removeLast()
        val snap = history.removeLastOrNull() ?: return

        board             = snap.board.copyOf()
        currentColor      = snap.color
        capturedByBlack   = snap.capB
        capturedByWhite   = snap.capW
        koPoint           = snap.ko
        lastBoard         = snap.lastBoard?.copyOf()
        consecutivePasses = snap.passes
        lastMovePt        = snap.lastPt
        gameOver          = false

        boardView.reset(board, boardSize, lastMovePt)
        scoreBar.update(capturedByBlack, capturedByWhite, currentColor)
        updateHud()
    }

    fun onMenuClicked() {
        val inProgress = !gameOver && history.isNotEmpty()
        val items = mutableListOf("New Game", "How to Play")
        if (vsAI) items.add("AI Difficulty")
        items.add("Main Menu")
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "New Game" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Forfeit Match?")
                            .setMessage("Starting a new game counts as a forfeit.")
                            .setPositiveButton("Forfeit & New Game") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                showModeDialog()
                            }.setNegativeButton("Cancel", null).show()
                    } else showModeDialog()
                    "How to Play"  -> showRules(showModeAfter = false)
                    "AI Difficulty" -> {
                        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_MinWidth)
                            .setTitle("AI Difficulty")
                            .setSingleChoiceItems(arrayOf("Easy", "Medium", "Hard"), difficulty) { d, i ->
                                difficulty = i; d.dismiss()
                            }.show()
                    }
                    "Main Menu" -> if (inProgress) {
                        AlertDialog.Builder(this).setTitle("Leave Match?")
                            .setMessage("Leaving counts as a forfeit.")
                            .setPositiveButton("Leave") { _, _ ->
                                if (vsAI) SettingsManager.recordForfeit(this)
                                finish()
                            }.setNegativeButton("Cancel", null).show()
                    } else finish()
                }
            }.show()
    }

    private fun updateHud() {
        val label = when {
            gameOver -> "Game over"
            vsAI && currentColor == playerColor -> "Your turn"
            else -> "${if (currentColor == BLACK) "Black" else "White"}'s turn"
        }
        hudView.setInfo(label, canUndo = history.isNotEmpty(), isBlack = currentColor == BLACK)
    }

    // ── Go engine ─────────────────────────────────────────────────────────────

    private fun opp(c: Int) = if (c == BLACK) WHITE else BLACK

    private fun neighbors(idx: Int, bs: Int): List<Int> {
        val r = idx / bs; val c = idx % bs
        val list = ArrayList<Int>(4)
        if (r > 0)    list.add((r - 1) * bs + c)
        if (r < bs-1) list.add((r + 1) * bs + c)
        if (c > 0)    list.add(r * bs + (c - 1))
        if (c < bs-1) list.add(r * bs + (c + 1))
        return list
    }

    private fun getGroup(b: IntArray, startIdx: Int, bs: Int): Pair<Set<Int>, Set<Int>> {
        val color     = b[startIdx]
        val group     = mutableSetOf(startIdx)
        val liberties = mutableSetOf<Int>()
        val queue     = ArrayDeque<Int>(); queue.add(startIdx)
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            for (n in neighbors(idx, bs)) {
                when {
                    b[n] == EMPTY && n !in liberties -> liberties.add(n)
                    b[n] == color && n !in group     -> { group.add(n); queue.add(n) }
                }
            }
        }
        return group to liberties
    }

    /** Places stone at idx, removes captures, returns new board or null if illegal. */
    private fun applyStone(b: IntArray, idx: Int, color: Int, ko: Int?, bs: Int): IntArray? {
        if (b[idx] != EMPTY) return null
        if (idx == ko) return null
        val nb = b.copyOf()
        nb[idx] = color
        for (n in neighbors(idx, bs)) {
            if (nb[n] != opp(color)) continue
            val (grp, libs) = getGroup(nb, n, bs)
            if (libs.isEmpty()) for (gi in grp) nb[gi] = EMPTY
        }
        val (_, myLibs) = getGroup(nb, idx, bs)
        if (myLibs.isEmpty()) return null   // suicide — illegal
        return nb
    }

    private fun countCaptures(old: IntArray, new_: IntArray, color: Int): Int {
        val o = opp(color)
        return old.indices.count { old[it] == o && new_[it] == EMPTY }
    }

    private fun computeKo(oldBoard: IntArray, newBoard: IntArray, placed: Int, color: Int, bs: Int): Int? {
        val o        = opp(color)
        val captured = oldBoard.indices.filter { oldBoard[it] == o && newBoard[it] == EMPTY }
        if (captured.size != 1) return null
        val (pg, _) = getGroup(newBoard, placed, bs)
        if (pg.size != 1) return null
        return captured[0]
    }

    private fun countTerritory(b: IntArray, bs: Int): Pair<Int, Int> {
        val visited = BooleanArray(bs * bs)
        var blackT = 0; var whiteT = 0
        for (start in b.indices) {
            if (b[start] != EMPTY || visited[start]) continue
            val group   = mutableListOf<Int>()
            val queue   = ArrayDeque<Int>(); queue.add(start); visited[start] = true
            val borders = mutableSetOf<Int>()
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst(); group.add(idx)
                for (n in neighbors(idx, bs)) when {
                    b[n] == EMPTY && !visited[n] -> { visited[n] = true; queue.add(n) }
                    b[n] != EMPTY                -> borders.add(b[n])
                }
            }
            when {
                borders.size == 1 && borders.first() == BLACK -> blackT += group.size
                borders.size == 1 && borders.first() == WHITE -> whiteT += group.size
            }
        }
        return blackT to whiteT
    }

    private fun buildTerritoryMap(b: IntArray, bs: Int): IntArray {
        val result  = IntArray(bs * bs)
        val visited = BooleanArray(bs * bs)
        for (start in b.indices) {
            if (b[start] != EMPTY || visited[start]) continue
            val group   = mutableListOf<Int>()
            val queue   = ArrayDeque<Int>(); queue.add(start); visited[start] = true
            val borders = mutableSetOf<Int>()
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst(); group.add(idx)
                for (n in neighbors(idx, bs)) when {
                    b[n] == EMPTY && !visited[n] -> { visited[n] = true; queue.add(n) }
                    b[n] != EMPTY                -> borders.add(b[n])
                }
            }
            val owner = if (borders.size == 1) borders.first() else 0
            for (gi in group) result[gi] = owner
        }
        return result
    }

    // ── AI ────────────────────────────────────────────────────────────────────

    private fun triggerAI() {
        boardView.isLocked = true
        hudView.setThinking(true)
        val snap = Snapshot(board.copyOf(), currentColor, capturedByBlack, capturedByWhite, koPoint, lastBoard?.copyOf(), consecutivePasses, lastMovePt)
        aiJob = scope.launch {
            val move = withContext(Dispatchers.Default) {
                try { aiPickMove(snap.board, snap.color, snap.ko, boardSize, difficulty) }
                catch (_: Throwable) { null }
            }
            hudView.setThinking(false)
            boardView.isLocked = false
            when {
                move == -1    -> handlePass()
                move != null  -> handlePlacement(move)
            }
        }
    }

    private fun aiPickMove(b: IntArray, color: Int, ko: Int?, bs: Int, diff: Int): Int {
        val legal = legalMoves(b, color, ko, bs)
        if (legal.isEmpty()) return -1
        return when (diff) {
            0    -> aiEasy(b, color, legal, ko, bs)
            1    -> aiMedium(b, color, legal, ko, bs)
            else -> aiHard(b, color, legal, ko, bs)
        }
    }

    private fun legalMoves(b: IntArray, color: Int, ko: Int?, bs: Int): List<Int> =
        b.indices.filter { b[it] == EMPTY && it != ko && applyStone(b, it, color, ko, bs) != null }

    private fun aiEasy(b: IntArray, color: Int, legal: List<Int>, ko: Int?, bs: Int): Int {
        if (b.any { it != EMPTY } && Math.random() < 0.12) return -1
        val captures = legal.filter { idx ->
            val nb = applyStone(b, idx, color, ko, bs)!!
            val o  = opp(color)
            b.indices.any { b[it] == o && nb[it] == EMPTY }
        }
        return if (captures.isNotEmpty()) captures.random() else legal.random()
    }

    private fun scoreMove(b: IntArray, idx: Int, color: Int, ko: Int?, bs: Int): Int {
        val nb  = applyStone(b, idx, color, ko, bs) ?: return Int.MIN_VALUE
        val o   = opp(color)
        var sc  = 0

        // Captures
        sc += b.indices.count { b[it] == o && nb[it] == EMPTY } * 10

        // Atari opponent groups
        for (n in neighbors(idx, bs)) {
            if (nb[n] != o) continue
            val (_, libs) = getGroup(nb, n, bs)
            if (libs.size == 1) sc += 5
        }

        // Defend own group at risk (our groups that had only 2 libs now extend)
        for (n in neighbors(idx, bs)) {
            if (nb[n] != color) continue
            val (_, libs) = getGroup(nb, n, bs)
            if (libs.size <= 2) sc += 4
        }

        // Don't create a weak group
        val (_, myLibs) = getGroup(nb, idx, bs)
        if (myLibs.size == 1) sc -= 10
        if (myLibs.size == 2) sc -= 3

        // Play near existing stones (connection / influence)
        sc += neighbors(idx, bs).count { b[it] != EMPTY } * 2

        // Slight centre bias on 9×9
        if (bs == 9) {
            val r = idx / bs; val c = idx % bs
            sc += (4 - maxOf(kotlin.math.abs(r - 4), kotlin.math.abs(c - 4)))
        }

        return sc
    }

    private fun aiMedium(b: IntArray, color: Int, legal: List<Int>, ko: Int?, bs: Int): Int {
        if (b.any { it != EMPTY } && Math.random() < 0.05) return -1
        val scored = legal.map { it to scoreMove(b, it, color, ko, bs) }
        val best   = scored.maxByOrNull { it.second }!!.second
        val top    = scored.filter { it.second >= best - 3 }
        return top.random().first
    }

    private fun aiHard(b: IntArray, color: Int, legal: List<Int>, ko: Int?, bs: Int): Int {
        val scored = legal.map { it to scoreMove(b, it, color, ko, bs) }
            .sortedByDescending { it.second }
        val topN   = minOf(12, scored.size)
        val top    = scored.take(topN)

        // Quick random playouts for top candidates
        val playouts = when (bs) { 9 -> 40; 13 -> 20; else -> 8 }
        val results = top.map { (idx, heurScore) ->
            val nb   = applyStone(b, idx, color, ko, bs)!!
            var wins = 0
            repeat(playouts) { if (randomPlayout(nb, opp(color), bs) == color) wins++ }
            idx to (wins * 2 + heurScore)
        }
        return results.maxByOrNull { it.second }!!.first
    }

    private fun randomPlayout(startBoard: IntArray, firstColor: Int, bs: Int): Int {
        var b     = startBoard.copyOf()
        var color = firstColor
        var passes = 0
        val limit  = bs * bs * 2
        repeat(limit) {
            if (passes >= 2) return@repeat
            val legal = b.indices.filter { b[it] == EMPTY && applyStone(b, it, color, null, bs) != null }
            if (legal.isEmpty() || (legal.size < 4 && Math.random() < 0.4)) {
                passes++; color = opp(color); return@repeat
            }
            passes = 0
            b      = applyStone(b, legal.random(), color, null, bs)!!
            color  = opp(color)
        }
        val (bt, wt) = countTerritory(b, bs)
        val bScore   = b.count { it == BLACK } + bt
        val wScore   = b.count { it == WHITE } + wt + KOMI.toInt()
        return if (bScore > wScore) BLACK else WHITE
    }

    // ── Board View ────────────────────────────────────────────────────────────

    inner class GoBoardView(ctx: Context) : View(ctx) {

        var isLocked = false

        private var bs        = 9
        private var board     = IntArray(9 * 9)
        private var lastPt:   Int? = null
        private var territory: IntArray? = null

        private var animIdx   = -1
        private var animScale = 1f

        private val dp = resources.displayMetrics.density

        private var boardLeft = 0f
        private var boardTop  = 0f
        private var cellSize  = 0f

        private val bgP      = Paint().apply { color = Color.parseColor("#2A1F0E") }
        private val lineP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B6914"); style = Paint.Style.STROKE; strokeWidth = 1.2f
        }
        private val blackP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
        private val whiteP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
        private val rimP     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f
        }
        private val lastBP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val lastWP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val starP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8B6914") }
        private val tBP      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 0, 0, 0) }
        private val tWP      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 245, 245, 245) }

        fun updateBoardSize(newBs: Int) {
            bs = newBs; board = IntArray(newBs * newBs); lastPt = null; territory = null
            if (width > 0 && height > 0) recalc(width, height)
            requestLayout(); invalidate()
        }

        fun reset(b: IntArray, newBs: Int, last: Int?) {
            bs = newBs; board = b.copyOf(); lastPt = last; territory = null
            isLocked = false; animIdx = -1; animScale = 1f
            if (width > 0 && height > 0) recalc(width, height)
            invalidate()
        }

        fun setState(b: IntArray, last: Int?) {
            board = b.copyOf(); lastPt = last; territory = null
            if (last != null) animateStone(last) else invalidate()
        }

        fun setTerritoryOverlay(t: IntArray) { territory = t; invalidate() }

        private fun animateStone(idx: Int) {
            animIdx = idx; animScale = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                addUpdateListener { animScale = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { recalc(w, h) }

        private fun recalc(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val pad  = 22f * dp
            val size = minOf(w - pad * 2, h - pad * 2)
            cellSize  = size / (bs - 1).toFloat()
            boardLeft = (w - size) / 2f
            boardTop  = (h - size) / 2f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP && !isLocked && cellSize > 0f) {
                val col = ((event.x - boardLeft + cellSize / 2f) / cellSize).toInt()
                val row = ((event.y - boardTop  + cellSize / 2f) / cellSize).toInt()
                if (col in 0 until bs && row in 0 until bs) {
                    val idx = row * bs + col
                    if (board[idx] == EMPTY) handlePlacement(idx)
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            if (cellSize <= 0f) return
            val size = cellSize * (bs - 1)
            val pad  = cellSize * 0.65f

            // Wooden background
            canvas.drawRect(boardLeft - pad, boardTop - pad, boardLeft + size + pad, boardTop + size + pad, bgP)

            // Grid
            for (i in 0 until bs) {
                canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, boardTop + size, lineP)
                canvas.drawLine(boardLeft, boardTop + i * cellSize, boardLeft + size, boardTop + i * cellSize, lineP)
            }

            // Star points
            drawHoshi(canvas)

            // Territory overlay (shown after game ends)
            territory?.let { t ->
                val s = cellSize * 0.26f
                for (i in t.indices) {
                    if (t[i] == 0 || board[i] != EMPTY) continue
                    val r  = i / bs; val c = i % bs
                    val cx = boardLeft + c * cellSize; val cy = boardTop + r * cellSize
                    canvas.drawRect(cx - s, cy - s, cx + s, cy + s, if (t[i] == BLACK) tBP else tWP)
                }
            }

            // Stones
            val stoneR = cellSize * 0.46f
            rimP.strokeWidth = maxOf(1f, cellSize * 0.03f)
            for (i in board.indices) {
                val color = board[i]; if (color == EMPTY) continue
                val r  = i / bs; val c = i % bs
                val cx = boardLeft + c * cellSize; val cy = boardTop + r * cellSize
                val sc = if (i == animIdx) animScale else 1f
                val sr = stoneR * sc

                canvas.drawCircle(cx, cy, sr, if (color == BLACK) blackP else whiteP)
                rimP.color = if (color == BLACK) Color.parseColor("#555555") else Color.parseColor("#AAAAAA")
                canvas.drawCircle(cx, cy, sr, rimP)

                // Last-move dot
                if (i == lastPt) {
                    val lmp = if (color == BLACK) lastBP else lastWP
                    lmp.strokeWidth = maxOf(1f, cellSize * 0.045f)
                    canvas.drawCircle(cx, cy, sr * 0.32f * sc, lmp)
                }
            }
        }

        private fun drawHoshi(canvas: Canvas) {
            val pts = when (bs) {
                9  -> listOf(2 to 2, 2 to 6, 6 to 2, 6 to 6, 4 to 4)
                13 -> listOf(3 to 3, 3 to 9, 9 to 3, 9 to 9, 6 to 6,
                             3 to 6, 9 to 6, 6 to 3, 6 to 9)
                19 -> listOf(3 to 3, 3 to 9, 3 to 15,
                             9 to 3, 9 to 9, 9 to 15,
                             15 to 3, 15 to 9, 15 to 15)
                else -> emptyList()
            }
            val r = cellSize * 0.13f
            for ((row, col) in pts)
                canvas.drawCircle(boardLeft + col * cellSize, boardTop + row * cellSize, r, starP)
        }
    }

    // ── HUD View ──────────────────────────────────────────────────────────────

    inner class HudView(ctx: Context) : View(ctx) {
        private var label    = "Black's turn"
        private var canUndo  = false
        private var thinking = false
        private var isBlack  = true

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP    = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP   = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = 14f * sp.coerceAtMost(3f)
        }
        private val subP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val dimP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }

        private val backRect = RectF()
        private val undoRect = RectF()
        private val passRect = RectF()
        private val menuRect = RectF()

        fun setInfo(l: String, canUndo: Boolean, isBlack: Boolean) {
            label = l; this.canUndo = canUndo; this.isBlack = isBlack; invalidate()
        }
        fun setThinking(t: Boolean) { thinking = t; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 42f * dp; val bh = 26f * dp; val by = (h - bh) / 2f
            backRect.set(6f * dp,       by, 6f * dp + bw,   by + bh)
            undoRect.set(w - bw * 3.3f, by, w - bw * 2.2f, by + bh)
            passRect.set(w - bw * 2.15f,by, w - bw * 1.1f, by + bh)
            menuRect.set(w - bw * 1.05f,by, w - 4f * dp,   by + bh)
        }

        @Suppress("DEPRECATION")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) when {
                backRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); this@GoActivity.onBackPressed() }
                undoRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onUndoClicked() }
                passRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); handlePass() }
                menuRect.contains(e.x, e.y) -> { SoundPlayer.play("ui_click"); onMenuClicked() }
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
            canvas.drawRoundRect(passRect, rr, rr, btnBgP)
            canvas.drawRoundRect(menuRect, rr, rr, btnBgP)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnP.textSize * 0.36f, btnP)
            canvas.drawText("Undo",   undoRect.centerX(), undoRect.centerY() + btnP.textSize * 0.36f, if (canUndo) btnP else dimP)
            canvas.drawText("Pass",   passRect.centerX(), passRect.centerY() + btnP.textSize * 0.36f, btnP)
            canvas.drawText("Menu",   menuRect.centerX(), menuRect.centerY() + btnP.textSize * 0.36f, btnP)
            val cx = w / 2f
            txtP.color = if (isBlack) Color.parseColor("#CCCCCC") else Color.parseColor("#7FC8F8")
            canvas.drawText(label, cx, h / 2f - txtP.textSize * 0.15f, txtP)
            if (thinking) canvas.drawText("Thinking…", cx, h / 2f + subP.textSize * 1.1f, subP)
        }
    }

    // ── Score Bar ─────────────────────────────────────────────────────────────

    inner class ScoreBarView(ctx: Context) : View(ctx) {
        private var capB = 0; private var capW = 0; private var turn = BLACK

        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP    = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP   = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val bStP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
        private val wStP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }
        private val rimSP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888"); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        private val lblP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 9f * sp.coerceAtMost(3f)
        }
        private val numP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = 13f * sp.coerceAtMost(3f)
        }
        private val komiP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.CENTER
            textSize = 10f * sp.coerceAtMost(3f)
        }

        fun update(b: Int, w: Int, t: Int) { capB = b; capW = w; turn = t; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, 0f, w, dp, divP)
            val cy    = h / 2f
            val third = w / 3f
            val sr    = h * 0.27f
            val rr    = 5f * dp

            // Black side
            val bx = third * 0.5f
            canvas.drawCircle(bx - sr * 2f, cy, sr, bStP)
            canvas.drawCircle(bx - sr * 2f, cy, sr, rimSP)
            canvas.drawText("Captured",    bx + sr * 0.3f, cy - numP.textSize * 0.45f, lblP)
            canvas.drawText(capB.toString(),bx + sr * 0.3f, cy + numP.textSize * 0.55f, numP)

            // Centre — komi + turn indicator
            canvas.drawText("Komi  6.5",   third * 1.5f, cy - komiP.textSize * 0.4f, komiP)
            val tdot = if (turn == BLACK) bStP else wStP
            canvas.drawCircle(third * 1.5f, cy + komiP.textSize * 1.3f, h * 0.16f, tdot)
            canvas.drawCircle(third * 1.5f, cy + komiP.textSize * 1.3f, h * 0.16f, rimSP)

            // White side
            val wx = third * 2.5f
            canvas.drawCircle(wx - sr * 2f, cy, sr, wStP)
            canvas.drawCircle(wx - sr * 2f, cy, sr, rimSP)
            canvas.drawText("Captured",    wx + sr * 0.3f, cy - numP.textSize * 0.45f, lblP)
            canvas.drawText(capW.toString(),wx + sr * 0.3f, cy + numP.textSize * 0.55f, numP)
        }
    }
}
