package com.mkdev.mkboardgames.challenges

import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.Piece
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import com.mkdev.mkboardgames.games.chess.ChessPiece
import com.mkdev.mkboardgames.games.chess.ChessPieceType
import com.mkdev.mkboardgames.games.chess.ChessRuleEngine

/**
 * Content validation for the ten custom challenge positions.
 */
object ChessPuzzleValidator {
    private val engine = ChessRuleEngine()

    fun validateAll(puzzles: List<ChessPuzzle> = ChessPuzzleData.all): List<String> {
        val issues = mutableListOf<String>()
        val expectedNumbers = (1..10).toList()
        if (puzzles.map { it.level } != expectedNumbers) {
            issues += "challenges must be present once each from 1 to 10"
        }
        if (ChessChallengeCatalogue.size != 10) {
            issues += "challenge catalogue must contain exactly 10 challenges"
        }
        if (ChessBeginnerChallenges.all.map { it.level } != expectedNumbers) {
            issues += "challenge descriptions must contain 1 to 10 once each"
        }
        if (ChessBeginnerChallenges.all.any { it.title != ChessChallengeCatalogue.titleFor(it.level) }) {
            issues += "challenge titles must match the challenge catalogue"
        }
        if (puzzles.any { it.fen.split(Regex("\\s+")).getOrNull(1) != "w" }) {
            issues += "all authored challenges must start with White to move"
        }
        puzzles.forEach { puzzle ->
            val state = parseFen(puzzle.fen)
            if (state.board.count { it != null } == 0) {
                issues += "challenge ${puzzle.level} has an empty position"
            }
            val kings = state.board.filterIsInstance<ChessPiece>()
                .filter { it.type == ChessPieceType.KING }
            if (kings.count { it.color == PieceColor.WHITE } != 1 ||
                kings.count { it.color == PieceColor.BLACK } != 1
            ) {
                issues += "challenge ${puzzle.level} must contain exactly one king per side"
            }
            if (engine.isInCheck(state, state.currentTurn.opponent())) {
                issues += "challenge ${puzzle.level} has the non-moving side in check"
            }
            if (state.get(Position(7, 4)) == null && puzzle.level == 1) {
                issues += "challenge ${puzzle.level} is missing the white king"
            }
            if (puzzle.recommendedMoves.isNotEmpty() &&
                puzzle.recommendedMoves.none { findMove(state, it) != null }
            ) {
                issues += "challenge ${puzzle.level} has no legal opening recommendation"
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