"""Build the Play Store phone screenshots.

Three layouts, per `docs/launch/aso-screenshot-plan.md`:

  A  full-bleed hero   slots 1-2   app capture pasted at NATIVE 1080 px, no resampling
  B  device-framed     slots 3-7   the existing bezel + cast shadow, over a cropped raw
  C  type only         slot 8      mark, wordmark, promises

Why the layouts differ at all: slots 1 and 2 are the two frames almost everyone
sees, so they get the most image and the least chrome. The measured failure of
the previous set was that the app UI occupied 39 % of the canvas and two frames
were 78 % near-black, which at the ~132 px Play actually renders in search
results made them two indistinguishable dark slabs. Layout A puts the app at
73 % of canvas area and at native resolution.

Run:  python3 make_store.py         (from this directory)
"""
from PIL import Image, ImageDraw, ImageFilter
from brand import (draw_rose, vertical_gradient, radial_glow, font, tracked,
                   text_width, FUTURA, HELV, FUT_MED, HN_LIGHT,
                   TEXT, TEXT_DIM)
import os
import sys

REPO = "/Users/habuild/codes/personal/minimal-compass/docs/launch/assets"
RAW = f"{REPO}/screenshots-raw/"
OUT = f"{REPO}/screenshots"
os.makedirs(OUT, exist_ok=True)

W, H, SS = 1080, 1920, 2
MARGIN = 84 * SS

# Type — sizes raised from the previous set. 73 px already survived the
# thumbnail; 88 is headroom in case Play ever renders smaller than the 132 px
# measured here, and it stops the caption block reading thin without a subhead.
HEAD_PX, HEAD_TRACK, HEAD_Y, HEAD_ADVANCE = 88, -1.0, 214, 100
SUB_PX, SUB_GAP = 34, 22
RAIL_PX, RAIL_TRACK, RAIL_Y = 21, 4.4, 104

# The night ground's bottom stop was #080A0E — DARKER than the app's own
# bgDeep (#0D0F14), so a device running off the bottom edge dissolved into the
# background instead of sitting on it. Raised above bgDeep so the silhouette
# reads.
NIGHT_THEME = dict(top=(0x16, 0x1C, 0x28), bottom=(0x14, 0x1B, 0x29),
                   head=TEXT, sub=TEXT_DIM, rail=(0x6E, 0x78, 0x8C),
                   bezel=(0x2C, 0x32, 0x40), edge=(0x49, 0x51, 0x63),
                   hairline=(0x5A, 0x64, 0x78))
DAY_THEME = dict(top=(0xFF, 0xFF, 0xFF), bottom=(0xE9, 0xED, 0xF4),
                 head=(0x0D, 0x10, 0x17), sub=(0x59, 0x62, 0x74),
                 rail=(0x8A, 0x93, 0xA6), bezel=(0xAF, 0xB8, 0xC9),
                 edge=(0xD7, 0xDD, 0xE8), hairline=(0xC2, 0xCA, 0xD8))

# Crop boxes into the 1080 x 2424 raws. The app's screens are vertically sparse
# by design; these keep the informative band and drop the empty one rather than
# shrinking the whole capture to fit.
FRAMES = [
    dict(name="01-compass.png", raw="raw-01-compass.png", theme="night", layout="A",
         head="True north, the\nsecond it opens.", sub=None,
         crop=(0, 560, 1080, 1960)),

    dict(name="02-ar.png", raw="raw-02-ar-forest.png", theme="night", layout="A",
         head="North, on the\nreal horizon.", sub=None,
         crop=(0, 300, 1080, 1700),
         pending="Current raw is the disclosed composite from ar_composite.py. "
                 "A real outdoor capture is preferred but this is shippable."),

    dict(name="03-daylight.png", raw="raw-04-daylight.png", theme="day", layout="B",
         head="Readable in\ndirect sunlight.",
         sub="For when the dark theme becomes a mirror.",
         crop=(0, 60, 1080, 1980)),

    dict(name="04-level.png", raw="raw-05-level.png", theme="night", layout="B",
         head="A spirit level,\none swipe away.",
         sub="For the shelf, the tripod, the crooked picture.",
         crop=(0, 500, 1080, 2000)),

    dict(name="05-calibrate.png", raw="raw-06-calibrate.png", theme="night", layout="B",
         head="It tells you\nwhen it's unsure.",
         sub="Most compass apps stay confident while wrong.",
         crop=(0, 100, 1080, 1400),
         pending="No raw exists. Needs a real-device capture with the amber "
                 "CALIBRATE line showing — hold a magnet or a laptop hinge near "
                 "the back of the phone. The emulator's magnetometer always "
                 "reports HIGH accuracy, so this state is unreachable there."),

    dict(name="06-widget.png", raw="raw-07-widget.png", theme="night", layout="B",
         head="One tap from the\nhome screen.",
         sub="No stale heading, because a stale heading is a guess.",
         crop=None),

    dict(name="07-settings.png", raw="raw-09-settings.png", theme="night", layout="B",
         head="The clever part\nis optional.",
         sub="Calibration help, always one tap away.",
         crop=(0, 600, 1080, 1420)),

    dict(name="08-promise.png", raw=None, theme="day", layout="C",
         head=None, sub=None, crop=None,
         promises=["No ads.", "No sign-in.", "No tracking."]),
]

