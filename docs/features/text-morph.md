# TextMorph

Reference: motion-primitives.com/docs/text-morph (`components/core/text-morph.tsx`).

## API
```kotlin
TextMorph(
    text: String, modifier, style,
    transition: TextMorphTransition = TextMorphTransition.Spring(280f, 18f, 0.3f), // or Tween(300, easeInOut)
    enter: TextMorphVariant = TextMorphVariant.Hidden,  // variants.initial { opacity: 0 }
    exit: TextMorphVariant = TextMorphVariant.Hidden,   // variants.exit    { opacity: 0 }
    ignoreCase = true, animateInitial = false,          // AnimatePresence initial={false}
    granularity: TextGranularity? = null, renderMode = SegmentRenderMode.Auto,
    softWrap = true, maxLines = Int.MAX_VALUE,
    speed = 1f, controller: TextAnimationController? = null, onFinished: (() -> Unit)? = null,
)
```
`TextMorphVariant(alpha, scale, offsetX, offsetY)`; the resting (`animate`) state is the identity.
Controller: pause/resume, `restart()` replays the latest morph, `seekTo(p)` scrubs it (0..1).

## Rendering technique
- Each piece (grapheme cluster) gets the reference identity `lowercase(piece) + occurrence index`
  (`morphKeys`), e.g. "hello" → h1 e1 l1 l2 o1. Same key = same Motion `layoutId`.
- `AnimatedTextLayout` gained `onLayoutChanged` (called in measure when a new layout is built).
  `MorphState.sync` diffs the new segments against the live pieces there — once per change,
  never per frame. Frames only evaluate closed-form curves and draw cached segments.
- Shared pieces: start offset = current visual position − new position; the offset decays with
  the transition. Like Motion's layout projection (0 → 1000 progress), an interrupted glide starts
  from the current visual position with zero velocity.
- Enter/exit: a presence value 0..1 blends `enter`/`exit` → identity. It is a plain spring that
  keeps its velocity when interrupted (Motion opacity semantics); exiting pieces are drawn from
  the previous layout at their last position (popLayout) and dropped once faded. A key that comes
  back while exiting fades back in from where it is.
- Container shift: `onGloballyPositioned` compares the root position before/after a text change,
  so centred text whose box resizes still glides from the previous on-screen position (Motion
  measures in page space). Later root movement (scrolling) is ignored.
- Spring: analytic damped oscillator (`SpringSolver`, under/critical/over-damped) with Motion's
  rest thresholds (restDelta 0.5 / restSpeed 2, granular 0.005 / 0.01 for values with Δ < 5),
  sampled at 120 Hz to find the settle time. Clock stops when every piece rests.
- Scripts needing shaping context (Arabic, Hebrew, Indic, …): granularity switches to words drawn
  as clipped windows of the shaped paragraph (`SegmentRenderMode.Clip`), so shaping is intact:
  surviving words glide, changed words cross-fade. Latin/CJK/emoji morph per grapheme.

## Platform differences
None: pure Compose drawing (translate/scale + `drawText`), identical on Android, iOS, Desktop, Web.

## Deviations from the reference
- Pieces are grapheme clusters instead of UTF-16 units (emoji/combining marks are not torn apart);
  keys are identical for plain BMP text. Whitespace has no visual so it gets no piece.
- Complex scripts morph per word (see above) instead of per broken code unit.
- `transition` supports Spring (stiffness/damping/mass/delay) and Tween (duration/easing/delay),
  not every Motion transition option (keyframes, per-property transitions, `bounce`/`visualDuration`).
- Variants are limited to alpha/scale/offset; the `animate` variant is fixed to the identity.
  If an entering piece is interrupted into exiting with different enter/exit variants, the blend
  switches variant (no jump with the default identical variants).
- Exiting pieces stay relative to the composable (as with popLayout inside its parent).

## Unverified
Nothing was run visually (project rule). Unit-tested: key matching, spring closed forms
(velocity = derivative, critical case, settle time), tween/delay, presence interruption.
To check visually: glide smoothness, centred-text container shift, RTL word mode, seek/replay.
