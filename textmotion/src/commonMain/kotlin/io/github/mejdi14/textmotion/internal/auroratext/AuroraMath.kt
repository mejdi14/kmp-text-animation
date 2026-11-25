package io.github.mejdi14.textmotion.internal.auroratext

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** One sampled frame of the Aurora keyframes. */
internal data class AuroraFrame(
    /** CSS `background-position-x` fraction (0..1) of the 200%-wide gradient layer. */
    val backgroundX: Float,
    /** Rotation of the text in degrees (clockwise, like CSS `rotate`). */
    val rotationDegrees: Float,
    /** Uniform scale of the text around its centre. */
    val scale: Float,
)

/**
 * Non-visual math of the AuroraText effect (Magic UI): the `aurora` keyframes, CSS
 * `infinite alternate` iteration mapping with per-interval easing, and the geometry of a CSS
 * `linear-gradient(<angle>, …)` painted over a `200% auto` background layer.
 */
internal object AuroraMath {
    /** Keyframe offsets of `@keyframes aurora`. */
    val Offsets: FloatArray = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    /** `background-position` x per keyframe (y has no visible effect: the layer is 100% tall). */
    val BackgroundX: FloatArray = floatArrayOf(0f, 0.5f, 1f, 0.5f, 0f)
    /** `rotate(…)` per keyframe, degrees. */
    val Rotation: FloatArray = floatArrayOf(-5f, 5f, -3f, 3f, -5f)
    /** `scale(…)` per keyframe. */
    val Scale: FloatArray = floatArrayOf(0.9f, 1.1f, 0.95f, 1.05f, 0.9f)

    /** Reference cycle length at `speed = 1` (`animationDuration = 10 / speed s`). */
    const val DefaultDurationMillis: Int = 10_000

    /** Reference gradient angle (`linear-gradient(135deg, …)`). */
    const val DefaultAngleDegrees: Float = 135f

    /** Reference background-size width factor (`bg-size-[200%_auto]`). */
    const val LayerWidthFactor: Float = 2f

    /**
     * Directed progress (0..1) of an `infinite alternate` animation: even iterations run
     * forward, odd iterations run backward. [elapsedMillis] is the clock time.
     */
    fun directedProgress(elapsedMillis: Float, durationMillis: Float): Float {
        if (durationMillis <= 0f) return 0f
        val cycles = elapsedMillis / durationMillis
        val iteration = floor(cycles)
        val local = cycles - iteration
        val odd = abs(iteration.toLong() % 2L) == 1L
        return if (odd) 1f - local else local
    }

    /**
     * Samples the keyframes at directed progress [p] (0..1). As in CSS, [easing] is applied to
     * the local progress of each keyframe interval (`animation-timing-function` is per keyframe).
     * [intensity] scales the rotation and the scale deviation from 1 (1 = reference, 0 = none).
     */
    fun frameAt(p: Float, easing: Easing, intensity: Float = 1f): AuroraFrame {
        val c = p.coerceIn(0f, 1f)
        val last = Offsets.size - 2
        var i = 0
        while (i < last && c >= Offsets[i + 1]) i++
        val span = Offsets[i + 1] - Offsets[i]
        val t = easing.transform(((c - Offsets[i]) / span).coerceIn(0f, 1f))
        val bg = lerp(BackgroundX[i], BackgroundX[i + 1], t)
        val rot = lerp(Rotation[i], Rotation[i + 1], t) * intensity
        val sc = 1f + (lerp(Scale[i], Scale[i + 1], t) - 1f) * intensity
        return AuroraFrame(bg, rot, sc)
    }

    /**
     * Horizontal offset (px) of the gradient layer for CSS `background-position-x: p%` when the
     * layer is [layerFactor]× as wide as the element of width [width]: `(W − kW)·p`.
     */
    fun layerOffsetX(p: Float, width: Float, layerFactor: Float = LayerWidthFactor): Float =
        (width - layerFactor * width) * p

    /**
     * Start/end points of a CSS `linear-gradient([angleDegrees], …)` painted in a box of
     * [boxWidth]×[boxHeight] whose top-left is the origin. CSS angles: 0° points up, 90° right,
     * clockwise; the gradient line passes through the box centre and its length is
     * `|W·sin a| + |H·cos a|`, so the corners get the first/last colours exactly.
     */
    fun cssGradientLine(angleDegrees: Float, boxWidth: Float, boxHeight: Float): Pair<Offset, Offset> {
        val a = angleDegrees * (PI.toFloat() / 180f)
        val dx = sin(a)
        val dy = -cos(a)
        val half = (abs(boxWidth * dx) + abs(boxHeight * dy)) / 2f
        val cx = boxWidth / 2f
        val cy = boxHeight / 2f
        return Offset(cx - dx * half, cy - dy * half) to Offset(cx + dx * half, cy + dy * half)
    }

    /**
     * Gradient colour stops of the reference: [colors] followed by the first colour again so the
     * flow loops seamlessly. Empty input yields an empty list; a single colour is a solid fill.
     */
    fun <T> loopedStops(colors: List<T>): List<T> = when (colors.size) {
        0 -> emptyList()
        1 -> listOf(colors[0], colors[0])
        else -> colors + colors[0]
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
