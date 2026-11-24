package io.github.mejdi14.textmotion

import androidx.compose.runtime.Immutable

/**
 * How pointer-reactive effects (TextPressure, VariableProximity, TrueFocus…) obtain the
 * position they react to. Mouse hover is used on Desktop/Web, touch-and-drag on Android/iOS.
 */
@Immutable
public sealed interface PointerInteraction {
    /** React only to a real mouse/touch pointer. Without one, the effect rests. */
    public data object Pointer : PointerInteraction

    /** Ignore input; a virtual pointer wanders over the text, completing a loop every [periodMillis]. */
    public data class Auto(val periodMillis: Int = 6000) : PointerInteraction

    /**
     * Follow the real pointer while it is present; after [idleMillis] without input (or on
     * touch devices after the finger lifts) a virtual pointer takes over. Default for touch-first UIs.
     */
    public data class PointerWithAutoFallback(val idleMillis: Int = 1500, val periodMillis: Int = 6000) : PointerInteraction

    /** No interaction at all. */
    public data object None : PointerInteraction
}
