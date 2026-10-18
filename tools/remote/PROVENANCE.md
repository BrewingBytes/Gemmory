# Remote picture provenance

Every remote picture (#43) published to `pictures/v1/`: source, license, freeze settings and when it went out.
Rows are added by `./gradlew :shared:publishRemotePicture` (README → Publishing remote pictures). A withdrawn
picture keeps its row, marked withdrawn. Grids are never re-frozen: a fix is a new id. A picture made with
`-Pcolors=more` or `fewer` (#29) says so in its Freeze column.

| Picture | Id | Source | Author, date | License | Freeze | Added | Catalog |
|---|---|---|---|---|---|---|---|
