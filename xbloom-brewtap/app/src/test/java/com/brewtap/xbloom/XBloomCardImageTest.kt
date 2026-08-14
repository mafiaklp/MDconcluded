package com.brewtap.xbloom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class XBloomCardImageTest {
    private val signature = hex(
        "1E DA FE 57 B4 1E C6 E1 41 5B 5B 99 67 80 B8 D8 " +
        "9B B2 39 A1 01 75 D8 C1 32 6A E8 93 A1 C1 1B C9"
    )
    private val sampleRecipe = BrewRecipe(
        name = "XB0001 sample",
        doseGrams = 15,
        totalWaterMl = 255,
        grindSize = 65,
        grinderRpm = 120,
        temperatureC = 93,
        pours = listOf(
            BrewPour(60, 93, 30, PourPattern.CIRCULAR, agitation = 2, pauseSeconds = 15),
            BrewPour(65, 93, 35, PourPattern.CENTERED, agitation = 2, pauseSeconds = 15),
            BrewPour(65, 93, 35, PourPattern.SPIRAL, agitation = 0, pauseSeconds = 15),
            BrewPour(65, 93, 35, PourPattern.CENTERED, agitation = 0, pauseSeconds = 5),
        ),
    )

    @Test fun encoderReproducesKnownXB0001RecipeBytesExactly() {
        val xid = byteArrayOf(0x58,0x42,0x30,0x30,0x30,0x31,0x00)
        val expected = hex(
            "58 42 30 30 30 31 00 00 " +
            "20 3C 5D 01 02 F1 0F 78 1E " +
            "41 5D 00 02 F1 00 00 23 " +
            "41 5D 02 00 F1 00 00 23 " +
            "41 5D 00 00 FB 00 00 23 " +
            "19 11 E8"
        )
        assertArrayEquals(expected, XBloomRecipeEncoder.encodePayloadForCard(sampleRecipe, xid, signature))
    }

    @Test fun cardImagePreservesSignatureAndOnlyReplacesRecipeRegion() {
        val original = ByteArray(128)
        signature.copyInto(original, 0)
        val xid = byteArrayOf(0x58,0x42,0x30,0x30,0x30,0x31,0x00)
        xid.copyInto(original, 32)
        val updated = XBloomCardImage.applyRecipe(original, sampleRecipe)
        val payload = XBloomRecipeEncoder.encodePayloadForCard(sampleRecipe, xid, signature)
        assertArrayEquals(signature, updated.copyOfRange(0, 32))
        assertEquals(original.size, updated.size)
        assertArrayEquals(payload, updated.copyOfRange(32, 32 + payload.size))
    }

    private fun hex(s: String): ByteArray = s.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
