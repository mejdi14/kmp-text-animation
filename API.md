# API — TextMotion

```kotlin
// build.gradle.kts (after ./gradlew :textmotion:publishToMavenLocal, with mavenLocal() in repositories)
commonMain.dependencies { implementation("io.github.mejdi14:textmotion:0.1.0-SNAPSHOT") }
```

## Conventions
Every effect is one composable in `io.github.mejdi14.textmotion`:
`Effect(text, modifier, style, <effect params>, <playback params>)`. Defaults match the original
web component, so `BlurText("Hello")` works with no configuration.

## Shared types
| Type | Purpose |
|---|---|
| `LocalTextMotionStyle` | Default `TextStyle` (Foundation-only library). Provide your theme's style once. |
| `TextAnimationController` / `rememberTextAnimationController(autoPlay)` | `play()`, `pause()`, `toggle()`, `restart()`, `seekTo(progress)`, `speed`, `isPlaying`, `isFinished`. |
| `AnimationTrigger` | `OnAppear`, `OnVisible(minFractionVisible, once)`, `Manual`. |
| `Repeat` | `Once`, `Forever`, `times(n, mode, delayBetweenMillis)`. |
| `Stagger(eachMillis, from, seed)` | `StaggerFrom.Start/End/Center/Edges/Random`. |
| `TextEasing` | CSS (`Ease`, `EaseOut`…), GSAP (`powerOut(n)`, `backOut()`, `elasticOut()`), `cubicBezier()`. |
| `TextGranularity` | `Character` (grapheme clusters), `Word`, `Line`. |
| `SegmentRenderMode` | `Auto`, `Clip` (keeps Arabic/Indic shaping), `Isolated`. |
| `PointerInteraction` | `Pointer`, `Auto(periodMillis)`, `PointerWithAutoFallback(idleMillis, periodMillis)`, `None`. |
| `ScrollRange` | GSAP-like start/end anchors: `FullPass`, `EnterToCenter`, custom. |
| `TextCharsets` | Character pools for scramble/decrypt effects. |

```kotlin
val controller = rememberTextAnimationController()
BlurText(
    text = "Isn't this so cool?!",
    style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    controller = controller,
    onFinished = { println("done") },
)
Button(onClick = { controller.restart() }) { Text("Replay") }
```

## Effects
(Coordinators add one section per effect: signature, parameters, example. Full notes in `docs/features/`.)

### BlurText
Pieces (words/graphemes/lines) blur-fade in with a staggered spring motion (React Bits Blur Text).
```kotlin
BlurText(
    text, modifier, style,
    effect = BlurTextEffect.Blur,            // Zoom, Tilt, Focus, Drift
    direction = BlurTextDirection.Top,       // Bottom, Left, Right
    blur = 10.dp, distance = 50.dp,
    fromColor = Color.Unspecified, focusOnHover = false,
    animationFrom = null, animationTo = null, easing = null,   // custom BlurTextKeyframe steps
    replayOn = BlurTextReplay.None,          // Hover, Click
    stepDurationMillis = 350, delayMillis = 0,
    stagger = BlurTextDefaults.Stagger,      // 200 ms, StaggerFrom.Start
    granularity = TextGranularity.Word, renderMode = SegmentRenderMode.Auto,
    trigger = AnimationTrigger.OnVisible(0.1f), repeat = Repeat.Once,   // BlurTextDefaults.Loop
    speed = 1f, controller = null, onFinished = null,
)
// BlurText("Isn't this so cool?!", granularity = TextGranularity.Character, effect = BlurTextEffect.Tilt)
```

### TextShimmer
A highlight band sweeps left→right over the glyphs forever (Motion Primitives Text Shimmer).
```kotlin
TextShimmer(
    text, modifier, style,
    durationMillis = 2000, spread = 2f,       // band half-width = text.length * spread dp
    baseColor = zinc-400 / zinc-500 (theme), highlightColor = Black / White (theme),
    easing = LinearEasing, speed = 1f, controller = null,
)
// TextShimmer("Generating code...", durationMillis = 1000)
```
