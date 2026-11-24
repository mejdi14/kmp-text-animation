# TrueFocus

Reference: reactbits.dev/text-animations/true-focus (spec: `docs/reference-specs/reactbits.md` §3).
Files: `TrueFocus.kt`, `internal/truefocus/{TrueFocusLogic,TrueFocusRenderer}.kt`, shared
`internal/SegmentBlurRenderer.kt`, demo `showcase/demos/TrueFocusDemo.kt`.

## API
`TrueFocus(text, modifier, style, separator=" ", manualMode=false, frameStyle=Brackets|Box|Underline|Highlight,
order=Sequential|Random, easing=Smooth|Spring, blurAmount=5.dp, focusScale=1f, borderColor=#008000 (CSS green),
highlightColor, glowColor=Transparent, animationDurationMillis=500, pauseBetweenMillis=1000, cornerSize=16.dp,
borderWidth=3.dp, unfocusedOpacity=1f, pointerInteraction=Pointer, renderMode, speed, controller, onFocusChanged)`.
All reference props/defaults are present (names Kotlin-ised, seconds → millis).

## Rendering
- `AnimatedTextLayout` with word granularity; words are joined by an em space (= the reference's `gap: 1em`).
  A reference word that contains the separator's neighbours (several whitespace tokens) is one focus unit.
- Each word is drawn through `SegmentBlurRenderer` (blur sigma = `(1-level)*blurAmount`), alpha =
  `lerp(unfocusedOpacity,1,level)`, scale around the word centre = `lerp(1,focusScale,level)`.
- Focus level of each word eases `from → target` over `animationDuration` (CSS `ease`); an interrupted
  move starts from the current levels and current frame rectangle (no jump).
- Frame: one animated rectangle (tween `cubic-bezier(.65,0,.35,1)` or the spring `visualDuration=duration,
  bounce=.38`); the four corner boxes / box / underline / highlight are derived from it with the reference
  geometry (GAP 10, cornerSize, 30 % highlight). Frame fades in over `animationDuration` on first show.
- Glow = 3 widening translucent strokes in `glowColor` (approximates `drop-shadow(0 0 4px)` without RenderEffect).
- All animated values are read in the draw phase; the clock (`rememberTimeline`) runs only while visible,
  not paused, and — in manual mode without auto fallback — only while a transition is in flight.
- Auto mode: `FocusCycle` advances every `duration + pause` (sequential or random-other-word, seeded by text).
  Manual mode: hover (desktop/web) / touch-drag (mobile) via `PointerTracker`, events never consumed.
  `PointerWithAutoFallback` keeps cycling while no pointer is present; `Auto` ignores pointers; `None` freezes.

## Shared infra
`internal/SegmentBlurRenderer` (layer + `BlurEffect`, Android < 12 multi-pass fallback) was extracted from
BlurText's renderer; `BlurPieceRenderer` now delegates to it (behaviour unchanged). `blurRadiusForSigma` moved with it.

## Differences from the reference
- Word boxes are the layout's line box (CSS uses the inline-flex word box) — same height for single-line text.
- Wrapping is the text layout's, not flex-wrap; frame/hit rects are per word on its line.
- Scale uses the focus level easing (CSS: separate `smooth`/`spring` bezier for the transform).
- Controller `seekTo` is ignored (continuous, event-driven effect). Reduced-motion is not read.
- Hit boxes extend 0.4 line-heights sideways so the 1 em gap has no dead zone.

## Unverified
Nothing was run visually. Android < 12 blur fallback, glow look, spring feel and multi-line wrapping need an owner check.
