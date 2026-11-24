package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.LayoutHolder
import io.github.mejdi14.textmotion.internal.blurtext.BlurPhase
import io.github.mejdi14.textmotion.internal.blurtext.BlurPieceRenderer
import io.github.mejdi14.textmotion.internal.blurtext.BlurRunKind
import io.github.mejdi14.textmotion.internal.blurtext.BlurRunState
import io.github.mejdi14.textmotion.internal.blurtext.BlurTextEvaluator
import io.github.mejdi14.textmotion.internal.blurtext.BlurTextSpec
import io.github.mejdi14.textmotion.internal.blurtext.BlurTextTiming
import io.github.mejdi14.textmotion.internal.blurtext.CHANNELS
import io.github.mejdi14.textmotion.internal.blurtext.FocusDimState
import io.github.mejdi14.textmotion.internal.blurtext.blurTextPieceCount
import io.github.mejdi14.textmotion.internal.blurtext.blurTextPieceIndex
import io.github.mejdi14.textmotion.internal.blurtext.blurTextRanks
import io.github.mejdi14.textmotion.internal.blurtext.focusDimBlur
import io.github.mejdi14.textmotion.internal.isActive
import io.github.mejdi14.textmotion.internal.minFraction
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.restartKey
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Preset motion of [BlurText] (the reference `effect` prop). */
public enum class BlurTextEffect {
    /** Blur + fade while sliding from [BlurTextDirection] with a springy overshoot (default). */
    Blur,

    /** Scales down from 1.4× while unblurring. */
    Zoom,

    /** 3D tilt (rotateX for top/bottom, rotateY for left/right) with perspective. */
    Tilt,

    /** Soft-focus pull: scale 1.12 → 1 with heavy blur, no spring. */
    Focus,

    /** Horizontal drift with a skew that straightens out. */
    Drift,
}

/** Side the pieces come from (the reference `direction` prop). */
public enum class BlurTextDirection { Top, Bottom, Left, Right }

/** Pointer gesture that replays a rested [BlurText] (reference `trigger = 'hover' | 'click'`). */
public enum class BlurTextReplay {
    /** No pointer replay. */
    None,

    /** Replay when the pointer enters the text (touch down on touch screens). */
    Hover,

    /** Replay on tap/click. */
    Click,
}

/**
 * One animation snapshot for [BlurText]'s custom `animationFrom` / `animationTo` keyframes
 * (the reference's `{ filter: 'blur(..)', opacity, y, … }` objects). Unset properties are at rest.
 *
 * @param blur Gaussian blur standard deviation (CSS `blur()`).
 * @param x horizontal offset; @param y vertical offset.
 * @param rotationX / [rotationY] 3D rotation in degrees; [skewX] horizontal skew in degrees.
 */
@Immutable
public data class BlurTextKeyframe(
    val blur: Dp = 0.dp,
    val opacity: Float = 1f,
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val scale: Float = 1f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val skewX: Float = 0f,
)

/** Defaults for [BlurText]. */
public object BlurTextDefaults {
    /** The reference's `loop = true` with the default `loopDelay = 2s`. */
    public val Loop: Repeat = Repeat(Repeat.INFINITE, delayBetweenMillis = 2000)

    /** The reference's per-piece `delay = 200` ms. */
    public val Stagger: Stagger = Stagger(eachMillis = 200)
}

