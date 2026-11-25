package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.rotatingtext.RCH
import io.github.mejdi14.textmotion.internal.rotatingtext.RC_BLUR
import io.github.mejdi14.textmotion.internal.rotatingtext.RC_OPACITY
import io.github.mejdi14.textmotion.internal.rotatingtext.RC_ROT_X
import io.github.mejdi14.textmotion.internal.rotatingtext.RC_SCALE
import io.github.mejdi14.textmotion.internal.rotatingtext.RC_Y
import io.github.mejdi14.textmotion.internal.rotatingtext.RotatingTextMachine
import io.github.mejdi14.textmotion.internal.rotatingtext.RotatingTextPlan
import io.github.mejdi14.textmotion.internal.rotatingtext.buildEffectSpec
import io.github.mejdi14.textmotion.internal.rotatingtext.highlightTextColorFor
import io.github.mejdi14.textmotion.internal.rotatingtext.rotatingUnitRanges
import io.github.mejdi14.textmotion.internal.splittext.CHANNELS
import io.github.mejdi14.textmotion.internal.splittext.CH_BLUR
import io.github.mejdi14.textmotion.internal.splittext.CH_OPACITY
import io.github.mejdi14.textmotion.internal.splittext.CH_ROT_X
import io.github.mejdi14.textmotion.internal.splittext.CH_SCALE
import io.github.mejdi14.textmotion.internal.splittext.CH_Y
import io.github.mejdi14.textmotion.internal.splittext.SplitTextRenderer
import io.github.mejdi14.textmotion.internal.splittext.SplitTextSpec
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import io.github.mejdi14.textmotion.internal.withDefaultColor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

/** Entrance/exit style of every unit of a [RotatingText] (the reference's `effect`). */
public enum class RotatingTextEffect {
    /** Units slide vertically (`y 100% -> 0 -> -120%`) on a spring; the default. */
    Slide,

    /** Units flip around the horizontal axis with perspective (`rotateX -90 -> 0 -> 90`). */
    Flip,

    /** Units drift 45 % of their height while fading and blurring (10 dp blur unless `blur` is set). */
    Blur,

    /** Units scale from 0.35x (or 1.7x when [RotatingTextDirection.Down]) to 1x and out to 1.7x. */
    Scale,

    /** Units fall in from above (`y -110%`) on a springy ease and drop out below. */
    Drop,
}

/** Direction of the [RotatingText] motion. [Down] mirrors every preset. */
public enum class RotatingTextDirection { Up, Down }

/** What a [RotatingText] animates as one unit (the reference's `splitBy`). */
public sealed interface RotatingTextSplit {
    /** Every user-perceived character (grapheme cluster) is a unit. */
    public data object Characters : RotatingTextSplit

    /** Every whitespace-separated word is a unit. */
    public data object Words : RotatingTextSplit

    /** Every `\n`-separated line is a unit. */
    public data object Lines : RotatingTextSplit

    /** The text is split on [separator]; the separators themselves are not drawn. */
    public data class Custom(val separator: String) : RotatingTextSplit
}

/** Which unit starts first when units are staggered (the reference's `staggerFrom`). */
public sealed interface RotatingTextStaggerFrom {
    /** Unit 0 first. */
    public data object First : RotatingTextStaggerFrom

    /** Last unit first. */
    public data object Last : RotatingTextStaggerFrom

    /** Middle unit first, spreading outwards. */
    public data object Center : RotatingTextStaggerFrom

    /** A random permutation, re-created every time a text enters. */
    public data object Random : RotatingTextStaggerFrom

    /** Unit [index] first, delay growing with the distance to it. */
    public data class Index(val index: Int) : RotatingTextStaggerFrom
}

/** Replacement for the enter/exit transition of a [RotatingText] (the reference's `transition`). */
public sealed interface RotatingTextTransition {
    /** Damped spring (Motion `type: "spring"`), from rest. */
    public data class Spring(
        val damping: Float = 25f,
        val stiffness: Float = 300f,
        val mass: Float = 1f,
    ) : RotatingTextTransition

    /** Time-based curve. */
    public data class Tween(
        val durationMillis: Int,
        val easing: Easing = TextEasing.EaseInOut,
    ) : RotatingTextTransition
}

/**
 * Imperative handle of a [RotatingText] (the reference's `next / previous / jumpTo / reset` ref).
 * Create it with [rememberRotatingTextState] and pass it as `state`.
 */
