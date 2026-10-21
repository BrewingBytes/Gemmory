# Sound provenance

Every sound the app plays, where it came from and its license (T41, #82). All of them are synthesized in-house by
`tools/make_sounds.py` from sine waves and noise: no recordings, samples or third-party audio, so there is nothing to
license or credit. The script is deterministic (fixed noise seeds); rerun it with `python3 tools/make_sounds.py` to
rewrite the same bytes into `androidApp/src/main/res/raw`, or pass a folder to write them there instead for listening.
22050 Hz, mono, 16-bit WAV, each normalized to 90% peak; the app sets the volume (`SoundCues`).

A new or changed sound gets a row here in the same change.

| File | Event (design system, Haptics and sound) | Made by | Voice | Source, license |
|---|---|---|---|---|
| `gem_place_1..5.wav` | Gem placed (tap); a tool's batch clicks | `gem_place(take)`, noise seeds 10-14 | a cut-glass "tink" (3 inharmonic partials, 2.2-2.9 kHz) over a low knock on the tray, ~75 ms; each take its own stone | in-house synthesis, BrewingBytes, 2026-10-21; no third-party audio |
| `gem_drag_1..3.wav` | Gem placed (drag) | `gem_drag(take)`, noise seeds 20-22 | the first three stones, shorter (~40 ms), darker and with less knock | in-house synthesis, BrewingBytes, 2026-10-21; no third-party audio |
| `color_done_1..3.wav` | Color finished | `color_done(take)`, noise seeds 30-32 | the felt-flap thump (a low falling sine and muffled noise) and a small rising run of three glass pings, ~240 ms | in-house synthesis, BrewingBytes, 2026-10-21; no third-party audio |
| `milestone_1..3.wav` | Progress milestone (#35) | `milestone(take)` | a short G4 bell (a fifth above the finish chime): plain, with a third, with an octave, ~1.2 s | in-house synthesis, BrewingBytes, 2026-10-21; no third-party audio |
| `finish_chime_1..3.wav` | Picture finished (#57) | `finish_chime(take)` | the low C4 bell, three metals, with a faint high shimmer, ~1.8 s; the app layers its fifth and octave | in-house synthesis, BrewingBytes, 2026-10-21; no third-party audio |
