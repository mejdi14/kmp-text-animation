package io.github.mejdi14.textmotion.internal.blurtext

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import io.github.mejdi14.textmotion.BlurTextDirection
import io.github.mejdi14.textmotion.BlurTextEffect
import io.github.mejdi14.textmotion.BlurTextKeyframe
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextEasing
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.internal.clamp01
import io.github.mejdi14.textmotion.internal.graphemeBoundaries
import io.github.mejdi14.textmotion.internal.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Channels of an animated snapshot. Distances/blur are in dp (CSS px), angles in degrees.
internal const val CH_BLUR = 0
internal const val CH_OPACITY = 1
internal const val CH_X = 2
internal const val CH_Y = 3
internal const val CH_SCALE = 4
internal const val CH_ROT_X = 5
internal const val CH_ROT_Y = 6
internal const val CH_SKEW_X = 7
internal const val CHANNELS = 8

/** Channels driven by the spring in the built-in effects (Motion's `MOVE_KEYS`). */
internal fun isMoveChannel(ch: Int): Boolean = ch >= CH_X

internal val BlurEaseOut: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
internal val BlurEaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal val BlurEaseIn: Easing = CubicBezierEasing(0.55f, 0f, 0.9f, 0.45f)
internal val BlurCssEase: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

internal fun BlurTextKeyframe.toChannels(): FloatArray = floatArrayOf(
    blur.value.coerceAtLeast(0f), opacity, x.value, y.value, scale, rotationX, rotationY, skewX,
)

internal fun snapshot(
    blur: Float = 0f, opacity: Float = 1f, x: Float = 0f, y: Float = 0f,
    scale: Float = 1f, rotX: Float = 0f, rotY: Float = 0f, skewX: Float = 0f,
): FloatArray = floatArrayOf(blur.coerceAtLeast(0f), opacity, x, y, scale, rotX, rotY, skewX)

