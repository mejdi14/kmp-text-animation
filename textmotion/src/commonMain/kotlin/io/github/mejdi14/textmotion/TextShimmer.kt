package io.github.mejdi14.textmotion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.resolveProgress
import io.github.mejdi14.textmotion.internal.textshimmer.ShimmerMath
import io.github.mejdi14.textmotion.internal.trackVisibility

/**
 * A soft band of light that sweeps left-to-right across the text on an endless loop
 * (Motion Primitives "Text Shimmer").
 *
 * The text is drawn in [baseColor]; a horizontal band `[transparent, highlight, transparent]`
 * (half-width `text.length * spread` dp) is composited only onto the glyph pixels. The animation
 * is continuous: it pauses while the composable is not visible or the [controller] is paused.
 *
 * @param text real, dynamic text.
 * @param style text style (font, size, weight, spacing, alignment). Its colour is replaced by [baseColor].
 * @param durationMillis duration of one sweep (reference: 2 s).
 * @param spread band half-width in dp per character of [text] (reference: 2).
 * @param baseColor resting text colour. Default follows the system theme (zinc-400 / zinc-500).
 * @param highlightColor colour at the centre of the band (black in light, white in dark theme).
 * @param easing easing of the sweep; the reference is linear.
 * @param speed playback speed multiplier.
 * @param controller optional play/pause/restart/seek control.
 */
@Composable
public fun TextShimmer(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    durationMillis: Int = 2000,
    spread: Float = 2f,
    baseColor: Color = if (isSystemInDarkTheme()) Color(0xFF71717A) else Color(0xFFA1A1AA),
    highlightColor: Color = if (isSystemInDarkTheme()) Color.White else Color.Black,
    easing: Easing = LinearEasing,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
) {
    val visibility = rememberVisibilityState()
    val timeline = rememberTimeline(controller, active = visibility.isVisible, speed = speed, restartKey = text)
    val density = LocalDensity.current
    val spreadPx = with(density) { ShimmerMath.dynamicSpread(text.length, spread).dp.toPx() }.coerceAtLeast(0.5f)
    val brush = remember(highlightColor, spreadPx) {
        val clear = highlightColor.copy(alpha = 0f)
        Brush.horizontalGradient(
            0f to clear, 0.5f to highlightColor, 1f to clear,
            startX = -spreadPx, endX = spreadPx,
        )
    }
    val paint = remember { Paint() }
    val resolved = remember(style, baseColor) { style.copy(color = baseColor) }
    val annotated = remember(text) { AnnotatedString(text) }

    AnimatedTextLayout(
        text = annotated,
        style = resolved,
        modifier = modifier.trackVisibility(visibility),
    ) { l ->
        val w = l.layout.size.width.toFloat()
        val h = l.layout.size.height.toFloat()
        val raw = ShimmerMath.sweepProgress(timeline.elapsedMillis, durationMillis.toFloat())
        val p = controller.resolveProgress(raw)
        val x = ShimmerMath.bandCenter(easing.transform(p), w)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(-spreadPx, 0f, w + spreadPx, h), paint)
            drawText(l.layout)
            translate(x, 0f) {
                drawRect(brush, topLeft = androidx.compose.ui.geometry.Offset(-spreadPx, 0f),
                    size = androidx.compose.ui.geometry.Size(2 * spreadPx, h), blendMode = BlendMode.SrcAtop)
            }
            canvas.restore()
        }
    }
}
