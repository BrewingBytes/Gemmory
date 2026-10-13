# Bundled picture provenance

Every bundled picture's source image, its license and how its frozen grid was made. Grids are frozen once with
`./gradlew :shared:freezeBundledGrid` and never re-converted (design D13, eng D11). Re-freezing a shipped picture
changes its grid CRC and orphans saves made on it.

| Picture | Asset | Source | Author, date | License | Freeze |
|---|---|---|---|---|---|
| The Starry Night (starter) | `androidApp/src/main/assets/pictures/starter.gmrg` | `starter-starry-night.jpg`, the 1280 px rendition of [File:Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg](https://commons.wikimedia.org/wiki/File:Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg) (Wikimedia Commons, downloaded 2026-10-13) | Vincent van Gogh, 1889 | Public domain (author died 1890; faithful photo of a 2D work) | spec `starter-3:4` (24×32, 8 colors), focus 0.2 (cypress and swirl), seed 0x474D5259 |
