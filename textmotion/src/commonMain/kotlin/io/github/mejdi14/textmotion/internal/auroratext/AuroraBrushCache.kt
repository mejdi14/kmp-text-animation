package io.github.mejdi14.textmotion.internal.auroratext

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * Caches the aurora gradient brush in gradient-layer coordinates (origin = left edge of the
 * 2W-wide layer) so frames only translate it; the brush is rebuilt only when the element size,
 * colours or angle change (never per frame).
 */
internal class AuroraBrushCache {
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var lastAngle = Float.NaN
    private var lastColors: List<Color>? = null
    private var brush: Brush? = null

    /** Brush for a [width]×[height] element, or null when [colors] is empty. */
    fun brush(width: Float, height: Float, colors: List<Color>, angleDegrees: Float): Brush? {
        if (width != lastWidth || height != lastHeight || angleDegrees != lastAngle || colors != lastColors) {
            lastWidth = width
            lastHeight = height
            lastAngle = angleDegrees
            lastColors = colors
            val stops = AuroraMath.loopedStops(colors)
            brush = if (stops.isEmpty()) null else {
                val (start, end) = AuroraMath.cssGradientLine(angleDegrees, width * AuroraMath.LayerWidthFactor, height)
                Brush.linearGradient(stops, start = start, end = end, tileMode = TileMode.Clamp)
            }
        }
        return brush
    }
}
