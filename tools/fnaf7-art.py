#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf7/images from the FNAF wiki.

    python3 tools/fnaf7-art.py

Same reason this file exists as tools/fnaf2-art.py through
tools/fnaf6-art.py: the art is not committed by hand, it is
*reproducible*. A sandbox that gets wiped takes the images with it, and a
game whose art cannot be rebuilt is a game that comes back with holes in
it.

FNAF 7 is the one with two doors and one bar. There is one room and one
picture per unit, and **there is no picture per hall** -- the unit is
drawn into whichever doorway it is standing in, by the screen, from the
engine's own state. That is the same choice FNAF 6 made about the chair,
for the same reason: the one thing the player has to read at a glance is
*which side is it on*, and that is a fact about position, which the engine
already knows exactly. Two photographs of the same animatronic in two
doorways would be two photographs that have to agree about lighting, angle
and scale, and they would not.

What this produces:

    room.jpg            the office, empty: a dark gradient, two doorways,
                        and a desk.
    unit_<key>.png      the thing in the hall, full body, transparent.
    scare_<key>.jpg     its face, filling the screen.

The room is drawn rather than fetched, and that is the honest choice. The
wiki has no picture of this office -- it is a room that exists in this
game and nowhere else -- and a room assembled out of somebody else's
screenshots is a room with the wrong thing already standing in it.

Two traps, both already paid for once on FNAF 2 through 6:

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
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf7", "images")

W, H = 1280, 720

# The five, and the render each one is built from. All five are full body
# and all five ship transparent, which is why the units are the easy half
# of this file.
UNITS = {
    "baby": "CircusBabyrender.webp",
    "ennard": "Ennardrender.webp",
    "glitchtrap": "Glitchtraprender.webp",
    "vanny": "Vannyrender.webp",
    "mimic": "SBRuin_Mimic.png",
}

# How tall a unit is drawn at, in pixels. All five are normalised to this
# so a player can compare one night's silhouette with another's.
UNIT_H = 780

# The one unit the wiki has no transparent render for, and what to do
# about it.
#
# Every other night is a full-body figure that ships with alpha. The Mimic
# does not: the only picture of it is a screenshot from Ruin, and it is a
# photograph of a cave with a head in it. So this one is cropped to the
# head and *masked* -- a radial falloff that takes the cave away and leaves
# the face -- rather than keyed, because a keyer run against a rock wall
# either eats the character or leaves the wall.
#
# It is drawn smaller than the other four on purpose. A head normalised to
# a full body's height is a head the size of a doorway, and the doorway is
# the one thing in the office the player has to be able to measure.
MASKED = {
    "mimic": {"crop": (150, 40, 750, 640), "height": 500, "inner": 0.90},
}

# The two doorways, as fractions of the frame. The office is symmetric and
# the doorways are the only thing in it, so they are also the layout the
# screen uses to decide where to draw a unit.
DOOR_W = 0.26
DOOR_TOP = 0.14
DOOR_BOTTOM = 0.80


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


def mask_figure(src, spec):
    """
    Crop a photographic source to its figure and take the background away.

    The falloff is radial and generous: a hard edge around a head is a
    sticker, and the doorway it is standing in is dark enough that a soft
    one reads as the figure being lit and the room not.
    """
    from PIL import Image, ImageDraw, ImageFilter
    img = src.convert("RGBA").crop(spec["crop"])
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    inner = spec["inner"]
    d.ellipse((w * (1 - inner) / 2, h * (1 - inner) / 2,
               w - w * (1 - inner) / 2, h - h * (1 - inner) / 2), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(w * 0.11))
    img.putalpha(mask)
    return trim(img)


