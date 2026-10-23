# Share video ad: approved design (founder, 2026-10-23)

Round 3, new ticket "T53 (share video ad)", wave 1 after T50 (#137) and before T48 (#135). Came out of the T50 /plan-design-review.

## Files here
- `share-video-mock-fit.mp4`: **the approved video** (16.8 s; mock at 540×960, the real one is 1080×1920 at 30 fps).
- `video.html`: the animated mock that rendered it. Serve this folder (`python3 -m http.server 8765`) and open `http://127.0.0.1:8765/video.html` (file:// blocks reading the picture's pixels). Buttons: VIP, Layout. `?t=<s>` freezes a moment, `?raw` hides the controls. The approved layout is the "fill" code path with `out = 1` (fitted, no zoom).
- `fit-sheet.png`: stills of the approved video (4 s, 10 s, 14 s, end card).
- `frame-D.png`, `frame-E.png`: full-size stills of the row and the end card (the earlier top-aligned picture; the approved one is centered).
- `board.png`: the first options board (today's frame, A, B, C). `share-video-mock.mp4` / `vf-sheet.png`: the rejected zoom-and-pan version.
- `badge/en_badge_web_generic.png`: Google's official "Get it on Google Play" badge (from play.google.com/intl/en_us/badges), used unmodified.
- `render.sh`: renders one frame at 15 fps for the MP4.

**This repo copy** (T53, #145) keeps the approved video, `video.html` and what it loads (fonts, icon, badge, the wave), `fit-sheet.png`, `frame-D.png`, `frame-E.png` and `board.png`. The rejected zoom-and-pan video, its stills (`vf-*`, `vt-*`), frames A-C, the earlier `board-2.png` and the rendered `mp4frames/` stay in `~/.gstack/projects/Gemmory/designs/t50-share-frame-20261023/`.

## Decisions
- **D1:** an end card plus a Gemmory row on every frame. Row: app icon, "Gemmory" (Bricolage 600 44px), "Your photos in gems" (Figtree 28px, 80%), and the official Play badge at the right (336×130 px incl. its built-in clear space), on a 150 px `scrim` band at 55%. It replaces "Made with Gemmory".
- **D2/D3:** no zoom and no pan. The whole picture fits at full width, centered vertically in the area above the row. The title (Bricolage 600 60px) and the app line "Gemmory · Photo Diamond Art" (Figtree 36px) sit over a dark fade (`scrim` 0 → 78% across 420 px) at the bottom of that area. The band behind is unchanged: the finished picture blurred to ~40 px at 45% brightness.
- **End card:** after the 2 s sparkle hold, it fades in over 0.3 s and holds ~1.8 s. On the same blurred band at about 30% brightness: the finished picture (760 px wide, shadowed), the app icon 180 px, "Gemmory" Bricolage 600 104px, "Your photos in gems" Figtree 44px, and the official badge at 520 px. Timing: 1 s intro + 12 s build + 2 s hold + 1.8 s end card (FramePlan 450 → 504 frames).
- **D4 VIP:** neither the row nor the end card (the "No watermark on your videos" perk stays as written).
- **D5:** its own ticket, right after T50. Encoder change: the Test Lab smoke, and T47's R1 "9:16 share unchanged" check updated deliberately. The PNG fallback, the progress share and T47's 4:5 clips follow the same frame. T48/T49 translate "Your photos in gems" and use the localized Play badge per language. T51 shrinks to the ≤7 s feed cut.

- **Superseded for founder clips (2026-10-23):** T47's clips get their own feed-safe frame (#144, `../clip-cta-20261023/`); T53 covers the player's share only.
- **Filed:** T53 = #145 (2026-10-23).
- **Built:** T53 (#145), 2026-10-23. Agent defaults the founder approved from the emulator stills: a tall picture fits a 760px square on the end card (570×760, so the block fits 1920), the end-card block is centered vertically (a wide picture starts at y≈325, the mock 300), and a progress share's end card shows the canvas as it stands (founder pick).
