# Coming Soon hero — handoff

Repo: **silo-android** (Android phone/tablet + Android TV). Server was read-only;
`git status` in `silo-server` is clean.

Branch: `Development`.

## What changed

A home row the administrator flags **Hero banner** in admin → Sections now renders
as a full-width hero carousel at the top of Home on phone, and its curated
"Most anticipated" slides stop resolving to "not found".

### Root cause of "not found"

`SectionItem` decoded only the fields it declared, so `availability`, `tmdb_id`,
`release_date`, `hero_video_url`, `in_watchlist` and `requestable` were dropped.
With `availability` gone, every consumer treated the card as an ordinary library
item and navigated to `Route.ItemDetail(contentId)`. An unowned slide's content id
is `ext:<media_type>-<tmdb_id>`, which is not a catalog row, so the catalog read
answered 404.

### Files

| File | Change |
| --- | --- |
| `shared/.../model/section/SectionModels.kt` | `SectionItem` decodes `availability`, `tmdb_id`, `release_date`, `hero_video_url`, `in_watchlist`, `requestable`. |
| `shared/.../model/section/AnticipatedSlide.kt` | **New.** `isComingSoon`, `isInLibraryAnticipated`, `anticipatedDetailTarget`, `heroArtworkUrl`, `heroArtworkThumbhash`, `comingSoonLabel`, `formatComingSoonDate`. |
| `androidApp/.../screens/home/HomeHeroCarousel.kt` | **New.** The hero: an inset, rounded 16:9 card laid out like Prime Video's mobile hero, holding the muted trailer, the title art bottom-left and a mute toggle top-right; chips, metadata, availability line and page dots beneath; 8 s auto-advance; `heroClipUrl`. |
| `androidApp/.../screens/home/HomeScreen.kt` | Splits the featured row out of the feed, renders the hero, routes coming-soon taps to the request detail. |
| `androidApp/.../screens/MainScreen.kt` | Wires `onComingSoonClick` → `Route.RequestDetail`. |
| `androidTvApp/.../screens/home/TvHomeScreen.kt` | Routes coming-soon Select to the TV request detail. |
| `androidTvApp/.../shell/TvMainShell.kt` | Wires `onComingSoonClick` → `TvMainRoute.RequestDetail`. |
| `androidTvApp/.../components/TvFocusMarqueeModel.kt` | The focus marquee's badge line reads `COMING SOON · <date>` for an unowned slide. |
| `docs/integration/coming-soon-hero.md` | **New.** The server contract as this client sees it. |
| `shared/.../commonTest/.../AnticipatedSlideTest.kt` | **New.** 8 tests. |
| `androidApp/.../androidUnitTest/.../HomeHeroMetadataTest.kt` | **New.** 8 tests. |

### Endpoints used (no new ones)

- `GET /api/v2/home/sections` — the row and its cards (already used).
- `GET /api/v2/requests/detail/{media_type}/{tmdb_id}` — the coming-soon detail
  page, via the existing `RequestsApi.detail` and the existing `RequestDetailScreen`.

No new UI or player library: Media3 `ExoPlayer` (already a dependency, the same
pattern as `StartupSplashVideo`) and `ThumbhashImage`.

### Phone hero layout

Luther asked for Prime Video's mobile hero rather than a port of the web hero, so
the phone hero is:

- an inset, rounded **16:9 card** (16 dp side inset, 20 dp corners) — not a
  full-bleed frame;
- the backdrop with the muted trailer playing over it, the title logo (or title
  text) at the card's bottom-left, a mute toggle at its top-right;
- beneath the card, one line of chips and metadata (`[PG-13] 1h 41m · 2026`),
  then the availability line (`Coming soon · Sep 19, 2026`) for a title the
  library does not own, then the page dots, centred;
- **no Play / More info button, no synopsis, no "FEATURED — No. 0N" eyebrow.**
  On a phone the card is the target: tapping anywhere on it opens the slide —
  the title page for a title the library owns, the coming-soon page for one it
  does not. The card is one accessibility target announcing the title, its
  facts, its availability and its position.

This deliberately diverges from the task's Step 2 ("Coming soon badge + More
info", "Play + More info") on Luther's instruction.


## Verification

Measured on a Pixel 8 Pro emulator against Luther's server, on the phone debug
APK built from this branch.

