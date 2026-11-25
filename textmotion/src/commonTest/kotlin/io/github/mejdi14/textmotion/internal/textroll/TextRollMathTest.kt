package io.github.mejdi14.textmotion.internal.textroll

import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.TextRollDefaults
import io.github.mejdi14.textmotion.TextRollKeyframe
import io.github.mejdi14.textmotion.TextRollVariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextRollMathTest {
    private fun near(expected: Float, actual: Float, eps: Float = 1e-3f) =
        assertTrue(kotlin.math.abs(expected - actual) < eps, "expected $expected but was $actual")

    @Test fun defaultDelaysMatchReference() {
        val enter = TextRollMath.delays(4, TextRollDefaults.EnterDelay)
        val exit = TextRollMath.delays(4, TextRollDefaults.ExitDelay)
        assertEquals(listOf(0f, 100f, 200f, 300f), enter.toList())
        assertEquals(listOf(200f, 300f, 400f, 500f), exit.toList())
    }

    @Test fun iterationEndsWithLastExitLayer() {
        val enter = TextRollMath.delays(5, TextRollDefaults.EnterDelay)
        val exit = TextRollMath.delays(5, TextRollDefaults.ExitDelay)
        // last exit delay 0.6 s + 0.5 s duration
        assertEquals(1100f, TextRollMath.iterationMillis(enter, exit, 500f))
        assertEquals(0f, TextRollMath.firstDelay(enter, exit))
        assertEquals(0f, TextRollMath.iterationMillis(FloatArray(0), FloatArray(0), 500f))
    }

    @Test fun layerProgressHoldsBeforeDelayAndAfterEnd() {
        assertEquals(0f, TextRollMath.layerProgress(100f, 200f, 500f))
        near(0.5f, TextRollMath.layerProgress(450f, 200f, 500f))
        assertEquals(1f, TextRollMath.layerProgress(900f, 200f, 500f))
        assertEquals(0f, TextRollMath.layerProgress(10f, 20f, 0f))
        assertEquals(1f, TextRollMath.layerProgress(20f, 20f, 0f))
    }

    @Test fun localTimeHonoursStartDelayAndRepeat() {
        assertEquals(0f, TextRollMath.localTime(50f, 100f, Repeat.Once, 1000f))
        near(400f, TextRollMath.localTime(500f, 100f, Repeat.Once, 1000f))
        near(1000f, TextRollMath.localTime(5000f, 100f, Repeat.Once, 1000f))
        val reverse = Repeat(2, RepeatMode.Reverse)
        near(750f, TextRollMath.localTime(1250f, 0f, reverse, 1000f))
        assertEquals(Float.POSITIVE_INFINITY, TextRollMath.totalMillis(0f, Repeat.Forever, 1000f))
        near(2100f, TextRollMath.totalMillis(100f, Repeat(2), 1000f))
    }

    @Test fun unitCountCountsSpacesForCharacters() {
        assertEquals(17, TextRollMath.unitCount("motion-primitives", TextGranularity.Character))
        assertEquals(5, TextRollMath.unitCount("ab cd", TextGranularity.Character))
        assertEquals(3, TextRollMath.unitCount(" one two  three ", TextGranularity.Word))
        assertEquals(2, TextRollMath.unitCount("a\nb", TextGranularity.Line))
    }

    @Test fun graphemeIndexIncludesWhitespace() {
        val b = intArrayOf(0, 1, 2, 3, 4, 5) // "ab cd"
        assertEquals(0, TextRollMath.graphemeIndexOf(b, 0))
        assertEquals(3, TextRollMath.graphemeIndexOf(b, 3))
        assertEquals(4, TextRollMath.graphemeIndexOf(b, 4))
        val emoji = intArrayOf(0, 2, 3) // surrogate pair + 1 char
        assertEquals(0, TextRollMath.graphemeIndexOf(emoji, 1))
        assertEquals(1, TextRollMath.graphemeIndexOf(emoji, 2))
    }

    @Test fun backfaceCulling() {
        assertFalse(TextRollMath.isFacingAway(0f, 0f))
        assertFalse(TextRollMath.isFacingAway(89f, 0f))
        assertTrue(TextRollMath.isFacingAway(90f, 0f))
        assertTrue(TextRollMath.isFacingAway(120f, 0f))
        assertTrue(TextRollMath.isFacingAway(0f, -90f))
    }

    @Test fun staticFastPathsForPresets() {
        assertTrue(TextRollMath.restsAtStart(TextRollVariants.Roll))
        assertTrue(TextRollMath.restsAtEnd(TextRollVariants.Roll))
        assertTrue(TextRollMath.restsAtStart(TextRollVariants.BlurRoll))
        // Slide keeps both copies visible (one is translated out), so it is never static.
        assertFalse(TextRollMath.restsAtStart(TextRollVariants.Slide))
        assertFalse(TextRollMath.restsAtEnd(TextRollVariants.Slide))
    }

    @Test fun poseInterpolatesInPixels() {
        val pose = RollPose()
        pose.set(TextRollKeyframe(rotationX = 90f, y = (-40).dp, blur = 2.dp), TextRollKeyframe(), 0.5f, density = 2f)
        near(45f, pose.rotationX)
        near(-40f, pose.translationY)
        near(2f, pose.blurPx)
        assertFalse(pose.isHidden)
        pose.set(TextRollKeyframe(), TextRollKeyframe(rotationX = 90f), 1f, 1f)
        assertTrue(pose.isHidden)
    }
}
