# Platform support

Targets: **Android** (minSdk 24, compileSdk 37), **iOS** (arm64, simulator arm64), **Desktop**
(JVM, Skia via skiko), **Web** (Kotlin/JS and Kotlin/Wasm, both Skia/canvas via skiko).

## Rendering capabilities
| Capability | Android | iOS | Desktop | Web (JS/Wasm) |
|---|---|---|---|---|
| Canvas text drawing, transforms, clipping | ✅ | ✅ | ✅ | ✅ |
| Perspective (3D projective) transforms | ✅ | ✅ | ✅ | ✅ |
| `RenderEffect` (blur) on layers | API 31+ (fallback below) | ✅ | ✅ | ✅ |
| Runtime shaders | AGSL, API 33+ (fallback below) | SkSL | SkSL | SkSL |
| Grapheme segmentation | ICU BreakIterator | NSString composed sequences | java.text.BreakIterator (JDK 20+, else fallback) | Intl.Segmenter (else fallback) |
| Pointer hover | mouse/stylus only | — | ✅ | ✅ |
| Touch drag | ✅ | ✅ | — | ✅ |
| Variable fonts | to be documented by C02/C05 | | | |

Fallback rule: when a capability is missing, effects use an alternative technique that reproduces
the original as closely as possible (never silently disabled). Each effect documents its fallback
below.

## Compatibility matrix (filled in by coordinators)
Legend: ✅ full · ⚠️ works with documented differences · ⛔ not available. "Compiled" only — not visually verified.

| Effect | Android | iOS | Desktop | Web | Notes |
|---|---|---|---|---|---|
| BlurText | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | GraphicsLayer + BlurEffect; Android < 12 uses multi-pass offset blur approximation |
| TextShimmer | ✅ | ✅ | ✅ | ✅ | saveLayer + SrcAtop gradient; no capability needed |
| TextMorph | ✅ | ✅ | ✅ | ✅ | Pure Compose drawing (translate/scale + drawText); no capability needed |
| TextPressure | ⚠️ / ✅ 8+ | ✅ | ✅ | ✅ | Variable font via skiko `makeClone` / Android `Font(file, variationSettings)` (API 26+); Android < 8 synthesizes weight/width/slant |
| TextScramble | ✅ | ✅ | ✅ | ✅ | Pure canvas text drawing (GlyphCache); no capability needed |
| TrueFocus | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Per-word blur via shared `SegmentBlurRenderer` (BlurEffect); Android < 12 multi-pass fallback; glow = layered strokes |
| SplitText | | | | | |
| AuroraText | | | | | |
| VariableProximity | | | | | |
| RotatingText | | | | | |
| FuzzyText | | | | | |
| ScrollReveal | | | | | |
| TextRoll | | | | | |
| GradientText | | | | | |
| TextLoop | | | | | |
| TextShimmerWave | | | | | |
| ScrollFloat | | | | | |
| GlitchText | | | | | |
| DecryptedText | | | | | |
| TextEffect | | | | | |

## Known platform limitations
(Coordinators append.)
- **BlurText**: on Android < 12 (no `RenderEffect`) blur is approximated by drawing each piece 9–17
  times on rings of offsets — softer edges and more draw calls than a true Gaussian. Units are dp
  instead of CSS px; no `rootMargin`; no reduced-motion handling.
- **TextShimmer**: multi-line text sweeps all lines at once (band spans the layout width).
- **TextMorph**: complex scripts (Arabic, Hebrew, Indic…) morph per word, not per glyph, to keep
  shaping intact. Only Spring/Tween transitions; variants limited to alpha/scale/offset.
- **TextPressure**: Android < 8.0 has no font variations → nearest `FontWeight` + synthetic bold,
  horizontal scale for width, shear for italic. Roboto Flex has no `ital` axis, so italic uses
  `slnt` (−10°). Axis values are quantized (wght 20, wdth 4, ital 0.1) to bound the typeface set.
  Pointer tracked inside the component only (web original tracks the whole window).
- **TextScramble**: random glyphs are drawn centred in the slot of the glyph they replace; wide
  glyphs overflow their slot (as on the web). Slot count uses graphemes (reference: UTF-16 units).
- **TrueFocus**: same Android < 12 blur approximation as BlurText. Glow is 3 widening translucent
  strokes instead of CSS `drop-shadow`. Word boxes come from the text layout's line box; wrapping is
  the text layout's, not flex-wrap. `controller.seekTo` is ignored (continuous effect).
