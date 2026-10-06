#!/usr/bin/env python3
"""
Key a generated sprite: flood the background away from the edges, crop to the subject, scale to the sprite
height the rest of the art uses.

    tools/key-sprite.py <in.png> <out.png> [height] [tolerance]

WHY THIS EXISTS. Flux draws a person on a plain light background, which is what the prompt asks for and which is
NOT what the engine draws: a flat PNG lands in the scene as a rectangle with a pale panel behind it, and the whole
character reads as a photograph taped to the room. Every other sprite in this repository is RGBA, cut out and
cropped tight - monty-neutral.png is 468x924, and 924 is the height they all share.

Flood fill from the border rather than a global colour key, because the background is not one colour: it is a
gradient, and the character wears grey. Only pixels CONNECTED to the edge are removed, so grey clothes survive.
"""
import sys
from collections import deque
from PIL import Image

if len(sys.argv) < 3:
    print(__doc__); raise SystemExit(2)
src, dst = sys.argv[1], sys.argv[2]
HEIGHT = int(sys.argv[3]) if len(sys.argv) > 3 else 924
TOL = int(sys.argv[4]) if len(sys.argv) > 4 else 26

im = Image.open(src).convert("RGBA")
w, h = im.size
px = im.load()

# sample the four corners for the background's range, then flood inward
corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
def near_bg(c):
    return any(abs(c[0]-b[0]) <= TOL and abs(c[1]-b[1]) <= TOL and abs(c[2]-b[2]) <= TOL for b in corners)

seen = [[False] * h for _ in range(w)]
q = deque()
for x in range(w):
    for y in (0, h - 1):
        if not seen[x][y] and near_bg(px[x, y]): seen[x][y] = True; q.append((x, y))
for y in range(h):
    for x in (0, w - 1):
        if not seen[x][y] and near_bg(px[x, y]): seen[x][y] = True; q.append((x, y))

removed = 0
while q:
    x, y = q.popleft()
    px[x, y] = (0, 0, 0, 0); removed += 1
    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
        nx, ny = x + dx, y + dy
        if 0 <= nx < w and 0 <= ny < h and not seen[nx][ny] and near_bg(px[nx, ny]):
            seen[nx][ny] = True; q.append((nx, ny))

# DESPECKLE: keep only the largest opaque region. The background is textured, so the flood leaves isolated
# patches that never connected to the edge - and they stretch getbbox() to the whole frame, which defeats the
# crop. The subject is one connected body; everything else is residue.
w, h = im.size
px = im.load()
seen = [[False] * h for _ in range(w)]
regions = []
for x0 in range(w):
    for y0 in range(h):
        if seen[x0][y0] or px[x0, y0][3] == 0: continue
        comp, q = [], deque([(x0, y0)]); seen[x0][y0] = True
        while q:
            x, y = q.popleft(); comp.append((x, y))
            for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and not seen[nx][ny] and px[nx, ny][3] > 0:
                    seen[nx][ny] = True; q.append((nx, ny))
        regions.append(comp)
if regions:
    keep = max(regions, key=len)
    keepset = set(keep)
    dropped = 0
    for comp in regions:
        if comp is keep: continue
        for (x, y) in comp: px[x, y] = (0, 0, 0, 0); dropped += 1
    print(f"  despeckle: {len(regions)} region(s), dropped {dropped} px in {len(regions)-1} of them")

box = im.getbbox()
if box: im = im.crop(box)
scale = HEIGHT / im.size[1]
im = im.resize((max(1, round(im.size[0] * scale)), HEIGHT), Image.LANCZOS)
im.save(dst)
print(f"  {dst.split('/')[-1]}: removed {removed} px ({100*removed/(w*h):.0f}%), cropped to {box}, saved {im.size}")
