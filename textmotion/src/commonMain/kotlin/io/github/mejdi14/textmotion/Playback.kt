package io.github.mejdi14.textmotion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * When a one-shot text animation starts playing.
 */
@Immutable
public sealed interface AnimationTrigger {
    /** Start as soon as the composable enters the composition. */
    public data object OnAppear : AnimationTrigger

    /**
     * Start when at least [minFractionVisible] of the composable is visible in its viewport
     * (the web equivalent of an IntersectionObserver threshold).
     * When [once] is false the animation restarts every time it becomes visible again.
     */
    public data class OnVisible(val minFractionVisible: Float = 0.1f, val once: Boolean = true) : AnimationTrigger

    /** Never start automatically; use [TextAnimationController.play]. */
    public data object Manual : AnimationTrigger
}

/**
 * How many times an animation runs and how it repeats.
 *
 * @param iterations number of runs; [INFINITE] for an endless loop.
 * @param mode [RepeatMode.Restart] jumps back to the start, [RepeatMode.Reverse] plays backwards.
 * @param delayBetweenMillis pause between two iterations.
 */
@Immutable
public data class Repeat(
    val iterations: Int = 1,
    val mode: RepeatMode = RepeatMode.Restart,
    val delayBetweenMillis: Int = 0,
) {
    val isInfinite: Boolean get() = iterations == INFINITE

    public companion object {
        public const val INFINITE: Int = Int.MAX_VALUE
        public val Once: Repeat = Repeat(1)
        public val Forever: Repeat = Repeat(INFINITE)
        public fun times(n: Int, mode: RepeatMode = RepeatMode.Restart, delayBetweenMillis: Int = 0): Repeat =
            Repeat(n, mode, delayBetweenMillis)
    }
}

/**
 * External playback control shared by every TextMotion composable.
 *
 * Create it with [rememberTextAnimationController] and pass it to an effect to pause, resume,
 * restart or scrub it. State changes only invalidate drawing, never the layout.
 */
@Stable
public class TextAnimationController(autoPlay: Boolean = true) {
    /** True while time is advancing. */
    public var isPlaying: Boolean by mutableStateOf(autoPlay)
        private set

    /** Incremented by [restart]; effects key their clocks on it. */
    public var restartCount: Int by mutableIntStateOf(0)
        private set

    /**
     * When not null, the effect renders this normalized progress (0..1 of one iteration)
     * instead of its own clock. Useful for scrubbing with a slider or driving from a gesture.
     */
    public var seekProgress: Float? by mutableStateOf(null)
        private set

    /** Playback speed multiplier applied on top of the effect's own `speed` parameter. */
    public var speed: Float by mutableFloatStateOf(1f)

    /** Set by the effect: whether the current run has finished. */
    public var isFinished: Boolean by mutableStateOf(false)
        internal set

    public fun play() { seekProgress = null; isPlaying = true }
    public fun pause() { isPlaying = false }
    public fun toggle() { if (isPlaying) pause() else play() }

    /** Restart from the beginning and play. */
    public fun restart() {
        seekProgress = null
        isFinished = false
        restartCount++
        isPlaying = true
    }

    /** Freeze the animation at [progress] (0..1). Call [play] to resume the internal clock. */
    public fun seekTo(progress: Float) {
        seekProgress = progress.coerceIn(0f, 1f)
        isPlaying = false
    }
}

/** Remembers a [TextAnimationController]. */
@Composable
public fun rememberTextAnimationController(autoPlay: Boolean = true): TextAnimationController =
    remember { TextAnimationController(autoPlay) }
