# ScrollReveal

Reference: reactbits.dev/text-animations/scroll-reveal (spec: `docs/reference-specs/reactbits.md` section 8).
File: `textmotion/.../ScrollReveal.kt`, internals in `internal/scrollreveal/`.

## API summary
`ScrollReveal(text, modifier, style, effect, granularity, enableBlur, baseOpacity, baseRotation,
blurStrength, baseOffset, stagger, highlightColor, mode, scrollRange, rotationRange, scrollProgress,
smoothingMillis, durationMillis, renderMode, trigger, speed, controller, onFinished)`

- Defaults = reference: `baseOpacity 0.1`, `baseRotation 3`, `blurStrength 4.dp`, `stagger 0.05`,
  `enableBlur true`, `effect Fade`, words, `smoothing 0` (locked to scroll), enter `duration 800 ms`.
- `effect`: `Fade | Rise | Flip | Scale` (reference `effect`); `granularity`: `Word | Character | Line`
  (`splitBy`); `mode`: `Scrub` (GSAP scrub) or `Enter` (timed one-shot).
- Scroll mapping: `scrollRange` (word reveal, default `ScrollRevealDefaults.WordRange` = GSAP
  `top bottom-=0.2*h` .. `bottom bottom`), `rotationRange` (`top bottom` .. `bottom bottom`). An external
  `scrollProgress: () -> Float` overrides both; `controller.seekTo()` freezes the reveal.

## Rendering
One shaped paragraph, `AnimatedTextLayout` + per-segment drawing, no composable per word. Piece `i` has local
progress `clamp((p*total - i*gap)/span)` with `span = 0.5` (scrub) or `duration` seconds (enter),
`total = span + (n-1)*gap`. `computeLook` ports `paintPiece` (opacity, blur, rise/flip/scale, base offset,
highlight glow). The block rotation (`baseRotation -> 0`, origin left/centre, right in RTL) wraps all pieces.
Progress is read in the draw phase only: scrolling never recomposes or relayouts. Tracking uses
`trackViewportPosition` + `ViewportPosition.progress(ScrollRange)` (any scroll container, window viewport).
`smoothingMillis > 0` eases the displayed progress towards the scroll position (exponential, ~95 % after N ms).

Blur goes through the shared `SegmentBlurRenderer`: `RenderEffect` layers on Android 12+ and all Skia targets,
multi-pass offset approximation on Android < 12.

## Platform differences
None beyond the blur fallback above. Same code on Android, iOS, Desktop, JS, Wasm.

## Deviations from the reference
- `ScrollRange` holds fractions, so `0.2*min(h, viewportHeight)` is approximated by `0.2*h` (differs only for
  elements taller than the viewport). GSAP `clamp()` of the end (never beyond max scroll) is not replicated.
- Viewport is the window, not a custom `scrollContainerRef` (use `scrollProgress` for custom containers).
- `splitBy = 'lines'` paints words individually (like the reference) with one stagger step per line.
- Enter mode: `trigger = OnVisible(0.2f)` approximates the ScrollTrigger start; no leave/re-enter replay unless
  `OnVisible(once = false)`. `Repeat` is not offered. Pause/restart apply only to the enter clock.
- Rise masking, `blurStrength`/`baseOffset` are `Dp` (CSS px). `stagger` is a plain float (reference unit).
- `reduced motion` is not honoured (no platform-independent API). Reference `className` props have no equivalent.
- A little draw padding (`2 x blurStrength`) is added around the text so blur is not clipped by parents.

## Unverified
Nothing was run visually (project rule). Math is unit tested (`ScrollRevealMathTest`); the scroll tracking,
smoothing loop, Rise clip box, Flip pivot/perspective and the Android < 12 blur look need an owner check.
