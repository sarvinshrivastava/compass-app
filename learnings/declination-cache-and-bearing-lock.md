# Caching declination without caching location

> **Rule:** persist the declination angle and its timestamp — never the latitude and longitude it came from.

## The feature

A real review asked for it directly: *"It does not remember its last declination
for use."* Before this, true north was unavailable on every cold start until a
fresh fix arrived, and permanently unavailable to anyone who had since denied
location — even though the correct value for their city was already known.

## The constraint that shapes the implementation

`DeclinationRepository` already carries the rule in a comment: *"Only the
derived correction leaves this function — never the position it came from."*
A cache keyed on latitude and longitude would be the obvious design and would
quietly turn `compass_prefs` into a location history, which contradicts both the
privacy policy and the Data Safety declaration.

So the cache stores two values and no more:

```kotlin
KEY_LAST_DECLINATION     // degrees
KEY_LAST_DECLINATION_AT  // System.currentTimeMillis()
```

Freshness is bounded by **time instead of distance**, which is the trade the
privacy rule forces. Thirty days is the chosen bound: declination drifts about
a degree over a few hundred kilometres and a fraction of a degree per year, so
the only person it is meaningfully wrong for is one who has flown somewhere —
and a live fix overwrites it within seconds of the app opening anyway.

Use `System.currentTimeMillis()` here, not `SystemClock.elapsedRealtime()`.
That is the opposite of the rule for the sensor watchdog, and for the opposite
reason: the watchdog measures a short interval and must not be disturbed by a
clock correction, while this must survive a **reboot**, which resets
`elapsedRealtime` to zero. Guard the subtraction with `age in 0..MAX` so a
backwards clock correction discards the value rather than yielding a negative
age that passes a naive `< MAX` test.

## Bearing lock: the lock lives in display space, so true north has to move it

`lockedBearing` is captured from `_azimuth`, which is the *displayed* heading —
magnetic or true depending on the toggle. That means toggling true north
changes what 0° means underneath a lock that is already set.

Without a fixup, tapping the MAG/TRUE tag mid-walk silently rotates the
direction the user is following by the declination. Both numbers still look
entirely plausible afterwards, which is what makes it dangerous. `toggleTrueNorth`
therefore shifts the lock by the same declination it shifts the heading by.

The alternative — storing the lock in magnetic space and converting on the way
out — is arguably cleaner, and was rejected only because the conversion would
then have to be repeated at every read site. If a third consumer of
`lockedBearing` ever appears, reconsider.

## The readout must use the shortest arc

A lock at 002° while heading 358° is **4° right**, not 356° left. This is the
fourth place in the app that needs `shortestArcDelta` — after the rose's
animation accumulator, the AR label accumulator, and the heading deadband.
`BearingLockMathTest` pins the seam cases.
