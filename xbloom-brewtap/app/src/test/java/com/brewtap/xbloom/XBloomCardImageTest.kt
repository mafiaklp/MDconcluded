package com.brewtap.xbloom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class XBloomCardImageTest {
    private val signature = hex(
        "5F 01 1D 79 56 BA 02 04 4F 5F 15 93 A7 15 AF 2C " +
        "71 8D 87 FD 0E 2C 3B 36 BF 30 AC 16 6A D9 D0 F1"
    )
    private val sampleRecipe = BrewRecipe(
        name = "TH0035 sample",
        doseGrams = 15,
        totalWaterMl = 225,
        grindSize = 55,
        grinderRpm = 120,
        temperatureC = 93,
        pours = listOf(
            BrewPour(40, 93, 35, PourPattern.SPIRAL, agitation = 1, pauseSeconds = 15),
            BrewPour(70, 92, 33, PourPattern.SPIRAL, agitation = 0, pauseSeconds = 10),
            BrewPour(75, 90, 33, PourPattern.SPIRAL, agitation = 0, pauseSeconds = 12),
            BrewPour(40, 90, 30, PourPattern.CENTERED, agitation = 0, pauseSeconds = 5),
        ),
    )

    @Test fun encoderReproducesKnownTH0035RecipeBytesExactly() {
        val xid = byteArrayOf(0x54,0x48,0x30,0x30,0x33,0x35,0x00)
        val expected = hex(
            "54 48 30 30 33 35 00 00 " +
            "20 28 5D 02 01 F1 0F 78 23 " +
            "46 5C 02 00 F6 00 00 21 " +
            "4B 5A 02 00 F4 00 00 21 " +
            "28 5A 00 00 FB 00 00 1E " +
            "0F 0F 11"
        )
        assertArrayEquals(expected, XBloomRecipeEncoder.encodePayloadForCard(sampleRecipe, xid, signature))
    }

    @Test fun cardImagePreservesSignatureAndOnlyReplacesRecipeRegion() {
        val original = ByteArray(160)
        signature.copyInto(original, 0)
        val xid = byteArrayOf(0x54,0x48,0x30,0x30,0x33,0x35,0x00)
        xid.copyInto(original, 32)
        val updated = XBloomCardImage.applyRecipe(original, sampleRecipe)
        val payload = XBloomRecipeEncoder.encodePayloadForCard(sampleRecipe, xid, signature)
        assertArrayEquals(signature, updated.copyOfRange(0, 32))
        assertEquals(original.size, updated.size)
        assertArrayEquals(payload, updated.copyOfRange(32, 32 + payload.size))
    }

    private fun hex(s: String): ByteArray = s.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
