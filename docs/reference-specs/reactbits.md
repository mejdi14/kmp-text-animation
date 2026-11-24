# React Bits text animations - technical reference spec

Research basis: raw source fetched from `https://raw.githubusercontent.com/DavidHDev/react-bits/main/` (main branch, files last touched around 2026-10-15; these are the *current* rewritten versions, which differ from older React Bits releases - e.g. GlitchText no longer uses CSS keyframes, VariableProximity no longer uses the `motion` library, etc.). Paths:

| Component | JSX source | CSS |
|---|---|---|
| BlurText | `src/content/TextAnimations/BlurText/BlurText.jsx` | none (inline styles) |
| TextPressure | `.../TextPressure/TextPressure.jsx` | inline `<style>` |
| TrueFocus | `.../TrueFocus/TrueFocus.jsx` | `TrueFocus.css` |
| SplitText | `.../SplitText/SplitText.jsx` | none |
| VariableProximity | `.../VariableProximity/VariableProximity.jsx` | `VariableProximity.css` |
| RotatingText | `.../RotatingText/RotatingText.jsx` | `RotatingText.css` |
| FuzzyText | `.../FuzzyText/FuzzyText.jsx` | none (canvas) |
| ScrollReveal | `.../ScrollReveal/ScrollReveal.jsx` | `ScrollReveal.css` |
| GradientText | `.../GradientText/GradientText.jsx` | `GradientText.css` |
| ScrollFloat | `.../ScrollFloat/ScrollFloat.jsx` | `ScrollFloat.css` |
| GlitchText | `.../GlitchText/GlitchText.jsx` | `GlitchText.css` |
| DecryptedText | `.../DecryptedText/DecryptedText.jsx` | none |

TypeScript variants exist at `src/ts-default/TextAnimations/<Name>/<Name>.tsx` (same logic). All 12 components found. A Tailwind variant was not needed.

## License

- Repo: https://github.com/DavidHDev/react-bits, file `LICENSE.md`. Name: **"MIT + Commons Clause License Condition v1.0"** (README badge: "MIT + Commons Clause").
- Copyright line: **"Copyright (c) 2026 David Haz"** (maintainer David Haz / DavidHDev). `package.json` carries no license field.
- Summary: MIT-style permission to use, copy, modify, merge, publish and distribute the Software **as part of an application, website or product**, provided the copyright notice is retained. Commons Clause restriction: commercial use is fine **as long as you do not sell, sublicense or redistribute the components themselves - alone, in a bundle, or as a ported version.** (Tools/sounds clause: Background Studio, Texture Lab, etc. may not be sold or redistributed or hosted as a service; exports may be used commercially but not resold as packs.) Standard "AS IS" no-warranty clause.
- **Implication for this project:** a Kotlin Compose port of these components is a "ported version" of the components. Using the behavior inside an app is allowed, but publishing/selling/redistributing the port as a standalone library/component pack is restricted by the Commons Clause. Treat this spec as reference for a clean-room behavioral re-implementation; keep attribution/notice; get legal review / permission from the author before distributing the Kotlin port as a library. (Not legal advice.)
- Two components note external origin: TextPressure header says "Component ported from https://codepen.io/JuanFuentes/full/rgXKGQ".

## Conventions used in this spec

- `ease(t)` bezier notation `cubic-bezier(x1,y1,x2,y2)` is the CSS form. Motion (framer-motion) arrays `[a,b,c,d]` are the same.
- Motion `spring` with `visualDuration d` and `bounce b` (Motion library behavior, from memory of its source): `omega = 2*PI/(d*1.2)`; `stiffness = omega^2`; `dampingRatio = clamp(1 - b, 0.05, 1)`; `damping = 2*dampingRatio*sqrt(stiffness)`; mass 1. Plain `{stiffness, damping, mass}` springs are the standard damped harmonic oscillator `a = (k*(target-x) - c*v)/m`.
- GSAP easing definitions: `powerN.out(t) = 1-(1-t)^(N+1)` (power1=quad, power2=cubic, power3=quart); `powerN.in(t)=t^(N+1)`; `power2.inOut` = cubic in-out; `expo.out(t) = t>=1 ? 1 : 1-2^(-10t)`; `back.out(s)(t) = 1 + (s+1)(t-1)^3 + s(t-1)^2`; `back.inOut(s)` = back in-out with overshoot `s` (GSAP: for t<.5: `0.5*(2t)^2*((s*1.525+1)*2t - s*1.525)`, else mirrored `0.5*((2t-2)^2*((s*1.525+1)*(2t-2)+s*1.525)+2)`).
- Almost every component honors `prefers-reduced-motion` (static final state). Recommended Compose analogue: respect system "remove animations".
- Accessibility pattern: visually-hidden full text (sr-only) + `aria-hidden` split pieces. In Compose: `semantics { contentDescription = text }` on the container, clear semantics on pieces.

---

## 1. BlurText

Source: https://raw.githubusercontent.com/DavidHDev/react-bits/main/src/content/TextAnimations/BlurText/BlurText.jsx (uses `motion/react`).

### Props (defaults)
`text=''`, `delay=200` (ms, stagger between pieces), `className`, `animateBy='words'` ('words'|'letters' - anything other than 'words' splits by grapheme/char), `direction='top'` ('top'|'bottom'|'left'|'right'), `threshold=0.1`, `rootMargin='0px'` (IntersectionObserver), `animationFrom` / `animationTo` (custom keyframe snapshot objects; `animationTo` is an *array* of steps), `easing` (custom ease), `onAnimationComplete`, `stepDuration=0.35` (s per keyframe step), `blur=10` (px), `distance=50` (px), `effect='blur'` ('blur'|'zoom'|'tilt'|'focus'|'drift'), `staggerFrom='start'` ('start'|'end'|'center'|'random'), `trigger='view'` ('view'|'load'|'hover'|'click'), `loop=false`, `loopDelay=2` (s), `color`, `fromColor` (text tint at hidden state, transitions to `color`), `focusOnHover=false`.

### Splitting
`words = text.split(' ')`. animateBy 'words': one piece per word; every word except the last gets a trailing ` ` (nbsp) glued to it. Otherwise one piece per grapheme (`Array.from(word)`) and a separate nbsp piece between words. Each word is a `display:inline-flex` wrapper (so words wrap as units); the root is `display:flex; flex-wrap:wrap`. Pieces are `inline-block` with `will-change: transform, filter, opacity`.

### Default effect ('blur') keyframes
Axis/sign from direction: top -> (y,-1), bottom -> (y,+1), left -> (x,-1), right -> (x,+1). With `b=blur`, `d=distance`:
```
from : blur(b),     opacity 0,   axis = sign*d
step1: blur(b/2),   opacity 0.5, axis = -sign*d/10     (small overshoot)
step2: blur(0),     opacity 1,   axis = 0
times = [0, 0.5, 1]; total duration = stepDuration*(steps) = stepDuration*2 = 0.7s default
opacity/filter ease: 'linear'; translate uses spring {visualDuration: totalDuration*0.5, bounce: 0.35}
```
(i.e. default 'top': text drops from y=-50 with blur 10 to rest, with a slight overshoot downward of +5 px at the midpoint then settles.) Position keys (`x,y,scale,rotateX,rotateY,skewX`) use the spring; `filter` and `opacity` use the tween with `fx.fade` easing. Piece delay = `rank * delay/1000` s.

