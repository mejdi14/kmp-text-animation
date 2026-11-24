# HANDOFF

**State (2026-11-24, after Coordinator 02):** foundation + 4/20 effects on `main`.
- ✅ `BlurText`, ✅ `TextShimmer` (C01), ✅ `TextMorph`, ✅ `TextPressure` (C02) merged, with demos in
  the showcase and notes in `docs/features/`.
- All targets compile (library, shared, desktopApp, androidApp assembleDebug); `:textmotion:jvmTest`
  green. Nothing visually verified (project rule).
- C02 added shared infra (see ARCHITECTURE.md "Decisions added by feature work"):
  `AnimatedTextLayout(onLayoutChanged)`, `PointerTracker.pressCount`, and the **variable-font infra**
  (`VariableFont`/`VariationAxisRange` public; `internal/font/`: `FontVariationKey`,
  `VariationQuantizer`, `rememberVariableFontFamilies`, `VariableGlyphCache`, bundled Roboto Flex).
- Session-start note: Coordinator 02 *could* start its worker sessions with `start_session`
  (`user_asked`) in its first turn, even though it was itself started by another session.

**Next:** Group 03 — TextScramble + TrueFocus (CLAUDE.md §7).

**Group 03 worker notes:** TextScramble (Sonnet): random chars from `TextCharsets` swap per frame
then resolve (reference speed/duration/characterSet/trigger props); measure glyphs via `GlyphCache`,
never per frame; keep layout width stable (reference uses fixed text width). TrueFocus (Sonnet):
words blurred except the focused one, an animated corner-bracket frame slides between words
(manual vs hover mode, blurAmount, borderColor, glowColor, animationDuration, pauseBetweenAnimations);
reuse the per-piece blur pattern from `internal/blurtext/BlurTextRenderer.kt` (Android < 12
fallback) — promote it to `internal/` if cleanly reusable, and report it.

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
- BlurText allocates a `BlurEffect` per blurred piece per frame while the radius changes (minor).
- BlurText Android < 12 fallback and 3D tilt need a visual check by the owner.
