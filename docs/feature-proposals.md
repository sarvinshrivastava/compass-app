# Minimal Compass — feature proposals

Prepared 2026-09-14, against `redesign/audit-p0-p1` at `b586e28`.
Scope: what to build after 1.0.0, ranked, with the arguments for and against each.
This document proposes; it changes no code.

---

## Before anything else: where the app actually is

`app/build.gradle.kts` still reads `versionCode = 1`, `versionName = "1.0.0"`, and
`site/index.html` says the app "is preparing for release on Google Play". The last
six commits are all launch preparation — store assets, the privacy policy, the Data
Safety CSV, release notes. Nothing in this backlog should be read as more urgent
than shipping the thing.

That matters for how you use this document. Every ranking below is an informed
guess about users you have not met yet. The moment 1.0.0 is live you will have
Play Console's search-term report, your actual install-to-uninstall curve, and the
seven Firebase counters in `telemetry/Telemetry.kt` — three of which
(`ar_opened`, `level_opened`, `accuracy_state`) were deliberately built to answer
exactly the questions this backlog has to guess at. `store-and-monetization.md`
already says it plainly: ASO is the only compounding channel, and Play's own
search data is free, real, and better than any estimate. Ship, wait thirty days,
then re-rank this list against evidence. If you build the top three items below
before you have a single review, you will have spent your best weeks guessing.

---

## The rules this backlog obeys

These are not invented for this document. Every one of them is already enforced
somewhere in the codebase or the launch docs, and a proposal that breaks one has to
say so out loud and defend it.

**1. Never show a number you cannot stand behind.** This is the app's whole
identity. It is why `CompassAccuracy` starts at `UNRELIABLE` and never at `HIGH`,
why `watchForStaleSensor()` synthesises `NO_SIGNAL` out of silence, why
`confidenceNote()` in `ui/CompassScreen.kt` returns `null` when things are fine so
that a warning colour never becomes background noise, and why the widget was
stripped back to a launcher tile. Any feature that produces a number must also
produce an honest account of how much that number can be trusted.

**2. Nothing is gated on a permission.** `CompassViewModel` renders a magnetic
heading on the first frame with nothing granted. Location only ever upgrades the
reading. `DeclinationRepository.fetchDeclination()` returns `null` on every failure
path rather than propagating an error. A feature that requires a permission before
the app is useful is not shippable here.

**3. The pager stays at two pages.** `Constants.Ui.PAGE_COUNT = 2`, and
`MainScreen` documents why the bottom navigation bar was deleted: 80 dp of the
compass screen given away to promote a secondary instrument. New capability
belongs as a *mode on the compass*, not as page three. The moment a fourth dot
appears under the rose, the app has become the thing it was built against.

**4. Red is north, and nothing else.** Stated in `CLAUDE.md`, enforced in
`CompassRose.drawCardinalLabels` and in the choice of a neutral `lubber` colour for
the fixed index. Every new marker needs its own palette entry in `AppPalette`
(`ui/theme/Theme.kt`), defined in all three palettes.

**5. Sensors follow the UI.** The `SharingStarted.WhileSubscribed(5_000)` +
`repeatOnLifecycle(STARTED)` arrangement in `CompassViewModel` and `MainScreen` is
the single largest battery decision in the app. Any new hardware subscription —
Play Services orientation, a second sensor, a location listener — has to live
inside the same lifetime, with its own `awaitClose`.

**6. The store copy and the privacy policy are part of the build.** `site/privacy.html`
says, in terms: "Your coordinates exist only in memory, for as long as the calculation
takes. They are never stored, logged, or transmitted anywhere."
`docs/launch/play-console-answers.md` says the most common rejection cause is a Data
Safety form that disagrees with the policy. Several proposals below would make that
sentence false. Each one flags it.

