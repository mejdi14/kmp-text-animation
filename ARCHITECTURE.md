# Architecture — TextMotion

## Modules
| Module | Role |
|---|---|
| `textmotion` | The library. Independent of the app. `explicitApi()`. Depends only on Compose runtime/foundation/ui/components-resources. Publishes via `maven-publish` (`publishToMavenLocal`). |
| `shared` | Showcase UI (gallery, demo screens, controls) used by every app. Depends on `textmotion`. |
| `androidApp`, `desktopApp`, `webApp`, `iosApp` | Thin platform launchers calling `App()`. |

Coordinates: `io.github.mejdi14:textmotion:0.1.0-SNAPSHOT`. Package `io.github.mejdi14.textmotion`.

## Source-set layout (`textmotion/src`)
```
commonMain     public API (root package) + internal/ shared infra + internal/<effect>/
skikoMain      Skia-backed actuals shared by iosMain, jvmMain, jsMain, wasmJsMain (RuntimeEffect, ImageFilter)
androidMain    Android actuals (android.graphics.RuntimeShader/RenderEffect, ICU BreakIterator)
iosMain        NSString composed-character segmentation
jvmMain        java.text.BreakIterator (JDK 20+ extended graphemes)
jsMain/wasmJsMain  Intl.Segmenter
commonTest     unit tests for non-visual logic
```
`skikoMain` is a manual intermediate source set (`dependsOn(commonMain)`); the default hierarchy
template is applied for the rest.

## Rendering model
Every effect is a single composable that draws with Canvas APIs — no per-character composables.

1. **Measure once.** `AnimatedTextLayout` (internal) is a `Layout` that measures the text with a
   `TextMeasurer` during the measure pass only when inputs/constraints change, then builds a
   `SegmentedTextLayout`. Size behaves like `BasicText`; optional `drawPadding` reserves room for
   ink that leaves the glyph boxes (blur, offsets).
2. **Draw-phase animation.** Clocks (`rememberTimeline`) write a `mutableFloatState` from a
   `withFrameNanos` loop. Effects read it only inside `onDraw`, so each frame invalidates drawing
   only — no recomposition, no relayout. The coroutine stops when finished, paused, inactive or
   disposed.
3. **Segments.** `SegmentedTextLayout` splits text into graphemes/words/lines (`TextGranularity`)
   with per-segment bounds, baseline, line index and word index. Two render strategies
   (`SegmentRenderMode`):
   - `Clip`: draw the full shaped paragraph clipped to the segment's window. Preserves Arabic
     joining, Indic conjuncts, ligatures and kerning. Clip windows extend to the midpoint of gaps
     between neighbours so word/line clips don't cut overhangs.
   - `Isolated`: each segment pre-measured on its own and placed on its original baseline/advance
     position. No clipping artifacts; shaping across segments is lost (fine for Latin/CJK/emoji).
   - `Auto` (default) picks `Clip` when `needsShapingContext(text)`.
   Brush fills always use the clip path so a gradient spans the whole paragraph.
