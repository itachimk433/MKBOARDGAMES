package com.mkdev.mkboardgames.games.yote

import com.mkdev.mkboardgames.engine.Move

/**
 * Detects a repeating alternating-AI move sequence.
 *
 * A Yoté autoplay game has one move from each side per cycle, so only even
 * cycle lengths are considered. The detector requires four complete repeats
 * before reporting a loop, which avoids stopping on a short-lived choice
 * pattern while still catching a true AI-vs-AI oscillation.
 */
class YoteAutoplayLoopDetector(
    private val repeatsRequired: Int = 4,
    private val maxCycleLength: Int = 8,
) {
    private val recentMoves = ArrayDeque<Move>()

    fun reset() {
        recentMoves.clear()
    }

    fun record(move: Move): Boolean {
        recentMoves.addLast(move)
        val maxHistory = maxCycleLength * repeatsRequired
        while (recentMoves.size > maxHistory) {
            recentMoves.removeFirst()
        }

        val history = recentMoves.toList()
        return (2..maxCycleLength step 2).any { cycleLength ->
            val requiredMoves = cycleLength * repeatsRequired
            if (history.size < requiredMoves) {
                return@any false
            }

            val window = history.takeLast(requiredMoves)
            (cycleLength until requiredMoves).all { index ->
                window[index] == window[index % cycleLength]
            }
        }
    }
}