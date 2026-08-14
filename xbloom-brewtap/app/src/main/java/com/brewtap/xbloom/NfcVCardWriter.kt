package com.brewtap.xbloom

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

data class WriteResult(
    val uidHex: String,
    val verifiedBytes: Int,
)

class NfcVCardWriter {
    fun writeRecipe(tag: Tag, recipe: BrewRecipe): WriteResult {
        val tech = NfcV.get(tag) ?: throw IOException("This is not an ISO15693 / NFC-V tag")
        tech.connect()
        try {
            val uid = tag.id ?: throw IOException("Tag has no UID")
            val system = getSystemInfo(tech, uid)
            require(system.blockSize == 4) { "Unsupported block size ${system.blockSize}; expected 4 bytes" }
            require(system.blockCount > 19) { "Card is too small (${system.blockCount} blocks)" }

            val signature = readBytes(tech, uid, startBlock = 0, blockCount = 8)
            require(signature.size == 32) { "Could not read 32-byte xBloom signature" }
            val xid = readBytes(tech, uid, startBlock = 8, blockCount = 2).copyOfRange(0, 7)
            val payload = XBloomRecipeEncoder.encodePayloadForCard(recipe, xid, signature)
            require(payload.size % system.blockSize == 0) { "Payload must align to ${system.blockSize}-byte blocks" }

            writeBytes(tech, uid, startBlock = 8, payload = payload, blockSize = system.blockSize)
            val verified = readBytes(tech, uid, startBlock = 8, blockCount = payload.size / system.blockSize)
            if (!verified.contentEquals(payload)) throw IOException("Verification failed: card bytes differ after write")
            val signatureAfter = readBytes(tech, uid, startBlock = 0, blockCount = 8)
            if (!signatureAfter.contentEquals(signature)) throw IOException("Safety check failed: card signature changed")

            return WriteResult(uid.joinToString("") { "%02X".format(it.toInt() and 0xFF) }, verified.size)
        } finally {
            try { tech.close() } catch (_: Exception) { }
        }
    }

    private data class SystemInfo(val blockCount: Int, val blockSize: Int)

    private fun getSystemInfo(tech: NfcV, uid: ByteArray): SystemInfo {
        val response = tech.transceive(byteArrayOf(0x22, 0x2B, *uid))
        if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("ISO15693 Get System Information failed")
        if (response.size < 14) throw IOException("Unexpected system information length ${response.size}")
        val blockCount = (response[12].toInt() and 0xFF) + 1
        val blockSize = (response[13].toInt() and 0x1F) + 1
        return SystemInfo(blockCount, blockSize)
    }

    private fun readBytes(tech: NfcV, uid: ByteArray, startBlock: Int, blockCount: Int): ByteArray {
        val out = ArrayList<Byte>(blockCount * 4)
        repeat(blockCount) { offset ->
            val block = startBlock + offset
            val response = tech.transceive(byteArrayOf(0x22, 0x20, *uid, block.toByte()))
            if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("Read failed at block $block")
            for (i in 1 until response.size) out.add(response[i])
        }
        return out.toByteArray()
    }

    private fun writeBytes(tech: NfcV, uid: ByteArray, startBlock: Int, payload: ByteArray, blockSize: Int) {
        var cursor = 0
        var block = startBlock
        while (cursor < payload.size) {
            val chunk = payload.copyOfRange(cursor, cursor + blockSize)
            val command = ByteArray(2 + uid.size + 1 + chunk.size)
            command[0] = 0x22
            command[1] = 0x21
            uid.copyInto(command, destinationOffset = 2)
            command[2 + uid.size] = block.toByte()
            chunk.copyInto(command, destinationOffset = 3 + uid.size)
            val response = tech.transceive(command)
            if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("Write failed at block $block")
            cursor += blockSize
            block++
        }
    }
}
