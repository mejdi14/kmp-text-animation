package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.isActive
import io.github.mejdi14.textmotion.internal.minFraction
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.restartKey
import io.github.mejdi14.textmotion.internal.textroll.TextRollMath
import io.github.mejdi14.textmotion.internal.textroll.TextRollRenderer
import io.github.mejdi14.textmotion.internal.textroll.UnitIndexCache
import io.github.mejdi14.textmotion.internal.trackVisibility

/**
 * One pose of a [TextRoll] glyph layer (a Motion `initial` / `animate` target). Unset properties
 * are at rest. Values follow CSS conventions: positive [rotationX] tilts the top edge away from the
 * viewer, positive [y] moves down.
 *
 * @param rotationX rotation around the horizontal axis, degrees (Motion `rotateX`).
 * @param rotationY rotation around the vertical axis, degrees (Motion `rotateY`).
 * @param x horizontal translation (Motion `x`, CSS px ≈ dp).
 * @param y vertical translation (Motion `y`).
 * @param scale uniform scale (Motion `scale`).
 * @param opacity 0..1 (Motion `opacity`).
 * @param blur Gaussian blur radius (CSS `filter: blur()`).
 */
@Immutable
public data class TextRollKeyframe(
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val blur: Dp = 0.dp,
)

/**
 * Animation of one [TextRoll] glyph layer: it holds [initial] until its delay elapses, then eases
 * to [animate] and stays there.
 *
 * @param origin transform origin inside the glyph box; `null` uses the reference's origin for the
 *   layer (enter `50% 25%`, exit `50% 100%`).
 */
@Immutable
public data class TextRollVariant(
    val initial: TextRollKeyframe,
    val animate: TextRollKeyframe,
    val origin: TransformOrigin? = null,
)

/**
 * The two stacked copies of every glyph (Motion Primitives `variants`): the [enter] layer is the
 * glyph that is visible at first and leaves; the [exit] layer is the copy that arrives and stays
 * (the reference's naming). The [exit] layer is drawn on top.
 */
@Immutable
public data class TextRollVariants(
    val enter: TextRollVariant = Roll.enter,
    val exit: TextRollVariant = Roll.exit,
) {
    public companion object {
        /** Reference default: enter `rotateX 0 → 90`, exit `rotateX 90 → 0`. */
        public val Roll: TextRollVariants = TextRollVariants(
            enter = TextRollVariant(TextRollKeyframe(rotationX = 0f), TextRollKeyframe(rotationX = 90f)),
            exit = TextRollVariant(TextRollKeyframe(rotationX = 90f), TextRollKeyframe(rotationX = 0f)),
        )

        /** The docs' "custom variants" example: the roll plus a 2 px blur on the edge-on pose. */
        public val BlurRoll: TextRollVariants = TextRollVariants(
            enter = TextRollVariant(
                TextRollKeyframe(rotationX = 0f, blur = 0.dp),
                TextRollKeyframe(rotationX = 90f, blur = 2.dp),
            ),
            exit = TextRollVariant(
                TextRollKeyframe(rotationX = 90f, blur = 2.dp),
                TextRollKeyframe(rotationX = 0f, blur = 0.dp),
            ),
        )

        /**
         * The docs' "custom transition & delay" example: letters drop out 40 dp while the copy
         * falls in from 40 dp above. Combine with `clipToBounds = true`, `durationMillis = 300`,
         * [TextRollDefaults.staggered] `(50, 50)` and [TextRollDefaults.SlideEasing].
         */
        public val Slide: TextRollVariants = TextRollVariants(
            enter = TextRollVariant(TextRollKeyframe(y = 0.dp), TextRollKeyframe(y = 40.dp)),
            exit = TextRollVariant(TextRollKeyframe(y = (-40).dp), TextRollKeyframe(y = 0.dp)),
        )
    }
}

/** Defaults of [TextRoll] (Motion Primitives `TextRoll`). */
public object TextRollDefaults {
    /** Reference `duration = 0.5 s`. */
    public const val DurationMillis: Int = 500

    /** Reference `getEnterDelay = (i) => i * 0.1`. */
    public val EnterDelay: (index: Int) -> Int = { it * 100 }

    /** Reference `getExitDelay = (i) => i * 0.1 + 0.2`. */
    public val ExitDelay: (index: Int) -> Int = { it * 100 + 200 }

    /** Reference `transition = { ease: 'easeIn' }`. */
    public val Easing: Easing = TextEasing.MotionEaseIn

    /** The docs' overshooting `ease: [0.175, 0.885, 0.32, 1.1]` used with [TextRollVariants.Slide]. */
    public val SlideEasing: Easing = TextEasing.cubicBezier(0.175f, 0.885f, 0.32f, 1.1f)

    /** Reference wrapper `perspective: 10000px`. */
    public val Perspective: Dp = 10000.dp

    /** Linear delay function `index * eachMillis + offsetMillis`. */
    public fun staggered(eachMillis: Int, offsetMillis: Int = 0): (index: Int) -> Int = { it * eachMillis + offsetMillis }
}

