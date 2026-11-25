package io.github.mejdi14.textmotion.internal.auroratext

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraMathTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")
    private val linear = Easing { it }
    private val easeInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    @Test fun alternateDirectionRunsForwardThenBackward() {
        near(AuroraMath.directedProgress(0f, 10_000f), 0f)
        near(AuroraMath.directedProgress(2_500f, 10_000f), 0.25f)
        near(AuroraMath.directedProgress(10_000f, 10_000f), 1f)
        near(AuroraMath.directedProgress(12_500f, 10_000f), 0.75f)
        near(AuroraMath.directedProgress(17_500f, 10_000f), 0.25f)
        near(AuroraMath.directedProgress(20_000f, 10_000f), 0f)
        near(AuroraMath.directedProgress(22_500f, 10_000f), 0.25f)
        near(AuroraMath.directedProgress(5_000f, 0f), 0f)
    }

    @Test fun keyframesMatchReferenceAtOffsets() {
        val expected = listOf(
            Triple(0f, -5f, 0.9f), Triple(0.5f, 5f, 1.1f), Triple(1f, -3f, 0.95f),
            Triple(0.5f, 3f, 1.05f), Triple(0f, -5f, 0.9f),
        )
        AuroraMath.Offsets.forEachIndexed { i, o ->
            val f = AuroraMath.frameAt(o, easeInOut)
            near(f.backgroundX, expected[i].first)
            near(f.rotationDegrees, expected[i].second)
            near(f.scale, expected[i].third)
        }
    }

    @Test fun easingAppliesPerInterval() {
        // Midpoint of the first interval: ease-in-out is symmetric, so half way.
        val mid = AuroraMath.frameAt(0.125f, easeInOut)
        near(mid.backgroundX, 0.25f)
        near(mid.rotationDegrees, 0f)
        // Early in an interval ease-in-out lags linear.
        val early = AuroraMath.frameAt(0.05f, easeInOut).backgroundX
        val earlyLinear = AuroraMath.frameAt(0.05f, linear).backgroundX
        assertTrue(early < earlyLinear, "$early !< $earlyLinear")
        near(earlyLinear, 0.1f)
    }

    @Test fun intensityScalesSwayOnly() {
        val none = AuroraMath.frameAt(0.25f, linear, intensity = 0f)
        near(none.rotationDegrees, 0f)
        near(none.scale, 1f)
        near(none.backgroundX, 0.5f)
        val half = AuroraMath.frameAt(0.25f, linear, intensity = 0.5f)
        near(half.rotationDegrees, 2.5f)
        near(half.scale, 1.05f)
    }

    @Test fun progressIsClamped() {
        near(AuroraMath.frameAt(-1f, linear).rotationDegrees, -5f)
        near(AuroraMath.frameAt(2f, linear).rotationDegrees, -5f)
    }

    @Test fun layerOffsetPansOneElementWidth() {
        near(AuroraMath.layerOffsetX(0f, 200f), 0f)
        near(AuroraMath.layerOffsetX(0.5f, 200f), -100f)
        near(AuroraMath.layerOffsetX(1f, 200f), -200f)
    }

    @Test fun cssGradientLineAt135Degrees() {
        val (s, e) = AuroraMath.cssGradientLine(135f, 400f, 100f)
        val half = (400f + 100f) * sqrt(0.5f) / 2f
        near(s.x, 200f - half * sqrt(0.5f), 1e-2f)
        near(s.y, 50f - half * sqrt(0.5f), 1e-2f)
        near(e.x, 200f + half * sqrt(0.5f), 1e-2f)
        near(e.y, 50f + half * sqrt(0.5f), 1e-2f)
        // The top-left corner projects exactly onto the start (first colour).
        val dx = e.x - s.x
        val dy = e.y - s.y
        val t = ((0f - s.x) * dx + (0f - s.y) * dy) / (dx * dx + dy * dy)
        near(t, 0f)
    }

    @Test fun cssGradientLineCardinalAngles() {
        val (s90, e90) = AuroraMath.cssGradientLine(90f, 300f, 50f)
        near(s90.x, 0f); near(e90.x, 300f); near(s90.y, 25f); near(e90.y, 25f)
        val (s0, e0) = AuroraMath.cssGradientLine(0f, 300f, 50f)
        near(s0.y, 50f); near(e0.y, 0f); near(s0.x, 150f)
    }

    @Test fun loopedStopsRepeatFirstColour() {
        assertEquals(listOf(1, 2, 3, 1), AuroraMath.loopedStops(listOf(1, 2, 3)))
        assertEquals(listOf(7, 7), AuroraMath.loopedStops(listOf(7)))
        assertEquals(emptyList(), AuroraMath.loopedStops(emptyList<Int>()))
    }
}
