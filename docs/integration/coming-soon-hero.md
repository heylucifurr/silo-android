# Coming Soon hero — server contract as the Android clients see it

Read-only study of `silo-server` (branch `GPU-node`, commit `d92f6b70f`) against this client repo
(`silo-android`, branch `Development`). Field names are listed without values.

## 1. Endpoints the web Home uses

| Purpose | Endpoint | Notes |
| --- | --- | --- |
| Home row layout + cards | `GET /api/v2/home/layout`, `GET /api/v2/home/sections` | v2 is what the Android clients already call. `include_items` / `item_limit` are capability-gated. |
| One row's cards | `GET /api/v2/home/sections/{id}/items` | Used by row hydration. |
| Home capabilities | `GET /api/v2/home/capabilities` | `section_item_limit_max`. |
| Non-library title detail | `GET /api/v2/requests/detail/{media_type}/{tmdb_id}` | The web `/title/:mediaType/:tmdbId` page (`TitleDetail.tsx` → `useRequestMediaDetail` → `getRequestMediaDetail`). |
| Admin curation (web admin only) | `GET/PUT /api/v2/admin/anticipated`, `POST /api/v2/admin/anticipated/search` | Not needed by clients. |

There is no separate "coming soon" endpoint. The hero's data arrives on the ordinary section card,
inside `GET /api/v2/home/sections`.

## 2. The hero flag

The row flag is `featured` (boolean), carried on each section:

- `internal/sections` `PageSection.Featured`, exposed by `sectionResponse.Featured` /
  `resolvedSectionResponse.Featured` (`json:"featured"`).
- Set in admin → Sections; the web reads `section.featured` and, for the first non-empty featured
  row, renders `HeroBanner` instead of a row (`web/src/pages/Home.tsx`, `homeSectionQueue`).

## 3. The coming-soon item

Section type is `anticipated` (`sections.SectionAnticipated`). The curated list lives in the
`anticipated` package and is matched to the library at read time by TMDB id — no catalog content id
is stored.

`fetchAnticipated` resolves each curated entry:

- **Library owns it** → the real catalog item, so the slide plays like any other card.
- **Library lacks it** → a synthetic item whose content id is `ext:<media_type>-<tmdb_id>`
  (`sections.ExternalContentIDPrefix`). Its artwork paths are **TMDB path fragments**, not asset-store
  keys.

The handler then overrides three things on such a card (`internal/api/handlers/sections.go`):

- `availability` = `unreleased` (`sections.AnticipatedAvailability`; `in_library` for a real item,
  `released_unowned` is in the contract but the server never sends it today).
- `tmdb_id` = the TMDB id.
- `poster_url` / `backdrop_url` / `logo_url` are rewritten to TMDB's image CDN
  (`https://image.tmdb.org/t/p/<size><path>`).

### Card fields the v2 client sees (`internal/apiv2/catalog_types.go`)

| Field | Meaning |
| --- | --- |
| `availability` | `unreleased` \| `released_unowned` \| `in_library`. Absent on every other card. |
| `tmdb_id` | Present only on an anticipated slide the library lacks. |
| `in_watchlist`, `requestable` | Present only on an anticipated slide the library lacks. |
| `hero_video_url` | Presigned, long-lived clip URL. Absent when the server has no clip. |
| `release_date` | `YYYY-MM-DD`. |
| `logo_url`, `backdrop_url`, `poster_url` | Title art / backdrop / poster. |
| `runtime`, `year`, `genres`, `content_rating`, `overview`, `rating_tmdb`, `rating_imdb` | Ordinary card metadata, filled from the TMDB snapshot. |

`availability`, `tmdb_id`, `in_watchlist`, `requestable` and `hero_video_url` are `json:"-"`
on the frozen v1 struct; only `/api/v2` emits them.

## 4. Coming soon vs Play

The web branches on `availability`, not on the id prefix (`HeroBanner.tsx`):

- `availability` absent, or `in_library` → **Play** (`buildMediaPlayHref`).
- `availability` = `unreleased` → **"Coming soon · <release_date>"** badge.
- `availability` = `released_unowned` → a Request action.
- **More Info** for a non-library item → `/title/{type}/{tmdb_id}`, i.e. the
  `getRequestMediaDetail` endpoint.

## 5. Trailer source

`hero_video_url` is a presigned URL served by silo-server, produced by `internal/heroclips`
(a generated short clip of the title's trailer, stored in the clip blob store). For curated titles
the library does not own, the same package publishes into `anticipated_clips`
(`heroclips.NewAnticipatedPublisher`). It is **not** a YouTube key and **not** a TMDB video.

The web plays it as a muted `<video>` behind the backdrop, fading the still out to black first
(`HeroTrailerLayer`, `heroTrailer.ts`). Absent or failing clip → backdrop only, no error.

## 6. Root cause of "item not found"

`SectionItem` in `shared/src/commonMain/.../model/section/SectionModels.kt` decodes only the fields
it declares. It declared **no** `availability`, `tmdb_id`, `release_date`, `hero_video_url`,
`in_watchlist` or `requestable`, so all of them were silently dropped.

With `availability` gone, every consumer had to treat the card as an ordinary library item:

- Phone `HomeScreen` → `onItemClick(contentId)` → `Route.ItemDetail(contentId)`.
- TV `TvHomeScreen` → `onItemClick(contentId)` → TV item detail.

`contentId` is `ext:<type>-<tmdb_id>`, which is not a catalog row, so the catalog read answers 404 and
the detail screen shows "not found". The row also rendered as an ordinary poster row because the
phone Home has no hero surface and TV renders every section as a row in the feed.

**No blocker.** Everything the hero needs is on `/api/v2/home/sections`, which the clients already
call, and the non-library detail endpoint is already implemented client-side
(`RequestsApi.detail` → `RequestMediaDetail`). Nothing here is web-only, cookie-only or
server-rendered.

## 7. Known discrepancy (server-side, out of scope)

Admin → Sections reported "Coming Soon · Most anticipated · 12 titles" while the curated list held 5.
Recorded under "Questions for Luther" in `checkpoints/CURRENT.md`; the server is read-only for this
change.