4. **Transforms.** Per-segment 2D transforms via `withTransform`; CSS-style 3D (`rotateX`,
   `rotateY`, `perspective`) via `Transform3D`, which builds a projective 3×3 matrix stored in a
   Compose `Matrix` (works on Android's `android.graphics.Matrix` perspective terms and Skia).
5. **Shaders.** `RuntimeShaderEffect(source)` compiles once; `setFloat*` updates uniforms per frame;
   `shader()` for `ShaderBrush` fills, `renderEffect(child)` for `graphicsLayer` post-processing.
   Android: AGSL (`android.graphics.RuntimeShader`, API 33+). Others: SkSL (`RuntimeEffect`,
   `RuntimeShaderBuilder`, `ImageFilter.makeRuntimeShader`). Write the common subset
   (`half4 main(float2 p)`, `uniform shader content; content.eval(p)`).
   `PlatformCapabilities.supportsRuntimeShader/supportsRenderEffect` gate fallbacks for Android < 13 / < 12.
6. **Character swapping** (scramble/decrypt/morph): `GlyphCache` measures single glyphs lazily and
   once; random glyphs are drawn at fixed slot positions, never re-measuring the paragraph.

## Shared infrastructure index (`internal/`)
| File | Provides |
|---|---|
| `AnimatedTextLayout.kt` | base composable, `LayoutHolder`, `withDefaultColor()` |
| `SegmentedTextLayout.kt` | `SegmentedTextLayout`, `TextSegment`, `DrawScope.drawSegment` |
| `TextSegmenter.kt` | `graphemeBoundaries`, `graphemes`, fallback UAX#29 approximation, `needsShapingContext`, `containsRtl`, `codePointAt` |
| `Timeline.kt` | `TimelineState`, `rememberTimeline`, `Repeat.totalMillis/progressAt`, `resolveProgress` |
| `Interaction.kt` | `PointerTracker`, `trackPointer`, `autoPointerPosition`, `PointerInteraction.resolve`, `VisibilityState`, `trackVisibility`, trigger helpers, `ViewportPosition`, `trackViewportPosition` |
| `RuntimeShaderEffect.kt` (+actuals) | shader abstraction, `PlatformCapabilities` |
| `Transform3D.kt` | CSS-like 3D projective transforms |
| `GlyphCache.kt` | per-glyph layout cache, `randomGlyph` |
| `MathUtil.kt` | `lerp`, `inverseLerp`, `smoothstep`, `segmentProgress`, `hash01`, `valueNoise1/2` |

Public shared types (root package): `TextEasing`, `Stagger`/`StaggerFrom`, `AnimationTrigger`,
`Repeat`, `TextAnimationController`/`rememberTextAnimationController`, `TextGranularity`,
`SegmentRenderMode`, `PointerInteraction`, `ScrollRange`, `LocalTextMotionStyle`, `TextCharsets`.

## Key decisions
- **Foundation-only dependency** (no Material) → `LocalTextMotionStyle` instead of `LocalTextStyle`.
- **Visibility trigger** uses `Modifier.onVisibilityChanged` (Compose 1.9+ API, available in CMP 1.12).
- **Scroll progress** derives from the element's unclipped window position (`onGloballyPositioned`),
  so it works in any scroll container; external `scrollProgress` lambdas override it.
- **Pointer tracking** never consumes events (keeps scroll containers working).
- **Timing values** come from the reference specs; durations are `Int` millis.

## Decisions added by feature work
(Coordinators append new shared infra / decisions here.)
- **C01 (no new shared infra).** Patterns worth reusing:
  - *Per-piece blur* (`internal/blurtext/BlurTextRenderer.kt`): one reusable `GraphicsLayer` per
    piece slot (from `LocalGraphicsContext`) with `BlurEffect(TileMode.Decal)`, recorded in the draw
    phase; sigma from CSS px via radius = (σ − 0.5)/0.57735; multi-pass ring fallback when
    `!PlatformCapabilities.supportsRenderEffect`. Promoted to `internal/SegmentBlurRenderer` in C03 (see below); was a candidate if
    another effect (TrueFocus, ScrollReveal, TextEffect) needs per-segment blur.
  - *Analytic spring* (`springRemaining` in `BlurTextMotion.kt`) reproducing Motion's
    `visualDuration`/`bounce` spring as a pure function of time.
  - *Glyph-clipped gradient* (TextShimmer): `saveLayer` → draw text → gradient rect with
    `BlendMode.SrcAtop` → restore (equivalent of CSS `background-clip: text`).
- **C02 new shared infra:**
  - `AnimatedTextLayout(onLayoutChanged = …)`: called during measurement whenever a new
    `SegmentedTextLayout` is built (never per frame) — lets an effect diff old/new geometry (TextMorph).
  - `PointerTracker.pressCount` (mutable int state): incremented on each press inside the element.
  - **Variable fonts** (`internal/font/`, public `VariableFont` + `VariationAxisRange`):
    `FontVariationKey` packs wght/wdth/ital/opsz into a `Long`; `VariationQuantizer` quantizes axis
    values; `rememberVariableFontFamilies(font)` loads the bytes once and caches one `FontFamily`
    per key (skiko: `FontMgr.makeFromData` once + `makeClone(variations)` per key; Android: file
    copied to `cacheDir/textmotion-fonts`, `Font(file, variationSettings)` on API 26+;
    `SyntheticVariation` fallback = FontWeight + scaleX + shear). `VariableGlyphCache[glyph, key]`
    measures each glyph once per key; prewarm it off the hot path. **Reuse for VariableProximity.**
  - Pattern: TextMorph identity keys (`internal/morph/MorphKeys.kt`, lowercased value + occurrence)
    and a closed-form Motion stiffness/damping/mass spring (`MorphMotion.kt`).
- **C03 new shared infra:**
  - `internal/SegmentBlurRenderer` (promoted from BlurText): `drawBlurred(layout, segment, slot, sigmaPx,
    alpha, color, tintColor, tint, brush)` records each blurred segment into a reusable `GraphicsLayer`
    with `BlurEffect` (effect re-created only when the radius changes), Android < 12 multi-pass ring
    fallback; `blurRadiusForSigma` moved with it. Used by BlurText and TrueFocus; **reuse for
    ScrollReveal, TextEffect, SplitText blur variants.** Call `release()` on dispose.
  - Pattern: TextScramble's pure seeded schedule (`internal/textscramble/ScrambleMath.kt`, hash of
    seed/tick/slot → glyph) — reuse for DecryptedText.
