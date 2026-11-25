# HANDOFF

**State (2026-11-25, after Coordinator 04):** foundation + 8/20 effects on `main`.
- ✅ `BlurText`, ✅ `TextShimmer` (C01), ✅ `TextMorph`, ✅ `TextPressure` (C02), ✅ `TextScramble`,
  ✅ `TrueFocus` (C03), ✅ `SplitText`, ✅ `AuroraText` (C04) merged, with demos in the showcase and
  notes in `docs/features/`.
- All targets compile (library, shared, desktopApp, androidApp assembleDebug); `:textmotion:jvmTest`
  green. Nothing visually verified (project rule).
- C04 added no shared infra; reusable patterns noted in ARCHITECTURE.md (GSAP `distribute` stagger,
  spring hover, cached gradient brush).
- Chaining runs as the scheduled task "textmotion-coordinator" (every 5 min, lock-gated; CLAUDE.md §7).

**Next:** Group 05 — VariableProximity + RotatingText (CLAUDE.md §7).

**Group 05 worker notes:** VariableProximity (Opus high): pointer-distance → per-letter font
variation (`fromFontVariationSettings` → `toFontVariationSettings`, `radius`, falloff linear/exponential/
gaussian); MUST reuse `internal/font/` (`rememberVariableFontFamilies`, `VariationQuantizer`,
`VariableGlyphCache`) and `PointerTracker`/`PointerInteraction` like TextPressure; Android < 8 fallback
as TextPressure. RotatingText (Sonnet): cycles `texts` every `rotationInterval`, per-character staggered
spring enter/exit (y 100% → 0 → -120%), `staggerFrom`, `splitBy`, `loop`, `auto`, controller
next/previous/jumpTo if practical; animate container width between phrases; reuse SplitText's
`distribute` stagger helper (`internal/splittext/SplitTextMotion.kt`) — promote to shared infra if needed.

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
