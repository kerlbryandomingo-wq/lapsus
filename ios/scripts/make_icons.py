#!/usr/bin/env python3
"""Renders Sophia's icon artwork (white S on maroon) into the asset catalog.

The S is the same path as Sophia for Android's ic_sophia_mark.xml, drawn in a 1024 grid:
  M615.9,332 A120,120 0 1,0 512,512 A120,120 0 1,1 408.1,692  (stroke 100, round caps)
Run from the ios/ directory: python3 scripts/make_icons.py
"""
import json, os
from PIL import Image, ImageDraw

MAROON = (0x7A, 0x1B, 0x1B, 255)
WHITE = (255, 255, 255, 255)
SS = 4  # supersampling


def draw_s(d, scale, ox=0, oy=0):
    def p(x, y):
        return (ox + x * scale, oy + y * scale)
    w = 100 * scale
    r = 120 * scale
    for (cx, cy), start, end in (((512, 392), 90, 330), ((512, 632), -90, 150)):
        c = p(cx, cy)
        box = [c[0] - r - w / 2, c[1] - r - w / 2, c[0] + r + w / 2, c[1] + r + w / 2]
        d.arc(box, start, end, fill=WHITE, width=int(round(w)))
    for x, y in ((615.9, 332), (408.1, 692), (512, 512)):
        c = p(x, y)
        d.ellipse([c[0] - w / 2, c[1] - w / 2, c[0] + w / 2, c[1] + w / 2], fill=WHITE)


def app_icon(size):
    big = size * SS
    img = Image.new("RGBA", (big, big), MAROON)
    # Home-screen icons must be opaque squares; iOS applies its own rounded mask.
    # The S is scaled to sit comfortably inside that mask.
    s = big / 1024 * 0.92
    off = (big - 1024 * s) / 2
    draw_s(ImageDraw.Draw(img), s, off, off)
    return img.resize((size, size), Image.LANCZOS).convert("RGB")


def round_mark(size):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, big - 1, big - 1], fill=MAROON)
    draw_s(d, big / 1024)
    return img.resize((size, size), Image.LANCZOS)


root = os.path.join(os.path.dirname(__file__), "..", "Sophia", "Resources", "Assets.xcassets")
icon_dir = os.path.join(root, "AppIcon.appiconset")
os.makedirs(icon_dir, exist_ok=True)
app_icon(1024).save(os.path.join(icon_dir, "AppIcon-1024.png"))
json.dump({"images": [{"filename": "AppIcon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
           "info": {"author": "xcode", "version": 1}}, open(os.path.join(icon_dir, "Contents.json"), "w"), indent=2)

for name, pt in (("LaunchMark", 112), ("SophiaMark", 64)):
    d = os.path.join(root, f"{name}.imageset")
    os.makedirs(d, exist_ok=True)
    images = []
    for scale in (1, 2, 3):
        fn = f"{name}@{scale}x.png"
        round_mark(pt * scale).save(os.path.join(d, fn))
        images.append({"filename": fn, "idiom": "universal", "scale": f"{scale}x"})
    json.dump({"images": images, "info": {"author": "xcode", "version": 1}}, open(os.path.join(d, "Contents.json"), "w"), indent=2)
print("ok")
