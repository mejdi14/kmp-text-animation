package io.github.mejdi14.textmotion.internal.rotatingtext

import androidx.compose.ui.graphics.Color
import io.github.mejdi14.textmotion.RotatingTextDirection
import io.github.mejdi14.textmotion.RotatingTextEffect
import io.github.mejdi14.textmotion.RotatingTextSplit
import io.github.mejdi14.textmotion.RotatingTextStaggerFrom
import io.github.mejdi14.textmotion.RotatingTextTransition
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotatingTextMotionTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    private fun texts(split: RotatingTextSplit, s: String) = rotatingUnitRanges(s, split).map { s.substring(it[0], it[1]) }

    // ---------------------------------------------------------------- springs / transitions

    @Test fun springStartsAtZeroAndSettlesAtOne() {
        near(springValue(0f, 25f, 300f, 1f), 0f)
        near(springValue(3f, 25f, 300f, 1f), 1f)
    }

    @Test fun underdampedSpringOvershoots() {
        // damping ratio of (17, 240) is ~0.55: visible overshoot
        val peak = (1..200).maxOf { springValue(it * 0.005f, 17f, 240f, 1f) }
        assertTrue(peak > 1.05f, "peak $peak")
    }

    @Test fun overdampedAndCriticalSpringsDoNotOvershoot() {
        for (damping in listOf(2f * 17.320508f, 80f)) { // critical for k=300 is 2*sqrt(300)
            val peak = (1..400).maxOf { springValue(it * 0.005f, damping, 300f, 1f) }
            assertTrue(peak <= 1.001f, "damping $damping peak $peak")
            near(springValue(5f, damping, 300f, 1f), 1f, 1e-2f)
        }
    }

    @Test fun springIsContinuousAcrossRegimes() {
        // zeta just below, at and above 1 give almost the same curve
        val a = springValue(0.1f, 34.6f, 300f, 1f)
        val b = springValue(0.1f, 34.641f, 300f, 1f)
        val c = springValue(0.1f, 34.7f, 300f, 1f)
        near(a, b, 5e-3f); near(b, c, 5e-3f)
    }

    @Test fun springStartsFromRest() {
        // zero initial velocity: value grows quadratically
        assertTrue(springValue(0.005f, 25f, 300f, 1f) < 0.01f)
    }

    @Test fun settleTimeOfReferenceSpringsIsReasonable() {
        val s = RotatingTextTransition.Spring(25f, 300f).settleMillis()
        assertTrue(s in 300f..1200f, "settle $s")
        assertEquals(600f, RotatingTextTransition.Tween(600).settleMillis())
    }

    @Test fun tweenEvaluation() {
        val t = RotatingTextTransition.Tween(500, io.github.mejdi14.textmotion.TextEasing.Linear)
        near(t.valueAt(-5f), 0f); near(t.valueAt(250f), 0.5f); near(t.valueAt(900f), 1f)
    }

    // ---------------------------------------------------------------- presets

    @Test fun slidePresetMatchesReference() {
        val up = buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Up, 0f, null)
        near(up.from[RC_Y], 1f); near(up.exit[RC_Y], -1.2f); near(up.from[RC_OPACITY], 0f); near(up.rest[RC_OPACITY], 1f)
        val down = buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Down, 0f, null)
        near(down.from[RC_Y], -1f); near(down.exit[RC_Y], 1.2f)
    }

    @Test fun flipUsesPerspective() {
        val s = buildEffectSpec(RotatingTextEffect.Flip, RotatingTextDirection.Up, 0f, null)
        near(s.from[RC_ROT_X], -90f); near(s.exit[RC_ROT_X], 90f); near(s.perspectiveDp, 480f); near(s.originZEm, -0.45f)
    }

    @Test fun scalePreset() {
        val s = buildEffectSpec(RotatingTextEffect.Scale, RotatingTextDirection.Up, 0f, null)
        near(s.from[RC_SCALE], 0.35f); near(s.exit[RC_SCALE], 1.7f)
        val d = buildEffectSpec(RotatingTextEffect.Scale, RotatingTextDirection.Down, 0f, null)
        near(d.from[RC_SCALE], 1.7f); near(d.exit[RC_SCALE], 0.35f)
    }

    @Test fun blurEffectDefaultsToTenAndBlurPropAppliesToAnyEffect() {
        near(buildEffectSpec(RotatingTextEffect.Blur, RotatingTextDirection.Up, 0f, null).from[RC_BLUR], 10f)
        near(buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Up, 0f, null).from[RC_BLUR], 0f)
        near(buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Up, 6f, null).exit[RC_BLUR], 6f)
        near(buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Up, 6f, null).rest[RC_BLUR], 0f)
    }

    @Test fun overrideReplacesBothTransitions() {
        val t = RotatingTextTransition.Tween(100)
        val s = buildEffectSpec(RotatingTextEffect.Drop, RotatingTextDirection.Up, 0f, t)
        assertEquals(t, s.enter); assertEquals(t, s.exitTransition)
    }

    // ---------------------------------------------------------------- units

    @Test fun characterUnitsSkipWhitespaceAndKeepGraphemes() {
        assertEquals(listOf("a", "b", "c"), texts(RotatingTextSplit.Characters, "a b\nc"))
        assertEquals(listOf("e\u0301", "\uD83D\uDC4D\uD83C\uDFFD"), texts(RotatingTextSplit.Characters, "e\u0301 \uD83D\uDC4D\uD83C\uDFFD"))
    }

    @Test fun wordUnits() {
        assertEquals(listOf("Make", "it", "pop"), texts(RotatingTextSplit.Words, "  Make it\tpop "))
    }

    @Test fun lineUnits() {
        assertEquals(listOf("one two", "three"), texts(RotatingTextSplit.Lines, "one two\n\nthree"))
    }

    @Test fun customSeparator() {
        assertEquals(listOf("a", "b", "c"), texts(RotatingTextSplit.Custom(","), "a,b,,c"))
        assertEquals(listOf("x", "y"), texts(RotatingTextSplit.Custom(" | "), "x | y"))
        assertEquals(3, texts(RotatingTextSplit.Custom(""), "abc").size)
    }

    // ---------------------------------------------------------------- stagger

    @Test fun staggerFirstAndLast() {
        val f = rotatingDelays(4, RotatingTextStaggerFrom.First, 50f, 0)
        assertEquals(listOf(0f, 50f, 100f, 150f), f.toList())
        val l = rotatingDelays(4, RotatingTextStaggerFrom.Last, 50f, 0)
        assertEquals(listOf(150f, 100f, 50f, 0f), l.toList())
    }

    @Test fun staggerCenterUsesFloorHalf() {
        val c = rotatingDelays(5, RotatingTextStaggerFrom.Center, 10f, 0)
        assertEquals(listOf(20f, 10f, 0f, 10f, 20f), c.toList())
        val even = rotatingDelays(4, RotatingTextStaggerFrom.Center, 10f, 0)
        assertEquals(listOf(20f, 10f, 0f, 10f), even.toList())
    }

    @Test fun staggerFromIndex() {
        val d = rotatingDelays(5, RotatingTextStaggerFrom.Index(1), 10f, 0)
        assertEquals(listOf(10f, 0f, 10f, 20f, 30f), d.toList())
        // out-of-range index still yields a distance field
        assertEquals(listOf(70f, 60f), rotatingDelays(2, RotatingTextStaggerFrom.Index(7), 10f, 0).toList())
    }

    @Test fun staggerRandomIsPermutationAndDeterministic() {
        val a = rotatingDelays(9, RotatingTextStaggerFrom.Random, 10f, 3)
        assertEquals((0 until 9).map { it * 10f }, a.sorted())
        assertEquals(a.toList(), rotatingDelays(9, RotatingTextStaggerFrom.Random, 10f, 3).toList())
        assertFalse(a.toList() == rotatingDelays(9, RotatingTextStaggerFrom.Random, 10f, 4).toList())
    }

    @Test fun noStaggerMeansNoDelay() {
        assertEquals(listOf(0f, 0f, 0f), rotatingDelays(3, RotatingTextStaggerFrom.First, 0f, 0).toList())
    }

    // ---------------------------------------------------------------- highlight colour

    @Test fun highlightTextColorFollowsLuminance() {
        assertEquals(Color.White, highlightTextColorFor(Color(0xFFA855F7))) // purple: L ~ 0.21
        assertEquals(Color(0xFF0B0B0C), highlightTextColorFor(Color(0xFFFACC15))) // yellow
        assertEquals(Color.White, highlightTextColorFor(Color.Black))
        near(relativeLuminance(Color.White), 1f, 1e-3f)
    }

    // ---------------------------------------------------------------- machine

    private fun machine(
        counts: IntArray = intArrayOf(3, 2, 4),
        stagger: Float = 0f,
        interval: Float = 2000f,
        loop: Boolean = true,
        enter: Boolean = false,
        auto: Boolean = false,
    ): RotatingTextMachine {
        val plan = RotatingTextPlan(
            counts, buildEffectSpec(RotatingTextEffect.Slide, RotatingTextDirection.Up, 0f, null),
            RotatingTextStaggerFrom.First, stagger, RotatingTextTransition.Spring(25f, 300f),
        )
        return RotatingTextMachine().also {
            it.intervalMillis = interval; it.loop = loop; it.autoEnabled = auto
            it.setPlan(plan, enter)
            it.setSizes(floatArrayOf(100f, 20f, 50f, 20f, 200f, 20f))
        }
    }

    private fun RotatingTextMachine.run(totalMs: Float, step: Float = 16f) {
        var t = 0f
        while (t < totalMs) { advance(step); t += step }
    }

    @Test fun nextAndPreviousWrapOnlyWithLoop() {
        val m = machine()
        assertEquals(1, m.nextIndex())
        m.jumpTo(2)
        assertEquals(0, m.nextIndex())
        m.loop = false
        assertEquals(2, m.nextIndex())
        assertEquals(1, m.previousIndex())
        m.jumpTo(0)
        assertEquals(0, m.previousIndex())
        m.loop = true
        assertEquals(2, m.previousIndex())
    }

    @Test fun jumpClampsAndSameIndexIsNoOp() {
        val m = machine()
        assertFalse(m.requestIndex(0))
        assertTrue(m.requestIndex(99))
        assertEquals(2, m.active)
        assertEquals(0, m.shown) // old text still exiting
    }

    @Test fun waitModeExitsThenEnters() {
        val m = machine()
        m.jumpTo(1)
        assertEquals(RotatingTextMachine.Phase.Exiting, m.phase)
        assertEquals(0, m.shown)
        m.run(2000f, 16f)
        assertEquals(1, m.shown)
        assertEquals(RotatingTextMachine.Phase.Rest, m.phase)
    }

    @Test fun enterStartsHiddenAndExitStartsFromRest() {
        val m = machine()
        val out = FloatArray(RCH)
        m.pose(0, out)
        near(out[RC_OPACITY], 1f); near(out[RC_Y], 0f)
        m.jumpTo(1)
        m.pose(0, out)
        near(out[RC_OPACITY], 1f) // exit has not progressed yet
        m.run(2000f)
        assertEquals(RotatingTextMachine.Phase.Rest, m.phase)
        m.pose(0, out)
        near(out[RC_OPACITY], 1f)
    }

    @Test fun initialEnterAnimatesFromHidden() {
        val m = machine(enter = true)
        assertEquals(RotatingTextMachine.Phase.Entering, m.phase)
        val out = FloatArray(RCH)
        m.pose(0, out)
        near(out[RC_OPACITY], 0f); near(out[RC_Y], 1f)
        m.run(2000f)
        assertEquals(RotatingTextMachine.Phase.Rest, m.phase)
    }

    @Test fun staggerDelaysUnitsInsideAPhase() {
        val m = machine(stagger = 100f, enter = true)
        m.advance(50f)
        val a = FloatArray(RCH); val b = FloatArray(RCH)
        m.pose(0, a); m.pose(2, b)
        assertTrue(a[RC_OPACITY] > 0f)
        near(b[RC_OPACITY], 0f) // third unit starts after 200 ms
    }

    @Test fun interruptedEnterExitsFromCurrentPose() {
        val m = machine(enter = true)
        m.advance(60f)
        val before = FloatArray(RCH); m.pose(0, before)
        m.jumpTo(2)
        val after = FloatArray(RCH); m.pose(0, after)
        near(after[RC_Y], before[RC_Y]); near(after[RC_OPACITY], before[RC_OPACITY])
    }

    @Test fun autoRotationFiresAfterInterval() {
        val m = machine(interval = 1000f, auto = true)
        val changes = ArrayList<Int>()
        m.onActiveChanged = { changes.add(it) }
        m.run(990f, 10f)
        assertEquals(0, m.active)
        m.run(30f, 10f)
        assertEquals(listOf(1), changes)
        // timer is re-armed on the index change: the next change is one interval later
        m.run(1000f, 10f)
        assertEquals(listOf(1, 2), changes)
    }

    @Test fun autoRotationStopsAtTheEndWithoutLoop() {
        val m = machine(counts = intArrayOf(1, 1), interval = 500f, loop = false, auto = true)
        m.run(5000f)
        assertEquals(1, m.active)
        assertEquals(Float.POSITIVE_INFINITY, m.millisUntilAuto())
        m.jumpTo(0) // manual navigation re-arms auto
        assertTrue(m.millisUntilAuto().isFinite())
    }

    @Test fun disabledAutoDoesNotRotate() {
        val m = machine(interval = 100f)
        m.run(1000f)
        assertEquals(0, m.active)
        assertEquals(Float.POSITIVE_INFINITY, m.millisUntilAuto())
    }

    @Test fun rapidRequestsEndOnTheLatestText() {
        val m = machine()
        m.jumpTo(1); m.advance(50f); m.jumpTo(2); m.jumpTo(0); m.jumpTo(2)
        m.run(3000f)
        assertEquals(2, m.shown)
        assertEquals(2, m.active)
    }

    @Test fun restartShowsFirstTextEntering() {
        val m = machine()
        m.jumpTo(2); m.run(3000f)
        m.restart()
        assertEquals(0, m.active); assertEquals(0, m.shown)
        assertEquals(RotatingTextMachine.Phase.Entering, m.phase)
    }

    @Test fun containerSizeAnimatesToTheNewText() {
        val m = machine()
        near(m.currentWidth(), 100f)
        m.jumpTo(2)
        m.run(2000f) // exit, then enter begins the size spring
        assertTrue(m.currentWidth() > 100f, "width ${m.currentWidth()}")
        m.run(2000f)
        near(m.currentWidth(), 200f, 0.5f)
        assertFalse(m.isSizeAnimating)
    }

    @Test fun sizeSnapsOnFirstMeasureAndOnConstraintChange() {
        val m = machine()
        m.setSizes(floatArrayOf(80f, 20f, 50f, 20f, 200f, 20f))
        near(m.currentWidth(), 80f)
    }
}
