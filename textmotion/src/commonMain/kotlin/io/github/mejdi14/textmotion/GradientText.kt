package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.gradienttext.GradientDynamics
import io.github.mejdi14.textmotion.internal.gradienttext.GradientSpec
import io.github.mejdi14.textmotion.internal.gradienttext.GradientTextMath
import io.github.mejdi14.textmotion.internal.gradienttext.GradientTextRenderer
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility

/** Gradient construction of [GradientText] (React Bits `variant`). */
public enum class GradientTextVariant {
    /** Repeating linear gradient that pans (or ping-pongs) along its axis. Reference default. */
    Linear,

    /** A base linear gradient with one drifting radial colour blob per palette colour. */
    Flow,

    /** A repeating conic gradient whose rays sweep across the text from a point below it. */
    Conic,
}

/** Named gradient axes of [GradientText] (React Bits `direction`); each maps to a CSS angle. */
public enum class GradientTextDirection(internal val angleDegrees: Float) {
    /** Left to right (90 degrees). */
    Horizontal(90f),

    /** Top to bottom (180 degrees). */
    Vertical(180f),

    /** Top-left to bottom-right (135 degrees). */
    Diagonal(135f),
}

/** Default values of [GradientText] (React Bits reference defaults). */
public object GradientTextDefaults {
    /** Reference palette: `#5227FF`, `#FF9FFC`, `#B497CF`. */
    public val Colors: List<Color> = GradientTextMath.DefaultColors

    /** Length of one gradient cycle at `speed = 1` (reference `animationSpeed = 8` seconds). */
    public const val CycleDurationMillis: Int = 8_000

    /** Reference `angle`. */
    public const val AngleDegrees: Float = 90f

    /** Reference gradient period multiplier (`scale = 3`, valid range 1..6). */
    public const val GradientScale: Float = 3f

    /** Reference ring width of the border (`borderWidth = 1.5`). */
    public val BorderWidth: Dp = 1.5.dp
}

/**
 * Text whose glyphs are filled with an animated multi-colour gradient (React Bits "Gradient
 * Text": `background-clip: text` over a gradient regenerated every frame).
 *
 * Three [variant]s are available: [GradientTextVariant.Linear] (a repeating gradient that
 * ping-pongs with a cosine ease when [yoyo] is set, or loops seamlessly), [GradientTextVariant.Flow]
 * (drifting radial blobs over a linear base) and [GradientTextVariant.Conic] (rays sweeping across
 * the text). With [showBorder] the text sits in a pill with a gradient ring of [borderWidth]; with
 * [glow] a blurred, saturated copy of the gradient text is drawn behind it. [pauseOnHover] freezes
 * the animation while the pointer is over the text and [followPointer] pulls the gradient towards
 * the pointer (hover on Desktop/Web, touch-drag on Android/iOS; events are not consumed).
 *
 * The gradient is animated in the draw phase only (no recomposition or relayout per frame). It
 * pauses while the composable is not visible or the [controller] is paused;
 * `controller.seekTo(p)` scrubs one full period (one ping-pong round trip when [yoyo]).
 *
 * @param text real, dynamic text.
 * @param style text style (font, size, weight, spacing, alignment). Its colour is not used: the
 *   glyphs are painted with the gradient.
 * @param colors gradient colours (reference default `#5227FF #FF9FFC #B497CF`). A single colour is
 *   duplicated; an empty list falls back to [GradientTextDefaults.Colors].
 * @param cycleDurationMillis length of one cycle at `speed = 1` (reference `animationSpeed`, in
 *   seconds, default 8, minimum 0.2).
 * @param variant gradient construction, see [GradientTextVariant].
 * @param angle CSS-style gradient angle in degrees (0 = up, 90 = right); ignored when [direction] is set.
 * @param direction named axis that overrides [angle].
 * @param gradientScale period multiplier of the gradient (reference `scale`, clamped to 1..6):
 *   larger values stretch the colour bands over the text in the linear variant and widen the blobs
 *   / narrow the conic rays in the others.
 * @param yoyo ping-pong the gradient back and forth instead of looping it.
 * @param glow opacity (0..1) of a blurred copy behind the text; 0 disables it.
 * @param showBorder draw the text inside a pill (padding 0.75em x 0.35em) with a gradient ring.
 * @param borderWidth ring width when [showBorder] is set.
 * @param pauseOnHover freeze the animation while the pointer is over the text.
 * @param followPointer pull the gradient towards the (spring-smoothed) pointer position.
 * @param speed playback speed multiplier on top of [cycleDurationMillis].
 * @param controller optional play/pause/restart/seek control.
 */
