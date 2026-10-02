#!/usr/bin/env python3
"""
Build the phone copies of the rental's art.

    python3 tools/fnaf5-phone-art.py
    python3 tools/fnaf5-phone-art.py --check

WHY THIS EXISTS. The desktop draws five room photographs and three "it is in
the room with you" frames at 1280x720, 680 KB of JPEG between them. The shelf
inlines every build it carries as base64, so shipping the desktop's own copies
would add about 900 KB to a page that is already 3 MB. So the phone gets its
own copies, built here, and the rule is the one tools/fnaf6-phone-art.py and
tools/vn-art.py follow: the downscale happens once, in Python, and its output
is committed, which is what makes the Java generator reproducible from Java
alone -- SelfTest regenerates web/fnaf5.html and compares it to the checked-in
copy, and that only works if the bytes it inlines are already on disk and do
not move.

WHAT IT WRITES, under art/phone/fnaf5/:

  room0.webp .. room4.webp 960x540   the five rooms, in Room.Where order
  here_<key>.webp          960x540   the thing standing in the room with you
  scare_<key>.webp         960x540   its face, filling the screen

WHY WEBP AND NOT JPEG. Measured on this art at the same nominal quality,
WebP is about half the size -- 352 KB of JPEG against 172 KB of WebP for the
eleven frames here, and 64% off on FNAF 4's eighteen. The shelf inlines every
build it carries, so the art is the shelf's weight, and halving it is what
makes the rest of the art affordable rather than a decision about megabytes.
WebP has been in every browser since 2020, and tools/vn-art.py already writes
it for the visual novel's sprites.

THE ORDER IS THE ENGINE'S. room0 is PARTS/SERVICE and room4 is the FUNTIME
AUDITORIUM, which is Room.Where's declaration order and the order the desktop
loads them in. A phone build that numbered them differently would put the
player in the wrong room and look perfectly fine doing it.
"""

import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf5", "images")
OUT = os.path.join("art", "phone", "fnaf5")

W, H = 960, 540
QUALITY = 78

ROOMS = ["room0", "room1", "room2", "room3", "room4"]
KEYS = ["ballora", "foxy", "freddy"]


def jobs():
    out = []
    for name in ROOMS:
        out.append((name + ".jpg", name + ".webp"))
    for key in KEYS:
        out.append(("here_" + key + ".jpg", "here_" + key + ".webp"))
    for key in KEYS:
        out.append(("scare_" + key + ".jpg", "scare_" + key + ".webp"))
    return out


def build(check):
    os.makedirs(OUT, exist_ok=True)
    changed = []
    for src_name, out_name in jobs():
        src = os.path.join(SRC, src_name)
        dst = os.path.join(OUT, out_name)
        if not os.path.exists(src):
            print("missing source: " + src)
            return 1
        im = Image.open(src).convert("RGB")
        if im.size != (W, H):
            im = im.resize((W, H), Image.LANCZOS)
        tmp = dst + ".tmp"
        im.save(tmp, "WEBP", quality=QUALITY, method=6)
        new = open(tmp, "rb").read()
        os.remove(tmp)
        old = open(dst, "rb").read() if os.path.exists(dst) else None
        if old != new:
            changed.append(out_name)
            if not check:
                open(dst, "wb").write(new)
    if check:
        if changed:
            print("STALE: " + ", ".join(changed))
            return 1
        print("the phone art is current")
        return 0
    if changed:
        print("wrote " + str(len(changed)) + " of " + str(len(jobs())) + " files")
    else:
        print("nothing changed")
    total = sum(os.path.getsize(os.path.join(OUT, n)) for _, n in jobs())
    print("art/phone/fnaf5 is " + str(total // 1024) + " KB")
    return 0


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
