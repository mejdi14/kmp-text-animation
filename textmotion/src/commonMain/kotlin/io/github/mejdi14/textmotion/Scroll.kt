package io.github.mejdi14.textmotion

import androidx.compose.runtime.Immutable

/**
 * Maps the position of an element inside its viewport to a 0..1 progress, like GSAP
 * ScrollTrigger `start`/`end`. Anchors are fractions: 0 = top edge, 1 = bottom edge.
 *
 * Progress is 0 when the element's [elementStart] anchor meets the viewport's [viewportStart]
 * anchor and 1 when [elementEnd] meets [viewportEnd]. The default ("top bottom" → "bottom top")
 * spans the whole time the element is on screen.
 */
@Immutable
public data class ScrollRange(
    val elementStart: Float = 0f,
    val viewportStart: Float = 1f,
    val elementEnd: Float = 1f,
    val viewportEnd: Float = 0f,
) {
    public companion object {
        /** Whole time the element crosses the viewport. */
        public val FullPass: ScrollRange = ScrollRange()

        /** GSAP `start: "top bottom", end: "bottom center"`. */
        public val EnterToCenter: ScrollRange = ScrollRange(0f, 1f, 1f, 0.5f)
    }
}