### Other effects (all: `settle` = fraction of totalDuration used as spring visualDuration; `bounce`; fade ease)
- `zoom`: from `blur(1.2b) op0 scale1.4 axis=sign*d*0.35`; mid `blur(0.2b) op0.9 scale0.97 axis=-sign*d*0.03`; end rest. times [0,.6,1], fade ease_out `[0.33,1,0.68,1]`, bounce .25, settle .5. exit: `blur(b) op0 scale .82 axis=-sign*d*.2`, exit ease `[0.55,0,0.9,0.45]`.
- `tilt` (3D): rotate axis = rotateX if y-axis else rotateY; `lean = (axis==y ? -sign : sign)`. from `blur(b) op0 rotate=lean*55 axis=sign*d*0.5 perspective 600`; mid `blur(.15b) op.95 rotate=-lean*8 axis=-sign*d*.04`; end rest. times [0,.6,1], ease_out, bounce .3, settle .8. transform-origin: x = (axis x ? (sign<0?0:1) : .5), y = (axis y ? (sign<0?0:1) : .5). exit `rotate=-lean*40 axis=-sign*d*.3`.
- `focus`: `soft = blur(1.4b) op0.32 scale1.12`; mid `blur(.1b) op.95 scale .995`; end rest. times [0,.72,1], ease in-out `[0.65,0,0.35,1]`, **no spring** (bounce null), settle 1. exit = soft, exit ease in-out.
- `drift`: `h = direction=='right' ? 1 : -1`, `v = axis=='y' ? sign : 0`. from `blur(b) op0 x=h*d*1.6 y=v*d*.3 skewX=h*28`; mid `blur(.15b) op.95 x=-h*d*.06 y=0 skewX=-h*7`; end rest. times [0,.62,1], ease_out, bounce .3, settle .55. exit `x=-h*d*1.2 y=-v*d*.2 skewX=h*20`.
- Default (blur) exit: `blur(b) op0 axis=-sign*d*0.6`. Default exit ease `[0.55,0,0.9,0.45]`.

Easings: EASE_OUT `[0.33,1,0.68,1]`, EASE_IN_OUT `[0.65,0,0.35,1]`, EASE_IN `[0.55,0,0.9,0.45]`, CSS_EASE (color/hover) `cubic-bezier(0.22,1,0.36,1)`.

### Custom animationFrom/animationTo
If supplied: keyframes = `[from, ...toSteps]`, `times[i] = i/(steps)`, duration = stepDuration*(stepCount-1), ease = `easing` or 'linear', no spring.

### Stagger ranks (`buildRanks`) - piece rank, delay = rank*delay ms
- start: rank = index; end: `count-1-index`; center: `|i - mid| - offset` with `mid=(count-1)/2`, `offset = (count even ? 0.5 : 0)`; random: seeded Fisher-Yates (seed = FNV-1a-ish hash of text: `seed=2166136261; seed=imul(seed ^ ch, 16777619)>>>0`; LCG `seed = (imul(seed,1664525)+1013904223)>>>0; r = seed/2^32`), deterministic per text.

### Phases / state machine
Phases: `from` -> `in` -> (settled/rest) -> `out` -> `in` ... 
- `trigger='view'`: start at `from`; IntersectionObserver(threshold, rootMargin) one-shot flips to `in`. `'load'`: start at `in`. `'hover'`/`'click'`: start at `rest` (fully visible, no intro) and replay on `pointerenter`/click, only if settled (replay = go to `out`, which when finished auto-transitions to `in`).
- `in` duration timer: `inTotal = (totalDuration * (custom ? 1 : max(1, settle*2.2)) + maxRank*stagger)*1000` ms, then `onAnimationComplete`, and if `loop` schedule `out` after `loopDelay`s.
- `out`: per-piece transition `duration = stepDuration*1.3`, ease = effect's exitEase, delay `rank * (stagger*0.4)`; timer `outTotal = (exitDuration + maxRank*exitStagger)*1000` then back to `in`.
- `fromColor`: while `from`/`out`, piece color = fromColor; CSS transition on color: `color {totalDuration*0.9}s cubic-bezier(0.22,1,0.36,1) {rank*stagger}s` (in), `color {exitDuration}s ... {rank*exitStagger}s` (out).
- `focusOnHover`: each word wrapper; hovering word w dims every other word: `filter: blur(dim px)`, `opacity .3`, where `dim = clamp(blur*0.3, 2, 6)`; undimmed word `blur(0) opacity 1`; transition `filter .45s, opacity .45s` with CSS_EASE.

---

## 2. TextPressure

Source: .../TextPressure/TextPressure.jsx (ported from codepen JuanFuentes rgXKGQ). Variable font: Roboto Flex (axes `wght 100..1000`, `wdth 25..151`, `ital`(slnt-like 0..1 as used) ). Pure DOM + rAF; no animation library.

### Props (defaults)
`text='Compressa'`, `fontFamily='Roboto Flex'`, `fontUrl=<Google Fonts Roboto Flex opsz,wdth,wght 8..144,25..151,100..1000>`, `width=true`, `weight=true`, `italic=true`, `alpha=false`, `flex=true` (letters spread with `justify-content: space-between`), `stroke=false`, `scale=false`, `radius=0.5` (fraction of title width), `smoothing=0.5`, `idle='none'` ('none'|'wave'|'pulse'), `idleSpeed=1`, `clickRipple=true`, `textColor='currentColor'`, `pressureColor=''`, `strokeColor='#FF0000'`, `strokeWidth=3`, `className`, `minFontSize=24`.

### Layout
Chars = `Array.from(text)`, each its own `inline-block` span. Title: uppercase, `line-height:1`, `font-weight:100`, `white-space:nowrap`, centered, width 100%, `transform: scale(1, scaleY)` origin `center top`. `fontSize = max(containerWidth / (charCount/2), minFontSize)` (recomputed on ResizeObserver). `scaleY = scale ? containerHeight/fontSize : 1` (stretch glyphs vertically to fill the container height).

### Per-frame algorithm (rAF, only runs while "animating" or recently changed; pauses when offscreen via IntersectionObserver)
```
elapsed = min(now-last, 64) ms
lag     = 1000 * smoothing^2
follow  = lag>1 ? 1 - exp(-elapsed/lag) : 1
mouse  += (cursor - mouse) * follow            // smoothed pointer (window mousemove / touch)

// idle engage (only if idle is wave|pulse and not reduced motion)
present = (cursor.seen && cursor inside container rect) ? 1 : 0   (when idle mode off: present=1)
engage += (present - engage) * (1 - exp(-elapsed/320))
idling  = idleMode != none && engage < 1;  if idling: clock += elapsed/1000 * idleSpeed

maxDist = max(titleWidth * radius, 1)
spread  = max(radius*2, 0.2)
for each char i with center c_i (viewport coords of span center):
    pressure = clamp01(1 - dist(mouse, c_i)/maxDist)          // LINEAR falloff
    if idling: pos = (c_i.x - title.left)/title.width
               rest = idlePressure(mode,pos,clock,spread)
               pressure = rest + (pressure - rest)*engage
    if ripples: swell = ripplePressure(...); pressure = 1 - (1-pressure)*(1-swell)   // screen-blend
    wdth = width  ? floor(5 + 200*pressure) : 100
    wght = weight ? floor(100 + 900*pressure) : 400
    ital = italic ? pressure.toFixed(2) : '0'
    alpha= alpha ? pressure.toFixed(2) : ''  -> element opacity
    tint = round((1-(1-pressure)^2)*100)
    color= pressureColor ? color-mix(in oklab, pressureColor tint%, textColor) : ''
    fontVariationSettings = "'wght' wght, 'wdth' wdth, 'ital' ital"
```
Note: wdth range sent is 5..205 (the font clamps to its 25..151 axis range).

### Idle patterns
- Constants: `WAVE_PERIOD=3.2`, `PULSE_PERIOD=2.8`, `PULSE_DEPTH=0.42`.
- wave: `crest = 0.5 + 0.5*cos(2PI*(pos/spread - clock/WAVE_PERIOD)); pressure = crest^2`.
- pulse: `lag = |pos - 0.5|*0.4; breath = 0.5 - 0.5*cos(2PI*(clock/PULSE_PERIOD - lag)); pressure = PULSE_DEPTH * smoothstep(breath)` where `smoothstep(b)=b^2*(3-2b)` (code: `PULSE_DEPTH * breath*breath*(3-2*breath)`).
- Idle only runs when cursor is not inside the container; entering blends back with the 320 ms time-constant.

### Click ripples
Constants `RIPPLE_LIFE=1.3 s`, `MAX_RIPPLES=6` (oldest dropped). On `pointerdown` in container push `{x,y,start}`. For char center `p`:
```
band   = max(span*0.14, 32)              // span = title width
for each ripple: age = (now-start)/1000; skip if age<=0 or >= 1.3
   offset = (dist(ripple,p) - age*span*1.25) / band      // ring front moves at 1.25*width per second
   skip if |offset| >= 1
   fade  = 1 - age/1.3
   swell = (1 - offset^2)^2 * fade^2 * min(1, age/0.06)
   total = 1 - (1-total)*(1-swell)
```
Ripple returns pressure that is screen-blended with the cursor pressure.

