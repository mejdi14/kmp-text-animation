package io.github.mejdi14.textmotion.textscramble

import io.github.mejdi14.textmotion.internal.textscramble.ScrambleMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrambleMathTest {
    private val steps = ScrambleMath.steps(800f, 40f)

    @Test fun defaultsGiveTwentySteps() = assertEquals(20f, steps)

    @Test fun totalIsTwentyOneTicks() = assertEquals(840f, ScrambleMath.totalMillis(800f, 40f))

    @Test fun tickAtFloorsElapsed() {
        assertEquals(0, ScrambleMath.tickAt(0f, 40f))
        assertEquals(0, ScrambleMath.tickAt(39f, 40f))
        assertEquals(2, ScrambleMath.tickAt(80f, 40f))
    }

    @Test fun nothingRevealedAtTickZero() = assertEquals(0, ScrambleMath.revealedCount(0, steps, 10))

    @Test fun everythingRevealedAtLastStep() = assertEquals(10, ScrambleMath.revealedCount(20, steps, 10))

    @Test fun revealIsStrictAndLeftToRight() {
        // tick 10 of 20 with 10 slots: 5 > i  -> slots 0..4
        assertEquals(5, ScrambleMath.revealedCount(10, steps, 10))
        assertTrue(ScrambleMath.isRevealed(4, 10, steps, 10))
        assertFalse(ScrambleMath.isRevealed(5, 10, steps, 10))
    }

    @Test fun revealedCountIsMonotonic() {
        var prev = 0
        for (k in 0..21) {
            val c = ScrambleMath.revealedCount(k, steps, 13)
            assertTrue(c >= prev); prev = c
        }
    }

    @Test fun finishesAfterSteps() {
        assertFalse(ScrambleMath.isFinished(20, steps))
        assertTrue(ScrambleMath.isFinished(21, steps))
    }

    @Test fun fractionalStepsNotRounded() {
        val s = ScrambleMath.steps(1000f, 300f) // 3.33
        assertFalse(ScrambleMath.isFinished(3, s))
        assertTrue(ScrambleMath.isFinished(4, s))
        assertEquals(1200f, ScrambleMath.totalMillis(1000f, 300f))
    }

    @Test fun glyphIndexDeterministicInRangeAndVaries() {
        val a = ScrambleMath.glyphIndex(7, 3, 2, 62)
        assertEquals(a, ScrambleMath.glyphIndex(7, 3, 2, 62))
        val seen = (0 until 40).map { ScrambleMath.glyphIndex(7, it, 2, 62) }
        assertTrue(seen.all { it in 0 until 62 })
        assertTrue(seen.toSet().size > 10)
        assertEquals(0, ScrambleMath.glyphIndex(1, 1, 1, 0))
    }

    @Test fun zeroStepsRevealsAll() = assertTrue(ScrambleMath.isRevealed(3, 0, 0f, 5))
}
