package io.github.mejdi14.textmotion.internal.truefocus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.mejdi14.textmotion.TrueFocusEasing
import io.github.mejdi14.textmotion.TrueFocusFrameStyle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrueFocusLogicTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = assertTrue(kotlin.math.abs(a - b) <= eps, "expected $b but was $a")

    @Test fun splitDropsBlankWords() {
        assertEquals(listOf("True", "Focus"), splitFocusWords("True  Focus ", " "))
        assertEquals(listOf("a", "b c"), splitFocusWords("a, ,b c", ","))
    }

    @Test fun intervalIsDurationPlusPause() = assertEquals(1500f, focusIntervalMillis(500, 1000))

    @Test fun sequentialWraps() {
        assertEquals(1, nextFocusIndex(0, 3, false, 0f))
        assertEquals(0, nextFocusIndex(2, 3, false, 0f))
    }

    @Test fun randomNeverRepeatsCurrent() {
        for (cur in 0 until 4) for (r in listOf(0f, 0.2f, 0.5f, 0.99f)) {
            val next = nextFocusIndex(cur, 4, true, r)
            assertTrue(next != cur && next in 0 until 4)
        }
        assertEquals(0, nextFocusIndex(0, 1, true, 0.5f))
    }

    @Test fun cycleFiresEveryInterval() {
        val c = FocusCycle(1500f, false)
        assertEquals(-1, c.tick(1000f, 0, 3))
        assertEquals(1, c.tick(1500f, 0, 3))
        assertEquals(-1, c.tick(2000f, 1, 3))
        assertEquals(2, c.tick(3000f, 1, 3))
        c.reset(3100f)
        assertEquals(-1, c.tick(4000f, 2, 3))
        assertEquals(-1, FocusCycle(10f, false).tick(100f, 0, 1))
    }

    @Test fun segmentOwnersHandleMultiTokenWords() {
        assertContentEquals(intArrayOf(0, 1, 1, 2), segmentOwners(listOf("a", "b c", "d")))
    }

    @Test fun hitTest() {
        val rects = listOf(Rect(0f, 0f, 10f, 10f), Rect(20f, 0f, 30f, 10f))
        assertEquals(0, hitTestWord(rects, Offset(5f, 5f)))
        assertEquals(1, hitTestWord(rects, Offset(20f, 9f)))
        assertEquals(-1, hitTestWord(rects, Offset(15f, 5f)))
        assertEquals(-1, hitTestWord(rects, Offset(10f, 5f)))
    }

    @Test fun scaledFrameKeepsCentre() {
        val w = Rect(10f, 20f, 50f, 40f)
        val f = scaledFrame(w, 1.5f)
        near(f.center.x, w.center.x); near(f.center.y, w.center.y)
        near(f.width, 60f); near(f.height, 30f)
        assertEquals(w, scaledFrame(w, 1f))
    }

    @Test fun bracketCornersFollowReferenceGeometry() {
        val c = bracketCorners(Rect(100f, 50f, 200f, 90f), cornerSize = 16f, gap = 10f)
        assertContentEquals(floatArrayOf(90f, 40f, 194f, 40f, 90f, 84f, 194f, 84f), c)
    }

    @Test fun styleRects() {
        val f = Rect(100f, 50f, 200f, 90f)
        assertEquals(Rect(90f, 40f, 210f, 100f), styleRect(TrueFocusFrameStyle.Box, f, 10f, 3f))
        assertEquals(Rect(100f, 94f, 200f, 97f), styleRect(TrueFocusFrameStyle.Underline, f, 10f, 3f))
        val h = styleRect(TrueFocusFrameStyle.Highlight, f, 10f, 3f)
        near(h.left, 100f - 40f * 0.14f); near(h.height, 40f * 0.92f); near(h.top, 50f + 40f * 0.04f)
    }

    @Test fun frameInterpolation() {
        val r = lerpRect(Rect(0f, 0f, 10f, 10f), Rect(100f, 20f, 130f, 50f), 0.5f)
        assertEquals(Rect(50f, 10f, 70f, 30f), r)
    }

    @Test fun smoothTweenEndpoints() {
        near(FrameMotion.progress(TrueFocusEasing.Smooth, 0f, 500f), 0f)
        near(FrameMotion.progress(TrueFocusEasing.Smooth, 250f, 500f), 0.5f)
        near(FrameMotion.progress(TrueFocusEasing.Smooth, 900f, 500f), 1f)
    }

    @Test fun springOvershootsThenSettles() {
        var peak = 0f
        for (ms in 0..1500 step 10) peak = maxOf(peak, FrameMotion.progress(TrueFocusEasing.Spring, ms.toFloat(), 500f))
        assertTrue(peak > 1.02f, "spring should overshoot, peak=$peak")
        near(FrameMotion.progress(TrueFocusEasing.Spring, 5000f, 500f), 1f, 0.01f)
        assertTrue(FrameMotion.isDone(TrueFocusEasing.Spring, 5000f, 500f))
    }

    @Test fun focusLevelsCrossFadeAndInterrupt() {
        val s = FocusState(3)
        val lin = { t: Float -> t }
        near(s.level(0, 0f, 100f, lin), 1f)
        s.retarget(1, 1000f, 100f, lin, null)
        near(s.level(0, 1050f, 100f, lin), 0.5f)
        near(s.level(1, 1050f, 100f, lin), 0.5f)
        s.retarget(2, 1050f, 100f, lin, null) // interrupted halfway
        near(s.level(0, 1050f, 100f, lin), 0.5f)
        near(s.level(2, 1150f, 100f, lin), 1f)
        near(s.level(1, 1150f, 100f, lin), 0f)
    }
}
