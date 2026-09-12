from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
from brand import (draw_rose, vertical_gradient, radial_glow, cover, font, tracked,
                   text_width, FUTURA, HELV, FUT_MED, HN_LIGHT, HN_MED,
                   TEXT, TEXT_DIM, RED)
import os

REPO = "/Users/habuild/codes/personal/minimal-compass/docs/launch/assets"
RAW = "../screenshots-raw/"
OUT = f"{REPO}/screenshots"
os.makedirs(OUT, exist_ok=True)

W, H, SS = 1080, 1920, 2
MARGIN = 84 * SS

NIGHT_THEME = dict(top=(0x16, 0x1C, 0x28), bottom=(0x08, 0x0A, 0x0E),
                   head=TEXT, sub=TEXT_DIM, rail=(0x6E, 0x78, 0x8C),
                   bezel=(0x2C, 0x32, 0x40), edge=(0x49, 0x51, 0x63))
DAY_THEME = dict(top=(0xFF, 0xFF, 0xFF), bottom=(0xE9, 0xED, 0xF4),
                 head=(0x0D, 0x10, 0x17), sub=(0x59, 0x62, 0x74), rail=(0x8A, 0x93, 0xA6),
                 bezel=(0xAF, 0xB8, 0xC9), edge=(0xD7, 0xDD, 0xE8))

FRAMES = [
    ("01-compass.png", RAW + "raw-01-compass.png", "night",
     "Open it.\nRead the bearing.",
     "Degrees and cardinal direction the moment it opens. No sign-up, no banner, nothing to dismiss."),
    ("02-ar.png", RAW + "raw-02-ar-forest.png", "night",
     "North, on the\nreal horizon.",
     "Raise the phone and the cardinal labels settle onto the scene, placed with your lens's real field of view."),
    ("03-daylight.png", RAW + "raw-04-daylight.png", "day",
     "Still readable\nat noon.",
     "A maximum-contrast daylight mode, for when the dark theme has turned into a mirror."),
    ("04-level.png", RAW + "raw-05-level.png", "night",
     "A spirit level,\none swipe away.",
     "Live pitch and roll in degrees. For the shelf, the tripod, and the picture that is not quite straight."),
]

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


def device(canvas, shot_path, theme, top, width):
    shot = Image.open(shot_path).convert("RGB")
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
    d.rounded_rectangle([x0 - b, y0 - b, x0 + sw + b, y0 + sh + b], r + b, fill=theme["bezel"] + (255,))
    d.rounded_rectangle([x0 - b, y0 - b, x0 + sw + b, y0 + sh + b], r + b,
                        outline=theme["edge"] + (255,), width=max(1, round(1.5 * SS)))

    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], r, fill=255)
    canvas.paste(shot, (x0, y0), mask)


DEVICE = {  # width, top — sized so the subject of each frame lands large
    "01-compass.png": (640, 662),
    "02-ar.png": (640, 662),
    "03-daylight.png": (640, 662),
    "04-level.png": (726, 636),
}


def build(name, raw, kind, headline, sub):
    theme = DAY_THEME if kind == "day" else NIGHT_THEME

    canvas = vertical_gradient((W * SS, H * SS), theme["top"], theme["bottom"])
    if kind != "day":
        canvas.alpha_composite(radial_glow((W * SS, H * SS), (W * SS * 0.22, H * SS * 0.06),
                                           W * SS * 0.85, (0x2C, 0x39, 0x55), 185))

    d = ImageDraw.Draw(canvas)

    # Brand rail — same place, same size, every frame.
    draw_rose(canvas, MARGIN + 19 * SS, 104 * SS, 38 * SS, ground=False)
    d = ImageDraw.Draw(canvas)
    f_rail = font(FUTURA, FUT_MED, 21 * SS)
    tracked(d, (MARGIN + 54 * SS, 104 * SS), "MINIMAL COMPASS", f_rail, theme["rail"] + (255,),
            track=4.4 * SS, anchor="lm")

    f_head = font(FUTURA, FUT_MED, 73 * SS)
    y = 214 * SS
    for line in headline.split("\n"):
        tracked(d, (MARGIN, y), line, f_head, theme["head"] + (255,), track=-0.8 * SS)
        y += 86 * SS

    y += 22 * SS
    f_sub = font(HELV, HN_LIGHT, 28 * SS)
    for line in wrap(d, sub, f_sub, (W - 168 - 80) * SS):
        d.text((MARGIN, y), line, font=f_sub, fill=theme["sub"] + (255,), anchor="la")
        y += 40 * SS

    dw, dtop = DEVICE[name]
    device(canvas, raw, theme, top=dtop, width=dw)
    canvas.convert("RGB").resize((W, H), Image.LANCZOS).save(f"{OUT}/{name}", quality=100)
    print(name)


for stale in os.listdir(OUT):
    os.remove(os.path.join(OUT, stale))
for args in FRAMES:
    build(*args)
