package com.mkdev.nexboard.games.foxandgeese

import com.mkdev.nexboard.engine.GameState
import com.mkdev.nexboard.engine.GameStatus
import com.mkdev.nexboard.engine.Move
import com.mkdev.nexboard.engine.Piece
import com.mkdev.nexboard.engine.PieceColor
import com.mkdev.nexboard.engine.Position
import com.mkdev.nexboard.engine.RuleEngine

/**
 * Traditional 33-point Fox and Geese:
 * - the fox moves along any connected line in any direction;
 * - it may jump over an adjacent goose into an empty point and capture it;
 * - the geese move one point forward toward the fox and never capture;
 * - the fox wins when the geese are gone or cannot move, while the geese win
 *   when the fox is trapped.
 */
class FoxAndGeeseRuleEngine : RuleEngine {

    private val moveDirs = listOf(
        Position(-1, -1), Position(-1, 0), Position(-1, 1),
        Position(0, -1),                    Position(0, 1),
        Position(1, -1),  Position(1, 0),  Position(1, 1)
    )

    override fun initialState(): GameState = FoxAndGeeseSetup.initialState()

    override fun legalMovesFrom(state: GameState, position: Position): List<Move> {
        val piece = state.get(position) as? FoxAndGeesePiece ?: return emptyList()
        if (piece.color != state.currentTurn) return emptyList()

        return when (piece.type) {
            FoxAndGeesePieceType.FOX -> foxMoves(state, position)
            FoxAndGeesePieceType.GOOSE -> gooseMoves(state, position)
        }
    }

    override fun allLegalMoves(state: GameState, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0 until state.boardSize) {
            for (col in 0 until state.boardSize) {
                val pos = Position(row, col)
                val piece = state.get(pos) as? FoxAndGeesePiece ?: continue
                if (piece.color != color) continue
                moves += when (piece.type) {
                    FoxAndGeesePieceType.FOX -> foxMoves(state, pos)
                    FoxAndGeesePieceType.GOOSE -> gooseMoves(state, pos)
                }
            }
        }
        return moves.sortedWith(
            compareByDescending<Move> { it.captures.size }
                .thenBy { it.from.row }
                .thenBy { it.from.col }
        )
    }

    private fun foxMoves(state: GameState, from: Position): List<Move> {
        val quiet = moveDirs.mapNotNull { dir ->
            val to = from + dir
            if (isPlayable(to) && state.get(to) == null) Move(from, to)
            else null
        }

        val jumps = mutableListOf<Move>()
        collectFoxJumps(state.board, state.boardSize, from, from, emptyList(), jumps)
        return quiet + jumps
    }

    /**
     * Generate complete multi-jump choices.  Each recursive branch moves a
     * temporary fox so a captured goose cannot be used twice in one turn.
     */
    private fun collectFoxJumps(
        board: Array<Piece?>,
        boardSize: Int,
        start: Position,
        current: Position,
        captured: List<Position>,
        result: MutableList<Move>
    ) {
        var foundJump = false
        for (dir in moveDirs) {
            val over = current + dir
            val landing = current + Position(dir.row * 2, dir.col * 2)
            if (!isPlayable(over) || !isPlayable(landing)) continue
            val jumped = board[indexOf(over, boardSize)] as? FoxAndGeesePiece ?: continue
            if (jumped.type != FoxAndGeesePieceType.GOOSE) continue
            if (board[indexOf(landing, boardSize)] != null) continue

            foundJump = true
            val nextBoard = board.copyOf()
            nextBoard[indexOf(current, boardSize)] = null
            nextBoard[indexOf(over, boardSize)] = null
            nextBoard[indexOf(landing, boardSize)] =
                FoxAndGeesePiece(FoxAndGeesePieceType.FOX, PieceColor.WHITE)
            collectFoxJumps(
                nextBoard,
                boardSize,
                start,
                landing,
                captured + over,
                result
            )
        }

        if (!foundJump && captured.isNotEmpty()) {
            result += Move(start, current, captures = captured)
        }
    }

    private fun gooseMoves(state: GameState, from: Position): List<Move> =
        listOf(Position(-1, -1), Position(-1, 0), Position(-1, 1)).mapNotNull { dir ->
            val to = from + dir
            if (isPlayable(to) && state.get(to) == null) Move(from, to)
            else null
        }

    override fun applyMove(state: GameState, move: Move): GameState {
        val nextBoard = state.board.copyOf()
        val movingPiece = nextBoard[indexOf(move.from, state.boardSize)] ?: return state
        nextBoard[indexOf(move.from, state.boardSize)] = null
        for (capture in move.captures) nextBoard[indexOf(capture, state.boardSize)] = null
        nextBoard[indexOf(move.to, state.boardSize)] = movingPiece

        val nextTurn = state.currentTurn.opponent()
        val next = state.copy(
            board = nextBoard,
            currentTurn = nextTurn,
            status = GameStatus.IN_PROGRESS,
            moveHistory = state.moveHistory + move
        )
        return next.copy(status = gameStatus(next))
    }

    override fun gameStatus(state: GameState): GameStatus {
        val fox = findFox(state)
        val geese = countGeese(state)
        if (fox == null || geese == 0) return GameStatus.WHITE_WINS
        if (allLegalMoves(state, PieceColor.WHITE).isEmpty()) return GameStatus.BLACK_WINS
        if (allLegalMoves(state, PieceColor.BLACK).isEmpty()) return GameStatus.WHITE_WINS
        return GameStatus.IN_PROGRESS
    }

    override fun evaluate(state: GameState): Int {
        when (state.status) {
            GameStatus.WHITE_WINS -> return 100_000
            GameStatus.BLACK_WINS -> return -100_000
            GameStatus.DRAW -> return 0
            GameStatus.IN_PROGRESS -> Unit
        }

        val fox = findFox(state)
        val geese = countGeese(state)
        if (fox == null) return 100_000

        val foxMobility = foxMoves(state, fox).size
        val gooseMobility = allLegalMoves(state, PieceColor.BLACK).size
        val foxRowBonus = (state.boardSize - 1 - fox.row) * 4
        return (13 - geese) * 700 + foxMobility * 18 - gooseMobility * 7 + foxRowBonus
    }

    private fun findFox(state: GameState): Position? {
        for (row in 0 until state.boardSize) {
            for (col in 0 until state.boardSize) {
                val piece = state.get(row, col) as? FoxAndGeesePiece ?: continue
                if (piece.type == FoxAndGeesePieceType.FOX) return Position(row, col)
            }
        }
        return null
    }

    private fun countGeese(state: GameState): Int =
        state.board.count {
            (it as? FoxAndGeesePiece)?.type == FoxAndGeesePieceType.GOOSE
        }

    private fun indexOf(pos: Position, boardSize: Int): Int = pos.row * boardSize + pos.col

    private fun isPlayable(pos: Position): Boolean = FoxAndGeeseSetup.isPlayable(pos)
}