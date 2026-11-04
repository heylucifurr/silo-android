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
| `shared/.../commonTest/.../AnticipatedSlideTest.kt` | **New.** 9 tests. |
| `androidApp/.../androidUnitTest/.../HomeHeroMetadataTest.kt` | **New.** 10 tests. |
| `docs/design/hero-after.png` | **New.** The restyled hero, captured on the Pixel 8 Pro emulator. |
| `docs/design/hero-current-android.png` | **New.** The hero before this restyle, captured from the previous commit. |

### Endpoints used (no new ones)

- `GET /api/v2/home/sections` — the row and its cards (already used).
- `GET /api/v2/requests/detail/{media_type}/{tmdb_id}` — the coming-soon detail
  page, via the existing `RequestsApi.detail` and the existing `RequestDetailScreen`.

No new UI or player library: Media3 `ExoPlayer` (already a dependency, the same
pattern as `StartupSplashVideo`) and `ThumbhashImage`.

### Phone hero layout

The phone hero follows the restyle spec (`docs/design/hero-reference-prime.png`
was not in the repo, so the written spec was treated as authoritative):

- **Card**: 16 dp side inset, 20 dp top corners, square bottom corners, height =
  width × 0.78 (~4:3), no border; capped at 720 dp wide and centred on a tablet.
- **Media**: backdrop and trailer both fill the card and centre-crop
  (`ContentScale.Crop`, `RESIZE_MODE_ZOOM`), so a letterboxed trailer shows no
  bars. The backdrop stays up until the trailer's first frame renders, then the
  trailer crossfades in over 300 ms.
- **Bottom fade**: the card's bottom 45 % runs transparent → `SiloPageBackground`
  (`#111111`), the page's own colour, so the card dissolves rather than ending on
  an edge. The page also lays its near-invisible washes over that base, so a
  20 dp feather of the card's colour is carried into the page below to turn the
  last few levels into a ramp (see "Not done / limitations").
- **Title**: the item's clear logo (`logo_url`) at the card's bottom-left, 16 from
  the left, max 60 % of the card wide, max 64 high, aspect preserved; no logo →
  bold 24 sp title, 2 lines, subtle shadow. No strip behind it.
- **Metadata row** 8 below the logo, over the fade: a white `COMING SOON` pill
  (black bold 13), a `#3A3A3A` classification pill (white bold 13), then the
  runtime in white and the year and first genre in grey (15), separated by spacing
  alone. An in-library slide carries no status pill.
- **Second line** 6 below that: a calendar icon plus the release date, white 15.
- **Mute**: 40 × 40, radius 10, black 45 % with a 1 px white-25 % border, white
  20 icon, 12 from the card's top and right, on a 48 dp touch target.
- **Dots** 14 below the date, centred: white 18 × 6 active pill, grey 6 × 6
  inactive circles, width animated over 200 ms. Above 7 slides the edge dots
  shrink to 4 × 4.
- **No Play / More info button, no synopsis, no eyebrow.** On a phone the card is
  the target: tapping anywhere on it opens the slide — the title page for a title
  the library owns, the coming-soon page for one it does not. The card is one
  accessibility target announcing the title, its facts, its availability and its
  position.

TV is unchanged from the previous task: its Skyline marquee already fills and
centre-crops its backdrop (`ContentScale.Crop`), already dissolves into its own
sampled wash, and already shows the logo with a text fallback. Spec item 7 has no
TV surface — TV plays no trailer, so it has no mute control.



## Verification

Measured on a Pixel 8 Pro emulator against Luther's server, on the phone debug
APK built from this branch.

