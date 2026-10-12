"""Compose App Store screenshots ("Day & Night"): GPT art + real app captures in both themes + localized caption.

Usage (run with a Python that has Pillow, e.g. /usr/bin/python3):
    python3 build_screenshots.py <raw-dir> [<out-dir>] [--only <shot>] [--locale <locale>] [--device iphone|ipad]

<raw-dir>/<device>/<locale>/<SCENE>_<theme>[_<BRAND>].png are simulator captures made with the
debug-only `-StoreShot <SCENE> -StoreShotTheme <light|dark> [-StoreShotBrand <BRAND>]` launch
arguments (see docs/context/store-listing.md). Devices: iphone (6.9", 1320x2868) and ipad (13",
2064x2752). Every screenshot shows the app in both themes: two devices (one light, one dark) or one
device split diagonally. Backgrounds and the 3D objects that break out of the frame live in art/
(generated with Codex / GPT, no text); captions in captions.json. Output:
<out-dir>/<device>/<locale>/NN_<shot>.png.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ART = HERE / "art"
CAPTIONS = json.loads((HERE / "captions.json").read_text())

# Per shot: background, captions index, the two captures (back, front) or a diagonal split
# (light, dark), and the breakout objects: (art, centre x, centre y, width) as canvas fractions + tilt.
SHOTS = [
    dict(name="sports", bg="bg_c1", back="MOVIES_light", front="SPORTS_dark",
         fg=[("fg_posters", 0.15, 0.30, 0.40, 8), ("fg_ball", 0.83, 0.47, 0.36, 0)]),
    dict(name="remote", bg="bg_c2", back="REMOTE_light_LG", front="REMOTE_dark",
         fg=[("fg_remote", 0.85, 0.86, 0.34, -14)]),
    dict(name="guide", bg="bg_c3", split=("GUIDE_light", "GUIDE_dark"),
         fg=[("fg_bell", 0.86, 0.33, 0.30, 10)]),
    dict(name="library", bg="bg_c4", back="IPTV_light", front="CHANNELS_dark",
         fg=[("fg_popcorn", 0.85, 0.87, 0.32, 8)]),
    dict(name="home", bg="bg_c5", back="HOME_dark", front="HOME_light",
         fg=[("fg_play", 0.15, 0.85, 0.30, -10)]),
    dict(name="player", bg="bg_c6", split=("PLAYER_light", "PLAYER_dark"),
         fg=[("fg_posters", 0.84, 0.88, 0.36, -8)]),
    dict(name="mirror", bg="bg_c7", back="MIRROR_dark", front="MIRROR_light",
         fg=[("fg_play", 0.86, 0.86, 0.26, 10)]),
]
DEVICES = {
    # canvas, device width share (pair / split), screen radius as a share of the device width,
    # bezel share, headline size, where the device area starts
    "iphone": dict(size=(1320, 2868), pair=0.57, island=True, single=0.74, radius=0.125, bezel=0.03, title=124, top=640),
    "ipad": dict(size=(2064, 2752), wide=True, pair=0.54, island=False, single=0.70, radius=0.045, bezel=0.022, title=132, top=600),
}
FONTS = "/System/Library/Fonts/"


def font(locale: str, size: int, heavy: bool) -> ImageFont.FreeTypeFont:
    if locale == "ja":
        return ImageFont.truetype(FONTS + ("ヒラギノ角ゴシック W8.ttc" if heavy else "ヒラギノ角ゴシック W5.ttc"), size)
    if locale == "ko":
        return ImageFont.truetype(FONTS + "AppleSDGothicNeo.ttc", size, index=6 if heavy else 2)
    if locale.startswith("zh"):
        return ImageFont.truetype(FONTS + "Hiragino Sans GB.ttc", size, index=2 if heavy else 0)
    f = ImageFont.truetype(FONTS + "SFNSRounded.ttf", size)
    f.set_variation_by_name("Heavy" if heavy else "Semibold")
    return f


def cover(image: Image.Image, size) -> Image.Image:
    ratio = max(size[0] / image.width, size[1] / image.height)
    image = image.resize((round(image.width * ratio), round(image.height * ratio)), Image.Resampling.LANCZOS)
    left, top = (image.width - size[0]) // 2, (image.height - size[1]) // 2
    return image.crop((left, top, left + size[0], top + size[1]))


def rounded(size, radius) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def gradient(size, stops) -> Image.Image:
    """Horizontal gradient through [stops] (RGB tuples)."""
    w, h = size
    row = Image.new("RGB", (w, 1))
    for x in range(w):
        t = x / max(1, w - 1) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        row.putpixel((x, 0), tuple(round(stops[i][c] + (stops[i + 1][c] - stops[i][c]) * f) for c in range(3)))
    return row.resize((w, h))


def fit(locale, lines, size, max_width, heavy=True):
    while size > 40:
        f = font(locale, size, heavy)
        if all(f.getlength(line) <= max_width for line in lines):
            return f
        size -= 4
    return font(locale, size, heavy)


def split_screen(light: Image.Image, dark: Image.Image) -> Image.Image:
    """Light theme above a diagonal, dark below it, with a glowing seam."""
    w, h = light.size
    dark = dark.resize((w, h))
    a, b = (0, round(h * 0.60)), (w, round(h * 0.40))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon([a, b, (w, h), (0, h)], fill=255)
    out = Image.composite(dark, light, mask)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).line([a, b], fill=(255, 80, 170, 255), width=round(w * 0.03))
    out = Image.alpha_composite(out.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(w * 0.02)))
    ImageDraw.Draw(out).line([a, b], fill=(255, 235, 245, 255), width=max(3, round(w * 0.004)))
    return out.convert("RGB")


def device(screen: Image.Image, width: int, spec, light: bool) -> Image.Image:
    """The capture in a device body: titanium frame (silver for the light theme, graphite for dark)."""
    sw = width
    sh = round(screen.height * sw / screen.width)
    screen = screen.resize((sw, sh), Image.Resampling.LANCZOS)
    bezel = round(sw * spec["bezel"])
    radius = round(sw * spec["radius"])
    W, H = sw + 2 * bezel, sh + 2 * bezel
    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    rim, inner = ((214, 210, 228), (246, 244, 250)) if light else ((70, 64, 96), (16, 14, 26))
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=radius + bezel, fill=rim + (255,))
    d.rounded_rectangle((4, 4, W - 5, H - 5), radius=radius + bezel - 4, fill=(10, 10, 16, 255))
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=radius + bezel, outline=inner + (255,), width=3)
    body.paste(screen, (bezel, bezel), rounded((sw, sh), radius))
    if spec["island"]:
        # Simulator captures only sometimes include the Dynamic Island; draw it on every phone.
        iw, ih, it = sw * 0.215, sw * 0.073, sw * 0.032
        d.rounded_rectangle((bezel + (sw - iw) / 2, bezel + it, bezel + (sw + iw) / 2, bezel + it + ih), radius=ih / 2, fill=(0, 0, 0, 255))
    return body


def place(canvas: Image.Image, item: Image.Image, cx: float, cy: float, angle: float, shadow=True, glow=None) -> Image.Image:
    """Paste [item] rotated by [angle] degrees centred on (cx, cy), with a soft drop shadow / colour glow."""
    item = item.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    x, y = round(cx - item.width / 2), round(cy - item.height / 2)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = item.getchannel("A")
    if glow:
        g = Image.new("RGBA", item.size, glow + (0,))
        g.putalpha(alpha.point(lambda v: v * 0.75))
        layer.paste(g, (x, y), g)
        canvas = Image.alpha_composite(canvas, layer.filter(ImageFilter.GaussianBlur(item.width * 0.06)))
        layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    if shadow:
        s = Image.new("RGBA", item.size, (6, 2, 20, 0))
        s.putalpha(alpha.point(lambda v: v * 0.55))
        layer.paste(s, (x + round(item.width * 0.03), y + round(item.width * 0.06)), s)
        canvas = Image.alpha_composite(canvas, layer.filter(ImageFilter.GaussianBlur(item.width * 0.035)))
        layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(item, (x, y), item)
    return Image.alpha_composite(canvas, layer)


def compose(device_name: str, locale: str, index: int, shot: dict, raw: Path, out: Path) -> bool:
    spec = DEVICES[device_name]
    W, H = spec["size"]
    folder = raw / device_name / locale
    names = shot["split"] if "split" in shot else (shot["back"], shot["front"])
    if not all((folder / f"{n}.png").exists() for n in names):
        return False
    art = ART / f"{shot['bg']}{'_ipad' if spec.get('wide') else ''}.png"
    canvas = cover(Image.open(art).convert("RGB"), (W, H)).convert("RGBA")
    # Deepen the sky behind the headline.
    shade = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shade).rectangle((0, 0, W, spec["top"] - 60), fill=120)
    canvas = Image.composite(Image.new("RGBA", (W, H), (20, 6, 44, 255)), canvas, shade.filter(ImageFilter.GaussianBlur(140)))
    top = spec["top"]
    area = H - top

    if "split" in shot:
        light, dark = (Image.open(folder / f"{n}.png").convert("RGB") for n in names)
        body = device(split_screen(light, dark), round(W * spec["single"]), spec, light=True)
        cy = top + area * 0.52
        canvas = place(canvas, body, W / 2, cy, 0, glow=(255, 70, 170))
    else:
        back, front = (Image.open(folder / f"{n}.png").convert("RGB") for n in names)
        dw = round(W * spec["pair"])
        b = device(back, dw, spec, light=names[0].endswith("light") or "_light_" in names[0])
        f = device(front, dw, spec, light=names[1].endswith("light") or "_light_" in names[1])
        canvas = place(canvas, b, W * 0.37, top + b.height * 0.5 + area * 0.01, 7, glow=(160, 100, 255))
        canvas = place(canvas, f, W * 0.63, H - f.height * 0.5 - area * 0.035, -5, glow=(255, 70, 170))

    for name, fx, fy, fw, angle in shot["fg"]:
        item = Image.open(ART / f"{name}.png").convert("RGBA")
        item = item.crop(item.getbbox())
        size = round(W * fw * (0.8 if spec.get("wide") else 1))
        item = item.resize((size, round(item.height * size / item.width)), Image.Resampling.LANCZOS)
        canvas = place(canvas, item, W * fx, H * fy, angle)

    # The caption goes on last so nothing covers it.
    canvas = caption(canvas, locale, index, spec)
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out, optimize=True)
    return True


def caption(canvas: Image.Image, locale: str, index: int, spec) -> Image.Image:
    """Headline (last line in the brand gradient, dark halo for contrast) and the subtitle pill."""
    title, subtitle = CAPTIONS[locale][index]
    W, H = canvas.size
    lines = title.split("\n")
    margin = round(W * 0.07)
    tf = fit(locale, lines, spec["title"], W - 2 * margin)
    y = round(H * 0.04) + 40
    line_h = round(tf.size * 1.12)
    text = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    for i, line in enumerate(lines):
        x = (W - tf.getlength(line)) / 2
        if i == len(lines) - 1:
            mask = Image.new("L", (W, line_h + 40), 0)
            ImageDraw.Draw(mask).text((x, 0), line, font=tf, fill=255)
            fill = gradient((W, line_h + 40), [(255, 214, 120), (255, 120, 190), (200, 160, 255)]).convert("RGBA")
            text.paste(fill, (0, y), mask)
        else:
            ImageDraw.Draw(text).text((x, y), line, font=tf, fill=(255, 255, 255, 255))
        y += line_h
    halo = Image.new("RGBA", canvas.size, (24, 6, 48, 0))
    halo.putalpha(text.getchannel("A").point(lambda v: min(255, int(v * 0.8))))
    canvas = Image.alpha_composite(canvas, halo.filter(ImageFilter.GaussianBlur(18)))
    canvas = Image.alpha_composite(canvas, text)
    sf = fit(locale, [subtitle], round(spec["title"] * 0.40), W - 2 * margin - 80, heavy=False)
    sw = sf.getlength(subtitle)
    pad_x, pill_h = 40, round(sf.size * 1.9)
    py = y + 26
    pill = (round((W - sw) / 2 - pad_x), py, round((W + sw) / 2 + pad_x), py + pill_h)
    overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(overlay).rounded_rectangle(pill, radius=pill_h // 2, fill=(30, 10, 60, 130), outline=(255, 255, 255, 110), width=2)
    canvas = Image.alpha_composite(canvas, overlay)
    ImageDraw.Draw(canvas).text((W / 2, py + pill_h / 2), subtitle, font=sf, fill=(255, 240, 248), anchor="mm")
    return canvas


def main():
    args = sys.argv[1:]
    opts = {k: args[i + 1] for i, k in enumerate(args) if k.startswith("--")}
    pos = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or not args[i - 1].startswith("--"))]
    raw = Path(pos[0])
    out = Path(pos[1]) if len(pos) > 1 else HERE / "screenshots"
    count = 0
    for device_name in DEVICES:
        if opts.get("--device", device_name) != device_name:
            continue
        for locale in CAPTIONS:
            if locale.startswith("_") or opts.get("--locale", locale) != locale:
                continue
            for index, shot in enumerate(SHOTS):
                if opts.get("--only", shot["name"]) != shot["name"]:
                    continue
                target = out / device_name / locale / f"{index + 1:02d}_{shot['name']}.png"
                count += compose(device_name, locale, index, shot, raw, target)
    print(count, "written to", out)


if __name__ == "__main__":
    main()
