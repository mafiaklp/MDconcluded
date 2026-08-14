package com.brewtap.xbloom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XBloomBleProtocolTest {
    @Test
    fun easyModeReferencePacketMatchesKnownVector() {
        val packet = XBloomBleProtocol.buildType2(11511, byteArrayOf(0x91.toByte(), 0x32, 0x78, 0x56))
        assertEquals("580102f72c100000000191327856ff58", packet.toHex())
    }

    @Test
    fun handshakeUsesKnownCommandAndPayload() {
        val packet = XBloomBleProtocol.buildHandshake()
        assertEquals(8100, XBloomBleProtocol.commandCode(packet))
        assertTrue(packet.size >= 20)
        assertArrayEquals(byteArrayOf(0xB9.toByte(), 0, 0, 0, 1, 0, 0, 0), packet.copyOfRange(10, 18))
    }

    @Test
    fun edisonRecipeUsesGrinderDirectCommand() {
        val recipe = RecipeGenerator.edisonEthiopia()
        val packet = XBloomBleProtocol.buildDirectRecipePacket(recipe)
        assertEquals(8001, XBloomBleProtocol.commandCode(packet))
        assertEquals(0x58, packet[0].toInt() and 0xFF)
        assertEquals(0x01, packet[2].toInt() and 0xFF)
    }

    @Test
    fun recipeBlobCarriesRatioAndGrinderTail() {
        val recipe = RecipeGenerator.edisonEthiopia()
        val blob = XBloomBleProtocol.encodeRecipe(recipe)
        assertEquals(recipe.grindSize, blob[blob.size - 2].toInt() and 0xFF)
        assertEquals(150, blob[blob.size - 1].toInt() and 0xFF)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