| # | Check | Result |
| --- | --- | --- |
| 1 | Side-by-side of the new hero next to the Prime reference, saved as `docs/design/hero-after.png` | **Partial.** `docs/design/hero-after.png` is saved (a settled frame with the trailer live). Neither `hero-reference-prime.png` nor `hero-current-android.png` was in the repo, so no side-by-side could be composed; `hero-current-android.png` was generated from the previous commit so the pair exists to compare. |
| 2 | A letterboxed trailer fills the card with no black bars | **Pass.** Row samples across the card's top at y=416–520 return mean luminance 100–108 with min 16 / max 245: image content, not a bar. `RESIZE_MODE_ZOOM` on the trailer and `ContentScale.Crop` on the backdrop. |
| 3 | No visible bottom edge on the card in dark mode | **Pass.** Before the feather the card's last row measured 17 and the page below it 23 — a hard 6/255 step. After the feather the ramp is 17→18→19→21→22→23 across 60 px, ≤2 levels per row. |
| 4 | Logo shows for items that have one; text fallback for one that does not | **Pass.** "Verity" renders its clear logo (cursive script); "Heart of the Beast", "The Beast" and "Other Mommy" render the bold 24 sp text fallback. |
| 5 | Mute button fully inside the card and toggles | **Pass.** The icon node sits at [1170,481][1230,541], putting the 40 dp control exactly 12 dp inside the card's top and right on a 48 dp touch target; tapping flips `Unmute trailer` → `Mute trailer`. |
| 6 | Dots and swipe behave as in spec items 8–9 | **Pass.** Five dots labelled "Go to item N of 5", centred 14 below the date, the active one the wider white pill. An explicit `input swipe` moved the carousel from "Item 5 of 5" to "Item 2 of 5", and the outgoing trailer's player is torn down (logcat shows it released as the next one starts). |
| 7 | Build + existing tests pass; warnings/lint not above baseline | **Pass.** Both debug APKs build. `shared` 2240 tests 0 failures; `androidApp` 953 tests 7 failures; `androidTvApp` 1348 tests 1 failure — all 8 pre-existing and unrelated (see below). No new compiler warnings. |
| 8 | silo-server `git status` unchanged | **Pass.** Clean, no stashes, HEAD still `d92f6b70f`. |

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
- **Neither reference image was in the repo.** `docs/design/hero-reference-prime.png`
  and `docs/design/hero-current-android.png` did not exist, so the written spec
  was treated as authoritative (as the task allows) and `hero-current-android.png`
  was generated from the previous commit for comparison.
- **The card's bottom needs a 20 dp feather.** The spec's fade lands on
  `SiloPageBackground` (`#111111`), but the page then lays its own washes over
  that base, so the page renders about 6 levels lighter where the card ends — a
  measurable hard step. The feather carries the card's colour into the page so
  the step becomes a ramp. Matching the page exactly would mean reproducing
  `siloPageBackdrop`'s radial highlight, whose position moves as Home scrolls, so
  the feather is the robust answer rather than a fixed colour.
- **The dot-overflow threshold is unspecified.** The spec gives the 4 × 4 edge-dot
  size but not how many slides trigger it; 7 was taken as the literal reading. The
  live row holds 5 slides, so the overflow path is implemented but not exercised
  by current data.
- **Tablet width is not verified.** The 720 dp cap and centring are implemented
  but no tablet AVD was run.

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
3. **TV scope.** This task's spec says to keep the TV layout and apply only items
   2, 3, 4 and 7 there. Items 2 (fill/centre-crop), 3 (dissolve into its wash) and
   4 (logo with text fallback) are already how the TV marquee behaves, and item 7
   has no TV surface because TV plays no trailer and so has no mute control — so
   TV was left untouched. The earlier task asked for a TV hero with the trailer
   and Play/Pause toggling it; that is still not built. Do you want it as a
   follow-up?
4. **Dot overflow threshold.** The spec gives the 4 × 4 edge-dot size but not how
   many slides trigger it. I used 7. If you have a number in mind (Prime's looks
   like 5), say so and I will change the constant.
5. **The phone hero has no call to action.** Per your instruction the phone
   hero has no Play / More info button and no synopsis — the card is the target.
   The earlier task's Step 2 asked for "Coming soon badge + More info" and
   "Play + More info"; this build follows your direction instead. Say the word if
   you want the buttons back for an in-library slide.
6. **No pause control on phone.** Prime's mobile hero has only the mute toggle,
   so the pause button is gone. Auto-advance still stops off screen, in the
   background, on battery saver and under reduce-motion.

