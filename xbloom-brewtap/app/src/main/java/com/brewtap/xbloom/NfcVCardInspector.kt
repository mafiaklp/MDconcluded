package com.brewtap.xbloom

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

data class CardDump(
    val uidHex: String,
    val blockCount: Int,
    val blockSize: Int,
    val dsfid: Int,
    val afi: Int,
    val bytes: ByteArray,
) {
    fun prettyHex(): String = buildString {
        appendLine("UID: $uidHex")
        appendLine("Blocks: $blockCount")
        appendLine("Block size: $blockSize")
        appendLine("DSFID: 0x%02X".format(dsfid))
        appendLine("AFI: 0x%02X".format(afi))
        appendLine()
        repeat(blockCount) { block ->
            val start = block * blockSize
            val end = minOf(start + blockSize, bytes.size)
            val hex = bytes.copyOfRange(start, end).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            appendLine("%03d: %s".format(block, hex))
        }
    }
}

class NfcVCardInspector {
    fun read(tag: Tag): CardDump {
        val tech = NfcV.get(tag) ?: throw IOException("Not an ISO15693 / NFC-V tag")
        tech.connect()
        try {
            val uid = tag.id ?: throw IOException("Tag has no UID")
            val sys = getSystemInfo(tech, uid)
            val bytes = readAllBlocks(tech, uid, sys.blockCount, sys.blockSize)
            return CardDump(
                uidHex = uid.joinToString(":") { "%02X".format(it.toInt() and 0xFF) },
                blockCount = sys.blockCount,
                blockSize = sys.blockSize,
                dsfid = sys.dsfid,
                afi = sys.afi,
                bytes = bytes,
            )
        } finally {
            try { tech.close() } catch (_: Exception) { }
        }
    }

    private data class SystemInfo(val blockCount: Int, val blockSize: Int, val dsfid: Int, val afi: Int)

    private fun getSystemInfo(tech: NfcV, uid: ByteArray): SystemInfo {
        val response = tech.transceive(byteArrayOf(0x22, 0x2B, *uid))
        if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("Get System Information failed")
        if (response.size < 14) throw IOException("Unexpected system info length ${response.size}")
        return SystemInfo(
            blockCount = (response[12].toInt() and 0xFF) + 1,
            blockSize = (response[13].toInt() and 0x1F) + 1,
            dsfid = response[10].toInt() and 0xFF,
            afi = response[11].toInt() and 0xFF,
        )
    }

    private fun readAllBlocks(tech: NfcV, uid: ByteArray, blockCount: Int, blockSize: Int): ByteArray {
        val out = ByteArray(blockCount * blockSize)
        var cursor = 0
        for (block in 0 until blockCount) {
            val response = tech.transceive(byteArrayOf(0x22, 0x20, *uid, block.toByte()))
            if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("Read failed at block $block")
            val payload = response.copyOfRange(1, response.size)
            if (payload.size < blockSize) throw IOException("Short block $block: ${payload.size} bytes")
            payload.copyOf(blockSize).copyInto(out, cursor)
            cursor += blockSize
        }
        return out
    }
}
