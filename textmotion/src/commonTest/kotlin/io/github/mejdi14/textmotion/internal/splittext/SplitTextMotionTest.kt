package io.github.mejdi14.textmotion.internal.splittext

import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.SplitTextEffect
import io.github.mejdi14.textmotion.SplitTextHoverEffect
import io.github.mejdi14.textmotion.SplitTextKeyframe
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextEasing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitTextMotionTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    private val power3Out = TextEasing.powerOut(3)

    private fun timing(
        duration: Float = 1000f,
        spread: Float = 200f,
        iterations: Int = 1,
        hold: Float = 0f,
    ) = SplitTextTiming(duration, spread, iterations, hold, power3Out)

    // ---------------------------------------------------------------- stagger

    @Test fun startStaggerAdvancesByEach() {
        val d = splitTextDelays(5, StaggerFrom.Start, 50f, 0)
        for (i in 0 until 5) near(d[i], i * 50f)
    }

    @Test fun endStaggerIsReversed() {
        val d = splitTextDelays(5, StaggerFrom.End, 50f, 0)
        for (i in 0 until 5) near(d[i], (4 - i) * 50f)
    }

    @Test fun centerStaggerKeepsTotalSpreadLikeGsap() {
        val d = splitTextDelays(5, StaggerFrom.Center, 50f, 0)
        near(d[2], 0f)
        near(d[0], 200f); near(d[4], 200f)
        near(d[1], 100f); near(d[3], 100f)
    }

    @Test fun evenCenterStaggerStartsWithTwoMiddleUnits() {
        val d = splitTextDelays(4, StaggerFrom.Center, 100f, 0)
        near(d[1], 0f); near(d[2], 0f)
        near(d[0], 300f); near(d[3], 300f)
    }

    @Test fun edgesStaggerStartsOutside() {
        val d = splitTextDelays(5, StaggerFrom.Edges, 50f, 0)
        near(d[0], 0f); near(d[4], 0f); near(d[2], 200f)
    }

    @Test fun randomStaggerIsAPermutationOfStartAndStable() {
        val a = splitTextDelays(8, StaggerFrom.Random, 40f, 7)
        val b = splitTextDelays(8, StaggerFrom.Random, 40f, 7)
        assertEquals(a.toList(), b.toList())
        a.sorted().forEachIndexed { i, v -> near(v, i * 40f) }
    }

    @Test fun degenerateStaggers() {
        assertEquals(0, splitTextDelays(0, StaggerFrom.Start, 50f, 0).size)
        near(splitTextDelays(1, StaggerFrom.Center, 50f, 0)[0], 0f)
        near(splitTextDelays(3, StaggerFrom.Start, 0f, 0).max(), 0f)
        val two = splitTextDelays(2, StaggerFrom.Center, 50f, 0)
        near(two[0], 0f); near(two[1], 0f)
    }

    // ---------------------------------------------------------------- specs

    @Test fun risePresetFromTo() {
        val s = buildSplitTextSpec(SplitTextEffect.Rise, 48f, 2f, false, null, null)
        near(s.from[CH_OPACITY], 0f); near(s.from[CH_Y], 80f) // 40 dp at density 2
        near(s.to[CH_OPACITY], 1f); near(s.to[CH_Y], 0f)
    }

    @Test fun presetsUseEmRelativeValues() {
        val flip = buildSplitTextSpec(SplitTextEffect.Flip, 50f, 1f, false, null, null)
        near(flip.from[CH_ROT_X], -90f); near(flip.perspectivePx, 300f); near(flip.originY, 1f)
        val skew = buildSplitTextSpec(SplitTextEffect.Skew, 50f, 1f, false, null, null)
        near(skew.from[CH_X], -20f); near(skew.from[CH_SKEW_X], -24f)
        val wave = buildSplitTextSpec(SplitTextEffect.Wave, 50f, 1f, false, null, null)
        near(wave.from[CH_Y], 22.5f)
        val pop = buildSplitTextSpec(SplitTextEffect.Pop, 50f, 1f, false, null, null)
        near(pop.from[CH_SCALE], 0f)
        val blur = buildSplitTextSpec(SplitTextEffect.Blur, 50f, 1f, false, null, null)
        near(blur.from[CH_BLUR], 7f) // round(50 * 1.4) / 10
    }

    @Test fun customSnapshotsReplacePresetWithGsapDefaults() {
        val onlyTo = buildSplitTextSpec(SplitTextEffect.Flip, 48f, 1f, true, null, SplitTextKeyframe(opacity = 0.8f))
        near(onlyTo.from[CH_OPACITY], 0f); near(onlyTo.from[CH_Y], 40f); near(onlyTo.to[CH_OPACITY], 0.8f)
        near(onlyTo.perspectivePx, 0f)
        val both = buildSplitTextSpec(
            SplitTextEffect.Rise, 48f, 2f, true,
            SplitTextKeyframe(opacity = 0f, x = 10.dp, scale = 0.5f, rotation = 30f), null,
        )
        near(both.from[CH_X], 20f); near(both.from[CH_SCALE], 0.5f); near(both.from[CH_ROT_Z], 30f)
        near(both.from[CH_Y], 0f)
    }

    @Test fun easingsMatchReference() {
        near(WaveEase.transform(0f), 0f); near(WaveEase.transform(1f), 1f)
        assertTrue((1..99).any { WaveEase.transform(it / 100f) > 1.01f }, "wave overshoots past 1")
        near(ExpoOut.transform(0f), 0f); near(ExpoOut.transform(1f), 1f)
        near(ExpoOut.transform(0.5f), 1f - 1f / 32f)
        near(defaultEasing(SplitTextEffect.Pop).transform(1f), 1f)
        assertTrue(defaultEasing(SplitTextEffect.Pop).transform(0.7f) > 1f, "back.out overshoots")
    }

    // ---------------------------------------------------------------- timing

    @Test fun exitDurationFollowsReferenceFormula() {
        near(timing(duration = 1250f).exitMillis, 687.5f)
        near(timing(duration = 400f).exitMillis, 350f)
    }

    @Test fun revealProgressesPerUnitWithItsDelay() {
        val t = timing()
        val u = UnitState()
        t.evaluate(-100f, 0f, false, u); near(u.mix, 0f)
        t.evaluate(500f, 0f, false, u); near(u.mix, power3Out.transform(0.5f))
        t.evaluate(500f, 200f, false, u); near(u.mix, power3Out.transform(0.3f))
        t.evaluate(150f, 200f, false, u); near(u.mix, 0f)
        t.evaluate(5000f, 200f, false, u); near(u.mix, 1f)
    }

    @Test fun accentFlashStartsAt30PercentAndEndsWithTheReveal() {
        val t = timing(duration = 1000f)
        val u = UnitState()
        t.evaluate(100f, 0f, false, u); near(u.accent, 1f)
        t.evaluate(300f, 0f, false, u); near(u.accent, 1f)
        t.evaluate(650f, 0f, false, u); near(u.accent, 0.5f) // halfway through power1.inOut
        t.evaluate(1000f, 0f, false, u); near(u.accent, 0f)
    }

    @Test fun onceRunIsRestedAfterRevealSpan() {
        val t = timing()
        assertEquals(1200f, t.revealSpan); assertEquals(1200f, t.end(false))
        assertTrue(!t.isRested(1199f, false)); assertTrue(t.isRested(1200f, false))
        assertEquals(0, t.completedReveals(1199f, false)); assertEquals(1, t.completedReveals(1200f, false))
    }

    @Test fun replayRunExitsThenRevealsEachUnitSequentially() {
        val t = timing(duration = 1000f, spread = 0f)
        val u = UnitState()
        val e = t.exitMillis
        near(e, 550f)
        t.evaluate(0f, 0f, true, u); near(u.mix, 1f)
        t.evaluate(e / 2f, 0f, true, u); near(u.mix, 1f - TextEasing.powerIn(2).transform(0.5f))
        t.evaluate(e, 0f, true, u); near(u.mix, 0f)
        t.evaluate(e + 500f, 0f, true, u); near(u.mix, power3Out.transform(0.5f))
        near(t.end(true), e + 1000f)
        assertTrue(!t.isRested(e + 500f, true)); assertTrue(t.isRested(e + 1000f, true))
    }

    @Test fun staggeredUnitStartsExitLaterAndRevealsRightAfterItsOwnExit() {
        val t = timing(duration = 1000f, spread = 200f)
        val u = UnitState()
        t.evaluate(100f, 200f, true, u); near(u.mix, 1f) // not exiting yet
        t.evaluate(200f + t.exitMillis, 200f, true, u); near(u.mix, 0f)
        t.evaluate(200f + t.exitMillis + 1000f, 200f, true, u); near(u.mix, 1f)
    }

    @Test fun loopRepeatsCyclesAfterTheHold() {
        val t = timing(duration = 1000f, spread = 0f, iterations = Int.MAX_VALUE, hold = 1500f)
        val u = UnitState()
        assertTrue(t.isInfinite)
        // first cycle starts after reveal (1000) + hold (1500)
        t.evaluate(2400f, 0f, false, u); near(u.mix, 1f)
        assertTrue(t.isRested(2400f, false))
        t.evaluate(2500f + t.exitMillis, 0f, false, u); near(u.mix, 0f)
        assertTrue(!t.isRested(2500f + t.exitMillis, false))
        // second cycle starts one period later
        val period = t.cycleSpan + 1500f
        t.evaluate(2500f + period + t.exitMillis, 0f, false, u); near(u.mix, 0f)
        assertEquals(1, t.completedReveals(2400f, false))
        assertEquals(2, t.completedReveals(2500f + t.cycleSpan, false))
        assertEquals(3, t.completedReveals(2500f + period + t.cycleSpan, false))
    }

    @Test fun finiteRepeatEndsAfterLastCycle() {
        val t = timing(duration = 1000f, spread = 0f, iterations = 3, hold = 500f)
        val period = t.cycleSpan + 500f
        near(t.end(false), 1000f + 500f + period + t.cycleSpan)
        assertEquals(3, t.completedReveals(t.end(false), false))
        assertEquals(3, t.completedReveals(t.end(false) + 10_000f, false))
        assertTrue(t.isRested(t.end(false) + 10_000f, false))
    }

    // ---------------------------------------------------------------- hover spring

    @Test fun liftTargetIsGaussianAroundThePointer() {
        val em = 40f
        near(SplitTextHover.liftTarget(100f, 50f, 100f, 50f, em), -8f)
        val far = SplitTextHover.liftTarget(500f, 50f, 100f, 50f, em)
        assertTrue(abs(far) < 0.01f)
        assertTrue(abs(SplitTextHover.liftTarget(120f, 50f, 100f, 50f, em)) < 8f)
    }

    @Test fun liftSpringConvergesAndSettles() {
        val h = SplitTextHover()
        val cx = floatArrayOf(100f); val cy = floatArrayOf(50f)
        repeat(240) { h.step(SplitTextHoverEffect.Lift, 0.016f, 1, cx, cy, true, 100f, 50f, 40f) }
        assertTrue(abs(h.offsets[0] + 8f) < 0.2f, "settled near the lift target, was ${h.offsets[0]}")
        repeat(400) { h.step(SplitTextHoverEffect.Lift, 0.016f, 1, cx, cy, false, 0f, 0f, 40f) }
        near(h.offsets[0], 0f, 0.05f)
        assertTrue(!h.moving)
    }

    @Test fun waveKicksRippleOutwardFromThePointer() {
        val h = SplitTextHover()
        val cx = floatArrayOf(0f, 360f)
        val cy = floatArrayOf(0f, 0f)
        h.onEnter(SplitTextHoverEffect.Wave, cx, 2, 0f, 40f)
        // speed = 9 em/s = 360 px/s -> the far unit is kicked 1 s later
        var farMovedBeforeKick = false
        repeat(20) {
            h.step(SplitTextHoverEffect.Wave, 0.016f, 2, cx, cy, true, 0f, 0f, 40f)
            if (abs(h.offsets[1]) > 0.0001f) farMovedBeforeKick = true
        }
        assertTrue(abs(h.offsets[0]) > 0f, "near unit moved")
        assertTrue(!farMovedBeforeKick, "far unit still at rest")
        repeat(80) { h.step(SplitTextHoverEffect.Wave, 0.016f, 2, cx, cy, true, 0f, 0f, 40f) }
        assertTrue(abs(h.offsets[1]) > 0f || h.moving, "far unit kicked later")
    }
}