/** Resolved description of one BlurText configuration (the reference's `buildEffect` + custom snapshots). */
internal class BlurTextSpec(
    /** Keyframes played during the "in" phase: `[from, …steps]`. */
    val keyframes: List<FloatArray>,
    /** Keyframe offsets (0..1) when [custom]; built-ins only use the first and last keyframe. */
    val times: FloatArray,
    val exit: FloatArray,
    /** Fade (blur/opacity) easing for built-ins, per-keyframe easing for [custom]. */
    val ease: Easing,
    /** Spring bounce for move channels, or null for a plain tween (focus / custom). */
    val bounce: Float?,
    val settle: Float,
    val exitEase: Easing,
    /** Transform origin as a fraction of the piece box. */
    val originX: Float,
    val originY: Float,
    /** CSS `perspective` in dp (0 = none). */
    val perspective: Float,
    val custom: Boolean,
) {
    val from: FloatArray get() = keyframes.first()
    val rest: FloatArray get() = keyframes.last()

    companion object {
        fun build(
            effect: BlurTextEffect,
            direction: BlurTextDirection,
            blur: Float,
            distance: Float,
            animationFrom: BlurTextKeyframe?,
            animationTo: List<BlurTextKeyframe>?,
            easing: Easing?,
        ): BlurTextSpec {
            val fx = builtIn(effect, direction, blur, distance)
            val custom = animationFrom != null || animationTo != null || easing != null
            if (!custom) return fx
            val from = animationFrom?.toChannels() ?: fx.from
            val steps = animationTo?.takeIf { it.isNotEmpty() }?.map { it.toChannels() } ?: fx.keyframes.drop(1)
            val frames = listOf(from) + steps
            val times = if (animationTo != null && animationTo.isNotEmpty()) {
                FloatArray(frames.size) { if (frames.size == 1) 0f else it / (frames.size - 1f) }
            } else fx.times
            return BlurTextSpec(
                keyframes = frames,
                times = times,
                exit = animationFrom?.toChannels() ?: fx.exit,
                ease = easing ?: TextEasing.Linear,
                bounce = null,
                settle = 1f,
                exitEase = fx.exitEase,
                originX = 0.5f,
                originY = 0.5f,
                perspective = 0f,
                custom = true,
            )
        }

        private fun builtIn(effect: BlurTextEffect, direction: BlurTextDirection, blurIn: Float, d: Float): BlurTextSpec {
            val b = max(0f, blurIn)
            val yAxis = direction == BlurTextDirection.Top || direction == BlurTextDirection.Bottom
            val sign = if (direction == BlurTextDirection.Top || direction == BlurTextDirection.Left) -1f else 1f
            fun axis(v: Float) = if (yAxis) 0f to v else v to 0f
            return when (effect) {
                BlurTextEffect.Zoom -> {
                    val (fx, fy) = axis(sign * d * 0.35f)
                    val (mx, my) = axis(-sign * d * 0.03f)
                    val (ex, ey) = axis(-sign * d * 0.2f)
                    BlurTextSpec(
                        keyframes = listOf(
                            snapshot(b * 1.2f, 0f, fx, fy, scale = 1.4f),
                            snapshot(b * 0.2f, 0.9f, mx, my, scale = 0.97f),
                            snapshot(),
                        ),
                        times = floatArrayOf(0f, 0.6f, 1f),
                        exit = snapshot(b, 0f, ex, ey, scale = 0.82f),
                        ease = BlurEaseOut, bounce = 0.25f, settle = 0.5f, exitEase = BlurEaseIn,
                        originX = 0.5f, originY = 0.5f, perspective = 0f, custom = false,
                    )
                }
                BlurTextEffect.Tilt -> {
                    val lean = if (yAxis) -sign else sign
                    fun rot(v: Float) = if (yAxis) v to 0f else 0f to v
                    val (fx, fy) = axis(sign * d * 0.5f); val (frx, fry) = rot(lean * 55f)
                    val (mx, my) = axis(-sign * d * 0.04f); val (mrx, mry) = rot(-lean * 8f)
                    val (ex, ey) = axis(-sign * d * 0.3f); val (erx, ery) = rot(-lean * 40f)
                    BlurTextSpec(
                        keyframes = listOf(
                            snapshot(b, 0f, fx, fy, rotX = frx, rotY = fry),
                            snapshot(b * 0.15f, 0.95f, mx, my, rotX = mrx, rotY = mry),
                            snapshot(),
                        ),
                        times = floatArrayOf(0f, 0.6f, 1f),
                        exit = snapshot(b, 0f, ex, ey, rotX = erx, rotY = ery),
                        ease = BlurEaseOut, bounce = 0.3f, settle = 0.8f, exitEase = BlurEaseIn,
                        originX = if (!yAxis) (if (sign < 0) 0f else 1f) else 0.5f,
                        originY = if (yAxis) (if (sign < 0) 0f else 1f) else 0.5f,
                        perspective = 600f, custom = false,
                    )
                }
                BlurTextEffect.Focus -> {
                    val soft = snapshot(b * 1.4f, 0.32f, scale = 1.12f)
                    BlurTextSpec(
                        keyframes = listOf(soft, snapshot(b * 0.1f, 0.95f, scale = 0.995f), snapshot()),
                        times = floatArrayOf(0f, 0.72f, 1f),
                        exit = soft.copyOf(),
                        ease = BlurEaseInOut, bounce = null, settle = 1f, exitEase = BlurEaseInOut,
                        originX = 0.5f, originY = 0.5f, perspective = 0f, custom = false,
                    )
                }
                BlurTextEffect.Drift -> {
                    val h = if (direction == BlurTextDirection.Right) 1f else -1f
                    val v = if (yAxis) sign else 0f
                    BlurTextSpec(
                        keyframes = listOf(
                            snapshot(b, 0f, h * d * 1.6f, v * d * 0.3f, skewX = h * 28f),
                            snapshot(b * 0.15f, 0.95f, -h * d * 0.06f, 0f, skewX = -h * 7f),
                            snapshot(),
                        ),
                        times = floatArrayOf(0f, 0.62f, 1f),
                        exit = snapshot(b, 0f, -h * d * 1.2f, -v * d * 0.2f, skewX = h * 20f),
                        ease = BlurEaseOut, bounce = 0.3f, settle = 0.55f, exitEase = BlurEaseIn,
                        originX = 0.5f, originY = 0.5f, perspective = 0f, custom = false,
                    )
                }
                BlurTextEffect.Blur -> {
                    val (fx, fy) = axis(sign * d)
                    val (mx, my) = axis(-sign * d / 10f)
                    val (ex, ey) = axis(-sign * d * 0.6f)
                    BlurTextSpec(
                        keyframes = listOf(snapshot(b, 0f, fx, fy), snapshot(b / 2f, 0.5f, mx, my), snapshot()),
                        times = floatArrayOf(0f, 0.5f, 1f),
                        exit = snapshot(b, 0f, ex, ey),
                        ease = TextEasing.Linear, bounce = 0.35f, settle = 0.5f, exitEase = BlurEaseIn,
                        originX = 0.5f, originY = 0.5f, perspective = 0f, custom = false,
                    )
                }
            }
        }
    }
}

