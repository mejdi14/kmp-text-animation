package io.github.mejdi14.textmotion.internal

import androidx.compose.animation.core.RepeatMode
import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.ScrollRange
import io.github.mejdi14.textmotion.Stagger
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextEasing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InfrastructureTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "$a != $b")

    @Test fun fallbackGraphemes() {
        assertContentEquals(intArrayOf(0, 1, 2, 3), fallbackGraphemeBoundaries("abc"))
        // e + combining acute
        assertContentEquals(intArrayOf(0, 2, 3), fallbackGraphemeBoundaries("éx"))
        // family emoji ZWJ sequence = one cluster
        val family = "👨‍👩‍👧"
        assertContentEquals(intArrayOf(0, family.length), fallbackGraphemeBoundaries(family))
        // two flags
        val flags = "🇫🇷🇯🇵"
        assertContentEquals(intArrayOf(0, 4, 8), fallbackGraphemeBoundaries(flags))
        // thumbs up + skin tone
        assertContentEquals(intArrayOf(0, 4), fallbackGraphemeBoundaries("👍🏽"))
    }

    @Test fun platformGraphemesAgreeOnEmoji() {
        val s = "a👍🏽b"
        assertEquals(listOf("a", "👍🏽", "b"), graphemes(s))
    }

    @Test fun shapingDetection() {
        assertTrue(needsShapingContext("مرحبا"))
        assertTrue(needsShapingContext("नमस्ते"))
        assertFalse(needsShapingContext("Hello 👋"))
        assertTrue(containsRtl("שלום"))
    }

    @Test fun staggerOrders() {
        val s = Stagger(10, StaggerFrom.Start)
        assertEquals(30, s.delayFor(3, 5))
        assertEquals(10, Stagger(10, StaggerFrom.End).delayFor(3, 5))
        assertEquals(0, Stagger(10, StaggerFrom.Center).delayFor(2, 5))
        assertEquals(20, Stagger(10, StaggerFrom.Center).delayFor(0, 5))
        val r = Stagger(10, StaggerFrom.Random, seed = 3)
        assertEquals((0 until 6).toSet(), (0 until 6).map { r.rank(it, 6) }.toSet())
    }

    @Test fun repeatProgress() {
        val once = Repeat.Once
        near(0.5f, once.progressAt(50f, 100f))
        near(1f, once.progressAt(500f, 100f))
        val rev = Repeat(3, RepeatMode.Reverse)
        near(0.75f, rev.progressAt(125f, 100f))
        near(1f, rev.progressAt(1000f, 100f))
        val delayed = Repeat(2, RepeatMode.Restart, delayBetweenMillis = 50)
        near(1f, delayed.progressAt(120f, 100f))
        near(0.1f, delayed.progressAt(160f, 100f))
        assertEquals(250f, delayed.totalMillis(100f))
    }

    @Test fun easings() {
        near(0f, TextEasing.powerOut(3).transform(0f)); near(1f, TextEasing.powerOut(3).transform(1f))
        near(1f, TextEasing.backOut().transform(1f))
        assertTrue(TextEasing.backOut().transform(0.7f) > 1f)
        near(0.5f, TextEasing.powerInOut(2).transform(0.5f))
    }

    @Test fun scrollProgress() {
        val v = ViewportPosition().apply { viewportTop = 0f; viewportHeight = 1000f; height = 100f }
        v.top = 1000f; near(0f, v.progress(ScrollRange.FullPass))
        v.top = -100f; near(1f, v.progress(ScrollRange.FullPass))
        v.top = 450f; near(0.5f, v.progress(ScrollRange.FullPass))
    }

    @Test fun transform3dIdentityAndPivot() {
        val t = Transform3D()
        val m = t.set(pivotX = 50f, pivotY = 50f)
        val p = m.map(androidx.compose.ui.geometry.Offset(10f, 20f))
        near(10f, p.x); near(20f, p.y)
        val r = t.set(pivotX = 50f, pivotY = 50f, rotationZ = 90f)
        val q = r.map(androidx.compose.ui.geometry.Offset(60f, 50f))
        near(50f, q.x, 1e-3f); near(60f, q.y, 1e-3f)
        // rotateX 60° with perspective keeps pivot fixed and shrinks y extent
        val px = t.set(pivotX = 0f, pivotY = 0f, rotationX = 60f, perspective = 500f)
        val top = px.map(androidx.compose.ui.geometry.Offset(0f, 100f))
        near(50f / (1f - 100f * 0.8660254f / 500f), top.y, 1e-2f) // CSS: bottom edge tilts toward the viewer
    }

    @Test fun noiseRange() {
        for (i in 0 until 100) {
            val n = valueNoise2(i * 0.37f, i * 0.11f)
            assertTrue(n >= 0f && n < 1f)
        }
    }
}
