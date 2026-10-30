package org.siloserver.silo.model.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCodeFormatTest {
    @Test
    fun groupsEightCharacterCodesFourPlusFour() {
        assertEquals("4821 7730", DeviceCodeFormat.display("4821-7730"))
        assertEquals("4821 7730", DeviceCodeFormat.display("48217730"))
        // Older servers issue letters; they are shown the same way.
        assertEquals("ABCD EFGH", DeviceCodeFormat.display("abcd-efgh"))
        // Anything else is shown as sent.
        assertEquals("WXYZ 12", DeviceCodeFormat.display("WXYZ-12"))
    }

    @Test
    fun normalizesTypedInputAndSpellsItOut() {
        assertEquals("48217730", DeviceCodeFormat.normalize(" 4821 77-30 "))
        assertEquals("4 8 2 1 7 7 3 0", DeviceCodeFormat.spoken("4821-7730"))
    }

    @Test
    fun activationAddressesFollowTheServerTheClientUses() {
        // The server advertises its own activation host; the client shows the
        // address it actually reaches that server on.
        assertEquals(
            "https://weflix.me/activate",
            DeviceCodeFormat.onServer("https://beta.weflix.me/activate", "https://weflix.me"),
        )
        assertEquals(
            "https://weflix.me/activate?code=44468983",
            DeviceCodeFormat.onServer("https://beta.weflix.me/activate?code=44468983", "https://weflix.me/"),
        )
        // Already the same origin: unchanged.
        assertEquals(
            "https://weflix.me/activate",
            DeviceCodeFormat.onServer("https://weflix.me/activate", "https://weflix.me"),
        )
        // A LAN client keeps its own origin, port and all.
        assertEquals(
            "http://192.168.1.5:8090/activate",
            DeviceCodeFormat.onServer("https://media.example.com/activate", "http://192.168.1.5:8090"),
        )
        // A server mounted under a path keeps the advertised path in full.
        assertEquals(
            "https://weflix.me/silo/activate",
            DeviceCodeFormat.onServer("https://beta.weflix.me/silo/activate", "https://weflix.me"),
        )
        // Anything that is not an absolute URL is left alone.
        assertEquals("", DeviceCodeFormat.onServer("", "https://weflix.me"))
        assertEquals("/activate", DeviceCodeFormat.onServer("/activate", "https://weflix.me"))
        assertEquals(
            "https://beta.weflix.me/activate",
            DeviceCodeFormat.onServer("https://beta.weflix.me/activate", "not a url"),
        )
    }

    @Test
    fun activateTextDropsHttpsButKeepsHttpAndPaths() {
        assertEquals(
            "silo.example.test/activate",
            DeviceCodeFormat.activateText("https://silo.example.test/activate", ""),
        )
        assertEquals(
            "http://192.168.1.5:8090/activate",
            DeviceCodeFormat.activateText("http://192.168.1.5:8090/activate", ""),
        )
        assertEquals(
            "media.example.com/silo/activate",
            DeviceCodeFormat.activateText("", "https://media.example.com/silo/activate?code=48217730"),
        )
        assertEquals("silo.example.test", DeviceCodeFormat.host("https://silo.example.test/activate"))
    }
}
