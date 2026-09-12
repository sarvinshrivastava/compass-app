# Play Console submission sheet — Minimal Compass

Package `com.pubpeb.compass` · versionName **1.0.0** · versionCode **1**
Updated 2026-09-12 for the first production submission.

These are Play Console forms, not files — nothing here can be uploaded. Open
each section in Console and copy the answers across. Every answer is derived
from what the code actually does, with the evidence cited so you can
re-verify it rather than trust this document.

**The single most common rejection cause here is a Data Safety form that
disagrees with the privacy policy.** Both are written from the same facts. If
you change one later, change the other in the same sitting.

---

## 0. Artefacts to upload

| Asset | File | Spec |
|---|---|---|
| App icon | `docs/launch/assets/play-icon-512.png` | 512 × 512, 24-bit PNG, full square |
| App icon (alt) | `docs/launch/assets/play-icon-512-transparent.png` | same mark, no ground |
| Feature graphic | `docs/launch/assets/feature-graphic-1024x500.png` | 1024 × 500, 24-bit PNG, no alpha, solid ground |
| Phone screenshots | `docs/launch/assets/screenshots/01…04-*.png` | 1080 × 1920, 24-bit PNG |
| App bundle | `app/build/outputs/bundle/release/app-release.aab` | signed — `jar verified` |

**Upload the filled icon, not the transparent one.** Google's icon spec allows
alpha but advises against it: *"Transparent assets will display the background
color of Google Play UI."* Play also masks the corners at 30 % radius and adds
its own shadow, so the artwork is meant to fill the square. The transparent
file is kept for places that genuinely need a free-standing mark — a press
kit, a website header — not for the listing.

The launcher icon (`res/drawable/ic_launcher_*.xml`, plus the legacy mipmaps)
is generated from the same geometry, so the Play tile and the home-screen icon
are the same object. Regenerate both together; never hand-edit one.

### The screenshot set

Four frames, in this order: compass, AR, daylight, level. Two were cut — the
settings sheet, because one toggle does not earn a frame, and the widget,
because the tile is too small in frame to say anything at listing size.

| # | Frame | Source |
|---|---|---|
| 01 | Compass, night, holding 47° NE | real capture |
| 02 | AR overlay on a forest trail | real capture, composited (see below) |
| 03 | Daylight mode | real capture |
| 04 | Bubble level | real capture |

Every frame sits on the same night ground drawn from `Theme.kt`, with one
deliberate exception: the daylight frame inverts to white, because that is what
the frame is about. The AR frame's photograph lives inside the phone only —
an earlier version bled it across the whole canvas and stopped matching the
rest of the set.

**Frame 02 is a composite, and you should know exactly what that means before
you upload it.** Every piece of app UI in it — the 288° HUD, the W and NW
labels, the close and torch buttons, the status bar — is lifted pixel-for-pixel
out of real captures of the app, positioned by a connected-components pass on
those captures rather than by hand. What was replaced is the camera feed behind
them: the emulator's virtual living room, swapped for a generated forest-trail
photo. The app genuinely draws that overlay; it did not genuinely see that
forest. This is ordinary practice for AR listings and the feature is not
misrepresented, but it is a judgement call and it is yours. Shooting the frame
outdoors on a real phone removes the question entirely, and the composer will
take the new capture under the same filename.

