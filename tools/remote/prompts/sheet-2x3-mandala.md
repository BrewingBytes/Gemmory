# Mandala sheet prompt

`sheet-2x3.md` with the theme and style lines changed for symmetric, decorative mandalas (batch 2026-12-b). The
layout, gutters and the "Do not include" list are the same, so `publishPictureBatch` splits these sheets the same way.
Rows made with it carry `prompt` = `prompts/sheet-2x3-mandala.md` in the CSV.

Mandalas here are decorative patterns only: no deities, no religious or spiritual symbols (no Om, lotus deities,
crosses, stars of David, crescents, yantras with script).

```text
Create one 1536×1024 landscape image: a sheet of exactly 6 separate pictures arranged in a grid of 3 columns and 2
rows, with a plain pure-white gutter about 24 pixels wide between the pictures and around the outside. Every picture
fills its own cell edge to edge; nothing crosses a gutter, and the gutters stay completely empty (no borders, shadows,
captions, numbers or frames).

Theme: {theme, e.g. "mandalas inspired by flowers"}.
The six pictures, in reading order (left to right, then top to bottom):
1. {mandala}
2. {mandala}
3. {mandala}
4. {mandala}
5. {mandala}
6. {mandala}

Style for all six: one large, perfectly symmetric decorative mandala centered in each picture, filling most of a square
frame, with bold, clearly separated color areas (petals, rings and rays you can tell apart), smooth gradients inside
each shape and no tiny fine-line detail; a calm, harmonious palette; a full-color background behind each mandala (a
deep or soft color, never white or empty). Purely ornamental pattern for adults to relax with.

Do not include: any text, letters, numbers or script; religious or spiritual symbols or figures of any faith; any
person, face, logo or brand; cartoon mascots, toy or nursery styles, or anything aimed at children; violence, fear,
nudity, alcohol or drugs.
```
