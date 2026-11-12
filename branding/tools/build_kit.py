"""Rebuild the ATW brand kit in branding/ from the checked-in source artwork.

Sources (never edited by this script):
  res/branding/atw.svg          app icon (dark raised tile, ATW lettering, blue bar)
  res/ui/window/label-atw.svg   outlined Figma "ATW" lettering
  res/fonts/Minecraft.ttf       pixel face used for "CLIENT" and UI text

Needs Python 3 with Pillow and fontTools, plus ImageMagick 7 (`magick`) on PATH.
Run from anywhere:  python branding/tools/build_kit.py
"""
from pathlib import Path
import io
import json
import subprocess

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "res"
KIT = ROOT / "branding"

PALETTE = json.loads((KIT / "colors" / "palette.json").read_text())["colors"]
C = {c["token"]: c["hex"] for c in PALETTE}

# Outlined "ATW" lettering in its own coordinate space (x 30.4584, y 81.5825, 205.2 x 76.4).
ATW_PATH = (RES / "ui/window/label-atw.svg").read_text().split(' d="', 1)[1].split('"', 1)[0]
ATW_X, ATW_Y, ATW_W, ATW_H = 30.4584, 81.5825, 205.2016, 76.4175


def rasterize(svg: str, width: int) -> Image.Image:
    """Render SVG text to an RGBA image `width` pixels wide."""
    view = svg.split('viewBox="', 1)[1].split('"', 1)[0].split()
    density = 96 * width / float(view[2])
    png = subprocess.check_output(
        ["magick", "-background", "none", "-density", f"{density:.3f}", "svg:-", "png:-"],
        input=svg.encode())
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    if image.width != width:
        image = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
    return image


def text_path(text: str, cap_height: float, x: float, baseline: float,
              tracking: float = 0.0) -> tuple[str, float]:
    """Outline `text` from Minecraft.ttf; `tracking` is extra space per letter in em.
    Returns (path data, drawn width)."""
    font = TTFont(RES / "fonts/Minecraft.ttf")
    glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
    bounds = BoundsPen(glyphs)
    glyphs[cmap[ord("H")]].draw(bounds)  # cap height comes from "H"
    scale = cap_height / bounds.bounds[3]
    pen = SVGPathPen(glyphs)
    cursor = 0.0
    extra = tracking * font["head"].unitsPerEm
    for ch in text:
        glyph = glyphs[cmap[ord(ch)]]
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, x + cursor * scale, baseline)))
        cursor += glyph.width + extra
    # Drop the trailing side bearing so the wordmark's right edge is the last stroke.
    last = glyphs[cmap[ord(text[-1])]]
    lb = BoundsPen(glyphs)
    last.draw(lb)
    advance = (cursor - extra - last.width + lb.bounds[2]) * scale
    return pen.getCommands(), advance


def atw_group(x: float, y: float, height: float, fill: str) -> str:
    s = height / ATW_H
    return (f'<g transform="translate({x:.3f} {y:.3f}) scale({s:.5f}) '
            f'translate({-ATW_X} {-ATW_Y})"><path d="{ATW_PATH}" fill="{fill}"/></g>')


def mark_svg(fill: str) -> str:
    """Flat lettermark: ATW over the accent bar, no tile. 1 unit = ATW cap height / 76.4."""
    w, h = ATW_W, ATW_H
    bar_w, bar_h, gap = 104.5, 9.3, 20
    pad = 6
    total_h = h + gap + bar_h
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w + 2*pad:.2f} {total_h + 2*pad:.2f}">'
            + atw_group(pad, pad, h, fill)
            + f'<rect x="{pad + (w - bar_w)/2:.2f}" y="{pad + h + gap:.2f}" width="{bar_w}" '
              f'height="{bar_h}" rx="3.6" fill="{C["accent"]}"/></svg>\n')


def wordmark_svg(fill: str, secondary: str) -> str:
    """ATW CLIENT on one line: heavy ATW, then a smaller, letter-spaced CLIENT
    sitting on the same baseline in the secondary colour."""
    h = ATW_H
    pad = 6
    gap = h * 0.26
    client_cap = h * 0.36
    baseline = pad + h
    path, advance = text_path("CLIENT", client_cap, pad + ATW_W + gap, baseline, tracking=0.12)
    width = ATW_W + gap + advance + 2 * pad
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:.2f} {h + 2*pad:.2f}">'
            + atw_group(pad, pad, h, fill)
            + f'<path d="{path}" fill="{secondary}"/></svg>\n')


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, Image.Image):
        data.save(path, optimize=True)
    else:
        path.write_text(data, encoding="utf-8", newline="\n")
    print("wrote", path.relative_to(ROOT))


