# Sortfold 1.2.0 — Design & Motion Notes

Everything below is built on the existing motion system: `ui/theme/Motion.kt`
is the single source of durations and easings, `LocalReducedMotion` gates
every animation, and the system "remove animations" setting feeds it. No
screen hard-codes a duration or an easing.

## Motion tokens (unchanged core, one addition)

| Token | Value | Used for |
|-------|-------|----------|
| `PRESS_MS` | 100 ms | press feedback window |
| `SMALL_MS` | 200 ms | selection color/scale changes, number counts |
| `ENTER_MS` | 300 ms | things entering (emphasis decelerate) |
| `EXIT_MS` | 225 ms | things leaving (accelerate) |
| `FADE_THROUGH_EXIT_MS` | **90 ms (new)** | top-level destination fade-through exit |
| `pressSpring()` | medium-bouncy spring | press scale (C-01) |
| `entrySpring()` | low-bouncy spring | check icons, outcome icons |
| `reduced()` | 1 ms linear | the reduced-motion stand-in for every animation |

## C-01 — press feedback that actually animates

`Modifier.pressable()` computed its scale as a plain float (`0.97f / 1f`),
so it snapped. It now runs `animateFloatAsState` with `Motion.pressSpring()`
(reduced motion: `Motion.reduced()` and the scale stays exactly 1f), applied
through `graphicsLayer` to avoid recomposition on every frame. Every
tappable card, chip and button uses it; toggle/apply keep the light haptic.

## C-02 — screen and step transitions

- **Wizard steps** (FOLDER → MODES → PREVIEW → APPLY → RESULT): directional
  shared-axis X — forward slides in from the end by 24 dp with fade, back
  reverses; `AnimatedContent` with `Motion.enter()` / `Motion.exit()`; the
  stepper header remains a shared element that glides between steps.
- **Stepper nodes**: the active node scales 1.0 → 1.1 and re-colors with
  `Motion.small()`; completed nodes fade to the 55% primary.
- **Top-level destinations** (Home, History, Settings, About, Language,
  Auto Rules, Error Library): fade-through — the outgoing screen fades out
  first in 90 ms (`Motion.fadeThroughExit()`), the incoming one fades in
  with an 8% scale-up (`scaleIn(initialScale = 0.92f)`). No sliding between
  top-level destinations.
- **Predictive back** in the wizard: `PredictiveBackHandler` tracks gesture
  progress and scales the current step from 1.0 toward 0.92; commit performs
  the real back, cancellation springs nothing (plain reset). Reduced motion
  keeps a plain back.

## C-03 — mode cards and the capacity panel

- **Mode cards**: selecting/deselecting cross-fades the container color and
  border (`animateColorAsState`, `Motion.small()`), the check icon scales in
  with `entrySpring()`, and press feedback rides the shared `pressable()`.
- **Capacity panel** expands with `expandVertically + fadeIn` and collapses
  with `shrinkVertically + fadeOut` (reduced: near-instant cross-fade);
  content below shifts smoothly, nothing overlaps mid-expansion.
- **Preset chips**: one sliding selection indicator moves between chips
  (`animateDpAsState`) instead of per-chip fades.
- **Live estimate**: the folder count animates with `animateIntAsState`
  (200 ms) and the largest-part size cross-fades via `AnimatedContent`;
  recalculation is debounced 150 ms and runs on `Dispatchers.Default`, so
  packing never blocks the main thread.

## Reduced motion

Every animation above reads `LocalReducedMotion`; when on, nothing moves —
animations collapse to `Motion.reduced()` (1 ms) cross-fades. The layout
matrix asserts the final (post-animation) layout only: `waitForIdle` then
overlap/clipping checks.

## Layout matrix coverage

`LayoutMatrixTest` now includes the capacity surfaces:

| Case | Width | Orientation | Font scale |
|------|-------|-------------|-----------|
| `wizard capacity panel holds the contract at 360dp and 1_3x font` | 360 dp | portrait | 1.3 |
| `wizard capacity panel holds the contract at 411dp portrait` | 411 dp | portrait | 1.0 |
| `wizard capacity panel holds the contract on a small tablet in landscape` | 800 dp | landscape | 1.3 |

plus the existing Home/Settings cases across Indonesian, Arabic (RTL),
Malay and Chinese at 1.5–2× font. Assertions: no overlaps, no clipping
against the root bounds, after `waitForIdle`.