### Stroke mode
If `stroke`, each span gets `::after { content: attr(data-char); position:absolute; left:0;top:0; color:transparent; z-index:-1; -webkit-text-stroke: strokeWidth strokeColor }` (outline copy behind glyph that follows variation settings).

### Interaction
Window `mousemove`/touch tracks pointer anywhere on page (not just inside component); `mouseleave` of document / touchend marks cursor unseen. Initial mouse position = container center.

---

## 3. TrueFocus

Source: .../TrueFocus/TrueFocus.jsx + TrueFocus.css (motion/react for frame, CSS transitions for words).

### Props (defaults)
`sentence='True Focus'`, `separator=' '`, `manualMode=false` (hover selects word instead of timer), `focusStyle='brackets'` ('brackets'|'box'|'underline'|'highlight'), `order='sequential'` ('sequential'|'random'), `easing='smooth'` ('smooth'|'spring'), `blurAmount=5` px, `focusScale=1`, `borderColor='green'`, `highlightColor` (defaults to borderColor), `glowColor='transparent'`, `animationDuration=0.5` s, `pauseBetweenAnimations=1` s, `cornerSize=16` px, `borderWidth=3` px, `unfocusedOpacity=1`.

### Behavior
- `words = sentence.split(separator)` with blank words filtered. Container: flex, `gap:1em`, centered, wrap. Word: `font-size:3rem; font-weight:900; cursor:pointer`.
- Auto mode (unless manualMode or <2 words): `setInterval` every `(animationDuration + pauseBetweenAnimations)` s. sequential: `(i+1)%n`. random: pick uniformly among *other* words: `next=floor(rand*(n-1)); next>=current ? next+1 : next`.
- Manual mode: `onMouseEnter` of a word sets active index (no auto advance).
- Non-active words: `filter: blur(blurAmount px)`, `opacity: unfocusedOpacity`; active: blur 0, opacity 1; if `focusScale != 1` active word `transform: scale(focusScale)`. Transition string: `filter {d}s ease, opacity {d}s ease, transform {d}s <smooth: cubic-bezier(0.65,0,0.35,1) | spring: cubic-bezier(0.34,1.56,0.64,1)>`.
- Frame animation (motion): tween `{duration, ease:[0.65,0,0.35,1]}` or, for 'spring', `{type:'spring', visualDuration: duration, bounce: 0.38}`. First mount: shapes fade in (opacity 0->1) at their position.

### Geometry (relative to container; word rect from offsetLeft/Top/Width/Height; scaled rect `frame` around the word center when focusScale != 1)
`GAP = 10`.
```
frame.x = word.x - word.w*(focusScale-1)/2 ; frame.y similarly ; frame.w = word.w*focusScale ; frame.h = word.h*focusScale
corners (brackets; each corner is a cornerSize x cornerSize box):
  TL = (frame.x - GAP,                          frame.y - GAP)
  TR = (frame.x + frame.w + GAP - cornerSize,   frame.y - GAP)
  BL = (frame.x - GAP,                          frame.y + frame.h + GAP - cornerSize)
  BR = (frame.x + frame.w + GAP - cornerSize,   frame.y + frame.h + GAP - cornerSize)
box:       x = frame.x-GAP, y = frame.y-GAP, w = frame.w+2*GAP, h = frame.h+2*GAP
underline: x = frame.x, y = frame.y + frame.h + GAP*0.4, w = frame.w, h = borderWidth
highlight: x = frame.x - frame.h*0.14, y = frame.y + frame.h*0.04, w = frame.w + frame.h*0.28, h = frame.h*0.92
```
Positions/sizes all animate with the tween/spring above.

### Styling (CSS)
- Corner: border `borderWidth solid borderColor`, radius 3px, each corner omits two sides (TL: no right/bottom; TR: no left/bottom; BL: no right/top; BR: no left/top), `filter: drop-shadow(0 0 4px glowColor)`.
- Box: border `borderWidth solid borderColor`, radius 6px, drop-shadow glow. Underline: filled `borderColor`, radius `borderWidth/2`, glow. Highlight: radius 6px, fill `color-mix(in srgb, (highlightColor ?? borderColor) 30%, transparent)` (= 30% alpha).
- Reduced motion: duration 0.

---

## 4. SplitText

Source: .../SplitText/SplitText.jsx. Uses **GSAP** (`gsap`, `ScrollTrigger`, `SplitText` plugin, `@gsap/react`). Re-implement the DOM split as per-char/word/line layout items.

### Props (defaults)
`text`, `className=''`, `delay=50` (ms stagger `each`), `duration=1.25` (s), `ease` (override), `splitType='chars'` ('chars'|'words'|'lines' or combos like 'chars,words'), `from`, `to` (custom GSAP vars), `threshold=0.1`, `rootMargin='-100px'`, `textAlign='center'`, `tag='p'`, `staggerFrom='start'` (GSAP stagger `from`: start|end|center|edges|random|index), `mask=false` (clip each unit in an overflow-hidden mask), `effect='rise'` ('rise'|'fade'|'blur'|'flip'|'pop'|'skew'|'wave'), `trigger='view'` ('view'|'load'|'hover'|'click'), `loop=false`, `loopDelay=1.5` (s), `hoverEffect='none'` ('none'|'lift'|'wave'), `color`, `accentColor` (chars flash this color during reveal), `onLetterAnimationComplete`.

### Effects (from -> to, ease); `em = computed font-size px`
- rise (default): `{opacity 0, y 40}` -> `{opacity 1, y 0}`, `power3.out`.
- fade: opacity 0->1, `power2.inOut`.
- blur: `{opacity 0, blur(round(em*1.4)/10 px)}` -> `{opacity 1, blur(0)}`, `power2.out`.
- flip: `{opacity 0, rotationX -90, transformPerspective em*6, transformOrigin '50% 100%'}` -> `{opacity 1, rotationX 0}`, `power3.out`.
- pop: `{opacity 0, scale 0}` -> `{opacity 1, scale 1}`, `back.out(1.6)`.
- skew: `{origin '50% 100%', opacity 0, x -em*0.4, skewX -24}` -> `{opacity 1, x 0, skewX 0}`, `expo.out`.
- wave: `{opacity 0, y em*0.45}` -> `{opacity 1, y 0}`, custom ease `waveEase(t) = 1 - cos(t*PI*1.5) * (1-t)^1.2` (starts at 0, overshoots past 1 once, settles to 1).
- Custom `from`/`to` replace effect vars; default ease then `power3.out`; defaults `from={opacity:0,y:40}`, `to={opacity:1,y:0}`.
- Unit animated = chars if splitType includes 'chars', else words if includes 'words', else lines.

### Timeline
`gsap.timeline().fromTo(units, from, {...to, duration, ease, stagger: distribute({each: delay/1000, from: staggerFrom})})`. If `accentColor`: second tween starts at `duration*0.3`: color `accent -> element's computed color`, `duration*0.7`, `power1.inOut`, same stagger. Afterwards clear transform/filter/willChange props.
- ScrollTrigger start: `top {(1-threshold)*100}% {rootMargin}` where a negative margin `-100px` becomes `-=100px` (i.e. `'top 90%-=100px'`); `once:true`. `trigger:'load'` plays immediately.
- Exit (used by loop/hover/click replay): `exit = max(0.35, duration*0.55)`; tween `to -> from`, ease `power2.in`, same stagger (accent color eases `power1.in`). Cycle = conceal then reveal (`reveal` offset at `exit`). Loop: wait `loopDelay` s after completion, then cycle again. `trigger:'hover'`: replay on pointerenter once revealed; `'click'`: replay on click (cursor pointer).
- Parent style: `display:inline-block; overflow: hidden` when (no hoverEffect and (custom from/to or effect rise/fade)) else `visible`; hidden until fonts loaded + split done; reduced motion -> instantly final.

