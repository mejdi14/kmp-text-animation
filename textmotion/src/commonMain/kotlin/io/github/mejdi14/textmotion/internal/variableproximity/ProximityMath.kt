package io.github.mejdi14.textmotion.internal.variableproximity

import io.github.mejdi14.textmotion.ProximityFalloff
import io.github.mejdi14.textmotion.VariableFont
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure math of VariableProximity (React Bits), frame-rate independent. */
internal object ProximityMath {
    /** Longest frame step honoured by the smoothing (ms), as in the reference. */
    const val MAX_FRAME_MS: Float = 64f
    /** Levels closer than this to their target snap onto it. */
    const val SNAP: Float = 0.002f
    /** Idle wave period (s) and peak strength. */
    const val WAVE_PERIOD: Float = 3.2f
    const val WAVE_PEAK: Float = 0.7f
    /** Time without a near letter before the idle wave fades in (ms). */
    const val WAVE_IDLE_DELAY_MS: Float = 500f
    /** Time constant of the wave mix fade (ms). */
    const val WAVE_MIX_TAU_MS: Float = 600f
    /** Minimum wave span (CSS px ≈ dp). */
    const val WAVE_MIN_SPAN_DP: Float = 220f
    /** Vertical transform origin of a letter (CSS `transform-origin: 50% 80%`). */
    const val ORIGIN_Y: Float = 0.8f

    private val EXP_M2 = exp(-2.0).toFloat()

    /** Strength 0..1 of a letter at [distance] from the pointer within [radius]. */
    fun strength(distance: Float, radius: Float, falloff: ProximityFalloff): Float {
        if (radius <= 0f || distance >= radius) return 0f
        val norm = 1f - distance / radius
        return when (falloff) {
            ProximityFalloff.Linear -> norm
            ProximityFalloff.Exponential -> norm * norm
            ProximityFalloff.Gaussian -> {
                val r = distance / radius
                ((exp(-2f * r * r) - EXP_M2) / (1f - EXP_M2)).coerceIn(0f, 1f)
            }
        }
    }

    /** Fraction of the remaining distance covered this frame (`1 - exp(-dt / (smoothing·400))`). */
    fun ease(dtMs: Float, smoothing: Float): Float {
        if (smoothing <= 0f) return 1f
        val dt = min(max(dtMs, 0f), MAX_FRAME_MS)
        return 1f - exp(-dt / (smoothing * 400f))
    }

    /** One smoothing step of [level] toward [target], snapping when close. */
    fun approach(level: Float, target: Float, ease: Float): Float {
        val next = level + (target - level) * ease
        return if (abs(target - next) < SNAP) target else next
    }

    /** Wave-mix fade factor for one frame (time constant [WAVE_MIX_TAU_MS]). */
    fun waveMixEase(dtMs: Float): Float = 1f - exp(-min(max(dtMs, 0f), MAX_FRAME_MS) / WAVE_MIX_TAU_MS)

    /** Idle-wave strength of an anchor at ([ax], [ay]) (box-relative px) at [timeSec]. */
    fun wave(ax: Float, ay: Float, span: Float, timeSec: Float): Float {
        val phase = ((ax + ay * 0.6f) / span - timeSec / WAVE_PERIOD) * 2f * PI.toFloat()
        val base = (1f + cos(phase)) / 2f
        return WAVE_PEAK * base.toDouble().pow(2.2).toFloat()
    }

    /** Span over which the idle wave travels: `max(220dp, 0.85 · textWidth)`. */
    fun waveSpan(textWidth: Float, minSpanPx: Float): Float = max(minSpanPx, textWidth * 0.85f)

    /** Letter scale at [level] (`1 + (scale - 1)·level`). */
    fun scaleAt(scale: Float, level: Float): Float = 1f + (scale - 1f) * level

