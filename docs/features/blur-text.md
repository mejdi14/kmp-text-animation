# BlurText

Reference: React Bits "Blur Text" (`BlurText.jsx`, motion/react). Spec: `docs/reference-specs/reactbits.md` §1.

## API
```kotlin
BlurText(
    text = "Isn't this so cool?!",
    effect = BlurTextEffect.Blur,            // Zoom, Tilt, Focus, Drift
    direction = BlurTextDirection.Top,       // Bottom, Left, Right
    blur = 10.dp, distance = 50.dp,
    granularity = TextGranularity.Word,      // Character = animateBy 'letters'
    stagger = Stagger(200, StaggerFrom.Start),   // delay + staggerFrom (Start/End/Center/Random)
    stepDurationMillis = 350,
    fromColor = Color.Unspecified, focusOnHover = false,
    animationFrom = null, animationTo = null, easing = null,   // custom BlurTextKeyframe snapshots
    replayOn = BlurTextReplay.None,          // Hover / Click = reference trigger 'hover' / 'click'
    trigger = AnimationTrigger.OnVisible(0.1f),   // 'view' + threshold; OnAppear = 'load'
    repeat = Repeat.Once,                    // BlurTextDefaults.Loop = loop + loopDelay 2 s
    controller = null, onFinished = null,    // onAnimationComplete (fires after each intro)
)
```

## Behaviour (matches the reference source)
- Built-in effects: fade channels (blur, opacity) tween from→rest over `2 × step` with the effect's
  fade ease; move channels (x, y, scale, rotateX/Y, skewX) use Motion's spring
  (`visualDuration = total × settle`, `bounce`), solved analytically (`springRemaining`). Like the
  reference, the intermediate "step" snapshot is not used by built-ins (only by custom keyframes).
- Custom snapshots: keyframes `[from, …to]`, evenly spaced, `easing` (default linear) per segment, no spring.
- Timing: `inTotal = total × max(1, settle × 2.2) + maxRank × stagger`; exit = `step × 1.3` with
  `rank × stagger × 0.4`; loop = in → hold (`delayBetweenMillis`) → out → in.
- Ranks: exact port of `buildRanks` (center fractional ranks, FNV-1a + LCG shuffle seeded by the text,
  `Stagger.seed` xor-ed in). In letter mode the space pieces between words consume a rank, like the reference.
- `fromColor` follows the CSS colour transitions (`0.9 × total`, ease `(0.22,1,0.36,1)`).
- `focusOnHover`: other words go to `blur(clamp(blur×0.3, 2, 6))` + opacity 0.3 over 450 ms.
- Hover/click replay only when settled; text then starts fully visible (trigger ignored).

## Rendering
- One `AnimatedTextLayout` (measured once), pieces = `SegmentedTextLayout` segments (Clip mode for
  Arabic/Indic). All animation is read in the draw phase.
- Per-piece transform: `Transform3D` (perspective 600 for Tilt, CSS transform-origin) × skew matrix.
- Blur, Android 12+ / iOS / Desktop / Web: each blurred piece is recorded into a reusable
  `GraphicsLayer` with `BlurEffect(TileMode.Decal)`; sigma converted from CSS px (radius =
  (σ − 0.5)/0.57735). Rested pieces draw directly (no layer cost once settled).
- Android < 12 fallback: multi-pass offset drawing (centre + 1–2 rings of 8 offsets scaled by σ, per-pass
  alpha so overlap reaches the target opacity). Approximate, softer edges, more draw calls.
- `focusOnHover` word blur combines with the piece blur as `sqrt(σ1² + σ2²)` (CSS nests filters).

## Deviations / limitations
- Units are dp instead of CSS px. Piece boxes exclude the trailing space (reference inline-block
  includes it) → transform origin of words is shifted by half a space.
- Pieces of a word broken across lines animate as two pieces with the same rank.
- `Repeat.mode` is ignored; `TextGranularity.Line` is an extension (rank count from hard line breaks).
- Brush text + `fromColor` cross-fades brush and tint instead of a colour mix.
- No `rootMargin`; no reduced-motion handling yet.
- Text drawn outside the composable bounds is not clipped by BlurText but can be clipped by parents.

## Unverified
Compiled on all targets and unit-tested (keyframes, spring, ranks, timing, phases) only. Nothing was
visually verified: blur appearance, the Android < 12 fallback, 3D tilt projection and hover dimming
need a visual check.