def build_room():
    """
    The office: a dark gradient, two doorways, a desk, and a vignette.

    The gradient is not decoration. The room is lit by one hand lamp that
    the player switches on and off, so the picture has to be dark enough
    that the lamp is the only thing that changes it -- and warm enough at
    the bottom that the desk reads as a desk rather than as a black bar.

    The doorways are drawn as holes rather than as frames: a doorway that
    is *lighter* than the room would be a doorway the player could read
    without spending the lamp, and reading without spending the lamp is the
    one thing the night is about.
    """
    from PIL import Image, ImageDraw, ImageFilter
    base = Image.new("RGB", (W, H), (4, 4, 7))
    d = ImageDraw.Draw(base)
    for y in range(H):
        t = y / H
        r = int(4 + 16 * t * t)
        g = int(4 + 11 * t * t)
        b = int(7 + 9 * t * t)
        d.line([(0, y), (W, y)], fill=(r, g, b))

    # The wall, so the office is a room and not a void. Vertical seams,
    # barely there: enough that the eye has something to measure the
    # doorways against, not enough to read without the lamp.
    for i in range(1, 8):
        x = int(W * i / 8)
        d.line([(x, 0), (x, int(H * 0.74))], fill=(9, 9, 13))

    # The two doorways. Nothing behind them at all: the halls are where the
    # unit is, and the unit is drawn by the screen. The frame is what makes
    # them read as openings rather than as panels.
    dw = int(W * DOOR_W)
    top, bot = int(H * DOOR_TOP), int(H * DOOR_BOTTOM)
    for x0 in (int(W * 0.03), W - int(W * 0.03) - dw):
        d.rectangle([x0, top, x0 + dw, bot], fill=(2, 2, 4))
        d.rectangle([x0 - 7, top - 7, x0 + dw + 7, top], fill=(24, 21, 26))
        d.rectangle([x0 - 7, bot, x0 + dw + 7, bot + 7], fill=(24, 21, 26))
        d.rectangle([x0 - 7, top - 7, x0, bot + 7], fill=(24, 21, 26))
        d.rectangle([x0 + dw, top - 7, x0 + dw + 7, bot + 7], fill=(24, 21, 26))
        d.rectangle([x0 - 7, top - 7, x0 + dw + 7, top - 4], fill=(38, 33, 40))

    # The desk, across the bottom, so the office has a near edge.
    d.rectangle([0, int(H * 0.74), W, H], fill=(13, 10, 9))
    d.rectangle([0, int(H * 0.74), W, int(H * 0.74) + 5], fill=(34, 27, 22))
    d.rectangle([0, int(H * 0.74) + 5, W, int(H * 0.74) + 8], fill=(20, 16, 13))

    # The vignette, so the corners of the room are not a flat colour.
    vig = Image.new("L", (W, H), 0)
    dv = ImageDraw.Draw(vig)
    dv.ellipse((-W * 0.35, -H * 0.35, W * 1.35, H * 1.35), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(160))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    return Image.composite(base, dark, vig.point(lambda v: 80 + v * 175 // 255))


def build_scare(unit):
    """
    The jumpscare: the unit's own head, filling the screen.

    Built from the render rather than fetched, and that is the honest
    choice rather than the cheap one -- the wiki has jumpscares for some of
    these and nothing for the others, and a franchise where some nights
    have a different kind of death screen is a franchise with a seam in it.
    Cropping the head means all five are the same picture at the same
    angle, which is what a jumpscare is supposed to be.
    """
    from PIL import Image, ImageEnhance
    iw, ih = unit.size
    # From just under the top to a bit past the shoulders. The top of a
    # full-body render is whatever is highest -- ears, a hat, an antenna --
    # and a jumpscare framed on a pair of ears is a jumpscare of a pair of
    # ears.
    head = unit.crop((0, int(ih * 0.05), iw, int(ih * 0.45)))
    img = cover(head.convert("RGB"))
    img = ImageEnhance.Brightness(img).enhance(1.28)
    img = ImageEnhance.Contrast(img).enhance(1.18)
    r, g, b = img.split()
    r = r.point(lambda v: min(255, int(v * 1.07)))
    b = b.point(lambda v: min(255, int(v * 0.93)))
    return Image.merge("RGB", (r, g, b))


def main():
    from PIL import Image

    os.makedirs(OUT, exist_ok=True)
    missing = []
    cache = {}

    def source(name):
        if name not in cache:
            data = fetch(name)
            cache[name] = open_image(data) if data else None
        return cache[name]

    units = {}
    for key, name in UNITS.items():
        src = source(name)
        if src is None:
            missing.append(name)
            continue
        if key in MASKED:
            img = mask_figure(src, MASKED[key])
        else:
            img = trim(src)
        h = MASKED[key]["height"] if key in MASKED else UNIT_H
        s = h / img.height
        img = img.resize((max(1, int(img.width * s)), h), Image.LANCZOS)
        units[key] = img
        img.save(os.path.join(OUT, f"unit_{key}.png"), "PNG", optimize=True)
        print(f"  unit_{key}.png{'':12s} {img.width}x{img.height}  <- {name}")

    room = build_room()
    room.save(os.path.join(OUT, "room.jpg"), "JPEG",
              quality=86, optimize=True, progressive=True)
    print(f"  room.jpg{'':18s} {room.width}x{room.height}  <- drawn")

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
