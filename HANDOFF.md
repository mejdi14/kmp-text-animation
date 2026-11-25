# HANDOFF

**State (2026-11-25, after Coordinator 05):** foundation + 10/20 effects on `main`.
- ✅ `BlurText`, ✅ `TextShimmer` (C01), ✅ `TextMorph`, ✅ `TextPressure` (C02), ✅ `TextScramble`,
  ✅ `TrueFocus` (C03), ✅ `SplitText`, ✅ `AuroraText` (C04), ✅ `VariableProximity`,
  ✅ `RotatingText` (C05) merged, with demos in the showcase and notes in `docs/features/`.
- All targets compile (library, shared, desktopApp, androidApp assembleDebug); `:textmotion:jvmTest`
  green. Nothing visually verified (project rule).
- C05 added `SegmentedTextLayout.buildFromRanges` (custom-range segments); VariableProximity added
  public `ProximityArea`/`Modifier.proximityArea`. See ARCHITECTURE.md.
- Chaining runs as the scheduled task "textmotion-coordinator" (every 5 min, lock-gated; CLAUDE.md §7).

**Next:** Group 06 — FuzzyText + ScrollReveal (CLAUDE.md §7).

**Group 06 worker notes:** FuzzyText (Opus high): per-row horizontal displacement noise of the text
raster (reference draws text to an offscreen canvas and re-blits each scanline with random offset
`fuzzRange`, `baseIntensity`/`hoverIntensity`, `enableHover`, `fps`); use a runtime shader
(`RuntimeShaderEffect`, AGSL 13+/SkSL) with a canvas row-slicing fallback for Android < 13 — never
drop to a plain fade; pause when invisible. ScrollReveal (Sonnet): per-word opacity/blur reveal plus
container rotation driven by scroll (`baseOpacity`, `baseRotation`, `blurStrength`,
`enableBlur`, scroll start/end); use `trackViewportPosition` + `ScrollRange`, accept external
`scrollProgress`, reuse `SegmentBlurRenderer` (Android < 12 fallback); demo in a `verticalScroll` column.

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
- Worktrees need `local.properties` (gitignored, holds `sdk.dir`) copied from the main checkout for
  Android builds.
- zsh gotcha: `git show "$B:path"` is mangled by zsh modifiers (`$B:t`); use `"${B}:path"`.

**Known issues**
- `SegmentBlurRenderer` (BlurText/TrueFocus) allocates a `BlurEffect` per blurred piece per frame
  only while its radius changes (minor).
- BlurText Android < 12 fallback and 3D tilt need a visual check by the owner.
- TrueFocus glow, spring feel, multi-line wrapping and TextScramble glyph alignment need an owner check.
