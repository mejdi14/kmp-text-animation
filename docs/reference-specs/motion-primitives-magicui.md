# Reference spec: Motion Primitives text components + Magic UI AuroraText

Sources verified by fetching raw files (branch `main`) on 2026-11-24.

## Licenses
| Library | License | Copyright holder | Evidence |
|---|---|---|---|
| Motion Primitives (github.com/ibelick/motion-primitives) | MIT | "Copyright (c) 2024 ibelick" | `LICENCE.md` (GitHub API spdx_id MIT) |
| Magic UI (github.com/magicuidesign/magicui) | MIT | "Copyright (c) Magic UI" | `LICENSE.md` (GitHub API spdx_id MIT) |

This spec is a behavioral description for clean-room re-implementation; ship attribution (MIT notice) in the KMP library's NOTICE/README if any code or constants are derived.

General notes on the React originals:
- Motion = `motion/react` (framer-motion successor). `as` prop = which HTML element tag to render (irrelevant in Compose).
- Motion durations/delays are in SECONDS. `ease: 'linear'`, `'easeIn'`, `'easeInOut'` are CSS-like cubic-bezier curves: easeIn = cubic-bezier(0.42,0,1,1), easeInOut = cubic-bezier(0.42,0,0.58,1), easeOut = (0,0,0.58,1).
- `React.memo`, `aria-*`, `sr-only` bits are accessibility only: in Compose set one semantics `contentDescription`/text equal to the full string and hide per-char pieces.
- Dark-mode colors are given by Tailwind `dark:`; in Compose expose them as params.

---

## 1. TextShimmer
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-shimmer.tsx  (docs: motion-primitives.com/docs/text-shimmer)

### Props
| prop | type | default |
|---|---|---|
| children | string | required |
| as | element | `'p'` |
| className | string | – |
| duration | number (s) | `2` |
| spread | number | `2` |

### Algorithm
- Text is rendered with `color: transparent` and `background-clip: text`; the visible color comes from the background.
- Computed: `dynamicSpread = children.length * spread` (px). So spread is a px-per-character multiplier; a longer string gets a wider highlight band.
- Background is two layers: `backgroundImage: var(--bg), linear-gradient(var(--base-color), var(--base-color))`.
  - Layer 1 (--bg): `linear-gradient(90deg, #0000 calc(50% - spread), var(--base-gradient-color), #0000 calc(50% + spread))` — i.e. transparent until (50% - dynamicSpread px), a single peak stop of the highlight color at 50%, transparent from (50% + dynamicSpread px). (Stops with no position: the middle stop is placed midway between neighbors = 50%.) The highlight is a triangular ramp, total width 2*dynamicSpread px.
  - Layer 2: solid base color fill.
- `background-size: 250% 100%, auto` (layer 1 is 2.5x element width, layer 2 auto/covers). `background-repeat: no-repeat, padding-box`. `bg-clip: text`.
- Animation: `backgroundPosition` from `'100% center'` to `'0% center'`, repeat Infinity, `duration` seconds, `ease: linear`, no repeat delay, restart (not reverse) each cycle. Layer 1 is 2.5W wide (W = element width). CSS background-position p% gives offset = (W - 2.5W) * p = -1.5W * p, and the highlight center is at 50% of the layer = 1.25W from its left edge, so center x = -1.5W*p + 1.25W. p=1 -> x = -0.25W (left of text); p=0 -> x = +1.25W (right of text). The highlight sweeps LEFT to RIGHT linearly (ease linear) over `duration`, then restarts.
- Colors: light mode base `#a1a1aa` (zinc-400), highlight `#000000`. Dark mode base `#71717a` (zinc-500), highlight `#ffffff`.
- Compose suggestion: draw text with `Brush.linearGradient` shader over the text via `drawWithContent`/`TextStyle(brush=...)`: base color text, then overlay a horizontal gradient band [transparent, highlight, transparent] of half-width `length*spread` px centered at x(t); only alpha-composited onto glyphs (BlendMode.SrcIn).

---

## 2. TextMorph
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-morph.tsx (docs: /docs/text-morph)

