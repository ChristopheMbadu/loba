#!/usr/bin/env python3
"""Writes app/src/main/res/raw/game_loop.wav: the music that plays behind Loba's games.

An original loop made here from scratch, in the spirit of the app's other cues: a likembe (thumb piano)
figure on a five-note scale, a soft ngoma on the beat and a shaker between. Tails wrap round the end,
so the loop repeats without a click. Run from the android folder: python3 tools/make_music.py
"""
import math, random, struct, wave

RATE = 22050
BPM = 96
BEAT = 60 / BPM
BARS = 8
N = int(RATE * BEAT * 4 * BARS)
out = [0.0] * N
random.seed(11)

def hz(semitones_from_a4):
    return 440 * 2 ** (semitones_from_a4 / 12)

# C major pentatonic around middle C, as semitones from A4.
C4, D4, E4, G4, A4, C5, D5, E5, G5 = -9, -7, -5, -2, 0, 3, 5, 7, 10
C3, G2, A2, F2 = -21, -26, -24, -28

def add(start, samples):
    i = int(start * RATE)
    for k, v in enumerate(samples):
        out[(i + k) % N] += v

def likembe(note, vol=0.5, length=1.1):
    f = hz(note)
    n = int(length * RATE)
    s = []
    for k in range(n):
        t = k / RATE
        env = math.exp(-t * 4.2) * min(1, t * 400)
        # A metal tine: the fundamental, a soft octave and a slightly sharp, quickly fading overtone.
        v = math.sin(2 * math.pi * f * t) + 0.32 * math.sin(2 * math.pi * 2 * f * t) * math.exp(-t * 6)
        v += 0.18 * math.sin(2 * math.pi * 5.4 * f * t) * math.exp(-t * 22)
        s.append(v * env * vol)
    return s

def bass(note, vol=0.5, length=1.6):
    f = hz(note)
    n = int(length * RATE)
    return [math.sin(2 * math.pi * f * k / RATE) * math.exp(-k / RATE * 2.2) * min(1, k / RATE * 120) * vol for k in range(n)]

def ngoma(vol=0.5, low=70, high=130):
    n = int(0.32 * RATE)
    s, phase = [], 0.0
    for k in range(n):
        t = k / RATE
        phase += 2 * math.pi * (low + (high - low) * math.exp(-t * 26)) / RATE
        s.append(math.sin(phase) * math.exp(-t * 13) * vol)
    return s

def shaker(vol=0.12):
    n = int(0.07 * RATE)
    s, prev = [], 0.0
    for k in range(n):
        w = random.uniform(-1, 1)
        hp = w - prev  # crude high-pass: keeps the hiss, drops the thump
        prev = w
        s.append(hp * math.exp(-k / RATE * 55) * vol)
    return s

# Two four-bar phrases; each bar is eight half-beats. None is a rest.
A = [
    [E4, G4, A4, G4, C5, A4, G4, None],
    [D4, G4, A4, C5, A4, G4, E4, None],
    [E4, G4, C5, D5, C5, A4, G4, None],
    [D4, E4, G4, A4, G4, E4, D4, None],
]
B = [
    [G4, C5, E5, D5, C5, A4, G4, None],
    [A4, C5, D5, C5, A4, G4, E4, None],
    [G4, A4, C5, E5, D5, C5, A4, None],
    [G4, E4, D4, E4, G4, None, C4, None],
]
roots = [C3, A2, F2, G2, C3, A2, F2, G2]

for bar, line in enumerate(A + B):
    t0 = bar * 4 * BEAT
    add(t0, bass(roots[bar]))
    add(t0 + 2.5 * BEAT, bass(roots[bar] + 7, 0.28, 0.9))
    for step, note in enumerate(line):
        t = t0 + step * BEAT / 2
        if note is not None:
            add(t, likembe(note, 0.5 if step % 2 == 0 else 0.38))
            # A quiet answer an octave up now and then, like a second player.
            if step in (2, 5) and bar % 2 == 1:
                add(t + BEAT / 4, likembe(note + 12, 0.14, 0.6))
        if step % 2 == 1:
            add(t, shaker())
        add(t + BEAT / 4, shaker(0.05))
    add(t0, ngoma(0.5))
    add(t0 + 1.5 * BEAT, ngoma(0.3, 95, 150))
    add(t0 + 2 * BEAT, ngoma(0.42))
    add(t0 + 3.5 * BEAT, ngoma(0.26, 95, 150))

peak = max(abs(v) for v in out)
with wave.open("app/src/main/res/raw/game_loop.wav", "wb") as f:
    f.setnchannels(1)
    f.setsampwidth(2)
    f.setframerate(RATE)
    f.writeframes(b"".join(struct.pack("<h", int(v / peak * 0.8 * 32767)) for v in out))
print(f"game_loop.wav: {N / RATE:.1f} s")
