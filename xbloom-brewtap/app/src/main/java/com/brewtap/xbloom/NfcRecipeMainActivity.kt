package com.brewtap.xbloom

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.brewtap.xbloom.domain.*
import java.util.concurrent.Executors

class NfcRecipeMainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val ivory = Color.rgb(247, 244, 237)
    private val paper = Color.rgb(255, 253, 248)
    private val ink = Color.rgb(24, 22, 19)
    private val muted = Color.rgb(108, 103, 96)
    private val espresso = Color.rgb(55, 42, 35)
    private val copper = Color.rgb(164, 103, 69)
    private val softCopper = Color.rgb(238, 226, 213)
    private val line = Color.rgb(226, 219, 209)

    private val io = Executors.newSingleThreadExecutor()
    private var nfc: NfcAdapter? = null
    private var profile: CoffeeProfile? = null
    private var hot: GeneratedRecipe? = null
    private var iced: GeneratedRecipe? = null
    private var selected: GeneratedRecipe? = null
    private var mode = BrewMode.HOT
    private var waitingForCard = false
    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke) setStroke(dp(1), line)
    }
    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(label: String, primary: Boolean = true, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else espresso)
        background = rounded(if (primary) espresso else softCopper, 18)
        minHeight = dp(56); setOnClickListener { click() }
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = rounded(paper)
    }
    private fun page(title: String, subtitle: String): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply { setBackgroundColor(ivory); isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(38)) }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        body.addView(text("BREWTAP", 12f, copper, true))
        body.addView(text(title, 34f, ink, true).apply { setPadding(0, dp(8), 0, dp(8)) })
        body.addView(text(subtitle, 16f, muted).apply { setPadding(0, 0, 0, dp(20)) })
        return scroll to body
    }

    private fun showHome() {
        val (scroll, body) = page("Smart recipes.", "Build Hot + Iced recipes, then write them to an xBloom-compatible NFC-V recipe card.")
        body.addView(card().apply {
            addView(text("NFC-FIRST WORKFLOW", 11f, copper, true))
            addView(text("Coffee → recipe → card → xBloom", 25f, ink, true).apply { setPadding(0, dp(9), 0, dp(7)) })
            addView(text("No Bluetooth machine control. BrewTap encodes the same ISO15693/NFC-V recipe structure reverse-engineered from real xBloom cards.", 14f, muted))
            addView(button("Create a coffee", true) { showCoffeeForm() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(18) })
        })
        profile?.let { p ->
            body.addView(card().apply {
                addView(text("CURRENT COFFEE", 11f, copper, true))
                addView(text(p.name, 23f, ink, true).apply { setPadding(0, dp(8), 0, dp(5)) })
                addView(button("Open Hot / Iced", true) { showRecipe() }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(14) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }
        setContentView(scroll)
    }

    private fun field(hint: String, value: String = "", numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 16f; setTextColor(ink); setHintTextColor(Color.rgb(145,138,128))
        background = rounded(Color.WHITE, 16, true); setPadding(dp(15), dp(13), dp(15), dp(13)); minHeight = dp(56)
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
    }

    private fun showCoffeeForm() {
        val (scroll, body) = page("New coffee", "Enter the bag profile. Recommended dose starts at 15 g but remains recipe-specific.")
        val form = card()
        val name = field("Coffee name")
        val origin = field("Country / origin")
        val process = field("Process — washed, natural, anaerobic…")
        val notes = field("Tasting notes — comma separated")
        val altitude = field("Altitude (m)", numeric = true)
        val dose = field("Dose (g)", "15", true)
        listOf(name, origin, process, notes, altitude, dose).forEach { form.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }

        form.addView(text("ROAST LEVEL", 11f, muted, true).apply { setPadding(dp(2), dp(4), 0, dp(6)) })
        val roast = Spinner(this).apply {
            adapter = ArrayAdapter(this@NfcRecipeMainActivity, android.R.layout.simple_spinner_dropdown_item, RoastLevel.values().map { it.name.replace('_',' ') })
            setSelection(RoastLevel.values().indexOf(RoastLevel.MEDIUM_LIGHT))
            background = rounded(Color.WHITE, 16, true); minimumHeight = dp(56)
        }
        form.addView(roast, LinearLayout.LayoutParams(-1, dp(56)))
        form.addView(button("Generate Hot + Iced", true) {
            val grams = dose.text.toString().toIntOrNull() ?: 15
            if (grams !in 8..30) { Toast.makeText(this, "Dose should be 8–30 g.", Toast.LENGTH_LONG).show(); return@button }
            val p = CoffeeProfile(
                name = name.text.toString().trim().ifBlank { "Untitled coffee" },
                country = origin.text.toString().trim(), process = process.text.toString().trim(),
                roastLevel = RoastLevel.values()[roast.selectedItemPosition],
                altitudeM = altitude.text.toString().toIntOrNull(),
                tastingNotes = notes.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() },
                source = CoffeeSource.MANUAL,
            )
            profile = p
            hot = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.HOT, doseGrams = grams))
            iced = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.ICED, doseGrams = grams))
            mode = BrewMode.HOT; selected = hot; showRecipe()
        }, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(16) })
        body.addView(form)
        setContentView(scroll)
    }

    private fun showRecipe() {
        val current = (if (mode == BrewMode.HOT) hot else iced) ?: return showHome()
        selected = current
        val p = profile ?: return showHome()
        val (scroll, body) = page(p.name, "Recommended recipes remain editable by taste direction and dose.")
        val switch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        switch.addView(button("Hot", mode == BrewMode.HOT) { mode = BrewMode.HOT; showRecipe() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        switch.addView(button("Iced", mode == BrewMode.ICED) { mode = BrewMode.ICED; showRecipe() }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(8) })
        body.addView(switch)
        body.addView(card().apply {
            addView(text(if (mode == BrewMode.HOT) "HOT — RECOMMENDED" else "ICED — RECOMMENDED", 11f, copper, true))
            addView(text(current.expectedCup.summary, 18f, ink, true).apply { setPadding(0, dp(9), 0, dp(12)) })
            addView(text("${current.recipe.doseGrams} g  •  ${current.recipe.totalWaterMl} g brew water  •  Grind ${current.recipe.grindSize} @ ${current.recipe.grinderRpm} RPM", 14f, muted))
            current.icedSplit?.let { addView(text("${it.brewWaterMl} g hot brew + ${it.iceGrams} g ice = ${it.targetBeverageWaterMl} g beverage", 14f, copper, true).apply { setPadding(0, dp(8), 0, 0) }) }
            addView(text("POURS", 11f, copper, true).apply { setPadding(0, dp(17), 0, dp(5)) })
            current.recipe.pours.forEachIndexed { i, pour ->
                addView(text("${i+1}. ${pour.volumeMl} g · ${pour.temperatureC}°C · ${pour.flowRateTenthsMlPerSec/10.0} ml/s · ${pour.pattern.name.lowercase()} · pause ${pour.pauseSeconds}s", 13f, ink).apply { setPadding(0, dp(4), 0, 0) })
            }
            addView(button("Tune taste", false) { tune(current) }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
            addView(button("Prepare NFC recipe card", true) { showWriteCard() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(9) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        setContentView(scroll)
    }

    private fun tune(current: GeneratedRecipe) {
        val goals = TasteGoal.values().filter { it != TasteGoal.RECOMMENDED }
        AlertDialog.Builder(this).setTitle("Tune the cup").setItems(goals.map { it.name.replace('_',' ').lowercase().replaceFirstChar(Char::uppercase) }.toTypedArray()) { _, which ->
            val tuned = RecipeTuner.tune(current, goals[which])
            if (current.mode == BrewMode.HOT) hot = tuned else iced = tuned
            selected = tuned; showRecipe()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showWriteCard() {
        val g = selected ?: return showRecipe()
        val (scroll, body) = page("Write recipe card", "Write the generated recipe to a reusable xBloom-compatible ISO15693/NFC-V card.")
        val payloadPreview = runCatching {
            val placeholderXid = byteArrayOf(0x54,0x48,0x30,0x30,0x30,0x30,0x00)
            XBloomRecipeEncoder.encodePayload(g.recipe, placeholderXid)
        }
        val c = card().apply {
            addView(text("READY TO WRITE", 11f, copper, true))
            addView(text(g.recipe.name, 22f, ink, true).apply { setPadding(0, dp(8), 0, dp(6)) })
            addView(text("${g.recipe.doseGrams} g · ${g.recipe.totalWaterMl} g · Grind ${g.recipe.grindSize}", 15f, muted))
            addView(text(if (payloadPreview.isSuccess) "✓ Recipe format validated · CRC 0x%02X".format(payloadPreview.getOrThrow().last().toInt() and 0xFF) else "Encoder error: ${payloadPreview.exceptionOrNull()?.message}", 14f, if (payloadPreview.isSuccess) ink else Color.RED, true).apply { setPadding(0, dp(16), 0, 0) })
            addView(text("The card must already be xBloom-compatible. BrewTap preserves its first 32 signature bytes and its XID, then replaces only the recipe region and verifies the write.", 13f, muted).apply { setPadding(0, dp(10), 0, 0) })
            val status = text("Tap WRITE, then hold the NFC-V card against the phone.", 14f, muted).apply { setPadding(0, dp(16), 0, 0) }
            statusView = status
            addView(button("WRITE TO xBLOOM NFC CARD", true) { armWriter(status) }, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(16) })
            addView(status)
            addView(button("Back to recipe", false) { showRecipe() }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        }
        body.addView(c)
        setContentView(scroll)
    }

    private fun armWriter(status: TextView) {
        val adapter = nfc ?: run { status.text = "NFC is unavailable on this phone."; return }
        if (!adapter.isEnabled) { status.text = "Turn NFC on, then tap WRITE again."; return }
        if (selected == null) { status.text = "No recipe selected."; return }
        waitingForCard = true
        status.text = "READY — hold the xBloom-compatible NFC-V card against the phone…"
        adapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!waitingForCard) return
        val recipe = selected?.recipe ?: return
        waitingForCard = false
        io.execute {
            val result = runCatching { NfcVCardWriter().writeRecipe(tag, recipe) }
            runOnUiThread {
                try { nfc?.disableReaderMode(this) } catch (_: Exception) {}
                val msg = result.fold(
                    onSuccess = { "✓ WRITTEN & VERIFIED — ${it.verifiedBytes} recipe bytes. Now tap this card on xBloom." },
                    onFailure = { "Write failed: ${it.message ?: it.javaClass.simpleName}" },
                )
                statusView?.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause(); try { nfc?.disableReaderMode(this) } catch (_: Exception) {}; waitingForCard = false
    }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }
}
