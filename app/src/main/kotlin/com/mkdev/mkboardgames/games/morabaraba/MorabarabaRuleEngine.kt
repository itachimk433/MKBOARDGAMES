package com.mkdev.mkboardgames.games.morabaraba

import com.mkdev.mkboardgames.engine.*
import com.mkdev.mkboardgames.games.morabaraba.MorabarabaBoard.META_B
import com.mkdev.mkboardgames.games.morabaraba.MorabarabaBoard.META_W

/**
 * Morabaraba rule engine — supports 6, 9, and 12-cow variants.
 *
 * The 9- and 12-cow variants use the full 24-position three-ring board.
 * The 6-cow variant uses the compact 9-position board. The topology is part
 * of the rules, not just a visual option, so every move and mill calculation
 * must use the active variant.
 *
 * Move encoding:
 *   Placement  → Move(from=PLACE, to=<node pos>, captures=[removedPos]?)
 *   Movement   → Move(from=<node pos>, to=<node pos>, captures=[removedPos]?)
 *
 * PLACE = Position(7,7) — outside the valid 7×7 board grid.
 *
 * Draw detection (movement phase only):
 *   If both players have ≥ 3 pieces and neither player can possibly form a new
 *   mill (all their remaining win lines are blocked by the opponent), the game
 *   ends as a draw immediately — the players would loop forever without progress.
 *   Additionally a 40-move (no-capture) counter triggers a draw, which catches
 *   edge cases the pattern check might miss.
 */
class MorabarabaRuleEngine(val pieceCount: Int = 12) : RuleEngine {

    init {
        require(pieceCount == 6 || pieceCount == 9 || pieceCount == 12) {
            "Morabaraba supports only 6, 9, or 12 cows"
        }
    }

    companion object {
        val PLACE = Position(7, 7)
        private const val NO_CAPTURE_DRAW_LIMIT = 40
        private const val META_NO_CAPTURE = "mora_no_cap"
    }

    override fun initialState(): GameState {
        val meta: Map<String, Any> = mapOf(META_W to 0, META_B to 0, META_NO_CAPTURE to 0)
        return GameState(board = arrayOfNulls(49), boardSize = 7,
            currentTurn = PieceColor.WHITE, metadata = meta)
    }

    // ─── Board topology ───────────────────────────────────────────────────────

    private fun activePos() =
        if (pieceCount == 6) MorabarabaBoard.SIMPLE_POSITIONS else MorabarabaBoard.POSITIONS

    private fun activeAdj() =
        if (pieceCount == 6) MorabarabaBoard.SIMPLE_ADJACENCY else MorabarabaBoard.ADJACENCY

    private fun activeMil() =
        if (pieceCount == 6) MorabarabaBoard.SIMPLE_MILLS else MorabarabaBoard.MILLS

    val activePositions: List<Position>  get() = activePos()
    val activeAdjacency: Array<IntArray> get() = activeAdj()
    val activeMills: List<IntArray>      get() = activeMil()

    // ─── Phase helpers ────────────────────────────────────────────────────────

    private fun whitePlaced(state: GameState) = (state.metadata[META_W] as? Int) ?: 0
    private fun blackPlaced(state: GameState) = (state.metadata[META_B] as? Int) ?: 0
    private fun noCaptureMoves(state: GameState) = (state.metadata[META_NO_CAPTURE] as? Int) ?: 0

    private fun inPlacementPhase(state: GameState, color: PieceColor): Boolean {
        val placed = if (color == PieceColor.WHITE) whitePlaced(state) else blackPlaced(state)
        return placed < pieceCount
    }

    fun isInPlacementPhase(state: GameState) =
        inPlacementPhase(state, state.currentTurn)

    private fun isFlying(state: GameState, color: PieceColor): Boolean {
        val wPlaced = whitePlaced(state); val bPlaced = blackPlaced(state)
        // Both players must have fully completed placement before flying is possible
        if (wPlaced < pieceCount || bPlaced < pieceCount) return false
        return piecesOf(state, color).size == 3
    }

    // ─── Board helpers ────────────────────────────────────────────────────────

    private fun piecesOf(state: GameState, color: PieceColor): List<Int> {
        val pos = activePos()
        return pos.indices.filter { (state.get(pos[it]) as? MorabarabaPiece)?.color == color }
    }

