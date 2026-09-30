#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf3/images from the FNAF wiki.

    python3 tools/fnaf3-art.py

Same reason this file exists as tools/fnaf2-art.py: the art is not
committed by hand, it is *reproducible*. A sandbox that gets wiped takes
the images with it, and a game whose art cannot be rebuilt is a game that
comes back with holes in it.

The FNAF 3 camera feeds are the wiki's `Hall1.webp` .. `Hall10.webp`,
which is what the CAM 01..CAM 10 articles actually embed. The office is
`SecurityOfficeFazbear'sFright.webp`. Springtrap and the six phantoms are
the `*render.webp` files.

Two traps, both already paid for once on FNAF 2:

  * Fandom serves WebP by default and JavaFX cannot decode WebP. Every
    download appends `&format=original`, which returns the file as
    uploaded. Anything still WebP afterwards is converted here.
  * Opaque art is photographic and belongs in JPEG; figures need alpha and
    stay PNG. The caller should not have to know which, so the extension
    is decided here by what the image actually is.
"""
import json, os, sys, urllib.parse, urllib.request

HOST = "fivenightsatfreddys.fandom.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120 Safari/537.36")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf3", "images")

# dest filename -> wiki File: name
MANIFEST = {
    "office.jpg": "SecurityOfficeFazbear'sFright.webp",
    "springtrap.png": "Springtraprender.webp",
    "phantomfreddy.png": "PhantomFreddyrender.webp",
    "phantomchica.png": "PhantomChicarender.webp",
    "phantomfoxy.png": "PhantomFoxyrender.webp",
    "phantommangle.png": "PhantomManglerender.webp",
    "phantompuppet.png": "PhantomMarionetterender.webp",
    "phantombb.png": "PhantomBBrender.webp",
}
for i in range(1, 11):
    MANIFEST[f"room{i}.jpg"] = f"Hall{i}.webp"


def api(params):
    url = f"https://{HOST}/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return json.load(urllib.request.urlopen(req))


def fetch(name):
    d = api({"action": "query", "titles": "File:" + name,
             "prop": "imageinfo", "iiprop": "url", "format": "json"})
    url = None
    for _, v in d.get("query", {}).get("pages", {}).items():
        if "missing" in v:
            return None
        url = v["imageinfo"][0]["url"]
    if url is None:
        return None
    url = url.split("/revision/")[0] + "?format=original"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req).read()


def main():
    from PIL import Image

    os.makedirs(OUT, exist_ok=True)
    missing = []
    for dest, src in MANIFEST.items():
        data = fetch(src)
        if data is None:
            print("MISSING", src)
            missing.append(src)
            continue
        tmp = os.path.join(OUT, dest)
        open(tmp, "wb").write(data)
        img = Image.open(tmp)
        if getattr(img, "is_animated", False):
            img.seek(0)
        img = img.convert("RGBA")
        if img.width > 1400:
            img = img.resize((1400, int(img.height * 1400 / img.width)), Image.LANCZOS)
        if dest.endswith(".jpg"):
            img.convert("RGB").save(tmp, "JPEG", quality=82,
                                    optimize=True, progressive=True)
        else:
            img.save(tmp, "PNG", optimize=True)
        print(f"  {dest:22s} {img.width}x{img.height}")

    if missing:
        print("could not fetch:", ", ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
