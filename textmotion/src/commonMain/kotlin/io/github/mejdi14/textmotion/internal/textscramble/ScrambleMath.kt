package io.github.mejdi14.textmotion.internal.textscramble

import kotlin.math.floor

/**
 * Pure, deterministic scramble schedule (motion-primitives "Text Scramble"):
 * a stepped timer ticks every `tickMillis`; at tick `k` slot `i` shows its final glyph once
 * `k / steps * slotCount > i` (strict), otherwise a random glyph. The run ends when `k > steps`.
 */
internal object ScrambleMath {
    /** Number of steps (not rounded, as in the reference): duration / tick. */
    fun steps(durationMillis: Float, tickMillis: Float): Float =
        if (tickMillis <= 0f) 0f else durationMillis / tickMillis

    /** Index of the tick showing at [elapsedMillis]; tick 0 is shown immediately. */
    fun tickAt(elapsedMillis: Float, tickMillis: Float): Int =
        if (tickMillis <= 0f) Int.MAX_VALUE else floor(elapsedMillis.coerceAtLeast(0f) / tickMillis).toInt()

    /** Wall time at which the run ends (first tick with `k > steps`). */
    fun totalMillis(durationMillis: Float, tickMillis: Float): Float =
        (floor(steps(durationMillis, tickMillis)) + 1f) * tickMillis

    /** True once the run is over and the original text is displayed. */
    fun isFinished(tick: Int, steps: Float): Boolean = tick > steps

    /** Whether slot [slot] (of [slotCount]) already shows its final glyph at [tick]. */
    fun isRevealed(slot: Int, tick: Int, steps: Float, slotCount: Int): Boolean {
        if (steps <= 0f) return true
        return (tick / steps) * slotCount > slot
    }

    /** Number of leading slots revealed at [tick] (spaces count as slots, as in the reference). */
    fun revealedCount(tick: Int, steps: Float, slotCount: Int): Int {
        var n = 0
        while (n < slotCount && isRevealed(n, tick, steps, slotCount)) n++
        return n
    }

    /** Deterministic pseudo-random pool index for ([seed], [tick], [slot]); fresh every tick. */
    fun glyphIndex(seed: Int, tick: Int, slot: Int, poolSize: Int): Int {
        if (poolSize <= 0) return 0
        var h = seed * -0x61c88647 + tick * 0x2c1b3c6d + slot * 0x297a2d39
        h = h xor (h ushr 15); h *= -0x7a143595
        h = h xor (h ushr 13); h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return (h and Int.MAX_VALUE) % poolSize
    }
}
