package io.github.mejdi14.textmotion.internal.fuzzy

import androidx.compose.ui.graphics.Color
import io.github.mejdi14.textmotion.FuzzyDirection
import io.github.mejdi14.textmotion.internal.hash01
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure, platform-independent formulas of FuzzyText (React Bits reference). */
internal object FuzzMath {
    /** Peak intensity reached at the start of the intro. */
    const val INTRO_PEAK: Float = 2f

    /** Burst attack / decay time constants (ms). */
    const val BURST_ATTACK: Float = 70f
    const val BURST_DECAY: Float = 240f

    /** Duration of the `clickEffect` full-intensity flash (ms). */
    const val CLICK_FLASH: Float = 150f

    /** Horizontal ink inset around the text in the source raster (reference: 5 px). */
    const val INSET: Float = 5f

    /** Random-stream salts shared by the shader and the canvas fallback. */
    const val SALT_ROWS: Int = 0
    const val SALT_COLS: Int = 211
    const val SALT_WOBBLE_0: Int = 401
    const val SALT_WOBBLE_2: Int = 613

    /** The shader receives the frame seed modulo this value to keep float precision. */
    const val SEED_PERIOD: Int = 1024

    /**
     * Extra room (in dp/CSS px) reserved around the text: `[horizontal, vertical]` per side.
     * Reference: `padX = fuzzRange + 20 (+ splitPad)`, `padY = 0` for horizontal fuzz, else
     * `fuzzRange + 10 (+ splitPad when vertical)`; `splitPad = ceil(fuzzRange / 2) + 4` with colour split.
     * The horizontal value also includes the 5 px source inset.
     */
    fun padding(fuzzRange: Float, colorSplit: Float, direction: FuzzyDirection): FloatArray {
        val splitPad = if (colorSplit > 0f) ceil(fuzzRange * 0.5f) + 4f else 0f
        val padX = fuzzRange + 20f + (if (direction == FuzzyDirection.Vertical) 0f else splitPad)
        val padY = if (direction == FuzzyDirection.Horizontal) 0f
        else fuzzRange + 10f + (if (direction == FuzzyDirection.Vertical) splitPad else 0f)
        return floatArrayOf(padX + INSET, padY)
    }

    /** Height (or width) of one displaced slice in px: `max(1, round(max(1px, sliceSize) * coarse))`. */
    fun sliceStep(sliceSizePx: Float, onePx: Float, coarse: Float): Int =
        max(1, (max(onePx, sliceSizePx) * coarse).roundToInt())

    /** Integer offset of slice [index] for frame [seed]: uniform in `[-amp/2, amp/2]`, JS-style rounding. */
    fun offset(index: Int, seed: Int, salt: Int, amp: Float): Int {
        if (amp <= 0f) return 0
        return floor((hash01(index, seed + salt) - 0.5f) * amp + 0.5f).toInt()
    }

    /** Colour-split channel distance in px: `round(colorSplit * (2 + intensity * fuzzRange * 0.18) * dpr)`. */
    fun splitDistance(colorSplit: Float, intensity: Float, fuzzRangeDp: Float, density: Float): Int =
        if (colorSplit <= 0f) 0 else (colorSplit * (2f + intensity * fuzzRangeDp * 0.18f) * density).roundToInt()

    /**
     * Whether the ink is "light" (mean Rec.709 luminance of the colours ≥ 0.5). Light inks combine
     * the split channels additively, dark inks multiplicatively (reference `lighter` / `multiply`).
     */
    fun isLightInk(colors: List<Color>): Boolean {
        if (colors.isEmpty()) return false
        var sum = 0f
        for (c in colors) {
            if (c == Color.Unspecified) continue
            sum += 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        }
        return sum / colors.size >= 0.5f
    }

    /** Burst envelope: quadratic attack over 70 ms then exponential decay (τ = 240 ms). */
    fun burstEnvelope(ageMillis: Float): Float =
        if (ageMillis < BURST_ATTACK) 1f - (1f - ageMillis / BURST_ATTACK).pow(2)
        else exp(-(ageMillis - BURST_ATTACK) / BURST_DECAY)

    /** Burst peak level: `min(2, 0.6 + hoverIntensity * 1.4)`. */
    fun burstPeak(hoverIntensity: Float): Float = minOf(2f, 0.6f + hoverIntensity * 1.4f)

    /** Intro envelope `(1 - p)^2.5`. */
    fun introEnvelope(progress: Float): Float = (1f - progress.coerceIn(0f, 1f)).pow(2.5f)

    /** Intro opacity: eases in over the first 30 % of the intro. */
    fun introOpacity(progress: Float): Float =
        if (progress < 0.3f) 1f - (1f - progress / 0.3f).pow(2) else 1f

    /** Slice coarsening during burst/intro energy: `1 + 3 * energy²`. */
    fun coarse(energy: Float): Float = 1f + 3f * energy * energy

    /**
     * Frame-rate independent version of the reference smoothing
     * `eased += (goal - eased) * (1 - 0.05^(1 / transitionFrames))`: after [transitionMillis] the
     * value has covered 95 % of the distance. Snaps when closer than 0.002.
     */
    fun transition(eased: Float, goal: Float, dtMillis: Float, transitionMillis: Float): Float {
        if (transitionMillis <= 0f) return goal
        val next = eased + (goal - eased) * (1f - 0.05f.pow(dtMillis.coerceAtLeast(0f) / transitionMillis))
        return if (kotlin.math.abs(goal - next) < 0.002f) goal else next
    }

    /**
     * Glitch schedule: after waiting [intervalMillis] the glitch is active for [durationMillis],
     * then the cycle repeats (reference: chained `setTimeout`s).
     */
    fun glitchActive(timeMillis: Float, intervalMillis: Float, durationMillis: Float): Boolean {
        if (durationMillis <= 0f || timeMillis < 0f) return false
        val period = intervalMillis.coerceAtLeast(0f) + durationMillis
        val phase = timeMillis - floor(timeMillis / period) * period
        return phase >= intervalMillis
    }
}
