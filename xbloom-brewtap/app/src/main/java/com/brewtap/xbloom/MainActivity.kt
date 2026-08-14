package com.brewtap.xbloom

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val io = Executors.newSingleThreadExecutor()
    private var nfc: NfcAdapter? = null
    private lateinit var status: TextView
    private lateinit var dumpView: TextView
    private lateinit var readButton: Button
    private lateinit var copyButton: Button
    private var lastDump: String = ""

    private val ivory = Color.rgb(246, 242, 233)
    private val ink = Color.rgb(23, 21, 18)
    private val muted = Color.rgb(116, 111, 103)
    private val espresso = Color.rgb(57, 43, 36)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        setContentView(buildUi())
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(ivory) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 42, 42, 60)
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "BrewTap Card Lab"
            textSize = 32f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Read a real xBloom recipe card before we reverse the format. Read-only — this build never writes to the card."
            textSize = 16f
            setTextColor(muted)
            setPadding(0, 8, 0, 28)
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 26, 26, 26)
            background = rounded(Color.rgb(255, 253, 248), 30f)
        }
        card.addView(TextView(this).apply {
            text = "SAMPLE CARD INSPECTOR"
            textSize = 12f
            setTextColor(muted)
        })
        card.addView(TextView(this).apply {
            text = "ISO15693 / NFC-V raw dump"
            textSize = 23f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        })
        card.addView(TextView(this).apply {
            text = "Reads UID, DSFID, AFI, block count, block size, and every memory block."
            textSize = 15f
            setTextColor(muted)
            setPadding(0, 0, 0, 20)
        })

        readButton = Button(this).apply {
            text = "READ xBLOOM CARD"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(espresso, 40f)
            setOnClickListener { startRead() }
        }
        card.addView(readButton, LinearLayout.LayoutParams(-1, 64))

        copyButton = Button(this).apply {
            text = "COPY RAW DUMP"
            isEnabled = false
            setOnClickListener { copyDump() }
        }
        card.addView(copyButton, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 10 })
        root.addView(card)

        status = TextView(this).apply {
            text = if (nfc == null) "NFC is not available on this phone." else "Ready. Tap READ xBLOOM CARD, then hold the sample card to the phone."
            textSize = 15f
            setTextColor(muted)
            setPadding(4, 24, 4, 16)
        }
        root.addView(status)

        dumpView = TextView(this).apply {
            text = "Raw card data will appear here."
            textSize = 12f
            setTextColor(ink)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(18, 18, 18, 18)
            background = rounded(Color.WHITE, 20f)
        }
        root.addView(dumpView, LinearLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun startRead() {
        val adapter = nfc
        if (adapter == null) {
            status.text = "NFC unavailable."
            return
        }
        if (!adapter.isEnabled) {
            status.text = "Turn NFC on, then try again."
            return
        }
        readButton.isEnabled = false
        status.text = "READY TO READ\nHold the real xBloom recipe card against the NFC area of your phone…"
        adapter.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onTagDiscovered(tag: Tag) {
        io.execute {
            val result = try {
                NfcVCardInspector().read(tag).prettyHex()
            } catch (e: Exception) {
                "READ ERROR\n${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            }
            runOnUiThread {
                try { nfc?.disableReaderMode(this) } catch (_: Exception) {}
                readButton.isEnabled = true
                lastDump = result
                dumpView.text = result
                copyButton.isEnabled = result.startsWith("UID:")
                status.text = if (result.startsWith("UID:")) {
                    "✓ CARD READ COMPLETE\nTap COPY RAW DUMP and send the text back to me."
                } else {
                    "Card read failed. Keep the card steady and try again."
                }
            }
        }
    }

    private fun copyDump() {
        if (lastDump.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xBloom card raw dump", lastDump))
        Toast.makeText(this, "Card dump copied", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        try { nfc?.disableReaderMode(this) } catch (_: Exception) {}
        if (::readButton.isInitialized) readButton.isEnabled = true
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
