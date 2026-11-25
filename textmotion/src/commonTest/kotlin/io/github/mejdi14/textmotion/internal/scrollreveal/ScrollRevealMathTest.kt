package io.github.mejdi14.textmotion.internal.scrollreveal

import io.github.mejdi14.textmotion.ScrollRevealEffect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollRevealMathTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "$a != $b")

    private fun look(effect: ScrollRevealEffect, t: Float, highlight: Boolean = false, offset: Float = 0f): PieceLook =
        PieceLook().also { computeLook(effect, t, 0.1f, offset, 4f, highlight, 20f, 30f, it) }

    @Test fun scrubTimingMatchesReference() {
        val timing = RevealTiming.scrub(0.05f)
        val n = 10
        near(timing.total(n), 0.5f + 9 * 0.05f)
        // piece 0 finishes at p = span / total, last piece exactly at p = 1
        near(timing.local(0.5f / timing.total(n), 0, n), 1f)
        near(timing.local(1f, n - 1, n), 1f)
        near(timing.local(0f, 0, n), 0f)
        // the last piece has not started before (n-1)*gap/total
        near(timing.local((n - 1) * 0.05f / timing.total(n) - 0.001f, n - 1, n), 0f)
        assertTrue(timing.local(0.3f, 5, n) < timing.local(0.3f, 2, n))
    }

    @Test fun enterTimingUsesDurationAsSpan() {
        val timing = RevealTiming.enter(800, 0.05f)
        near(timing.span, 0.8f)
        near(timing.total(5), 0.8f + 4 * 0.05f)
        // a very short duration is floored at 0.05 s
        near(RevealTiming.enter(0, 0f).span, 0.05f)
    }

    @Test fun pieceOrdersByLineShareRanks() {
        assertContentEquals(intArrayOf(0, 0, 1, 1, 1, 2), pieceOrders(intArrayOf(0, 0, 1, 1, 1, 3), byLine = true))
        assertContentEquals(intArrayOf(0, 1, 2), pieceOrders(intArrayOf(0, 0, 1), byLine = false))
        assertEquals(3, pieceCount(intArrayOf(0, 0, 1, 1, 1, 2)))
        assertEquals(0, pieceCount(IntArray(0)))
    }

    @Test fun easingsHitTheirEndpoints() {
        for (o in listOf(1.2f, 1.6f)) {
            near(easeBack(0f, o), 0f)
            near(easeBack(1f, o), 1f)
            assertTrue((1..99).any { easeBack(it / 100f, o) > 1f }, "overshoot $o")
        }
        near(easeOutCubic(0f), 0f)
        near(easeOutCubic(1f), 1f)
    }

    @Test fun glowIsABellCurve() {
        near(glowAt(0f), 0f)
        near(glowAt(1f), 0f)
        near(glowAt(0.35f), 1f)
        assertTrue(glowAt(0.1f) in 0f..1f && glowAt(0.7f) in 0f..1f)
    }

    @Test fun fadeStartsAtBaseOpacityAndBlur() {
        val start = look(ScrollRevealEffect.Fade, 0f)
        near(start.opacity, 0.1f)
        near(start.blurPx, 4f)
        val mid = look(ScrollRevealEffect.Fade, 0.5f)
        near(mid.opacity, 0.1f + 0.9f * 0.5f)
        near(mid.blurPx, 2f)
        val end = look(ScrollRevealEffect.Fade, 1f)
        near(end.opacity, 1f)
        near(end.blurPx, 0f)
    }

    @Test fun baseOffsetSettles() {
        near(look(ScrollRevealEffect.Fade, 0f, offset = 12f).translateY, 12f)
        near(look(ScrollRevealEffect.Fade, 0.5f, offset = 12f).translateY, 6f)
        near(look(ScrollRevealEffect.Fade, 1f, offset = 12f).translateY, 0f)
    }

    @Test fun riseIsMaskedAndTravelsOneLineBoxPlusFifthEm() {
        val start = look(ScrollRevealEffect.Rise, 0f)
        near(start.translateY, 30f + 0.2f * 20f)
        assertTrue(start.masked)
        val end = look(ScrollRevealEffect.Rise, 1f)
        near(end.translateY, 0f)
        assertTrue(!end.masked)
    }

    @Test fun flipAndScaleStartFolded() {
        val flip = look(ScrollRevealEffect.Flip, 0f)
        near(flip.rotationX, 90f)
        near(look(ScrollRevealEffect.Flip, 1f).rotationX, 0f)
        val scale = look(ScrollRevealEffect.Scale, 0f)
        near(scale.scale, 0.35f)
        near(look(ScrollRevealEffect.Scale, 1f).scale, 1f)
    }

    @Test fun highlightBoostsOpacityEarlyAndGlows() {
        val plain = look(ScrollRevealEffect.Fade, 0.2f)
        val lit = look(ScrollRevealEffect.Fade, 0.2f, highlight = true)
        assertTrue(lit.opacity > plain.opacity)
        assertTrue(lit.glow > 0f)
        near(look(ScrollRevealEffect.Fade, 1f, highlight = true).glow, 0f)
    }

    @Test fun enterRotationRunsForAtMost1400ms() {
        near(enterRotationProgress(0f, 5f), 0f)
        near(enterRotationProgress(1.4f, 5f), 1f)
        near(enterRotationProgress(0.5f, 0.5f), 1f)
        assertTrue(enterRotationProgress(0.7f, 5f) > 0.8f) // power3.out is front loaded
    }

    @Test fun smoothingApproachesTargetExponentially() {
        near(smoothStep(0f, 1f, 16f, 0f), 1f)
        var v = 0f
        repeat(60) { v = smoothStep(v, 1f, 16f, 1000f) } // 960 ms with a 1 s smoothing
        assertTrue(v > 0.9f && v < 1f, "v=$v")
    }
}
