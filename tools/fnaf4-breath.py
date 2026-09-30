#!/usr/bin/env python3
"""
Rebuild the FNAF 4 breath cues in audio/.

    python3 tools/fnaf4-breath.py

Same reason this file exists as tools/fnaf4-art.py: the asset is not
committed by hand, it is *reproducible*. The difference is that this one
is not a download -- there is no wiki page with four animatronics
breathing on it, so the breath is synthesised here.

It has to exist, because FNAF 4 is the one in the series with no cameras.
You cannot see the room; the only thing that tells you something is
standing in a doorway is the sound it makes, and the four of them have to
sound different or the cue carries no information. A game whose whole
premise is listening cannot ship with one generic thud.

Each cue is filtered noise under an envelope, with a slow amplitude pulse
for the breathing and a fast one for anything that rattles. The character
is entirely in three numbers: how dark the filter is, how fast the pulse
is, and how long it lasts.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz -- small, and
decodable everywhere the engine runs.
"""
import math, os, random, struct, sys, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, pulse Hz, pulse depth, rattle Hz, level)
#
#   cutoff   how dark it is. Low is a big chest; high is a hiss.
#   pulse    the breathing itself, in and out.
#   depth    how much the pulse moves the level. 0 is a steady hiss.
#   rattle   a fast flutter on top, for anything with teeth.
CUES = {
    "breath_bonnie":  (1.60,  700.0, 2.6, 0.85,  0.0, 0.55),
    "breath_chica":   (1.30, 1800.0, 3.4, 0.55, 14.0, 0.50),
    "breath_foxy":    (1.15, 3000.0, 4.2, 0.40,  0.0, 0.45),
    "breath_freddy":  (1.80,  480.0, 2.0, 0.90,  0.0, 0.60),
    # Fredbear does not breathe so much as wait. Slower, lower, and long.
    "breath_fredbear": (2.20, 900.0, 1.4, 0.95,  0.0, 0.65),
}


def one_pole(x, cutoff):
    """A one-pole low pass. The whole timbre of the cue is this line."""
    a = math.exp(-2.0 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for v in x:
        y = (1.0 - a) * v + a * y
        out.append(y)
    return out


def envelope(i, n, rate):
    """
    In, hold, out -- and the out is long, because a breath that stops
    dead sounds like a file ending rather than like something exhaling.
    """
    t = i / rate
    total = n / rate
    attack = 0.18
    release = 0.45
    if t < attack:
        return t / attack
    if t > total - release:
        return max(0.0, (total - t) / release)
    return 1.0


def build(name):
    secs, cutoff, pulse, depth, rattle, level = CUES[name]
    n = int(secs * RATE)
    rng = random.Random(hash(name) & 0xFFFF)
    noise = [rng.uniform(-1.0, 1.0) for _ in range(n)]
    filtered = one_pole(noise, cutoff)

    # Normalise, so the level table above is the only thing that decides
    # how loud a cue is.
    peak = max(1e-9, max(abs(v) for v in filtered))
    samples = []
    for i, v in enumerate(filtered):
        t = i / RATE
        amp = envelope(i, n, RATE)
        breathe = 1.0 - depth + depth * (0.5 + 0.5 * math.sin(2 * math.pi * pulse * t))
        flutter = 1.0 if rattle <= 0 else 0.75 + 0.25 * math.sin(2 * math.pi * rattle * t)
        s = (v / peak) * amp * breathe * flutter * level
        samples.append(int(max(-1.0, min(1.0, s)) * 32767))
    return samples


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in CUES:
        path = os.path.join(OUT, name + ".wav")
        samples = build(name)
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", s) for s in samples))
        print(f"  {name + '.wav':24s} {len(samples) / RATE:.2f}s  "
              f"{os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
