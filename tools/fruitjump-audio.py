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


def stable_seed(name):
    """A seed that is the same in every process.

    THIS USED TO SEED FROM PYTHON'S BUILT-IN hash() OF THE NAME, WHICH IS NOT STABLE. Python salts string hashing
    per process unless PYTHONHASHSEED is set, so the same cue came out with different samples on every run -
    measured, twice, on this machine. Re-running the generator therefore MODIFIED COMMITTED ASSETS: six of them on
    the run that found this, and the committed ones could not be reproduced from this source at all.

    That is directly against this file's own first paragraph - "the asset is not committed by hand, it is
    reproducible" - which was true of the twelve files as they sat on disk and false of any regeneration, and
    nothing was checking it. crc32 is a DIGEST of the name rather than a hash of it: the same everywhere and
    forever, which is the only property a seed needs.
    """
    import zlib
    return zlib.crc32(name.encode("utf-8"))


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
    hit = normalise(one_pole(noise(n, stable_seed(name)), cutoff))
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
    raw = noise(n, stable_seed(name))
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


# --- MUSIC --------------------------------------------------------------------------------------------------
#
# FOUR TRACKS THE ENGINE HAS ALWAYS BEEN ABLE TO ASK FOR AND NOBODY COULD HEAR. `AudioSystem.Music` declares
# title-theme, level-theme, boss-theme and victory-theme; `playMusic` records which is current, the boss asks for
# BOSS on every phase change, and not one of the four had a file - so every request was silence. The guard at the
# bottom of this file SKIPPED the Music enum on purpose, with a note that its first version swept those four in as
# "missing". That was the right call while there was nothing to build; this is the other half of it.
#
# WHY THESE ARE LOOPS AND NOT TUNES. Each is eight seconds and is meant to repeat while a screen is up, so the
# properties that matter are that it starts and ends at silence and that it does not demand attention: a melody
# with a beginning would be wrong in a level that lasts two minutes or ten.
#   * ONE VOICE, SLOW. A note every half second, held. Nothing here is a hook.
#   * THE ROOT IS THE MOOD, and the four differ by a fifth or a mode rather than by instrumentation, so the set
#     sounds like one game.
#   * THE LAST NOTE RETURNS TO THE FIRST, so the repeat is a return rather than a jolt.
def build_music(notes, root, step=0.5, voice=0.30, pad=0.12):
    n = int(len(notes) * step * RATE)
    out = [0.0] * n
    for i, semi in enumerate(notes):
        f = root * (2.0 ** (semi / 12.0))
        start = int(i * step * RATE)
        length = int(step * 2.4 * RATE)          # held past its own step, so the notes overlap a little
        for k in range(length):
            j = start + k
            if j >= n: break
            t = k / RATE
            env = min(1.0, t / 0.06) * math.exp(-t * 1.7)   # soft attack, long decay: nothing clicks
            out[j] += voice * env * math.sin(2 * math.pi * f * t)
    # a low pad under it, an octave below the root, breathing over four seconds
    for j in range(n):
        t = j / RATE
        out[j] += pad * math.sin(2 * math.pi * (root / 2.0) * t) * (0.6 + 0.4 * math.sin(2 * math.pi * t / 4.0))
    # start and end at silence so the loop seam is inaudible
    fade = int(0.05 * RATE)
    for j in range(fade):
        out[j] *= j / fade
        out[n - 1 - j] *= j / fade
    return normalise(out)


MUSIC = {
    # name: (notes in semitones from the root, root Hz). The climb, then the same evening going on.
    "title-theme":   ([0, 7, 12, 7, 3, 7, 12, 15, 12, 7, 3, 7, 0, 7, 12, 0], 220.0),
    "level-theme":   ([0, 5, 7, 5, 0, 5, 7, 10, 7, 5, 0, 5, 7, 5, 0, 0], 196.0),
    "boss-theme":    ([0, 1, 0, -2, 0, 1, 3, 1, 0, -2, 0, 1, 0, -2, 0, 0], 146.83),
    "victory-theme": ([0, 4, 7, 12, 7, 4, 0, 4, 7, 12, 16, 12, 7, 4, 0, 0], 261.63),
}


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

    # MUSIC. Built separately because it is a different kind of asset - a loop rather than a cue - and kept out of
    # `built` so the sfx guard below still compares like with like.
    music = {name: build_music(notes, root) for name, (notes, root) in MUSIC.items()}

    for name, samples in built.items():
        path = write(name, samples)
        print(f"  {name + '.wav':16s} {len(samples) / RATE:5.2f}s  {os.path.getsize(path):6d} bytes")

    print()
    for name, samples in music.items():
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
    # ONE ENUM AT A TIME, and now both of them. The first version took every line shaped like a constant, which
    # swept in the four Music values as "missing" and skipped the last Sfx value (which ends in ';' rather than
    # ',') as "extra" - a guard reporting two problems that were both its own parsing. Reading only Sfx was the
    # fix then. Reading BOTH is the fix now, because those four are no longer a nuisance in the guard: they are
    # tracks with files, and "the engine can ask for a track that does not exist" is exactly the fault this file
    # exists to catch. It reported silence for months and nothing noticed.
    def names_in(enum_name):
        block = java.split("enum " + enum_name + " {", 1)[1].split("}", 1)[0]
        found = set()
        for line in block.splitlines():
            line = line.strip()
            # NAME("cue"), and nothing else - both enums' constructors take a String and have no quotes.
            if '("' in line:
                found.add(line.split('("', 1)[1].split('")', 1)[0])
        found.discard("")
        return found

    want = names_in("Sfx")
    got = set(built)
    missing = sorted(want - got)
    extra = sorted(got - want)
    print(f"engine asks for {len(want)} sfx; built {len(got)}")
    if missing:
        print("  MISSING (the engine can post these and there is no file): " + ", ".join(missing))
    if extra:
        print("  EXTRA (a file nothing asks for): " + ", ".join(extra))

    want_music = names_in("Music")
    got_music = set(music)
    missing_music = sorted(want_music - got_music)
    extra_music = sorted(got_music - want_music)
    print(f"engine asks for {len(want_music)} music track(s); built {len(got_music)}")
    if missing_music:
        print("  MISSING TRACK (the engine can ask for these and there is no file): " + ", ".join(missing_music))
    if extra_music:
        print("  EXTRA TRACK (a file nothing asks for): " + ", ".join(extra_music))

    return 1 if (missing or missing_music) else 0


if __name__ == "__main__":
    raise SystemExit(main())
