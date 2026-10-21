# Sound provenance

Every sound the app plays, where it came from and its license (T41, #82), and the music (T42, #83, below). All the sounds are synthesized in-house by
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

## Music (T42, #83)

Six calm tracks from OpenGameArt, each dedicated to the public domain under
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) on its page (checked 2026-10-21): no credit is required,
and the authors are named here anyway. Picked by the founder ("all") from a listening page on 2026-10-21. Each was
converted with ffmpeg 8 (libopus) from the file on its page:
`ffmpeg -i <source> -af loudnorm=I=-20:TP=-2,aresample=48000 -c:a libopus -b:a 48k -map_metadata -1 music_<n>.ogg`
(Ogg Opus, 48 kHz stereo, −20 LUFS integrated, −2 dBTP). Not edited otherwise. The app picks them at random, never the
same one twice in a row; `music_1` plays three times in a row (`MusicController`).

| File | Track | Author | Source page (downloaded file, SHA-256 prefix) | Length, size | License |
|---|---|---|---|---|---|
| `music_1.ogg` | Heavenly Loop | isaiah658 | [opengameart.org/content/heavenly-loop](https://opengameart.org/content/heavenly-loop) (`Heavenly Loop.flac`, `1324464c995635ce`) | 0:34, 385 KB | CC0 1.0 |
| `music_2.ogg` | Contemplation | Joth | [opengameart.org/content/contemplation-0](https://opengameart.org/content/contemplation-0) (`Contemplation.mp3`, `283962d3a975c936`) | 2:00, 1.07 MB | CC0 1.0 |
| `music_3.ogg` | Calm Piano 1 (Vaporware) | cynicmusic (The Cynic Project, cynicmusic.com, pixelsphere.org; credit requested, not required) | [opengameart.org/content/calm-piano-1-vaporware](https://opengameart.org/content/calm-piano-1-vaporware) (`003_Vaporware_2.mp3`, `1697fa1b7ed6f3fe`) | 2:45, 1.04 MB | CC0 1.0 |
| `music_4.ogg` | Calm Ambient 3 (Lifewave 2k) | cynicmusic (The Cynic Project) | [opengameart.org/content/calm-ambient-3-lifewave-2k](https://opengameart.org/content/calm-ambient-3-lifewave-2k) (`006_lifeWave2k_0.mp3`, `f6a796483aee743d`) | 3:07, 1.20 MB | CC0 1.0 |
| `music_5.ogg` | Calm Relax 1 (Synthwave 421k) | cynicmusic (The Cynic Project) | [opengameart.org/content/calm-relax-1-synthwave-421k](https://opengameart.org/content/calm-relax-1-synthwave-421k) (`007_Synthwave_421k.mp3`, `5d5cb73be5a1e3e4`) | 7:54, 2.91 MB | CC0 1.0 |
| `music_6.ogg` | The Budding of Consciousness (Yoiyami Blue Series No. 4) | Yoiyami | [opengameart.org/content/the-budding-of-consciousness-…](https://opengameart.org/content/the-budding-of-consciousness-%E2%80%93-cc0-ambient-minimalist-theme-yoiyami-blue-series-%E2%80%93-no4) (`the_budding_of_consciousness.wav`, `c879c57f4353c169`) | 3:44, 1.46 MB | CC0 1.0 |

A new or changed track gets a row here in the same change.
