# The AR overlay filtered one axis and not the other

> **Rule:** every value that positions something in the AR overlay gets a second-stage filter — if you add a new one, filter it, and never point `adaptiveLowPass` at a bearing.
> **Applies to:** `app/src/main/java/com/pubpeb/compass/util/AngleUtil.kt`, `app/src/main/java/com/pubpeb/compass/util/SmoothingUtil.kt`

Found 2026-09-14 from a user report: "the direction tags wiggle too much in the
vertical direction". Horizontal was fine. That asymmetry was the whole clue.

## The bug

Label **X** went through three stages of smoothing:

1. `SensorRepository` low-pass on the raw sensor arrays, α = 0.15
2. `CompassViewModel` second-stage LPF on the bearing, α = 0.05 (`arAzimuth`)
3. `animateFloatAsState` spring in `CardinalOverlay`

Label **Y** went through two — stage 2 was simply missing:

```kotlin
// CompassViewModel.onSensorData, before
_cameraElevation.value = data.cameraElevationDeg   // raw, straight through
```

So the same hand tremor was filtered on one axis and not the other. Worse, the
unfiltered axis is the noisier one: wrist flexion produces more angular noise in
pitch than in yaw for a hand-held phone.

**The gain was never the problem** and it is worth not "fixing" it —
`vFovHalfDeg = safeFovHalfDeg * (heightPx / widthPx)` deliberately gives the
same degrees-per-pixel on both axes. Both axes move ~18 px per degree on a
1080 px-wide portrait screen. That part is correct.

## Why the fix is not "add α = 0.05 to Y as well"

The horizon has a constraint the bearing does not: it is checked against the
camera image directly behind it. A flat heavy alpha holds the labels still and
then lets them visibly swim behind the scene during a deliberate tilt, which
reads as worse than the wiggle.

So `util/SmoothingUtil.kt` picks the alpha per sample from the size of the step:
noise gets the slow alpha, real movement gets the fast one.

## The cliff — the part that is not obvious

The first version switched **hard** at the threshold:

```kotlin
val alpha = if (abs(delta) >= fastThreshold) fastAlpha else slowAlpha   // wrong
```

That looks equivalent and is not. A large move closes most of the gap at
`fastAlpha`, and then the residual falls *under* the threshold and the last two
degrees close at tremor-rejection speed. On screen: labels arrive about 2° low
after a quick tilt and sag into place over the next second.

The ramp has no cliff, because the closing speed decays with the residual
instead of falling off it:

```kotlin
val t = (abs(delta) / fastThreshold).coerceIn(0f, 1f)
val alpha = slowAlpha + (fastAlpha - slowAlpha) * t * t
```

Squared, not linear, so the small end stays genuinely slow: ±0.5° of tremor
against a 2.5° threshold lands at t² = 0.04, barely off `slowAlpha`, where a
linear ramp would already have tripled it.

A unit test caught this, not a device — `SmoothingUtilTest`'s convergence case
failed against the hard switch. Keep the "final approach does not collapse to
the slow alpha" test; it is the only thing standing between this and a
plausible-looking one-line "simplification" back to the cliff.

## `adaptiveLowPass` is for non-cyclic signals only

There is no shortest-arc handling in it. Elevation, pitch and roll live in
[-90, +90] with no seam, which is what makes the plain subtraction safe. Point
it at a bearing and a blend of 1° and 359° gives 180° — see
`util/AngleUtil.kt`. The bearing path keeps its own `shortestArcDelta` filter
for exactly this reason, and the two must not be merged.