@Stable
public class RotatingTextState {
    /** Index of the text that is shown or about to be shown (changes at the start of the exit). */
    public var currentIndex: Int by mutableIntStateOf(0)
        internal set

    /** Number of texts of the attached [RotatingText]. */
    public var textCount: Int by mutableIntStateOf(0)
        internal set

    internal var machine: RotatingTextMachine? = null

    /** Moves to the next text (wraps around when the attached text has `loop = true`). */
    public fun next() { machine?.next() }

    /** Moves to the previous text (wraps around when the attached text has `loop = true`). */
    public fun previous() { machine?.previous() }

    /** Moves to text [index] (clamped to the valid range). */
    public fun jumpTo(index: Int) { machine?.jumpTo(index) }

    /** Jumps back to the first text (`reset()`). */
    public fun reset() { machine?.jumpTo(0) }
}

/** Remembers a [RotatingTextState]. */
@Composable
public fun rememberRotatingTextState(): RotatingTextState = remember { RotatingTextState() }

/** Layout cache of every text for one constraint/style combination. */
private class RotatingLayouts {
    var key: Any? = null
    var layouts: List<SegmentedTextLayout> = emptyList()
}

private data class RotatingLayoutKey(
    val texts: List<String>,
    val split: RotatingTextSplit,
    val style: TextStyle,
    val maxWidth: Int,
    val density: Float,
    val fontScale: Float,
    val rtl: Boolean,
    val renderMode: SegmentRenderMode,
    val padding: Int,
)

/**
 * Cycles through [texts] in place, animating every character (or word / line) of the leaving text
 * out and of the entering text in with a staggered spring (React Bits "Rotating Text").
 *
 * The old text fully exits before the new one enters (`AnimatePresence mode="wait"`), and the
 * container animates its width/height to the new text on a spring so surrounding content reflows
 * smoothly. All units are drawn by one custom draw pass (no composable per character), animated in
 * the draw phase only. The only per-frame layout work is the width animation.
 *
 * @param texts phrases to rotate (any Unicode text; an empty list draws nothing).
 * @param style text style; the colour defaults to black. With [highlight] the colour is chosen from
 *   [highlightColor] unless [highlightTextColor] is set.
 * @param effect see [RotatingTextEffect]; the presets match the reference's table.
 * @param direction [RotatingTextDirection.Up] (default) or [RotatingTextDirection.Down].
 * @param splitBy what is animated as one unit; characters, words, lines or a custom separator.
 * @param rotationIntervalMillis time between two index changes (`rotationInterval = 2000`).
 * @param staggerMillis delay between consecutive units, in ms (`staggerDuration`, which is in seconds
 *   in the reference; default 0 = all units move together).
 * @param staggerFrom which unit starts first.
 * @param loop wrap around at the end; without it rotation stops at the last text.
 * @param auto rotate automatically every [rotationIntervalMillis]; when false use [state] to navigate.
 * @param pauseOnHover pause the auto rotation while a pointer hovers (or a touch rests on) the text.
 * @param blur extra blur radius applied to the hidden poses (reference `blur`); 0 = none, except for
 *   [RotatingTextEffect.Blur] which then uses 10 dp.
 * @param highlight draw a rounded pill of [highlightColor] behind the text; it animates its width to
 *   the current text on a spring (damping 34, stiffness 320) and clips the units.
 * @param highlightColor pill colour (reference `#a855f7`).
 * @param highlightTextColor text colour on the pill; [Color.Unspecified] picks near-black or white by
 *   relative luminance like the reference.
 * @param transition replaces the preset enter and exit transition of [effect] (reference `transition`).
 * @param animateInitial play the enter animation of the first text on appearance
 *   (`animatePresenceInitial`, default false: it is simply there).
 * @param renderMode segment rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param state optional handle to call `next()`, `previous()`, `jumpTo()` and `reset()` and observe the index.
 * @param speed playback speed multiplier (also scales the rotation interval).
 * @param controller optional pause/resume/restart control; pausing freezes the rotation and the
 *   animation, `restart()` shows the first text again with its enter animation. `seekTo` is not supported.
 * @param onNext called with the new index every time the active text changes (`onNext`).
 */
