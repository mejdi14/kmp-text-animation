package io.github.mejdi14.textmotion.internal.textshimmer

/**
 * Non-visual math of the TextShimmer effect (reference: background layer 2.5x the element width,
 * `background-position` animated from 100% to 0%, which moves the band centre from -0.25W to 1.25W).
 */
internal object ShimmerMath {
    /** Band centre x (px) at sweep progress [p] (0..1) for a text/element of width [width]. */
    fun bandCenter(p: Float, width: Float): Float = -0.25f * width + 1.5f * width * p

    /** Half-width of the band: `text.length * spread` (CSS px, treated as dp by the caller). */
    fun dynamicSpread(textLength: Int, spread: Float): Float = textLength * spread

    /** Sweep progress (0..1, restarting) for [elapsedMillis] given a cycle of [durationMillis]. */
    fun sweepProgress(elapsedMillis: Float, durationMillis: Float): Float {
        if (durationMillis <= 0f) return 0f
        val p = (elapsedMillis / durationMillis) % 1f
        return if (p < 0f) p + 1f else p
    }
}
