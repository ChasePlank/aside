#!/usr/bin/env python3
"""
Build the phone copies of the bedroom's art.

    python3 tools/fnaf4-phone-art.py
    python3 tools/fnaf4-phone-art.py --check

WHY THIS EXISTS. The desktop draws four stations, each in three states -- dark,
lit, and something standing in it -- plus Fredbear and five scares, at
1280x720, 1.5 MB of JPEG and PNG between them. The shelf inlines every build
it carries as base64, so shipping the desktop's own copies would add about
2 MB to it. So the phone gets its own copies, built here, and the rule is the
one tools/fnaf5-phone-art.py and tools/fnaf6-phone-art.py follow: the
downscale happens once, in Python, and its output is committed, which is what
makes the Java generator reproducible from Java alone.

WHAT IT WRITES, under art/phone/fnaf4/:

  <station>_dark.webp   960x540   the station with nothing in it and no light
  <station>_lit.webp    960x540   the same, with the flashlight on it
  <station>_here.webp   960x540   something standing in it
  fredbear.webp         520 tall  Fredbear, RGBA -- he is composited, not framed
  scare_<key>.webp      960x540   its face, filling the screen

WHY WEBP AND NOT JPEG. Measured on this art at the same nominal quality, WebP
is 64% smaller -- 960 KB of JPEG against 346 KB of WebP for the eighteen
frames here. The shelf inlines every build it carries, so the art is the
shelf's weight, and this is what makes the rest of the art affordable rather
than a decision about megabytes. WebP has been in every browser since 2020,
and tools/vn-art.py already writes it for the visual novel's sprites.

THE THREE STATES ARE THE GAME. FNAF 4's whole mechanic is that the light
reaches one move out and that a station you have not lit is a station you are
guessing about, so the difference between the dark frame and the lit one is
not decoration -- it is the answer to the question the player is asking. The
phone draws the station you are standing at, so the frame it shows is the
state of the thing in front of you.

STATIONS ARE THE ENGINE'S NAMES, lowercased for the filenames and uppercased
again by the generator, which keys the map by the name the page looks up.
"""

import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf4", "images")
OUT = os.path.join("art", "phone", "fnaf4")

W, H = 960, 540
QUALITY = 78
FREDBEAR_H = 520

STATIONS = ["bed", "left", "right", "closet"]
STATES = ["dark", "lit", "here"]
SCARES = ["bonnie", "chica", "foxy", "fredbear", "freddy"]


def jobs():
    out = []
    for s in STATIONS:
        for st in STATES:
            out.append((s + "_" + st + ".jpg", s + "_" + st + ".webp"))
    out.append(("fredbear.png", "fredbear.webp"))
    for k in SCARES:
        out.append(("scare_" + k + ".jpg", "scare_" + k + ".webp"))
    return out


def convert(src_name, dst_name):
    src = os.path.join(SRC, src_name)
    im = Image.open(src)
    if dst_name.endswith(".webp"):
        im = im.convert("RGBA")
        h = FREDBEAR_H
        w = max(1, round(im.width * h / im.height))
        im = im.resize((w, h), Image.LANCZOS)
        return im, "WEBP", {"quality": QUALITY, "method": 6}
    im = im.convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return im, "WEBP", {"quality": QUALITY, "method": 6}


def build(check):
    os.makedirs(OUT, exist_ok=True)
    changed = []
    for src_name, dst_name in jobs():
        src = os.path.join(SRC, src_name)
        dst = os.path.join(OUT, dst_name)
        if not os.path.exists(src):
            print("missing source: " + src)
            return 1
        im, fmt, opts = convert(src_name, dst_name)
        tmp = dst + ".tmp"
        im.save(tmp, fmt, **opts)
        new = open(tmp, "rb").read()
        os.remove(tmp)
        old = open(dst, "rb").read() if os.path.exists(dst) else None
        if old != new:
            changed.append(dst_name)
            if not check:
                open(dst, "wb").write(new)
    if check:
        if changed:
            print("STALE: " + ", ".join(changed))
            return 1
        print("the phone art is current")
        return 0
    print("wrote " + str(len(changed)) + " of " + str(len(jobs())) + " files")
    total = sum(os.path.getsize(os.path.join(OUT, n)) for _, n in jobs())
    print("art/phone/fnaf4 is " + str(total // 1024) + " KB")
    return 0


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
