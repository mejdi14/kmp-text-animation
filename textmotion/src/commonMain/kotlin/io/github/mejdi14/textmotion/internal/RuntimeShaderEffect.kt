package io.github.mejdi14.textmotion.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader

/**
 * Cross-platform runtime shader (AGSL on Android 13+, SkSL on iOS/Desktop/Web via Skia).
 *
 * Write shaders in the common subset of both languages:
 * `uniform float2 resolution; uniform shader content; half4 main(float2 p) { return content.eval(p); }`.
 * Compile once (remember the instance) and only update uniforms per frame.
 *
 * When [isSupported] is false (Android < 13 or compile failure) callers MUST fall back to a
 * non-shader implementation that reproduces the effect as closely as possible.
 */
internal expect class RuntimeShaderEffect(source: String) {
    val isSupported: Boolean
    fun setFloat(name: String, value: Float)
    fun setFloat(name: String, x: Float, y: Float)
    fun setFloat(name: String, x: Float, y: Float, z: Float)
    fun setFloat(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setFloatArray(name: String, values: FloatArray)

    /** Shader to use in a `ShaderBrush` (e.g. for filling text). Null when unsupported. */
    fun shader(): Shader?

    /**
     * Post-processing effect for `graphicsLayer { renderEffect = … }`; the layer content is bound
     * to the `uniform shader [childName]`. Null when unsupported.
     */
    fun renderEffect(childName: String): RenderEffect?
}

internal fun RuntimeShaderEffect.setColor(name: String, color: Color) =
    setFloat(name, color.red, color.green, color.blue, color.alpha)

@Composable
internal fun rememberRuntimeShader(source: String): RuntimeShaderEffect = remember(source) { RuntimeShaderEffect(source) }

/** Rendering capabilities of the current platform. */
internal expect object PlatformCapabilities {
    /** `graphicsLayer { renderEffect }` (blur etc.) is honoured. False on Android < 12. */
    val supportsRenderEffect: Boolean

    /** Runtime shaders compile and run. False on Android < 13. */
    val supportsRuntimeShader: Boolean
}