### Hover effects (pointer over the element after reveal; custom spring integrated per frame)
Constants `STIFFNESS=180`, `DAMPING=19`, `KICK=0.024`. Per char/mask node keeps `{y, v}` (px, px/s).
```
dt = clamp((now-last)/1000, 0.001, 0.032)
v += (STIFFNESS*(target - y) - DAMPING*v) * dt ; y += v*dt         // semi-implicit Euler
settle when |target-y|<0.02 and |v|<0.05 -> y=target, v=0
node.style.translate = "0 {y}px"
```
- `lift`: `radius = em*1.15; dx=(cx-px)/radius; dy=(cy-py)/(radius*1.4); target = -lift*exp(-(dx^2+dy^2)*0.5)` with `lift = em*0.2` (Gaussian bump; chars near pointer rise 0.2em). cx,cy are char centers relative to element box (measured at pointerenter, minus current spring offset).
- `wave`: on pointerenter schedule a velocity kick per char: `speed = em*9` px/s; `velocity = (em*0.16)/KICK`; char i gets kick at `now + |cx_i - px|/speed*1000` ms: `spring.v -= velocity` (impulse upward), spring target 0, so a ripple travels outward from the pointer and the char oscillates and settles.

---

## 5. VariableProximity

Source: .../VariableProximity/VariableProximity.jsx + .css (font: Roboto Flex `opsz,wght@8..144,100..1000`, class `.variable-proximity {font-family:'Roboto Flex', sans-serif}`). No animation library, rAF loop.

### Props (defaults)
`label=''`, `fromFontVariationSettings="'wght' 400, 'opsz' 9"`, `toFontVariationSettings="'wght' 1000, 'opsz' 40"`, `containerRef` (optional element whose bounds define the active area), `radius=50` px, `falloff='linear'` ('linear'|'exponential'|'gaussian'), `smoothing=0.2`, `fromColor`, `toColor`, `scale=1`, `lift=0` (em), `idleWave=false`, `fontSize`, `className`, `onClick`, `style`.

### Algorithm
- Axes: parse both settings strings into maps; for each axis in `from`, `toValue = to.get(axis) ?? fromValue`. Settings at level `s in [0,1]`: `'axis' = from + (to-from)*s` for every axis.
- Each letter is an inline-block span (`transform-origin: 50% 80%`). Words are `inline-block; nowrap` groups; line starts are detected by measuring word `top` (a word whose top > previous top+2 starts a new line -> a `<br>` is inserted and root set `white-space:nowrap`) so that per-letter variable-width changes don't reflow the paragraph.
- Pointer: window `mousemove`/`touchmove` set `pointer {x,y,active}`; mouse/touch leave sets inactive. Scroll (capture) re-measures.
- Target strength per letter (distance = Euclidean distance pointer -> letter rect center, px):
```
if distance >= radius: 0
norm = 1 - distance/radius
linear:      norm
exponential: norm^2
gaussian:    (exp(-2*(distance/radius)^2) - exp(-2)) / (1 - exp(-2))      // normalized to hit 0 at edge, 1 at center
```
  If `containerRef` is set and the pointer's distance to that rect >= radius, all targets are 0.
- Smoothing per frame: `delta = min(now-last, 64) ms; ease = smoothing>0 ? 1 - exp(-delta/(smoothing*400)) : 1; level += (target - level)*ease`; snap when `|target-level|<0.002`. Written `fontVariationSettings` = fromSettings string when level==0 else interpolated.
- Colors (if `fromColor`/`toColor`): `color: color-mix(in oklab, from (1-level)*100%, to)`; at level 0 = fromColor.
- Transform (if `scale!=1` or `lift!=0`): `translateY(-lift*level em) scale(1 + (scale-1)*level)`.

### idleWave
`WAVE_PERIOD=3.2 s`, `WAVE_PEAK=0.7`. Anchors = letter centers relative to the text bounding box; `span = max(220, textWidth*0.85)`. When no letter was near the pointer for >500 ms (and the element is visible and reduced-motion is off): `waveMix += (1 - waveMix)*(1 - exp(-delta/600))` (fades out symmetrically toward 0 when pointer returns).
```
phase = ((anchor.x + anchor.y*0.6)/span - time/WAVE_PERIOD) * 2PI
wave  = WAVE_PEAK * ((1 + cos(phase))/2)^2.2
target = max(pointerTarget, waveMix * wave)
```

---

## 6. RotatingText

Source: .../RotatingText/RotatingText.jsx + .css (motion/react; AnimatePresence; exposed imperative API `next/previous/jumpTo/reset`).

### Props (defaults)
`texts` (array), `transition` (overrides preset), `initial/animate/exit` (override preset states), `effect='slide'` ('slide'|'flip'|'blur'|'scale'|'drop'), `direction='up'` ('up'|'down'), `highlight=false` (pill background that fits width), `highlightColor='#a855f7'`, `animatePresenceMode='wait'`, `animatePresenceInitial=false`, `rotationInterval=2000` ms, `staggerDuration=0` (s per char), `staggerFrom='first'` ('first'|'last'|'center'|'random'|number), `loop=true`, `auto=true`, `pauseOnHover=false`, `blur=0` (px; effect 'blur' defaults to 10 if blur=0), `splitBy='characters'` ('characters'|'words'|'lines'|custom separator string), `onNext`, class-name hooks.

### Behavior
- Auto: `setTimeout(next, rotationInterval)` re-armed on every index change; paused while hovered if `pauseOnHover`. `next`: `i<last ? i+1 : (loop ? 0 : i)`; `previous` mirrors; `jumpTo` clamps.
- Split: characters -> words split by ' ' then graphemes via `Intl.Segmenter('en', grapheme)`; words -> each word one unit; lines -> split on '\n'; any other string -> split on it. A `text-rotate-space` (white-space:pre) is inserted between words.
- Stagger delay of unit index `i` (counted across the whole string): first `i*s`; last `(total-1-i)*s`; center `|floor(total/2) - i|*s`; random `shuffled[i]*s` (random permutation re-created per text); number n: `|n - i|*s`.
- Container `layout` animation (width change) spring `{damping:25, stiffness:300}`. AnimatePresence swaps by key=activeIndex with mode 'wait' (old text exits fully, then new enters).

### Effect presets (`up = direction != 'down'`); each unit animates independently
| effect | initial | animate | exit | enter transition | exit transition |
|---|---|---|---|---|---|
| slide | `y: up?'100%':'-100%', opacity 0` | `y 0, opacity 1` | `y: up?'-120%':'120%', opacity 0` | spring damping 25, stiffness 300 | same |
| flip | `rotateX: up?-90:90, opacity 0, perspective 480` | `rotateX 0, opacity 1` | `rotateX: up?90:-90, opacity 0` | tween 0.6 s ease `[0.34,1.4,0.64,1]` | tween 0.32 s ease `[0.5,0,0.75,0]` (delay = unit stagger delay) |
| blur | `y: up?'45%':'-45%', opacity 0` | `y 0, opacity 1` | `y: up?'-45%':'45%', opacity 0` | tween 0.55 s `[0.22,1,0.36,1]` | same |
| scale | `scale: up?0.35:1.7, opacity 0` | `scale 1, opacity 1` | `scale: up?1.7:0.35, opacity 0` | spring damping 17, stiffness 240 | same |
| drop | `y '-110%', opacity 0` | `y 0, opacity 1` | `y '110%', opacity 0` | spring damping 18, stiffness 320, mass 0.9 | tween 0.3 s `[0.55,0,1,0.45]` |

Flip units use `transform-origin: 50% 50% -0.45em; backface-visibility:hidden`. Blur param adds `filter: blur(Npx)` to initial and exit, `blur(0px)` to animate.
`slide`-style clipping: the `y: 100%` percentages are relative to each unit's own height; the container is not clipped (opacity fades cover it) except in highlight mode.