/**
 * Value of a Motion spring (`visualDuration`, `bounce`, mass 1, zero initial velocity) expressed as
 * the fraction of the initial displacement that remains after [tMillis]: 1 at t = 0, → 0 at rest.
 * Underdamped springs go negative (overshoot).
 */
internal fun springRemaining(tMillis: Float, visualDurationMillis: Float, bounce: Float): Float {
    if (tMillis <= 0f) return 1f
    if (visualDurationMillis <= 0f) return 0f
    val t = tMillis / 1000f
    val omega = 2f * PI.toFloat() / (visualDurationMillis / 1000f * 1.2f)
    val zeta = (1f - bounce).coerceIn(0.05f, 1f)
    return if (zeta < 1f) {
        val wd = omega * sqrt(1f - zeta * zeta)
        exp(-zeta * omega * t) * (cos(wd * t) + zeta * omega / wd * sin(wd * t))
    } else {
        exp(-omega * t) * (1f + omega * t)
    }
}

/** Piecewise keyframe interpolation with per-segment [ease], like Motion's `keyframes` generator. */
internal fun keyframeValue(frames: List<FloatArray>, times: FloatArray, ch: Int, progress: Float, ease: Easing): Float {
    if (frames.size == 1) return frames[0][ch]
    val p = clamp01(progress)
    var i = 0
    while (i < frames.size - 2 && p > times[i + 1]) i++
    val t0 = times[i]; val t1 = times[i + 1]
    val local = if (t1 <= t0) 1f else clamp01((p - t0) / (t1 - t0))
    return lerp(frames[i][ch], frames[i + 1][ch], ease.transform(local))
}

/**
 * Ranks of [count] pieces in the stagger order (the reference's `buildRanks`): start, end, center
 * (fractional ranks for even counts) and a deterministic FNV/LCG shuffle seeded by [seedText].
 */
internal fun blurTextRanks(count: Int, from: StaggerFrom, seedText: String, extraSeed: Int = 0): FloatArray {
    val ranks = FloatArray(count) { it.toFloat() }
    when (from) {
        StaggerFrom.Start -> Unit
        StaggerFrom.End -> for (i in 0 until count) ranks[i] = (count - 1 - i).toFloat()
        StaggerFrom.Center -> {
            val mid = (count - 1) / 2f
            val offset = if (count % 2 == 0) 0.5f else 0f
            for (i in 0 until count) ranks[i] = abs(i - mid) - offset
        }
        StaggerFrom.Edges -> {
            val mid = (count - 1) / 2f
            val offset = if (count % 2 == 0) 0.5f else 0f
            for (i in 0 until count) ranks[i] = (mid - offset) - (abs(i - mid) - offset)
        }
        StaggerFrom.Random -> {
            var seed = 2166136261u
            for (c in seedText) seed = (seed xor c.code.toUInt()) * 16777619u
            seed = seed xor extraSeed.toUInt()
            fun next(): Double {
                seed = seed * 1664525u + 1013904223u
                return seed.toDouble() / 4294967296.0
            }
            val order = IntArray(count) { it }
            for (i in count - 1 downTo 1) {
                val j = floor(next() * (i + 1)).toInt()
                val t = order[i]; order[i] = order[j]; order[j] = t
            }
            for (rank in 0 until count) ranks[order[rank]] = rank.toFloat()
        }
    }
    return ranks
}

