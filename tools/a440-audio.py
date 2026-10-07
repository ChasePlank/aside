#!/usr/bin/env python3
"""
Rebuild A440's cue in audio/.

    python3 tools/a440-audio.py

Same reason tools/bell-codes-audio.py and the others exist: the asset is not committed by hand, it is
REPRODUCIBLE, and the reasoning for the sound lives here rather than in whoever's memory made it.

ONE CUE, because the story is one room and one object.

  fork.wav   A tuning fork struck once on the edge of the bench. This is the sound the whole story is about - the
             narrator's opening line is "The fork goes on the bench first, because it is the only thing in the room
             that is not an opinion" - so it has to be a PURE, STEADY 440 Hz with almost no partials. A tuning fork
             is deliberately the simplest sustained pitch a person can own: no harmonics to speak of, no decay
             character, nothing to interpret. It is a reference, not a note.

             The strike transient is kept, and kept short - 3 ms of noise - because a fork that fades in sounds
             like a synthesiser and a fork that clicks sounds like a fork. The decay is long and even: 2.5 seconds,
             which is roughly how long you can hold one to your ear before it stops being useful.
"""
import math
import wave
import numpy as np

RATE = 44100


def write(name, x, peak=0.55):
    x = np.asarray(x, dtype=np.float64)
    m = np.max(np.abs(x)) or 1.0
    x = (x / m) * peak
    pcm = np.clip(x * 32767.0, -32768, 32767).astype('<i2')
    with wave.open(f"audio/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    return len(pcm) / RATE


def fork():
    n = int(2.6 * RATE)
    t = np.arange(n) / RATE
    # A440 and nothing else. A real fork has a faint second mode an octave up; 0.04 of it is enough to stop the
    # tone reading as a test signal without making it read as an instrument.
    x = np.sin(2 * math.pi * 440.0 * t) * np.exp(-t / 1.6)
    x += 0.04 * np.sin(2 * math.pi * 880.0 * t) * np.exp(-t / 0.5)
    k = int(0.003 * RATE)
    x[:k] += np.random.default_rng(3).normal(0, 0.6, k)
    return x


if __name__ == "__main__":
    print(f"  audio/fork.wav  {write('fork', fork()):.2f}s")
