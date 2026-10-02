#!/usr/bin/env python3
"""
Build the phone copies of the attraction's art.

    python3 tools/fnaf3-phone-art.py
    python3 tools/fnaf3-phone-art.py --check

WHY THIS EXISTS. The desktop draws an office, ten camera rooms, Springtrap and
six phantoms, at 1.7 MB of JPEG and PNG between them. The shelf inlines every
build it carries as base64, so shipping the desktop's own copies would add
about 2 MB to it. So the phone gets its own copies, built here, and the rule is
the one tools/fnaf4-phone-art.py and tools/fnaf5-phone-art.py follow: the
downscale happens once, in Python, and its output is committed, which is what
makes the Java generator reproducible from Java alone.

WHAT IT WRITES, under art/phone/fnaf3/:

  office.webp          960 wide   the office, with its window and its vent
  room1..10.webp       720 wide   the ten cameras, by House room number
  springtrap.webp      520 tall   RGBA -- he is composited, not framed
  phantom<key>.webp    300 tall   RGBA -- so are the six of them

WHY WEBP AND NOT JPEG. Measured on this art at the same nominal quality, WebP
is about half the size. The shelf inlines every build it carries, so the art is
the shelf's weight, and that is what makes the rest of the art affordable
rather than a decision about megabytes. WebP has been in every browser since
2020, and tools/vn-art.py already writes it for the visual novel's sprites.

THE ROOM NUMBERS ARE THE ENGINE'S. room1 is the Entrance and room10 is the
Vent, which is House.NAME's order and the order the desktop loads them in. A
phone build that numbered them differently would show the player the wrong
camera and look perfectly fine doing it.
"""

import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf3", "images")
OUT = os.path.join("art", "phone", "fnaf3")

QUALITY = 78
OFFICE_W = 960
ROOM_W = 720
SPRINGTRAP_H = 520
PHANTOM_H = 300

PHANTOMS = ["freddy", "chica", "foxy", "mangle", "puppet", "bb"]


def jobs():
    out = [("office.jpg", "office.webp", OFFICE_W, None)]
    for r in range(1, 11):
        out.append(("room" + str(r) + ".jpg", "room" + str(r) + ".webp", ROOM_W, None))
    out.append(("springtrap.png", "springtrap.webp", None, SPRINGTRAP_H))
    for k in PHANTOMS:
        out.append(("phantom" + k + ".png", "phantom" + k + ".webp", None, PHANTOM_H))
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
    print("art/phone/fnaf3 is " + str(total // 1024) + " KB")
    return 0


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
