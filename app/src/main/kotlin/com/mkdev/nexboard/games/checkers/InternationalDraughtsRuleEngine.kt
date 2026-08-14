package com.mkdev.nexboard.games.checkers

import com.mkdev.nexboard.engine.GameState
import com.mkdev.nexboard.engine.GameStatus
import com.mkdev.nexboard.engine.Move
import com.mkdev.nexboard.engine.Piece
import com.mkdev.nexboard.engine.PieceColor
import com.mkdev.nexboard.engine.Position
import com.mkdev.nexboard.engine.RuleEngine

/**
 * International Draughts (also called Polish draughts):
 *
 * - 10×10 board with twenty men per player.
 * - Captures are compulsory.
 * - A capture sequence must take the greatest possible number of pieces.
 * - If capture counts tie, the sequence taking the most kings wins.
 * - Men capture forwards or backwards.
 * - Kings are "flying" kings: they move any distance diagonally and may land
 *   on any empty square beyond the piece they capture.
 */
class InternationalDraughtsRuleEngine : RuleEngine {

    private data class CaptureOption(
        val landing: Position,
        val captured: Position,
        val capturedPiece: CheckersPiece,
        val resultingPiece: CheckersPiece
    )

    private data class CapturePath(
        val landing: Position,
        val captures: List<Position>,
        val kingCaptures: Int,
        val promoted: Boolean
    )

    override fun initialState(): GameState = InternationalDraughtsSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = state.get(position) as? CheckersPiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()

