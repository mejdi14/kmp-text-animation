package io.github.mejdi14.textmotion

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

/**
 * Default [TextStyle] used by every TextMotion composable when no `style` is passed.
 *
 * TextMotion depends on Compose Foundation only (not Material). Apps using Material can bridge
 * their theme once:
 * ```
 * CompositionLocalProvider(LocalTextMotionStyle provides MaterialTheme.typography.headlineMedium) { … }
 * ```
 * When the resolved style has no colour, text is drawn in black.
 */
public val LocalTextMotionStyle: androidx.compose.runtime.ProvidableCompositionLocal<TextStyle> =
    staticCompositionLocalOf { TextStyle.Default }

/** Ready-made character pools for scramble/decrypt style effects. */
public object TextCharsets {
    public const val Uppercase: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    public const val Lowercase: String = "abcdefghijklmnopqrstuvwxyz"
    public const val Digits: String = "0123456789"
    public const val Alphanumeric: String = Uppercase + Lowercase + Digits
    public const val Symbols: String = "!@#$%^&*()_+-=[]{}|;:,.<>?/~"
    public const val AlphanumericSymbols: String = Alphanumeric + "!@#$%^&*()_+"
    public const val Binary: String = "01"
    public const val Hex: String = "0123456789ABCDEF"
    public const val Katakana: String = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン"
}
