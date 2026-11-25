package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.SegmentBlurRenderer
import io.github.mejdi14.textmotion.internal.Transform3D
import io.github.mejdi14.textmotion.internal.clamp01
import io.github.mejdi14.textmotion.internal.isActive
import io.github.mejdi14.textmotion.internal.minFraction
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberViewportPosition
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.restartKey
import io.github.mejdi14.textmotion.internal.scrollreveal.FLIP_PERSPECTIVE_EM
import io.github.mejdi14.textmotion.internal.scrollreveal.OrderCache
import io.github.mejdi14.textmotion.internal.scrollreveal.PieceLook
import io.github.mejdi14.textmotion.internal.scrollreveal.RevealTiming
import io.github.mejdi14.textmotion.internal.scrollreveal.SmoothedProgress
import io.github.mejdi14.textmotion.internal.scrollreveal.computeLook
import io.github.mejdi14.textmotion.internal.scrollreveal.enterRotationProgress
import io.github.mejdi14.textmotion.internal.scrollreveal.pieceCount
import io.github.mejdi14.textmotion.internal.scrollreveal.pieceOrders
import io.github.mejdi14.textmotion.internal.splittext.emPx
import io.github.mejdi14.textmotion.internal.splittext.estimateUnitCount
import io.github.mejdi14.textmotion.internal.trackViewportPosition
import io.github.mejdi14.textmotion.internal.trackVisibility
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** How each piece of a [ScrollReveal] settles into place (reference `effect`). */
public enum class ScrollRevealEffect {
    /** Opacity (and blur) only. */
    Fade,

    /** Pieces rise out of their line box (masked), fading in. */
    Rise,

    /** Pieces flip up around their bottom edge (`rotateX 90° -> 0`, perspective 6 em) with a slight overshoot. */
    Flip,

    /** Pieces scale from 0.35 to 1 with an overshoot. */
    Scale,
}

/** What drives a [ScrollReveal] (reference `mode`). */
public enum class ScrollRevealMode {
    /** Progress is locked to the scroll position (GSAP `scrub`), reversible. Default. */
    Scrub,

    /** One-shot: scroll (visibility) only triggers a timed reveal (`duration`, `stagger` in seconds). */
    Enter,
}

/** Defaults of [ScrollReveal], matching the reference. */
public object ScrollRevealDefaults {
    /**
     * Words: from the element's top 20 % at the viewport bottom (`top bottom-=0.2*h`) to the
     * element's bottom at the viewport bottom (`wordAnimationEnd = 'bottom bottom'`).
     */
    public val WordRange: ScrollRange = ScrollRange(
        elementStart = 0.2f, viewportStart = 1f, elementEnd = 1f, viewportEnd = 1f,
    )

    /** Block rotation: `top bottom` to `bottom bottom` (`rotationEnd = 'bottom bottom'`). */
    public val RotationRange: ScrollRange = ScrollRange(
        elementStart = 0f, viewportStart = 1f, elementEnd = 1f, viewportEnd = 1f,
    )

    /** Reference `baseOpacity`. */
    public const val BaseOpacity: Float = 0.1f

    /** Reference `baseRotation` (degrees). */
    public const val BaseRotation: Float = 3f

    /** Reference `blurStrength` (px). */
    public val BlurStrength: Dp = 4.dp

    /** Reference `stagger`: gap between two pieces, in units of the 0.5 piece span. */
    public const val Stagger: Float = 0.05f

    /** Reference `duration` of the enter mode (0.8 s). */
    public const val DurationMillis: Int = 800
}