### Highlight mode
CSS: pill `padding: 0.1em 0.28em; border-radius: 0.16em; overflow:hidden; box-sizing:content-box; flex-wrap:nowrap; justify-content:center`, background `highlightColor`, text color auto-chosen by relative luminance: `L = 0.2126 R + 0.7152 G + 0.0722 B` on sRGB-linearized (`c<=0.04045 ? c/12.92 : ((c+0.055)/1.055)^2.4`); `L > 0.3` -> `#0b0b0c` else `#ffffff`. Width is animated via a spring `{damping:34, stiffness:320}` to `contentWidth + 1` (first measure snaps, reduced motion snaps); `layout` animation disabled in highlight mode.

---

## 7. FuzzyText

Source: .../FuzzyText/FuzzyText.jsx. **Canvas 2D**. Text is rasterized once to an offscreen canvas, then each frame redrawn in horizontal 1-px-ish slices, each slice shifted randomly in x.

### Props (defaults)
`children` (text), `fontSize='clamp(2rem, 8vw, 8rem)'` (number = px or CSS length), `fontWeight=900`, `fontFamily='inherit'`, `color='currentColor'`, `enableHover=true`, `baseIntensity=0.18`, `hoverIntensity=0.5`, `fuzzRange=30` (max shift px at intensity 1, before dpr), `fps=60`, `direction='horizontal'` ('horizontal'|'vertical'|'both'), `transitionDuration=0` (frames-ish smoothing; 0=instant), `clickEffect=false`, `glitchMode=false`, `glitchInterval=2000` ms, `glitchDuration=200` ms, `gradient=null` (array of >=2 colors, horizontal linear gradient across text width), `letterSpacing=0` px, `sliceSize=1` px, `trigger='hover'` ('hover'|'click'|'always'), `intro=false`, `introDuration=1400` ms, `colorSplit=0`, `className`.

### Build (per text/font/size/dpr change)
```
dpr = min(devicePixelRatio, 2)
metrics = measureText(text) at font `${fontWeight} ${fontPx}px ${family}`
width  = actualBoundingBoxLeft + actualBoundingBoxRight (or sum of per-char advances + letterSpacing when letterSpacing != 0)
height = ceil(ascent + descent)
splitPad = colorSplit>0 ? ceil(fuzzRange*0.5)+4 : 0
padX = fuzzRange + 20 + (direction=='vertical' ? 0 : splitPad)
padY = direction=='horizontal' ? 0 : fuzzRange + 10 + (direction=='vertical' ? splitPad : 0)
inset = 5
source canvas  = (textWidth + 2*inset) x textHeight   (x dpr)
visible canvas = (textWidth + 2*inset + 2*padX) x (textHeight + 2*padY)  (x dpr)
text drawn in source at x = inset + box.left (or per-char with advance+letterSpacing), baseline y = ascent
```
Ink color: if `color` is currentColor/inherit and no gradient, reads `getComputedStyle(canvas).color` every 250 ms and repaints source if it changed.

### Per-frame fuzz (frame throttled to `fps`: skip if `now - lastFrame < 1000/fps - 1`)
Target intensity:
```
base   = reducedMotion ? 0 : baseIntensity
active = trigger=='always' ? (reduced ? base : hoverIntensity)
       : (trigger!='click' && hovering && enableHover) ? hoverIntensity : base
goal   = (clicking || glitchActive) ? 1 : active
eased  = transitionDuration>0 ? eased + (goal-eased)*(1 - 0.05^(1/transitionDuration)) : goal   // snap when |diff|<0.002
```
`clicking` = true for 150 ms after click when `clickEffect`. `glitchActive` toggles on every `glitchInterval` ms for `glitchDuration` ms (timer pair; disabled when reduced motion).
Hover hit-test: pointer must be inside the *text box* (not the padding): `x in [padX+inset, padX+inset+textWidth]`, `y in [padY, padY+textHeight]` in canvas CSS coordinates. Touch/pen pointers un-hover on pointerup.

Burst (`trigger=='click'`, on click): `burstAt=now`. Envelope: `age<70ms ? 1 - (1-age/70)^2 : exp(-(age-70)/240)` (ends when below 0.004 after attack). `peak = min(2, 0.6 + hoverIntensity*1.4)`; `level = eased + (max(peak, eased) - eased)*envelope`.
Intro (if `intro`): `p = min(1, (now - start)/introDuration)`, `env=(1-p)^2.5`, `level += (2 - level)*env` (INTRO_PEAK=2), canvas opacity `p<0.3 ? 1-(1-p/0.3)^2 : 1`.
`coarse = 1 + 3*energy^2` (slice size multiplier during burst/intro energy), `step = max(1, round(max(1,sliceSize)*coarse*dpr))` device px.
`amp = intensity * fuzzRange * dpr`.

Draw (horizontal): clear; if `amp < 0.5` just blit source at `(padX*dpr, padY*dpr)`. Otherwise:
```
rows[k] = round((random()-0.5) * amp)        for each slice k (slice height = step source px)
for y in 0..sourceHeight step `step`: drawImage(source, 0,y, sw,h  ->  ox + rows[k] (+ wobble) , oy + y, sw,h)
```
So each horizontal band is displaced by a uniform random integer in `[-amp/2, +amp/2]`, re-randomized every frame (no smoothing between frames). `vertical`: same but per column (`cols[x/step]`, shift y). `both`: bands shifted in x into a scratch canvas, then columns of the scratch shifted in y with amplitude `amp*0.5` (`cols = round((rand-0.5)*amp*0.5)`).
Color split (`colorSplit>0`): `split = round(colorSplit*(2 + intensity*fuzzRange*0.18)*dpr)`; text rendered into 3 channel canvases (R,G,B isolated; for light inks channels are zero-filled -> blend `lighter` (additive), for dark inks 255-filled -> blend `multiply`; light if mean luminance >= 0.5). Channel 0 drawn shifted `-split`, channel 1 at 0, channel 2 at `+split`, with `jitter = colorSplit*amp*0.2` per-slice wobble on channels 0 and 2, all with the chosen composite op; this produces chromatic-aberration fringes.
Skips redraw if intensity==0 and already drawn at 0. IntersectionObserver pauses the loop offscreen.

---

## 8. ScrollReveal

Source: .../ScrollReveal/ScrollReveal.jsx + .css. **GSAP ScrollTrigger** drives one progress value `state.p in [0,1]`; per-piece painting is done manually in `paintPiece`.

### Props (defaults)
`children` (string), `scrollContainerRef`, `enableBlur=true`, `baseOpacity=0.1`, `baseRotation=3` (deg; whole block starts rotated, rotates to 0), `blurStrength=4` (px), `baseOffset=0` (px initial downward offset), `stagger=0.05`, `smoothing=0` (scrub seconds; 0 = `scrub:true` = instantly locked to scroll), `splitBy='words'` ('words'|'chars'|'lines'), `effect='fade'` ('fade'|'rise'|'flip'|'scale'), `mode='scrub'` ('scrub'|'enter'), `duration=0.8` (enter mode, s), `highlightColor=''`, `containerClassName`, `textClassName`, `rotationEnd='bottom bottom'`, `wordAnimationEnd='bottom bottom'`.

CSS: `h2.scroll-reveal {margin:20px 0}`, `.scroll-reveal-text {display:block; font-size: clamp(1.6rem, 4vw, 3rem); line-height:1.5; font-weight:600}`, words/pieces `inline-block`. Text split by `/(\s+)/` (whitespace preserved).

### Progress mapping
Constants `SCRUB_SPAN = 0.5` ("each piece takes half the total timeline"). `span = mode=='enter' ? max(0.05, duration) : 0.5`; `gap = max(0, stagger)`. For 'lines', `order[i]` = line index of word i (line detection via `offsetTop` change > 2 px), `count`=#lines; else `order[i]=i`, `count`=#pieces.
```
total = span + max(0, count-1)*gap
at    = p * total
t_i   = clamp01((at - order_i*gap)/span)             // per-piece local progress
```
(In scrub mode `total` can exceed 1 progress unit, but p still maps 0..1, so with stagger .05 and N words, piece N-1 starts at (N-1)*.05/total.)

