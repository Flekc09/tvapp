package com.tvapp.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DeviceCountryTest {
    @Test fun mapsToIptvOrgCodes() {
        assertEquals("UK", deviceCountry(Locale.UK))
        assertEquals("US", deviceCountry(Locale.US))
        assertEquals("FR", deviceCountry(Locale.FRANCE))
        assertEquals("US", deviceCountry(Locale("en")))
    }
}
