# Play Store graphic assets — ASO audit and rebuild plan

Package `com.pubpeb.compass` · listing v1.0.0 · written 2026-09-14

Trigger: a reviewer said the store screenshots "do not have ASO" and suggested
keeping two frames as pure app-UI shots and making the rest ASO-optimised
(framed, captioned, benefit-led, keyword-rich).

This document audits every asset that exists, rules on that advice, and
specifies the replacement set precisely enough to build without further
research.

---

## 0. What Play actually requires, verified

Everything in this section was checked against Google's own pages on
2026-09-14. Where a number is repeated across ASO vendor blogs but does **not**
appear in Google's own copy, it is marked UNVERIFIED — do not treat those as
compliance gates.

| Asset | Requirement | Source |
|---|---|---|
| Phone screenshots | Min **2**, max **8** (max is vendor-reported, UNVERIFIED against Google's page). Each side min **320 px**, max **3840 px**. **The longer side may not exceed 2× the shorter.** JPEG or **24-bit PNG, no alpha**. Recommended portrait **9:16, min 1080 × 1920**; landscape 16:9, min 1920 × 1080. | [answer/9866151](https://support.google.com/googleplay/android-developer/answer/9866151) |
| Max file size | 8 MB per screenshot — vendor-reported, **UNVERIFIED** against Google's page. Current largest asset is 1.6 MB, so moot. | — |
| Tablet screenshots | Min 4 recommended, 1080–7680 px per side, 16:9 or 9:16. | [answer/9866151](https://support.google.com/googleplay/android-developer/answer/9866151) |
| Feature graphic | Exactly **1024 × 500**, JPEG or 24-bit PNG **no alpha**, **mandatory** to publish. Displayed as the cover image for preview videos, large-format app collections, and recommended game groups. | [answer/9866151](https://support.google.com/googleplay/android-developer/answer/9866151) |
| App icon | **512 × 512**, 32-bit PNG with alpha, max 1024 KB, sRGB. Upload a **full square** — Play applies its own mask (30 % corner radius) and its own shadow. Do not bake either in. Transparency is permitted by the format but discouraged: transparent areas show the Play UI's background colour. | [icon-design-specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) |
| Title | **30 characters**, stated explicitly by Google. | [answer/9898842](https://support.google.com/googleplay/android-developer/answer/9898842) |
| Short / full description | 80 / 4,000 characters — universally reported, **UNVERIFIED** against a primary Google page in this research pass. Both current drafts are comfortably inside those numbers anyway. | — |

### Two policy rules that constrain the caption copy

From [answer/13393723, "Best practices for your store listing"](https://support.google.com/googleplay/android-developer/answer/13393723):

1. **"Use taglines in your screenshots if absolutely necessary… But keep text
   to a minimum."** This is Google's own guidance, and it directly caps how
   much caption copy belongs on a frame. Short benefit lines: yes. Paragraphs
   and keyword lists: no.
2. **Banned outright in screenshots and the feature graphic:** anything
   indicating store performance or ranking — *"App of the year," "#1," "Best of
   Play 20XX," "Popular"* — and price or promo claims such as *"10 % off,"
   *"free for a limited time only."* None of the proposed copy goes near
   either, but any future A/B variant must be checked against this.

Plus the rule this repo already established in `play-console-answers.md` and
must keep: **never caption anything "no tracking", "no data collected", "100 %
private" or "fully offline".** The app ships Firebase Analytics and
Crashlytics and the Data Safety form says so. `No ads. No sign-in.` is true,
is what this category's users actually care about, and is safe.

### The single most important correction to the brief

**Google Play does not index the text burned into screenshot pixels.** No
Google document describes screenshot-text indexing, and Play's own ranking
pages name the fields that do count: [answer/9958766, "App Discovery and
Ranking"](https://support.google.com/googleplay/android-developer/answer/9958766)
lists metadata as *"title, description, category"* alongside app quality,
editorial curation and engagement signals — screenshots appear nowhere.
[answer/4448378](https://support.google.com/googleplay/android-developer/answer/4448378)
likewise names only title and description, and states the weightings are
proprietary and undisclosed.

So "keyword-rich captions" is a false lever. **Screenshot captions do not
affect what you rank for.** They affect whether somebody who already sees the
listing taps Install. Whether that install rate then feeds back into ranking is
industry inference, not confirmed Google policy — Play's page cites "ratings,
reviews and engagement" generically and never names store-listing conversion
rate.

The practical consequence: keyword work belongs in the title, short
description and full description, and this repo has **already done that
well** (`store-and-monetization.md` §E, `play-console-answers.md` §1 — title
`Minimal Compass: North & Level`, short description carrying *true north,
compass, bubble level, AR camera view, no ads, no sign-in*). The screenshots'
one job is persuasion. Write the captions for a human, keep them short because
Google asks you to, and stop worrying about keywords in them.

### On the large-screen / tablet claim

Widely repeated vendor claim: apps without tablet screenshots lose ranking or a
"Tablet-optimized" badge from 2025. **I could not verify any official Google
page stating a hard eligibility gate, an enforcement date, or a named penalty.
Treat it as vendor folklore.** What *is* official, on
[adaptive-app-quality](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality):
*"Adaptive apps rank higher and are more often selected for Editors' Choice on
Google Play,"* and it recommends uploading screenshots showing the app on
tablets and foldables. That is an upside for complying, not a stated penalty
for not.

This listing currently opts **out** of tablets entirely
(`play-console-answers.md` §2), which sidesteps the question and forfeits the
upside. That remains a defensible 1.0.0 call — nothing in this app is laid out
for a tablet, and stretched phone shots are flagged in store quality review.
Revisit at 1.1, not now.

---

## 1. Current state audit

Every screenshot in `docs/launch/assets/screenshots/` is **generated**, not
hand-placed, by `docs/launch/assets/tools/make_store.py` from raws in
`screenshots-raw/`. The outputs are git-ignored; only the raws and the
generators are committed. That is a good architecture and the plan below keeps
it.

### The premise of the review is partly stale

The committed set is **not** raw unannotated device screenshots. All four
frames already carry a brand rail, a two-line Futura headline, a subhead, a
drawn device frame with a cast shadow, and a palette-matched ground. The
reviewer was most likely looking at `screenshots-raw/`, or at an older upload,
or at what is currently live in Play Console — **which should be confirmed
before acting** (see Open Questions).

The underlying critique still lands, but for different reasons than stated.
Those reasons are below, measured rather than asserted.

### Measured evidence

Each finished frame was downscaled to 132 px wide — roughly what a Play search
result shows on a phone — and its tonal distribution measured:

| Frame | pixels < L40 (near-black) | L40–170 (midtones) | > L170 (near-white) |
|---|---|---|---|
| `01-compass.png` | **78.1 %** | 21.2 % | 0.7 % |
| `02-ar.png` | 44.4 % | 51.6 % | 4.0 % |
| `03-daylight.png` | 0.1 % | 1.6 % | **98.3 %** |
| `04-level.png` | **78.6 %** | 20.8 % | 0.6 % |

Viewing the four as a thumbnail contact sheet confirms what those numbers
predict, and also corrects one assumption worth stating plainly: **the 73 px
Futura headlines survive the shrink and are comfortably readable.** The
typography is not the problem. Everything below it is.

### Asset-by-asset

**`screenshots/01-compass.png` — "Open it. / Read the bearing."**
The strongest headline in the set and the weakest image beneath it. The device
frame is 640 px wide on a 1080 px canvas, so the app UI occupies only **39 % of
the canvas area**; the remaining 61 % is gradient ground and empty black phone
screen. Inside the phone, roughly 45 % of the visible screen is flat black —
there is a ~360 px dead band between the `47°` readout and the top of the rose,
and another between the icon row and the readout. At thumbnail the bezel colour
(`#2C3240`) and its edge (`#495163`) are indistinguishable from the ground's
lower gradient stop (`#080A0E`), which is *darker than the app's own*
`bgDeep = #0D0F14` — so the phone silhouette dissolves and the frame reads as a
caption floating over a black void. `NE`, `MAG` and the rose's cardinal letters
are all illegible at listing size. Worst of all for ASO alignment: the tag
reads **`MAG`**, so the one frame most people see never demonstrates the "True
North" that the app's own title sells.

**`screenshots/02-ar.png` — "North, on the / real horizon."**
By a distance the best frame in the set, and the only one with tonal range
(51.6 % midtones, 4 % highlights). The forest photograph is the only thing in
the whole gallery that reads as a photograph at thumbnail size, and the `288°`
HUD survives the shrink. Two caveats. First, the `W` and `NW` labels — the
actual feature — are illegible at listing size, so the caption carries the
entire idea. Second, this frame is a **composite**: `ar_composite.py` lifts
every piece of app chrome pixel-for-pixel out of a real capture and replaces
only the camera feed behind it, swapping the emulator's virtual living room for
a generated forest trail. The app genuinely draws that overlay; it did not
genuinely see that forest. `play-console-answers.md` already discloses this and
calls it a judgement call. It is ordinary practice for AR listings and is not a
misrepresentation of functionality, but a real outdoor capture removes the
question for about twenty minutes of work.

**`screenshots/03-daylight.png` — "Still readable / at noon."**
Technically the best-executed frame. It is the only one that inverts the
marketing ground to white along with the app, so the contrast switch is legible
at thumbnail — 98.3 % of its pixels are above L170. It is also, as a direct
consequence, the only frame whose compass rose is genuinely crisp at listing
size. In a gallery row it is the frame the eye lands on. Nothing here needs
fixing beyond the shared layout changes. Keep this frame's treatment as the
reference for what the others should achieve tonally.

**`screenshots/04-level.png` — "A spirit level, / one swipe away."**
Tonally a near-clone of frame 01 (78.6 % vs 78.1 % sub-L40). Placed fourth in a
row of four, it and frame 01 read as two identical dark slabs — the gallery has
no visual rhythm. The bubble-level graphic at thumbnail reads as a crosshair
circle with a yellow dot, which does not communicate "spirit level" to anyone
who does not already know; `PITCH`, `ROLL` and the degree values are invisible.
The caption does 100 % of the work. The device is drawn wider here (726 px) and
overflows the canvas bottom by 345 px, which is fine as a deliberate bleed but
means the pitch/roll readout — the informative part — sits in the lowest,
least-looked-at band of the frame.

**Shared across all four.** Only **4 of 8 available slots are used**, so half
the listing's visual real estate is simply unclaimed. The subheads are 28 px on
a 1080 px canvas — 2.6 % of width, about 3 px tall at thumbnail — so they are
pure noise in search results; they earn their keep only on the expanded
listing page, and at two lines each they cost 120 px of canvas to do it. The
whole set uses one layout with the device in nearly the same place every time,
which reads as a template rather than a product. Four features the listing copy
sells are never shown: true north with declination, the calibration warning,
the widget, and the fact that the AR auto-switch is a preference.

**`screenshots-raw/*.png` (7 files, 1080 × 2424 except `forest-pov.png` at
572 × 1024).** These are inputs, not deliverables, and are correct as such.
`raw-01-compass`, `raw-04-daylight`, `raw-05-level` and `raw-07-widget` are
clean captures in Android demo mode (9:30, full battery, full wifi, no
notifications) — exactly right. `raw-02-ar.png` is a real capture of the AR
overlay but pointed at an office sofa, which sells nothing; `forest-pov.png` is
the generated replacement backdrop and `raw-02-ar-forest.png` the composite of
the two. `raw-07-widget.png` shows the widget on a real launcher among ordinary
icons, which is the right idea, but the tile is small in frame and the stock
wallpaper's bright sky competes with it.

**`feature-graphic-1024x500.png`.** Correct spec (1024 × 500, RGB, no alpha),
clean, on-brand, and the rose mark is genuinely good — it is the app's own
launcher geometry, not a marketing redraw. Three problems, all minor. The
lockup is left-weighted: the mark sits at roughly x = 172 and the wordmark
block ends near x = 700, leaving the right ~320 px empty, so any centre-crop
across Play's surfaces clips asymmetrically and awkwardly. The
`NO ADS · NO SIGN-IN` kicker is 19 px on a 1024 px canvas (1.9 % of width) —
where this asset renders as a video cover at ~320 px wide, that line is under
6 px and is illegible; it is costing composition for no communication. And the
asset is trying to say three things (wordmark, tagline, kicker) in a slot that
Play scales unpredictably.

**`play-icon-512.png` / `play-icon-512-transparent.png`.** Both correct and
both should be left alone. The filled version is RGB 512 × 512 with the mark
filling the square — exactly what Google's spec asks for, since Play applies
its own 30 % mask and shadow. The transparent RGBA variant is correctly kept
for press-kit and website use and correctly **not** the one nominated for
upload. The mark reads at every size tested, the red needle is unambiguous, and
the geometry is shared with `res/drawable/ic_launcher_*.xml` so the Play tile
and the home-screen icon cannot drift apart. **No change.**

---

## 2. Verdict on the reviewer's advice

**Disagree with the prescription. Agree with the instinct behind it.**

Four reasons, in order of importance.

**1. "Keyword-rich captions" is based on a false premise.** Play does not
index screenshot pixels (§0). Captions cannot move you up a search result;
they can only move somebody who is already looking at your listing toward the
Install button. And Google's own store-listing guidance says to *keep
screenshot text to a minimum*. Following the reviewer's advice literally —
stuffing keywords into captions — would violate the platform's own guidance to
chase a ranking effect that does not exist. The keyword work belongs in the
title and the two descriptions, where this repo has already done it properly.

**2. The slot ordering is backwards.** Slots 1 and 2 receive by far the most
attention — a searcher scanning results sees those two and usually nothing
else. Spending the highest-traffic real estate on *unannotated* frames, and
reserving the considered treatment for slots 3–8 that far fewer people reach,
inverts the effort. If anything, slots 1 and 2 deserve the **most** design
attention in the set.

**3. The premise is factually wrong about the committed set.** All four frames
are already captioned, framed and branded. Confirm what is actually live in
Play Console before changing anything on the strength of this note — it is
plausible the reviewer saw the raw folder or a stale upload.

**4. But the instinct is right, and the measurements prove it.** Something
*is* wrong with these screenshots, just not "no ASO". It is that 61 % of each
canvas carries no information, that frames 01 and 04 are indistinguishable dark
slabs at the size users actually see, that the subheads are invisible, that
half the slots are empty, and that the app's own UI — which is the genuine
selling point and is better-looking than most of this category — is shown at
59 % width behind a lot of gradient. "Show me the app, bigger, and tell me why
I should care" is exactly the right note. The reviewer just reached for the
wrong instrument.

### The split I recommend instead

**Eight frames. All eight captioned. Zero raw frames. Two layouts:**

- **Slots 1–2 — full-bleed hero.** The app capture runs edge to edge at native
  1080 px width, with the caption in a ground-coloured band across the top.
  App UI goes from 39 % of canvas area to **73 %**. This is what the reviewer
  is actually asking for — the app shown honestly and at full size — while
  still carrying the one short benefit line that converts. It also does the
  most for thumbnail legibility, because the compass rose grows from 38 % of
  canvas width to roughly 65 %.
- **Slots 3–8 — device-framed**, the existing treatment with the fixes in §4.
  Variety of layout between the first two frames and the rest is a feature: it
  stops the set reading as a template.

Nothing is left uncaptioned, because an uncaptioned frame in a Play gallery is
a frame that says nothing to anyone who has not already installed the app.

---

## 3. The proposed screenshot set

Order matters more than anything else here. Slot 1 must demonstrate the
promise in the title (`Minimal Compass: North & Level`), slot 2 must be the
frame that stops a scroll, and everything after that is for people who are
already interested.

All captions are 4–6 words, benefit-led, and checked against the policy rules
in §0. Subheads exist only where marked; they are for the expanded listing
page, never for the thumbnail.

---

**Slot 1 — The compass, night palette, true north active**

- **Feature:** the core bearing readout — degrees, cardinal, and the `TRUE ·
  x.x° DECL` tag proving the declination correction is applied.
- **Caption:** `True north, the second it opens.`
- **Subhead:** none. The full-bleed layout has no room and the caption is
  self-sufficient.
- **Background:** full-bleed app capture; caption band in the night ground
  gradient (`#161C28` → `#141B29`).
- **Device-framed:** no — full bleed.
- **Source:** **needs new capture.** `raw-01-compass.png` shows `MAG`, not
  `TRUE`. Capture the flat compass screen outdoors with location granted and
  the mode tag tapped to `TRUE`, so the readout reads `TRUE · 1.2° DECL` (see
  `CompassScreen.kt:477–497` — the declination line only renders when
  `trueActive`). Hold a memorable heading; `47° NE` is already established
  across the set, so reproduce it if you can. No warning state.
- **Fallback if a fix cannot be obtained:** reuse `raw-01-compass.png` and
  change the caption to `A bearing, the second it opens.` Do not caption a
  `MAG` frame with "true north".

---

**Slot 2 — The AR camera overlay against a real horizon**

- **Feature:** cardinal labels floating in place over a live camera view,
  positioned from the lens's real field of view.
- **Caption:** `North, on the real horizon.`
- **Subhead:** none.
- **Background:** full-bleed app capture (the camera feed is the background);
  caption band in the night ground.
- **Device-framed:** no — full bleed. The photograph is the strongest image in
  the set and a bezel only shrinks it.
- **Source:** `screenshots-raw/raw-02-ar-forest.png` works today. **Strongly
  prefer a real outdoor capture** — raise the phone toward a treeline, a
  ridge, or a street with a visible vanishing point, ideally in soft light, and
  save it over the same filename (the composer reads it by name). That removes
  the composite question in §1 permanently and costs about twenty minutes.
  Frame it so at least two cardinal labels sit against sky or distant
  background rather than against clutter.

---

**Slot 3 — Daylight mode**

- **Feature:** the maximum-contrast white outdoor palette.
- **Caption:** `Readable in direct sunlight.`
- **Subhead:** `The dark theme becomes a mirror outdoors. This one doesn't.`
- **Background:** white ground (`#FFFFFF` → `#E9EDF4`), the one deliberate
  inversion in the set. Placed third so the bright frame breaks up the dark run
  early.
- **Device-framed:** yes.
- **Source:** `screenshots-raw/raw-04-daylight.png` — no recapture needed.

---

**Slot 4 — The bubble level**

- **Feature:** live pitch and roll on page 1 of the pager.
- **Caption:** `A spirit level, one swipe away.`
- **Subhead:** `Live pitch and roll in degrees, for the shelf and the tripod.`
- **Background:** night ground.
- **Device-framed:** yes, cropped tight to the bubble and the pitch/roll
  readout (see the crop box in §4) so the degrees are not stranded at the
  bottom of the frame.
- **Source:** `screenshots-raw/raw-05-level.png` — no recapture needed.

---

**Slot 5 — The calibration warning**

- **Feature:** the `CALIBRATE · FIGURE 8` state, in `levelWarn` amber, below
  the heading.
- **Caption:** `It tells you when it's unsure.`
- **Subhead:** `Most compass apps show a confident number regardless.`
- **Background:** night ground.
- **Device-framed:** yes, cropped to the readout block so the amber warning
  line is large enough to read.
- **Source:** **needs new capture.** This is counter-intuitive as a marketing
  frame and it is the single most differentiated thing in the listing — every
  competitor in this category displays a confident bearing when the
  magnetometer is unreliable. See §6 for how to induce the state.

---

**Slot 6 — The home-screen widget**

- **Feature:** the launcher tile, among ordinary app icons, at its real scale.
- **Caption:** `One tap from the home screen.`
- **Subhead:** `A launcher tile, not a fake live reading.`
- **Background:** night ground.
- **Device-framed:** yes.
- **Source:** `screenshots-raw/raw-07-widget.png` exists but the tile is small
  in frame and the wallpaper's bright sky competes with it. **Re-capture
  preferred:** set a plain dark wallpaper, place the widget in the upper-left
  cell cluster with four or five ordinary icons around it for scale, and leave
  the rest of the home screen sparse. Then crop to the widget and its
  neighbours rather than showing the whole screen.

---

**Slot 7 — The settings dialog**

- **Feature:** the single `Auto-switch to AR` switch, with its subtitle *"Raise
  the phone to open the camera view."*
- **Caption:** `The clever part is optional.`
- **Subhead:** `One switch. Everything else is one tap on the compass itself.`
- **Background:** night ground.
- **Device-framed:** yes.
- **Source:** **needs new capture.** Tap the gear icon, top right of the
  compass screen. The dialog is `SettingsDialog` in `CompassScreen.kt:351`.
  Crop tight to the dialog card — the dimmed compass behind it should be
  visible but incidental.
- **Honest note:** `play-console-answers.md` previously cut this frame on the
  grounds that "one toggle does not earn a frame". That was right when the set
  had four slots competing for space. With eight, the frame's real job is not
  the toggle — it is demonstrating that the app's most surprising behaviour is
  consensual rather than imposed, which is a genuine objection-handler for
  exactly the restraint-minded user this listing targets. If slot 7 has to be
  cut for any reason, cut this one first.

---

**Slot 8 — The promise, type only**

- **Feature:** none — this is the closing statement.
- **Caption:** `No ads. No sign-in. Ever.`
- **Subhead:** `No account, no onboarding, nothing to dismiss.`
- **Background:** night ground, with the rose mark drawn large and centred
  above the type (`brand.py: draw_rose`).
- **Device-framed:** no — no device at all. A type-and-mark frame closes the
  gallery and gives the set a third layout, which prevents the run of framed
  devices from blurring together.
- **Source:** no capture needed; generated entirely from `brand.py`.
- **Policy check:** all three claims are literally true and none touches the
  forbidden privacy/offline territory or the banned ranking/price claims.

---

### Summary table

| Slot | Feature | Caption | Layout | Source |
|---|---|---|---|---|
| 1 | Compass, `TRUE` + declination | `True north, the second it opens.` | full-bleed | **new capture** |
| 2 | AR overlay on a horizon | `North, on the real horizon.` | full-bleed | `raw-02-ar-forest.png` (real capture preferred) |
| 3 | Daylight mode | `Readable in direct sunlight.` | framed | `raw-04-daylight.png` |
| 4 | Bubble level | `A spirit level, one swipe away.` | framed | `raw-05-level.png` |
| 5 | `CALIBRATE · FIGURE 8` | `It tells you when it's unsure.` | framed | **new capture** |
| 6 | Home-screen widget | `One tap from the home screen.` | framed | `raw-07-widget.png` (re-shoot preferred) |
| 7 | Settings dialog | `The clever part is optional.` | framed | **new capture** |
| 8 | Promise | `No ads. No sign-in. Ever.` | type only | generated |

**Six strong frames beat eight padded ones.** If slots 7 and 8 feel thin when
you see them rendered, ship six. Play's minimum is 2 and nothing in Google's
documentation rewards using all 8. Do not pad.

---

## 4. Design spec

### Canvas

**1080 × 1920 px, 24-bit PNG, no alpha, sRGB.** Keep the current size.

Two notes on why, because the obvious "modern phones are 20:9, use 1080 × 2400"
is **not available**: Play caps the longer side at 2× the shorter, and
2400 / 1080 = 2.22. The tallest legal 1080-wide portrait frame is **1080 ×
2160** (exactly 2:1), which is legal but not recommended — Play's own
recommended portrait target is 9:16, gallery cards are laid out to a consistent
aspect, and a taller-than-16:9 asset risks being letterboxed or cropped in
surfaces you cannot preview. Stay at 9:16.

The second consequence is the one that shapes the whole layout: **a whole
modern Android phone screen (1080 × 2424, 20.2:9) cannot fit inside a 16:9
canvas at full width.** You must either shrink it — which is what the current
set does, to 59 % width — or crop it. The plan below crops, because the app's
screens are vertically sparse by design and the empty bands are exactly what
should go.

Render at **SS = 2** (2160 × 3840) and downsample with `Image.LANCZOS`, as
`make_store.py` already does.

### Margins and safe area

- Outer margin **84 px** (the existing `MARGIN`, kept).
- Nothing meaningful within **64 px** of any edge.
- The caption block's left edge aligns to the margin on every frame, including
  the full-bleed ones. One vertical alignment across all eight is what makes
  them read as a set.

### Type

| Element | Font | Size | Tracking | Position |
|---|---|---|---|---|
| Brand rail wordmark | Futura Medium (`FUTURA`, `FUT_MED`) | 21 px | +4.4 px | baseline y = 104, x = `MARGIN + 54`; rose mark 38 px at x = `MARGIN + 19` |
| Headline | Futura Medium | **88 px** (up from 73) | −1.0 px | first baseline y = 214, line advance 100 px, **max 2 lines** |
| Subhead | Helvetica Neue Light (`HELV`, `HN_LIGHT`) | **34 px** (up from 28) | 0 | starts 22 px below the headline block, **max 1 line, ~58 characters** |

Rationale for each change:

- **Headline 73 → 88 px.** 73 px already survives the thumbnail — this is not a
  rescue, it is headroom. 88 / 1080 = 8.1 % of canvas width, which holds up if
  Play ever renders search-result screenshots smaller than the 132 px measured
  here, and it lets the subhead go without the caption block looking thin.
- **Subhead 28 → 34 px, two lines → one.** At any plausible size a subhead is
  invisible in search results — 28 px is ~3 px at thumbnail, and 34 px is ~4 px.
  Its only audience is somebody on the expanded listing page, so the right move
  is not to make it thumbnail-legible (impossible) but to make it worth reading
  once and cost less canvas. One line at 34 px reads better on the detail page
  and returns ~80 px of vertical space to the image.
- **Drop the subhead entirely on slots 1, 2 and 8.** Slots 1–2 are full-bleed
  and have no room; slot 8 is a type frame whose caption is already the whole
  message.

### Colour

All values below are lifted from `app/src/main/java/com/pubpeb/compass/ui/theme/Theme.kt`.
Do not invent marketing colours.

**`NightPalette`** — the app's own night surfaces
`bgDeep #0D0F14` · `bgCard #161A22` · `textPrimary #CCD6F6` ·
`textSecondary #8090B0` · `roseRing #4A5464` · `northLabel #F0616C` ·
`levelWarn #E6A817` · `levelOk #4CAF80`

**`DayPalette`** — system light
`bgDeep #F2F3F6` · `textPrimary #12161D` · `textSecondary #515C6B` ·
`northLabel #C22A36`

**`DaylightPalette`** — maximum-contrast outdoor
`bgDeep #FFFFFF` · `textPrimary #000000` · `textSecondary #3A3A3A` ·
`northLabel #B3121F`

**Marketing ground — one change required.**

| Token | Current | Change to | Why |
|---|---|---|---|
| Night ground, top stop | `#161C28` | `#161C28` | unchanged |
| Night ground, **bottom stop** | `#080A0E` | **`#141B29`** | The current bottom stop is **darker than the app's own `bgDeep` (#0D0F14)**. The phone screen is therefore lighter than the ground beneath it and darker than the ground above it, and the silhouette crosses over and disappears. Lifting the bottom stop keeps the ground consistently lighter than the app screen so the device reads as an object at every height. |
| Bezel fill | `#2C3240` | `#2C3240` | unchanged |
| Bezel **edge** | `#495163` | **`#5A6478`** | clears 3:1 against both the new ground and the app screen, so the 1.5 px rim actually renders at listing size |

The day theme (`#FFFFFF` → `#E9EDF4`, bezel `#AFB8C9`, edge `#D7DDE8`) is
correct as it stands — frame 03 is the best-reading frame in the set and its
values are why. **Do not touch it.**

Red stays reserved for north, in the marketing exactly as in the app. The rose
mark's needle and slot 8's accent dot are the only red in the set.

### Layout A — full-bleed hero (slots 1, 2)

```
y 0 ─────────────────────────────  night ground gradient
      brand rail        (y = 104)
      headline, 88px    (y = 214, up to 2 lines)
y 520 ───────────────────────────  app capture begins
      1080 × 1400 native crop of the raw,
      bleeding to the left, right and bottom edges
y 1920 ──────────────────────────
```

- The capture is placed at **native 1080 px width — no resampling at all.**
  Crop a 1080 × 1400 band out of the 1080 × 2424 raw and paste it. This is
  sharper than anything the current pipeline produces.
- Slot 1 crop box: `(0, 620, 1080, 2020)` — contains the `47°` readout at
  y ≈ 630, the `TRUE · DECL` tag beneath it, and the full rose (≈ y 1315–1895).
  Drops the top icon row and the pager dots, both of which are chrome.
- Slot 2 crop box: `(0, 300, 1080, 1700)` — contains the `288°` HUD, both
  cardinal labels, and the strongest part of the photograph. Adjust to whatever
  a fresh outdoor capture needs; the rule is *keep the HUD and at least two
  cardinal labels, drop the ground clutter*.
- A 1 px hairline in `#5A6478` across the top of the capture separates it from
  the caption band.
- **Result:** app UI occupies 1080 × 1400 = 73 % of canvas area, against 39 %
  today.

### Layout B — device-framed (slots 3–7)

Keep the existing `device()` helper — the rounded bezel, the 1.5 px edge and
the blurred cast shadow are all good. Three changes:

1. **Crop each raw to its informative band before framing.** The app's screens
   are sparse and the empty bands should not be paid for twice. Suggested
   boxes, to be adjusted by eye:
   - `raw-04-daylight.png` (slot 3): `(0, 60, 1080, 1980)`
   - `raw-05-level.png` (slot 4): `(0, 500, 1080, 2000)` — keeps the bubble
     dial *and* the `PITCH` / `ROLL` readout, drops the 500 px of black above
   - calibrate capture (slot 5): `(0, 100, 1080, 1400)` — readout block only;
     the warning line is the subject
   - widget capture (slot 6): crop to the widget and its neighbouring icons
   - settings capture (slot 7): crop to the dialog card plus a little of the
     dimmed screen behind it
2. **Device width 640 → 700 px**, top edge y = 640. With the crops above the
   phone no longer needs to be small to fit.
3. **Keep the bottom bleed.** A device running off the bottom edge is a
   deliberate and well-established treatment; it reads correctly at thumbnail.

### Layout C — type only (slot 8)

Rose mark at 340 px diameter centred at (540, 700); wordmark beneath it in the
rail treatment at 2× scale; the three promises stacked in 88 px Futura, centred,
starting at y = 1180. Night ground, no device, no photograph.

### The thumbnail rule

> **The first two frames must survive being shrunk to thumbnail size.**

Concretely: downscale the finished PNG to **132 px wide** and look at it at
100 %. The frame passes only if, without any prior knowledge of the app, you
can (a) read the headline and (b) tell from the imagery alone that this is a
compass. If either fails, the frame is not finished.

Slots 3–8 should be checked the same way but are allowed to fail (b) — by then
the user has already decided to look.

Verify the whole set with one command:

```bash
cd docs/launch/assets/screenshots
python3 - <<'PY'
from PIL import Image
import glob
TW = 132
fs = sorted(glob.glob("*.png"))
ts = [Image.open(f).convert("RGB").resize((TW, round(TW*1920/1080)), Image.LANCZOS) for f in fs]
sheet = Image.new("RGB", (len(ts)*(TW+16)+16, ts[0].height+32), (255,255,255))
for i,t in enumerate(ts): sheet.paste(t, (16+i*(TW+16), 16))
sheet.resize((sheet.width*3, sheet.height*3), Image.NEAREST).save("/tmp/thumbs.png")
for f,t in zip(fs,ts):
    px = list(t.convert("L").getdata()); n=len(px)
    print(f"{f:20s} <40:{sum(v<40 for v in px)/n:5.1%}  >=170:{sum(v>=170 for v in px)/n:5.1%}")
PY
open /tmp/thumbs.png
```

Target: **no frame above ~65 % sub-L40**, and no two adjacent frames within
10 percentage points of each other on that measure. That second rule is what
stops frames 01 and 04 from repeating their current 78.1 % / 78.6 % collision.

---

## 5. Feature graphic verdict

**Keep and retouch. Do not redo, and do not over-invest.**

The mark is the app's own launcher geometry, the palette is the app's own, and
the spec is already correct (1024 × 500, RGB, no alpha — verified). It is also
worth knowing, before spending an afternoon on it, exactly where this asset
appears: per Google's own page it is the cover image for **preview videos,
large-format app collections, and recommended game groups**. It is not the
listing header a user sees when they tap through from search. This listing has
no promo video and is not a game. So the feature graphic is close to
invisible in practice — it is mandatory to publish and little else.

Three retouches, all inside `tools/make_feature.py`:

1. **Centre the lockup.** The mark currently sits at x ≈ 172 and the wordmark
   block ends near x ≈ 700, leaving the right ~320 px empty. Shift the whole
   group right by ~110 px so it occupies a centred band roughly x 280–900.
   Rationale: Play scales and crops this asset across surfaces you cannot
   preview, so an off-centre lockup crops asymmetrically. Note honestly that
   Google's page states no crop rule — this is prudence, not a requirement.
2. **Drop the `NO ADS · NO SIGN-IN` kicker.** At 19 px on a 1024 px canvas it
   is 1.9 % of width; rendered as a ~320 px video cover it is under 6 px and
   cannot be read by anyone. It is costing composition to communicate nothing.
   The claim already appears in the short description, the full description and
   slot 8 of the screenshots.
3. **Raise the tagline.** `True north, nothing else.` goes from 27 px to
   **38 px**, which makes it legible at the sizes this asset is actually
   rendered. With the kicker gone there is room.

Keep the 60 px `MARGIN`, the vertical rule between mark and wordmark, and the
radial glow. One graphic, one message: the mark, the name, and four words.

**Icons: no change.** Both files are correct, the filled one is correctly
nominated for upload, and the transparent one is correctly reserved for press
kit and website use.

---

## 6. Production path

### Ranked by effort and by what it costs you

| Rank | Approach | Effort | Verdict |
|---|---|---|---|
| **1** | **Extend the existing Python/Pillow pipeline** (`tools/make_store.py`) | **~3 h** | **Recommended.** |
| 2 | HTML/CSS compositor rendered headless via Playwright | ~6 h + a new toolchain | Rejected |
| 3 | ImageMagick compositing | ~5 h | Rejected |
| 4 | Figma / Canva artboards | ~4 h, then permanent | Rejected |

**Why the existing pipeline wins, plainly.** It already exists, is committed,
is documented in `tools/README.md`, reads the app's real palette from
`Theme.kt` rather than a copy of it, shares its rose geometry with the launcher
icon so the two cannot drift, has no dependencies beyond Pillow, and
regenerates every asset byte-identically from one command. The work ahead is
*adding four frames and changing some numbers* — not building a compositor.
Every alternative starts by throwing that away.

**Why not HTML/CSS + Playwright,** specifically, since it was suggested: it
would genuinely give better typography (real text layout instead of `brand.py`'s
per-character `tracked()` loop) and nicer iteration. But this repo has **no
`package.json` and no `node_modules`** — `site/` is four static files deployed
to Vercel, not a build. So this means adopting Node, Playwright and a browser
binary as build dependencies for a set of eight PNGs, and maintaining two
rendering stacks (Pillow still draws the icon and the launcher vectors). The
typography gain does not pay for that. Revisit only if you ever want a
non-developer to edit these.

**Why not Figma:** it produces the best-looking result fastest, and it takes
the assets out of git permanently. The repo's stated principle — *"Every store
graphic is generated, not hand-placed, so a change to the mark or the palette
flows through the whole set in one run"* — is worth more over the life of the
app than one afternoon saved.

### Concrete steps

**Step 1 — capture the three missing raws** (see §7 for the exact device
steps). Save into `docs/launch/assets/screenshots-raw/` as:

```
raw-03-truenorth.png     # slot 1 — TRUE tag + declination
raw-06-calibrate.png     # slot 5 — CALIBRATE · FIGURE 8
raw-08-settings.png      # slot 7 — settings dialog
```

Optionally re-shoot `raw-02-ar-forest.png` (real horizon) and
`raw-07-widget.png` (plain wallpaper, tighter tile) over the same filenames.

**Step 2 — update `tools/make_store.py`.** Four edits:

1. Add a `layout` key to each `FRAMES` tuple: `"hero"`, `"framed"` or `"type"`.
2. Add a per-frame `crop` box, applied to the raw before anything else.
3. Bump the type scale (headline 73 → 88, subhead 28 → 34, one line) and the
   night ground's bottom stop (`0x08,0x0A,0x0E` → `0x14,0x1B,0x29`) and bezel
   edge (`0x49,0x51,0x63` → `0x5A,0x64,0x78`).
4. Add the `hero()` and `type_frame()` builders alongside the existing
   `device()`.

**Step 3 — regenerate everything**, in the order `tools/README.md` requires
(`ar_composite.py` writes what `make_store.py` reads):

```bash
export PATH="$PATH"   # nothing special needed; Pillow only
cd /Users/habuild/codes/personal/minimal-compass/docs/launch/assets/tools
python3 ar_composite.py \
  && python3 make_feature.py \
  && python3 make_store.py \
  && python3 make_icon.py \
  && python3 make_vector.py
```

**Step 4 — run the thumbnail gate** from §4 and check the two rules: no frame
above ~65 % sub-L40, no two adjacent frames within 10 points of each other.

**Step 5 — verify the output spec before upload:**

```bash
cd /Users/habuild/codes/personal/minimal-compass/docs/launch/assets
python3 -c "
from PIL import Image; import glob
for f in sorted(glob.glob('screenshots/*.png')) + ['feature-graphic-1024x500.png','play-icon-512.png']:
    i = Image.open(f); print(f'{f:34s} {i.mode:5s} {i.size}')
"
```

Every screenshot must print `RGB (1080, 1920)`, the feature graphic
`RGB (1024, 500)`, the icon `RGB (512, 512)`. **Any `RGBA` in that list is a
rejection** — Play requires no alpha on screenshots and the feature graphic.

**Step 6 — update the docs that describe the set:** the asset table and the
"The screenshot set" section in `play-console-answers.md` (which currently
documents four frames and explains why the widget and settings frames were
cut), and `tools/README.md` (which says `make_store.py` produces "the five
1080 × 1920 listing screenshots" — it currently produces four).

---

## 7. Capture checklist

### Before you start — screen timeout

Save and raise the screen timeout so the device never sleeps mid-session, and
restore it afterwards:

```bash
adb shell settings get system screen_off_timeout          # note the value
adb shell settings put system screen_off_timeout 2147483647
# ... capture everything ...
adb shell settings put system screen_off_timeout <saved value>
```

### Demo-mode status bar — run this first, every session

A clean status bar is what makes the set look deliberate. Straight from
`tools/README.md`:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# ... capture ...
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

Capture with `adb exec-out screencap -p > <file>.png`.

**Use the real device, not the emulator.** Three of these states are
impossible on an emulator: the emulator's GMS network location provider is
disabled so no fix ever returns (no `TRUE`), its magnetometer always reports
high accuracy (no `CALIBRATE`), and it has no real camera pointed at a real
horizon.

Per the repo's own rule, drive the UI through **android-mcp**
(`mcp__android__*`) rather than guessing at `adb shell input` coordinates.

### The states

| # | State | How to get there |
|---|---|---|
| 1 | **Compass, `TRUE` + declination** | Page 0 (the default). Grant approximate location when prompted, then **tap the `MAG` tag** beneath the cardinal label — it is the only control on that screen and toggles to `TRUE · x.x° DECL`. A fix is needed for the declination to resolve, so be outdoors or near a window. Wait for the accuracy warning to clear (the confidence line goes silent when the heading is trustworthy). Hold a steady heading; `47° NE` matches the rest of the set. |
| 2 | **AR overlay, real horizon** | Page 0. Either **raise the phone past 65° of tilt and hold for 800 ms** (the auto-trigger; `Constants.Ar.ENTER_DEG = 65f`, `DWELL_MS = 800L`), or **tap the camera icon** in the top bar to force it. Grant the camera permission first. Point at a treeline, ridge or street with depth. Capture with at least two cardinal labels over sky or distance, not clutter. Exit by lowering below 50° (`EXIT_DEG`) or tapping the `×`. |
| 3 | **Daylight mode** | Page 0. **Tap the sun icon** in the top bar (middle of the three). The whole UI inverts to the white maximum-contrast palette. The icon becomes a moon while daylight is active — that is the frame you want. |
| 4 | **Bubble level** | **Swipe left** from the compass to page 1. Tilt the phone a few degrees off flat so the bubble sits visibly off-centre and the readout shows non-zero values — a perfectly centred bubble reads as a mock-up. `6°` / `−10°` as in the current raw is ideal. |
| 5 | **`CALIBRATE · FIGURE 8`** | Page 0, with the magnetometer forced to `UNRELIABLE`. Bring a magnet, a speaker, a laptop hinge or a steel desk frame close to the back of the phone and hold it there until the amber `CALIBRATE · FIGURE 8` line appears under the heading (`confidenceNote()` in `CompassScreen.kt:520`). Capture **while the interference is still present** — the line clears within a second or two of removing it. Do not capture the `NO SENSOR SIGNAL` state by mistake; that one is red (`levelCritical`), not amber. |
| 6 | **Home-screen widget** | Set a plain dark wallpaper first. Long-press the home screen → Widgets → Compass → drag the tile into the **upper-left** cell cluster. Leave four or five ordinary app icons nearby for scale and keep the rest of the screen sparse. The tile needs ≥ 140 dp of width to show the wordmark alongside the mark, so place it at least two cells wide. |
| 7 | **Settings dialog** | Page 0. **Tap the gear icon**, top right. The dialog shows the single `Auto-switch to AR` switch with its subtitle. Leave the switch **on** — that is the default and the frame is about the behaviour being consensual, not about disabling it. |
| 8 | **Promise frame** | No capture. Generated from `brand.py`. |

### After capture

Restore the screen timeout, exit demo mode, and confirm every new raw is
1080 × 2424 RGB:

```bash
cd /Users/habuild/codes/personal/minimal-compass/docs/launch/assets/screenshots-raw
python3 -c "
from PIL import Image; import glob
for f in sorted(glob.glob('*.png')): i=Image.open(f); print(f'{f:28s} {i.mode} {i.size}')
"
```

Note that `screenshots-raw/raw-02-ar-forest.png` is git-ignored (it is
generated); every other raw is committed, so new captures need to be added to
git.

---

## 8. Open questions

1. **What is actually live in Play Console right now?** The reviewer's "no
   ASO" note does not match the committed set, which is fully captioned and
   framed. Before rebuilding anything, confirm whether Console currently holds
   these four frames, an older set, or the raws. If it holds the raws, the fix
   is an upload, not a redesign.
2. **Real AR capture, or keep the composite?** `raw-02-ar-forest.png` is a
   disclosed composite — real app chrome over a generated forest. It is
   ordinary practice and is not a misrepresentation, but the repo already flags
   it as a judgement call that is yours. Twenty minutes outdoors removes the
   question permanently. Which do you want?
3. **Eight frames or six?** Slots 7 (settings) and 8 (promise) are the weakest
   of the eight and slot 7 was previously cut for good reasons. Six strong
   frames is a perfectly good listing. Do you want the full eight?
4. **Does slot 1 have to show `TRUE`?** It requires a location fix on a real
   device, outdoors. If that is awkward, the fallback caption is
   `A bearing, the second it opens.` — but then the app's own title promises a
   "True North" that no screenshot ever demonstrates.
5. **Tablets.** The listing currently opts out, which is defensible for 1.0.0
   and sidesteps the large-screen question. Google's *adaptive app* guidance
   says adaptive apps rank higher and get picked for Editors' Choice more
   often — an upside forfeited, not a penalty incurred. Revisit at 1.1?
6. **Promo video.** None exists. Without one, the feature graphic is close to
   invisible (§5), since its main documented use is as a video cover. Worth
   considering later; not worth blocking 1.0.0 on.
