package com.mkdev.mkboardgames

import android.app.AlertDialog
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.checkers.CheckersRuleEngine
import com.mkdev.mkboardgames.games.checkers.InternationalDraughtsRuleEngine
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine
import com.mkdev.mkboardgames.games.connectfour.ConnectFourPiece
import com.mkdev.mkboardgames.games.connectfour.ConnectFourRuleEngine
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseRuleEngine
import com.mkdev.mkboardgames.games.go.GoRuleEngine
import com.mkdev.mkboardgames.games.ludo.LudoRuleEngine
import com.mkdev.mkboardgames.games.ludo.LudoSetup
import com.mkdev.mkboardgames.games.morabaraba.MorabarabaRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import com.mkdev.mkboardgames.games.shogi.ShogiRuleEngine
import com.mkdev.mkboardgames.games.tictactoe.TicTacToePiece
import com.mkdev.mkboardgames.games.tictactoe.TicTacToeRuleEngine
import com.mkdev.mkboardgames.games.xiangqi.XiangqiRuleEngine
import com.mkdev.mkboardgames.games.yote.YoteRuleEngine
import com.mkdev.mkboardgames.ui.BoardView
import com.mkdev.mkboardgames.ui.BoardStyleSwitchView
import com.mkdev.mkboardgames.ui.ChessBoardStyle
import com.mkdev.mkboardgames.ui.ConnectFourBoardStyle
import com.mkdev.mkboardgames.ui.DraughtsBoardStyle
import com.mkdev.mkboardgames.ui.FoxAndGeeseBoardStyle
import com.mkdev.mkboardgames.ui.LudoBoardView
import com.mkdev.mkboardgames.ui.MorabarabaBoardStyle
import com.mkdev.mkboardgames.ui.MorabaraBoardView
import com.mkdev.mkboardgames.ui.OthelloBoardStyle
import com.mkdev.mkboardgames.ui.ShogiBoardStyle
import com.mkdev.mkboardgames.ui.XiangqiBoardStyle
import com.mkdev.mkboardgames.ui.YoteBoardView
import com.mkdev.mkboardgames.games.onitama.OnitamaRuleEngine
import com.mkdev.mkboardgames.ui.OnitamaBoardView
import org.json.JSONArray
import org.json.JSONObject

class ReplayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVES_JSON = "moves_json"
        const val EXTRA_GAME_TYPE  = "game_type"
        const val EXTRA_RESULT     = "game_result"
        const val EXTRA_BOARD_STYLE_INDEX = "board_style_index"
        const val EXTRA_LOCK_BOARD_STYLE = "lock_board_style"
        const val EXTRA_ONITAMA_SETUP_SEED = "onitama_setup_seed"
        const val EXTRA_BOARD_SIZE = "board_size"   // for TicTacToe
        const val EXTRA_MORABARABA_PIECE_COUNT = "morabaraba_piece_count"
        private val MOVE_METADATA_KEYS = arrayOf(
            "dice", "player", "token", "targetProgress", "drop", "promote", "pass",
            "pawnDouble", "enPassant", "castle", "card",
        )

        fun buildMovesJson(moves: List<Move>): String {
            val arr = JSONArray()
            for (m in moves) {
                val obj = JSONObject()
                obj.put("fr", m.from.row); obj.put("fc", m.from.col)
                obj.put("tr", m.to.row);   obj.put("tc", m.to.col)
                if (m.captures.isNotEmpty()) {
                    val caps = JSONArray()
                    for (c in m.captures) {
                        val cobj = JSONObject(); cobj.put("r", c.row); cobj.put("c", c.col)
                        caps.put(cobj)
                    }
                    obj.put("caps", caps)
                }
                if (m.promotionType != null) obj.put("promo", m.promotionType)
                (m.metadata["bonusCapture"] as? Position)?.let { bonus ->
                    obj.put(
                        "bonusCapture",
                        JSONObject().apply {
                            put("r", bonus.row)
                            put("c", bonus.col)
                        },
                    )
                }
                for (key in MOVE_METADATA_KEYS) {
                    val value = m.metadata[key]
                    if (value is Int || value is String || value is Boolean) obj.put(key, value)
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        private fun parseMoves(json: String): List<Move> {
            val arr  = JSONArray(json)
            val list = mutableListOf<Move>()
            for (i in 0 until arr.length()) {
                val obj   = arr.getJSONObject(i)
                val from  = Position(obj.getInt("fr"), obj.getInt("fc"))
                val to    = Position(obj.getInt("tr"), obj.getInt("tc"))
                val caps  = mutableListOf<Position>()
                if (obj.has("caps")) {
                    val ca = obj.getJSONArray("caps")
                    for (j in 0 until ca.length()) {
                        val c = ca.getJSONObject(j)
                        caps += Position(c.getInt("r"), c.getInt("c"))
                    }
                }
                val metadata = mutableMapOf<String, Any>()
                obj.optJSONObject("bonusCapture")?.let { bonus ->
                    metadata["bonusCapture"] = Position(
                        bonus.getInt("r"),
                        bonus.getInt("c"),
                    )
                }
                val promo = if (obj.has("promo")) obj.getString("promo") else null
                for (key in MOVE_METADATA_KEYS) {
                    if (!obj.has(key) || obj.isNull(key)) continue
                    when (val value = obj.get(key)) {
                        is Boolean -> metadata[key] = value
                        is String -> metadata[key] = value
                        is Number -> metadata[key] = value.toInt()
                    }
                }
                list += Move(from, to, caps, promo, metadata)
            }
            return list
        }
    }

    // ─── Data ─────────────────────────────────────────────────────────────────

    data class CaptureSnapshot(
        val byWhite: List<Piece>,   // pieces WHITE captured  (are BLACK pieces)
        val byBlack: List<Piece>    // pieces BLACK captured  (are WHITE pieces)
    )

    // ─── Views ────────────────────────────────────────────────────────────────

    private var boardView:     BoardView?          = null
    private var ticBoardView:  TicReplayBoard?     = null
    private var connectBoardView: ConnectReplayBoard? = null
    private var moraBoardView: MorabaraBoardView?  = null
    private var ludoBoardView: LudoBoardView? = null
    private var yoteBoardView: YoteBoardView? = null
    private var onitamaBoardView: OnitamaBoardView? = null
    private lateinit var seekBar:      SeekBar
    private lateinit var controlsView: ReplayControlsView
    private lateinit var infoView:     ReplayInfoView
    private lateinit var captureView:  ReplayCaptureView
    private lateinit var boardStyleSwitch: BoardStyleSwitchView

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

    // ─── State ────────────────────────────────────────────────────────────────

    private var states:           List<GameState>       = emptyList()
    private var moves:            List<Move>            = emptyList()
    private var moveLabels:       List<String>          = emptyList()
    private var captureSnapshots: List<CaptureSnapshot> = emptyList()
    private var cursor     = 0
    private var isPlaying  = false
    private val handler    = Handler(Looper.getMainLooper())
    private var resultText = ""
    private var gameType   = "CHESS"
    private var lockedBoardStyleIndex: Int? = null


    // ─── Vibration ────────────────────────────────────────────────────────────

    private fun vibrateMove() {
        try {
            @Suppress("DEPRECATION")
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(22L, 60))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(22L)
            }
        } catch (_: Exception) {}
    }

    // ─── Speed ────────────────────────────────────────────────────────────────

    private val speedMs    = intArrayOf(4000, 2000, 1000)
    val         speedLabel = arrayOf("1×", "2×", "4×")
    private var speedIdx   = 2
    private val autoDelay  get() = speedMs[speedIdx]

    fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % speedMs.size
        infoView.invalidate()
    }

    // ─── Auto-play ────────────────────────────────────────────────────────────

    private val autoPlayRunnable = object : Runnable {
        override fun run() {
            if (cursor < states.lastIndex) {
                stepTo(cursor + 1, animate = true)
                handler.postDelayed(this, autoDelay.toLong())
            } else {
                isPlaying = false
                controlsView.invalidate()
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullscreen()
        SoundPlayer.init(this)

        gameType          = intent.getStringExtra(EXTRA_GAME_TYPE)  ?: "CHESS"
        val movesJson     = intent.getStringExtra(EXTRA_MOVES_JSON) ?: "[]"
        resultText        = intent.getStringExtra(EXTRA_RESULT)     ?: ""
        if (intent.getBooleanExtra(EXTRA_LOCK_BOARD_STYLE, false)) {
            lockedBoardStyleIndex = intent.getIntExtra(EXTRA_BOARD_STYLE_INDEX, 0)
        }
        val ticBoardSize  = intent.getIntExtra(EXTRA_BOARD_SIZE, 3)
        val morabarabaPieceCount = intent.getIntExtra(EXTRA_MORABARABA_PIECE_COUNT, 12)
        val onitamaSetupSeed = intent.getLongExtra(EXTRA_ONITAMA_SETUP_SEED, Long.MIN_VALUE)

        val isTicTacToe  = gameType == "TICTACTOE"
        val isConnectFour = gameType == "CONNECTFOUR"
        val isMorabaraba = gameType == "MORABARABA"
        val isLudo = gameType == "LUDO"
        val isYote = gameType == "YOTE"
        val isOnitama = gameType == "ONITAMA"

        val engine: RuleEngine = when (gameType) {
            "TICTACTOE"  -> TicTacToeRuleEngine(ticBoardSize, ticBoardSize)
            "CONNECTFOUR" -> ConnectFourRuleEngine()
            "CHECKERS"   -> CheckersRuleEngine()
            "INTERNATIONAL_DRAUGHTS" -> InternationalDraughtsRuleEngine()
            "MORABARABA" -> MorabarabaRuleEngine(morabarabaPieceCount)
            "FOX_AND_GEESE" -> FoxAndGeeseRuleEngine()
            "LUDO" -> LudoRuleEngine()
            "XIANGQI" -> XiangqiRuleEngine()
            "SHOGI" -> ShogiRuleEngine()
            "GO" -> GoRuleEngine()
            "YOTE" -> YoteRuleEngine()
            "ONITAMA" -> OnitamaRuleEngine()
            else         -> ChessRuleEngine()
        }

        // Reconstruct every board state from the move list
        moves = parseMoves(movesJson)
        val initialState = if (isOnitama && onitamaSetupSeed != Long.MIN_VALUE) {
            (engine as OnitamaRuleEngine).initialState(onitamaSetupSeed)
        } else {
            engine.initialState()
        }
        val allStates  = mutableListOf(initialState)
        val allLabels  = mutableListOf("Start")
        for ((idx, move) in moves.withIndex()) {
            val actor = if (isLudo) {
                val player = (move.metadata["player"] as? Int ?: idx % LudoSetup.PLAYER_COUNT)
                    .coerceIn(0, LudoSetup.PLAYER_COUNT - 1)
                LudoSetup.PLAYER_NAMES[player]
            } else if (gameType == "FOX_AND_GEESE") {
                if (idx % 2 == 0) "Fox" else "Geese"
            } else if (gameType == "SHOGI") {
                if (idx % 2 == 0) "Sente" else "Gote"
            } else {
                if (idx % 2 == 0) "White" else "Black"
            }
            allStates += engine.applyMove(allStates.last(), move)
            allLabels += if (isLudo) "${idx + 1}. $actor" else "${idx / 2 + 1}. $actor"
        }
        states     = allStates
        moveLabels = allLabels

        // Build per-state capture snapshots (not meaningful for TicTacToe or Othello)
        val hasCaptures = !isTicTacToe && !isConnectFour && gameType != "OTHELLO" && !isLudo && !isOnitama
        val snaps = mutableListOf(CaptureSnapshot(emptyList(), emptyList()))
        if (hasCaptures) {
            if (gameType == "SHOGI") {
                for (state in states.drop(1)) {
                    snaps += CaptureSnapshot(
                        state.hands[PieceColor.WHITE].orEmpty(),
                        state.hands[PieceColor.BLACK].orEmpty(),
                    )
                }
            } else {
                for ((idx, move) in moves.withIndex()) {
                    val prevState  = states[idx]
                    val prev       = snaps.last()
                    val moverColor = if (isMorabaraba) prevState.currentTurn
                                     else prevState.get(move.from)?.color ?: prevState.currentTurn

                    val byWhite = prev.byWhite.toMutableList()
                    val byBlack = prev.byBlack.toMutableList()
                    for (pos in move.captures) {
                        val captured = prevState.get(pos) ?: continue
                        // White captures → black pieces go to byWhite (bottom strip)
                        // Black captures → white pieces go to byBlack (top strip)
                        if (moverColor == PieceColor.WHITE) byWhite += captured
                        else                                byBlack += captured
                    }
                    snaps += CaptureSnapshot(
                        byWhite.sortedByDescending { it.value() },
                        byBlack.sortedByDescending { it.value() }
                    )
                }
            }
        } else {
            repeat(moves.size) { snaps += CaptureSnapshot(emptyList(), emptyList()) }
        }
        captureSnapshots = snaps

        // ── Build UI ────────────────────────────────────────────────────────
        val dp   = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        infoView     = ReplayInfoView(this)
        captureView  = ReplayCaptureView(this, gameType)
        boardStyleSwitch = BoardStyleSwitchView(this)
        val boardStyleRow = LinearLayout(this).apply {
            gravity = Gravity.START
            setPadding((10 * dp).toInt(), 0, 0, 0)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        boardStyleRow.addView(
            boardStyleSwitch,
            LinearLayout.LayoutParams((118 * dp).toInt(), (44 * dp).toInt()),
        )
        seekBar      = SeekBar(this).apply {
            max      = (states.size - 1).coerceAtLeast(1)
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7FC8F8"))
            thumbTintList    = android.content.res.ColorStateList.valueOf(Color.parseColor("#7FC8F8"))
            val vp = (10 * dp).toInt(); val hp = (16 * dp).toInt()
            setPadding(hp, vp, hp, vp)
        }
        controlsView = ReplayControlsView(this)

        root.addView(infoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * dp).toInt()))
        if (supportsBoardStyleSwitch() && lockedBoardStyleIndex == null) {
            root.addView(boardStyleRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * dp).toInt(),
            ))
        }

        when {
            isTicTacToe -> {
                val tbv = TicReplayBoard(this, ticBoardSize, engine as TicTacToeRuleEngine)
                ticBoardView = tbv
                root.addView(tbv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            isConnectFour -> {
                val cbv = ConnectReplayBoard(this, engine as ConnectFourRuleEngine)
                connectBoardView = cbv
                root.addView(cbv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            isMorabaraba -> {
                val mbv = MorabaraBoardView(this).apply {
                    ruleEngine  = engine as MorabarabaRuleEngine
                    gameState   = states.first()
                    isLocked    = true
                    onMoveMade  = null
                }
                moraBoardView = mbv
                root.addView(mbv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            isLudo -> {
                val lbv = LudoBoardView(this).apply {
                    gameState = states.first()
                    isLocked = true
                    onGameOverTapped = { showReplayResultDialog() }
                }
                ludoBoardView = lbv
                root.addView(lbv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            isYote -> {
                val ybv = YoteBoardView(this).apply {
                    gameState = states.first()
                    isLocked = true
                    onMoveMade = null
                    onGameOverTapped = { showReplayResultDialog() }
                }
                yoteBoardView = ybv
                root.addView(ybv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            isOnitama -> {
                val obv = OnitamaBoardView(this).apply {
                    gameState = states.first()
                    selectedCardId = (engine as OnitamaRuleEngine).cards(states.first(), PieceColor.WHITE).firstOrNull()?.id
                    isLocked = true
                    onGameOverTapped = { showReplayResultDialog() }
                }
                onitamaBoardView = obv
                root.addView(obv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
            else -> {
                val bv = BoardView(this).apply {
                    isLocked = true
                    ruleEngine = engine
                    onGameOverTapped = { showReplayResultDialog() }
                }
                boardView = bv
                root.addView(bv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            }
        }

        configureBoardStyleSwitch()

        // Hide capture strip for TicTacToe (no captures) and Othello
        val showCaptures = hasCaptures
        captureView.visibility = if (showCaptures) View.VISIBLE else View.GONE
        root.addView(captureView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))

        root.addView(seekBar,      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * dp).toInt()))
        root.addView(controlsView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (72 * dp).toInt()))

        setContentView(root)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { stopAutoPlay(); stepTo(p) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) = stopAutoPlay()
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        stepTo(0)
    }

    private fun supportsBoardStyleSwitch(): Boolean = when (gameType) {
        "CHESS", "CHECKERS", "INTERNATIONAL_DRAUGHTS", "OTHELLO",
        "FOX_AND_GEESE", "XIANGQI", "SHOGI", "CONNECTFOUR", "MORABARABA" -> true
        else -> false
    }

    private fun configureBoardStyleSwitch() {
        if (!supportsBoardStyleSwitch()) return
        lockedBoardStyleIndex?.let {
            applyBoardStyle(it)
            return
        }

        val styleCount = when (gameType) {
            "CHESS" -> ChessBoardStyle.entries.size
            "INTERNATIONAL_DRAUGHTS" -> internationalDraughtsStyles.size
            "CHECKERS" ->
                DraughtsBoardStyle.entries.size -
                    internationalDraughtsStyles.count { it != DraughtsBoardStyle.CANVAS }
            "OTHELLO" -> OthelloBoardStyle.entries.size
            "FOX_AND_GEESE" -> foxAndGeeseStyles.size
            "XIANGQI" -> xiangqiStyles.size
            "SHOGI" -> ShogiBoardStyle.entries.size
            "CONNECTFOUR" -> ConnectFourBoardStyle.entries.size
            "MORABARABA" -> MorabarabaBoardStyle.entries.size
            else -> 2
        }
        boardStyleSwitch.setStyleCount(styleCount)
        boardStyleSwitch.setSelectedIndex(currentBoardStyleIndex(), animate = false)
        boardStyleSwitch.onStyleChanged = ::applyBoardStyle
    }

    private fun currentBoardStyleIndex(): Int = when (gameType) {
        "CHESS" -> boardView?.chessBoardStyle?.ordinal ?: 0
        "CHECKERS", "INTERNATIONAL_DRAUGHTS" -> {
            val style = boardView?.draughtsBoardStyle ?: DraughtsBoardStyle.CANVAS
            if (gameType == "INTERNATIONAL_DRAUGHTS") {
                internationalDraughtsStyles.indexOf(style).coerceAtLeast(0)
            } else {
                style.ordinal
            }
        }
        "OTHELLO" -> boardView?.othelloBoardStyle?.ordinal ?: 0
        "FOX_AND_GEESE" ->
            foxAndGeeseStyles.indexOf(
                boardView?.foxAndGeeseBoardStyle ?: FoxAndGeeseBoardStyle.CANVAS,
            ).coerceAtLeast(0)
        "XIANGQI" ->
            xiangqiStyles.indexOf(
                boardView?.xiangqiBoardStyle ?: XiangqiBoardStyle.CLASSIC,
            ).coerceAtLeast(0)
        "SHOGI" -> boardView?.shogiBoardStyle?.ordinal ?: 0
        "CONNECTFOUR" -> connectBoardView?.boardStyle?.ordinal ?: 0
        "MORABARABA" -> moraBoardView?.boardStyle?.ordinal ?: 0
        else -> 0
    }

    private fun applyBoardStyle(index: Int) {
        when (gameType) {
            "CHESS" -> boardView?.chessBoardStyle =
                ChessBoardStyle.entries.getOrElse(index) { ChessBoardStyle.CANVAS }
            "CHECKERS" -> boardView?.draughtsBoardStyle =
                DraughtsBoardStyle.entries.getOrElse(index) { DraughtsBoardStyle.CANVAS }
            "INTERNATIONAL_DRAUGHTS" -> boardView?.draughtsBoardStyle =
                internationalDraughtsStyles.getOrElse(index) { internationalDraughtsStyles.first() }
            "OTHELLO" -> boardView?.othelloBoardStyle =
                OthelloBoardStyle.entries.getOrElse(index) { OthelloBoardStyle.CANVAS }
            "FOX_AND_GEESE" -> boardView?.foxAndGeeseBoardStyle =
                foxAndGeeseStyles.getOrElse(index) { foxAndGeeseStyles.first() }
            "XIANGQI" -> boardView?.xiangqiBoardStyle =
                xiangqiStyles.getOrElse(index) { xiangqiStyles.first() }
            "SHOGI" -> boardView?.shogiBoardStyle =
                ShogiBoardStyle.entries.getOrElse(index) { ShogiBoardStyle.CLASSIC }
            "CONNECTFOUR" -> connectBoardView?.boardStyle =
                ConnectFourBoardStyle.entries.getOrElse(index) { ConnectFourBoardStyle.CANVAS }
            "MORABARABA" -> moraBoardView?.boardStyle =
                MorabarabaBoardStyle.entries.getOrElse(index) { MorabarabaBoardStyle.CANVAS }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun stepTo(idx: Int, animate: Boolean = false) {
        val newCursor = idx.coerceIn(0, states.lastIndex)

        if (animate && newCursor == cursor + 1 && cursor < moves.size) {
            val move  = moves[cursor]
            cursor    = newCursor

            seekBar.progress = cursor
            val label = moveLabels.getOrElse(cursor) { "Move $cursor" }
            infoView.update(label, cursor, states.size - 1, resultText)
            captureView.update(captureSnapshots.getOrNull(cursor) ?: CaptureSnapshot(emptyList(), emptyList()))
            controlsView.invalidate()

            vibrateMove()
            boardView?.let { bv ->
                bv.cancelMoveAnimation()
                bv.gameState = states[cursor - 1]
                val targetCursor = cursor
                bv.onMoveMade = {
                    if (cursor == targetCursor) {
                        bv.onMoveMade = null
                        bv.gameState  = states[targetCursor]
                        bv.isLocked   = true
                    }
                }
                bv.animateExternalMove(move)
            }
            ticBoardView?.showState(states[cursor])
            connectBoardView?.showState(states[cursor], animate = true)
            moraBoardView?.let { mbv ->
                mbv.gameState = states[cursor - 1]
                mbv.isLocked  = true
                mbv.onMoveMade = {
                    mbv.onMoveMade = null
                    mbv.gameState  = states[cursor]
                    mbv.isLocked   = true
                }
                mbv.animateExternalMove(move)
            }
            ludoBoardView?.let { lbv ->
                lbv.gameState = states[cursor - 1]
                lbv.isLocked = true
                lbv.animateMove(move) {
                    lbv.gameState = states[cursor]
                    lbv.isLocked = true
                }
            }
            yoteBoardView?.let { ybv ->
                ybv.cancelMoveAnimation()
                ybv.gameState = states[cursor - 1]
                ybv.isLocked = true
                val targetCursor = cursor
                ybv.onMoveMade = { _, _ ->
                    if (cursor == targetCursor) {
                        ybv.onMoveMade = null
                        ybv.gameState = states[targetCursor]
                        ybv.isLocked = true
                    }
                }
                ybv.animateMove(move)
            }
            onitamaBoardView?.let { obv ->
                obv.cancelMoveAnimation()
                obv.gameState = states[cursor - 1]
                obv.isLocked = true
                val targetCursor = cursor
                obv.onMoveMade = { _, _ ->
                    if (cursor == targetCursor) {
                        obv.onMoveMade = null
                        obv.gameState = states[targetCursor]
                        obv.isLocked = true
                    }
                }
                obv.animateMove(move, fromComputer = true)
            }
        } else {
            cursor              = newCursor
            boardView?.let {
                it.cancelMoveAnimation()
                it.onMoveMade = null
                it.gameState = states[cursor]
                it.isLocked = true
            }
            ticBoardView?.showState(states[cursor])
            connectBoardView?.showState(states[cursor])
            moraBoardView?.let { it.gameState = states[cursor]; it.isLocked = true }
            ludoBoardView?.let { it.gameState = states[cursor]; it.isLocked = true }
            yoteBoardView?.let {
                it.cancelMoveAnimation()
                it.onMoveMade = null
                it.gameState = states[cursor]
                it.isLocked = true
            }
            onitamaBoardView?.let {
                it.cancelMoveAnimation()
                it.onMoveMade = null
                it.gameState = states[cursor]
                it.isLocked = true
            }
            seekBar.progress    = cursor
            val label = moveLabels.getOrElse(cursor) { "Move $cursor" }
            infoView.update(label, cursor, states.size - 1, resultText)
            captureView.update(captureSnapshots.getOrNull(cursor) ?: CaptureSnapshot(emptyList(), emptyList()))
            controlsView.invalidate()
        }
    }

    fun onFirst()  { stopAutoPlay(); stepTo(0) }
    fun onPrev()   { stopAutoPlay(); stepTo(cursor - 1) }
    fun onNext()   { stopAutoPlay(); stepTo(cursor + 1, animate = true) }
    fun onLast()   { stopAutoPlay(); stepTo(states.lastIndex) }

    fun onTogglePlay() {
        if (isPlaying) stopAutoPlay()
        else {
            if (cursor >= states.lastIndex) stepTo(0)
            isPlaying = true
            controlsView.invalidate()
            handler.postDelayed(autoPlayRunnable, 200)
        }
    }

    private fun stopAutoPlay() {
        isPlaying = false
        handler.removeCallbacks(autoPlayRunnable)
        controlsView.invalidate()
    }

    private fun showReplayResultDialog() {
        stopAutoPlay()
        AlertDialog.Builder(this)
            .setTitle(replayGameName())
            .setMessage(resultText.ifBlank { "Replay finished." })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun replayGameName(): String = when (gameType) {
        "CHECKERS" -> "Draughts"
        "INTERNATIONAL_DRAUGHTS" -> "International Draughts"
        "FOX_AND_GEESE" -> "Fox and Geese"
        "SHOGI" -> "Shogi"
        "XIANGQI" -> "Xiangqi"
        "GO" -> "Go"
        "CHESS" -> "Chess"
        "LUDO" -> "Ludo"
        "MORABARABA" -> "Morabaraba"
        "TICTACTOE" -> "Tic-Tac-Toe"
        "CONNECTFOUR" -> "Connect Four"
        "YOTE" -> "Yoté"
        "ONITAMA" -> "Onitama"
        else -> "Replay"
    }

    override fun onPause() {
        stopAutoPlay()
        super.onPause()
    }

    override fun onDestroy() { super.onDestroy(); stopAutoPlay() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        stopAutoPlay()
        finish()
    }

    override fun onResume()                          { super.onResume(); makeFullscreen() }
    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) makeFullscreen() }

    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─── TicTacToe replay board ───────────────────────────────────────────────

    inner class TicReplayBoard(
        ctx: Context,
        private val bs: Int,
        private val ticEngine: TicTacToeRuleEngine
    ) : View(ctx) {

        private var state     = ticEngine.initialState()
        private var prevState = ticEngine.initialState()
        private var winLine:  List<Int>? = null
        private val cellScale = HashMap<Int, Float>()

        private val dp = resources.displayMetrics.density
        private var boardLeft = 0f; private var boardTop = 0f; private var cellSize = 0f

        private val bgP   = Paint().apply { color = Color.parseColor("#121212") }
        private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#383838"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val xP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val oP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val winP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            if (w <= 0 || h <= 0) return
            val pad  = 24f * dp
            val size = minOf(w - pad * 2, h - pad * 2)
            cellSize  = size / bs.toFloat()
            boardLeft = (w - size) / 2f
            boardTop  = (h - size) / 2f
            lineP.strokeWidth = maxOf(cellSize * 0.022f, 2f)
            xP.strokeWidth    = cellSize * 0.085f
            oP.strokeWidth    = cellSize * 0.085f
            winP.strokeWidth  = cellSize * 0.05f
        }

        fun showState(newState: GameState) {
            val old = prevState
            prevState = newState
            state     = newState
            winLine   = ticEngine.winningLine(newState)

            // Find any newly placed piece and animate it with an overshoot pop-in
            for (idx in 0 until bs * bs) {
                if (old.board[idx] == null && newState.board[idx] != null) {
                    cellScale[idx] = 0f
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration     = 220L
                        interpolator = OvershootInterpolator(1.6f)
                        addUpdateListener { cellScale[idx] = it.animatedValue as Float; invalidate() }
                        start()
                    }
                    return   // only one piece placed per move
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            drawGrid(canvas); drawPieces(canvas); drawWinLine(canvas)
        }

        private fun drawGrid(canvas: Canvas) {
            val right  = boardLeft + bs * cellSize
            val bottom = boardTop  + bs * cellSize
            for (i in 1 until bs) {
                canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, bottom, lineP)
                canvas.drawLine(boardLeft, boardTop + i * cellSize, right, boardTop + i * cellSize, lineP)
            }
        }

        private fun drawPieces(canvas: Canvas) {
            for (row in 0 until bs) for (col in 0 until bs) {
                val piece = state.get(row, col) as? TicTacToePiece ?: continue
                val idx   = row * bs + col
                val scale = cellScale[idx] ?: 1f
                val cx = boardLeft + col * cellSize + cellSize / 2f
                val cy = boardTop  + row * cellSize + cellSize / 2f
                val r  = cellSize * 0.29f * scale
                if (piece.color == PieceColor.WHITE) {
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, xP)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, xP)
                } else {
                    canvas.drawCircle(cx, cy, r, oP)
                }
            }
        }

        private fun drawWinLine(canvas: Canvas) {
            val line = winLine ?: return
            val color = (state.board[line.first()] as? TicTacToePiece)?.color
            winP.color = if (color == PieceColor.WHITE) Color.parseColor("#EF5350")
                         else Color.parseColor("#7FC8F8")
            winP.alpha = 220
            val ax = boardLeft + (line.first() % bs) * cellSize + cellSize / 2f
            val ay = boardTop  + (line.first() / bs) * cellSize + cellSize / 2f
            val bx = boardLeft + (line.last()  % bs) * cellSize + cellSize / 2f
            val by = boardTop  + (line.last()  / bs) * cellSize + cellSize / 2f
            canvas.drawLine(ax, ay, bx, by, winP)
        }
    }

    // ─── Connect Four replay board ────────────────────────────────────────────

    inner class ConnectReplayBoard(
        ctx: Context,
        private val connectEngine: ConnectFourRuleEngine
    ) : View(ctx) {
        private var state = connectEngine.initialState()
        private var winLine: List<Int>? = null
        private var fallingAnimator: ValueAnimator? = null
        private var fallingIndex: Int? = null
        private var fallingColor: PieceColor? = null
        private var fallingProgress = 0f
        private var animationGeneration = 0
        private val dp = resources.displayMetrics.density
        private var boardLeft = 0f
        private var boardTop = 0f
        private var cellSize = 0f
        private val boardImageRect = RectF()
        private var imageCellWidth = 0f
        private var imageCellHeight = 0f
        var boardStyle: ConnectFourBoardStyle = ConnectFourBoardStyle.CANVAS
            set(value) {
                if (field == value) return
                field = value
                updateBoardGeometry()
                winP.strokeWidth = cellSize * 0.065f
                invalidate()
            }

        private val blueBoardBitmap: Bitmap? = try {
            context.assets.open("connect_four_blue.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val redPieceBitmap: Bitmap? = try {
            context.assets.open("connect_four_red_piece.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val yellowPieceBitmap: Bitmap? = try {
            context.assets.open("connect_four_yellow_piece.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        private val pieceBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val imageGridX = floatArrayOf(
            207f / 1261f, 349f / 1261f, 490.5f / 1261f,
            633f / 1261f, 774f / 1261f, 915f / 1261f,
            1057f / 1261f,
        )
        private val imageGridY = floatArrayOf(
            128.5f / 1002f, 263.5f / 1002f, 399f / 1002f,
            535.5f / 1002f, 671f / 1002f, 807f / 1002f,
        )

        private val bgP = Paint().apply { color = Color.parseColor("#121212") }
        private val boardP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#24527A") }
        private val holeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#101820") }
        private val redP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        private val yellowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
        private val highlightP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 127, 200, 248)
        }
        private val winP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            if (w <= 0 || h <= 0) return
            updateBoardGeometry()
            winP.strokeWidth = cellSize * 0.065f
        }

        private fun updateBoardGeometry() {
            if (width <= 0 || height <= 0) return
            val bitmap = boardBitmap()
            if (bitmap != null) {
                val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                boardImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                imageCellWidth = (imageGridX.last() - imageGridX.first()) *
                    boardImageRect.width() / (ConnectFourRuleEngine.COLUMNS - 1)
                imageCellHeight = (imageGridY.last() - imageGridY.first()) *
                    boardImageRect.height() / (ConnectFourRuleEngine.ROWS - 1)
                cellSize = minOf(imageCellWidth, imageCellHeight)
                return
            }

            val pad = 20f * dp
            cellSize = minOf(
                (width - pad * 2) / ConnectFourRuleEngine.COLUMNS,
                (height - pad * 2) / ConnectFourRuleEngine.ROWS
            )
            boardLeft = (width - cellSize * ConnectFourRuleEngine.COLUMNS) / 2f
            boardTop = (height - cellSize * ConnectFourRuleEngine.ROWS) / 2f
        }

        private fun boardBitmap(): Bitmap? = when (boardStyle) {
            ConnectFourBoardStyle.CANVAS -> null
            ConnectFourBoardStyle.BLUE -> blueBoardBitmap
        }

        private fun isImageBoard() = boardBitmap() != null

        private fun imageColumnCenter(column: Int): Float =
            boardImageRect.left + boardImageRect.width() * imageGridX[column]

        private fun imageRowCenter(row: Int): Float =
            boardImageRect.top + boardImageRect.height() * imageGridY[row]

        fun showState(newState: GameState, animate: Boolean = false) {
            animationGeneration++
            fallingAnimator?.cancel()
            fallingAnimator = null
            fallingIndex = null
            fallingColor = null
            fallingProgress = 0f
            state = newState
            winLine = if (animate) null else connectEngine.winningLine(newState)

            if (animate) {
                val move = newState.lastMove
                val piece = move?.let { newState.get(it.to) as? ConnectFourPiece }
                if (move != null && piece != null) {
                    val idx = move.to.row * ConnectFourRuleEngine.COLUMNS + move.to.col
                    fallingIndex = idx
                    fallingColor = piece.color
                    val generation = animationGeneration
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        // Match the live board: lower landing slots take longer.
                        duration = 250L + (move.to.row + 1) * 55L
                        interpolator = AccelerateInterpolator(1.25f)
                        addUpdateListener {
                            fallingProgress = it.animatedValue as Float
                            invalidate()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (generation != animationGeneration) return
                                fallingAnimator = null
                                fallingIndex = null
                                fallingColor = null
                                fallingProgress = 0f
                                winLine = connectEngine.winningLine(state)
                                invalidate()
                            }
                        })
                    }
                    fallingAnimator = animator
                    animator.start()
                } else {
                    winLine = connectEngine.winningLine(newState)
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
            if (cellSize <= 0f) return
            val right = boardLeft + ConnectFourRuleEngine.COLUMNS * cellSize
            val bottom = boardTop + ConnectFourRuleEngine.ROWS * cellSize
            val imageBoard = isImageBoard()
            val fallingRow = fallingIndex?.div(ConnectFourRuleEngine.COLUMNS)
            val fallingCol = fallingIndex?.rem(ConnectFourRuleEngine.COLUMNS)
            val fallingX = fallingCol?.let {
                if (imageBoard) imageColumnCenter(it)
                else boardLeft + it * cellSize + cellSize / 2f
            }
            val fallingTargetY = fallingRow?.let {
                if (imageBoard) imageRowCenter(it)
                else boardTop + it * cellSize + cellSize / 2f
            }
            val boardStartY = if (imageBoard) boardImageRect.top else boardTop
            val fallingStartY = boardStartY - cellSize * 0.85f
            val fallingY = fallingTargetY?.let {
                fallingStartY + (it - fallingStartY) * fallingProgress
            }
            val fallingRadius = cellSize * if (imageBoard) 0.37f else 0.31f

            if (fallingX != null && fallingY != null && fallingColor != null && fallingY < boardStartY) {
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
            }

            if (imageBoard) {
                boardBitmap()?.let {
                    canvas.drawBitmap(
                        it,
                        null,
                        boardImageRect,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                }
            } else {
                canvas.drawRoundRect(
                    boardLeft, boardTop, right, bottom, cellSize * .14f, cellSize * .14f, boardP
                )
            }
            val holePath = Path()
            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val cx = if (imageBoard) imageColumnCenter(col)
                else boardLeft + col * cellSize + cellSize / 2f
                val cy = if (imageBoard) imageRowCenter(row)
                else boardTop + row * cellSize + cellSize / 2f
                val holeRadius = cellSize * if (imageBoard) 0.37f else 0.36f
                if (!imageBoard) canvas.drawCircle(cx, cy, holeRadius, holeP)
                holePath.addCircle(cx, cy, holeRadius, Path.Direction.CW)
            }

            if (fallingX != null && fallingY != null && fallingColor != null && fallingY >= boardStartY) {
                canvas.save()
                canvas.clipPath(holePath)
                drawDisc(canvas, fallingX, fallingY, fallingRadius, fallingColor!!)
                canvas.restore()
            }

            state.lastMove?.let { move ->
                val cx = if (imageBoard) imageColumnCenter(move.to.col)
                else boardLeft + move.to.col * cellSize + cellSize / 2f
                val cy = if (imageBoard) imageRowCenter(move.to.row)
                else boardTop + move.to.row * cellSize + cellSize / 2f
                canvas.drawCircle(cx, cy, cellSize * 0.43f, highlightP)
            }

            for (row in 0 until ConnectFourRuleEngine.ROWS) for (col in 0 until ConnectFourRuleEngine.COLUMNS) {
                val piece = state.get(row, col) as? ConnectFourPiece ?: continue
                val idx = row * ConnectFourRuleEngine.COLUMNS + col
                if (idx == fallingIndex) continue
                val cx = if (imageBoard) imageColumnCenter(col)
                else boardLeft + col * cellSize + cellSize / 2f
                val cy = if (imageBoard) imageRowCenter(row)
                else boardTop + row * cellSize + cellSize / 2f
                drawDisc(canvas, cx, cy, cellSize * if (imageBoard) 0.37f else 0.31f, piece.color)
            }
            winLine?.takeIf { it.size >= 2 }?.let { line ->
                val first = line.first()
                val last = line.last()
                canvas.drawLine(
                    if (imageBoard) imageColumnCenter(first % ConnectFourRuleEngine.COLUMNS)
                    else boardLeft + (first % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    if (imageBoard) imageRowCenter(first / ConnectFourRuleEngine.COLUMNS)
                    else boardTop + (first / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    if (imageBoard) imageColumnCenter(last % ConnectFourRuleEngine.COLUMNS)
                    else boardLeft + (last % ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    if (imageBoard) imageRowCenter(last / ConnectFourRuleEngine.COLUMNS)
                    else boardTop + (last / ConnectFourRuleEngine.COLUMNS) * cellSize + cellSize / 2f,
                    winP
                )
            }
        }

        private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: PieceColor) {
            val bitmap = if (color == PieceColor.WHITE) yellowPieceBitmap else redPieceBitmap
            if (bitmap != null && isImageBoard()) {
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    pieceBitmapPaint,
                )
            } else {
                canvas.drawCircle(cx, cy, radius, if (color == PieceColor.WHITE) redP else yellowP)
                canvas.drawCircle(
                    cx - radius * 0.22f,
                    cy - radius * 0.25f,
                    radius * 0.15f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = Color.argb(70, 255, 255, 255)
                    },
                )
            }
        }
    }

    // ─── Info header ─────────────────────────────────────────────────────────

    inner class ReplayInfoView(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP  = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            isFakeBoldText = true; textSize = 15f * sp.coerceAtMost(3f)
        }
        private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 13f * sp.coerceAtMost(3f)
        }
        private val btnP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 14f * sp.coerceAtMost(3f)
        }
        private val btnBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }

        private val backRect  = RectF()
        private val speedRect = RectF()

        private var label = "Replay"; var moveNum = 0; var totalMoves = 0; var result = ""

        fun update(l: String, m: Int, t: Int, r: String) {
            label = l; moveNum = m; totalMoves = t; result = r; invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = 60f * dp; val bh = 32f * dp; val by = (h - bh) / 2f
            backRect.set(8f * dp, by, 8f * dp + bw, by + bh)
            speedRect.set(w - 8f * dp - bw, by, w - 8f * dp, by + bh)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                if (backRect.contains(e.x, e.y))  { finish(); return true }
                if (speedRect.contains(e.x, e.y)) { cycleSpeed(); return true }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, h - dp, w, h, divP)
            val rr = 6f * dp

            canvas.drawRoundRect(backRect, rr, rr, btnBgP)
            canvas.drawText("← Back", backRect.centerX(), backRect.centerY() + btnP.textSize * 0.36f, btnP)

            canvas.drawRoundRect(speedRect, rr, rr, btnBgP)
            canvas.drawText(speedLabel[speedIdx], speedRect.centerX(), speedRect.centerY() + btnP.textSize * 0.36f, btnP)

            val cx = w / 2f
            canvas.drawText(if (moveNum == 0) "Start" else label,
                cx, h / 2f - txtP.textSize * 0.55f, txtP)
            val sub = if (result.isNotEmpty() && moveNum == totalMoves) result
                      else "Move $moveNum / $totalMoves"
            canvas.drawText(sub, cx, h / 2f + subP.textSize * 0.80f, subP)
        }
    }

    // ─── Captured Pieces Panel ────────────────────────────────────────────────

    inner class ReplayCaptureView(ctx: Context, private val gameType: String) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity

        private val bgP   = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP  = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val lblP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666"); textAlign = Paint.Align.LEFT
        }
        private val pieceP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        private var snapshot = CaptureSnapshot(emptyList(), emptyList())

        fun update(s: CaptureSnapshot) { snapshot = s; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, 0f, w, dp, divP)

            val half  = w / 2f
            canvas.drawRect(half - dp / 2f, 6f * dp, half + dp / 2f, h - 6f * dp, divP)

            lblP.textSize  = 9f * sp.coerceAtMost(3f)
            pieceP.textSize = h * 0.34f
            val labelY  = lblP.textSize + 4f * dp
            val pieceY  = h * 0.70f
            val padStart = 8f * dp
            val sameKindGap = pieceP.textSize * 0.58f
            val newKindGap = pieceP.textSize * 0.98f

            // LEFT: White's captures, or Gote's hand in Shogi.
            canvas.drawText(if (gameType == "SHOGI") "Gote hand" else "White ⚔", padStart, labelY, lblP)
            var x = padStart + pieceP.textSize * 0.34f
            val leftPieces = snapshot.byWhite.groupBy { pieceKindKey(it) }.values.flatten()
            for ((index, piece) in leftPieces.withIndex()) {
                pieceP.color = pieceColor(piece)
                canvas.drawText(piece.symbol(), x, pieceY, pieceP)
                val sameKind = leftPieces.getOrNull(index + 1)?.let { pieceKindKey(it) == pieceKindKey(piece) } == true
                x += if (sameKind) sameKindGap else newKindGap
                if (x > half - pieceP.textSize * 0.34f) break
            }

            // RIGHT: Black's captures, or Sente's hand in Shogi.
            lblP.textAlign = Paint.Align.RIGHT
            canvas.drawText(if (gameType == "SHOGI") "Sente hand" else "Black ⚔", w - padStart, labelY, lblP)
            lblP.textAlign = Paint.Align.LEFT
            var rx = half + padStart + pieceP.textSize * 0.34f
            val rightPieces = snapshot.byBlack.groupBy { pieceKindKey(it) }.values.flatten()
            for ((index, piece) in rightPieces.withIndex()) {
                pieceP.color = pieceColor(piece)
                canvas.drawText(piece.symbol(), rx, pieceY, pieceP)
                val sameKind = rightPieces.getOrNull(index + 1)?.let { pieceKindKey(it) == pieceKindKey(piece) } == true
                rx += if (sameKind) sameKindGap else newKindGap
                if (rx > w - pieceP.textSize * 0.34f) break
            }
        }

        private fun pieceKindKey(piece: Piece): String = when (piece) {
            is ChessPiece -> "chess:${piece.type.name}:${piece.color.name}"
            is CheckersPiece -> "checkers:${piece.type.name}:${piece.color.name}"
            is ShogiPiece -> "shogi:${piece.type.name}:${piece.promoted}:${piece.color.name}"
            else -> "${piece::class.java.name}:${piece.symbol()}:${piece.color.name}"
        }

        /**
         * Render each captured piece in its own color so White pieces show cream
         * and Black pieces show grey — matching how they appear on the board.
         */
        private fun pieceColor(piece: Piece): Int = when (piece) {
            is ChessPiece    -> if (piece.color == PieceColor.WHITE) Color.parseColor("#F5F5F5")
                                else Color.parseColor("#9E9E9E")
            is CheckersPiece -> if (piece.color == PieceColor.WHITE) Color.parseColor("#E0E0E0")
                                else Color.parseColor("#757575")
            is ShogiPiece    -> if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDE7")
                                else Color.parseColor("#9E9E9E")
            else             -> Color.parseColor("#AAAAAA")
        }
    }

    // ─── Controls ────────────────────────────────────────────────────────────

    inner class ReplayControlsView(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val bgP  = Paint().apply { color = Color.parseColor("#1A1A1A") }
        private val divP = Paint().apply { color = Color.parseColor("#2A2A2A") }
        private val btnP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#252525") }
        private val actP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7FC8F8"); textAlign = Paint.Align.CENTER
            textSize = 18f * sp.coerceAtMost(3f)
        }
        private val dimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444"); textAlign = Paint.Align.CENTER
            textSize = 18f * sp.coerceAtMost(3f)
        }

        private val rects   = Array(5) { RectF() }
        private val labels  = arrayOf("⏮", "◀", "⏯", "▶", "⏭")
        private val actions : Array<() -> Unit> = arrayOf(
            { onFirst() }, { onPrev() }, { onTogglePlay() }, { onNext() }, { onLast() }
        )

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val bw = (w - 60f * dp) / 5f
            val bh = 44f * dp; val by = (h - bh) / 2f
            for (i in 0..4) rects[i].set(10f * dp + i * bw, by, 10f * dp + (i + 1) * bw - 6f * dp, by + bh)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP)
                for ((i, r) in rects.withIndex()) if (r.contains(e.x, e.y)) { actions[i](); break }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgP)
            canvas.drawRect(0f, 0f, w, dp, divP)
            val rr = 8f * dp
            for ((i, r) in rects.withIndex()) {
                canvas.drawRoundRect(r, rr, rr, btnP)
                val lbl = if (i == 2 && isPlaying) "⏸" else labels[i]
                val dimmed = (i == 0 || i == 1) && cursor == 0 ||
                             (i == 3 || i == 4) && cursor == states.lastIndex
                val p = if (dimmed) dimP else actP
                canvas.drawText(lbl, r.centerX(), r.centerY() + p.textSize * 0.36f, p)
            }
        }
    }
}
