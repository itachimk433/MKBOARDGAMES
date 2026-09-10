package com.mkdev.mkboardgames.challenges

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessPieceType
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine

/**
 * Offline validation for the challenge catalogue.
 *
 * Keeping this independent from Android makes it useful both to unit tests
 * and to a future command-line content check in CI.
 */
object ChessPuzzleValidator {
    private val engine = ChessRuleEngine()

    fun validateAll(puzzles: List<ChessPuzzle> = ChessPuzzleData.all): List<String> {
        val issues = mutableListOf<String>()
        val levels = puzzles.map { it.level }
        if (levels != (1..101).toList()) {
            issues += "levels must be present once each from 1 to 101"
        }

        puzzles.forEach { puzzle ->
            if (puzzle.solutionLines.isEmpty()) {
                issues += "level ${puzzle.level} has no solution line"
                return@forEach
            }
            puzzle.solutionLines.forEachIndexed lineLoop@{ lineIndex, line ->
                val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.size < 2 || tokens.size % 2 != 0) {
                    issues += "level ${puzzle.level} line ${lineIndex + 1} has an incomplete move pair"
                    return@lineLoop
                }
                var state = parseFen(puzzle.fen)
                val playerColor = state.currentTurn.opponent()
                tokens.forEachIndexed moveLoop@{ moveIndex, token ->
                    val move = findMove(state, token)
                    if (move == null) {
                        issues += "level ${puzzle.level} line ${lineIndex + 1} has illegal move ${moveIndex + 1}: $token"
                        return@moveLoop
                    }
                    state = engine.applyMove(state, move)
                }
                val expectedStatus = if (playerColor == PieceColor.WHITE) {
                    GameStatus.WHITE_WINS
                } else {
                    GameStatus.BLACK_WINS
                }
                if (state.status != expectedStatus) {
                    issues += "level ${puzzle.level} line ${lineIndex + 1} does not end in checkmate"
                }
                val targetMoves = puzzle.objective.targetPlayerMoves
                if (targetMoves != null && targetMoves != tokens.size / 2) {
                    issues += "level ${puzzle.level} target move count does not match its authored line"
                }
            }
        }
        return issues
    }

    private fun findMove(state: GameState, uci: String) =
        if (uci.length < 4) {
            null
        } else {
            val from = position(uci.substring(0, 2))
            val to = position(uci.substring(2, 4))
            val promotion = uci.getOrNull(4)?.uppercaseChar()?.let {
                when (it) {
                    'Q' -> "QUEEN"
                    'R' -> "ROOK"
                    'B' -> "BISHOP"
                    'N' -> "KNIGHT"
                    else -> null
                }
            }
            if (from == null || to == null) null
            else engine.legalMovesFrom(state, from).firstOrNull {
                it.to == to && (promotion == null || it.promotionType == promotion)
            }
        }

    private fun parseFen(fen: String): GameState {
        val fields = fen.trim().split(Regex("\\s+"))
        val board = arrayOfNulls<Piece>(64)
        fields.firstOrNull()?.split("/")?.take(8)?.forEachIndexed { row, rank ->
            var col = 0
            rank.forEach { token ->
                when {
                    token.isDigit() -> col += token.digitToInt()
                    col < 8 -> {
                        val type = when (token.lowercaseChar()) {
                            'k' -> ChessPieceType.KING
                            'q' -> ChessPieceType.QUEEN
                            'r' -> ChessPieceType.ROOK
                            'b' -> ChessPieceType.BISHOP
                            'n' -> ChessPieceType.KNIGHT
                            'p' -> ChessPieceType.PAWN
                            else -> null
                        }
                        if (type != null) {
                            val color = if (token.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                            board[row * 8 + col] = ChessPiece(type, color)
                        }
                        col++
                    }
                }
            }
        }
        val castling = fields.getOrNull(2) ?: "-"
        val enPassant = fields.getOrNull(3)?.takeIf { it.length == 2 }?.let { it[0] - 'a' } ?: -1
        return GameState(
            board = board,
            currentTurn = if (fields.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE,
            metadata = mapOf(
                "castleWK" to castling.contains('K'),
                "castleWQ" to castling.contains('Q'),
                "castleBK" to castling.contains('k'),
                "castleBQ" to castling.contains('q'),
                "enPassant" to enPassant,
            ),
        )
    }

    private fun position(square: String): Position? {
        if (square.length != 2) return null
        val col = square[0] - 'a'
        val row = 8 - (square[1] - '0')
        return if (row in 0..7 && col in 0..7) Position(row, col) else null
    }
}