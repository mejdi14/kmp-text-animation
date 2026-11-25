package io.github.mejdi14.textmotion.internal.gradienttext

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradientTextMathTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    @Test fun paletteNormalisation() {
        assertEquals(GradientTextMath.DefaultColors, GradientTextMath.normalizeColors(emptyList()))
        assertEquals(listOf(Color.Red, Color.Red), GradientTextMath.normalizeColors(listOf(Color.Red)))
        val three = listOf(Color.Red, Color.Green, Color.Blue)
        assertEquals(three, GradientTextMath.normalizeColors(three))
        assertEquals(three + Color.Red, GradientTextMath.loopColors(three))
    }

    @Test fun directionOverridesAngleAndParamsAreClamped() {
        near(GradientTextMath.resolveAngle(90f, null), 90f)
        near(GradientTextMath.resolveAngle(90f, 135f), 135f)
        near(GradientTextMath.clampScale(0f), 1f)
        near(GradientTextMath.clampScale(9f), 6f)
        near(GradientTextMath.cycleSeconds(50), 0.2f)
        near(GradientTextMath.cycleSeconds(8000), 8f)
    }

    @Test fun yoyoOffsetPingPongsWithCosineEase() {
        // scale 3: period 300%, travel = period - 100 = 200%.
        near(GradientTextMath.linearOffsetPercent(0f, 3f, true), 0f)
        near(GradientTextMath.linearOffsetPercent(0.5f, 3f, true), 100f)
        near(GradientTextMath.linearOffsetPercent(1f, 3f, true), 200f)
        near(GradientTextMath.linearOffsetPercent(1.5f, 3f, true), 100f)
        near(GradientTextMath.linearOffsetPercent(2f, 3f, true), 0f)
        near(GradientTextMath.linearOffsetPercent(-0.5f, 3f, true), 100f)
        // Cosine ease: slower than linear at the start.
        assertTrue(GradientTextMath.linearOffsetPercent(0.1f, 3f, true) < 0.1f * 200f)
    }

    @Test fun loopingOffsetIsSeamless() {
        near(GradientTextMath.linearOffsetPercent(0.25f, 3f, false), 75f)
        near(GradientTextMath.linearOffsetPercent(1.25f, 3f, false), 75f)
        near(GradientTextMath.linearOffsetPercent(0.999f, 3f, false), 299.7f, 0.1f)
    }

    @Test fun pointerOffsetScalesWithPull() {
        near(GradientTextMath.pointerOffsetPercent(0.5f, 3f, 1f), 0f)
        near(GradientTextMath.pointerOffsetPercent(1f, 3f, 1f), 75f)
        near(GradientTextMath.pointerOffsetPercent(1f, 3f, 0f), 0f)
    }

    @Test fun flowBlobsFollowReferenceFormula() {
        val b0 = GradientTextMath.flowBlob(0, 0f, 3f, 0f, 0.5f, 0.5f)
        near(b0.xPercent, 50f)
        near(b0.yPercent, 50f)
        near(b0.radiusXPercent, 22f + 36f)
        near(b0.radiusYPercent, 60f + 90f)
        // Blob 1 phase: x = 50 + 44 sin(2.1), y = 50 + 40 sin(1.3) at cycle 0.
        val b1 = GradientTextMath.flowBlob(1, 0f, 3f, 0f, 0.5f, 0.5f)
        near(b1.xPercent, 50f + 44f * kotlin.math.sin(2.1f))
        near(b1.yPercent, 50f + 40f * kotlin.math.sin(1.3f))
        // Only blob 0 is pulled to the pointer.
        val pulled0 = GradientTextMath.flowBlob(0, 0f, 3f, 1f, 1f, 0f)
        near(pulled0.xPercent, 100f)
        near(pulled0.yPercent, 0f)
        val pulled1 = GradientTextMath.flowBlob(1, 0f, 3f, 1f, 1f, 0f)
        near(pulled1.xPercent, b1.xPercent)
    }

    @Test fun conicParameters() {
        near(GradientTextMath.conicSwayDegrees(0.5f, true), 50f)
        near(GradientTextMath.conicSwayDegrees(0.5f, false), 180f)
        near(GradientTextMath.conicFromDegrees(0f, true), -30f)
        near(GradientTextMath.conicPeriodDegrees(3f), 60f)
        near(GradientTextMath.conicPeriodDegrees(6f), 30f)
        near(GradientTextMath.conicCenterXPercent(0.5f, 0f), 50f)
        near(GradientTextMath.conicCenterYPercent(0.5f, 0f), 260f)
        near(GradientTextMath.conicCenterYPercent(0.5f, 1f), 50f)
    }

    @Test fun conicStopsCoverFullTurnMonotonically() {
        val colors = listOf(Color.Red, Color.Green, Color.Blue)
        for (scale in listOf(1f, 2f, 3f, 4.5f, 6f)) {
            val stops = GradientTextMath.conicStops(colors, scale)
            near(stops.first().first, 0f)
            near(stops.last().first, 1f)
            assertEquals(Color.Red, stops.first().second)
            for (k in 1 until stops.size) assertTrue(stops[k].first >= stops[k - 1].first, "scale $scale stop $k")
        }
        // scale 3 => 60 degree period => 6 repetitions of 3 colours + closing stop.
        assertEquals(6 * 3 + 1, GradientTextMath.conicStops(colors, 3f).size)
        // Whole periods end exactly on the first colour.
        assertEquals(Color.Red, GradientTextMath.conicStops(colors, 3f).last().second)
    }

    @Test fun dynamicsAccumulateAndFreezeOnHover() {
        val d = GradientDynamics()
        d.advance(0f, false, 0.5f, 0.5f, false, false)
        d.advance(1000f, false, 0.5f, 0.5f, false, false)
        near(d.time, 1f)
        d.advance(2000f, true, 0.5f, 0.5f, false, true) // hover-paused
        near(d.time, 1f)
        d.advance(3000f, false, 0.5f, 0.5f, false, false)
        near(d.time, 2f)
        // Same clock again does nothing.
        d.advance(3000f, false, 0.5f, 0.5f, false, false)
        near(d.time, 2f)
        // Restart (clock goes backwards) resets.
        d.advance(0f, false, 0.5f, 0.5f, false, false)
        near(d.time, 0f)
    }

    @Test fun pullAndSpringConvergeToThePointer() {
        val d = GradientDynamics()
        var t = 0f
        d.advance(t, true, 0.9f, 0.2f, true, false)
        repeat(300) { t += 16f; d.advance(t, true, 0.9f, 0.2f, true, false) }
        near(d.pull, 1f, 1e-2f)
        near(d.px, 0.9f, 1e-2f)
        near(d.py, 0.2f, 1e-2f)
        // Leaving: pull fades, the spring holds its last target.
        repeat(300) { t += 16f; d.advance(t, false, 0.5f, 0.5f, true, false) }
        near(d.pull, 0f, 1e-2f)
        near(d.px, 0.9f, 1e-2f)
    }

    @Test fun springIsStableWithLargeFrames() {
        val d = GradientDynamics()
        var t = 0f
        d.advance(t, true, 1f, 1f, true, false)
        repeat(60) { t += 500f; d.advance(t, true, 1f, 1f, true, false) }
        assertTrue(d.px in 0f..1.05f && d.py in 0f..1.05f)
    }
}
