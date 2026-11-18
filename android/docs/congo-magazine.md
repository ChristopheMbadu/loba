# Congo magazine

The map game's book now opens a paged reader: Places, History, Culture, Facts.
Each page scrolls vertically; swipe or use the previous/next controls to change
articles. Changing section returns to its first article. All content and artwork
are bundled for offline use; only external source links require a connection.

Seven bilingual feature articles live in `ios/Loba/Resources/congo-magazine.json`.
They include a place, introduction, explanation, reveal question and source URL.
`match` replaces the corresponding legacy card by its English title; other
existing map cards remain available. The independent bundle prevents older
cached map responses from replacing the new editorial content.

Sources reviewed 18 November 2026: UNESCO Salonga, Virunga, Mbanza Kongo and
Congolese rumba records; Institute of Natural Sciences Ishango exhibit; The Met
Kuba Double Prestige Panel; AGU Congo Basin hydrology paper. Each feature links
to its source. Ishango's proposed function is explicitly distinguished from
observed marks. Existing non-feature cards were retained, not comprehensively
re-researched in this change.

Every one of the 37 bundled pages has its own original ChatGPT-generated
editorial illustration. `magazine-art.json` assigns each stable article ID a
unique sheet/tile. Ten PNG sheets contain 37 individual compositions; the
native views display only the assigned region, never the whole contact sheet.
Artwork is interpretive, not a photograph, precise map, or reconstruction.
No generic illustration is reused as a fallback for future downloaded cards.
Android decodes off the main thread and bounds its sheet cache to three images.
Labels sit below the art to keep subjects visible. Original generation files
remain in the workspace; bundled copies are stored alongside app code.

Run `python3 scripts/check-magazine-art.py` from the iOS checkout to check
complete coverage, unique artwork, valid tile sizes and Android asset parity.

Android mirrors this in `ui/CongoMagazine.kt` and `assets/congo-magazine.json`.
Keep both JSON files identical when editing features.

Validation: Swift all-source typecheck and Kotlin all-source Compose compiler.
Device visual checks and installation require the Mac to be unlocked; they
were not completed in this change.
