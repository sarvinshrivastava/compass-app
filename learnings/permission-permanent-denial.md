# "Permanently denied" is not a state Android reports

> **Rule:** never branch on `isGranted` + `shouldShowRationale` alone — pair them with the persisted marker in `util/PermissionAskLog.kt`, or the denied-for-good user gets a button that cannot work.
> **Applies to:** `app/src/main/java/com/pubpeb/compass/util/PermissionAskLog.kt`

## The trap

On Android 11+ a permission denied twice can no longer be requested from inside
the app. The platform reports that state as:

- `status.isGranted` — **false**
- `status.shouldShowRationale` — **false**

Which is byte-for-byte the same pair it reports **before the first ask**. There
is no API that distinguishes them.

A `when` with the three obvious branches (granted / rationale / else) therefore
routes the permanently-denied user into the first-run branch. The screen shows
first-run copy and a "Grant Camera" button, and
`launchPermissionRequest()` returns denied without ever drawing a system dialog.
The user taps a control that visibly does nothing, forever, with no explanation.

Both permission call sites in this app had it: `ArOverlayScreen` (camera) showed
the dead button; `CompassScreen` (location) was worse — the MAG tag simply never
flipped to TRUE and nothing on screen said why.

## The fix

Persist the third fact yourself. `util/PermissionAskLog.kt` writes a
per-permission boolean into the existing `compass_prefs` on the way into
`launchPermissionRequest()`:

```kotlin
permanentlyDenied = !isGranted && !shouldShowRationale && hasAsked
```

In that state the only route is `Context.openAppSettings()` —
`ACTION_APPLICATION_DETAILS_SETTINGS` with `FLAG_ACTIVITY_NEW_TASK`, wrapped in
`runCatching` because a few OEM builds ship no activity for it.

- **`rememberSaveable` is not sufficient.** It survives rotation and process
  death within a task, but not a cold start — after which the denied user is
  back on the dead button.
- Keep an observable mirror (`var hasAsked by remember { mutableStateOf(...) }`)
  next to the persisted value, or flipping the marker will not recompose.

## It also biases analytics

`onLocationOutcome` was keyed on `shouldShowRationale || granted`. A permanent
denial clears the rationale flag, so the single outcome most worth counting was
the one never counted, and the "did asking for COARSE improve grant rate?"
metric read high. Key that reporting on the persisted marker instead.
