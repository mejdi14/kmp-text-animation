package io.github.mejdi14.textmotion.internal.morph

import androidx.compose.animation.core.Easing
import io.github.mejdi14.textmotion.TextMorphTransition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Closed-form damped harmonic oscillator, equivalent to Motion's `spring` generator
 * (stiffness k, damping c, mass m).
 *
 * `x` is the displacement from the target (current − target) and `v` its velocity in units per
 * second. Both are pure functions of the initial state, so a frame never integrates anything.
 */
internal class SpringSolver(stiffness: Float, damping: Float, mass: Float) {
    private val k = stiffness.coerceAtLeast(1e-3f).toDouble()
    private val c = damping.coerceAtLeast(0f).toDouble()
    private val m = mass.coerceAtLeast(1e-3f).toDouble()

    /** Undamped angular frequency (rad/s). */
    val omega0: Double = sqrt(k / m)

    /** Damping ratio ζ = c / (2·√(k·m)). */
    val dampingRatio: Double = c / (2.0 * sqrt(k * m))

    private val omegaD = if (dampingRatio < 1.0) omega0 * sqrt(1.0 - dampingRatio * dampingRatio) else 0.0
    private val overRoot = if (dampingRatio > 1.0) omega0 * sqrt(dampingRatio * dampingRatio - 1.0) else 0.0

    /** Displacement at [tSec] for initial displacement [x0] and velocity [v0]. */
    fun position(x0: Double, v0: Double, tSec: Double): Double {
        if (tSec <= 0.0) return x0
        val z = dampingRatio; val w = omega0
        return when {
            z < 1.0 -> {
                val e = exp(-z * w * tSec)
                e * (x0 * cos(omegaD * tSec) + (v0 + z * w * x0) / omegaD * sin(omegaD * tSec))
            }
            z == 1.0 -> exp(-w * tSec) * (x0 + (v0 + w * x0) * tSec)
            else -> {
                val r1 = -z * w + overRoot
                val r2 = -z * w - overRoot
                val a = (v0 - r2 * x0) / (r1 - r2)
                val b = x0 - a
                a * exp(r1 * tSec) + b * exp(r2 * tSec)
            }
        }
    }

    /** Velocity (units/s) at [tSec] for initial displacement [x0] and velocity [v0]. */
    fun velocity(x0: Double, v0: Double, tSec: Double): Double {
        if (tSec <= 0.0) return v0
        val z = dampingRatio; val w = omega0
        return when {
            z < 1.0 -> {
                val e = exp(-z * w * tSec)
                val b = (v0 + z * w * x0) / omegaD
                val cs = cos(omegaD * tSec); val sn = sin(omegaD * tSec)
                e * ((-x0 * omegaD + b * -z * w) * sn + (b * omegaD - z * w * x0) * cs)
            }
            z == 1.0 -> {
                val e = exp(-w * tSec)
                val b = v0 + w * x0
                e * (b - w * (x0 + b * tSec))
            }
            else -> {
                val r1 = -z * w + overRoot
                val r2 = -z * w - overRoot
                val a = (v0 - r2 * x0) / (r1 - r2)
                val b = x0 - a
                a * r1 * exp(r1 * tSec) + b * r2 * exp(r2 * tSec)
            }
        }
    }

    /**
     * Time (ms) after which the spring is at rest, sampled like Motion's per-frame check:
     * `|x| <= restDelta && |v| <= restSpeed`. Capped at [maxMillis].
     */
    fun settleMillis(x0: Double, v0: Double, restDelta: Double, restSpeed: Double, maxMillis: Double = 20_000.0): Float {
        if (abs(x0) <= restDelta && abs(v0) <= restSpeed) return 0f
        var t = 0.0
        while (t < maxMillis) {
            t += SAMPLE_MS
            val s = t / 1000.0
            if (abs(position(x0, v0, s)) <= restDelta && abs(velocity(x0, v0, s)) <= restSpeed) return t.toFloat()
        }
        return maxMillis.toFloat()
    }

    companion object {
        /** Motion checks rest once per frame; 1/120 s sampling keeps us at least as precise. */
        const val SAMPLE_MS: Double = 1000.0 / 120.0
    }
}

/** Motion's `springDefaults`: values whose total change is < 5 are "granular" (opacity, scale). */
internal object MotionRest {
    const val DELTA_DEFAULT: Double = 0.5
    const val DELTA_GRANULAR: Double = 0.005
    const val SPEED_DEFAULT: Double = 2.0
    const val SPEED_GRANULAR: Double = 0.01

