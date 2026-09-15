# `GeomagneticField` still ships WMM-2020, and that is not a bug to fix

Checked 2026-09-14 against AOSP `main`, after a review flagged "every TRUE
reading runs an expired model" as a release blocker. It is not one, and the
reasoning is worth keeping so nobody re-opens it.

## What the platform actually ships

`frameworks/base/core/java/android/hardware/GeomagneticField.java` on the
**main** branch:

- coefficients are **WMM-2020**, `BASE_TIME` = 1 January 2020
- the class doc says it "currently uses WMM-2020 which is valid until 2025, but
  should produce acceptable results for several years after that"
- `DELTA_G` / `DELTA_H` carry the annual rate of change, so the model
  extrapolates linearly past its epoch rather than freezing

So: yes, past the nominal validity window. No, there is no newer platform API to
switch to — this is the only declination source Android offers, and `main` has
not moved.

## Why the "fix" is not worth doing

Hand-bundling WMM-2025 coefficients would mean reimplementing the spherical
harmonic evaluation `GeomagneticField` already does, to buy back the secular
variation error the `DELTA` arrays are specifically designed to cover — a few
tenths of a degree at mid-latitudes a couple of years past epoch.

Against a magnetometer whose own error is several degrees routinely, and 20–90°
near a car or a laptop (see `sensor-orientation-and-remap.md`), that is noise
under noise. The declination correction is not the weak link in this app's
heading and making it more precise improves nothing a user can perceive.

## What the underlying concern actually points at

The real gap it surfaced is that the app reports `TRUE` as a confident binary
with no error bar at all. The honest answer to "how wrong might this be" is a
confidence figure on the heading — not a better magnetic model.

Two corollaries for the store listing: do not claim survey-grade accuracy, and
do not claim a current magnetic model.