/**
 * Number of animated pieces the reference creates for [text]: one per word, or one per grapheme
 * plus one space piece between words.
 */
internal fun blurTextPieceCount(text: String, granularity: TextGranularity): Int {
    val b = graphemeBoundaries(text)
    var words = 0; var chars = 0; var inWord = false
    for (k in 0 until b.size - 1) {
        if (text.substring(b[k], b[k + 1]).isBlank()) inWord = false
        else { chars++; if (!inWord) { words++; inWord = true } }
    }
    return when (granularity) {
        TextGranularity.Word -> words
        TextGranularity.Character -> chars + (words - 1).coerceAtLeast(0)
        TextGranularity.Line -> text.split('\n').count { it.isNotBlank() }
    }
}

/** Piece index (as counted by [blurTextPieceCount]) of a drawable segment. */
internal fun blurTextPieceIndex(granularity: TextGranularity, segmentIndex: Int, wordIndex: Int): Int = when (granularity) {
    TextGranularity.Word -> wordIndex
    TextGranularity.Character -> segmentIndex + wordIndex
    TextGranularity.Line -> segmentIndex
}

/** Phase of the reference state machine. */
internal enum class BlurPhase { From, In, Rest, Out }

/**
 * Timing of the BlurText state machine. One cycle = in (with stagger) → hold (`loopDelay`) → out.
 * Time `t = 0` is the start of the first "in" phase; negative times are either the hidden "from"
 * state (intro) or the hold/out that precedes a replay.
 */
internal class BlurTextTiming(
    val stepMillis: Float,
    stepCount: Int,
    custom: Boolean,
    settle: Float,
    val staggerMillis: Float,
    val maxRank: Float,
    /** Number of "in" phases; [Int.MAX_VALUE] for an endless loop. */
    val iterations: Int,
    val loopDelayMillis: Float,
) {
    val totalDuration: Float = stepMillis * (stepCount - 1)
    val inTotal: Float = totalDuration * (if (custom) 1f else max(1f, settle * 2.2f)) + maxRank * staggerMillis
    val exitDuration: Float = stepMillis * 1.3f
    val exitStagger: Float = staggerMillis * 0.4f
    val outTotal: Float = exitDuration + maxRank * exitStagger
    val cycle: Float = inTotal + loopDelayMillis + outTotal
    val isInfinite: Boolean get() = iterations == Int.MAX_VALUE

    /** End of the last "in" phase. */
    val end: Float = if (isInfinite) Float.POSITIVE_INFINITY else iterations * cycle - loopDelayMillis - outTotal

    /** Start time of a replay (out → in) triggered while rested. */
    val replayStart: Float get() = -outTotal

    /** Start time when the text starts rested and loops (hold → out → in). */
    val restLoopStart: Float get() = -(loopDelayMillis + outTotal)

    /** Phase and phase-local time at [t]; [intro] decides what negative times mean. */
    fun phaseAt(t: Float, intro: Boolean, out: FloatArray): BlurPhase {
        if (t < 0f) {
            if (intro) { out[0] = 0f; return BlurPhase.From }
            return if (t >= -outTotal) { out[0] = t + outTotal; BlurPhase.Out } else { out[0] = 0f; BlurPhase.Rest }
        }
        if (t >= end) { out[0] = 0f; return BlurPhase.Rest }
        val k = floor(t / cycle)
        val c = t - k * cycle
        return when {
            c < inTotal -> { out[0] = c; BlurPhase.In }
            c < inTotal + loopDelayMillis -> { out[0] = 0f; BlurPhase.Rest }
            else -> { out[0] = c - inTotal - loopDelayMillis; BlurPhase.Out }
        }
    }

    /** Number of completed "in" phases at [t] (drives `onFinished`). */
    fun completedIns(t: Float): Int {
        if (t < inTotal) return 0
        val n = floor((t - inTotal) / cycle).toInt() + 1
        return if (isInfinite) n else n.coerceAtMost(iterations)
    }
}

