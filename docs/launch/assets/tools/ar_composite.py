"""Put the app's real AR chrome over the generated forest POV.

Every overlay element below is lifted pixel-for-pixel out of a real capture of
ArOverlayScreen — the bounding boxes came from a connected-components pass on
the capture, not from eyeballing. Only the camera feed behind them is replaced.
"""
from PIL import Image, ImageDraw, ImageChops

SRC = Image.open("../screenshots-raw/raw-02-ar.png").convert("RGB")   # real capture, 1080x2424
W, H = SRC.size

# id: (x, y, w, h, corner radius)  — radius None means a full circle/pill
CHIPS = [
    ("hud",   331, 163, 419, 163, None),
    ("close",  21, 163, 126, 126, None),
    ("torch", 895, 2176, 139, 139, None),
    ("w",     276,  950,  84,  85, 14),
    ("nw",    821,  950, 103,  85, 14),
    ("nav",   398, 2388, 284,  10, 5),
]


def cover(img, w, h):
    """Scale-and-crop to fill, centred."""
    s = max(w / img.width, h / img.height)
    r = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    return r.crop(((r.width - w) // 2, (r.height - h) // 2,
                   (r.width - w) // 2 + w, (r.height - h) // 2 + h))


out = cover(Image.open("../screenshots-raw/forest-pov.png").convert("RGB"), W, H)

SS = 4  # supersample the chip masks so the rounded corners are not stair-stepped
for name, x, y, w, h, rad in CHIPS:
    m = Image.new("L", (w * SS, h * SS), 0)
    d = ImageDraw.Draw(m)
    r = (min(w, h) / 2 if rad is None else rad) * SS
    d.rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], r, fill=255)
    out.paste(SRC.crop((x, y, x + w, y + h)), (x, y), m.resize((w, h), Image.LANCZOS))

# Status bar. The AR capture's own bar is dark glyphs baked onto the old
# scene's wood and cannot be lifted cleanly, so take the real white glyphs from
# the night compass capture instead — drawing a replacement by hand left this
# frame with a different wifi and battery shape from every other screenshot.
# The forest's sky is bright, so the glyphs carry their own soft shadow, which
# is what Android does over a light camera preview.
from PIL import ImageFilter

BAR_H = 118
bar_src = Image.open("../screenshots-raw/raw-01-compass.png").convert("RGB").crop((0, 0, W, BAR_H))
br, bg_, bb = bar_src.split()
lum = ImageChops.lighter(ImageChops.lighter(br, bg_), bb)
glyphs = lum.point(lambda v: min(255, max(0, (v - 90) * 4)))

shadow = Image.new("RGBA", (W, BAR_H), (0, 0, 0, 0))
shadow.paste((0, 0, 0, 165), (0, 0), glyphs)
shadow = shadow.filter(ImageFilter.GaussianBlur(7))
out.paste(shadow.convert("RGB"), (0, 0), shadow.split()[3])

white = Image.new("RGB", (W, BAR_H), (255, 255, 255))
out.paste(white, (0, 0), glyphs)

out.save("../screenshots-raw/raw-02-ar-forest.png")
print("wrote raw-02-ar-forest.png", out.size)
