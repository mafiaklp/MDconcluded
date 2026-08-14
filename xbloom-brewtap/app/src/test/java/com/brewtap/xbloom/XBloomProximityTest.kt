package com.brewtap.xbloom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XBloomProximityTest {
    @Test
    fun strongRssiCountsAsTapProximity() {
        assertTrue(XBloomBleClient.isNearEnough(-48, -58))
        assertTrue(XBloomBleClient.isNearEnough(-58, -58))
    }

    @Test
    fun weakRssiDoesNotCountAsTapProximity() {
        assertFalse(XBloomBleClient.isNearEnough(-72, -58))
    }
}
