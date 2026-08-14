package com.brewtap.xbloom

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.brewtap.xbloom.domain.*
import com.brewtap.xbloom.photo.AnalyzedCoffeeDraft
import com.brewtap.xbloom.photo.CoffeeBagAnalyzer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private enum class DevNfcMode { NONE, READ, WRITE_SELECTED }

    private val io = Executors.newSingleThreadExecutor()
    private var nfc: NfcAdapter? = null
    private var devNfcMode = DevNfcMode.NONE
    private var currentProfile: CoffeeProfile? = null
    private var hotRecipe: GeneratedRecipe? = null
    private var icedRecipe: GeneratedRecipe? = null
    private var selectedMode = BrewMode.HOT
    private var selectedRecipe: GeneratedRecipe? = null
    private var devBackup: CardDump? = null

    private val ivory = Color.rgb(247, 244, 237)
    private val white = Color.rgb(255, 253, 248)
    private val ink = Color.rgb(24, 22, 19)
    private val muted = Color.rgb(112, 106, 98)
    private val espresso = Color.rgb(55, 42, 35)
    private val copper = Color.rgb(162, 105, 72)
    private val paleCopper = Color.rgb(235, 222, 209)

    companion object { private const val PICK_BAG_IMAGE = 701 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        showHome()
    }

    private fun rounded(color: Int, radius: Float = 28f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun tv(text: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun primary(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE)
        background = rounded(espresso, 42f)
        setOnClickListener { click() }
    }

    private fun secondary(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 14f; setTextColor(espresso)
        background = rounded(paleCopper, 42f)
        setOnClickListener { click() }
    }

    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 16f; setTextColor(ink); setHintTextColor(muted)
        background = rounded(Color.WHITE, 20f); setPadding(22, 14, 22, 14)
    }

    private fun page(title: String, subtitle: String, content: LinearLayout, selectedTab: String): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(ivory) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 30, 34, 36)
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        root.addView(tv("BREWTAP", 12f, copper, true))
        root.addView(tv(title, 31f, ink, true).apply { setPadding(0, 5, 0, 4) })
        root.addView(tv(subtitle, 15f, muted).apply { setPadding(0, 0, 0, 24) })
        root.addView(content)
        root.addView(bottomNav(selectedTab), LinearLayout.LayoutParams(-1, 76).apply { topMargin = 30 })
        return scroll
    }

    private fun bottomNav(selected: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        val items = listOf(
            "HOME" to { showHome() },
            "COFFEES" to { showCoffees() },
            "BREW" to { showBrew() },
            "SETTINGS" to { showSettings() },
        )
        items.forEach { (name, action) ->
            addView(Button(this@MainActivity).apply {
                text = name; textSize = 10f
                setTextColor(if (selected == name) Color.WHITE else espresso)
                background = rounded(if (selected == name) espresso else Color.TRANSPARENT, 30f)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, 58, 1f).apply { marginStart = 3; marginEnd = 3 })
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 22, 24, 22)
        background = rounded(white, 28f)
    }

    private fun showHome() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hero = card()
        hero.addView(tv("SMART RECIPE ENGINE", 11f, copper, true))
        hero.addView(tv("Brew the coffee, not the template.", 25f, ink, true).apply { setPadding(0, 8, 0, 8) })
        hero.addView(tv("Competition-style Hot + Iced recipes built from origin, process, roast, altitude and tasting notes — then tuned toward the cup you want.", 15f, muted))
        hero.addView(primary("＋  ADD A COFFEE") { showAddCoffee() }, LinearLayout.LayoutParams(-1, 62).apply { topMargin = 22 })
        box.addView(hero)

        currentProfile?.let { p ->
            val recent = card().apply {
                addView(tv("CURRENT COFFEE", 11f, copper, true))
                addView(tv(p.name, 23f, ink, true).apply { setPadding(0, 8, 0, 4) })
                addView(tv(listOf(p.country, p.process, p.roastLevel.name.replace('_', ' ')).filter { it.isNotBlank() && it != "UNKNOWN" }.joinToString("  •  "), 14f, muted))
                addView(primary("OPEN HOT / ICED RECIPES") { showCoffeeDetail() }, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 18 })
            }
            box.addView(recent, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })
        }

        val philosophy = card().apply {
            addView(tv("HOW IT THINKS", 11f, copper, true))
            addView(tv("Light washed coffees bias clarity and aromatics. Naturals and anaerobics control agitation to keep fruit defined. Iced recipes treat ice as part of the beverage-water budget and build a deliberate concentrate.", 15f, ink).apply { setPadding(0, 8, 0, 0) })
        }
        box.addView(philosophy, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })
        setContentView(page("Good evening.", "Your smart xBloom recipe studio", box, "HOME"))
    }

    private fun showCoffees() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        currentProfile?.let { p ->
            val c = card().apply {
                addView(tv(p.name, 23f, ink, true))
                addView(tv(p.tastingNotes.joinToString(" • ").ifBlank { "No tasting notes yet" }, 14f, muted).apply { setPadding(0, 5, 0, 14) })
                addView(primary("VIEW RECIPES") { showCoffeeDetail() }, LinearLayout.LayoutParams(-1, 56))
            }
            box.addView(c)
        } ?: box.addView(card().apply {
            addView(tv("No coffees yet", 22f, ink, true)); addView(tv("Add a bag by photo or manual entry.", 15f, muted).apply { setPadding(0, 5, 0, 16) })
            addView(primary("ADD COFFEE") { showAddCoffee() }, LinearLayout.LayoutParams(-1, 56))
        })
        setContentView(page("Coffees", "Saved coffee profiles and their recipe revisions", box, "COFFEES"))
    }

    private fun showAddCoffee(draft: AnalyzedCoffeeDraft? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val intake = card()
        intake.addView(tv("ADD COFFEE", 11f, copper, true))
        intake.addView(tv("Photo or manual — both stay editable", 22f, ink, true).apply { setPadding(0, 6, 0, 14) })
        intake.addView(secondary("📷  SCAN COFFEE BAG") { pickBagImage() }, LinearLayout.LayoutParams(-1, 58))
        draft?.let { intake.addView(tv("Bag text recognized. Review every field before generating.", 13f, copper, true).apply { setPadding(0, 12, 0, 0) }) }

        val name = field("Coffee name", draft?.name.orEmpty())
        val country = field("Country / origin", draft?.country.orEmpty())
        val process = field("Process (washed, natural, anaerobic…)", draft?.process.orEmpty())
        val altitude = field("Altitude (m)", draft?.altitudeM?.toString().orEmpty()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val notes = field("Tasting notes, comma separated", draft?.tastingNotes?.joinToString(", ").orEmpty())
        listOf(name, country, process, altitude, notes).forEach { intake.addView(it, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 10 }) }

        val roast = Spinner(this)
        val roastNames = RoastLevel.values().map { it.name.replace('_', ' ') }
        roast.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roastNames)
        roast.setSelection(RoastLevel.values().indexOf(draft?.roastLevel ?: RoastLevel.UNKNOWN))
        intake.addView(tv("ROAST LEVEL", 12f, muted, true).apply { setPadding(4, 14, 0, 4) })
        intake.addView(roast, LinearLayout.LayoutParams(-1, 58))

        intake.addView(primary("GENERATE HOT + ICED") {
            val profile = CoffeeProfile(
                name = name.text.toString().trim(),
                country = country.text.toString().trim(),
                process = process.text.toString().trim(),
                roastLevel = RoastLevel.values()[roast.selectedItemPosition],
                altitudeM = altitude.text.toString().toIntOrNull(),
                tastingNotes = notes.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() },
                source = if (draft != null) CoffeeSource.PHOTO else CoffeeSource.MANUAL,
            )
            if (!profile.isGeneratable()) {
                Toast.makeText(this, "Add roast, process, origin, or tasting notes first.", Toast.LENGTH_LONG).show()
            } else {
                currentProfile = profile
                hotRecipe = SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.HOT, doseGrams = 18))
                icedRecipe = SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.ICED, doseGrams = 18))
                selectedMode = BrewMode.HOT; selectedRecipe = hotRecipe
                showCoffeeDetail()
            }
        }, LinearLayout.LayoutParams(-1, 62).apply { topMargin = 20 })
        box.addView(intake)
        setContentView(page("New coffee", "Turn bag information into a competition-minded recipe", box, "COFFEES"))
    }

    private fun pickBagImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }, PICK_BAG_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_BAG_IMAGE || resultCode != RESULT_OK || data?.data == null) return
        Toast.makeText(this, "Reading bag text…", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, data.data!!)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result ->
                    val draft = CoffeeBagAnalyzer.analyze(result.text)
                    showAddCoffee(draft)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Could not read bag: ${e.message}", Toast.LENGTH_LONG).show()
                    showAddCoffee()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCoffeeDetail() {
        val profile = currentProfile ?: return showHome()
        val hot = hotRecipe ?: SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.HOT)).also { hotRecipe = it }
        val iced = icedRecipe ?: SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.ICED)).also { icedRecipe = it }
        val current = if (selectedMode == BrewMode.HOT) hot else iced
        selectedRecipe = current

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val switch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        switch.addView(modeButton("☕ HOT", BrewMode.HOT), LinearLayout.LayoutParams(0, 56, 1f))
        switch.addView(modeButton("❄ ICED", BrewMode.ICED), LinearLayout.LayoutParams(0, 56, 1f).apply { marginStart = 8 })
        box.addView(switch)

        val recipeCard = card().apply {
            addView(tv(if (selectedMode == BrewMode.HOT) "HOT — RECOMMENDED" else "ICED — RECOMMENDED", 11f, copper, true))
            addView(tv(profile.name, 25f, ink, true).apply { setPadding(0, 6, 0, 3) })
            addView(tv(current.expectedCup.summary, 15f, muted))
            addView(bigStats(current), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })
            current.icedSplit?.let { split ->
                addView(tv("ICE PLAN", 11f, copper, true).apply { setPadding(0, 16, 0, 3) })
                addView(tv("${split.brewWaterMl} g hot brew  +  ${split.iceGrams} g ice  =  ${split.targetBeverageWaterMl} g beverage", 16f, ink, true))
            }
            addView(tv("EXPECTED TASTE", 11f, copper, true).apply { setPadding(0, 18, 0, 8) })
            addView(sensoryView(current.expectedCup))
            addView(tv("WHY THIS RECIPE", 11f, copper, true).apply { setPadding(0, 18, 0, 5) })
            addView(tv(current.rationale, 14f, muted))
            addView(secondary("TUNE TASTE") { showTuneDialog(current) }, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 18 })
            addView(primary("USE THIS RECIPE") { selectedRecipe = current; showBrew() }, LinearLayout.LayoutParams(-1, 60).apply { topMargin = 10 })
        }
        box.addView(recipeCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14 })

        val pours = card().apply {
            addView(tv("POUR ARCHITECTURE", 11f, copper, true))
            current.recipe.pours.forEachIndexed { i, p ->
                addView(tv("${i + 1}", 12f, copper, true).apply { setPadding(0, 13, 0, 2) })
                addView(tv("${p.volumeMl} g  •  ${p.temperatureC}°C  •  ${p.flowRateTenthsMlPerSec / 10.0} ml/s  •  ${p.pattern.name.lowercase()}  •  pause ${p.pauseSeconds}s", 14f, ink))
            }
        }
        box.addView(pours, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        setContentView(page(profile.name, listOf(profile.country, profile.process, profile.roastLevel.name.replace('_', ' ')).filter { it.isNotBlank() && it != "UNKNOWN" }.joinToString("  •  "), box, "COFFEES"))
    }

    private fun modeButton(text: String, mode: BrewMode) = Button(this).apply {
        this.text = text; textSize = 14f
        val on = selectedMode == mode
        setTextColor(if (on) Color.WHITE else espresso)
        background = rounded(if (on) espresso else paleCopper, 34f)
        setOnClickListener { selectedMode = mode; showCoffeeDetail() }
    }

    private fun bigStats(g: GeneratedRecipe): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val items = listOf(
            "${g.recipe.doseGrams}g" to "DOSE",
            "${g.recipe.totalWaterMl}g" to if (g.mode == BrewMode.ICED) "BREW WATER" else "WATER",
            "${g.recipe.grindSize}" to "GRIND",
            "${g.recipe.temperatureC}°" to "TEMP",
        )
        items.forEach { (value, label) ->
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(tv(value, 22f, ink, true)); addView(tv(label, 9f, muted, true))
            }, LinearLayout.LayoutParams(0, 58, 1f))
        }
    }

    private fun sensoryView(c: ExpectedCup): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        listOf(
            "Sweetness" to c.sweetness, "Acidity" to c.acidity, "Clarity" to c.clarity,
            "Floral" to c.floral, "Fruit" to c.fruit, "Body" to c.body, "Bitterness risk" to c.bitternessRisk
        ).forEach { (name, score) ->
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(tv(name, 12f, ink), LinearLayout.LayoutParams(0, 32, 1f))
            row.addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 10; progress = score }, LinearLayout.LayoutParams(0, 12, 1.3f))
            row.addView(tv("$score/10", 11f, muted, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, 32, .55f))
            addView(row)
        }
    }

    private fun showTuneDialog(current: GeneratedRecipe) {
        val goals = TasteGoal.values().filter { it != TasteGoal.RECOMMENDED }
        val labels = goals.map { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tune the cup")
            .setItems(labels) { _, which ->
                val tuned = RecipeTuner.tune(current, goals[which])
                if (current.mode == BrewMode.HOT) hotRecipe = tuned else icedRecipe = tuned
                selectedRecipe = tuned
                showCoffeeDetail()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBrew() {
        val g = selectedRecipe ?: run {
            val p = currentProfile
            if (p != null) SmartRecipeEngine.generate(p, RecipeIntent(selectedMode)).also { selectedRecipe = it } else null
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (g == null) {
            box.addView(card().apply {
                addView(tv("No recipe selected", 22f, ink, true)); addView(tv("Create a coffee first.", 14f, muted).apply { setPadding(0, 5, 0, 14) })
                addView(primary("ADD COFFEE") { showAddCoffee() }, LinearLayout.LayoutParams(-1, 56))
            })
        } else {
            val xid = xidFor(g.profile)
            val payload = runCatching { XBloomRecipeEncoder.encodePayload(g.recipe, xid) }
            val c = card().apply {
                addView(tv("READY FOR xBLOOM", 11f, copper, true))
                addView(tv(g.recipe.name, 23f, ink, true).apply { setPadding(0, 7, 0, 3) })
                addView(tv("${g.recipe.doseGrams}g  •  ${g.recipe.totalWaterMl}g brew water  •  Grind ${g.recipe.grindSize} @ ${g.recipe.grinderRpm} RPM", 14f, muted))
                g.icedSplit?.let { addView(tv("Add ${it.iceGrams} g ice before brewing.", 14f, copper, true).apply { setPadding(0, 8, 0, 0) }) }
                addView(tv("xBloom encoder", 11f, copper, true).apply { setPadding(0, 18, 0, 4) })
                if (payload.isSuccess) {
                    addView(tv("✓ Valid recipe payload • ${payload.getOrThrow().size} bytes • CRC 0x%02X".format(payload.getOrThrow().last().toInt() and 0xFF), 14f, ink, true))
                    addView(tv(payload.getOrThrow().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }, 10f, muted).apply { setTextIsSelectable(true); setPadding(0, 8, 0, 0) })
                } else addView(tv("Preflight error: ${payload.exceptionOrNull()?.message}", 14f, Color.RED, true))
                addView(tv("Direct phone → xBloom remains Experimental until hardware transport is proven. The app never auto-starts the brewer.", 13f, muted).apply { setPadding(0, 18, 0, 0) })
                addView(secondary("DEVELOPER TOOLS / TEST CARD") { showDeveloperTools() }, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 16 })
            }
            box.addView(c)
        }
        setContentView(page("Brew", "Exact recipe preflight before xBloom", box, "BREW"))
    }

    private fun xidFor(profile: CoffeeProfile): ByteArray {
        val suffix = profile.id.filter { it.isLetterOrDigit() }.uppercase().take(5).padEnd(5, '0')
        return ("BT" + suffix).encodeToByteArray()
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(card().apply {
            addView(tv("BREWTAP 1.3.0", 11f, copper, true))
            addView(tv("Modern Premium Utility", 22f, ink, true).apply { setPadding(0, 7, 0, 3) })
            addView(tv("Recommended dose: 18 g • Direct machine transport: Experimental/off", 14f, muted))
            addView(secondary("DEVELOPER TOOLS") { showDeveloperTools() }, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 16 })
        })
        setContentView(page("Settings", "Preferences, updates and diagnostic tools", box, "SETTINGS"))
    }

    private fun showDeveloperTools() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = tv("Card Lab idle.", 13f, muted)
        val c = card().apply {
            addView(tv("DEVELOPER TOOLS", 11f, copper, true))
            addView(tv("xBloom Card Lab", 23f, ink, true).apply { setPadding(0, 7, 0, 3) })
            addView(tv("Diagnostics only. Recipe intelligence does not depend on reading cards anymore.", 14f, muted))
            addView(secondary("READ TEST CARD") { armDevNfc(DevNfcMode.READ, status) }, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 16 })
            addView(primary("WRITE SELECTED RECIPE TO TEST CARD") { armDevNfc(DevNfcMode.WRITE_SELECTED, status) }, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 8 })
            addView(status.apply { setPadding(0, 14, 0, 0) })
        }
        box.addView(c)
        setContentView(page("Developer Tools", "NFC-V diagnostics and controlled test-card writing", box, "SETTINGS"))
    }

    private fun armDevNfc(mode: DevNfcMode, status: TextView) {
        val adapter = nfc ?: run { status.text = "NFC unavailable."; return }
        if (!adapter.isEnabled) { status.text = "Turn NFC on first."; return }
        if (mode == DevNfcMode.WRITE_SELECTED && selectedRecipe == null) { status.text = "Select a recipe first."; return }
        devNfcMode = mode
        status.text = if (mode == DevNfcMode.READ) "Hold an xBloom card to the phone…" else "Hold a backed-up TEST xBloom card to the phone…"
        adapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    override fun onTagDiscovered(tag: Tag) {
        val mode = devNfcMode
        if (mode == DevNfcMode.NONE) return
        io.execute {
            val message = try {
                when (mode) {
                    DevNfcMode.READ -> {
                        val dump = NfcVRecipeLab().read(tag); devBackup = dump
                        val d = XBloomCardCodec.decode(dump.bytes)
                        "Read ${dump.uidHex}\n${d.recipe.name} • ${d.recipe.doseGrams}g • ${d.recipe.totalWaterMl}g • grind ${d.recipe.grindSize}"
                    }
                    DevNfcMode.WRITE_SELECTED -> {
                        val g = requireNotNull(selectedRecipe)
                        val dump = NfcVRecipeLab().read(tag); devBackup = dump
                        val original = XBloomCardCodec.decode(dump.bytes)
                        val recipe = g.recipe.copy(name = original.recipe.name, cupType = original.recipe.cupType)
                        val result = NfcVRecipeLab().writeRecipe(tag, recipe)
                        "✓ Test card written & verified (${result.verifiedBytes} bytes). First 32 signature bytes preserved."
                    }
                    DevNfcMode.NONE -> "Idle"
                }
            } catch (e: Exception) { "NFC error: ${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread {
                try { nfc?.disableReaderMode(this) } catch (_: Exception) {}
                devNfcMode = DevNfcMode.NONE
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                showDeveloperTools()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try { nfc?.disableReaderMode(this) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
