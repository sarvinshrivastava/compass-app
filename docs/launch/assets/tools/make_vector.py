"""Emit the adaptive-icon vector drawables from the same geometry the store
icon is drawn from, so the launcher tile and the Play tile cannot drift."""
import math

RES = "/Users/habuild/codes/personal/minimal-compass/app/src/main/res/drawable"
C = 54.0

TICK = "#CCD6F6"
TICK_DIM = "#535C71"
BEZEL = "#394052"
BEZEL_HI = "#6E7A94"
RED = "#E63946"
RED_DARK = "#9E232E"
SILVER = "#E8EEFB"
SILVER_DARK = "#8B96B0"


def pol(r, deg):
    a = math.radians(deg)
    return C + r * math.cos(a), C + r * math.sin(a)


def arc_path(r, start, end):
    x0, y0 = pol(r, start)
    x1, y1 = pol(r, end)
    large = 1 if (end - start) % 360 > 180 else 0
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 {large} 1 {x1:.2f},{y1:.2f}"


def stroke(path, colour, width, cap="butt"):
    return (f'    <path\n        android:pathData="{path}"\n'
            f'        android:strokeColor="{colour}"\n'
            f'        android:strokeWidth="{width}"\n'
            f'        android:strokeLineCap="{cap}"\n'
            f'        android:fillColor="#00000000" />\n')


def fill(path, colour):
    return f'    <path\n        android:pathData="{path}"\n        android:fillColor="{colour}" />\n'


def circle(r):
    return f"M{C},{C - r}a{r},{r} 0 1,1 0,{2 * r}a{r},{r} 0 1,1 0,{-2 * r}z"


HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<!--\n'
        '    Generated from the same geometry as docs/launch/assets/play-icon-512.png.\n'
        '    If the store icon changes, regenerate this rather than hand-editing: a\n'
        '    launcher icon that does not match the Play tile reads as a different app.\n'
        '-->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n')

# ---- background: a lit ground, not a flat slab -----------------------------
bg = HEAD + '''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="31"
                android:centerY="23"
                android:gradientRadius="96"
                android:startColor="#1E2637"
                android:endColor="#080A0F" />
        </aapt:attr>
    </path>
</vector>
'''
open(f"{RES}/ic_launcher_background.xml", "w").write(bg)

# ---- foreground ------------------------------------------------------------
fg = [HEAD]
fg.append('''    <!-- Dial glass -->
    <path android:pathData="%s">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="45"
                android:centerY="43"
                android:gradientRadius="34"
                android:startColor="#232B3D"
                android:endColor="#0E121A" />
        </aapt:attr>
    </path>
''' % circle(28.6))

fg.append("    <!-- Bezel, with the light catch at the upper left -->\n")
fg.append(stroke(circle(30), BEZEL, 2.1))
fg.append(stroke(arc_path(30, 168, 300), BEZEL_HI, 2.1))

fg.append("    <!-- Sixty minute ticks, as CompassRose draws them -->\n")
for i in range(60):
    deg = i * 6 - 90
    cardinal = i % 15 == 0
    inner = 23.4 if cardinal else 25.4
    x0, y0 = pol(inner, deg)
    x1, y1 = pol(27.6, deg)
    fg.append(stroke(f"M{x0:.2f},{y0:.2f}L{x1:.2f},{y1:.2f}",
                     TICK if cardinal else TICK_DIM, 1.6 if cardinal else 0.9))

fg.append("    <!-- Needle: two facets a side, which is what reads as machined -->\n")
fg.append(fill(f"M{C},{C - 25.2}L{C - 2.9},{C}L{C},{C}z", RED))
fg.append(fill(f"M{C},{C - 25.2}L{C + 2.9},{C}L{C},{C}z", RED_DARK))
fg.append(fill(f"M{C},{C + 25.2}L{C - 2.9},{C}L{C},{C}z", SILVER_DARK))
fg.append(fill(f"M{C},{C + 25.2}L{C + 2.9},{C}L{C},{C}z", SILVER))

fg.append("    <!-- Pivot -->\n")
fg.append(fill(circle(2.1), SILVER_DARK))
fg.append(stroke(circle(2.1), "#10141D", 0.5))
fg.append(stroke(arc_path(2.1, 175, 305), SILVER, 0.6))
fg.append("</vector>\n")
open(f"{RES}/ic_launcher_foreground.xml", "w").write("".join(fg))

# ---- monochrome: one flat silhouette, themed icons tint it -----------------
mono = [HEAD.replace('    xmlns:aapt="http://schemas.android.com/aapt"\n', "")]
mono.append(stroke(circle(30), "#FFFFFF", 2.4))
for i in range(0, 60, 5):
    deg = i * 6 - 90
    cardinal = i % 15 == 0
    x0, y0 = pol(23.4 if cardinal else 25.0, deg)
    x1, y1 = pol(27.6, deg)
    mono.append(stroke(f"M{x0:.2f},{y0:.2f}L{x1:.2f},{y1:.2f}", "#FFFFFF", 1.8 if cardinal else 1.1))
mono.append(fill(f"M{C},{C - 25.2}L{C - 3.2},{C}L{C + 3.2},{C}z", "#FFFFFF"))
mono.append(fill(f"M{C},{C + 25.2}L{C - 3.2},{C}L{C + 3.2},{C}z", "#FFFFFF"))
mono.append("</vector>\n")
open(f"{RES}/ic_launcher_monochrome.xml", "w").write("".join(mono))
print("vectors written")
