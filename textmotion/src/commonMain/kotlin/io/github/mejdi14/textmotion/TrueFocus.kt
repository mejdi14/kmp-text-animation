package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.LayoutHolder
import io.github.mejdi14.textmotion.internal.SegmentBlurRenderer
import io.github.mejdi14.textmotion.internal.clamp01
import io.github.mejdi14.textmotion.internal.lerp
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import io.github.mejdi14.textmotion.internal.truefocus.FocusCycle
import io.github.mejdi14.textmotion.internal.truefocus.FocusState
import io.github.mejdi14.textmotion.internal.truefocus.FrameMotion
import io.github.mejdi14.textmotion.internal.truefocus.WordGeometry
import io.github.mejdi14.textmotion.internal.truefocus.drawFocusFrame
import io.github.mejdi14.textmotion.internal.truefocus.focusIntervalMillis
import io.github.mejdi14.textmotion.internal.truefocus.hitTestWord
import io.github.mejdi14.textmotion.internal.truefocus.lerpRect
import io.github.mejdi14.textmotion.internal.truefocus.scaledFrame
import io.github.mejdi14.textmotion.internal.truefocus.segmentOwners
import io.github.mejdi14.textmotion.internal.truefocus.splitFocusWords

/** Shape of the focus indicator (reference `focusStyle`). */
public enum class TrueFocusFrameStyle {
    /** Four corner brackets around the word (default). */
    Brackets,

    /** A full rounded rectangle outline. */
    Box,

    /** A bar under the word. */
    Underline,

    /** A translucent (30 %) filled marker behind the word. */
    Highlight,
}

/** Order in which auto mode visits the words (reference `order`). */
public enum class TrueFocusOrder {
    /** Word after word, wrapping around. */
    Sequential,

    /** A random *other* word each time. */
    Random,
}

/** Motion of the frame and the word scale (reference `easing`). */
public enum class TrueFocusEasing {
    /** Tween with `cubic-bezier(0.65, 0, 0.35, 1)`. */
    Smooth,

    /** Spring with a small overshoot (visual duration = animation duration, bounce 0.38). */
    Spring,
}

/**
 * Words of [text] where one is sharp, framed by an animated indicator, and the rest are blurred
 * (React Bits "True Focus"), recreated natively.
 *
 * In automatic mode the focus moves to the next word every `animationDurationMillis +
 * pauseBetweenMillis`; with [manualMode] it follows the pointer instead (hover on desktop/web,
 * touch/drag on phones, see [pointerInteraction]). The frame slides and resizes between words;
 * blur, opacity and scale of the words cross-fade over the same duration. The clock stops while
 * the text is off-screen or the [controller] is paused.
 *
 * @param text the sentence; split at [separator] (blank words are dropped).
 * @param style typography and colour. Words are separated by a 1 em gap like the reference.
 * @param separator where [text] is split into focusable words.
 * @param manualMode follow the pointer instead of the timer.
 * @param frameStyle indicator shape.
 * @param order auto-mode word order.
 * @param easing frame/scale motion.
 * @param blurAmount blur of unfocused words (CSS `blur()` standard deviation).
 * @param focusScale scale of the focused word (frame follows).
 * @param borderColor indicator colour (CSS `green` by default).
 * @param highlightColor fill of [TrueFocusFrameStyle.Highlight]; [Color.Unspecified] uses [borderColor].
 * @param glowColor soft glow around the indicator; transparent disables it.
 * @param animationDurationMillis duration of each focus change.
 * @param pauseBetweenMillis rest between two automatic changes.
 * @param cornerSize bracket arm length. @param borderWidth stroke thickness.
 * @param unfocusedOpacity opacity of unfocused words.
 * @param pointerInteraction how [manualMode] gets its pointer: [PointerInteraction.Pointer] (reference),
 *   [PointerInteraction.PointerWithAutoFallback] keeps cycling automatically while no pointer is
 *   present, [PointerInteraction.Auto] ignores pointers, [PointerInteraction.None] freezes the focus.
 * @param renderMode word rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param speed time multiplier.
 * @param controller pause/resume/restart (seek is not supported: the effect is continuous).
 * @param onFocusChanged called with the index and text of every newly focused word.
 */
