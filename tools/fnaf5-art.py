#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf5/images from the FNAF wiki.

    python3 tools/fnaf5-art.py

Same reason this file exists as tools/fnaf2-art.py, tools/fnaf3-art.py and
tools/fnaf4-art.py: the art is not committed by hand, it is *reproducible*.
A sandbox that gets wiped takes the images with it, and a game whose art
cannot be rebuilt is a game that comes back with holes in it.

FNAF 5 is the one with a monitor and no window. The whole game is seen
through a security feed, so the art is treated the same way a cheap camera
treats a room:

    desaturate   the colour is mostly gone
    darken       the building is unlit and the camera has no lamp
    tint         toward the green of a cheap CCD

That treatment is here rather than in the game because it is an art
decision, and because doing it here is what lets five photographs from
five different places in the franchise read as five feeds from one
building.

Two states per room, and the second is the game:

    roomN.jpg        the feed is up and the room is clear
    here_<key>.jpg   the feed is up and something is standing in it

There is no third state. The monitor cannot show the room you are standing
in, so that room has no picture at all -- which is the rule of the game,
and the one thing the art must not quietly contradict.

Two traps, both already paid for once on FNAF 2, 3 and 4:

  * Fandom serves WebP by default and JavaFX cannot decode WebP. Every
    download appends `&format=original`, which returns the file as
    uploaded. Anything still WebP afterwards is converted here.
  * Opaque art is photographic and belongs in JPEG; figures need alpha and
    stay PNG. The caller should not have to know which, so the extension
    is decided here by what the image actually is.