### paintPiece(piece, t, look) 
Helpers: `smooth(x)=x^2(3-2x)`, `easeOut(x)=1-(1-x)^3`, `easeBack(x,o) = 1 + (o+1)(x-1)^3 + o(x-1)^2`, `glowAt(t)= t<=0||t>=1 ? 0 : t<.35 ? smooth(t/.35) : 1 - smooth((t-.35)/.65)`.
```
fade   : reveal = t;                fadeIn = t
rise   : reveal = easeOut(t); fadeIn = clamp01(reveal*1.25)
         if reveal<1: masked(words exist): translateY((1-reveal)*100%) translateY((1-reveal)*0.2em)   // word wrapper has clip-path inset(-1em -1em -0.15em -1em)
                      else: translateY((1-reveal)*0.8em)
flip   : reveal = easeBack(t,1.2); fadeIn = clamp01(t*2);  if t<1: perspective(6em) rotateX((1-reveal)*90deg), origin 50% 100%
scale  : reveal = easeBack(t,1.6); fadeIn = clamp01(t*1.6); if t<1: scale(0.35 + 0.65*reveal)
if highlightColor: fadeIn = max(fadeIn, clamp01(t/0.35))
settle = clamp01(reveal)
opacity   = t>=1 ? '' : baseOpacity + (1-baseOpacity)*fadeIn
translateY(px) = baseOffset*(1-settle)  (prepended to transform)
filter    = (blur && settle<1) ? blur(blurStrength*(1-settle) px) : none     (enableBlur ? blurStrength : 0)
color     = glow>0.002 ? color-mix(in srgb, highlightColor glow*100%, currentColor) : ''   glow=glowAt(t)  // flash color passing through
```
### Scroll triggers
- scrub mode: block rotation `fromTo(el, {transformOrigin:'0% 50%', rotate: baseRotation}, {rotate:0, ease:'none', scrollTrigger:{trigger:el, scroller, start:'top bottom', end:clamp(rotationEnd), scrub}})`. Progress: `gsap.to(state,{p:1, ease:'none', scrollTrigger:{trigger:el, scroller, start: () => \`top bottom-=${0.2*min(el.offsetHeight, viewportHeight)}\`, end: clamp(wordAnimationEnd), scrub}})`. `clamp(x)` = `clamp(x)` GSAP clamp so end never exceeds max scroll. `scrub = smoothing>0 ? smoothing : true`.
- enter mode: one-shot. Start position = `elTop - viewH + 0.2*min(elHeight, viewH)` clamped to `maxScroll-1`; on enter/leave/refresh-with-progress, play `gsap.to(state,{p:1, duration: total, ease:'none'})` and (if baseRotation) `rotate -> 0` over `min(total, 1.4)` s `power3.out`.
- reduced motion: nothing animated (final state). willChange only set when <=80 pieces.

---

## 9. GradientText

Source: .../GradientText/GradientText.jsx + .css. Pure CSS gradients re-generated per frame by JS; `background-clip:text; -webkit-text-fill-color: transparent`.

### Props (defaults)
`children`, `colors=['#5227FF','#FF9FFC','#B497CF']` (single color is duplicated), `animationSpeed=8` (seconds per cycle, min 0.2), `variant='linear'` ('linear'|'flow'|'conic'), `angle=90`, `direction` ('horizontal'->90 | 'vertical'->180 | 'diagonal'->135, overrides angle), `scale=3` (clamped 1..6; gradient period multiplier), `yoyo=true`, `glow=0` (0..1), `showBorder=false`, `borderWidth=1.5`, `pauseOnHover=false`, `followPointer=false`, `className`, `style`.

### Per-frame state
`time += dt` (dt clamped 0..0.05 s; frozen if reduced motion or (pauseOnHover and pointer inside)). `cycle = time/speed`. `pull` eases toward 1 if `followPointer && pointerInside` else 0: `pull += (target - pull)*(1 - exp(-dt*6))`. Pointer smoothing (spring, stiffness 160, critically damped: `damping = 2*sqrt(160)`): `v += ((x - px)*160 - v*2*sqrt(160))*dt; px += v*dt` (same for y; x,y = pointer position normalized 0..1 inside the element). Loop sleeps when not visible or when paused and settled.

### Variants (output sets CSS var `--gt-gradient` on the root)
- **linear** (default): `loop = [...colors, colors[0]]`, `period = 100*scale` (%).
  - yoyo: `swing = cycle % 2; t = swing<1 ? swing : 2-swing; offset = (0.5 - cos(PI*t)/2) * (period - 100)` (cosine ease ping-pong).
  - else: `offset = (cycle % 1) * period` (seamless loop).
  - `offset += (px - 0.5)*period*0.5*pull`.
  - `repeating-linear-gradient(angle deg, c_i (i/(n-1)*period - offset)% , ...)` over the `loop` list.
- **flow**: one radial blob per color: `spin = cycle*2PI; rate = 0.55 + ((i*0.618) % 1)*0.5; x = 50 + 44*sin(spin*rate + i*2.1); y = 50 + 40*sin(spin*(rate*0.83+0.21) + i*1.3)`; blob 0 pulled toward pointer: `x += (px*100 - x)*pull` (same y). Each: `radial-gradient(ellipse (22+12*scale)% (60+30*scale)% at x% y%, color 0%, transparent 100%)`; blobs stacked on top of base `linear-gradient(angle deg, ...colors)`.
- **conic**: `sway = yoyo ? sin(cycle*PI)*50 : cycle*360`; center `cx = 50 + (px*100-50)*pull`, `cy = 260 + (py*100-260)*pull` (center far below the text so rays sweep across it); `repeating-conic-gradient(from (sway-30)deg at cx% cy%, c_i (i/n * 60 * (3/scale))deg ... , c0 at end)` (colors list closed with c0).

### Border & glow
- `showBorder`: padding `0.35em 0.75em`, pill radius 999px, `::before` ring of width `borderWidth` painted with the same gradient (via mask-composite exclude of content-box vs full box).
- `glow>0`: a duplicate of the text positioned behind (`z-index:-1`) with the same gradient, `filter: blur(0.3em) saturate(1.4)`, `opacity: glow`.

---

## 10. ScrollFloat

Source: .../ScrollFloat/ScrollFloat.jsx + .css. GSAP + ScrollTrigger scrubbed timeline.

### Props (defaults)
`children` (string), `scrollContainerRef`, `containerClassName`, `textClassName`, `effect='float'` ('float'|'flip'|'scatter'|'blur'|'wave'), `splitBy='chars'` ('chars'|'words'), `direction='up'` (('down' => sign=-1)), `order='start'` ('start'|'end'|'center'|'edges'|'random'), `fromColor='currentColor'` (initial tint), `animationDuration=1`, `ease='back.inOut(2)'`, `scrollStart='center bottom+=50%'`, `scrollEnd='bottom bottom-=40%'`, `stagger=0.03`, `distance=120`, `stretch=2.3`, `smoothing=0`.

CSS: `.scroll-float {margin:20px 0; overflow:hidden}` (`--open`: `overflow:visible; overflow-x:clip` for non-float effects), text `display:inline-block; font-size: clamp(1.6rem, 8vw, 10rem); font-weight:900; text-align:center; line-height:1.5`, words `inline-block nowrap`, chars `inline-block`.

### Timeline
`gsap.timeline({scrollTrigger:{trigger:el, scroller, start:scrollStart, end:clamp(scrollEnd), scrub: smoothing>0 ? smoothing : true, invalidateOnRefresh:true}})`. Every tween placed at time 0 with `duration: animationDuration, stagger: index => delays[index]`. Sizes: `size = computed font-size px`, `reach = distance/120`, `sign = direction=='down' ? -1 : 1`, `squeeze = stretch^-0.43` (volume-preserving horizontal squash), `timing(p) = clamp01(parseEase(ease)(p))`, `edge(i) = middle>0 ? (i-middle)/middle : 0` with `middle=(n-1)/2`.
Stagger delays: `ranks` by order (start `i`; end `n-1-i`; center `|i-middle|`; edges `middle-|i-middle|`; random: sort indices by `random(i,9)` and assign rank = position); `delay_i = (rank_i - minRank)*max(0,stagger)` seconds. Deterministic hash random: `random(i,salt) = frac(sin((i+1)*12.9898 + salt*78.233)*43758.5453)`.

