# Sensor orientation, remapping, and what each value actually means

## `remapCoordinateSystem` silently redefines pitch and roll

`SensorManager.remapCoordinateSystem(m, AXIS_X, AXIS_Z, out)` is needed to get a
correct **azimuth** when the phone is held upright — without it, gravity aligns
with device Y instead of Z and `getOrientation()` returns a wrong heading.

But the remap also changes what `orientation[1]` (pitch) and `orientation[2]`
(roll) mean. Feeding those to a bubble level produced a **~90° discontinuity**
the instant the device crossed the remap threshold — the bubble slammed from one
rim to the opposite rim.

- **Rule:** derive azimuth from the *remapped* matrix, and pitch/roll from the
  *unremapped* matrix. Call `getOrientation()` twice, on two different matrices.
- Symptom that points here: a tilt/level readout that is smooth up to some angle
  then teleports.

## `|gravity.y| > |gravity.z|` is a hard 45° threshold in disguise

This expression reads like a cheap "is the phone upright" test. It is exactly
`tan(tilt) > 1`, i.e. a **45° boundary with zero hysteresis**.

- 45° is the angle people naturally hold a phone at while walking, so any UI
  bound to it oscillates continuously on sensor noise.
- Also ignores roll: it only measures the Y/Z split, so a rolled device
  misclassifies.

Prefer a continuous magnitude:

```kotlin
// Angle between the screen normal and vertical, 0..90°.
// 0 = flat on either face, 90 = upright.
val ratio = (abs(g[2]) / magnitude).coerceIn(0f, 1f)
val tiltFromFlat = Math.toDegrees(acos(ratio).toDouble()).toFloat()
```

Taking `abs(g[2])` folds face-up and face-down onto the same value, which is what
"how upright is this phone" should mean. Then apply hysteresis + dwell in the
consumer (enter > 65°, exit < 50°, 800 ms dwell) rather than a bare threshold.

## `onAccuracyChanged` is the only trust signal you get

Both `SensorRepository` and the (now deleted) widget worker had
`override fun onAccuracyChanged(...) = Unit`. That throws away the only thing
the platform tells you about whether the magnetometer is currently lying.

- Magnetometer errors of 20–90° are routine near cars, speakers, magnetic phone
  mounts, steel structures, and laptops.
- Only the **magnetometer's** accuracy matters for heading; the accelerometer's
  is effectively always high. Filter on `sensor.type == TYPE_MAGNETIC_FIELD`.
- `SENSOR_STATUS_UNRELIABLE` is the correct initial value — never start at HIGH.

## Silence is a state, and it needs a watchdog

`getRotationMatrix()` returning `false` causes the flow to simply stop emitting.
There is no error, no exception, no callback. The UI freezes at whatever it last
showed — in this app, a confident `0° NORTH` forever.

Any sensor-backed `StateFlow` needs a staleness watchdog:

```kotlin
// >3 s with no emission == no signal, reported as its own accuracy state
if (SystemClock.elapsedRealtime() - lastEmissionAt > 3_000L) {
    _accuracy.value = CompassAccuracy.NO_SIGNAL
}
```

Use `SystemClock.elapsedRealtime()`, not `System.currentTimeMillis()` — the
latter jumps when the clock is corrected.

## Shortest-arc blending for anything cyclic

A naïve weighted average of 1° and 359° gives 180°, i.e. exactly backwards. Every
filter, animation target, and deadband over a compass bearing needs the signed
shortest arc:

```kotlin
val delta = ((new - old + 540f) % 360f) - 180f   // always in (-180, +180]
```

Verified: `359°→0°` gives `+1°`; `0°→359°` gives `−1°`.

This applies to three separate places here — the rose's animation accumulator,
the AR label accumulator, and the heading readout's 0.5° deadband. Missing it in
the deadband would make the readout freeze near due north.
