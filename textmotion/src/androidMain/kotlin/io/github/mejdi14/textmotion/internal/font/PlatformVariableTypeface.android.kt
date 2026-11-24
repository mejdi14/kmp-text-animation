package io.github.mejdi14.textmotion.internal.font

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation: the font bytes are copied once to the app cache directory and every
 * axis position is a `Font(file, variationSettings)` (Compose applies the settings through
 * `Typeface.Builder.setFontVariationSettings` on API 26+). Below API 26 axes are ignored by the
 * platform, so [supportsVariations] is false and callers synthesize weight/width/slant.
 */
internal actual class PlatformVariableTypeface(private val file: File) {
    actual val supportsVariations: Boolean = Build.VERSION.SDK_INT >= 26

    actual fun family(settings: FontVariation.Settings, alias: String): FontFamily =
        FontFamily(Font(file, FontWeight.Normal, FontStyle.Normal, settings))
}

@Composable
internal actual fun rememberPlatformVariableTypeface(bytes: ByteArray?, id: String): PlatformVariableTypeface? {
    val context = LocalContext.current.applicationContext
    val typeface by produceState<PlatformVariableTypeface?>(null, bytes, id) {
        if (bytes == null) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "textmotion-fonts").apply { mkdirs() }
                val safe = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                val file = File(dir, "$safe-${bytes.size}-${bytes.contentHashCode().toUInt().toString(16)}.ttf")
                if (!file.exists() || file.length() != bytes.size.toLong()) {
                    val tmp = File(dir, "${file.name}.tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(file)) { file.writeBytes(bytes); tmp.delete() }
                }
                PlatformVariableTypeface(file)
            } catch (e: Throwable) {
                android.util.Log.w("TextMotion", "could not cache variable font $id", e)
                null
            }
        }
    }
    return typeface
}
