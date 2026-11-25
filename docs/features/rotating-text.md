# RotatingText

Native recreation of React Bits "Rotating Text" (motion/react, `AnimatePresence`). Code:
`RotatingText.kt`, `internal/rotatingtext/` (`RotatingTextMotion`, `RotatingTextMachine`).
Tests: `commonTest/.../internal/rotatingtext/RotatingTextMotionTest.kt`.

## API summary
```kotlin
RotatingText(texts, modifier, style, effect = Slide, direction = Up, splitBy = Characters,
    rotationIntervalMillis = 2000, staggerMillis = 0, staggerFrom = First, loop = true, auto = true,
    pauseOnHover = false, blur = 0.dp, highlight = false, highlightColor = #A855F7,
    highlightTextColor = Unspecified, transition = null, animateInitial = false,
    renderMode = Auto, state = null, speed = 1f, controller = null, onNext = null)
```
- `RotatingTextEffect`: Slide, Flip, Blur, Scale, Drop (reference preset table: poses, springs, tweens,
  Flip perspective 480 and z-origin -0.45em, Blur effect = 10 dp unless `blur` set).
- `splitBy`: Characters (graphemes, whitespace not animated), Words, Lines (`\n`), `Custom(separator)`.
- `staggerFrom`: First, Last, Center, Random (re-shuffled per entering text), `Index(n)`; formulas as the
  reference (`i*s`, `(n-1-i)*s`, `|floor(n/2)-i|*s`, `perm[i]*s`, `|n-i|*s`). `staggerMillis` = `staggerDuration` in ms.
- `RotatingTextState` (`rememberRotatingTextState()`): `next()`, `previous()`, `jumpTo(i)`, `reset()`,
  observable `currentIndex`, `textCount` (the reference's imperative ref). `onNext(index)` fires on every change.
- `highlight`: pill (padding 0.1em/0.28em, radius 0.16em) that clips the units; text colour auto black/white by
  relative luminance (> 0.3) unless `highlightTextColor` is set; width spring damping 34 / stiffness 320.
- `transition: RotatingTextTransition` (`Spring(damping, stiffness, mass)` / `Tween(ms, easing)`) replaces the
  preset enter and exit transition (reference `transition`).
- `controller`: pause/resume freeze rotation and animation; `restart()` shows text 0 with its enter animation.

## Rendering technique
One shaped paragraph per text (measured once per constraints/style, cached; never per frame). Every unit is a
`TextSegment` (new shared `SegmentedTextLayout.buildFromRanges` for word/line/custom units) drawn in the draw
phase through the SplitText renderer (`Transform3D` rotateX + perspective, scale, alpha, shared blur renderer with
the Android < 12 multi-pass fallback). No composable per character. A small state machine
(Rest / Exiting / Entering, `AnimatePresence mode="wait"`) is advanced by one frame loop that only runs while
animating; during the idle hold it sleeps with `delay` until the next rotation, and stops when invisible/paused.
Springs are evaluated analytically (under/critically/over-damped). An interrupted enter exits from the current
pose; rapid requests end on the latest text. The container width/height animates on a spring at the start of
every enter; only that animation relayouts (cached text layouts), the glyph animation never does.

## Platform differences
Same code everywhere (commonMain). Blur uses `BlurEffect` layers (Android 12+, Skia) or the multi-pass
fallback on older Android. Hover pause = mouse hover on Desktop/Web, a resting touch on Android/iOS.

## Deviations from the reference
- `animatePresenceMode` is always `wait` (no `sync`/`popLayout`); `initial/animate/exit` overrides are replaced
  by `transition`; no class-name hooks. `animatePresenceInitial` is `animateInitial`.
- Flip: the `transform-origin` z offset is approximated by a vertical translation (the 2D projective matrix has
  no z translation), `backface-visibility` is not needed with the overshoot ranges used.
- Custom-separator and line units that wrap onto several visual lines use their union box (rare).
- Percent `y` offsets are relative to the line box height of the unit, not the glyph ink.
- Content is not clipped (like the reference) except in highlight mode; the parent may clip overflow.
- `controller.seekTo` is ignored; random stagger uses a deterministic seeded shuffle.

## Unverified
Nothing was run visually. Spring feel, the width reflow timing, flip perspective look and the highlight pill
metrics need an owner check on a device.
