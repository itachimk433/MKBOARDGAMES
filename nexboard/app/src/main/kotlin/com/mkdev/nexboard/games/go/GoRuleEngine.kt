package com.mkdev.nexboard.games.go

import com.mkdev.nexboard.engine.*

const val GO_SIZE = 9
/** Sentinel Position used to represent a PASS move. */
val PASS_POS = Position(GO_SIZE, GO_SIZE)

data class GoStone(override val color: PieceColor) : Piece(color) {
    override fun symbol() = if (color == PieceColor.BLACK) "●" else "○"
    override fun value() = 100
}

/**
 * Go rule engine — Chinese area scoring, simple Ko, standard komi 6.5.
 *
 * Move encoding:
 *   Placement : from == to == the intersection Position
 *   Pass      : from == to == PASS_POS  (Position(size, size))
 *
 * GameState.metadata keys:
 *   "blackCaptures"      Int   — white stones captured by black
 *   "whiteCaptures"      Int   — black stones captured by white
 *   "koPoint"            Position — illegal placement target (simple Ko); PASS_POS if none
 *   "consecutivePasses"  Int   — increments on every PASS, resets on placement
 */
class GoRuleEngine(val size: Int = GO_SIZE) : RuleEngine {

    // ─── Index helpers ────────────────────────────────────────────────────────

    fun idx(pos: Position) = pos.row * size + pos.col
    fun idx(row: Int, col: Int) = row * size + col

    fun neighbors(pos: Position): List<Position> = listOf(
        Position(pos.row - 1, pos.col),
        Position(pos.row + 1, pos.col),
        Position(pos.row, pos.col - 1),
        Position(pos.row, pos.col + 1)
    ).filter { it.row in 0 until size && it.col in 0 until size }

    // ─── Group & liberty helpers ──────────────────────────────────────────────

