# TextScramble

Reference: motion-primitives.com/docs/text-scramble (spec: `docs/reference-specs/motion-primitives-magicui.md` §3).

## API
```kotlin
TextScramble(text, modifier, style = LocalTextMotionStyle.current,
    durationMillis = 800, tickMillis = 40, characterSet = TextCharsets.Alphanumeric,
    seed: Int? = null, trigger = AnimationTrigger.OnAppear, speed = 1f,
    controller = null, onFinished = null)
```
Reference mapping: `duration` → `durationMillis`, `speed` (s/frame) → `tickMillis`, `characterSet`,
`trigger` → `AnimationTrigger`, `onScrambleComplete` → `onFinished`. `speed` is the library-wide playback multiplier.

## Technique
- Pure schedule in `internal/textscramble/ScrambleMath` (steps = duration/tick, unrounded; slot `i` resolved when
  `tick/steps*count > i`; run ends at first tick `> steps`, i.e. 21 ticks = 840 ms for defaults). Random glyph is a
  deterministic hash of (seed, tick, slot) → fresh each tick, reproducible with `seed`.
- Rendering: `AnimatedTextLayout` with `TextGranularity.Character` (grapheme clusters). Resolved slots are drawn with
  `drawSegment`; unresolved slots draw a cached single-glyph layout (`GlyphCache`, prewarmed for the whole character set)
  centred in the slot of the glyph they replace. Nothing is measured per frame; the clock is read in the draw phase only.
- Layout is always measured with the final text, so the width never jitters (deliberately better than the reference).
- Spaces/line breaks are never scrambled. Slot index counts graphemes including spaces (as `text[i]` in the reference),
  so the reveal timing matches. Reveal order is logical text order (also for RTL).
- Unstarted state (OnVisible before visible, Manual before `controller.restart()`) shows the final text.

## Platform differences
None: pure canvas text drawing, same path on Android, iOS, Desktop, JS, Wasm. Arabic/Indic segments use the shared
`SegmentRenderMode.Auto` (clip) for resolved slots.

## Deviations
- First tick (random glyphs) is shown immediately; the reference shows the original text for the first 40 ms.
- Trigger is an `AnimationTrigger` (default OnAppear = reference mount behaviour) rather than a boolean; replay via
  `controller.restart()`. Changing `text` does not restart (as in the reference).
- Slot count uses graphemes, the reference uses UTF-16 units (identical for BMP text).
- `controller.seekTo(p)` scrubs the whole run (0..1 of the 840 ms).

## Unverified
Nothing visually verified. Random glyph vertical alignment uses the segment baseline vs. glyph first baseline; wide
glyphs (e.g. `W`, katakana) overflow their slot rather than clip (as in the web version).
