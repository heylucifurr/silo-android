package org.siloserver.silo.tv.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class TvSettingsCategoryTest {
    @Test
    fun railKeepsGeneralPlaybackSubtitlesInOrder() {
        // Diagnostics and Server are not offered in this build: Support
        // (diagnostics) and Connection → Server are hidden on phone, and the
        // Server pane's only focusable row was Manage Servers, which is the
        // server-switching entry this fork removes. About → App Version moved
        // into General so the build number stays reachable for support.
        assertEquals(
            listOf(
                TvSettingsCategory.General,
                TvSettingsCategory.Playback,
                TvSettingsCategory.Subtitles,
            ),
            TvSettingsCategory.entries,
        )
    }
}
