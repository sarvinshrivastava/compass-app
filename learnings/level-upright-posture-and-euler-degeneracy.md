# The bubble level covered one posture, and `roll` cannot cover the other

> **Rule:** for any tilt reading taken while the phone is upright, derive the angle from the gravity vector — never from `getOrientation`'s roll, which is degenerate at exactly that pitch.

Found 2026-09-14 from a user question: "the level is only on one plane — is this
by design?" It was not by design. It was unfinished, and the obvious completion
is wrong for a reason worth writing down.

## What was actually missing

`BubbleLevelIndicator` is a **bullseye**: it reads pitch *and* roll as a 2-D dot,
so both axes of that one posture were always covered. The gap was not an axis,
it was a **posture**:

| Posture | Question | Instrument |
|---|---|---|
| Flat on a surface | which corner is high? | bullseye (had it) |
| Held against a wall / frame | is this edge plumb? | single-axis rule (missing) |

Held upright, the bullseye's `normY = pitch / 45f` clamps to 1 and the bubble
parks dead against the rim reporting nothing — so the screen looked broken
exactly when someone held the phone against a door frame, which is the second
most obvious thing to do with a level app.

## Why you cannot just feed `roll` to the new instrument

`SensorManager.getOrientation()` returns a **Z-X-Y Euler decomposition**. Like
every three-angle decomposition it has a gimbal lock, and for this one it sits
at pitch = ±90° — which is precisely the upright posture the new instrument
exists for. Approaching it, roll becomes numerically unstable; at it, roll and
azimuth describe the same rotation and the split between them is arbitrary.

Note this is a *different* failure from the remap problem already documented in
`sensor-orientation-and-remap.md`. That one is about `remapCoordinateSystem`
redefining what pitch and roll mean. This one bites even with no remap in sight:
the decomposition itself stops carrying the information.

## The fix: read where "down" falls in the plane of the screen

No decomposition, so no singularity — just the direction of gravity projected
onto the screen plane:

```kotlin
// Android reports +G along the axis pointing UP, not along gravity's pull.
// Upright plumb:      g = (0, +G, 0)
// Rolled CW by θ:     g = (-G·sinθ, +G·cosθ, 0)
internal fun screenRoll(gravity: FloatArray): Float =
    Math.toDegrees(atan2(-gravity[0].toDouble(), gravity[1].toDouble())).toFloat()
```

### This was wrong twice, and both times something downstream hid it

Worth recording because the *hiding* is the interesting part:

1. First as `atan2(-x, -y)`, from a derivation with gravity pointing *down* the
   device axes. That is a constant **180°** error — and `foldToQuarterTurn`
   folds a half-turn to exactly the same answer, so the readout was perfect.
2. Then as `atan2(x, y)`: right magnitude, **inverted sign**. The `OFF PLUMB`
   readout takes `abs()`, so the number stayed right and only the *drawn rule*
   was wrong — mirrored about the vertical. Subtle at small angles, and
   invisible entirely unless you hold the phone against something slanted and
   look at which way the line leans.

> **Rule:** derive the gravity vector from "the accelerometer reads +G along the
> axis pointing up" every single time. Never from what the implementation does.

A test built by copying the implementation's convention is **circular** — it
proves self-consistency, not correctness, and it passes just as happily when
both halves are wrong together. The first version of `ScreenRollTest` did
exactly this, as did a hand check on an emulator where the injected
`sensor set acceleration` vector was itself built from the wrong convention.
Two green checks, one real bug.

- It is **meaningless when the phone is flat** — `g.x` and `g.y` are both near
  zero there and the angle is pure noise. `Constants.Level.UPRIGHT_ENTER_DEG`
  gates its use; do not read it outside the upright posture.
- It resolves well below the 0.4° the plumb readout calls level — pinned by
  `ScreenRollTest`, which builds the gravity vector for a known rotation and
  checks the function recovers it.

## Fold to the nearest quarter turn

Someone holding the phone sideways or upside down against a frame is asking the
same question about the same edge. "170° off plumb" is arithmetically true and
useless; `foldToQuarterTurn` maps [-180, 180] onto [-45, 45] so the answer is
"10° out" either way.

## The posture switch needs hysteresis, like everything else here

`LevelPostureGate` is `ArTriggerGate` minus the dwell: enter above 60°, fall back
below 45°. A bare threshold swaps the whole instrument under the user's hand on
sensor noise — the same bug, in the same shape, as the old bare 45° AR trigger.

No dwell, though, and that asymmetry is deliberate: AR entry replaces the screen
and opens a camera, so it should be reluctant; this only changes which of two
drawings of the same data is showing, and a delay there reads as lag rather than
as deliberation.
