# GradientText

Reference: reactbits.dev/text-animations/gradient-text (spec: `docs/reference-specs/reactbits.md` §9).

## API
```kotlin
GradientText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextMotionStyle.current,
    colors: List<Color> = GradientTextDefaults.Colors,        // #5227FF #FF9FFC #B497CF
    cycleDurationMillis: Int = 8_000,                          // reference animationSpeed = 8 s (min 0.2 s)
    variant: GradientTextVariant = Linear,                     // Linear | Flow | Conic
    angle: Float = 90f, direction: GradientTextDirection? = null, // Horizontal 90 / Vertical 180 / Diagonal 135
    gradientScale: Float = 3f,                                 // reference `scale`, clamped 1..6
    yoyo: Boolean = true, glow: Float = 0f,
    showBorder: Boolean = false, borderWidth: Dp = 1.5.dp,
    pauseOnHover: Boolean = false, followPointer: Boolean = false,
    speed: Float = 1f, controller: TextAnimationController? = null,
)
```
`style.color` is ignored (glyphs are painted with the gradient). One colour is duplicated, empty
list falls back to the defaults. `controller.seekTo(p)` scrubs one period (full ping-pong if `yoyo`).

## Rendering
`AnimatedTextLayout` measures once; everything per frame happens in the draw phase. Glyphs are drawn
black into a `saveLayer`, the gradient is composited with `BlendMode.SrcAtop` (same mask pattern as
AuroraText). Brushes are cached and only transformed per frame.
- **Linear**: cached `Brush.linearGradient(colors + colors[0], TileMode.Repeated)` whose period is
  `100*scale %` of the CSS gradient line; the canvas is translated along the line by
  `-offset` (cosine ping-pong over `period-100` with `yoyo`, else seamless `cycle%1 * period`).
- **Flow**: clamped base linear gradient + one unit-radius radial brush per colour, scaled into an
  ellipse (`22+12s %` x `60+30s %`) and moved on the reference sine paths.
- **Conic**: cached `sweepGradient` with the palette repeated over 360 degrees (period `60*3/scale`);
  the canvas is rotated to `sway-30deg` (CSS 12 o'clock origin) around a centre at `(50%, 260%)`.
- **followPointer**: spring (stiffness 160, critically damped) smoothed pointer + `pull` easing
  (`1-exp(-6dt)`) in `GradientDynamics`; `pauseOnHover` freezes the accumulated time.
- **showBorder**: padding 0.75em x 0.35em, pill ring (`borderWidth`) drawn as a stroked rounded rect
  masked with the same gradient over the full pill box.
- **glow**: a copy of the gradient text recorded into a `GraphicsLayer` with `BlurEffect(0.3em)`,
  saturation 1.4 colour filter and alpha = `glow`, drawn behind the text.
Pauses when invisible (`trackVisibility`) or controller-paused; pointer events are not consumed.

## Platform differences
- Glow blur needs `RenderEffect` (Android 12+, all Skia targets). Android < 12 uses a 9/17-pass
  offset approximation (alpha-compensated, saturation via layer paint colour filter).
- No shaders needed; behaviour is identical on every target.

## Deviations from the reference
- `animationSpeed` (seconds) is `cycleDurationMillis` (Int millis) + the shared `speed` multiplier.
- Reduced-motion handling is not implemented (no cross-platform signal).
- The reference loop keeps running to settle `pull` while paused; here the spring/pull freeze with
  the clock when the controller is paused or the element is invisible.
- Text is drawn as one layout (not per element), so `variant`/`glow` apply to the whole string.
- Pill radius is `min(w, h)/2` (CSS 999px equivalent).

## Unverified (no visual testing, per project rules)
Colour/offset agreement with the web version, flow blob appearance, conic ray orientation (Compose
sweep starts at 3 o'clock; compensated by -90 degrees), ring stroke alignment, glow strength and
Android < 12 fallback look, spring feel. Only the math/state logic is unit tested
(`GradientTextMathTest`).
