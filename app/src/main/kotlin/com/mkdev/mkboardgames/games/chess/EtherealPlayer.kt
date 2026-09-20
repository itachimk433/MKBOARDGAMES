package com.mkdev.mkboardgames.games.chess

import android.content.Context
import android.os.Build
import android.util.Log
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.PieceColor
import com.mkdev.mkboardgames.engine.Position
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * UCI adapter for the compact Ethereal engine built by GitHub Actions.
 *
 * The generated binary is not committed to the repository. Local builds
 * without workflow-generated assets fall back to the Kotlin chess player.
 */
class EtherealPlayer(
    private val context: Context,
    private val profile: SettingsManager.ChessEtherealProfile,
) {
    private val chessEngine = ChessRuleEngine()

    fun bestMove(state: GameState): Move? {
        if (state.status != GameStatus.IN_PROGRESS) return null

        val legalMoves = chessEngine.allLegalMoves(state, state.currentTurn)
        if (legalMoves.isEmpty()) return null

        val executable = try {
            EtherealBinary.resolve(context)
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to extract Ethereal", error)
            null
        } ?: return null
        val process = try {
            ProcessBuilder(executable.absolutePath)
                .directory(executable.parentFile)
                .redirectErrorStream(true)
                .start()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start Ethereal at ${executable.absolutePath}", error)
            return null
        }

        return try {
            val writer = OutputStreamWriter(process.outputStream).buffered()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            send(writer, "uci")
            if (!readUntil(reader, "uciok", STARTUP_TIMEOUT_MS)) {
                Log.e(TAG, "Ethereal did not answer uci before timeout")
                return null
            }

            send(writer, "setoption name Threads value 1")
            send(writer, "setoption name Hash value ${profile.hashMb}")
            send(writer, "isready")
            if (!readUntil(reader, "readyok", STARTUP_TIMEOUT_MS)) {
                Log.e(TAG, "Ethereal did not become ready before timeout")
                return null
            }

            send(writer, "position fen ${ChessFen.fromState(state)}")
            send(writer, "go movetime ${profile.timeLimitMs}")

            val uciMove = readBestMove(reader, profile.timeLimitMs + RESULT_GRACE_MS)
                ?: run {
                    Log.e(TAG, "Ethereal did not return a bestmove")
                    return null
                }
            val move = legalMoves.firstOrNull { it.toUci() == uciMove }
            if (move == null) {
                Log.e(TAG, "Ethereal returned illegal move '$uciMove'")
            }
            move
        } catch (error: Throwable) {
            Log.e(TAG, "Ethereal search failed", error)
            null
        } finally {
            try {
                process.outputStream.close()
            } catch (_: Throwable) {
                // The process may already have exited.
            }
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun send(writer: BufferedWriter, command: String) {
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private fun readUntil(reader: BufferedReader, marker: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                val line = reader.readLine() ?: return false
                if (line.trim() == marker) return true
            } else {
                Thread.sleep(8L)
            }
        }
        return false
    }

    private fun readBestMove(reader: BufferedReader, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                val line = reader.readLine() ?: return null
                if (line.startsWith("bestmove ")) {
                    return line.substringAfter("bestmove ")
                        .trim()
                        .substringBefore(' ')
                        .takeIf { it != "(none)" }
                }
            } else {
                Thread.sleep(8L)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "EtherealPlayer"
        const val STARTUP_TIMEOUT_MS = 2_000L
        const val RESULT_GRACE_MS = 1_500L
    }
}

private object EtherealBinary {
    fun resolve(context: Context): File? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { supportedAbi ->
            context.assets.list("$ASSET_ROOT/$supportedAbi")
                ?.contains(ENGINE_NAME) == true
        } ?: return null

        val target = File(context.noBackupFilesDir, "$ASSET_ROOT/$abi/$ENGINE_NAME")
        synchronized(this) {
            if (!target.exists() || target.length() == 0L) {
                target.parentFile?.mkdirs()
                context.assets.open("$ASSET_ROOT/$abi/$ENGINE_NAME").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.setReadable(true, false)
                target.setExecutable(true, false)
            }
        }
        return target.takeIf { it.exists() && it.canExecute() }
    }

    private const val ASSET_ROOT = "ethereal"
    private const val ENGINE_NAME = "ethereal"
}

private fun Move.toUci(): String {
    fun square(position: Position): String =
        "${('a'.code + position.col).toChar()}${8 - position.row}"

    val promotion = when (promotionType) {
        "QUEEN" -> "q"
        "ROOK" -> "r"
        "BISHOP" -> "b"
        "KNIGHT" -> "n"
        else -> ""
    }
    return "${square(from)}${square(to)}$promotion"
}

/**
 * Converts the app board representation into the FEN form accepted by UCI
 * engines. Row 0 is Black's back rank, which is already FEN rank 8.
 */
internal object ChessFen {
    fun fromState(state: GameState): String {
        val board = (0 until 8).joinToString("/") { row ->
            val result = StringBuilder()
            var empty = 0
            for (col in 0 until 8) {
                val piece = state.board[row * 8 + col] as? ChessPiece
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        result.append(empty)
                        empty = 0
                    }
                    result.append(pieceChar(piece))
                }
            }
            if (empty > 0) result.append(empty)
            result.toString()
        }

        val side = if (state.currentTurn == PieceColor.WHITE) "w" else "b"
        val castling = buildString {
            if (state.metadata["castleWK"] == true) append('K')
            if (state.metadata["castleWQ"] == true) append('Q')
            if (state.metadata["castleBK"] == true) append('k')
            if (state.metadata["castleBQ"] == true) append('q')
        }.ifEmpty { "-" }

        val enPassantColumn = (state.metadata["enPassant"] as? Int)
            ?.takeIf { it in 0..7 }
        val enPassant = if (enPassantColumn == null) {
            "-"
        } else {
            val targetRow = if (state.currentTurn == PieceColor.BLACK) 5 else 2
            "${('a'.code + enPassantColumn).toChar()}${8 - targetRow}"
        }

        val fullmove = (state.moveHistory.size / 2) + 1
        return "$board $side $castling $enPassant 0 $fullmove"
    }

    private fun pieceChar(piece: ChessPiece): Char {
        val symbol = when (piece.type) {
            ChessPieceType.KING -> 'k'
            ChessPieceType.QUEEN -> 'q'
            ChessPieceType.ROOK -> 'r'
            ChessPieceType.BISHOP -> 'b'
            ChessPieceType.KNIGHT -> 'n'
            ChessPieceType.PAWN -> 'p'
        }
        return if (piece.color == PieceColor.WHITE) symbol.uppercaseChar() else symbol
    }
}