package io.github.mejdi14.textmotion.internal.textshimmer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ShimmerMathTest {
    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 1e-3f, "$a != $b")

    @Test fun bandSweepsFromLeftOfTextToRight() {
        near(ShimmerMath.bandCenter(0f, 100f), -25f)
        near(ShimmerMath.bandCenter(1f, 100f), 125f)
        near(ShimmerMath.bandCenter(0.5f, 100f), 50f)
    }

    @Test fun spreadScalesWithLength() {
        near(ShimmerMath.dynamicSpread(10, 2f), 20f)
        near(ShimmerMath.dynamicSpread(0, 2f), 0f)
    }

    @Test fun progressRestartsEachCycle() {
        near(ShimmerMath.sweepProgress(0f, 2000f), 0f)
        near(ShimmerMath.sweepProgress(1000f, 2000f), 0.5f)
        near(ShimmerMath.sweepProgress(2500f, 2000f), 0.25f)
        near(ShimmerMath.sweepProgress(100f, 0f), 0f)
    }
}
