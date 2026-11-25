package io.github.mejdi14.textmotion.internal.splittext

import androidx.compose.animation.core.Easing
import io.github.mejdi14.textmotion.SplitTextEffect
import io.github.mejdi14.textmotion.SplitTextKeyframe
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextEasing
import io.github.mejdi14.textmotion.internal.clamp01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// Animated channels. Distances are in px, angles in degrees.
internal const val CH_OPACITY = 0
internal const val CH_X = 1
internal const val CH_Y = 2
internal const val CH_SCALE = 3
internal const val CH_ROT_X = 4
internal const val CH_ROT_Y = 5
internal const val CH_ROT_Z = 6
internal const val CH_SKEW_X = 7
internal const val CH_BLUR = 8
internal const val CHANNELS = 9

/** GSAP `expo.out`. */
internal val ExpoOut: Easing = Easing { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }

/**
 * The reference's custom "wave" ease: starts at 0, overshoots past 1 once and settles to 1
 * (`1 - cos(t * 1.5π) * (1 - t)^1.2`).
 */
internal val WaveEase: Easing = Easing { t ->
    val c = t.coerceIn(0f, 1f)
    1f - cos(c * PI.toFloat() * 1.5f) * (1f - c).pow(1.2f)
}

private val Power2In: Easing = TextEasing.powerIn(2)
private val Power1In: Easing = TextEasing.powerIn(1)
private val Power1InOut: Easing = TextEasing.powerInOut(1)

/** Default reveal ease of each preset (reference table). */
internal fun defaultEasing(effect: SplitTextEffect): Easing = when (effect) {
    SplitTextEffect.Rise -> TextEasing.powerOut(3)
    SplitTextEffect.Fade -> TextEasing.powerInOut(2)
    SplitTextEffect.Blur -> TextEasing.powerOut(2)
    SplitTextEffect.Flip -> TextEasing.powerOut(3)
    SplitTextEffect.Pop -> TextEasing.backOut(1.6f)
    SplitTextEffect.Skew -> ExpoOut
    SplitTextEffect.Wave -> WaveEase
}

/** Rest pose: `to` of every preset. */
private fun restChannels() = FloatArray(CHANNELS).also { it[CH_OPACITY] = 1f; it[CH_SCALE] = 1f }

internal fun SplitTextKeyframe.toChannels(density: Float): FloatArray = FloatArray(CHANNELS).also {
    it[CH_OPACITY] = opacity
    it[CH_X] = x.value * density
    it[CH_Y] = y.value * density
    it[CH_SCALE] = scale
    it[CH_ROT_X] = rotationX
    it[CH_ROT_Y] = rotationY
    it[CH_ROT_Z] = rotation
    it[CH_SKEW_X] = skewX
    it[CH_BLUR] = blur.value * density
}

/**
 * Resolved start/end snapshot of a [SplitText][io.github.mejdi14.textmotion.SplitText] reveal.
 * Rebuilt only when the font size or density changes (the presets are expressed in `em`).
 */
internal class SplitTextSpec(
    val from: FloatArray,
    val to: FloatArray,
    /** Transform origin as a fraction of the unit's box. */
    val originX: Float,
    val originY: Float,
    /** CSS `transformPerspective` in px (0 = none). */
    val perspectivePx: Float,
)

/**
 * Builds the [SplitTextSpec] for [effect] at font size [emPx] (device px) and [density].
 * When [custom] is true, [from]/[to] replace the preset like the reference's `from` / `to` props
 * (defaults `{opacity: 0, y: 40}` → `{opacity: 1, y: 0}`).
 */