    /**
     * Parses a CSS `font-variation-settings` value such as `"'wght' 400, 'opsz' 9"` into ordered
     * (tag, value) pairs. `normal`, empty strings and malformed entries yield no axes; a repeated
     * tag keeps its last value.
     */
    fun parseSettings(settings: String): List<Pair<String, Float>> {
        val out = ArrayList<Pair<String, Float>>()
        for (raw in settings.split(',')) {
            val entry = raw.trim()
            if (entry.isEmpty() || entry.equals("normal", ignoreCase = true)) continue
            val quote = entry[0]
            if (quote != '\'' && quote != '"') continue
            val close = entry.indexOf(quote, 1)
            if (close < 0) continue
            val tag = entry.substring(1, close)
            if (tag.length != 4) continue
            val value = entry.substring(close + 1).trim().toFloatOrNull() ?: continue
            if (value.isNaN() || value.isInfinite()) continue
            out.removeAll { it.first == tag }
            out += tag to value
        }
        return out
    }
}

/**
 * The interpolated axis set of one VariableProximity instance. Every axis of `from` moves toward
 * the same axis in `to` (or stays when `to` lacks it); axes only present in `to` are ignored,
 * exactly like the reference.
 */
internal class ProximityAxes(from: String, to: String) {
    val tags: Array<String>
    val fromValues: FloatArray
    val toValues: FloatArray

    init {
        val f = ProximityMath.parseSettings(from)
        val t = ProximityMath.parseSettings(to).toMap()
        tags = Array(f.size) { f[it].first }
        fromValues = FloatArray(f.size) { f[it].second }
        toValues = FloatArray(f.size) { t[f[it].first] ?: f[it].second }
    }

    /** Value of [tag] at [level], or NaN when the axis is not animated. */
    fun valueAt(tag: String, level: Float): Float {
        for (i in tags.indices) if (tags[i] == tag) return fromValues[i] + (toValues[i] - fromValues[i]) * level
        return Float.NaN
    }

    /** Tags the platform cannot render (anything besides wght, wdth, ital, slnt, opsz). */
    val unsupportedTags: List<String> get() = tags.filter { it !in SUPPORTED }

    /**
     * Quantized cache key at [level]. `slnt` (degrees) becomes the semantic italic amount relative
     * to the font's slant range; without `opsz`, CSS automatic optical sizing uses [autoOpticalSize]
     * (the font size in CSS px).
     */
    fun keyAt(quantizer: VariationQuantizer, font: VariableFont, level: Float, autoOpticalSize: Float): Long {
        val ital = valueAt("ital", level)
        val slnt = valueAt("slnt", level)
        val italic = when {
            !ital.isNaN() -> ital
            !slnt.isNaN() -> slantToItalic(slnt, font)
            else -> Float.NaN
        }
        val opsz = valueAt("opsz", level).let { if (it.isNaN()) autoOpticalSize else it }
        return quantizer.key(font, weight = valueAt("wght", level), width = valueAt("wdth", level), italic = italic, opticalSize = opsz)
    }

    /** Lookup table of [buckets] + 1 keys over level 0..1 (bucket 0 is exactly the `from` settings). */
    fun keyTable(quantizer: VariationQuantizer, font: VariableFont, autoOpticalSize: Float, buckets: Int): LongArray =
        LongArray(buckets + 1) { keyAt(quantizer, font, it / buckets.toFloat(), autoOpticalSize) }

    companion object {
        private val SUPPORTED = setOf("wght", "wdth", "ital", "slnt", "opsz")

        /** Synthetic slant (degrees) of a full italic when the font has no slant axis (≈ 12°). */
        const val SYNTHETIC_ITALIC_DEGREES: Float = -12f

        /** Semantic italic (0..1) for a CSS `slnt` angle (negative = leaning right). */
        fun slantToItalic(slnt: Float, font: VariableFont): Float {
            val min = font.slant?.min?.takeIf { it < 0f } ?: SYNTHETIC_ITALIC_DEGREES
            return (slnt / min).coerceIn(0f, 1f)
        }
    }
}

/** Level → key bucket index. */
internal fun levelBucket(level: Float, buckets: Int): Int = (level.coerceIn(0f, 1f) * buckets).roundToInt()
