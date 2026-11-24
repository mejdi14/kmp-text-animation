# TextPressure

Reference: reactbits.dev/text-animations/text-pressure (spec: `docs/reference-specs/reactbits.md` §2).

## API
```kotlin
TextPressure(
    text, modifier, style, font = VariableFont.RobotoFlex,
    width = true, weight = true, italic = true, alpha = false, flex = true, stroke = false, scale = false,
    radius = 0.5f, smoothing = 0.5f, idle = TextPressureIdle.None /* Wave, Pulse */, idleSpeed = 1f,
    clickRipple = true, textColor = style colour, pressureColor = Color.Unspecified,
    strokeColor = Color(0xFFFF0000), strokeWidth = 3.dp, minFontSize = 24.sp, uppercase = true,
    pointerInteraction = PointerInteraction.Pointer, controller = null,
)
```
All reference props and defaults are covered (`fontFamily`/`fontUrl` → `font: VariableFont`).

## Rendering
- One piece per grapheme. Font size `max(width / (n/2), minFontSize)`, title height = font size
  (CSS `line-height: 1`, half-leading baseline); `scale` stretches Y to the bounded height (pivot top).
- Per frame (draw phase): pressure `clamp01(1 - dist(mouse, centre) / max(width·radius, 1))`, idle
  wave/pulse blended by `engage` (320 ms), ripples screen-blended; → `wdth 5+200p`, `wght 100+900p`,
  italic `p`, opacity `p`, Oklab colour mix `1-(1-p)²`. Pieces laid out space-between (`flex`) or
  packed + centred, using the widths measured this frame and centres from the previous frame
  (as the reference reads `getBoundingClientRect`).
- Cursor: `mouse += (cursor - mouse)·(1 - e^{-dt/(1000·smoothing²)})`, dt ≤ 64 ms; starts at centre.
- Simulation (`PressureState`) runs in a `withFrameNanos` loop that sleeps when settled (no easing,
  no idle, no ripples) and wakes on pointer/press/layout changes; pauses when invisible or paused.
- Stroke: outline pass (`Stroke`) drawn behind each fill, following the variation.

## Variable-font infra (`internal/font/`, reusable by VariableProximity)
- Public `VariableFont(name, resource, weight/width/italic/slant/opticalSize: VariationAxisRange?)`,
  `VariableFont.RobotoFlex` bundled (`composeResources/font/roboto_flex.ttf`, OFL in
  `composeResources/files/licenses/RobotoFlex-OFL.txt`).
- `FontVariationKey` packs wght/wdth/ital/opsz into a `Long`; `VariationQuantizer` (steps 20 wght,
  4 wdth, 0.1 ital, 4 opsz) bounds the typeface set (~70 keys over the full pressure path).
- `VariableFontFamilies` (`rememberVariableFontFamilies(font)`): bytes via `getFontResourceBytes`,
  one `FontFamily` per key (cache cleared past 192). Semantic italic → `ital`, else `slnt·min`,
  else synthetic lean. `VariableGlyphCache[glyph, key]` measures each glyph once per key.
- Platform: skiko parses once (`FontMgr.makeFromData`) and `makeClone(variations)` per key under a
  unique alias; Android copies the file to `cacheDir/textmotion-fonts` once and uses
  `Font(file, variationSettings)` (honoured on API 26+).
- Fallback (`SyntheticVariation`): Android < 26 or before bytes load / missing axes → nearest
  `FontWeight` + synthetic bold, horizontal scale `wdth/100`, shear 0.21·italic.
- Glyph cache is prewarmed along the pressure path (one key per frame) after each relayout.

## Deviations
- Roboto Flex has no `ital` axis (the web original sends `ital`, which has no effect there);
  italic is rendered via `slnt` (−10°) so the toggle is visible.
- Axis values are quantized (steps above) instead of continuous floor() values.
- Pointer is tracked inside the component only (web tracks the whole window); touch-drag on
  mobile, hover on desktop/web; release keeps the last position (like the reference).
- Spaces keep their natural width (reference inline-block spans collapse them).
- `PointerInteraction.Auto`/`PointerWithAutoFallback` added for showcases/touch screens.
- No reduced-motion handling (no common Compose API).

## Unverified
Nothing visually verified (project rule). Unverified: exact visual match, skiko alias registration
cost on first sweep, Android file-font variation on all OEMs, synthetic fallback appearance
on Android < 8, web font loading latency (fallback until loaded).
