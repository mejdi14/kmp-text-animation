package io.github.mejdi14.textmotion.internal.pressure

import io.github.mejdi14.textmotion.internal.clamp01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Pure formulas of React Bits TextPressure (frame-rate independent). */
internal object PressureMath {
    const val MAX_FRAME_MS: Float = 64f
    const val ENGAGE_TAU_MS: Float = 320f
    const val WAVE_PERIOD: Float = 3.2f
    const val PULSE_PERIOD: Float = 2.8f
    const val PULSE_DEPTH: Float = 0.42f
    const val RIPPLE_LIFE: Float = 1.3f
    const val MAX_RIPPLES: Int = 6

    /** Fraction of the remaining distance the smoothed cursor covers this frame. */
    fun followFactor(elapsedMs: Float, smoothing: Float): Float {
        val lag = 1000f * smoothing * smoothing
        return if (lag > 1f) 1f - exp(-min(elapsedMs, MAX_FRAME_MS) / lag) else 1f
    }

    /** Idle engage blend factor for this frame (320 ms time constant). */
    fun engageFactor(elapsedMs: Float): Float = 1f - exp(-min(elapsedMs, MAX_FRAME_MS) / ENGAGE_TAU_MS)

    /** Linear distance falloff: 1 at the cursor, 0 at [maxDist] and beyond. */
    fun cursorPressure(distance: Float, maxDist: Float): Float = clamp01(1f - distance / max(maxDist, 1f))

    /** Spread of the idle wave, from the radius. */
    fun idleSpread(radius: Float): Float = max(radius * 2f, 0.2f)

    /** Travelling wave crest at horizontal position [pos] (0..1) and idle clock [clock] (s). */
    fun wave(pos: Float, clock: Float, spread: Float): Float {
        val crest = 0.5f + 0.5f * cos(2f * PI.toFloat() * (pos / spread - clock / WAVE_PERIOD))
        return crest * crest
    }

    /** Centre-out breathing pulse. */
    fun pulse(pos: Float, clock: Float): Float {
        val lag = abs(pos - 0.5f) * 0.4f
        val b = 0.5f - 0.5f * cos(2f * PI.toFloat() * (clock / PULSE_PERIOD - lag))
        return PULSE_DEPTH * b * b * (3f - 2f * b)
    }

    /** Blends the idle rest pressure with the cursor pressure by [engage] (0 = idle, 1 = cursor). */
    fun engaged(rest: Float, cursor: Float, engage: Float): Float = rest + (cursor - rest) * engage

    /** Screen blend `1 - (1-a)(1-b)`. */
    fun screen(a: Float, b: Float): Float = 1f - (1f - a) * (1f - b)

    /**
     * Swell of one click ripple at [distance] from its origin, [age] seconds after the click,
     * for a title [span] px wide. The ring front travels at 1.25 × span per second.
     */
    fun rippleSwell(distance: Float, age: Float, span: Float): Float {
        if (age <= 0f || age >= RIPPLE_LIFE) return 0f
        val band = max(span * 0.14f, 32f)
        val offset = (distance - age * span * 1.25f) / band
        if (abs(offset) >= 1f) return 0f
        val fade = 1f - age / RIPPLE_LIFE
        val o = 1f - offset * offset
        return o * o * fade * fade * min(1f, age / 0.06f)
    }

    /** `wdth` sent to the font (5..205 before the font clamps it), or 100 when disabled. */
    fun width(pressure: Float, enabled: Boolean): Float = if (enabled) floor(5f + 200f * pressure) else 100f

    /** `wght` (100..1000), or 400 when disabled. */
    fun weight(pressure: Float, enabled: Boolean): Float = if (enabled) floor(100f + 900f * pressure) else 400f

    /** Italic amount (0..1, two decimals), or 0 when disabled. */
    fun italic(pressure: Float, enabled: Boolean): Float = if (enabled) round(pressure * 100f) / 100f else 0f

    /** Pressure-colour mix fraction: ease-out-quad of the pressure, rounded to whole percents. */
    fun tint(pressure: Float): Float {
        val inv = 1f - pressure
        return round((1f - inv * inv) * 100f) / 100f
    }

    /**
     * Lays out pieces of [widths] inside [containerWidth]. With [flex] they are spread with
     * `justify-content: space-between` (a single piece is left-aligned, overflow starts at 0);
     * otherwise they are packed with [gap] and centred. Writes left edges into [outX].
     */
    fun layoutRow(widths: FloatArray, count: Int, containerWidth: Float, flex: Boolean, gap: Float, outX: FloatArray) {
        if (count == 0) return
        var total = 0f
        for (i in 0 until count) total += widths[i]
        if (flex) {
            val space = if (count > 1) max(0f, (containerWidth - total) / (count - 1)) else 0f
            var x = 0f
            for (i in 0 until count) { outX[i] = x; x += widths[i] + space }
        } else {
            val packed = total + gap * (count - 1)
            var x = max(0f, (containerWidth - packed) / 2f)
            for (i in 0 until count) { outX[i] = x; x += widths[i] + gap }
        }
    }

    /** Reference font size: `max(containerWidth / (chars / 2), minFontSize)`. */
    fun fontSize(containerWidth: Float, charCount: Int, minFontSize: Float): Float =
        if (charCount <= 0) minFontSize else max(containerWidth / (charCount / 2f), minFontSize)
}