    fun findGroup(board: Array<Piece?>, pos: Position): Set<Position> {
        val stone = board[idx(pos)] ?: return emptySet()
        val color = stone.color
        val group = LinkedHashSet<Position>()
        val queue = ArrayDeque<Position>()
        queue.add(pos); group.add(pos)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (nb in neighbors(cur)) {
                if (nb !in group && board[idx(nb)]?.color == color) {
                    group.add(nb); queue.add(nb)
                }
            }
        }
        return group
    }

    fun groupLiberties(board: Array<Piece?>, group: Set<Position>): Set<Position> {
        val libs = mutableSetOf<Position>()
        for (pos in group) {
            for (nb in neighbors(pos)) {
                if (board[idx(nb)] == null) libs.add(nb)
            }
        }
        return libs
    }

    // ─── Initial state ────────────────────────────────────────────────────────

    override fun initialState(): GameState = GameState(
        board          = arrayOfNulls(size * size),
        boardSize      = size,
        currentTurn    = PieceColor.BLACK,
        metadata       = mapOf(
            "blackCaptures"     to 0,
            "whiteCaptures"     to 0,
            "koPoint"           to PASS_POS,
            "consecutivePasses" to 0
        )
    )

    // ─── Move application ─────────────────────────────────────────────────────

    override fun applyMove(state: GameState, move: Move): GameState {
        val meta     = HashMap(state.metadata)
        val nextTurn = state.currentTurn.opponent()

        // ── PASS ──────────────────────────────────────────────────────────────
        if (move.to == PASS_POS) {
            val passes = (meta["consecutivePasses"] as? Int ?: 0) + 1
            meta["consecutivePasses"] = passes
            meta["koPoint"]           = PASS_POS
            val base = state.copy(
                currentTurn  = nextTurn,
                moveHistory  = state.moveHistory + move,
                metadata     = meta
            )
            return if (passes >= 2) {
                base.copy(status = scoredStatus(base.board, meta))
            } else base
        }

        // ── PLACEMENT ─────────────────────────────────────────────────────────
        meta["consecutivePasses"] = 0
        val board = state.board.copyOf()
        board[idx(move.to)] = GoStone(state.currentTurn)

        var bCaps = meta["blackCaptures"] as? Int ?: 0
        var wCaps = meta["whiteCaptures"] as? Int ?: 0
        var koPoint = PASS_POS

        // Capture opponent groups with no liberties
        val allCaptured = mutableListOf<Position>()
        for (nb in neighbors(move.to)) {
            val nbStone = board[idx(nb)] ?: continue
            if (nbStone.color != state.currentTurn) {
                val grp  = findGroup(board, nb)
                val libs = groupLiberties(board, grp)
                if (libs.isEmpty()) {
                    allCaptured.addAll(grp)
                    for (cap in grp) board[idx(cap)] = null
                    if (state.currentTurn == PieceColor.BLACK) bCaps += grp.size
                    else                                        wCaps += grp.size
                    // Ko: exactly one stone captured and not already set
                    if (grp.size == 1 && allCaptured.size == 1) koPoint = grp.first()
                }
            }
        }

        meta["blackCaptures"] = bCaps
        meta["whiteCaptures"] = wCaps
        meta["koPoint"]       = koPoint

        return GameState(
            board       = board,
            boardSize   = size,
            currentTurn = nextTurn,
            status      = GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + move,
            metadata    = meta
        )
    }

    // ─── Legality check ───────────────────────────────────────────────────────

    fun isLegal(state: GameState, pos: Position): Boolean {
        if (pos.row !in 0 until size || pos.col !in 0 until size) return false
        if (state.get(pos) != null) return false
        val koPoint = state.metadata["koPoint"] as? Position ?: PASS_POS
        if (koPoint != PASS_POS && pos == koPoint) return false

        val board = state.board.copyOf()
        board[idx(pos)] = GoStone(state.currentTurn)
        var captured = false
        for (nb in neighbors(pos)) {
            val nbStone = board[idx(nb)] ?: continue
            if (nbStone.color != state.currentTurn) {
                val grp = findGroup(board, nb)
                if (groupLiberties(board, grp).isEmpty()) {
                    for (cap in grp) board[idx(cap)] = null
                    captured = true
                }
            }
        }
        val myGrp = findGroup(board, pos)
        return captured || groupLiberties(board, myGrp).isNotEmpty()
    }

    private fun capturesFor(board: Array<Piece?>, pos: Position, color: PieceColor): List<Position> {
        val tmp = board.copyOf()
        tmp[idx(pos)] = GoStone(color)
        val result = mutableListOf<Position>()
        for (nb in neighbors(pos)) {
            val nbStone = tmp[idx(nb)] ?: continue
            if (nbStone.color != color) {
                val grp = findGroup(tmp, nb)
                if (groupLiberties(tmp, grp).isEmpty()) {
                    result.addAll(grp)
                    for (cap in grp) tmp[idx(cap)] = null
                }
            }
        }
        return result
    }

    // ─── Legal move generation ────────────────────────────────────────────────

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        if (!isLegal(state, position)) return emptyList()
        val caps = capturesFor(state.board, position, state.currentTurn)
        return listOf(Move(from = position, to = position, captures = caps))
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        val moves = mutableListOf<Move>()
        // Pass is always legal
        moves.add(Move(from = PASS_POS, to = PASS_POS))
        for (row in 0 until size) {
            for (col in 0 until size) {
                val pos = Position(row, col)
                if (!isLegal(state, pos)) continue
                val caps = capturesFor(state.board, pos, color)
                moves.add(Move(from = pos, to = pos, captures = caps))
            }
        }
        return moves
    }

    // ─── Scoring ─────────────────────────────────────────────────────────────

    /**
     * Chinese area scoring: territory + stones on board + captures.
     * Returns (blackScore, whiteScore) as Doubles (komi = 6.5).
     */
    fun scoreFor(board: Array<Piece?>, meta: Map<String, Any>): Pair<Double, Double> {
        val komi  = 6.5
        var black = (meta["blackCaptures"] as? Int ?: 0).toDouble()
        var white = (meta["whiteCaptures"] as? Int ?: 0).toDouble() + komi

        val visited = BooleanArray(size * size)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val i     = idx(row, col)
                val stone = board[i]
                if (stone != null) {
                    visited[i] = true
                    if (stone.color == PieceColor.BLACK) black++ else white++
                    continue
                }
                if (visited[i]) continue
                // Flood-fill empty region
                val region  = mutableSetOf<Position>()
                val borders = mutableSetOf<PieceColor>()
                val q       = ArrayDeque<Position>()
                val start   = Position(row, col)
                q.add(start); region.add(start); visited[i] = true
                while (q.isNotEmpty()) {
                    val cur = q.removeFirst()
                    for (nb in neighbors(cur)) {
                        val ni = idx(nb); val ns = board[ni]
                        if (ns != null) borders.add(ns.color)
                        else if (!visited[ni]) { visited[ni] = true; region.add(nb); q.add(nb) }
                    }
                }
                // Neutral (dame) if bordered by both colors — counts for nobody
                if (borders.size == 1) {
                    if (borders.first() == PieceColor.BLACK) black += region.size
                    else                                      white += region.size
                }
            }
        }
        return black to white
    }

    private fun scoredStatus(board: Array<Piece?>, meta: Map<String, Any>): GameStatus {
        val (b, w) = scoreFor(board, meta)
        return when {
            b > w  -> GameStatus.BLACK_WINS
            w > b  -> GameStatus.WHITE_WINS
            else   -> GameStatus.DRAW
        }
    }

    // ─── Game status ─────────────────────────────────────────────────────────

    override fun gameStatus(state: GameState) = state.status

    // ─── Static evaluation (from WHITE's perspective — positive = WHITE better) ─

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.WHITE_WINS) return  100_000
        if (state.status == GameStatus.DRAW)       return  0

        val board = state.board
        var score = 0

        // Stone count
        var bSt = 0; var wSt = 0
        for (p in board) when (p?.color) {
            PieceColor.BLACK -> bSt++
            PieceColor.WHITE -> wSt++
            null -> {}
        }
        score += (wSt - bSt) * 10

        // Captures
        val bCaps = state.metadata["blackCaptures"] as? Int ?: 0
        val wCaps = state.metadata["whiteCaptures"] as? Int ?: 0
        score += (wCaps - bCaps) * 12

        // Liberty count
        var bLibs = 0; var wLibs = 0
        val seen = mutableSetOf<Position>()
        for (row in 0 until size) {
            for (col in 0 until size) {
                val pos = Position(row, col)
                val stone = board[idx(row, col)] as? GoStone ?: continue
                if (pos in seen) continue
                val grp  = findGroup(board, pos)
                seen.addAll(grp)
                val libs = groupLiberties(board, grp).size
                if (stone.color == PieceColor.BLACK) bLibs += libs else wLibs += libs
            }
        }
        score += (wLibs - bLibs) * 4

        // Crude influence: empty points surrounded mostly by one color
        var bInf = 0; var wInf = 0
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (board[idx(row, col)] != null) continue
                var bAdj = 0; var wAdj = 0
                for (nb in neighbors(Position(row, col))) {
                    when (board[idx(nb)]?.color) {
                        PieceColor.BLACK -> bAdj++
                        PieceColor.WHITE -> wAdj++
                        null -> {}
                    }
                }
                if (wAdj > bAdj) wInf++ else if (bAdj > wAdj) bInf++
            }
        }
        score += (wInf - bInf) * 5

        return score
    }
}
