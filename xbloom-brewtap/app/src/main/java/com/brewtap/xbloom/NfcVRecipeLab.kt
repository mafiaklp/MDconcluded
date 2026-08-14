package com.brewtap.xbloom

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

class NfcVRecipeLab {
    fun read(tag: Tag): CardDump = NfcVCardInspector().read(tag)

    fun writeRecipe(tag: Tag, recipe: BrewRecipe): WriteResult = NfcVCardWriter().writeRecipe(tag, recipe)

    fun restore(tag: Tag, backup: CardDump) {
        val tech = NfcV.get(tag) ?: throw IOException("Not an ISO15693 / NFC-V tag")
        val uidNow = tag.id.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        require(uidNow.equals(backup.uidHex, ignoreCase = true)) { "Backup belongs to another card" }
        require(backup.blockSize == 4) { "Unsupported block size ${backup.blockSize}" }
        tech.connect()
        try {
            val uid = tag.id
            for (block in 8 until backup.blockCount) {
                val start = block * backup.blockSize
                val chunk = backup.bytes.copyOfRange(start, start + backup.blockSize)
                val cmd = byteArrayOf(0x22, 0x21, *uid, block.toByte(), *chunk)
                val response = tech.transceive(cmd)
                if (response.isEmpty() || response[0].toInt() != 0x00) throw IOException("Restore failed at block $block")
            }
        } finally {
            try { tech.close() } catch (_: Exception) { }
        }
        val verify = NfcVCardInspector().read(tag)
        if (!verify.bytes.contentEquals(backup.bytes)) throw IOException("Restore verification failed")
    }
}
