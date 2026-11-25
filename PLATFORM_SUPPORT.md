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
| SplitText | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Per-segment draw-phase transforms (translate/scale/skew/`Transform3D` flip); `blur` preset uses `SegmentBlurRenderer` (Android < 12 multi-pass fallback) |
| AuroraText | ✅ | ✅ | ✅ | ✅ | Plain `Brush` linear gradient composited onto glyphs (`SrcAtop`); no shader needed, identical on all platforms |
| VariableProximity | ⚠️ / ✅ 8+ | ✅ | ✅ | ✅ | Same variable-font path as TextPressure (129 quantized levels, cached typefaces); Android < 8 synthesizes weight/width/slant (no `opsz`) |
| RotatingText | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Draw-phase per-unit transforms via SplitText renderer; Blur effect uses `SegmentBlurRenderer` (Android < 12 multi-pass fallback) |
| FuzzyText | ⚠️ / ✅ 13+ | ✅ | ✅ | ✅ | Slice displacement in a runtime shader (AGSL on Android 13+, SkSL on Skia targets); Android < 13 uses the reference canvas slice re-blit (`drawImage` per slice, ColorMatrix colour split) |
| ScrollReveal | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Draw-phase scroll progress (`trackViewportPosition`); per-word blur via `SegmentBlurRenderer` (Android < 12 multi-pass fallback) |
| TextRoll | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Per-grapheme `Transform3D` projective transforms (two layers per unit, backface culling); `BlurRoll` uses `SegmentBlurRenderer` (Android < 12 multi-pass fallback) |
| GradientText | ⚠️ / ✅ 12+ | ✅ | ✅ | ✅ | Cached linear/radial/sweep `Brush` composited onto glyphs (`SrcAtop`), no shader; `glow` uses `BlurEffect` (Android < 12 multi-pass offset approximation) |
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
- **SplitText**: `rootMargin` (-100px) is approximated by the visibility threshold (`OnVisible(0.1f)`).
  `staggerFrom = 'index'` and reduced-motion handling are not supported. Custom `from`/`to` keyframes
  are full snapshots with a centred transform origin. Android < 12 `blur` preset uses the multi-pass
  approximation. The stagger unit count is estimated from the text until the first layout.
- **AuroraText**: rotated/scaled text can overflow the composable bounds (clipping parents cut it).
  Glyph ink outside the line box is still painted by the gradient; colour emoji are tinted (as with
  CSS `background-clip: text`).
- **VariableProximity**: only `wght`, `wdth`, `opsz`, `ital`, `slnt` axes are rendered (others parsed
  and ignored). Without a `ProximityArea` the pointer is tracked over the text bounds only (reference:
  whole window). No reduced-motion switch for `idleWave`. RTL handled by mirroring word order per line
  (no full bidi); complex-script words react as whole words.
- **RotatingText**: only the `wait` presence mode (no `sync`/`popLayout`); `initial/animate/exit`
  overrides replaced by one `transition` parameter; Flip z-origin approximated by a vertical
  translation; units wrapping across lines use their union box; `controller.seekTo` ignored.
- **FuzzyText**: `transitionDuration` (frames) became `transitionDurationMillis`; default size from the
  style (weight 900 only when unset); click/burst fire on press; no reduced-motion; `seekTo` freezes the
  frame; ink overhanging the line box by > 25 % of line height may be clipped. AGSL on a real Android
  13+ device and the < 13 fallback are unverified (SkSL compile is unit-tested on JVM).
- **ScrollReveal**: `ScrollRange` fractions approximate `0.2*min(h, viewport)` as `0.2*h`; GSAP end clamp
  not replicated; viewport is the window (custom scroll containers: pass `scrollProgress`); no
  reduced-motion; pause/restart only affect the Enter-mode clock.
- **TextRoll**: delays in ms (reference: seconds); `transition` reduced to an easing (no springs);
  `onFinished` fires when the latest layer ends; text change restarts the roll; Clip render mode
  (Arabic/Indic) may cut overhanging ink while rotating; Android < 12 `BlurRoll` uses multi-pass blur.
- **GradientText**: Android < 12 glow is an alpha-compensated multi-pass offset approximation; no
  reduced-motion; followPointer spring/pull freeze with the clock when paused/invisible (reference keeps
  settling); variant/glow apply to the whole string; conic orientation compensated by −90°.
