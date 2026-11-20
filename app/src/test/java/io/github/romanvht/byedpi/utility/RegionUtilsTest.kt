package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionUtilsTest {

    @Test
    fun mobileNetworkComesFirst() {
        assertEquals("TR", RegionUtils.pickCountry("tr", "ru", "DE", "US"))
    }

    @Test
    fun fallsBackInOrder() {
        assertEquals("RU", RegionUtils.pickCountry("", "ru", "DE", "US"))
        assertEquals("DE", RegionUtils.pickCountry(null, null, "DE", "US"))
        assertEquals("US", RegionUtils.pickCountry(null, null, "001", "US"))
        assertNull(RegionUtils.pickCountry(null, " ", "001", ""))
    }

    @Test
    fun countryCodes() {
        assertTrue(RegionUtils.isCountryCode("IR"))
        assertFalse(RegionUtils.isCountryCode("ZZ"))
        assertFalse(RegionUtils.isCountryCode("tr"))
        assertFalse(RegionUtils.isCountryCode("TUR"))
    }
}
