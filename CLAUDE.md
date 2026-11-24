# CLAUDE.md — TextMotion (Compose Multiplatform text animation library)

**Every session (coordinator, worker, integration, subagent) reads this file first.** Then read
`HANDOFF.md`. Read other docs only when your task needs them. These files plus Git are the project
memory — keep them accurate and concise.

## 1. Mission
Build **TextMotion** (`:textmotion`, package `io.github.mejdi14.textmotion`), a production-quality,
reusable Compose Multiplatform library that natively recreates 20 web text animations on
**Android, iOS, Desktop (JVM) and Web (JS + Wasm)**, matching the originals' visuals, behaviour,
timing, smoothness and quality as closely as possible. It is a library, not hardcoded demos.
The existing app modules (`shared`, `androidApp`, `desktopApp`, `webApp`, `iosApp`) host the
**showcase**; keep their architecture working.

The 20 effects (composable name → reference):
| # | Composable | Reference |
|---|---|---|
| 1 | `BlurText` | reactbits.dev/text-animations/blur-text |
| 2 | `TextShimmer` | motion-primitives.com/docs/text-shimmer |
| 3 | `TextMorph` | motion-primitives.com/docs/text-morph |
| 4 | `TextPressure` | reactbits.dev/text-animations/text-pressure |
| 5 | `TextScramble` | motion-primitives.com/docs/text-scramble |
| 6 | `TrueFocus` | reactbits.dev/text-animations/true-focus |
| 7 | `SplitText` | reactbits.dev/text-animations/split-text |
| 8 | `AuroraText` | magicui.design/docs/components/aurora-text |
| 9 | `VariableProximity` | reactbits.dev/text-animations/variable-proximity |
| 10 | `RotatingText` | reactbits.dev/text-animations/rotating-text |
| 11 | `FuzzyText` | reactbits.dev/text-animations/fuzzy-text |
| 12 | `ScrollReveal` | reactbits.dev/text-animations/scroll-reveal |
| 13 | `TextRoll` | motion-primitives.com/docs/text-roll |
| 14 | `GradientText` | reactbits.dev/text-animations/gradient-text |
| 15 | `TextLoop` | motion-primitives.com/docs/text-loop |
| 16 | `TextShimmerWave` | motion-primitives.com/docs/text-shimmer-wave |
| 17 | `ScrollFloat` | reactbits.dev/text-animations/scroll-float |
| 18 | `GlitchText` | reactbits.dev/text-animations/glitch-text |
| 19 | `DecryptedText` | reactbits.dev/text-animations/decrypted-text |
| 20 | `TextEffect` | motion-primitives.com/docs/text-effect |

Exact reference behaviour (props, defaults, keyframes, easings, formulas) is in
`docs/reference-specs/reactbits.md` and `docs/reference-specs/motion-primitives-magicui.md`.
Read only your component's section. If something is missing, research the original source online
(GitHub: DavidHDev/react-bits, ibelick/motion-primitives, magicuidesign/magicui). Never copy
JS/TSX code verbatim; reimplement the behaviour in Kotlin.

## 2. Hard rules (non-negotiable)
1. **Native only.** Kotlin + Compose Multiplatform. NO WebViews, embedded HTML/React, JS bridges as
   substitutes for rendering, Lottie, videos, GIFs or image sequences. Every effect works with real,
   dynamically provided text.
2. **Never downgrade** a complex effect to a basic fade because it is hard. If a technique is
   unavailable on a platform, implement the closest alternative; never silently disable an effect.
   Document unavoidable limitations in `PLATFORM_SUPPORT.md`.
3. **No visual/runtime testing — by anyone.** Do NOT launch the Android emulator, iOS simulator,
   desktop app or web app; no browser automation, screenshots, recordings or visual regression.
   Opening the *original reference websites* for research is allowed. Allowed verification: Gradle
   compilation, static analysis, code review, dependency checks, unit tests of math/non-visual
   logic, shader compile checks that do not launch the app. Never claim an effect was visually
   verified.
4. **Full autonomy.** Never ask the user questions or for approval; decide, document, continue. If a
   feature is blocked, document it in `FEATURES.md`/`HANDOFF.md` and move on.
5. **Never publish** to Maven Central, GitHub Packages or any remote artifact repository.
   `publishToMavenLocal` is fine. Pushing commits to `origin` (github.com/mejdi14/kmp-text-animation,
   account `mejdi14`) is expected.
6. **Git discipline.** Commit every meaningful change immediately with conventional commits
   (`feat:`, `fix:`, `perf:`, `refactor:`, `docs:`, `chore:`, `test:`). Small focused commits.
   End messages with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
   Concurrent work happens on separate branches/worktrees; never overwrite another agent's work
   without reviewing it.
