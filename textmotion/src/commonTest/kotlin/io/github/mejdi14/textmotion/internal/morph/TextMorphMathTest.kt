package io.github.mejdi14.textmotion.internal.morph

import io.github.mejdi14.textmotion.TextEasing
import io.github.mejdi14.textmotion.TextMorphTransition
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextMorphMathTest {
    private fun near(a: Double, b: Double, eps: Double = 1e-6) = assertTrue(abs(a - b) < eps, "$a != $b")
    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "$a != $b")

    @Test fun keysUseLowercaseAndOccurrence() {
        val keys = morphKeys(listOf("H", "e", "l", "l", "o"))
        assertEquals(listOf("h\u00001", "e\u00001", "l\u00001", "l\u00002", "o\u00001"), keys)
    }

    @Test fun keysCaseSensitiveWhenRequested() {
        val keys = morphKeys(listOf("A", "a"), ignoreCase = false)
        assertEquals(listOf("A\u00001", "a\u00001"), keys)
        assertEquals(listOf("a\u00001", "a\u00002"), morphKeys(listOf("A", "a")))
    }

    @Test fun helloToHelpKeepsSharedPrefix() {
        val old = morphKeys("Hello".map { it.toString() })
        val new = morphKeys("Help".map { it.toString() })
        assertContentEquals(intArrayOf(0, 1, 2, -1), matchKeys(old, new))
    }

    @Test fun reorderedCharactersMatchByOccurrence() {
        val old = morphKeys("listen".map { it.toString() })
        val new = morphKeys("silent".map { it.toString() })
        // s i l e n t ← l i s t e n
        assertContentEquals(intArrayOf(2, 1, 0, 4, 5, 3), matchKeys(old, new))
    }

    @Test fun graphemeClustersAreSingleKeys() {
        val keys = morphKeys(listOf("👍🏽", "a", "👍🏽"))
        assertEquals(listOf("👍🏽\u00001", "a\u00001", "👍🏽\u00002"), keys)
    }

    @Test fun prefixSeparatesGranularities() {
        val c = morphKeys(listOf("a"), prefix = "c:")
        val w = morphKeys(listOf("a"), prefix = "w:")
        assertContentEquals(intArrayOf(-1), matchKeys(c, w))
    }

    @Test fun referenceSpringIsNearCriticallyDamped() {
        val s = SpringSolver(280f, 18f, 0.3f)
        near(s.dampingRatio, 18.0 / (2.0 * kotlin.math.sqrt(280.0 * 0.3)))
        assertTrue(s.dampingRatio in 0.97..0.99)
        near(s.omega0, kotlin.math.sqrt(280.0 / 0.3))
    }

    @Test fun springStartsAtInitialState() {
        val s = SpringSolver(280f, 18f, 0.3f)
        near(s.position(3.0, -2.0, 0.0), 3.0)
        near(s.velocity(3.0, -2.0, 0.0), -2.0)
    }

    @Test fun springVelocityIsDerivativeOfPosition() {
        for (s in listOf(SpringSolver(280f, 18f, 0.3f), SpringSolver(100f, 5f, 1f), SpringSolver(100f, 20f, 1f), SpringSolver(100f, 40f, 1f))) {
            for (t in listOf(0.01, 0.05, 0.2, 0.5)) {
                val h = 1e-6
                val numeric = (s.position(1.0, 0.7, t + h) - s.position(1.0, 0.7, t - h)) / (2 * h)
                near(s.velocity(1.0, 0.7, t), numeric, 1e-4)
            }
        }
    }

    @Test fun criticallyDampedClosedForm() {
        val s = SpringSolver(100f, 20f, 1f) // ζ = 1, ω0 = 10
        val t = 0.1
        near(s.position(1.0, 0.0, t), exp(-1.0) * (1.0 + 1.0), 1e-9)
    }

    @Test fun springDecaysToRest() {
        val s = SpringSolver(280f, 18f, 0.3f)
        val settle = s.settleMillis(1000.0, 0.0, MotionRest.DELTA_DEFAULT, MotionRest.SPEED_DEFAULT)
        assertTrue(settle in 300f..800f, "settle $settle")
        assertTrue(abs(s.position(1000.0, 0.0, settle / 1000.0)) <= 0.5)
    }

    @Test fun layoutRemainingGoesFromOneToZero() {
        val m = MorphMotion(TextMorphTransition.Spring())
        near(m.layoutRemaining(0f), 1f)
        assertTrue(m.layoutRemaining(50f) in 0f..1f)
        assertTrue(m.layoutRemaining(100f) < m.layoutRemaining(50f))
        near(m.layoutRemaining(m.layoutSettleMillis), 0f)
    }

    @Test fun presenceSpringKeepsVelocityOnInterrupt() {
        val m = MorphMotion(TextMorphTransition.Spring())
        val settle = m.presenceSettleMillis(0f, 0f, 1f)
        val mid = m.presence(0f, 0f, 1f, 60f, settle)
        val vel = m.presenceVelocity(0f, 0f, 1f, 60f, settle)
        assertTrue(mid in 0.01f..0.99f)
        assertTrue(vel > 0f)
        // Reversing: starts at the same value with the same velocity, then turns around.
        val back = m.presenceSettleMillis(mid, vel, 0f)
        near(m.presence(mid, vel, 0f, 0f, back), mid)
        near(m.presence(0f, 0f, 1f, 10_000f, settle), 1f)
    }

    @Test fun tweenFollowsEasingAndDelay() {
        val m = MorphMotion(TextMorphTransition.Tween(durationMillis = 200, easing = TextEasing.Linear, delayMillis = 100))
        near(m.layoutRemaining(50f), 1f)
        near(m.layoutRemaining(200f), 0.5f)
        near(m.layoutRemaining(300f), 0f)
        near(m.layoutSettleMillis, 300f)
        val settle = m.presenceSettleMillis(0f, 0f, 1f)
        near(m.presence(0f, 0f, 1f, 200f, settle), 0.5f)
        near(m.presenceVelocity(0f, 0f, 1f, 200f, settle), 0f)
    }
}
