#!/usr/bin/env python3
"""Synthesizes the play-canvas sounds into androidApp/src/main/res/raw (design system, Haptics and sound).

Made here from sine waves and noise, so there is no third-party audio to license. Deterministic: rerunning it
rewrites the same bytes. Usage: python3 tools/make_sounds.py
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "androidApp/src/main/res/raw"


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(32767 * 0.9 * s / peak)) for s in samples))


def lowpass(samples, alpha):
    out, y = [], 0.0
    for s in samples:
        y += alpha * (s - y)
        out.append(y)
    return out


def fade_in(samples, ms=2):
    n = int(RATE * ms / 1000)
    return [s * min(1.0, i / n) for i, s in enumerate(samples)]


def gem_click():
    """Soft click: a short damped 1.4 kHz tone over a breath of filtered noise, ~45 ms."""
    rng = random.Random(1)
    n = int(RATE * 0.045)
    noise = lowpass([rng.uniform(-1, 1) for _ in range(n)], 0.25)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-t * 110)
        out.append(env * (0.8 * math.sin(2 * math.pi * 1400 * t) + 0.35 * noise[i]))
    return fade_in(out)


def flap_thump():
    """Felt-flap thump: a low falling sine with a muffled noise burst, ~160 ms."""
    rng = random.Random(2)
    n = int(RATE * 0.16)
    noise = lowpass([rng.uniform(-1, 1) for _ in range(n)], 0.06)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        freq = 70 + 60 * math.exp(-t * 30)
        phase += 2 * math.pi * freq / RATE
        out.append(math.exp(-t * 28) * math.sin(phase) + 0.5 * math.exp(-t * 60) * noise[i])
    return fade_in(out)


def finish_chime():
    """One low chime: a C4 bell with inharmonic partials and a slow decay, ~1.8 s."""
    n = int(RATE * 1.8)
    partials = [(1.0, 1.0, 2.2), (2.0, 0.45, 3.0), (2.76, 0.25, 4.5), (5.4, 0.08, 7.0)]
    out = []
    for i in range(n):
        t = i / RATE
        out.append(sum(a * math.exp(-t * d) * math.sin(2 * math.pi * 261.63 * r * t) for r, a, d in partials))
    return fade_in(out, ms=4)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    write("gem_click", gem_click())
    write("flap_thump", flap_thump())
    write("finish_chime", finish_chime())
