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
    fun aMovieReadsYearRuntimeGenreAndRating() {
        val line = heroMetadataLine(
            item(year = 2026, runtime = 100, genres = listOf("Action", "Crime"), contentRating = "r"),
        )

        assertEquals("2026 · 1h 40m · Action · R", line)
    }

    @Test
    fun anEpisodeLeadsWithItsPositionAndDropsTheGenre() {
        val line = heroMetadataLine(
            item(
                type = "episode",
                year = 2026,
                runtime = 55,
                genres = listOf("Drama"),
                contentRating = "TV-MA",
                seasonNumber = 3,
                episodeNumber = 7,
            ),
        )

        assertEquals("S3 · E7 · 55 min · TV-MA", line)
    }

    @Test
    fun aCardWithNothingToSayProducesAnEmptyLine() {
        assertEquals("", heroMetadataLine(item()))
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
