package com.brewtap.xbloom

import java.io.ByteArrayOutputStream

object XBloomRecipeEncoder {
    private const val GRIND_SIZE_OFFSET = 40

    fun encodePayload(recipe: BrewRecipe, xid: ByteArray): ByteArray {
        val withoutCrc = encodeWithoutCrc(recipe, xid)
        return withoutCrc + crc8Maxim(withoutCrc).toByte()
    }

    fun encodePayloadForCard(recipe: BrewRecipe, xid: ByteArray, signature: ByteArray): ByteArray {
        require(signature.size == 32) { "xBloom card signature must be exactly 32 bytes" }
        val withoutCrc = encodeWithoutCrc(recipe, xid)
        val crc = crc8Maxim(signature + withoutCrc)
        return withoutCrc + crc.toByte()
    }

    private fun encodeWithoutCrc(recipe: BrewRecipe, xid: ByteArray): ByteArray {
        require(xid.size == 7) { "xBloom XID must be exactly 7 bytes" }
        require(recipe.pours.isNotEmpty()) { "recipe must contain at least one pour" }
        require(recipe.pours.size <= 31) { "pour count exceeds card format" }
        require(recipe.grindSize in 41..120) { "grind size out of range" }
        require(recipe.doseGrams in 1..31) { "dose must fit lower 5 bits" }
        require(recipe.grinderRpm in 0..255) { "RPM out of range" }
        require(recipe.machineRatio in 1..255) { "machine ratio out of range" }

        val out = ByteArrayOutputStream()
        out.write(xid)
        out.write(recipe.cupType and 0x0F)
        out.write((recipe.pours.size shl 3) and 0xFF)

        recipe.pours.forEachIndexed { index, pour ->
            require(pour.volumeMl in 0..255)
            require(pour.temperatureC in 0..255)
            require(pour.flowRateTenthsMlPerSec in 0..255)
            require(pour.agitation in 0..3)
            require(pour.pauseSeconds in 0..360)

            out.write(pour.volumeMl)
            out.write(pour.temperatureC)
            out.write(pour.pattern.code)
            out.write(pour.agitation)

            val waitMinutes = if (pour.pauseSeconds > 255) pour.pauseSeconds / 60 else 0
            val waitSeconds = if (pour.pauseSeconds > 255) pour.pauseSeconds % 60 else pour.pauseSeconds
            out.write(if (waitSeconds == 0) 0 else (256 - waitSeconds) and 0xFF)

            val minuteBits = (waitMinutes shl 5) and 0xE0
            if (index == 0) {
                out.write(minuteBits or (recipe.doseGrams and 0x1F))
                out.write(recipe.grinderRpm)
            } else {
                out.write(minuteBits)
                out.write(0)
            }
            out.write(pour.flowRateTenthsMlPerSec)
        }

        out.write(recipe.grindSize - GRIND_SIZE_OFFSET)
        out.write(recipe.machineRatio)
        return out.toByteArray()
    }

    fun crc8Maxim(data: ByteArray): Int {
        var crc = 0x00
        for (byte in data) {
            var inByte = byte.toInt() and 0xFF
            repeat(8) {
                val mix = (crc xor inByte) and 0x01
                crc = crc ushr 1
                if (mix != 0) crc = crc xor 0x8C
                inByte = inByte ushr 1
            }
        }
        return crc and 0xFF
    }
}
