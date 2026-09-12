"""Feature graphic: solid night ground, same palette and type as the screenshots.

A photo behind the wordmark was tried and dropped — Play crops this asset
unpredictably across surfaces, and a half-photo ground stopped matching every
other asset in the set.
"""
from PIL import Image, ImageDraw
from brand import (draw_rose, vertical_gradient, radial_glow, font, tracked,
                   FUTURA, HELV, FUT_MED, HN_LIGHT, RED)

OUT = "/Users/habuild/codes/personal/minimal-compass/docs/launch/assets/feature-graphic-1024x500.png"
W, H, SS = 1024, 500, 3
MARGIN = 60 * SS   # nothing lives outside this; Play crops the edges

canvas = vertical_gradient((W * SS, H * SS), (0x16, 0x1C, 0x28), (0x08, 0x0A, 0x0E))
canvas.alpha_composite(radial_glow((W * SS, H * SS), (W * SS * 0.19, H * SS * 0.40),
                                   W * SS * 0.42, (0x2C, 0x39, 0x55), 190))

draw_rose(canvas, MARGIN + 112 * SS, H * SS / 2, 196 * SS)

d = ImageDraw.Draw(canvas)
d.line([(MARGIN + 250 * SS, 148 * SS), (MARGIN + 250 * SS, 352 * SS)],
       fill=(0x2E, 0x35, 0x45, 255), width=max(1, round(1.5 * SS)))

x0 = MARGIN + 302 * SS
f_word = font(FUTURA, FUT_MED, 58 * SS)
f_tag = font(HELV, HN_LIGHT, 27 * SS)
f_kick = font(FUTURA, FUT_MED, 19 * SS)

tracked(d, (x0, 150 * SS), "MINIMAL", f_word, (0xF2, 0xF5, 0xFC, 255), track=5.5 * SS)
tracked(d, (x0, 218 * SS), "COMPASS", f_word, (0xF2, 0xF5, 0xFC, 255), track=5.5 * SS)
d.text((x0, 300 * SS), "True north, nothing else.", font=f_tag, fill=(0x96, 0xA0, 0xB6, 255), anchor="la")

ky = 352 * SS
d.ellipse([x0, ky + 7 * SS, x0 + 9 * SS, ky + 16 * SS], fill=RED + (255,))
tracked(d, (x0 + 24 * SS, ky), "NO ADS   ·   NO SIGN-IN", f_kick, (0x6E, 0x78, 0x8C, 255), track=3.4 * SS)

canvas.convert("RGB").resize((W, H), Image.LANCZOS).save(OUT, quality=100)
print(OUT)
