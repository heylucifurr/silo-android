package org.siloserver.silo.common.startup

import org.siloserver.silo.model.server.ServerEntry
import org.siloserver.silo.network.ServerRegistry

/**
 * The server this build ships pointed at.
 *
 * These clients are the first-party app for one deployment, so a fresh install
 * has nothing to ask: the address screen would only ever be answered with this
 * value. Adopting it up front means the auth flow opens on Sign In.
 */
const val DEFAULT_SERVER_URL = "https://weflix.me"

/**
 * The server to route the auth flow with, adopting [DEFAULT_SERVER_URL] when
 * nothing is saved yet.
 *
 * Returns the already-active entry untouched when one exists, so a server the
 * person added, renamed, or switched to is never overwritten. The seeded entry
 * is an ordinary [ServerEntry]: it shows in the server list and can be renamed
 * or removed like any other, and the next launch re-seeds it if it is gone.
 *
 * Returns null only when persisted state could not be read back. Seeding there
 * would bury a decode failure under a plausible-looking entry, and callers that
 * purge data derived from registry absence (`OrphanedServerDataPurger`) treat an
 * empty registry as untrustworthy while `stateLoadFailed` is set.
 */
suspend fun ServerRegistry.defaultServerOrNull(): ServerEntry? {
    activeEntry.value?.let { return it }
    if (stateLoadFailed) return null
    val id = addOrUpdate(DEFAULT_SERVER_URL)
    // addOrUpdate persists and re-applies, so the entry is active by now; the
    // lookup is belt-and-braces for a registry that only inserts.
    return activeEntry.value ?: entries.value.firstOrNull { it.id == id }
}
