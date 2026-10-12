"""Compose Google Play screenshots with the App Store "Day & Night" layout (../app-store/build_screenshots.py).

Usage (Python with Pillow, e.g. /usr/bin/python3):
    python3 build_screenshots.py <raw-dir> [<out-dir>] [--only <shot>] [--locale <caption locale>] [--device phone|tablet]

<raw-dir>/<device>/<caption locale>/<SCENE>_<theme>[_<BRAND>].png are emulator captures of the debug
build opened with `am start ... --es StoreShot <SCENE> --es StoreShotTheme <light|dark>` (see
docs/context/store-listing.md). Play rejects screenshots longer than 2:1, so the phone canvas is
9:16 (1440x2560) and the tablet canvas 3:4 (1920x2560, used for both 7" and 10" tablets). Frames
are Android-style (punch-hole camera, no Dynamic Island) and the player / cast captions name
Chromecast and DLNA instead of AirPlay. Output: <out-dir>/<device>/<play locale>/NN_<shot>.png.
"""
import json
import sys
from pathlib import Path

from PIL import ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "app-store"))
import build_screenshots as base  # noqa: E402

# Caption locale (shared with the App Store captions) -> Play Console locale.
PLAY_LOCALES = {"en-US": "en-US", "vi": "vi", "es-MX": "es-419", "pt-BR": "pt-BR", "fr-FR": "fr-FR",
                "de-DE": "de-DE", "ja": "ja-JP", "ko": "ko-KR", "zh-Hans": "zh-CN"}

# Android wording for shot 6 (player) and 7 (Cast tab: DLNA / Chromecast media casting).
ANDROID = {
    "en-US": [("Watch it big,\nwatch it anywhere", "Picture in Picture & Chromecast"),
              ("Cast photos & videos\nto your TV", "Chromecast & DLNA smart TVs")],
    "vi": [("Xem thật đã,\nxem mọi nơi", "Hình trong hình & Chromecast"),
           ("Truyền ảnh & video\nlên TV", "Chromecast & TV thông minh DLNA")],
    "es-MX": [("Míralo en grande,\nen cualquier lugar", "Imagen en imagen y Chromecast"),
              ("Envía fotos y videos\na tu TV", "Chromecast y Smart TV DLNA")],
    "pt-BR": [("Assista em grande,\nonde quiser", "Picture in Picture e Chromecast"),
              ("Transmita fotos e\nvídeos para a TV", "Chromecast e Smart TVs DLNA")],
    "fr-FR": [("Regardez en grand,\npartout", "Image dans l'image et Chromecast"),
              ("Diffusez photos et\nvidéos sur la TV", "Chromecast et Smart TV DLNA")],
    "de-DE": [("Groß schauen,\nüberall", "Bild-in-Bild & Chromecast"),
              ("Fotos & Videos\nauf den TV streamen", "Chromecast & DLNA-Smart-TVs")],
    "ja": [("大画面で、\nどこでも", "ピクチャ・イン・ピクチャ & Chromecast"),
           ("写真や動画を\nテレビにキャスト", "Chromecast & DLNA対応テレビ")],
    "ko": [("크게 보고,\n어디서나 보고", "PIP & Chromecast"),
           ("사진과 동영상을\nTV로 전송", "Chromecast & DLNA 스마트 TV")],
    "zh-Hans": [("大屏观看\n随处可看", "画中画与 Chromecast"),
                ("把照片和视频\n投到电视上", "Chromecast 与 DLNA 智能电视")],
}

base.DEVICES = {
    "phone": dict(size=(1440, 2560), pair=0.53, island=False, punch=True, single=0.66, radius=0.09, bezel=0.028, title=128, top=600),
    "tablet": dict(size=(1920, 2560), wide=True, pair=0.54, island=False, punch=False, single=0.68, radius=0.035, bezel=0.03, title=124, top=560),
}
base.CAPTIONS = {k: v[:5] + [list(t) for t in ANDROID[k]] for k, v in base.CAPTIONS.items() if not k.startswith("_")}

_device = base.device


def android_device(screen, width, spec, light):
    body = _device(screen, width, spec, light)
    if spec.get("punch"):
        # The emulator has no camera cutout; draw the punch-hole camera every Android phone has.
        bezel = round(width * spec["bezel"])
        r, top = width * 0.022, width * 0.034
        cx = body.width / 2
        ImageDraw.Draw(body).ellipse((cx - r, bezel + top - r, cx + r, bezel + top + r), fill=(0, 0, 0, 255))
    return body


base.device = android_device


def main():
    args = sys.argv[1:]
    opts = {k: args[i + 1] for i, k in enumerate(args) if k.startswith("--")}
    pos = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or not args[i - 1].startswith("--"))]
    raw = Path(pos[0])
    out = Path(pos[1]) if len(pos) > 1 else HERE / "screenshots"
    count = 0
    for device_name in base.DEVICES:
        if opts.get("--device", device_name) != device_name:
            continue
        for locale in base.CAPTIONS:
            if opts.get("--locale", locale) != locale:
                continue
            for index, shot in enumerate(base.SHOTS):
                if opts.get("--only", shot["name"]) != shot["name"]:
                    continue
                target = out / device_name / PLAY_LOCALES[locale] / f"{index + 1:02d}_{shot['name']}.png"
                count += base.compose(device_name, locale, index, shot, raw, target)
    print(count, "written to", out)


if __name__ == "__main__":
    main()
