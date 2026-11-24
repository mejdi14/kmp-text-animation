package io.github.mejdi14.textmotion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Easing curves used by the reference web implementations (CSS keywords, GSAP and Motion),
 * expressed as Compose [Easing]s so they can be passed to any TextMotion composable or to
 * regular Compose animation APIs.
 */
public object TextEasing {
    public val Linear: Easing = Easing { it }

    // CSS keywords
    public val Ease: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    public val EaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    public val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    public val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    // Motion (framer-motion) named easings
    public val MotionEaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    public val MotionEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    public val MotionEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** GSAP `power{n}.in`: `t^(n+1)`. `power1` = quad, `power2` = cubic, `power3` = quart, `power4` = quint. */
    public fun powerIn(power: Int): Easing = Easing { t -> t.pow(power + 1) }

    /** GSAP `power{n}.out`: `1 - (1 - t)^(n+1)`. */
    public fun powerOut(power: Int): Easing = Easing { t -> 1f - (1f - t).pow(power + 1) }

    /** GSAP `power{n}.inOut`. */
    public fun powerInOut(power: Int): Easing = Easing { t ->
        val e = power + 1
        if (t < 0.5f) (2f * t).pow(e) / 2f else 1f - (2f * (1f - t)).pow(e) / 2f
    }

    /** GSAP `back.out(overshoot)`; default 1.70158 matches GSAP's `back.out`. */
    public fun backOut(overshoot: Float = 1.70158f): Easing = Easing { t ->
        val c3 = overshoot + 1f
        val u = t - 1f
        1f + c3 * u * u * u + overshoot * u * u
    }

    /** GSAP `back.in(overshoot)`. */
    public fun backIn(overshoot: Float = 1.70158f): Easing = Easing { t ->
        (overshoot + 1f) * t * t * t - overshoot * t * t
    }

    /** GSAP `elastic.out(amplitude, period)`. */
    public fun elasticOut(amplitude: Float = 1f, period: Float = 0.3f): Easing = Easing { t ->
        if (t == 0f || t == 1f) return@Easing t
        val a = amplitude.coerceAtLeast(1f)
        val s = period / (2f * PI.toFloat()) * kotlin.math.asin(1f / a)
        a * 2f.pow(-10f * t) * sin(((t - s) * (2f * PI.toFloat()) / period)) + 1f
    }

    /** Sine in/out, `-(cos(PI t) - 1) / 2`. */
    public val SineInOut: Easing = Easing { t -> -(cos(PI.toFloat() * t) - 1f) / 2f }

    /** CSS `cubic-bezier(x1, y1, x2, y2)`. */
    public fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing = CubicBezierEasing(x1, y1, x2, y2)
}
