package com.brewtap.xbloom

import android.app.Activity
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var nfcAdapter: NfcAdapter? = null
    private var waitingForCard = false
    private var recipe: BrewRecipe = RecipeGenerator.edisonEthiopia()

    private lateinit var recipeText: TextView
    private lateinit var statusText: TextView
    private lateinit var writeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContentView(buildUi())
        renderRecipe()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "BrewTap for xBloom"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Generate → Tap Card → Brew"
            textSize = 16f
            setPadding(0, 8, 0, 28)
        })

        recipeText = TextView(this).apply { textSize = 18f }
        root.addView(recipeText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(Button(this).apply {
            text = "GENERATE EDISON RECIPE"
            setOnClickListener {
                recipe = RecipeGenerator.edisonEthiopia()
                renderRecipe()
                statusText.text = "Recipe generated. Ready to write."
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 })

        writeButton = Button(this).apply {
            text = "WRITE TO xBLOOM NFC CARD"
            setOnClickListener { startCardWrite() }
        }
        root.addView(writeButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 })

        statusText = TextView(this).apply {
            text = if (nfcAdapter == null) "NFC is not available on this phone." else "Ready. Use a genuine xBloom recipe card."
            textSize = 16f
            setPadding(0, 28, 0, 20)
        }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "Important: BrewTap preserves the card signature/hash and XID, and only rewrites the recipe area starting at block 8."
            textSize = 13f
        })
        return scroll
    }

    private fun renderRecipe() {
        recipeText.text = buildString {
            appendLine(recipe.name)
            appendLine()
            appendLine("Coffee: ${recipe.doseGrams} g")
            appendLine("Water: ${recipe.totalWaterMl} ml (1:${"%.1f".format(recipe.displayedRatio)})")
            appendLine("Grind: ${recipe.grindSize} @ ${recipe.grinderRpm} RPM")
            appendLine("Temperature: ${recipe.temperatureC} °C")
            appendLine()
            recipe.pours.forEachIndexed { index, p ->
                append("${index + 1}. ${p.volumeMl} ml • ${p.flowRateTenthsMlPerSec / 10.0} ml/s • ${p.pattern.name.lowercase()}")
                if (p.pauseSeconds > 0) append(" • wait ${p.pauseSeconds}s")
                appendLine()
            }
        }
    }

    private fun startCardWrite() {
        val adapter = nfcAdapter
        if (adapter == null) {
            statusText.text = "NFC is not available on this phone."
            return
        }
        if (!adapter.isEnabled) {
            statusText.text = "Please turn NFC on, then press Write again."
            return
        }
        waitingForCard = true
        writeButton.isEnabled = false
        statusText.text = "Hold a genuine xBloom recipe card against the NFC area of your phone…"
        adapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!waitingForCard) return
        waitingForCard = false
        io.execute {
            val message = try {
                val result = NfcVCardWriter().writeRecipe(tag, recipe)
                "✓ Recipe written and verified (${result.verifiedBytes} bytes)\nCard UID: ${result.uidHex}"
            } catch (e: Exception) {
                "Write failed: ${e.message ?: e.javaClass.simpleName}"
            }
            main.post {
                nfcAdapter?.disableReaderMode(this)
                writeButton.isEnabled = true
                statusText.text = message
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (waitingForCard) {
            nfcAdapter?.disableReaderMode(this)
            waitingForCard = false
            if (::writeButton.isInitialized) writeButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
