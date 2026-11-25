package io.github.mejdi14.textmotion.internal.rotatingtext

import androidx.compose.ui.graphics.Color
import io.github.mejdi14.textmotion.RotatingTextDirection
import io.github.mejdi14.textmotion.RotatingTextEffect
import io.github.mejdi14.textmotion.RotatingTextSplit
import io.github.mejdi14.textmotion.RotatingTextStaggerFrom
import io.github.mejdi14.textmotion.RotatingTextTransition
import io.github.mejdi14.textmotion.TextEasing
import io.github.mejdi14.textmotion.internal.clamp01
import io.github.mejdi14.textmotion.internal.graphemeBoundaries
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Animated channels of one unit. Y is a fraction of the unit's own height (CSS `y: 100%`).
internal const val RC_OPACITY = 0
internal const val RC_Y = 1
internal const val RC_ROT_X = 2
internal const val RC_SCALE = 3
/** Blur sigma in dp. */
internal const val RC_BLUR = 4
internal const val RCH = 5

// ---------------------------------------------------------------- transitions

/**
 * Position of a damped harmonic oscillator that moves from 0 to 1 starting at rest, [tSeconds]
 * after it was released (Motion `spring({ damping, stiffness, mass })`). May overshoot 1.
 */
internal fun springValue(tSeconds: Float, damping: Float, stiffness: Float, mass: Float): Float {
    if (tSeconds <= 0f) return 0f
    val k = stiffness.coerceAtLeast(1e-3f)
    val m = mass.coerceAtLeast(1e-3f)
    val w0 = sqrt(k / m)
    val zeta = damping.coerceAtLeast(0f) / (2f * sqrt(k * m))
    val t = tSeconds
    return when {
        zeta < 0.9999f -> {
            val wd = w0 * sqrt(1f - zeta * zeta)
            1f - exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
        }
        zeta <= 1.0001f -> 1f - exp(-w0 * t) * (1f + w0 * t)
        else -> {
            val s = sqrt(zeta * zeta - 1f)
            val r1 = -w0 * (zeta - s)
            val r2 = -w0 * (zeta + s)
            1f + (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r1 - r2)
        }
    }
}

/** Eased value (0 before the start, 1 at the end; springs/overshooting eases may exceed 1) of a transition. */
internal fun RotatingTextTransition.valueAt(tMillis: Float): Float {
    if (tMillis <= 0f) return 0f
    return when (this) {
        is RotatingTextTransition.Spring -> springValue(tMillis / 1000f, damping, stiffness, mass)
        is RotatingTextTransition.Tween ->
            if (durationMillis <= 0) 1f else easing.transform(clamp01(tMillis / durationMillis))
    }
}

/** Time after which the transition is visually at rest (springs: within 0.002 of the target for good). */
internal fun RotatingTextTransition.settleMillis(): Float = when (this) {
    is RotatingTextTransition.Tween -> durationMillis.coerceAtLeast(0).toFloat()
    is RotatingTextTransition.Spring -> {
        var last = 0f
        var t = 0f
        while (t < 6000f) {
            t += 4f
            if (abs(springValue(t / 1000f, damping, stiffness, mass) - 1f) > 0.002f) last = t
        }
        last + 4f
    }
}

// ---------------------------------------------------------------- effect presets

/** Resolved presentation of an effect: start pose, rest pose, exit pose and the two transitions. */
internal class RotatingEffectSpec(
    val from: FloatArray,
    val rest: FloatArray,
    val exit: FloatArray,
    val enter: RotatingTextTransition,
    val exitTransition: RotatingTextTransition,
    /** CSS `perspective` in dp (0 = none). */
    val perspectiveDp: Float,
    /** CSS `transform-origin` z offset in em (negative = behind the glyph plane). */
    val originZEm: Float,
) {
    val enterSettle: Float = enter.settleMillis()
    val exitSettle: Float = exitTransition.settleMillis()
}

private fun pose(opacity: Float = 1f, y: Float = 0f, rotX: Float = 0f, scale: Float = 1f, blur: Float = 0f) =
    FloatArray(RCH).also {
        it[RC_OPACITY] = opacity; it[RC_Y] = y; it[RC_ROT_X] = rotX; it[RC_SCALE] = scale; it[RC_BLUR] = blur
    }

/**
 * Builds the presets of the reference table. [blurDp] is the `blur` prop; the Blur effect uses
 * 10 dp when it is 0. [override] replaces both transitions (reference `transition`).
 */
