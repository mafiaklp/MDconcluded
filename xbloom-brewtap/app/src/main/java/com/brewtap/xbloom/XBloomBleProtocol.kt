package com.brewtap.xbloom

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object XBloomBleProtocol {
    fun crc16(data: ByteArray): Int {
        var crc = 0
        data.forEach { b ->
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x8408 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    private fun le(v: Int, n: Int): ByteArray = ByteArray(n) { i -> ((v ushr (i * 8)) and 0xFF).toByte() }

    fun buildType1(cmd: Int, data: ByteArray = byteArrayOf()): ByteArray = build(0x01, cmd, data)
    fun buildType2(cmd: Int, data: ByteArray = byteArrayOf()): ByteArray = build(0x02, cmd, data)

    private fun build(type: Int, cmd: Int, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x58, 0x01, type.toByte()))
        out.write(le(cmd, 2))
        out.write(le(data.size + 12, 4))
        out.write(0x01)
        out.write(data)
        val body = out.toByteArray()
        val crc = crc16(body)
        out.write(crc and 0xFF)
        out.write((crc ushr 8) and 0xFF)
        return out.toByteArray()
    }

    fun buildHandshake(): ByteArray = buildType1(8100, le(185, 4) + le(1, 4))
    fun buildBackHome(): ByteArray = buildType1(8022)
    fun buildBypass(recipe: BrewRecipe): ByteArray = buildType1(8102, le(0,4)+le(0,4)+le(recipe.doseGrams,4))
    fun buildSetCup(): ByteArray {
        fun f(v: Float) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()
        return buildType1(8104, f(110f) + f(90f))
    }

    fun encodeRecipe(recipe: BrewRecipe): ByteArray {
        val out = ByteArrayOutputStream()
        recipe.pours.forEachIndexed { i, p ->
            out.write(p.volumeMl and 0xFF)
            out.write(p.temperatureC and 0xFF)
            out.write(p.pattern.code and 0xFF)
            out.write(p.agitation and 0xFF)
            out.write((-p.pauseSeconds) and 0xFF)
            out.write(0)
            out.write(if (i == 0) recipe.grinderRpm and 0xFF else 0)
            out.write(p.flowRateTenthsMlPerSec and 0xFF)
        }
        val data = out.toByteArray()
        val ratio10 = kotlin.math.round(recipe.displayedRatio * 10.0).toInt()
        return byteArrayOf(data.size.toByte()) + data + byteArrayOf(recipe.grindSize.toByte(), ratio10.toByte())
    }

    fun buildDirectRecipePacket(recipe: BrewRecipe): ByteArray = buildType1(if (recipe.grindSize > 0) 8001 else 8004, encodeRecipe(recipe))
    fun commandCode(packet: ByteArray): Int = (packet[3].toInt() and 0xFF) or ((packet[4].toInt() and 0xFF) shl 8)
}
