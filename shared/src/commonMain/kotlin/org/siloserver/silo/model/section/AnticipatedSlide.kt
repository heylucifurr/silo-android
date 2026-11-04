package org.siloserver.silo.model.section

/**
 * How a curated "Most anticipated" slide reads on a hero. Mirrors the server's
 * `availability` enum (`internal/sections/anticipated_availability.go`).
 *
 * A client branches on these values rather than on the `ext:` content-id prefix,
 * so it never depends on the server's internal id convention.
 */
object SectionAvailability {
    /** The library now has the title; the slide plays like any other item. */
    const val InLibrary = "in_library"

    /** A curated title the library lacks and whose copy has not arrived. */
    const val Unreleased = "unreleased"

    /** In the contract for a server that can tell a curated title is obtainable. */
    const val ReleasedUnowned = "released_unowned"
}

/**
 * A curated title the library does not own yet. The server sends `availability`
 * only on an anticipated slide, and sends anything other than `in_library` for
 * one it does not have, so the presence of the field is the signal.
 */
val SectionItem.isComingSoon: Boolean
    get() = availability != null && availability != SectionAvailability.InLibrary

/** A curated title that has since landed in the library; its slide plays. */
val SectionItem.isInLibraryAnticipated: Boolean
    get() = availability == SectionAvailability.InLibrary

/**
 * The `(media_type, tmdb_id)` pair the non-library detail page is keyed by, for
 * a coming-soon slide that carries a TMDB id. Null for an ordinary card and for
 * a slide the library owns (which opens its catalog item instead).
 */
val SectionItem.anticipatedDetailTarget: Pair<String, Int>?
    get() = if (isComingSoon) tmdbId?.takeIf { it > 0 }?.let { type to it } else null

/** The widest artwork a hero can draw, falling back to the poster. */
val SectionItem.heroArtworkUrl: String?
    get() = backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl?.takeIf { it.isNotBlank() }

/** The hero artwork's ThumbHash, matching whichever URL [heroArtworkUrl] chose. */
val SectionItem.heroArtworkThumbhash: String?
    get() = if (!backdropUrl.isNullOrBlank()) backdropThumbhash else posterThumbhash

private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * A `YYYY-MM-DD` calendar date as `Mon D, YYYY` (`Dec 16, 2026`), the shape the
 * web hero shows. Anything else — including a null or blank date — is null, so
 * the caller falls back to the bare "Coming soon" label rather than printing a
 * malformed date.
 */
fun formatComingSoonDate(releaseDate: String?): String? {
    val raw = releaseDate?.trim().orEmpty()
    val parts = raw.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year <= 0 || month !in 1..12 || day !in 1..31) return null
    return "${monthNames[month - 1]} $day, $year"
}

/**
 * The label a coming-soon slide shows in place of Play: "Coming soon · Dec 16,
 * 2026", or the bare "Coming soon" when the slide carries no usable date.
 */
val SectionItem.comingSoonLabel: String
    get() = formatComingSoonDate(releaseDate)?.let { "Coming soon · $it" } ?: "Coming soon"
