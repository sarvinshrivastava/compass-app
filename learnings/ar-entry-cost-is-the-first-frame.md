# The AR entry delay is the camera's first frame, not the binding

> **Rule:** warming `ProcessCameraProvider.getInstance()` is not a camera prewarm — only `bindToLifecycle` starts the clock that matters.

Measured 2026-09-15 on a Pixel 9a, after a first attempt at this optimisation
was shipped, tested by the owner, and reported as having changed nothing.

## The numbers

Instrumentation inside the bind `DisposableEffect`, logged relative to the
effect starting (grep `ar-timing`):

```
ar-timing effect-start        +0ms
ar-timing provider-resolved  +78ms
ar-timing bound              +96ms
ar-timing streaming         +507ms
```

- Resolving the provider: **78 ms**
- `bindToLifecycle` itself: **18 ms**
- Camera producing its first frame: **411 ms** — 81% of the total

## Why the first attempt failed

The original fix warmed only `ProcessCameraProvider.getInstance()` during the
AR trigger's dwell, deliberately *not* binding, so that the platform camera
indicator would not light while the user was still looking at the compass.

That was a considered tradeoff and it was also nearly a no-op: it targeted the
78 ms stage of a 507 ms cost. The ceiling on that approach was ~15% and the
user could not feel it.

**The lesson generalises past CameraX:** when optimising a pipeline, measure
which stage owns the time before picking one to optimise. "Getting the handle"
and "the hardware waking up" are different stages, and only one of them is
expensive. An optimisation aimed at the cheap stage is indistinguishable from
no optimisation at all, and costs a release cycle to discover.

## What actually works

Two changes, and they compose:

1. **Bind during the dwell**, not after the gate opens. Nothing but an actual
   bind starts the sensor warming. This does light the camera indicator a few
   hundred milliseconds early — an explicit, accepted cost, taken only once the
   user has already raised the phone past the AR threshold.
2. **Gate the visual transition on `PreviewView.previewStreamState`.** Crossfading
   the moment the gate opens shows a black rectangle for half a second, which
   reads as the app hanging. Holding the compass until `STREAMING` means AR
   arrives fully formed.

Guard rail on (2): a failed bind must also report "streaming", or the user is
stranded on the compass forever waiting for a frame that is never coming. See
the `onStreamingChanged(true)` in the bind's catch block.

## Testing note

`previewStreamState` is a `LiveData`, so observing it needs a `LifecycleOwner`
and the observation must be set up after the bind succeeds. It fires
`IDLE` → `STREAMING`; only the latter means pixels are on screen.