### Effects
- **float**: from `{opacity 0, yPercent 120*reach*sign, scaleY stretch, scaleX squeeze, origin: sign>0 ? '50% 0%' : '50% 100%'}` to `{opacity 1, yPercent 0, scaleY 1, scaleX 1}`, ease = `ease` (default back.inOut(2)). Container clips overflow, so chars rise from below the line stretched tall and snap to shape.
- **flip**: rotationX `-90*sign -> 0` (ease), `transformPerspective = size*4`, `transformOrigin '50% 50% (-size*0.55*reach)px'`, backface hidden; opacity 0->1 with ease `smoothstep(0.05, 0.55, timing(p))`.
- **scatter**: per char i: `x = ((random(i,1)*2-1)*1.3 - edge(i)*0.9)*size*reach`, `y = sign*(0.35 + random(i,2))*size*1.1*reach`, `rotation = (random(i,3)<.5 ? -1 : 1)*(40 + random(i,4)*120)` deg, `scale = 0.35 + random(i,5)*0.9`; all -> rest (x0,y0,rot0,scale1) with `ease`; opacity ease `smoothstep(0, 0.4, timing(p))`.
- **blur**: from `{opacity 0, y sign*size*0.3*reach, scale 1.45, blur(size*0.2 px)}` -> rest, ease.
- **wave**: `yPercent 120*reach*sign -> 0`, `scaleY stretch -> 1`, `scaleX squeeze -> 1` with ease `swell(p) = 1 - cos(t*PI*2.5)*(1-t)^2.6` (t=timing(p)); rotation `0 -> -16*sign` with ease `sway(p) = sin(t*PI*2.5)*(1-t)^2.6` (returns to 0, so it wobbles); opacity ease `clamp01(timing(p)*2.5)`; origin '50% 100%' (sign>0).
- **fromColor** (if not currentColor): CSS var `--scroll-float-mix` 1 -> 0 with ease `smoothstep(0.5,1,timing(p))`; char color = `color-mix(in oklab, fromColor calc(mix*100%), currentColor)`.
- `smoothstep(a,b,x) = k^2(3-2k), k=clamp01((x-a)/(b-a))`. Reduced motion: no animation.

---

## 11. GlitchText

Source: .../GlitchText/GlitchText.jsx + GlitchText.css. **Not CSS keyframes** in this version: a rAF loop writes CSS custom properties; two clipped/masked copies of the text provide the shifted slices.

### Props (defaults)
`children` (string/node), `speed=0.5`, `enableShadows=true`, `enableOnHover=false` (legacy; same as trigger 'hover'), `offset=10` (px), `shadowColors=['#ff0000','#00ffff']`, `className`, `mode='slices'` ('slices'|'rgb'|'scan'|'noise'), `intensity=0.5` (0..1), `skew=0` (deg), `flicker=false`, `trigger='always'` ('always'|'hover'|'click'), `burstDuration=0.6` (s, click burst), `color`, `fontSize`, `fontWeight`. (`color`, `fontSize` etc. set on the root; default CSS font-size `clamp(2rem,10vw,8rem)`, weight 900, `white-space:nowrap`.)

### Structure
Root contains `.glitch__text` (main layer, masked) + two absolutely positioned slice copies `--a` (top) and `--b` (below). Each copy has `clip-path: inset(top 0 bottom 0)` and `transform: translate3d(x,y,0) skewX(skew)`. The main text is *masked* so the bands that the slices cover are cut out of it: `mask = mask-a ∩ mask-b`, where mask-x = `linear-gradient(#000 top%, transparent top%, transparent (100-bottom)%, #000 (100-bottom)%)`... i.e. the band `[top%, 100-bottom%]` is transparent. (Slice b is also masked by mask-a so it does not overlap slice a.) Slice colors: with `enableShadows`, `text-shadow: (-x/2)px 0 shadowColor` (slice a uses shadowColors[0], b uses [1]); otherwise `none`.

### Timing
- `stepLength(speed) = 70*sqrt(max(0.05,speed)/0.5)` ms (= 70 ms at speed 0.5). A "step" re-rolls random state every `ms * (0.7 + rand*0.6)`.
- `power = max(clamp(intensity,0,1), burstLeft)`, `burstLeft = bursting ? 1 - (elapsed/burstDuration)*0.55 : 0`. `scale = 0.4 + 1.2*power`; `off = max(0, offset)`; `pace = max(0.05, speed)`; `clock` = accumulated seconds (dt capped 0.1).
- Each step: with probability `power^2*0.35` start a random **event** lasting `ms*(1 + floor(rand*3))` ms:
  `aTop=rand*80, aHeight=5+rand*22, bTop=rand*85, bHeight=3+rand*14, ax=±(1.3+rand*1.5), bx=±(1.1+rand*1.2), ay=(rand-.5)*0.5, by=(rand-.5)*0.5, skew=±(0.4+rand*0.6)`.
  Also jitter `[0.6+rand*0.8, 0.6+rand*0.8, rand*2-1, rand*2-1]`; flicker alpha: if `flicker` and `rand < 0.04+0.16*power` then `alpha = 0.45 + rand*0.35` else 1.
- Active only when visible and not reduced-motion and (always | hover while hovering | within burst window). When inactive all vars reset to rest (bands 0%/100%, offsets 0, shadows none, skew 0, alpha 1).
- Click burst: `burstEnd = now + max(0.1, burstDuration)*1000`, immediately spawns an event.

### Mode math
**slices** (the classic): the ping-pong keyframe table `SLICE_STOPS` (21 stops of `[topInset%, bottomInset%]`, linearly interpolated, evenly spaced over u in 0..1):
```
[20,50],[10,60],[15,55],[25,35],[30,40],[40,20],[10,60],[15,55],[25,35],[30,40],
[20,50],[10,60],[15,55],[25,35],[30,40],[40,20],[20,50],[10,60],[15,55],[25,35],[30,40]
```
(these stops resemble the classic `clip-path: inset(20% 0 50% 0)` ... `inset(30% 0 40% 0)` glitch keyframes; here they are sampled in JS.) `bandAt(u)`: `x=clamp01(u)*20; i=min(19,floor(x)); lerp stop i -> i+1`. Slice a: `bandAt(pingPong(clock, 3*pace))`, slice b: `bandAt(pingPong(clock, 2*pace))`, where `pingPong(time, period): p=(time/period)%2; p<1 ? 1-p : p-1`. Offsets: `ax = +off*scale`, `bx = -off*scale`, y 0. During an event: `a=[aTop, 100-aTop-aHeight]`, `b=[bTop, 100-bTop-bHeight]`, `ax = off*scale*ev.ax`, `bx = off*scale*ev.bx`, `ay = off*ev.ay`, `by = off*ev.by`, skew = `skew*ev.skew`.
**rgb**: slices are full-text (no clip, no mask) tinted with `shadowColors[0]` / `[1]`; layer opacity `alpha * split`, `split = enableShadows ? 0.9 : 0.4`; main text above (z-index 1) unmasked. `spread = off*(0.22 + 0.45*power)`; `ax = spread*jitter0`, `bx = -spread*jitter1`, `ay = off*0.06*jitter2`, `by = off*0.06*jitter3`; during event `ax = off*scale*|ev.ax|`, `bx = -off*scale*|ev.bx|`, `ay = off*ev.ay`, `by = off*ev.by`. Text-shadows disabled in rgb mode.
**scan**: a bright band sweeps vertically. `pass = 2.4*pace`; `cycle = pass*(1 + (1-power)*1.2)`; `local = clock % cycle`; `height = 9 + 9*power`. If `local < pass`: `dir = floor(clock/cycle)%2 ? -1 : 1`; `center = -height + (local/pass)*(100 + 2*height)`; `trail = center - height*1.5`; `thin = height*0.35`; `a = [center - height/2, 100-(center+height/2)]`, `b = [trail - thin/2, 100-(trail+thin/2)]`; `ax = off*scale*1.5*dir*(0.75 + 0.25*sin(clock*23))`, `bx = -ax*0.5`. Event overrides `b` band and `bx = off*scale*ev.bx*1.4`.
**noise**: text split into per-char cells (slices hidden). Glyph set `GLYPHS = '#%&@$?<>/\\=+*~^!'`. Each step each char swaps with probability `0.01 + 0.16*power^2` (0.35 if already swapped): replaced by a random glyph (via `data-glyph` overlay; original hidden) colored `shadowColors[i%2]` if shadows else currentColor; swapped state lasts `ms*(1 + floor(rand*3))`. On a fresh event, a run of `2 + floor(rand*3)` consecutive chars (wrapping) from a random start is swapped. Spaces never swap.
**band fitting**: `fitBand(top,bottom)` clamps both to 0..100 and, if `top + bottom >= 100`, falls back to the full `[0,100]`.