**7. Restraint is the differentiation, and the position is already occupied at the
other end.** [Trail Sense](https://github.com/kylecorry31/Trail-Sense) is free,
open source, MIT-licensed, entirely offline, and already ships beacons, backtrack,
paths, barometric weather prediction, sun and moon times, a flashlight and a photo-map
([F-Droid listing](https://f-droid.org/en/packages/com.kylecorry.trail_sense/)). You
cannot out-feature it and there is no reason to try. Minimal Compass's only defensible
ground is being the app that does less, better, and tells the truth about it. Every
feature below is scored against that, not against a competitor's bullet list.

---

## The market finding that should change how you think about all of this

I went in expecting to confirm `store-and-monetization.md`'s pessimism — a saturated
category, immovable incumbents, 2,000 first-year installs. Half of that held. One half
did not, and it is the more important half.

**Play's "compass" search results are not ranked by installs.** Scraped live, the US
top-15 for the query runs: Digital Compass (44.4M installs) — then **Compass by Fatih
Balsoy, which has 16,711 installs**. Ad-free, no in-app purchase, 4.9 stars, released
July 2024, updated this month. It sits above Compass Steel (7.7M), Digital Compass by
KTW (18.4M) and melon soft's Compass (31.1M). Fourth place goes to an app literally
named **"Compass (without Ads)"** at 461K installs. India's top-15 has the same shape.
Meanwhile **Pixel Compass** went from zero to 68,078 installs in about fourteen months
on exactly that profile, and the highest-rated incumbent in the category is from 2014.

Three of the US top-ten carry no ads at all. The 17-million-install ADMAVA compass sits
at **3.4 stars across 84,000 reviews** and is from 2015.

Rating, recency and a clean exact-match title are visibly outweighing install count
here. A plausible cause is that Google removed roughly half the apps on Play between
January 2024 and April 2025 — about 3.4 million listings down to 1.8 million, including
2.36 million blocked for policy violations — which would have thinned the long tail and
reweighted whatever survives. That is inference, not something Google has confirmed.

**What this means for this backlog:** the ceiling is higher than the launch report
assumed, and the lever is not features. It is rating, freshness and the absence of ads
— all three of which this app already has or is one release away from having. Every
feature below should be judged partly on whether it produces a *reason to ship an
update and a reason for someone to leave five stars*, because those appear to be the
ranking currency in this category.

Two footnotes worth knowing. A dormant app called **"Minimal Compass"**
(`com.roshan.compass`) already exists on Play with a few hundred downloads — negligible,
but it exists, and the launch doc's preferred title is "Minimal Compass: True North".
And Google's July 2024 policy against apps with **"limited functionality and content"**
is the rule that helped delete those 1.6 million listings; it describes the shape of a
single-screen compass uncomfortably well. The AR overlay, the bubble level and the
widget are what put this app on the right side of it. That is an argument against
stripping *further*, and it is worth having in mind while reading the "do not build"
section.

---

## What the category's users actually complain about

This section was rewritten after reading **10,162 scraped Play reviews across 13
compass apps**, of which 1,724 are one-to-three-star with text. Two of my own prior
assumptions did not survive it, and they are marked.

**Monetisation is the number one complaint, and it is not close.** Across the 805
substantive low-star reviews (forty characters or more), ads and paywalls account for
**21.4%** and privacy or permissions for **18.0%**, against **7.2%** for accuracy. Nine
of the ten largest compass apps carry a "Contains ads" label. The reviews are not
measured about it:

> "All this app does is open loud full screen ads that you can't get out of… What a
> piece of junk."

> "$19/week after free trial for an ad-free compass is INSANE."

> "Recently banner ads began appearing at the bottom, **covering up compass
> information**. No apparent way to close the ads. App is now useless."

There is a distinct and even angrier sub-genre about apps that were clean and then were
not — *"I purchased this app without ads. It worked accurately for years. Now, there are
ads appearing every time I open it. To remove the ads, payment is demanded again."*
Everything in `store-and-monetization.md` about never adding ads is correct, and this is
the evidence for it. It also means the one-time-unlock plan in that document has to be
introduced very carefully, because this audience has been burned by exactly that shape
of change.

**Privacy has overtaken classic permission-greed, and the modern form is the consent
wall.** The old complaint still exists — *"A compass does not require a camera"* —
but the 2025–26 version is about TCF consent dialogues and consent-or-pay:

> "**210 vendors** with legit interest in my data? no way..."

> "Why does the new update suddenly have all these untogglable permission about internet
> access etc? **This is a magnetic field sensor based compass app! It doesn't need
> internet to work.**"

This app has no consent dialogue, no ad SDK, no internet requirement for its core
function, and a manifest that strips the advertising ID. That is worth more in the
listing than any feature in this document, and the full description should say it in
the user's own words rather than in ours.

**Accuracy is real but smaller than its reputation, and the sharp edge is
non-repeatability.** Errors cluster at clean 90° and 180°, and what makes people
furious is not being wrong, it is being differently wrong each time:

> "it doesn't even remotely point North and it will show you something different each
> time you open up the app."

> "held the phone in three different positions at three different locations and got
> three completely different directions"

One review is effectively a bug report for a quadrant or remap fault: a compass correct
until 270°, then "flip by 125 degrees… It will stay in error by 125 degrees until you
shut down the app." That is the exact failure class
`learnings/sensor-orientation-and-remap.md` exists to prevent. The underlying physics is
also independently documented: a 2022 study across seventeen devices found typical
deviation of five to ten degrees and, more damningly, calibration that decayed within
an hour, concluding that phone compasses are not equivalent to professional instruments.
Note that the ad-free FOSS compasses get the same one-star accuracy reviews as the
ad-laden ones — users blame the app regardless of its business model, so a clean
monetisation record buys you nothing here. This is what item 2 is for.

**Calibration confusion is the most actionable gap in the entire corpus**, and one
reviewer wrote the specification for item 3 without meaning to:

> "I have No way of determining what the problem is because the graphic instruction is
> ONLY one picture with no further information as to where the calibration should be
> done, indoors or outdoors, the number of rotations required (1 or 5 or 10). NO
> suggestions for possible sources of interference… **No mention as to whether the
> calibration instruction would disappear as an indication that the compass was
> calibrated or not**"

Every one of those is a design requirement, and the last one — *does the prompt go away
when I have succeeded* — is the one most apps miss. But there is a counter-signal that
matters just as much: **over-prompting is its own one-star**. *"Just hate the constant
calibration checks, please fix this, and I'll leave a 5 star review."* Build the coach
behind a tap on an existing warning, as item 3 proposes; do not build an interstitial.

**A device with no magnetometer is a small complaint with brutal severity**, about one
percent of low-star reviews, and the grievance is always the same: the app fails
*silently*.

> "it may be that my phone lacks a magnetometer but **other compass apps tell you that
> they wont work and this one is just dead**"

> "**As is the crude compass just sits there and looks dumb.** … if it detected that the
> phone it was running on did not have a magnetometer, and then threw an error, it would
> probably be an OK app."

That is this app's current behaviour, verbatim, and it is the first item in the
non-feature section for that reason.

**Assumption refuted: nobody complains about cluttered UIs.** I expected this to be a
theme and wrote it into an earlier draft. It is **four hits in 10,162 reviews, and all
four are actually about ads** — *"way too much bloat. ads, pop ups, permissions."*
"Too few features" is the more common complaint. When a reviewer in this category says
"minimal" they mean no advertising and no data collection; they do not mean fewer
capabilities. This is an important correction to how the app talks about itself: the
restraint is a **trust** story, not a **simplicity** story, and the listing should sell
it as the former.

**Assumption refuted: battery drain is not a category problem.** Twenty-four low-star
mentions total, and twenty-one of them belong to a single app, Compass & Altimeter,
between October 2025 and March 2026, with users naming the mechanism themselves —
*"leaves location service still running when you exit"*. That is the precise bug the
`WhileSubscribed` + `repeatOnLifecycle` arrangement in `CompassViewModel` and
`MainScreen` was built to prevent. Another invisible win.

**Three smaller findings that each map onto something this app already did.**
Dark-only interfaces draw real complaints outdoors — *"Every other app increases the
screen brightness when outside. This thing dims the screen so it's unreadable. It's not
good when I only use it outside"* — which is Daylight mode's entire justification, and
it currently gets one screenshot and no sentence in the description. Widgets are wanted
and universally broken, every live-data implementation in the sample failing the same
way. And someone has already asked for item 4 by name: *"It does not remember its last
declination for use."*

**What people actually ask for**, counted across 2,484 reviews of forty-five characters
or more, at every star rating: **altitude or altimeter, 95 mentions** — by a distance
the most-requested thing in the category. Then true north and declination (53),
coordinates in lat-long, UTM or MGRS (36), **bearing to a waypoint or holding a bearing
(17)**, offline operation (12), a widget (8), a floating overlay (8), night or red mode
(6), landscape (6), a bubble level (5), Wear OS (4), camera or AR view (3). The
altimeter number is dealt with in "do not build", because it is the clearest case in
this document of a large, genuine demand that this app still should not serve.

And the meta-wish, which is really the product brief:

> "You want a compass? This is a virtualized Compass, it does EXACTLY what a compass
> should, and **NOT A SINGLE THING MORE**. No ads forcing me to wait 5-20 seconds, no
> banners, no click throughs, no need to log in… IT. IS. A. COMPASS. Perfection."


---

# Build next

Four items. Together they are a credible 1.1 and 1.2, they need no new runtime
permission, and three of the four make the app's existing honesty claim *visible*
rather than merely true.

---

## 1. Bearing lock — "hold this bearing"

**Pitch.** Tap once to nail the bearing you are on; the app then tells you how far
off it you have drifted, and ticks when you come back onto it, so you can walk a
line instead of squinting at a number.

**Why it belongs in this app.** This is not a new instrument bolted onto a compass —
it is the thing a compass is *for*. Every physical baseplate compass has a rotating
bezel for exactly this, and the reason is that a bearing you have to keep re-reading
is a bearing you will lose. The app's own tagline is "open it, read the bearing, put
your phone away"; bearing lock is the feature that makes putting the phone away
actually possible, because the phone can now tell you you have drifted without you
staring at it. It adds one control and one line of text, needs no permission, no
network, no new dependency, and no new page. It is the most on-philosophy feature in
this document.

It also fixes something slightly embarrassing: right now the AR overlay labels eight
cardinal directions and nothing else. If you sight a distant ridge at 247° there is
nothing in the app that will remember 247° for you.

**User value.** Hikers, hunters, geocachers, anyone following a bearing off a paper
map, anyone aligning a satellite dish or a solar panel to a specified azimuth, and
anyone sighting a feature and wanting to walk toward it.

**The competitive position here is better than I expected and this is the strongest
single result in the research.** Across the descriptions of every major compass app on
Play, bearing lock appears in exactly three: Spyglass, Trail Sense, and NorthPin. It is
absent from Compass Steel, Compass: Accurate, Digital Compass by Axiomatic (44 million
installs), melon soft's Compass (31 million), Digital Compass by KTW (18 million) and
Geodetic Compass. Seventeen reviews in the scraped corpus ask for a bearing to a
waypoint or a way to hold a heading. So: a genuinely absent capability in the entire
mainstream tier, with demonstrated demand, that costs a day and needs no permission,
no network and no dependency. That combination does not come up often.

**Implementation sketch.**

- `viewmodel/CompassViewModel.kt` — add `private val _lockedBearing = MutableStateFlow<Float?>(...)`
  seeded from prefs, plus `fun lockBearing()` (captures the current *published*
  `azimuth`, not the raw one) and `fun clearLock()`. Add a derived
  `bearingOffset: StateFlow<Float?>` computed with `shortestArcDelta(lockedBearing, azimuth)`.
- `Constants.kt` — `Heading.ON_BEARING_TOLERANCE_DEG` (2° is a sensible start; a
  hand-held phone is not better than that) and `Prefs.KEY_LOCKED_BEARING`.
  SharedPreferences has no nullable float, so store `-1f` for "no lock".
- `util/AngleUtil.kt` — nothing new. `shortestArcDelta` already does the whole job,
  and this is precisely the function whose header comment warns it was once
  copy-pasted at five call sites. Do not add a sixth copy.
- `ui/theme/Theme.kt` — one new `AppPalette` field, e.g. `bearingMark`, defined in
  `NightPalette`, `DayPalette` and `DaylightPalette`. It must not be
  `northLabel`. Something in the green/cyan family reads as "target" without
  colliding with `levelOk`'s meaning on a different screen; amber collides with
  `levelWarn`. This is a real design decision, not a fill-in.
- `ui/CompassRose.kt` — draw a second index *inside* the `rotate(-animatedAngle)`
  block, so it orbits with the card the way north does. The fixed lubber index stays
  where it is; the locked bearing is a property of the world, not of the device.
- `ui/CompassScreen.kt` — `TopInfoBar` gains a fourth icon (a pin or a flag) as the
  discoverable route in; `HeadingReadout`'s third row swaps the `MAG · DECL` line for
  `HOLD 247° · 12° RIGHT` while a lock is active. Tapping the big degree number as a
  fast path is fine as a bonus, but it cannot be the only affordance — an invisible
  gesture on a screen with one visible control is not a feature, it is a secret.
- `ui/ArOverlayScreen.kt` — pass `lockedBearing` into `CardinalOverlay` and emit one
  more chip through the existing `fracX` mapping. This is nearly free and it is the
  best screenshot in the whole backlog: a live camera view with a marker sitting on
  the actual ridge you are walking toward.
- Haptics — `BubbleLevelIndicator` already establishes the pattern: a
  `LaunchedEffect` keyed on a boolean so the tick fires once on arrival and never
  repeats while held. Copy it exactly rather than inventing a second convention.

**Effort: S–M.** No new dependency, no permission, no manifest change, no policy
change. The work is roughly one day of ViewModel plus Canvas plus three UI surfaces,
and half a day of unit tests for the offset maths (which is pure and belongs beside
`AngleUtilTest`).

**Risks and gotchas.**
- *Shortest arc, everywhere.* An offset computed as `locked - azimuth` will tell the
  user to turn 350° left. `learnings/sensor-orientation-and-remap.md` lists three
  existing places this bites; this is the fourth.
- *Use the deadbanded heading.* Compute the offset from `azimuth`, not from
  `arAzimuth` and not from the raw sensor value, or the flat screen and the AR HUD
  will disagree about the same physical bearing — the exact defect the two-azimuth
  split in `CompassViewModel` was introduced to prevent.
- *Red is north.* Covered above, but it is the single easiest mistake to make here.
- *Honesty rule.* If the magnetometer is `UNRELIABLE`, a locked bearing is a
  precise-looking number derived from an untrusted one. The offset line should
  degrade the same way `confidenceNote()` does rather than sitting there looking
  authoritative.
- *Persistence.* Put it in `compass_prefs` via the ViewModel, not in a
  `rememberSaveable` — a bearing you took an hour ago should survive the app being
  swapped out, and `learnings/permission-permanent-denial.md` already documents why
  `rememberSaveable` is the wrong tool for anything that must outlive a cold start.

**Store and ASO.** Earns a fifth screenshot and probably the best one in the set.
Adds `bearing`, `hold a bearing`, `follow a bearing`, `bearing lock` and `sighting
compass` to the description — long-tail terms where, per `store-and-monetization.md`'s
own analysis, the incumbents are weak. It is also the first line of a "what's new"
paragraph that says something a user cares about.

---

## 2. Heading confidence in degrees — "±4°" instead of a four-word warning

**Pitch.** Replace the four-bucket `HIGH / MEDIUM / LOW / UNRELIABLE` model with a
real number — "247° ±4°" — sourced from Google's Fused Orientation Provider, with
the platform rotation vector as a fallback and the current accelerometer + magnetometer
path as the floor.

**Why it belongs in this app.** The app's entire pitch is that it tells you when it
is not sure. Right now "not sure" is a four-state enum that the platform hands over
with no explanation, and the UI renders it as one of three short strings. A
quantified heading error is the same promise, kept properly. It is also the only
claim in this document that no mainstream compass app appears to make, which makes it
both a product differentiator and a listing line.

**And it fixes a correctness bug you do not yet know you have.** The reference page
for `android.hardware.GeomagneticField` — the class `DeclinationRepository` uses to
compute true north — says in its own class documentation: *"This class currently uses
WMM-2020 which is valid until 2025."* The World Magnetic Model is re-fitted every five
years precisely because the field moves; WMM2025 shipped in December 2024 and runs to
2029. Every `TRUE` reading this app has ever produced is derived from a model the
platform itself declares out of date. The error is small in most of the world and not
small everywhere, and there is nothing you can do about it from inside `GeomagneticField`
— it is the platform's table, not yours. The Fused Orientation Provider sidesteps it
entirely, because Google's fusion *"directly incorporates local magnetic declination"*
server-side rather than from a table baked into the device's Android version. For an
app whose listing leads with "Finds true north, not just magnetic north", this is the
most important sentence in this document.

There is a second, less glamorous reason. `sensor/SensorRepository.kt` fuses raw
`TYPE_ACCELEROMETER` and `TYPE_MAGNETIC_FIELD` by hand with a fixed-alpha low-pass
filter and `getRotationMatrix()`. That is the textbook approach, and to be fair to it,
Android's own position-sensors page still presents `getRotationMatrix()` +
`getOrientation()` as the recommended replacement for the long-deprecated
`TYPE_ORIENTATION` sensor. But it uses no gyroscope at all, so it has no way to
reject a transient magnetic disturbance, and it inherits every quirk of the OEM's raw
sensor timing. Google shipped the Fused Orientation Provider in 2024 for precisely
that reason — the announcement says it "compensates for lower quality sensors and
OEM implementations (e.g., gyro bias, sensor timing)" and exists to give "consistent
and reliable device orientation" across manufacturers. When the platform vendor ships
a service because hand-rolled fusion is inconsistent in the field, that is a strong
signal about hand-rolled fusion.

**User value.** "Inaccurate" is the most common substantive complaint in the
category after ads, and a large share of it is not the app's fault — it is a magnetic
case, a car dashboard, or a laptop. An app that says `±3°` in a field and `±25°` in a
car has converted an argument into an explanation. For the hiker taking a bearing off
a map it is also directly actionable: a ±2° reading is worth walking, a ±30° reading
is not.

**Implementation sketch.**

- **The dependency is already there.** `gradle/libs.versions.toml` pins
  `playServicesLocation = "21.3.0"`; the FOP API needs `play-services-location:21.2.0`
  or above. No new artifact, no APK growth, no new permission
  ([announcement](https://android-developers.googleblog.com/2024/03/introducing-fused-orientation-provider-api.html)).
- New `sensor/OrientationRepository.kt` beside `SensorRepository` and
  `DeclinationRepository`, following the same shape: a `callbackFlow` wrapping
  `LocationServices.getFusedOrientationProviderClient(context)`,
  `requestOrientationUpdates(request, executor, listener)`, and
  `awaitClose { removeOrientationUpdates(listener) }`. Build the request as
  `DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()`
  — 20,000 µs, i.e. 50 Hz, and the constant whose own documentation names "compass or
  navigation app" as the example use case. `DeviceOrientation` then gives you
  `getHeadingDegrees()` in [0, 360), **`getHeadingErrorDegrees()` in [0, 180]**, a
  `getConservativeHeadingErrorDegrees()` variant guarded by
  `hasConservativeHeadingErrorDegrees()`, `getAttitude()` as an east-north-up
  quaternion, and `getElapsedRealtimeNs()`
  ([reference](https://developers.google.com/android/reference/com/google/android/gms/location/DeviceOrientation)).
  Bump `playServicesLocation` to 21.4.0 while you are in there.
- `sensor/CompassSensorData.kt` — add `headingErrorDeg: Float?`, null when no source
  can supply one.
- `ui/CompassScreen.kt` — `confidenceNote()` becomes the fallback rather than the
  primary channel. When an error figure exists, the third line reads `±4°` in
  `textSecondary` when it is small and in `levelWarn` when it is not. Keep the
  existing silence-means-fine discipline: below some threshold, say nothing.
- **Fallback ladder, all three rungs needed**, because FOP is Google Play Services
  only and has no non-GMS path at all. Rung two is `TYPE_ROTATION_VECTOR`, whose
  `values[4]` is documented as *"estimated heading Accuracy (in radians) (-1 if
  unavailable)"*, with the AOSP contract that *"the heading error must be less than
  the estimated accuracy … 95% of the time"*
  ([AOSP sensor types](https://source.android.com/docs/core/interaction/sensors/sensor-types)).
  Rung three is the existing accel + mag path, unchanged. Each rung is a
  `getDefaultSensor` null check away.
- **Pick the right rotation vector — this is the single easiest wrong turn here.**
  `TYPE_ROTATION_VECTOR` is the one you want: the CDD requires it to use a gyroscope
  as its main input and says it *"can't be implemented using only the accelerometer
  and magnetometer"*, and it is mandatory on any device that has accelerometer,
  magnetometer and gyroscope — which is to say, on almost every device that can run
  this app at all. `TYPE_GAME_ROTATION_VECTOR` does not use the magnetic field, has no
  idea where north is, and its `values[4]` is explicitly reserved and set to zero.
  `TYPE_GEOMAGNETIC_ROTATION_VECTOR` drops the gyroscope for lower power, *does* carry
  a real `values[4]`, and is the right rung 2b for a gyro-less phone — but it is
  noisier and the docs say it "will work best outdoors".
- **Quaternion ordering will bite you silently.** `SensorManager.getQuaternionFromVector()`
  returns `[w, x, y, z]` — scalar **first**. The rotation vector itself, and FOP's
  `getAttitude()`, are scalar **last**. Mixing the two produces a plausible-looking,
  completely wrong rotation with no error anywhere. Use
  `getRotationMatrixFromVector()` and stay in matrix space, which is what the existing
  pipeline already speaks.
- **Do not chase `Sensor.TYPE_HEADING`.** It exists, it was added in API 33, and it
  looks exactly like what you want. It is an *automotive* sensor and is documented
  under automotive sensors in AOSP. It will be null on every phone.

**Effort: M.** The wiring is small. The *design* is not, and one decision below is
genuinely hard.

**Risks and gotchas.**
- **The big one: FOP's heading is already true north where it can be.** Google's
  announcement is explicit — "In cases where the local magnetic declination is not
  known (e.g., location is not available), the orientation will be relative to
  magnetic north." There is no flag on `DeviceOrientation` telling you which one you
  got. Feed that straight into `CompassViewModel.computeDisplayAzimuth()` and the
  `MAG` tag silently starts showing true north for any user whose phone has a
  location, and `TRUE` starts double-counting declination. That is a wrong number
  presented confidently — the exact failure this app exists to avoid. The safe
  design is to keep `SensorRepository`'s magnetic azimuth as the authority for the
  displayed heading and the AR remap, and take **only the error estimate** from FOP
  in v1. Adopting FOP's heading itself is a separate, later decision that requires
  rethinking the MAG/TRUE toggle from scratch.
- *The remap rule does not go away.* If you move to `TYPE_ROTATION_VECTOR` +
  `getRotationMatrixFromVector()`, you still need `remapCoordinateSystem(AXIS_X, AXIS_Z)`
  for the upright azimuth, and pitch and roll must still come from the *unremapped*
  matrix. `learnings/sensor-orientation-and-remap.md` describes a ~90° bubble-level
  discontinuity; that trap is identical on the new path.
- *Sensor lifetime.* FOP updates are foreground-only (fine — so is this app), but
  the subscription must live inside the same `WhileSubscribed` window as the
  `SensorManager` listener or rule 5 is broken by a new route.
- *Double filtering.* FOP output is already fused and smoothed. Running it through
  `Constants.Sensor.LPF_ALPHA` and then the 0.5° deadband and then the rose's spring
  would produce a heading that lags visibly.
- *Play Services absence.* Some devices and any future F-Droid flavour have none.
  The ladder above handles it; just do not let `OrientationRepository` throw on
  construction.
- *`NO_SIGNAL` still matters.* The watchdog in `watchForStaleSensor()` protects
  against a silent `getRotationMatrix` failure. A second source means a second way to
  go quiet; the watchdog should cover whichever source is actually in use.
- *Callbacks do not arrive on the main thread.* FOP delivers on the `Executor` you
  hand it. The `callbackFlow` + `StateFlow` shape already handles this correctly, but
  do not be tempted to touch Compose state from the listener directly.
- *Resist `OUTPUT_PERIOD_FAST` and the permission it implies.* 50 Hz is already far
  more than a 0.5°-deadbanded readout can use. Reaching for
  `HIGH_SAMPLING_RATE_SENSORS` to go faster would trip lint's `HighSamplingRate`
  check — and `lint` is wired to fail this build — on top of warning that apps using
  it may be held for Play review. There is no reason to go near it.
- *One more platform quirk worth knowing.* If the user kills microphone access with
  the device-wide toggle, the motion and position sensors are rate-limited regardless
  of any permission you hold. It will look like your fusion got worse.

**Store and ASO.** "Shows how sure it is, in degrees" is a sentence no competitor
listing in this category appears to carry. It is also the honest upgrade to the
existing full-description paragraph that begins "Tells you when it is not sure",
which is currently the most distinctive paragraph in the listing. Pairs naturally
with item 3.

---

## 3. Calibration coach — make `CALIBRATE · FIGURE 8` into something a person can act on

**Pitch.** Tap the calibration warning and get a screen that shows the figure-eight
motion, shows the live magnetic field strength against the Earth's normal range, and
dismisses itself the moment the magnetometer recovers.

**Why it belongs in this app.** The app currently detects the single most common
real-world compass failure and then tells the user four words about it in 12 sp type.
That is diagnosis without treatment. The support page on `site/support.html` already
contains the full explanation — figure of eight, step away from metal, a magnetic
case is the most common cause — which is an admission that the in-app copy is not
enough. Bringing that content into the moment it is needed is not feature creep; it
is closing a loop the app already opened.

The field-strength readout is the part that earns its place. Earth's field is
roughly 25–65 µT depending on latitude. When a user is next to a laptop, a speaker,
a car dashboard or a magnetic phone mount, the magnitude of `TYPE_MAGNETIC_FIELD`
goes far outside that band, and showing that is the difference between "calibrate,
somehow" and "the thing 20 cm to your left is the problem". It converts the app's
most common support ticket into a self-service answer.

**User value.** Calibration confusion is a recurring complaint across the category
and it generates one-star reviews that are not really about the app. This is also the
one screen in this document that a user will screenshot and send to a friend.

**Implementation sketch.**

- `sensor/SensorRepository.kt` — expose `fieldStrengthUt = sqrt(x² + y² + z²)`.
  Note the trap: `lowPass()` writes into `geomagnetic` **in place**, so by the time
  the listener body runs, the raw event values for this axis set are already blended.
  Compute the magnitude from `event.values` at the top of the
  `TYPE_MAGNETIC_FIELD` branch, before the filter call, or keep a separate lightly
  filtered copy. A magnitude taken from the heavily smoothed array will lag by
  several hundred milliseconds, which defeats the "wave it near the laptop and watch
  it spike" interaction that makes the screen work.
- `sensor/CompassSensorData.kt` — one more field. It is already `@Immutable` and
  already carries six values; a seventh is proportionate.
- New `ui/CalibrationSheet.kt` — a `Dialog` or bottom sheet, not a page (rule 3).
  A looping figure-eight path animation, a horizontal field-strength bar with the
  25–65 µT band shaded, the current value in µT, and one line of adaptive copy
  ("Field strength is 180 µT — something magnetic is very close. Step away and try
  again."). Auto-dismiss when `accuracy` reaches `HIGH`, with a brief confirmation
  rather than a silent close.
- `ui/CompassScreen.kt` — `confidenceNote()` currently returns a `Pair<String, Color>`.
  Make the `LOW` and `UNRELIABLE` cases clickable, with a real 48 dp target as the
  MAG tag already does.
- `ui/MotionUtil.kt` — the figure-eight animation must respect
  `rememberReduceMotion()`; a looping curve is exactly the kind of motion that helper
  exists for. Show a static diagram instead.

**Effort: M.** Perhaps two days, most of it in the animation and the copy. No
permission, no dependency, no policy change.

**Risks and gotchas.**
- **Do not call it a metal detector.** Several competitors ship the magnetometer
  magnitude as a novelty "metal detector", and it is the kind of feature this app's
  full description explicitly promises not to have — it names "metal detector" in the
  WHAT IT DOES NOT DO section. Framing this as *interference detection, in service of
  a correct heading* is the difference between honest and hypocritical, and the copy
  has to carry that weight.
- **Be honest about what the number proves.** A magnitude outside the Earth-field
  band proves interference. A magnitude *inside* it does **not** prove the
  magnetometer is calibrated — a hard-iron offset can leave the magnitude plausible
  while the direction is wrong. The screen must say the platform's accuracy flag is
  the authority and the µT figure is a hint, or it becomes a confident wrong signal:
  precisely rule 1.
- **There is no API to trigger calibration, and this was checked properly.** The
  entire public surface of `SensorManager` contains nothing calibration-related — the
  word appears only inside the `SENSOR_STATUS_*` constant descriptions. There is no
  Android equivalent of iOS's `locationManagerShouldDisplayHeadingCalibration`.
  `onAccuracyChanged` is a report, not a request. What the figure-eight actually does
  is feed the OEM's continuous online calibration faster: the CDD requires any device
  with a magnetometer to *"support online calibration and compensation of the hard
  iron bias, and preserve the compensation parameters between device reboots"*. The
  screen is a coach, not a control, and the copy must not imply otherwise.
- **The figure-eight advice is not even official end-user guidance.** The single
  sentence Android publishes about it — *"When testing your app, you can improve the
  sensor's accuracy by waving the device in a figure-8 pattern"* — is addressed to
  developers testing their own app, not to users in the field. There is no official
  user-facing calibration guidance anywhere. That is a gap in the platform, and it is
  the gap this screen fills; it also means you are writing the copy from scratch with
  no canonical wording to lean on. Budget for that.
- *Telemetry already tells you whether this matters.* `Telemetry.sampleAccuracy()`
  records one accuracy state per session. If the `UNRELIABLE` share turns out to be
  2%, this is a smaller feature than it looks. Read that number before you build it.

**Store and ASO.** Earns a screenshot, and "calibrate compass" / "compass not
working" / "compass wrong direction" are exactly the long-tail help queries that send
people to Play in the first place. It also gives the support page something to link
to instead of describe.

---

## 4. Remembered declination — true north on the first frame, with no network

**Pitch.** Cache the declination so `TRUE` is live the instant the app opens,
instead of sitting on `MAG` for up to ten seconds while a fix arrives, every single
launch, forever.

**Why it belongs in this app.** This is the only item here that is closer to a
defect than a feature. Today `trueNorth` defaults to `true`, but `hasDeclination`
starts `false` on every cold start, and `computeDisplayAzimuth()` needs both. So a
user who granted location on day one and has opened the app forty times in the same
city still sees `MAG` on launch and waits for `DeclinationRepository`'s 10-second
`getCurrentLocation` to return — indoors, often it simply times out and they never
get true north at all. The support page's "The tag never changes to TRUE" section
exists because of this. Declination for a given place is stable over years; there is
no reason to re-derive it from a fresh GPS fix every time the app is opened.

**User value.** Silent, and that is the point. It removes a ten-second wait and an
indoors failure mode from the single feature the listing leads with.

**Sequencing note.** If you adopt the Fused Orientation Provider's *heading* rather
than just its error estimate (item 2), this item partly evaporates — FOP applies
declination itself, server-side and against a current model, so there is nothing left
to cache. Decide item 2's scope first. If FOP stays confined to the error figure, as
I recommend for v1, build this; and either way the WMM-2020 problem described in item
2 means the number you are caching is already slightly stale at source. Caching a
marginally-wrong correction and showing it instantly is still better than making the
user wait for the same marginally-wrong correction, but it is worth knowing which
problem you are solving.

**Implementation sketch.**

- `sensor/DeclinationRepository.kt` — add a cache read and write. Keep the
  `GeomagneticField` call exactly where it is.
- `Constants.kt` — `Prefs.KEY_DECLINATION`, `KEY_DECL_AT_MS`, and (see below)
  optionally `KEY_DECL_LAT_Q` / `KEY_DECL_LON_Q`.
- `viewmodel/CompassViewModel.kt` — seed `_declination` and `_hasDeclination` from
  the cache in `init`, before any fix. `updateDeclination()` then becomes a refresh
  rather than a first acquisition.
- Invalidate on two axes: age (declination drifts by a few arc-minutes a year, so an
  annual refresh is generous) and distance (a cached value is worthless after a
  flight).

**The distance check is where this gets interesting, and it is a real decision:**

- *Option A — store a rounded position.* Keep latitude and longitude rounded to
  0.1° (about 11 km, well inside the "a city-block fix is as good as a precise one"
  argument the manifest already makes) so you can discard the cache when the new fix
  is far away. This is correct and cheap, and it makes
  `site/privacy.html`'s "Your coordinates exist only in memory … never stored" false.
  You would edit the privacy policy and the Data Safety CSV in the same sitting —
  which `docs/launch/play-console-answers.md` says in bold is the one thing you must
  never do separately. The honest new sentence is easy to write: a rounded position,
  stored on your device only, never transmitted, used solely to notice when you have
  moved far enough that the correction needs recomputing.
- *Option B — store only the angle.* No policy change at all. You lose the ability
  to detect travel, so the cached correction has to be treated as provisional:
  applied immediately, refreshed in the background, and silently corrected when the
  new fix lands. The window of possible wrongness is the few seconds before the
  refresh completes, which is no worse than today's behaviour and considerably more
  useful.

I would ship Option A, because "cache a bearing correction and never notice you flew
to another continent" is the sort of quiet wrongness this app is supposed to be
against, and because the policy edit is three sentences. But Option B is a legitimate
choice if you would rather not reopen the Data Safety form so soon after submitting it.

**Effort: S** for the code, plus an hour of policy and Data Safety editing if you
take Option A.

**Risks and gotchas.**
- *The policy and the form move together.* Non-negotiable; see rule 6.
- *`GeomagneticField` takes a time argument.* The cached value was computed against
  `System.currentTimeMillis()` at capture. An old enough cache is wrong on that axis
  alone, which is the second half of the invalidation rule.
- *Do not let the cache gate anything.* If the prefs read fails or the value is
  absent, behaviour must be exactly what it is today. Rule 2.
- *Telemetry.* `Telemetry.onDeclinationOutcome(acquired:)` currently separates "the
  fix never arrived" from "the magnetometer is off". Keep that distinction and add a
  third state for "served from cache", or you will lose the ability to tell whether
  this change worked.

**Store and ASO.** Small but real: it makes "Finds true north, not just magnetic
north" true on the first frame rather than after a wait, and it lets the listing say
true north works offline — which, once cached, it genuinely does.

---

# Worth considering

Six items with a real case and a real reason to wait.

*(A seventh — a Quick Settings tile — started this section and ended up in
"do not build" once I read the guidance. See below.)*

---

## 5. The level, finished: plumb mode and a zero

**Pitch.** Make the bubble level work on a vertical surface, and let the user zero
it against a reference.

**Why it might belong.** Two reasons, one of which is a straight bug.
`site/support.html` tells users: "For a vertical surface, hold the phone flat against
it." The level cannot do that. `BubbleLevelIndicator` maps `pitch` and `roll` through
`Constants.Level.MAX_TILT_DEG = 45f` and clamps to the unit circle; a phone held flat
against a wall is at roughly 90° of pitch, so the bubble pins hard against the rim
and the `isLevel` threshold can never fire. The documentation promises a mode that
does not exist.

The second reason is the most common request for any level app: no table is flat and
no phone is flat in its case, so a "zero here" that stores an offset pair is what
makes the reading usable for real work.

**Why it can wait.** `Telemetry.onLevelOpened()` exists precisely because a review
questioned whether the level belongs in this app at all. Read that number before
investing in it. If the level turns out to be opened in 4% of sessions, the right
move is to fix the support page, not to build plumb mode.

**Sketch.** A vertical mode cannot be derived from `pitch` and `roll` — for the
same reason `cameraElevationDeg` exists, `getOrientation`'s angles fold and lose
information at extreme attitudes. Derive it directly from the gravity vector in
`SensorRepository`, which already has it in hand for `tiltFromFlat`. Detect the
posture from `tiltFromFlatDeg` (the app's existing continuous, hysteresis-friendly
signal) rather than inventing a second threshold. `TiltScreen` and
`BubbleLevelIndicator` then take a mode parameter; the zero offset lives in
`compass_prefs`.

**Effort: M.** The maths is the work, not the UI.

**Gotchas.** `learnings/sensor-orientation-and-remap.md` is the required reading
here, and its rule is absolute: do not reach for the remapped matrix to get pitch and
roll at high tilt. That is exactly the change that produced the original ~90°
discontinuity. Also: a hysteresis band between flat and plumb mode, not a bare
threshold, for the same reason `ArTriggerGate` exists.

**Store and ASO.** Modest. "Level" and "spirit level" are already in the title and
short description; plumb mode adds `plumb`, `vertical level` and `angle finder` to
the long tail, and it lets the existing level screenshot show something the
competition's does not. It does not earn a new frame on its own.

---

## 6. Sun and moon bearings on the rose

**Pitch.** Two small markers on the compass card showing where the sun and moon are
now, and where the sun will set.

**Why it might belong.** It is the one "extra instrument" request that does not
require a new permission, a network call, or a new page — it rides entirely on the
location fix the app already takes for declination, and renders as two marks on a
card that already has eight labels. The audience is concrete: landscape and
architectural photographers planning a shot, anyone siting a solar panel, anyone
choosing which side of a campsite gets morning sun, gardeners, and people buying or
renting a flat who want to know which way the balcony faces at 4 pm. It is also the
kind of feature that generates the "oh, that's clever" reaction a small app needs to
be shared.

**Why it can wait.** It is the first proposal in this document that adds an
instrument rather than improving the one that is there, and the point at which a
minimal compass starts becoming a Swiss army knife is exactly here. It also needs a
third-party dependency and it keeps coordinates alive in memory for the session,
where today they are discarded within milliseconds.

**Sketch.** [`org.shredzone.commons:commons-suncalc:3.11`](https://shredzone.org/maven/commons-suncalc/),
Apache-2.0, **56.9 KB, zero runtime dependencies**. `SunPosition.getAzimuth()` is
already north-based 0–360°, which is the same convention `CompassRose` draws in, so
it drops onto the existing maths with no conversion. Add `sunAzimuth` / `moonAzimuth`
to the ViewModel, recomputed on a timer (once a minute is generous); draw them inside
`CompassRose`'s rotating block; add palette entries.

**The one thing that will bite you, and it will bite in production rather than CI.**
The 3.x line is compiled against `java.time`, i.e. Android API 26, and this app's
`minSdk` is 24. Without `isCoreLibraryDesugaringEnabled = true` plus
`coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")` you get
`NoClassDefFoundError: java.time.ZonedDateTime` — **on API 24 and 25 only**. It
compiles clean, it passes lint, it runs perfectly on your test device, and it crashes
for the users on the oldest phones you deliberately chose to support. Either enable
desugaring or pin `commons-suncalc:2.14`, the last `java.util.Date`-based release.

**Gotchas.** The privacy policy currently says coordinates live in memory "for as
long as the calculation takes". Holding them for a whole session is arguably still
"in memory", but it is a stretch of the current wording and the sentence should be
made accurate rather than argued about. Two more markers on the rose is also a real
legibility risk in daylight mode — the card already carries eight labels and
360 ticks.

**Effort: M.** A day for the maths and the markers, plus the desugaring decision.

**Store and ASO.** Good. `sun direction`, `sunset direction`, `solar compass`,
`which way does this window face` are all real queries that the incumbents serve
badly, and the audience — photographers, solar installers, people viewing a
property — skews toward exactly the discerning slice that would pay for a Pro
unlock. Earns a screenshot.

---

## 7. A degree ribbon across the top of the AR view

**Pitch.** Replace the eight discrete cardinal chips in AR with a continuous
bearing tape, the way an aircraft HUD does it.

**Why it might belong.** Pure drawing, no new inputs, no permission, no dependency,
and it makes the AR mode look considerably more like an instrument and less like a
toy. `CardinalOverlay` already computes `fracX = shortestArcDelta(dir, azimuth) / fovHalfDeg`
for eight fixed angles; a tape is the same expression evaluated every 5° or 10° with
a label every 30°. It also gives the AR mode something to show when the user is
looking between two cardinals, which is most of the time — right now, pointing at
070° shows a lonely `NE` chip drifting off-centre.

**Why it can wait.** It is a refinement of a feature whose usage you have not
measured yet. `Telemetry.onArOpened(auto:)` will tell you both how often AR is used
and whether the auto-trigger is wanted. If AR turns out to be a novelty people open
once, this is polish on a dead limb.

**Effort: S–M.** One composable, some careful tick spacing, and real care with
culling — the existing `Constants.Ar.CULL_MARGIN` logic assumes a handful of labels,
not seventy ticks.

**Gotchas.** Legibility over a bright sky is the whole problem; `chipBg` is
near-opaque for exactly this reason and a thin tick tape does not get that luxury.
Draw it on a gradient scrim or accept that it disappears at noon.

**Store and ASO.** No new keywords, but it materially improves the AR screenshot,
which is the one frame in the current set that has to do the differentiating work.
`play-console-answers.md` already notes that frame 02 is a composite and that
shooting it outdoors on real hardware "removes the question entirely" — a ribbon
gives you a reason to reshoot it properly.

---

## 8. Qibla as a bearing-lock preset

**Pitch.** If bearing lock ships, qibla is that same feature with one preset bearing
computed by great-circle from the user's position to 21.4225°N, 39.8262°E.

**Why it might belong.** Mechanically it is nearly free — the initial great-circle
bearing is about fifteen lines of spherical trigonometry against a fix the app
already takes, and it renders through machinery item 1 already built. The demand is
enormous and global, and India is one of its largest markets. `store-and-monetization.md`
already names it: "qibla dominates a large adjacent slice of this category's search
volume — a genuine, large opportunity."

**Why I am not putting it in Build next.** That same paragraph finishes the thought
correctly: "a different app with different obligations, and out of scope for a
listing built on restraint." I agree with the existing analysis, and I want to add
two specific reasons rather than just defer to it.

First, the obligations are real. A qibla feature carries a correctness duty that a
hiking bearing does not, and the honest-instrument philosophy cuts hard here: this
app knows its magnetometer is often wrong by tens of degrees indoors — which is
exactly where this feature would be used, surrounded by steel and concrete. Shipping
it means shipping a prominent accuracy warning that will be, correctly, quite
discouraging.

Second, and more practically, it is a one-way door on positioning. The listing that
says qibla gets reviews asking for prayer times, then the Hijri calendar, then
tasbih. The [qibla category on Play](https://play.google.com/store/apps/details?id=com.qiblacompass.qibladirection)
is deep, competitive, and built around exactly that bundle. You would be entering it
with one thirteenth of a feature set, from an app whose differentiation is that it
refuses to grow.

The version I *would* endorse: a separate listing, sharing this codebase through a
product flavour, positioned honestly as "a qibla direction that tells you when it
cannot be trusted". That is a genuinely underserved angle in a category full of
confident wrong arrows. It is also a different project and should be costed as one.

**Effort: S** for the great-circle bearing on top of item 1; **L** for everything
that would then be true about the product, the listing and the review inbox. That gap
between the two numbers is the entire argument.

**Two corrections to what I assumed going in, both of which sharpen the case.** First,
qibla is even larger than the launch doc implies: four dedicated qibla apps sit at ten
million installs *each*, and the umbrella prayer apps are an order of magnitude beyond
that. More pointedly, an app called **"Qibla Compass: A Minimal Qibla" already has over
a million installs at 4.6 stars** — the minimalist framing is not a hypothesis in this
niche, it is proven, by someone else, already. Second, the technical requirement is
stricter than a normal bearing: the initial great-circle bearing to the Kaaba
(21.4225°N, 39.8262°E) is a *true* bearing, so it has to be rendered against
declination-corrected north — and a flat-map rhumb line, which is the obvious wrong
implementation, can be more than thirty degrees out. Showing the raw magnetic reading
is the documented top failure mode of the existing apps. That is a correctness duty,
not a nice-to-have.

**And a claim I went looking for and could not substantiate: vastu is not a large
category.** A Play search for "vastu compass" restricted to India returns seven apps;
the leader,
[Vastu Compass by AppliedVastu](https://play.google.com/store/apps/details?id=com.appliedvastu.compass),
has around 500,000 installs, and the whole category aggregates to roughly 1.4 million —
less than a seventh of a *single* qibla app, and about a tenth of what one ordinary
generic compass has in India on its own. The real vastu differentiator is not the rose
anyway; the market leader's own copy says a digital compass "can only indicate the
direction, whereas this app can instantly mark 16 zones with the vastu Purusha
mandala" over a photographed floor plan. That is a floor-plan annotation tool with a
compass in it, which is a different product entirely. If you had vastu on a list
somewhere, take it off.

---

## 9. Dish and panel alignment — a bearing *and* an elevation target

**Pitch.** Enter a target azimuth and elevation — or pick a geostationary satellite
from a built-in table — and the app guides you onto both at once, using the compass for
one axis and the phone's existing tilt sensing for the other.

**Why it might belong, and why I did not expect to be writing this.** The research
turned up a niche I had not considered and it is the best-served-by-existing-machinery
idea in the document. Two satellite-finder apps on Play have **over ten million installs
each and are rated 3.4 and 3.8 stars**. A ten-million-install app at 3.4 stars is a
market telling you something. And look at what one competitor named its package:
`com.satfinder.satellitedirector.inclinometer.bubblelevel.gyrocompass`. They are
keyword-stuffing the exact primitives this app already ships — compass, inclinometer,
bubble level — and doing them badly enough to earn 3.4 stars.

The kicker is that the hard part is already built. `CompassSensorData.cameraElevationDeg`
is a **signed** −90…+90° elevation of the rear-camera axis, derived as `asin(-R[8])`
precisely because `pitch` folds up and down onto the same value. That is exactly the
signal a dish installer needs and it is the one most naive implementations get wrong.
Pair it with bearing lock from item 1 and the feature is largely assembly. Satellite
positions are a static table of longitudes; the azimuth and elevation from any point on
Earth is closed-form trigonometry, entirely offline, no network, no new permission
beyond the coarse fix the app already takes for declination.

**Why it can wait, and why it might never happen.** It is a professional tool wearing a
consumer app's clothes, and it points the product at an audience with no overlap
whatsoever with the hiker the listing currently addresses. It also needs a
domain-specific input surface — a satellite picker, or two numeric fields — which is
the first genuinely *form-like* UI in an app that has one control on its main screen.
And LNB skew, which real installers need, is a third angle the app has no concept of.

The honest framing: this is the most commercially interesting thing in this document
and the least Minimal Compass–shaped. If you ever want a second product built out of
this codebase, it is a stronger candidate than qibla, because the machinery transfers
almost completely and the incumbents are visibly weak.

**Effort: M** as a mode in this app, **L** as the separate product it probably wants to
be.

**Gotchas.** `cameraElevationDeg` is derived from the *unremapped* matrix by design —
do not "fix" that while adding an elevation target. Holding a phone against a dish arm
is a high-tilt posture, which is the same regime that breaks the bubble level (item 5);
the two features want the same underlying work. And accuracy claims here need care for
the same reason as everywhere else: a magnetometer that is ten degrees out will not
find a satellite, so this is a feature that *must* surface item 2's error figure.

**Store and ASO.** Strong and almost entirely non-overlapping with the current listing:
`satellite finder`, `dish alignment`, `azimuth and elevation`, `inclinometer`,
`solar panel angle`. If it ships inside this app it deserves its own screenshot; if it
ships as a separate listing it is a separate ASO project.

---

## 10. One waypoint — "mark this spot"

**Pitch.** Save where you are; the compass shows a marker and a distance pointing
back to it.

**Why it might belong.** It is the second most-requested thing in outdoor compass
reviews after accuracy, and "I parked somewhere in this field" is a genuinely common
problem. With bearing lock already built, the rendering is free — it is a bearing
marker whose angle is recomputed as you move.

**Why it probably does not belong, and the reason is concrete.** The app's
`ACCESS_COARSE_LOCATION` choice is not a minor preference; it is argued in the
manifest, in `CompassScreen`'s KDoc, in the privacy policy and in the listing copy.
Coarse gives you a fix good to something like a kilometre or worse. A waypoint at
that resolution cannot find a car, a trailhead or a tent — it is not a weaker version
of the feature, it is a useless one. Doing it properly requires
`ACCESS_FINE_LOCATION`, continuous location updates rather than a single fix, and
on-device storage of a real position. That is a manifest change, a Data Safety
change, a privacy-policy rewrite, a stronger and scarier system dialog, and the
abandonment of one of the app's better arguments — all for a feature that
[Trail Sense](https://f-droid.org/en/packages/com.kylecorry.trail_sense/) already
does properly, offline, for free.

**And the paperwork is about to get worse, on a published schedule.** Play's location
policy changed on 2026-04-15 to make the Android location button the minimum scope for
precise location. A precise-location declaration tool goes live in **November 2026**
and becomes **mandatory on 2027-01-27**: to keep `ACCESS_FINE_LOCATION` you will have
to pick a preset use case and justify, in writing, why coarse location and the system
location button are insufficient. For a bearing-back-to-a-pin feature the honest
justification exists, but it is a form you will refill at every review. Background
location is a different league again — a separate declaration plus a ≤30-second video
showing your disclosure and the runtime prompt — and the stated review test is whether
the feature *"could be replaced with foreground-only access"*, which for walk-out /
walk-back it demonstrably could. You would be arguing against Google's own criterion.

If you want it anyway, scope it ruthlessly: exactly one waypoint, foreground only,
no background service, no track recording, no history, cleared by a single tap. If you
need it to keep working with the screen off, that is a `foregroundServiceType="location"`
started from a visible tap with a persistent notification and a stop action — one
declaration and one demo video, and crucially **no** `ACCESS_BACKGROUND_LOCATION`.
Budget a full week for the store and policy work, not just the code.

**Effort: L**, and almost none of it is the feature. The code is perhaps a day on top
of bearing lock; the permission escalation, the Data Safety re-submission, the
privacy-policy rewrite, the listing copy that no longer says "the app asks for
exactly two permissions", and the re-argument of the coarse-location decision are the
rest of the week.

**Store and ASO.** Genuinely strong — `find my car`, `where did I park`, `mark
location`, `walk back` are high-volume queries. That is precisely why it is
tempting, and precisely why the cost above has to be paid with eyes open rather
than discovered halfway through.

---

# Explicitly do not build

This section is the valuable one. Each of these is an obvious idea that a reasonable
person would suggest, and each is wrong for this app for a specific reason.

**A live heading on the home-screen widget.** Already decided, already documented in
`CLAUDE.md`, `widget/CompassWidget.kt`, `learnings/glance-widget-gotchas.md` and the
store description. Restating it because it is the single most likely feature request
you will receive: a widget cannot hold the magnetometer open, so the number would be
one reading snatched in whatever pose the phone was in, shown as fact for up to
fifteen minutes. The bar for reopening this is written down — a capture timestamp, a
visible age, and an accuracy gate that refuses to persist a `LOW` or `UNRELIABLE`
reading — and once you have built all three you will have a widget that usually shows
"—", which is the correct answer and not one anybody wants on their home screen.
Nothing between API 31 and 36 has changed this: `updatePeriodMillis` still floors at
thirty minutes, WorkManager still floors at fifteen and is standby-bucket throttled,
Glance's own guidance is still "no more than once an hour", and Android 15 added a
*new* failure mode where force-stopping an app disables all of its widgets until the
app is next launched.

**A Quick Settings tile.** This one started life in "worth considering" and lost.
The mechanics are trivial and favourable — `TileService` has existed since API 24, so
your `minSdk` covers it with no guard, and Android 13's
`StatusBarManager.requestAddTileService()` lets you offer the tile once instead of
hoping the user finds it. The problem is that Google's own guidance tells you not to
build the tile you would be building. Verbatim: *"Avoid using tiles that display
information, but aren't interactive for users. Use a notification or a widget
instead"*, and *"Avoid using tiles to launch an app."* A compass tile can only update
while the shade is open, so it cannot be a readout; and a tile that just opens the app
is the second prohibited case. That is the whole design space, and both halves are
ruled out. ([Quick settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles).)
Worth knowing anyway if you ever revisit it: `startActivityAndCollapse(Intent)` now
throws `UnsupportedOperationException` for apps targeting 34 and above — use the
`PendingIntent` overload — and the `requestAddTileService` prompt is silently rate
limited, with the system able to stop processing your requests entirely after enough
of them.

**A promoted "Live Update" notification showing your heading.** Android 16 added
`Notification.ProgressStyle` and promoted ongoing notifications, and it is a tempting
surface for a compass. The eligibility guidance rules it out in one line:
*"Don't show ambient information, such as that about the user's environment."* A
passive "you are facing NNE" is exactly that. Active navigation toward a waypoint
*would* be a legitimate use — it is the first item on the appropriate-uses list — so
this is a door that only opens if item 10 is ever built, not before. Note also that the
pieces are split across releases: `ProgressStyle` and `FLAG_PROMOTED_ONGOING` are API
36, but `setRequestPromotedOngoing()` and the `POST_PROMOTED_NOTIFICATIONS` permission
arrive in Android 16's *minor* release, 36.1.

**ARCore Geospatial for a better heading.** The `// TODO Phase 2 – ARCore hook:`
comment in `MainActivity.kt` should be deleted rather than implemented, and there is
now a single sentence in Google's own documentation that settles it: *"the Geospatial
API cannot be configured to work with coarse location, and API requests will fail when
the user has set this permission. Your app cannot use the Geospatial API with coarse
location."* Every argument this app makes about `ACCESS_COARSE_LOCATION` — in the
manifest, in `CompassScreen`'s KDoc, in the privacy policy, in the listing — would
have to be reversed to enable a feature. That is the wrong order of operations.

Three more, any one of which would be sufficient on its own. There is no headless
call: `getCameraGeospatialPose()` is only valid while `Earth.getTrackingState()` is
`TRACKING`, so you must open the camera and hold motion tracking until VPS localises,
with no published latency figure. `GeospatialPose.getHeading()` and
`getHeadingAccuracy()` are already deprecated in favour of
`getEastUpSouthQuaternion()` and `getOrientationYawAccuracy()`, and yaw is the weak
axis — where VPS data is thin it falls back to the device's own magnetic fusion, which
is the signal you already have. And the ARCore terms prohibit building *"features that
are substantially similar to or that re-create the features of another Google product
or service (such as Live View on Google Maps)"*, which a VPS-driven cardinal-label
camera overlay sits uncomfortably close to. Also worth knowing: `com.google.ar:core:1.56.0`
raises its own `targetSdkVersion` to API 37, which manifest-merges into you.

Item 2 above buys most of the accuracy benefit for none of this. *(I could not verify
current ARCore Geospatial pricing — Google's own pricing pages for it return 404, and
the third-party "free tier" claims are uncorroborated. The architectural objections
above stand regardless of the number.)*

**A map, or a map layer.** This is the single biggest jump in APK size,
permissions, network usage and support burden available to you, and it converts the
app into a category — navigation — where it would be competing with Google Maps,
OsmAnd and Organic Maps. The full description currently promises "no map layer" in
so many words. Three specifics, in case someone argues it is cheap now:

- MapLibre's plain `org.maplibre.gl:android-sdk` artifact — the one the official
  quickstart recommends — ships `<uses-feature android:name="android.hardware.vulkan.version"
  android:required="true" />`, which makes Play filter your listing off every
  non-Vulkan device, silently. You would want `android-sdk-opengl` instead, at roughly
  3.6 MiB of extra download per arm64 user.
- Its manifest merge pulls in `ACCESS_FINE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE`
  and `ACCESS_WIFI_STATE`. You would be undoing commit `b586e28` — the one titled
  "make the privacy policy true, and the app match it" — by dependency, not by
  decision.
- The obvious lightweight alternative is gone: osmdroid is archived and takes no more
  updates, and in any case OpenStreetMap's own tile usage policy prohibits exactly the
  feature people want from it — *"Offline use is not permitted on tile.openstreetmap.org.
  Features such as 'Download city/country for offline use' … are therefore
  prohibited,"* including building tile archives for later distribution.

**A metal detector.** Named in the listing as something the app does not have. The
honest version of the same sensor data is item 3, framed as interference detection.
Shipping it as a toy would make the app's own marketing copy a lie and would
undermine the calibration screen sitting next to it.

**An altimeter. This is the hardest call in the document and I am still saying no.**

Be clear about what is being turned down. **Altitude is the single most-requested
feature in the entire category** — 95 mentions across the 2,484 substantive reviews
scraped, nearly twice the next item (true north, 53) and more than five times the
demand for bearing lock. Essentially every compass app above a million installs shows
an altitude figure. A reasonable person reads that table and concludes this is the
obvious next feature, and I want to be honest that the demand is not in doubt.

The problem is that there is no version of it this app can ship without breaking rule 1.

The barometric route fails on hardware and on physics. `TYPE_PRESSURE` is only
*strongly recommended* by the CDD, never required, so it is absent on an unknown but
substantial fraction of phones — and no official penetration figure exists, which is
itself telling. Where it is present, `SensorManager.getAltitude()` needs a sea-level
pressure you do not have; the documentation says it plainly: *"If unknown, you can use
`PRESSURE_STANDARD_ATMOSPHERE` as an approximation, but absolute altitudes won't be
accurate."* Ordinary weather moves the local reference by far more than the sensor's
own ±1 hPa. Altitude *differences* are the honest use case and they are not what anyone
asking for an altimeter means.

The GPS route — which is what most of those million-install apps actually use — fails
differently and worse for this app specifically. Vertical GPS error is routinely two to
three times the horizontal, so you are showing a number that can be tens of metres out
while looking authoritative to the metre. Worse, it needs `ACCESS_FINE_LOCATION` and a
continuous location subscription rather than the single coarse fix the app takes today,
which drags in the whole Play declaration regime described in item 10 and makes the
privacy policy's central promise false. You would trade the app's cleanest
differentiator for a number you would then have to caveat.

So the answer is no, and the reason is worth writing into the listing rather than
hiding: *this app does not show an altitude because it cannot show one it would stand
behind.* That sentence is on-brand, it pre-empts the review, and it is true. If the
demand proves overwhelming after launch, the honest minimum is a barometric
*relative* altitude — "you have climbed 140 m since you tapped this" — gated on the
sensor existing, which is a real feature for hikers and is not what the request
usually means. Weather prediction on top of it is Trail Sense's territory and it does
it well.

**A speedometer, pedometer, or activity recognition.** GPS speed needs continuous
fine location; step counting needs `ACTIVITY_RECOGNITION`, a runtime permission
category that reads as fitness tracking in the system dialog. Neither has anything to
do with which way you are facing, and both would appear in the Data Safety section of
a listing whose main asset is that its Data Safety section is clean.

**Ads, in any form.** `store-and-monetization.md` covers the commercial case
(a compass session is measured in seconds; you will never have the daily actives for
banner revenue to matter). The structural case is stronger: `AndroidManifest.xml`
carries three deliberate `tools:node="remove"` entries stripping the advertising ID,
and the Data Safety CSV was filled in against that. Adding ads means putting all of
it back, and it destroys the one thing that distinguishes this app from a sixteen
-million-install incumbent, in the same week that incumbent's reviews are complaining
about six interstitials in ten minutes.

**A subscription.** No recurring cost, no recurring value, no recurring reason to
open the app. Already argued and correctly rejected in the existing launch doc.

**An onboarding carousel, a tutorial, or a "rate us" interstitial.** The app's first
screen is a compass showing a bearing. That *is* the onboarding. Every screen placed
in front of it directly contradicts the product's only promise. If you want ratings,
the Play In-App Review API called sparingly — after a third or fourth session, never
on first launch — is the only version of this worth considering, and even that is a
judgement call.

**Themes, skins, a 3D rose, or a needle-versus-card option.** Reviews will ask for
all of these. `CLAUDE.md` and `CompassRose`'s KDoc already contain the reasoning for
why the card carries north and the fixed index carries the heading; it is the correct
design and a settings screen offering the incorrect one as an alternative is not
neutrality, it is abdication. Three palettes is already one more than most apps
justify.

**Background track recording or backtrack.** Foreground-service location, a
persistent notification, a Play declaration for background location, a database, and
a battery profile this app has spent real effort avoiding. Trail Sense does it,
offline and free.

**A Wear OS app — for now.** Not wrong in principle; a compass on the wrist is a
genuinely good idea, and Pixel Watch 4 and 5, Galaxy Watch8 and TicWatch Pro 5 all
list a magnetometer. Four things make it the wrong 2026 project. The CDD's watch
requirements section contains no magnetometer clause at all — only a strong
recommendation for an accelerometer — so "many Wear devices have no compass" is not a
guess, it is the specification. It is a separate APK with its own version code, its
own Play track, a mandatory closed test and a *separate Wear OS review*, even under
the same package name. `androidx.glance.wear.tiles` is deprecated and never left
alpha, its replacement `androidx.glance.wear` is alpha-only and needs compileSdk 37,
so there is no stable Compose-for-tiles path and you would be writing ProtoLayout by
hand. And Wear OS 7 is turning full-screen Tiles into "Wear Widgets" anyway, so
anything you build now is against a surface mid-migration. `SensorRepository`,
`AngleUtil`, `Constants` and the ViewModel port almost unchanged; everything else —
round-screen rose, ambient mode, rotary input, a second release pipeline — is new.
Revisit when the phone app has an audience worth extending.

**Prayer times, Hijri calendar, tasbih.** See item 8. If the qibla direction ever
ships it will ship as its own product, and that product's scope is a decision for
that day, not a drift.

**Page three of the pager.** Not a feature, but the failure mode all of the above
share. Two pages and two dots is a design commitment. Anything that cannot be a mode
on the compass or a sheet over it is a signal that the feature does not belong in
this app.

---

# Non-feature work that outranks most of the above

Found while reading the code for this document. Three of these are, in my view, more
valuable than anything in the "Worth considering" list.

**Ship 1.0.0, then read the data for thirty days.** Stated at the top and repeated
here because it is the actual recommendation. `store-and-monetization.md` already
lays out the launch sequence, the Reddit strategy and the fourteen-day closed-testing
gate. Three of the seven Firebase events were built to decide what to delete or keep,
and right now they have measured nothing.

**The true-north correction runs on an expired magnetic model, and the platform says
so in its own documentation.** `android.hardware.GeomagneticField` — the only thing
standing between `DeclinationRepository` and a declination figure — states: *"This
class currently uses WMM-2020 which is valid until 2025."* WMM2025 has been out since
December 2024. This is not your bug and you cannot fix it inside that class; the
correction table is baked into the platform. But it is a fact about the app's
headline feature that you should know before someone else discovers it, and it is a
strong second argument for item 2, since the Fused Orientation Provider applies
declination itself rather than from a device-local table. In the meantime it is worth
deciding whether the listing's confident language about true north deserves a
qualifier. Most of the world sees an error well under a degree from this; some of it
does not.

**A talking, haptic compass is an underserved niche you are one small change away
from.** The research surfaced real demand for orientation tools usable by blind and
low-vision people — DotWalker ships a talking compass, and Talking Compass's own
description is *"speaks your current heading and provides haptic feedback as you cross
over any of the cardinal directions"*. That description is item 1's haptic and a fixed
TalkBack announcement, which is to say: two things already in this backlog, pointed at
a different user. The app's accessibility foundations are already unusually strong
(real 48 dp targets, documented WCAG contrast reasoning, a described level canvas), so
the gap between where it is and a genuinely good screen-reader compass is smaller than
for any competitor. Fix the live-region problem below first — it is currently the thing
standing in the way — and this becomes almost free, in a niche where the mainstream
apps are absent.

**`sensorAvailable` is computed and never read, and that is the one bug this app's
philosophy cannot tolerate.** `CompassViewModel` exposes it
(`sensorRepo.isAvailable`, from a null check on both sensors) and no composable
collects it — I grepped the whole source tree. On a device with no magnetometer, the
flow never emits, the watchdog fires after three seconds, and the user sees
`NO SENSOR SIGNAL` under a frozen `0° NORTH` with no explanation, plus a Crashlytics
non-fatal for something that is not a fault. `AndroidManifest.xml` declares
`android.hardware.sensor.compass` as `required="true"` so Play filters those devices
out of the store — but that does not cover sideloads, alternative stores, emulators,
or OEM builds that misreport the feature flag. The app that exists to never show a
confident wrong number currently shows `0° NORTH` on exactly the devices where no
number is possible. This is a one-screen fix and it should be in 1.0.1. It is also a
sentence worth having in the listing: *"On a phone without a magnetometer it says so,
instead of showing you a zero."*

The reviews confirm both the severity and the fix. From the scraped corpus, one star:
*"it may be that my phone lacks a magnetometer but **other compass apps tell you that
they wont work and this one is just dead**."* And from a forum thread about a different
minimal compass: *"As is the crude compass just sits there and looks dumb… if it
detected that the phone it was running on did not have a magnetometer, and then threw
an error, it would probably be an OK app."* Users are explicit that saying so is what
separates an acceptable app from a one-star one. You have already computed the boolean.

**The support page documents a level mode that does not exist.** Covered under item
6: `site/support.html` says "For a vertical surface, hold the phone flat against it",
and `BubbleLevelIndicator`'s 45° clamp makes that impossible. Whatever you decide
about plumb mode, fix the sentence this week — it costs nothing and it is currently
generating a support experience where the app appears broken.

**Every user-facing string is a Kotlin literal, and that is a ceiling, not a
nitpick.** `res/values/strings.xml` holds two strings. The settings dialog, the
confidence labels, all camera permission copy, the axis labels and every
`contentDescription` are inline. `technical-readiness.md` lists this as N1. It is
worth promoting: you are an India-based developer whose most plausible long-tail
audience includes Hindi, Marathi, Bengali, Tamil and Telugu speakers, and localised
listings are one of the few ASO levers that reliably move installs for a utility. Not
one line of that is possible until the strings move. Extracting them is a mechanical
afternoon; putting it off makes every feature in this document more expensive,
because each one adds more literals.

**The low-pass filter's smoothing varies by device, invisibly.**
`SensorRepository.lowPass()` applies a fixed `alpha = 0.15` per *event*, not per unit
of time. `SENSOR_DELAY_UI` is a hint, not a contract, and delivery rates differ
substantially across OEMs — so the effective time constant, and therefore how
"floaty" or "twitchy" the compass feels, is different on every phone, and none of it
is visible from a unit test. If you adopt the Fused Orientation Provider (item 2) the
question partly goes away. If you do not, make the filter time-aware using the event
timestamp. Either way, it is worth knowing that the app does not currently feel the
same on two different handsets.

**The accessibility story is better than it looks in one place and worse in another,
and the worse one is the live region.** `technical-readiness.md` N3 records that
neither pager page has a pane title, so swiping between compass and level announces
nothing — that is real, and `paneTitle` is now specifically the sanctioned mechanism
for it, since Android 16 deprecated `announceForAccessibility` and `TYPE_ANNOUNCEMENT`
outright. It would also be the right fix for the flat↔AR crossfade, which currently
announces nothing at all.

The part that is not in N3: both heading readouts carry `liveRegion = LiveRegionMode.Polite`
keyed on the *whole degree* (`ui/CompassScreen.kt` and `ui/ArOverlayScreen.kt`). The
live-region documentation warns against exactly this — *"Live regions shouldn't be
used on content that updates frequently, such as countdown timers, to avoid
overwhelming users with constant feedback."* A heading changes by a degree constantly
while a person turns, so a TalkBack user turning on the spot gets a continuous
unbroken stream of numbers. The code comments show the author already worried about
this and keyed the announcement on the rounded value to limit it; one degree is simply
not coarse enough. The fix is to quantise the *announced* value far harder than the
displayed one — 45° buckets, announced as "Northeast" rather than "forty-seven
degrees", with hysteresis on the bucket boundary (reuse `shortestArcDelta` at a
coarser setting, or the bearing sitting on 22.5° oscillates forever) and a floor of a
second or two between announcements. `stateDescription` for the value and
`contentDescription` for what the thing is. There is no official throttling
algorithm — only the "use sparingly" caution — so this is judgement, but the current
behaviour is the one shape the documentation explicitly names as wrong. Worth testing
with TalkBack on before 1.0.0 goes out, because it is the sort of thing that produces
a one-star review from someone who is right.

**Smaller, already catalogued, still worth clearing before new features land.**
`showSettings` in `ui/CompassScreen.kt` uses `remember` rather than `rememberSaveable`,
so the settings dialog vanishes on rotation (N2). `Constants.Legacy.WIDGET_DATASTORE_FILE`
and `Prefs.KEY_LEGACY_STORE_PURGED` describe a migration that nothing performs (N4).
`CLAUDE.md`'s ARCore table lists `// TODO Phase 2` hooks in five files; only
`MainActivity.kt` actually has one — and per the "do not build" section above, that
one should be deleted rather than reconciled.

**Two dependency bumps that are not routine hygiene, because they block things in
this document.** Glance 1.1.1 is two stable releases behind 1.2.0, which is the
release that adds generated widget previews — relevant given how much trouble the
widget picker already caused (`learnings/glance-widget-three-silent-failures.md`).
Do not jump to 1.3.0-alpha, which wants AGP 9.2 and compileSdk 37. More pointedly,
Compose BOM 2024.09.00 resolves `compose-ui` 1.7.0, whose `HapticFeedbackType` has
exactly two members: `LongPress` and `TextHandleMove`. The constant bearing lock
actually wants — `SegmentFrequentTick`, documented as being for *"the user … switching
between a series of many potential choices"*, which is precisely a bearing crossing a
threshold — arrives in Compose 1.8.0 / BOM 2025.04.01. On the current BOM you cannot
express the right haptic in Compose at all and would have to drop to
`View.performHapticFeedback`. Bump the BOM before building item 1, not after. (Good
news on that front: `View.performHapticFeedback` needs no `VIBRATE` permission and
automatically respects the user's touch-feedback setting, so the haptic in item 1
costs you no manifest change either way.)

**Test coverage where the product actually lives.** `technical-readiness.md`'s T2
and T3 are still open: `CompassViewModel`'s heading logic — `computeDisplayAzimuth`,
the deadband path, `toggleTrueNorth`'s re-publish, `updateDeclination`'s failure
branch — has no tests at all, and that is the code that decides which number the user
sees. Every item in the "Build next" section adds branches to exactly those methods.
Extracting `SensorRepository` and `DeclinationRepository` behind interfaces and adding
`kotlinx-coroutines-test` is a prerequisite for building items 1, 2 and 4 safely, not
a follow-up to them.

---

## If I had to pick one sentence

Ship it, read the numbers, then build bearing lock — and spend the accuracy work on
making the app's existing honesty visible in degrees, rather than on new instruments
the app was designed not to have.

And if you read only two paragraphs of the research, read these.

The platform's own `GeomagneticField` documentation says it uses a magnetic model that
expired in 2025, which means the app's flagship `TRUE` reading is built on a stale
table. The Fused Orientation Provider fixes that, quantifies the confidence the app
already claims to care about, and needs a dependency you are one point release away
from having. That is the highest-value change available here, and it is not a feature
at all.

And the market is kinder than the launch report assumed. Play's compass search is not
install-ranked: a sixteen-thousand-install app, ad-free and recently updated, currently
sits above four apps with more than seven million installs each, and the two biggest
incumbents have not been meaningfully reworked since 2014 and 2015. Rating, freshness
and the absence of advertising appear to be the ranking currency. This app has all
three by construction. The constraint on it is not the competition and it is not the
feature list — it is that nobody has been able to install it yet.

---

## Sources

Verified during preparation of this document:

- Position sensors, Android Developers — https://developer.android.com/develop/sensors-and-location/sensors/sensors_position
- `SensorEvent` (rotation vector `values[3]`/`values[4]`) — https://developer.android.com/reference/android/hardware/SensorEvent
- `SensorManager` (full public surface; no calibration API exists) — https://developer.android.com/reference/android/hardware/SensorManager
- `GeomagneticField` — "currently uses WMM-2020 which is valid until 2025" — https://developer.android.com/reference/android/hardware/GeomagneticField
- Sensor types (rotation vector `values[4]`, 95% heading-error contract, automotive `TYPE_HEADING`), AOSP — https://source.android.com/docs/core/interaction/sensors/sensor-types
- Android 16 Compatibility Definition Document (rotation vector requirements, magnetometer online calibration, barometer and watch sections) — https://source.android.com/docs/compatibility/16/android-16-cdd
- Introducing the Fused Orientation Provider API, Android Developers Blog — https://android-developers.googleblog.com/2024/03/introducing-fused-orientation-provider-api.html
- `DeviceOrientation` and `DeviceOrientationRequest`, Google Play services reference — https://developers.google.com/android/reference/com/google/android/gms/location/DeviceOrientation
- Create custom Quick Settings tiles ("avoid using tiles to launch an app") — https://developer.android.com/develop/ui/views/quicksettings-tiles
- Accessibility: live regions and the Android 16 deprecation of announcements — https://developer.android.com/guide/topics/ui/accessibility/principles
- Behaviour changes for apps targeting Android 16 — https://developer.android.com/about/versions/16/behavior-changes-16
- commons-suncalc — https://shredzone.org/maven/commons-suncalc/ and https://github.com/shred/commons-suncalc
- Trail Sense — https://github.com/kylecorry31/Trail-Sense and https://f-droid.org/en/packages/com.kylecorry.trail_sense/
- Digital Compass (`com.vincentlee.compass`), Google Play — https://play.google.com/store/apps/details?id=com.vincentlee.compass
- Compass & Altimeter (`net.androgames.compass`), Google Play — https://play.google.com/store/apps/details?id=net.androgames.compass
- Compass for Wear OS watches — https://play.google.com/store/apps/details?id=com.appfour.wearcompass
- Qibla Compass — Qibla Finder — https://play.google.com/store/apps/details?id=com.qiblacompass.qibladirection
- Vastu Compass by AppliedVastu — https://play.google.com/store/apps/details?id=com.appliedvastu.compass

In-repo sources relied on throughout: `CLAUDE.md`, `learnings/sensor-orientation-and-remap.md`,
`learnings/glance-widget-gotchas.md`, `learnings/glance-widget-three-silent-failures.md`,
`learnings/permission-permanent-denial.md`, `docs/launch/store-and-monetization.md`,
`docs/launch/technical-readiness.md`, `docs/launch/play-console-answers.md`,
`site/privacy.html`, `site/support.html`.

Competitive figures come from Play listing pages scraped directly (which yields
Google's exact `realInstalls` integer rather than the rounded "10M+" bucket) plus
10,162 reviews across 13 compass apps pulled with `google-play-scraper`, of which 1,724
are one-to-three-star with text. Play exposes no per-review permalinks, so quotes are
citable only to the app listing. The compass-app deviation study is Bowers (2022),
*Compass Errors in Mobile AR Navigation Apps*, Springer LNNS 508 —
https://oro.open.ac.uk/84729/ — and the Play catalogue contraction is
https://techcrunch.com/2025/04/29/google-play-sees-47-decline-in-apps-since-start-of-last-year

Not independently verified, and flagged where used: Play "compass" keyword search
volume and difficulty (paywalled — treat any number quoted for it as fabricated);
the causal link between Play's 2024–25 catalogue purge and the search-ranking pattern,
which is inference; the exact weekly subscription price on one competitor (four
converging user reports, checkout screen never seen); what fraction of the install base
lacks a magnetometer;
ARCore Geospatial pricing and VPS coverage (Google publishes no pricing page for it —
the obvious URLs 404 — and no coverage map or percentage exists); barometer market
penetration (no official statistic); Play's `requestAddTileService` throttle number
(documented as existing, never quantified); and whether any given real device actually
populates rotation-vector `values[4]` rather than returning `-1`, which needs testing
on hardware.
