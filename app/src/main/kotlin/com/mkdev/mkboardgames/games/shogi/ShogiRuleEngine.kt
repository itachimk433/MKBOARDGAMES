package com.mkdev.mkboardgames.games.shogi

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position

/** Standard Shogi movement, promotion, capture, hand, and drop rules. */
class ShogiRuleEngine : com.mkdev.mkboardgames.engine.RuleEngine {
    override fun initialState() = ShogiSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = pieceAt(state, position) as? ShogiPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()

        return rawMovesFrom(state, position, piece)
            .filter { (pieceAt(state, it.to) as? ShogiPiece)?.type != ShogiPieceType.KING }
            .flatMap { move -> promotionVariants(piece, move) }
            .filter { move ->
                !isKingInCheck(applyMoveInternal(state, move), piece.color)
            }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val turnState = state.copy(currentTurn = color)
        return buildList {
            for (row in 0 until ShogiSetup.SIZE) {
                for (col in 0 until ShogiSetup.SIZE) {
                    addAll(legalMovesFrom(turnState, Position(row, col)))
                }
            }
            ShogiPieceType.entries.forEach { addAll(legalDropsFrom(turnState, it)) }
        }
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        require(state.status == GameStatus.IN_PROGRESS) {
            "Cannot apply a move after the game has ended"
        }
        require(move in allLegalMoves(state, state.currentTurn)) {
            "Illegal Shogi move"
        }
        val applied = applyMoveInternal(state, move)
        return applied.copy(
            moveHistory = state.moveHistory + move,
            status = gameStatus(applied.copy(moveHistory = state.moveHistory + move)),
        )
    }

    override fun gameStatus(state: GameState): GameStatus {
        val whiteKing = findKing(state, PieceColor.WHITE)
        val blackKing = findKing(state, PieceColor.BLACK)
        if (whiteKing == null) return GameStatus.BLACK_WINS
        if (blackKing == null) return GameStatus.WHITE_WINS

        // Most positions have a board move. Avoid generating every possible
        // hand drop (including pawn-drop-mate checks) just to answer the
        // common "is the game over?" question. Drops are only needed when
        // there are no legal board moves.
        if (hasAnyBoardLegalMove(state, state.currentTurn)) return GameStatus.IN_PROGRESS
        val hasLegalDrop = ShogiPieceType.entries.any { type ->
            legalDropsFrom(state.copy(currentTurn = state.currentTurn), type).isNotEmpty()
        }
        if (hasLegalDrop) return GameStatus.IN_PROGRESS

        // Shogi has no chess-style stalemate draw. A player with no legal
        // move loses whether or not their king is currently in check.
        return winnerFor(state.currentTurn.opponent())
    }

    /** Static evaluation from WHITE's perspective (used by generic callers). */
    override fun evaluate(state: GameState): Int = ShogiEvaluator.evaluate(state)

    // ------------------------------------------------------------------
    // Fast search-only API (used by ShogiSearch). These never mutate state
    // and are behaviorally equivalent to the public API, minus validation.
    // ------------------------------------------------------------------

    /**
     * Apply a move that is already known to be legal. No legality check,
     * no status computation. The caller (search) detects terminal states by
     * an empty legal-move list at the node.
     */
    fun applyForSearch(state: GameState, move: Move): GameState =
        applyMoveInternal(state, move).copy(moveHistory = emptyList())

    /** Full legal move list for the side to move, without Move-list validation. */
    fun searchMoves(state: GameState): List<Move> =
        allLegalMoves(state, state.currentTurn)

    /** True if [color]'s king is currently attacked. */
    fun inCheck(state: GameState, color: PieceColor): Boolean = isKingInCheck(state, color)

    /** Does this move give check to the opponent of the mover? */
    fun givesCheck(stateBefore: GameState, move: Move): Boolean {
        val after = applyMoveInternal(stateBefore, move)
        return isKingInCheck(after, stateBefore.currentTurn.opponent())
    }

    /** Does the board of [state] still contain both kings? */
    fun bothKingsPresent(state: GameState): Boolean =
        findKing(state, PieceColor.WHITE) != null && findKing(state, PieceColor.BLACK) != null

    fun winnerStatusIfKingMissing(state: GameState): GameStatus? {
        if (findKing(state, PieceColor.WHITE) == null) return GameStatus.BLACK_WINS
        if (findKing(state, PieceColor.BLACK) == null) return GameStatus.WHITE_WINS
        return null
    }

    private fun rawMovesFrom(
        state: GameState,
        from: Position,
        piece: ShogiPiece,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        fun add(to: Position) {
            if (!onBoard(to)) return
            val target = pieceAt(state, to)
            if (target?.color == piece.color) return
            moves += Move(
                from = from,
                to = to,
                captures = if (target == null) emptyList() else listOf(to),
            )
        }
        fun slide(directions: List<Pair<Int, Int>>) {
            for ((dr, dc) in directions) {
                var row = from.row + dr
                var col = from.col + dc
                while (onBoard(Position(row, col))) {
                    val target = pieceAt(state, Position(row, col))
                    if (target == null) {
                        moves += Move(from, Position(row, col))
                    } else {
                        if (target.color != piece.color) moves += Move(from, Position(row, col), listOf(Position(row, col)))
                        break
                    }
                    row += dr
                    col += dc
                }
            }
        }

        val forward = if (piece.color == PieceColor.WHITE) -1 else 1
        when (piece.type) {
            ShogiPieceType.PAWN -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else add(Position(from.row + forward, from.col))
            }
            ShogiPieceType.LANCE -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else slide(listOf(forward to 0))
            }
            ShogiPieceType.KNIGHT -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else {
                    add(Position(from.row + forward * 2, from.col - 1))
                    add(Position(from.row + forward * 2, from.col + 1))
                }
            }
            ShogiPieceType.SILVER -> {
                if (piece.promoted) goldSteps(forward, from, ::add)
                else {
                    add(Position(from.row + forward, from.col))
                    add(Position(from.row + forward, from.col - 1))
                    add(Position(from.row + forward, from.col + 1))
                    add(Position(from.row - forward, from.col - 1))
                    add(Position(from.row - forward, from.col + 1))
                }
            }
            ShogiPieceType.GOLD -> goldSteps(forward, from, ::add)
            ShogiPieceType.KING -> {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr != 0 || dc != 0) add(Position(from.row + dr, from.col + dc))
                }
            }
            ShogiPieceType.BISHOP -> {
                slide(diagonalDirections)
                if (piece.promoted) orthogonalDirections.forEach { (dr, dc) ->
                    add(Position(from.row + dr, from.col + dc))
                }
            }
            ShogiPieceType.ROOK -> {
                slide(orthogonalDirections)
                if (piece.promoted) diagonalDirections.forEach { (dr, dc) ->
                    add(Position(from.row + dr, from.col + dc))
                }
            }
        }
        return moves
    }

    private fun goldSteps(
        forward: Int,
        from: Position,
        add: (Position) -> Unit,
    ) {
        listOf(
            forward to 0,
            forward to -1,
            forward to 1,
            0 to -1,
            0 to 1,
            -forward to 0,
        ).forEach { (dr, dc) -> add(Position(from.row + dr, from.col + dc)) }
    }

    private fun promotionVariants(piece: ShogiPiece, move: Move): List<Move> {
        if (piece.promoted || !canPromote(piece.type)) return listOf(move)
        val forced = when (piece.type) {
            ShogiPieceType.PAWN, ShogiPieceType.LANCE ->
                move.to.row == promotionLastRank(piece.color)
            ShogiPieceType.KNIGHT ->
                move.to.row == promotionLastRank(piece.color) ||
                    move.to.row == promotionLastRank(piece.color) + if (piece.color == PieceColor.WHITE) 1 else -1
            else -> false
        }
        val mayPromote = inPromotionZone(move.from, piece.color) ||
            inPromotionZone(move.to, piece.color)
        if (forced) {
            return listOf(move.copy(
                promotionType = piece.type.name,
                metadata = move.metadata + ("promote" to true),
            ))
        }
        if (!mayPromote) return listOf(move)
        return listOf(
            move,
            move.copy(
                promotionType = piece.type.name,
                metadata = move.metadata + ("promote" to true),
            ),
        )
    }

    private fun applyMoveInternal(state: GameState, move: Move): GameState {
        val board = state.board.copyOf()
        val hands = state.hands.mapValues { it.value.toMutableList() }.toMutableMap()
        val dropType = dropType(move)
        if (dropType != null) {
            val hand = hands[state.currentTurn].orEmpty().toMutableList()
            val handIndex = hand.indexOfFirst { it is ShogiPiece && it.type == dropType }
            if (handIndex < 0 || pieceAt(state, move.to) != null) return state
            hand.removeAt(handIndex)
            hands[state.currentTurn] = hand
            board[index(move.to)] = ShogiPiece(dropType, state.currentTurn)
        } else {
            val piece = board.getOrNull(index(move.from)) as? ShogiPiece ?: return state
            move.captures.forEach { capture ->
                val captured = board.getOrNull(index(capture)) as? ShogiPiece
                board[index(capture)] = null
                if (captured != null) {
                    val hand = hands[state.currentTurn].orEmpty().toMutableList()
                    hand += captured.copy(color = state.currentTurn, promoted = false)
                    hands[state.currentTurn] = hand
                }
            }
            val promoted = move.promotionType != null || move.metadata["promote"] == true
            board[index(move.to)] = if (promoted) piece.copy(promoted = true) else piece
            board[index(move.from)] = null
        }
        return state.copy(
            board = board,
            currentTurn = state.currentTurn.opponent(),
            status = GameStatus.IN_PROGRESS,
            hands = hands.mapValues { it.value.toList() },
        )
    }

    /** Legal drops for a held piece type, exposed to the hand UI. */
    fun legalDropsFrom(
        state: GameState,
        type: ShogiPieceType,
        checkPawnDropMate: Boolean = true,
    ): List<Move> {
        val color = state.currentTurn
        if (state.hands[color].orEmpty().none { it is ShogiPiece && it.type == type }) {
            return emptyList()
        }
        val moves = mutableListOf<Move>()
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val to = Position(row, col)
                if (pieceAt(state, to) != null) continue
                if (type == ShogiPieceType.PAWN && row == promotionLastRank(color)) continue
                if (type == ShogiPieceType.LANCE && row == promotionLastRank(color)) continue
                if (type == ShogiPieceType.KNIGHT &&
                    (row == promotionLastRank(color) ||
                        row == promotionLastRank(color) + if (color == PieceColor.WHITE) 1 else -1)
                ) continue
                if (type == ShogiPieceType.PAWN && hasUnpromotedPawnOnFile(state, color, col)) continue

                val move = Move(
                    from = dropPosition(type),
                    to = to,
                    metadata = mapOf("drop" to type.name),
                )
                // A drop must not expose the dropping player's king. This is
                // especially important while escaping check: the old code
                // exposed pseudo-legal drops to both the UI and the AI.
                val next = applyMoveInternal(state, move)
                if (isKingInCheck(next, color)) continue
                if (checkPawnDropMate && type == ShogiPieceType.PAWN &&
                    isPawnDropMate(state, move)
                ) continue
                moves += move
            }
        }
        return moves
    }

    private fun isPawnDropMate(state: GameState, move: Move): Boolean {
        val next = applyMoveInternal(state, move)
        val opponent = state.currentTurn.opponent()
        return isKingInCheck(next, opponent) &&
            boardLegalMoves(next, opponent).isEmpty() &&
            ShogiPieceType.entries.all { type ->
                legalDropsFrom(next.copy(currentTurn = opponent), type, checkPawnDropMate = false)
                    .isEmpty()
            }
    }

    private fun boardLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val turnState = state.copy(currentTurn = color)
        return buildList {
            for (row in 0 until ShogiSetup.SIZE) {
                for (col in 0 until ShogiSetup.SIZE) {
                    addAll(legalMovesFrom(turnState, Position(row, col)))
                }
            }
        }
    }

    private fun hasAnyBoardLegalMove(state: GameState, color: PieceColor): Boolean {
        val turnState = state.copy(currentTurn = color)
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                if (legalMovesFrom(turnState, Position(row, col)).isNotEmpty()) return true
            }
        }
        return false
    }

    private fun hasUnpromotedPawnOnFile(state: GameState, color: PieceColor, col: Int): Boolean =
        (0 until ShogiSetup.SIZE).any { row ->
            val piece = pieceAt(state, Position(row, col))
            piece is ShogiPiece &&
                piece.color == color &&
                piece.type == ShogiPieceType.PAWN &&
                !piece.promoted
        }

    private fun dropPosition(type: ShogiPieceType) = Position(-1, type.ordinal)

    private fun dropType(move: Move): ShogiPieceType? =
        (move.metadata["drop"] as? String)?.let { name ->
            runCatching { ShogiPieceType.valueOf(name) }.getOrNull()
        }

    private fun isKingInCheck(state: GameState, color: PieceColor): Boolean {
        val king = findKing(state, color) ?: return true
        return isSquareAttackedBy(state, king, color.opponent())
    }

    /**
     * Reverse attack test: is [target] attacked by any piece of [attacker]?
     * Walking outward from the target avoids generating every enemy move.
     */
    private fun isSquareAttackedBy(
        state: GameState,
        target: Position,
        attacker: PieceColor,
    ): Boolean {
        val fwd = if (attacker == PieceColor.WHITE) -1 else 1

        fun stepAttacker(dr: Int, dc: Int, matches: (ShogiPiece) -> Boolean): Boolean {
            val p = pieceAt(state, Position(target.row - dr, target.col - dc)) ?: return false
            return p.color == attacker && matches(p)
        }

        fun goldLike(p: ShogiPiece) = p.type == ShogiPieceType.GOLD ||
            (p.promoted && (p.type == ShogiPieceType.PAWN || p.type == ShogiPieceType.LANCE ||
                p.type == ShogiPieceType.KNIGHT || p.type == ShogiPieceType.SILVER))

        // Gold-like steps: forward, forward-left, forward-right, left, right, back.
        val goldSteps = arrayOf(fwd to 0, fwd to -1, fwd to 1, 0 to -1, 0 to 1, -fwd to 0)
        for ((dr, dc) in goldSteps) {
            if (stepAttacker(dr, dc) { goldLike(it) }) return true
        }
        // Pawn (unpromoted): forward one.
        if (stepAttacker(fwd, 0) { it.type == ShogiPieceType.PAWN && !it.promoted }) return true
        // Silver (unpromoted): forward 3, back diagonals.
        for ((dr, dc) in arrayOf(fwd to 0, fwd to -1, fwd to 1, -fwd to -1, -fwd to 1)) {
            if (stepAttacker(dr, dc) { it.type == ShogiPieceType.SILVER && !it.promoted }) return true
        }
        // Knight (unpromoted).
        for (dc in intArrayOf(-1, 1)) {
            if (stepAttacker(fwd * 2, dc) { it.type == ShogiPieceType.KNIGHT && !it.promoted }) return true
        }
        // King steps.
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            if (stepAttacker(dr, dc) { it.type == ShogiPieceType.KING }) return true
        }
        // Promoted rook: diagonal one-steps; promoted bishop: orthogonal one-steps.
        for ((dr, dc) in diagonalDirections) {
            if (stepAttacker(dr, dc) { it.type == ShogiPieceType.ROOK && it.promoted }) return true
        }
        for ((dr, dc) in orthogonalDirections) {
            if (stepAttacker(dr, dc) { it.type == ShogiPieceType.BISHOP && it.promoted }) return true
        }

        // Sliders: walk each ray from the target until a piece is hit.
        for ((dr, dc) in orthogonalDirections) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0 until ShogiSetup.SIZE && c in 0 until ShogiSetup.SIZE) {
                val p = state.board[r * ShogiSetup.SIZE + c] as? ShogiPiece
                if (p != null) {
                    if (p.color == attacker) {
                        if (p.type == ShogiPieceType.ROOK) return true
                        if (p.type == ShogiPieceType.LANCE && !p.promoted &&
                            dc == 0 && dr == -fwd
                        ) return true
                    }
                    break
                }
                r += dr
                c += dc
            }
        }
        for ((dr, dc) in diagonalDirections) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0 until ShogiSetup.SIZE && c in 0 until ShogiSetup.SIZE) {
                val p = state.board[r * ShogiSetup.SIZE + c] as? ShogiPiece
                if (p != null) {
                    if (p.color == attacker && p.type == ShogiPieceType.BISHOP) return true
                    break
                }
                r += dr
                c += dc
            }
        }
        return false
    }

    private fun findKing(state: GameState, color: PieceColor): Position? {
        for (row in 0 until ShogiSetup.SIZE) {
            for (col in 0 until ShogiSetup.SIZE) {
                val piece = pieceAt(state, Position(row, col)) as? ShogiPiece ?: continue
                if (piece.color == color && piece.type == ShogiPieceType.KING) {
                    return Position(row, col)
                }
            }
        }
        return null
    }

    private fun pieceAt(state: GameState, position: Position): ShogiPiece? =
        if (onBoard(position)) state.board[index(position)] as? ShogiPiece else null

    private fun index(position: Position) = position.row * ShogiSetup.SIZE + position.col
    private fun onBoard(position: Position) =
        position.row in 0 until ShogiSetup.SIZE && position.col in 0 until ShogiSetup.SIZE

    private fun canPromote(type: ShogiPieceType) =
        type != ShogiPieceType.KING && type != ShogiPieceType.GOLD

    private fun promotionLastRank(color: PieceColor) =
        if (color == PieceColor.WHITE) 0 else ShogiSetup.SIZE - 1

    private fun inPromotionZone(position: Position, color: PieceColor) =
        if (color == PieceColor.WHITE) position.row <= 2 else position.row >= 6

    private fun winnerFor(color: PieceColor) =
        if (color == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS

    private val orthogonalDirections = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
    )
    private val diagonalDirections = listOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
    )
}