### Props
| prop | default |
|---|---|
| children: string | required |
| as | `'p'` |
| className, style | – |
| variants: Variants | `{initial:{opacity:0}, animate:{opacity:1}, exit:{opacity:0}}` |
| transition | `{type:'spring', stiffness:280, damping:18, mass:0.3}` |

### Algorithm
- `uniqueId = useId()` (component instance id).
- Split `children` into individual characters (`split('')`, UTF-16 units). For each char compute key:
  - `lower = char.toLowerCase()`; `charCounts[lower]++` (occurrence count of that lowercase char so far in the string, starting at 1).
  - `id = "${uniqueId}-${lower}${count}"`, e.g. "hello" -> h1, e1, l1, l2, o1. Label = char, but `' '` is replaced by NBSP ` `.
  - So a character's identity = (lowercased char, n-th occurrence). When the text changes (e.g. "Hello" -> "Help"), h1,e1,l1 persist (same key = same layoutId), l2/o1 exit, new chars enter.
- Each character is a `motion.span` (`inline-block`), `key = layoutId = id`, `initial='initial' animate='animate' exit='exit'`, with the variants/transition above, inside `<AnimatePresence mode="popLayout" initial={false}>` (no enter animation on first mount).
- Effect:
  - Persisting characters: their layout position changes (because neighbors were added/removed) and `layoutId` makes Motion animate the position delta (FLIP) using the given transition (spring stiffness 280, damping 18, mass 0.3 => snappy slightly underdamped; damping ratio = 18/(2*sqrt(280*0.3)) ≈ 0.98, i.e. near critically damped, ~ no overshoot... settle ≈ 0.3-0.4 s).
  - New characters: fade in from opacity 0 -> 1 with the same spring (opacity spring).
  - Removed characters: `exit` opacity -> 0; `popLayout` pulls them out of layout flow (position: absolute at their last position) immediately so remaining characters reflow/slide while the leavers fade.
- Compose approach: maintain a list of glyph items keyed by (lowercase char, occurrence index); on text change diff by key; animate each persistent glyph's x offset (and y for wrapped text) from old measured position to new using `spring(stiffness≈280-ish, dampingRatio≈0.98)`; fade-in new (alpha 0->1), fade-out removed (alpha 1->0) drawn at their old position without participating in layout. Note Compose spring params: Motion's mass 0.3 should be folded in: Compose stiffness_equiv = stiffness/mass ≈ 933 (Compose assumes mass 1), dampingRatio ≈ 0.98.

---

## 3. TextScramble
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-scramble.tsx (docs: /docs/text-scramble)

### Props
| prop | default |
|---|---|
| children: string | required |
| duration (s) | `0.8` |
| speed (s per frame) | `0.04` |
| characterSet | `'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'` |
| as | `'p'` |
| className | – |
| trigger: boolean | `true` |
| onScrambleComplete | – |
| ...MotionProps | forwarded to the element |

### Algorithm (exact)
```
steps = duration / speed          // 0.8/0.04 = 20 (float, not rounded)
step = 0
every (speed*1000) ms:            // 40 ms
  progress = step / steps
  for i in 0 until text.length:
     if text[i] == ' ' -> ' '                         // spaces never scrambled
     else if progress * text.length > i -> text[i]    // revealed (strict >)
     else -> characterSet[floor(random()*characterSet.length)]   // fresh random each tick
  display = scrambled; step++
  if step > steps: stop timer; display = original (null -> children); isAnimating=false; onScrambleComplete()
```
- Left-to-right reveal: character i becomes final once `step/steps * len > i`. With step 0, progress 0 => nothing revealed (all random except spaces). Total ticks = floor(steps)+1... the interval stops when `step > steps` (i.e. after 21 ticks for defaults, last tick at step 20 reveals everything since progress=1 -> 1*len > i for all i; then step=21 > 20 ends and resets).
- Started by effect on `trigger` change when `trigger` is true (runs on mount and every change of trigger value; re-trigger while animating is ignored via `isAnimating` guard). Prop `children` changes do NOT retrigger by themselves.
- No easing; purely stepped timer. Width is not preserved (proportional font => jitter); use monospace or fixed measuring if desired (not done in original).

