#!/usr/bin/env python3
"""
Rebuild Bell Codes' three cues in audio/.

    python3 tools/bell-codes-audio.py

Same reason tools/fruitjump-audio.py and tools/fnaf*-audio.py exist: the asset is not committed by hand, it is
REPRODUCIBLE, and the reasoning behind each sound lives here rather than in whoever's memory made it.

WHY THIS STORY NEEDS ITS OWN. The engine's shared cues are other stories' weather - sea swells, fan hums, pizzeria
doors - and a signal box is a room made of brass and paper. Three sounds carry it:

  bell.m4a   THE BLOCK BELL, struck once by its hammer. This is the story's second voice: the code the signalman
             reads by ear. A railway block bell is SMALL and struck hard, so it is bright and very short - a
             nominal around 1.2 kHz with the strike transient left in, decaying inside half a second. It must not
             ring like a church bell; it is a signal, and it is meant to be countable. That is the whole design
             constraint: a code has to be countable, so no partial may outlast the next beat.
  book.wav   Taking the ledger off the shelf. Two layers, because that is what it is: a paper edge (a short
             bandpassed noise sweep) over a low wooden thud as the spine clears the shelf. The thud is what makes
             it read as weight rather than as paper alone.
  phone.wav  The regional centre's telephone. Two gongs struck alternately with a tremolo, 0.9 s on and 0.6 s off
             - the standard interrupted ring. Deliberately thinner and more intrusive than the block bell, because
             it arrives from outside the room and it is the sound of the railway as an institution rather than as
             a craft.

Nothing is normalised to the same level: the bell is a working sound heard all night, the phone is an
interruption, and it should be louder.
"""
import math
import wave
import numpy as np

RATE = 44100


def write(name, x, peak=0.6):
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


def bell():
    """One strike. Countable: every partial is gone inside 0.45 s so the next beat is never masked."""
    n = int(0.6 * RATE)
    t = np.arange(n) / RATE
    x = np.zeros(n)
    # nominal strongest, then the partials that make it brass rather than a sine
    for f, amp, tau in ((1180, 1.00, 0.30), (1770, 0.55, 0.20), (2384, 0.30, 0.13), (3150, 0.18, 0.08)):
        x += amp * np.sin(2 * math.pi * f * t) * np.exp(-t / tau)
    # the hammer: 4 ms of noise, gone before the ear can call it a click
    k = int(0.004 * RATE)
    x[:k] += np.random.default_rng(7).normal(0, 0.5, k)
    return x


def book():
    """Paper edge over a wooden thud."""
    n = int(0.7 * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(11)
    noise = rng.normal(0, 1, n)
    # paper: noise with a fast attack and a 120 ms tail, high-passed by first-differencing
    env = np.exp(-t / 0.045) * (1 - np.exp(-t / 0.004))
    paper = np.diff(np.concatenate([[0.0], noise])) * env
    # the shelf thud, 90 Hz, slower
    thud = np.sin(2 * math.pi * 90 * t) * np.exp(-t / 0.10)
    thud += 0.4 * np.sin(2 * math.pi * 140 * t) * np.exp(-t / 0.06)
    return 0.55 * paper + thud


def phone():
    """Two gongs, tremolo, 0.9 s on and 0.6 s off, twice."""
    seg = int(0.9 * RATE)
    gap = np.zeros(int(0.6 * RATE))
    ring = np.zeros(seg)
    t = np.arange(seg) / RATE
    trem = 0.5 + 0.5 * np.cos(2 * math.pi * 25 * t)      # the interrupted ring
    for f, amp, tau in ((1000, 1.0, 0.35), (1300, 0.8, 0.35), (2000, 0.25, 0.15)):
        ring += amp * np.sin(2 * math.pi * f * t) * np.exp(-t / tau)
    ring *= trem
    return np.concatenate([ring, gap, ring, gap])


if __name__ == "__main__":
    for name, fn, peak in (("bell", bell, 0.55), ("book", book, 0.5), ("phone", phone, 0.85)):
        print(f"  audio/{name}.wav  {write(name, fn(), peak):.2f}s")
