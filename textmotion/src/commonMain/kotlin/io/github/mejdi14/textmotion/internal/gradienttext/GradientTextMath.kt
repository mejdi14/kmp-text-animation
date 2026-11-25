package io.github.mejdi14.textmotion.internal.gradienttext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Position and size of one `flow` variant blob, all in percent of the element box. */
internal data class FlowBlob(
    val xPercent: Float,
    val yPercent: Float,
    /** Horizontal radius, percent of the element width. */
    val radiusXPercent: Float,
    /** Vertical radius, percent of the element height. */
    val radiusYPercent: Float,
)

/**
 * Non-visual math of the GradientText effect (React Bits): palette normalisation, the per-variant
 * gradient parameters derived from the cycle position, and the conic stop list.
 */
internal object GradientTextMath {
    val DefaultColors: List<Color> = listOf(Color(0xFF5227FF), Color(0xFFFF9FFC), Color(0xFFB497CF))

    const val MinCycleSeconds: Float = 0.2f
    const val MinScale: Float = 1f
    const val MaxScale: Float = 6f

    /** Empty palettes fall back to the defaults and a single colour is duplicated (reference). */
    fun normalizeColors(colors: List<Color>): List<Color> = when (colors.size) {
        0 -> DefaultColors
        1 -> listOf(colors[0], colors[0])
        else -> colors
    }

    /** Palette closed with its first colour so the repeating gradient is seamless. */
    fun loopColors(colors: List<Color>): List<Color> = colors + colors[0]

    /** `direction` overrides `angle`. */
    fun resolveAngle(angleDegrees: Float, directionAngle: Float?): Float = directionAngle ?: angleDegrees

    fun clampScale(scale: Float): Float = scale.coerceIn(MinScale, MaxScale)

    /** Length of one cycle in seconds (reference `animationSpeed`, minimum 0.2 s). */
    fun cycleSeconds(cycleMillis: Int): Float = (cycleMillis / 1000f).coerceAtLeast(MinCycleSeconds)

    /** Gradient period of the linear variant, percent of the gradient line (`100 * scale`). */
    fun periodPercent(scale: Float): Float = 100f * clampScale(scale)

    /** Cosine ping-pong: 0..1..0 over a `cycle` of 2. */
    fun pingPong(cycle: Float): Float {
        val swing = positiveMod(cycle, 2f)
        val t = if (swing < 1f) swing else 2f - swing
        return 0.5f - cos(PI.toFloat() * t) / 2f
    }

    /**
     * Linear variant: shift of the repeating gradient in percent of the gradient line (before the
     * pointer pull). Yoyo ping-pongs with a cosine ease over `period - 100`; otherwise it loops
     * seamlessly over one period.
     */
    fun linearOffsetPercent(cycle: Float, scale: Float, yoyo: Boolean): Float {
        val period = periodPercent(scale)
        return if (yoyo) pingPong(cycle) * (period - 100f) else positiveMod(cycle, 1f) * period
    }

    /** Extra offset that follows the pointer (`(px - 0.5) * period * 0.5 * pull`). */
    fun pointerOffsetPercent(px: Float, scale: Float, pull: Float): Float =
        (px - 0.5f) * periodPercent(scale) * 0.5f * pull

    /** Flow variant: blob [index] at [cycle]; blob 0 is pulled towards the pointer by [pull]. */
    fun flowBlob(index: Int, cycle: Float, scale: Float, pull: Float, px: Float, py: Float): FlowBlob {
        val spin = cycle * 2f * PI.toFloat()
        val rate = 0.55f + positiveMod(index * 0.618f, 1f) * 0.5f
        var x = 50f + 44f * sin(spin * rate + index * 2.1f)
        var y = 50f + 40f * sin(spin * (rate * 0.83f + 0.21f) + index * 1.3f)
        if (index == 0) {
            x += (px * 100f - x) * pull
            y += (py * 100f - y) * pull
        }
        val s = clampScale(scale)
        return FlowBlob(x, y, 22f + 12f * s, 60f + 30f * s)
    }

    /** Conic variant: sway of the ray fan in degrees. */
    fun conicSwayDegrees(cycle: Float, yoyo: Boolean): Float =
        if (yoyo) sin(cycle * PI.toFloat()) * 50f else cycle * 360f