"""
import io, json, os, sys, urllib.parse, urllib.request

HOST = "fivenightsatfreddys.fandom.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120 Safari/537.36")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf5", "images")

W, H = 1280, 720


def api(params):
    url = f"https://{HOST}/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return json.load(urllib.request.urlopen(req))


def fetch(name):
    d = api({"action": "query", "titles": "File:" + name,
             "prop": "imageinfo", "iiprop": "url", "format": "json"})
    url = None
    for _, v in d.get("query", {}).get("pages", {}).items():
        if "imageinfo" not in v:
            continue
        url = v["imageinfo"][0]["url"]
    if url is None:
        return None
    url = url.split("/revision/")[0] + "?format=original"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req).read()


def open_image(data, frame=0):
    from PIL import Image
    img = Image.open(io.BytesIO(data))
    if getattr(img, "is_animated", False):
        img.seek(frame)
    return img.convert("RGBA")


def cover(img, w=W, h=H, crop=None):
    """Scale and centre-crop to exactly w x h."""
    from PIL import Image
    if crop:
        x0, y0, x1, y1 = crop
        iw, ih = img.size
        img = img.crop((int(x0 * iw), int(y0 * ih), int(x1 * iw), int(y1 * ih)))
    iw, ih = img.size
    s = max(w / iw, h / ih)
    img = img.resize((max(1, int(iw * s)), max(1, int(ih * s))), Image.LANCZOS)
    iw, ih = img.size
    return img.crop(((iw - w) // 2, (ih - h) // 2,
                     (iw - w) // 2 + w, (ih - h) // 2 + h))


def camera(img, dark=0.55, tint=(0.62, 1.00, 0.80)):
    """
    Make a photograph look like a feed from a cheap security camera.

    Desaturate, darken, then push each channel toward the tint. The tint
    is deliberately not neutral: a green cast is the single cheapest way
    to say "this is a monitor" without drawing a single line of UI.
    """
    from PIL import Image, ImageEnhance
    img = img.convert("RGB")
    img = ImageEnhance.Color(img).enhance(0.35)
    img = ImageEnhance.Brightness(img).enhance(dark)
    r, g, b = img.split()
    r = r.point(lambda v: min(255, int(v * tint[0])))
    g = g.point(lambda v: min(255, int(v * tint[1])))
    b = b.point(lambda v: min(255, int(v * tint[2])))
    return Image.merge("RGB", (r, g, b))


def paste_face(base, face, height_frac, cx_frac=0.5, bottom_frac=1.0):
    """
    Put a face into a room, lit, at the bottom of the frame.

    The faces are menu renders on black, so they do not need alpha -- they
    are composited with a screen blend, which keeps the black background
    from becoming a rectangle and lets the room show through the dark
    parts. That is also what a camera sees when something is standing
    right in front of it: a lit face and the room still visible around it.
    """
    from PIL import Image, ImageChops
    base = base.convert("RGB")
    fh = int(base.height * height_frac)
    fw = max(1, int(face.width * fh / face.height))
    face = face.convert("RGB").resize((fw, fh), Image.LANCZOS)
    layer = Image.new("RGB", base.size, (0, 0, 0))
    x = int(base.width * cx_frac - fw / 2)
    y = int(base.height * bottom_frac - fh)
    layer.paste(face, (max(0, x), max(0, y)))
    return ImageChops.screen(base, layer)


# --- what to fetch -----------------------------------------------------

# One room per feed. The sources are five different places in the
# franchise and the camera treatment is what makes them one building.
ROOMS = {
    0: "BreakerRoom.jpg",             # Parts/Service
    1: "Hall9.webp",                  # Ballora Gallery
    2: "Hall1.webp",                  # Control Module
    3: "PlushBabyGallery.png",        # Circus Gallery
    4: "Hall10.webp",                 # Funtime Auditorium
}

# Which frame of an animation to take, keyed by destination.
FRAMES = {
    1: 0,
}

# Fractional crop boxes, applied before the 16:9 cover.
CROPS = {
    # The breaker room is a wide dark shot with an EXIT sign in the top
    # left; the sign is the brightest thing in it and it is not part of
    # this building.
    0: (0.10, 0.18, 1.0, 1.0),
    # Plush Baby Gallery is a birthday banner and a wall of balloons, and
    # the banner is at the top of the frame.
    3: (0.0, 0.10, 1.0, 1.0),
}

# How dark each room's feed is. Parts/Service and the Auditorium are the
# two ends of the building and the two darkest rooms in it.
DARK = {0: 0.58, 1: 0.85, 2: 1.05, 3: 0.78, 4: 0.72}

# The faces. Menu renders on black, which is why they composite so well.
FACES = {
    "ballora": ("MenuBallora.gif", 0),
    "foxy": ("MenuFuntimeFoxy.gif", 0),
    "freddy": ("MenuFuntimeFreddy.gif", 0),
}

# Which room each one is standing in when the camera finds it.
HOME = {"ballora": 1, "foxy": 4, "freddy": 0}


def main():
    from PIL import Image

    os.makedirs(OUT, exist_ok=True)
    missing = []
    cache = {}

    def source(name, frame=0):
        key = (name, frame)
        if key not in cache:
            data = fetch(name)
            cache[key] = open_image(data, frame) if data else None
        return cache[key]

    def need(name, frame=0):
        img = source(name, frame)
        if img is None:
            missing.append(name)
        return img

    # --- the five feeds ---------------------------------------------------
    for i, name in ROOMS.items():
        src = need(name, FRAMES.get(i, 0))
        if src is None:
            continue
        img = cover(src, crop=CROPS.get(i))
        img = camera(img, DARK.get(i, 0.55))
        dest = os.path.join(OUT, f"room{i}.jpg")
        img.save(dest, "JPEG", quality=84, optimize=True, progressive=True)
        print(f"  room{i}.jpg{'':14s} {img.width}x{img.height}  <- {name}")

    # --- the same five feeds, with something standing in them -------------
    for key, (name, frame) in FACES.items():
        face = need(name, frame)
        room = HOME[key]
        base_src = need(ROOMS[room], FRAMES.get(room, 0))
        if face is None or base_src is None:
            continue
        base = camera(cover(base_src, crop=CROPS.get(room)), DARK.get(room, 0.55))
        img = paste_face(base, face, 0.62, 0.5, 1.0)
        img = camera(img, 0.92, tint=(0.80, 1.00, 0.88))
        dest = os.path.join(OUT, f"here_{key}.jpg")
        img.save(dest, "JPEG", quality=84, optimize=True, progressive=True)
        print(f"  here_{key}.jpg{'':10s} {img.width}x{img.height}  <- {name}")

    # --- the jumpscares ---------------------------------------------------
    for key, (name, frame) in FACES.items():
        src = need(name, frame)
        if src is None:
            continue
        img = cover(src)
        img = camera(img, 1.05, tint=(0.92, 1.00, 1.00))
        dest = os.path.join(OUT, f"scare_{key}.jpg")
        img.save(dest, "JPEG", quality=84, optimize=True, progressive=True)
        print(f"  scare_{key}.jpg{'':9s} {img.width}x{img.height}  <- {name}")

    if missing:
        print("could not fetch:", ", ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
