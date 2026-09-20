package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.amazons.AmazonsPiece
import com.mkdev.mkboardgames.games.amazons.AmazonsPieceType
import com.mkdev.mkboardgames.games.amazons.AmazonsRuleEngine
import com.mkdev.mkboardgames.games.checkers.CheckersPiece
import com.mkdev.mkboardgames.games.checkers.CheckersRuleEngine
import com.mkdev.mkboardgames.games.checkers.InternationalDraughtsRuleEngine
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessPieceType
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeeseSetup
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePiece
import com.mkdev.mkboardgames.games.foxandgeese.FoxAndGeesePieceType
import com.mkdev.mkboardgames.games.go.GoPiece
import com.mkdev.mkboardgames.games.go.GoRuleEngine
import com.mkdev.mkboardgames.games.othello.OthelloPiece
import com.mkdev.mkboardgames.games.othello.OthelloRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiPiece
import com.mkdev.mkboardgames.games.shogi.ShogiRuleEngine
import com.mkdev.mkboardgames.games.shogi.ShogiSetup
import com.mkdev.mkboardgames.games.shogi.ShogiPieceType
import com.mkdev.mkboardgames.games.xiangqi.XiangqiPiece
import com.mkdev.mkboardgames.games.xiangqi.XiangqiRuleEngine

class BoardView(context: Context) : View(context) {

    // ─── External state ───────────────────────────────────────────────────────
    var gameState: GameState = GameState(arrayOfNulls(64))
        set(value) {
            field = value
            updateBoardGeometry()
            selectedPos = null
            amazonsPendingMoves = null
            legalMoves  = if (shogiDropPiece != null && ruleEngine is ShogiRuleEngine) {
                (ruleEngine as ShogiRuleEngine).legalDropsFrom(value, shogiDropPiece!!)
            } else {
                emptyList()
            }
            mustCapturePieces = if (showMustCaptureHints && ruleEngine != null)
                computeMustCapturePieces(value) else emptySet()
            if (directMoveMode && ruleEngine != null && !isLocked)
                legalMoves = ruleEngine!!.allLegalMoves(value, value.currentTurn)
            invalidate()
        }
    var ruleEngine: RuleEngine? = null
    var onMoveMade: ((Move) -> Unit)? = null
    var onGameOverTapped: (() -> Unit)? = null
    var isFlipped: Boolean = false
    var isLocked: Boolean = false
    var showMustCaptureHints: Boolean = false
    var rotateBlackPieces: Boolean = false
    var directMoveMode: Boolean = false
    var onEmptySpaceTapped: (() -> Unit)? = null
    var chessBoardStyle: ChessBoardStyle = ChessBoardStyle.CANVAS
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var draughtsBoardStyle: DraughtsBoardStyle = DraughtsBoardStyle.CANVAS
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var othelloBoardStyle: OthelloBoardStyle = OthelloBoardStyle.CANVAS
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var foxAndGeeseBoardStyle: FoxAndGeeseBoardStyle = FoxAndGeeseBoardStyle.CANVAS
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var xiangqiBoardStyle: XiangqiBoardStyle = XiangqiBoardStyle.CLASSIC
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var shogiBoardStyle: ShogiBoardStyle = ShogiBoardStyle.CLASSIC
        set(value) {
            if (field == value) return
            field = value
            refreshBoardStyleGeometry()
            invalidate()
        }
    var onPromotionChoice: ((List<Move>) -> Unit)? = null

    // ─── Selection ───────────────────────────────────────────────────────────
    private var selectedPos: Position? = null
    private var legalMoves: List<Move> = emptyList()
    private var amazonsPendingMoves: List<Move>? = null
    private var shogiDropPiece: ShogiPieceType? = null
    private var hintMove: Move? = null
    private var hintStage: Int = 3

    /**
     * Hint stages: 1 highlights the piece, 2 highlights the destination, and
     * 3 shows the complete move. A null move clears the hint.
     */
    fun setHintMove(move: Move?, stage: Int = 3) {
        hintMove = move
        hintStage = stage.coerceIn(1, 3)
        invalidate()
    }

    fun beginShogiDrop(type: ShogiPieceType) {
        val engine = ruleEngine as? ShogiRuleEngine ?: return
        if (isLocked || gameState.status != GameStatus.IN_PROGRESS) return
        shogiDropPiece = type
        selectedPos = null
        legalMoves = engine.legalDropsFrom(gameState, type)
        invalidate()
    }

    fun cancelShogiDrop() {
        shogiDropPiece = null
        legalMoves = emptyList()
        invalidate()
    }

    // ─── Must-capture highlights ──────────────────────────────────────────────
    private var mustCapturePieces: Set<Position> = emptySet()

    private fun computeMustCapturePieces(state: GameState): Set<Position> {
        val engine = ruleEngine ?: return emptySet()
        if (state.status != GameStatus.IN_PROGRESS) return emptySet()
        val result = mutableSetOf<Position>()
        for (row in 0 until state.boardSize) for (col in 0 until state.boardSize) {
            val pos = Position(row, col)
            if (state.get(pos)?.color != state.currentTurn) continue
            if (engine.legalMovesFrom(state, pos).any { it.isCapture }) result.add(pos)
        }
        return result
    }

    // ─── Animation ───────────────────────────────────────────────────────────
    private var animPiece: Piece? = null
    private var animFromPx  = PointF()
    private var animToPx    = PointF()
    private var animFromPos: Position? = null
    private var animToPos: Position? = null
    private var animProgress: Float = 0f
    private var animator: ValueAnimator? = null
    private var pendingMove: Move? = null

    // Othello disc pop/flip animation
    private var recentOthelloPieces: Set<Position> = emptySet()
    private var othelloPopProgress: Float = 1f
    private var popAnimator: ValueAnimator? = null

    // ─── Board geometry ───────────────────────────────────────────────────────
    private var cellSize  = 0f
    private var boardLeft = 0f
    private var boardTop  = 0f
    private var shogiImageRect = RectF()
    private var shogiGridLeft = 0f
    private var shogiGridRight = 0f
    private var shogiGridTop = 0f
    private var shogiGridBottom = 0f
    private var shogiCellWidth = 0f
    private var shogiCellHeight = 0f
    // The supplied board image is photographed with slight perspective, so
    // its nine columns and rows are not perfectly uniform. Keep the measured
    // grid lines in image-relative coordinates so pieces sit in the visual
    // centre of each box instead of drifting across the board.
    private val classicShogiGridX = floatArrayOf(
        35f / 1190f, 161f / 1190f, 290f / 1190f, 414f / 1190f, 540f / 1190f,
        668f / 1190f, 793f / 1190f, 917f / 1190f, 1041f / 1190f, 1162f / 1190f,
    )
    private val classicShogiGridY = floatArrayOf(
        35f / 1322f, 178f / 1322f, 317f / 1322f, 456f / 1322f, 596f / 1322f,
        735f / 1322f, 876f / 1322f, 1015f / 1322f, 1154f / 1322f, 1290f / 1322f,
    )
    private val woodShogiGridX = floatArrayOf(
        35f / 1191f, 161f / 1191f, 290f / 1191f, 414f / 1191f, 540f / 1191f,
        668f / 1191f, 793f / 1191f, 917f / 1191f, 1041f / 1191f, 1162f / 1191f,
    )
    private val woodShogiGridY = floatArrayOf(
        35f / 1321f, 178f / 1321f, 317f / 1321f, 456f / 1321f, 596f / 1321f,
        735f / 1321f, 876f / 1321f, 1015f / 1321f, 1154f / 1321f, 1290f / 1321f,
    )
    private var xiangqiImageRect = RectF()
    private var xiangqiGridLeft = 0f
    private var xiangqiGridRight = 0f
    private var xiangqiGridTop = 0f
    private var xiangqiGridBottom = 0f
    private var xiangqiCellWidth = 0f
    private var xiangqiCellHeight = 0f
    private var goImageRect = RectF()
    private val goGridX = floatArrayOf(
        0.084f, 0.154f, 0.224f, 0.294f, 0.364f, 0.434f, 0.503f,
        0.572f, 0.641f, 0.711f, 0.781f, 0.851f, 0.919f,
    )
    private val goGridY = floatArrayOf(
        0.079f, 0.145f, 0.211f, 0.275f, 0.341f, 0.406f, 0.472f,
        0.537f, 0.603f, 0.667f, 0.733f, 0.798f, 0.866f,
    )
    private var othelloImageRect = RectF()
    private var othelloCellWidth = 0f
    private var othelloCellHeight = 0f
    private var foxAndGeeseImageRect = RectF()
    // Measured centres of the 7x7 lattice in each supplied board image. The
    // transparent cross is not a square grid, so the point centres are used
    // directly by drawing, animation, and touch conversion.
    private val foxAndGeeseLightWoodGridX = floatArrayOf(
        76f / 1024f, 221f / 1024f, 367f / 1024f, 512f / 1024f,
        657f / 1024f, 801f / 1024f, 947f / 1024f,
    )
    private val foxAndGeeseLightWoodGridY = floatArrayOf(
        72f / 985f, 206f / 985f, 344f / 985f, 486f / 985f,
        628f / 985f, 771f / 985f, 914f / 985f,
    )
    private val foxAndGeeseCrossWoodGridX = floatArrayOf(
        76f / 1024f, 221f / 1024f, 367f / 1024f, 512f / 1024f,
        657f / 1024f, 801f / 1024f, 947f / 1024f,
    )
    private val foxAndGeeseCrossWoodGridY = floatArrayOf(
        69f / 949f, 199f / 949f, 332f / 949f, 468f / 949f,
        604f / 949f, 743f / 949f, 880f / 949f,
    )
    // Measured playable bounds of the supplied 1272x1236 green-felt board.
    // Keep each boundary so the slight perspective in the photograph is
    // shared by discs, highlights, animations, and touch conversion.
    private val othelloGridX = floatArrayOf(
        66f / 1272f, 207f / 1272f, 350f / 1272f, 492f / 1272f,
        638f / 1272f, 780f / 1272f, 925f / 1272f, 1070f / 1272f,
        1205f / 1272f,
    )
    private val othelloGridY = floatArrayOf(
        55f / 1236f, 200f / 1236f, 342f / 1236f, 485f / 1236f,
        626f / 1236f, 770f / 1236f, 912f / 1236f, 1050f / 1236f,
        1183f / 1236f,
    )
    private var chessImageRect = RectF()
    private var chessCellWidth = 0f
    private var chessCellHeight = 0f
    private var draughtsImageRect = RectF()
    private var draughtsCellWidth = 0f
    private var draughtsCellHeight = 0f
    // The supplied chess board includes a wooden frame and a slight camera
    // perspective. These are the measured boundaries of its playable 8x8 area
    // in the 1024px asset. Keep every boundary instead of deriving cells from
    // one average size: the draw, highlight, animation, and touch paths all
    // use these same lines.
    private val suppliedChessGridX = floatArrayOf(
        42f / 1024f, 163f / 1024f, 278f / 1024f, 395f / 1024f,
        511f / 1024f, 628f / 1024f, 744f / 1024f, 860f / 1024f,
        983f / 1024f,
    )
    private val suppliedChessGridY = floatArrayOf(
        33f / 1024f, 151f / 1024f, 265f / 1024f, 381f / 1024f,
        498f / 1024f, 613f / 1024f, 729f / 1024f, 845f / 1024f,
        975f / 1024f,
    )
    // The original framed board is a 1024px image with a slightly different
    // inner border. Keeping its geometry separate prevents pieces and taps
    // from drifting when switching between the two photographs.
    private val classicChessGridX = floatArrayOf(
        48f / 1024f, 166f / 1024f, 284f / 1024f, 401f / 1024f,
        512f / 1024f, 630f / 1024f, 748f / 1024f, 864f / 1024f,
        978f / 1024f,
    )
    private val classicChessGridY = floatArrayOf(
        45f / 1024f, 158f / 1024f, 272f / 1024f, 385f / 1024f,
        500f / 1024f, 615f / 1024f, 730f / 1024f, 845f / 1024f,
        959f / 1024f,
    )
    private val realisticChessGridX = floatArrayOf(
        48f / 1024f, 164f / 1024f, 280f / 1024f, 396f / 1024f,
        512f / 1024f, 628f / 1024f, 744f / 1024f, 860f / 1024f,
        976f / 1024f,
    )
    private val realisticChessGridY = realisticChessGridX.copyOf()
    // The black-and-white supplied image has an inset frame around an
    // otherwise regular 8x8 board. These lines are measured from the 1024px
    // optimized asset after resizing the uploaded 1254px source.
    private val blackWhiteChessGridX = floatArrayOf(
        25f / 1024f, 148f / 1024f, 269f / 1024f, 390f / 1024f,
        511f / 1024f, 632f / 1024f, 753f / 1024f, 876f / 1024f,
        1004f / 1024f,
    )
    private val blackWhiteChessGridY = floatArrayOf(
        25f / 1024f, 145f / 1024f, 266f / 1024f, 387f / 1024f,
        510f / 1024f, 632f / 1024f, 755f / 1024f, 876f / 1024f,
        1004f / 1024f,
    )
    // The supplied red-and-black Draughts image uses an inset 8x8 grid. Keep
    // its geometry separate from the Chess photographs so switching games
    // never reuses the wrong playable bounds.
    private val redBlackDraughtsGridX = floatArrayOf(
        60f / 1024f, 173f / 1024f, 286f / 1024f, 399f / 1024f,
        512f / 1024f, 625f / 1024f, 738f / 1024f, 851f / 1024f,
        964f / 1024f,
    )
    private val redBlackDraughtsGridY = floatArrayOf(
        60f / 1024f, 173f / 1024f, 286f / 1024f, 399f / 1024f,
        512f / 1024f, 625f / 1024f, 738f / 1024f, 851f / 1024f,
        964f / 1024f,
    )
    // The two uploaded International Draughts boards are optimized to 1024px.
    // Both photographs contain a 10x10 playable grid inset inside the wooden
    // frame. Keep each board's measured boundaries separate.
    private val internationalDarkDraughtsGridX = floatArrayOf(
        64f / 1024f, 153f / 1024f, 243f / 1024f, 333f / 1024f,
        423f / 1024f, 512f / 1024f, 601f / 1024f, 691f / 1024f,
        781f / 1024f, 870f / 1024f, 960f / 1024f,
    )
    private val internationalDarkDraughtsGridY = floatArrayOf(
        60f / 1024f, 151f / 1024f, 241f / 1024f, 331f / 1024f,
        421f / 1024f, 510f / 1024f, 599f / 1024f, 688f / 1024f,
        777f / 1024f, 867f / 1024f, 956f / 1024f,
    )
    private val internationalLightDraughtsGridX = floatArrayOf(
        57f / 1024f, 145f / 1024f, 236f / 1024f, 328f / 1024f,
        419f / 1024f, 510f / 1024f, 602f / 1024f, 694f / 1024f,
        785f / 1024f, 877f / 1024f, 969f / 1024f,
    )
    private val internationalLightDraughtsGridY = floatArrayOf(
        57f / 1024f, 145f / 1024f, 235f / 1024f, 326f / 1024f,
        417f / 1024f, 508f / 1024f, 599f / 1024f, 690f / 1024f,
        780f / 1024f, 871f / 1024f, 961f / 1024f,
    )

