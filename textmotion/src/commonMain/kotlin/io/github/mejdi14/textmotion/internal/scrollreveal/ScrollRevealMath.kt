package io.github.mejdi14.textmotion.internal.scrollreveal

import io.github.mejdi14.textmotion.ScrollRevealEffect
import io.github.mejdi14.textmotion.internal.clamp01
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/** Reference `SCRUB_SPAN`: in scrub mode each piece takes half of the timeline. */
internal const val SCRUB_SPAN = 0.5f

/** Longest duration (seconds) of the block rotation in enter mode (`min(total, 1.4)`). */
internal const val ENTER_ROTATION_MAX_SECONDS = 1.4f

/** Perspective of the flip effect (`perspective(6em)`). */
internal const val FLIP_PERSPECTIVE_EM = 6f

/**
 * Per-piece progress mapping of the reference. All values share one unit (progress units in scrub
 * mode, seconds in enter mode).
 */
internal class RevealTiming(span: Float, gap: Float) {
    val span: Float = max(0.0001f, span)
    val gap: Float = max(0f, gap)

    /** Whole timeline length for [count] pieces. */
    fun total(count: Int): Float = span + max(0, count - 1) * gap

    /** Local progress (0..1) of the piece with stagger rank [order] when the global progress is [p]. */
    fun local(p: Float, order: Int, count: Int): Float = clamp01((p * total(count) - order * gap) / span)

    companion object {
        fun scrub(stagger: Float) = RevealTiming(SCRUB_SPAN, stagger)
        fun enter(durationMillis: Int, stagger: Float) = RevealTiming(max(0.05f, durationMillis / 1000f), stagger)
    }
}

/**
 * Stagger rank of every segment. Pieces order by their own index, or, when [byLine], by the visual
 * line they sit on (so a whole line reveals together, like the reference `splitBy = 'lines'`).
 *
 * @param lineIndexes line index of each segment in text order.
 * @return the rank of each segment; the piece count is `max + 1` (see [pieceCount]).
 */
internal fun pieceOrders(lineIndexes: IntArray, byLine: Boolean): IntArray {
    if (!byLine) return IntArray(lineIndexes.size) { it }
    val out = IntArray(lineIndexes.size)
    var rank = -1
    var previous = Int.MIN_VALUE
    for (i in lineIndexes.indices) {
        if (lineIndexes[i] != previous) { rank++; previous = lineIndexes[i] }
        out[i] = rank
    }
    return out
}

/** Number of distinct ranks in [orders]. */
internal fun pieceCount(orders: IntArray): Int = if (orders.isEmpty()) 0 else orders.max() + 1

internal fun smooth(x: Float): Float = x * x * (3f - 2f * x)
internal fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)
internal fun easeBack(x: Float, overshoot: Float): Float {
    val u = x - 1f
    return 1f + (overshoot + 1f) * u * u * u + overshoot * u * u
}

/** Bell shaped 0 -> 1 -> 0 curve the highlight colour follows while a piece reveals. */
internal fun glowAt(t: Float): Float = when {
    t <= 0f || t >= 1f -> 0f
    t < 0.35f -> smooth(t / 0.35f)
    else -> 1f - smooth((t - 0.35f) / 0.65f)
}

/** Resolved paint state of one piece. Reused across pieces/frames. */
internal class PieceLook {
    var opacity = 1f
    /** Total vertical translation in px (base offset + rise travel). */
    var translateY = 0f
    var blurPx = 0f
    var scale = 1f
    var rotationX = 0f
    var glow = 0f
    /** Rise effect with word wrappers: the piece is masked by its line box. */
    var masked = false
}

/** Reference `paintPiece`. [t] is the piece's local progress. */
internal fun computeLook(
    effect: ScrollRevealEffect,
    t: Float,
    baseOpacity: Float,
    baseOffsetPx: Float,
    blurStrengthPx: Float,
    highlight: Boolean,
    emPx: Float,
    lineHeightPx: Float,
    out: PieceLook,
) {
    val reveal: Float
    var fadeIn: Float
    when (effect) {
        ScrollRevealEffect.Fade -> { reveal = t; fadeIn = t }
        ScrollRevealEffect.Rise -> { reveal = easeOutCubic(t); fadeIn = clamp01(reveal * 1.25f) }
        ScrollRevealEffect.Flip -> { reveal = easeBack(t, 1.2f); fadeIn = clamp01(t * 2f) }
        ScrollRevealEffect.Scale -> { reveal = easeBack(t, 1.6f); fadeIn = clamp01(t * 1.6f) }
    }
    if (highlight) fadeIn = max(fadeIn, clamp01(t / 0.35f))
    val settle = clamp01(reveal)
    out.opacity = if (t >= 1f) 1f else baseOpacity + (1f - baseOpacity) * fadeIn
    var ty = baseOffsetPx * (1f - settle)
    out.masked = false
    if (effect == ScrollRevealEffect.Rise && reveal < 1f) {
        ty += (1f - reveal) * (lineHeightPx + 0.2f * emPx)
        out.masked = true
    }
    out.translateY = ty
    out.rotationX = if (effect == ScrollRevealEffect.Flip && t < 1f) (1f - reveal) * 90f else 0f
    out.scale = if (effect == ScrollRevealEffect.Scale && t < 1f) 0.35f + 0.65f * reveal else 1f
    out.blurPx = if (blurStrengthPx > 0f && settle < 1f) blurStrengthPx * (1f - settle) else 0f
    out.glow = if (highlight) glowAt(t) else 0f
}

/** Enter mode: block rotation progress (power3.out over `min(total, 1.4)` seconds). */
internal fun enterRotationProgress(elapsedSeconds: Float, totalSeconds: Float): Float {
    val d = max(0.0001f, minOf(totalSeconds, ENTER_ROTATION_MAX_SECONDS))
    return easeOutCubic(clamp01(elapsedSeconds / d))
}

/** One step of the exponential catch-up used for `smoothing > 0` (GSAP `scrub: seconds`). */
internal fun smoothStep(current: Float, target: Float, dtMillis: Float, smoothingMillis: Float): Float {
    if (smoothingMillis <= 0f) return target
    // About 95 % of the distance is covered after `smoothingMillis`.
    val tau = smoothingMillis / 3f
    return current + (target - current) * (1f - exp(-dtMillis / tau))
}
