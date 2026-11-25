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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.LayoutHolder
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.isActive
import io.github.mejdi14.textmotion.internal.lerp
import io.github.mejdi14.textmotion.internal.minFraction
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.restartKey
import io.github.mejdi14.textmotion.internal.splittext.CHANNELS
import io.github.mejdi14.textmotion.internal.splittext.SplitRunState
import io.github.mejdi14.textmotion.internal.splittext.SplitTextHover
import io.github.mejdi14.textmotion.internal.splittext.SplitTextRenderer
import io.github.mejdi14.textmotion.internal.splittext.SplitTextSpecCache
import io.github.mejdi14.textmotion.internal.splittext.SplitTextTiming
import io.github.mejdi14.textmotion.internal.splittext.UnitState
import io.github.mejdi14.textmotion.internal.splittext.defaultEasing
import io.github.mejdi14.textmotion.internal.splittext.emPx
import io.github.mejdi14.textmotion.internal.splittext.estimateUnitCount
import io.github.mejdi14.textmotion.internal.splittext.splitTextDelays
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import kotlinx.coroutines.flow.first

/** Preset reveal of [SplitText] (the reference `effect` prop). */
public enum class SplitTextEffect {
    /** Units rise 40 dp into place while fading in (`power3.out`, default). */
    Rise,

    /** Plain fade (`power2.inOut`). */
    Fade,

    /** Fade in from a blur of about 0.14 em (`power2.out`). */
    Blur,

    /** Flip up around the bottom edge: `rotationX -90°` with perspective (`power3.out`). */
    Flip,

    /** Scale up from 0 with an overshoot (`back.out(1.6)`). */
    Pop,

    /** Slide in from the left while a −24° skew straightens (`expo.out`). */
    Skew,

    /** Rise 0.45 em with a custom ease that overshoots once and settles. */
    Wave,
}

/** Pointer interaction that keeps playing with the units after the reveal (reference `hoverEffect`). */
public enum class SplitTextHoverEffect {
    /** None. */
    None,

    /** Units near the pointer rise 0.2 em (Gaussian bump) on a spring. */
    Lift,

    /** A ripple travels outward from the point where the pointer entered. */
    Wave,
}

/** Gesture that replays a revealed [SplitText] (reference `trigger = 'hover' | 'click'`). */
public enum class SplitTextReplay {
    /** No pointer replay. */
    None,

    /** Replay (exit, then reveal) when the pointer enters; touch down on touch screens. */
    Hover,

    /** Replay on tap/click. */
    Click,
}

/**
 * One animation snapshot for [SplitText]'s custom `animationFrom` / `animationTo` (the reference's
 * GSAP `from` / `to` vars). Unset properties are at rest.
 *
 * @param opacity 0..1; @param x / [y] translation; @param scale uniform scale.
 * @param rotation rotation around the z axis in degrees; [rotationX] / [rotationY] 3D rotation (no
 *   perspective) in degrees; [skewX] horizontal skew in degrees; [blur] Gaussian blur (CSS `blur()`).
 */
@Immutable
public data class SplitTextKeyframe(
    val opacity: Float = 1f,
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val skewX: Float = 0f,
    val blur: Dp = 0.dp,
)

/** Defaults for [SplitText]. */
public object SplitTextDefaults {
    /** The reference's `delay = 50` ms between two units. */
    public val Stagger: Stagger = Stagger(eachMillis = 50)

    /** The reference's `loop = true` with the default `loopDelay = 1.5 s`. */
    public val Loop: Repeat = Repeat(Repeat.INFINITE, delayBetweenMillis = 1500)

    /** The reference's `duration = 1.25 s`. */
    public const val DurationMillis: Int = 1250
}

