package com.brewtap.xbloom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class XBloomRecipeEncoderTest {
    @Test fun encodesEdisonRecipeIntoXBloomLayout() {
        val xid = byteArrayOf(69, 68, 73, 83, 79, 78, 0)
        val payload = XBloomRecipeEncoder.encodePayload(RecipeGenerator.edisonEthiopia(), xid)
        assertEquals(44, payload.size)
        assertArrayEquals(xid, payload.copyOfRange(0, 7))
        assertEquals(2, payload[7].toInt() and 0xFF)
        assertEquals(32, payload[8].toInt() and 0xFF)
        assertEquals(15, payload[41].toInt() and 0xFF)
        assertEquals(18, payload[42].toInt() and 0xFF)
        assertEquals(0x3A, payload[43].toInt() and 0xFF)
    }

    @Test fun crcMatchesStandardCheckVector() {
        assertEquals(0xA1, XBloomRecipeEncoder.crc8Maxim("123456789".encodeToByteArray()))
    }
}
