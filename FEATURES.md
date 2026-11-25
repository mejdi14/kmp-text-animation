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
| 7 | SplitText | C04 | ✅ | merged from `feat-split-text-sched`; 7 effect presets (rise/fade/blur/flip/pop/skew/wave), custom from/to keyframes, GSAP `distribute` stagger, mask, accent flash, hover lift/wave springs, hover/click replay, loop; see `docs/features/split-text.md` |
| 8 | AuroraText | C04 | ✅ | merged from `feat-aurora-text-sched`; 135° multi-colour gradient clipped to glyphs (`saveLayer` + `SrcAtop`), reference keyframes for position/rotate/scale, pauses when invisible; see `docs/features/aurora-text.md` |
| 9 | VariableProximity | C05 | ✅ | merged from `feat-variable-proximity-sched`; per-letter variable-font axes (wght/wdth/opsz/ital/slnt) driven by pointer distance (linear/exponential/gaussian falloff, smoothing), optional Oklab colour mix/scale/lift/idle wave, `ProximityArea` container tracking; reuses `internal/font/`; see `docs/features/variable-proximity.md` |
| 10 | RotatingText | C05 | ✅ | merged from `feat-rotating-text-sched`; staggered spring exit-then-enter per char/word/line (Slide/Flip/Blur/Scale/Drop), animated container size, highlight pill, `RotatingTextState` next/previous/jumpTo/reset; see `docs/features/rotating-text.md` |
| 11 | FuzzyText | C06 | ✅ | merged from `feat-fuzzy-text-sched`; per-slice random displacement of the text raster via `RuntimeShaderEffect` (AGSL 13+/SkSL) with the reference canvas slice-blit fallback on Android < 13; hover/click/always triggers, glitch mode, click burst, intro, gradient, RGB colour split; see `docs/features/fuzzy-text.md` |
| 12 | ScrollReveal | C06 | ✅ | merged from `feat-scroll-reveal-sched`; scroll-scrubbed per-word opacity/blur reveal + block rotation (Fade/Rise/Flip/Scale, Word/Character/Line), Enter mode, smoothing, external `scrollProgress`; see `docs/features/scroll-reveal.md` |
| 13 | TextRoll | C07 | ✅ | merged from `feat-text-roll-sched`; two stacked per-grapheme layers (enter pivot 50%/25%, exit 50%/100%) with `Transform3D` rotateX + perspective and backface culling, index-delay lambdas, `Roll`/`BlurRoll`/`Slide` variants + custom keyframes; see `docs/features/text-roll.md` |
| 14 | GradientText | C07 | ✅ | merged from `feat-gradient-text-sched`; Linear/Flow/Conic animated gradients clipped to glyphs (`SrcAtop`), angle/direction, scale, yoyo, glow, animated pill border, pauseOnHover, followPointer spring; see `docs/features/gradient-text.md` |
| 15 | TextLoop | C08 | ⬜ | |
| 16 | TextShimmerWave | C08 | ⬜ | |
| 17 | ScrollFloat | C09 | ⬜ | |
| 18 | GlitchText | C09 | ⬜ | |
| 19 | DecryptedText | C10 | ⬜ | |
| 20 | TextEffect | C10 | ⬜ | |

## Infrastructure
✅ Shared infrastructure (see ARCHITECTURE.md) — compiled on Android, iOS (arm64 sim), JVM, JS, Wasm; 9 unit tests pass on JVM.
✅ Showcase skeleton (gallery, demo screen, controls, placeholders for all 20) — compiled.