/**
 * Text split into characters, words or lines that reveal one after another, recreating React Bits
 * "Split Text" (a GSAP SplitText + ScrollTrigger effect) natively.
 *
 * Default behaviour: once 10 % of the text is visible, every character rises 40 dp while fading in
 * (`power3.out`, 1.25 s), each starting 50 ms after the previous one; the text box clips the
 * characters while they travel (the reference's `overflow: hidden`). Everything is drawn from a
 * single shaped paragraph in the draw phase: no composable per letter.
 *
 * @param text the text to animate (any Unicode, multi-line, RTL; emoji clusters stay whole).
 * @param style typography and colour (alignment included); a `brush` in the style is honoured.
 * @param effect preset reveal, see [SplitTextEffect].
 * @param granularity unit that animates: [TextGranularity.Character] (`splitType = 'chars'`),
 *   [TextGranularity.Word] (`'words'`) or [TextGranularity.Line] (`'lines'`).
 * @param animationFrom custom start snapshot (reference `from`); together with [animationTo] it
 *   replaces the preset. Default `opacity 0, y 40 dp`.
 * @param animationTo custom end snapshot (reference `to`); defaults to the rest pose.
 * @param mask clip every unit to its own line box so it appears out of a mask (reference `mask`).
 * @param clipOverflow clip everything to the text box. `null` = like the reference: only without a
 *   hover effect and for [SplitTextEffect.Rise], [SplitTextEffect.Fade] or custom snapshots.
 * @param accentColor units flash this colour during the reveal, then settle on the text colour
 *   (ignored when the style uses a brush).
 * @param hoverEffect pointer interaction after the reveal, see [SplitTextHoverEffect].
 * @param replayOn pointer gesture that replays the animation once it is revealed.
 * @param durationMillis duration of one unit's reveal (`duration = 1.25 s`).
 * @param delayMillis extra delay before the first reveal.
 * @param easing overrides the preset ease (reference `ease`); `power3.out` for custom snapshots.
 * @param stagger delay between units (`delay`) and order (`staggerFrom`: start, end, center, edges,
 *   random). Like GSAP the whole spread is `each × (count − 1)`.
 * @param renderMode segment rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param trigger when the first reveal starts; `OnVisible(0.1f)` = the reference's `threshold = 0.1`
 *   (the `rootMargin` of the ScrollTrigger start is approximated by the visibility threshold),
 *   `OnAppear` = `trigger = 'load'`.
 * @param repeat number of reveals. Between two reveals the text holds for [Repeat.delayBetweenMillis],
 *   exits (`max(350 ms, 0.55 × duration)`, `power2.in`) and reveals again; [SplitTextDefaults.Loop]
 *   matches `loop = true`. [Repeat.mode] is ignored.
 * @param speed playback speed multiplier.
 * @param controller optional external playback control (pause, restart, seek).
 * @param onFinished called each time a reveal completes (`onLetterAnimationComplete`).
 */
