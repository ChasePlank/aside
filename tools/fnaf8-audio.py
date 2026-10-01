#!/usr/bin/env python3
"""
Rebuild the FNAF 8 cues in audio/.

    python3 tools/fnaf8-audio.py

Same reason this file exists as tools/fnaf4-breath.py through
tools/fnaf7-audio.py: the asset is not committed by hand, it is
*reproducible*, and there is no wiki page with a lamp crossing on it.

FNAF 8's information model is thinner than FNAF 7's, and that is the
design rather than a loss. FNAF 7 had one distinction -- a step on the left
against a step on the right -- and it was the whole audio channel. FNAF 8
has **no directional channel at all**: the lamp is the only way to know
where either of them is, and a footfall carries no information about which
hall it came from, because if it did the lamp would stop being the game.

So the cues here are not information. They are **texture and confirmation**:

    step        a footfall somewhere in the building. Deliberately the
                same sound for both halls -- see above.
    push        the beam landing on something and moving it. The one cue
                in the game that means *you did something*.
    at_door     a knock. Something has stopped, and it is not waiting for
                you.
    gives_up    a footfall receding: the one good sound in the game.
    lamp        a servo crossing, and a clunk arriving. The lamp is a
                physical object on a swivel and the night is timed against
                how long it takes to move, so it has to sound like one.
    met         the only cue that gets louder as it goes.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, sys, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, decay, thud Hz, thud level, level)
STEPS = {
    "f8_step": (0.34, 1900.0, 11.0, 104.0, 0.55, 0.66),
    "f8_back": (0.42, 1500.0, 7.0, 92.0, 0.45, 0.52),
    "f8_gives_up": (0.55, 1300.0, 5.0, 84.0, 0.40, 0.50),
}

# name -> (seconds, cutoff Hz, decay, body Hz, body level, level)
KNOCKS = {
    "f8_door": (0.46, 3200.0, 15.0, 120.0, 0.55, 0.78),
}

# name -> (seconds, cutoff Hz, decay, sweep Hz, sweep level, level)
PUSHES = {
    "f8_push": (0.70, 1400.0, 5.5, 74.0, 0.50, 0.80),
}

# name -> (seconds, cutoff Hz, ring Hz, ring level, level)
SERVOS = {
    "f8_swivel": (0.95, 2600.0, 210.0, 0.30, 0.55),
    "f8_set":    (0.26, 3000.0, 520.0, 0.35, 0.68),
}

# name -> (seconds, click cutoff, ping Hz, ping level, level)
LIGHTS = {
    "f8_dim":    (0.22, 5000.0, 980.0, 0.22, 0.50),
    "f8_bright": (0.34, 5400.0, 1320.0, 0.30, 0.62),
    "f8_off":    (0.14, 4200.0, 0.0, 0.0, 0.48),
}

# name -> (seconds, cutoff Hz, rise, level)
ARRIVALS = {
    "f8_met": (1.05, 2600.0, 11.0, 0.92),
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
    # have to spend the lamp to use.
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


def build_push(name):
    """
    The beam landing on something and moving it.

    A shove, not a hit: a low body that *sweeps down* as the thing gives
    ground, with a scrape on top of it. The sweep is the whole cue -- a
    push that did not move anything is a push that did nothing, and the
    player has to be able to hear the difference between the beam landing
    on a unit at the far end of its hall and the beam landing on one that
    is already as far back as it goes.
    """
    secs, cutoff, decay, sweep_hz, sweep_level, level = PUSHES[name]
    n = int(secs * RATE)
    scrape = normalise(one_pole(noise(n, hash("push")), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(scrape):
        t = i / RATE
        hz = sweep_hz * (1.0 + 0.55 * t)
        phase += 2 * math.pi * hz / RATE
        body = math.sin(phase) * math.exp(-t * decay) * sweep_level
        samples.append((v * math.exp(-t * 9.0) * 0.5 + body) * level)
    return normalise(samples)


def build_servo(name):
    """
    The lamp crossing, and the lamp arriving.

    The swivel is a servo: a tone with a slow wobble on it, held for the
    whole of SWIVEL. It is the longest cue in the game and that is
    deliberate -- it is the sound of the thing you cannot do anything
    during, and the night is timed against it.
    """
    secs, cutoff, ring_hz, ring_level, level = SERVOS[name]
    n = int(secs * RATE)
    body = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    ring = 0.0
    for i, v in enumerate(body):
        t = i / RATE
        wobble = 1.0 + 0.06 * math.sin(2 * math.pi * 7.0 * t)
        ring += 2 * math.pi * ring_hz * wobble / RATE
        env = math.exp(-t * (5.0 if secs > 0.5 else 26.0))
        out = v * math.exp(-t * 12.0) * 0.35
        out += math.sin(ring) * env * ring_level
        samples.append(out * level)
    return normalise(samples)


def build_light(name):
    secs, cutoff, ping_hz, ping_level, level = LIGHTS[name]
    n = int(secs * RATE)
    click = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(click):
        t = i / RATE
        out = v * math.exp(-t * 95.0)
        if ping_hz > 0:
            phase += 2 * math.pi * ping_hz / RATE
            out += math.sin(phase) * math.exp(-t * 24.0) * ping_level
        samples.append(out * level)
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
    for name in PUSHES:
        built[name] = build_push(name)
    for name in SERVOS:
        built[name] = build_servo(name)
    for name in LIGHTS:
        built[name] = build_light(name)
    for name in ARRIVALS:
        built[name] = build_arrival(name)

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':18s} {len(samples) / RATE:5.2f}s  "
              f"{os.path.getsize(path):7d} bytes")

    # The one thing that must never be true. FNAF 8 has no directional
    # audio channel *by design* -- the lamp is the only way to know where
    # either of them is -- so the two steps are deliberately the same
    # sound. This check exists to keep it that way: a later edit that
    # "improves" the two steps by making them tell the halls apart would
    # hand the player a free channel and quietly delete the game.
    # FNAF 7 owns step_left and step_right, and they are *directional* --
    # two sounds built to be told apart, which is what that game's whole
    # audio channel was. This game has no directional channel at all, so
    # its footfall is one cue under a different name, and it must stay that
    # way: an edit that renamed f8_step to step_left would hand FNAF 8's
    # player a free channel and quietly delete the game.
    if "step_left" in built or "step_right" in built:
        print("this tool must not write FNAF 7's directional step cues")
        sys.exit(1)
    print("  f8_step is one cue for both halls, as it must be")


if __name__ == "__main__":
    main()
