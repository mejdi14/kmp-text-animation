package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.auroratext.AuroraBrushCache
import io.github.mejdi14.textmotion.internal.auroratext.AuroraMath
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.resolveProgress
import io.github.mejdi14.textmotion.internal.trackVisibility

/** Default values of [AuroraText] (Magic UI reference defaults). */
public object AuroraTextDefaults {
    /** Reference palette: `#FF0080`, `#7928CA`, `#0070F3`, `#38bdf8`. */
    public val Colors: List<Color> = listOf(
        Color(0xFFFF0080),
        Color(0xFF7928CA),
        Color(0xFF0070F3),
        Color(0xFF38BDF8),
    )

    /** Length of one keyframe pass at `speed = 1` (reference: `10 / speed` seconds). */
    public const val DurationMillis: Int = 10_000

    /** Angle of the CSS-style linear gradient (reference: `135deg`, towards the bottom-right). */
    public const val AngleDegrees: Float = 135f
}

/**
 * Text whose glyphs are filled with a slowly drifting multi-colour aurora gradient while the
 * word gently sways (Magic UI "Aurora Text").
 *
 * A `linear-gradient(135°, colors…, colors[0])` twice as wide as the text pans back and forth
 * under the glyphs (`background-clip: text`), while the whole text rotates (−5°…5°) and scales
 * (0.9…1.1) around its centre, following the reference `aurora` keyframes with `ease-in-out` per
 * keyframe interval, played `infinite alternate`. Like a CSS transform, the sway does not affect
 * layout; the ink may extend slightly past the composable bounds.
 *
 * The animation is continuous: it pauses while the composable is not visible or the
 * [controller] is paused. `controller.seekTo(p)` scrubs one forward keyframe pass.
 *
 * @param text real, dynamic text.
 * @param style text style (font, size, weight, spacing, alignment). Its colour is not used: the
 *   glyphs are painted with the gradient.
 * @param colors gradient colours; the first colour is repeated at the end for a seamless loop.
 *   A single colour gives a solid fill; an empty list falls back to [AuroraTextDefaults.Colors].
 * @param durationMillis length of one keyframe pass at `speed = 1` (reference: 10 s). A full
 *   forward + backward period lasts twice as long.
 * @param angleDegrees CSS-style gradient angle (0° = up, 90° = right; reference 135°).
 * @param motionIntensity scales the rotation and scale sway (1 = reference, 0 = colour flow only).
 * @param easing per-keyframe-interval easing (reference: CSS `ease-in-out`).
 * @param speed playback speed multiplier; the reference `speed` prop (duration = `10 / speed` s).
 * @param controller optional play/pause/restart/seek control.
 */
@Composable
public fun AuroraText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    colors: List<Color> = AuroraTextDefaults.Colors,
    durationMillis: Int = AuroraTextDefaults.DurationMillis,
    angleDegrees: Float = AuroraTextDefaults.AngleDegrees,
    motionIntensity: Float = 1f,
    easing: Easing = TextEasing.EaseInOut,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
) {
    val visibility = rememberVisibilityState()
    val timeline = rememberTimeline(controller, active = visibility.isVisible, speed = speed)
    val brushCache = remember { AuroraBrushCache() }
    val paint = remember { Paint() }
    // Glyphs act as an opaque alpha mask; the gradient is composited onto them with SrcAtop.
    val mask = remember(style) { style.copy(color = Color.Black) }
    val annotated = remember(text) { AnnotatedString(text) }
    val palette = colors.ifEmpty { AuroraTextDefaults.Colors }

    AnimatedTextLayout(
        text = annotated,
        style = mask,
        modifier = modifier.trackVisibility(visibility),
    ) { l ->
        val w = l.layout.size.width.toFloat()
        val h = l.layout.size.height.toFloat()
        if (w <= 0f || h <= 0f) return@AnimatedTextLayout
        val brush = brushCache.brush(w, h, palette, angleDegrees) ?: return@AnimatedTextLayout
        val clock = AuroraMath.directedProgress(timeline.elapsedMillis, durationMillis.toFloat())
        val frame = AuroraMath.frameAt(controller.resolveProgress(clock), easing, motionIntensity)
        val pivot = Offset(w / 2f, h / 2f)
        // Room for glyph ink overhanging the line box (italics, diacritics, emoji).
        val margin = (l.layout.getLineBottom(0) - l.layout.getLineTop(0)) * 0.5f
        val offsetX = AuroraMath.layerOffsetX(frame.backgroundX, w)

        rotate(frame.rotationDegrees, pivot) {
            scale(frame.scale, frame.scale, pivot) {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(-margin, -margin, w + margin, h + margin), paint)
                    drawText(l.layout)
                    translate(offsetX, 0f) {
                        drawRect(
                            brush = brush,
                            topLeft = Offset(-margin - offsetX, -margin),
                            size = Size(w + 2f * margin, h + 2f * margin),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                    canvas.restore()
                }
            }
        }
    }
}
