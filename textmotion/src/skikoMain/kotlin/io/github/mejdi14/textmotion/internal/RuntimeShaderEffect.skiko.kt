package io.github.mejdi14.textmotion.internal

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual class RuntimeShaderEffect actual constructor(source: String) {
    private val builder: RuntimeShaderBuilder? = try {
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
    } catch (e: Throwable) {
        println("TextMotion: runtime shader compilation failed: ${e.message}")
        null
    }

    actual val isSupported: Boolean get() = builder != null

    actual fun setFloat(name: String, value: Float) { builder?.uniform(name, value) }
    actual fun setFloat(name: String, x: Float, y: Float) { builder?.uniform(name, x, y) }
    actual fun setFloat(name: String, x: Float, y: Float, z: Float) { builder?.uniform(name, x, y, z) }
    actual fun setFloat(name: String, x: Float, y: Float, z: Float, w: Float) { builder?.uniform(name, x, y, z, w) }
    actual fun setFloatArray(name: String, values: FloatArray) { builder?.uniform(name, values) }

    actual fun shader(): Shader? = builder?.makeShader()?.asComposeShader()

    actual fun renderEffect(childName: String): RenderEffect? =
        builder?.let { ImageFilter.makeRuntimeShader(it, childName, null).asComposeRenderEffect() }
}

internal actual object PlatformCapabilities {
    actual val supportsRenderEffect: Boolean get() = true
    actual val supportsRuntimeShader: Boolean get() = true
}
