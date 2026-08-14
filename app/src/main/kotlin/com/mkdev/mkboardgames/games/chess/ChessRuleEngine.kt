package com.mkdev.mkboardgames.games.chess

import com.mkdev.mkboardgames.engine.*

class ChessRuleEngine : RuleEngine {

    override fun initialState() = ChessSetup.initialState()

    // ─── Legal move generation ────────────────────────────────────────────────

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = state.get(position) as? ChessPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()
        return pseudoMovesFrom(state, position, piece).filter { move ->
            !isInCheck(applyMoveInternal(state, move), piece.color)
        }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0..7) for (col in 0..7) {
            val pos = Position(row, col)
            if (state.get(pos)?.color == color) moves += legalMovesFrom(state, pos)
        }
        return moves
    }

    // ─── Pseudo-move generation (no check filtering) ─────────────────────────

    private fun pseudoMovesFrom(
        state: GameState, pos: Position, piece: ChessPiece, forAttack: Boolean = false
    ): List<Move> = when (piece.type) {
        ChessPieceType.PAWN   -> pawnMoves(state, pos, piece.color)
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
            if (epCol == diag.col && from.row == epRow) {
                val capturedPawn = Position(from.row, diag.col)
                moves += Move(from, diag, listOf(capturedPawn), metadata = mapOf("enPassant" to true))
            }
        }
        return moves
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
                    && state.get(Position(row, 5)) == null
                    && state.get(Position(row, 6)) == null
                    && !squareAttacked(state, Position(row, 5), color)
                    && !squareAttacked(state, Position(row, 6), color)) {
                    moves += Move(from, Position(row, 6), metadata = mapOf("castle" to "K"))
                }
                if (state.metadata["castle${side}Q"] == true
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
        if (state.status == GameStatus.WHITE_WINS) return  100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW)       return 0

        // ── 3-fold repetition: 4-move cycle in history → treat as draw ─────────
        val hist = state.moveHistory
        if (hist.size >= 8) {
            if ((0..3).all { i ->
                hist[hist.size - 8 + i].from == hist[hist.size - 4 + i].from &&
                hist[hist.size - 8 + i].to   == hist[hist.size - 4 + i].to
            }) return 0
        }

        // ── Pre-collect pawn files so the rook open-file bonus is always correct ─
        // (Board is indexed 0..63 top-to-bottom; without pre-collection, black rooks
        //  at row 0 are evaluated before black pawns at row 1 are added, giving every
        //  black rook a spurious +20 "open file" bonus at the start of the game.)
        val wPawnFiles = mutableSetOf<Int>()
        val bPawnFiles = mutableSetOf<Int>()
        val wPawnCols  = IntArray(8)
        val bPawnCols  = IntArray(8)
        for (idx in 0 until 64) {
            val p = state.board[idx] as? ChessPiece ?: continue
            if (p.type == ChessPieceType.PAWN) {
                val c = idx % 8
                if (p.color == PieceColor.WHITE) { wPawnFiles.add(c); wPawnCols[c]++ }
                else                             { bPawnFiles.add(c); bPawnCols[c]++ }
            }
        }

        var score        = 0
        var whiteBishops = 0; var blackBishops = 0
        var whiteQueens  = 0; var blackQueens  = 0

        // ── Material + piece-square tables ────────────────────────────────────────
        for (idx in 0 until 64) {
            val p = state.board[idx] as? ChessPiece ?: continue
            val logicRow = idx / 8
            val col      = idx % 8
            val pstRow   = if (p.color == PieceColor.WHITE) logicRow else 7 - logicRow
            val pstCol   = col

            val bonus = when (p.type) {
                ChessPieceType.PAWN   -> PAWN_PST[pstRow][pstCol]
                ChessPieceType.KNIGHT -> KNIGHT_PST[pstRow][pstCol]
                ChessPieceType.BISHOP -> {
                    if (p.color == PieceColor.WHITE) whiteBishops++ else blackBishops++
                    BISHOP_PST[pstRow][pstCol]
                }
                ChessPieceType.ROOK   -> {
                    val ownPawns = if (p.color == PieceColor.WHITE) wPawnFiles else bPawnFiles
                    val oppPawns = if (p.color == PieceColor.WHITE) bPawnFiles else wPawnFiles
                    val openBonus = when {
                        col !in ownPawns && col !in oppPawns -> 20
                        col !in ownPawns                     -> 10
                        else                                 ->  0
                    }
                    ROOK_PST[pstRow][pstCol] + openBonus
                }
                ChessPieceType.QUEEN  -> {
                    if (p.color == PieceColor.WHITE) whiteQueens++ else blackQueens++
                    QUEEN_PST[pstRow][pstCol]
                }
                ChessPieceType.KING   -> {
                    val endgame = whiteQueens == 0 && blackQueens == 0
                    if (endgame) KING_END_PST[pstRow][pstCol] else KING_MID_PST[pstRow][pstCol]
                }
            }
            val v = p.type.points + bonus
            score += if (p.color == PieceColor.WHITE) v else -v
        }

        // ── Structural bonuses ────────────────────────────────────────────────────
        if (whiteBishops >= 2) score += 30
        if (blackBishops >= 2) score -= 30

        // Doubled pawn penalty — O(8) using pre-collected column counts
        for (c in 0..7) {
            if (wPawnCols[c] > 1) score -= (wPawnCols[c] - 1) * 20
            if (bPawnCols[c] > 1) score += (bPawnCols[c] - 1) * 20
        }

        if (state.metadata["castleWK"] == true || state.metadata["castleWQ"] == true) score += 15
        if (state.metadata["castleBK"] == true || state.metadata["castleBQ"] == true) score -= 15

        return score
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    companion object {
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
