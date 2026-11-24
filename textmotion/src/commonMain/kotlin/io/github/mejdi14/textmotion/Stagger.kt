package io.github.mejdi14.textmotion

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.random.Random

/** Where a staggered sequence starts. Mirrors GSAP/Motion `staggerFrom`. */
public enum class StaggerFrom { Start, End, Center, Edges, Random }

/**
 * Per-segment delay distribution.
 *
 * @param eachMillis delay added between two consecutive segments.
 * @param from which segment starts first.
 * @param seed seed used by [StaggerFrom.Random] so the order is stable across recompositions.
 */
@Immutable
public data class Stagger(
    val eachMillis: Int = 50,
    val from: StaggerFrom = StaggerFrom.Start,
    val seed: Int = 0,
) {
    /** Delay in milliseconds for segment [index] out of [count]. */
    public fun delayFor(index: Int, count: Int): Int = rank(index, count) * eachMillis

    /** Ordinal position (0-based) of [index] in the stagger order. Not necessarily unique for Center/Edges. */
    public fun rank(index: Int, count: Int): Int {
        if (count <= 1) return 0
        return when (from) {
            StaggerFrom.Start -> index
            StaggerFrom.End -> count - 1 - index
            StaggerFrom.Center -> {
                val center = (count - 1) / 2f
                (abs(index - center) + 0.5f).toInt()
            }
            StaggerFrom.Edges -> {
                val center = (count - 1) / 2f
                (center - abs(index - center) + 0.5f).toInt()
            }
            StaggerFrom.Random -> randomOrder(count)[index]
        }
    }

    /** Largest rank that [rank] can return for [count] segments. */
    public fun maxRank(count: Int): Int = if (count <= 1) 0 else (0 until count).maxOf { rank(it, count) }

    /** Total time from the first segment start to the last segment start. */
    public fun totalDelay(count: Int): Int = maxRank(count) * eachMillis

    private fun randomOrder(count: Int): IntArray {
        val order = IntArray(count) { it }
        val random = Random(seed * 31 + count)
        for (i in count - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return order
    }

    public companion object {
        public val None: Stagger = Stagger(eachMillis = 0)
    }
}