internal fun buildEffectSpec(
    effect: RotatingTextEffect,
    direction: RotatingTextDirection,
    blurDp: Float,
    override: RotatingTextTransition?,
): RotatingEffectSpec {
    val up = direction == RotatingTextDirection.Up
    val b = if (blurDp > 0f) blurDp else if (effect == RotatingTextEffect.Blur) 10f else 0f
    val rest = pose()
    val spec = when (effect) {
        RotatingTextEffect.Slide -> RotatingEffectSpec(
            from = pose(0f, if (up) 1f else -1f, blur = b), rest = rest,
            exit = pose(0f, if (up) -1.2f else 1.2f, blur = b),
            enter = RotatingTextTransition.Spring(25f, 300f), exitTransition = RotatingTextTransition.Spring(25f, 300f),
            perspectiveDp = 0f, originZEm = 0f,
        )
        RotatingTextEffect.Flip -> RotatingEffectSpec(
            from = pose(0f, rotX = if (up) -90f else 90f, blur = b), rest = rest,
            exit = pose(0f, rotX = if (up) 90f else -90f, blur = b),
            enter = RotatingTextTransition.Tween(600, TextEasing.cubicBezier(0.34f, 1.4f, 0.64f, 1f)),
            exitTransition = RotatingTextTransition.Tween(320, TextEasing.cubicBezier(0.5f, 0f, 0.75f, 0f)),
            perspectiveDp = 480f, originZEm = -0.45f,
        )
        RotatingTextEffect.Blur -> RotatingEffectSpec(
            from = pose(0f, if (up) 0.45f else -0.45f, blur = b), rest = rest,
            exit = pose(0f, if (up) -0.45f else 0.45f, blur = b),
            enter = RotatingTextTransition.Tween(550, TextEasing.cubicBezier(0.22f, 1f, 0.36f, 1f)),
            exitTransition = RotatingTextTransition.Tween(550, TextEasing.cubicBezier(0.22f, 1f, 0.36f, 1f)),
            perspectiveDp = 0f, originZEm = 0f,
        )
        RotatingTextEffect.Scale -> RotatingEffectSpec(
            from = pose(0f, scale = if (up) 0.35f else 1.7f, blur = b), rest = rest,
            exit = pose(0f, scale = if (up) 1.7f else 0.35f, blur = b),
            enter = RotatingTextTransition.Spring(17f, 240f), exitTransition = RotatingTextTransition.Spring(17f, 240f),
            perspectiveDp = 0f, originZEm = 0f,
        )
        RotatingTextEffect.Drop -> RotatingEffectSpec(
            from = pose(0f, -1.1f, blur = b), rest = rest,
            exit = pose(0f, 1.1f, blur = b),
            enter = RotatingTextTransition.Spring(18f, 320f, 0.9f),
            exitTransition = RotatingTextTransition.Tween(300, TextEasing.cubicBezier(0.55f, 0f, 1f, 0.45f)),
            perspectiveDp = 0f, originZEm = 0f,
        )
    }
    if (override == null) return spec
    return RotatingEffectSpec(spec.from, spec.rest, spec.exit, override, override, spec.perspectiveDp, spec.originZEm)
}

// ---------------------------------------------------------------- units & stagger

/** Character range `[start, end)` of one animated unit. */
internal fun rotatingUnitRanges(text: String, split: RotatingTextSplit): List<IntArray> {
    val out = ArrayList<IntArray>()
    when (split) {
        RotatingTextSplit.Characters -> {
            val b = graphemeBoundaries(text)
            for (k in 0 until b.size - 1) {
                if (text.substring(b[k], b[k + 1]).isNotBlank()) out.add(intArrayOf(b[k], b[k + 1]))
            }
        }
        RotatingTextSplit.Words -> splitRuns(text, out) { it.isWhitespace() }
        RotatingTextSplit.Lines -> splitRuns(text, out) { it == '\n' || it == '\r' }
        is RotatingTextSplit.Custom -> {
            val sep = split.separator
            if (sep.isEmpty()) return rotatingUnitRanges(text, RotatingTextSplit.Characters)
            var start = 0
            while (true) {
                val at = text.indexOf(sep, start)
                val end = if (at < 0) text.length else at
                if (end > start && text.substring(start, end).isNotBlank()) out.add(intArrayOf(start, end))
                if (at < 0) break
                start = at + sep.length
            }
        }
    }
    return out
}

private inline fun splitRuns(text: String, out: MutableList<IntArray>, isSeparator: (Char) -> Boolean) {
    var start = -1
    for (i in text.indices) {
        if (isSeparator(text[i])) {
            if (start >= 0) { out.add(intArrayOf(start, i)); start = -1 }
        } else if (start < 0) start = i
    }
    if (start >= 0) out.add(intArrayOf(start, text.length))
}

/**
 * Per-unit stagger delays in ms, counted across the whole text: first `i·s`, last `(n−1−i)·s`,
 * center `|⌊n/2⌋−i|·s`, random `perm[i]·s`, index `|k−i|·s`.
 */
internal fun rotatingDelays(count: Int, from: RotatingTextStaggerFrom, eachMillis: Float, seed: Int): FloatArray {
    val out = FloatArray(count)
    if (count <= 1 || eachMillis <= 0f) return out
    when (from) {
        RotatingTextStaggerFrom.First -> for (i in 0 until count) out[i] = i * eachMillis
        RotatingTextStaggerFrom.Last -> for (i in 0 until count) out[i] = (count - 1 - i) * eachMillis
        RotatingTextStaggerFrom.Center -> for (i in 0 until count) out[i] = abs(count / 2 - i) * eachMillis
        is RotatingTextStaggerFrom.Index -> for (i in 0 until count) out[i] = abs(from.index - i) * eachMillis
        RotatingTextStaggerFrom.Random -> {
            val perm = IntArray(count) { it }
            var s = (seed * 2654435761L + count * 40503L + 0x9E3779B9L) and 0xFFFFFFFFL
            for (i in count - 1 downTo 1) {
                s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
                val j = floor(s / 4294967296.0 * (i + 1)).toInt()
                val t = perm[i]; perm[i] = perm[j]; perm[j] = t
            }
            for (i in 0 until count) out[i] = perm[i] * eachMillis
        }
    }
    return out
}

// ---------------------------------------------------------------- highlight

/** WCAG-style relative luminance of an sRGB colour (`0.2126 R + 0.7152 G + 0.0722 B` on linearised channels). */
internal fun relativeLuminance(c: Color): Float {
    fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
}

/** Text colour used on a highlight pill: near-black on light backgrounds (`L > 0.3`), white otherwise. */
internal fun highlightTextColorFor(background: Color): Color =
    if (relativeLuminance(background) > 0.3f) Color(0xFF0B0B0C) else Color.White
