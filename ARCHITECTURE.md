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
