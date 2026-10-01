#!/usr/bin/env python3
"""
Rebuild the FNAF 9 cues in audio/.

    python3 tools/fnaf9-audio.py

Same reason this file exists as tools/fnaf4-breath.py through
tools/fnaf8-audio.py: the asset is not committed by hand, it is
*reproducible*, and there is no wiki page with a door mechanism giving out
on it.

FNAF 9's information model is FNAF 8's, one step further on. FNAF 8 had no
directional channel -- the lamp was the only way to know where either of
them was, so a footfall could not say which hall it came from. FNAF 9 has
no directional channel *and no live channel at all*: the monitor is a
picture of the past, and the one thing in the office that reads now is a
contact sensor on the door that only reads while the door is down. So the
audio cannot carry position, and it cannot carry "something is coming" --
that is what the monitor is for, and the monitor is always late.

What is left is **texture, and the mechanism**:

    step        a footfall in one of the halls. Deliberately the same
                sound for both -- see above, and see the guard at the
                bottom of this file.
    door        a walker reaching the doorway. A knock, and heavier than
                FNAF 8's: this door is a mechanism with a travel time.
    gives_up    a footfall receding. The one good sound in the game.
    switch      the monitor waking on a hall. A click and a ping -- the
                picture is a device, and it has to sound like one.
    jam         the mechanism giving out at HOLD_MAX. The only cue in the
                franchise that means *the building did something to you*,
                and the only one the player never chose.
    taken       the arrival that got through.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, sys, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, decay, thud Hz, thud level, level)
STEPS = {
    "f9_step":     (0.36, 1700.0, 10.0, 98.0, 0.55, 0.64),
    "f9_gives_up": (0.62, 1200.0, 4.5, 80.0, 0.38, 0.46),
}

# name -> (seconds, cutoff Hz, decay, body Hz, body level, level)
KNOCKS = {
    "f9_door": (0.50, 3000.0, 13.0, 112.0, 0.60, 0.80),
}

# name -> (seconds, cutoff Hz, ping Hz, ping level, level)
SWITCHES = {
    "f9_switch": (0.28, 5200.0, 1150.0, 0.26, 0.56),
}

# name -> (seconds, cutoff Hz, decay, sweep Hz, sweep level, level)
JAMS = {
    "f9_jam": (0.85, 2200.0, 6.5, 150.0, 0.62, 0.86),
}

# name -> (seconds, cutoff Hz, rise, level)
ARRIVALS = {
    "f9_taken": (1.10, 2400.0, 12.0, 0.94),
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


def build_step(name):
    secs, cutoff, decay, thud_hz, thud_level, level = STEPS[name]
    n = int(secs * RATE)
    # The seed is the *family*, not the name, and that is the whole point
    # of this cue: a footfall somewhere in the building. Two seeds would
    # be two sounds, and two sounds would be a channel the player does not
    # have to spend a look on -- and in this game a look is the only
    # currency there is.
    scrape = normalise(one_pole(noise(n, hash("step")), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(scrape):
        t = i / RATE
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 1400.0))
        phase += 2 * math.pi * thud_hz / RATE
        thud = math.sin(phase) * math.exp(-t * 13.0) * thud_level
        samples.append((v * amp + thud) * level)
    return normalise(samples)


def build_knock(name):
    secs, cutoff, decay, body_hz, body_level, level = KNOCKS[name]
    n = int(secs * RATE)
    hit = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(hit):
        t = i / RATE
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 3000.0))
        phase += 2 * math.pi * body_hz / RATE
        body = math.sin(phase) * math.exp(-t * 22.0) * body_level
        samples.append((v * amp + body) * level)
    return normalise(samples)


def build_switch(name):
    """
    The monitor waking on a hall.

    A click and a ping, and short. This is the sound of the *device*, not
    of anything in the building -- the player hears it several hundred
    times a night and it must never be mistaken for information about a
    walker. It is the only cue in the game that is about you.
    """
    secs, cutoff, ping_hz, ping_level, level = SWITCHES[name]
    n = int(secs * RATE)
    click = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(click):
        t = i / RATE
        out = v * math.exp(-t * 95.0)
        phase += 2 * math.pi * ping_hz / RATE
        out += math.sin(phase) * math.exp(-t * 24.0) * ping_level
        samples.append(out * level)
    return normalise(samples)


def build_jam(name):
    """
    The mechanism giving out.

    A hard clunk and a grind that *falls* -- the door coming down on its
    own, which is the one thing in the night the player did not ask for.
    The sweep goes down where FNAF 8's push swept up, and that is the
    difference between a thing you did and a thing that happened to you.
    """
    secs, cutoff, decay, sweep_hz, sweep_level, level = JAMS[name]
    n = int(secs * RATE)
    grind = normalise(one_pole(noise(n, hash("jam")), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(grind):
        t = i / RATE
        hz = sweep_hz * (1.0 - 0.45 * min(1.0, t * 1.6))
        phase += 2 * math.pi * hz / RATE
        body = math.sin(phase) * math.exp(-t * decay) * sweep_level
        samples.append((v * math.exp(-t * 7.0) * 0.55 + body) * level)
    return normalise(samples)


def build_arrival(name):
    secs, cutoff, rise, level = ARRIVALS[name]
    n = int(secs * RATE)
    body = normalise(one_pole(noise(n, hash(name)), cutoff))
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
    for name in STEPS:
        built[name] = build_step(name)
    for name in KNOCKS:
        built[name] = build_knock(name)
    for name in SWITCHES:
        built[name] = build_switch(name)
    for name in JAMS:
        built[name] = build_jam(name)
    for name in ARRIVALS:
        built[name] = build_arrival(name)

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':18s} {len(samples) / RATE:5.2f}s  "
              f"{os.path.getsize(path):7d} bytes")

    # The one thing that must never be true. FNAF 9 has no directional
    # audio channel *by design* -- the monitor is the only way to know
    # where anything is, and the monitor is a picture of the past -- so
    # the two halls share one footfall. A later edit that "improved" the
    # step by making it tell the halls apart would hand the player a free
    # channel and quietly delete the game.
    if "step_left" in built or "step_right" in built:
        print("this tool must not write FNAF 7's directional step cues")
        sys.exit(1)
    print("  f9_step is one cue for both halls, as it must be")


if __name__ == "__main__":
    main()
