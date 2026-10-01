#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf6/images from the FNAF wiki.

    python3 tools/fnaf6-art.py

Same reason this file exists as tools/fnaf2-art.py through
tools/fnaf5-art.py: the art is not committed by hand, it is *reproducible*.
A sandbox that gets wiped takes the images with it, and a game whose art
cannot be rebuilt is a game that comes back with holes in it.

FNAF 6 is the one with a chair in it. There is one room and one picture per
unit, and **there is no picture per pose** -- the pose is drawn, by scaling
and raising the unit out of the chair. That is a deliberate choice rather
than a shortcut: five photographs of the same animatronic in five poses
would be five photographs that have to agree about lighting, angle and
scale, and the one thing the player has to read at a glance -- *is it
higher than it was* -- is a fact about position, which the engine already
knows exactly.

What this produces:

    room.jpg            the bay, empty: a dark gradient and the desk.
    desk.png            the desk alone, with alpha.
    unit_<key>.png      the thing in the chair, full body, transparent.
    scare_<key>.jpg     its face, filling the screen.

`desk.png` exists because the desk has to be drawn **after** the unit, not
with the room. The thing in the chair is behind the desk, and the one
thing the player has to read at a glance is how much of it is above the
desk line -- a unit drawn on top of the desk is a unit standing on the
table, and a unit whose legs are hidden is a unit that is getting up.

The desk is the one real asset here and it comes out of the salvage-room
jumpscare animation, which is the only picture of the actual room the wiki
has. The rest of the room is drawn, because the wiki's salvage room frames
are all *of an animatronic* and a room with the wrong thing already
standing in it is worse than a dark one.

Two traps, both already paid for once on FNAF 2 through 5:

  * Fandom serves WebP by default and JavaFX cannot decode WebP. Every
    download appends `&format=original`, which returns the file as
    uploaded. Anything still WebP afterwards is converted here.
  * Opaque art is photographic and belongs in JPEG; a figure needs alpha
    and stays PNG. The caller should not have to know which, so the
    extension is decided here by what the image actually is.
