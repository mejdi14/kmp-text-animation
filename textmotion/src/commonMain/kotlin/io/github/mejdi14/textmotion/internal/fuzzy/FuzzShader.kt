package io.github.mejdi14.textmotion.internal.fuzzy

/**
 * Slice-displacement shader (common subset of AGSL and SkSL), applied as a render effect on a
 * graphics layer holding the crisp text. It reproduces the reference canvas algorithm per pixel:
 * every `step`-px band (rows, columns or both) is shifted by a whole-pixel random offset in
 * `[-amp/2, amp/2]` that changes with `seed` every fuzz frame; optional RGB channel split with
 * per-band wobble, combined additively (light ink, canvas `lighter`) or multiplicatively
 * (dark ink, canvas `multiply`).
 *
 * Uniforms:
 * - `origin`: top-left of the text block in layer px (band phase).
 * - `fuzz`: x = amp px, y = step px, z = seed (mod 1024), w = mode (0 horizontal, 1 vertical, 2 both).
 * - `chroma`: x = split px, y = wobble px, z = 1 light ink / 0 dark ink, w = 1 split enabled.
 *
 * Salts must match [FuzzMath] (rows 0, columns 211, wobble 401 / 613).
 */
internal const val FUZZ_SHADER: String = """
uniform shader content;
uniform float2 origin;
uniform float4 fuzz;
uniform float4 chroma;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float sliceOffset(float idx, float salt, float amp) {
    return floor((hash12(float2(idx, fuzz.z + salt)) - 0.5) * amp + 0.5);
}

float2 displace(float2 p, float wobbleSalt, float jitter) {
    float amp = fuzz.x;
    float st = fuzz.y;
    if (fuzz.w > 0.5 && fuzz.w < 1.5) {
        float j = floor((p.x - origin.x) / st);
        return float2(p.x, p.y - sliceOffset(j, 211.0, amp) - sliceOffset(j, wobbleSalt, jitter));
    }
    float2 q = p;
    if (fuzz.w > 1.5) {
        float jc = floor(p.x / st);
        q.y = q.y - sliceOffset(jc, 211.0, amp * 0.5);
    }
    float k = floor((q.y - origin.y) / st);
    return float2(q.x - sliceOffset(k, 0.0, amp) - sliceOffset(k, wobbleSalt, jitter), q.y);
}

float4 blendMultiply(float4 s, float4 d) {
    return s * (1.0 - d.a) + d * (1.0 - s.a) + s * d;
}

half4 main(float2 p) {
    if (chroma.w < 0.5) {
        return content.eval(displace(p, 0.0, 0.0));
    }
    float2 axis = float2(1.0, 0.0);
    if (fuzz.w > 0.5 && fuzz.w < 1.5) {
        axis = float2(0.0, 1.0);
    }
    float2 sh = axis * chroma.x;
    float4 c0 = float4(content.eval(displace(p, 401.0, chroma.y) + sh));
    float4 c1 = float4(content.eval(displace(p, 0.0, 0.0)));
    float4 c2 = float4(content.eval(displace(p, 613.0, chroma.y) - sh));
    if (chroma.z > 0.5) {
        return half4(c0.r, c1.g, c2.b, min(1.0, c0.a + c1.a + c2.a));
    }
    float4 d = float4(c0.r, c0.a, c0.a, c0.a);
    d = blendMultiply(float4(c1.a, c1.g, c1.a, c1.a), d);
    d = blendMultiply(float4(c2.a, c2.a, c2.b, c2.a), d);
    return half4(d);
}
"""