# Alternate raw for slot 4, showing the upright/plumb instrument instead of the
# bullseye. Kept out of the default set on purpose: the bullseye is the more
# recognisable "this is a level" silhouette at thumbnail size, and the plan's
# rule is that six strong frames beat eight padded ones. Swap it in by hand if
# you would rather lead on the two-posture story.
ALT_LEVEL_RAW = "raw-08-level-plumb.png"

DEVICE = {  # width, top edge — bigger than the old 640 now that the raws are cropped
    "03-daylight.png": (700, 640),
    "04-level.png": (700, 640),
    "05-calibrate.png": (700, 640),
    "06-widget.png": (700, 640),
    "07-settings.png": (880, 700),
}


def wrap(d, text, f, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=f) <= max_w or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def load_crop(raw_name, crop):
    """Open a raw capture and crop it, flattening any alpha channel.

    `adb exec-out screencap -p` emits RGBA; pasting that straight onto the
    canvas punches the ground through wherever the app drew transparency.
    """
    img = Image.open(RAW + raw_name)
    if img.mode != "RGB":
        img = img.convert("RGB")
    return img.crop(crop) if crop else img


def caption(canvas, theme, head, sub):
    """Brand rail + headline + optional subhead, at supersampled scale."""
    d = ImageDraw.Draw(canvas)

    draw_rose(canvas, MARGIN + 19 * SS, RAIL_Y * SS, 38 * SS, ground=False)
    d = ImageDraw.Draw(canvas)
    f_rail = font(FUTURA, FUT_MED, RAIL_PX * SS)
    tracked(d, (MARGIN + 54 * SS, RAIL_Y * SS), "MINIMAL COMPASS", f_rail,
            theme["rail"] + (255,), track=RAIL_TRACK * SS, anchor="lm")

    if not head:
        return

    f_head = font(FUTURA, FUT_MED, HEAD_PX * SS)
    y = HEAD_Y * SS
    for line in head.split("\n")[:2]:          # two lines, hard cap
        tracked(d, (MARGIN, y), line, f_head, theme["head"] + (255,),
                track=HEAD_TRACK * SS)
        y += HEAD_ADVANCE * SS

    if sub:
        y += SUB_GAP * SS
        f_sub = font(HELV, HN_LIGHT, SUB_PX * SS)
        # One line only. A subhead is ~4 px at thumbnail no matter what we do,
        # so its only audience is the expanded listing page — it should cost
        # one line of canvas there, not three.
        #
        # Taking wrap()[0] silently truncated every subhead mid-sentence when
        # the copy did not happen to fit, and nothing flagged it — the frames
        # rendered and passed the pending gate looking finished. Fail loudly
        # instead, and keep the copy short enough to fit.
        lines = wrap(d, sub, f_sub, (W - 168) * SS)
        if len(lines) > 1:
            raise SystemExit(
                f"subhead too long for one line and would be truncated: {sub!r}\n"
                f"  would render: {lines[0]!r}\n"
                f"  dropped:      {' '.join(lines[1:])!r}"
            )
        d.text((MARGIN, y), lines[0], font=f_sub, fill=theme["sub"] + (255,), anchor="la")


def ground(theme, kind):
    canvas = vertical_gradient((W * SS, H * SS), theme["top"], theme["bottom"])
    if kind != "day":
        canvas.alpha_composite(radial_glow(
            (W * SS, H * SS), (W * SS * 0.22, H * SS * 0.06),
            W * SS * 0.85, (0x2C, 0x39, 0x55), 185))
    return canvas


