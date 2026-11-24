package io.github.mejdi14.textmotion.internal.blurtext

import io.github.mejdi14.textmotion.BlurTextDirection
import io.github.mejdi14.textmotion.BlurTextEffect
import io.github.mejdi14.textmotion.BlurTextKeyframe
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextGranularity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlurTextMotionTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    private fun spec(effect: BlurTextEffect = BlurTextEffect.Blur, dir: BlurTextDirection = BlurTextDirection.Top) =
        BlurTextSpec.build(effect, dir, 10f, 50f, null, null, null)

    private fun timing(s: BlurTextSpec, maxRank: Float = 0f, iterations: Int = 1, loopDelay: Float = 0f) =
        BlurTextTiming(350f, s.keyframes.size, s.custom, s.settle, 200f, maxRank, iterations, loopDelay)

    @Test fun defaultBlurKeyframes() {
        val s = spec()
        assertEquals(3, s.keyframes.size)
        near(s.from[CH_BLUR], 10f); near(s.from[CH_OPACITY], 0f); near(s.from[CH_Y], -50f)
        near(s.keyframes[1][CH_Y], 5f); near(s.keyframes[1][CH_BLUR], 5f)
        near(s.exit[CH_Y], 30f)
        near(s.rest[CH_OPACITY], 1f); near(s.rest[CH_Y], 0f)
        val bottom = spec(dir = BlurTextDirection.Bottom)
        near(bottom.from[CH_Y], 50f)
        val left = spec(dir = BlurTextDirection.Left)
        near(left.from[CH_X], -50f); near(left.from[CH_Y], 0f)
    }

    @Test fun tiltUsesRotateAxisAndOrigin() {
        val top = spec(BlurTextEffect.Tilt, BlurTextDirection.Top)
        near(top.from[CH_ROT_X], 55f); near(top.from[CH_ROT_Y], 0f)
        near(top.originY, 0f); near(top.originX, 0.5f); near(top.perspective, 600f)
        val right = spec(BlurTextEffect.Tilt, BlurTextDirection.Right)
        near(right.from[CH_ROT_Y], 55f); near(right.originX, 1f)
    }

    @Test fun driftAndFocus() {
        val d = spec(BlurTextEffect.Drift, BlurTextDirection.Right)
        near(d.from[CH_X], 80f); near(d.from[CH_SKEW_X], 28f); near(d.from[CH_Y], 0f)
        val f = spec(BlurTextEffect.Focus)
        assertEquals(null, f.bounce); near(f.from[CH_SCALE], 1.12f); near(f.from[CH_OPACITY], 0.32f)
    }

    @Test fun defaultTimingMatchesReference() {
        val s = spec()
        val t = timing(s, maxRank = 3f)
        near(t.totalDuration, 700f)
        near(t.inTotal, 700f * 1.1f + 600f)
        near(t.exitDuration, 455f)
        near(t.outTotal, 455f + 3f * 80f)
        near(t.end, t.inTotal)
    }

    @Test fun springStartsAtOneOvershootsAndSettles() {
        near(springRemaining(0f, 350f, 0.35f), 1f)
        var min = 1f
        var t = 0f
        while (t < 1000f) { min = minOf(min, springRemaining(t, 350f, 0.35f)); t += 5f }
        assertTrue(min < -0.01f, "expected overshoot, min=$min")
        assertTrue(abs(springRemaining(1500f, 350f, 0.35f)) < 1e-3f)
        // Critically damped (bounce 0) never overshoots.
        t = 0f
        while (t < 1000f) { assertTrue(springRemaining(t, 350f, 0f) >= -1e-6f); t += 5f }
    }

    @Test fun evaluatorFadesAndMoves() {
        val s = spec()
        val tm = timing(s)
        val ev = BlurTextEvaluator(s, tm)
        val out = FloatArray(CHANNELS)
        ev.evaluate(BlurPhase.In, 0f, 0f, out)
        near(out[CH_OPACITY], 0f); near(out[CH_BLUR], 10f); near(out[CH_Y], -50f)
        ev.evaluate(BlurPhase.In, 350f, 0f, out)
        near(out[CH_OPACITY], 0.5f); near(out[CH_BLUR], 5f)
        ev.evaluate(BlurPhase.In, 700f, 0f, out)
        near(out[CH_OPACITY], 1f); near(out[CH_BLUR], 0f)
        // Rank delays the piece.
        ev.evaluate(BlurPhase.In, 200f, 1f, out)
        near(out[CH_OPACITY], 0f)
        ev.evaluate(BlurPhase.Out, 10_000f, 0f, out)
        near(out[CH_Y], 30f); near(out[CH_OPACITY], 0f)
    }

    @Test fun customKeyframesAreEvenlySpacedAndLinear() {
        val s = BlurTextSpec.build(
            BlurTextEffect.Blur, BlurTextDirection.Top, 10f, 50f,
            BlurTextKeyframe(opacity = 0f, y = (-20).dp),
            listOf(BlurTextKeyframe(opacity = 0.2f, y = 10.dp), BlurTextKeyframe(opacity = 0.6f), BlurTextKeyframe()),
            null,
        )
        assertTrue(s.custom)
        assertContentEquals(floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f), s.times)
        val tm = timing(s)
        near(tm.totalDuration, 350f * 3)
        near(tm.inTotal, 1050f)
        val out = FloatArray(CHANNELS)
        BlurTextEvaluator(s, tm).evaluate(BlurPhase.In, 175f, 0f, out)
        near(out[CH_OPACITY], 0.1f); near(out[CH_Y], -5f)
    }

    @Test fun ranks() {
        assertContentEquals(floatArrayOf(0f, 1f, 2f, 3f), blurTextRanks(4, StaggerFrom.Start, "x"))
        assertContentEquals(floatArrayOf(3f, 2f, 1f, 0f), blurTextRanks(4, StaggerFrom.End, "x"))
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 1f), blurTextRanks(4, StaggerFrom.Center, "x"))
        assertContentEquals(floatArrayOf(2f, 1f, 0f, 1f, 2f), blurTextRanks(5, StaggerFrom.Center, "x"))
        val r = blurTextRanks(6, StaggerFrom.Random, "Hello world")
        assertContentEquals(floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f), r.sortedArray())
        assertContentEquals(r, blurTextRanks(6, StaggerFrom.Random, "Hello world"))
    }

    @Test fun pieceCountsLikeReference() {
        assertEquals(3, blurTextPieceCount("Isn't this cool", TextGranularity.Word))
        // 5 + 5 letters + 1 space piece
        assertEquals(11, blurTextPieceCount("Hello world", TextGranularity.Character))
        assertEquals(6, blurTextPieceIndex(TextGranularity.Character, segmentIndex = 5, wordIndex = 1))
    }

    @Test fun phasesWithLoopAndReplay() {
        val s = spec()
        val t = timing(s, maxRank = 0f, iterations = Int.MAX_VALUE, loopDelay = 2000f)
        val local = FloatArray(1)
        assertEquals(BlurPhase.From, t.phaseAt(-10f, intro = true, out = local))
        assertEquals(BlurPhase.In, t.phaseAt(100f, true, local)); near(local[0], 100f)
        assertEquals(BlurPhase.Rest, t.phaseAt(t.inTotal + 10f, true, local))
        assertEquals(BlurPhase.Out, t.phaseAt(t.inTotal + 2000f + 5f, true, local)); near(local[0], 5f)
        assertEquals(BlurPhase.In, t.phaseAt(t.cycle + 1f, true, local))
        assertEquals(BlurPhase.Out, t.phaseAt(t.replayStart, intro = false, out = local))
        assertEquals(BlurPhase.Rest, t.phaseAt(t.restLoopStart, intro = false, out = local))
        assertEquals(0, t.completedIns(t.inTotal - 1f))
        assertEquals(1, t.completedIns(t.inTotal))
        assertEquals(2, t.completedIns(t.cycle + t.inTotal))
        val once = timing(s)
        assertEquals(BlurPhase.Rest, once.phaseAt(once.end + 1f, true, local))
        assertEquals(1, once.completedIns(once.end + 5000f))
    }

    @Test fun blurRadiusConversion() {
        near(blurRadiusForSigma(0f), 0f)
        near(0.57735f * blurRadiusForSigma(10f) + 0.5f, 10f)
        near(focusDimBlur(10f), 3f); near(focusDimBlur(2f), 2f); near(focusDimBlur(40f), 6f)
    }
}