    private val xiangqiBoardBitmap: Bitmap? = try {
        context.assets.open("xiangqi_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val xiangqiChineseBoardBitmap: Bitmap? = try {
        context.assets.open("xiangqi_board_chinese.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val xiangqiEnglishBoardBitmap: Bitmap? = try {
        context.assets.open("xiangqi_board_english.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val classicShogiBoardBitmap: Bitmap? = try {
        context.assets.open("shogi_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val woodShogiBoardBitmap: Bitmap? = try {
        context.assets.open("shogi_board_wood.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val goBoardBitmap: Bitmap? = try {
        context.assets.open("go_board.webp").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val classicChessBoardBitmap: Bitmap? = try {
        context.assets.open("chess_board.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val suppliedChessBoardBitmap: Bitmap? = try {
        context.assets.open("chess_board_wood.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val realisticChessBoardBitmap: Bitmap? = createRealisticChessBoardBitmap()
    private val blackWhiteChessBoardBitmap: Bitmap? = try {
        context.assets.open("chess_board_black_white.png").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val draughtsBoardBitmap: Bitmap? = try {
        context.assets.open("draughts_board_red_black.png").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val internationalDarkDraughtsBoardBitmap: Bitmap? = try {
        context.assets.open("international_draughts_board_dark.jpg")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val internationalLightDraughtsBoardBitmap: Bitmap? = try {
        context.assets.open("international_draughts_board_light.jpg")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val othelloBoardBitmap: Bitmap? = try {
        context.assets.open("othello_board_green.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val foxAndGeeseLightWoodBoardBitmap: Bitmap? = try {
        context.assets.open("fox_and_geese_board_light.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val foxAndGeeseCrossWoodBoardBitmap: Bitmap? = try {
        context.assets.open("fox_and_geese_board_cross.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val foxAndGeeseFoxPieceBitmap: Bitmap? = try {
        context.assets.open("fox_and_geese_fox_piece.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val foxAndGeeseGoosePieceBitmap: Bitmap? = try {
        context.assets.open("fox_and_geese_goose_piece.webp")
            .use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
    private val amazonsBlackAmazonBitmap = loadAssetBitmap("amazons_black_amazon.webp")
    private val amazonsWhiteAmazonBitmap = loadAssetBitmap("amazons_white_amazon.webp")
    private val amazonsBlackArrowBitmap = loadAssetBitmap("amazons_black_arrow.webp")
    private val amazonsWhiteArrowBitmap = loadAssetBitmap("amazons_white_arrow.webp")

    private fun loadAssetBitmap(name: String): Bitmap? = try {
        context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }

    private fun createRealisticChessBoardBitmap(): Bitmap {
        val size = 1024
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#171B20")
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)

        // Keep the outer edge close to the bitmap bounds so this presentation
        // has the same visible footprint as the first (canvas) board.
        val boardRect = RectF(14f, 14f, size - 14f, size - 14f)
        val boardRadius = 42f
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2F3540")
        }
        canvas.drawRoundRect(boardRect, boardRadius, boardRadius, framePaint)

        val boardInset = 48f
        val squareSize = (size - boardInset * 2f) / 8f
        val gridRect = RectF(boardInset, boardInset, size - boardInset, size - boardInset)
        val gridPath = Path().apply {
            addRoundRect(gridRect, 18f, 18f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(gridPath)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val isLight = (row + col) % 2 == 0
                val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLight) Color.parseColor("#EAE5DA") else Color.parseColor("#30363F")
                }
                val left = boardInset + col * squareSize
                val top = boardInset + row * squareSize
                canvas.drawRect(left, top, left + squareSize, top + squareSize, squarePaint)
            }
        }
        canvas.restore()

        val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val bevelRect = RectF(boardRect).apply { inset(5f, 5f) }
        canvas.drawRoundRect(bevelRect, boardRadius - 5f, boardRadius - 5f, bevelPaint)

        return bitmap
    }

    // ─── Paints ───────────────────────────────────────────────────────────────
    private var lightPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private var darkPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90,255,215,0) }
    private val hintGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(165, 255, 193, 62)
        style = Paint.Style.FILL
    }
    private val highlightBlue = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val mustCapturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8F00"); style = Paint.Style.STROKE
    }
    private val shogiSelectionEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val foxAndGeesePiecePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
    )
    private val amazonsPiecePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
    )
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ─── Theme ───────────────────────────────────────────────────────────────
    private fun applyTheme() {
        val t = SettingsManager.currentTheme(context)
        lightPaint.color  = t.light
        darkPaint.color   = t.dark
        accentPaint.color = t.accent
        highlightBlue.color = Color.argb(160, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        shogiSelectionEdgePaint.color =
            Color.argb(225, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        dotPaint.color  = Color.argb(130, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))
        ringPaint.color = Color.parseColor("#EF5350")
    }

    init { applyTheme() }
    fun refreshTheme() { applyTheme(); invalidate() }

    // ─── Size ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        updateBoardGeometry()
    }

    private fun updateBoardGeometry() {
        if (width <= 0 || height <= 0) return
        if (isFoxAndGeeseImageBoard()) {
            val bitmap = foxAndGeeseBitmap()
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                foxAndGeeseImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                val gridX = foxAndGeeseGridX()
                val gridY = foxAndGeeseGridY()
                val averageX = (gridX.last() - gridX.first()) * imageWidth / 6f
                val averageY = (gridY.last() - gridY.first()) * imageHeight / 6f
                cellSize = minOf(averageX, averageY)
                piecePaint.textSize = cellSize * 0.60f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isShogiBoard()) {
            val bitmap = shogiBitmap()
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                shogiImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                // The uploaded reference includes a narrow wooden frame around
                // the playable 9×9 grid. Use the measured outer lines for the
                // legacy bounds and the per-line arrays for each box.
                shogiGridLeft = shogiLineX(0)
                shogiGridRight = shogiLineX(9)
                shogiGridTop = shogiLineY(0)
                shogiGridBottom = shogiLineY(9)
                shogiCellWidth = (shogiGridRight - shogiGridLeft) / 9f
                shogiCellHeight = (shogiGridBottom - shogiGridTop) / 9f
                cellSize = minOf(shogiCellWidth, shogiCellHeight)
                piecePaint.textSize = cellSize * 0.58f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
                shogiSelectionEdgePaint.strokeWidth = cellSize * 0.035f
            }
            return
        }
        if (isXiangqiBoard()) {
            val bitmap = xiangqiBitmap()
            if (bitmap != null) {
                // The supplied 2:3 boards are taller than the playable area.
                // Scale them to the view width and let the view crop the
                // image's excess top/bottom margin rather than leaving side
                // letterboxing around the board.
                val scale = if (xiangqiBoardStyle == XiangqiBoardStyle.CLASSIC) {
                    minOf(
                        width.toFloat() / bitmap.width,
                        height.toFloat() / bitmap.height,
                    )
                } else {
                    width.toFloat() / bitmap.width
                }
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                xiangqiImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                val gridX = xiangqiGridX()
                val gridY = xiangqiGridY()
                xiangqiGridLeft = xiangqiImageRect.left + imageWidth * gridX.first()
                xiangqiGridRight = xiangqiImageRect.left + imageWidth * gridX.last()
                xiangqiGridTop = xiangqiImageRect.top + imageHeight * gridY.first()
                xiangqiGridBottom = xiangqiImageRect.top + imageHeight * gridY.last()
                xiangqiCellWidth = (xiangqiGridRight - xiangqiGridLeft) / 8f
                xiangqiCellHeight = (xiangqiGridBottom - xiangqiGridTop) / 9f
                cellSize = minOf(xiangqiCellWidth, xiangqiCellHeight)
                piecePaint.textSize = cellSize * 0.72f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isGoBoard()) {
            val bitmap = goBoardBitmap
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                goImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                val gridSize = imageWidth * 0.832f
                cellSize = gridSize / 12f
                piecePaint.textSize = cellSize * 0.60f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isOthelloImageBoard()) {
            val bitmap = othelloBoardBitmap
            if (bitmap != null) {
                val scale = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                othelloImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                othelloCellWidth = (othelloLineX(8) - othelloLineX(0)) / 8f
                othelloCellHeight = (othelloLineY(8) - othelloLineY(0)) / 8f
                cellSize = minOf(othelloCellWidth, othelloCellHeight)
                piecePaint.textSize = cellSize * 0.60f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isChessImageBoard()) {
            val bitmap = chessBitmap()
            if (bitmap != null) {
                // The first canvas board leaves a 4dp edge on the smaller
                // dimension. Give the realistic board the same outer bounds;
                // the other photo boards retain their original full-bleed fit.
                val canvasBoardMargin = if (chessBoardStyle == ChessBoardStyle.REALISTIC_BLACK_WHITE) {
                    4f * resources.displayMetrics.density
                } else {
                    0f
                }
                val availableWidth = (width.toFloat() - canvasBoardMargin * 2f).coerceAtLeast(0f)
                val availableHeight = (height.toFloat() - canvasBoardMargin * 2f).coerceAtLeast(0f)
                val scale = minOf(
                    availableWidth / bitmap.width,
                    availableHeight / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                chessImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                chessCellWidth = (chessLineX(8) - chessLineX(0)) / 8f
                chessCellHeight = (chessLineY(8) - chessLineY(0)) / 8f
                cellSize = minOf(chessCellWidth, chessCellHeight)
                piecePaint.textSize = cellSize * 0.60f
                labelPaint.textSize = cellSize * 0.18f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        if (isDraughtsImageBoard()) {
            val bitmap = draughtsBitmap()
            if (bitmap != null) {
                val boardMargin = if (draughtsBoardStyle == DraughtsBoardStyle.REALISTIC_BLACK_WHITE) {
                    4f * resources.displayMetrics.density
                } else {
                    0f
                }
                val availableWidth = (width.toFloat() - boardMargin * 2f).coerceAtLeast(0f)
                val availableHeight = (height.toFloat() - boardMargin * 2f).coerceAtLeast(0f)
                val scale = minOf(
                    availableWidth / bitmap.width,
                    availableHeight / bitmap.height,
                )
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                draughtsImageRect.set(
                    (width - imageWidth) / 2f,
                    (height - imageHeight) / 2f,
                    (width + imageWidth) / 2f,
                    (height + imageHeight) / 2f,
                )
                val boardDimension = gameState.boardSize
                draughtsCellWidth =
                    (draughtsLineX(boardDimension) - draughtsLineX(0)) / boardDimension
                draughtsCellHeight =
                    (draughtsLineY(boardDimension) - draughtsLineY(0)) / boardDimension
                cellSize = minOf(draughtsCellWidth, draughtsCellHeight)
                piecePaint.textSize = cellSize * 0.60f
                mustCapturePaint.strokeWidth = cellSize * 0.055f
            }
            return
        }
        val dp      = resources.displayMetrics.density
        val margin  = 4f * dp
        val boardSz = minOf(width.toFloat() - margin * 2, height.toFloat() - margin * 2)
        cellSize  = boardSz / gameState.boardSize.toFloat()
        boardLeft = (width - boardSz) / 2f
        boardTop  = (height - boardSz) / 2f
        if (isChessBoard() || isAmazons8Board()) {
            chessCellWidth = cellSize
            chessCellHeight = cellSize
        }
        piecePaint.textSize       = cellSize * 0.60f
        labelPaint.textSize       = cellSize * 0.22f
        labelPaint.color          = Color.argb(130, 120, 80, 40)
        mustCapturePaint.strokeWidth = cellSize * 0.055f
    }

    private fun refreshBoardStyleGeometry() {
        updateBoardGeometry()
        // Keep an in-flight move aligned when the board style changes.
        animFromPos?.let { animFromPx = cellCenter(it) }
        animToPos?.let { animToPx = cellCenter(it) }
    }

    // ─── Touch ───────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Game over: re-show result dialog on any tap
            if (gameState.status != GameStatus.IN_PROGRESS) {
                onGameOverTapped?.invoke()
                return true
            }
            val position = screenToBoard(event.x, event.y)
            if (position == null) {
                onEmptySpaceTapped?.invoke()
                return true
            }
            if (!isLocked && animPiece == null) handleTap(position)
        }
        return true
    }

    private fun handleTap(pos: Position) {
        if (isAmazonsBoard()) {
            handleAmazonsTap(pos)
            return
        }
        if (isGoBoard()) {
            val engine = ruleEngine ?: return
            val move = engine.allLegalMoves(gameState, gameState.currentTurn)
                .firstOrNull { it.to == pos && it.metadata["pass"] != true }
            if (move != null) startMoveAnimation(move)
            return
        }
        if (directMoveMode) {
            val engine = ruleEngine ?: return
            if (legalMoves.isEmpty())
                legalMoves = engine.allLegalMoves(gameState, gameState.currentTurn)
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) startMoveAnimation(move)
            return
        }

        val engine = ruleEngine ?: return

        if (shogiDropPiece != null) {
            val move = legalMoves.firstOrNull { it.to == pos }
            if (move != null) {
                startMoveAnimation(move)
            } else {
                cancelShogiDrop()
            }
            return
        }

        if (selectedPos != null) {
            val choices = legalMoves.filter { it.to == pos }
            if (choices.size == 1) { startMoveAnimation(choices.first()); return }
            if (choices.size > 1) {
                // Flying kings and multi-capture routes can legitimately
                // produce more than one legal route to the same landing
                // square. That is not a promotion choice, and showing the
                // promotion UI here makes a valid capture appear untappable.
                val promotionTypes = choices.mapNotNull { it.promotionType }.distinct()
                if (promotionTypes.size > 1) {
                    selectedPos = null
                    legalMoves = emptyList()
                    onPromotionChoice?.invoke(choices)
                    invalidate()
                } else {
                    startMoveAnimation(choices.first())
                }
                return
            }

            val piece = gameState.get(pos)
            if (piece != null && piece.color == gameState.currentTurn) {
                val moves = engine.legalMovesFrom(gameState, pos)
                if (moves.isNotEmpty()) { selectedPos = pos; legalMoves = moves; invalidate(); return }
            }
            selectedPos = null; legalMoves = emptyList(); invalidate(); return
        }

        val piece = gameState.get(pos) ?: run { invalidate(); return }
        if (piece.color != gameState.currentTurn) { invalidate(); return }
        val moves = engine.legalMovesFrom(gameState, pos)
        if (moves.isNotEmpty()) { selectedPos = pos; legalMoves = moves }
        invalidate()
    }

    private fun handleAmazonsTap(pos: Position) {
        val engine = ruleEngine as? AmazonsRuleEngine ?: return
        amazonsPendingMoves?.let { pending ->
            val move = pending.firstOrNull {
                (it.metadata[AmazonsRuleEngine.ARROW_METADATA] as? Position) == pos
            }
            if (move != null) {
                amazonsPendingMoves = null
                startMoveAnimation(move)
                return
            }
            amazonsPendingMoves = null
            selectedPos = null
            legalMoves = emptyList()
        }

        val piece = gameState.get(pos)
        if (piece is AmazonsPiece &&
            piece.type == AmazonsPieceType.AMAZON &&
            piece.color == gameState.currentTurn
        ) {
            val moves = engine.legalMovesFrom(gameState, pos)
            selectedPos = pos
            legalMoves = moves
            invalidate()
            return
        }

        val choices = legalMoves.filter { it.to == pos }
        if (choices.isNotEmpty()) {
            if (choices.size == 1) {
                startMoveAnimation(choices.first())
            } else {
                amazonsPendingMoves = choices
                selectedPos = pos
                legalMoves = choices
                invalidate()
            }
            return
        }

        selectedPos = null
        legalMoves = emptyList()
        invalidate()
    }

    // ─── Animation ───────────────────────────────────────────────────────────

    fun animateExternalMove(move: Move) = startMoveAnimation(move)

    /**
     * Cancel an in-flight move animation without applying its callback.
     *
     * Replay controls can jump to another state while the previous animation
     * is still running. Clearing the callback before cancelling prevents the
     * old move from overwriting the newly selected replay state.
     */
    fun cancelMoveAnimation() {
        val activeAnimator = animator
        animator = null
        pendingMove = null
        activeAnimator?.cancel()
        animPiece = null
        animFromPos = null
        animToPos = null
        animProgress = 0f
        isLocked = false
        invalidate()
    }

    /** Pop-in animation for Othello: placed disc + all flipped discs grow in with overshoot. */
    fun playOthelloPopAnim(positions: Set<Position>) {
        recentOthelloPieces = positions
        othelloPopProgress  = 0f
        popAnimator?.cancel()
        popAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L; interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { othelloPopProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    recentOthelloPieces = emptySet()
                    othelloPopProgress  = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun startMoveAnimation(move: Move) {
        cancelMoveAnimation()
        if (isGoBoard()) {
            selectedPos = null
            legalMoves = emptyList()
            isLocked = true
            postDelayed({
                isLocked = false
                invalidate()
                onMoveMade?.invoke(move)
            }, 140L)
            return
        }
        if (move.metadata["drop"] != null) {
            selectedPos = null
            legalMoves = emptyList()
            shogiDropPiece = null
            isLocked = true
            // Drops originate in the hand rather than from a board square, so
            // use a short placement pause instead of a from-to animation.
            postDelayed({
                isLocked = false
                invalidate()
                onMoveMade?.invoke(move)
            }, 160L)
            return
        }
        val piece = gameState.get(move.from) ?: run { isLocked = false; onMoveMade?.invoke(move); return }

        selectedPos = null; legalMoves = emptyList()
        animPiece   = piece; animFromPos = move.from; animToPos = move.to
        animFromPx  = cellCenter(move.from); animToPx = cellCenter(move.to)
        pendingMove = move; isLocked = true

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animation !== animator) return
                    animator = null
                    animPiece = null; animFromPos = null; animToPos = null; animProgress = 0f
                    isLocked  = false; invalidate()
                    val completedMove = pendingMove
                    pendingMove = null
                    completedMove?.let { onMoveMade?.invoke(it) }
                }
            })
            start()
        }
    }

    private fun cellCenter(pos: Position): PointF {
        if (isShogiBoard()) return shogiPoint(pos)
        if (isXiangqiBoard()) return xiangqiPoint(pos)
        if (isGoBoard()) return goPoint(pos)
        if (isOthelloImageBoard()) return othelloPoint(pos)
        if (isFoxAndGeeseImageBoard()) return foxAndGeesePoint(pos)
        if (isAmazons10Board()) return draughtsPoint(pos)
        if (isAmazons8Board()) return chessPoint(pos)
        if (isChessBoard()) return chessPoint(pos)
        if (isDraughtsImageBoard()) return draughtsPoint(pos)
        val last = gameState.boardSize - 1
        val dr = if (isFlipped) last - pos.row else pos.row
        val dc = if (isFlipped) last - pos.col else pos.col
        return PointF(boardLeft + dc * cellSize + cellSize / 2f,
                      boardTop  + dr * cellSize + cellSize / 2f)
    }

    // ─── Drawing ─────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (isAmazonsBoard()) {
            if (isAmazons8Board()) {
                if (isChessImageBoard()) drawChessBoard(canvas) else drawBoard(canvas)
                drawAmazonsHighlights(canvas)
                drawChessLabels(canvas)
            } else {
                drawDraughtsBoard(canvas)
                drawAmazonsHighlights(canvas)
            }
            drawAmazonsPieces(canvas)
            return
        }
        if (isFoxAndGeeseImageBoard()) {
            drawFoxAndGeeseImageBoard(canvas)
            drawFoxAndGeeseHighlights(canvas)
            drawFoxAndGeesePieces(canvas)
            return
        }
        if (isChessBoard()) {
            if (isChessImageBoard()) drawChessBoard(canvas) else drawBoard(canvas)
            drawChessHighlights(canvas)
            drawChessLabels(canvas)
            drawChessPieces(canvas)
            return
        }
        if (isDraughtsImageBoard()) {
            drawDraughtsBoard(canvas)
            drawDraughtsHighlights(canvas)
            drawDraughtsPieces(canvas)
            return
        }
        if (isShogiBoard()) {
            drawShogiBoard(canvas)
            drawShogiHighlights(canvas)
            drawShogiPieces(canvas)
            return
        }
        if (isXiangqiBoard()) {
            drawXiangqiBoard(canvas)
            drawXiangqiHighlights(canvas)
            drawXiangqiPieces(canvas)
            return
        }
        if (isGoBoard()) {
            canvas.drawColor(Color.rgb(20, 20, 20))
            goBoardBitmap?.let {
                canvas.drawBitmap(it, null, goImageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            drawGoHighlights(canvas)
            drawGoPieces(canvas)
            return
        }
        if (isOthelloImageBoard()) {
            drawOthelloBoard(canvas)
            drawOthelloHighlights(canvas)
            drawOthelloPieces(canvas)
            return
        }
        drawBoard(canvas); drawLabels(canvas); drawHighlights(canvas); drawPieces(canvas)
    }

    private fun drawChessBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        chessBitmap()?.let {
            canvas.drawBitmap(
                it,
                null,
                chessImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawChessLabels(canvas: Canvas) {
        val inset = cellSize * 0.105f
        labelPaint.textSize = (cellSize * 0.17f).coerceIn(10f, 24f)
        labelPaint.style = Paint.Style.FILL

        for (displayedRow in 0 until 8) {
            for (displayedCol in 0 until 8) {
                val left = chessLineX(displayedCol)
                val top = chessLineY(displayedRow)
                val right = chessLineX(displayedCol + 1)
                val bottom = chessLineY(displayedRow + 1)
                val file = ('a' + if (isFlipped) 7 - displayedCol else displayedCol).toString()
                val rank = (if (isFlipped) displayedRow + 1 else 8 - displayedRow).toString()

                // Keep labels in opposite corners from the piece centre and
                // change their colour per square for legibility on both photos.
                val lightSquare = (displayedRow + displayedCol) % 2 == 0
                labelPaint.color = if (lightSquare) {
                    Color.argb(205, 45, 28, 18)
                } else {
                    Color.argb(220, 255, 244, 220)
                }
                labelPaint.setShadowLayer(
                    cellSize * 0.018f,
                    0f,
                    cellSize * 0.012f,
                    Color.argb(145, 0, 0, 0),
                )
                val metrics = labelPaint.fontMetrics
                if (displayedRow == 7) {
                    canvas.drawText(
                        file,
                        right - inset,
                        bottom - inset - metrics.descent,
                        labelPaint,
                    )
                }
                if (displayedCol == 0) {
                    canvas.drawText(
                        rank,
                        left + inset,
                        top + inset - metrics.ascent,
                        labelPaint,
                    )
                }
            }
        }
        labelPaint.clearShadowLayer()
    }

    private fun drawChessHighlights(canvas: Canvas) {
        gameState.lastMove?.let {
            drawChessCell(canvas, it.from, highlightGold)
            drawChessCell(canvas, it.to, highlightGold)
        }
        hintMove?.let {
            if (hintStage == 1 || hintStage == 3) drawChessCell(canvas, it.from, hintGold)
            if (hintStage == 2 || hintStage == 3) drawChessCell(canvas, it.to, hintGold)
        }
        selectedPos?.let { drawChessCell(canvas, it, highlightBlue) }

        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = chessPoint(move.to)
                if (!directMoveMode && move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.44f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
                }
            }
        }
    }

    private fun drawChessCell(canvas: Canvas, position: Position, paint: Paint) {
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        canvas.drawRect(
            chessLineX(displayedCol),
            chessLineY(displayedRow),
            chessLineX(displayedCol + 1),
            chessLineY(displayedRow + 1),
            paint,
        )
    }

    private fun drawChessPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = chessPoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawAmazonsHighlights(canvas: Canvas) {
        val drawCell: (Canvas, Position, Paint) -> Unit =
            if (isAmazons8Board()) {
                { target, position, paint -> drawChessCell(target, position, paint) }
            } else {
                { target, position, paint -> drawDraughtsCell(target, position, paint) }
            }

        gameState.lastMove?.let { move ->
            drawCell(canvas, move.from, highlightGold)
            drawCell(canvas, move.to, highlightGold)
            (move.metadata[AmazonsRuleEngine.ARROW_METADATA] as? Position)?.let {
                drawCell(canvas, it, highlightGold)
            }
        }
        selectedPos?.let { drawCell(canvas, it, highlightBlue) }

        val targets = if (amazonsPendingMoves != null) {
            legalMoves.mapNotNull {
                it.metadata[AmazonsRuleEngine.ARROW_METADATA] as? Position
            }.distinct()
        } else {
            legalMoves.map { it.to }.distinct()
        }
        targets.forEach { position ->
            val point = cellCenter(position)
            canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
        }
    }

    private fun drawAmazonsPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) as? AmazonsPiece ?: continue
                val point = cellCenter(position)
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawDraughtsBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        val bitmap = draughtsBitmap()
        if (bitmap == null) {
            for (row in 0 until gameState.boardSize) {
                for (col in 0 until gameState.boardSize) {
                    val displayedCol = if (isFlipped) gameState.boardSize - 1 - col else col
                    val displayedRow = if (isFlipped) gameState.boardSize - 1 - row else row
                    val left = draughtsLineX(displayedCol)
                    val top = draughtsLineY(displayedRow)
                    canvas.drawRect(
                        left,
                        top,
                        draughtsLineX(displayedCol + 1),
                        draughtsLineY(displayedRow + 1),
                        if ((row + col) % 2 == 0) lightPaint else darkPaint,
                    )
                }
            }
        } else {
            canvas.drawBitmap(
                bitmap,
                null,
                draughtsImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawDraughtsHighlights(canvas: Canvas) {
        gameState.lastMove?.let {
            drawDraughtsCell(canvas, it.from, highlightGold)
            drawDraughtsCell(canvas, it.to, highlightGold)
        }
        selectedPos?.let { drawDraughtsCell(canvas, it, highlightBlue) }

        val showHints = directMoveMode || SettingsManager.getCheckersHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = draughtsPoint(move.to)
                if (!directMoveMode && move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.44f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
                }
            }
        }
    }

    private fun drawDraughtsCell(canvas: Canvas, position: Position, paint: Paint) {
        val last = gameState.boardSize - 1
        val displayedCol = if (isFlipped) last - position.col else position.col
        val displayedRow = if (isFlipped) last - position.row else position.row
        canvas.drawRect(
            draughtsLineX(displayedCol),
            draughtsLineY(displayedRow),
            draughtsLineX(displayedCol + 1),
            draughtsLineY(displayedRow + 1),
            paint,
        )
    }

    private fun drawDraughtsPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = draughtsPoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        xiangqiBitmap()?.let {
            canvas.drawBitmap(
                it,
                null,
                xiangqiImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawShogiBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        shogiBitmap()?.let {
            canvas.drawBitmap(it, null, shogiImageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun drawShogiHighlights(canvas: Canvas) {
        val showHints = SettingsManager.getShowHints(context)
        gameState.lastMove?.let {
            drawShogiCell(canvas, it.from, highlightGold)
            drawShogiCell(canvas, it.to, highlightGold)
        }
        selectedPos?.let { drawShogiPieceHighlight(canvas, it) }
        if (showHints) {
            legalMoves.forEach { move ->
                val point = shogiPoint(move.to)
                if (move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.40f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.13f, dotPaint)
                }
            }
        }
    }

    private fun drawShogiCell(canvas: Canvas, position: Position, paint: Paint) {
        // Hand drops use a synthetic origin (row -1, column = piece type)
        // because they do not start on a board square. Never try to index the
        // Shogi grid with that origin while highlighting the last move.
        if (position.row !in 0 until ShogiSetup.SIZE ||
            position.col !in 0 until ShogiSetup.SIZE
        ) return
        val col = if (isFlipped) 8 - position.col else position.col
        val row = if (isFlipped) 8 - position.row else position.row
        canvas.drawRect(
            shogiLineX(col),
            shogiLineY(row),
            shogiLineX(col + 1),
            shogiLineY(row + 1),
            paint,
        )
    }

    private fun drawShogiPieceHighlight(canvas: Canvas, position: Position) {
        val piece = gameState.get(position) ?: return
        val point = shogiPoint(position)
        val halfW = shogiCellWidth * 0.395f
        val halfH = shogiCellHeight * 0.415f
        val path = shogiPiecePath(point.x, point.y, halfW, halfH)

        canvas.save()
        if (piece.color == PieceColor.BLACK) canvas.rotate(180f, point.x, point.y)
        canvas.drawPath(path, highlightBlue)
        canvas.drawPath(path, shogiSelectionEdgePaint)
        canvas.restore()
    }

    private fun drawShogiPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = shogiPoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiHighlights(canvas: Canvas) {
        val radius = cellSize * 0.46f
        gameState.lastMove?.let {
            canvas.drawCircle(xiangqiPoint(it.from).x, xiangqiPoint(it.from).y, radius, highlightGold)
            canvas.drawCircle(xiangqiPoint(it.to).x, xiangqiPoint(it.to).y, radius, highlightGold)
        }
        selectedPos?.let {
            canvas.drawCircle(xiangqiPoint(it).x, xiangqiPoint(it).y, radius, highlightBlue)
        }
        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = xiangqiPoint(move.to)
                if (move.isCapture) canvas.drawCircle(point.x, point.y, cellSize * 0.42f, ringPaint)
                else canvas.drawCircle(point.x, point.y, cellSize * 0.15f, dotPaint)
            }
        }
    }

    private fun drawXiangqiPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until 10) for (col in 0 until 9) {
            val position = Position(row, col)
            if (position == skipPos) continue
            val piece = gameState.get(position) ?: continue
            val point = xiangqiPoint(position)
            drawXiangqiPiece(canvas, piece, point.x, point.y)
        }
        animPiece?.let {
            drawXiangqiPiece(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawXiangqiPiece(canvas: Canvas, piece: Piece, cx: Float, cy: Float) {
        val radius = cellSize * 0.43f
        canvas.drawCircle(cx + radius * 0.08f, cy + radius * 0.11f, radius, shadowPaint)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7E8C8") }
        canvas.drawCircle(cx, cy, radius, fill)
        val edge = if (piece.color == PieceColor.WHITE) Color.parseColor("#C62828")
        else Color.parseColor("#171717")
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = radius * 0.09f
            it.color = edge
            canvas.drawCircle(cx, cy, radius * 0.91f, it)
            it.strokeWidth = radius * 0.035f
            canvas.drawCircle(cx, cy, radius * 0.79f, it)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.02f
            isFakeBoldText = true
            color = edge
        }
        val metrics = text.fontMetrics
        canvas.drawText(piece.symbol(), cx, cy - (metrics.ascent + metrics.descent) / 2f, text)
    }

    private fun drawBoard(canvas: Canvas) {
        if (isFoxAndGeeseBoard()) {
            drawFoxAndGeeseBoard(canvas)
            return
        }
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            val l = boardLeft + col * cellSize; val t = boardTop + row * cellSize
            canvas.drawRect(l, t, l + cellSize, t + cellSize,
                if ((row + col) % 2 == 0) lightPaint else darkPaint)
        }
    }

    private fun drawLabels(canvas: Canvas) {
        if (isFoxAndGeeseBoard()) return
        val size = gameState.boardSize
        for (i in 0 until size) {
            val file = ('a' + if (isFlipped) size - 1 - i else i).toString()
            val rank = ((if (isFlipped) i + 1 else size - i)).toString()
            canvas.drawText(file, boardLeft + i * cellSize + cellSize * 0.86f,
                boardTop + size * cellSize - cellSize * 0.06f, labelPaint)
            canvas.drawText(rank, boardLeft + cellSize * 0.10f,
                boardTop + i * cellSize + cellSize * 0.28f, labelPaint)
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        gameState.lastMove?.let { m ->
            highlightCell(canvas, m.from, highlightGold)
            highlightCell(canvas, m.to,   highlightGold)
        }
        selectedPos?.let { highlightCell(canvas, it, highlightBlue) }
        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            for (move in legalMoves) {
                val cx = boardLeft + boardCol(move.to.col) * cellSize + cellSize / 2f
                val cy = boardTop  + boardRow(move.to.row) * cellSize + cellSize / 2f
                if (!directMoveMode && move.isCapture)
                    canvas.drawCircle(cx, cy, cellSize * 0.44f, ringPaint)
                else
                    canvas.drawCircle(cx, cy, cellSize * 0.17f, dotPaint)
            }
        }
    }

    private fun highlightCell(canvas: Canvas, pos: Position, paint: Paint) {
        val c = boardCol(pos.col); val r = boardRow(pos.row)
        if (isFoxAndGeeseBoard()) {
            canvas.drawCircle(
                boardLeft + c * cellSize + cellSize / 2f,
                boardTop + r * cellSize + cellSize / 2f,
                cellSize * 0.38f,
                paint
            )
        } else {
            canvas.drawRect(boardLeft + c * cellSize, boardTop + r * cellSize,
                boardLeft + (c+1) * cellSize, boardTop + (r+1) * cellSize, paint)
        }
    }

    /**
     * Draw the traditional 33-point cross board rather than a checkerboard.
     * Every playable point is connected to its valid neighbouring points,
     * matching the movement graph used by FoxAndGeeseRuleEngine.
     */
    private fun drawFoxAndGeeseBoard(canvas: Canvas) {
        val size = FoxAndGeeseSetup.BOARD_SIZE
        val left = boardLeft
        val top = boardTop
        val right = left + size * cellSize
        val bottom = top + size * cellSize
        val theme = SettingsManager.currentTheme(context)

        fun mix(first: Int, second: Int, secondWeight: Float): Int {
            val weight = secondWeight.coerceIn(0f, 1f)
            val inverse = 1f - weight
            return Color.rgb(
                (Color.red(first) * inverse + Color.red(second) * weight).toInt(),
                (Color.green(first) * inverse + Color.green(second) * weight).toInt(),
                (Color.blue(first) * inverse + Color.blue(second) * weight).toInt()
            )
        }

        val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.light
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.035f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.dark, Color.BLACK, 0.18f)
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.045f
            strokeCap = Paint.Cap.ROUND
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.light, Color.WHITE, 0.55f)
            style = Paint.Style.FILL
        }
        val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mix(theme.dark, theme.accent, 0.35f)
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.025f
        }

        val cross = Path().apply {
            moveTo(left + 2f * cellSize, top)
            lineTo(left + 5f * cellSize, top)
            lineTo(left + 5f * cellSize, top + 2f * cellSize)
            lineTo(right, top + 2f * cellSize)
            lineTo(right, top + 5f * cellSize)
            lineTo(left + 5f * cellSize, top + 5f * cellSize)
            lineTo(left + 5f * cellSize, bottom)
            lineTo(left + 2f * cellSize, bottom)
            lineTo(left + 2f * cellSize, top + 5f * cellSize)
            lineTo(left, top + 5f * cellSize)
            lineTo(left, top + 2f * cellSize)
            lineTo(left + 2f * cellSize, top + 2f * cellSize)
            close()
        }
        canvas.drawPath(cross, boardPaint)
        canvas.drawPath(cross, borderPaint)

        val dirs = listOf(
            Position(-1, -1), Position(-1, 0), Position(-1, 1),
            Position(0, -1),                  Position(0, 1),
            Position(1, -1),  Position(1, 0),  Position(1, 1)
        )
        for (row in 0 until size) for (col in 0 until size) {
            val from = Position(row, col)
            if (!FoxAndGeeseSetup.isPlayable(from)) continue
            for (dir in dirs) {
                val to = from + dir
                if (!FoxAndGeeseSetup.isConnected(from, to)) continue
                if (to.row < row || (to.row == row && to.col <= col)) continue
                canvas.drawLine(
                    boardLeft + col * cellSize + cellSize / 2f,
                    boardTop + row * cellSize + cellSize / 2f,
                    boardLeft + to.col * cellSize + cellSize / 2f,
                    boardTop + to.row * cellSize + cellSize / 2f,
                    linePaint
                )
            }
        }

        for (row in 0 until size) for (col in 0 until size) {
            if (!FoxAndGeeseSetup.isPlayable(Position(row, col))) continue
            val cx = boardLeft + col * cellSize + cellSize / 2f
            val cy = boardTop + row * cellSize + cellSize / 2f
            canvas.drawCircle(cx, cy, cellSize * 0.105f, pointPaint)
            canvas.drawCircle(cx, cy, cellSize * 0.105f, pointBorderPaint)
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            if (skipPos != null && skipPos.row == row && skipPos.col == col) continue
            val piece = gameState.get(row, col) ?: continue
            val cx = boardLeft + boardCol(col) * cellSize + cellSize / 2f
            val cy = boardTop  + boardRow(row) * cellSize + cellSize / 2f
            if (showMustCaptureHints && Position(row, col) in mustCapturePieces)
                canvas.drawCircle(cx, cy, cellSize * 0.42f, mustCapturePaint)
            drawPieceAt(canvas, piece, cx, cy, Position(row, col))
        }
        val ap = animPiece
        if (ap != null) {
            val cx = lerp(animFromPx.x, animToPx.x, animProgress)
            val cy = lerp(animFromPx.y, animToPx.y, animProgress)
            drawPieceAt(canvas, ap, cx, cy)
        }
    }

    private fun drawGoPieces(canvas: Canvas) {
        for (row in 0 until gameState.boardSize) for (col in 0 until gameState.boardSize) {
            val piece = gameState.get(row, col) as? GoPiece ?: continue
            val point = goPoint(Position(row, col))
            drawGoPiece(canvas, piece, point.x, point.y)
        }
    }

    private fun drawGoHighlights(canvas: Canvas) {
        val lastMove = gameState.lastMove
        if (lastMove != null && lastMove.metadata["pass"] != true) {
            val point = goPoint(lastMove.to)
            canvas.drawCircle(point.x, point.y, cellSize * 0.16f, highlightGold)
        }
    }

    private fun drawOthelloBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        othelloBoardBitmap?.let {
            canvas.drawBitmap(
                it,
                null,
                othelloImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawFoxAndGeeseImageBoard(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 20))
        foxAndGeeseBitmap()?.let {
            canvas.drawBitmap(
                it,
                null,
                foxAndGeeseImageRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun drawFoxAndGeeseHighlights(canvas: Canvas) {
        gameState.lastMove?.let {
            drawFoxAndGeesePoint(canvas, it.from, highlightGold)
            drawFoxAndGeesePoint(canvas, it.to, highlightGold)
        }
        selectedPos?.let { drawFoxAndGeesePoint(canvas, it, highlightBlue) }

        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = foxAndGeesePoint(move.to)
                if (!directMoveMode && move.isCapture) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.44f, ringPaint)
                } else {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
                }
            }
        }
    }