def device(canvas, shot, theme, top, width):
    """The existing bezel treatment: rounded frame, hairline edge, cast shadow."""
    sw = width * SS
    sh = round(sw * shot.height / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x0 = (W * SS - sw) // 2
    y0 = top * SS
    b = 11 * SS
    r = 42 * SS

    # Cast shadow: the phone sits in the scene rather than on top of it.
    sh_img = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh_img).rounded_rectangle(
        [x0 - b, y0 - b + 18 * SS, x0 + sw + b, y0 + sh + b + 40 * SS], r + b,
        fill=(0, 0, 0, 150))
    canvas.alpha_composite(sh_img.filter(ImageFilter.GaussianBlur(34 * SS)))

    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle([x0 - b, y0 - b, x0 + sw + b, y0 + sh + b], r + b,
                        fill=theme["bezel"] + (255,))
    d.rounded_rectangle([x0 - b, y0 - b, x0 + sw + b, y0 + sh + b], r + b,
                        outline=theme["edge"] + (255,), width=max(1, round(1.5 * SS)))

    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], r, fill=255)
    canvas.paste(shot, (x0, y0), mask)


def build_a(spec, theme):
    """Full-bleed hero. Caption is supersampled; the capture is not touched."""
    canvas = ground(theme, spec["theme"])
    caption(canvas, theme, spec["head"], spec["sub"])
    flat = canvas.convert("RGB").resize((W, H), Image.LANCZOS)

    shot = load_crop(spec["raw"], spec["crop"])
    if shot.width != W:
        shot = shot.resize((W, round(W * shot.height / shot.width)), Image.LANCZOS)

    top = H - shot.height
    # Downsample the caption band first and paste the capture onto the finished
    # 1080-wide canvas, so the app pixels land 1:1. Compositing at SS and
    # downsampling the whole thing would put every screenshot through a
    # resample it does not need.
    flat.paste(shot, (0, top))
    ImageDraw.Draw(flat).line([(0, top), (W, top)], fill=theme["hairline"], width=1)
    return flat


def build_b(spec, theme):
    canvas = ground(theme, spec["theme"])
    caption(canvas, theme, spec["head"], spec["sub"])
    shot = load_crop(spec["raw"], spec["crop"])
    width, top = DEVICE[spec["name"]]
    device(canvas, shot, theme, top=top, width=width)
    return canvas.convert("RGB").resize((W, H), Image.LANCZOS)


def build_c(spec, theme):
    canvas = ground(theme, spec["theme"])
    draw_rose(canvas, W * SS // 2, 700 * SS, 340 * SS, ground=False)
    d = ImageDraw.Draw(canvas)

    f_mark = font(FUTURA, FUT_MED, RAIL_PX * 2 * SS)
    track = RAIL_TRACK * 2 * SS
    w = text_width(d, "MINIMAL COMPASS", f_mark, track)
    tracked(d, ((W * SS - w) / 2, 960 * SS), "MINIMAL COMPASS", f_mark,
            theme["rail"] + (255,), track=track, anchor="lm")

    f_head = font(FUTURA, FUT_MED, HEAD_PX * SS)
    y = 1180 * SS
    for line in spec["promises"]:
        lw = text_width(d, line, f_head, HEAD_TRACK * SS)
        tracked(d, ((W * SS - lw) / 2, y), line, f_head, theme["head"] + (255,),
                track=HEAD_TRACK * SS)
        y += HEAD_ADVANCE * SS
    return canvas.convert("RGB").resize((W, H), Image.LANCZOS)


BUILDERS = {"A": build_a, "B": build_b, "C": build_c}


def main():
    for stale in os.listdir(OUT):
        os.remove(os.path.join(OUT, stale))

    built, blocked = [], []
    for spec in FRAMES:
        theme = DAY_THEME if spec["theme"] == "day" else NIGHT_THEME
        raw = spec.get("raw")
        if raw and not os.path.exists(RAW + raw):
            blocked.append((spec["name"], f"missing raw {raw} — {spec.get('pending', '')}"))
            continue
        img = BUILDERS[spec["layout"]](spec, theme)
        img.save(f"{OUT}/{spec['name']}", quality=100)
        built.append(spec["name"])
        print(f"  built  {spec['name']}")

    for name, why in blocked:
        print(f"  SKIP   {name}: {why}")

    pending = [s for s in FRAMES if s.get("pending") and s["name"] in built]
    if pending or blocked:
        print("\n" + "=" * 72)
        print("NOT READY TO UPLOAD — these frames need a real-device capture first:")
        for s in pending:
            print(f"\n  {s['name']}\n    {s['pending']}")
        for name, why in blocked:
            print(f"\n  {name}\n    {why}")
        print("=" * 72)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
