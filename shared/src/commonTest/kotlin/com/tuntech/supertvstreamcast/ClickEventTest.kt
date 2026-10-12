package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.analytics.EVENT_NAME_MAX
import com.tuntech.supertvstreamcast.analytics.PARAM_VALUE_MAX
import com.tuntech.supertvstreamcast.analytics.clickEventName
import com.tuntech.supertvstreamcast.analytics.clickParams
import com.tuntech.supertvstreamcast.domain.TvBrand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickEventTest {
    @Test fun namesAreSnakeCaseWithThePrefix() {
        assertEquals("ec_dock_remote", clickEventName("dock_REMOTE"))
        assertEquals("ec_remote_volume_up", clickEventName("remote_VOLUME_UP"))
        assertEquals("ec_iptv_sheet_xtream", clickEventName("iptv sheet--XTREAM"))
    }

    @Test fun namesFitFirebaseLimits() {
        val name = clickEventName("a_very_long_area_name_with_an_even_longer_element_name")
        assertTrue(name.length <= EVENT_NAME_MAX)
        assertTrue(!name.endsWith("_"))
    }

    @Test fun paramsUseLowerCaseEnumsAndDropNulls() {
        assertEquals(mapOf("brand" to "samsung", "on" to "true"),
            clickParams(listOf("brand" to TvBrand.SAMSUNG, "product" to null, "on" to true)))
        assertEquals(PARAM_VALUE_MAX, clickParams(listOf("app" to "x".repeat(300)))["app"]!!.length)
    }
}
