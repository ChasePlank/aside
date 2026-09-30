#!/usr/bin/env python3
"""
Rebuild src/main/resources/fnaf4/images from the FNAF wiki.

    python3 tools/fnaf4-art.py

Same reason this file exists as tools/fnaf2-art.py and tools/fnaf3-art.py:
the art is not committed by hand, it is *reproducible*. A sandbox that gets
wiped takes the images with it, and a game whose art cannot be rebuilt is a
game that comes back with holes in it.

FNAF 4 is the one with no cameras, so its art is not a set of camera feeds.
It is four places to look, and each one has three states:

    dark      what you see with the flashlight off
    lit       what you see with it on, and nothing there
    here      what you see with it on, and something there

The three door views are the wiki's own `Left door dark/lit/peek` and
`Right door dark/lit/peek`, which is why they are the two stations that
needed no compositing at all -- the game shipped those three states as
screenshots. The closet and the bed did need it: the closet's "here" is
Nightmare Foxy's render pasted into the closet, and the bed's three states
are three frames of two different wiki animations.

Every scene is written out at exactly 1280x720, the engine's canvas. That
is deliberate: the crop is an art decision, so it belongs here, in the file
that can be re-run, and not in the game code where it would be invisible.

Two traps, both already paid for once on FNAF 2 and FNAF 3:

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
OUT = os.path.join(HERE, "..", "src", "main", "resources", "fnaf4", "images")

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
        if "missing" in v:
            return None
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
        img.seek(min(frame, img.n_frames - 1))
    return img.convert("RGBA")


def cover(img, w=W, h=H, crop=None):
    """
    Crop, then scale to fill w x h and centre-crop the overflow.

    `crop` is a fractional box (l, t, r, b) applied first. It exists because
    the door screenshots are 526x720 portraits with FNAF 1's power meter
    along the bottom, and a straight 16:9 cover keeps the meter and throws
    away the top of the doorway. The box is the fix, and it is written down
    here rather than tuned by eye in the game.
    """
    if crop:
        l, t, r, b = crop
        img = img.crop((int(img.width * l), int(img.height * t),
                        int(img.width * r), int(img.height * b)))
    s = max(w / img.width, h / img.height)
    img = img.resize((max(w, int(round(img.width * s))),
                      max(h, int(round(img.height * s)))), 1)
    x = (img.width - w) // 2
    y = (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def bright(img, factor):
    from PIL import ImageEnhance
    return ImageEnhance.Brightness(img).enhance(factor)


def paste_figure(base, figure, height_frac, cx_frac=0.5, bottom_frac=1.0):
    """Scale a transparent render and paste it into a scene."""
    fh = int(base.height * height_frac)
    fw = max(1, int(figure.width * fh / figure.height))
    fig = figure.resize((fw, fh), 1)
    x = int(base.width * cx_frac - fw / 2)
    y = int(base.height * bottom_frac - fh)
    base.alpha_composite(fig, (max(0, x), max(0, y)))
    return base


# dest filename -> wiki File: name
MANIFEST = {
    "left_dark.jpg": "Left door dark.png",
    "left_lit.jpg": "Left door lit.png",
    "left_here.jpg": "Left door peek.png",
    "right_dark.jpg": "Right door dark.png",
    "right_lit.jpg": "Right door lit.png",
    "right_here.jpg": "Right door peek.png",
    "closet.jpg": "Closet.png",
    "bed_dark.jpg": "FNAF4 Bedroom.gif",
    "bed_lit.jpg": "FNAF4 Bedroom.gif",
    "bed_here.jpg": "Freddles on bed.png",
    "foxy.png": "Nightmare Foxy.png",
    "fredbear.png": "NightmareFredbearrender.webp",
    "scare_bonnie.jpg": "Left door peek.png",
    "scare_chica.jpg": "NightmareChicaMugshot.png",
    "scare_foxy.jpg": "NightmareFoxyBright.jpg",
    "scare_freddy.jpg": "Nightmare Freddy.gif",
    "scare_fredbear.jpg": "Fredbear Left Hall Close.jpg",
}

# Which animation frame to take, keyed by DESTINATION -- two destinations can
# share one source and need different frames of it, which is exactly what the
# bed's dark and lit states are.
FRAMES = {
    "bed_dark.jpg": 0,      # the room, flashlight off
    "bed_lit.jpg": 12,      # the room, flashlight on
    "bed_here.jpg": 0,
    "scare_freddy.jpg": 15, # his face, filling the frame
}

# Fractional crop boxes, applied before the 16:9 cover.
#
# The door shots are 526x720 portraits: a doorway, FNAF 1's DOOR/LIGHT
# buttons down the left edge, and FNAF 1's power meter along the bottom.
# Neither belongs in FNAF 4, and a plain 16:9 cover keeps both and throws
# away the top of the doorway instead. So the box takes the doorway and
# drops the rest, at an aspect that is already 16:9 -- which makes the
# cover a pure scale and means nothing is cropped twice.
CROPS = {
    "left_dark.jpg": (0.14, 0.0, 1.0, 0.353),
    "left_lit.jpg": (0.14, 0.0, 1.0, 0.353),
    "left_here.jpg": (0.14, 0.0, 1.0, 0.353),
    # The right-hand shots carry FNAF 1's "12 AM / Night 7" clock in the top
    # right corner, which the left-hand ones do not. The box starts below it.
    "right_dark.jpg": (0.14, 0.14, 1.0, 0.5025),
    "right_lit.jpg": (0.14, 0.14, 1.0, 0.5025),
    "right_here.jpg": (0.14, 0.14, 1.0, 0.5025),
    # The Freddles sit along the top of the bed, which is the top of the
    # frame -- a centred cover takes them off.
    "bed_here.jpg": (0.0, 0.05, 1.0, 0.824),
    # Bonnie's face, for the jumpscare.
    "scare_bonnie.jpg": (0.10, 0.0, 0.62, 0.62),
    # The source is a brightened fan edit with "Or me?" written across the
    # top right corner. Cropping the corner is the whole fix.
    "scare_foxy.jpg": (0.0, 0.14, 1.0, 1.0),
}

# Brightness applied after the cover, so the same source can serve as both
# the dark and the lit state of a station.
BRIGHT = {
    # The dark states are the flashlight being off, and they have to be
    # dark enough that the lit state is a relief rather than a change of
    # exposure. The wiki's own "dark" door shots are already close; the
    # bedroom and the closet are not, so they get pushed down here.
    "left_dark.jpg": 0.55,
    "left_lit.jpg": 1.70,
    "left_here.jpg": 1.70,
    "right_dark.jpg": 0.55,
    "right_lit.jpg": 1.70,
    "right_here.jpg": 1.70,
    "closet_dark.jpg": 0.35,
    "closet_lit.jpg": 1.55,
    "closet_here.jpg": 1.55,
    "bed_dark.jpg": 0.32,
    "bed_lit.jpg": 1.35,
    "bed_here.jpg": 1.15,
}


def main():
    from PIL import Image

    os.makedirs(OUT, exist_ok=True)
    missing = []
    cache = {}

    def source(dest):
        """The source image for a destination, at the frame that dest wants."""
        if dest not in cache:
            data = fetch(MANIFEST[dest])
            cache[dest] = open_image(data, FRAMES.get(dest, 0)) if data else None
        return cache[dest]

    def need(dest):
        img = source(dest)
        if img is None:
            missing.append(MANIFEST[dest])
        return img

    # --- the four stations -------------------------------------------------
    for dest in ["left_dark.jpg", "left_lit.jpg", "left_here.jpg",
                 "right_dark.jpg", "right_lit.jpg", "right_here.jpg",
                 "bed_dark.jpg", "bed_lit.jpg", "bed_here.jpg"]:
        src = need(dest)
        if src is None:
            continue
        img = cover(src, crop=CROPS.get(dest))
        img = bright(img, BRIGHT.get(dest, 1.0))
        img.convert("RGB").save(os.path.join(OUT, dest), "JPEG", quality=84,
                                optimize=True, progressive=True)
        print(f"  {dest:20s} {img.width}x{img.height}")

    # --- the closet, which is one picture in three states ------------------
    closet = need("closet.jpg")
    foxy = need("foxy.png")
    if closet is None:
        pass
    else:
        base = cover(closet)
        for dest in ["closet_dark.jpg", "closet_lit.jpg", "closet_here.jpg"]:
            img = base.copy()
            if dest == "closet_here.jpg":
                if foxy is None:
                    pass
                else:
                    # He is pasted *over* the slats rather than behind them.
                    # Behind would be more faithful and would also be
                    # invisible: the slats are dark, and a figure you cannot
                    # make out is the same frame as an empty closet.
                    img = paste_figure(img, foxy, 0.86, 0.5, 1.0)
            img = bright(img, BRIGHT[dest])
            img.convert("RGB").save(os.path.join(OUT, dest), "JPEG", quality=84,
                                    optimize=True, progressive=True)
            print(f"  {dest:20s} {img.width}x{img.height}")

    # --- the sprites -------------------------------------------------------
    for dest in ["fredbear.png"]:
        src = need(dest)
        if src is None:
            continue
        src.save(os.path.join(OUT, dest), "PNG", optimize=True)
        print(f"  {dest:20s} {src.width}x{src.height} (alpha)")

    # --- the jumpscares ----------------------------------------------------
    for dest in ["scare_bonnie.jpg", "scare_chica.jpg", "scare_foxy.jpg",
                 "scare_freddy.jpg", "scare_fredbear.jpg"]:
        src = need(dest)
        if src is None:
            continue
        img = cover(src, crop=CROPS.get(dest))
        img.convert("RGB").save(os.path.join(OUT, dest), "JPEG", quality=84,
                                optimize=True, progressive=True)
        print(f"  {dest:20s} {img.width}x{img.height}")

    if missing:
        print("could not fetch:", ", ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
