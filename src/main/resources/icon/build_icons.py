import cairosvg, os, shutil
from PIL import Image

SVG = "icon.svg"
os.makedirs("png", exist_ok=True)
os.makedirs("AppIcon.iconset", exist_ok=True)

# Render a high-res master once, then downscale with Lanczos for crisp small sizes
cairosvg.svg2png(url=SVG, write_to="png/icon_1024.png", output_width=1024, output_height=1024)
master = Image.open("png/icon_1024.png").convert("RGBA")

def render(size):
    # render directly from SVG for best quality at each size
    out = f"png/icon_{size}.png"
    cairosvg.svg2png(url=SVG, write_to=out, output_width=size, output_height=size)
    return Image.open(out).convert("RGBA")

# Standalone key-size PNGs
for s in [16, 32, 64, 128, 256, 512, 1024]:
    render(s)

# macOS iconset
iconset = {
    "icon_16x16.png": 16,
    "icon_16x16@2x.png": 32,
    "icon_32x32.png": 32,
    "icon_32x32@2x.png": 64,
    "icon_128x128.png": 128,
    "icon_128x128@2x.png": 256,
    "icon_256x256.png": 256,
    "icon_256x256@2x.png": 512,
    "icon_512x512.png": 512,
    "icon_512x512@2x.png": 1024,
}
for name, size in iconset.items():
    src = f"png/icon_{size}.png"
    if not os.path.exists(src):
        render(size)
    shutil.copy(src, f"AppIcon.iconset/{name}")

# Build .icns via Pillow (works cross-platform, no macOS iconutil needed)
master.save("AppIcon.icns", format="ICNS")
print("done")
