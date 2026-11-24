package io.github.mejdi14.textmotion.internal

import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.TextAnimationController

/**
 * Frame-driven clock. [elapsedMillis] is a snapshot state that should only be read during the
 * draw phase (inside `Modifier.drawBehind`, `Canvas`, `graphicsLayer {}` lambdas…) so that each
 * frame only invalidates drawing, never composition or layout.
 */
@Stable
internal class TimelineState {
    var elapsedMillis: Float by mutableFloatStateOf(0f)
    var finished: Boolean by mutableStateOf(false)
    internal var lastRestart: Int = -1
    internal var lastKey: Any? = Unset

    internal object Unset
}

/**
 * Remembers a [TimelineState] that advances while [active] and the optional [controller] is
 * playing. The coroutine is cancelled when the composable leaves the composition, so nothing
 * keeps running in the background.
 *
 * @param totalDurationMillis when reached the clock stops and [onFinished] is called. Use
 *   [Float.POSITIVE_INFINITY] for continuous effects.
 * @param restartKey changing it resets the clock to 0 (e.g. pass the text).
 */
@Composable
internal fun rememberTimeline(
    controller: TextAnimationController?,
    active: Boolean = true,
    speed: Float = 1f,
    totalDurationMillis: Float = Float.POSITIVE_INFINITY,
    restartKey: Any? = null,
    onFinished: (() -> Unit)? = null,
): TimelineState {
    val state = remember { TimelineState() }
    val restartCount = controller?.restartCount ?: 0
    val playing = controller?.isPlaying ?: true
    val currentSpeed by rememberUpdatedState(speed)
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(restartCount, restartKey, playing, active, totalDurationMillis) {
        if (state.lastRestart != restartCount || state.lastKey != restartKey) {
            state.lastRestart = restartCount
            state.lastKey = restartKey
            state.elapsedMillis = 0f
            state.finished = false
            controller?.isFinished = false
        }
        if (!playing || !active || state.finished) return@LaunchedEffect
        if (state.elapsedMillis >= totalDurationMillis) {
            state.finished = true
            controller?.isFinished = true
            currentOnFinished?.invoke()
            return@LaunchedEffect
        }
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0L) {
                    val dt = (now - last) / 1_000_000f
                    val s = currentSpeed * (controller?.speed ?: 1f)
                    state.elapsedMillis = (state.elapsedMillis + dt * s).coerceAtMost(totalDurationMillis)
                }
                last = now
            }
            if (state.elapsedMillis >= totalDurationMillis) {
                state.finished = true
                controller?.isFinished = true
                currentOnFinished?.invoke()
                break
            }
        }
    }
    return state
}

/** Total wall time of a [Repeat] whose single iteration lasts [iterationMillis]. */
internal fun Repeat.totalMillis(iterationMillis: Float): Float =
    if (isInfinite) Float.POSITIVE_INFINITY
    else iterations * iterationMillis + (iterations - 1).coerceAtLeast(0) * delayBetweenMillis

/**
 * Maps a raw elapsed time to the 0..1 progress of the current iteration, honouring
 * [Repeat.mode] (reverse iterations run 1 → 0) and [Repeat.delayBetweenMillis] (holds the end
 * state during the delay).
 */
internal fun Repeat.progressAt(elapsedMillis: Float, iterationMillis: Float): Float {
    if (iterationMillis <= 0f) return 1f
    val cycle = iterationMillis + delayBetweenMillis
    val total = totalMillis(iterationMillis)
    if (elapsedMillis >= total) {
        val lastReversed = mode == RepeatMode.Reverse && (iterations - 1) % 2 == 1
        return if (lastReversed) 0f else 1f
    }
    val iteration = (elapsedMillis / cycle).toInt()
    val local = elapsedMillis - iteration * cycle
    val p = (local / iterationMillis).coerceIn(0f, 1f)
    return if (mode == RepeatMode.Reverse && iteration % 2 == 1) 1f - p else p
}

/** Resolves the progress to render, letting a controller seek override the clock. */
internal fun TextAnimationController?.resolveProgress(clockProgress: Float): Float =
    this?.seekProgress ?: clockProgress