/**
 * Text whose words (or letters) appear one after another out of a blur, recreating React Bits
 * "Blur Text" natively.
 *
 * Default behaviour: when 10 % of the text becomes visible, every word fades in from
 * `opacity 0 / blur(10px)` while dropping 50 dp from above with a springy overshoot; each word starts
 * 200 ms after the previous one and the fade lasts `2 × stepDurationMillis`.
 *
 * @param text the text to animate (any Unicode, multi-line, RTL…).
 * @param style typography and colour; a `brush` in the style is honoured.
 * @param effect preset motion, see [BlurTextEffect].
 * @param direction side the pieces come from.
 * @param blur starting blur (CSS `blur()` standard deviation).
 * @param distance travel distance of the pieces.
 * @param fromColor optional tint of the hidden pieces that transitions to the text colour.
 * @param focusOnHover hovering (or touching) a word blurs and dims every other word.
 * @param animationFrom custom start snapshot; together with [animationTo] / [easing] it replaces the
 *   preset (plain keyframes, no spring), like the reference's custom snapshots.
 * @param animationTo custom keyframes after [animationFrom], evenly spaced in time.
 * @param easing easing of each custom keyframe segment (linear when null and custom snapshots are used).
 * @param replayOn pointer gesture that replays the animation (out → in). When not [BlurTextReplay.None]
 *   the text starts fully visible and [trigger] is ignored, as in the reference.
 * @param stepDurationMillis duration of one keyframe step; total = steps × this (700 ms by default).
 * @param delayMillis extra delay before the first intro.
 * @param stagger delay between pieces (`delay` in the reference) and the order (`staggerFrom`).
 * @param granularity [TextGranularity.Word] (reference `animateBy='words'`) or
 *   [TextGranularity.Character] (`'letters'`); [TextGranularity.Line] is an extension.
 * @param renderMode segment rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param trigger when the intro starts; `OnVisible(0.1f)` = the reference's `threshold = 0.1`.
 * @param repeat number of intros. Between two intros the text holds for
 *   [Repeat.delayBetweenMillis] and then plays the exit animation; [BlurTextDefaults.Loop] matches
 *   the reference's `loop`. [Repeat.mode] is ignored.
 * @param speed playback speed multiplier.
 * @param controller optional external playback control (pause, restart, seek).
 * @param onFinished called each time an intro completes (`onAnimationComplete`).
 */
