package com.brewtap.xbloom.photo

import com.brewtap.xbloom.domain.RoastLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeBagAnalyzerTest {
    @Test fun parsesCommonBagFieldsWithoutInventingMissingData() {
        val d = CoffeeBagAnalyzer.analyze("Ethiopia Guji / Natural / 2100m / jasmine berry / light roast")
        assertEquals("Ethiopia", d.country)
        assertEquals("natural", d.process)
        assertEquals(2100, d.altitudeM)
        assertEquals(RoastLevel.LIGHT, d.roastLevel)
        assertTrue(d.tastingNotes.contains("jasmine"))
        assertTrue(d.tastingNotes.contains("berry"))
    }

    @Test fun unknownFieldsStayUnknown() {
        val d = CoffeeBagAnalyzer.analyze("Mystery Coffee")
        assertEquals("", d.country)
        assertEquals("", d.process)
        assertEquals(RoastLevel.UNKNOWN, d.roastLevel)
    }
}
