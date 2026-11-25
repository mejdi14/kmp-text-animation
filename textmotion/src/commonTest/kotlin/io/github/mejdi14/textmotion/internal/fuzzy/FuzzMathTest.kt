package io.github.mejdi14.textmotion.internal.fuzzy

import androidx.compose.ui.graphics.Color
import io.github.mejdi14.textmotion.FuzzyDirection
import io.github.mejdi14.textmotion.FuzzyTrigger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzMathTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    @Test fun paddingFollowsReference() {
        val h = FuzzMath.padding(30f, 0f, FuzzyDirection.Horizontal)
        near(h[0], 30f + 20f + 5f); near(h[1], 0f)
        val v = FuzzMath.padding(30f, 1f, FuzzyDirection.Vertical)
        near(v[0], 55f); near(v[1], 30f + 10f + 19f) // splitPad = ceil(15) + 4
        val b = FuzzMath.padding(30f, 1f, FuzzyDirection.Both)
        near(b[0], 55f + 19f); near(b[1], 40f)
    }

    @Test fun offsetsAreBoundedIntegersAndVary() {
        val amp = 30f
        val seen = HashSet<Int>()
        for (seed in 0 until 20) for (k in 0 until 50) {
            val o = FuzzMath.offset(k, seed, FuzzMath.SALT_ROWS, amp)
            assertTrue(abs(o) <= 15, "offset $o out of range")
            seen += o
        }
        assertTrue(seen.size > 20, "offsets should spread over the range")
        assertEquals(0, FuzzMath.offset(3, 7, 0, 0f))
        for (k in 0 until 100) assertEquals(0, FuzzMath.offset(k, 1, 0, 0.4f))
        // Deterministic per (index, seed, salt).
        assertEquals(FuzzMath.offset(5, 9, 0, amp), FuzzMath.offset(5, 9, 0, amp))
    }

    @Test fun sliceStepAndSplit() {
        assertEquals(3, FuzzMath.sliceStep(3f, 3f, 1f))
        assertEquals(3, FuzzMath.sliceStep(0.5f, 3f, 1f)) // at least 1 dp
        assertEquals(12, FuzzMath.sliceStep(3f, 3f, 4f))
        assertEquals(0, FuzzMath.splitDistance(0f, 1f, 30f, 2f))
        // round(1 * (2 + 0.5 * 30 * 0.18) * 2) = round(9.4) = 9
        assertEquals(9, FuzzMath.splitDistance(1f, 0.5f, 30f, 2f))
    }

    @Test fun inkLightness() {
        assertTrue(FuzzMath.isLightInk(listOf(Color.White)))
        assertFalse(FuzzMath.isLightInk(listOf(Color.Black)))
        assertFalse(FuzzMath.isLightInk(listOf(Color.White, Color.Black, Color.Black)))
        assertFalse(FuzzMath.isLightInk(emptyList()))
    }

    @Test fun envelopes() {
        near(FuzzMath.burstEnvelope(0f), 0f)
        near(FuzzMath.burstEnvelope(35f), 0.75f)
        near(FuzzMath.burstEnvelope(70f), 1f)
        near(FuzzMath.burstEnvelope(310f), exp(-1f))
        near(FuzzMath.burstPeak(0.5f), 1.3f)
        near(FuzzMath.burstPeak(2f), 2f)
        near(FuzzMath.introEnvelope(0f), 1f)
        near(FuzzMath.introEnvelope(1f), 0f)
        near(FuzzMath.introOpacity(0f), 0f)
        near(FuzzMath.introOpacity(0.15f), 0.75f)
        near(FuzzMath.introOpacity(0.5f), 1f)
        near(FuzzMath.coarse(0f), 1f)
        near(FuzzMath.coarse(1f), 4f)
    }

    @Test fun transitionReaches95PercentAfterDuration() {
        assertEquals(1f, FuzzMath.transition(0f, 1f, 16f, 0f))
        var v = 0f
        repeat(10) { v = FuzzMath.transition(v, 1f, 20f, 200f) }
        near(v, 0.95f, 1e-3f)
        assertEquals(1f, FuzzMath.transition(0.999f, 1f, 1f, 1000f)) // snaps
    }

    @Test fun glitchSchedule() {
        assertFalse(FuzzMath.glitchActive(0f, 2000f, 200f))
        assertFalse(FuzzMath.glitchActive(1999f, 2000f, 200f))
        assertTrue(FuzzMath.glitchActive(2000f, 2000f, 200f))
        assertTrue(FuzzMath.glitchActive(2199f, 2000f, 200f))
        assertFalse(FuzzMath.glitchActive(2200f, 2000f, 200f))
        assertTrue(FuzzMath.glitchActive(4250f, 2000f, 200f))
        assertFalse(FuzzMath.glitchActive(5000f, 2000f, 0f))
    }
}

class FuzzEngineTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(abs(a - b) < eps, "$a != $b")

    private fun settings(
        trigger: FuzzyTrigger = FuzzyTrigger.Hover,
        base: Float = 0.18f,
        fps: Int = 60,
        transition: Float = 0f,
        clickEffect: Boolean = false,
        glitch: Boolean = false,
        intro: Boolean = false,
    ) = FuzzSettings(base, 0.5f, true, trigger, fps, transition, clickEffect, glitch, 2000f, 200f, intro, 1400f)

    @Test fun hoverRaisesIntensity() {
        val e = FuzzEngine()
        val s = settings()
        assertTrue(e.frame(0f, hovering = false, settings = s))
        near(e.intensity, 0.18f)
        assertTrue(e.frame(17f, hovering = true, settings = s))
        near(e.intensity, 0.5f)
        val disabled = s.copy(enableHover = false)
        e.frame(34f, hovering = true, settings = disabled)
        near(e.intensity, 0.18f)
    }

    @Test fun alwaysAndClickTriggers() {
        val e = FuzzEngine()
        e.frame(0f, false, settings(FuzzyTrigger.Always))
        near(e.intensity, 0.5f)
        val c = FuzzEngine()
        val s = settings(FuzzyTrigger.Click)
        c.frame(0f, true, s)
        near(c.intensity, 0.18f) // hover ignored with click trigger
        c.press(0f, s)
        c.frame(70f, false, s)
        near(c.intensity, 1.3f) // burst peak at the end of the attack
        assertTrue(c.coarse > 3.9f)
        c.frame(5000f, false, s)
        near(c.intensity, 0.18f)
        near(c.coarse, 1f)
    }

    @Test fun fpsThrottleSkipsFrames() {
        val e = FuzzEngine()
        val s = settings(fps = 30)
        assertTrue(e.frame(0f, false, s))
        val seed = e.seed
        assertFalse(e.frame(16f, false, s))
        assertEquals(seed, e.seed)
        assertTrue(e.frame(33f, false, s))
        assertEquals(seed + 1, e.seed)
    }

    @Test fun zeroIntensityDrawsOnce() {
        val e = FuzzEngine()
        val s = settings(base = 0f)
        assertTrue(e.frame(0f, false, s))
        assertFalse(e.frame(20f, false, s))
        assertFalse(e.frame(40f, false, s))
    }

    @Test fun clickEffectFlashesFullIntensity() {
        val e = FuzzEngine()
        val s = settings(clickEffect = true)
        e.frame(0f, false, s)
        e.press(10f, s)
        e.frame(20f, false, s)
        near(e.intensity, 1f)
        e.frame(200f, false, s)
        near(e.intensity, 0.18f)
    }

    @Test fun glitchModeRaisesToOne() {
        val e = FuzzEngine()
        val s = settings(glitch = true)
        e.frame(0f, false, s)
        near(e.intensity, 0.18f)
        e.frame(2050f, false, s)
        near(e.intensity, 1f)
        e.frame(2300f, false, s)
        near(e.intensity, 0.18f)
    }

    @Test fun introStartsAtPeakAndSettles() {
        val e = FuzzEngine()
        val s = settings(intro = true)
        assertTrue(e.introPending(s))
        e.frame(0f, false, s)
        assertFalse(e.introPending(s))
        near(e.intensity, 2f)
        near(e.opacity, 0f)
        near(e.coarse, 4f)
        e.frame(1400f, false, s)
        near(e.intensity, 0.18f)
        near(e.opacity, 1f)
        e.reset()
        assertTrue(e.introPending(s))
    }
}
