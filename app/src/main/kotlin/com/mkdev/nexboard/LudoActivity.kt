package com.mkdev.nexboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LudoActivity : AppCompatActivity() {

    // ─── Board constants ──────────────────────────────────────────────────────
    companion object {
        const val P_RED    = 0
        const val P_YELLOW = 2   // opposite diagonal

        // 52-square main track: (row, col). Position 0 = Red's entry.
        val TRACK = arrayOf(
            // Red arm — row 6 going right [0-4]
            intArrayOf(6,1),intArrayOf(6,2),intArrayOf(6,3),intArrayOf(6,4),intArrayOf(6,5),
            // Up col 6 [5-10]
            intArrayOf(5,6),intArrayOf(4,6),intArrayOf(3,6),intArrayOf(2,6),intArrayOf(1,6),intArrayOf(0,6),
            // Top connector [11-12]
            intArrayOf(0,7),intArrayOf(0,8),
            // Down col 8 — Green-start marker at 13 [13-17]
            intArrayOf(1,8),intArrayOf(2,8),intArrayOf(3,8),intArrayOf(4,8),intArrayOf(5,8),
            // Row 6 right [18-22]
            intArrayOf(6,9),intArrayOf(6,10),intArrayOf(6,11),intArrayOf(6,12),intArrayOf(6,13),
            // Right edge — skip row 7 (Yellow home) [23-24]
            intArrayOf(6,14),intArrayOf(8,14),
            // Row 8 left — Yellow enters at 26 [25-30]
            intArrayOf(8,13),intArrayOf(8,12),intArrayOf(8,11),intArrayOf(8,10),intArrayOf(8,9),intArrayOf(8,8),
            // Down col 8 bottom arm [31-36]
            intArrayOf(9,8),intArrayOf(10,8),intArrayOf(11,8),intArrayOf(12,8),intArrayOf(13,8),intArrayOf(14,8),
            // Bottom connector [37-38]
            intArrayOf(14,7),intArrayOf(14,6),
            // Up col 6 — Blue-start marker at 39 [39-44]
            intArrayOf(13,6),intArrayOf(12,6),intArrayOf(11,6),intArrayOf(10,6),intArrayOf(9,6),intArrayOf(8,6),
            // Row 8 left [45-50]
            intArrayOf(8,5),intArrayOf(8,4),intArrayOf(8,3),intArrayOf(8,2),intArrayOf(8,1),intArrayOf(8,0),
            // Corner — last square before Red home [51]
            intArrayOf(7,0)
        )

        // Home columns: 5 squares per player, index 0 = entry square
        val HOME_COL = arrayOf(
            arrayOf(intArrayOf(7,1),intArrayOf(7,2),intArrayOf(7,3),intArrayOf(7,4),intArrayOf(7,5)),   // Red →
            arrayOf(intArrayOf(1,7),intArrayOf(2,7),intArrayOf(3,7),intArrayOf(4,7),intArrayOf(5,7)),   // Green ↓
            arrayOf(intArrayOf(7,13),intArrayOf(7,12),intArrayOf(7,11),intArrayOf(7,10),intArrayOf(7,9)), // Yellow ←
            arrayOf(intArrayOf(13,7),intArrayOf(12,7),intArrayOf(11,7),intArrayOf(10,7),intArrayOf(9,7)) // Blue ↑
        )

        // Piece starting slots in each player's yard (4 pieces)
        val YARD_SLOTS = arrayOf(
            arrayOf(intArrayOf(2,2),intArrayOf(2,3),intArrayOf(3,2),intArrayOf(3,3)),   // Red
            arrayOf(intArrayOf(2,11),intArrayOf(2,12),intArrayOf(3,11),intArrayOf(3,12)),// Green
            arrayOf(intArrayOf(11,11),intArrayOf(11,12),intArrayOf(12,11),intArrayOf(12,12)), // Yellow
            arrayOf(intArrayOf(11,2),intArrayOf(11,3),intArrayOf(12,2),intArrayOf(12,3)) // Blue
        )

        // Where each player enters the main track
        val START_POS = intArrayOf(0, 13, 26, 39)

        // Safe squares (stars + player starts) — no captures allowed here
        val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)

        // Encoded position constants
        const val POS_YARD     = -1
        const val POS_FINISHED = 57
        const val HOME_START   = 52  // 52-56 = home col indices 0-4

        val COLORS = intArrayOf(
            Color.parseColor("#EF5350"), // Red
            Color.parseColor("#66BB6A"), // Green
            Color.parseColor("#FFCA28"), // Yellow
            Color.parseColor("#42A5F5")  // Blue
        )
        val DARK_COLORS = intArrayOf(
            Color.parseColor("#B71C1C"),
            Color.parseColor("#1B5E20"),
            Color.parseColor("#F57F17"),
            Color.parseColor("#0D47A1")
        )
        val YARD_BG = intArrayOf(
            Color.parseColor("#4A1515"),
            Color.parseColor("#154A15"),
            Color.parseColor("#4A4A10"),
            Color.parseColor("#10204A")
        )
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private val pieces = Array(4) { IntArray(4) { POS_YARD } }
    private var currentPlayer = P_RED
    private var diceValue     = 0
    private var diceRolled    = false
    private var gameOver      = false
    private var vsAI          = true
    private var humanPlayer   = P_RED
    private var aiPlayer      = P_YELLOW

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var boardView: LudoBoardView
    private lateinit var hudView:   LudoHudView

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        hudView   = LudoHudView(this)
        boardView = LudoBoardView(this)
        root.addView(hudView,   LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (70 * dp).toInt()))
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        AdManager.attachBanner(root)
        setContentView(root)
        showModeDialog()
    }

    override fun onResume() { super.onResume(); makeFullscreen() }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameOver) {
            AlertDialog.Builder(this).setTitle("Leave Game?")
                .setMessage("Your progress will be lost.")
                .setPositiveButton("Leave") { _, _ -> @Suppress("DEPRECATION") super.onBackPressed() }
                .setNegativeButton("Keep Playing", null).show()
        } else { @Suppress("DEPRECATION") super.onBackPressed() }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this).setTitle("Ludo")
            .setItems(arrayOf("vs AI", "2 Players", "How to Play")) { _, w ->
                when (w) {
                    0 -> { vsAI = true;  humanPlayer = P_RED; aiPlayer = P_YELLOW; startGame() }
                    1 -> { vsAI = false; humanPlayer = P_RED; aiPlayer = P_YELLOW; startGame() }
                    2 -> showTutorial()
                }
            }
            .setCancelable(true)
            .setOnCancelListener { if (!gameOver) finish() }
            .show()
    }

    private fun showTutorial() {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0")); textSize = 14f
            setPadding((16*dp).toInt(),(12*dp).toInt(),(16*dp).toInt(),(12*dp).toInt())
            setLineSpacing(4f*dp, 1f)
            text = """
LUDO — Rules

Overview
Two players (Red vs Yellow) each have 4 tokens to race around the board.
First to get all 4 tokens home wins.

─────────────────────────

Rolling the Dice
Tap anywhere on the top bar to roll the dice on your turn.

─────────────────────────

Starting a Token
Tokens begin in your yard. You need to roll a 6 to bring one out.
Rolling a 6 also gives you an extra turn.

─────────────────────────

Moving Tokens
After rolling, tap one of your highlighted tokens to move it forward.
Tokens travel clockwise around the board.

─────────────────────────

Home Column
After completing a full lap, tokens enter your coloured home column.
You must roll the exact number to advance through it.

─────────────────────────

Capturing
Landing on an opponent's token (not on a ★ safe square) sends it back to their yard.

─────────────────────────

Winning
Get all 4 of your tokens into the home column to win!
            """.trimIndent()
        }
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1A1A1A")); addView(tv) }
        AlertDialog.Builder(this).setTitle("How to Play Ludo").setView(sv)
            .setPositiveButton("Got it!") { _, _ -> showModeDialog() }.show()
            .window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
    }

    private fun showMenuDialog() {
        val inProgress = !gameOver
        val items = mutableListOf("New Game")
        if (inProgress) items.add("Forfeit & Quit")
        items.add("Main Menu")
        AlertDialog.Builder(this).setTitle("Menu")
            .setItems(items.toTypedArray()) { _, w ->
                when (items[w]) {
                    "New Game" -> showModeDialog()
                    "Forfeit & Quit" -> finish()
                    "Main Menu" -> finish()
                }
            }.show()
    }

    // ─── Game start ───────────────────────────────────────────────────────────

    private fun startGame() {
        handler.removeCallbacksAndMessages(null)
        for (p in 0..3) pieces[p].fill(POS_YARD)
        currentPlayer = P_RED
        diceValue = 0
        diceRolled = false
        gameOver   = false
        SoundPlayer.play("game_start")
        updateHud("${playerLabel(currentPlayer)}'s turn", "Tap to roll")
        boardView.clearHighlights()
        boardView.invalidate()
        // AI goes first if current player is AI (not in 2P mode)
        if (vsAI && currentPlayer == aiPlayer) handler.postDelayed({ triggerAI() }, 600)
    }

    // ─── Core logic ───────────────────────────────────────────────────────────

    /** Relative position (0-51) for a piece on the track, from this player's perspective. */
    private fun relPos(player: Int, absPos: Int) = (absPos - START_POS[player] + 52) % 52

    /** Absolute track index given a player and their relative position. */
    private fun absPos(player: Int, rel: Int) = (START_POS[player] + rel) % 52

    /** Pieces this player can legally move with the given dice roll. */
    private fun validMoves(player: Int, dice: Int): List<Int> {
        val moves = mutableListOf<Int>()
        for (i in 0..3) {
            val pos = pieces[player][i]
            when {
                pos == POS_FINISHED -> continue
                pos == POS_YARD -> if (dice == 6) moves.add(i)
                pos in HOME_START..56 -> {
                    val hIdx = pos - HOME_START
                    if (hIdx + dice <= 5) moves.add(i)   // 5 = exactly done
                }
                else -> {
                    val rel = relPos(player, pos)
                    val relNew = rel + dice
                    if (relNew < 52) moves.add(i)          // stays on track
                    else if (relNew - 52 <= 4) moves.add(i) // enters home col (max 5 squares)
                    else if (relNew == 57) moves.add(i)     // exact finish via home
                }
            }
        }
        return moves
    }

    /** Apply the move and return true if the player gets an extra turn. */
    private fun applyMove(player: Int, pieceIdx: Int, dice: Int): Boolean {
        val pos = pieces[player][pieceIdx]
        when {
            pos == POS_YARD -> {
                pieces[player][pieceIdx] = START_POS[player]
                checkCapture(player, pieces[player][pieceIdx])
                SoundPlayer.playMovement("mora_place")
            }
            pos in HOME_START..56 -> {
                val hIdx = pos - HOME_START
                val newH = hIdx + dice
                pieces[player][pieceIdx] = if (newH >= 5) POS_FINISHED else HOME_START + newH
                SoundPlayer.playMovement("mora_move")
            }
            else -> {
                val rel    = relPos(player, pos)
                val relNew = rel + dice
                if (relNew < 52) {
                    pieces[player][pieceIdx] = absPos(player, relNew)
                    checkCapture(player, pieces[player][pieceIdx])
                } else {
                    val hIdx = relNew - 52
                    pieces[player][pieceIdx] = if (hIdx >= 5) POS_FINISHED else HOME_START + hIdx
                }
                SoundPlayer.playMovement("mora_move")
            }
        }
        if (pieces[player].all { it == POS_FINISHED }) {
            gameOver = true
            return false
        }
        return dice == 6   // extra turn on rolling 6
    }

    private fun checkCapture(mover: Int, trackPos: Int) {
        if (trackPos in SAFE || trackPos >= HOME_START) return
        val opponent = if (mover == P_RED) P_YELLOW else P_RED
        for (i in 0..3) {
            if (pieces[opponent][i] == trackPos) {
                pieces[opponent][i] = POS_YARD
                SoundPlayer.playMovement("mora_capture")
            }
        }
    }

    // ─── Human turn ───────────────────────────────────────────────────────────

    fun humanRoll() {
        if (gameOver || diceRolled) return
        if (vsAI && currentPlayer != humanPlayer) return
        if (!vsAI && currentPlayer != P_RED && currentPlayer != P_YELLOW) return
        performRoll(currentPlayer, isHuman = true)
    }

    fun humanPieceTapped(pieceIdx: Int) {
        if (gameOver || !diceRolled) return
        val isHumanTurn = vsAI && currentPlayer == humanPlayer ||
                          !vsAI && (currentPlayer == P_RED || currentPlayer == P_YELLOW)
        if (!isHumanTurn) return
        val valid = validMoves(currentPlayer, diceValue)
        if (pieceIdx !in valid) return

        val extra = applyMove(currentPlayer, pieceIdx, diceValue)
        diceRolled = false
        boardView.clearHighlights()
        boardView.invalidate()

        if (gameOver) { showResult(currentPlayer); return }

        if (extra) {
            updateHud("${playerLabel(currentPlayer)}'s turn  (+1)", "Tap to roll again")
        } else {
            nextTurn()
        }
    }

    private fun performRoll(player: Int, isHuman: Boolean) {
        diceValue  = (1..6).random()
        diceRolled = true
        val valid  = validMoves(player, diceValue)
        boardView.highlightedPieces = if (isHuman) valid else emptyList()
        boardView.invalidate()

        if (valid.isEmpty()) {
            updateHud("${playerLabel(player)}'s turn", "Rolled $diceValue — no moves, tap to pass")
            if (!isHuman) handler.postDelayed({ autoPass() }, 900)
        } else {
            val extra = if (diceValue == 6) "  ★ roll again!" else ""
            updateHud("${playerLabel(player)}'s turn", "Rolled $diceValue$extra — tap a piece")
            if (!isHuman) handler.postDelayed({ aiPickPiece(valid) }, 900)
        }
    }

    private fun autoPass() {
        diceRolled = false
        boardView.clearHighlights()
        nextTurn()
    }

    private fun nextTurn() {
        currentPlayer = if (currentPlayer == P_RED) P_YELLOW else P_RED
        updateHud("${playerLabel(currentPlayer)}'s turn", "Tap to roll")
        boardView.invalidate()
        if (vsAI && currentPlayer == aiPlayer) handler.postDelayed({ triggerAI() }, 500)
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private fun triggerAI() {
        if (gameOver || currentPlayer != aiPlayer) return
        updateHud("AI thinking…", "")
        handler.postDelayed({ performRoll(aiPlayer, isHuman = false) }, 600)
    }

    private fun aiPickPiece(valid: List<Int>) {
        val pick = chooseBestAIMove(valid)
        val extra = applyMove(aiPlayer, pick, diceValue)
        diceRolled = false
        boardView.clearHighlights()
        boardView.invalidate()

        if (gameOver) { showResult(aiPlayer); return }

        if (extra) {
            updateHud("AI's turn  (+1)", "Rolling again…")
            handler.postDelayed({ triggerAI() }, 700)
        } else {
            nextTurn()
        }
    }

    private fun chooseBestAIMove(valid: List<Int>): Int {
        // 1. Capture opponent
        for (i in valid) {
            val pos = pieces[aiPlayer][i]
            if (pos == POS_YARD || pos >= HOME_START) continue
            val rel    = relPos(aiPlayer, pos)
            val relNew = rel + diceValue
            if (relNew < 52) {
                val newAbs = absPos(aiPlayer, relNew)
                if (newAbs !in SAFE) {
                    for (j in 0..3) if (pieces[humanPlayer][j] == newAbs) return i
                }
            }
        }
        // 2. Bring a new piece out on 6
        if (diceValue == 6) { val yard = valid.firstOrNull { pieces[aiPlayer][it] == POS_YARD }; if (yard != null) return yard }
        // 3. Advance piece closest to home (highest progress)
        return valid.maxByOrNull { i ->
            val pos = pieces[aiPlayer][i]
            when {
                pos == POS_YARD -> -1
                pos >= HOME_START -> 52 + (pos - HOME_START)
                else -> relPos(aiPlayer, pos)
            }
        } ?: valid[0]
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun playerLabel(player: Int) = if (player == P_RED) "Red" else "Yellow"

    private fun updateHud(title: String, sub: String) {
        hudView.update(title, sub, diceValue, diceRolled, currentPlayer)
    }

    private fun showResult(winner: Int) {
        val msg = when {
            vsAI && winner == humanPlayer -> "You win! 🎉"
            vsAI -> "AI wins!"
            else -> "${playerLabel(winner)} wins!"
        }
        SoundPlayer.play("game_end")
        handler.postDelayed({
            AlertDialog.Builder(this).setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Play Again") { _, _ -> showModeDialog() }
                .setNegativeButton("Main Menu")  { _, _ -> finish() }
                .setCancelable(false).show()
        }, 400)
    }

    @Suppress("DEPRECATION")
    private fun makeFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─── Board View ───────────────────────────────────────────────────────────

    inner class LudoBoardView(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        var highlightedPieces: List<Int> = emptyList()
        fun clearHighlights() { highlightedPieces = emptyList() }

        private val bgP       = Paint().apply { color = Color.parseColor("#111118") }
        private val trackP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22222E") }
        private val safeTrackP= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A3A") }
        private val borderP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33334A"); style = Paint.Style.STROKE; strokeWidth = 0.8f * dp
        }
        private val starTxtP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val arrowP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        private var cellSize  = 0f
        private var bLeft     = 0f
        private var bTop      = 0f

        // Touch targets for Red and Yellow pieces
        private val pRects = Array(4) { Array(4) { RectF() } }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val sz = minOf(w.toFloat(), h.toFloat())
            cellSize = sz / 15f
            bLeft = (w - sz) / 2f
            bTop  = (h - sz) / 2f
        }

        private fun cx(col: Int) = bLeft + col * cellSize + cellSize / 2f
        private fun cy(row: Int) = bTop  + row * cellSize + cellSize / 2f
        private fun cellL(col: Int) = bLeft + col * cellSize
        private fun cellT(row: Int) = bTop  + row * cellSize

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            drawYards(canvas)
            drawTrack(canvas)
            drawHomeColumns(canvas)
            drawCenter(canvas)
            drawPieces(canvas)
        }

        private fun drawYards(canvas: Canvas) {
            // All 4 yards always shown (dimmed if unused in 2P)
            val bounds = arrayOf(
                floatArrayOf(0f,0f,6f,6f),    // Red  cols 0-5, rows 0-5
                floatArrayOf(9f,0f,15f,6f),   // Green
                floatArrayOf(9f,9f,15f,15f),  // Yellow
                floatArrayOf(0f,9f,6f,15f)    // Blue
            )
            val activePl = intArrayOf(P_RED, P_YELLOW)

            for (p in 0..3) {
                val b = bounds[p]; val c1 = b[0]; val r1 = b[1]; val c2 = b[2]; val r2 = b[3]
                val active = p in activePl
                val bgAlpha = if (active) 255 else 80
                val yardBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YARD_BG[p]; alpha = bgAlpha }
                canvas.drawRect(cellL(c1.toInt()), cellT(r1.toInt()),
                                cellL(c2.toInt()), cellT(r2.toInt()), yardBgP)
                val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLORS[p]; style = Paint.Style.STROKE; strokeWidth = 2f * dp
                    alpha = bgAlpha
                }
                canvas.drawRect(cellL(c1.toInt()), cellT(r1.toInt()),
                                cellL(c2.toInt()), cellT(r2.toInt()), rimP)
                // Inner circle background
                val icCx = bLeft + (c1 + c2) / 2f * cellSize
                val icCy = bTop  + (r1 + r2) / 2f * cellSize
                val icR  = cellSize * 1.8f
                val icP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK_COLORS[p]; alpha = if (active) 160 else 50 }
                canvas.drawCircle(icCx, icCy, icR, icP)

                if (active) {
                    // Piece slots
                    for (i in 0..3) {
                        val (sr, sc) = YARD_SLOTS[p][i].let { it[0] to it[1] }
                        val slotP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = DARK_COLORS[p]; alpha = 120
                        }
                        canvas.drawCircle(cx(sc), cy(sr), cellSize * 0.32f, slotP)
                    }
                }
            }
        }

        private fun drawTrack(canvas: Canvas) {
            for (i in TRACK.indices) {
                val (r, c) = TRACK[i][0] to TRACK[i][1]
                val safe = i in SAFE
                val rect = RectF(cellL(c), cellT(r), cellL(c) + cellSize, cellT(r) + cellSize)
                canvas.drawRoundRect(rect, 3f * dp, 3f * dp, if (safe) safeTrackP else trackP)
                canvas.drawRoundRect(rect, 3f * dp, 3f * dp, borderP)
                if (safe) {
                    starTxtP.textSize = cellSize * 0.52f; starTxtP.color = Color.parseColor("#FFD700")
                    canvas.drawText("★", cx(c), cy(r) + starTxtP.textSize * 0.36f, starTxtP)
                }
                // Start arrow for each player
                val sPlayer = when (i) { 0 -> P_RED; 26 -> P_YELLOW; else -> -1 }
                if (sPlayer >= 0) {
                    arrowP.textSize = cellSize * 0.44f; arrowP.color = COLORS[sPlayer]
                    val arrow = if (sPlayer == P_RED) "▶" else "◀"
                    canvas.drawText(arrow, cx(c), cy(r) + arrowP.textSize * 0.36f, arrowP)
                }
            }
        }

        private fun drawHomeColumns(canvas: Canvas) {
            for (p in intArrayOf(P_RED, P_YELLOW)) {
                val hcP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLORS[p]; alpha = 170 }
                val hbP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLORS[p]; style = Paint.Style.STROKE; strokeWidth = 1f * dp
                }
                for (i in 0..4) {
                    val (r, c) = HOME_COL[p][i][0] to HOME_COL[p][i][1]
                    val rect = RectF(cellL(c), cellT(r), cellL(c) + cellSize, cellT(r) + cellSize)
                    canvas.drawRoundRect(rect, 3f * dp, 3f * dp, hcP)
                    canvas.drawRoundRect(rect, 3f * dp, 3f * dp, hbP)
                }
            }
        }

        private fun drawCenter(canvas: Canvas) {
            // 3×3 center at rows/cols 6-8
            val cBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111118") }
            canvas.drawRect(cellL(6), cellT(6), cellL(9), cellT(9), cBgP)

            val midX = bLeft + 7.5f * cellSize
            val midY = bTop  + 7.5f * cellSize
            val hs   = cellSize * 1.48f

            // Red triangle (left)
            drawTriangle(canvas, P_RED,
                midX - hs, midY - hs,
                midX - hs, midY + hs,
                midX, midY)
            // Yellow triangle (right)
            drawTriangle(canvas, P_YELLOW,
                midX + hs, midY - hs,
                midX + hs, midY + hs,
                midX, midY)

            // Center star
            val sP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF"); textAlign = Paint.Align.CENTER
                textSize = cellSize * 1.1f
            }
            canvas.drawText("★", midX, midY + sP.textSize * 0.36f, sP)
        }

        private fun drawTriangle(canvas: Canvas, player: Int,
                                  x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val p = Path()
            p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.close()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLORS[player]; alpha = 200 }
            canvas.drawPath(p, tp)
        }

        private fun drawPieces(canvas: Canvas) {
            for (p in intArrayOf(P_RED, P_YELLOW)) {
                for (i in 0..3) {
                    val pos = pieces[p][i]
                    if (pos == POS_FINISHED) continue

                    val (row, col) = when {
                        pos == POS_YARD -> YARD_SLOTS[p][i][0] to YARD_SLOTS[p][i][1]
                        pos in HOME_START..56 -> HOME_COL[p][pos - HOME_START][0] to HOME_COL[p][pos - HOME_START][1]
                        else -> TRACK[pos][0] to TRACK[pos][1]
                    }

                    val pcx   = cx(col)
                    val pcy   = cy(row)
                    val r     = cellSize * 0.32f
                    val isHl  = (diceRolled && currentPlayer == p && i in highlightedPieces)

                    // Glow for valid-move pieces
                    if (isHl) {
                        val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLORS[p]; alpha = 90
                            maskFilter = BlurMaskFilter(cellSize * 0.5f, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.drawCircle(pcx, pcy, r * 1.7f, glowP)
                    }

                    // Shadow
                    val shP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(90, 0, 0, 0)
                        maskFilter = BlurMaskFilter(3f * dp, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawCircle(pcx + dp, pcy + 2f * dp, r, shP)

                    // Body
                    val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLORS[p] }
                    canvas.drawCircle(pcx, pcy, r, bodyP)

                    // Highlight sheen
                    val sheenP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; alpha = 55
                    }
                    canvas.drawCircle(pcx - r * 0.25f, pcy - r * 0.3f, r * 0.45f, sheenP)

                    // Outline
                    val outP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isHl) Color.WHITE else DARK_COLORS[p]
                        style = Paint.Style.STROKE
                        strokeWidth = (if (isHl) 2.5f else 1.5f) * dp
                    }
                    canvas.drawCircle(pcx, pcy, r, outP)

                    // Number label
                    val numP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textAlign = Paint.Align.CENTER
                        textSize = r * 1.0f; isFakeBoldText = true
                    }
                    canvas.drawText("${i + 1}", pcx, pcy + numP.textSize * 0.36f, numP)

                    // Touch target
                    pRects[p][i].set(pcx - r * 1.8f, pcy - r * 1.8f, pcx + r * 1.8f, pcy + r * 1.8f)
                }
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action != MotionEvent.ACTION_UP) return true
            val x = ev.x; val y = ev.y

            // Check piece taps for the current player
            val checkPlayer = if (vsAI) humanPlayer else currentPlayer
            for (i in 0..3) {
                if (pRects[checkPlayer][i].contains(x, y)) {
                    SoundPlayer.play("ui_click")
                    humanPieceTapped(i)
                    return true
                }
            }

            // Tap anywhere else to pass when no moves
            if (diceRolled && validMoves(currentPlayer, diceValue).isEmpty()) {
                autoPass()
            }
            return true
        }
    }

    // ─── HUD View ─────────────────────────────────────────────────────────────

    inner class LudoHudView(ctx: Context) : View(ctx) {
        private val dp  = resources.displayMetrics.density
        private val sp  = resources.displayMetrics.scaledDensity
        private val bgP = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP= Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val ttP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; isFakeBoldText = true; textAlign = Paint.Align.LEFT
            textSize = 13f * sp.coerceAtMost(3f)
        }
        private val subP= Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E"); textAlign = Paint.Align.LEFT
            textSize = 10f * sp.coerceAtMost(3f)
        }
        private val btnBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val btnTxt= Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 11f * sp.coerceAtMost(3f)
        }

        private var titleTxt  = ""
        private var subTxt    = ""
        private var diceVal   = 0
        private var diceRldSt = false
        private var curPlayer = P_RED

        private val backR = RectF(); private val menuR = RectF()
        private val diceR = RectF()

        fun update(t: String, s: String, dv: Int, dr: Boolean, cp: Int) {
            titleTxt = t; subTxt = s; diceVal = dv; diceRldSt = dr; curPlayer = cp; invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 50f*dp; val bh = 28f*dp; val by = (h - bh)/2f
            backR.set(6f*dp, by, 6f*dp+bw, by+bh)
            menuR.set(w-bw-6f*dp, by, w-6f*dp, by+bh)
            val ds = 46f*dp
            diceR.set(w-bw-6f*dp-ds-8f*dp, (h-ds)/2f, w-bw-6f*dp-8f*dp, (h+ds)/2f)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_UP) {
                when {
                    backR.contains(ev.x, ev.y) -> { SoundPlayer.play("ui_click"); @Suppress("DEPRECATION") (context as LudoActivity).onBackPressed() }
                    menuR.contains(ev.x, ev.y) -> { SoundPlayer.play("ui_click"); showMenuDialog() }
                    else -> { SoundPlayer.play("ui_click"); humanRoll() }
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, h - dp, w, h, divP)

            val rr = 5f*dp
            canvas.drawRoundRect(backR, rr, rr, btnBg)
            canvas.drawRoundRect(menuR, rr, rr, btnBg)
            canvas.drawText("← Back", backR.centerX(), backR.centerY() + btnTxt.textSize*0.36f, btnTxt)
            canvas.drawText("Menu",   menuR.centerX(), menuR.centerY() + btnTxt.textSize*0.36f, btnTxt)

            // Draw dice
            drawDice(canvas, diceR)

            // Title + sub — between Back button and dice
            val infoLeft = backR.right + 10f*dp
            val infoRight = diceR.left - 6f*dp
            if (infoLeft < infoRight) {
                ttP.textAlign  = Paint.Align.LEFT
                subP.textAlign = Paint.Align.LEFT
                // Player color dot
                val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLORS[curPlayer]
                }
                canvas.drawCircle(infoLeft + 5f*dp, h*0.35f, 5f*dp, dotP)
                canvas.drawText(titleTxt, infoLeft + 16f*dp, h*0.35f + ttP.textSize*0.36f, ttP)
                canvas.drawText(subTxt,  infoLeft + 6f*dp,  h*0.72f + subP.textSize*0.36f, subP)
            }
        }

        private fun drawDice(canvas: Canvas, r: RectF) {
            val active = diceRldSt && ((!diceRolled) || currentPlayer == humanPlayer || !vsAI)
            val diceBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (diceVal > 0 && diceRldSt) Color.parseColor("#1E2E1E") else Color.parseColor("#222222")
            }
            canvas.drawRoundRect(r, 8f*dp, 8f*dp, diceBg)

            if (diceVal == 0) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
                    textSize = 22f * (resources.displayMetrics.scaledDensity.coerceAtMost(3f))
                }
                canvas.drawText("🎲", r.centerX(), r.centerY() + tp.textSize*0.36f, tp)
            } else {
                drawDiceDots(canvas, r, diceVal)
            }

            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (diceRldSt) Color.parseColor("#7FC8F8") else Color.parseColor("#444444")
                style = Paint.Style.STROKE; strokeWidth = 1.5f*dp
            }
            canvas.drawRoundRect(r, 8f*dp, 8f*dp, border)
        }

        private fun drawDiceDots(canvas: Canvas, r: RectF, v: Int) {
            val dotR = r.width() * 0.09f
            val off  = r.width() * 0.28f
            val cx   = r.centerX(); val cy = r.centerY()
            val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val pts  = when (v) {
                1 -> listOf(cx to cy)
                2 -> listOf(cx-off to cy-off, cx+off to cy+off)
                3 -> listOf(cx-off to cy-off, cx to cy, cx+off to cy+off)
                4 -> listOf(cx-off to cy-off, cx+off to cy-off, cx-off to cy+off, cx+off to cy+off)
                5 -> listOf(cx-off to cy-off, cx+off to cy-off, cx to cy, cx-off to cy+off, cx+off to cy+off)
                6 -> listOf(cx-off to cy-off, cx+off to cy-off, cx-off to cy, cx+off to cy, cx-off to cy+off, cx+off to cy+off)
                else -> emptyList()
            }
            pts.forEach { (px, py) -> canvas.drawCircle(px, py, dotR, dotP) }
        }
    }
}