/**
 * Every letter rolls over like a flap: the visible glyph rotates away around its upper quarter
 * while an identical copy rolls in from its bottom edge, one letter after another (Motion
 * Primitives "Text Roll").
 *
 * Each unit is drawn twice in the draw phase from one shaped paragraph (no composable per
 * letter), each copy with its own CSS-like 3D transform (`rotateX` + perspective) and backface
 * culling. Before its delay a layer holds its `initial` pose; after it, the pose stays on
 * `animate`.
 *
 * @param text the text to animate; changing it restarts the roll.
 * @param style typography and colour; a `brush` in the style is honoured.
 * @param variants enter/exit poses (reference `variants`), see [TextRollVariants].
 * @param enterDelayMillis delay of unit `index`'s enter layer (reference `getEnterDelay`, seconds
 *   there, millis here). For characters, `index` counts every grapheme including spaces, like the
 *   reference's `split('')`.
 * @param exitDelayMillis delay of unit `index`'s exit layer (reference `getExitDelay`).
 * @param clipToBounds clip the glyphs to the text box (the docs' `overflow-clip`, useful with
 *   [TextRollVariants.Slide]).
 * @param perspective CSS `perspective` of each glyph box.
 * @param granularity unit that rolls: [TextGranularity.Character] (reference), words or lines.
 * @param renderMode segment rendering strategy (Auto keeps Arabic/Indic shaping intact).
 * @param durationMillis duration of each layer's animation (reference `duration = 0.5 s`).
 * @param delayMillis extra delay before the whole roll starts.
 * @param easing reference `transition.ease` (`easeIn`).
 * @param trigger when the roll starts (the reference plays on mount).
 * @param repeat number of rolls; each iteration starts again from the initial poses.
 * @param speed playback speed multiplier.
 * @param controller optional external playback control (pause, restart, seek).
 * @param onFinished called when the roll completes (reference `onAnimationComplete`).
 */
@Composable
public fun TextRoll(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    variants: TextRollVariants = TextRollVariants.Roll,
    enterDelayMillis: (index: Int) -> Int = TextRollDefaults.EnterDelay,
    exitDelayMillis: (index: Int) -> Int = TextRollDefaults.ExitDelay,
    clipToBounds: Boolean = false,
    perspective: Dp = TextRollDefaults.Perspective,
    granularity: TextGranularity = TextGranularity.Character,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    durationMillis: Int = TextRollDefaults.DurationMillis,
    delayMillis: Int = 0,
    easing: Easing = TextRollDefaults.Easing,
    trigger: AnimationTrigger = AnimationTrigger.OnVisible(),
    repeat: Repeat = Repeat.Once,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    var unitCount by remember(text, granularity) { mutableIntStateOf(TextRollMath.unitCount(text, granularity)) }
    val enterDelays = remember(unitCount, enterDelayMillis) { TextRollMath.delays(unitCount, enterDelayMillis) }
    val exitDelays = remember(unitCount, exitDelayMillis) { TextRollMath.delays(unitCount, exitDelayMillis) }
    val duration = durationMillis.coerceAtLeast(0).toFloat()
    val iteration = TextRollMath.iterationMillis(enterDelays, exitDelays, duration)
    val startDelay = delayMillis.coerceAtLeast(0).toFloat()
    val total = TextRollMath.totalMillis(startDelay, repeat, iteration)

    val visibility = rememberVisibilityState()
    val started = if (trigger == AnimationTrigger.Manual) (controller?.restartCount ?: 0) > 0 else trigger.isActive(visibility)
    val timeline = rememberTimeline(
        controller = controller,
        active = started && (!repeat.isInfinite || visibility.isVisible),
        speed = speed,
        totalDurationMillis = total,
        restartKey = listOf(text, granularity, durationMillis, delayMillis, iteration, variants, repeat, trigger.restartKey(visibility)),
        onFinished = onFinished,
    )

    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { TextRollRenderer(graphicsContext) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    val units = remember { UnitIndexCache() }
    val annotated = remember(text) { AnnotatedString(text) }
    val color = if (style.color.isSpecified) style.color else Color.Black
    val brush = style.brush
    val staticStart = remember(variants) { TextRollMath.restsAtStart(variants) }
    val staticEnd = remember(variants) { TextRollMath.restsAtEnd(variants) }
    val firstDelay = remember(enterDelays, exitDelays) { TextRollMath.firstDelay(enterDelays, exitDelays) }

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = modifier.trackVisibility(visibility, trigger.minFraction()),
        granularity = granularity,
        renderMode = renderMode,
        onLayoutChanged = { if (granularity == TextGranularity.Line) unitCount = it.segments.size },
    ) { l ->
        val seek = controller?.seekProgress
        val t = if (seek != null) seek * iteration
        else TextRollMath.localTime(timeline.elapsedMillis, startDelay, repeat, iteration)
        withTransform({ if (clipToBounds) clipRect(0f, 0f, l.layout.size.width.toFloat(), l.layout.size.height.toFloat()) }) {
            if ((staticEnd && t >= iteration) || (staticStart && t <= firstDelay)) {
                drawText(l.layout)
            } else {
                val unitOf = units.get(l)
                val perspectivePx = perspective.toPx()
                for (s in l.segments) {
                    val u = unitOf[s.index]
                    val enterDelay = if (u < enterDelays.size) enterDelays[u] else 0f
                    val exitDelay = if (u < exitDelays.size) exitDelays[u] else 0f
                    with(renderer) {
                        drawUnit(
                            layout = l, segment = s, variants = variants,
                            enterProgress = easing.transform(TextRollMath.layerProgress(t, enterDelay, duration)),
                            exitProgress = easing.transform(TextRollMath.layerProgress(t, exitDelay, duration)),
                            perspectivePx = perspectivePx, color = color, brush = brush,
                        )
                    }
                }
            }
        }
    }
}
