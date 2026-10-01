#!/usr/bin/env python3
"""
Build the web copies of the visual novel's art.

WHY THIS EXISTS. aside.engine.WebExport turns a story into one self-contained
HTML file so a phone can play it, and for as long as it has existed it has
carried the words and not the pictures -- "NOT YET RENDERED: art/sprites" was
the note in its own header. The words are the story; the art is the reason it
is a *visual* novel, and the restore library being generated for Chase's own
novel is nothing but art. So the export needed a way to carry images, and this
is the half of it that a Java program should not be doing.

WHY NOT IN JAVA. The export has to inline the art as data URIs to stay one
file, and the art has to be small enough that a phone downloads it without
noticing. The originals are 14 MB of PNG -- 9.4 MB of sprites at 924px tall
and 4.4 MB of backgrounds up to 1920px wide. Inlining that is a 19 MB page.
What is needed is a lossy, alpha-preserving, well-supported format, and Java's
ImageIO cannot write one: no WebP, and PNG is lossless so it saves nothing.
PIL can. So the downscale happens here, once, and its output is committed --
which is also what makes the export reproducible from Java alone, because
SelfTest regenerates the HTML and compares it to the checked-in copy.

WHAT IT WRITES, under art/web/:

  sprites/<name>.webp       RGBA, SPRITE_H tall, quality SPRITE_Q
  backgrounds/<name>.jpg    BG_W x BG_H, cover-cropped, quality BG_Q

SPRITE_H is 660, which is not a guess: it is VnScreen.FIGURE_H, the height a
figure is drawn at on the desktop. The phone draws them smaller, so this is
the desktop size and the browser scales down from it -- sharp on a retina
phone, and never upscaled.

The backgrounds are cover-cropped to 16:9 rather than letterboxed, because
that is what the desktop does (VnScreen.drawCover) and a phone build that
framed the room differently from the desktop build would be a second version
of the story's staging rather than a port of it.

Run from the repository root:

  python3 tools/vn-art.py            # write art/web/
  python3 tools/vn-art.py --check    # fail if art/web/ is stale

It also writes art/web/MANIFEST, a sha256 of every source image it read.
aside.engine.SelfTest reads that and fails if a source has changed since the
build, which is the one kind of staleness Java can still detect on its own.
"""

import argparse
import hashlib
import io
import os
import sys
from PIL import Image

SRC_SPRITES = os.path.join("art", "sprites")
SRC_BACKGROUNDS = os.path.join("art", "backgrounds")
OUT = os.path.join("art", "web")

SPRITE_H = 660          # = VnScreen.FIGURE_H
SPRITE_Q = 72
BG_W, BG_H = 1024, 576  # 16:9
BG_Q = 68


def cover(im, w, h):
    """Scale to fill w x h and crop the overflow, centred -- VnScreen.drawCover."""
    sw, sh = im.size
    scale = max(w / sw, h / sh)
    nw, nh = max(1, round(sw * scale)), max(1, round(sh * scale))
    im = im.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - w) // 2, (nh - h) // 2
    return im.crop((left, top, left + w, top + h))


def fit_height(im, h):
    sw, sh = im.size
    return im.resize((max(1, round(sw * h / sh)), h), Image.LANCZOS)


def build():
    """Return {relative path: bytes} for everything art/web/ should hold."""
    out = {}
    sources = []

    for name in sorted(os.listdir(SRC_SPRITES)):
        src = os.path.join(SRC_SPRITES, name)
        if not os.path.isfile(src):
            continue
        stem = os.path.splitext(name)[0]
        sources.append(src)
        im = Image.open(src).convert("RGBA")
        im = fit_height(im, SPRITE_H)
        buf = io.BytesIO()
        im.save(buf, "WEBP", quality=SPRITE_Q, method=6, exact=True)
        out["sprites/%s.webp" % stem] = buf.getvalue()

    for name in sorted(os.listdir(SRC_BACKGROUNDS)):
        src = os.path.join(SRC_BACKGROUNDS, name)
        if not os.path.isfile(src):
            continue
        stem = os.path.splitext(name)[0]
        sources.append(src)
        im = Image.open(src).convert("RGB")
        im = cover(im, BG_W, BG_H)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=BG_Q, optimize=True, progressive=True)
        out["backgrounds/%s.jpg" % stem] = buf.getvalue()

    # The manifest is the one thing here that is not an image, and it is the
    # thing that makes staleness checkable. art/web/ is derived, and a derived
    # directory that nobody re-derives is a second copy of the art quietly
    # disagreeing with the first -- the same failure the phone builds are
    # guarded against, one level up. Java cannot re-run this pipeline, so it
    # cannot tell whether art/web/ is current; what it CAN do is hash the
    # sources and compare them to the hashes recorded here at build time. Edit
    # a sprite and the check fails until this tool is run again.
    lines = []
    for src in sorted(sources):
        with open(src, "rb") as fh:
            lines.append("%s  %s" % (hashlib.sha256(fh.read()).hexdigest(), src.replace(os.sep, "/")))
    out["MANIFEST"] = ("\n".join(lines) + "\n").encode("utf-8")

    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="do not write; fail if art/web/ differs from what this would build")
    args = ap.parse_args()

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
            print("stale: art/web/%s" % rel)
        for rel in extra:
            print("not built by this tool: art/web/%s" % rel)
        if stale or extra:
            print("\nart/web/ is out of date -- run: python3 tools/vn-art.py")
            return 1
        print("art/web/ is current: %d files, %d KB" % (len(built), total // 1024))
        return 0

    for rel, data in sorted(built.items()):
        path = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)

    for rel in extra:
        os.remove(os.path.join(OUT, rel))

    print("wrote art/web/: %d files, %d KB" % (len(built), total // 1024))
    for rel, data in sorted(built.items()):
        print("  %-34s %5d KB" % (rel, len(data) // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
