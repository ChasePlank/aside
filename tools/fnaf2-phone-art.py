#!/usr/bin/env python3
"""
Build the phone copies of the pizzeria's art.

    python3 tools/fnaf2-phone-art.py
    python3 tools/fnaf2-phone-art.py --check

WHY THIS EXISTS. FNAF 2 has the most art of the eight FNAF games: four office
views, eleven cameras, eight units at up to 1280 px tall with alpha, the Freddy
mask at 1 MB on its own, and six jumpscares -- 5.3 MB of source. The shelf
inlines every build it carries as base64, so shipping the desktop's own copies
would add about 7 MB to it. So the phone gets its own copies, built here, and
the rule is the one tools/fnaf8-phone-art.py and the rest follow: the downscale
happens once, in Python, and its output is committed, which is what makes the
Java generator reproducible from Java alone.

WHAT IT WRITES, under art/phone/fnaf2/:

  office.webp           960 wide   the office, dark
  office.hall.webp      960 wide   the same, with the hall light on
  office.ventL.webp     960 wide   with the left vent light on
  office.ventR.webp     960 wide   with the right vent light on
  room1..11.webp        720 wide   the eleven cameras
  unit_<key>.webp       520 tall   RGBA -- composited, not framed
  mask.webp             960x540    RGBA -- the mask, worn
  scare_<key>.webp      960x540    its face, filling the screen

WHY WEBP AND NOT JPEG. Measured on this art at the same nominal quality, WebP
is about half the size. The shelf inlines every build it carries, so the art is
the shelf's weight, and that is what makes this affordable rather than a
decision about megabytes. WebP has been in every browser since 2020.

THE KEYS ARE THE FILENAMES, NOT THE NAMES. The engine calls one of them "The
Puppet" and the file is puppet.png; another is "Balloon Boy" and the file is
balloonboy.png. So the generator carries an explicit key per unit rather than
deriving one from the name, because deriving it would look right for seven of
the eight and be wrong for the eighth.

TWO UNITS HAVE NO JUMPSCARE. There is no js.witheredbonnie and no
js.toybonnie in the source, so the page falls back to one of the six that
exist -- which is what the desktop does too.
"""

import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf2", "images")
OUT = os.path.join("art", "phone", "fnaf2")

QUALITY = 78
OFFICE_W = 960
ROOM_W = 720
UNIT_H = 520

OFFICES = ["office.jpg", "office.hall.jpg", "office.ventL.jpg", "office.ventR.jpg"]
UNITS = ["toyfreddy", "toybonnie", "toychica", "mangle",
         "witheredbonnie", "witheredfoxy", "balloonboy", "puppet"]
SCARES = ["toyfreddy", "toychica", "mangle", "witheredfoxy", "balloonboy", "puppet"]


def jobs():
    out = []
    for name in OFFICES:
        out.append((name, name.replace(".jpg", ".webp"), OFFICE_W, None))
    for r in range(1, 12):
        out.append(("room" + str(r) + ".jpg", "room" + str(r) + ".webp", ROOM_W, None))
    for k in UNITS:
        out.append((k + ".png", "unit_" + k + ".webp", None, UNIT_H))
    out.append(("mask.png", "mask.webp", OFFICE_W, None))
    for k in SCARES:
        out.append(("js." + k + ".jpg", "scare_" + k + ".webp", OFFICE_W, None))
    return out


def build(check):
    os.makedirs(OUT, exist_ok=True)
    changed = []
    all_jobs = jobs()
    for src_name, dst_name, width, height in all_jobs:
        src = os.path.join(SRC, src_name)
        dst = os.path.join(OUT, dst_name)
        if not os.path.exists(src):
            print("missing source: " + src)
            return 1
        im = Image.open(src)
        if src_name.endswith(".png"):
            im = im.convert("RGBA")
        else:
            im = im.convert("RGB")
        if height is not None:
            w = max(1, round(im.width * height / im.height))
            im = im.resize((w, height), Image.LANCZOS)
        elif width is not None and im.width != width:
            h = max(1, round(im.height * width / im.width))
            im = im.resize((width, h), Image.LANCZOS)
        tmp = dst + ".tmp"
        im.save(tmp, "WEBP", quality=QUALITY, method=6)
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
    print("wrote " + str(len(changed)) + " of " + str(len(all_jobs)) + " files")
    total = sum(os.path.getsize(os.path.join(OUT, n)) for _, n, _, _ in all_jobs)
    print("art/phone/fnaf2 is " + str(total // 1024) + " KB")
    return 0


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
