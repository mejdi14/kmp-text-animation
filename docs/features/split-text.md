# SplitText

Native recreation of React Bits "Split Text" (GSAP SplitText + ScrollTrigger). Code:
`SplitText.kt`, `internal/splittext/` (`SplitTextMotion`, `SplitTextHover`, `SplitTextRenderer`,
`SplitTextState`). Tests: `commonTest/.../internal/splittext/SplitTextMotionTest.kt` (22 tests).

## API summary
```kotlin
SplitText(text, modifier, style, effect = Rise, granularity = Character,
    animationFrom = null, animationTo = null, mask = false, clipOverflow = null,
    accentColor = Unspecified, hoverEffect = None, replayOn = None,
    durationMillis = 1250, delayMillis = 0, easing = null, stagger = Stagger(50),
    renderMode = Auto, trigger = OnVisible(0.1f), repeat = Once, speed = 1f,
    controller = null, onFinished = null)
```
- `SplitTextEffect`: Rise, Fade, Blur, Flip, Pop, Skew, Wave (from/to/ease table of the reference).
- `granularity`: Character / Word / Line = `splitType` chars / words / lines (combos like
  `'chars,words'` animate the chars, as in the reference).
- `stagger`: `Stagger(eachMillis, from)` = GSAP `distribute({each, from})`: spread is always
  `each x (count-1)`; center/edges step by `2 x each`; random shuffles the start distribution.
- `animationFrom/To: SplitTextKeyframe` replace the preset (defaults `opacity 0, y 40` -> rest, ease power3.out).
- `repeat`: `SplitTextDefaults.Loop` = `loop` with `loopDelay 1.5 s`. A loop/replay cycle is
  "exit (power2.in, `max(350 ms, 0.55 x duration)`), then reveal", per unit, same stagger.
- `replayOn`: Hover / Click replay once revealed. `hoverEffect`: Lift / Wave springs (180/19, kick 0.024).
- `onFinished` = `onLetterAnimationComplete`, fired on every completed reveal.

## Rendering technique
One shaped paragraph measured by `AnimatedTextLayout`; every unit is a `TextSegment` drawn in the
draw phase with its own matrix (`Transform3D`: translate, scale, rotateX/Y/Z + perspective, skewX)
and alpha. No composable per letter, no per-frame text measurement. Blur goes through the shared
`SegmentBlurRenderer` (per-unit GraphicsLayer + `BlurEffect`, multi-pass fallback on Android < 12).
Accent flash = tint of the unit colour (`lerp(text, accent)`, 0.3 x duration delay, 0.7 x duration, power1.inOut).
`mask` clips each unit to its line box; `clipOverflow` (auto like the reference's
`overflow: hidden`) clips to the text box so Rise/Fade units emerge from the edge.
Hover springs run in a frame loop only while the pointer is over a revealed text or springs move.

## Platform differences
Same code path on all targets. Blur effect: Android 12+/Skia use `RenderEffect`; Android < 12 uses an
approximation. `SegmentRenderMode.Auto` uses Clip mode for Arabic/Indic (ink overhang may be cut).

## Deviations from the reference
- `trigger`: `view` -> `OnVisible(minFractionVisible)`; the ScrollTrigger `rootMargin` (-100 px) is
  approximated by the visibility threshold; `load` -> `OnAppear`; hover/click -> `replayOn`
  (the first reveal always uses `trigger`).
- No reduced-motion handling, no `tag`/`className`/`textAlign` props (use `style`/`modifier`).
- Custom keyframes are full snapshots (unset = rest); transform origin of custom keyframes is the unit centre.
- Spaces are not animated units (not drawn anyway); `from: 'index'` stagger is not supported.
- CSS `skewX` sign convention (same helper as BlurText); GSAP's sign may differ visually.
- `hoverEffect` pointer centres are re-read from the layout every frame (reference measures at enter).
- Unit count for the stagger is estimated from the text until the first layout, then taken from it.

## Unverified (no visual testing by project rule)
Compiles on all targets and unit tests cover delays, timeline, presets, springs. Not visually checked:
overall feel of all seven presets, mask/clip edges (first/last line extended by one font size),
flip perspective, skew direction, accent flash, hover lift/wave amplitude, multi-line and RTL behaviour.
