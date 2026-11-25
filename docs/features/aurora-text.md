# AuroraText

Reference: magicui.design/docs/components/aurora-text (spec: `docs/reference-specs/motion-primitives-magicui.md` §8).

## API
```kotlin
AuroraText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextMotionStyle.current,
    colors: List<Color> = AuroraTextDefaults.Colors,          // #FF0080 #7928CA #0070F3 #38bdf8
    durationMillis: Int = 10_000, angleDegrees: Float = 135f,
    motionIntensity: Float = 1f, easing: Easing = TextEasing.EaseInOut,
    speed: Float = 1f, controller: TextAnimationController? = null,
)
```
- `speed` = reference `speed` (one keyframe pass lasts `durationMillis / speed`, i.e. `10 / speed` s).
- `style.color` is ignored: glyphs are painted with the gradient (`text-transparent` + `bg-clip-text`).
- Empty `colors` → defaults; one colour → solid fill.

## Rendering
`AnimatedTextLayout` measures once. Per frame (draw phase only):
1. Clock → directed progress of an `infinite alternate` animation (odd passes run backward).
2. Sample the `aurora` keyframes (0/25/50/75/100 %): background-position-x `0, .5, 1, .5, 0`,
   rotate `-5°, 5°, -3°, 3°, -5°`, scale `.9, 1.1, .95, 1.05, .9`; `ease-in-out` applied per
   keyframe interval (CSS semantics).
3. Rotate + scale the canvas around the text centre (CSS transform: no layout change).
4. `saveLayer` → draw text as an opaque mask → draw a cached `Brush.linearGradient(colors + colors[0])`
   with `BlendMode.SrcAtop`, translated by `-W·bgX` → restore.

The gradient reproduces CSS exactly: a `135deg` gradient over a layer `2W × H`
(`background-size: 200% auto`), gradient line through the layer centre with length
`|2W·sin a| + |H·cos a|` (corners get the first/last colour). Math in
`internal/auroratext/AuroraMath.kt`; brush cached in `AuroraBrushCache` (rebuilt only when size,
colours or angle change). No per-frame allocation besides the layer and one small frame value.

## Platforms
No runtime shader is needed: the reference is a plain CSS linear gradient, which `Brush` renders
identically on Android (all API levels), iOS, Desktop and Web. No capability fallback required.

## Playback
Continuous: `rememberTimeline` runs only while visible (`trackVisibility`) and the controller is
playing. `controller.seekTo(p)` renders forward pass progress `p`.

## Deviations
- Extra parameters (reference values as defaults): `durationMillis`, `angleDegrees`,
  `motionIntensity` (scales the rotate/scale sway; 0 = colour flow only), `easing`, `controller`.
- Size of the element = text layout size (CSS `inline-block` shrink-to-fit). If the composable is
  given a fixed/fill width, the gradient spans that width.
- Glyph ink overhanging the line box still receives the (clamped) gradient; CSS would leave ink
  outside the border box unpainted.
- The rotated/scaled ink can extend past the composable bounds (like a CSS transform); a parent
  that clips its children will clip it.
- Colour emoji are tinted by the gradient (SrcAtop), as with `background-clip: text`.

## Unverified
Not visually tested (project rule). Compiles on all targets; keyframes, alternate timing and
gradient geometry unit-tested (`AuroraMathTest`).