def pixel_text(text: str, size: int, fill: str) -> Image.Image:
    font = ImageFont.truetype(str(RES / "fonts/Minecraft.ttf"), size)
    box = font.getbbox(text)
    image = Image.new("RGBA", (box[2] - box[0] + 4, box[3] - box[1] + 4))
    ImageDraw.Draw(image).text((2 - box[0], 2 - box[1]), text, font=font, fill=fill)
    return image


def banner(size: tuple[int, int], tagline: str, background: Path | None) -> Image.Image:
    w, h = size
    canvas = Image.new("RGBA", size, C["frame"])
    if background and background.exists():
        bg = Image.open(background).convert("RGBA")
        scale = max(w / bg.width, h / bg.height)
        bg = bg.resize((round(bg.width * scale), round(bg.height * scale)), Image.Resampling.LANCZOS)
        bg = bg.crop(((bg.width - w) // 2, (bg.height - h) // 2, (bg.width - w) // 2 + w, (bg.height - h) // 2 + h))
        bg = bg.filter(ImageFilter.GaussianBlur(h / 60))
        shade = Image.new("RGBA", size, C["frame"] + "C7")  # 78% frame colour over the scene
        canvas = Image.alpha_composite(bg, shade)
    icon = rasterize((RES / "branding/atw.svg").read_text(), round(h * 0.52))
    word = rasterize(wordmark_svg(C["white"], C["value"]), round(w * 0.40))
    line = pixel_text(tagline, round(h * 0.052), C["value"])
    block_w = icon.width + round(h * 0.07) + max(word.width, line.width)
    x0 = (w - block_w) // 2
    canvas.alpha_composite(icon, (x0, (h - icon.height) // 2))
    tx = x0 + icon.width + round(h * 0.07)
    text_h = word.height + round(h * 0.05) + line.height
    ty = (h - text_h) // 2
    canvas.alpha_composite(word, (tx, ty))
    canvas.alpha_composite(line, (tx + 2, ty + word.height + round(h * 0.05)))
    return canvas.convert("RGB")


def palette_sheet() -> Image.Image:
    font = ImageFont.truetype(str(RES / "fonts/Minecraft.ttf"), 22)
    small = ImageFont.truetype(str(RES / "fonts/Minecraft.ttf"), 16)
    cols, sw, sh, pad = 4, 260, 170, 24
    rows = (len(PALETTE) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * sw + (cols + 1) * pad, rows * (sh + 70) + (rows + 1) * pad), C["body"])
    d = ImageDraw.Draw(sheet)
    for i, color in enumerate(PALETTE):
        x = pad + (i % cols) * (sw + pad)
        y = pad + (i // cols) * (sh + 70 + pad)
        d.rounded_rectangle((x, y, x + sw, y + sh), 10, fill=color["hex"], outline="#1B1B1C", width=2)
        d.text((x, y + sh + 10), color["token"], font=font, fill=C["value"])
        d.text((x, y + sh + 40), color["hex"].upper(), font=small, fill=C["label"])
    return sheet


def main():
    icon_svg = (RES / "branding/atw.svg").read_text()
    write(KIT / "logo/atw-icon.svg", icon_svg)
    for size in (16, 32, 64, 128, 256, 512, 1024):
        write(KIT / f"logo/png/atw-icon-{size}.png", rasterize(icon_svg, size))

    # "light" artwork is for dark backgrounds; "dark" artwork is for light ones.
    for variant, fill, secondary in (("light", C["white"], C["value"]),
                                     ("dark", C["frame"], C["tab-inactive"])):
        mark, word = mark_svg(fill), wordmark_svg(fill, secondary)
        write(KIT / f"logo/atw-mark-{variant}.svg", mark)
        write(KIT / f"logo/atw-wordmark-{variant}.svg", word)
        write(KIT / f"logo/png/atw-mark-{variant}-512.png", rasterize(mark, 512))
        write(KIT / f"logo/png/atw-wordmark-{variant}-1200.png", rasterize(word, 1200))

    scene = KIT / "screenshots/main-menu.jpg"
    tagline = "Lunar Client 1.8.9, tuned for Hypixel Bed Wars"
    write(KIT / "banners/readme-banner.png", banner((1600, 520), tagline, scene))
    write(KIT / "banners/social-preview.png", banner((1280, 640), tagline, scene))
    write(KIT / "colors/palette.png", palette_sheet())


if __name__ == "__main__":
    main()
