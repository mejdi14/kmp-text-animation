package io.github.mejdi14.textmotion.internal.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Skia implementation: the font file is parsed once into a base [SkTypeface]; each axis position
 * is a cheap `makeClone(variations)` registered under a unique alias.
 */
internal actual class PlatformVariableTypeface(private val base: SkTypeface) {
    actual val supportsVariations: Boolean = (base.variationAxes?.isNotEmpty() == true)

    actual fun family(settings: FontVariation.Settings, alias: String): FontFamily {
        val typeface = if (settings.settings.isEmpty()) base else {
            val variations = Array(settings.settings.size) { i ->
                val s = settings.settings[i]
                org.jetbrains.skia.FontVariation(s.axisName, s.toVariationValue(null))
            }
            try { base.makeClone(variations) } catch (e: Throwable) { base }
        }
        return FontFamily(Typeface(typeface, alias))
    }
}

@Composable
internal actual fun rememberPlatformVariableTypeface(bytes: ByteArray?, id: String): PlatformVariableTypeface? =
    remember(bytes, id) {
        if (bytes == null) null else try {
            FontMgr.default.makeFromData(Data.makeFromBytes(bytes))?.let { PlatformVariableTypeface(it) }
        } catch (e: Throwable) {
            println("TextMotion: could not parse variable font $id: ${e.message}")
            null
        }
    }
