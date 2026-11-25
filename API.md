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

### SplitText
Characters, words or lines animate in with a GSAP-style stagger (React Bits Split Text).
```kotlin
SplitText(
    text, modifier, style,
    effect = SplitTextEffect.Rise /* Fade, Blur, Flip, Pop, Skew, Wave */,
    granularity = TextGranularity.Character, animationFrom = null, animationTo = null /* SplitTextKeyframe */,
    mask = false, clipOverflow = null, accentColor = Color.Unspecified,
    hoverEffect = SplitTextHoverEffect.None /* Lift, Wave */, replayOn = SplitTextReplay.None /* Hover, Click */,
    durationMillis = 1250, delayMillis = 0, easing = null /* per-effect default */,
    stagger = SplitTextDefaults.Stagger /* 50 ms each, from start */, renderMode = SegmentRenderMode.Auto,
    trigger = AnimationTrigger.OnVisible(minFractionVisible = 0.1f), repeat = Repeat.Once,
    speed = 1f, controller = null, onFinished = null,
)
// SplitText("Hello, you!", effect = SplitTextEffect.Flip, hoverEffect = SplitTextHoverEffect.Wave)
```

### AuroraText
A drifting multi-colour aurora gradient flows through the glyphs (Magic UI Aurora Text).
```kotlin
AuroraText(
    text, modifier, style, colors = AuroraTextDefaults.Colors /* #FF0080 #7928CA #0070F3 #38BDF8 */,
    durationMillis = 10_000, angleDegrees = 135f, motionIntensity = 1f /* 0 = no rotate/scale sway */,
    easing = TextEasing.EaseInOut, speed = 1f, controller = null,
)
// AuroraText("Ship faster", speed = 2f)
```

### VariableProximity
Letters morph between two variable-font settings as the pointer approaches (React Bits Variable Proximity).
```kotlin
VariableProximity(
    text, modifier, style, font = VariableFont.RobotoFlex,
    fromFontVariationSettings = "'wght' 400, 'opsz' 9", toFontVariationSettings = "'wght' 1000, 'opsz' 40",
    radius = 50.dp, falloff = ProximityFalloff.Linear /* Exponential, Gaussian */, smoothing = 0.2f,
    fromColor = Color.Unspecified, toColor = Color.Unspecified, scale = 1f, lift = 0f /* em */,
    idleWave = false, area = null /* rememberProximityArea() + Modifier.proximityArea(area) */,
    pointerInteraction = PointerInteraction.Pointer, controller = null,
)
// val area = rememberProximityArea(); Box(Modifier.proximityArea(area)) { VariableProximity("Hover me", area = area) }
```

### RotatingText
Cycles through phrases with staggered spring exit/enter per unit (React Bits Rotating Text).
```kotlin
RotatingText(
    texts, modifier, style, effect = RotatingTextEffect.Slide /* Flip, Blur, Scale, Drop */,
    direction = RotatingTextDirection.Up, splitBy = RotatingTextSplit.Characters,
    rotationIntervalMillis = 2000, staggerMillis = 0, staggerFrom = RotatingTextStaggerFrom.First,
    loop = true, auto = true, pauseOnHover = false, blur = 0.dp,
    highlight = false, highlightColor = Color(0xFFA855F7), highlightTextColor = Color.Unspecified,
    transition = null, animateInitial = false, renderMode = SegmentRenderMode.Auto,
    state = null /* rememberRotatingTextState(): next/previous/jumpTo/reset */,
    speed = 1f, controller = null, onNext = null,
)
// RotatingText(listOf("React", "Compose", "Kotlin"), staggerMillis = 25)
```

### FuzzyText
Scanline-displaced "fuzzy" text that intensifies on hover (React Bits Fuzzy Text).
```kotlin
FuzzyText(
    text, modifier, style, baseIntensity = 0.18f, hoverIntensity = 0.5f, fuzzRange = 30.dp,
    direction = FuzzyDirection.Horizontal /* Vertical, Both */, sliceSize = 1.dp, fps = 60,
    enableHover = true, trigger = FuzzyTrigger.Hover /* Click, Always */,
    transitionDurationMillis = 0, clickEffect = false,
    glitchMode = false, glitchIntervalMillis = 2000, glitchDurationMillis = 200,
    gradient = null, colorSplit = 0f, intro = false, introDurationMillis = 1400,
    pointerInteraction = PointerInteraction.Pointer, speed = 1f, controller = null,
)
// FuzzyText("404", hoverIntensity = 0.6f)
```

### ScrollReveal
Words fade/unblur and the block straightens as it scrolls through the viewport (React Bits Scroll Reveal).
```kotlin
ScrollReveal(
    text, modifier, style, effect = ScrollRevealEffect.Fade /* Rise, Flip, Scale */,
    granularity = TextGranularity.Word, enableBlur = true, baseOpacity = 0.1f, baseRotation = 3f,
    blurStrength = 4.dp, baseOffset = 0.dp, stagger = 0.05f, highlightColor = Color.Unspecified,
    mode = ScrollRevealMode.Scrub /* Enter */, scrollRange = ScrollRevealDefaults.WordRange,
    rotationRange = ScrollRevealDefaults.RotationRange, scrollProgress = null, smoothingMillis = 0,
    durationMillis = 800, renderMode = SegmentRenderMode.Auto,
    trigger = AnimationTrigger.OnVisible(minFractionVisible = 0.2f), speed = 1f,
    controller = null, onFinished = null,
)
// Column(Modifier.verticalScroll(rememberScrollState())) { ScrollReveal("When does a man die?") }
```

### TextRoll
Letters roll vertically one after another: two stacked 3D copies per grapheme (motion-primitives Text Roll).
```kotlin
TextRoll(
    text, modifier, style, variants = TextRollVariants.Roll /* BlurRoll, Slide, custom */,
    enterDelayMillis = { i -> i * 100 }, exitDelayMillis = { i -> i * 100 + 200 },
    clipToBounds = false, perspective = 10000.dp, granularity = TextGranularity.Character,
    renderMode = SegmentRenderMode.Auto, durationMillis = 500, delayMillis = 0,
    easing = TextEasing.MotionEaseIn, trigger = AnimationTrigger.OnVisible(), repeat = Repeat.Once,
    speed = 1f, controller = null, onFinished = null,
)
// TextRoll("tom is in the big house", variants = TextRollVariants.Slide, clipToBounds = true,
//          enterDelayMillis = TextRollDefaults.staggered(50, 50))
```
`TextRollVariants(enter, exit)` of `TextRollVariant(initial, animate, origin)` with
`TextRollKeyframe(rotationX, rotationY, x, y, scale, opacity, blur)`.

### GradientText
Animated multi-colour gradient clipped to the glyphs, optional animated pill border (React Bits Gradient Text).
```kotlin
GradientText(
    text, modifier, style, colors = GradientTextDefaults.Colors, cycleDurationMillis = 8_000,
    variant = GradientTextVariant.Linear /* Flow, Conic */, angle = 90f,
    direction = null /* Horizontal, Vertical, Diagonal */, gradientScale = 3f, yoyo = true,
    glow = 0f, showBorder = false, borderWidth = 1.5.dp, pauseOnHover = false,
    followPointer = false, speed = 1f, controller = null,
)
// GradientText("Add a splash of color!", showBorder = true)
```