| # | Check | Result |
| --- | --- | --- |
| 1 | Hero shows the admin's list in admin order; dots/counter match the count | **Pass.** 5 dots labelled "Go to item N of 5", centred under the card; the card's own label ends "Item N of 5". The API returns **5** items. |
| 2 | Trailer autoplays muted; mute works; backdrop-only when there is no trailer | **Pass.** Frames differ by 1.76 MB between captures (video playing); the mute control appears only once a clip is ready and flips `Unmute trailer` → `Mute trailer`; a slide with no clip shows no mute control and keeps its backdrop. |
| 3 | Tapping a non-library slide opens coming-soon details (no "not found") | **Pass.** Tapping the card opens the title page: title, year · runtime, genres, classification, request status, overview, director. |
| 4 | A slide the library owns opens its normal details, where Play lives | **Pass.** Slide 4 ("Verity", `availability=in_library`) carries no "Coming soon" line, and tapping the card opens the item page with Play / Favorite / Watchlist / Watched / Download. Play itself was verified in this session (`SiloPlayback: Playback player created … live instance count = 1`, frames differ by 757 KB). |
| 5 | Hero flag off → no hero, no broken row | **Not verified on device** (needs an admin change). Covered by unit test `anEmptyFeaturedRowIsNotAHeroAndNoRowIsLeftBehind`; with no flagged row `splitFeatured()` returns null and the feed is untouched. |
| 6 | All other rows, library pages and playback unchanged | **Pass.** Continue Watching (with progress), Trending this week and the bottom nav render as before. |
| 7 | TV: full remote/D-pad navigation through the hero and into the rows | **Not verified.** No TV AVD has a signed-in session. |
| 8 | Build and existing tests pass; warnings/lint not above baseline | **Pass.** Both debug APKs build. `shared` 2239 tests 0 failures; `androidApp` 953 tests 7 failures; `androidTvApp` 1348 tests 1 failure — all 8 pre-existing and unrelated (see below). No new compiler warnings. |
| 9 | silo-server `git status` unchanged | **Pass.** Clean, no stashes, HEAD still `d92f6b70f`. |

### Pre-existing failures (not caused by this change)

Proven by stashing this work and re-running the same tests on pristine `HEAD`:

- `androidApp`, 7 tests — Windows `file://C:\...` URI construction
  (`MobileDownloadVisibilityTest`, `OfflinePlaybackTracksTest`,
  `ReaderFileResolverTest`, `ReaderViewModelReaderTargetSourceTest`).
- `androidTvApp`, 1 test — `TvHomeSectionsTest.editorButtonsCenterTheirContent…`
  reads a source file and asserts on `\n`, but the Windows checkout is CRLF.

## Not done / limitations

- **Android TV has no hero carousel and no trailer autoplay.** TV keeps its
  Skyline focus marquee: the featured row is still an ordinary row, D-pad
  left/right moves through it and the marquee + ambient backdrop follow focus.
  What was added is the `COMING SOON · <date>` badge and the Select fix. TV
  trailer playback and a dedicated TV hero were not implemented.
- **`hero_video_url` arrives site-relative.** The server's `artworkurl` resolver
  returns `/api/v2/artwork/...`, which a browser resolves against its origin and
  a player cannot — ExoPlayer tried to open it as a local file and failed with
  `ENOENT`. The client now resolves it against the active server through the
  existing `resolvePlaybackStreamUrl`, the same helper playback uses. This is a
  client-side accommodation of a server URL shape, not a server fix.
- Trailer autoplay pause conditions are wired for off-screen, background,
  battery saver and reduce-motion; only the off-screen and background paths were
  observed on device.
- Slide 4 ("Verity") is in the library and has no clip; several other slides do.
  The clip generator is server-side and out of scope.

## Questions for Luther

1. **Admin reports "12 titles", the API returns 5.** Admin → Sections shows
   "Coming Soon · Most anticipated · 12 titles" while the curated list holds 5,
   and `GET /api/v2/home/sections` returns **5** cards for the row. The `12` is a
   server-side count; the server is out of scope for this change.
2. **Should any `featured` row be a hero, or only the Coming Soon row?** I took
   the literal reading: every flagged row renders as the hero carousel and is
   removed from the rows below, matching the web hero and the TV marquee. On
   phone this changes a previously ordinary row into a hero, so a flagged library
   row would now look like a hero rather than a poster row. Say the word if the
   hero should be limited to `section_type = anticipated`.
3. **TV scope.** The spec asks for a TV hero across the top with the trailer and
   Play/Pause toggling it. TV keeps its focus marquee today. Do you want a
   dedicated TV hero carousel with trailer playback as a follow-up?
4. **The phone hero has no call to action.** Per your instruction the phone
   hero has no Play / More info button and no synopsis — the card is the target.
   The task's Step 2 asked for "Coming soon badge + More info" and "Play + More
   info"; this build follows your direction instead. Say the word if you want the
   buttons back for an in-library slide.
5. **No pause control on phone.** Prime's mobile hero has only the mute toggle,
   so the pause button is gone. Auto-advance still stops off screen, in the
   background, on battery saver and under reduce-motion.

