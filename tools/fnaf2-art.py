#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf2/images from the FNAF wiki.

    python3 tools/fnaf2-art.py

Why this exists: the first FNAF 2 build was made, tested, and then lost
when the sandbox that held it was wiped, because the art had been pulled
by hand and never recorded. Everything here is reproducible from the
manifest below.

Two traps, both paid for once already:

  * Fandom serves WebP by default and JavaFX cannot decode WebP. Every
    download appends `&format=original`, which returns the file as
    uploaded. Anything still WebP afterwards is converted here.
  * The wiki's vent-lit office images always have a character already
    standing in the vent, and the right vent has no image at all. The
    vent-lit views are therefore synthesised: the vent opening is filled
    with a crop of the vent camera feed, so the lit vent has the same
    corrugated texture the camera sees.

The mask is drawn rather than downloaded. The FNAF 2 teaser -- "NO PLACE
TO RUN / and exactly one place to hide" -- is literally the view through
the mask, and the only mask images on the wiki are from other games.
"""
import json, os, sys, urllib.parse, urllib.request

HOST = "fivenightsatfreddys.fandom.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120 Safari/537.36")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf2", "images")

# dest filename -> wiki File: name
MANIFEST = {
    "office.png":        "SecurityOfficeFreddyFazbear'sPizza1987.webp",
    "office.hall.png":   "FNAF2OfficeOnlyFlashlight.png",

    "room1.png":  "ShowStage.webp",
    "room2.png":  "GameAreaflashlight.webp",
    "room3.png":  "Kid'sCove(FreddyFazbear'sPizza)flashlight.webp",
    "room4.png":  "MainHallflashlight.webp",
    "room5.png":  "PartyRoom1flashlight.webp",
    "room6.png":  "PartyRoom2flashlight.webp",
    "room7.png":  "Vent1.webp",
    "room8.png":  "PartyRoom3flashlight.webp",
    "room9.png":  "PartyRoom4flashlight.webp",
    "room10.png": "Vent2.webp",
    "room11.png": "PrizeCornerflashlight.webp",

    "toyfreddy.png":      "Toy freddy.webp",
    "witheredfoxy.png":   "WitheredFoxyrender.webp",
    "toybonnie.png":      "Toy bonnie.webp",
    "toychica.png":       "ToyChica.png",
    "mangle.png":         "Manglerender.webp",
    "witheredbonnie.png": "Withered bonnie full body.png",
    "balloonboy.png":     "BBrender.webp",
    "puppet.png":         "Security Puppet.png",

    "js.toyfreddy.png":    "ToyFreddyJumpscare.gif",
    "js.toychica.png":     "ToyChicaJumpscare.gif",
    "js.witheredfoxy.png": "OLDWitheredFoxyJumpscare.gif",
    "js.mangle.png":       "MangleAttacks.gif",
    "js.balloonboy.png":   "BBAttack.gif",
    "js.puppet.png":       "FNAF2GoldenFreddyJumpscare.gif",
}

# The vent openings, in source pixels of the 1600x768 office.
VENT_L = (48, 468, 200, 690)
VENT_R = (1600 - 200, 468, 1600 - 48, 690)


def api(params):
    url = f"https://{HOST}/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return json.load(urllib.request.urlopen(req))


def fetch(name):
    d = api({"action": "query", "titles": "File:" + name,
             "prop": "imageinfo", "iiprop": "url", "format": "json"})
    for _, v in d.get("query", {}).get("pages", {}).items():
        if "missing" in v:
            return None
        url = v["imageinfo"][0]["url"]
    url = url.split("/revision/")[0] + "?format=original"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req).read()


def main():
    from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
    import numpy as np

    os.makedirs(OUT, exist_ok=True)
    for dest, src in MANIFEST.items():
        data = fetch(src)
        if data is None:
            print("MISSING", src)
            continue
        tmp = os.path.join(OUT, dest)
        open(tmp, "wb").write(data)
        img = Image.open(tmp)
        if getattr(img, "is_animated", False):
            img.seek(0)
        img = img.convert("RGBA")
        if img.width > 1400:
            img = img.resize((1400, int(img.height * 1400 / img.width)), Image.LANCZOS)
        # Opaque art is JPEG -- it is photographic, and PNG stores it at
        # roughly five times the size for no benefit. Figures and the mask
        # need alpha, so they stay PNG.
        if dest.startswith("js.") or dest.startswith("room") or dest.startswith("office.") \
                or dest == "office.png":
            img.convert("RGB").save(tmp[:-4] + ".jpg", "JPEG", quality=82,
                                    optimize=True, progressive=True)
            os.remove(tmp)
        else:
            img.save(tmp, "PNG", optimize=True)
        print(f"  {dest:22s} {img.width}x{img.height}")

    # ---- the vent-lit office views ----
    base = Image.open(os.path.join(OUT, "office.png")).convert("RGB")
    arr = np.asarray(base).astype(float)
    H, W = arr.shape[:2]
    vent = Image.open(os.path.join(OUT, "room7.png")).convert("RGB")

    def lit_view(x0, y0, x1, y1, name, flip):
        w, h = x1 - x0, y1 - y0
        tex = vent.crop((60, 60, 60 + int(640 * 0.55), 700)).resize((w, h), Image.LANCZOS)
        if flip:
            tex = tex.transpose(Image.FLIP_LEFT_RIGHT)
        tex = ImageEnhance.Brightness(tex).enhance(0.62)
        t = np.asarray(tex).astype(float)
        t[..., 0] *= 1.06; t[..., 2] *= 0.92          # warm it: a bulb, not daylight
        m = np.zeros((H, W), dtype=float)
        m[y0:y1, x0:x1] = 1.0
        m = np.asarray(Image.fromarray((m * 255).astype(np.uint8))
                       .filter(ImageFilter.GaussianBlur(9))).astype(float) / 255.0
        out = arr.copy()
        out[y0:y1, x0:x1] = t
        out = arr * (1 - m[..., None]) + out * m[..., None]
        Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).convert("RGBA").save(
            os.path.join(OUT, name), "PNG", optimize=True)
        print("  " + name)

    lit_view(*VENT_L, "office.ventL.png", False)
    lit_view(*VENT_R, "office.ventR.png", True)

    # ---- the mask ----
    mw, mh = 1280, 720
    mask = Image.new("RGBA", (mw, mh), (26, 18, 13, 255))
    yy, xx = np.mgrid[0:mh, 0:mw]
    r = np.sqrt(((xx - mw / 2) / (mw * 0.62)) ** 2 + ((yy - mh * 0.46) / (mh * 0.85)) ** 2)
    glow = np.clip(1.0 - r, 0, 1) ** 1.6
    a = np.asarray(mask).astype(float)
    a[..., 0] += glow * 46; a[..., 1] += glow * 30; a[..., 2] += glow * 20
    rng = np.random.default_rng(7)
    a[..., :3] = np.clip(a[..., :3] + rng.normal(0, 5.0, (mh, mw, 1)), 0, 255)
    mask = Image.fromarray(a.astype(np.uint8), "RGBA")

    holes = Image.new("L", (mw, mh), 0)
    hd = ImageDraw.Draw(holes)
    for ex in (mw * 0.295, mw * 0.705):
        hd.ellipse([ex - mw * 0.115, mh * 0.30, ex + mw * 0.115, mh * 0.66], fill=255)
    holes = holes.filter(ImageFilter.GaussianBlur(16))
    a = np.asarray(mask).astype(float)
    h = np.asarray(holes).astype(float) / 255.0
    a[..., 3] *= (1.0 - h)

    snout = Image.new("L", (mw, mh), 0)
    ImageDraw.Draw(snout).ellipse([mw * 0.36, mh * 0.62, mw * 0.64, mh * 1.02], fill=255)
    snout = snout.filter(ImageFilter.GaussianBlur(34))
    s = np.asarray(snout).astype(float) / 255.0
    for ch, v in ((0, 58), (1, 42), (2, 32)):
        a[..., ch] = a[..., ch] * (1 - s) + v * s
    a[..., 3] = np.maximum(a[..., 3], s * 255)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(
        os.path.join(OUT, "mask.png"), "PNG", optimize=True)
    print("  mask.png")


if __name__ == "__main__":
    main()