/** Evaluates one piece's animated channels at a phase-local time. */
internal class BlurTextEvaluator(private val spec: BlurTextSpec, private val timing: BlurTextTiming) {
    /** Writes channel values into [out]. */
    fun evaluate(phase: BlurPhase, localMillis: Float, rank: Float, out: FloatArray) {
        when (phase) {
            BlurPhase.From -> spec.from.copyInto(out)
            BlurPhase.Rest -> spec.rest.copyInto(out)
            BlurPhase.In -> {
                val u = localMillis - rank * timing.staggerMillis
                val dur = timing.totalDuration
                val p = if (dur <= 0f) (if (u >= 0f) 1f else 0f) else clamp01(u / dur)
                if (spec.custom) {
                    for (ch in 0 until CHANNELS) out[ch] = keyframeValue(spec.keyframes, spec.times, ch, p, spec.ease)
                    return
                }
                val from = spec.from; val rest = spec.rest
                val fade = spec.ease.transform(p)
                val bounce = spec.bounce
                val spring = if (bounce != null) (if (u <= 0f) 1f else springRemaining(u, dur * spec.settle, bounce)) else 0f
                for (ch in 0 until CHANNELS) {
                    out[ch] = if (bounce != null && isMoveChannel(ch)) rest[ch] + (from[ch] - rest[ch]) * spring
                    else lerp(from[ch], rest[ch], fade)
                }
            }
            BlurPhase.Out -> {
                val u = localMillis - rank * timing.exitStagger
                val d = timing.exitDuration
                val p = spec.exitEase.transform(if (d <= 0f) (if (u >= 0f) 1f else 0f) else clamp01(u / d))
                val rest = spec.rest; val exit = spec.exit
                for (ch in 0 until CHANNELS) out[ch] = lerp(rest[ch], exit[ch], p)
            }
        }
        out[CH_BLUR] = out[CH_BLUR].coerceAtLeast(0f)
        out[CH_OPACITY] = clamp01(out[CH_OPACITY])
    }

    /**
     * Colour mix toward the hidden tint (`fromColor`): 1 = fully tinted, 0 = final colour, following
     * the reference's CSS colour transitions.
     */
    fun tint(phase: BlurPhase, localMillis: Float, rank: Float): Float = when (phase) {
        BlurPhase.From -> 1f
        BlurPhase.Rest -> 0f
        BlurPhase.In -> {
            val d = timing.totalDuration * 0.9f
            val u = localMillis - rank * timing.staggerMillis
            1f - BlurCssEase.transform(if (d <= 0f) (if (u >= 0f) 1f else 0f) else clamp01(u / d))
        }
        BlurPhase.Out -> {
            val d = timing.exitDuration
            val u = localMillis - rank * timing.exitStagger
            BlurCssEase.transform(if (d <= 0f) (if (u >= 0f) 1f else 0f) else clamp01(u / d))
        }
    }
}

/**
 * Converts a CSS `blur()` standard deviation (px) to the radius expected by Compose's
 * `BlurEffect`, which maps radius → sigma as `0.57735 * r + 0.5` on every platform.
 */

/** Reference hover-dim blur: `clamp(blur * 0.3, 2, 6)` px. */
internal fun focusDimBlur(blur: Float): Float = (blur * 0.3f).coerceIn(2f, 6f)
