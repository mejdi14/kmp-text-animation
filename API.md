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

### TextMorph
Shared characters glide to their new positions when `text` changes; others fade in/out (Motion Primitives Text Morph).
```kotlin
TextMorph(
    text, modifier, style,
    transition = TextMorphTransition.Spring(),   // stiffness 280, damping 18, mass 0.3; or Tween(300, MotionEaseInOut)
    enter = TextMorphVariant.Hidden, exit = TextMorphVariant.Hidden,  // alpha/scale/offsetX/offsetY
    ignoreCase = true, animateInitial = false,
    granularity = null /* auto: Character, or Word for complex scripts */, renderMode = SegmentRenderMode.Auto,
    softWrap = true, maxLines = Int.MAX_VALUE, speed = 1f, controller = null, onFinished = null,
)
// var word by remember { mutableStateOf("Hello") }; TextMorph(word)
```

### TextPressure
Per-letter weight/width/italic/alpha respond to pointer distance on a variable font (React Bits Text Pressure).
```kotlin
TextPressure(
    text, modifier, style, font = VariableFont.RobotoFlex,
    width = true, weight = true, italic = true, alpha = false, flex = true, stroke = false, scale = false,
    radius = 0.5f, smoothing = 0.5f, idle = TextPressureIdle.None /* Wave, Pulse */, idleSpeed = 1f,
    clickRipple = true, textColor = Color.Unspecified, pressureColor = Color.Unspecified,
    strokeColor = Color(0xFFFF0000), strokeWidth = 3.dp, minFontSize = 24.sp, uppercase = true,
    pointerInteraction = PointerInteraction.Pointer, controller = null,
)
// TextPressure("Compressa!", modifier = Modifier.fillMaxWidth().height(200.dp))
```
`VariableFont(name, resource, weight, width, italic, slant, opticalSize)` (+ `VariationAxisRange`) is public
so callers can supply their own variable font; `VariableFont.RobotoFlex` is bundled.

### TextScramble
Random glyphs shuffle every tick and resolve left-to-right into the text (Motion Primitives Text Scramble).
```kotlin
TextScramble(
    text, modifier, style, durationMillis = 800, tickMillis = 40,
    characterSet = TextCharsets.Alphanumeric, seed = null /* Int for reproducible output */,
    trigger = AnimationTrigger.OnAppear, speed = 1f, controller = null, onFinished = null,
)
// TextScramble("Hello world", trigger = AnimationTrigger.OnVisible(once = false))
```

### TrueFocus
One word is sharp inside an animated frame; the others are blurred (React Bits True Focus).
```kotlin
TrueFocus(
    text, modifier, style, separator = " ", manualMode = false,
    frameStyle = TrueFocusFrameStyle.Brackets /* Box, Underline, Highlight */,
    order = TrueFocusOrder.Sequential /* Random */, easing = TrueFocusEasing.Smooth /* Spring */,
    blurAmount = 5.dp, focusScale = 1f, borderColor = Color(0xFF008000), highlightColor = Color.Unspecified,
    glowColor = Color.Transparent, animationDurationMillis = 500, pauseBetweenMillis = 1000,
    cornerSize = 16.dp, borderWidth = 3.dp, unfocusedOpacity = 1f,
    pointerInteraction = PointerInteraction.Pointer, renderMode = SegmentRenderMode.Auto,
    speed = 1f, controller = null, onFocusChanged = null /* (index, word) -> Unit */,
)
// TrueFocus("True Focus", manualMode = true, glowColor = Color(0x9900FF00))
```