- **C04 (no new shared infra):** SplitText reuses `SegmentBlurRenderer`, `Transform3D`, `Stagger`,
  `trackPointer`; its GSAP `distribute({each, from})` stagger and semi-implicit spring hover
  (`internal/splittext/SplitTextMotion.kt`, `SplitTextHover.kt`) are reusable patterns for ScrollFloat /
  TextEffect. AuroraText reuses TextShimmer's glyph-mask pattern (`saveLayer` + `SrcAtop`) with a
  cached `Brush` (`internal/auroratext/AuroraBrushCache.kt`) — reuse the cache pattern for GradientText.
- **C05:** new shared helper `SegmentedTextLayout.buildFromRanges(layout, ranges, mode, measurer)` —
  segments from arbitrary character ranges (custom separators, words, lines); used by RotatingText,
  reuse for TextLoop / TextRoll. RotatingText reuses SplitText's `SplitTextRenderer` + `CH_*` channels;
  its analytic spring + exit-then-enter machine (`internal/rotatingtext/`) is a pattern for TextLoop.
  VariableProximity adds no infra: reuses `internal/font/` + `drawPressureGlyph`, public
  `ProximityArea`/`Modifier.proximityArea` for container-wide pointer tracking.
- **C06:** FuzzyText is the first user of `RuntimeShaderEffect`: one shader source valid as AGSL and
  SkSL (`internal/fuzzy/FuzzShader.kt`), text recorded once into a `GraphicsLayer` with the shader as
  render effect, uniforms updated per frame; `ImageBitmap` slice-blit fallback when shaders are
  unavailable or fail to compile — pattern for GlitchText. Build: `textmotion` `jvmTest` depends on
  `compose.desktop.currentOs` (test-only) so JVM unit tests compile SkSL with the real Skia compiler
  (`FuzzShaderCompileTest`). ScrollReveal adds no infra (reuses `SegmentBlurRenderer`,
  `trackViewportPosition`, and `splittext.emPx`/`estimateUnitCount` — candidates to move to shared
  internals); its `SmoothedProgress` (`internal/scrollreveal/`) is reusable for ScrollFloat.
- **C07:** no new shared infra. TextRoll (`internal/textroll/`) draws each unit twice from one
  paragraph with `Transform3D` + backface culling and `SegmentBlurRenderer` (2 slots per unit) — a
  pattern for TextLoop/TextEffect 3D presets. GradientText (`internal/gradienttext/`) reuses
  `AuroraMath.cssGradientLine` + `blurRadiusForSigma`; its renderer caches repeating linear,
  radial-blob and sweep brushes and draws glow via a blurred `GraphicsLayer` (multi-pass fallback);
  `GradientDynamics` (spring-smoothed pointer + frozen time) is reusable for hover-driven effects.
