"""Draws the original Lapsus app icon (not an AMA asset) and writes PNG and ICO files.

Run: python3 -I scripts/make-icons.py   (requires Pillow)
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAROON_TOP = (138, 36, 36)
MAROON_BOTTOM = (94, 20, 20)
GOLD = (231, 163, 35)
WHITE = (255, 255, 255)
S = 4096  # draw large, downsample for smooth edges


def draw(size: int = 1024) -> Image.Image:
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(grad)
    for y in range(S):
        t = y / (S - 1)
        c = tuple(int(MAROON_TOP[i] + (MAROON_BOTTOM[i] - MAROON_TOP[i]) * t) for i in range(3))
        gd.line([(0, y), (S, y)], fill=c + (255,))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.23), fill=255)
    img.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(img)
    u = S / 1024
    # Gold progress ring, open at the lower left.
    cx, cy, r, w = 640 * u, 392 * u, 196 * u, 64 * u
    d.arc((cx - r, cy - r, cx + r, cy + r), start=-200, end=60, fill=GOLD, width=int(w))
    for ang in (-200, 60):
        import math
        a = math.radians(ang)
        px, py = cx + (r - w / 2) * math.cos(a), cy + (r - w / 2) * math.sin(a)
        d.ellipse((px - w / 2, py - w / 2, px + w / 2, py + w / 2), fill=GOLD)
    # White "L".
    t = 112 * u
    d.rounded_rectangle((268 * u, 236 * u, 268 * u + t, 788 * u), radius=int(t / 2), fill=WHITE)
    d.rounded_rectangle((268 * u, 788 * u - t, 760 * u, 788 * u), radius=int(t / 2), fill=WHITE)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    big = draw(1024)
    out = ROOT / "build"
    out.mkdir(exist_ok=True)
    big.save(out / "icon.png")
    big.resize((256, 256), Image.LANCZOS).save(out / "icon-256.png")
    big.save(out / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    ui = ROOT / "src" / "renderer" / "src" / "assets"
    big.resize((128, 128), Image.LANCZOS).save(ui / "lapsus-mark.png")
    big.resize((64, 64), Image.LANCZOS).save(ROOT / "src" / "renderer" / "public" / "favicon.png")
    print("icons written")


if __name__ == "__main__":
    main()
