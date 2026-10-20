# Picture sheet prompt (T44)

One ChatGPT image makes six pictures on one sheet. `publishPictureBatch` splits the sheet (README → Publishing a batch of pictures), so the layout below matters more than the wording. Every provenance row names this file; if you change the prompt, save it as a new file (`sheet-2x3-v2.md`) and put that name in the CSV's `prompt` column.

## Pick the aspect first

One aspect per sheet. Use the image size and layout for that aspect:

| Tiles | Image size | Layout | Tile before the crop |
|---|---|---|---|
| 1:1 (square) | 1536×1024 (landscape) | 3 columns × 2 rows | about 475×475 |
| 3:4 (portrait) | 1024×1024 (square) | 3 columns × 2 rows | about 310×475, cut to 310×410 |
| 4:3 (landscape) | 1024×1024 (square) | 2 columns × 3 rows | about 475×310, cut to 410×310 |

## The prompt

Fill in the `{…}` parts and paste the rest as is. Ask for one sheet per message.

```text
Create one {1536×1024 landscape | 1024×1024 square} image: a sheet of exactly 6 separate pictures arranged in a grid
of {3 columns and 2 rows | 2 columns and 3 rows}, with a plain pure-white gutter about 24 pixels wide between the
pictures and around the outside. Every picture fills its own cell edge to edge; nothing crosses a gutter, and the
gutters stay completely empty (no borders, shadows, captions, numbers or frames).

Theme: {theme, e.g. "a quiet autumn morning in the countryside"}.
The six pictures, in reading order (left to right, then top to bottom):
1. {subject}
2. {subject}
3. {subject}
4. {subject}
5. {subject}
6. {subject}

Style for all six: a painterly, softly lit scene for adults to relax with; a calm, harmonious palette of a few clear
color areas (large shapes, gentle gradients, not busy fine detail); one clear subject per picture; full-color
backgrounds (no white or empty backgrounds inside a picture). {Compose each picture for a square | portrait 3:4 |
landscape 4:3} frame, keeping the subject away from the edges.

Do not include: any text, letters or numbers; any real or recognizable person (faces turned away or at a distance are
fine); logos, brands or recognizable products, vehicles or buildings; characters from films, games or books; cartoon
mascots, toy or nursery styles, or anything aimed at children; violence, fear, nudity, alcohol or drugs.
```

## Before you write the CSV

- Run `./gradlew :shared:splitPictureSheet -Psheet=<sheet> -Pratio=<1:1|3:4|4:3>` and look at the six tiles in `shared/build/sheet-tiles/`. A sheet that doesn't split into six stops with a named error: regenerate it.
- Throw out any tile with text, a face, a recognizable design (lessons: a Vespa-like scooter was dropped) or a white background. Leave its row out of the CSV; the batch doesn't need all six.
