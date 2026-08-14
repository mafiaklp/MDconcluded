package com.brewtap.xbloom

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object XBloomBleProtocol {
    const val SERVICE_UUID = "0000e0ff-3c17-d293-8e48-14fe2e4da212"
    const val COMMAND_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val STATUS_UUID = "0000ffe2-0000-1000-8000-00805f9b34fb"
    const val LOAD_SEQ = 0x1F

    fun crc16Kermit(data: ByteArray): Int {
        var crc = 0
        data.forEach { b ->
            crc = crc xor (b.toInt() and 0xff)
            repeat(8) { crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x8408 else crc ushr 1 }
        }
        return crc and 0xffff
    }

    fun frame(cmd: Int, seq: Int = LOAD_SEQ, payload: ByteArray): ByteArray {
        val body = byteArrayOf(0x58,0x01,0x01,cmd.toByte(),seq.toByte(),0,0,0,0) + payload
        val total = body.size + 2
        body[5] = (total and 0xff).toByte(); body[6] = ((total ushr 8) and 0xff).toByte()
        val crc = crc16Kermit(body)
        return body + byteArrayOf((crc and 0xff).toByte(), ((crc ushr 8) and 0xff).toByte())
    }

    fun sessionStart() = frame(0xA4, payload = byteArrayOf(0x01,0xB9.toByte(),0,0,0,1,0,0,0))
    fun statusQuery() = frame(0x56, payload = byteArrayOf(0x01))
    fun dose(dose: Int): ByteArray { val p=ByteArray(13);p[0]=1;p[9]=dose.toByte();return frame(0xA6,payload=p) }
    fun stageTemps(t1: Float = 110f, t2: Float = 90f): ByteArray = frame(0xA8,payload=ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).put(1).putFloat(t1).putFloat(t2).array())

    private fun patternBytes(p: BrewPour): Pair<Int,Int> = when(p.pattern) {
        PourPattern.CENTERED -> 0x00 to 0x01
        PourPattern.CIRCULAR -> 0x01 to 0x00
        PourPattern.SPIRAL -> 0x02 to if (p.agitation != 0) 0x02 else 0x00
    }

    fun pours(recipe: BrewRecipe): ByteArray {
        val segments=ByteArrayOutputStream()
        recipe.pours.forEachIndexed { i,p ->
            val (pat,agit)=patternBytes(p)
            var left=p.volumeMl
            while(left>127){segments.write(byteArrayOf(127,p.temperatureC.toByte(),pat.toByte(),agit.toByte()));left-=127}
            segments.write(byteArrayOf(left.toByte(),p.temperatureC.toByte(),pat.toByte(),agit.toByte(),((256-p.pauseSeconds) and 0xff).toByte(),0,(if(i==0)recipe.grinderRpm else 0).toByte(),p.flowRateTenthsMlPerSec.toByte()))
        }
        val body=segments.toByteArray()
        val ratio10=(recipe.totalWaterMl.toDouble()/recipe.doseGrams*10.0).roundToInt().coerceIn(0,255)
        val payload=byteArrayOf(0x01,body.size.toByte())+body+byteArrayOf((if(recipe.grindSize==0)0xFE else recipe.grindSize).toByte(),ratio10.toByte())
        return frame(if(recipe.grindSize==0)0x44 else 0x41,payload=payload)
    }

    fun loadFrames(recipe:BrewRecipe)=listOf(sessionStart(),dose(recipe.doseGrams),stageTemps(),pours(recipe))
    fun isArmedNotification(data:ByteArray):Boolean{
        if(data.size<12||data[0].toInt()!=0x58||(data[3].toInt() and 0xff)!=0x57)return false
        val marker=data.indexOf(0xC1.toByte())
        return marker>=0&&marker+1<data.size&&(data[marker+1].toInt() and 0xff)==0x1F
    }

    // Compatibility for the earlier prototype client; all now route to the verified load protocol.
    fun buildHandshake()=sessionStart()
    fun buildBackHome()=statusQuery()
    fun buildBypass(recipe:BrewRecipe)=dose(recipe.doseGrams)
    fun buildSetCup()=stageTemps()
    fun buildDirectRecipePacket(recipe:BrewRecipe)=pours(recipe)
    fun commandCode(packet:ByteArray):Int=packet.getOrNull(3)?.toInt()?.and(0xff) ?: -1
}