"""
import io, json, os, sys, urllib.parse, urllib.request

HOST = "fivenightsatfreddys.fandom.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120 Safari/537.36")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf6", "images")

W, H = 1280, 720

# The four units, and the render each one is built from. All four are
# full-body and already transparent, which is why the units are the easy
# half of this file.
UNITS = {
    "scraptrap": "Scraptrap Infobox.png",
    "scrapbaby": "ScrapBabyrender.webp",
    "moltenfreddy": "MoltenFreddyrender.webp",
    "lefty": "Leftyrender.webp",
}

# The desk. The only picture of the actual salvage room on the wiki, and
# it has an animatronic sitting behind it -- so only the bottom band is
# taken, which is the part the desk occupies and the part the thing in the
# chair never reaches.
DESK = ("Salvage Springtrap Salvage Room Jumpscare.gif", 0)
DESK_TOP = 0.78      # fraction of the frame where the desk starts

# How tall a unit is drawn at, in pixels. All four are normalised to this
# so a player can compare one night's silhouette with another's.
UNIT_H = 900


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


def trim(img):
    """Crop to the alpha bounding box, so every unit is the same shape."""
    box = img.getbbox()
    return img.crop(box) if box else img


def cover(img, w=W, h=H):
    """Scale and centre-crop to exactly w x h."""
    from PIL import Image
    iw, ih = img.size
    s = max(w / iw, h / ih)
    img = img.resize((max(1, int(iw * s)), max(1, int(ih * s))), Image.LANCZOS)
    iw, ih = img.size
    return img.crop(((iw - w) // 2, (ih - h) // 2,
                     (iw - w) // 2 + w, (ih - h) // 2 + h))


def build_room(desk_frame):
    """
    The bay: a dark gradient, the desk, and a vignette.

    The gradient is not decoration. The room is lit by one lamp that the
    player switches on and off, so the picture has to be dark enough that
    the lamp is the only thing that changes it -- and warm enough at the
    bottom that the desk reads as wood rather than as a black bar.
    """
    from PIL import Image, ImageDraw, ImageFilter
    base = Image.new("RGB", (W, H), (4, 4, 7))
    d = ImageDraw.Draw(base)
    for y in range(H):
        t = y / H
        r = int(4 + 14 * t * t)
        g = int(4 + 10 * t * t)
        b = int(7 + 8 * t * t)
        d.line([(0, y), (W, y)], fill=(r, g, b))

    if desk_frame is not None:
        band = desk_band(desk_frame)
        base.paste(band, (0, H - band.height), band)

    # The vignette, so the corners of the room are not a flat colour.
    vig = Image.new("L", (W, H), 0)
    dv = ImageDraw.Draw(vig)
    dv.ellipse((-W * 0.35, -H * 0.35, W * 1.35, H * 1.35), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(160))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    base = Image.composite(base, dark, vig.point(lambda v: 90 + v * 165 // 255))
    return base


def desk_band(frame):
    """The desk alone, scaled to the frame width, with its alpha kept."""
    from PIL import Image
    iw, ih = frame.size
    band = frame.crop((0, int(ih * DESK_TOP), iw, ih))
    s = W / band.width
    return band.resize((W, max(1, int(band.height * s))), Image.LANCZOS)


def build_scare(unit):
    """
    The jumpscare: the unit's own head, filling the screen.

    Built from the render rather than fetched, and that is the honest
    choice rather than the cheap one -- the wiki has salvage-room
    jumpscares for two of the four units and nothing for the other two,
    and a franchise where two of the four have a different kind of death
    screen is a franchise with a seam in it. Cropping the head means all
    four are the same picture at the same angle, which is what a jumpscare
    is supposed to be.
    """
    from PIL import Image, ImageEnhance
    iw, ih = unit.size
    head = unit.crop((0, 0, iw, int(ih * 0.42)))
    img = cover(head.convert("RGB"))
    img = ImageEnhance.Brightness(img).enhance(1.25)
    img = ImageEnhance.Contrast(img).enhance(1.15)
    r, g, b = img.split()
    r = r.point(lambda v: min(255, int(v * 1.06)))
    b = b.point(lambda v: min(255, int(v * 0.94)))
    return Image.merge("RGB", (r, g, b))


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

    # --- the units --------------------------------------------------------
    units = {}
    for key, name in UNITS.items():
        src = source(name)
        if src is None:
            missing.append(name)
            continue
        img = trim(src)
        s = UNIT_H / img.height
        img = img.resize((max(1, int(img.width * s)), UNIT_H), Image.LANCZOS)
        units[key] = img
        dest = os.path.join(OUT, f"unit_{key}.png")
        img.save(dest, "PNG", optimize=True)
        print(f"  unit_{key}.png{'':12s} {img.width}x{img.height}  <- {name}")

    # --- the room ---------------------------------------------------------
    desk_src = source(*DESK)
    if desk_src is None:
        missing.append(DESK[0])
    room = build_room(desk_src)
    room.save(os.path.join(OUT, "room.jpg"), "JPEG",
              quality=86, optimize=True, progressive=True)
    print(f"  room.jpg{'':18s} {room.width}x{room.height}  <- {DESK[0]}")

    if desk_src is not None:
        desk = desk_band(desk_src)
        desk.save(os.path.join(OUT, "desk.png"), "PNG", optimize=True)
        print(f"  desk.png{'':18s} {desk.width}x{desk.height}  <- {DESK[0]}")

    # --- the jumpscares ---------------------------------------------------
    for key, img in units.items():
        scare = build_scare(img)
        scare.save(os.path.join(OUT, f"scare_{key}.jpg"), "JPEG",
                   quality=84, optimize=True, progressive=True)
        print(f"  scare_{key}.jpg{'':11s} {scare.width}x{scare.height}  <- {key}")

    if missing:
        print("could not fetch:", ", ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
