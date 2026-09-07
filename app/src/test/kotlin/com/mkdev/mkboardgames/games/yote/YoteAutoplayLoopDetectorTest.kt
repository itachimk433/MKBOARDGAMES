package com.mkdev.mkboardgames.games.yote

import com.mkdev.mkboardgames.engine.Move
import com.mkdev.mkboardgames.engine.Position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YoteAutoplayLoopDetectorTest {
    @Test
    fun stopsAfterFourRepeatsOfAnAlternatingAiCycle() {
        val detector = YoteAutoplayLoopDetector()
        val cycle = listOf(
            Move(Position(0, 0), Position(0, 1)),
            Move(Position(4, 5), Position(4, 4)),
        )

        repeat(3) {
            cycle.forEach { assertFalse(detector.record(it)) }
        }
        assertTrue(detector.record(cycle[0]))
        assertTrue(detector.record(cycle[1]))
    }

    @Test
    fun doesNotStopOnASequenceThatOnlyRepeatsThreeTimes() {
        val detector = YoteAutoplayLoopDetector()
        val cycle = listOf(
            Move(Position(0, 0), Position(0, 1)),
            Move(Position(4, 5), Position(4, 4)),
        )

        repeat(3) {
            cycle.forEach { assertFalse(detector.record(it)) }
        }
    }
}