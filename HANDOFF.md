# HANDOFF

**State (2026-11-25, after Coordinator 06):** foundation + 12/20 effects on `main`.
- ✅ `BlurText`, ✅ `TextShimmer` (C01), ✅ `TextMorph`, ✅ `TextPressure` (C02), ✅ `TextScramble`,
  ✅ `TrueFocus` (C03), ✅ `SplitText`, ✅ `AuroraText` (C04), ✅ `VariableProximity`,
  ✅ `RotatingText` (C05), ✅ `FuzzyText`, ✅ `ScrollReveal` (C06) merged, with demos in the showcase
  and notes in `docs/features/`.
- All targets compile (library, shared, desktopApp, androidApp assembleDebug); `:textmotion:jvmTest`
  green (includes a real SkSL compile test via test-only `compose.desktop.currentOs`). Nothing
  visually verified (project rule).
- C06: FuzzyText is the first `RuntimeShaderEffect` user (AGSL/SkSL single source + ImageBitmap
  slice fallback); ScrollReveal reuses `SegmentBlurRenderer` + `trackViewportPosition`. See ARCHITECTURE.md.
- Chaining runs as the scheduled task "textmotion-coordinator" (every 5 min, lock-gated; CLAUDE.md §7).

**Next:** Group 07 — TextRoll + GradientText (CLAUDE.md §7).

**Group 07 worker notes:** TextRoll (Opus high): letters roll vertically (3D rotateX / translate)
one after another (motion-primitives `TextRoll`: `duration`, `getEnterDelay`/`getExitDelay`,
`transition`, `variants`, `onAnimationComplete`); reuse `Transform3D`, `SegmentedTextLayout`
(+ `buildFromRanges`), `Stagger`; per-grapheme draw-phase transforms, no per-letter composables.
GradientText (Sonnet): animated multi-colour gradient across glyphs (`colors`, `animationSpeed`,
`showBorder`; check current react-bits source for newer props like direction/yoyo/pauseOnHover);
reuse AuroraText's glyph-mask pattern (`saveLayer` + `SrcAtop`) and its cached `Brush`
(`internal/auroratext/AuroraBrushCache.kt`); pause when invisible / controller paused.

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
- FuzzyText AGSL on a real Android 13+ device, its < 13 fallback and colour split; ScrollReveal scroll
  tracking/smoothing, Rise clip and Flip pivot need an owner check.
- `splittext.emPx`/`estimateUnitCount` are used by ScrollReveal too; consider moving to shared internals.