internal fun buildSplitTextSpec(
    effect: SplitTextEffect,
    emPx: Float,
    density: Float,
    custom: Boolean,
    from: SplitTextKeyframe?,
    to: SplitTextKeyframe?,
): SplitTextSpec {
    if (custom) {
        val f = from?.toChannels(density) ?: FloatArray(CHANNELS).also { it[CH_OPACITY] = 0f; it[CH_SCALE] = 1f; it[CH_Y] = 40f * density }
        val t = to?.toChannels(density) ?: restChannels()
        return SplitTextSpec(f, t, 0.5f, 0.5f, 0f)
    }
    val f = restChannels()
    f[CH_OPACITY] = 0f
    var originY = 0.5f
    var persp = 0f
    when (effect) {
        SplitTextEffect.Rise -> f[CH_Y] = 40f * density
        SplitTextEffect.Fade -> Unit
        SplitTextEffect.Blur -> f[CH_BLUR] = (emPx / density * 1.4f).roundToInt() / 10f * density
        SplitTextEffect.Flip -> { f[CH_ROT_X] = -90f; persp = emPx * 6f; originY = 1f }
        SplitTextEffect.Pop -> f[CH_SCALE] = 0f
        SplitTextEffect.Skew -> { originY = 1f; f[CH_X] = -emPx * 0.4f; f[CH_SKEW_X] = -24f }
        SplitTextEffect.Wave -> f[CH_Y] = emPx * 0.45f
    }
    return SplitTextSpec(f, restChannels(), 0.5f, originY, persp)
}

/**
 * Per-piece start delays (ms) of a GSAP `stagger: { each, from }` distribution without a grid.
 *
 * GSAP normalises the distance of every piece from the origin to 0..1 and multiplies it by
 * `each × (count − 1)`, so the whole spread is always `each × (count − 1)` whichever `from` is used
 * (start/end advance by `each` per piece, center/edges by `2 × each`). `edges` is inverted
 * (outer pieces first, centre last) and `random` shuffles the `start` distribution.
 */
internal fun splitTextDelays(count: Int, from: StaggerFrom, eachMillis: Float, seed: Int): FloatArray {
    val out = FloatArray(count)
    if (count <= 1 || eachMillis <= 0f) return out
    val ratio = when (from) {
        StaggerFrom.Start, StaggerFrom.Random -> 0f
        StaggerFrom.End -> 1f
        StaggerFrom.Center, StaggerFrom.Edges -> 0.5f
    }
    val origin = count * ratio - 0.5f
    val dist = FloatArray(count) { abs(it - origin) }
    if (from == StaggerFrom.Random) shuffle(dist, seed)
    var min = Float.POSITIVE_INFINITY
    var maxD = 0f
    for (d in dist) { if (d < min) min = d; if (d > maxD) maxD = d }
    val range = maxD - min
    val amount = eachMillis * (count - 1)
    for (i in 0 until count) {
        val n = if (range > 0f) (dist[i] - min) / range else 0f
        out[i] = if (from == StaggerFrom.Edges) (1f - n) * amount else n * amount
    }
    return out
}

private fun shuffle(a: FloatArray, seed: Int) {
    var s = (seed * 2654435761L + a.size * 40503L + 0x9E3779B9L) and 0xFFFFFFFFL
    fun next(): Double {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return s / 4294967296.0
    }
    for (i in a.size - 1 downTo 1) {
        val j = floor(next() * (i + 1)).toInt()
        val t = a[i]; a[i] = a[j]; a[j] = t
    }
}

/** Per-unit result of [SplitTextTiming.evaluate]. */
internal class UnitState {
    /** Interpolation between the `from` (0) and `to` (1) snapshot; may overshoot with back/wave eases. */
    var mix = 0f
    /** Weight of the accent colour (1 = fully accent). */
    var accent = 0f
}

/**
 * Timeline of a SplitText run.
 *
 * Time `t = 0` is the start of the first reveal of an intro run (negative = hidden start delay),
 * or the start of the first exit of a replay run. A replay/loop cycle is "exit, then reveal", both
 * staggered, where each unit reveals right after its own exit (the reference's `reveal` offset).
 *
 * @param durationMillis `duration`; @param spreadMillis largest stagger delay.
 * @param iterations number of reveals ([Int.MAX_VALUE] = loop); @param holdMillis `loopDelay`.
 */