---

## 4. TextRoll
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-roll.tsx (docs: /docs/text-roll)

### Props
| prop | default |
|---|---|
| children: string | required |
| duration (s) | `0.5` |
| getEnterDelay(i) | `(i) => i * 0.1` |
| getExitDelay(i) | `(i) => i * 0.1 + 0.2` |
| className | – |
| transition | `{ ease: 'easeIn' }` |
| variants | `{enter:{initial,animate}, exit:{initial,animate}}` (see defaults) |
| onAnimationComplete | called when the LAST character's exit span finishes |

### Default variants
```
enter: initial {rotateX: 0},  animate {rotateX: 90}
exit : initial {rotateX: 90}, animate {rotateX: 0}
```
(degrees)

### Structure and algorithm
- Text split per character (`split('')`), keyed by index `i`. Each char container: `relative inline-block`, CSS `perspective: 10000px; transform-style: preserve-3d; width:auto`.
- Inside each, THREE layers of the same glyph (NBSP for space):
  1. "enter" span: `absolute inline-block`, `backface-visibility: hidden`, `transform-origin: 50% 25%`. Animates rotateX 0 -> 90 (the visible glyph rotates away, top edge going back so the glyph rolls up/out of view, edge-on at 90deg).
  2. "exit" span: `absolute inline-block`, `backface-visibility: hidden`, `transform-origin: 50% 100%` (bottom edge). Starts at rotateX 90 (edge-on, invisible) and animates to 0 (the second copy rolls in from below).
  3. An `invisible` static copy that gives the container its width/height (layout sizing).
- Each animated span transition = `{...transition, duration, delay}` where `ease` defaults to easeIn (cubic-bezier .42,0,1,1) and delay = `getEnterDelay(i)` for enter span (default i*0.1 s) and `getExitDelay(i)` for exit span (default i*0.1+0.2 s). Both run once, on mount (animate-on-mount, no repeat). Since `rotateX` 90 -> 0 starts 0.2 s after the enter rotation of the same letter starts (overlap 0.3 s with duration 0.5), it produces a continuous roll from the old face to the second face per letter, left-to-right wave.
- Net default result per letter: visible glyph A (enter) rotates 0->90 about its 50%/25% origin while an identical glyph (exit) rotates 90->0 about 50%/100% origin; with identical text this reads as a "roll/flip" of each letter. (In the docs it is typically used on hover/mount with the same text; a displaced second copy is not translated, the effect is purely rotateX.)
- Compose: per-char `graphicsLayer { rotationX; cameraDistance = very large (perspective 10000px ≈ cameraDistance big, ~ 10000 * density); transformOrigin = TransformOrigin(0.5f, 0.25f) / (0.5f,1f); alpha hidden when |rotationX| > 90 (backface) }`. Delays/durations/ease as above. Keep an invisible copy for measuring.
- Accessibility: full text in sr-only span; glyph spans aria-hidden.

---

## 5. TextLoop
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-loop.tsx (docs: /docs/text-loop)

### Props
| prop | default |
|---|---|
| children: ReactNode[] | required (items) |
| className | – |
| interval (s) | `2` |
| transition | `{ duration: 0.3 }` |
| variants | default below |
| onIndexChange(index) | – |
| trigger: boolean | `true` |
| mode (AnimatePresence) | `'popLayout'` |

