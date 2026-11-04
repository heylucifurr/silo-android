package org.siloserver.silo.model.section

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Coming Soon hero contract: a curated "Most anticipated" slide arrives on
 * an ordinary v2 section card carrying `availability` and `tmdb_id`, and every
 * other card must keep decoding exactly as before.
 */
class AnticipatedSlideTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decodeItem(body: String): SectionItem =
        json.decodeFromString(SectionItem.serializer(), body)

    private fun ordinary(id: String): SectionItem =
        decodeItem("{\"content_id\":\"$id\",\"type\":\"movie\",\"title\":\"X\"}")

    private fun anticipated(id: String, extra: String): SectionItem =
        decodeItem(
            "{\"content_id\":\"$id\",\"type\":\"movie\",\"title\":\"X\"," +
                "\"availability\":\"unreleased\",\"tmdb_id\":1$extra}",
        )

    @Test
    fun decodesAnUnownedAnticipatedSlide() {
        val slide = decodeItem(
            """
            {"content_id":"ext:movie-1234","type":"movie","title":"Heat 2","year":2026,
             "runtime":170,"genres":["Action"],"content_rating":"R",
             "release_date":"2026-12-16","availability":"unreleased","tmdb_id":1234,
             "in_watchlist":true,"requestable":true,
             "backdrop_url":"https://image.tmdb.org/t/p/w780/x.jpg",
             "logo_url":"https://image.tmdb.org/t/p/w300/l.png",
             "hero_video_url":"https://silo.invalid/clips/x.mp4"}
            """.trimIndent(),
        )

        assertEquals("unreleased", slide.availability)
        assertEquals(1234, slide.tmdbId)
        assertEquals("2026-12-16", slide.releaseDate)
        assertEquals("https://silo.invalid/clips/x.mp4", slide.heroVideoUrl)
        assertEquals(true, slide.inWatchlist)
        assertEquals(true, slide.requestable)

        assertTrue(slide.isComingSoon)
        assertFalse(slide.isInLibraryAnticipated)
        assertEquals("movie" to 1234, slide.anticipatedDetailTarget)
        assertEquals("Coming soon · Dec 16, 2026", slide.comingSoonLabel)
        assertEquals("https://image.tmdb.org/t/p/w780/x.jpg", slide.heroArtworkUrl)
    }

    @Test
    fun anInLibrarySlideIsNotComingSoon() {
        val inLibrary = decodeItem(
            """
            {"content_id":"movie:heat-1995","type":"movie","title":"Heat","year":1995,
             "availability":"in_library","tmdb_id":949}
            """.trimIndent(),
        )

        assertFalse(inLibrary.isComingSoon)
        assertTrue(inLibrary.isInLibraryAnticipated)
        // It has a catalog row, so it must open that row, not a TMDB title page.
        assertNull(inLibrary.anticipatedDetailTarget)
    }

    @Test
    fun anOrdinaryCardIsUntouchedByTheNewFields() {
        val plain = ordinary("movie:heat-1995")

        assertNull(plain.availability)
        assertNull(plain.tmdbId)
        assertNull(plain.heroVideoUrl)
        assertFalse(plain.isComingSoon)
        assertNull(plain.anticipatedDetailTarget)
        assertEquals("X", plain.title)
    }

    @Test
    fun anUnknownFieldOnAnotherRowDoesNotBreakDecoding() {
        val section = json.decodeFromString(
            ResolvedSection.serializer(),
            """
            {"id":"row/a","section_type":"recently_added","title":"Recently Added",
             "featured":false,"items":[
               {"content_id":"movie:heat-1995","type":"movie","title":"Heat",
                "something_the_client_has_never_heard_of":{"nested":[1,2,3]}}]}
            """.trimIndent(),
        )

        assertEquals(1, section.items.size)
        assertEquals("Heat", section.items.single().title)
        assertFalse(section.featured)
    }

    @Test
    fun aComingSoonSlideWithoutAUsableDateFallsBackToTheBareLabel() {
        // No date at all, an empty date, free text, and a month outside 1..12.
        val slides = listOf(
            anticipated("ext:movie-1", ""),
            anticipated("ext:movie-1", ",\"release_date\":\"\""),
            anticipated("ext:movie-1", ",\"release_date\":\"soon\""),
            anticipated("ext:movie-1", ",\"release_date\":\"2026-13-01\""),
        )

        for (slide in slides) {
            assertEquals("Coming soon", slide.comingSoonLabel)
        }
    }

    @Test
    fun theDateAloneIsOfferedForAHeroThatAlreadySaysComingSoon() {
        assertEquals("Dec 16, 2026", anticipated("ext:movie-1", ",\"release_date\":\"2026-12-16\"").comingSoonDateLabel)
        // No usable date, and an ordinary card, both offer nothing.
        assertNull(anticipated("ext:movie-1", "").comingSoonDateLabel)
        assertNull(ordinary("movie:1").comingSoonDateLabel)
    }

    @Test
    fun aComingSoonSlideWithoutATmdbIdHasNoDetailTarget() {
        val noId = decodeItem(
            "{\"content_id\":\"ext:movie-1\",\"type\":\"movie\",\"title\":\"X\"," +
                "\"availability\":\"unreleased\"}",
        )

        assertTrue(noId.isComingSoon)
        assertNull(noId.anticipatedDetailTarget)
    }

    @Test
    fun theHeroFlagSplitsTheRowOutOfTheFeed() {
        val hero = ResolvedSection(
            id = "coming-soon",
            sectionType = "anticipated",
            title = "Coming Soon",
            featured = true,
            items = listOf(anticipated("ext:movie-1", "")),
        )
        val row = ResolvedSection(
            id = "recent",
            sectionType = "recently_added",
            title = "Recently Added",
            items = listOf(ordinary("movie:1")),
        )

        val split = listOf(hero, row).splitFeatured()

        assertEquals("coming-soon", split.featured?.id)
        assertEquals(listOf("recent"), split.rest.map { it.id })
    }

    @Test
    fun anEmptyFeaturedRowIsNotAHeroAndNoRowIsLeftBehind() {
        val emptyFeatured = ResolvedSection(
            id = "coming-soon",
            sectionType = "anticipated",
            title = "Coming Soon",
            featured = true,
            items = emptyList(),
        )
        val row = ResolvedSection(
            id = "recent",
            sectionType = "recently_added",
            title = "Recently Added",
            items = listOf(ordinary("movie:1")),
        )

        val split = listOf(emptyFeatured, row).splitFeatured()

        assertNull(split.featured)
        assertEquals(listOf("coming-soon", "recent"), split.rest.map { it.id })
    }
}
