# HANDOFF

**State (2026-11-24, bootstrap session):** foundation done on `main`.
- `:textmotion` library module with shared infrastructure (see ARCHITECTURE.md), compiled for
  Android, iOS sim arm64, JVM, JS, Wasm; `:textmotion:jvmTest` green (9 tests).
- Showcase skeleton in `shared/.../showcase/` (gallery, demo screen, controls, 20 placeholders in
  `DemoRegistry.kt`). Compiled on all targets.
- Reference specs for all 20 components in `docs/reference-specs/`.
- Docs: CLAUDE.md (rules + orchestration protocol), ARCHITECTURE, FEATURES, REFERENCES (⚠️ React Bits
  Commons Clause note), PLATFORM_SUPPORT, API.

**Next:** Coordinator 01 — BlurText + TextShimmer (follow CLAUDE.md §7).

**Notes for workers**
- Showcase controls (package `...showcase.controls`): `DemoStage`, `ControlPanel`, `TextControl`,
  `SliderControl`, `IntSliderControl`, `DurationControl`, `ColorControl`, `ColorsControl`,
  `SwitchControl`, `ChoiceControl`, `PlaybackControls(controller)`, `CodeBlock(code)`.
- `ShowcaseColors` (in `ShowcaseApp.kt`) holds the showcase palette (dark background).
- Text in demos should be light-coloured (dark showcase background).
- Variable-font infra does not exist yet: C02 (TextPressure) creates it under
  `internal/font/`, C05 (VariableProximity) reuses it.

**Known issues:** none yet.
