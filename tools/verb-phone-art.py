#!/usr/bin/env python3
"""Build the phone builds' room art.

The desktop reads a game's room as a PNG out of src/main/resources/<game>/.
A phone build is one file with no side files, so the room has to be inlined --
and a 1 MB PNG base64s to 1.3 MB, which is most of a shelf. WebP at the phone's
own size costs about a tenth of that and is what the FNAF phone builds use.

Writes art/phone/<game>/room.webp for every <game> that has a room.png.
Run from the repository root.  Usage: tools/verb-phone-art.py [game ...]
"""
import os, sys
from PIL import Image

SRC = os.path.join("src", "main", "resources")
OUT = os.path.join("art", "phone")
W, H, Q = 1280, 720, 72

def main():
    want = sys.argv[1:]
    games = sorted(d for d in os.listdir(SRC)
                   if os.path.isfile(os.path.join(SRC, d, "room.png")))
    if want:
        games = [g for g in games if g in want]
    if not games:
        print("no game has a room.png")
        return
    total = 0
    for g in games:
        src = os.path.join(SRC, g, "room.png")
        dst = os.path.join(OUT, g, "room.webp")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        im = Image.open(src).convert("RGB")
        if im.size != (W, H):
            im = im.resize((W, H), Image.LANCZOS)
        im.save(dst, "WEBP", quality=Q, method=6)
        a, b = os.path.getsize(src), os.path.getsize(dst)
        total += b
        print(f"{g:14s} {a//1024:>5} KB -> {b//1024:>4} KB")
    print(f"{'total':14s} {'':>5}    {total//1024:>4} KB")

main()