    private fun formsMillAt(board: Array<Piece?>, idx: Int, color: PieceColor): Boolean {
        val pos = activePos()
        return activeMil().any { mill ->
            idx in mill && mill.all { i ->
                (board[pos[i].row * 7 + pos[i].col] as? MorabarabaPiece)?.color == color
            }
        }
    }

    private fun inMillOn(board: Array<Piece?>, idx: Int, color: PieceColor): Boolean =
        activeMil().any { mill ->
            idx in mill && mill.all { i ->
                val p = activePos()[i]
                (board[p.row * 7 + p.col] as? MorabarabaPiece)?.color == color
            }
        }

    private fun removable(board: Array<Piece?>, attacker: PieceColor): List<Int> {
        val opp = attacker.opponent()
        val pos = activePos()
        val oppNodes = pos.indices.filter { (board[pos[it].row * 7 + pos[it].col] as? MorabarabaPiece)?.color == opp }
        val notInMill = oppNodes.filter { !inMillOn(board, it, opp) }
        return if (notInMill.isNotEmpty()) notInMill else oppNodes
    }

    // ─── Move generation ─────────────────────────────────────────────────────

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> =
        allLegalMoves(state, state.currentTurn).filter { it.from == position || it.from == PLACE }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        if (state.status != GameStatus.IN_PROGRESS) return emptyList()
        // AI search asks for both colors while the snapshot's currentTurn may
        // belong to the other side. Keep the state turn-aligned so callers can
        // safely apply a returned move and so placement is checked per color.
        val colorState = if (state.currentTurn == color) state else state.copy(currentTurn = color)
        return if (inPlacementPhase(colorState, color)) placementMoves(colorState, color)
        else movementMoves(colorState, color)
    }

    private fun placementMoves(state: GameState, color: PieceColor): List<Move> {
        val pos   = activePos()
        val moves = mutableListOf<Move>()
        for (idx in pos.indices) {
            val p = pos[idx]
            if (state.get(p) != null) continue
            val tempBoard = state.board.copyOf()
            tempBoard[p.row * 7 + p.col] = MorabarabaPiece(color)
            if (formsMillAt(tempBoard, idx, color)) {
                for (rmIdx in removable(tempBoard, color)) moves += Move(PLACE, p, listOf(pos[rmIdx]))
            } else {
                moves += Move(PLACE, p)
            }
        }
        return moves
    }

    private fun movementMoves(state: GameState, color: PieceColor): List<Move> {
        val pos   = activePos()
        val adj   = activeAdj()
        val moves = mutableListOf<Move>()
        val flying = isFlying(state, color)
        for (fromIdx in piecesOf(state, color)) {
            val from = pos[fromIdx]
            val dests = if (flying) pos.indices.filter { state.get(pos[it]) == null }
                        else adj[fromIdx].filter { state.get(pos[it]) == null }
            for (toIdx in dests) {
                val to = pos[toIdx]
                val tempBoard = state.board.copyOf()
                tempBoard[from.row * 7 + from.col] = null
                tempBoard[to.row * 7 + to.col]     = MorabarabaPiece(color)
                if (formsMillAt(tempBoard, toIdx, color)) {
                    for (rmIdx in removable(tempBoard, color)) moves += Move(from, to, listOf(pos[rmIdx]))
                } else {
                    moves += Move(from, to)
                }
            }
        }
        return moves
    }

    // ─── Apply move ───────────────────────────────────────────────────────────

    override fun applyMove(state: GameState, move: Move): GameState {
        // Morabaraba has mandatory captures only through mills, and a move can
        // also remove a specifically selected opponent piece. Never allow a
        // stale UI/AI move to mutate the board.
        if (state.status != GameStatus.IN_PROGRESS ||
            allLegalMoves(state, state.currentTurn).none { it == move }
        ) return state

        val color    = state.currentTurn
        val newBoard = state.board.copyOf()
        val meta     = state.metadata.toMutableMap()
        if (move.from == PLACE) {
            newBoard[move.to.row * 7 + move.to.col] = MorabarabaPiece(color)
            val key = if (color == PieceColor.WHITE) META_W else META_B
            meta[key] = ((meta[key] as? Int) ?: 0) + 1
        } else {
            newBoard[move.from.row * 7 + move.from.col] = null
            newBoard[move.to.row * 7 + move.to.col]     = MorabarabaPiece(color)
        }
        // Track moves without a capture for the no-progress draw rule
        val prevNoCapture = (meta[META_NO_CAPTURE] as? Int) ?: 0
        meta[META_NO_CAPTURE] = if (move.captures.isNotEmpty()) 0 else prevNoCapture + 1

        for (cap in move.captures) newBoard[cap.row * 7 + cap.col] = null
        val next = state.withBoard(newBoard, color.opponent(), move, meta = meta)
        return next.copy(status = gameStatus(next))
    }

    // ─── Game status ─────────────────────────────────────────────────────────

    override fun gameStatus(state: GameState): GameStatus {
        val wPlaced = whitePlaced(state)
        val bPlaced = blackPlaced(state)
        val wCount  = piecesOf(state, PieceColor.WHITE).size
        val bCount  = piecesOf(state, PieceColor.BLACK).size

        if (wPlaced >= pieceCount && bPlaced >= pieceCount) {
            if (wCount < 3) return GameStatus.BLACK_WINS
            if (bCount < 3) return GameStatus.WHITE_WINS
            if (allLegalMoves(state, state.currentTurn).isEmpty())
                return if (state.currentTurn == PieceColor.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS

            // ── Early draw checks (movement phase only) ──────────────────────

            // 1. No-capture counter exceeded
            if (noCaptureMoves(state) >= NO_CAPTURE_DRAW_LIMIT) return GameStatus.DRAW

            // 2. Neither player can ever form a mill — all mill lines are
            //    blocked (contain at least one piece of each color).
            //    We only declare this draw if BOTH players are in this state
            //    AND neither is flying (flying players can always reach any spot).
            val wFlying = wCount == 3 && wPlaced >= pieceCount
            val bFlying = bCount == 3 && bPlaced >= pieceCount
            if (!wFlying && !bFlying) {
                val canWhiteWin = canFormNewMill(state, PieceColor.WHITE)
                val canBlackWin = canFormNewMill(state, PieceColor.BLACK)
                if (!canWhiteWin && !canBlackWin) return GameStatus.DRAW
            }
        }
        return GameStatus.IN_PROGRESS
    }

    /**
     * Returns true if [color] still has at least one mill line where no
     * opponent piece is present (i.e., it is theoretically completable).
     * This is a necessary — not sufficient — condition for winning, but
     * it's a clean heuristic: if every mill line contains an opponent
     * piece, the player can never score another capture and the game is
     * effectively deadlocked.
     */
    private fun canFormNewMill(state: GameState, color: PieceColor): Boolean {
        val pos = activePos()
        val opp = color.opponent()
        return activeMil().any { mill ->
            // Mill is still achievable if it has no opponent pieces in it
            mill.none { i ->
                val p = pos[i]
                (state.board[p.row * 7 + p.col] as? MorabarabaPiece)?.color == opp
            }
        }
    }

    // ─── Evaluation ──────────────────────────────────────────────────────────

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.WHITE_WINS) return  100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW)       return 0

        val wPieces = piecesOf(state, PieceColor.WHITE)
        val bPieces = piecesOf(state, PieceColor.BLACK)
        var score = (wPieces.size - bPieces.size) * 200

        val mil = activeMil(); val pos = activePos()
        val wMills = mil.count { mill -> mill.all { i -> (state.get(pos[i]) as? MorabarabaPiece)?.color == PieceColor.WHITE } }
        val bMills = mil.count { mill -> mill.all { i -> (state.get(pos[i]) as? MorabarabaPiece)?.color == PieceColor.BLACK } }
        score += (wMills - bMills) * 150

        val wMob = allLegalMoves(state.copy(currentTurn = PieceColor.WHITE), PieceColor.WHITE).size
        val bMob = allLegalMoves(state.copy(currentTurn = PieceColor.BLACK), PieceColor.BLACK).size
        score += (wMob - bMob) * 10

        val midpoints = setOf(1,3,4,6,9,11,12,14,17,19,20,22)
        for (i in wPieces) score += if (i in midpoints) 30 else 10
        for (i in bPieces) score -= if (i in midpoints) 30 else 10
        return score
    }
}
