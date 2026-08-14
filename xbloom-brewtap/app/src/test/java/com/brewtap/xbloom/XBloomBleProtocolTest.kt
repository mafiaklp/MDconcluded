package com.brewtap.xbloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XBloomBleProtocolTest {
    @Test fun frameMatchesCapturedCommitVector() {
        val packet = XBloomBleProtocol.frame(0x42, 0x1F, byteArrayOf(0x01))
        assertEquals("580101421f0c000000017fcf", packet.toHex())
    }

    @Test fun doseFrameCarriesRequestedDose() {
        val packet = XBloomBleProtocol.dose(15)
        assertEquals(0xA6, packet[3].toInt() and 0xff)
        assertEquals(15, packet[18].toInt() and 0xff)
    }

    @Test fun loadFramesNeverContainStartOpcodes() {
        val recipe = RecipeGenerator.edisonEthiopia().copy(doseGrams = 15)
        val frames = XBloomBleProtocol.loadFrames(recipe)
        assertEquals(listOf(0xA4,0xA6,0xA8,0x41), frames.map { it[3].toInt() and 0xff })
        assertFalse(frames.any { (it[3].toInt() and 0xff) in setOf(0x42,0x46,0x47) })
    }

    @Test fun armedStatusIsDetected() {
        val raw = byteArrayOf(0x58,0x02,0x07,0x57,0,0,0,0,0,0xC1.toByte(),0x1F,0,0)
        assertTrue(XBloomBleProtocol.isArmedNotification(raw))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