@Composable
public fun TrueFocus(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    separator: String = " ",
    manualMode: Boolean = false,
    frameStyle: TrueFocusFrameStyle = TrueFocusFrameStyle.Brackets,
    order: TrueFocusOrder = TrueFocusOrder.Sequential,
    easing: TrueFocusEasing = TrueFocusEasing.Smooth,
    blurAmount: Dp = 5.dp,
    focusScale: Float = 1f,
    borderColor: Color = Color(0xFF008000),
    highlightColor: Color = Color.Unspecified,
    glowColor: Color = Color.Transparent,
    animationDurationMillis: Int = 500,
    pauseBetweenMillis: Int = 1000,
    cornerSize: Dp = 16.dp,
    borderWidth: Dp = 3.dp,
    unfocusedOpacity: Float = 1f,
    pointerInteraction: PointerInteraction = PointerInteraction.Pointer,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFocusChanged: ((index: Int, word: String) -> Unit)? = null,
) {
    val words = remember(text, separator) { splitFocusWords(text, separator) }
    val joined = remember(words) { AnnotatedString(words.joinToString(WORD_GAP)) }
    val owners = remember(words) { segmentOwners(words) }
    val n = words.size
    val duration = animationDurationMillis.coerceAtLeast(0).toFloat()
    val settleMillis = if (easing == TrueFocusEasing.Spring) {
        maxOf(duration, FrameMotion.settleMillis(duration))
    } else duration

    val state = remember(words) { FocusState(n).also { it.settleUntil = settleMillis } }
    val geometry = remember(words) { WordGeometry() }
    val cycle = remember(words, animationDurationMillis, pauseBetweenMillis, order) {
        FocusCycle(focusIntervalMillis(animationDurationMillis, pauseBetweenMillis), order == TrueFocusOrder.Random, seed = text.hashCode())
    }
    val tracker = rememberPointerTracker()
    val holder = remember { LayoutHolder() }
    val visibility = rememberVisibilityState()
    val currentOnFocus by rememberUpdatedState(onFocusChanged)

    val autoCapable = n >= 2 && (!manualMode || pointerInteraction is PointerInteraction.Auto ||
        pointerInteraction is PointerInteraction.PointerWithAutoFallback)
    var busy by remember(words) { mutableStateOf(true) }
    val timeline = rememberTimeline(
        controller = controller,
        active = visibility.isVisible && (autoCapable || busy),
        speed = speed,
        restartKey = words,
    )

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val gapPx = GAP_DP * density
    val cornerPx = cornerSize.value * density
    val borderPx = borderWidth.value * density
    val scale = focusScale.coerceAtLeast(0.01f)
    val levelEase = TextEasing.Ease

    /** Animated frame rectangle (px, layout space) at [now]; null before the first layout. */
    fun frameAt(rects: List<Rect>, now: Float): Rect? {
        val target = rects.getOrNull(state.target) ?: return null
        val to = scaledFrame(target, scale)
        val from = state.fromFrame ?: return to
        return lerpRect(from, to, FrameMotion.progress(easing, now - state.startedAt, duration))
    }

    LaunchedEffect(state, cycle, timeline, manualMode, pointerInteraction, duration, easing, scale, words) {
        snapshotFlow { Triple(timeline.elapsedMillis, tracker.position, tracker.isActive) }.collect {
            val now = timeline.elapsedMillis
            val l = holder.value
            if (l != null && n > 0) {
                val rects = geometry.get(l, owners, n)
                var next = -1
                if (manualMode && pointerInteraction !is PointerInteraction.None && pointerInteraction !is PointerInteraction.Auto && tracker.isActive) {
                    val p = Offset(tracker.position.x - holder.originPx, tracker.position.y - holder.originPx)
                    val hit = hitTestWord(geometry.hitRects, p)
                    if (hit >= 0 && hit != state.target) { next = hit; cycle.reset(now) }
                }
                val auto = autoCapable && when (val pi = pointerInteraction) {
                    is PointerInteraction.PointerWithAutoFallback -> !manualMode || (!tracker.isActive && tracker.idleMillis >= pi.idleMillis)
                    else -> true
                }
                if (next < 0 && auto) next = cycle.tick(now, state.target, n)
                if (next >= 0 && next != state.target) {
                    state.retarget(next, now, duration, { levelEase.transform(it) }, frameAt(rects, now))
                    state.settleUntil = now + settleMillis
                    currentOnFocus?.invoke(next, words[next])
                }
            }
            busy = now < state.settleUntil
        }
    }

    val graphicsContext = LocalGraphicsContext.current
    val blur = remember(graphicsContext) { SegmentBlurRenderer(graphicsContext) }
    DisposableEffect(blur) { onDispose { blur.release() } }
    val corners = remember { FloatArray(8) }
    val textColor = if (style.color != Color.Unspecified) style.color else Color.Black
    val brush = style.brush
    val blurPx = blurAmount.value * density

    AnimatedTextLayout(
        text = joined,
        style = style,
        modifier = modifier
            .trackVisibility(visibility)
            .trackPointer(tracker, enabled = manualMode && pointerInteraction !is PointerInteraction.None),
        granularity = TextGranularity.Word,
        renderMode = renderMode,
        drawPadding = (GAP_DP + borderWidth.value + 10f).dp,
        holder = holder,
    ) { l ->
        val now = timeline.elapsedMillis
        val rects = geometry.get(l, owners, n)
        val ease = { x: Float -> levelEase.transform(x) }
        for (s in l.segments) {
            val w = owners.getOrNull(s.wordIndex) ?: continue
            val level = state.level(w, now, duration, ease)
            val sigma = (1f - level) * blurPx
            val alpha = lerp(unfocusedOpacity, 1f, level)
            val k = lerp(1f, scale, level)
            if (k != 1f) {
                withTransform({ scale(k, k, rects[w].center) }) {
                    with(blur) { drawBlurred(l, s, s.index, sigma, alpha, textColor, Color.Unspecified, 0f, brush) }
                }
            } else {
                with(blur) { drawBlurred(l, s, s.index, sigma, alpha, textColor, Color.Unspecified, 0f, brush) }
            }
        }
        val frame = frameAt(rects, now)
        if (frame != null) {
            val fade = if (duration <= 0f) 1f else clamp01((now - state.mountedAt) / duration)
            drawFocusFrame(frameStyle, frame, cornerPx, gapPx, borderPx, borderColor, highlightColor, glowColor, fade, corners)
        }
    }
}

private const val GAP_DP = 10f

/** One em of space between words, like the reference's `gap: 1em`. */
private const val WORD_GAP = " "
