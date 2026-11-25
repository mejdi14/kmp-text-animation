package io.github.mejdi14.textmotion.internal.fuzzy

import androidx.compose.runtime.Immutable
import io.github.mejdi14.textmotion.FuzzyTrigger
import kotlin.math.max

/** Tunables read by [FuzzEngine] every fuzz frame. */
@Immutable
internal data class FuzzSettings(
    val baseIntensity: Float,
    val hoverIntensity: Float,
    val enableHover: Boolean,
    val trigger: FuzzyTrigger,
    val fps: Int,
    val transitionMillis: Float,
    val clickEffect: Boolean,
    val glitchMode: Boolean,
    val glitchIntervalMillis: Float,
    val glitchDurationMillis: Float,
    val intro: Boolean,
    val introDurationMillis: Float,
)

/**
 * Per-frame intensity state machine of FuzzyText (hover target, transition smoothing, click flash,
 * glitch bursts, click burst envelope, intro). Plain fields, no snapshot state: it is stepped from
 * the frame loop and read in the draw phase after a redraw tick.
 *
 * All times are the effect's own clock in milliseconds (scaled by `speed`, frozen while paused).
 */
internal class FuzzEngine {
    /** Fuzz amplitude factor (multiplied by `fuzzRange`); may exceed 1 during burst/intro. */
    var intensity: Float = 0f
        private set

    /** Slice size multiplier (> 1 during burst/intro energy). */
    var coarse: Float = 1f
        private set

    /** Whole-text opacity (fades in during the first 30 % of the intro). */
    var opacity: Float = 1f
        private set

    /** Incremented for every drawn fuzz frame; seeds the per-slice random offsets. */
    var seed: Int = 0
        private set

    /** Clock owned by the frame loop. */
    var clock: Float = 0f

    private var started = false
    private var eased = 0f
    private var lastFrame = 0f
    private var lastTick = 0f
    private var drawn = -1f
    private var clickingUntil = Float.NEGATIVE_INFINITY
    private var bursting = false
    private var burstAt = 0f
    private var introStarted = false
    private var introStart = 0f
    private var introDone = false

    /** Restarts everything (intro included), e.g. on `controller.restart()`. */
    fun reset() {
        intensity = 0f; coarse = 1f; opacity = 1f; clock = 0f
        started = false; drawn = -1f; clickingUntil = Float.NEGATIVE_INFINITY
        bursting = false; introStarted = false; introDone = false
    }

    /** True while an intro is configured but has not completed: the text should still be hidden before it starts. */
    fun introPending(settings: FuzzSettings): Boolean = settings.intro && !introDone && !introStarted

    /** A click/tap on the text. */
    fun press(now: Float, settings: FuzzSettings) {
        if (settings.trigger == FuzzyTrigger.Click) {
            bursting = true
            burstAt = now
        } else if (settings.clickEffect) {
            clickingUntil = now + FuzzMath.CLICK_FLASH
        }
    }

    /**
     * Advances to [now]. Frames are throttled to `settings.fps` like the reference
     * (`now - last < 1000 / fps - 1` skips). Returns true when a new fuzz frame must be drawn.
     */
    fun frame(now: Float, hovering: Boolean, settings: FuzzSettings): Boolean {
        val interval = 1000f / max(1, settings.fps)
        if (started && now - lastFrame < interval - 1f) return false
        val dt = if (started) now - lastTick else interval
        if (!started) {
            started = true
            lastFrame = now
            eased = settings.baseIntensity
        } else {
            lastFrame = max(lastFrame + interval, now - interval)
        }
        lastTick = now

        val base = settings.baseIntensity
        val active = when {
            settings.trigger == FuzzyTrigger.Always -> settings.hoverIntensity
            settings.trigger != FuzzyTrigger.Click && hovering && settings.enableHover -> settings.hoverIntensity
            else -> base
        }
        val glitch = settings.glitchMode &&
            FuzzMath.glitchActive(now, settings.glitchIntervalMillis, settings.glitchDurationMillis)
        val goal = if (now < clickingUntil || glitch) 1f else active
        eased = FuzzMath.transition(eased, goal, dt, settings.transitionMillis)

        var level = eased
        var energy = 0f
        if (bursting) {
            val age = now - burstAt
            val envelope = FuzzMath.burstEnvelope(age)
            if (age >= FuzzMath.BURST_ATTACK && envelope < 0.004f) {
                bursting = false
            } else {
                val peak = FuzzMath.burstPeak(settings.hoverIntensity)
                level += (max(peak, level) - level) * envelope
                energy = envelope
            }
        }
        if (!introDone) {
            if (!settings.intro) {
                introDone = true
                opacity = 1f
            } else {
                if (!introStarted) { introStarted = true; introStart = now }
                val p = ((now - introStart) / max(1f, settings.introDurationMillis)).coerceAtMost(1f)
                val envelope = FuzzMath.introEnvelope(p)
                level += (FuzzMath.INTRO_PEAK - level) * envelope
                energy = max(energy, envelope)
                opacity = FuzzMath.introOpacity(p)
                if (p >= 1f) { introDone = true; opacity = 1f }
            }
        }
        intensity = level
        coarse = FuzzMath.coarse(energy)
        if (intensity == 0f && drawn == 0f) return false
        drawn = intensity
        seed++
        return true
    }
}
