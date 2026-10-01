#!/usr/bin/env python3
"""
Rebuild the FNAF 7 cues in audio/.

    python3 tools/fnaf7-audio.py

Same reason this file exists as tools/fnaf4-breath.py, tools/fnaf5-audio.py
and tools/fnaf6-audio.py: the asset is not committed by hand, it is
*reproducible*, and there is no wiki page with a door bar sliding on it.

It has to exist, and the argument is sharper here than it was for FNAF 6.
FNAF 7's whole information model is one distinction:

    a step on the left    it is walking down the left hall.
    a step on the right   it is walking down the right hall.

Those two are the audio channel, and **they are the only pair of cues in
the franchise that have to be told apart by *direction* rather than by
kind.** A drag and a creak are different noises; a left step and a right
step are the same noise in two places, and a player who cannot place one
is a player with no audio channel at all. So the two are built from the
same body with the thud an octave apart, which is the cheapest way to make
two sounds that are obviously the same event and obviously not the same
place.

The character of each one is three numbers: how bright the scrape is, how
fast it decays, and how much low end is under it.

    step      a footfall. Bright scrape, short, and a thud underneath it
              that is the weight arriving. The thud's pitch is the side.
    at_door   a knuckle on a door. One hard transient and almost no tail.
    bar       metal on metal. `bar_move` is the slide, `bar_set` is the
              latch, and `repel` is the bar taking a hit -- which is the
              same clang with a ring on it, the way FNAF 6's shock_hit was
              the same arc with a ring on it.
    light     a switch, and a filament coming up. `light_blown` is the
              filament going, which is a pop and then nothing.
    caught    the only cue in the game that gets louder as it goes.

JavaFX plays WAV, so these are 16-bit mono PCM at 22050Hz.
"""
import math, os, random, struct, wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "audio")

RATE = 22050

# name -> (seconds, cutoff Hz, decay, thud Hz, thud level, level)
#
# The thud is the whole difference between the two steps. 96Hz against
# 148Hz is a bit over half an octave, which is far enough that nobody has
# to be told which is which and close enough that they are obviously the
# same foot.
STEPS = {
    "step_left":  (0.34, 2100.0, 11.0, 96.0, 0.62, 0.72),
    "step_right": (0.34, 2100.0, 11.0, 148.0, 0.62, 0.72),
}

# name -> (seconds, cutoff Hz, decay, body Hz, body level, level)
KNOCKS = {
    "at_door": (0.42, 3400.0, 16.0, 128.0, 0.55, 0.80),
}

# name -> (seconds, cutoff Hz, decay, ring Hz, ring level, level)
BARS = {
    "bar_move": (0.55, 3000.0, 6.5, 0.0, 0.0, 0.62),
    "bar_set":  (0.30, 2400.0, 14.0, 430.0, 0.30, 0.72),
    "repel":    (0.85, 2600.0, 4.5, 720.0, 0.45, 0.85),
}

# name -> (seconds, click cutoff, ping Hz, ping level, level)
LIGHTS = {
    "light_on":    (0.30, 5200.0, 1240.0, 0.28, 0.55),
    "light_off":   (0.14, 4200.0, 0.0, 0.0, 0.50),
    "light_ready": (0.10, 3600.0, 0.0, 0.0, 0.42),
}

# name -> (seconds, cutoff Hz, pop Hz, pop level, level)
BLOWNS = {
    "light_blown": (0.55, 6000.0, 900.0, 0.55, 0.70),
}

# name -> (seconds, cutoff Hz, rise, level)
ARRIVALS = {
    "caught": (0.95, 3000.0, 13.0, 0.90),
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


def noise(n, seed):
    rng = random.Random(seed & 0xFFFF)
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def normalise(x):
    peak = max(1e-9, max(abs(v) for v in x))
    return [v / peak for v in x]


def build_step(name):
    secs, cutoff, decay, thud_hz, thud_level, level = STEPS[name]
    n = int(secs * RATE)
    scrape = normalise(one_pole(noise(n, hash(name)), cutoff))
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


def build_bar(name):
    secs, cutoff, decay, ring_hz, ring_level, level = BARS[name]
    n = int(secs * RATE)
    body = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    ring = 0.0
    for i, v in enumerate(body):
        t = i / RATE
        amp = math.exp(-t * decay) * (1.0 - math.exp(-t * 700.0))
        out = v * amp
        if ring_hz > 0:
            ring += 2 * math.pi * ring_hz / RATE
            # The ring outlives the hit, which is the whole difference
            # between the bar landing and the bar being hit.
            out += math.sin(ring) * math.exp(-t * 5.0) * ring_level
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


def build_blown(name):
    secs, cutoff, pop_hz, pop_level, level = BLOWNS[name]
    n = int(secs * RATE)
    fizz = normalise(one_pole(noise(n, hash(name)), cutoff))
    samples = []
    phase = 0.0
    for i, v in enumerate(fizz):
        t = i / RATE
        # The pop is over in a hundredth of a second and the fizz is what
        # is left of it, which is what a filament actually does.
        phase += 2 * math.pi * pop_hz / RATE
        pop = math.sin(phase) * math.exp(-t * 90.0) * pop_level
        tail = v * math.exp(-t * 9.0) * (1.0 - math.exp(-t * 400.0))
        samples.append((pop + tail * 0.5) * level)
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
    for name in BARS:
        built[name] = build_bar(name)
    for name in LIGHTS:
        built[name] = build_light(name)
    for name in BLOWNS:
        built[name] = build_blown(name)
    for name in ARRIVALS:
        built[name] = build_arrival(name)

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':18s} {len(samples) / RATE:5.2f}s  "
              f"{os.path.getsize(path):7d} bytes")

    # The one thing that must never be true: the two steps are the same
    # sound. If a later edit makes them the same, the audio channel stops
    # being a direction and becomes a presence, and nothing else would say
    # so -- the night would go on working while quietly becoming a game
    # about whether something is in the building at all.
    if built["step_left"] == built["step_right"]:
        raise SystemExit("step_left and step_right are the same samples -- "
                         "that is the whole audio channel of the game")


if __name__ == "__main__":
    main()
