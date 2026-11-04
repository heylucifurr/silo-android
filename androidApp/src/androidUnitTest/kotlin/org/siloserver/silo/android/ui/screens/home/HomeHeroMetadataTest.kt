package org.siloserver.silo.android.ui.screens.home

import org.siloserver.silo.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The hero's metadata line: the facts the web hero shows, from the card's own fields. */
class HomeHeroMetadataTest {

    private fun item(
        type: String = "movie",
        year: Int = 0,
        runtime: Int? = null,
        genres: List<String> = emptyList(),
        contentRating: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) = SectionItem(
        contentId = "movie:heat-1995",
        type = type,
        title = "Heat",
        year = year,
        runtime = runtime,
        genres = genres,
        contentRating = contentRating,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

    @Test
    fun aMovieReadsRuntimeYearAndGenre() {
        val item = item(year = 2026, runtime = 100, genres = listOf("Action", "Crime"), contentRating = "r")

        assertEquals("1h 40m · 2026 · Action", heroMetadataLine(item))
        // The classification is the chip beside the line, not part of it.
        assertEquals("R", heroRatingChip(item))
    }

    @Test
    fun anEpisodeLeadsWithItsPositionAndDropsTheYearAndGenre() {
        val item = item(
            type = "episode",
            year = 2026,
            runtime = 55,
            genres = listOf("Drama"),
            contentRating = "TV-MA",
            seasonNumber = 3,
            episodeNumber = 7,
        )

        assertEquals("S3 · E7 · 55 min", heroMetadataLine(item))
        assertEquals("TV-MA", heroRatingChip(item))
    }

    @Test
    fun aCardWithNothingToSayProducesAnEmptyLineAndNoChip() {
        val item = item()

        assertEquals("", heroMetadataLine(item))
        assertNull(heroRatingChip(item))
    }

    @Test
    fun aBlankOrMissingClassificationIsNoChip() {
        assertNull(heroRatingChip(item(contentRating = null)))
        assertNull(heroRatingChip(item(contentRating = "   ")))
        assertEquals("PG-13", heroRatingChip(item(contentRating = "pg-13")))
    }

    @Test
    fun theCardIsAnnouncedAsOneTarget() {
        val comingSoon = item(
            year = 2026,
            runtime = 100,
            genres = listOf("Action"),
            contentRating = "R",
        ).copy(
            contentId = "ext:movie-1",
            title = "Heat 2",
            availability = "unreleased",
            releaseDate = "2026-12-16",
        )

        assertEquals(
            "Coming Soon. Heat 2. 1h 40m · 2026 · Action. Coming soon · Dec 16, 2026. Item 2 of 5",
            heroAccessibilityLabel("Coming Soon", comingSoon, position = 1, count = 5),
        )
    }

    @Test
    fun runtimesFormatAtTheHourBoundary() {
        assertNull(heroRuntimeLabel(null))
        assertNull(heroRuntimeLabel(0))
        assertNull(heroRuntimeLabel(-5))
        assertEquals("45 min", heroRuntimeLabel(45))
        assertEquals("1h", heroRuntimeLabel(60))
        assertEquals("1h 40m", heroRuntimeLabel(100))
        assertEquals("2h", heroRuntimeLabel(120))
    }

    @Test
    fun aSiteRelativeClipUrlResolvesAgainstTheServer() {
        // The server sends the clip as `/api/v2/artwork/...`; a browser resolves
        // that against its own origin, a player cannot.
        assertEquals(
            "https://silo.invalid/api/v2/artwork/hero-clips/abc/v1.mp4",
            heroClipUrl("/api/v2/artwork/hero-clips/abc/v1.mp4", "https://silo.invalid/"),
        )
    }

    @Test
    fun anAbsoluteClipUrlIsLeftAlone() {
        assertEquals(
            "https://cdn.invalid/clip.mp4",
            heroClipUrl("https://cdn.invalid/clip.mp4", "https://silo.invalid"),
        )
        // Even with no known server address.
        assertEquals(
            "https://cdn.invalid/clip.mp4",
            heroClipUrl("https://cdn.invalid/clip.mp4", null),
        )
    }

    @Test
    fun aRelativeClipUrlWithoutAServerAddressIsNotPlayable() {
        assertNull(heroClipUrl("/api/v2/artwork/hero-clips/abc/v1.mp4", null))
        assertNull(heroClipUrl("/api/v2/artwork/hero-clips/abc/v1.mp4", "  "))
    }

    @Test
    fun noClipUrlMeansTheBackdropStandsAlone() {
        assertNull(heroClipUrl(null, "https://silo.invalid"))
        assertNull(heroClipUrl("", "https://silo.invalid"))
        assertNull(heroClipUrl("   ", "https://silo.invalid"))
    }
}