    /** Conic variant: angular width of one colour period in degrees (`60 * 3 / scale`). */
    fun conicPeriodDegrees(scale: Float): Float = 60f * (3f / clampScale(scale))

    /** CSS `from` angle of the conic gradient (`sway - 30`). */
    fun conicFromDegrees(cycle: Float, yoyo: Boolean): Float = conicSwayDegrees(cycle, yoyo) - 30f

    /** Centre of the conic gradient in percent of the element box (`pull` moves it to the pointer). */
    fun conicCenterXPercent(px: Float, pull: Float): Float = 50f + (px * 100f - 50f) * pull
    fun conicCenterYPercent(py: Float, pull: Float): Float = 260f + (py * 100f - 260f) * pull

    /**
     * Stops of a full 360 degree sweep that reproduces `repeating-conic-gradient` with the
     * (already normalised) palette [colors], closed with its first colour, repeated every
     * [conicPeriodDegrees]. Positions are fractions of the turn in `0..1`, starting at the
     * gradient's own 0 degree angle.
     */
    fun conicStops(colors: List<Color>, scale: Float): List<Pair<Float, Color>> {
        val n = colors.size
        val period = conicPeriodDegrees(scale)
        // Points of every period; each period runs c0..c(n-1) and ends on c0 (= next period's start).
        val points = ArrayList<Pair<Float, Color>>()
        var rep = 0
        while (true) {
            val base = rep * period
            for (i in 0 until n) points += (base + i.toFloat() / n * period) to colors[i]
            if (base + period >= 360f) break
            rep++
        }
        val endPos = (rep + 1) * period
        val last = points.indexOfLast { it.first < 360f }
        val before = points[last]
        val afterPos = if (last + 1 < points.size) points[last + 1].first else endPos
        val afterColor = if (last + 1 < points.size) points[last + 1].second else colors[0]
        val t = ((360f - before.first) / (afterPos - before.first)).coerceIn(0f, 1f)
        val out = ArrayList<Pair<Float, Color>>()
        for (k in 0..last) out += (points[k].first / 360f) to points[k].second
        out += 1f to lerp(before.second, afterColor, t)
        return out
    }

    private fun positiveMod(x: Float, m: Float): Float = x - floor(x / m) * m
}

/**
 * Frame-to-frame state of GradientText that the reference keeps in its rAF loop: accumulated
 * time (frozen while hovered with `pauseOnHover`), the follow-pointer `pull` and a critically
 * damped spring that smooths the pointer. [advance] is idempotent for an unchanged clock, so it
 * can be called by several draw passes of the same frame.
 */
internal class GradientDynamics {
    /** Accumulated animation time in seconds (frozen while hover-paused). */
    var time: Float = 0f
        private set
    var pull: Float = 0f
        private set
    /** Smoothed pointer position, normalised 0..1 inside the element. */
    var px: Float = 0.5f
        private set
    var py: Float = 0.5f
        private set

    private var vx = 0f
    private var vy = 0f
    private var targetX = 0.5f
    private var targetY = 0.5f
    private var last = -1f

    fun advance(
        elapsedMillis: Float,
        pointerInside: Boolean,
        pointerX: Float,
        pointerY: Float,
        followPointer: Boolean,
        freezeTime: Boolean,
    ) {
        if (last < 0f || elapsedMillis < last) { // first frame or restart
            time = elapsedMillis / 1000f
            pull = 0f; px = 0.5f; py = 0.5f; vx = 0f; vy = 0f; targetX = 0.5f; targetY = 0.5f
            last = elapsedMillis
            return
        }
        val raw = (elapsedMillis - last) / 1000f
        if (raw <= 0f) return
        last = elapsedMillis
        if (!freezeTime) time += raw
        if (!followPointer) return
        val dt = raw.coerceAtMost(MaxStep)
        if (pointerInside) { targetX = pointerX.coerceIn(0f, 1f); targetY = pointerY.coerceIn(0f, 1f) }
        val goal = if (pointerInside) 1f else 0f
        pull += (goal - pull) * (1f - exp(-dt * 6f))
        vx += ((targetX - px) * STIFFNESS - vx * DAMPING) * dt
        px += vx * dt
        vy += ((targetY - py) * STIFFNESS - vy * DAMPING) * dt
        py += vy * dt
    }

    private companion object {
        const val MaxStep = 0.05f
        const val STIFFNESS = 160f
        val DAMPING = 2f * sqrt(STIFFNESS)
    }
}
