#!/usr/bin/env python3
"""Synthesizes the play-canvas sounds into androidApp/src/main/res/raw (design system, Haptics and sound; T41).

Made here from sine waves and noise, so there is no third-party audio to license (tools/sounds/PROVENANCE.md lists every
file). Each event has a few takes the app picks between, on top of its random pitch. Deterministic: rerunning it
rewrites the same bytes. Usage: python3 tools/make_sounds.py [out_dir]
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "androidApp/src/main/res/raw"
C4 = 261.63


def write(out, name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(str(out / f"{name}.wav"), "wb") as w:
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


def highpass(samples, alpha):
    return [s - l for s, l in zip(samples, lowpass(samples, alpha))]


def fade_in(samples, ms=2):
    n = int(RATE * ms / 1000)
    return [s * min(1.0, i / n) for i, s in enumerate(samples)]


def fade_out(samples, ms=8):
    n = int(RATE * ms / 1000)
    size = len(samples)
    return [s * min(1.0, (size - i) / n) for i, s in enumerate(samples)]


def mix(*layers):
    size = max(len(layer) for layer in layers)
    return [sum(layer[i] for layer in layers if i < len(layer)) for i in range(size)]


def delayed(samples, ms):
    return [0.0] * int(RATE * ms / 1000) + samples


def ring(freq, seconds, partials, gain=1.0):
    """A struck glass or bell: [partials] are (ratio, amplitude, decay per second) over [freq]."""
    n = int(RATE * seconds)
    return [
        gain * sum(a * math.exp(-(i / RATE) * d) * math.sin(2 * math.pi * freq * r * i / RATE) for r, a, d in partials)
        for i in range(n)
    ]


def tap(seed, seconds, body_hz, alpha):
    """The gem landing on the tray: a short low knock and a breath of filtered noise."""
    rng = random.Random(seed)
    n = int(RATE * seconds)
    noise = highpass(lowpass([rng.uniform(-1, 1) for _ in range(n)], alpha), 0.05)
    return [
        0.55 * math.exp(-(i / RATE) * 150) * math.sin(2 * math.pi * body_hz * i / RATE)
        + 0.5 * math.exp(-(i / RATE) * 400) * noise[i]
        for i in range(n)
    ]


# Gem placed: a cut glass "tink" (inharmonic partials) over the knock on the tray. Each take is its own stone.
PLACE_TAKES = [
    (2400, [(1.0, 1.0, 70), (1.47, 0.45, 95), (2.09, 0.22, 130)], 460),
    (2650, [(1.0, 1.0, 75), (1.62, 0.40, 100), (2.33, 0.20, 140)], 500),
    (2200, [(1.0, 1.0, 65), (1.38, 0.50, 90), (2.71, 0.18, 120)], 430),
    (2900, [(1.0, 1.0, 80), (1.53, 0.42, 105), (2.18, 0.20, 135)], 520),
    (2500, [(1.0, 1.0, 72), (1.71, 0.38, 98), (2.47, 0.24, 125)], 480),
]


def gem_place(take):
    freq, partials, body = PLACE_TAKES[take]
    glass = ring(freq, 0.075, partials, gain=0.7)
    return fade_out(fade_in(mix(glass, tap(10 + take, 0.075, body, 0.45))))


def gem_drag(take):
    """The drag tick: the same stone, shorter, darker and with less knock, so a run of them stays soft."""
    freq, partials, body = PLACE_TAKES[take]
    glass = ring(freq * 0.8, 0.04, [(r, a, d * 1.8) for r, a, d in partials], gain=0.5)
    return fade_out(lowpass(fade_in(mix(glass, tap(20 + take, 0.04, body, 0.2))), 0.4), ms=5)


def flap_thump(seed, low):
    """Felt-flap thump: a low falling sine with a muffled noise burst, ~160 ms."""
    rng = random.Random(seed)
    n = int(RATE * 0.16)
    noise = lowpass([rng.uniform(-1, 1) for _ in range(n)], 0.06)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        freq = low + 60 * math.exp(-t * 30)
        phase += 2 * math.pi * freq / RATE
        out.append(math.exp(-t * 28) * math.sin(phase) + 0.5 * math.exp(-t * 60) * noise[i])
    return out


# Color finished: the thump, then the color's last gems settling as a small rising run of glass pings.
DONE_TAKES = [
    (70, [(0, 1568), (45, 2093), (95, 2637)]),
    (76, [(0, 1760), (40, 2349), (85, 2960)]),
    (64, [(0, 1397), (50, 1865), (100, 2349)]),
]


def color_done(take):
    low, pings = DONE_TAKES[take]
    glass = [delayed(ring(f, 0.25, [(1.0, 1.0, 22), (2.4, 0.2, 40)], gain=0.22 - 0.04 * k), ms) for k, (ms, f) in enumerate(pings)]
    return fade_out(fade_in(mix(flap_thump(30 + take, low), *glass)), ms=20)


BELL = [(1.0, 1.0, 2.2), (2.0, 0.45, 3.0), (2.76, 0.25, 4.5), (5.4, 0.08, 7.0)]


def bell(freq, seconds, partials=BELL, gain=1.0):
    return ring(freq, seconds, partials, gain)


def milestone(take):
    """A progress milestone: a short bell a fifth above the finish chime, three voicings."""
    g4 = C4 * 1.5
    short = [(r, a, d * 1.8) for r, a, d in BELL]
    if take == 0:
        out = bell(g4, 1.2, short)
    elif take == 1:
        out = mix(bell(g4, 1.2, short), delayed(bell(g4 * 1.25, 1.0, short, gain=0.45), 70))
    else:
        out = mix(bell(g4, 1.2, short), delayed(bell(g4 * 2, 0.9, short, gain=0.3), 50))
    return fade_out(fade_in(out, ms=4), ms=60)


def finish_chime(take):
    """The low chime (C4 bell, ~1.8 s); the app layers its fifth and octave on top. Takes differ in the bell's metal."""
    partials = [
        BELL,
        [(1.0, 1.0, 2.0), (2.0, 0.40, 2.8), (3.01, 0.20, 4.0), (4.95, 0.10, 6.5)],
        [(1.0, 1.0, 2.4), (2.0, 0.50, 3.2), (2.5, 0.18, 4.8), (6.2, 0.06, 8.0)],
    ][take]
    shimmer = delayed(ring(C4 * 8, 0.6, [(1.0, 1.0, 9), (1.33, 0.6, 11)], gain=0.05), 30)
    return fade_out(fade_in(mix(bell(C4, 1.8, partials), shimmer), ms=4), ms=80)


SOUNDS = {
    **{f"gem_place_{k + 1}": (gem_place, k) for k in range(5)},
    **{f"gem_drag_{k + 1}": (gem_drag, k) for k in range(3)},
    **{f"color_done_{k + 1}": (color_done, k) for k in range(3)},
    **{f"milestone_{k + 1}": (milestone, k) for k in range(3)},
    **{f"finish_chime_{k + 1}": (finish_chime, k) for k in range(3)},
}

if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for name, (make, take) in SOUNDS.items():
        write(out, name, make(take))
