package io.github.mejdi14.textmotion.internal.pressure

import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressureMathTest {
    private val eps = 1e-4f

    @Test fun linearFalloff() {
        assertEquals(1f, PressureMath.cursorPressure(0f, 200f))
        assertEquals(0.5f, PressureMath.cursorPressure(100f, 200f))
        assertEquals(0f, PressureMath.cursorPressure(250f, 200f))
        assertEquals(0f, PressureMath.cursorPressure(5f, 0f)) // maxDist floored at 1
    }

    @Test fun axisMappingMatchesReference() {
        assertEquals(5f, PressureMath.width(0f, true)); assertEquals(205f, PressureMath.width(1f, true))
        assertEquals(105f, PressureMath.width(0.5f, true)); assertEquals(100f, PressureMath.width(0.7f, false))
        assertEquals(100f, PressureMath.weight(0f, true)); assertEquals(1000f, PressureMath.weight(1f, true))
        assertEquals(550f, PressureMath.weight(0.5f, true)); assertEquals(400f, PressureMath.weight(0.9f, false))
        assertEquals(0.33f, PressureMath.italic(0.3333f, true), eps); assertEquals(0f, PressureMath.italic(1f, false))
    }

    @Test fun tintIsEaseOutQuadInPercent() {
        assertEquals(0f, PressureMath.tint(0f)); assertEquals(1f, PressureMath.tint(1f))
        assertEquals(0.75f, PressureMath.tint(0.5f), eps)
    }

    @Test fun followFactorUsesSquaredSmoothing() {
        assertEquals(1f, PressureMath.followFactor(16f, 0f))
        assertEquals(1f - exp(-16f / 250f), PressureMath.followFactor(16f, 0.5f), eps)
        // Frame time is capped at 64 ms.
        assertEquals(PressureMath.followFactor(64f, 0.5f), PressureMath.followFactor(500f, 0.5f), eps)
    }

    @Test fun idlePatterns() {
        assertEquals(1f, PressureMath.wave(0f, 0f, 1f), eps)
        assertEquals(0f, PressureMath.wave(0.5f, 0f, 1f), eps)
        assertEquals(0f, PressureMath.pulse(0.5f, 0f), eps)
        assertEquals(PressureMath.PULSE_DEPTH, PressureMath.pulse(0.5f, PressureMath.PULSE_PERIOD / 2f), eps)
        assertEquals(1f, PressureMath.idleSpread(0.5f)); assertEquals(0.2f, PressureMath.idleSpread(0.01f))
    }

    @Test fun rippleRingTravelsAndFades() {
        val span = 1000f
        // Ring front at age 0.4 s is at 500 px.
        val onRing = PressureMath.rippleSwell(500f, 0.4f, span)
        assertTrue(onRing > 0.3f, "onRing=$onRing")
        assertEquals(0f, PressureMath.rippleSwell(100f, 0.4f, span)) // far inside the ring
        assertEquals(0f, PressureMath.rippleSwell(0f, 0f, span))
        assertEquals(0f, PressureMath.rippleSwell(1625f, 1.3f, span))
        assertTrue(PressureMath.rippleSwell(1250f, 1.0f, span) < onRing)
    }

    @Test fun screenBlend() {
        assertEquals(0.75f, PressureMath.screen(0.5f, 0.5f), eps)
        assertEquals(1f, PressureMath.screen(1f, 0.2f), eps)
    }

    @Test fun flexSpaceBetweenFillsWidth() {
        val out = FloatArray(3)
        PressureMath.layoutRow(floatArrayOf(10f, 20f, 30f), 3, 100f, flex = true, gap = 0f, outX = out)
        assertEquals(listOf(0f, 30f, 70f), out.toList()) // space = 20
        PressureMath.layoutRow(floatArrayOf(10f, 20f, 30f), 3, 100f, flex = false, gap = 5f, outX = out)
        assertEquals(listOf(15f, 30f, 55f), out.toList()) // packed 70 centred
        PressureMath.layoutRow(floatArrayOf(80f, 80f), 2, 100f, flex = false, gap = 0f, outX = out)
        assertEquals(0f, out[0]) // overflow starts at the left edge
    }

    @Test fun fontSizeFromWidth() {
        assertEquals(100f, PressureMath.fontSize(500f, 10, 24f))
        assertEquals(24f, PressureMath.fontSize(50f, 10, 24f))
    }

    @Test fun stateSmoothsCursorAndSettles() {
        val s = PressureState()
        s.ensureCursor(0f, 0f)
        s.cursorX = 100f
        var moving = true
        var frames = 0
        while (moving && frames < 1000) { moving = s.step(16f, 0.5f, 1f, false, 1f); frames++ }
        assertTrue(frames in 20..600, "frames=$frames")
        assertEquals(100f, s.mouseX)
    }

    @Test fun ripplesExpireAndCapAtSix() {
        val s = PressureState()
        repeat(8) { s.addRipple(it.toFloat(), 0f) }
        assertEquals(PressureMath.MAX_RIPPLES, s.rippleCount)
        assertTrue(s.hasLiveRipples)
        repeat(100) { s.step(16f, 0f, 1f, false, 1f) }
        assertTrue(!s.hasLiveRipples)
    }

    @Test fun idleEngageBlendsTowardsPresence() {
        val s = PressureState()
        s.step(16f, 0f, present = 0f, idleEnabled = true, idleSpeed = 1f)
        assertTrue(s.isIdling)
        repeat(300) { s.step(16f, 0f, present = 1f, idleEnabled = true, idleSpeed = 1f) }
        assertEquals(1f, s.engage)
        assertTrue(!s.isIdling)
    }
}