### Algorithm
- State `currentIndex = 0`. If `trigger`, `setInterval(interval*1000)` advances `currentIndex = (current+1) % items.length` and calls `onIndexChange(next)`. Timer cleared on unmount / dep change (deps: items.length, interval, onIndexChange, trigger).
- Wrapper: `relative inline-block whitespace-nowrap`.
- Renders only `items[currentIndex]` in a keyed `motion.div` (`key = currentIndex`) inside `<AnimatePresence mode={mode} initial={false}>` (first item shown without enter animation).
- Default variants:
```
initial: { y: 20, opacity: 0 }
animate: { y: 0,  opacity: 1 }
exit:    { y: -20, opacity: 0 }
```
(px). Transition `{duration: 0.3}`: Motion default type for y is spring but with explicit `duration` and no `type` it behaves as a duration-based spring (visual duration 0.3 s, default bounce); opacity is tween 0.3 s ease. A Compose port may use tween(300, FastOutSlowIn/EaseOut) or spring with dampingRatio ~0.75 (bounce 0.25) and visualDuration 0.3 — the original is NOT plain linear.
- mode 'popLayout': the exiting item is popped out of layout (absolute) so the entering item takes the flow position immediately; both animate simultaneously (old slides up -20 and fades, new slides from +20 up to 0 and fades in). Other AnimatePresence modes: 'sync' (both in flow concurrently), 'wait' (exit finishes before enter starts).

---

## 6. TextShimmerWave
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-shimmer-wave.tsx (docs: /docs/text-shimmer-wave)

### Props
| prop | default |
|---|---|
| children: string | required |
| as | `'p'` |
| className | – |
| duration (s) | `1` |
| zDistance (px) | `10` |
| xDistance (px) | `2` |
| yDistance (px) | `-2` |
| spread | `1` |
| scaleDistance | `1.1` |
| rotateYDistance (deg) | `10` |
| transition | merged last, overrides defaults |

### Algorithm
- Container: `relative inline-block`, `perspective: 500px`, `color: var(--base-color)`. Colors: light base `#a1a1aa`, highlight `#000`; dark base `#71717a`, highlight `#fff`.
- Each char (`split('')`, keyed by index, `inline-block whitespace-pre`, `transform-style: preserve-3d`) animates independently:
  - initial: `translateZ 0, scale 1, rotateY 0, color base`.
  - animate (3-keyframe arrays, evenly spaced, there-and-back): 
    - translateZ `[0, zDistance, 0]`
    - translateX `[0, xDistance, 0]`
    - translateY `[0, yDistance, 0]`  (negative = up)
    - scale `[1, scaleDistance, 1]`
    - rotateY `[0, rotateYDistance, 0]`
    - color `[base, highlight, base]`
  - transition: `{ duration, repeat: Infinity, repeatDelay: (len * 0.05) / spread, delay, ease: 'easeInOut', ...transition }`
  - Per-char delay: `delay = (i * duration * (1 / spread)) / len` where len = children.length. So with spread=1 the wave's start offsets span exactly `duration` across the string; larger spread compresses the offsets (and the repeat delay) — effectively wave width smaller/faster travel: offset between adjacent chars = duration/(spread*len).
  - Note: `delay` applies only to the first iteration in Motion; repeats do not re-apply the initial delay, but repeatDelay (len*0.05/spread s) is applied between each cycle for every char equally, and every char has period `duration + repeatDelay`. After the first cycle, the per-char phase offsets stay equal to the initial `delay` differences (they all have same period), so the wave persists.
  - Each keyframe segment (0->peak, peak->0) uses easeInOut (cubic-bezier .42,0,.58,1) per segment, each taking duration/2.
- Compose: per-glyph `graphicsLayer { translationX/Y, translationZ via scale/cameraDistance, rotationY, scale }` + color lerp; use `infiniteRepeatable` with keyframes [0 → duration/2 peak → duration] + a trailing hold of repeatDelay (include in a total period = duration + repeatDelay with keyframes 0 / duration/2 / duration / period), start offset `delay` via `StartOffset(ms)`. translateZ with perspective 500px has the visual effect of scaling around the center by 500/(500-z) (≈1.02 for z=10) combined with the explicit scale.

---

## 7. TextEffect
Source: https://raw.githubusercontent.com/ibelick/motion-primitives/main/components/core/text-effect.tsx (docs: /docs/text-effect)

