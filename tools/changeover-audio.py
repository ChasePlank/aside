#!/usr/bin/env python3
"""
Rebuild Changeover's cue in audio/.

    python3 tools/changeover-audio.py

ONE CUE, because the story is one room and one machine.

  projector.wav   A 35mm projector running. This is the sound the story opens on - the narrator's first line is
                  about hearing a carbon arc going - so it has to be a STEADY MECHANICAL LOOP rather than a
                  musical note: a motor, a shutter, and the film going past the gate.

                  24 frames a second, so the shutter interrupts the light 48 times a second (twice per frame), and
                  that 48 Hz flutter is the thing you actually hear in a booth. It is built here as a 48 Hz pulse
                  train with a little jitter, because a perfectly regular one reads as a synthesiser and a booth
                  is never perfectly regular. Underneath it: a low motor hum at 60 Hz and its second harmonic, and
                  a soft broadband hiss for the film and the air.

                  4 seconds, loopable - the loop point is a whole number of flutter cycles so it does not click.
"""
import math
import wave
import numpy as np

RATE = 44100


def write(name, x, peak=0.5):
    x = np.asarray(x, dtype=np.float64)
    m = np.max(np.abs(x)) or 1.0
    pcm = np.clip((x / m) * peak * 32767.0, -32768, 32767).astype('<i2')
    with wave.open(f"audio/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    return len(pcm) / RATE


def projector():
    n = int(4.0 * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(11)

    # 48 Hz shutter flutter - a short click per cycle, with slight jitter so it does not read as a test tone
    flutter = np.zeros(n)
    period = RATE / 48.0
    k = 0
    while True:
        start = int(k * period + rng.normal(0, 12))
        if start >= n:
            break
        end = min(n, start + int(0.0018 * RATE))
        flutter[start:end] += np.hanning(end - start) * 1.0
        k += 1

    motor = 0.5 * np.sin(2 * math.pi * 60.0 * t) + 0.18 * np.sin(2 * math.pi * 120.0 * t)
    hiss = rng.normal(0, 0.06, n)
    x = 0.9 * flutter + 0.35 * motor + hiss
    # whole number of flutter cycles in the loop, so the ends meet
    return x[: int(4.0 * RATE)]


if __name__ == "__main__":
    print(f"  audio/projector.wav  {write('projector', projector()):.2f}s")