@Composable
public fun RotatingText(
    texts: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    effect: RotatingTextEffect = RotatingTextEffect.Slide,
    direction: RotatingTextDirection = RotatingTextDirection.Up,
    splitBy: RotatingTextSplit = RotatingTextSplit.Characters,
    rotationIntervalMillis: Int = 2000,
    staggerMillis: Int = 0,
    staggerFrom: RotatingTextStaggerFrom = RotatingTextStaggerFrom.First,
    loop: Boolean = true,
    auto: Boolean = true,
    pauseOnHover: Boolean = false,
    blur: Dp = 0.dp,
    highlight: Boolean = false,
    highlightColor: Color = Color(0xFFA855F7),
    highlightTextColor: Color = Color.Unspecified,
    transition: RotatingTextTransition? = null,
    animateInitial: Boolean = false,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    state: RotatingTextState? = null,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onNext: ((index: Int) -> Unit)? = null,
) {
    val localDensity = LocalDensity.current
    val machine = remember { RotatingTextMachine() }
    val spec = remember(effect, direction, blur, transition) {
        buildEffectSpec(effect, direction, blur.value, transition)
    }
    val ranges = remember(texts, splitBy) { texts.map { rotatingUnitRanges(it, splitBy) } }
    val plan = remember(ranges, spec, staggerFrom, staggerMillis, highlight) {
        RotatingTextPlan(
            unitCounts = IntArray(ranges.size) { ranges[it].size },
            spec = spec,
            staggerFrom = staggerFrom,
            staggerMillis = staggerMillis.coerceAtLeast(0).toFloat(),
            sizeSpring = if (highlight) RotatingTextTransition.Spring(34f, 320f) else RotatingTextTransition.Spring(25f, 300f),
        )
    }
    val applied = remember { arrayOfNulls<RotatingTextPlan>(1) }
    if (applied[0] !== plan) {
        machine.setPlan(plan, enter = animateInitial && applied[0] == null)
        applied[0] = plan
    }

    // Pointer hover/touch pauses the auto rotation.
    val tracker = rememberPointerTracker()
    val hovered = pauseOnHover && tracker.isActive
    val autoOn = auto && !hovered
    machine.intervalMillis = rotationIntervalMillis.coerceAtLeast(1).toFloat()
    machine.loop = loop
    machine.autoEnabled = autoOn

    var activeIndex by remember { mutableIntStateOf(machine.active) }
    val currentOnNext by rememberUpdatedState(onNext)
    val currentState by rememberUpdatedState(state)
    val drawTick = remember { mutableIntStateOf(0) }
    val layoutTick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    machine.onWake = { wake.intValue++ }
    machine.onActiveChanged = { i ->
        activeIndex = i
        currentState?.currentIndex = i
        currentOnNext?.invoke(i)
    }
    SideEffect {
        if (activeIndex != machine.active) activeIndex = machine.active
        state?.currentIndex = machine.active
        state?.textCount = texts.size
    }
    DisposableEffect(state, machine) {
        state?.machine = machine
        onDispose { state?.let { if (it.machine === machine) it.machine = null } }
    }
    LaunchedEffect(hovered) { machine.resetAutoClock() }

    val restarts = controller?.restartCount ?: 0
    LaunchedEffect(restarts) {
        if (restarts > 0) { machine.restart(); drawTick.intValue++; layoutTick.intValue++ }
    }

    val visibility = rememberVisibilityState()
    val playing = controller?.isPlaying ?: true
    val visible = visibility.isVisible
    val currentSpeed by rememberUpdatedState(speed)
    val wakeValue = wake.intValue
    LaunchedEffect(machine, plan, playing, visible, autoOn, wakeValue) {
        if (!playing || !visible) return@LaunchedEffect
        var last = -1L
        while (true) {
            machine.pollAuto()
            if (machine.isAnimating || machine.isSizeAnimating) {
                withFrameNanos { now ->
                    val sizeBefore = machine.isSizeAnimating
                    if (last >= 0L) {
                        val dt = (now - last) / 1_000_000f * currentSpeed * (controller?.speed ?: 1f)
                        machine.advance(dt)
                    }
                    last = now
                    drawTick.intValue++
                    if (sizeBefore || machine.isSizeAnimating) layoutTick.intValue++
                }
            } else {
                last = -1L
                val wait = machine.millisUntilAuto()
                if (wait.isInfinite()) awaitCancellation()
                val factor = max(0.01f, currentSpeed * (controller?.speed ?: 1f))
                val mark = TimeSource.Monotonic.markNow()
                try {
                    delay((wait / factor).toLong() + 1L)
                } finally {
                    machine.addAutoTime(mark.elapsedNow().inWholeMilliseconds * factor)
                }
            }
        }
    }

    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { SplitTextRenderer(graphicsContext) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    val splitSpec = remember(spec, localDensity.density) {
        SplitTextSpec(FloatArray(CHANNELS), FloatArray(CHANNELS), 0.5f, 0.5f, spec.perspectiveDp * localDensity.density)
    }
    val values = remember { FloatArray(CHANNELS) }
    val pose = remember { FloatArray(RCH) }
    val pill = remember { Path() }

    val measurer = rememberTextMeasurer(cacheSize = 4)
    val cache = remember { RotatingLayouts() }
    val textColor = if (!highlight) style.color else if (highlightTextColor.isSpecified) highlightTextColor else highlightTextColorFor(highlightColor)
    val resolvedStyle = remember(style, highlight, textColor) {
        (if (highlight) style.copy(color = textColor) else style).withDefaultColor()
    }
    val brush = if (highlight) null else style.brush
    val baseColor = resolvedStyle.color

    Layout(
        modifier = modifier
            .trackVisibility(visibility)
            .trackPointer(tracker, enabled = pauseOnHover)
            .semantics { this.text = AnnotatedString(texts.getOrElse(activeIndex) { "" }) }
            .drawBehind {
                drawTick.intValue
                val layouts = cache.layouts
                if (layouts.isEmpty() || machine.plan.textCount != layouts.size) return@drawBehind
                val l = layouts[machine.shown.coerceIn(0, layouts.size - 1)]
                val em = emPx(resolvedStyle, this)
                val w = machine.currentWidth()
                val h = machine.currentHeight()
                val padY = if (highlight) 0.1f * em else 0f
                val ox = if (highlight) (w - l.layout.size.width) / 2f else 0f
                val zShift = spec.originZEm * em
                val drawUnits: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                    translate(ox, padY) {
                        for (s in l.segments) {
                            machine.pose(s.index, pose)
                            val rot = pose[RC_ROT_X]
                            values[CH_OPACITY] = pose[RC_OPACITY]
                            values[CH_Y] = pose[RC_Y] * s.bounds.height + zShift * sin(rot * PI.toFloat() / 180f)
                            values[CH_ROT_X] = rot
                            values[CH_SCALE] = pose[RC_SCALE]
                            values[CH_BLUR] = pose[RC_BLUR] * this.density
                            with(renderer) {
                                drawUnit(
                                    layout = l, segment = s, slot = s.index, v = values, spec = splitSpec, extraY = 0f,
                                    mask = false, color = baseColor, accentColor = Color.Unspecified, accent = 0f, brush = brush,
                                )
                            }
                        }
                    }
                }
                if (highlight) {
                    val radius = 0.16f * em
                    pill.reset()
                    pill.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)))
                    drawPath(pill, highlightColor)
                    clipPath(pill) { drawUnits() }
                } else {
                    drawUnits()
                }
            },
    ) { _, constraints ->
        val em = emPx(resolvedStyle, this)
        val padX = if (highlight) 0.28f * em else 0f
        val padY = if (highlight) 0.1f * em else 0f
        val maxInner = if (constraints.hasBoundedWidth) (constraints.maxWidth - 2 * padX).toInt().coerceAtLeast(0) else Constraints.Infinity
        val key = RotatingLayoutKey(
            texts, splitBy, resolvedStyle, maxInner, density, fontScale,
            layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl, renderMode, if (highlight) 1 else 0,
        )
        if (cache.key != key) {
            val inner = Constraints(maxWidth = maxInner)
            cache.layouts = texts.mapIndexed { i, t ->
                val result = measurer.measure(
                    text = AnnotatedString(t),
                    style = resolvedStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    constraints = inner,
                    layoutDirection = layoutDirection,
                    density = this,
                )
                SegmentedTextLayout.buildFromRanges(result, ranges[i], renderMode, measurer)
            }
            cache.key = key
        }
        val sizes = FloatArray(cache.layouts.size * 2)
        cache.layouts.forEachIndexed { i, l ->
            sizes[2 * i] = l.layout.size.width + 2f * padX + (if (highlight) 1f else 0f)
            sizes[2 * i + 1] = ceil(l.layout.size.height.toFloat()) + 2f * padY
        }
        if (machine.plan.textCount == sizes.size / 2) machine.setSizes(sizes)
        layoutTick.intValue
        val size = constraints.constrain(IntSize(machine.currentWidth().roundToInt(), machine.currentHeight().roundToInt()))
        layout(size.width, size.height) {}
    }
}

private fun emPx(style: TextStyle, density: androidx.compose.ui.unit.Density): Float =
    if (style.fontSize.isSp) with(density) { style.fontSize.toPx() } else 16f * density.density