internal class SplitTextTiming(
    val durationMillis: Float,
    val spreadMillis: Float,
    val iterations: Int,
    val holdMillis: Float,
    private val ease: Easing,
) {
    val isInfinite: Boolean get() = iterations == Int.MAX_VALUE

    /** Exit duration `max(0.35 s, duration × 0.55)`. */
    val exitMillis: Float = max(350f, durationMillis * 0.55f)

    /** Span of one staggered reveal. */
    val revealSpan: Float = durationMillis + spreadMillis

    /** Span of one exit + reveal cycle. */
    val cycleSpan: Float = exitMillis + revealSpan

    private val period: Float = cycleSpan + holdMillis

    private fun firstCycleStart(replay: Boolean): Float = if (replay) 0f else revealSpan + holdMillis

    /** Number of exit cycles of the run. */
    private fun exitCycles(replay: Boolean): Int =
        if (isInfinite) Int.MAX_VALUE else if (replay) iterations else iterations - 1

    /** End time of a finite run. */
    fun end(replay: Boolean): Float {
        val cycles = exitCycles(replay)
        return if (cycles <= 0) revealSpan else firstCycleStart(replay) + (cycles - 1) * period + cycleSpan
    }

    private fun cycleIndex(t: Float, replay: Boolean): Int {
        val k = floor((t - firstCycleStart(replay)) / period).toInt()
        val cycles = exitCycles(replay)
        return if (k >= cycles) cycles - 1 else k
    }

    /** Fills [out] for a unit with stagger [delay] at time [t]. */
    fun evaluate(t: Float, delay: Float, replay: Boolean, out: UnitState) {
        val c0 = firstCycleStart(replay)
        if (exitCycles(replay) <= 0 || (!replay && t < c0)) {
            reveal(t, delay, out)
            return
        }
        if (t < 0f) { out.mix = 1f; out.accent = 0f; return } // replay run before its start: rested
        val k = cycleIndex(t, replay)
        val local = t - c0 - k * period
        val q = (local - delay) / exitMillis
        when {
            q <= 0f -> { out.mix = 1f; out.accent = 0f }
            q < 1f -> {
                out.mix = 1f - Power2In.transform(q) // power2.in, `to` → `from`
                out.accent = Power1In.transform(q)   // accent colour eases power1.in
            }
            else -> reveal(local - exitMillis, delay, out)
        }
    }

    private fun reveal(t: Float, delay: Float, out: UnitState) {
        val p = if (durationMillis <= 0f) (if (t >= delay) 1f else 0f) else clamp01((t - delay) / durationMillis)
        out.mix = ease.transform(p)
        // Accent tween: starts at 0.3 × duration, lasts 0.7 × duration, power1.inOut, accent → text colour.
        val r = if (durationMillis <= 0f) 1f else clamp01((t - delay - 0.3f * durationMillis) / (0.7f * durationMillis))
        out.accent = 1f - Power1InOut.transform(r)
    }

    /** True while the run is idle (fully revealed, before an exit starts or after the last reveal). */
    fun isRested(t: Float, replay: Boolean): Boolean {
        val c0 = firstCycleStart(replay)
        if (exitCycles(replay) <= 0) return t >= revealSpan
        if (!replay && t < c0) return t >= revealSpan
        if (t < 0f) return true
        val k = cycleIndex(t, replay)
        return t - c0 - k * period >= cycleSpan
    }

    /** Number of completed reveals at time [t] (drives `onLetterAnimationComplete`). */
    fun completedReveals(t: Float, replay: Boolean): Int {
        val c0 = firstCycleStart(replay)
        val base = if (replay) 0 else 1
        if (exitCycles(replay) <= 0 || (!replay && t < c0)) return if (t >= revealSpan) 1 else 0
        if (t < 0f) return 0
        val k = cycleIndex(t, replay)
        val done = if (t - c0 - k * period >= cycleSpan) 1 else 0
        return base + k + done
    }
}
