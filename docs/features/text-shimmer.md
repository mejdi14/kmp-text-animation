# TextShimmer

Reference: motion-primitives.com/docs/text-shimmer (spec: `docs/reference-specs/motion-primitives-magicui.md` §1).

## API
```kotlin
TextShimmer(
    text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextMotionStyle.current,
    durationMillis: Int = 2000, spread: Float = 2f,
    baseColor: Color = zinc-400 (light) / zinc-500 (dark), highlightColor: Color = black / white,
    easing: Easing = LinearEasing, speed: Float = 1f, controller: TextAnimationController? = null,
)
```
- `spread` = band half-width in dp **per character** (`text.length * spread`), as in the reference.
- Default colours follow `isSystemInDarkTheme()` (reference light/dark palettes).
- `style.color` is replaced by `baseColor`.

## Rendering
`AnimatedTextLayout` measures once. In the draw phase: `saveLayer` → draw text in `baseColor` →
draw a cached `Brush.horizontalGradient(transparent, highlight, transparent)` rect translated to
the band centre with `BlendMode.SrcAtop` (composites only on glyph pixels = `background-clip:text`)
→ restore. Band centre x = `-0.25W + 1.5W·p` (reference's 250%-wide background moving 100%→0%),
`p` restarts linearly every `durationMillis`. Math in `internal/textshimmer/ShimmerMath.kt`.
No per-frame allocation except the layer; brush/paint are remembered.

## Playback
Continuous: the clock (`rememberTimeline`) runs only while visible (`trackVisibility`) and the
controller is playing; `controller.seekTo(p)` scrubs the sweep. No shaders, so identical on all
platforms (no capability fallback needed).

## Deviations
- Added `easing` (default linear = reference), `speed`, `controller` (not in reference).
- The band spans the text layout width (the reference uses the element width, which for
  `inline` text is the same). Multi-line text sweeps all lines simultaneously.
- CSS px treated as dp.

## Unverified
Not visually tested (project rule). Compiles on all targets; math unit-tested (`ShimmerMathTest`).
