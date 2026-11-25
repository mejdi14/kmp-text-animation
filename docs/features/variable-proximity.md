# VariableProximity

Reference: reactbits.dev/text-animations/variable-proximity (React Bits `VariableProximity.jsx`).

## API
```kotlin
val area = rememberProximityArea()                 // optional, = containerRef
Box(Modifier.proximityArea(area)) {
    VariableProximity(
        text, modifier, style,
        font = VariableFont.RobotoFlex,
        fromFontVariationSettings = "'wght' 400, 'opsz' 9",
        toFontVariationSettings = "'wght' 1000, 'opsz' 40",
        radius = 50.dp, falloff = ProximityFalloff.Linear, smoothing = 0.2f,
        fromColor = Color.Unspecified, toColor = Color.Unspecified,
        scale = 1f, lift = 0f /* em */, idleWave = false,
        area = area, pointerInteraction = PointerInteraction.Pointer, controller = null,
    )
}
```
New public types: `ProximityFalloff` (Linear/Exponential/Gaussian), `ProximityArea`,
`rememberProximityArea()`, `Modifier.proximityArea(area)`.

## Technique
- Text → `ProximityText`: graphemes grouped in words; whitespace pieces never react (reference: spaces
  are separate spans). Words needing shaping context (Arabic, Indic…) stay one piece.
- Lines are frozen at the resting (`from`) settings with greedy word wrapping (`ProximityLines`);
  swelling letters push their neighbours along the line (alignment re-applied per frame), never reflow.
- Settings strings are parsed (`ProximityAxes`); every `from` axis lerps toward `to` (missing → stays,
  `to`-only axes ignored). Level 0..1 is bucketed (129 buckets) into a key table built with the shared
  `VariationQuantizer` (finer steps: wght 10, wdth 2, ital 0.05, opsz 2) → `VariableFontFamilies` /
  `VariableGlyphCache` (typeface per key, glyph measured once). Prewarm walks the table, one key/frame.
- Per frame (`ProximityState.step`, `withFrameNanos`): distance pointer → live letter centre
  (includes lift/scale, like the reference's live rects) → falloff → idle wave max → smoothing
  `1-exp(-min(dt,64)/(smoothing·400))`, snap 0.002 → relayout. Draw phase only reads arrays.
- Colour: Compose `lerp` (Oklab, = CSS `color-mix(in oklab)`). Transform: `translateY(-lift·level em)
  scale(1+(scale-1)·level)` about (50 %, 80 %) of the letter box.
- Idle wave: after 500 ms with no letter near, `waveMix` fades in (τ 600 ms); per letter
  `0.7·((1+cos φ)/2)^2.2`, φ from resting anchors, span `max(220dp, 0.85·textWidth)`, period 3.2 s.
- Loop sleeps when settled (wakes on pointer/layout change), stops when invisible or paused.

## Platform differences / limitations
- Android < 8.0: no variable fonts → shared synthetic fallback (nearest `FontWeight` + synthetic
  bold, width scale, skew); `opsz` has no fallback there.
- Only `wght`, `wdth`, `opsz`, `ital`, `slnt` are rendered (shared `FontVariationKey` has four
  fields). Other axes (e.g. Roboto Flex `GRAD`, `XTRA`) are parsed but ignored.
- Without `area`, the pointer is tracked over the text bounds only (the reference listens on the
  window): letters at the edge do not react to a pointer just outside the text. Use `ProximityArea`
  on a container to get the reference's behaviour (the reference demo uses `containerRef` too).
- Pointer leaves → letters relax to rest (touch: on finger lift). Events are never consumed.

## Deviations from reference
- Grapheme pieces (the reference splits UTF-16 code units, breaking emoji/combining marks).
- Without `opsz` in `from`, automatic optical sizing uses the font size in dp (CSS px analogue).
- `prefers-reduced-motion` is not available in Compose Multiplatform: `idleWave` is not auto-disabled.
- `onClick`/`className` → use Compose modifiers. RTL paragraphs are mirrored per line; full bidi
  reordering (numbers inside RTL text) is approximated by word order.
- Axis values are quantized (≤ 10 wght units) to bound typeface creation.

## Unverified
Compiled for all targets and unit-tested (parsing, falloff, smoothing, wave, keys, wrapping, RTL
order). Not visually verified (project rule): glyph alignment while swelling, smoothness of
typeface switches, Android < 8 fallback, ProximityArea coordinate conversion in scroll containers.