    private fun drawFoxAndGeesePoint(canvas: Canvas, position: Position, paint: Paint) {
        val point = foxAndGeesePoint(position)
        canvas.drawCircle(point.x, point.y, cellSize * 0.38f, paint)
    }

    private fun drawFoxAndGeesePieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = foxAndGeesePoint(position)
                if (showMustCaptureHints && position in mustCapturePieces) {
                    canvas.drawCircle(point.x, point.y, cellSize * 0.42f, mustCapturePaint)
                }
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawOthelloHighlights(canvas: Canvas) {
        gameState.lastMove?.let { drawOthelloCell(canvas, it.to, highlightGold) }
        selectedPos?.let { drawOthelloCell(canvas, it, highlightBlue) }

        val showHints = directMoveMode || SettingsManager.getShowHints(context)
        if (showHints) {
            legalMoves.forEach { move ->
                val point = othelloPoint(move.to)
                canvas.drawCircle(point.x, point.y, cellSize * 0.17f, dotPaint)
            }
        }
    }

    private fun drawOthelloCell(canvas: Canvas, position: Position, paint: Paint) {
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        canvas.drawRect(
            othelloLineX(displayedCol),
            othelloLineY(displayedRow),
            othelloLineX(displayedCol + 1),
            othelloLineY(displayedRow + 1),
            paint,
        )
    }

