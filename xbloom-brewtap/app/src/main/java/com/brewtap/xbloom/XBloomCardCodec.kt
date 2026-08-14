package com.brewtap.xbloom

object XBloomCardCodec {
    data class Decoded(val xid: ByteArray, val recipe: BrewRecipe, val storedRatio: Int, val crc: Int)

    fun decode(raw: ByteArray): Decoded {
        require(raw.size >= 44) { "Card data too short" }
        var i = 32
        val xid = raw.copyOfRange(i, i + 7); i += 7
        val cupType = raw[i++].toInt() and 0x0F
        val pourCount = (raw[i++].toInt() and 0xFF) ushr 3
        require(pourCount in 1..16) { "Invalid pour count $pourCount" }
        val pours = mutableListOf<BrewPour>()
        var dose = 15
        var rpm = 120
        repeat(pourCount) { idx ->
            val volume = raw[i++].toInt() and 0xFF
            val temp = raw[i++].toInt() and 0xFF
            val patternCode = raw[i++].toInt() and 0xFF
            val agitation = raw[i++].toInt() and 0xFF
            val pauseByte = raw[i++].toInt() and 0xFF
            val doseMinute = raw[i++].toInt() and 0xFF
            val rpmByte = raw[i++].toInt() and 0xFF
            val flow = raw[i++].toInt() and 0xFF
            if (idx == 0) { dose = doseMinute and 0x1F; rpm = rpmByte }
            val minuteBits = (doseMinute ushr 5) and 0x07
            val sec = if (pauseByte == 0) 0 else 256 - pauseByte
            val pause = minuteBits * 60 + sec
            val pattern = when (patternCode) { 2 -> PourPattern.SPIRAL; 1 -> PourPattern.CIRCULAR; else -> PourPattern.CENTERED }
            pours += BrewPour(volume, temp, flow, pattern, agitation, pause)
        }
        val storedGrind = raw[i++].toInt() and 0xFF
        val ratio = raw[i++].toInt() and 0xFF
        val crc = raw[i].toInt() and 0xFF
        val total = pours.sumOf { it.volumeMl }
        val recipe = BrewRecipe(
            name = xid.takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.US_ASCII),
            doseGrams = dose,
            totalWaterMl = total,
            grindSize = storedGrind + 40,
            grinderRpm = rpm,
            temperatureC = pours.first().temperatureC,
            pours = pours,
            cupType = cupType,
        )
        return Decoded(xid, recipe, ratio, crc)
    }
}
