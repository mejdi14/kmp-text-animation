package io.github.mejdi14.textmotion.internal.textroll

import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.TextRollKeyframe
import io.github.mejdi14.textmotion.TextRollVariants
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.graphemeBoundaries
import io.github.mejdi14.textmotion.internal.progressAt
import io.github.mejdi14.textmotion.internal.totalMillis
import kotlin.math.PI
import kotlin.math.cos

/** Pure timing / pose maths of TextRoll (unit tested). */
internal object TextRollMath {
    /** Below this facing factor (`cos rx · cos ry`) a layer is edge-on or shows its back: culled. */
    const val BACKFACE_EPSILON: Float = 1e-3f

    /** Number of animated units before layout (exact for characters and words). */
    fun unitCount(text: String, granularity: TextGranularity): Int = when (granularity) {
        TextGranularity.Character -> graphemeBoundaries(text).size - 1
        TextGranularity.Word -> wordCount(text)
        TextGranularity.Line -> text.count { it == '\n' } + 1
    }

    private fun wordCount(text: String): Int {
        val b = graphemeBoundaries(text)
        var count = 0
        var inWord = false
        for (k in 0 until b.size - 1) {
            val blank = text.substring(b[k], b[k + 1]).isBlank()
            if (!blank && !inWord) count++
            inWord = !blank
        }
        return count
    }

    /** Delays in millis for units `0 until count`. */
    fun delays(count: Int, delayOf: (Int) -> Int): FloatArray = FloatArray(count.coerceAtLeast(0)) { delayOf(it).toFloat() }

    /** Linear 0..1 progress of a layer that starts at [delayMillis] and lasts [durationMillis]. */
    fun layerProgress(timeMillis: Float, delayMillis: Float, durationMillis: Float): Float {
        val local = timeMillis - delayMillis
        if (durationMillis <= 0f) return if (local >= 0f) 1f else 0f
        return (local / durationMillis).coerceIn(0f, 1f)
    }

    /** Length of one roll: the latest layer end over every unit (at least [durationMillis]). */
    fun iterationMillis(enter: FloatArray, exit: FloatArray, durationMillis: Float): Float {
        if (enter.isEmpty() && exit.isEmpty()) return 0f
        var end = 0f
        for (d in enter) end = maxOf(end, d + durationMillis)
        for (d in exit) end = maxOf(end, d + durationMillis)
        return end.coerceAtLeast(0f)
    }

    /** Earliest layer start (the text is static before it). */
    fun firstDelay(enter: FloatArray, exit: FloatArray): Float {
        var first = Float.POSITIVE_INFINITY
        for (d in enter) first = minOf(first, d)
        for (d in exit) first = minOf(first, d)
        return if (first == Float.POSITIVE_INFINITY) 0f else first
    }

    /** Wall time of the whole animation, start delay included. */
    fun totalMillis(startDelayMillis: Float, repeat: Repeat, iterationMillis: Float): Float =
        if (repeat.isInfinite) Float.POSITIVE_INFINITY else startDelayMillis + repeat.totalMillis(iterationMillis)

    /** Maps the clock to the time inside the current roll (honours repeat mode and pauses). */
    fun localTime(elapsedMillis: Float, startDelayMillis: Float, repeat: Repeat, iterationMillis: Float): Float {
        val e = elapsedMillis - startDelayMillis
        if (e <= 0f || iterationMillis <= 0f) return 0f
        return repeat.progressAt(e, iterationMillis) * iterationMillis
    }

    /** `cos(rx) · cos(ry)`: the z component of the glyph's normal after rotation. */
    fun facing(rotationXDeg: Float, rotationYDeg: Float): Float {
        val d2r = PI.toFloat() / 180f
        return cos(rotationXDeg * d2r) * cos(rotationYDeg * d2r)
    }

    /** CSS `backface-visibility: hidden`: edge-on or turned away. */
    fun isFacingAway(rotationXDeg: Float, rotationYDeg: Float): Boolean = facing(rotationXDeg, rotationYDeg) <= BACKFACE_EPSILON

    fun isHidden(k: TextRollKeyframe): Boolean = k.opacity <= 0.002f || k.scale == 0f || isFacingAway(k.rotationX, k.rotationY)

    fun isRest(k: TextRollKeyframe): Boolean = k == REST

    /** Whether, before any layer starts, the composition equals plain text (enter at rest, exit hidden). */
    fun restsAtStart(v: TextRollVariants): Boolean = isRest(v.enter.initial) && isHidden(v.exit.initial)

    /** Whether, after the roll, the composition equals plain text (exit at rest, enter hidden). */
    fun restsAtEnd(v: TextRollVariants): Boolean = isRest(v.exit.animate) && isHidden(v.enter.animate)

    /** Index of the grapheme containing [offset] among [boundaries] (binary search). */
    fun graphemeIndexOf(boundaries: IntArray, offset: Int): Int {
        var lo = 0
        var hi = boundaries.size - 2
        if (hi < 0) return 0
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (boundaries[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private val REST = TextRollKeyframe()
}

/** Interpolated pose of one glyph layer, in px / degrees (reused, no per-frame allocation). */
internal class RollPose {
    var rotationX = 0f
    var rotationY = 0f
    var translationX = 0f
    var translationY = 0f
    var scale = 1f
    var opacity = 1f
    var blurPx = 0f

    fun set(from: TextRollKeyframe, to: TextRollKeyframe, t: Float, density: Float) {
        rotationX = mix(from.rotationX, to.rotationX, t)
        rotationY = mix(from.rotationY, to.rotationY, t)
        translationX = mix(from.x.value, to.x.value, t) * density
        translationY = mix(from.y.value, to.y.value, t) * density
        scale = mix(from.scale, to.scale, t)
        opacity = mix(from.opacity, to.opacity, t).coerceIn(0f, 1f)
        blurPx = (mix(from.blur.value, to.blur.value, t) * density).coerceAtLeast(0f)
    }

    val isHidden: Boolean
        get() = opacity <= 0.002f || scale == 0f || TextRollMath.isFacingAway(rotationX, rotationY)

    val isIdentity: Boolean
        get() = rotationX == 0f && rotationY == 0f && translationX == 0f && translationY == 0f && scale == 1f

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/** Per-layout map from segment index to the delay index (unit) it uses. */
internal class UnitIndexCache {
    private var layout: SegmentedTextLayout? = null
    private var units = IntArray(0)

    fun get(l: SegmentedTextLayout): IntArray {
        if (layout !== l) {
            layout = l
            units = when (l.granularity) {
                TextGranularity.Character -> {
                    val b = graphemeBoundaries(l.text.text)
                    IntArray(l.segments.size) { TextRollMath.graphemeIndexOf(b, l.segments[it].start) }
                }
                TextGranularity.Word -> IntArray(l.segments.size) { l.segments[it].wordIndex }
                else -> IntArray(l.segments.size) { it }
            }
        }
        return units
    }
}
