from PIL import Image, ImageDraw, ImageFilter
from brand import draw_rose, radial_glow, NIGHT, NIGHT_HI
import os

OUT = "/Users/habuild/codes/personal/minimal-compass/docs/launch/assets"
S, SS = 512, 4
N = S * SS


def ground():
    """Deep ground with a light source at the upper left.

    Play masks corners at 30% radius and adds its own shadow, so the artwork
    fills the square and carries its lighting internally.
    """
    img = Image.new("RGBA", (N, N), NIGHT + (255,))
    img.alpha_composite(radial_glow((N, N), (N * 0.28, N * 0.20), N * 0.62, NIGHT_HI, 210))
    # Falloff into the lower right keeps the square from reading flat.
    v = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(v).ellipse([-N * 0.35, -N * 0.35, N * 1.05, N * 1.05], fill=(0, 0, 0, 120))
    img.alpha_composite(v.filter(ImageFilter.GaussianBlur(N * 0.18)))
    return img


# Filled — the recommended upload. Mark at the adaptive icon's 60/72 proportion
# so the Play tile and the launcher icon read as the same object.
filled = ground()
draw_rose(filled, N / 2, N / 2, N * (60 / 72))
filled.convert("RGB").resize((S, S), Image.LANCZOS).save(f"{OUT}/play-icon-512.png")

# Transparent — same mark, ground dropped outside the bezel.
clear = Image.new("RGBA", (N, N), (0, 0, 0, 0))
draw_rose(clear, N / 2, N / 2, N * (60 / 72))
clear.resize((S, S), Image.LANCZOS).save(f"{OUT}/play-icon-512-transparent.png")

print("icons written")
