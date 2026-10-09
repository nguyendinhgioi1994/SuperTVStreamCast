package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.repository.parseThemeMode
import com.tuntech.supertvstreamcast.theme.TvThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {
    @Test fun storedNamesRoundTrip() {
        TvThemeMode.entries.forEach { assertEquals(it, parseThemeMode(it.name)) }
    }

    @Test fun missingOrUnknownValuesFollowTheSystem() {
        assertEquals(TvThemeMode.SYSTEM, parseThemeMode(null))
        assertEquals(TvThemeMode.SYSTEM, parseThemeMode(""))
        assertEquals(TvThemeMode.SYSTEM, parseThemeMode("SEPIA"))
    }
}
