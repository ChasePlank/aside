#!/usr/bin/env python3
"""
Build the phone copies of the office's art.

    python3 tools/fnaf7-phone-art.py
    python3 tools/fnaf7-phone-art.py --check

WHY THIS EXISTS. The desktop draws an office, five units at 780 px tall with
alpha and five scares, at 2.3 MB of JPEG and PNG between them. The shelf
inlines every build it carries as base64, so shipping the desktop's own copies
would add about 3 MB to it. So the phone gets its own copies, built here, and
the rule is the one tools/fnaf3-phone-art.py and tools/fnaf4-phone-art.py
follow: the downscale happens once, in Python, and its output is committed,
which is what makes the Java generator reproducible from Java alone.

WHAT IT WRITES, under art/phone/fnaf7/:

  room.webp            960x540   the office, with its two doorways
  unit_<key>.webp      520 tall  RGBA -- composited, not framed
  scare_<key>.webp     960x540   its face, filling the screen

WHY WEBP AND NOT JPEG. Measured on this art at the same nominal quality, WebP
is about half the size. The shelf inlines every build it carries, so the art is
the shelf's weight, and that is what makes the rest of the art affordable
rather than a decision about megabytes. WebP has been in every browser since
2020, and tools/vn-art.py already writes it for the visual novel's sprites.

THE UNIT KEYS ARE THE ENGINE'S. baby, ennard, glitchtrap, mimic, vanny -- the
five in Unit.all()'s order, and the page looks a unit up by the key the engine
gives it rather than by the night number, so a night cannot show the wrong one.
"""

import os
import sys

from PIL import Image

SRC = os.path.join("src", "main", "resources", "fnaf7", "images")
OUT = os.path.join("art", "phone", "fnaf7")

W, H = 960, 540
QUALITY = 78
UNIT_H = 520

KEYS = ["baby", "ennard", "glitchtrap", "mimic", "vanny"]


def jobs():
    out = [("room.jpg", "room.webp")]
    for k in KEYS:
        out.append(("unit_" + k + ".png", "unit_" + k + ".webp"))
    for k in KEYS:
        out.append(("scare_" + k + ".jpg", "scare_" + k + ".webp"))
    return out


def build(check):
    os.makedirs(OUT, exist_ok=True)
    changed = []
    all_jobs = jobs()
    for src_name, dst_name in all_jobs:
        src = os.path.join(SRC, src_name)
        dst = os.path.join(OUT, dst_name)
        if not os.path.exists(src):
            print("missing source: " + src)
            return 1
        im = Image.open(src)
        if src_name.endswith(".png"):
            im = im.convert("RGBA")
            h = UNIT_H
            w = max(1, round(im.width * h / im.height))
            im = im.resize((w, h), Image.LANCZOS)
        else:
            im = im.convert("RGB")
            if im.size != (W, H):
                im = im.resize((W, H), Image.LANCZOS)
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
    total = sum(os.path.getsize(os.path.join(OUT, n)) for _, n in all_jobs)
    print("art/phone/fnaf7 is " + str(total // 1024) + " KB")
    return 0


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