Two frames still need real hardware and are absent: the `TRUE` tag (the
emulator's GMS network location provider is disabled, so no fix ever returns)
and `CALIBRATE · FIGURE 8` (emulator magnetometer accuracy is always high).
Four frames is Play's minimum for recommendation-surface eligibility and twice
its minimum for a listing, so nothing is blocked — but there is no headroom.
Adding either of those two later is a listing edit, not a release.

## 1. Store listing text

### Title — 30 characters

```
Minimal Compass: North & Level
```
Exactly 30. Brand first, then the two terms people actually search. Dropped
"& more" from the original idea: it costs six characters and adds no
searchable term, and vagueness is the one thing this app's positioning cannot
afford.

Alternatives, both 27: `Minimal Compass: True North` keeps the higher-value
keyword but loses "level"; `Compass: True North & Level` ranks best and reads
most generic. The title is editable at any time.

### Short description — 80 characters

```
True north compass, bubble level and AR camera view. No ads, no sign-in.
```
72 characters.

### Full description — 4,000 characters

The 3,521-character draft in `store-and-monetization.md` §E.4 is current and
needs no edit. Copy it verbatim.

### Other listing fields

| Field | Value |
|---|---|
| Category | Tools |
| Tags | Compass, Navigation, Utilities |
| Contact email | `sarvin5124@gmail.com` |
| Website | `https://minimal-compass.vercel.app` |
| Support URL | `https://minimal-compass.vercel.app/support` |
| Privacy policy | `https://minimal-compass.vercel.app/privacy` |
| Phone | Leave blank |
| External marketing | Off |

**Do not caption or describe anything as "no tracking", "no data collected",
"100 % private" or "fully offline".** The app ships Firebase Analytics and
Crashlytics and the Data Safety form says so. "No ads. No sign-in." is true,
is what users in this category care about, and is safe.

---

## 2. Device catalogue

Opt **out** of tablets. Nothing in the app is laid out for a tablet, and
stretched phone screenshots are flagged in store quality review. Phones only
for 1.0.0.

---

## 3. App content → Privacy policy

| Field | Value |
|---|---|
| Privacy policy URL | `https://minimal-compass.vercel.app/privacy` |

Live and verified — HTTP 200, no redirect, on 2026-09-13.

**Use `minimal-compass.vercel.app`, never the
`minimal-compass-sarvinshrivastavas-projects.vercel.app` form.** The project
has both aliases, and Vercel's Standard Deployment Protection covers the
`-<team>.vercel.app` class: that host answers **302 to a Vercel SSO login**,
which would fail Play's privacy-policy check outright. The short alias is the
public production domain. Re-check it with `curl -I` before you submit — it
must stay reachable for the life of the listing, because Play re-checks it.

---

## 4. App content → Ads

| Question | Answer |
|---|---|
| Does your app contain ads? | **No** |

Firebase Analytics is not an ad SDK. The manifest strips the advertising-ID
permission outright (`AndroidManifest.xml`, three `tools:node="remove"`
entries) and disables ad personalisation and ad user data in the
`google_analytics_*` meta-data.

---

## 5. App content → App access

| Question | Answer |
|---|---|
| Is all functionality available without special access? | **Yes** |

No login, no account, no region lock, no paywall. Nothing to give the reviewer.

---

## 6. App content → Content rating (IARC)

Category: **Utility, Productivity, Communication or Other**. Then:

| Question | Answer |
|---|---|
| Violence, blood, sexual content, nudity | No to all |
| Profanity or crude humour | No |
| Controlled substances | No |
| Gambling or real-money contests | No |
| Horror, fear or disturbing content | No |
| Can users interact or communicate with each other? | **No** |
| Can users share their current location with other users? | **No** |
| Shares user-provided personal information with third parties? | No |
| Purchase of digital goods? | **No** |
| Contains ads? | No |
| Web browser or search engine? | No |
| Collects or transmits any user data? | **Yes** — anonymous analytics and crash diagnostics |

Expected outcome: **Everyone / PEGI 3 / ESRB Everyone**.

> The two "location" questions differ. IARC asks whether location is shared
> **with other users** — it is not. Data Safety asks whether location is
> **collected at all**, and the answer there is Yes, for the reason in §8.

---

## 7. App content → Target audience and content

**Select 13–15, 16–17, and 18 and over. Do not select any band under 13.**

Selecting an under-13 band enrols the app in Designed for Families — a much
heavier compliance surface (ad-SDK certification, tighter data rules, extra
review) for an app with no ads and nothing child-specific to gain. It does not
prevent children from using the app; the question is about who the app is
targeted and marketed to. The rating still comes out Everyone, which is the
signal parents see.

| Question | Answer |
|---|---|
| Target age groups | 13–15, 16–17, 18 and over |
| Could it appeal to children anyway? | **No** |
| Designed for Families | Do not opt in |

---

## 8. App content → Data safety

**Does your app collect or share any of the required user data types? → Yes.**

Nothing in this section comes from app logic sending data. All five
declarations come from the two Firebase SDKs, whose collection counts as
yours.

### App activity → App interactions

| Field | Answer |
|---|---|
| Collected / Shared | Yes / No |
| Processed ephemerally | No |
| Required or optional | Required (no in-app opt-out exists in 1.0.0) |
| Purposes | Analytics |
| Linked to user identity | No |

Seven low-cardinality counters, listed in full in `telemetry/Telemetry.kt`:
`compass_session`, `accuracy_state`, `ar_opened`, `level_opened`,
`daylight_toggled`, `location_outcome`, `declination_outcome`. No screen
views, no taps, no user ID.

### App info and performance → Crash logs

| Field | Answer |
|---|---|
| Collected / Shared | Yes / No |
| Purposes | App functionality, Analytics |
| Linked to user identity | No |
| Required or optional | Required |

Firebase Crashlytics, including the deliberate `SensorStalledException`
non-fatal.

### App info and performance → Diagnostics

| Field | Answer |
|---|---|
| Collected / Shared | Yes / No |
| Purposes | App functionality, Analytics |
| Linked to user identity | No |
| Required or optional | Required |

Device model, Android version, app version — the metadata Crashlytics attaches
to every report.

### Device or other IDs

| Field | Answer |
|---|---|
| Collected / Shared | **Yes** / No |
| Purposes | Analytics |
| Linked to user identity | No |
| Required or optional | Required |

Google's own Analytics documentation states that Analytics "automatically
assigns an app-instance id to each instance" of the app and sends it with
every event
(`support.google.com/analytics/answer/11582702`). It is not the advertising ID
— that is stripped from the package — it does not identify a person, and it
dies with an uninstall. It is still a device identifier, and answering No here
while shipping Firebase Analytics is a known rejection and enforcement
trigger.

### Location → Approximate location

| Field | Answer |
|---|---|
| Collected / Shared | **Yes** / No |
| Purposes | Analytics |
| Linked to user identity | No |
| Required or optional | Required |

**This reverses the earlier draft, which left location undeclared — that was
wrong, and it is the answer most likely to cost you the submission.** The
reasoning that led there was about the app's own code, and that part is
correct: `sensor/DeclinationRepository.kt` takes one coarse fix, passes the
coordinates into `GeomagneticField(...)`, returns only `.declination`, and the
position never leaves the device. But the same Google Analytics documentation
states that Analytics "derives coarse location data from the masked IP
addresses" of incoming requests. That derivation happens on Google's servers,
from traffic this app causes, which makes it collection you must declare.

Declare it, and let the privacy policy explain the distinction — which it now
does, in the "Two things Firebase adds" section.

### NOT declared: Photos and videos

Not collected. `ui/ArOverlayScreen.kt` binds CameraX `Preview` only — no
`ImageCapture`, no `VideoCapture`. Frames are rendered to the screen and
discarded; nothing is saved, analysed or transmitted.

### Security practices

| Question | Answer |
|---|---|
| Data encrypted in transit | **Yes** — all Firebase traffic is HTTPS |
| Users can request deletion | **No** |
| Committed to Play Families Policy | Not applicable |
| Independent security review | Leave unticked |

Deletion is No because there is no identifier that could locate a user's
records to delete. That is a stronger privacy position than a deletion flow,
but the question is literal — answer it literally. The privacy policy says the
same thing in the same terms, deliberately.

---

## 9. Not applicable — answer No and move on

News app · COVID-19 contact tracing or status · financial features · health
apps · government app · Play Pass · sensitive permissions (no
`QUERY_ALL_PACKAGES`, no `SMS`/`CALL_LOG`, no `MANAGE_EXTERNAL_STORAGE`, no
foreground service, no accessibility service, no VPN).

Two declaration forms that are **not** triggered, confirmed: the background
location form (this app uses foreground-only `ACCESS_COARSE_LOCATION`) and the
photo/video permissions form (no `READ_MEDIA_*`). The camera needs no separate
form — core-functionality justification applies and the AR overlay satisfies
it.

---

## Cross-check before you submit

- [ ] Privacy policy URL resolves over HTTPS with no redirect chain, and the
      **redeployed** version is live (it changed on 2026-09-12)
- [ ] Data Safety declares **Device or other IDs = Yes** and **Approximate
      location = Yes**
- [ ] Data Safety declares no Photos/videos
- [ ] Privacy policy names Firebase Analytics **and** Crashlytics explicitly
- [ ] Ads = No, and no listing copy claims "no tracking" or "offline"
- [ ] Content rating came out Everyone / PEGI 3
- [ ] Target audience excludes under-13; Designed for Families off
- [ ] Tablets opted out of the device catalogue
- [ ] `jarsigner -verify app-release.aab` prints `jar verified`
- [ ] The AAB you upload was built **after** the widget fixes of 2026-09-12
      (see `learnings/glance-widget-three-silent-failures.md`)