### Props
| prop | type/default |
|---|---|
| children | string, required |
| per | `'word' \| 'char' \| 'line'`, default `'word'` |
| as | HTML tag, default `'p'` |
| variants | `{container?: Variants, item?: Variants}` custom override |
| className, style | – |
| preset | `'blur' \| 'fade-in-blur' \| 'scale' \| 'fade' \| 'slide'`, default `'fade'` |
| delay (s) | `0` (becomes `delayChildren` on container) |
| speedReveal | `1` (divides stagger) |
| speedSegment | `1` (divides segment duration) |
| trigger | `true` (when true mount motion element; when false it is removed -> exit animation via AnimatePresence) |
| onAnimationComplete / onAnimationStart | forwarded to container |
| segmentWrapperClassName | optional wrapper span around each segment (clipping/mask effects) |
| containerTransition | Transition merged into container `visible` transition |
| segmentTransition | Transition merged into item `visible` and `exit` transition |

### Splitting
- `per='line'`: `text.split('\n')`.
- `per='word'` and `per='char'`: `text.split(/(\s+)/)` — whitespace runs are KEPT as their own segments (captured group), so spaces are segments too (rendered `whitespace-pre`).
- `per='char'`: each segment (word/space) is a `motion.span inline-block whitespace-pre` (no variants of its own) containing one `motion.span` per character with the item variants. `per='word'`: each segment is itself an animated `inline-block` span. `per='line'`: animated `block` span.
- Whole thing in `<AnimatePresence mode="popLayout">{trigger && <MotionTag initial="hidden" animate="visible" exit="exit" variants={container}>…}`. Segment keys = `${per}-${index}-${segment}`.

### Stagger / durations
- `defaultStaggerTimes = { char: 0.03, word: 0.05, line: 0.1 }` (s).
- `stagger = defaultStaggerTimes[per] / speedReveal`.
- `baseDuration = 0.3 / speedSegment` (item duration, s).
- Custom stagger/delay can come from `variants.container.visible.transition.staggerChildren / delayChildren`; otherwise stagger and `delay`.
- Container variants after merge:
  - `visible.transition = { staggerChildren: customStagger ?? stagger, delayChildren: customDelay ?? delay, ...containerTransition }` (plus whatever the preset container has: its own visible transition `staggerChildren: 0.05`, overridden by the merge).
  - `exit.transition = { staggerChildren: …, staggerDirection: -1 }` (the `exit` sub-key passed through is dropped by `{exit: _, ...main}` destructure, but staggerDirection -1 is always appended) -> on exit (trigger false) the segments animate out in reverse order.
- Item variants after merge: `visible.transition = { duration: baseDuration, ...segmentTransition }` and `exit.transition = { duration: baseDuration, ...segmentTransition, staggerDirection: -1 }`. No explicit ease: Motion defaults (opacity/filter = tween 0.3 s ease default "easeOut"-like curve; y/scale are physical props and with explicit duration use a duration-based spring with default bounce 0.25; reasonable Compose approximation: tween(300 ms, easeOut) for opacity & blur, spring/visualDuration 0.3 for y and scale).

### Container (same for all presets)
```
hidden:  { opacity: 0 }
visible: { opacity: 1, transition: { staggerChildren: 0.05 } }   // overridden by computed stagger
exit:    { transition: { staggerChildren: 0.05, staggerDirection: -1 } }
```
### Item variants per preset (px for y, blur radius in CSS px)
| preset | hidden | visible | exit |
|---|---|---|---|
| blur | `{opacity:0, filter:'blur(12px)'}` | `{opacity:1, filter:'blur(0px)'}` | `{opacity:0, filter:'blur(12px)'}` |
| fade-in-blur | `{opacity:0, y:20, filter:'blur(12px)'}` | `{opacity:1, y:0, filter:'blur(0px)'}` | `{opacity:0, y:20, filter:'blur(12px)'}` |
| scale | `{opacity:0, scale:0}` | `{opacity:1, scale:1}` | `{opacity:0, scale:0}` |
| fade | `{opacity:0}` | `{opacity:1}` | `{opacity:0}` |
| slide | `{opacity:0, y:20}` | `{opacity:1, y:0}` | `{opacity:0, y:20}` |
(Fallback if `preset` falsy: `defaultItemVariants` = hidden opacity 0 / visible opacity 1 / exit opacity 0.)

