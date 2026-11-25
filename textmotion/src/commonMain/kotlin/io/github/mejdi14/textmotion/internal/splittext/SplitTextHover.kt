package io.github.mejdi14.textmotion.internal.splittext

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.textmotion.SplitTextHoverEffect
import kotlin.math.abs
import kotlin.math.exp

/**
 * Spring simulation behind the reference's `hoverEffect` (`lift` / `wave`): every unit keeps a
 * vertical offset `y` (px) and a velocity `v` (px/s) integrated with a semi-implicit Euler step.
 * [tick] is bumped every frame while anything moves so the draw phase is invalidated.
 */
@Stable
internal class SplitTextHover {
    var tick: Int by mutableIntStateOf(0)

    var offsets = FloatArray(0); private set
    private var velocities = FloatArray(0)
    private var targets = FloatArray(0)
    private var kickAt = FloatArray(0)
    private var kicked = BooleanArray(0)
    private var kickVelocity = 0f
    private var clock = 0f

    /** True while any unit still moves or a wave kick is pending. */
    var moving: Boolean = false
        private set

    private fun ensure(count: Int) {
        if (offsets.size == count) return
        offsets = FloatArray(count); velocities = FloatArray(count); targets = FloatArray(count)
        kickAt = FloatArray(count); kicked = BooleanArray(count) { true }
    }

    /**
     * Pointer entered: schedules the ripple of the `wave` effect. Unit [i] receives an upward velocity
     * impulse `|cx_i − px| / (9 em / s)` seconds later.
     */
    fun onEnter(effect: SplitTextHoverEffect, centersX: FloatArray, count: Int, pointerX: Float, emPx: Float) {
        if (effect != SplitTextHoverEffect.Wave) return
        ensure(count)
        val speed = emPx * WAVE_SPEED_EM
        kickVelocity = emPx * WAVE_AMPLITUDE_EM / KICK
        for (i in 0 until count) {
            kickAt[i] = clock + abs(centersX[i] - pointerX) / speed * 1000f
            kicked[i] = false
        }
        moving = true
    }

    /**
     * Advances the springs by [dtSeconds] (clamped like the reference to 1–32 ms).
     *
     * @param pointerActive whether the pointer is over the text (and the text is revealed).
     * @param pointerX / [pointerY] pointer position in layout coordinates.
     */
    fun step(
        effect: SplitTextHoverEffect,
        dtSeconds: Float,
        count: Int,
        centersX: FloatArray,
        centersY: FloatArray,
        pointerActive: Boolean,
        pointerX: Float,
        pointerY: Float,
        emPx: Float,
    ) {
        ensure(count)
        val dt = dtSeconds.coerceIn(0.001f, 0.032f)
        clock += dt * 1000f
        var any = false
        for (i in 0 until count) {
            targets[i] = if (effect == SplitTextHoverEffect.Lift && pointerActive) {
                liftTarget(centersX[i], centersY[i], pointerX, pointerY, emPx)
            } else 0f
            if (!kicked[i]) {
                if (clock >= kickAt[i]) { velocities[i] -= kickVelocity; kicked[i] = true } else any = true
            }
            val y = offsets[i]
            val v = velocities[i] + (STIFFNESS * (targets[i] - y) - DAMPING * velocities[i]) * dt
            val ny = y + v * dt
            if (abs(targets[i] - ny) < 0.02f && abs(v) < 0.05f) {
                offsets[i] = targets[i]; velocities[i] = 0f
            } else {
                offsets[i] = ny; velocities[i] = v; any = true
            }
        }
        moving = any
        tick++
    }

    /** Resets every unit to rest. */
    fun reset() {
        offsets.fill(0f); velocities.fill(0f); targets.fill(0f)
        kicked.fill(true)
        moving = false
        tick++
    }

    companion object {
        const val STIFFNESS = 180f
        const val DAMPING = 19f
        const val KICK = 0.024f
        const val LIFT_EM = 0.2f
        const val WAVE_SPEED_EM = 9f
        const val WAVE_AMPLITUDE_EM = 0.16f

        /** Gaussian bump of the `lift` effect: units near the pointer rise 0.2 em. */
        fun liftTarget(cx: Float, cy: Float, px: Float, py: Float, emPx: Float): Float {
            val radius = emPx * 1.15f
            val dx = (cx - px) / radius
            val dy = (cy - py) / (radius * 1.4f)
            return -emPx * LIFT_EM * exp(-(dx * dx + dy * dy) * 0.5f)
        }
    }
}
