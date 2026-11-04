"""Regenerate checked-in ATW artwork (developer tools: Pillow and ImageMagick)."""
from pathlib import Path
import io
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "res"
OUT = RES / "branding"


def main():
    OUT.mkdir(exist_ok=True)
    label = Image.open(io.BytesIO(subprocess.check_output([
        "magick", "-background", "none", "-density", "600",
        str(RES / "ui/window/label-atw.svg"), "png:-"]))).convert("RGBA")
    icon = Image.new("RGBA", (1024, 1024))
    draw = ImageDraw.Draw(icon)
    draw.rounded_rectangle((64, 64, 960, 960), 190, fill="#202021")
    draw.rounded_rectangle((64, 48, 960, 916), 190, fill="#2c2c2c")
    draw.rounded_rectangle((68, 48, 956, 892), 185, fill="#343435", outline="#565658", width=5)
    draw.rounded_rectangle((118, 99, 906, 831), 138, fill="#252526")
    word = label.copy(); word.thumbnail((682, 380), Image.Resampling.LANCZOS)
    icon.alpha_composite(word, ((1024-word.width)//2, 435-word.height//2))
    draw.rounded_rectangle((338, 704, 686, 735), 12, fill="#4779d8")
    icon.save(OUT / "atw.png")
    icon.save(OUT / "atw.icns", sizes=[(s, s) for s in (16,32,64,128,256,512,1024)])
    for name in ("icon.ico", "icon-config.ico", "icon-launch.ico", "minecraft.ico"):
        icon.save(RES / name, sizes=[(s,s) for s in (16,24,32,48,64,128,256)])
    # The Figma paths remain the authoritative display lettering.
    path = (RES / "ui/window/label-atw.svg").read_text().split('<path', 1)[1].split('/>', 1)[0]
    (OUT / "atw.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">'
        '<rect x="64" y="64" width="896" height="896" rx="190" fill="#202021"/>'
        '<rect x="64" y="48" width="896" height="850" rx="190" fill="#343435"/>'
        '<rect x="118" y="99" width="788" height="732" rx="138" fill="#252526"/>'
        '<g transform="translate(169 307) scale(3.33) translate(-30.4584 -81.5825)"><path'+path+'/></g>'
        '<rect x="338" y="704" width="348" height="31" rx="12" fill="#4779d8"/></svg>\n')
    # Wordmarks fit the upstream texture geometry, avoiding layout changes.
    font = ImageFont.truetype(str(RES / "fonts/Minecraft.ttf"), 100)
    client = Image.new("RGBA", (600, 140)); cd = ImageDraw.Draw(client)
    cd.text((0, 0), "CLIENT", font=font, fill="white", stroke_width=0)
    client = client.crop(client.getbbox())
    wordmark = Image.new("RGBA", (1500, 280))
    atw = label.resize((630, 235), Image.Resampling.LANCZOS)
    client.thumbnail((700, 185), Image.Resampling.LANCZOS)
    wordmark.alpha_composite(atw, (0,22)); wordmark.alpha_composite(client, (690,(280-client.height)//2))
    wordmark = wordmark.crop(wordmark.getbbox())
    for filename, size in {
        "logo-branding-215x33.png": (215,33), "logo-branding-light-215x33.png": (215,33),
        "logo-branding-thick-300x60.png": (300,60), "logo-branding-thick-440x88.png": (440,88),
        "logo-branding-thick-logo-390x60.png": (390,60),
    }.items():
        canvas = Image.new("RGBA", size); fitted=wordmark.copy()
        fitted.thumbnail((size[0]-4,size[1]-4),Image.Resampling.LANCZOS)
        canvas.alpha_composite(fitted, ((size[0]-fitted.width)//2,(size[1]-fitted.height)//2))
        canvas.save(RES / "lunar-overrides/assets/lunar/logo" / filename)
    root = RES / "lunar-overrides/assets/lunar"
    logos = {f"logo/logo-{s}x{s}.png": (s,s) for s in (16,24,32,64,100)}
    logos["logo/logo-128x117.png"] = (128,117)
    logos["icons/assets/logo-22x22.png"] = (22,22)
    logos["icons/auth/icon.png"] = (32,32)
    for size,height in ((128,117),(64,59)):
        logos[f"animatedlogo/{size}/logo_{size}_no_stars.png"] = (size,height)
        for star in range(1,9): logos[f"animatedlogo/{size}/logo_{size}_star_{star}.png"] = (size,height)
    for relative, size in logos.items():
        destination=root/relative; destination.parent.mkdir(parents=True,exist_ok=True)
        canvas=Image.new("RGBA",size)
        if "_star_" not in relative:
            fitted=icon.copy(); fitted.thumbnail(size,Image.Resampling.LANCZOS)
            canvas.alpha_composite(fitted,((size[0]-fitted.width)//2,(size[1]-fitted.height)//2))
        canvas.save(destination)
    (root/"logo/logo-200x182.svg").write_bytes((OUT/"atw.svg").read_bytes())
    print("Generated ATW app icon, native icon formats and five wordmarks")


if __name__ == "__main__": main()
