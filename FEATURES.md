# Features — status

Legend: ⬜ not started · 🟡 in progress · ✅ implemented & compiled (all targets) · ⚠️ implemented with limitations · ⛔ blocked.
"Compiled" = Gradle compilation of every target succeeded. **No effect has been visually verified** (visual testing is deliberately out of scope; the owner reviews visuals).

| # | Effect | Group | Status | Branch / notes |
|---|---|---|---|---|
| 1 | BlurText | C01 | ✅ | merged from `feat-blur-text`; all 5 effects, custom keyframes, loop, hover/click replay; see `docs/features/blur-text.md` |
| 2 | TextShimmer | C01 | ✅ | merged from `feat-text-shimmer`; see `docs/features/text-shimmer.md` |
| 3 | TextMorph | C02 | ✅ | merged from `feat-text-morph`; char+occurrence keys, spring/tween glide, enter/exit fade, per-word morph for complex scripts; see `docs/features/text-morph.md` |
| 4 | TextPressure | C02 | ✅ | merged from `feat-text-pressure`; all reference props + idle/ripple extras; created variable-font infra (`internal/font/`, bundled Roboto Flex); see `docs/features/text-pressure.md` |
| 5 | TextScramble | C03 | ✅ | merged from `feat-text-scramble`; all reference props, deterministic seeded schedule (`seed`), stable width; see `docs/features/text-scramble.md` |
| 6 | TrueFocus | C03 | ✅ | merged from `feat-true-focus`; auto/manual (hover/touch) modes, 4 frame styles, smooth/spring easing; extracted shared `SegmentBlurRenderer`; see `docs/features/true-focus.md` |
| 7 | SplitText | C04 | ⬜ | |
| 8 | AuroraText | C04 | ⬜ | |
| 9 | VariableProximity | C05 | ⬜ | |
| 10 | RotatingText | C05 | ⬜ | |
| 11 | FuzzyText | C06 | ⬜ | |
| 12 | ScrollReveal | C06 | ⬜ | |
| 13 | TextRoll | C07 | ⬜ | |
| 14 | GradientText | C07 | ⬜ | |
| 15 | TextLoop | C08 | ⬜ | |
| 16 | TextShimmerWave | C08 | ⬜ | |
| 17 | ScrollFloat | C09 | ⬜ | |
| 18 | GlitchText | C09 | ⬜ | |
| 19 | DecryptedText | C10 | ⬜ | |
| 20 | TextEffect | C10 | ⬜ | |

## Infrastructure
✅ Shared infrastructure (see ARCHITECTURE.md) — compiled on Android, iOS (arm64 sim), JVM, JS, Wasm; 9 unit tests pass on JVM.
✅ Showcase skeleton (gallery, demo screen, controls, placeholders for all 20) — compiled.
