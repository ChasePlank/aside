#!/usr/bin/env python3
"""
Rebuild the FNAF 6 cues in audio/.

    python3 tools/fnaf6-audio.py

Same reason this file exists as tools/fnaf4-breath.py and
tools/fnaf5-audio.py: the asset is not committed by hand, it is
*reproducible*, and there is no wiki page with a chair creaking on it.

It has to exist, and the argument is sharper here than it was for FNAF 5.
FNAF 6's whole information model is one distinction:

    a drag   the thing in the chair moved. Free information, and the only
             free information in the building.
    a creak  the building settled. Nothing behind it at all.

Those two are the audio channel, and before this file existed both fell
back to `footstep` and `pot_clank` -- which happen to be distinguishable,
so the game worked, but by accident. A cue that is *accidentally*
different from another cue is a cue that a later edit can accidentally
make the same, and the night would go on working while quietly becoming a
coin flip. `GameScreen.play` still has the fallbacks, and should: a cue
that resolves to nothing is worse than a cue that resolves to the wrong
file. But the fallbacks are a safety net, not the design.

The character of each one is three numbers: how dark the filter is, how
fast it decays, and how much low end is under it.

    drag    a chair leg on concrete. Bright scrape, long tail, and a thud
            underneath it that is the weight arriving.
    creak   the building. Low, slow, and it bends in pitch -- wood does
            not snap, it complains.
    lamp    a switch, and a filament coming up. The click is the whole
            sound; the ping is what makes it a *lamp* and not a button.
    shock   an arc. `shock_hit` lands on metal and rings; `shock_miss`
            arcs into the chair and does not.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, decay, thud Hz, thud level, level)
#
#   cutoff  how bright the scrape is. A drag is a hard thing on a hard
#           floor, so it is much brighter than a footfall.
#   decay   how fast it goes. A drag is long; a step is over at once.
#   thud    the weight arriving underneath the scrape, or 0.
DRAGS = {
    "drag": (0.62, 2600.0, 5.5, 92.0, 0.55, 0.70),
}

# name -> (seconds, cutoff Hz, bend Hz, level)
#
# A creak is a slow pitch bend rather than a hit, and it is deliberately
# *lower* than a drag: the two have to be told apart by ear, and the
# easiest way to make two noises different is to put them in different
# octaves.
CREAKS = {
    "creak_a": (1.10, 420.0, 0.55, 0.55),
    "creak_b": (0.85, 520.0, 0.72, 0.48),
}

# name -> (seconds, click cutoff, ping Hz, ping level, level)
LAMPS = {
    "lamp_on":  (0.30, 5200.0, 1180.0, 0.30, 0.55),
    "lamp_off": (0.14, 4200.0, 0.0, 0.0, 0.50),
}

# name -> (seconds, f0, f1, noise level, tone level, ring Hz, ring level)
#
# The shock is one arc and it either lands on something or it does not.
# `shock_hit` is the same discharge with a metal ring on top of it, which
# is the sound of the current finding the frame instead of the chair.
SHOCKS = {
    "shock_hit":  (0.75, 1500.0, 240.0, 0.55, 0.45, 640.0, 0.35),
    "shock_miss": (0.45, 1100.0, 300.0, 0.60, 0.30, 0.0, 0.0),
}

# name -> (seconds, cutoff Hz, rise, level)
LUNGES = {
    "lunge": (0.90, 3000.0, 14.0, 0.85),
}


def one_pole(x, cutoff):
    """A one-pole low pass. The whole timbre of a cue is this line."""
    a = math.exp(-2.0 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for v in x:
        y = (1.0 - a) * v + a * y
        out.append(y)
    return out


def stable_seed(name):
    """A seed that is the same in every process.

    THIS SEEDED FROM PYTHON'S BUILT-IN stable_seed() OF THE NAME, WHICH IS NOT STABLE. Python salts string hashing per
    process unless PYTHONHASHSEED is set, so the same cue came out with different samples on every run - and the
    committed files could not be reproduced by the script that claims to produce them. The same bug was found and
    fixed in tools/fruitjump-audio.py, where its fix note is longer; it had never been propagated to this family.

    A seed needs exactly one property: the same everywhere and forever. crc32 is a DIGEST of the name rather than a
    hash of it, so it has it.
    """
    import zlib
    return zlib.crc32(name.encode("utf-8"))


def noise(n, seed):
    rng = random.Random(seed & 0xFFFF)
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def normalise(x):
    peak = max(1e-9, max(abs(v) for v in x))
    return [v / peak for v in x]


def build_drag(name):
    secs, cutoff, decay, thud_hz, thud_level, level = DRAGS[name]
    n = int(secs * RATE)
    scrape = normalise(one_pole(noise(n, stable_seed(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(scrape):
        t = i / RATE
        # The scrape is instant on and slow off, which is what a hard edge
        # being pulled across a floor actually does.
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 900.0))
        phase += 2 * math.pi * thud_hz / RATE
        thud = math.sin(phase) * math.exp(-t * 9.0) * thud_level
        samples.append((v * amp + thud) * level)
    return normalise(samples)


def build_creak(name):
    secs, cutoff, bend, level = CREAKS[name]
    n = int(secs * RATE)
    body = normalise(one_pole(noise(n, stable_seed(name)), cutoff))
    samples = []
    for i, v in enumerate(body):
        t = i / RATE
        k = i / max(1, n - 1)
        # A slow in and a slow out, with the level wandering: wood does not
        # snap, it complains, and a complaint has a shape.
        amp = (1.0 - math.exp(-t * 5.0)) * math.exp(-t * 1.6)
        wander = 1.0 - bend * k * (1.0 - k) * 2.0
        samples.append(v * amp * wander * level)
    return normalise(samples)


def build_lamp(name):
    secs, cutoff, ping_hz, ping_level, level = LAMPS[name]
    n = int(secs * RATE)
    click = normalise(one_pole(noise(n, stable_seed(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(click):
        t = i / RATE
        amp = math.exp(-t * 90.0)
        out = v * amp
        if ping_hz > 0:
            phase += 2 * math.pi * ping_hz / RATE
            out += math.sin(phase) * math.exp(-t * 22.0) * ping_level
        samples.append(out * level)
    return normalise(samples)


def build_shock(name):
    secs, f0, f1, noise_level, tone_level, ring_hz, ring_level = SHOCKS[name]
    n = int(secs * RATE)
    hiss = noise(n, stable_seed(name))
    samples = []
    phase = 0.0
    ring = 0.0
    for i in range(n):
        t = i / RATE
        k = i / max(1, n - 1)
        freq = f0 + (f1 - f0) * k
        phase += 2 * math.pi * freq / RATE
        # Instant attack, exponential decay: the arc is over almost as soon
        # as it starts, and the tail is the room answering.
        amp = math.exp(-t * 7.0) * (1.0 - math.exp(-t * 1200.0))
        out = (hiss[i] * noise_level + math.sin(phase) * tone_level) * amp
        if ring_hz > 0:
            ring += 2 * math.pi * ring_hz / RATE
            # The ring outlives the arc, which is the whole difference
            # between hitting the frame and hitting the chair.
            out += math.sin(ring) * math.exp(-t * 4.0) * ring_level
        samples.append(out)
    return normalise(samples)


def build_lunge(name):
    secs, cutoff, rise, level = LUNGES[name]
    n = int(secs * RATE)
    body = normalise(one_pole(noise(n, stable_seed(name)), cutoff))
    samples = []
    for i, v in enumerate(body):
        t = i / RATE
        # The only cue in the game that gets louder as it goes. Everything
        # else is a thing that happened; this is a thing arriving.
        amp = (1.0 - math.exp(-t * rise)) * min(1.0, t * 2.2)
        samples.append(v * amp * level)
    return normalise(samples)


def write(name, samples):
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32000)) for v in samples)
        w.writeframes(frames)
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    built = {}
    for name in DRAGS:
        built[name] = build_drag(name)
    for name in CREAKS:
        built[name] = build_creak(name)
    for name in LAMPS:
        built[name] = build_lamp(name)
    for name in SHOCKS:
        built[name] = build_shock(name)
    for name in LUNGES:
        built[name] = build_lunge(name)

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':18s} {len(samples) / RATE:5.2f}s  {os.path.getsize(path):7d} bytes")

    # The one thing that must never be true: the drag and the creak are the
    # same sound. If a later edit makes them the same, the night stops
    # being a game and becomes a coin flip, and nothing else would say so.
    if built["drag"] == built["creak_a"]:
        raise SystemExit("drag and creak_a are the same samples -- that is the "
                         "whole information model of the game")


if __name__ == "__main__":
    main()
