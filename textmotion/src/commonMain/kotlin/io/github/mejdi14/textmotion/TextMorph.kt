package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.drawSegment
import io.github.mejdi14.textmotion.internal.lerp
import io.github.mejdi14.textmotion.internal.morph.MorphMotion
import io.github.mejdi14.textmotion.internal.morph.MorphState
import io.github.mejdi14.textmotion.internal.needsShapingContext

/**
 * Transition used by [TextMorph] for both the layout glide of shared pieces and the
 * enter/exit blend (Motion `transition` prop).
 */
@Immutable
public sealed interface TextMorphTransition {
    /**
     * Physical spring (Motion `type: 'spring'`). Defaults are the reference's
     * `{ stiffness: 280, damping: 18, mass: 0.3 }` (damping ratio ≈ 0.98, settles in ≈ 0.4 s).
     * Enter/exit springs keep their velocity when interrupted, like Motion.
     */
    @Immutable
    public data class Spring(
        val stiffness: Float = 280f,
        val damping: Float = 18f,
        val mass: Float = 0.3f,
        val delayMillis: Int = 0,
    ) : TextMorphTransition

    /** Duration-based transition (Motion `type: 'tween'`; Motion's default tween is 300 ms easeInOut). */
    @Immutable
    public data class Tween(
        val durationMillis: Int = 300,
        val easing: Easing = TextEasing.MotionEaseInOut,
        val delayMillis: Int = 0,
    ) : TextMorphTransition
}

/**
 * Visual state of a piece while it enters or leaves (Motion `variants.initial` / `variants.exit`).
 * The resting (`animate`) state is always the identity: fully opaque, unscaled, in place.
 *
 * @param alpha opacity (0..1).
 * @param scale uniform scale around the piece's centre.
 * @param offsetX horizontal offset.
 * @param offsetY vertical offset (positive = down).
 */
@Immutable
public data class TextMorphVariant(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
) {
    public companion object {
        /** The reference variant: `{ opacity: 0 }`. */
        public val Hidden: TextMorphVariant = TextMorphVariant(alpha = 0f)
    }
}

/**
 * Morphs smoothly between successive values of [text] (Motion Primitives "Text Morph").
 *
 * Every character gets an identity made of its lowercased value and its occurrence index
 * ("hello" → h1 e1 l1 l2 o1). When [text] changes, characters whose identity survives glide from
 * their previous on-screen position to their new one with [transition]; new characters fade in
 * ([enter]) and removed ones fade out ([exit]) where they were, out of the layout flow (Motion
 * `AnimatePresence mode="popLayout"`). Changing [text] mid-morph starts from the current animated
 * positions. Nothing animates on first appearance unless [animateInitial] is true.
 *
 * Pieces are grapheme clusters, so emoji and combining sequences morph as one unit. Text in
 * scripts whose glyphs depend on their neighbours (Arabic, Hebrew, Indic…) morphs word by word
 * instead, keeping shaping intact. Measurement happens once per text change; frames only draw.
 *
 * @param text real, dynamic text; change it to morph.
 * @param style text style (font, size, weight, letter spacing, alignment, colour or brush).
 * @param transition spring (default, reference values) or tween used for every channel.
 * @param enter state new pieces fade in from (reference: opacity 0).
 * @param exit state removed pieces fade out to (reference: opacity 0).
 * @param ignoreCase whether "H" and "h" are the same piece (the reference lowercases: true).
 * @param animateInitial animate the first text in with [enter] (reference: false).
 * @param granularity unit that morphs; null picks characters, or words for complex scripts.
 * @param renderMode how pieces are drawn (see [SegmentRenderMode]).
 * @param softWrap whether the text wraps at the available width.
 * @param maxLines maximum number of lines.
 * @param speed time multiplier.
 * @param controller optional pause/resume, restart (replays the latest morph) and seek
 *   (0..1 of the latest morph).
 * @param onFinished called each time a morph comes to rest.
 */
@Composable
public fun TextMorph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    transition: TextMorphTransition = TextMorphTransition.Spring(),
    enter: TextMorphVariant = TextMorphVariant.Hidden,
    exit: TextMorphVariant = TextMorphVariant.Hidden,
    ignoreCase: Boolean = true,
    animateInitial: Boolean = false,
    granularity: TextGranularity? = null,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    val state = remember { MorphState() }
    state.motion = remember(transition) { MorphMotion(transition) }
    val annotated = remember(text) { AnnotatedString(text) }
    val autoGranularity = remember(text) { if (needsShapingContext(text)) TextGranularity.Word else TextGranularity.Character }
    val resolvedGranularity = granularity ?: autoGranularity
    val currentSpeed by rememberUpdatedState(speed)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val playing = controller?.isPlaying ?: true
    val restartCount = controller?.restartCount ?: 0

    LaunchedEffect(state.generation, playing, restartCount) {
        if (state.lastRestart != restartCount) {
            val firstSeen = state.lastRestart == Int.MIN_VALUE
            state.lastRestart = restartCount
            if (!firstSeen) {
                state.replay() // bumps generation → this effect relaunches and plays it
                return@LaunchedEffect
            }
        }
        if (!playing) return@LaunchedEffect
        if (!state.isSettled()) {
            controller?.isFinished = false
            var last = -1L
            while (!state.isSettled()) {
                withFrameNanos { frame ->
                    if (last >= 0L) {
                        val dt = (frame - last) / 1_000_000f
                        state.advance(dt * currentSpeed * (controller?.speed ?: 1f))
                    }
                    last = frame
                }
            }
        }
        if (state.hasPendingMorph) {
            state.hasPendingMorph = false
            controller?.isFinished = true
            currentOnFinished?.invoke()
        }
    }

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = modifier.onGloballyPositioned { state.onPositioned(it.positionInRoot()) },
        granularity = resolvedGranularity,
        renderMode = renderMode,
        softWrap = softWrap,
        maxLines = maxLines,
        onLayoutChanged = { state.sync(it, ignoreCase, animateInitial) },
    ) { _ ->
        state.onDrawn()
        val motion = state.motion ?: return@AnimatedTextLayout
        val seek = controller?.seekProgress
        val now = if (seek != null) state.morphStart + seek * (state.morphEnd - state.morphStart) else state.clockMillis
        val enterX = enter.offsetX.toPx(); val enterY = enter.offsetY.toPx()
        val exitX = exit.offsetX.toPx(); val exitY = exit.offsetY.toPx()
        for (item in state.items) {
            if (item.exiting && now >= item.presenceStart + item.presenceSettle) continue
            val p = item.presence(motion, now)
            val v = if (item.useExitVariant) exit else enter
            val alpha = lerp(v.alpha, 1f, p).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val scale = lerp(v.scale, 1f, p)
            val r = item.moveRemaining(motion, now)
            val vx = if (item.useExitVariant) exitX else enterX
            val vy = if (item.useExitVariant) exitY else enterY
            val dx = item.fromX * r + vx * (1f - p)
            val dy = item.fromY * r + vy * (1f - p)
            val seg = item.segment
            if (dx == 0f && dy == 0f && scale == 1f) {
                drawSegment(item.layout, seg, alpha = alpha)
            } else {
                withTransform({
                    translate(dx, dy)
                    if (scale != 1f) scale(scale, scale, pivot = Offset(seg.bounds.center.x, seg.bounds.center.y))
                }) { drawSegment(item.layout, seg, alpha = alpha) }
            }
        }
    }
}
