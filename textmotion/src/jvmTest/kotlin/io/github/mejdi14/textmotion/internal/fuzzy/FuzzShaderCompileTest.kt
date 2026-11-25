package io.github.mejdi14.textmotion.internal.fuzzy

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Compiles the FuzzyText SkSL with the real Skia compiler (no app launched, nothing rendered). */
class FuzzShaderCompileTest {
    @Test fun shaderCompilesWithSkia() {
        val effect = RuntimeEffect.makeForShader(FUZZ_SHADER)
        val builder = RuntimeShaderBuilder(effect)
        builder.uniform("origin", 0f, 0f)
        builder.uniform("fuzz", 10f, 1f, 3f, 2f)
        builder.uniform("chroma", 2f, 1f, 0f, 1f)
        assertNotNull(builder.makeShader())
    }
}
