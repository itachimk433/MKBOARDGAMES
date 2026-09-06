package com.mkdev.mkboardgames

import android.content.Context
import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores the moves needed to reconstruct an unfinished non-Ludo match.
 *
 * Moves are used instead of serialising GameState directly so the stored format
 * remains independent of the concrete Piece implementation for each game.
 */
object PausedMatchStore {

    data class Match(
        val gameType: String,
        val vsAI: Boolean,
        val playerColor: String,
        val boardSize: Int? = null,
        val winLength: Int? = null,
        val pieceCount: Int? = null,
        val boardVariant: Int? = null,
        val moves: List<Move>,
    )

    private const val PREFS = "paused_matches"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun has(context: Context, gameType: String): Boolean =
        prefs(context).contains(gameType)

    fun save(
        context: Context,
        gameType: String,
        vsAI: Boolean,
        playerColor: String,
        moves: List<Move>,
        boardSize: Int? = null,
        winLength: Int? = null,
        pieceCount: Int? = null,
        boardVariant: Int? = null,
    ) {
        if (moves.isEmpty()) return
        val payload = JSONObject().apply {
            put("gameType", gameType)
            put("vsAI", vsAI)
            put("playerColor", playerColor)
            boardSize?.let { put("boardSize", it) }
            winLength?.let { put("winLength", it) }
            pieceCount?.let { put("pieceCount", it) }
            boardVariant?.let { put("boardVariant", it) }
            put("moves", encodeMoves(moves))
        }
        prefs(context).edit().putString(gameType, payload.toString()).apply()
    }

    fun load(context: Context, gameType: String): Match? {
        val raw = prefs(context).getString(gameType, null) ?: return null
        return try {
            val payload = JSONObject(raw)
            Match(
                gameType = payload.getString("gameType"),
                vsAI = payload.getBoolean("vsAI"),
                playerColor = payload.optString("playerColor", "WHITE"),
                boardSize = payload.optionalInt("boardSize"),
                winLength = payload.optionalInt("winLength"),
                pieceCount = payload.optionalInt("pieceCount"),
                boardVariant = payload.optionalInt("boardVariant"),
                moves = decodeMoves(payload.getJSONArray("moves")),
            ).takeIf { it.moves.isNotEmpty() }
        } catch (_: Throwable) {
            clear(context, gameType)
            null
        }
    }

    fun clear(context: Context, gameType: String) {
        prefs(context).edit().remove(gameType).apply()
    }

    private fun encodeMoves(moves: List<Move>): JSONArray =
        JSONArray().also { array ->
            moves.forEach { move ->
                array.put(JSONObject().apply {
                    put("from", position(move.from))
                    put("to", position(move.to))
                    put("captures", JSONArray().also { captures ->
                        move.captures.forEach { captures.put(position(it)) }
                    })
                    move.promotionType?.let { put("promotionType", it) }
                    put("metadata", encodeMetadata(move.metadata))
                })
            }
        }

    private fun decodeMoves(array: JSONArray): List<Move> =
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val captures = buildList {
                    val encodedCaptures = item.optJSONArray("captures") ?: JSONArray()
                    for (captureIndex in 0 until encodedCaptures.length()) {
                        add(decodePosition(encodedCaptures.getJSONObject(captureIndex)))
                    }
                }
                add(
                    Move(
                        from = decodePosition(item.getJSONObject("from")),
                        to = decodePosition(item.getJSONObject("to")),
                        captures = captures,
                        promotionType = item.optString("promotionType").takeIf { it.isNotEmpty() },
                        metadata = decodeMetadata(item.optJSONObject("metadata") ?: JSONObject()),
                    )
                )
            }
        }

    private fun position(value: Position) = JSONObject().apply {
        put("row", value.row)
        put("col", value.col)
    }

    private fun decodePosition(value: JSONObject) =
        Position(value.getInt("row"), value.getInt("col"))

    private fun encodeMetadata(metadata: Map<String, Any>): JSONObject =
        JSONObject().also { encoded ->
            metadata.forEach { (key, value) ->
                when (value) {
                    is Boolean, is Int, is Long, is Double, is String ->
                        encoded.put(key, value)
                    is Position -> encoded.put(key, position(value))
                    is List<*> -> {
                        val values = JSONArray()
                        value.forEach { item ->
                            when (item) {
                                is Position -> values.put(position(item))
                                is Int, is Long, is Double, is String, is Boolean -> values.put(item)
                            }
                        }
                        encoded.put(key, values)
                    }
                }
            }
        }

    private fun decodeMetadata(metadata: JSONObject): Map<String, Any> =
        buildMap {
            val keys = metadata.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = metadata.get(key)
                put(
                    key,
                    when (value) {
                        is JSONArray -> buildList {
                            for (index in 0 until value.length()) {
                                val item = value.get(index)
                                add(
                                    if (item is JSONObject &&
                                        item.has("row") && item.has("col")
                                    ) {
                                        decodePosition(item)
                                    } else {
                                        item
                                    }
                                )
                            }
                        }
                        is JSONObject -> if (value.has("row") && value.has("col")) {
                            decodePosition(value)
                        } else {
                            value.toString()
                        }
                        else -> value
                    },
                )
            }
        }

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}