7. **Token efficiency.** Load only the files you need (never the whole codebase). Short focused
   sessions, concise reports, reuse infrastructure, decisions go into docs.

## 3. Library architecture (summary — details in ARCHITECTURE.md)
- Module `textmotion/`, `explicitApi()`, depends on compose runtime/foundation/ui/resources only
  (no Material). Targets: android (KMP library plugin), iosArm64, iosSimulatorArm64, jvm, js, wasmJs.
- Source sets: `commonMain` (almost everything), `skikoMain` (iOS+JVM+JS+Wasm: Skia APIs),
  `androidMain`, plus per-platform `iosMain/jvmMain/jsMain/wasmJsMain` for small actuals.
- Public API in package `io.github.mejdi14.textmotion` (one file per effect, e.g. `BlurText.kt`).
  Internals in `io.github.mejdi14.textmotion.internal` (shared) and
  `io.github.mejdi14.textmotion.internal.<effect>` (effect-private helpers).
- **Shared infrastructure (reuse it, don't duplicate):**
  - `AnimatedTextLayout(...)` — base composable: measures once per input change, sizes like
    `BasicText`, sets text semantics, gives you a cached `SegmentedTextLayout` in the draw phase.
  - `SegmentedTextLayout` / `TextSegment` / `DrawScope.drawSegment(...)` — grapheme/word/line
    segmentation with geometry; `SegmentRenderMode.Clip` preserves Arabic/Indic shaping.
  - `rememberTimeline(...)`, `Repeat.progressAt`, `Repeat.totalMillis`, `segmentProgress`,
    `TextAnimationController.resolveProgress` — frame clock (read in draw phase only).
  - `Stagger`, `TextEasing` (CSS/GSAP/Motion curves), `AnimationTrigger`, `Repeat`,
    `TextAnimationController`, `PointerInteraction`, `ScrollRange`, `TextGranularity`,
    `LocalTextMotionStyle`, `TextCharsets`.
  - `PointerTracker` + `Modifier.trackPointer` + `PointerInteraction.resolve` (auto mode for touch).
  - `VisibilityState` + `Modifier.trackVisibility`, `AnimationTrigger.isActive/restartKey`.
  - `ViewportPosition` + `Modifier.trackViewportPosition` → `progress(ScrollRange)`.
  - `RuntimeShaderEffect` (AGSL on Android 13+, SkSL elsewhere) + `PlatformCapabilities`.
  - `Transform3D` (CSS rotateX/rotateY + perspective as a projective matrix), `GlyphCache`,
    `MathUtil` (lerp, smoothstep, hash/value noise).
- Adding shared infra is allowed when justified; keep it backward compatible and mention it in your
  report so the coordinator documents it in `ARCHITECTURE.md`.

## 4. Public API conventions (every effect)
```kotlin
@Composable
public fun BlurText(
    text: String,                                   // real dynamic text
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current, // font family/size/weight/spacing/align/colour
    /* effect-specific parameters, defaults = the reference's defaults */
    durationMillis: Int = …, delayMillis: Int = 0, easing: Easing = …,
    stagger: Stagger = …, granularity: TextGranularity = …,
    trigger: AnimationTrigger = AnimationTrigger.OnVisible(),  // one-shot effects
    repeat: Repeat = Repeat.Once,                               // where meaningful
    speed: Float = 1f,
    controller: TextAnimationController? = null,               // play/pause/restart/seek
    onFinished: (() -> Unit)? = null,                           // completion callback
)
```
- Parameter order: `text`, `modifier`, `style`, effect params, then playback params. Include only
  parameters that make sense for the effect; sensible defaults so `BlurText("Hello")` just works.
- Colours/gradients as `Color`/`List<Color>`/`Brush`; durations as `Int` millis; easing as
  Compose `Easing`; intensity as `Float`. Pointer effects take `pointerInteraction:
  PointerInteraction`; scroll effects take `scrollRange: ScrollRange` and
  `scrollProgress: (() -> Float)? = null` (external progress overrides auto tracking).
- Continuous effects (shimmer, aurora, gradient, glitch, fuzzy…) pause automatically when not
  visible (`trackVisibility`) and when `controller` is paused.
- KDoc every public declaration. No platform types in the common public API.

## 5. Rendering & quality rules
- **Draw-phase animation:** read animated state only inside draw lambdas (`AnimatedTextLayout`
  `onDraw`, `drawWithCache`, `graphicsLayer {}`) so frames don't recompose or relayout.
- Never measure text per frame (use `SegmentedTextLayout`, `GlyphCache`). No per-frame allocations
  in hot loops where avoidable; reuse `Transform3D`, paths, arrays. Compile shaders once
  (`remember`), update uniforms per frame. No hundreds of composables — custom drawing instead.
- Stop clocks when finished/invisible/disposed (`rememberTimeline` does this when used properly).
- Typography: support any `FontFamily`, weights, sizes, letter spacing, multiline, alignment,
  wrapping, Unicode, emoji/grapheme clusters, RTL, Arabic shaping, mixed direction. Never assume one
  char = one glyph; use `TextGranularity` + `SegmentRenderMode.Auto`. Preserve semantics (done by
  `AnimatedTextLayout`).
- Platform rendering: blur via `RenderEffect` needs Android 12+; runtime shaders need Android 13+.
  Always provide a fallback path (e.g. multi-pass/offset drawing, canvas slicing) for older
  Android; check `PlatformCapabilities`. Web uses Skia (skiko) like iOS/Desktop.
- Pointer effects: hover on Desktop/Web, touch-drag on Android/iOS, `PointerInteraction` auto mode
  for devices without a persistent pointer. Don't consume events (must work in scroll containers).
- Scroll effects: work in `Column(verticalScroll)` and `LazyColumn` via
  `trackViewportPosition`, and accept external `scrollProgress`.
- 60 fps target; never claim measured FPS without measurement.

## 6. Showcase
In `shared/src/commonMain/kotlin/com/mejdi14/kmp_text_animation/showcase/`:
`DemoRegistry.kt` (one line per effect — replace ONLY your own placeholder line),
`demos/<Name>Demo.kt` (your demo: preview + screen with editable text, duration/speed/colour/
intensity controls where relevant, replay & pause via `PlaybackControls`, interactive/scroll demos,
Kotlin usage `code` string), reusable controls in `controls/`. Demos must use the public API only.

## 7. Orchestration protocol (distributed, no master coordinator)
**Active mechanism (since 2026-11-24): scheduled task `textmotion-coordinator`** (hourly, owner
approved, Auto mode; prompt at `~/.claude/scheduled-tasks/textmotion-coordinator/SKILL.md`). Each run
is a fresh coordinator: it takes `.orchestration/lock` (gitignored; stale after 8 h), skips if
main is dirty or a `feat-<A>`/`feat-<B>` worktree was modified in the last 3 h, then runs ONE group
(the `Next:` line) with two **Agent-tool subagent workers** (`isolation: "worktree"`, branch renamed
to `feat-<kebab>-sched`), reviews, merges, compiles, documents, pushes and releases the lock. No
cards and no session starts are needed. After group 10 it does the Final Integration, then sets
`Next: DONE`. A session-based coordinator (like C03) must hold the lock and delete it when done.

**Background limitation:** the desktop app only lets a session start other sessions in a turn that
came from a human; automated turns can only propose cards. The steps below still apply to a
coordinator started manually as a session (its workers may be sessions or subagents).

| Coordinator | Feature A | Feature B |
|---|---|---|
| 01 | Blur Text | Text Shimmer |
| 02 | Text Morph | Text Pressure |
| 03 | Text Scramble | True Focus |
| 04 | Split Text | Aurora Text |
| 05 | Variable Proximity | Rotating Text |
| 06 | Fuzzy Text | Scroll Reveal |
| 07 | Text Roll | Gradient Text |
| 08 | Text Loop | Text Shimmer Wave |
| 09 | Scroll Float | Glitch Text |
| 10 | Decrypted Text | Text Effect |



**Coordinator NN** (fresh session, main checkout, branch `main`):
1. Read `CLAUDE.md` and `HANDOFF.md` (the `Next:` line names your group). Make sure `main` is
   clean; commit stray work first.
2. **In your first turn**, start two worker sessions in parallel with `start_session`
   (ToolSearch `select:mcp__ccd_session__start_session`): `initiation: "user_asked"`,
   `context: "fresh"`, `use_worktree: true`, `wake_when_done: true`,
   `branch_name: "feat-<effect-kebab>"`, model/effort from §8. Use the worker brief in §9 plus the
   group notes in HANDOFF.md. Never start more than two workers.
   If `start_session` is refused, use Agent-tool subagents instead (`isolation: "worktree"`,
   `run_in_background: true`) with the same brief.
3. When a worker reports back, review its branch: `git branch | grep feat`, then
   `git diff main...<branch> --stat`, then read the key files and check §2/§4/§5. Send fixes back to
   the same worker with SendMessage. Merge with `git merge --no-ff refs/heads/<branch>`; conflicts in
   `DemoRegistry.kt` are normal. Compile all targets (§10), fix, commit.
4. Once both features are merged: update `FEATURES.md`, `API.md`, `PLATFORM_SUPPORT.md`,
   `ARCHITECTURE.md` (new infra) and `REFERENCES.md`. Rewrite `HANDOFF.md`: set `Next:` to the next
   group (or `FINAL INTEGRATION` after group 10) and add notes for the next workers. Commit, then
   `git push origin main`.
5. (Session-based coordinators only, when the scheduled task is disabled.) Propose the next
   coordinator as an `own_initiative` card. Title: "Coordinator NN+1: A + B".
   Prompt: "You are Coordinator NN+1 of TextMotion: features A and B. Read CLAUDE.md and follow §7."
   Add a short `background`. After group 10, propose "TextMotion Final Integration" instead (§9).
   End with a summary of at most 5 lines whose first line is `GROUP NN COMPLETE`.
- Groups may be reordered for technical dependencies; never more than two features per coordinator.
- If a worker fails, retry once with a fresh worker. If it is still blocked, mark the feature ⛔ in
  `FEATURES.md` with the reason and continue. Never ask the user questions.

## 8. Model / effort selection
- Coordinators: Opus, effort `medium`.
- Workers — Opus `high` (hard: shaders, complex glyph work, variable fonts, morphing, 3D):
  TextMorph, TextPressure, VariableProximity, FuzzyText, GlitchText, AuroraText, BlurText,
  TextShimmerWave, TextRoll.
- Workers — Sonnet (`claude-sonnet-5-5`), effort `high` (straightforward): TextShimmer,
  TextScramble, TrueFocus, SplitText, RotatingText, ScrollReveal, GradientText, TextLoop,
  ScrollFloat, DecryptedText, TextEffect.
- Inside any session, delegate routine sub-work (docs, demo screens, gradle fixes) to cheaper
  subagents (`model: "sonnet"` or `"haiku"`).

## 9. Session brief templates
**Worker brief** (fill `<…>`):
> You implement `<Composable>` (<reference URL>) for TextMotion. First read `CLAUDE.md` (all) and
> `HANDOFF.md`, then only: the `<Name>` section of `docs/reference-specs/<file>.md`, the public API
> files `textmotion/src/commonMain/kotlin/io/github/mejdi14/textmotion/*.kt`, and the infra files you
> need from `.../internal/`. You are in your own git worktree/branch. Deliver:
> (1) `textmotion/src/commonMain/kotlin/io/github/mejdi14/textmotion/<Composable>.kt` (+ effect
> internals under `internal/<effect>/`, platform actuals if needed) faithfully reproducing the
> reference (all props/defaults/timing/easing), following §4/§5, with fallbacks for platforms lacking
> a capability; (2) unit tests for non-visual logic in `textmotion/src/commonTest`; (3) showcase demo
> `shared/.../showcase/demos/<Name>Demo.kt` and replace only your line in `DemoRegistry.kt`;
> (4) `docs/features/<effect-kebab>.md` (≤60 lines: API summary, rendering technique, platform
> differences/limitations, deviations from reference, what is unverified); (5) compile all targets
> (§10) and run `:textmotion:jvmTest`; (6) commit in small conventional commits on your branch (do not
> merge, do not push). Do NOT edit root docs (coordinator does). No visual testing. Final reply ≤15
> lines: branch name, files, new shared infra, compile results per target, limitations.

**Coordinator brief**: "You are Coordinator NN of TextMotion: features <A> and <B>. Read `CLAUDE.md` and follow §7." Background: one paragraph on the project and the HANDOFF state.

**Final Integration brief** (max two coordination groups' worth of fixes per integration session;
start more fresh integration sessions if needed): full compile of all targets, `publishToMavenLocal`
check, verify every effect has public API docs + showcase demo, library independent of the app,
finalize all docs, write `README.md` sections (integration into another CMP project; running the
showcase on Android/iOS/Web/Desktop; compatibility matrix for all 20; implemented vs compiled vs
unverified; known limitations), write `FINAL_REPORT.md`, commit, push.

## 10. Build & verification commands
```bash
./gradlew :textmotion:jvmTest
./gradlew :textmotion:compileAndroidMain :textmotion:compileKotlinJvm :textmotion:compileKotlinJs :textmotion:compileKotlinWasmJs :textmotion:compileKotlinIosSimulatorArm64 :textmotion:compileKotlinIosArm64
./gradlew :shared:compileAndroidMain :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlin :androidApp:assembleDebug
./gradlew :textmotion:publishToMavenLocal
```
Environment: macOS, JDK 26 (Gradle toolchain JDK 21), Xcode 26.6, Kotlin 2.4.21, CMP 1.12.2,
AGP 9.1.1, Gradle 9.5.1. Use `--console=plain -q` and filter `^w:` warnings to save tokens.
Run long builds in the background.

## 11. Documentation files
`CLAUDE.md` (this), `ARCHITECTURE.md`, `FEATURES.md`, `REFERENCES.md`, `PLATFORM_SUPPORT.md`,
`API.md`, `HANDOFF.md`, per-feature notes in `docs/features/`, reference specs in
`docs/reference-specs/`. Update them when your work changes what they say.