    private fun drawOthelloPieces(canvas: Canvas) {
        val skipPos = animFromPos
        for (row in 0 until gameState.boardSize) {
            for (col in 0 until gameState.boardSize) {
                val position = Position(row, col)
                if (position == skipPos) continue
                val piece = gameState.get(position) ?: continue
                val point = othelloPoint(position)
                drawPieceAt(canvas, piece, point.x, point.y, position)
            }
        }
        animPiece?.let {
            drawPieceAt(
                canvas,
                it,
                lerp(animFromPx.x, animToPx.x, animProgress),
                lerp(animFromPx.y, animToPx.y, animProgress),
            )
        }
    }

    private fun drawGoPiece(canvas: Canvas, piece: GoPiece, cx: Float, cy: Float) {
        val radius = cellSize * 0.43f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 0, 0, 0)
            maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + radius * 0.10f, cy + radius * 0.15f, radius, shadow)

        val colors = if (piece.color == PieceColor.BLACK) {
            intArrayOf(Color.rgb(92, 92, 92), Color.rgb(28, 28, 28), Color.rgb(3, 3, 3))
        } else {
            intArrayOf(Color.WHITE, Color.rgb(235, 235, 235), Color.rgb(164, 164, 164))
        }
        val stone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.33f,
                cy - radius * 0.38f,
                radius * 1.35f,
                colors,
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, stone)

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, radius * 0.045f)
            color = if (piece.color == PieceColor.BLACK)
                Color.argb(190, 0, 0, 0)
            else
                Color.argb(150, 118, 118, 118)
        }
        canvas.drawCircle(cx, cy, radius, edge)

        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.color == PieceColor.BLACK)
                Color.argb(110, 255, 255, 255)
            else
                Color.argb(145, 255, 255, 255)
        }
        canvas.drawOval(
            RectF(
                cx - radius * 0.52f,
                cy - radius * 0.62f,
                cx - radius * 0.03f,
                cy - radius * 0.34f,
            ),
            glint,
        )
    }

    private fun drawPieceAt(canvas: Canvas, piece: Piece, cx: Float, cy: Float, pos: Position? = null) {
        when (piece) {
            is AmazonsPiece  -> drawAmazonsPiece(canvas, piece, cx, cy)
            is ChessPiece    -> drawChessPiece(canvas, piece, cx, cy)
            is CheckersPiece -> drawCheckersPiece(canvas, piece, cx, cy)
            is FoxAndGeesePiece -> drawFoxAndGeesePiece(canvas, piece, cx, cy)
            is OthelloPiece  -> drawOthelloPiece(canvas, piece, cx, cy, pos)
            is ShogiPiece    -> drawShogiPiece(canvas, piece, cx, cy)
            is XiangqiPiece  -> drawXiangqiPiece(canvas, piece, cx, cy)
        }
    }

    private fun drawAmazonsPiece(
        canvas: Canvas,
        piece: AmazonsPiece,
        cx: Float,
        cy: Float,
    ) {
        amazonsPieceBitmap(piece)?.let { bitmap ->
            val size = cellSize * 0.86f
            val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
            canvas.save()
            if (shouldRotate) {
                canvas.rotate(180f, cx, cy)
            }
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f),
                amazonsPiecePaint,
            )
            canvas.restore()
            return
        }

        if (piece.type == AmazonsPieceType.ARROW) {
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4A3326")
                style = Paint.Style.STROKE
                strokeWidth = cellSize * 0.075f
                strokeCap = Paint.Cap.ROUND
            }
            val radius = cellSize * 0.23f
            canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, arrowPaint)
            canvas.drawLine(cx + radius, cy - radius, cx - radius, cy + radius, arrowPaint)
            return
        }

        val symbol = piece.symbol()
        piecePaint.textSize = cellSize * 0.62f
        piecePaint.textAlign = Paint.Align.CENTER
        val isBlack = piece.color == PieceColor.BLACK
        piecePaint.style = Paint.Style.FILL
        piecePaint.strokeWidth = 0f
        piecePaint.color = if (!isBlack) {
            Color.parseColor("#FFF8E8")
        } else {
            Color.parseColor("#211A18")
        }
        val bounds = Rect()
        piecePaint.getTextBounds(symbol, 0, symbol.length, bounds)
        val baseline = cy - (bounds.top + bounds.bottom) / 2f
        piecePaint.setShadowLayer(
            cellSize * 0.045f,
            cellSize * 0.025f,
            cellSize * 0.04f,
            Color.argb(150, 0, 0, 0),
        )
        if (isBlack) {
            piecePaint.style = Paint.Style.STROKE
            piecePaint.strokeWidth = maxOf(1f, cellSize * 0.018f)
            piecePaint.color = Color.parseColor("#E8E8E8")
            canvas.drawText(symbol, cx, baseline, piecePaint)
            piecePaint.style = Paint.Style.FILL
            piecePaint.strokeWidth = 0f
            piecePaint.color = Color.parseColor("#211A18")
        }
        canvas.drawText(symbol, cx, baseline, piecePaint)
        piecePaint.clearShadowLayer()
    }

    private fun drawChessPiece(canvas: Canvas, piece: ChessPiece, cx: Float, cy: Float) {
        val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
        val symbol = piece.symbol()
        piecePaint.style = Paint.Style.FILL
        piecePaint.strokeWidth = 0f
        val glyphBounds = Rect()
        piecePaint.getTextBounds(symbol, 0, symbol.length, glyphBounds)
        val glyphDimension = maxOf(glyphBounds.width(), glyphBounds.height()).toFloat()
        val targetDimension = cellSize * 0.66f
        val glyphScale = if (glyphDimension > 0f) targetDimension / glyphDimension else 1f
        val glyphBaseline = cy - (glyphBounds.top + glyphBounds.bottom) / 2f

        // Unicode chess glyphs have different native bounds. Fit every glyph
        // to the same square footprint so no piece looks taller or wider than
        // the others, while keeping its silhouette proportions intact.
        canvas.save()
        if (shouldRotate) { canvas.save(); canvas.rotate(180f, cx, cy) }
        canvas.scale(glyphScale, glyphScale, cx, cy)
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = cellSize * 0.018f / glyphScale
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#757575")
                           else Color.parseColor("#EEEEEE")
        canvas.drawText(symbol, cx, glyphBaseline, piecePaint)
        piecePaint.style = Paint.Style.FILL
        piecePaint.color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDE7")
                           else Color.parseColor("#212121")
        canvas.drawText(symbol, cx, glyphBaseline, piecePaint)
        canvas.restore()
        if (shouldRotate) canvas.restore()
    }

    /**
     * The second Chess set is drawn entirely from Canvas paths. Keeping the
     * silhouette in code makes the outline thickness and board-size scaling
     * consistent on every device.
     */
    private fun drawReferenceStauntonChessPiece(
        canvas: Canvas,
        piece: ChessPiece,
        cx: Float,
        cy: Float,
    ) {
        drawReferenceChessPiece(canvas, piece, cx, cy, illustrated = false)
    }

    private fun drawReferenceIllustratedChessPiece(
        canvas: Canvas,
        piece: ChessPiece,
        cx: Float,
        cy: Float,
    ) {
        drawReferenceChessPiece(canvas, piece, cx, cy, illustrated = true)
    }

    private fun drawReferenceChessPiece(
        canvas: Canvas,
        piece: ChessPiece,
        cx: Float,
        cy: Float,
        illustrated: Boolean,
    ) {
        val scale = cellSize
        val isWhite = piece.color == PieceColor.WHITE
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (illustrated) {
                shader = LinearGradient(
                    cx - scale * 0.24f,
                    cy - scale * 0.46f,
                    cx + scale * 0.24f,
                    cy + scale * 0.42f,
                    if (isWhite) {
                        intArrayOf(
                            Color.parseColor("#FFFFFF"),
                            Color.parseColor("#E8E1D8"),
                            Color.parseColor("#9D9185"),
                        )
                    } else {
                        intArrayOf(
                            Color.parseColor("#555C68"),
                            Color.parseColor("#2A303C"),
                            Color.parseColor("#0B0E15"),
                        )
                    },
                    null,
                    Shader.TileMode.CLAMP,
                )
            } else {
                color = if (isWhite) Color.parseColor("#FFF9EC")
                else Color.parseColor("#202735")
            }
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) Color.parseColor("#312A29")
            else Color.parseColor("#F1D7B9")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1.5f, scale * if (illustrated) 0.030f else 0.026f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (illustrated) {
                if (isWhite) Color.argb(155, 255, 255, 255)
                else Color.argb(175, 207, 216, 228)
            } else {
                if (isWhite) Color.parseColor("#8E8176")
                else Color.parseColor("#786B68")
            }
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, scale * if (illustrated) 0.015f else 0.012f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        canvas.save()
        if (rotateBlackPieces && piece.color == PieceColor.BLACK) {
            canvas.rotate(180f, cx, cy)
        }
        canvas.drawOval(
            cx - scale * 0.245f,
            cy + scale * 0.355f,
            cx + scale * 0.265f,
            cy + scale * 0.465f,
            shadowPaint,
        )
        when (piece.type) {
            ChessPieceType.PAWN ->
                drawReferencePawn(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.ROOK ->
                drawReferenceRook(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.KNIGHT ->
                drawReferenceKnight(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.BISHOP ->
                drawReferenceBishop(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.QUEEN ->
                drawReferenceQueen(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.KING ->
                drawReferenceKing(canvas, cx, cy, scale, fill, edge, detail)
        }
        canvas.restore()
    }

    private fun drawReferencePawn(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.095f, cy - scale * 0.17f)
            cubicTo(
                cx - scale * 0.105f, cy - scale * 0.06f,
                cx - scale * 0.095f, cy + scale * 0.07f,
                cx - scale * 0.145f, cy + scale * 0.18f,
            )
            cubicTo(
                cx - scale * 0.17f, cy + scale * 0.235f,
                cx - scale * 0.19f, cy + scale * 0.27f,
                cx - scale * 0.20f, cy + scale * 0.30f,
            )
            lineTo(cx + scale * 0.20f, cy + scale * 0.30f)
            cubicTo(
                cx + scale * 0.19f, cy + scale * 0.27f,
                cx + scale * 0.17f, cy + scale * 0.235f,
                cx + scale * 0.145f, cy + scale * 0.18f,
            )
            cubicTo(
                cx + scale * 0.095f, cy + scale * 0.07f,
                cx + scale * 0.105f, cy - scale * 0.06f,
                cx + scale * 0.095f, cy - scale * 0.17f,
            )
            close()
        }
        drawReferencePath(canvas, body, fill, edge)
        canvas.drawOval(
            cx - scale * 0.14f,
            cy - scale * 0.215f,
            cx + scale * 0.14f,
            cy - scale * 0.125f,
            fill,
        )
        canvas.drawOval(
            cx - scale * 0.14f,
            cy - scale * 0.215f,
            cx + scale * 0.14f,
            cy - scale * 0.125f,
            edge,
        )
        canvas.drawCircle(cx, cy - scale * 0.30f, scale * 0.108f, fill)
        canvas.drawCircle(cx, cy - scale * 0.30f, scale * 0.108f, edge)
        canvas.drawLine(
            cx - scale * 0.09f,
            cy - scale * 0.315f,
            cx + scale * 0.045f,
            cy - scale * 0.355f,
            detail,
        )
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceRook(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.165f, cy - scale * 0.27f)
            cubicTo(
                cx - scale * 0.15f, cy - scale * 0.14f,
                cx - scale * 0.13f, cy + scale * 0.08f,
                cx - scale * 0.17f, cy + scale * 0.30f,
            )
            lineTo(cx + scale * 0.17f, cy + scale * 0.30f)
            cubicTo(
                cx + scale * 0.13f, cy + scale * 0.08f,
                cx + scale * 0.15f, cy - scale * 0.14f,
                cx + scale * 0.165f, cy - scale * 0.27f,
            )
            close()
        }
        drawReferencePath(canvas, body, fill, edge)
        val crown = Path().apply {
            moveTo(cx - scale * 0.20f, cy - scale * 0.24f)
            lineTo(cx - scale * 0.20f, cy - scale * 0.43f)
            lineTo(cx - scale * 0.125f, cy - scale * 0.43f)
            lineTo(cx - scale * 0.125f, cy - scale * 0.35f)
            lineTo(cx - scale * 0.045f, cy - scale * 0.35f)
            lineTo(cx - scale * 0.045f, cy - scale * 0.43f)
            lineTo(cx + scale * 0.045f, cy - scale * 0.43f)
            lineTo(cx + scale * 0.045f, cy - scale * 0.35f)
            lineTo(cx + scale * 0.125f, cy - scale * 0.35f)
            lineTo(cx + scale * 0.125f, cy - scale * 0.43f)
            lineTo(cx + scale * 0.20f, cy - scale * 0.43f)
            lineTo(cx + scale * 0.20f, cy - scale * 0.24f)
            close()
        }
        drawReferencePath(canvas, crown, fill, edge)
        drawReferenceBand(canvas, cx, cy - scale * 0.235f, scale * 0.18f, detail)
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceKnight(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val horse = Path().apply {
            moveTo(cx - scale * 0.18f, cy + scale * 0.30f)
            cubicTo(
                cx - scale * 0.19f, cy + scale * 0.14f,
                cx - scale * 0.205f, cy + scale * 0.025f,
                cx - scale * 0.18f, cy - scale * 0.085f,
            )
            cubicTo(
                cx - scale * 0.16f, cy - scale * 0.16f,
                cx - scale * 0.20f, cy - scale * 0.22f,
                cx - scale * 0.23f, cy - scale * 0.28f,
            )
            lineTo(cx - scale * 0.13f, cy - scale * 0.30f)
            cubicTo(
                cx - scale * 0.10f, cy - scale * 0.34f,
                cx - scale * 0.105f, cy - scale * 0.405f,
                cx - scale * 0.08f, cy - scale * 0.445f,
            )
            lineTo(cx - scale * 0.015f, cy - scale * 0.375f)
            cubicTo(
                cx + scale * 0.045f, cy - scale * 0.43f,
                cx + scale * 0.12f, cy - scale * 0.43f,
                cx + scale * 0.17f, cy - scale * 0.38f,
            )
            cubicTo(
                cx + scale * 0.15f, cy - scale * 0.32f,
                cx + scale * 0.20f, cy - scale * 0.27f,
                cx + scale * 0.195f, cy - scale * 0.18f,
            )
            cubicTo(
                cx + scale * 0.19f, cy - scale * 0.07f,
                cx + scale * 0.13f, cy + scale * 0.08f,
                cx + scale * 0.16f, cy + scale * 0.30f,
            )
            close()
        }
        drawReferencePath(canvas, horse, fill, edge)
        canvas.drawLine(
            cx - scale * 0.14f,
            cy - scale * 0.30f,
            cx + scale * 0.12f,
            cy - scale * 0.35f,
            detail,
        )
        canvas.drawLine(
            cx - scale * 0.13f,
            cy - scale * 0.10f,
            cx + scale * 0.055f,
            cy - scale * 0.16f,
            detail,
        )
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceBishop(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.115f, cy - scale * 0.19f)
            cubicTo(
                cx - scale * 0.12f, cy - scale * 0.08f,
                cx - scale * 0.13f, cy + scale * 0.09f,
                cx - scale * 0.17f, cy + scale * 0.30f,
            )
            lineTo(cx + scale * 0.17f, cy + scale * 0.30f)
            cubicTo(
                cx + scale * 0.13f, cy + scale * 0.09f,
                cx + scale * 0.12f, cy - scale * 0.08f,
                cx + scale * 0.115f, cy - scale * 0.19f,
            )
            close()
        }
        drawReferencePath(canvas, body, fill, edge)
        val mitre = Path().apply {
            moveTo(cx, cy - scale * 0.47f)
            cubicTo(
                cx - scale * 0.11f, cy - scale * 0.40f,
                cx - scale * 0.14f, cy - scale * 0.27f,
                cx - scale * 0.10f, cy - scale * 0.19f,
            )
            lineTo(cx + scale * 0.10f, cy - scale * 0.19f)
            cubicTo(
                cx + scale * 0.14f, cy - scale * 0.27f,
                cx + scale * 0.11f, cy - scale * 0.40f,
                cx, cy - scale * 0.47f,
            )
            close()
        }
        drawReferencePath(canvas, mitre, fill, edge)
        canvas.drawLine(
            cx - scale * 0.04f,
            cy - scale * 0.405f,
            cx + scale * 0.045f,
            cy - scale * 0.245f,
            detail,
        )
        drawReferenceBand(canvas, cx, cy - scale * 0.18f, scale * 0.12f, detail)
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceQueen(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.13f, cy - scale * 0.20f)
            cubicTo(
                cx - scale * 0.13f, cy - scale * 0.05f,
                cx - scale * 0.12f, cy + scale * 0.10f,
                cx - scale * 0.18f, cy + scale * 0.30f,
            )
            lineTo(cx + scale * 0.18f, cy + scale * 0.30f)
            cubicTo(
                cx + scale * 0.12f, cy + scale * 0.10f,
                cx + scale * 0.13f, cy - scale * 0.05f,
                cx + scale * 0.13f, cy - scale * 0.20f,
            )
            close()
        }
        drawReferencePath(canvas, body, fill, edge)
        val crown = Path().apply {
            moveTo(cx - scale * 0.205f, cy - scale * 0.20f)
            lineTo(cx - scale * 0.17f, cy - scale * 0.40f)
            cubicTo(
                cx - scale * 0.13f, cy - scale * 0.34f,
                cx - scale * 0.095f, cy - scale * 0.30f,
                cx - scale * 0.055f, cy - scale * 0.39f,
            )
            lineTo(cx, cy - scale * 0.30f)
            lineTo(cx + scale * 0.055f, cy - scale * 0.39f)
            cubicTo(
                cx + scale * 0.095f, cy - scale * 0.30f,
                cx + scale * 0.13f, cy - scale * 0.34f,
                cx + scale * 0.17f, cy - scale * 0.40f,
            )
            lineTo(cx + scale * 0.205f, cy - scale * 0.20f)
            close()
        }
        drawReferencePath(canvas, crown, fill, edge)
        drawReferenceBand(canvas, cx, cy - scale * 0.19f, scale * 0.18f, detail)
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceKing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.135f, cy - scale * 0.20f)
            cubicTo(
                cx - scale * 0.14f, cy - scale * 0.05f,
                cx - scale * 0.13f, cy + scale * 0.10f,
                cx - scale * 0.18f, cy + scale * 0.30f,
            )
            lineTo(cx + scale * 0.18f, cy + scale * 0.30f)
            cubicTo(
                cx + scale * 0.13f, cy + scale * 0.10f,
                cx + scale * 0.14f, cy - scale * 0.05f,
                cx + scale * 0.135f, cy - scale * 0.20f,
            )
            close()
        }
        drawReferencePath(canvas, body, fill, edge)
        val crown = RectF(
            cx - scale * 0.18f,
            cy - scale * 0.35f,
            cx + scale * 0.18f,
            cy - scale * 0.18f,
        )
        canvas.drawRoundRect(crown, scale * 0.035f, scale * 0.035f, fill)
        canvas.drawRoundRect(crown, scale * 0.035f, scale * 0.035f, edge)
        canvas.drawRoundRect(
            cx - scale * 0.026f,
            cy - scale * 0.49f,
            cx + scale * 0.026f,
            cy - scale * 0.29f,
            scale * 0.012f,
            scale * 0.012f,
            edge,
        )
        canvas.drawRoundRect(
            cx - scale * 0.085f,
            cy - scale * 0.425f,
            cx + scale * 0.085f,
            cy - scale * 0.375f,
            scale * 0.012f,
            scale * 0.012f,
            edge,
        )
        drawReferenceBand(canvas, cx, cy - scale * 0.19f, scale * 0.18f, detail)
        drawReferenceBase(canvas, cx, cy, scale, fill, edge, detail)
    }

    private fun drawReferenceBase(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val upper = RectF(
            cx - scale * 0.22f,
            cy + scale * 0.21f,
            cx + scale * 0.22f,
            cy + scale * 0.34f,
        )
        canvas.drawRoundRect(upper, scale * 0.035f, scale * 0.035f, fill)
        canvas.drawRoundRect(upper, scale * 0.035f, scale * 0.035f, edge)
        val lower = RectF(
            cx - scale * 0.275f,
            cy + scale * 0.30f,
            cx + scale * 0.275f,
            cy + scale * 0.43f,
        )
        canvas.drawRoundRect(lower, scale * 0.055f, scale * 0.055f, fill)
        canvas.drawRoundRect(lower, scale * 0.055f, scale * 0.055f, edge)
        canvas.drawLine(
            cx - scale * 0.20f,
            cy + scale * 0.265f,
            cx + scale * 0.20f,
            cy + scale * 0.265f,
            detail,
        )
        canvas.drawLine(
            cx - scale * 0.245f,
            cy + scale * 0.365f,
            cx + scale * 0.245f,
            cy + scale * 0.365f,
            detail,
        )
    }

    private fun drawReferenceBand(
        canvas: Canvas,
        cx: Float,
        y: Float,
        halfWidth: Float,
        detail: Paint,
    ) {
        canvas.drawLine(cx - halfWidth, y, cx + halfWidth, y, detail)
        canvas.drawLine(
            cx - halfWidth * 0.82f,
            y + cellSize * 0.025f,
            cx + halfWidth * 0.82f,
            y + cellSize * 0.025f,
            detail,
        )
    }

    private fun drawReferencePath(
        canvas: Canvas,
        path: Path,
        fill: Paint,
        edge: Paint,
    ) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
    }

    private fun drawVectorIllustratedChessPiece(
        canvas: Canvas,
        piece: ChessPiece,
        cx: Float,
        cy: Float,
    ) {
        val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
        val isWhite = piece.color == PieceColor.WHITE
        val scale = cellSize
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - scale * 0.24f,
                cy - scale * 0.46f,
                cx + scale * 0.24f,
                cy + scale * 0.42f,
                if (isWhite) {
                    intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#E9E4DB"),
                        Color.parseColor("#A69B8E"),
                    )
                } else {
                    intArrayOf(
                        Color.parseColor("#686D72"),
                        Color.parseColor("#2D3135"),
                        Color.parseColor("#0B0D0F"),
                    )
                },
                null,
                Shader.TileMode.CLAMP,
            )
            style = Paint.Style.FILL
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) Color.parseColor("#4A4138") else Color.parseColor("#050505")
            style = Paint.Style.STROKE
            strokeWidth = scale * 0.032f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) {
                Color.argb(175, 255, 255, 255)
            } else {
                Color.argb(165, 196, 204, 210)
            }
            style = Paint.Style.STROKE
            strokeWidth = scale * 0.016f
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        if (shouldRotate) canvas.rotate(180f, cx, cy)
        drawStauntonShadow(canvas, cx, cy, scale)
        drawStauntonBase(canvas, cx, cy, scale, fill, edge, detail)
        when (piece.type) {
            ChessPieceType.PAWN ->
                drawStauntonPawn(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.ROOK ->
                drawStauntonRook(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.KNIGHT ->
                drawStauntonKnight(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.BISHOP ->
                drawStauntonBishop(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.QUEEN ->
                drawStauntonQueen(canvas, cx, cy, scale, fill, edge, detail)
            ChessPieceType.KING ->
                drawStauntonKing(canvas, cx, cy, scale, fill, edge, detail)
        }
        canvas.restore()
    }

    private fun drawVectorStauntonChessPiece(canvas: Canvas, piece: ChessPiece, cx: Float, cy: Float) {
        val shouldRotate = rotateBlackPieces && piece.color == PieceColor.BLACK
        val isWhite = piece.color == PieceColor.WHITE
        val scale = cellSize
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) Color.parseColor("#F4F0E7") else Color.parseColor("#171513")
            style = Paint.Style.FILL
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) Color.parseColor("#3F3831") else Color.parseColor("#050505")
            style = Paint.Style.STROKE
            strokeWidth = scale * 0.018f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isWhite) Color.parseColor("#6E6257") else Color.parseColor("#51483F")
            style = Paint.Style.STROKE
            strokeWidth = scale * 0.014f
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        if (shouldRotate) canvas.rotate(180f, cx, cy)
        drawStauntonShadow(canvas, cx, cy, scale)
        drawStauntonBase(canvas, cx, cy, scale, fill, edge, detail)
        when (piece.type) {
            com.mkdev.mkboardgames.games.chess.ChessPieceType.PAWN ->
                drawStauntonPawn(canvas, cx, cy, scale, fill, edge, detail)
            com.mkdev.mkboardgames.games.chess.ChessPieceType.ROOK ->
                drawStauntonRook(canvas, cx, cy, scale, fill, edge, detail)
            com.mkdev.mkboardgames.games.chess.ChessPieceType.KNIGHT ->
                drawStauntonKnight(canvas, cx, cy, scale, fill, edge, detail)
            com.mkdev.mkboardgames.games.chess.ChessPieceType.BISHOP ->
                drawStauntonBishop(canvas, cx, cy, scale, fill, edge, detail)
            com.mkdev.mkboardgames.games.chess.ChessPieceType.QUEEN ->
                drawStauntonQueen(canvas, cx, cy, scale, fill, edge, detail)
            com.mkdev.mkboardgames.games.chess.ChessPieceType.KING ->
                drawStauntonKing(canvas, cx, cy, scale, fill, edge, detail)
        }
        canvas.restore()
    }

    private fun drawStauntonShadow(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(scale * 0.035f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(
            cx - scale * 0.25f, cy + scale * 0.34f,
            cx + scale * 0.28f, cy + scale * 0.46f, shadow,
        )
    }

    private fun drawStauntonBase(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Paint,
        edge: Paint,
        detail: Paint,
    ) {
        val base = RectF(
            cx - scale * 0.275f,
            cy + scale * 0.30f,
            cx + scale * 0.275f,
            cy + scale * 0.425f,
        )
        canvas.drawRoundRect(base, scale * 0.045f, scale * 0.045f, fill)
        canvas.drawRoundRect(base, scale * 0.045f, scale * 0.045f, edge)
        canvas.drawLine(
            cx - scale * 0.255f, cy + scale * 0.345f,
            cx + scale * 0.255f, cy + scale * 0.345f, detail,
        )
        canvas.drawLine(
            cx - scale * 0.235f, cy + scale * 0.385f,
            cx + scale * 0.235f, cy + scale * 0.385f, detail,
        )
    }

    private fun drawStauntonPawn(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        val body = Path().apply {
            moveTo(cx - scale * 0.16f, cy + scale * 0.31f)
            cubicTo(
                cx - scale * 0.13f, cy + scale * 0.20f,
                cx - scale * 0.09f, cy + scale * 0.11f,
                cx - scale * 0.08f, cy + scale * 0.03f,
            )
            cubicTo(
                cx - scale * 0.15f, cy - scale * 0.04f,
                cx - scale * 0.16f, cy - scale * 0.10f,
                cx - scale * 0.14f, cy - scale * 0.14f,
            )
            lineTo(cx + scale * 0.14f, cy - scale * 0.14f)
            cubicTo(
                cx + scale * 0.16f, cy - scale * 0.10f,
                cx + scale * 0.15f, cy - scale * 0.04f,
                cx + scale * 0.08f, cy + scale * 0.03f,
            )
            cubicTo(
                cx + scale * 0.09f, cy + scale * 0.11f,
                cx + scale * 0.13f, cy + scale * 0.20f,
                cx + scale * 0.16f, cy + scale * 0.31f,
            )
            close()
        }
        drawStauntonPath(canvas, body, fill, edge)
        canvas.drawCircle(cx, cy - scale * 0.23f, scale * 0.115f, fill)
        canvas.drawCircle(cx, cy - scale * 0.23f, scale * 0.115f, edge)
        canvas.drawLine(
            cx - scale * 0.105f, cy - scale * 0.075f,
            cx + scale * 0.105f, cy - scale * 0.075f, detail,
        )
    }

    private fun drawStauntonRook(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        drawStauntonPath(
            canvas, taperedStauntonBody(cx, cy, scale, -0.26f, 0.17f), fill, edge,
        )
        val crown = Path().apply {
            moveTo(cx - scale * 0.20f, cy - scale * 0.22f)
            lineTo(cx - scale * 0.20f, cy - scale * 0.40f)
            lineTo(cx - scale * 0.12f, cy - scale * 0.40f)
            lineTo(cx - scale * 0.12f, cy - scale * 0.33f)
            lineTo(cx - scale * 0.04f, cy - scale * 0.33f)
            lineTo(cx - scale * 0.04f, cy - scale * 0.40f)
            lineTo(cx + scale * 0.04f, cy - scale * 0.40f)
            lineTo(cx + scale * 0.04f, cy - scale * 0.33f)
            lineTo(cx + scale * 0.12f, cy - scale * 0.33f)
            lineTo(cx + scale * 0.12f, cy - scale * 0.40f)
            lineTo(cx + scale * 0.20f, cy - scale * 0.40f)
            lineTo(cx + scale * 0.20f, cy - scale * 0.22f)
            close()
        }
        drawStauntonPath(canvas, crown, fill, edge)
        drawStauntonBand(canvas, cx, cy - scale * 0.21f, scale * 0.18f, detail)
    }

    private fun drawStauntonKnight(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        val horse = Path().apply {
            moveTo(cx - scale * 0.18f, cy + scale * 0.30f)
            cubicTo(
                cx - scale * 0.12f, cy + scale * 0.16f,
                cx - scale * 0.17f, cy + scale * 0.07f,
                cx - scale * 0.20f, cy - scale * 0.02f,
            )
            cubicTo(
                cx - scale * 0.22f, cy - scale * 0.11f,
                cx - scale * 0.15f, cy - scale * 0.17f,
                cx - scale * 0.08f, cy - scale * 0.19f,
            )
            cubicTo(
                cx - scale * 0.15f, cy - scale * 0.29f,
                cx - scale * 0.12f, cy - scale * 0.40f,
                cx - scale * 0.03f, cy - scale * 0.45f,
            )
            lineTo(cx + scale * 0.02f, cy - scale * 0.36f)
            cubicTo(
                cx + scale * 0.12f, cy - scale * 0.35f,
                cx + scale * 0.20f, cy - scale * 0.28f,
                cx + scale * 0.21f, cy - scale * 0.18f,
            )
            cubicTo(
                cx + scale * 0.22f, cy - scale * 0.10f,
                cx + scale * 0.15f, cy - scale * 0.04f,
                cx + scale * 0.10f, cy + scale * 0.02f,
            )
            cubicTo(
                cx + scale * 0.10f, cy + scale * 0.12f,
                cx + scale * 0.15f, cy + scale * 0.23f,
                cx + scale * 0.18f, cy + scale * 0.30f,
            )
            close()
        }
        drawStauntonPath(canvas, horse, fill, edge)
        val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (edge.color == Color.parseColor("#050505")) {
                Color.WHITE
            } else {
                Color.parseColor("#2E2925")
            }
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx + scale * 0.085f, cy - scale * 0.23f, scale * 0.018f, eye)
        canvas.drawLine(
            cx - scale * 0.09f, cy - scale * 0.15f,
            cx + scale * 0.07f, cy - scale * 0.09f, detail,
        )
    }

    private fun drawStauntonBishop(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        drawStauntonPath(
            canvas, taperedStauntonBody(cx, cy, scale, -0.24f, 0.13f), fill, edge,
        )
        val mitre = Path().apply {
            moveTo(cx, cy - scale * 0.45f)
            cubicTo(
                cx - scale * 0.12f, cy - scale * 0.40f,
                cx - scale * 0.15f, cy - scale * 0.28f,
                cx - scale * 0.10f, cy - scale * 0.19f,
            )
            cubicTo(
                cx - scale * 0.06f, cy - scale * 0.13f,
                cx + scale * 0.06f, cy - scale * 0.13f,
                cx + scale * 0.10f, cy - scale * 0.19f,
            )
            cubicTo(
                cx + scale * 0.15f, cy - scale * 0.28f,
                cx + scale * 0.12f, cy - scale * 0.40f,
                cx, cy - scale * 0.45f,
            )
            close()
        }
        drawStauntonPath(canvas, mitre, fill, edge)
        canvas.drawLine(
            cx - scale * 0.055f, cy - scale * 0.38f,
            cx + scale * 0.055f, cy - scale * 0.22f, detail,
        )
        drawStauntonBand(canvas, cx, cy - scale * 0.20f, scale * 0.14f, detail)
    }

    private fun drawStauntonQueen(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        drawStauntonPath(
            canvas, taperedStauntonBody(cx, cy, scale, -0.21f, 0.14f), fill, edge,
        )
        val crown = Path().apply {
            moveTo(cx - scale * 0.20f, cy - scale * 0.18f)
            lineTo(cx - scale * 0.16f, cy - scale * 0.39f)
            lineTo(cx - scale * 0.08f, cy - scale * 0.29f)
            lineTo(cx, cy - scale * 0.42f)
            lineTo(cx + scale * 0.08f, cy - scale * 0.29f)
            lineTo(cx + scale * 0.16f, cy - scale * 0.39f)
            lineTo(cx + scale * 0.20f, cy - scale * 0.18f)
            close()
        }
        drawStauntonPath(canvas, crown, fill, edge)
        drawStauntonBand(canvas, cx, cy - scale * 0.18f, scale * 0.18f, detail)
        val jewel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (edge.color == Color.parseColor("#050505")) {
                Color.parseColor("#8A7B6E")
            } else {
                Color.parseColor("#6E6257")
            }
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy - scale * 0.40f, scale * 0.025f, jewel)
    }

    private fun drawStauntonKing(
        canvas: Canvas, cx: Float, cy: Float, scale: Float,
        fill: Paint, edge: Paint, detail: Paint,
    ) {
        drawStauntonPath(
            canvas, taperedStauntonBody(cx, cy, scale, -0.22f, 0.15f), fill, edge,
        )
        val crown = Path().apply {
            moveTo(cx - scale * 0.19f, cy - scale * 0.18f)
            lineTo(cx - scale * 0.17f, cy - scale * 0.37f)
            lineTo(cx + scale * 0.17f, cy - scale * 0.37f)
            lineTo(cx + scale * 0.19f, cy - scale * 0.18f)
            close()
        }
        drawStauntonPath(canvas, crown, fill, edge)
        drawStauntonBand(canvas, cx, cy - scale * 0.18f, scale * 0.17f, detail)
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (edge.color == Color.parseColor("#050505")) {
                Color.parseColor("#51483F")
            } else {
                Color.parseColor("#3F3831")
            }
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            cx - scale * 0.025f, cy - scale * 0.48f,
            cx + scale * 0.025f, cy - scale * 0.30f,
            scale * 0.012f, scale * 0.012f, cross,
        )
        canvas.drawRoundRect(
            cx - scale * 0.075f, cy - scale * 0.43f,
            cx + scale * 0.075f, cy - scale * 0.38f,
            scale * 0.012f, scale * 0.012f, cross,
        )
    }

    private fun taperedStauntonBody(
        cx: Float,
        cy: Float,
        scale: Float,
        top: Float,
        topWidth: Float,
    ): Path = Path().apply {
        moveTo(cx - scale * 0.17f, cy + scale * 0.31f)
        cubicTo(
            cx - scale * 0.15f, cy + scale * 0.17f,
            cx - scale * topWidth, cy + scale * 0.05f,
            cx - scale * topWidth, cy + scale * top,
        )
        lineTo(cx + scale * topWidth, cy + scale * top)
        cubicTo(
            cx + scale * topWidth, cy + scale * 0.05f,
            cx + scale * 0.15f, cy + scale * 0.17f,
            cx + scale * 0.17f, cy + scale * 0.31f,
        )
        close()
    }

    private fun drawStauntonPath(canvas: Canvas, path: Path, fill: Paint, edge: Paint) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
    }

    private fun drawStauntonBand(canvas: Canvas, cx: Float, y: Float, halfWidth: Float, detail: Paint) {
        canvas.drawLine(cx - halfWidth, y, cx + halfWidth, y, detail)
        canvas.drawLine(
            cx - halfWidth * 0.90f, y + cellSize * 0.025f,
            cx + halfWidth * 0.90f, y + cellSize * 0.025f, detail,
        )
    }

    private fun drawCheckersPiece(canvas: Canvas, piece: CheckersPiece, cx: Float, cy: Float) {
        val r = cellSize * 0.38f
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        val isWhite = piece.color == PieceColor.WHITE
        val base = if (isWhite) Color.parseColor("#F2F2F2") else Color.parseColor("#432B3A")
        val edge = if (isWhite) Color.parseColor("#858585") else Color.parseColor("#1C1420")
        val highlight = if (isWhite) Color.WHITE else Color.parseColor("#765064")
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.32f, cy - r * 0.38f, r * 1.25f,
                intArrayOf(highlight, base, edge),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, face)
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.10f
            it.color = edge
            canvas.drawCircle(cx, cy, r * 0.92f, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE; it.strokeWidth = r * 0.06f
            it.color = if (isWhite) Color.parseColor("#C7C7C7") else Color.parseColor("#765064")
            canvas.drawCircle(cx, cy, r * 0.76f, it)
            canvas.drawCircle(cx, cy, r * 0.61f, it)
        }
        if (piece.isKing) {
            val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = r * 1.0f
                color = if (isWhite) Color.parseColor("#555555") else Color.parseColor("#E7B45C")
                isFakeBoldText = true
            }
            val metrics = kp.fontMetrics
            canvas.drawText(
                CheckersPiece.KING_SYMBOL,
                cx,
                cy - (metrics.ascent + metrics.descent) / 2f + r * 0.04f,
                kp,
            )
        }
    }

    private fun drawFoxAndGeesePiece(
        canvas: Canvas,
        piece: FoxAndGeesePiece,
        cx: Float,
        cy: Float
    ) {
        val radius = cellSize * 0.36f
        val fox = piece.type == FoxAndGeesePieceType.FOX
        val bitmap = if (fox) foxAndGeeseFoxPieceBitmap else foxAndGeeseGoosePieceBitmap
        if (bitmap != null) {
            // The photographed board has tighter visual spacing than the
            // canvas version, so keep its supplied pieces slightly smaller.
            val imageRadius = cellSize * 0.35f
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    cx - imageRadius,
                    cy - imageRadius,
                    cx + imageRadius,
                    cy + imageRadius,
                ),
                foxAndGeesePiecePaint,
            )
            return
        }
        val base = if (fox) Color.parseColor("#35B7A1") else Color.parseColor("#F2F2F2")
        val highlight = if (fox) Color.parseColor("#A8F1D7") else Color.WHITE
        val edge = if (fox) Color.parseColor("#126E69") else Color.parseColor("#858585")
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * .30f, cy - radius * .38f, radius * 1.25f,
                intArrayOf(highlight, base, edge),
                floatArrayOf(0f, .58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, face)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = edge
            style = Paint.Style.STROKE
            strokeWidth = radius * .09f
        }
        canvas.drawCircle(cx, cy, radius * .91f, ring)
        ring.color = if (fox) Color.parseColor("#D7FFF0") else Color.parseColor("#C7C7C7")
        ring.strokeWidth = radius * .035f
        canvas.drawCircle(cx, cy, radius * .72f, ring)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (fox) Color.parseColor("#083F43") else Color.parseColor("#333333")
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.02f
            isFakeBoldText = true
        }
        val metrics = label.fontMetrics
        canvas.drawText(if (fox) "F" else "G", cx, cy - (metrics.ascent + metrics.descent) / 2f, label)
    }

    private fun drawOthelloPiece(canvas: Canvas, piece: OthelloPiece, cx: Float, cy: Float, pos: Position? = null) {
        val r       = cellSize * 0.40f
        val isWhite = piece.color == PieceColor.WHITE
        val popScale = if (pos != null && pos in recentOthelloPieces)
            othelloPopProgress.coerceAtLeast(0f) else 1f
        canvas.save()
        if (popScale != 1f) canvas.scale(popScale, popScale, cx, cy)
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.30f, cy - r * 0.36f, r * 1.25f,
                if (isWhite) {
                    intArrayOf(Color.WHITE, Color.parseColor("#E6E0D0"), Color.parseColor("#A79F90"))
                } else {
                    intArrayOf(Color.parseColor("#56636A"), Color.parseColor("#20292E"), Color.BLACK)
                },
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, disc)
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = r * 0.045f
            it.color = if (isWhite) Color.parseColor("#938A7B") else Color.parseColor("#0B0F11")
            canvas.drawCircle(cx, cy, r * 0.96f, it)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = if (isWhite) Color.argb(145, 255, 255, 255) else Color.argb(90, 255, 255, 255)
            canvas.drawOval(
                RectF(cx - r * 0.56f, cy - r * 0.68f, cx - r * 0.08f, cy - r * 0.40f),
                it,
            )
        }
        canvas.restore()
    }

    private fun drawShogiPiece(canvas: Canvas, piece: ShogiPiece, cx: Float, cy: Float) {
        val halfW = shogiCellWidth * 0.34f
        val halfH = shogiCellHeight * 0.36f
        val path = shogiPiecePath(cx, cy, halfW, halfH)
        canvas.save()
        if (piece.color == PieceColor.BLACK) canvas.rotate(180f, cx, cy)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(cellSize * 0.035f, cellSize * 0.055f)
        canvas.drawPath(path, shadow)
        canvas.restore()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.color == PieceColor.WHITE) Color.parseColor("#FFFDF2")
            else Color.parseColor("#E7E0D1")
        }
        canvas.drawPath(path, fill)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.promoted) Color.parseColor("#B23A32")
            else Color.parseColor("#2A211B")
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.035f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, edge)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (piece.promoted) Color.parseColor("#B23A32")
            else Color.parseColor("#221B17")
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.43f
            isFakeBoldText = true
        }
        val metrics = text.fontMetrics
        canvas.drawText(piece.symbol(), cx, cy - (metrics.ascent + metrics.descent) / 2f + cellSize * 0.025f, text)
        canvas.restore()
    }

    private fun shogiPiecePath(cx: Float, cy: Float, halfW: Float, halfH: Float) =
        Path().apply {
            moveTo(cx, cy - halfH)
            lineTo(cx + halfW * 0.76f, cy - halfH * 0.60f)
            lineTo(cx + halfW, cy + halfH)
            lineTo(cx - halfW, cy + halfH)
            lineTo(cx - halfW * 0.76f, cy - halfH * 0.60f)
            close()
        }

    // ─── Coordinate helpers ───────────────────────────────────────────────────

    private fun boardRow(logicRow: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicRow else logicRow
    private fun boardCol(logicCol: Int) =
        if (isFlipped) gameState.boardSize - 1 - logicCol else logicCol

    private fun isFoxAndGeeseBoard(): Boolean =
        gameState.boardSize == FoxAndGeeseSetup.BOARD_SIZE &&
            gameState.board.any { it is FoxAndGeesePiece }

    private fun isXiangqiBoard(): Boolean =
        ruleEngine is XiangqiRuleEngine || gameState.board.any { it is XiangqiPiece }

    private fun xiangqiBitmap(): Bitmap? = when (xiangqiBoardStyle) {
        XiangqiBoardStyle.CLASSIC -> xiangqiBoardBitmap
        XiangqiBoardStyle.CHINESE -> xiangqiChineseBoardBitmap
        XiangqiBoardStyle.ENGLISH -> xiangqiEnglishBoardBitmap
    }

    private fun xiangqiGridX(): FloatArray = when (xiangqiBoardStyle) {
        XiangqiBoardStyle.CLASSIC -> floatArrayOf(
            0.052f, 0.164375f, 0.27675f, 0.389125f, 0.5015f,
            0.613875f, 0.72625f, 0.838625f, 0.951f,
        )
        XiangqiBoardStyle.CHINESE,
        XiangqiBoardStyle.ENGLISH -> floatArrayOf(
            100f / 1024f, 205f / 1024f, 308f / 1024f, 410f / 1024f,
            514f / 1024f, 617f / 1024f, 719f / 1024f, 821f / 1024f,
            923f / 1024f,
        )
    }

    private fun xiangqiGridY(): FloatArray = when (xiangqiBoardStyle) {
        XiangqiBoardStyle.CLASSIC -> floatArrayOf(
            0.113f, 0.200222f, 0.287444f, 0.374667f, 0.461889f,
            0.549111f, 0.636333f, 0.723556f, 0.810778f, 0.898f,
        )
        XiangqiBoardStyle.CHINESE,
        XiangqiBoardStyle.ENGLISH -> floatArrayOf(
            230f / 1536f, 340f / 1536f, 452f / 1536f, 563f / 1536f,
            677f / 1536f, 815f / 1536f, 928f / 1536f, 1038f / 1536f,
            1151f / 1536f, 1261f / 1536f,
        )
    }

    private fun xiangqiLineX(index: Int): Float =
        xiangqiImageRect.left + xiangqiImageRect.width() * xiangqiGridX()[index]

    private fun xiangqiLineY(index: Int): Float =
        xiangqiImageRect.top + xiangqiImageRect.height() * xiangqiGridY()[index]

    private fun isShogiBoard(): Boolean =
        ruleEngine is ShogiRuleEngine || gameState.board.any { it is ShogiPiece }

    private fun shogiBitmap(): Bitmap? = when (shogiBoardStyle) {
        ShogiBoardStyle.CLASSIC -> classicShogiBoardBitmap
        ShogiBoardStyle.WOOD -> woodShogiBoardBitmap
    }

    private fun shogiGridX(): FloatArray = when (shogiBoardStyle) {
        ShogiBoardStyle.CLASSIC -> classicShogiGridX
        ShogiBoardStyle.WOOD -> woodShogiGridX
    }

    private fun shogiGridY(): FloatArray = when (shogiBoardStyle) {
        ShogiBoardStyle.CLASSIC -> classicShogiGridY
        ShogiBoardStyle.WOOD -> woodShogiGridY
    }

    private fun isGoBoard(): Boolean = ruleEngine is GoRuleEngine

    private fun isOthelloBoard(): Boolean =
        ruleEngine is OthelloRuleEngine || gameState.board.any { it is OthelloPiece }

    private fun isOthelloImageBoard(): Boolean =
        isOthelloBoard() && othelloBitmap() != null

    private fun othelloBitmap(): Bitmap? =
        if (othelloBoardStyle == OthelloBoardStyle.GREEN_FELT) othelloBoardBitmap else null

    private fun isFoxAndGeeseImageBoard(): Boolean =
        isFoxAndGeeseBoard() && foxAndGeeseBitmap() != null

    private fun foxAndGeeseBitmap(): Bitmap? = when (foxAndGeeseBoardStyle) {
        FoxAndGeeseBoardStyle.CANVAS -> null
        FoxAndGeeseBoardStyle.LIGHT_WOOD -> foxAndGeeseLightWoodBoardBitmap
        FoxAndGeeseBoardStyle.CROSS_WOOD -> foxAndGeeseCrossWoodBoardBitmap
    }

    private fun foxAndGeeseGridX(): FloatArray = when (foxAndGeeseBoardStyle) {
        FoxAndGeeseBoardStyle.CANVAS -> floatArrayOf()
        FoxAndGeeseBoardStyle.LIGHT_WOOD -> foxAndGeeseLightWoodGridX
        FoxAndGeeseBoardStyle.CROSS_WOOD -> foxAndGeeseCrossWoodGridX
    }

    private fun foxAndGeeseGridY(): FloatArray = when (foxAndGeeseBoardStyle) {
        FoxAndGeeseBoardStyle.CANVAS -> floatArrayOf()
        FoxAndGeeseBoardStyle.LIGHT_WOOD -> foxAndGeeseLightWoodGridY
        FoxAndGeeseBoardStyle.CROSS_WOOD -> foxAndGeeseCrossWoodGridY
    }

    private fun othelloPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        return PointF(
            (othelloLineX(displayedCol) + othelloLineX(displayedCol + 1)) / 2f,
            (othelloLineY(displayedRow) + othelloLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun othelloLineX(index: Int): Float =
        othelloImageRect.left + othelloImageRect.width() * othelloGridX[index]

    private fun othelloLineY(index: Int): Float =
        othelloImageRect.top + othelloImageRect.height() * othelloGridY[index]

    private fun foxAndGeesePoint(position: Position): PointF {
        val displayedRow = if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - position.row else position.row
        val displayedCol = if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - position.col else position.col
        val gridX = foxAndGeeseGridX()
        val gridY = foxAndGeeseGridY()
        return PointF(
            foxAndGeeseImageRect.left + foxAndGeeseImageRect.width() * gridX[displayedCol],
            foxAndGeeseImageRect.top + foxAndGeeseImageRect.height() * gridY[displayedRow],
        )
    }

    private fun isChessBoard(): Boolean =
        ruleEngine is ChessRuleEngine || gameState.board.any { it is ChessPiece }

    private fun isAmazonsBoard(): Boolean =
        ruleEngine is AmazonsRuleEngine ||
            gameState.board.any { it is AmazonsPiece }

    private fun isAmazons8Board(): Boolean =
        isAmazonsBoard() && gameState.boardSize == 8

    private fun isAmazons10Board(): Boolean =
        isAmazonsBoard() && gameState.boardSize == 10

    private fun isAmazonsImageBoard(): Boolean =
        if (isAmazons8Board()) chessBitmap() != null
        else if (isAmazons10Board()) draughtsBitmap() != null
        else false

    private fun amazonsPieceBitmap(piece: AmazonsPiece): Bitmap? {
        if (!isAmazonsImageBoard()) return null
        return when (piece.type) {
            AmazonsPieceType.AMAZON ->
                if (piece.color == PieceColor.WHITE) {
                    amazonsWhiteAmazonBitmap
                } else {
                    amazonsBlackAmazonBitmap
                }
            AmazonsPieceType.ARROW ->
                if (piece.color == PieceColor.WHITE) {
                    amazonsWhiteArrowBitmap
                } else {
                    amazonsBlackArrowBitmap
                }
        }
    }

    private fun isDraughtsBoard(): Boolean =
        ruleEngine is CheckersRuleEngine ||
            ruleEngine is InternationalDraughtsRuleEngine ||
            gameState.board.any { it is CheckersPiece } ||
            isAmazons10Board()

    private fun isDraughtsImageBoard(): Boolean =
        isDraughtsBoard() && draughtsBitmap() != null

    private fun draughtsBitmap(): Bitmap? = when (draughtsBoardStyle) {
        DraughtsBoardStyle.CANVAS -> null
        DraughtsBoardStyle.RED_BLACK -> draughtsBoardBitmap
        DraughtsBoardStyle.CLASSIC_WOOD -> classicChessBoardBitmap
        DraughtsBoardStyle.SUPPLIED_WOOD -> suppliedChessBoardBitmap
        DraughtsBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessBoardBitmap
        DraughtsBoardStyle.BLACK_WHITE -> blackWhiteChessBoardBitmap
        DraughtsBoardStyle.INTERNATIONAL_DARK_WOOD -> internationalDarkDraughtsBoardBitmap
        DraughtsBoardStyle.INTERNATIONAL_LIGHT_WOOD -> internationalLightDraughtsBoardBitmap
    }

    private fun draughtsGridX(): FloatArray = when (draughtsBoardStyle) {
        DraughtsBoardStyle.RED_BLACK -> redBlackDraughtsGridX
        DraughtsBoardStyle.SUPPLIED_WOOD -> suppliedChessGridX
        DraughtsBoardStyle.CLASSIC_WOOD -> classicChessGridX
        DraughtsBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridX
        DraughtsBoardStyle.BLACK_WHITE -> blackWhiteChessGridX
        DraughtsBoardStyle.INTERNATIONAL_DARK_WOOD -> internationalDarkDraughtsGridX
        DraughtsBoardStyle.INTERNATIONAL_LIGHT_WOOD -> internationalLightDraughtsGridX
        DraughtsBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun draughtsGridY(): FloatArray = when (draughtsBoardStyle) {
        DraughtsBoardStyle.RED_BLACK -> redBlackDraughtsGridY
        DraughtsBoardStyle.SUPPLIED_WOOD -> suppliedChessGridY
        DraughtsBoardStyle.CLASSIC_WOOD -> classicChessGridY
        DraughtsBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridY
        DraughtsBoardStyle.BLACK_WHITE -> blackWhiteChessGridY
        DraughtsBoardStyle.INTERNATIONAL_DARK_WOOD -> internationalDarkDraughtsGridY
        DraughtsBoardStyle.INTERNATIONAL_LIGHT_WOOD -> internationalLightDraughtsGridY
        DraughtsBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun chessBitmap(): Bitmap? = when (chessBoardStyle) {
        ChessBoardStyle.CANVAS -> null
        ChessBoardStyle.CLASSIC_WOOD -> classicChessBoardBitmap
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessBoardBitmap
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessBoardBitmap
        ChessBoardStyle.BLACK_WHITE -> blackWhiteChessBoardBitmap
    }

    private fun isChessImageBoard(): Boolean =
        (isChessBoard() || isAmazons8Board()) && chessBitmap() != null

    private fun chessGridX(): FloatArray = when (chessBoardStyle) {
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessGridX
        ChessBoardStyle.CLASSIC_WOOD -> classicChessGridX
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridX
        ChessBoardStyle.BLACK_WHITE -> blackWhiteChessGridX
        ChessBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun chessGridY(): FloatArray = when (chessBoardStyle) {
        ChessBoardStyle.SUPPLIED_WOOD -> suppliedChessGridY
        ChessBoardStyle.CLASSIC_WOOD -> classicChessGridY
        ChessBoardStyle.REALISTIC_BLACK_WHITE -> realisticChessGridY
        ChessBoardStyle.BLACK_WHITE -> blackWhiteChessGridY
        ChessBoardStyle.CANVAS -> floatArrayOf()
    }

    private fun goPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 12 - position.row else position.row
        val displayedCol = if (isFlipped) 12 - position.col else position.col
        return PointF(
            goImageRect.left + goImageRect.width() * goGridX[displayedCol],
            goImageRect.top + goImageRect.height() * goGridY[displayedRow],
        )
    }

    private fun chessPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 7 - position.row else position.row
        val displayedCol = if (isFlipped) 7 - position.col else position.col
        return PointF(
            (chessLineX(displayedCol) + chessLineX(displayedCol + 1)) / 2f,
            (chessLineY(displayedRow) + chessLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun chessLineX(index: Int): Float =
        if (isChessImageBoard()) {
            chessImageRect.left + chessImageRect.width() * chessGridX()[index]
        } else {
            boardLeft + index * cellSize
        }

    private fun chessLineY(index: Int): Float =
        if (isChessImageBoard()) {
            chessImageRect.top + chessImageRect.height() * chessGridY()[index]
        } else {
            boardTop + index * cellSize
        }

    private fun draughtsPoint(position: Position): PointF {
        val last = gameState.boardSize - 1
        val displayedRow = if (isFlipped) last - position.row else position.row
        val displayedCol = if (isFlipped) last - position.col else position.col
        return PointF(
            (draughtsLineX(displayedCol) + draughtsLineX(displayedCol + 1)) / 2f,
            (draughtsLineY(displayedRow) + draughtsLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun draughtsLineX(index: Int): Float =
        if (isDraughtsImageBoard()) {
            draughtsImageRect.left + draughtsImageRect.width() * draughtsGridX()[index]
        } else {
            boardLeft + index * cellSize
        }

    private fun draughtsLineY(index: Int): Float =
        if (isDraughtsImageBoard()) {
            draughtsImageRect.top + draughtsImageRect.height() * draughtsGridY()[index]
        } else {
            boardTop + index * cellSize
        }

    private fun shogiPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 8 - position.row else position.row
        val displayedCol = if (isFlipped) 8 - position.col else position.col
        return PointF(
            (shogiLineX(displayedCol) + shogiLineX(displayedCol + 1)) / 2f,
            (shogiLineY(displayedRow) + shogiLineY(displayedRow + 1)) / 2f,
        )
    }

    private fun shogiLineX(index: Int): Float =
        shogiImageRect.left + shogiImageRect.width() * shogiGridX()[index]

    private fun shogiLineY(index: Int): Float =
        shogiImageRect.top + shogiImageRect.height() * shogiGridY()[index]

    private fun xiangqiPoint(position: Position): PointF {
        val displayedRow = if (isFlipped) 9 - position.row else position.row
        val displayedCol = if (isFlipped) 8 - position.col else position.col
        return PointF(
            xiangqiLineX(displayedCol),
            xiangqiLineY(displayedRow),
        )
    }

    private fun screenToBoard(x: Float, y: Float): Position? {
        if (isChessBoard() || isAmazons8Board()) {
            if (chessCellWidth <= 0f || chessCellHeight <= 0f) return null
            val displayedCol = (0 until 8).firstOrNull {
                x >= chessLineX(it) && x < chessLineX(it + 1)
            } ?: return null
            val displayedRow = (0 until 8).firstOrNull {
                y >= chessLineY(it) && y < chessLineY(it + 1)
            } ?: return null
            return Position(
                if (isFlipped) 7 - displayedRow else displayedRow,
                if (isFlipped) 7 - displayedCol else displayedCol,
            )
        }
        if (isDraughtsBoard()) {
            val boardDimension = gameState.boardSize
            val left = draughtsLineX(0)
            val right = draughtsLineX(boardDimension)
            val top = draughtsLineY(0)
            val bottom = draughtsLineY(boardDimension)
            if (x < left || x >= right || y < top || y >= bottom) return null

            // Use the same measured cell centres used for rendering pieces and
            // capture targets. This matters for the photographed boards, whose
            // perspective makes neighbouring cells slightly different widths,
            // and keeps human taps aligned with the visible capture rings.
            val displayedCol = (0 until boardDimension).minByOrNull { index ->
                kotlin.math.abs(
                    x - (draughtsLineX(index) + draughtsLineX(index + 1)) / 2f,
                )
            } ?: return null
            val displayedRow = (0 until boardDimension).minByOrNull { index ->
                kotlin.math.abs(
                    y - (draughtsLineY(index) + draughtsLineY(index + 1)) / 2f,
                )
            } ?: return null
            val last = boardDimension - 1
            return Position(
                if (isFlipped) last - displayedRow else displayedRow,
                if (isFlipped) last - displayedCol else displayedCol,
            )
        }
        if (isShogiBoard()) {
            if (shogiCellWidth <= 0f || shogiCellHeight <= 0f) return null
            val displayedCol = (0 until 9).firstOrNull { x >= shogiLineX(it) && x < shogiLineX(it + 1) }
                ?: return null
            val displayedRow = (0 until 9).firstOrNull { y >= shogiLineY(it) && y < shogiLineY(it + 1) }
                ?: return null
            return Position(
                if (isFlipped) 8 - displayedRow else displayedRow,
                if (isFlipped) 8 - displayedCol else displayedCol,
            )
        }
        if (isXiangqiBoard()) {
            if (xiangqiCellWidth <= 0f || xiangqiCellHeight <= 0f) return null
            val displayedCol = xiangqiGridX().indices.minByOrNull { index ->
                kotlin.math.abs(x - xiangqiLineX(index))
            } ?: return null
            val displayedRow = xiangqiGridY().indices.minByOrNull { index ->
                kotlin.math.abs(y - xiangqiLineY(index))
            } ?: return null
            val nearestX = xiangqiLineX(displayedCol)
            val nearestY = xiangqiLineY(displayedRow)
            if (kotlin.math.abs(x - nearestX) > xiangqiCellWidth * 0.48f ||
                kotlin.math.abs(y - nearestY) > xiangqiCellHeight * 0.48f
            ) return null
            return Position(
                if (isFlipped) 9 - displayedRow else displayedRow,
                if (isFlipped) 8 - displayedCol else displayedCol,
            )
        }
        if (isGoBoard()) {
            if (goImageRect.width() <= 0f || goImageRect.height() <= 0f) return null
            val displayedCol = goGridX.indices.minByOrNull { index ->
                kotlin.math.abs(x - (goImageRect.left + goImageRect.width() * goGridX[index]))
            } ?: return null
            val displayedRow = goGridY.indices.minByOrNull { index ->
                kotlin.math.abs(y - (goImageRect.top + goImageRect.height() * goGridY[index]))
            } ?: return null
            val nearest = goPoint(
                Position(
                    if (isFlipped) 12 - displayedRow else displayedRow,
                    if (isFlipped) 12 - displayedCol else displayedCol,
                ),
            )
            if (kotlin.math.abs(x - nearest.x) > cellSize * 0.52f ||
                kotlin.math.abs(y - nearest.y) > cellSize * 0.52f
            ) return null
            return Position(
                if (isFlipped) 12 - displayedRow else displayedRow,
                if (isFlipped) 12 - displayedCol else displayedCol,
            )
        }
        if (isOthelloImageBoard()) {
            if (othelloCellWidth <= 0f || othelloCellHeight <= 0f) return null
            val displayedCol = (0 until 8).firstOrNull {
                x >= othelloLineX(it) && x < othelloLineX(it + 1)
            } ?: return null
            val displayedRow = (0 until 8).firstOrNull {
                y >= othelloLineY(it) && y < othelloLineY(it + 1)
            } ?: return null
            return Position(
                if (isFlipped) 7 - displayedRow else displayedRow,
                if (isFlipped) 7 - displayedCol else displayedCol,
            )
        }
        if (isFoxAndGeeseImageBoard()) {
            val gridX = foxAndGeeseGridX()
            val gridY = foxAndGeeseGridY()
            if (gridX.isEmpty() || gridY.isEmpty()) return null
            val displayedCol = gridX.indices.minByOrNull { index ->
                kotlin.math.abs(x - (foxAndGeeseImageRect.left + foxAndGeeseImageRect.width() * gridX[index]))
            } ?: return null
            val displayedRow = gridY.indices.minByOrNull { index ->
                kotlin.math.abs(y - (foxAndGeeseImageRect.top + foxAndGeeseImageRect.height() * gridY[index]))
            } ?: return null
            val nearest = foxAndGeesePoint(
                Position(
                    if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - displayedRow else displayedRow,
                    if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - displayedCol else displayedCol,
                ),
            )
            if (kotlin.math.abs(x - nearest.x) > cellSize * 0.54f ||
                kotlin.math.abs(y - nearest.y) > cellSize * 0.54f
            ) return null
            val logicRow =
                if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - displayedRow else displayedRow
            val logicCol =
                if (isFlipped) FoxAndGeeseSetup.BOARD_SIZE - 1 - displayedCol else displayedCol
            val position = Position(logicRow, logicCol)
            return if (FoxAndGeeseSetup.isPlayable(position)) position else null
        }
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        val size = gameState.boardSize
        if (col !in 0 until size || row !in 0 until size) return null
        val logicRow = if (isFlipped) size - 1 - row else row
        val logicCol = if (isFlipped) size - 1 - col else col
        return Position(logicRow, logicCol)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
