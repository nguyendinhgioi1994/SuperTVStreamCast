"""Replace the Google Play screenshots through the Android Publisher API (one edit, then commit).

Usage (a Python with google-auth + requests):
    python upload_screenshots.py [<screenshots-dir>] [--dry-run]

<screenshots-dir>/<phone|tablet>/<play locale>/NN_<shot>.png (output of build_screenshots.py).
phone -> phoneScreenshots; tablet -> sevenInchScreenshots and tenInchScreenshots. Each type is
cleared before uploading. A locale without a store listing in Play Console first gets one from
listing.json (title, short and full description); existing listings are left as they are. Credentials:
the service account key in ~/.googleplay/play-publisher.json (needs "Manage store presence").
"""
import json
import os
import sys
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PKG = "com.tuntech.supertvstreamcast"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}"
# listing.json key -> Play language code where they differ.
LANGUAGE = {"vi-VN": "vi"}
TYPES = {"phone": ["phoneScreenshots"], "tablet": ["sevenInchScreenshots", "tenInchScreenshots"]}


def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args
    pos = [a for a in args if not a.startswith("--")]
    root = Path(pos[0]) if pos else Path(__file__).resolve().parent / "screenshots"
    creds = service_account.Credentials.from_service_account_file(
        os.path.expanduser("~/.googleplay/play-publisher.json"),
        scopes=["https://www.googleapis.com/auth/androidpublisher"])
    s = AuthorizedSession(creds)
    r = s.post(f"{API}/edits")
    r.raise_for_status()
    edit = r.json()["id"]
    listed = {l["language"] for l in s.get(f"{API}/edits/{edit}/listings").json().get("listings", [])}
    print("listings:", sorted(listed))
    texts = json.loads((Path(__file__).resolve().parent / "listing.json").read_text())["locales"]
    wanted = {f.name for device in TYPES for f in (root / device).iterdir() if f.is_dir()}
    try:
        for key, text in texts.items():
            lang = LANGUAGE.get(key, key)
            if lang in listed or lang not in wanted:
                continue
            print("create listing", lang)
            if not dry:
                s.put(f"{API}/edits/{edit}/listings/{lang}", json=dict(language=lang, **text)).raise_for_status()
            listed.add(lang)
        for device, types in TYPES.items():
            for folder in sorted((root / device).iterdir()):
                if folder.name not in listed:
                    print("skip (no listing)", device, folder.name)
                    continue
                files = sorted(folder.glob("*.png"))
                for t in types:
                    print(device, folder.name, t, len(files))
                    if dry:
                        continue
                    s.delete(f"{API}/edits/{edit}/listings/{folder.name}/{t}").raise_for_status()
                    for f in files:
                        r = s.post(f"{UPLOAD}/edits/{edit}/listings/{folder.name}/{t}?uploadType=media",
                                   data=f.read_bytes(), headers={"Content-Type": "image/png"})
                        if not r.ok:
                            raise RuntimeError(f"{f}: {r.status_code} {r.text[:300]}")
        if dry:
            s.delete(f"{API}/edits/{edit}")
            return
        r = s.post(f"{API}/edits/{edit}:commit")
        if not r.ok and "changesNotSentForReview" in r.text:
            r = s.post(f"{API}/edits/{edit}:commit?changesNotSentForReview=true")
        print("commit", r.status_code, r.text[:300])
        r.raise_for_status()
    except Exception:
        s.delete(f"{API}/edits/{edit}")
        raise


if __name__ == "__main__":
    main()
