# Static font instances for the build-time tools

AWT (the JVM's text drawing) only draws a variable font at its default instance, and that isn't the weight the app
draws: Bricolage Grotesque's default is 800 and Geologica's is 100, while titles are 600. `StoreFrames` (the listing
captions) and `FrameTextFitTest` (frame text widths) read these 600 instances instead. They're never in the app.

Made from `androidApp/src/main/res/font/` with fontTools 4.54.1 `varLib.instancer` (T49, #136):

| File | Source | Axes |
|---|---|---|
| `bricolage_grotesque_600.ttf` | `bricolage_grotesque.ttf` | wght 600, opsz 96, wdth 100 |
| `geologica_600.ttf` | `geologica.ttf` | wght 600, CRSV 0, SHRP 0, slnt 0 |
| `figtree_600.ttf` | `figtree.ttf` | wght 600 |
| `onest_600.ttf` | `onest.ttf` | wght 600 |

All four are under the SIL Open Font License 1.1 (no Reserved Font Names), as their sources; the licenses are in
`androidApp/fonts-licenses/`. Remake them when a source font changes.