/**
 * Text that reveals piece by piece as it is scrolled into view, recreating React Bits
 * "Scroll Reveal" natively. Every word starts at [baseOpacity], blurred by [blurStrength] and the
 * whole block starts rotated by [baseRotation] degrees around its left edge; scrolling brings
 * each word to full opacity, sharp, and the block upright.
 *
 * Progress comes from the element's position in the window (works inside `Column(verticalScroll)`,
 * `LazyColumn`, nested scrolling) mapped by [scrollRange]/[rotationRange], or from an external
 * [scrollProgress]. It is read in the draw phase only, so scrolling never recomposes or relayouts.
 * Everything is drawn from one shaped paragraph; no composable per word.
 *
 * Timeline: with `total = span + (pieces − 1) × stagger` and `span = 0.5`, a piece with rank `i`
 * has local progress `clamp((p × total − i × stagger) / span)`.
 *
 * @param text the text to reveal (any Unicode, multi-line, RTL).
 * @param style typography and colour; a `brush` in the style is honoured.
 * @param effect how a piece settles, see [ScrollRevealEffect].
 * @param granularity piece size: [TextGranularity.Word] (`splitBy = 'words'`), [TextGranularity.Character]
 *   (`'chars'`) or [TextGranularity.Line] (`'lines'`: words are painted individually but a whole
 *   line shares one stagger step).
 * @param enableBlur whether pieces start blurred.
 * @param baseOpacity opacity of a piece before it is revealed (0..1).
 * @param baseRotation initial rotation of the whole block in degrees (clockwise), eased to 0.
 * @param blurStrength initial blur radius (CSS `blur()` px).
 * @param baseOffset initial downward offset of every piece.
 * @param stagger gap between two consecutive pieces, in units where one piece takes 0.5 (scrub) or
 *   in seconds (enter mode).
 * @param highlightColor when specified, pieces flash this colour while they reveal, then settle on
 *   the text colour (ignored for gradient-brush text).
 * @param mode [ScrollRevealMode.Scrub] (scroll-locked) or [ScrollRevealMode.Enter] (timed one-shot).
 * @param scrollRange scroll window of the piece reveal (scrub); default: [ScrollRevealDefaults.WordRange].
 * @param rotationRange scroll window of the block rotation (scrub); default [ScrollRevealDefaults.RotationRange].
 * @param scrollProgress external normalised progress (0..1) that overrides the automatic tracking of
 *   both the reveal and the rotation (scrub mode).
 * @param smoothingMillis scrub smoothing: the displayed progress catches up with the scroll
 *   position in about this long (reference `smoothing`, 0 = locked to scroll).
 * @param durationMillis enter mode: duration of one piece's reveal (reference `duration = 0.8 s`).
 * @param renderMode segment rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param trigger enter mode: when the timed reveal starts; `OnVisible(0.2f)` is the reference start
 *   (`top bottom-=0.2*h`).
 * @param speed enter mode: playback speed multiplier.
 * @param controller optional external control: [TextAnimationController.seekTo] freezes the reveal
 *   at a progress (both modes); pause/restart apply to the enter mode clock.
 * @param onFinished called whenever the reveal reaches its end (progress 1).
 */
