# Sortfold 1.1.0 — Design System & Screen Rework

This document records the Part B rework: the token layer, the motion
choreography, the per-screen before/after, and the design rationale. The
brand voice is unchanged: a restrained deep-teal accent (`#0F6B5F`), neutral
surfaces, calm typography — the rework is about consistency and craft, not a
new identity.

## 1. Token layer (Part B1)

All values live in `ui/theme` and no screen invents its own:

| Token group | Source | Values |
|-------------|--------|--------|
| Colour roles | `Theme.kt` | Light/dark schemes; teal primary `#0F6B5F` (light) / `#66D9C4` (dark); amber tertiary for warnings; dynamic colour on API 31+ when the user opts in |
| Spacing | `Theme.kt` `Spacing` | 4 / 8 / 12 / 16 / 24 / 32 dp; max content width 720 dp |
| Elevation | `Theme.kt` `Elevation` (new) | card 1 dp · raised 3 dp · overlay 6 dp |
| Shape | `Theme.kt` `ShapeScale` (new) | 8 / 12 / 16 dp corners, mirrored by `Shapes` |
| Type | `Theme.kt` `SortfoldTypography` | M3 scale; SemiBold display/title, Medium labels; titleMedium letter-spacing 0.1 sp |
| Motion | `Motion.kt` | durations 100/200/300/225 ms; emphasized-decelerate enter, accelerate exit; spring tokens; stagger 30 ms capped at 5 items |
| Interaction | `Interactions.kt` (new) | `pressable()` spring press scale 0.97; `rememberHaptics()` light tick |

Rationale: dynamic colour was already a persisted setting; it is now verified
as real behaviour (scheme swap on API 31+, inert below) and documented as
such. The brand palette remains the default.

## 2. Motion choreography (Part B2)

| Moment | 1.0.1 | 1.1.0 |
|--------|-------|-------|
| Press feedback | M3 default | `pressable()` graphicsLayer scale with a medium-bouncy spring (no layout resize) |
| Card/list entry | fade | `StaggeredEntry`: alpha + 1/8-height slide on `entrySpring()` (low-bouncy), stagger 30 ms capped at 5 items |
| Wizard steps | shared-axis X tweens | unchanged tweens **plus** the stepper header as a shared element (`SharedTransitionLayout` + `sharedBounds`) gliding between steps |
| Apply/Result state change | text swap | outcome icon scales in with `entrySpring()` |
| Loading | text spinners / none | `SkeletonRow` shimmer in Home (settings + recent list) |
| Reduce animations | instant fades | everything above falls back to 1 ms fades or static states; the shimmer renders a static tint; haptics remain (not motion) |

Rules kept from 1.0.1: one primary motion at a time; the logo loader appears
only after 400 ms and stays ≥ 300 ms; determinate progress wherever a total
is known; no infinite animation except the loader/skeletons.

## 3. Adaptive layout (Part B3)

Compact width keeps the bottom bar (hosted by each screen's own Scaffold —
insets applied exactly once). Medium/expanded widths keep the navigation
rail. Lists read with a 720 dp max width on expanded. No nested Scaffolds
exist; the layout contract (`LayoutAssertions.assertLayoutContract`) is tested
at 320/411/600/840 dp equivalents via the Robolectric matrix.

## 4. Per-screen before/after (Part B4)

### Home
- **Before:** subtitle, CTA, crash/battery banners, list of recent jobs.
- **After:** hero block with two big-number stats (files sorted, recent
  operations) under the value line; **live job card** with animated
  determinate progress while anything is RUNNING/PAUSED; skeleton shimmer
  while settings load; staggered banner entry. Crash/battery banners,
  primary CTA and recent list keep their roles and tests.
- **Rationale:** the home screen answers "is my app working and is anything
  running?" in one glance; the CTA stays the single primary action.

### Wizard
- **Before:** "Step n" title, no progress affordance, text-only warnings,
  plain Apply.
- **After:** visual 1-2-3 stepper (Folder / Sort / Preview) with done-checks;
  the header is a shared element that glides between steps; step content
  keeps the shared-axis X transitions; warnings remain tiered
  (error-blocking red / amber caution) with an info tier implied by tertiary
  colouring; Apply shows determinate progress; a light haptic tick confirms
  the confirmed apply.
- **Rationale:** the wizard is the product; making position and progress
  visible reduces drop-off and accidental applies.

### JobResult
- **Before:** status text, summary card, undo button without feedback.
- **After:** animated outcome icon (check / warning / error) that springs in
  and is announced by its localized result title; collapsible Skipped /
  Failed sections listing file names with reasons; undo shows a busy
  indicator and then a localized "restored N, failed M" card.
- **Rationale:** the outcome must be legible in one glance and every
  non-success must be explainable (the reasons come straight from the move
  log).

### History
- **Before:** static list of job cards.
- **After:** live search field (localized status label, date text, auto tag)
  with an empty state when nothing matches; staggered card entry.
- **Rationale:** with retention up to 30 days the list grows; search beats
  scrolling.

### Error Library
- **Before:** fixed-height chip rows, grouped cards, export/clear in the top
  bar.
- **After:** export moved to an **extended FAB with the confirmation
  dialog** (a deliberate sheet-style confirmation); grouped cards enter with
  the capped stagger; list bottom padding clears the FAB; chip rows, masking
  and grouping are unchanged.
- **Rationale:** export is an action on the whole library and deserves
  primary placement; the confirm dialog protects against accidental sharing
  of diagnostics.

### Settings / AutoRules / About / Language
- **Before:** grouped list rows with icons and supporting text.
- **After:** consistent grouped cards with per-category icons and
  explanatory subtitles on every toggle (verified row-by-row); data-size row
  now computes off the main thread; no layout redesign — the pass here was
  consistency and responsiveness.

### Onboarding
- **Before:** 3 pages via back/next buttons; static dots.
- **After:** `HorizontalPager` with real swipe; animated indicator dots
  (spring size change); final CTA unchanged; skippable; state survives
  rotation via pager state.
- **Rationale:** swipeable pagers are the expected mobile onboarding idiom.

## 5. Deliberate trade-offs

- **Swipe-reveal actions** on History/Error Library rows were designed and
  deliberately deferred: hidden actions discover poorly under TalkBack and
  conflict with the pager gesture language introduced on Onboarding. The
  same actions are available as visible, ≥48dp targets. Documented as a
  decision, not a TODO.
- **Preview sticky headers** were evaluated for the wizard preview list; the
  preview is a capped (≤100-row) plain list grouped by destination folder
  text, so sticky headers add complexity without measurable benefit at that
  size.
- One icon family throughout: Material Icons (the extended set already in
  the dependencies); no mixed icon metaphors introduced.

## 6. Verification

Visual results are **verified by code and tests only**: semantics-tree
layout contract, resource parity in 5 locales, reduced-motion paths, and
95-green unit suites. Human confirmation of rendering, haptics feel, and
motion smoothness belongs to the manual device checklist in
`docs/AUDIT-1.1.0.md`.
