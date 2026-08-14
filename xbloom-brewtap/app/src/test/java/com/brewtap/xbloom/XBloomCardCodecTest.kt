package com.brewtap.xbloom

import org.junit.Assert.assertEquals
import org.junit.Test

class XBloomCardCodecTest {
    @Test fun decodesRealEdisonCard() {
        val hex = """
            5F011D7956BA02044F5F1593A715AF2C718D87FD0E2C3B36BF30AC166AD9D0F1
            544830303335000020285D0201F10F7823465C0200F60000214B5A0200F4000021285A0000FB00001E0F0F11
        """.trimIndent().replace("\n", "").replace(" ", "")
        val raw = ByteArray(160)
        for (i in hex.indices step 2) raw[i/2] = hex.substring(i, i+2).toInt(16).toByte()
        val d = XBloomCardCodec.decode(raw)
        assertEquals("TH0035", d.recipe.name)
        assertEquals(15, d.recipe.doseGrams)
        assertEquals(225, d.recipe.totalWaterMl)
        assertEquals(55, d.recipe.grindSize)
        assertEquals(120, d.recipe.grinderRpm)
        assertEquals(15, d.storedRatio)
        assertEquals(listOf(40,70,75,40), d.recipe.pours.map { it.volumeMl })
        assertEquals(listOf(93,92,90,90), d.recipe.pours.map { it.temperatureC })
    }
}
