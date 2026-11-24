package io.github.mejdi14.textmotion.internal.pressure

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Mutable simulation state of one TextPressure instance. Advanced once per frame by [step]
 * (outside composition); the draw phase subscribes to [frame] only.
 */
@Stable
internal class PressureState {
    /** Bumped every simulated frame to invalidate drawing. */
    var frame: Int by mutableIntStateOf(0)

    // Target (raw) and smoothed cursor, local px. NaN until the first layout.
    var cursorX = Float.NaN; var cursorY = Float.NaN
    var mouseX = Float.NaN; var mouseY = Float.NaN
    var engage = 1f
    var idleClock = 0f
    var timeSec = 0f
    var autoClockMs = 0f

    // Ring buffer of click ripples (oldest dropped beyond MAX_RIPPLES).
    val rippleX = FloatArray(PressureMath.MAX_RIPPLES)
    val rippleY = FloatArray(PressureMath.MAX_RIPPLES)
    val rippleStart = FloatArray(PressureMath.MAX_RIPPLES) { -100f }
    var rippleCount = 0
    private var rippleNext = 0

    // Per-piece geometry from the previous frame (left edge, width), reused across frames.
    var x = FloatArray(0); var w = FloatArray(0)
    var newW = FloatArray(0)
    var keys = LongArray(0)
    var pressure = FloatArray(0)

    fun ensureCapacity(n: Int, containerWidth: Float) {
        if (x.size == n) return
        x = FloatArray(n) { (it + 0.5f) * containerWidth / n.coerceAtLeast(1) }
        w = FloatArray(n); newW = FloatArray(n); keys = LongArray(n); pressure = FloatArray(n)
    }

    /** Centers the cursor when nothing is known yet (reference: initial mouse = container center). */
    fun ensureCursor(cx: Float, cy: Float) {
        if (cursorX.isNaN()) { cursorX = cx; cursorY = cy; mouseX = cx; mouseY = cy }
    }

    fun reset() {
        cursorX = Float.NaN; cursorY = Float.NaN; mouseX = Float.NaN; mouseY = Float.NaN
        engage = 1f; idleClock = 0f; timeSec = 0f; autoClockMs = 0f
        rippleCount = 0; rippleNext = 0; rippleStart.fill(-100f)
        x = FloatArray(0)
        frame++
    }

    fun addRipple(px: Float, py: Float) {
        rippleX[rippleNext] = px; rippleY[rippleNext] = py; rippleStart[rippleNext] = timeSec
        rippleNext = (rippleNext + 1) % PressureMath.MAX_RIPPLES
        if (rippleCount < PressureMath.MAX_RIPPLES) rippleCount++
    }

    /** True while at least one ripple is alive. */
    val hasLiveRipples: Boolean get() {
        for (i in 0 until PressureMath.MAX_RIPPLES) if (timeSec - rippleStart[i] < PressureMath.RIPPLE_LIFE) return true
        return false
    }

    /** Screen-blended swell of every live ripple at point ([px], [py]). */
    fun ripplePressure(px: Float, py: Float, span: Float): Float {
        var total = 0f
        for (i in 0 until PressureMath.MAX_RIPPLES) {
            val age = timeSec - rippleStart[i]
            if (age <= 0f || age >= PressureMath.RIPPLE_LIFE) continue
            val s = PressureMath.rippleSwell(hypot(px - rippleX[i], py - rippleY[i]), age, span)
            if (s > 0f) total = PressureMath.screen(total, s)
        }
        return total
    }

    /**
     * Advances one frame.
     * @param elapsedMs wall time since the previous frame.
     * @param present 1 when the pointer engages the text (or no idle mode), else 0.
     * @return true while something is still moving (cursor easing, idle animation, ripples).
     */
    fun step(elapsedMs: Float, smoothing: Float, present: Float, idleEnabled: Boolean, idleSpeed: Float): Boolean {
        val dt = elapsedMs.coerceIn(0f, PressureMath.MAX_FRAME_MS)
        timeSec += dt / 1000f
        autoClockMs += dt
        if (!cursorX.isNaN()) {
            val f = PressureMath.followFactor(dt, smoothing)
            mouseX += (cursorX - mouseX) * f
            mouseY += (cursorY - mouseY) * f
            if (abs(cursorX - mouseX) < 0.05f && abs(cursorY - mouseY) < 0.05f) { mouseX = cursorX; mouseY = cursorY }
        }
        var idling = false
        if (idleEnabled) {
            engage += (present - engage) * PressureMath.engageFactor(dt)
            if (abs(present - engage) < 0.001f) engage = present
            idling = engage < 1f
            if (idling) idleClock += dt / 1000f * idleSpeed
        } else engage = 1f
        frame++
        val easing = !cursorX.isNaN() && (mouseX != cursorX || mouseY != cursorY)
        return easing || idling || hasLiveRipples
    }

    val isIdling: Boolean get() = engage < 1f
}
