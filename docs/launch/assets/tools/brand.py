"""Shared drawing kit for the Minimal Compass store assets.

The mark is the app's own rose, redrawn with the weight hierarchy a real
instrument has: a machined bezel that catches light, sixty minute ticks (the
same ones CompassRose draws), and a faceted needle. Geometry stays on the
108-unit viewport of ic_launcher_foreground.xml so the store icon and the
launcher icon cannot drift apart.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import math

# Palette — lifted from ui/theme/Theme.kt, not invented for the marketing.
NIGHT = (0x0D, 0x0F, 0x14)
NIGHT_HI = (0x18, 0x1E, 0x2C)
FACE_HI = (0x23, 0x2B, 0x3D)
FACE_LO = (0x0E, 0x12, 0x1A)
BEZEL = (0x39, 0x40, 0x52)
BEZEL_HI = (0x6E, 0x7A, 0x94)
TICK = (0xCC, 0xD6, 0xF6)
TICK_DIM = (0x53, 0x5C, 0x71)
RED = (0xE6, 0x39, 0x46)
RED_DARK = (0x9E, 0x23, 0x2E)
SILVER = (0xE8, 0xEE, 0xFB)
SILVER_DARK = (0x8B, 0x96, 0xB0)
TEXT = (0xF2, 0xF5, 0xFC)
TEXT_DIM = (0x96, 0xA0, 0xB6)

FUTURA = "/System/Library/Fonts/Supplemental/Futura.ttc"
HELV = "/System/Library/Fonts/HelveticaNeue.ttc"
FUT_MED, FUT_BOLD = 0, 2
HN_LIGHT, HN_MED, HN_REG = 7, 10, 0


def font(path, idx, px):
    return ImageFont.truetype(path, round(px), index=idx)


def tracked(d, xy, text, f, fill, track=0, anchor="la"):
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=f, fill=fill, anchor=anchor)
        x += d.textlength(ch, font=f) + track
    return x - xy[0]


def text_width(d, text, f, track=0):
    if not text:
        return 0
    return sum(d.textlength(c, font=f) + track for c in text) - track


def draw_rose(img, cx, cy, diameter, ground=True):
    """Draw the mark centred on (cx, cy). `diameter` spans the outer bezel."""
    u = diameter / 60.0                       # 60 viewport units across the ring
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def box(r):
        return [cx - r * u, cy - r * u, cx + r * u, cy + r * u]

    if ground:
        # Dial face: a radial lift so the glass is not a flat hole.
        steps = 44
        for i in range(steps, 0, -1):
            t = i / steps
            r = 28.6 * t
            col = tuple(round(FACE_LO[c] + (FACE_HI[c] - FACE_LO[c]) * (1 - t) ** 1.4) for c in range(3))
            d.ellipse(box(r), fill=col + (255,))

    # Bezel, with a light catch at the upper left.
    d.ellipse(box(30), outline=BEZEL + (255,), width=max(2, round(2.1 * u)))
    d.arc(box(30), 168, 300, fill=BEZEL_HI + (255,), width=max(2, round(2.1 * u)))

    # Sixty minute ticks, exactly as CompassRose draws them.
    for i in range(60):
        a = math.radians(i * 6 - 90)
        inner = 25.4 if i % 15 else 23.4
        outer = 27.6
        col = TICK_DIM if i % 5 else (TICK if i % 15 == 0 else TICK_DIM)
        w = max(1, round((1.6 if i % 15 == 0 else 0.9) * u))
        d.line([(cx + inner * u * math.cos(a), cy + inner * u * math.sin(a)),
                (cx + outer * u * math.cos(a), cy + outer * u * math.sin(a))],
               fill=col + (255,), width=w)

    # Needle. Two facets per half — the single detail that reads as machined.
    def needle(tip, base, left, right, near, far):
        d.polygon([(cx, cy + tip * u), (cx - 2.9 * u, cy + base * u), (cx, cy + base * u)], fill=left + (255,))
        d.polygon([(cx, cy + tip * u), (cx + 2.9 * u, cy + base * u), (cx, cy + base * u)], fill=right + (255,))

    needle(-25.2, 0, RED, RED_DARK, None, None)      # north
    needle(24.5, 0, SILVER_DARK, SILVER, None, None)  # south

    # Cap: a small machined pivot, not a bead. A bright ring plus a highlight
    # arc at the light source; a filled dot here reads as an eye.
    d.ellipse(box(2.1), fill=SILVER_DARK + (255,))
    d.ellipse(box(2.1), outline=(0x10, 0x14, 0x1D, 255), width=max(1, round(0.5 * u)))
    d.arc(box(2.1), 175, 305, fill=SILVER + (255,), width=max(1, round(0.6 * u)))

    img.alpha_composite(layer)
    return img


def vertical_gradient(size, top, bottom):
    w, h = size
    g = Image.new("RGB", (1, h))
    px = g.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[c] + (bottom[c] - top[c]) * t) for c in range(3))
    return g.resize(size, Image.BILINEAR).convert("RGBA")


def radial_glow(size, centre, radius, colour, alpha):
    g = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse([centre[0] - radius, centre[1] - radius, centre[0] + radius, centre[1] + radius],
              fill=colour + (alpha,))
    return g.filter(ImageFilter.GaussianBlur(radius / 2.2))


def cover(img, w, h):
    s = max(w / img.width, h / img.height)
    r = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    x, y = (r.width - w) // 2, (r.height - h) // 2
    return r.crop((x, y, x + w, y + h))
