# FuzzyText

Reference: reactbits.dev/text-animations/fuzzy-text (spec: `docs/reference-specs/reactbits.md` §7;
behaviour re-checked against the current `FuzzyText.jsx`).

## API
```kotlin
FuzzyText(
    text, modifier, style = LocalTextMotionStyle.current,     // no weight in style → Black (900)
    baseIntensity = 0.18f, hoverIntensity = 0.5f, fuzzRange = 30.dp,
    direction = FuzzyDirection.Horizontal /* Vertical, Both */, sliceSize = 1.dp, fps = 60,
    enableHover = true, trigger = FuzzyTrigger.Hover /* Click, Always */,
    transitionDurationMillis = 0, clickEffect = false,
    glitchMode = false, glitchIntervalMillis = 2000, glitchDurationMillis = 200,
    gradient: List<Color>? = null, colorSplit = 0f, intro = false, introDurationMillis = 1400,
    pointerInteraction = PointerInteraction.Pointer, speed = 1f, controller = null,
)
```
All reference props are covered. `fontSize/fontWeight/fontFamily/color/letterSpacing` come from `style`.

## Rendering
- `AnimatedTextLayout` measures once; the composable reserves the reference padding around the text
  (`fuzzRange + 20 (+ splitPad)` + 5 inset horizontally, `fuzzRange + 10` vertically for
  Vertical/Both) via `Modifier.padding`, so shifted slices stay inside the bounds.
- `FuzzEngine` (frame loop, `withFrameNanos`): fps throttle (`now - last < 1000/fps - 1` skips),
  hover/always/click target, transition smoothing, 150 ms click flash, glitch schedule
  (wait interval → active duration → repeat), click burst (70 ms quadratic attack, τ = 240 ms decay,
  peak `min(2, 0.6 + 1.4·hover)`), intro (`(1-p)^2.5` towards 2, opacity eases in over 30 %),
  slice coarsening `1 + 3·energy²`. Each drawn frame bumps a seed and one int state → draw only.
- **Shader path** (Android 13+ AGSL, iOS/Desktop/Web SkSL): text recorded once into a
  `GraphicsLayer`; `RuntimeShaderEffect` render effect shifts each `step`-px band by
  `round((rand-0.5)·amp)` (hash of band index + seed), Both = rows then columns at amp/2. Colour split
  samples 3 times (channel shift ± `split`, per-band wobble) and combines additively (light ink) or
  with a multiply chain (dark ink), like canvas `lighter`/`multiply`.
- **Canvas fallback** (Android < 13 or shader compile failure): the reference algorithm itself —
  text rasterised once into an `ImageBitmap`, re-blitted per slice with `drawImage(src, dst)`;
  Both uses a reusable scratch bitmap; colour split uses channel `ColorMatrix` filters + `Plus`
  in a layer (dark inks: inverted channels, layer inverted back = multiply result).
- Pauses when not visible (`trackVisibility`) or when the controller is paused (last frame stays).
  Hover hit-box = text line bounds (not the padding), like the reference.

## Deviations / limitations
- `transitionDuration` is in frames in the reference; here `transitionDurationMillis` is the time to
  cover 95 % of the change (`n` frames ≈ `n·1000/fps` ms), frame-rate independent.
- Default font size is the style's (reference: `clamp(2rem, 8vw, 8rem)`); weight 900 is applied
  only when the style has none.
- Click/burst fires on press (pointer down), not on `click` (release). Touch: hover while the finger
  is down, like the reference's touch un-hover on pointer-up.
- `prefers-reduced-motion` is not read (no common Compose API); pass `baseIntensity = 0f` instead.
- `controller.seekTo` only freezes the current frame (the effect is random, not time-scrubbable);
  `restart()` replays the intro.
- Random offsets come from a deterministic hash (shader and fallback use different hashes, both
  uniform); multi-line text is supported (the reference is single-line).
- Ink overhanging the line box by more than 25 % of the line height may be clipped.

## Verification
- Compiles on all targets; `:textmotion:jvmTest` covers FuzzMath/FuzzEngine and compiles the SkSL
  with the real Skia compiler (`FuzzShaderCompileTest`, jvmTest-only `compose.desktop.currentOs`).
- AGSL compile on a real Android 13+ device, the Android < 13 fallback, colour-split colours and the
  overall look are **not visually verified** (project rule).