        val captureMoves = allCaptureMoves(state, state.currentTurn)
        return if (captureMoves.isNotEmpty()) {
            captureMoves.filter { it.from == position }
        } else {
            quietMovesFrom(state, position, piece)
        }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val captures = mutableListOf<Move>()
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val position = Position(row, col)
                val piece = state.get(position) as? CheckersPiece ?: continue
                if (piece.color == color) {
                    captures += captureMovesFrom(state.board, position, piece)
                }
            }
        }

        if (captures.isNotEmpty()) {
            return selectMaximumCaptures(captures)
        }

        val quiet = mutableListOf<Move>()
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val position = Position(row, col)
                val piece = state.get(position) as? CheckersPiece ?: continue
                if (piece.color == color) {
                    quiet += quietMovesFrom(state, position, piece)
                }
            }
        }
        return quiet
    }

    private fun selectMaximumCaptures(moves: List<Move>): List<Move> {
        val greatestCount = moves.maxOf { it.captures.size }
        val longest = moves.filter { it.captures.size == greatestCount }
        val greatestKingCount = longest.maxOf {
            (it.metadata[KING_CAPTURES_KEY] as? Int) ?: 0
        }
        return longest.filter {
            ((it.metadata[KING_CAPTURES_KEY] as? Int) ?: 0) == greatestKingCount
        }
    }

    private fun allCaptureMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val position = Position(row, col)
                val piece = state.get(position) as? CheckersPiece ?: continue
                if (piece.color == color) {
                    moves += captureMovesFrom(state.board, position, piece)
                }
            }
        }
        return if (moves.isEmpty()) emptyList() else selectMaximumCaptures(moves)
    }

    private fun captureMovesFrom(
        board: Array<Piece?>,
        from: Position,
        piece: CheckersPiece
    ): List<Move> {
        return capturePaths(
            board = board,
            position = from,
            piece = piece,
            origin = from,
            captures = emptyList(),
            kingCaptures = 0,
            promoted = false
        ).map { path ->
            Move(
                from = from,
                to = path.landing,
                captures = path.captures,
                promotionType = if (path.promoted) CheckersPieceType.KING.name else null,
                metadata = mapOf(KING_CAPTURES_KEY to path.kingCaptures)
            )
        }
    }

    private fun capturePaths(
        board: Array<Piece?>,
        position: Position,
        piece: CheckersPiece,
        origin: Position,
        captures: List<Position>,
        kingCaptures: Int,
        promoted: Boolean
    ): List<CapturePath> {
        val options = captureOptions(board, position, piece)
        if (options.isEmpty()) {
            return if (captures.isEmpty()) emptyList()
            else listOf(CapturePath(position, captures, kingCaptures, promoted))
        }

        val paths = mutableListOf<CapturePath>()
        for (option in options) {
            val nextBoard = board.copyOf()
            nextBoard[position.row * BOARD_SIZE + position.col] = null
            nextBoard[option.captured.row * BOARD_SIZE + option.captured.col] = null
            nextBoard[option.landing.row * BOARD_SIZE + option.landing.col] = option.resultingPiece

            val isPromotion = !piece.isKing && option.resultingPiece.isKing
            paths += capturePaths(
                board = nextBoard,
                position = option.landing,
                piece = option.resultingPiece,
                origin = origin,
                captures = captures + option.captured,
                kingCaptures = kingCaptures + if (option.capturedPiece.isKing) 1 else 0,
                promoted = promoted || isPromotion
            )
        }
        return paths
    }

    private fun captureOptions(
        board: Array<Piece?>,
        position: Position,
        piece: CheckersPiece
    ): List<CaptureOption> {
        val options = mutableListOf<CaptureOption>()
        for (direction in DIAGONALS) {
            if (!piece.isKing) {
                val capturedPosition = position + direction
                val landing = Position(
                    position.row + direction.row * 2,
                    position.col + direction.col * 2
                )
                if (!capturedPosition.isValid(BOARD_SIZE) || !landing.isValid(BOARD_SIZE)) continue
                val captured = board[capturedPosition.row * BOARD_SIZE + capturedPosition.col]
                    as? CheckersPiece ?: continue
                if (captured.color == piece.color) continue
                if (board[landing.row * BOARD_SIZE + landing.col] != null) continue

                options += CaptureOption(
                    landing = landing,
                    captured = capturedPosition,
                    capturedPiece = captured,
                    resultingPiece = promote(piece, landing)
                )
                continue
            }

            var row = position.row + direction.row
            var col = position.col + direction.col
            while (Position(row, col).isValid(BOARD_SIZE) &&
                board[row * BOARD_SIZE + col] == null
            ) {
                row += direction.row
                col += direction.col
            }
            if (!Position(row, col).isValid(BOARD_SIZE)) continue

            val capturedPosition = Position(row, col)
            val captured = board[row * BOARD_SIZE + col] as? CheckersPiece ?: continue
            if (captured.color == piece.color) continue

            row += direction.row
            col += direction.col
            while (Position(row, col).isValid(BOARD_SIZE) &&
                board[row * BOARD_SIZE + col] == null
            ) {
                options += CaptureOption(
                    landing = Position(row, col),
                    captured = capturedPosition,
                    capturedPiece = captured,
                    resultingPiece = piece
                )
                row += direction.row
                col += direction.col
            }
        }
        return options
    }

    private fun quietMovesFrom(
        state: GameState,
        from: Position,
        piece: CheckersPiece
    ): List<Move> {
        val moves = mutableListOf<Move>()
        for (direction in moveDirections(piece)) {
            var row = from.row + direction.row
            var col = from.col + direction.col
            while (Position(row, col).isValid(BOARD_SIZE) &&
                state.get(row, col) == null
            ) {
                moves += Move(from, Position(row, col))
                if (!piece.isKing) break
                row += direction.row
                col += direction.col
            }
        }
        return moves
    }

    override fun applyMove(state: GameState, move: Move): GameState {
        val piece = state.get(move.from) as? CheckersPiece ?: return state
        val newBoard = state.board.copyOf()

        for (captured in move.captures) {
            newBoard[captured.row * BOARD_SIZE + captured.col] = null
        }
        newBoard[move.from.row * BOARD_SIZE + move.from.col] = null

        val movedPiece = if (piece.isKing || move.promotionType == CheckersPieceType.KING.name) {
            CheckersPiece(CheckersPieceType.KING, piece.color)
        } else {
            piece
        }
        newBoard[move.to.row * BOARD_SIZE + move.to.col] = movedPiece

        val next = state.withBoard(newBoard, piece.color.opponent(), move)
        return next.copy(status = gameStatus(next))
    }

    override fun gameStatus(state: GameState): GameStatus {
        val hasWhite = state.board.any { (it as? CheckersPiece)?.color == PieceColor.WHITE }
        val hasBlack = state.board.any { (it as? CheckersPiece)?.color == PieceColor.BLACK }
        if (!hasWhite) return GameStatus.BLACK_WINS
        if (!hasBlack) return GameStatus.WHITE_WINS
        if (allLegalMoves(state, state.currentTurn).isEmpty()) {
            return if (state.currentTurn == PieceColor.WHITE) {
                GameStatus.BLACK_WINS
            } else {
                GameStatus.WHITE_WINS
            }
        }
        return GameStatus.IN_PROGRESS
    }

    override fun evaluate(state: GameState): Int {
        if (state.status == GameStatus.WHITE_WINS) return 100_000
        if (state.status == GameStatus.BLACK_WINS) return -100_000
        if (state.status == GameStatus.DRAW) return 0

        var score = 0
        var whitePieces = 0
        var blackPieces = 0
        for (index in state.board.indices) {
            val piece = state.board[index] as? CheckersPiece ?: continue
            val row = index / BOARD_SIZE
            val col = index % BOARD_SIZE
            val material = if (piece.isKing) 300 else 100
            val centerDistance = kotlin.math.abs(4 - row) + kotlin.math.abs(4 - col)
            val positional = if (piece.isKing) {
                (12 - centerDistance) * 12
            } else if (piece.color == PieceColor.WHITE) {
                (9 - row) * 8 + if (row == 9) 20 else 0
            } else {
                row * 8 + if (row == 0) 20 else 0
            }
            val pieceScore = material + positional - if (col == 0 || col == 9) 8 else 0
            if (piece.color == PieceColor.WHITE) {
                score += pieceScore
                whitePieces++
            } else {
                score -= pieceScore
                blackPieces++
            }
        }

        score += (whitePieces - blackPieces) * 15
        val currentCaptures = allLegalMoves(state, state.currentTurn).count { it.isCapture }
        val opponentCaptures = allLegalMoves(state, state.currentTurn.opponent()).count { it.isCapture }
        val captureBonus = (currentCaptures - opponentCaptures) * 8
        return score + if (state.currentTurn == PieceColor.WHITE) captureBonus else -captureBonus
    }

    private fun moveDirections(piece: CheckersPiece): List<Position> {
        if (piece.isKing) return DIAGONALS
        return if (piece.color == PieceColor.WHITE) WHITE_FORWARD else BLACK_FORWARD
    }

    private fun promote(piece: CheckersPiece, landing: Position): CheckersPiece {
        if (piece.isKing) return piece
        val reached = (piece.color == PieceColor.WHITE && landing.row == 0) ||
            (piece.color == PieceColor.BLACK && landing.row == BOARD_SIZE - 1)
        return if (reached) {
            CheckersPiece(CheckersPieceType.KING, piece.color)
        } else {
            piece
        }
    }

    companion object {
        const val BOARD_SIZE = InternationalDraughtsSetup.BOARD_SIZE
        const val KING_CAPTURES_KEY = "kingCaptures"
        private val DIAGONALS = listOf(
            Position(-1, -1), Position(-1, 1),
            Position(1, -1), Position(1, 1)
        )
        private val WHITE_FORWARD = listOf(Position(-1, -1), Position(-1, 1))
        private val BLACK_FORWARD = listOf(Position(1, -1), Position(1, 1))
    }
}