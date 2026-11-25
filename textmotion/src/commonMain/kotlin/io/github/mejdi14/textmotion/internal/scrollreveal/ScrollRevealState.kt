package io.github.mejdi14.textmotion.internal.scrollreveal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** Stagger ranks of the segments of one [SegmentedTextLayout], cached per layout. */
internal class OrderCache {
    private var layout: SegmentedTextLayout? = null
    private var byLine = false
    var orders: IntArray = IntArray(0)
        private set
    var count: Int = 0
        private set

    fun get(l: SegmentedTextLayout, byLine: Boolean): OrderCache {
        if (layout !== l || this.byLine != byLine) {
            orders = pieceOrders(IntArray(l.segments.size) { l.segments[it].lineIndex }, byLine)
            count = pieceCount(orders)
            layout = l
            this.byLine = byLine
        }
        return this
    }
}

/**
 * Optionally smoothed view of a scroll-driven progress. With `smoothingMillis <= 0` it reads
 * [target] directly (GSAP `scrub: true`); otherwise [run] eases an internal value towards the
 * target (GSAP `scrub: seconds`). [current] must be read in the draw phase.
 */
@Stable
internal class SmoothedProgress(private val target: () -> Float) {
    private var smoothed by mutableFloatStateOf(0f)

    fun current(smoothingMillis: Int): Float = if (smoothingMillis <= 0) target() else smoothed

    suspend fun run(smoothingMillis: Int) {
        if (smoothingMillis <= 0) return
        smoothed = target()
        while (true) {
            snapshotFlow { target() }.first { abs(it - smoothed) > EPSILON }
            var last = -1L
            var settled = false
            while (!settled) {
                withFrameNanos { now ->
                    val dt = if (last < 0L) 16f else (now - last) / 1_000_000f
                    last = now
                    val goal = target()
                    smoothed = smoothStep(smoothed, goal, dt, smoothingMillis.toFloat())
                    if (abs(goal - smoothed) <= EPSILON) { smoothed = goal; settled = true }
                }
            }
        }
    }

    private companion object { const val EPSILON = 0.0005f }
}
