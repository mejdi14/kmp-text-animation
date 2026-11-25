# TextRoll

Reference: motion-primitives.com/docs/text-roll (spec: `docs/reference-specs/motion-primitives-magicui.md`
§4; behaviour re-checked against the current `text-roll.tsx` and the three docs examples).

## API
```kotlin
TextRoll(
    text, modifier, style = LocalTextMotionStyle.current,
    variants = TextRollVariants.Roll /* BlurRoll, Slide, or custom TextRollVariants(enter, exit) */,
    enterDelayMillis = { i -> i * 100 },        // getEnterDelay
    exitDelayMillis = { i -> i * 100 + 200 },   // getExitDelay
    clipToBounds = false, perspective = 10000.dp,
    granularity = TextGranularity.Character, renderMode = SegmentRenderMode.Auto,
    durationMillis = 500, delayMillis = 0, easing = TextEasing.MotionEaseIn,   // transition
    trigger = AnimationTrigger.OnVisible(), repeat = Repeat.Once, speed = 1f,
    controller = null, onFinished = null,      // onAnimationComplete
)
```
- `TextRollVariant(initial, animate, origin: TransformOrigin? = null)` per layer;
  `TextRollKeyframe(rotationX, rotationY, x, y, scale, opacity, blur)` covers the Motion values used by
  the reference and its docs (`rotateX`, `y`, `filter: blur()`), plus a few common extras.
- Presets: `Roll` (default), `BlurRoll` (docs "custom variants"), `Slide` (docs "custom transition &
  delay"; pair with `clipToBounds`, 300 ms, `TextRollDefaults.staggered(50, 50)`, `SlideEasing`).

## Rendering
- One `AnimatedTextLayout` paragraph; per frame each unit is drawn **twice** from it (no composable per
  letter): the enter copy (pivot 50 % / 25 % of the glyph box) then the exit copy on top (pivot
  50 % / 100 %), like the reference's two absolutely positioned spans over an invisible sizer.
- Each copy gets a `Transform3D` projective matrix (`rotateX`/`rotateY` + CSS perspective, default
  10000 px → dp) and CSS `backface-visibility: hidden` culling (`cos rx · cos ry ≤ 1e-3` → skipped, so
  the edge-on 90° pose is invisible exactly as in CSS).
- Before its delay a layer holds `initial`; afterwards it eases to `animate` and stays (Motion
  semantics). Delay index = grapheme index **including spaces** (reference `split('')`); emoji/
  combining clusters stay whole. Word/line granularity use the word/line index.
- Blur goes through the shared `SegmentBlurRenderer` (graphics-layer `BlurEffect`; multi-pass
  fallback on Android < 12), 2 layer slots per unit.
- Fast path: when the variants rest as plain text before the first delay / after the roll (true for
  `Roll` and `BlurRoll`), the paragraph is drawn with a single `drawText`.
- The clock (`rememberTimeline`) only runs while triggered; infinite repeats pause when invisible or
  when the controller is paused. Seek maps to one roll's progress.

## Platform differences / limitations
- Same code path on all targets. Android uses the projective `Matrix` on the canvas; blur needs
  Android 12+ for the exact Gaussian, otherwise the multi-pass approximation.
- `SegmentRenderMode.Clip` (Arabic/Indic) draws each copy as a clipped window of the shaped paragraph;
  ink overhanging a glyph's advance box can be cut while rotating.

## Deviations from reference
- `getEnterDelay`/`getExitDelay` take/return milliseconds (`Int`) instead of seconds.
- `transition` is reduced to its easing (`easing`); springs are not supported.
- `onFinished` fires when the whole run (all repeats) ends = when the latest layer finishes. The
  reference fires on the last character's exit layer; identical for monotonic delays (defaults).
- Changing `text` restarts the roll (the reference re-uses spans by index and does not replay).
- Default trigger `OnVisible()` (library convention) instead of on-mount; `OnAppear` gives the latter.
- Added: `granularity`, `repeat`, `delayMillis`, `clipToBounds`, `perspective`, `speed`, `controller`.

## Unverified
Nothing was visually verified (project rule): roll direction/feel versus the web, perspective
conversion, Android projective-matrix drawing, blur roll on Android < 12, RTL/Arabic rolling.
Unit tests cover delays, iteration/total timing, repeat mapping, unit indexing, culling and poses.
