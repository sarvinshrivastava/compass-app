# Store asset generators

Every store graphic in `docs/launch/assets/` is generated, not hand-placed, so a
change to the mark or the palette flows through the whole set in one run.
Requires Python 3 with Pillow, plus ImageMagick for the mipmap re-encode.

| Script | Produces |
|---|---|
| `brand.py` | Shared kit: the rose, the palette (read from `ui/theme/Theme.kt`), gradients, tracked type. Nothing is drawn twice. |
| `make_icon.py` | `play-icon-512.png` and `play-icon-512-transparent.png` |
| `make_vector.py` | `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` — the launcher icon, from the same geometry as the store icon |
| `make_feature.py` | `feature-graphic-1024x500.png` |
| `ar_composite.py` | `screenshots-raw/raw-02-ar-forest.png` — the real AR chrome over a new camera feed |
| `make_store.py` | The five 1080 × 1920 listing screenshots |

Run them from this directory; paths are relative to it and the raws are read
from `../screenshots-raw/`. From a fresh clone, in this order — `ar_composite.py`
first, because `make_store.py` reads what it writes:

```bash
cd docs/launch/assets/tools
python3 ar_composite.py && python3 make_feature.py && python3 make_store.py \
  && python3 make_icon.py && python3 make_vector.py
```

Only the inputs are committed. Every output above is git-ignored, because all of
it comes back byte-identical from this one command. To swap a frame, drop a replacement capture into
`screenshots-raw/` under the same filename and re-run `make_store.py` — the
caption treatment is identical across the set by design.

**The launcher icon and the store icon must be regenerated together.** They
share `brand.py`'s geometry precisely so the Play tile and the home-screen icon
cannot drift into being two different marks; hand-editing either one breaks
that.

## Capturing new raws

The emulator cannot produce three of the frames. `TRUE` needs a location fix
and the emulator's GMS network location provider is disabled; `CALIBRATE`
needs a magnetometer that reports low accuracy and the emulator's is always
high; a real AR frame needs a real camera pointed at a real horizon. All three
are a two-minute job on a phone:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb exec-out screencap -p > screenshots-raw/raw-02-ar.png
adb shell am broadcast -a com.android.systemui.demo -e command exit
```
