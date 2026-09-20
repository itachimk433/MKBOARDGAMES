package com.mkdev.mkboardgames.games.chess

import com.mkdev.mkboardgames.engine.*

class ChessRuleEngine : RuleEngine {

    override fun initialState() = ChessSetup.initialState()

    // ─── Legal move generation ────────────────────────────────────────────────

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = state.get(position) as? ChessPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()
        return pseudoMovesFrom(state, position, piece).filter { move ->
            !capturesKing(state, move) &&
            !isInCheck(applyMoveInternal(state, move), piece.color)
        }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        // legalMovesFrom is intentionally scoped to the state's active turn for
        // touch input. AI search also asks for the other side's moves, so use a
        // turn-scoped snapshot here instead of silently returning no moves.
        val turnState = if (state.currentTurn == color) state else state.copy(currentTurn = color)
        for (row in 0..7) for (col in 0..7) {
            val pos = Position(row, col)
            if (state.get(pos)?.color == color) moves += legalMovesFrom(turnState, pos)
        }
        return moves
    }

    // ─── Pseudo-move generation (no check filtering) ─────────────────────────

    private fun pseudoMovesFrom(
        state: GameState, pos: Position, piece: ChessPiece, forAttack: Boolean = false
    ): List<Move> = when (piece.type) {
        ChessPieceType.PAWN   -> if (forAttack) pawnAttackMoves(pos, piece.color) else pawnMoves(state, pos, piece.color)
        ChessPieceType.KNIGHT -> knightMoves(state, pos, piece.color)
        ChessPieceType.BISHOP -> slidingMoves(state, pos, piece.color, DIAGONALS)
        ChessPieceType.ROOK   -> slidingMoves(state, pos, piece.color, ORTHOGONALS)
        ChessPieceType.QUEEN  -> slidingMoves(state, pos, piece.color, ALL_DIRS)
        ChessPieceType.KING   -> kingMoves(state, pos, piece.color, forAttack)
    }

    private fun slidingMoves(state: GameState, from: Position, color: PieceColor, dirs: List<Position>): List<Move> {
        val moves = mutableListOf<Move>()
        for (dir in dirs) {
            var cur = from + dir
            while (cur.isValid()) {
                val t = state.get(cur)
                if (t == null) { moves += Move(from, cur) }
                else {
                    if (t.color != color) moves += Move(from, cur, listOf(cur))
                    break
                }
                cur = cur + dir
            }
        }
        return moves
    }

    private fun knightMoves(state: GameState, from: Position, color: PieceColor): List<Move> =
        KNIGHT_OFFSETS.mapNotNull { d ->
            val to = from + d
            if (!to.isValid()) null
            else {
                val t = state.get(to)
                if (t?.color == color) null
                else Move(from, to, if (t != null) listOf(to) else emptyList())
            }
        }

    private fun pawnMoves(state: GameState, from: Position, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        val dir      = if (color == PieceColor.WHITE) -1 else 1
        val startRow = if (color == PieceColor.WHITE) 6 else 1
        val promRow  = if (color == PieceColor.WHITE) 0 else 7
        val epCol    = (state.metadata["enPassant"] as? Int) ?: -1

        val step1 = Position(from.row + dir, from.col)
        if (step1.isValid() && state.get(step1) == null) {
            if (step1.row == promRow) PROMO_TYPES.forEach { moves += Move(from, step1, promotionType = it) }
            else {
                moves += Move(from, step1)
                if (from.row == startRow) {
                    val step2 = Position(from.row + 2 * dir, from.col)
                    if (state.get(step2) == null)
                        moves += Move(from, step2, metadata = mapOf("pawnDouble" to true))
                }
            }
        }

        for (dc in listOf(-1, 1)) {
            val diag = Position(from.row + dir, from.col + dc)
            if (!diag.isValid()) continue
            val target = state.get(diag)
            if (target != null && target.color != color) {
                if (diag.row == promRow) PROMO_TYPES.forEach { moves += Move(from, diag, listOf(diag), promotionType = it) }
                else moves += Move(from, diag, listOf(diag))
            }
            val epRow = if (color == PieceColor.WHITE) 3 else 4
            val adjacent = state.get(Position(from.row, diag.col)) as? ChessPiece
            if (epCol == diag.col && from.row == epRow &&
                adjacent?.type == ChessPieceType.PAWN && adjacent.color != color
            ) {
                val capturedPawn = Position(from.row, diag.col)
                moves += Move(from, diag, listOf(capturedPawn), metadata = mapOf("enPassant" to true))
            }
        }
        return moves
    }

    private fun pawnAttackMoves(from: Position, color: PieceColor): List<Move> {
        val dir = if (color == PieceColor.WHITE) -1 else 1
        return listOf(-1, 1).mapNotNull { dc ->
            val to = Position(from.row + dir, from.col + dc)
            if (to.isValid()) Move(from, to) else null
        }
    }

    private fun kingMoves(state: GameState, from: Position, color: PieceColor, forAttack: Boolean = false): List<Move> {
        val moves = mutableListOf<Move>()
        for (dir in ALL_DIRS) {
            val to = from + dir
            if (!to.isValid()) continue
            val t = state.get(to)
            if (t?.color == color) continue
            moves += Move(from, to, if (t != null) listOf(to) else emptyList())
        }
        if (!forAttack) {
            val row  = if (color == PieceColor.WHITE) 7 else 0
            val side = if (color == PieceColor.WHITE) "W" else "B"
            if (from == Position(row, 4) && !isInCheck(state, color)) {
                if (state.metadata["castle${side}K"] == true
                    && state.get(Position(row, 7)) is ChessPiece &&
                    (state.get(Position(row, 7)) as ChessPiece).let {
                        it.type == ChessPieceType.ROOK && it.color == color
                    }
                    && state.get(Position(row, 5)) == null
                    && state.get(Position(row, 6)) == null
                    && !squareAttacked(state, Position(row, 5), color)
                    && !squareAttacked(state, Position(row, 6), color)) {
                    moves += Move(from, Position(row, 6), metadata = mapOf("castle" to "K"))
                }
                if (state.metadata["castle${side}Q"] == true
                    && state.get(Position(row, 0)) is ChessPiece &&
                    (state.get(Position(row, 0)) as ChessPiece).let {
                        it.type == ChessPieceType.ROOK && it.color == color
                    }
                    && state.get(Position(row, 3)) == null
                    && state.get(Position(row, 2)) == null
                    && state.get(Position(row, 1)) == null
                    && !squareAttacked(state, Position(row, 3), color)
                    && !squareAttacked(state, Position(row, 2), color)) {
                    moves += Move(from, Position(row, 2), metadata = mapOf("castle" to "Q"))
                }
            }
        }
        return moves
    }

    // ─── Apply move ───────────────────────────────────────────────────────────

    override fun applyMove(state: GameState, move: Move): GameState {
        // Keep the engine authoritative even when a caller supplies a stale
        // move from a previous board snapshot.
        if (state.status != GameStatus.IN_PROGRESS ||
            allLegalMoves(state, state.currentTurn).none { it == move }
        ) return state
        val next = applyMoveInternal(state, move)
        return next.copy(status = gameStatus(next))
    }

    fun applyMoveInternal(state: GameState, move: Move): GameState {
        val piece = state.get(move.from) as? ChessPiece ?: return state
        var newBoard = state.board.copyOf()

        for (cap in move.captures) newBoard[cap.row * 8 + cap.col] = null

        newBoard[move.from.row * 8 + move.from.col] = null
        val placed = if (move.promotionType != null)
            ChessPiece(ChessPieceType.valueOf(move.promotionType), piece.color)
        else piece
        newBoard[move.to.row * 8 + move.to.col] = placed

        val castleSide = move.metadata["castle"] as? String
        if (castleSide != null) {
            val r = move.from.row
            if (castleSide == "K") {
                newBoard[r * 8 + 5] = newBoard[r * 8 + 7]; newBoard[r * 8 + 7] = null
            } else {
                newBoard[r * 8 + 3] = newBoard[r * 8 + 0]; newBoard[r * 8 + 0] = null
            }
        }

        val meta = state.metadata.toMutableMap()
        val s = if (piece.color == PieceColor.WHITE) "W" else "B"
        if (piece.type == ChessPieceType.KING) {
            meta["castle${s}K"] = false; meta["castle${s}Q"] = false
        }
        if (piece.type == ChessPieceType.ROOK) {
            if (move.from.col == 7) meta["castle${s}K"] = false
            if (move.from.col == 0) meta["castle${s}Q"] = false
        }
        // Capturing a rook on its home square also removes that side's
        // corresponding castling right.
        for (capture in move.captures) {
            if (capture.row == 0 && capture.col == 0) meta["castleBQ"] = false
            if (capture.row == 0 && capture.col == 7) meta["castleBK"] = false
            if (capture.row == 7 && capture.col == 0) meta["castleWQ"] = false
            if (capture.row == 7 && capture.col == 7) meta["castleWK"] = false
        }
        meta["enPassant"] = if (move.metadata["pawnDouble"] == true) move.to.col else -1

        return state.withBoard(newBoard, piece.color.opponent(), move, meta = meta)
    }

    // ─── Check detection ─────────────────────────────────────────────────────

    fun isInCheck(state: GameState, color: PieceColor): Boolean {
        val kingIdx = state.board.indexOfFirst { p ->
            (p as? ChessPiece)?.let { it.type == ChessPieceType.KING && it.color == color } ?: false
        }
        if (kingIdx < 0) return false
        return squareAttacked(state, Position(kingIdx / 8, kingIdx % 8), color)
    }

    /**
     * In chess the king is never captured. A checking move ends the game when
     * the checked side has no legal response, so moves that land on the enemy
     * king must never enter the legal move list.
     */
    private fun capturesKing(state: GameState, move: Move): Boolean =
        move.captures.any { capture ->
            (state.get(capture) as? ChessPiece)?.type == ChessPieceType.KING
        }

    private fun squareAttacked(state: GameState, pos: Position, defenderColor: PieceColor): Boolean {
        val attackerColor = defenderColor.opponent()
        for (row in 0..7) for (col in 0..7) {
            val src = Position(row, col)
            val p = state.get(src) as? ChessPiece ?: continue
            if (p.color != attackerColor) continue
            val pseudo = pseudoMovesFrom(
                state.copy(currentTurn = attackerColor), src, p, forAttack = true
            )
            if (pseudo.any { it.to == pos }) return true
        }
        return false
    }

    // ─── Game status ─────────────────────────────────────────────────────────

    override fun gameStatus(state: GameState): GameStatus {
        val whiteKingPresent = state.board.any {
            (it as? ChessPiece)?.let { piece ->
                piece.color == PieceColor.WHITE && piece.type == ChessPieceType.KING
            } == true
        }
        val blackKingPresent = state.board.any {
            (it as? ChessPiece)?.let { piece ->
                piece.color == PieceColor.BLACK && piece.type == ChessPieceType.KING
            } == true
        }
        if (!whiteKingPresent) return GameStatus.BLACK_WINS
        if (!blackKingPresent) return GameStatus.WHITE_WINS

        val color = state.currentTurn
        val moves = allLegalMoves(state, color)
        return when {
            moves.isNotEmpty()      -> GameStatus.IN_PROGRESS
            isInCheck(state, color) -> if (color == PieceColor.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS
            else                    -> GameStatus.DRAW
        }
    }

    // ─── Evaluation ──────────────────────────────────────────────────────────

    override fun evaluate(state: GameState): Int {
        when (state.status) {
            GameStatus.WHITE_WINS -> return 100_000
            GameStatus.BLACK_WINS -> return -100_000
            GameStatus.DRAW -> return 0
            else -> Unit
        }

        // ── Pre-collect pawn files so the rook open-file bonus is always correct ─
        // (Board is indexed 0..63 top-to-bottom; without pre-collection, black rooks
        //  at row 0 are evaluated before black pawns at row 1 are added, giving every
        //  black rook a spurious +20 "open file" bonus at the start of the game.)
        val board = state.board
        val whitePawnsOnFile = IntArray(8)
        val blackPawnsOnFile = IntArray(8)
        val whitePawnRows = Array(8) { mutableListOf<Int>() }
        val blackPawnRows = Array(8) { mutableListOf<Int>() }

        var whiteBishops = 0
        var blackBishops = 0
        var phase = 0
        var whiteKingIndex = -1
        var blackKingIndex = -1
        var whiteMaterial = 0
        var blackMaterial = 0

        for (index in 0 until minOf(64, board.size)) {
            val piece = board[index] as? ChessPiece ?: continue
            val row = index / 8
            val col = index % 8
            val isWhite = piece.color == PieceColor.WHITE
            when (piece.type) {
                ChessPieceType.PAWN -> {
                    if (isWhite) {
                        whitePawnsOnFile[col]++
                        whitePawnRows[col].add(row)
                    } else {
                        blackPawnsOnFile[col]++
                        blackPawnRows[col].add(row)
                    }
                }
                ChessPieceType.KNIGHT -> phase += 1
                ChessPieceType.BISHOP -> {
                    phase += 1
                    if (isWhite) whiteBishops++ else blackBishops++
                }
                ChessPieceType.ROOK -> phase += 2
                ChessPieceType.QUEEN -> phase += 4
                ChessPieceType.KING -> {
                    if (isWhite) whiteKingIndex = index else blackKingIndex = index
                }
            }
            if (piece.type != ChessPieceType.KING) {
                if (isWhite) whiteMaterial += piece.type.points
                else blackMaterial += piece.type.points
            }
        }
        phase = phase.coerceIn(0, 24)

        var middlegame = 0
        var endgame = 0

        for (index in 0 until minOf(64, board.size)) {
            val piece = board[index] as? ChessPiece ?: continue
            val row = index / 8
            val col = index % 8
            val isWhite = piece.color == PieceColor.WHITE
            val sign = if (isWhite) 1 else -1
            val pstRow = if (isWhite) row else 7 - row

            var middleValue = piece.type.points
            var endValue = piece.type.points

            when (piece.type) {
                ChessPieceType.PAWN -> {
                    middleValue += PAWN_PST[pstRow][col]
                    endValue += PAWN_PST[pstRow][col]
                    val ownPawns = if (isWhite) whitePawnsOnFile else blackPawnsOnFile
                    val enemyRows = if (isWhite) blackPawnRows else whitePawnRows
                    val hasLeftPawn = col > 0 && ownPawns[col - 1] > 0
                    val hasRightPawn = col < 7 && ownPawns[col + 1] > 0
                    if (!hasLeftPawn && !hasRightPawn) {
                        middleValue -= 15
                        endValue -= 20
                    }

                    var passed = true
                    for (file in maxOf(0, col - 1)..minOf(7, col + 1)) {
                        for (enemyRow in enemyRows[file]) {
                            val ahead = if (isWhite) enemyRow < row else enemyRow > row
                            if (ahead) {
                                passed = false
                                break
                            }
                        }
                        if (!passed) break
                    }
                    if (passed) {
                        val rank = if (isWhite) 7 - row else row
                        val bonus = PASSED_BONUS[rank.coerceIn(0, 7)]
                        middleValue += bonus / 2
                        endValue += bonus
                    }
                }
                ChessPieceType.KNIGHT -> {
                    middleValue += KNIGHT_PST[pstRow][col]
                    endValue += KNIGHT_PST[pstRow][col]
                }
                ChessPieceType.BISHOP -> {
                    middleValue += BISHOP_PST[pstRow][col]
                    endValue += BISHOP_PST[pstRow][col]
                }
                ChessPieceType.ROOK -> {
                    val rookPst = ROOK_PST[pstRow][col]
                    middleValue += rookPst
                    endValue += rookPst
                    val ownPawns = if (isWhite) whitePawnsOnFile[col] else blackPawnsOnFile[col]
                    val enemyPawns = if (isWhite) blackPawnsOnFile[col] else whitePawnsOnFile[col]
                    if (ownPawns == 0 && enemyPawns == 0) {
                        middleValue += 22
                        endValue += 12
                    } else if (ownPawns == 0) {
                        middleValue += 10
                        endValue += 6
                    }
                    if (pstRow == 1) {
                        middleValue += 15
                        endValue += 25
                    }
                }
                ChessPieceType.QUEEN -> {
                    middleValue += QUEEN_PST[pstRow][col]
                    endValue += QUEEN_PST[pstRow][col]
                }
                ChessPieceType.KING -> {
                    middleValue += KING_MID_PST[pstRow][col]
                    endValue += KING_END_PST[pstRow][col]
                    val forward = if (isWhite) -1 else 1
                    var shield = 0
                    for (file in maxOf(0, col - 1)..minOf(7, col + 1)) {
                        for (distance in 1..2) {
                            val shieldRow = row + forward * distance
                            if (shieldRow !in 0..7) continue
                            val shieldPiece = board[shieldRow * 8 + file] as? ChessPiece
                            if (shieldPiece?.type == ChessPieceType.PAWN &&
                                shieldPiece.color == piece.color
                            ) {
                                shield += if (distance == 1) 10 else 5
                                break
                            }
                        }
                    }
                    middleValue += shield
                    for (file in maxOf(0, col - 1)..minOf(7, col + 1)) {
                        val ownPawns = if (isWhite) {
                            whitePawnsOnFile[file]
                        } else {
                            blackPawnsOnFile[file]
                        }
                        if (ownPawns == 0) middleValue -= 12
                    }
                }
            }

            middlegame += sign * middleValue
            endgame += sign * endValue
        }

        if (whiteBishops >= 2) {
            middlegame += 30
            endgame += 45
        }
        if (blackBishops >= 2) {
            middlegame -= 30
            endgame -= 45
        }

        for (file in 0..7) {
            if (whitePawnsOnFile[file] > 1) {
                val doubled = whitePawnsOnFile[file] - 1
                middlegame -= 12 * doubled
                endgame -= 20 * doubled
            }
            if (blackPawnsOnFile[file] > 1) {
                val doubled = blackPawnsOnFile[file] - 1
                middlegame += 12 * doubled
                endgame += 20 * doubled
            }
        }

        if (whiteKingIndex >= 0 && blackKingIndex >= 0) {
            val materialDifference = whiteMaterial - blackMaterial
            if (kotlin.math.abs(materialDifference) >= 300 && phase <= 8) {
                val winningKing = if (materialDifference > 0) whiteKingIndex else blackKingIndex
                val losingKing = if (materialDifference > 0) blackKingIndex else whiteKingIndex
                val losingRow = losingKing / 8
                val losingCol = losingKing % 8
                val winningRow = winningKing / 8
                val winningCol = winningKing % 8
                val centreDistance =
                    maxOf(3 - minOf(losingRow, 7 - losingRow), 0) +
                        maxOf(3 - minOf(losingCol, 7 - losingCol), 0)
                val kingDistance =
                    kotlin.math.abs(losingRow - winningRow) +
                        kotlin.math.abs(losingCol - winningCol)
                val mopUp = centreDistance * 12 + (14 - kingDistance) * 6
                endgame += if (materialDifference > 0) mopUp else -mopUp
            }
        }

        return (middlegame * phase + endgame * (24 - phase)) / 24
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
        private val PASSED_BONUS = intArrayOf(0, 5, 10, 20, 35, 60, 100, 0)

        val DIAGONALS   = listOf(Position(-1,-1), Position(-1,1), Position(1,-1), Position(1,1))
        val ORTHOGONALS = listOf(Position(-1,0), Position(1,0), Position(0,-1), Position(0,1))
        val ALL_DIRS    = DIAGONALS + ORTHOGONALS
        val KNIGHT_OFFSETS = listOf(
            Position(-2,-1), Position(-2,1), Position(-1,-2), Position(-1,2),
            Position(1,-2), Position(1,2), Position(2,-1), Position(2,1)
        )
        val PROMO_TYPES = listOf("QUEEN","ROOK","BISHOP","KNIGHT")

        // PST tables — row 0 = own back rank, row 7 = opponent's back rank

        private val PAWN_PST = arrayOf(
            intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0),
            intArrayOf( 50, 50, 50, 50, 50, 50, 50, 50),
            intArrayOf( 10, 10, 20, 30, 30, 20, 10, 10),
            intArrayOf(  5,  5, 10, 25, 25, 10,  5,  5),
            intArrayOf(  0,  0,  0, 20, 20,  0,  0,  0),
            intArrayOf(  5, -5,-10,  0,  0,-10, -5,  5),
            intArrayOf(  5, 10, 10,-20,-20, 10, 10,  5),
            intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0)
        )
        private val KNIGHT_PST = arrayOf(
            intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
            intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
            intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
            intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
            intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
            intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
            intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
            intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
        )
        private val BISHOP_PST = arrayOf(
            intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
            intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
            intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
            intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
            intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
            intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
            intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
            intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
        )
        private val ROOK_PST = arrayOf(
            intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0),
            intArrayOf(  5, 10, 10, 10, 10, 10, 10,  5),
            intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
            intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
            intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
            intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
            intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
            intArrayOf(  0,  0,  0,  5,  5,  0,  0,  0)
        )
        private val QUEEN_PST = arrayOf(
            intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
            intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
            intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
            intArrayOf( -5,  0,  5,  5,  5,  5,  0, -5),
            intArrayOf(  0,  0,  5,  5,  5,  5,  0, -5),
            intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
            intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
            intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
        )
        // Middlegame king: hide behind pawns
        private val KING_MID_PST = arrayOf(
            intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
            intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
            intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
            intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
            intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
            intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
            intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
            intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
        )
        // Endgame king: centralise
        private val KING_END_PST = arrayOf(
            intArrayOf(-50,-40,-30,-20,-20,-30,-40,-50),
            intArrayOf(-30,-20,-10,  0,  0,-10,-20,-30),
            intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
            intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
            intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
            intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
            intArrayOf(-30,-30,  0,  0,  0,  0,-30,-30),
            intArrayOf(-50,-30,-30,-30,-30,-30,-30,-50)
        )
    }
}
