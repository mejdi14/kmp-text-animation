package io.github.mejdi14.textmotion.internal

import kotlin.math.floor

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun inverseLerp(a: Float, b: Float, v: Float): Float =
    if (a == b) (if (v >= b) 1f else 0f) else ((v - a) / (b - a))

internal fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp01(inverseLerp(edge0, edge1, x))
    return t * t * (3f - 2f * t)
}

/** Progress of one staggered segment: 0 before its [delayMillis], 1 after [durationMillis]. */
internal fun segmentProgress(elapsedMillis: Float, delayMillis: Float, durationMillis: Float): Float =
    if (durationMillis <= 0f) (if (elapsedMillis >= delayMillis) 1f else 0f)
    else clamp01((elapsedMillis - delayMillis) / durationMillis)

/** Positive modulo. */
internal fun Float.mod1(): Float = this - floor(this)

/** Cheap deterministic hash → [0, 1). Stable across platforms (no platform Random). */
internal fun hash01(x: Int, y: Int = 0, seed: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / 2147483648f
}

/** Smooth 1D value noise in [0, 1). */
internal fun valueNoise1(x: Float, seed: Int = 0): Float {
    val i = floor(x).toInt()
    val f = x - i
    val u = f * f * (3f - 2f * f)
    return lerp(hash01(i, 0, seed), hash01(i + 1, 0, seed), u)
}

/** Smooth 2D value noise in [0, 1). */
internal fun valueNoise2(x: Float, y: Float, seed: Int = 0): Float {
    val ix = floor(x).toInt(); val iy = floor(y).toInt()
    val fx = x - ix; val fy = y - iy
    val ux = fx * fx * (3f - 2f * fx); val uy = fy * fy * (3f - 2f * fy)
    val a = hash01(ix, iy, seed); val b = hash01(ix + 1, iy, seed)
    val c = hash01(ix, iy + 1, seed); val d = hash01(ix + 1, iy + 1, seed)
    return lerp(lerp(a, b, ux), lerp(c, d, ux), uy)
}
