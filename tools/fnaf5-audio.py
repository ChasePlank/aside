#!/usr/bin/env python3
"""
Rebuild the FNAF 5 cues in audio/.

    python3 tools/fnaf5-audio.py

Same reason this file exists as tools/fnaf4-breath.py: the asset is not
committed by hand, it is *reproducible*, and there is no wiki page with
three animatronics walking around a rental on it.

It has to exist, and it is the same argument FNAF 4's breath script makes.
FNAF 5's three threats do not differ in speed -- they differ in *what they
follow*, and the three answers contradict each other:

    Ballora        follows sound.      You move, she comes.
    Funtime Foxy   follows the camera. You look, she comes.
    Funtime Freddy follows you.        You wait, he comes.

So the player's whole job is knowing which one of them just walked into
the room, and the only channel that says so is the sound. Before this file
existed all three fell back to the same `at_door` / `footstep` /
`door_close`, which meant the game shipped with one generic thud for three
characters -- and a cue you cannot tell apart from another cue carries no
information at all. `GameScreen.play` still has the fallbacks, and should:
a cue that resolves to nothing is worse than a cue that resolves to the
wrong file. But the fallbacks are a safety net, not the design.

The character of each one is three numbers: how dark the filter is, how
fast it pulses, and how much it rattles.

    Ballora         low, slow, almost musical. She is listening, not
                    hunting, and she is the one you survive by standing
                    still.
    Funtime Foxy    high, quick, with a flutter. She is a performer, and
                    she is the one that punishes looking.
    Funtime Freddy  the lowest and the biggest, with a rattle on top for
                    Bon-Bon. He is the one that never gives up.

`lost_ballora` is the only `lost_` cue the engine can emit -- Ballora is
the only one with a way to lose you -- so it is the only one generated.
A file for a cue that is never fired is dead weight, which this repo has
already had to clean up once.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, pulse Hz, pulse depth, rattle Hz, level)
#
#   cutoff   how dark it is. Low is a big chest; high is a hiss.
#   pulse    the breathing itself, in and out.
#   depth    how much the pulse moves the level. 0 is a steady hiss.
#   rattle   a fast flutter on top, for anything with teeth.
HERES = {
    "here_ballora": (2.00,  520.0, 1.7, 0.92,  0.0, 0.62),
    "here_foxy":    (1.25, 2600.0, 4.6, 0.45, 13.0, 0.50),
    "here_freddy":  (1.80,  380.0, 2.2, 0.95,  7.0, 0.68),
}

# name -> (seconds, cutoff Hz, level). One footfall, one room away.
STEPS = {
    # A soft low knock: something heavy, a long way off.
    "step_ballora": (0.34,  900.0, 0.55),
    # Sharper and brighter: closer, and quicker about it.
    "step_foxy":    (0.24, 3400.0, 0.50),
    # The heaviest of the three, and the slowest to decay.
    "step_freddy":  (0.42,  600.0, 0.62),
}

# name -> (seconds, [(start Hz, end Hz, level), ...])
#
# Ballora walking off. She is a ballerina and she is blind, so this is the
# one cue in the game that is a relief rather than a warning, and it is
# written as a music box running down rather than as a footstep.
LOSTS = {
    "lost_ballora": (1.30, [(784.0, 740.0, 0.30), (587.0, 494.0, 0.26)]),
}

# name -> (seconds, start Hz, end Hz, noise level, tone level)
#
# The controlled shock. It currently falls back to `power_down`, which is
# the wrong sound: a shock is an arc in the room with you, not a breaker
# tripping somewhere else in the building.
SHOCKS = {
    "shock": (0.55, 1400.0, 260.0, 0.55, 0.50),
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


def envelope(i, n, rate, attack=0.18, release=0.45):
    """
    In, hold, out -- and the out is long, because a breath that stops dead
    sounds like a file ending rather than like something exhaling.
    """
    t = i / rate
    total = n / rate
    if t < attack:
        return t / attack
    if t > total - release:
        return max(0.0, (total - t) / release)
    return 1.0


def noise(n, seed):
    rng = random.Random(seed & 0xFFFF)
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def normalise(x):
    peak = max(1e-9, max(abs(v) for v in x))
    return [v / peak for v in x]


def build_here(name):
    secs, cutoff, pulse, depth, rattle, level = HERES[name]
    n = int(secs * RATE)
    filtered = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    for i, v in enumerate(filtered):
        t = i / RATE
        amp = envelope(i, n, RATE)
        breathe = 1.0 - depth + depth * (0.5 + 0.5 * math.sin(2 * math.pi * pulse * t))
        flutter = 1.0 if rattle <= 0 else 0.75 + 0.25 * math.sin(2 * math.pi * rattle * t)
        samples.append(v * amp * breathe * flutter * level)
    return samples


def build_step(name):
    secs, cutoff, level = STEPS[name]
    n = int(secs * RATE)
    filtered = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    for i, v in enumerate(filtered):
        # A footfall is all attack: loud on the frame it lands and gone
        # inside a fifth of a second.
        amp = math.exp(-(i / RATE) * 18.0)
        samples.append(v * amp * level)
    return samples


def build_tones(name, table, decay=3.2, gap=0.0):
    """A short sequence of notes, each one decaying away."""
    secs, notes = table[name]
    n = int(secs * RATE)
    samples = [0.0] * n
    span = n / len(notes)
    for k, (f0, f1, level) in enumerate(notes):
        start = int(k * span)
        phase = 0.0
        for i in range(start, n):
            t = (i - start) / RATE
            k2 = (i - start) / max(1, span)
            freq = f0 + (f1 - f0) * k2
            phase += 2 * math.pi * freq / RATE
            amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 600.0))
            samples[i] += math.sin(phase) * amp * level
    return normalise(samples)


def build_shock(name):
    secs, f0, f1, noise_level, tone_level = SHOCKS[name]
    n = int(secs * RATE)
    hiss = noise(n, hash(name))
    samples = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        k = i / max(1, n - 1)
        freq = f0 + (f1 - f0) * k
        phase += 2 * math.pi * freq / RATE
        # Instant attack, exponential decay: the arc is over almost as soon
        # as it starts, and the tail is the room answering.
        amp = math.exp(-t * 6.0) * (1.0 - math.exp(-t * 900.0))
        samples.append((hiss[i] * noise_level + math.sin(phase) * tone_level) * amp)
    return normalise(samples)


def main():
    os.makedirs(OUT, exist_ok=True)
    built = {}
    for name in HERES:
        built[name] = build_here(name)
    for name in STEPS:
        built[name] = build_step(name)
    for name in LOSTS:
        built[name] = build_tones(name, LOSTS, decay=1.6)
    for name in SHOCKS:
        built[name] = build_shock(name)

    for name, samples in built.items():
        path = os.path.join(OUT, name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(
                struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767))
                for s in samples))
        print(f"  {name + '.wav':20s} {len(samples) / RATE:.2f}s  "
              f"{os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
