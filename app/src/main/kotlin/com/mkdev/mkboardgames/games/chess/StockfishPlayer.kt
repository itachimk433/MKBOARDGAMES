package com.mkdev.mkboardgames.games.chess

import android.content.Context
import android.os.Build
import com.mkdev.mkboardgames.SettingsManager
import com.mkdev.mkboardgames.engine.GameState
import com.mkdev.mkboardgames.engine.GameStatus
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * UCI adapter for the compact Stockfish small-NNUE engine built by GitHub
 * Actions. The binary is intentionally not committed to the repository.
 *
 * Stockfish's small network is shared by both ABIs. It is copied beside the
 * selected executable because the engine resolves external network files
 * relative to its working directory.
 */
class StockfishPlayer(
    private val context: Context,
    private val profile: SettingsManager.ChessStockfishProfile,
) {
    private val chessEngine = ChessRuleEngine()

    fun bestMove(state: GameState): Move? {
        if (state.status != GameStatus.IN_PROGRESS) return null

        val legalMoves = chessEngine.allLegalMoves(state, state.currentTurn)
        if (legalMoves.isEmpty()) return null

        val executable = StockfishBinary.resolve(context) ?: return null
        val process = try {
            ProcessBuilder(executable.absolutePath)
                .directory(executable.parentFile)
                .redirectErrorStream(true)
                .start()
        } catch (_: Throwable) {
            return null
        }

        return try {
            val writer = OutputStreamWriter(process.outputStream).buffered()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            send(writer, "uci")
            if (!readUntil(reader, "uciok", STARTUP_TIMEOUT_MS)) return null

            send(writer, "setoption name Threads value 1")
            send(writer, "setoption name Hash value ${profile.hashMb}")
            send(writer, "setoption name Move Overhead value 20")
            send(writer, "setoption name EvalFile value $NETWORK_NAME")
            send(writer, "setoption name EvalFileSmall value $NETWORK_NAME")
            send(writer, "isready")
            if (!readUntil(reader, "readyok", STARTUP_TIMEOUT_MS)) return null

            send(writer, "position fen ${ChessFen.fromState(state)}")
            send(writer, "go movetime ${profile.timeLimitMs}")

            val uciMove = readBestMove(reader, profile.timeLimitMs + RESULT_GRACE_MS)
                ?: return null
            legalMoves.firstOrNull { it.toUci() == uciMove }
        } catch (_: Throwable) {
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

    private object StockfishBinary {
        fun resolve(context: Context): File? {
            val abi = Build.SUPPORTED_ABIS.firstOrNull { supportedAbi ->
                context.assets.list("$ASSET_ROOT/$supportedAbi")
                    ?.contains(ENGINE_NAME) == true
            } ?: return null

            val directory = File(context.noBackupFilesDir, "$ASSET_ROOT/$abi")
            val executable = File(directory, ENGINE_NAME)
            val network = File(directory, NETWORK_NAME)
            synchronized(lock) {
                copyAssetIfNeeded(
                    context = context,
                    assetPath = "$ASSET_ROOT/$abi/$ENGINE_NAME",
                    target = executable,
                )
                copyAssetIfNeeded(
                    context = context,
                    assetPath = "$ASSET_ROOT/$NETWORK_NAME",
                    target = network,
                )
                executable.setReadable(true, false)
                executable.setExecutable(true, false)
                network.setReadable(true, false)
            }

            return executable.takeIf {
                it.exists() && it.length() > 0L && it.canExecute() &&
                    network.exists() && network.length() > 0L
            }
        }

        private fun copyAssetIfNeeded(context: Context, assetPath: String, target: File) {
            if (target.exists() && target.length() > 0L) return
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }

        private const val ASSET_ROOT = "stockfish"
        private const val ENGINE_NAME = "stockfish"
        private val lock = Any()
    }
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

private const val NETWORK_NAME = "nn-37f18f62d772.nnue"
private const val STARTUP_TIMEOUT_MS = 2_000L
private const val RESULT_GRACE_MS = 1_500L