@Composable
public fun ScrollReveal(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    effect: ScrollRevealEffect = ScrollRevealEffect.Fade,
    granularity: TextGranularity = TextGranularity.Word,
    enableBlur: Boolean = true,
    baseOpacity: Float = ScrollRevealDefaults.BaseOpacity,
    baseRotation: Float = ScrollRevealDefaults.BaseRotation,
    blurStrength: Dp = ScrollRevealDefaults.BlurStrength,
    baseOffset: Dp = 0.dp,
    stagger: Float = ScrollRevealDefaults.Stagger,
    highlightColor: Color = Color.Unspecified,
    mode: ScrollRevealMode = ScrollRevealMode.Scrub,
    scrollRange: ScrollRange = ScrollRevealDefaults.WordRange,
    rotationRange: ScrollRange = ScrollRevealDefaults.RotationRange,
    scrollProgress: (() -> Float)? = null,
    smoothingMillis: Int = 0,
    durationMillis: Int = ScrollRevealDefaults.DurationMillis,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    trigger: AnimationTrigger = AnimationTrigger.OnVisible(minFractionVisible = 0.2f),
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    val byLine = granularity == TextGranularity.Line
    val layoutGranularity = if (byLine) TextGranularity.Word else granularity
    val enter = mode == ScrollRevealMode.Enter
    val opacityFloor = baseOpacity.coerceIn(0f, 1f)

    val timing = remember(mode, durationMillis, stagger) {
        if (enter) RevealTiming.enter(durationMillis, stagger) else RevealTiming.scrub(stagger)
    }

    // Piece count: estimated from the text, then corrected from the real layout.
    var count by remember(text, granularity) {
        mutableIntStateOf(estimateUnitCount(text, if (byLine) TextGranularity.Word else granularity))
    }
    val totalSeconds = timing.total(count)

    // ---- scroll tracking (scrub) / clock (enter)
    val viewport = rememberViewportPosition()
    val visibility = rememberVisibilityState()
    val currentController by rememberUpdatedState(controller)
    val currentExternal by rememberUpdatedState(scrollProgress)
    val currentWordRange by rememberUpdatedState(scrollRange)
    val currentRotationRange by rememberUpdatedState(rotationRange)

    val wordScroll = remember(viewport) {
        SmoothedProgress {
            currentController?.seekProgress ?: currentExternal?.invoke()?.let { clamp01(it) }
                ?: viewport.progress(currentWordRange)
        }
    }
    val rotationScroll = remember(viewport) {
        SmoothedProgress {
            currentController?.seekProgress ?: currentExternal?.invoke()?.let { clamp01(it) }
                ?: viewport.progress(currentRotationRange)
        }
    }
    if (!enter) {
        LaunchedEffect(smoothingMillis, wordScroll, rotationScroll) {
            coroutineScope {
                launch { wordScroll.run(smoothingMillis) }
                launch { rotationScroll.run(smoothingMillis) }
            }
        }
    }

    val totalMillis = totalSeconds * 1000f
    val timeline = rememberTimeline(
        controller = controller,
        active = enter && trigger.isActive(visibility),
        speed = speed,
        totalDurationMillis = totalMillis,
        restartKey = listOf(text, mode, trigger.restartKey(visibility), totalMillis),
    )

    fun wordProgress(): Float {
        if (!enter) return wordScroll.current(smoothingMillis)
        val seek = currentController?.seekProgress
        return when {
            seek != null -> seek
            timeline.finished -> 1f
            totalMillis <= 0f -> 0f
            else -> clamp01(timeline.elapsedMillis / totalMillis)
        }
    }

    fun rotationProgress(): Float =
        if (!enter) rotationScroll.current(smoothingMillis)
        else enterRotationProgress(wordProgress() * totalSeconds, totalSeconds)

    val readWord by rememberUpdatedState(::wordProgress)
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        snapshotFlow { readWord() >= 0.9999f }.distinctUntilChanged().collect { done ->
            if (done) currentOnFinished?.invoke()
        }
    }

    // ---- rendering
    val graphicsContext = LocalGraphicsContext.current
    val blur = remember(graphicsContext) { SegmentBlurRenderer(graphicsContext) }
    DisposableEffect(blur) { onDispose { blur.release() } }
    val transform = remember { Transform3D() }
    val look = remember { PieceLook() }
    val orders = remember { OrderCache() }

    val annotated = remember(text) { AnnotatedString(text) }
    val baseColor = if (style.color.isSpecified) style.color else Color.Black
    val brush = style.brush
    val highlight = highlightColor.isSpecified && brush == null
    val pad = if (enableBlur) maxOf(blurStrength * 2, baseOffset) else baseOffset

    var tracked: Modifier = modifier
    if (!enter) tracked = tracked.trackViewportPosition(viewport)
    if (enter) tracked = tracked.trackVisibility(visibility, trigger.minFraction())

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = tracked,
        granularity = layoutGranularity,
        renderMode = renderMode,
        drawPadding = pad,
        onLayoutChanged = { l ->
            count = pieceCount(pieceOrders(IntArray(l.segments.size) { l.segments[it].lineIndex }, byLine))
        },
    ) { l ->
        val cache = orders.get(l, byLine)
        val p = wordProgress()
        val rotation = baseRotation * (1f - rotationProgress())
        val em = l.emPx()
        val blurPx = if (enableBlur) blurStrength.toPx() else 0f
        val offsetPx = baseOffset.toPx()
        val textHeight = l.layout.size.height.toFloat()
        val pivotX = if (layoutDirection == LayoutDirection.Rtl) l.layout.size.width.toFloat() else 0f

        withTransform({ if (rotation != 0f) rotate(rotation, Offset(pivotX, textHeight / 2f)) }) {
            for (s in l.segments) {
                val t = timing.local(p, cache.orders[s.index], cache.count)
                val lineTop = l.layout.getLineTop(s.lineIndex)
                val lineBottom = l.layout.getLineBottom(s.lineIndex)
                computeLook(effect, t, opacityFloor, offsetPx, blurPx, highlight, em, lineBottom - lineTop, look)
                if (look.opacity <= 0.002f) continue
                val box = s.bounds
                val moves = look.translateY != 0f || look.scale != 1f || look.rotationX != 0f
                withTransform({
                    if (look.masked) {
                        // Reference: clip-path inset(-1em -1em -0.15em -1em) on the word wrapper.
                        clipRect(box.left - em, lineTop - em, box.right + em, lineBottom + 0.15f * em)
                    }
                    if (moves) {
                        transform(
                            transform.set(
                                pivotX = box.center.x,
                                pivotY = if (look.rotationX != 0f) box.bottom else box.center.y,
                                translationY = look.translateY,
                                scaleX = look.scale,
                                scaleY = look.scale,
                                rotationX = look.rotationX,
                                perspective = if (look.rotationX != 0f) FLIP_PERSPECTIVE_EM * em else 0f,
                            ),
                        )
                    }
                }) {
                    with(blur) {
                        drawBlurred(
                            layout = l, segment = s, slot = s.index, sigmaPx = look.blurPx, alpha = look.opacity,
                            color = baseColor, tintColor = highlightColor, tint = look.glow, brush = brush,
                        )
                    }
                }
            }
        }
    }
}
