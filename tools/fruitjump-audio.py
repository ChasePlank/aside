#!/usr/bin/env python3
"""
Rebuild the platformer's cues in audio/.

    python3 tools/fruitjump-audio.py

Same reason this file exists as tools/fnaf4-breath.py through tools/fnaf9-audio.py: the asset is not
committed by hand, it is *reproducible*, and the reasoning behind each sound is in here rather than in
whoever's memory made it.

WHY THIS FILE WAS NEEDED AT ALL. The platformer has an audio event system -- twelve `Sfx` values, posted by
the engine, collected in a bounded log -- and, until now, NO FILES. All twelve names were missing. It has had
a way to ask for sound since it was written and nothing to play. (FNAF's and the visual novel's 86 cues were
in audio/ the whole time; `AudioTest` never noticed because it only checks their cue lists.)

WHAT THESE CUES ARE FOR. This is a movement game, so almost every sound is feedback for something the PLAYER
just did, and the job is legibility rather than atmosphere: a player watching their own climber should be able
to tell what happened without looking at the HUD. Three rules follow from that.

  * Jump and land are the same gesture in opposite directions, so they are the same sound in opposite
    directions -- one pitch sweep up, one down. A player learns the pair, not two sounds.
  * Hurt must not be mistakable for anything else. It is the only cue that LASTS: everything the player
    chooses is short and bright, and being hurt is long and low. That is the whole distinction, and it is
    carried by length, not by volume, because volume is the one thing a player cannot judge from a speaker.
  * Rewards rise. Pickup and key are the same shape a fifth apart, so the key reads as "the better one"
    without being a different kind of sound.

The palette is warm and wooden rather than electronic -- a climber on a sunset island, not a machine. Sine
tones and filtered noise, no square waves.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")
RATE = 22050

# name -> (seconds, start Hz, end Hz, decay, level)
# One tone that sweeps. The pair that matters is jump/land: same sweep, opposite sign.
BLIPS = {
    "jump":   (0.16, 380.0, 700.0, 22.0, 0.55),
    "land":   (0.14, 420.0, 180.0, 26.0, 0.60),
    "hurt":   (0.34, 300.0, 110.0, 7.0, 0.80),   # the long low one; see above
    "pickup": (0.20, 880.0, 1320.0, 14.0, 0.55),
    "key":    (0.26, 1046.0, 1568.0, 12.0, 0.55),  # a fifth above pickup
    "death":  (0.95, 280.0, 55.0, 3.0, 0.75),      # the longest, and the end of the run
}

# name -> (seconds, cutoff Hz, decay, body Hz, body level, level)
# A filtered noise burst over a low body. Impact sounds.
THUDS = {
    "stomp":     (0.22, 2400.0, 9.0, 150.0, 0.55, 0.75),
    "door":      (0.40, 3000.0, 12.0, 180.0, 0.50, 0.70),
    "explosion": (0.75, 900.0, 5.0, 70.0, 0.80, 0.95),
}

# name -> (seconds, cutoff Hz, decay, level)
# Noise with a fast decay and no body: movement through air or water, not an impact.
WHOOSHES = {
    "arrow":    (0.20, 1800.0, 16.0, 0.50),
    "hookshot": (0.28, 2600.0, 11.0, 0.60),
    "splash":   (0.45, 1400.0, 7.0, 0.70),
}


def one_pole(x, cutoff):
    a = math.exp(-2.0 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for v in x:
        y = (1.0 - a) * v + a * y
        out.append(y)
    return out


def noise(n, seed):
    rng = random.Random(seed & 0xFFFF)
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def normalise(x):
    peak = max(1e-9, max(abs(v) for v in x))
    return [v / peak for v in x]


def build_blip(name):
    secs, f0, f1, decay, level = BLIPS[name]
    n = int(secs * RATE)
    samples = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = f0 + (f1 - f0) * (t / secs)
        phase += 2 * math.pi * f / RATE
        # A fast attack so the onset is crisp, and the sweep does the rest.
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 900.0))
        samples.append(math.sin(phase) * amp * level)
    return normalise(samples)


def build_thud(name):
    secs, cutoff, decay, body_hz, body_level, level = THUDS[name]
    n = int(secs * RATE)
    hit = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(hit):
        t = i / RATE
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 3000.0))
        phase += 2 * math.pi * body_hz / RATE
        body = math.sin(phase) * math.exp(-t * 20.0) * body_level
        samples.append((v * amp + body) * level)
    return normalise(samples)


def build_whoosh(name):
    secs, cutoff, decay, level = WHOOSHES[name]
    n = int(secs * RATE)
    # The cutoff itself falls over the cue, so the noise darkens as it goes: air moving past and then
    # stopping, rather than a burst of static. One pole per block, cheap and enough.
    raw = noise(n, hash(name))
    samples = []
    block = 256
    for start in range(0, n, block):
        t = start / RATE
        c = cutoff * math.exp(-t * 6.0) + 200.0
        chunk = one_pole(raw[start:start + block], c)
        for j, v in enumerate(chunk):
            tt = (start + j) / RATE
            amp = math.exp(-tt * decay) * (1.0 - math.exp(-tt * 400.0))
            samples.append(v * amp * level)
    return normalise(samples)


def write(name, samples):
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32000)) for v in samples))
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    built = {}
    for name in BLIPS:
        built[name] = build_blip(name)
    for name in THUDS:
        built[name] = build_thud(name)
    for name in WHOOSHES:
        built[name] = build_whoosh(name)

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':16s} {len(samples) / RATE:5.2f}s  {os.path.getsize(path):6d} bytes")

    # The guard at the bottom of every one of these scripts: the names here must be exactly the names the
    # engine can post, or the file is a sound nothing asks for. Read them out of the Java rather than
    # trusting this file's own keys.
    java = open(os.path.join(HERE, "..", "src", "main", "java", "aside", "games", "fruitjump",
                             "engine", "AudioSystem.java")).read()
    # Only the Sfx enum. The first version of this guard took every line shaped like a constant, which swept
    # in the four Music values as "missing" and skipped the last Sfx value (which ends in ';' rather than ',')
    # as "extra" - a guard that reports two problems and both of them are its own parsing.
    block = java.split("enum Sfx {", 1)[1].split("}", 1)[0]
    want = set()
    for line in block.splitlines():
        line = line.strip()
        # NAME("cue"), and nothing else - the enum's own constructor is `Sfx(String name)` and has no quotes.
        if '("' in line:
            want.add(line.split('("', 1)[1].split('")', 1)[0])
    want.discard("")
    got = set(built)
    missing = sorted(want - got)
    extra = sorted(got - want)
    print(f"engine asks for {len(want)} sfx; built {len(got)}")
    if missing:
        print("  MISSING (the engine can post these and there is no file): " + ", ".join(missing))
    if extra:
        print("  EXTRA (a file nothing asks for): " + ", ".join(extra))
    return 1 if missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
