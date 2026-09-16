# A waiting indicator derived from "no result yet" never stops

> **Rule:** drive any waiting affordance from the in-flight operation itself, cleared in a `finally` — never from the absence of its result.

## The shape of the bug

`NorthModePill` animated a three-dot ellipsis while a location fix was
outstanding. Its condition read:

```kotlin
awaitingNorthFix = trueNorth && locationGranted && !hasDeclination
```

Every term is true when the fetch **fails**. `DeclinationRepository` returns
`null` on timeout and `updateDeclination()` returned early without touching any
state, so `_hasDeclination` stayed `false` forever — and the dots cycled for the
life of the process, on the app's main screen, with nothing behind them.

Nothing threw, nothing logged, and the unit tests could not see it: the whole
condition lived in the composable.

## What made it hard to spot

- It only appears with **no cached declination**, which after
  `restoreRememberedDeclination()` means a first run or freshly cleared data.
  Every ordinary launch hides it.
- On an emulator no fix ever resolves, so the permanently-spinning state looks
  like the normal emulator caveat rather than a bug.
- `!hasDeclination` reads like "still loading" at the call site. It actually
  means "we do not have one", which includes "we tried and failed".

## The fix, in three parts

1. **Bound the state to the operation.** `_fetchingDeclination` set `true` before
   the call and cleared in a `finally`, so it ends whichever way the fetch goes —
   at most `DeclinationRepository.TIMEOUT_MS`.
2. **Give failure a voice.** `_declinationFailed` drives a static
   `NO LOCATION FIX` note that is tappable to retry. Before this, a deliberate
   tap on TRUE bounced silently back to MAG after ten seconds and nothing ever
   retried.
3. **Gate the report on an explicit request.** `Constants.Prefs.KEY_TRUE_NORTH`
   defaults to **on**, so gating on the preference alone put a red warning in
   front of a first-run user who had asked for nothing. A compass showing
   magnetic north without a location is this app working as designed — see
   "Location is never a gate" in `CLAUDE.md`. The note needs a real tap on TRUE
   *and* no declination to fall back on.

## Related

A progress animation inside a two-state control was the wrong idiom regardless
of the bug: the rose is meant to be the only moving thing on that screen.
Waiting and failing are both reported once, in words, on the note line below the
pill. See `learnings/compose-semantics-and-layout-traps.md` for the other half of
why that line — not the control — is where status belongs.