---

## 12. DecryptedText

Source: .../DecryptedText/DecryptedText.jsx. React state-driven interval ticker (`setInterval` every `step`) + Web Animations API for 'flip'.

### Props (defaults)
`text`, `speed=50` (ms per tick; `step = max(16, speed)`), `maxIterations=10`, `sequential=false`, `revealDirection='start'` ('start'|'end'|'center'|'random'), `useOriginalCharsOnly=false`, `characters='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()_+'`, `className` (revealed chars), `parentClassName`, `encryptedClassName` (scrambled chars), `animateOn='hover'` ('hover'|'view'|'click'|'inViewHover'), `clickMode='once'` ('once'|'toggle'), `effect='scramble'` ('scramble'|'fade'|'flip'), `scrambleColor=''` (tint of scrambled glyphs), `loop=false`, `loopDelay=2000` ms, `hoverRadius=0` (px; >0 = local per-letter hover heat), plus `...props` forwarded to the wrapper.

### Data model
`chars = Array.from(text)`; `pool` = `useOriginalCharsOnly ? unique non-space chars of text : Array.from(characters)` (fallback to the text's letters if empty). Spaces are never scrambled. Each char cell is `position:relative; inline-block`; scrambled cells render an invisible "ghost" of the real char (keeps layout width) plus an absolutely centered glyph (`left:50%; translateX(-50%)`). Letters whose scrambled glyph is wider than the real one are squeezed: `scaleX = round(max(0.05, room/need)*1000)/1000` where `room = advance(realChar)+letterSpacing`, `need = advance(glyph)+letterSpacing` (canvas measureText advances, cached per font), only if `need > room+0.01`.
`animateOn` 'click'/'view'/'inViewHover' start fully hidden (all non-space chars scrambled) (view/inViewHover revealed instantly if reduced motion); 'hover' starts revealed.

### Reveal order (`revealOrder`)
Indices of non-space chars: start = ascending; end = reversed; center = sorted by `|i - floor(len/2)|`; random = Fisher-Yates shuffle. Hiding (reverse run) uses the reversed order.

### Tick (every `step` ms), `run = {forward, iteration, queue}`
- **Non-sequential, forward**: `iteration++`; done when `iteration >= maxIterations`, at which point all revealed. In between, `paint` re-randomizes every hidden char each tick. Note: it does *not* reveal progressively in non-sequential forward mode - the text stays fully scrambled (only the `ramp = iteration/maxIterations` dim level rises for `fade`) and the final tick reveals everything at once.
- **Sequential, forward**: each tick pops the next index from `queue` (those not yet matching the target) and sets `mask[idx]=true`; done when queue empty. Total time ~= `nonSpaceCount*step`.
- **Backward (hide, used by loop/toggle)**: sequential: pops from queue setting `false`. Non-sequential: each tick hides `hideCount = max(1, ceil(nonSpaceCount/maxIterations))` random still-visible letters; done when none visible or `iteration >= maxIterations`.
- Scrambled glyph each paint: `pool[floor(random()*pool.length)]` (fresh random glyph each tick for each hidden char). Initial hidden glyph (no animation yet) is deterministic: `pool[(codePoint + index*7) % pool.length]`.
- Loop: after a forward run, wait `loopDelay` then run backward `{cycle:true}`; after that backward cycle run forward again with `lead = sequential ? max(2, round(320/step)) : 0` empty queue slots (a short pause before the first reveal). Timers are held while offscreen (IntersectionObserver threshold 0.1, resumed when visible). For `inViewHover`, mouseleave re-hides after `loopDelay` when `loop`.
- Reduced motion: mask jumps to the end state without animating.

### Triggers
- hover: `onMouseEnter` -> start forward run from `mask = spaces only` (everything scrambled); ignored if a run is active. `onMouseLeave`: stop run, set all revealed.
- view: IntersectionObserver (threshold 0.1) starts one forward run the first time visible. inViewHover: both view and hover.
- click: starts hidden; click runs forward once (`clickMode='once'`: only if currently hidden) or toggles direction (`'toggle'`).
- **hoverRadius > 0** (local mode, hover/inViewHover): no run on enter; pointer tracked; per-letter heat: `target = distance<1 ? 1 - d^2(3-2d) : 0` with `d = dist(pointer, letterCenter)/hoverRadius` (inverse smoothstep falloff); `heat = max(heat - 1/maxIterations, target)` each tick (cools over `maxIterations` ticks); a letter is scrambled when `random() < heat*2`. Ticker stops when no heat and no run.

### Effects / visuals
- scramble (default): plain swap of glyphs.
- `scrambleColor`: scrambled glyph color = `scrambleColor` (when fully tinted) or `color-mix(in srgb, scrambleColor tint%, currentColor)`; revealed -> transition `color .7s cubic-bezier(0.22,1,0.36,1), opacity .6s same`; scrambled -> `color .12s linear, opacity {step}ms linear`.
- fade: opacity of scrambled glyphs: `FADE_FLOOR=0.16`, `FADE_PEAK=0.62`, `FADE_LEAD=6`. Non-sequential: `dim = FLOOR + (PEAK-FLOOR)*ramp` (ramp = iteration/maxIterations, forward run); sequential: `lift = max(0, 1 - rank/6)` where rank = position in remaining queue -> the next ~6 letters to reveal brighten toward 0.62; other hidden letters stay at 0.16; revealed = 1 (with the 0.6 s settle transition). Heat-scrambled letters: `level = min(1, heat*1.5)`, `tint=level`, `dim = 1 - (1-FLOOR)*level`.
- flip (Web Animations, per cell when its glyph changes): while scrambling: `rotateX(angle)->0` with `transform: perspective(4em) translateZ(-0.42em) rotateX(a) translateZ(0.42em)`, `a = 58 + random*26` deg, opacity 0.3 -> 1, duration `min(step*0.92, 240)` ms, easing `cubic-bezier(0.3,0.7,0.4,1)`; when a cell becomes revealed: `rotateX(90deg) opacity 0 -> rotateX(0) opacity 1`, duration `min(max(step*7, 360), 760)` ms, easing `cubic-bezier(0.34,1.45,0.64,1)`.

---

## Implementation notes for Compose

- Frame-driven components (TextPressure, VariableProximity, FuzzyText, GradientText, GlitchText, DecryptedText local mode, SplitText hover) map to `withFrameNanos` loops / `Animatable`; use the same exponential-smoothing formulas `1 - exp(-dt/tau)` (frame-rate independent).
- Scroll-driven (ScrollReveal, ScrollFloat): drive a normalized progress `0..1` from list/scroll state using the start/end offsets above (start = element top at viewport-bottom minus offset; end = element bottom hits viewport bottom or similar); then evaluate per-piece formulas as pure functions of progress. With `scrub:true` there is no smoothing.
- Variable-font axes (TextPressure `wght/wdth/ital`, VariableProximity `wght/opsz`) need a variable font and `FontVariation.Settings(...)` in Compose; ranges to clamp to the actual font axes (Roboto Flex: wght 100-1000, wdth 25-151, opsz 8-144).
- FuzzyText maps naturally to `Canvas` + `drawImage` with `srcOffset/srcSize/dstOffset/dstSize` for per-row slices of a pre-rendered `ImageBitmap`.
- GlitchText "clip-path inset" -> `clipRect` with fractional top/bottom; masks -> draw main text in two clipped passes (outside bands).
- Blur filters -> `Modifier.blur` (API 31+ / skiko) or `RenderEffect`; `color-mix(in srgb/oklab)` -> `lerp(Color,Color,f)` (use Oklab interpolation for oklab cases).