@Composable
public fun BlurText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    effect: BlurTextEffect = BlurTextEffect.Blur,
    direction: BlurTextDirection = BlurTextDirection.Top,
    blur: Dp = 10.dp,
    distance: Dp = 50.dp,
    fromColor: Color = Color.Unspecified,
    focusOnHover: Boolean = false,
    animationFrom: BlurTextKeyframe? = null,
    animationTo: List<BlurTextKeyframe>? = null,
    easing: Easing? = null,
    replayOn: BlurTextReplay = BlurTextReplay.None,
    stepDurationMillis: Int = 350,
    delayMillis: Int = 0,
    stagger: Stagger = BlurTextDefaults.Stagger,
    granularity: TextGranularity = TextGranularity.Word,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    trigger: AnimationTrigger = AnimationTrigger.OnVisible(minFractionVisible = 0.1f),
    repeat: Repeat = Repeat.Once,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    val spec = remember(effect, direction, blur, distance, animationFrom, animationTo, easing) {
        BlurTextSpec.build(effect, direction, blur.value, distance.value, animationFrom, animationTo, easing)
    }
    val ranks = remember(text, granularity, stagger.from, stagger.seed) {
        blurTextRanks(blurTextPieceCount(text, granularity), stagger.from, text, stagger.seed)
    }
    val maxRank = remember(ranks) { ranks.maxOrNull() ?: 0f }
    val timing = remember(spec, stepDurationMillis, stagger.eachMillis, maxRank, repeat) {
        BlurTextTiming(
            stepMillis = stepDurationMillis.coerceAtLeast(0).toFloat(),
            stepCount = spec.keyframes.size,
            custom = spec.custom,
            settle = spec.settle,
            staggerMillis = stagger.eachMillis.coerceAtLeast(0).toFloat(),
            maxRank = maxRank,
            iterations = repeat.iterations.coerceAtLeast(1),
            loopDelayMillis = repeat.delayBetweenMillis.coerceAtLeast(0).toFloat(),
        )
    }
    val evaluator = remember(spec, timing) { BlurTextEvaluator(spec, timing) }

    val run = remember(replayOn) { BlurRunState(if (replayOn == BlurTextReplay.None) BlurRunKind.Intro else BlurRunKind.Rested) }
    val restarts = controller?.restartCount ?: 0
    LaunchedEffect(restarts) { if (restarts > 0) run.kind = BlurRunKind.Intro }

    val visibility = rememberVisibilityState()
    val startOffset = run.startOffset(timing, delayMillis.coerceAtLeast(0).toFloat())
    val intro = run.kind == BlurRunKind.Intro
    val started = !intro || trigger.isActive(visibility)
    val active = started && (!timing.isInfinite || visibility.isVisible)
    val total = if (timing.isInfinite) Float.POSITIVE_INFINITY else (timing.end - startOffset).coerceAtLeast(0f)
    val timeline = rememberTimeline(
        controller = controller,
        active = active,
        speed = speed,
        totalDurationMillis = total,
        restartKey = listOf(text, run.kind, run.replays, trigger.restartKey(visibility), timing, spec),
    )

    fun currentTime(): Float {
        val seek = controller?.seekProgress
        return when {
            seek != null -> if (timing.isInfinite) startOffset + seek * timing.cycle else startOffset + seek * total
            timeline.finished -> timing.end
            else -> startOffset + timeline.elapsedMillis
        }
    }

    // onAnimationComplete: fired every time an "in" phase completes.
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(timeline, timing, run.kind, run.replays) {
        var last = timing.completedIns(startOffset)
        snapshotFlow { timing.completedIns(currentTime()) }.collect { c ->
            if (c > last) currentOnFinished?.invoke()
            last = c
        }
    }

    val phaseOut = remember { FloatArray(1) }
    fun settled(): Boolean = timing.phaseAt(currentTime(), intro, phaseOut) == BlurPhase.Rest
    val isSettled by rememberUpdatedState({ settled() })

    val tracker = rememberPointerTracker()
    val holder = remember { LayoutHolder() }
    val dim = remember { FocusDimState() }
    if (replayOn == BlurTextReplay.Hover) {
        LaunchedEffect(tracker) {
            snapshotFlow { tracker.isActive }.collect { if (it && isSettled()) run.replay() }
        }
    }
    if (focusOnHover) {
        LaunchedEffect(tracker, granularity) {
            var ticker: Job? = null
            snapshotFlow { if (tracker.isActive) tracker.position else Offset.Unspecified }.collect { pos ->
                val l = holder.value ?: return@collect
                var word = -1
                if (pos.isSpecified) {
                    val p = Offset(pos.x - holder.originPx, pos.y - holder.originPx)
                    val hit = l.segments.firstOrNull { s ->
                        p.x >= s.clipRect.left && p.x < s.clipRect.right &&
                            p.y >= l.layout.getLineTop(s.lineIndex) && p.y < l.layout.getLineBottom(s.lineIndex)
                    }
                    word = hit?.wordIndex ?: -1
                }
                if (word == dim.focus) return@collect
                dim.setFocus(word, l.wordCount, dim.nowMillis())
                if (ticker?.isActive != true) ticker = launch {
                    while (dim.isAnimating(dim.nowMillis())) withFrameNanos { dim.tick++ }
                    dim.tick++
                }
            }
        }
    }

    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { BlurPieceRenderer(graphicsContext) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    val values = remember { FloatArray(CHANNELS) }
    val local = remember { FloatArray(1) }

    var pointerModifier: Modifier = Modifier.trackPointer(tracker, enabled = focusOnHover || replayOn == BlurTextReplay.Hover)
    if (replayOn == BlurTextReplay.Click) {
        pointerModifier = pointerModifier.pointerInput(run) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (waitForUpOrCancellation() != null && isSettled()) run.replay()
            }
        }
    }

    val annotated = remember(text) { AnnotatedString(text) }
    val baseColor = if (style.color.isSpecified) style.color else Color.Black
    val brush = style.brush
    val dimBlur = focusDimBlur(blur.value)

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = modifier
            .trackVisibility(visibility, trigger.minFraction())
            .then(pointerModifier),
        granularity = granularity,
        renderMode = renderMode,
        holder = holder,
    ) { l ->
        val t = currentTime()
        val phase = timing.phaseAt(t, intro, local)
        if (focusOnHover) dim.tick // invalidate while the hover transition runs
        val now = if (focusOnHover) dim.nowMillis() else 0f
        val tinted = fromColor.isSpecified
        for (s in l.segments) {
            val piece = blurTextPieceIndex(granularity, s.index, s.wordIndex)
            val rank = if (ranks.isEmpty()) 0f else ranks[piece.coerceIn(0, ranks.size - 1)]
            evaluator.evaluate(phase, local[0], rank, values)
            val tint = if (tinted) evaluator.tint(phase, local[0], rank) else 0f
            val level = if (focusOnHover) dim.level(s.wordIndex, now) else 0f
            with(renderer) {
                drawPiece(
                    layout = l,
                    segment = s,
                    slot = s.index,
                    v = values,
                    extraSigmaPx = level * dimBlur * density,
                    alphaMultiplier = 1f - 0.7f * level,
                    spec = spec,
                    color = baseColor,
                    tintColor = fromColor,
                    tint = tint,
                    brush = brush,
                )
            }
        }
    }
}
