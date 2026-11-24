# HANDOFF

**State (2026-11-24, after Coordinator 01):** foundation + 2/20 effects on `main`.
- ✅ `BlurText` (5 effects, custom keyframes, loop, hover/click replay, focusOnHover, fromColor) and
  ✅ `TextShimmer` merged, with demos in the showcase and notes in `docs/features/`.
- All targets compile; `:textmotion:jvmTest` green. Nothing visually verified (project rule).
- No new shared infra in C01; reusable patterns (per-piece blur layer, analytic spring,
  glyph-clipped gradient) are listed in ARCHITECTURE.md "Decisions added by feature work".

**Next:** Coordinator 02 — TextMorph + TextPressure (follow CLAUDE.md §7).

**Notes for workers**
- Showcase controls (package `...showcase.controls`): `DemoStage`, `ControlPanel`, `TextControl`,
  `SliderControl`, `IntSliderControl`, `DurationControl`, `ColorControl`, `ColorsControl`,
  `SwitchControl`, `ChoiceControl`, `PlaybackControls(controller)`, `CodeBlock(code)`.
- `ShowcaseColors` (in `ShowcaseApp.kt`) holds the showcase palette (dark background).
- Text in demos should be light-coloured (dark showcase background).
- Variable-font infra does not exist yet: C02 (TextPressure) creates it under
  `internal/font/`, C05 (VariableProximity) reuses it.
- Worktree branches get an app prefix/suffix (e.g. `claude/feat-blur-text-d1f390`); find them with
  `git branch | grep feat` before merging. Parallel workers both edit `DemoRegistry.kt` → expect a
  trivial merge conflict.
- zsh gotcha: `git show "$B:path"` is mangled by zsh modifiers (`$B:t`); use `"${B}:path"`.

**Known issues**
- BlurText allocates a `BlurEffect` per blurred piece per frame while the radius changes (minor).
- BlurText Android < 12 fallback and 3D tilt need a visual check by the owner.