    /** Motion's layout animations animate a 0 → 1000 progress value with the given transition. */
    const val LAYOUT_PROGRESS_SCALE: Double = 1000.0
}

/**
 * Evaluates a [TextMorphTransition] for the two channels TextMorph animates:
 * - **layout**: Motion animates a normalized progress (0 → 1000) and projects the box from its
 *   previous visual position to its new one. An interrupted layout animation restarts from the
 *   current visual position with zero velocity. [layoutRemaining] returns the fraction of the
 *   start offset still to travel (1 → 0, may go slightly negative on overshoot).
 * - **presence** (the variant blend 0 = initial/exit, 1 = animate): a plain value animation that
 *   inherits the current velocity when interrupted, exactly like Motion's opacity springs.
 */
internal class MorphMotion(val transition: TextMorphTransition) {
    private val spring: SpringSolver? = (transition as? TextMorphTransition.Spring)?.let {
        SpringSolver(it.stiffness, it.damping, it.mass)
    }
    private val tweenDuration: Float = (transition as? TextMorphTransition.Tween)?.durationMillis?.toFloat()?.coerceAtLeast(0f) ?: 0f
    private val tweenEasing: Easing? = (transition as? TextMorphTransition.Tween)?.easing

    /** Delay before every channel starts moving. */
    val delayMillis: Float = when (transition) {
        is TextMorphTransition.Spring -> transition.delayMillis.toFloat()
        is TextMorphTransition.Tween -> transition.delayMillis.toFloat()
    }.coerceAtLeast(0f)

    /** Duration of a layout move, delay included (identical for every glyph). */
    val layoutSettleMillis: Float = delayMillis + (
        spring?.settleMillis(
            MotionRest.LAYOUT_PROGRESS_SCALE, 0.0, MotionRest.DELTA_DEFAULT, MotionRest.SPEED_DEFAULT,
        ) ?: tweenDuration
        )

    /** Remaining fraction of a layout move [tMillis] after it started. */
    fun layoutRemaining(tMillis: Float): Float {
        val t = tMillis - delayMillis
        if (t <= 0f) return 1f
        if (tMillis >= layoutSettleMillis) return 0f
        val s = spring
        return if (s != null) {
            (s.position(1.0, 0.0, t / 1000.0)).toFloat()
        } else {
            1f - tweenEasing!!.transform((t / tweenDuration.coerceAtLeast(1f)).coerceIn(0f, 1f))
        }
    }

    /** Time (ms) until a presence animation from [from] (velocity [v0]/s) to [target] rests. */
    fun presenceSettleMillis(from: Float, v0: Float, target: Float): Float {
        val s = spring ?: return if (from == target) 0f else delayMillis + tweenDuration
        val x0 = (from - target).toDouble()
        val granular = abs(x0) < 5.0
        val settle = s.settleMillis(
            x0, v0.toDouble(),
            if (granular) MotionRest.DELTA_GRANULAR else MotionRest.DELTA_DEFAULT,
            if (granular) MotionRest.SPEED_GRANULAR else MotionRest.SPEED_DEFAULT,
        )
        return if (settle == 0f) 0f else delayMillis + settle
    }

    /** Presence value [tMillis] after an animation from [from]/[v0] to [target] started. */
    fun presence(from: Float, v0: Float, target: Float, tMillis: Float, settleMillis: Float): Float {
        if (tMillis >= settleMillis) return target
        val t = tMillis - delayMillis
        if (t <= 0f) return from
        val s = spring
        return if (s != null) {
            target + s.position((from - target).toDouble(), v0.toDouble(), t / 1000.0).toFloat()
        } else {
            from + (target - from) * tweenEasing!!.transform((t / tweenDuration.coerceAtLeast(1f)).coerceIn(0f, 1f))
        }
    }

    /** Presence velocity (units/s). Tweens report 0, like Motion's keyframe generator ignoring velocity. */
    fun presenceVelocity(from: Float, v0: Float, target: Float, tMillis: Float, settleMillis: Float): Float {
        if (tMillis >= settleMillis) return 0f
        val t = tMillis - delayMillis
        if (t <= 0f) return v0
        val s = spring ?: return 0f
        return s.velocity((from - target).toDouble(), v0.toDouble(), t / 1000.0).toFloat()
    }
}