@Composable
public fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    colors: List<Color> = GradientTextDefaults.Colors,
    cycleDurationMillis: Int = GradientTextDefaults.CycleDurationMillis,
    variant: GradientTextVariant = GradientTextVariant.Linear,
    angle: Float = GradientTextDefaults.AngleDegrees,
    direction: GradientTextDirection? = null,
    gradientScale: Float = GradientTextDefaults.GradientScale,
    yoyo: Boolean = true,
    glow: Float = 0f,
    showBorder: Boolean = false,
    borderWidth: Dp = GradientTextDefaults.BorderWidth,
    pauseOnHover: Boolean = false,
    followPointer: Boolean = false,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
) {
    val visibility = rememberVisibilityState()
    val timeline = rememberTimeline(controller, active = visibility.isVisible, speed = speed)
    val tracker = rememberPointerTracker()
    val dynamics = remember { GradientDynamics() }
    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { GradientTextRenderer(graphicsContext) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }

    val density = LocalDensity.current
    val emPx = with(density) { (if (style.fontSize.isSp) style.fontSize else 16.sp).toPx() }
    val padXPx = if (showBorder) kotlin.math.round(emPx * BORDER_PAD_X_EM) else 0f
    val padYPx = if (showBorder) kotlin.math.round(emPx * BORDER_PAD_Y_EM) else 0f
    val borderPx = with(density) { borderWidth.toPx() }

    // Glyphs act as an opaque alpha mask; the gradient is composited onto them with SrcAtop.
    val mask = remember(style) { style.copy(color = Color.Black) }
    val annotated = remember(text) { AnnotatedString(text) }
    val spec = remember(colors, variant, angle, direction, gradientScale, yoyo) {
        GradientSpec(
            colors = GradientTextMath.normalizeColors(colors),
            variant = variant,
            angleDegrees = GradientTextMath.resolveAngle(angle, direction?.angleDegrees),
            scale = GradientTextMath.clampScale(gradientScale),
            yoyo = yoyo,
        )
    }
    val cycleSeconds = GradientTextMath.cycleSeconds(cycleDurationMillis)
    val needsPointer = pauseOnHover || followPointer

    AnimatedTextLayout(
        text = annotated,
        style = mask,
        modifier = modifier
            .trackVisibility(visibility)
            .trackPointer(tracker, enabled = needsPointer)
            .pad(padXPx, padYPx),
    ) { l ->
        val textW = l.layout.size.width.toFloat()
        val textH = l.layout.size.height.toFloat()
        if (textW <= 0f || textH <= 0f) return@AnimatedTextLayout
        val box = Size(textW + 2f * padXPx, textH + 2f * padYPx)

        // Advance the frame-driven state (idempotent for an unchanged clock).
        val inside = needsPointer && tracker.isActive
        val pos = if (inside) tracker.position else Offset.Unspecified
        dynamics.advance(
            elapsedMillis = timeline.elapsedMillis,
            pointerInside = inside,
            pointerX = if (inside) pos.x / box.width else 0.5f,
            pointerY = if (inside) pos.y / box.height else 0.5f,
            followPointer = followPointer,
            freezeTime = pauseOnHover && inside,
        )
        val seek = controller?.seekProgress
        val frame = renderer.frame
        frame.cycle = if (seek != null) seek * (if (yoyo) 2f else 1f) else dynamics.time / cycleSeconds
        frame.pull = dynamics.pull
        frame.px = dynamics.px
        frame.py = dynamics.py

        // Box frame: origin at the pill's top-left; the text sits at (padX, padY) inside it.
        val margin = (l.layout.getLineBottom(0) - l.layout.getLineTop(0)) * 0.5f
        val textBounds = Rect(padXPx - margin, padYPx - margin, padXPx + textW + margin, padYPx + textH + margin)
        translate(-padXPx, -padYPx) {
            val drawMask: DrawScope.() -> Unit = {
                translate(padXPx, padYPx) { drawText(l.layout) }
            }
            with(renderer) {
                if (glow > 0f) drawGlow(spec, box, textBounds, glow, 0.3f * emPx, drawMask)
                if (showBorder) drawRing(spec, box, Rect(0f, 0f, box.width, box.height), borderPx)
                maskedFill(spec, box, textBounds, drawMask = drawMask)
            }
        }
    }
}

private const val BORDER_PAD_X_EM = 0.75f
private const val BORDER_PAD_Y_EM = 0.35f

/** Pixel padding that does not allocate when zero. */
private fun Modifier.pad(horizontalPx: Float, verticalPx: Float): Modifier =
    if (horizontalPx <= 0f && verticalPx <= 0f) this else layout { measurable, constraints ->
        val h = horizontalPx.toInt()
        val v = verticalPx.toInt()
        val placeable = measurable.measure(constraints.offset(-2 * h, -2 * v))
        layout(placeable.width + 2 * h, placeable.height + 2 * v) { placeable.place(h, v) }
    }
