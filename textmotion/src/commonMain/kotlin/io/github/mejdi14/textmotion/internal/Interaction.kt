package io.github.mejdi14.textmotion.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.findRootCoordinates
import io.github.mejdi14.textmotion.AnimationTrigger
import io.github.mejdi14.textmotion.PointerInteraction
import io.github.mejdi14.textmotion.ScrollRange
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.TimeSource

// ---------------------------------------------------------------- pointer

/** Latest pointer state, in the composable's local coordinates. Read it in the draw phase. */
@Stable
internal class PointerTracker {
    var position: Offset by mutableStateOf(Offset.Unspecified)
    var isActive: Boolean by mutableStateOf(false)
    var isPressed: Boolean by mutableStateOf(false)
    var isTouch: Boolean = false
    private var lastInput = TimeSource.Monotonic.markNow()

    internal fun touched() { lastInput = TimeSource.Monotonic.markNow() }
    val idleMillis: Long get() = lastInput.elapsedNow().inWholeMilliseconds
}

@Composable
internal fun rememberPointerTracker(): PointerTracker = remember { PointerTracker() }

/**
 * Tracks hover (mouse/trackpad/stylus) and touch-drag positions without consuming events, so the
 * text stays usable inside scrolling containers.
 */
internal fun Modifier.trackPointer(tracker: PointerTracker, enabled: Boolean = true): Modifier =
    if (!enabled) this else pointerInput(tracker) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                tracker.isTouch = change.type == PointerType.Touch
                tracker.touched()
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move, PointerEventType.Press -> {
                        tracker.position = change.position
                        tracker.isActive = true
                        tracker.isPressed = change.pressed
                    }
                    PointerEventType.Release -> {
                        tracker.isPressed = false
                        if (tracker.isTouch) tracker.isActive = false
                    }
                    PointerEventType.Exit -> {
                        tracker.isActive = false
                        tracker.isPressed = false
                    }
                    else -> Unit
                }
            }
        }
    }

/** Point on a smooth Lissajous path covering [size], looping every [periodMillis]. */
internal fun autoPointerPosition(timeMillis: Float, periodMillis: Int, size: Size): Offset {
    val t = timeMillis / periodMillis.coerceAtLeast(1) * 2f * PI.toFloat()
    val x = 0.5f + 0.46f * sin(t)
    val y = 0.5f + 0.32f * sin(2f * t + 0.6f)
    return Offset(x * size.width, y * size.height)
}

/**
 * Resolves the effective pointer position for an effect, or [Offset.Unspecified] when the
 * effect should rest.
 */
internal fun PointerInteraction.resolve(tracker: PointerTracker, timeMillis: Float, size: Size): Offset = when (this) {
    PointerInteraction.None -> Offset.Unspecified
    PointerInteraction.Pointer -> if (tracker.isActive) tracker.position else Offset.Unspecified
    is PointerInteraction.Auto -> autoPointerPosition(timeMillis, periodMillis, size)
    is PointerInteraction.PointerWithAutoFallback -> when {
        tracker.isActive -> tracker.position
        tracker.idleMillis >= idleMillis || tracker.position == Offset.Unspecified -> autoPointerPosition(timeMillis, periodMillis, size)
        else -> tracker.position // hold the last position briefly after the pointer leaves
    }
}

// ---------------------------------------------------------------- visibility / trigger

@Stable
internal class VisibilityState {
    var isVisible: Boolean by mutableStateOf(false)
    var hasBeenVisible: Boolean by mutableStateOf(false)
    /** Incremented each time the element becomes visible. */
    var appearances: Int by mutableStateOf(0)
}

@Composable
internal fun rememberVisibilityState(): VisibilityState = remember { VisibilityState() }

internal fun Modifier.trackVisibility(state: VisibilityState, minFractionVisible: Float = 0.01f): Modifier =
    onVisibilityChanged(minFractionVisible = minFractionVisible.coerceIn(0f, 1f)) { visible ->
        if (visible && !state.isVisible) state.appearances++
        state.isVisible = visible
        if (visible) state.hasBeenVisible = true
    }

/** Whether a one-shot effect may run, given its trigger. */
internal fun AnimationTrigger.isActive(visibility: VisibilityState): Boolean = when (this) {
    AnimationTrigger.OnAppear, AnimationTrigger.Manual -> true
    is AnimationTrigger.OnVisible -> visibility.hasBeenVisible
}

/** Restart key derived from the trigger: OnVisible(once = false) restarts on every appearance. */
internal fun AnimationTrigger.restartKey(visibility: VisibilityState): Int =
    if (this is AnimationTrigger.OnVisible && !once) visibility.appearances else 0

/** Minimum visible fraction required by the trigger (used for [trackVisibility]). */
internal fun AnimationTrigger.minFraction(): Float =
    if (this is AnimationTrigger.OnVisible) minFractionVisible else 0.01f

// ---------------------------------------------------------------- scroll

/** Element position relative to the window, updated on every layout/scroll change. */
@Stable
internal class ViewportPosition {
    var top: Float by mutableStateOf(0f)
    var height: Float by mutableStateOf(0f)
    var viewportTop: Float by mutableStateOf(0f)
    var viewportHeight: Float by mutableStateOf(0f)

    /** Progress for [range]; read during draw. Returns 0 before the first layout. */
    fun progress(range: ScrollRange): Float {
        if (viewportHeight <= 0f) return 0f
        val startTop = viewportTop + range.viewportStart * viewportHeight - range.elementStart * height
        val endTop = viewportTop + range.viewportEnd * viewportHeight - range.elementEnd * height
        return clamp01(inverseLerp(startTop, endTop, top))
    }
}

@Composable
internal fun rememberViewportPosition(): ViewportPosition = remember { ViewportPosition() }

/**
 * Tracks the element's unclipped position in the window. Works with any scroll container
 * (Column + verticalScroll, LazyColumn, nested scrolling) because it observes the final position.
 * The viewport defaults to the root (window) bounds.
 */
internal fun Modifier.trackViewportPosition(state: ViewportPosition): Modifier = onGloballyPositioned { c ->
    state.top = c.positionInWindow().y
    state.height = c.size.height.toFloat()
    val root = c.findRootCoordinates()
    state.viewportTop = root.positionInWindow().y
    state.viewportHeight = root.size.height.toFloat()
}