Timing summary: unit i (char/word/line) of N starts at `delay + i*stagger` (exit: reversed order, `(N-1-i)*stagger`, no delay), each runs ~0.3 s/speedSegment. Defaults: per=char: 0.03 s stagger; per=word: 0.05 s; per=line: 0.1 s. Total ≈ delay + (N-1)*stagger + 0.3.
Notes: `scale` animates from 0 about the element center (default transform-origin 50% 50%); `blur(12px)` -> use `Modifier.blur(radius)` (Compose blur radius ~ CSS blur radius; Compose uses sigma-like radius, equals roughly 12.dp), applied per segment.
`onAnimationComplete` fires when the container's animation (incl. children) completes.

---

## 8. AuroraText (Magic UI)
Source: https://raw.githubusercontent.com/magicuidesign/magicui/main/apps/www/registry/magicui/aurora-text.tsx
(registry JSON incl. CSS: apps/www/public/r/aurora-text.json; docs magicui.design/docs/components/aurora-text). Note: file is NOT at repo-root `registry/magicui/` (404); it is under `apps/www/registry/magicui/`.

### Props
| prop | default |
|---|---|
| children: ReactNode | required |
| className | `""` |
| colors: string[] | `["#FF0080", "#7928CA", "#0070F3", "#38bdf8"]` |
| speed: number | `1` |

### Algorithm
- Structure: outer `relative inline-block` span; inner `sr-only` copy of text; visible copy `aria-hidden` with classes `animate-aurora relative bg-size-[200%_auto] bg-clip-text text-transparent`.
- Gradient: `backgroundImage: linear-gradient(135deg, c0, c1, c2, c3, c0)` (colors joined, first color appended again at the end to make the loop seamless), `background-clip: text`, `-webkit-text-fill-color: transparent`, `background-size: 200% auto` (gradient layer is 2x element width).
- Animation duration inline: `animationDuration = ${10 / speed}s` (default 10 s; overrides the theme's 8s). Theme token: `--animate-aurora: aurora 8s ease-in-out infinite alternate` (=> timing function ease-in-out, iteration infinite, direction alternate; the 8s is overridden by the inline duration).
- Keyframes `aurora` (CSS, applied to the text element itself, i.e. the transform moves/rotates the whole text span):
```
0%   { background-position: 0% 50%;   transform: rotate(-5deg) scale(0.9);  }
25%  { background-position: 50% 100%; transform: rotate(5deg)  scale(1.1);  }
50%  { background-position: 100% 50%; transform: rotate(-3deg) scale(0.95); }
75%  { background-position: 50% 0%;   transform: rotate(3deg)  scale(1.05); }
100% { background-position: 0% 50%;   transform: rotate(-5deg) scale(0.9);  }
```
- Timing function `ease-in-out` is applied PER keyframe interval (CSS semantics), direction `alternate` means every second cycle plays backward (so full forward+backward period = 2 * duration; default 20 s). Rotation/scale pivot = element center (default transform-origin 50% 50%).
- Because bg-size is 200% wide and the position is a percentage, background-position x in % maps to offset = (W - 2W) * p = -W*p, i.e. gradient shifts left by 0..W; y position has no travel in x-dimension... (height is `auto` = image has 100% height of the box for a gradient with auto height => y-position % has no effect since the image height equals the box height; only x panning is visible).
- Compose: draw text with a `Brush.linearGradient` at 135 deg across a 2x-wide canvas (tile, TileMode.Clamp is fine because first color repeated at the end), translate x offset by -W*p(t), animate `rotationZ`, `scale` via graphicsLayer with the same keyframe table, `infiniteRepeatable(keyframes, RepeatMode.Reverse)`, duration = 10000/speed ms, per-segment easing FastOutSlowIn-like ease-in-out (cubic-bezier .42,0,.58,1). 135deg CSS gradient on a wide box: direction vector toward bottom-right (CSS angle: 0deg = up, 90deg = right, 135deg = down-right), and gradient line length = |W*sin(a)| + |H*cos(a)| of the (2W x H) box.
