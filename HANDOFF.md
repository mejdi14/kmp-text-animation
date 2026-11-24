# HANDOFF

**State (2026-11-24, after Coordinator 03):** foundation + 6/20 effects on `main`.
- ✅ `BlurText`, ✅ `TextShimmer` (C01), ✅ `TextMorph`, ✅ `TextPressure` (C02), ✅ `TextScramble`,
  ✅ `TrueFocus` (C03) merged, with demos in the showcase and notes in `docs/features/`.
- All targets compile (library, shared, desktopApp, androidApp assembleDebug); `:textmotion:jvmTest`
  green. Nothing visually verified (project rule).
- C03 added shared infra (see ARCHITECTURE.md): `internal/SegmentBlurRenderer` (per-segment blur with
  reusable GraphicsLayers + Android < 12 fallback, extracted from BlurText) and the seeded
  scramble schedule pattern `internal/textscramble/ScrambleMath`.
- Chaining now runs as the hourly scheduled task "textmotion-coordinator" (see CLAUDE.md §7);
  `.orchestration/lock` guards against overlapping runs.

**Next:** Group 04 — SplitText + AuroraText (CLAUDE.md §7).

**Group 04 worker notes:** SplitText (Sonnet): GSAP SplitText-style chars/words/lines entrance with
stagger (`from` y/opacity → `to`, ease `power3.out`, `delay` ms per piece, threshold/rootMargin →
`AnimationTrigger.OnVisible`, `onLetterAnimationComplete` → `onFinished`); use `TextGranularity` +
`segmentProgress`/`Stagger`; draw-phase translate/alpha per segment, no composables per letter.
AuroraText (Opus high): animated multi-colour gradient flowing through the glyphs (magicui `colors`,
`speed`); reuse TextShimmer's glyph-clipped gradient pattern (`saveLayer` + `BlendMode.SrcAtop`),
continuous → pause when invisible/controller paused; consider a runtime shader (`RuntimeShaderEffect`)
only with a plain-gradient fallback for Android < 13.

**Notes for workers**
- Showcase controls (package `...showcase.controls`): `DemoStage`, `ControlPanel`, `TextControl`,
  `SliderControl`, `IntSliderControl`, `DurationControl`, `ColorControl`, `ColorsControl`,
  `SwitchControl`, `ChoiceControl`, `PlaybackControls(controller)`, `CodeBlock(code)`.
- `ShowcaseColors` (in `ShowcaseApp.kt`) holds the showcase palette (dark background).
- Text in demos should be light-coloured (dark showcase background).
- Variable-font infra exists in `internal/font/` (C02, TextPressure); C05 (VariableProximity) must
  reuse it (`rememberVariableFontFamilies`, `VariationQuantizer`, `VariableGlyphCache`).
- Worktree branches get an app prefix/suffix (e.g. `claude/feat-blur-text-d1f390`); find them with
  `git branch | grep feat` before merging. Parallel workers both edit `DemoRegistry.kt` → expect a
  trivial merge conflict.
- zsh gotcha: `git show "$B:path"` is mangled by zsh modifiers (`$B:t`); use `"${B}:path"`.

**Known issues**
- `SegmentBlurRenderer` (BlurText/TrueFocus) allocates a `BlurEffect` per blurred piece per frame
  only while its radius changes (minor).
- BlurText Android < 12 fallback and 3D tilt need a visual check by the owner.
- TrueFocus glow, spring feel, multi-line wrapping and TextScramble glyph alignment need an owner check.
