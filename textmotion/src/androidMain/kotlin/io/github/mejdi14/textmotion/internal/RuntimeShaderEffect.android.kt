package io.github.mejdi14.textmotion.internal

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect

internal actual class RuntimeShaderEffect actual constructor(source: String) {
    // Typed as Any so this class loads on API < 33 without touching RuntimeShader.
    private val impl: Any? = if (Build.VERSION.SDK_INT >= 33) Api33.create(source) else null

    actual val isSupported: Boolean get() = impl != null

    actual fun setFloat(name: String, value: Float) { if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.set(impl, name, floatArrayOf(value)) }
    actual fun setFloat(name: String, x: Float, y: Float) { if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.set(impl, name, floatArrayOf(x, y)) }
    actual fun setFloat(name: String, x: Float, y: Float, z: Float) { if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.set(impl, name, floatArrayOf(x, y, z)) }
    actual fun setFloat(name: String, x: Float, y: Float, z: Float, w: Float) { if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.set(impl, name, floatArrayOf(x, y, z, w)) }
    actual fun setFloatArray(name: String, values: FloatArray) { if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.set(impl, name, values) }

    actual fun shader(): Shader? = if (Build.VERSION.SDK_INT >= 33 && impl != null) impl as Shader else null

    actual fun renderEffect(childName: String): RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && impl != null) Api33.renderEffect(impl, childName) else null

    @RequiresApi(33)
    private object Api33 {
        fun create(source: String): Any? = try {
            RuntimeShader(source)
        } catch (e: Exception) {
            println("TextMotion: runtime shader compilation failed: ${e.message}")
            null
        }

        fun set(impl: Any, name: String, values: FloatArray) {
            val s = impl as RuntimeShader
            when (values.size) {
                1 -> s.setFloatUniform(name, values[0])
                2 -> s.setFloatUniform(name, values[0], values[1])
                3 -> s.setFloatUniform(name, values[0], values[1], values[2])
                4 -> s.setFloatUniform(name, values[0], values[1], values[2], values[3])
                else -> s.setFloatUniform(name, values)
            }
        }

        fun renderEffect(impl: Any, childName: String): RenderEffect =
            android.graphics.RenderEffect.createRuntimeShaderEffect(impl as RuntimeShader, childName).asComposeRenderEffect()
    }
}

internal actual object PlatformCapabilities {
    actual val supportsRenderEffect: Boolean get() = Build.VERSION.SDK_INT >= 31
    actual val supportsRuntimeShader: Boolean get() = Build.VERSION.SDK_INT >= 33
}