@Composable
public fun SplitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    effect: SplitTextEffect = SplitTextEffect.Rise,
    granularity: TextGranularity = TextGranularity.Character,
    animationFrom: SplitTextKeyframe? = null,
    animationTo: SplitTextKeyframe? = null,
    mask: Boolean = false,
    clipOverflow: Boolean? = null,
    accentColor: Color = Color.Unspecified,
    hoverEffect: SplitTextHoverEffect = SplitTextHoverEffect.None,
    replayOn: SplitTextReplay = SplitTextReplay.None,
    durationMillis: Int = SplitTextDefaults.DurationMillis,
    delayMillis: Int = 0,
    easing: Easing? = null,
    stagger: Stagger = SplitTextDefaults.Stagger,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    trigger: AnimationTrigger = AnimationTrigger.OnVisible(minFractionVisible = 0.1f),
    repeat: Repeat = Repeat.Once,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    val custom = animationFrom != null || animationTo != null
    val specCache = remember(effect, custom, animationFrom, animationTo) {
        SplitTextSpecCache(effect, custom, animationFrom, animationTo)
    }
    val ease = remember(effect, custom, easing) {
        easing ?: if (custom) TextEasing.powerOut(3) else defaultEasing(effect)
    }

    // Number of animated units: estimated from the text, then corrected from the real layout.
    var unitCount by remember(text, granularity) { mutableIntStateOf(estimateUnitCount(text, granularity)) }
    val delays = remember(unitCount, stagger.from, stagger.eachMillis, stagger.seed) {
        splitTextDelays(unitCount, stagger.from, stagger.eachMillis.coerceAtLeast(0).toFloat(), stagger.seed)
    }
    val timing = remember(durationMillis, delays, repeat, ease) {
        SplitTextTiming(
            durationMillis = durationMillis.coerceAtLeast(0).toFloat(),
            spreadMillis = delays.maxOrNull() ?: 0f,
            iterations = repeat.iterations.coerceAtLeast(1),
            holdMillis = repeat.delayBetweenMillis.coerceAtLeast(0).toFloat(),
            ease = ease,
        )
    }

    val run = remember { SplitRunState() }
    val restarts = controller?.restartCount ?: 0
    LaunchedEffect(restarts) { if (restarts > 0) run.resetToIntro() }

    val visibility = rememberVisibilityState()
    val replay = run.replay
    val startOffset = if (replay) 0f else -delayMillis.coerceAtLeast(0).toFloat()
    val started = replay || trigger.isActive(visibility)
    val active = started && (!timing.isInfinite || visibility.isVisible)
    val total = if (timing.isInfinite) Float.POSITIVE_INFINITY else (timing.end(replay) - startOffset).coerceAtLeast(0f)
    val timeline = rememberTimeline(
        controller = controller,
        active = active,
        speed = speed,
        totalDurationMillis = total,
        restartKey = listOf(text, replay, run.replays, trigger.restartKey(visibility), timing),
    )

    fun currentTime(): Float {
        val seek = controller?.seekProgress
        return when {
            seek != null -> startOffset + seek * (if (timing.isInfinite) timing.cycleSpan else total)
            timeline.finished -> timing.end(replay)
            else -> startOffset + timeline.elapsedMillis
        }
    }

    // onLetterAnimationComplete: fired every time a reveal completes.
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(timeline, timing, replay, run.replays) {
        var last = timing.completedReveals(startOffset, replay)
        snapshotFlow { timing.completedReveals(currentTime(), replay) }.collect { c ->
            if (c > last) currentOnFinished?.invoke()
            last = c
        }
    }

    fun isSettled(): Boolean = timing.isRested(currentTime(), replay)
    val settled by rememberUpdatedState({ isSettled() })

    val tracker = rememberPointerTracker()
    val holder = remember { LayoutHolder() }
    val hover = remember { SplitTextHover() }
    if (replayOn == SplitTextReplay.Hover) {
        LaunchedEffect(tracker) {
            snapshotFlow { tracker.isActive }.collect { if (it && settled()) run.requestReplay() }
        }
    }
    if (hoverEffect != SplitTextHoverEffect.None) {
        LaunchedEffect(hoverEffect, tracker) {
            var cachedLayout: SegmentedTextLayout? = null
            var cx = FloatArray(0)
            var cy = FloatArray(0)
            var wasActive = false
            var last = 0L
            while (true) {
                if (!hover.moving && !tracker.isActive) {
                    snapshotFlow { tracker.isActive }.first { it }
                    wasActive = false
                    last = 0L
                }
                withFrameNanos { now ->
                    val l = holder.value
                    if (l != null) {
                        val n = l.segments.size
                        if (cachedLayout !== l) {
                            cx = FloatArray(n) { l.segments[it].bounds.center.x }
                            cy = FloatArray(n) { l.segments[it].bounds.center.y }
                            cachedLayout = l
                        }
                        val em = l.emPx()
                        val pointer = tracker.position
                        val px = pointer.x - holder.originPx
                        val py = pointer.y - holder.originPx
                        val activeNow = tracker.isActive && settled()
                        if (activeNow && !wasActive) hover.onEnter(hoverEffect, cx, n, px, em)
                        wasActive = activeNow
                        val dt = if (last == 0L) 0.016f else (now - last) / 1_000_000_000f
                        hover.step(hoverEffect, dt, n, cx, cy, activeNow, px, py, em)
                    }
                    last = now
                }
            }
        }
    }

    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { SplitTextRenderer(graphicsContext) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    val values = remember { FloatArray(CHANNELS) }
    val unit = remember { UnitState() }

    var pointerModifier: Modifier = Modifier.trackPointer(
        tracker,
        enabled = hoverEffect != SplitTextHoverEffect.None || replayOn == SplitTextReplay.Hover,
    )
    if (replayOn == SplitTextReplay.Click) {
        pointerModifier = pointerModifier.pointerInput(run) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (waitForUpOrCancellation() != null && settled()) run.requestReplay()
            }
        }
    }

    val annotated = remember(text) { AnnotatedString(text) }
    val baseColor = if (style.color.isSpecified) style.color else Color.Black
    val brush = style.brush
    val clip = clipOverflow ?: (hoverEffect == SplitTextHoverEffect.None &&
        (custom || effect == SplitTextEffect.Rise || effect == SplitTextEffect.Fade))
    val hoverOn = hoverEffect != SplitTextHoverEffect.None

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = modifier
            .trackVisibility(visibility, trigger.minFraction())
            .then(pointerModifier),
        granularity = granularity,
        renderMode = renderMode,
        holder = holder,
        onLayoutChanged = { unitCount = it.segments.size },
    ) { l ->
        val t = currentTime()
        val spec = specCache.get(l.emPx(), density)
        if (hoverOn) hover.tick // invalidate while the springs move
        withTransform({ if (clip) clipRect(0f, 0f, size.width, size.height) }) {
            for (s in l.segments) {
                val i = s.index
                val delay = if (delays.isEmpty()) 0f else delays[i.coerceIn(0, delays.size - 1)]
                timing.evaluate(t, delay, replay, unit)
                val m = unit.mix
                for (c in 0 until CHANNELS) values[c] = lerp(spec.from[c], spec.to[c], m)
                val extra = if (hoverOn && i < hover.offsets.size) hover.offsets[i] else 0f
                val accent = if (accentColor.isSpecified && brush == null) unit.accent else 0f
                with(renderer) {
                    drawUnit(
                        layout = l, segment = s, slot = i, v = values, spec = spec, extraY = extra, mask = mask,
                        color = baseColor, accentColor = accentColor, accent = accent, brush = brush,
                    )
                }
            }
        }
    }
}
