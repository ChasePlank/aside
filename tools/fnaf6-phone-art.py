#!/usr/bin/env python3
"""
Build the phone copies of the salvage bay's art.

    python3 tools/fnaf6-phone-art.py
    python3 tools/fnaf6-phone-art.py --check

WHY THIS EXISTS. FNAF 1 through 8 are desktop-only, and the reason is not
that they are real-time -- Night Shift is real-time and it has been on the
phone shelf since the shelf existed. The reason is that they are JavaFX
canvas games with 2.7 MB of PNG in them, and the shelf inlines every build
it carries as base64. Inlining the bay at its desktop size is a 3.6 MB
page on top of a 2 MB shelf.

So the phone gets its own copies, built here, and the rule is the same one
tools/vn-art.py follows: the downscale happens once, in Python, and its
output is committed, which is what makes the Java generator reproducible
from Java alone -- SelfTest regenerates web/fnaf6.html and compares it to
the checked-in copy, and that only works if the bytes it inlines are
already on disk and do not move.

WHY NOT IN JAVA. Same reason as the visual novel: the export has to inline
the art as data URIs to stay one file, and Java's ImageIO cannot write
WebP. PNG is lossless, so it saves nothing on a 900px render with alpha,
and the units are exactly that. PIL can write WebP with alpha, so the
figures stay figures instead of turning into 700 KB of PNG each.

WHAT IT WRITES, under art/phone/fnaf6/:

  room.jpg            960x540   the bay, empty, in the dark
  desk.webp           960x119   the desk alone, RGBA
  unit_<key>.webp     520 tall  the thing in the chair, RGBA
  scare_<key>.jpg     800x450   its face, filling the screen

THE SIZES ARE NOT ROUND NUMBERS PICKED FOR TIDINESS.

  * room and desk are the desktop's own 0.75 scale. The desktop draws the
    1280x720 room into a 1280x526 rect and the desk at its native 1280x158,
    and the phone draws both into the same fractions of the same frame, so
    the two builds frame the room identically and neither is a re-crop.
  * units are 520 tall, which is above the largest they are ever drawn at
    (470px at pose 5, times the phone's zoom) so the browser only ever
    scales them down. A unit upscaled from a phone-sized copy is a unit
    with a soft edge, and the whole read of the night is its edge.
  * the scares are the one place the phone is allowed to be worse than the
    desktop, because a jumpscare is on screen for a second and a half and
    is the largest thing on it.

It also writes art/phone/fnaf6/MANIFEST, a sha256 of every source image it
read. aside.engine.SelfTest reads that and fails if a source has changed
since the build, which is the one kind of staleness Java can still detect
on its own.
"""

import argparse
import hashlib
import io
import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf6", "images")
OUT = os.path.join("art", "phone", "fnaf6")

# The desktop's own scale for the room and the desk.
ROOM_W, ROOM_H = 960, 540
ROOM_Q = 76
DESK_W = 960

# Taller than the 470px the desktop ever draws a unit at, so the browser
# only scales down.
UNIT_H = 520
UNIT_Q = 78

SCARE_W, SCARE_H = 800, 450
SCARE_Q = 70

UNITS = ["scraptrap", "scrapbaby", "moltenfreddy", "lefty"]


def fit_height(im, h):
    sw, sh = im.size
    return im.resize((max(1, round(sw * h / sh)), h), Image.LANCZOS)


def fit_width(im, w):
    sw, sh = im.size
    return im.resize((w, max(1, round(sh * w / sw))), Image.LANCZOS)


def build():
    """Return {relative path: bytes} for everything art/phone/fnaf6/ holds."""
    out = {}
    sources = []

    def src(name):
        path = os.path.join(SRC, name)
        sources.append(path)
        return path

    # --- the room ---------------------------------------------------------
    im = Image.open(src("room.jpg")).convert("RGB")
    im = fit_width(im, ROOM_W)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=ROOM_Q, optimize=True, progressive=True)
    out["room.jpg"] = buf.getvalue()

    # --- the desk ---------------------------------------------------------
    # Alpha kept, so it stays a cut-out: the unit is drawn behind it and the
    # amount of the unit above the desk line is the only thing the player
    # has to read at a glance.
    im = Image.open(src("desk.png")).convert("RGBA")
    im = fit_width(im, DESK_W)
    buf = io.BytesIO()
    im.save(buf, "WEBP", quality=UNIT_Q, method=6, exact=True)
    out["desk.webp"] = buf.getvalue()

    # --- the units --------------------------------------------------------
    for key in UNITS:
        im = Image.open(src("unit_%s.png" % key)).convert("RGBA")
        im = fit_height(im, UNIT_H)
        buf = io.BytesIO()
        im.save(buf, "WEBP", quality=UNIT_Q, method=6, exact=True)
        out["unit_%s.webp" % key] = buf.getvalue()

    # --- the scares -------------------------------------------------------
    for key in UNITS:
        im = Image.open(src("scare_%s.jpg" % key)).convert("RGB")
        im = fit_width(im, SCARE_W)
        if im.height > SCARE_H:
            top = (im.height - SCARE_H) // 2
            im = im.crop((0, top, im.width, top + SCARE_H))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=SCARE_Q, optimize=True, progressive=True)
        out["scare_%s.jpg" % key] = buf.getvalue()

    # The manifest is the one thing here that is not an image, and it is
    # what makes staleness checkable. art/phone/fnaf6/ is derived, and a
    # derived directory nobody re-derives is a second copy of the art
    # quietly disagreeing with the first. Java cannot re-run this pipeline,
    # so it cannot tell whether the directory is current; what it CAN do is
    # hash the sources and compare them to the hashes recorded here.
    lines = []
    for path in sorted(sources):
        with open(path, "rb") as fh:
            lines.append("%s  %s" % (hashlib.sha256(fh.read()).hexdigest(),
                                     path.replace(os.sep, "/")))
    out["MANIFEST"] = ("\n".join(lines) + "\n").encode("utf-8")

    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="do not write; fail if art/phone/fnaf6/ differs from "
                         "what this would build")
    args = ap.parse_args()

    if not os.path.isdir(SRC):
        print("no %s -- run this from the repository root" % SRC)
        return 2

    built = build()
    total = sum(len(v) for v in built.values())

    stale = []
    for rel, data in sorted(built.items()):
        path = os.path.join(OUT, rel)
        if not os.path.exists(path) or open(path, "rb").read() != data:
            stale.append(rel)

    extra = []
    for root, _dirs, files in os.walk(OUT):
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), OUT).replace(os.sep, "/")
            if rel not in built:
                extra.append(rel)

    if args.check:
        for rel in stale:
            print("stale: art/phone/fnaf6/%s" % rel)
        for rel in extra:
            print("not built by this tool: art/phone/fnaf6/%s" % rel)
        if stale or extra:
            print("\nart/phone/fnaf6/ is out of date -- run: "
                  "python3 tools/fnaf6-phone-art.py")
            return 1
        print("art/phone/fnaf6/ is current: %d files, %d KB" % (len(built), total // 1024))
        return 0

    for rel, data in sorted(built.items()):
        path = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)

    for rel in extra:
        os.remove(os.path.join(OUT, rel))

    print("wrote art/phone/fnaf6/: %d files, %d KB" % (len(built), total // 1024))
    for rel, data in sorted(built.items()):
        print("  %-30s %5d KB" % (rel, len(data) // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
