package com.brewtap.xbloom

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.brewtap.xbloom.domain.*
import com.brewtap.xbloom.photo.AnalyzedCoffeeDraft
import com.brewtap.xbloom.photo.CoffeeBagAnalyzer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PremiumMainActivity : Activity() {
    private val ivory = Color.rgb(247, 244, 237)
    private val paper = Color.rgb(255, 253, 248)
    private val ink = Color.rgb(24, 22, 19)
    private val muted = Color.rgb(108, 103, 96)
    private val espresso = Color.rgb(55, 42, 35)
    private val copper = Color.rgb(164, 103, 69)
    private val softCopper = Color.rgb(238, 226, 213)
    private val line = Color.rgb(226, 219, 209)

    private var profile: CoffeeProfile? = null
    private var hot: GeneratedRecipe? = null
    private var iced: GeneratedRecipe? = null
    private var mode = BrewMode.HOT
    private var selected: GeneratedRecipe? = null

    companion object { private const val PICK_BAG_IMAGE = 901 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radiusDp: Int = 22, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke) setStroke(dp(1), line)
    }

    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean = true, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else espresso)
        background = rounded(if (primary) espresso else softCopper, 18)
        minHeight = dp(56)
        setPadding(dp(18), 0, dp(18), 0)
        setOnClickListener { click() }
    }

    private fun input(label: String, value: String = "", numeric: Boolean = false): LinearLayout {
        val field = EditText(this).apply {
            tag = "input"
            hint = label
            setText(value)
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.rgb(145, 138, 128))
            background = rounded(Color.WHITE, 16, stroke = true)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            minHeight = dp(56)
            maxLines = if (label.startsWith("Tasting")) 3 else 1
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(field, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = rounded(paper, 22)
    }

    private fun root(title: String, subtitle: String): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ivory)
            isFillViewport = true
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(40))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        body.addView(text("BREWTAP", 12f, copper, true))
        body.addView(text(title, 34f, ink, true).apply { setPadding(0, dp(8), 0, dp(8)) })
        body.addView(text(subtitle, 16f, muted).apply { setPadding(0, 0, 0, dp(22)) })
        return scroll to body
    }

    private fun nav(body: LinearLayout, current: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        listOf(
            "Home" to { showHome() },
            "Coffees" to { if (profile == null) showAddCoffee() else showDetail() },
            "Brew" to { showBrew() },
            "Settings" to { showSettings() },
        ).forEach { (name, action) ->
            row.addView(Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 11f
                minHeight = dp(48)
                setTextColor(if (name == current) Color.WHITE else espresso)
                background = rounded(if (name == current) espresso else Color.TRANSPARENT, 16)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showHome() {
        val (scroll, body) = root("Brew smarter.", "Competition-minded recipes designed for your coffee and your cup.")
        body.addView(card().apply {
            addView(text("SMART RECIPE ENGINE", 11f, copper, true))
            addView(text("One coffee. Two purpose-built recipes.", 25f, ink, true).apply { setPadding(0, dp(10), 0, dp(8)) })
            addView(text("Add a bag by photo or manual entry. BrewTap creates independent Hot and Iced recipes, predicts the cup, then lets you tune the taste.", 15f, muted))
            addView(button("Add a coffee", true) { showAddCoffee() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(20) })
        })
        profile?.let { p ->
            body.addView(card().apply {
                addView(text("CURRENT COFFEE", 11f, copper, true))
                addView(text(p.name, 24f, ink, true).apply { setPadding(0, dp(8), 0, dp(5)) })
                addView(text(listOf(p.country, p.process, p.roastLevel.name.replace('_', ' ')).filter { it.isNotBlank() && it != "UNKNOWN" }.joinToString("  •  "), 14f, muted))
                addView(button("Open Hot / Iced", true) { showDetail() }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(18) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        nav(body, "Home")
        setContentView(scroll)
    }

    private fun showAddCoffee(draft: AnalyzedCoffeeDraft? = null) {
        val (scroll, body) = root("New coffee", "Scan the bag or enter the details yourself. Everything stays editable before generation.")

        body.addView(card().apply {
            addView(text("START WITH THE BAG", 11f, copper, true))
            addView(text("Let BrewTap read it first", 23f, ink, true).apply { setPadding(0, dp(8), 0, dp(6)) })
            addView(text("Choose a clear front/back photo. We extract what is visible and never lock the fields.", 14f, muted))
            addView(button("Scan coffee bag", true) { pickBagImage() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(16) })
            if (draft != null) addView(text("Bag text recognized — review the details below.", 13f, copper, true).apply { setPadding(0, dp(12), 0, 0) })
        })

        val form = card()
        form.addView(text("COFFEE PROFILE", 11f, copper, true))
        form.addView(text("Review & complete", 23f, ink, true).apply { setPadding(0, dp(8), 0, dp(16)) })

        val nameWrap = input("Coffee name", draft?.name.orEmpty())
        val countryWrap = input("Country / origin", draft?.country.orEmpty())
        val processWrap = input("Process — washed, natural, anaerobic…", draft?.process.orEmpty())
        val altitudeWrap = input("Altitude (m)", draft?.altitudeM?.toString().orEmpty(), true)
        val notesWrap = input("Tasting notes — comma separated", draft?.tastingNotes?.joinToString(", ").orEmpty())
        listOf(nameWrap, countryWrap, processWrap, altitudeWrap, notesWrap).forEach {
            form.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }

        form.addView(text("ROAST LEVEL", 11f, muted, true).apply { setPadding(dp(2), dp(3), 0, dp(7)) })
        val roast = Spinner(this).apply {
            val labels = RoastLevel.values().map { it.name.replace('_', ' ') }
            adapter = ArrayAdapter(this@PremiumMainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(RoastLevel.values().indexOf(draft?.roastLevel ?: RoastLevel.UNKNOWN))
            background = rounded(Color.WHITE, 16, stroke = true)
            setPadding(dp(12), 0, dp(10), 0)
            minimumHeight = dp(56)
        }
        form.addView(roast, LinearLayout.LayoutParams(-1, dp(56)))

        form.addView(button("Generate Hot + Iced", true) {
            val name = (nameWrap.getChildAt(0) as EditText).text.toString().trim()
            val country = (countryWrap.getChildAt(0) as EditText).text.toString().trim()
            val process = (processWrap.getChildAt(0) as EditText).text.toString().trim()
            val altitude = (altitudeWrap.getChildAt(0) as EditText).text.toString().toIntOrNull()
            val notes = (notesWrap.getChildAt(0) as EditText).text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }
            val p = CoffeeProfile(
                name = name,
                country = country,
                process = process,
                roastLevel = RoastLevel.values()[roast.selectedItemPosition],
                altitudeM = altitude,
                tastingNotes = notes,
                source = if (draft == null) CoffeeSource.MANUAL else CoffeeSource.PHOTO,
            )
            if (!p.isGeneratable()) {
                Toast.makeText(this, "Add a coffee name plus roast, origin, process or tasting notes.", Toast.LENGTH_LONG).show()
                return@button
            }
            profile = p
            hot = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.HOT, doseGrams = 18))
            iced = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.ICED, doseGrams = 18))
            mode = BrewMode.HOT
            selected = hot
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(roast.windowToken, 0)
            showDetail()
        }, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(20) })

        body.addView(form, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        nav(body, "Coffees")
        setContentView(scroll)
    }

    private fun pickBagImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, PICK_BAG_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_BAG_IMAGE || resultCode != RESULT_OK || data?.data == null) return
        Toast.makeText(this, "Reading coffee bag…", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, data.data!!)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { showAddCoffee(CoffeeBagAnalyzer.analyze(it.text)) }
                .addOnFailureListener {
                    Toast.makeText(this, "Could not read this image. You can still enter it manually.", Toast.LENGTH_LONG).show()
                    showAddCoffee()
                }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open this image.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDetail() {
        val p = profile ?: return showAddCoffee()
        val current = (if (mode == BrewMode.HOT) hot else iced) ?: return
        selected = current
        val (scroll, body) = root(p.name, listOf(p.country, p.process, p.roastLevel.name.replace('_', ' ')).filter { it.isNotBlank() && it != "UNKNOWN" }.joinToString("  •  "))

        val switch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        switch.addView(button("Hot", mode == BrewMode.HOT) { mode = BrewMode.HOT; showDetail() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        switch.addView(button("Iced", mode == BrewMode.ICED) { mode = BrewMode.ICED; showDetail() }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(8) })
        body.addView(switch)

        body.addView(card().apply {
            addView(text(if (mode == BrewMode.HOT) "HOT — RECOMMENDED" else "ICED — RECOMMENDED", 11f, copper, true))
            addView(text(current.expectedCup.summary, 18f, ink, true).apply { setPadding(0, dp(10), 0, dp(16)) })
            addView(text("${current.recipe.doseGrams} g coffee   •   ${current.recipe.totalWaterMl} g brew water", 17f, ink, true))
            addView(text("Grind ${current.recipe.grindSize} @ ${current.recipe.grinderRpm} RPM   •   ${current.recipe.temperatureC}°C", 15f, muted).apply { setPadding(0, dp(6), 0, 0) })
            current.icedSplit?.let {
                addView(text("ICE PLAN", 11f, copper, true).apply { setPadding(0, dp(18), 0, dp(5)) })
                addView(text("${it.brewWaterMl} g hot brew + ${it.iceGrams} g ice = ${it.targetBeverageWaterMl} g beverage", 15f, ink, true))
            }
            addView(text("EXPECTED CUP", 11f, copper, true).apply { setPadding(0, dp(18), 0, dp(6)) })
            addView(text("Sweetness ${current.expectedCup.sweetness}/10   •   Acidity ${current.expectedCup.acidity}/10\nClarity ${current.expectedCup.clarity}/10   •   Floral ${current.expectedCup.floral}/10   •   Body ${current.expectedCup.body}/10", 14f, ink))
            addView(button("Tune taste", false) { tune(current) }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(18) })
            addView(button("Use this recipe", true) { selected = current; showBrew() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        body.addView(card().apply {
            addView(text("POUR PLAN", 11f, copper, true))
            current.recipe.pours.forEachIndexed { i, pour ->
                addView(text("Pour ${i + 1}", 13f, ink, true).apply { setPadding(0, dp(12), 0, dp(3)) })
                addView(text("${pour.volumeMl} g  •  ${pour.temperatureC}°C  •  ${pour.flowRateTenthsMlPerSec / 10.0} ml/s  •  ${pour.pattern.name.lowercase()}  •  pause ${pour.pauseSeconds}s", 14f, muted))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        nav(body, "Coffees")
        setContentView(scroll)
    }

    private fun tune(current: GeneratedRecipe) {
        val goals = TasteGoal.values().filter { it != TasteGoal.RECOMMENDED }
        val labels = goals.map { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Tune the cup").setItems(labels) { _, index ->
            val tuned = RecipeTuner.tune(current, goals[index])
            if (current.mode == BrewMode.HOT) hot = tuned else iced = tuned
            selected = tuned
            showDetail()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showBrew() {
        val g = selected
        val (scroll, body) = root("Brew", "Preflight the exact recipe before xBloom.")
        if (g == null) {
            body.addView(card().apply {
                addView(text("No recipe selected", 22f, ink, true))
                addView(button("Add a coffee", true) { showAddCoffee() }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })
            })
        } else {
            val xid = ("BT" + g.profile.id.filter { it.isLetterOrDigit() }.uppercase().take(5).padEnd(5, '0')).encodeToByteArray()
            val payload = runCatching { XBloomRecipeEncoder.encodePayload(g.recipe, xid) }
            body.addView(card().apply {
                addView(text("READY FOR xBLOOM", 11f, copper, true))
                addView(text(g.recipe.name, 23f, ink, true).apply { setPadding(0, dp(8), 0, dp(7)) })
                addView(text("${g.recipe.doseGrams} g   •   ${g.recipe.totalWaterMl} g brew water   •   Grind ${g.recipe.grindSize}", 15f, muted))
                g.icedSplit?.let { addView(text("Add ${it.iceGrams} g ice before brewing.", 15f, copper, true).apply { setPadding(0, dp(8), 0, 0) }) }
                addView(text(if (payload.isSuccess) "✓ Recipe payload validated" else "Preflight error: ${payload.exceptionOrNull()?.message}", 15f, if (payload.isSuccess) ink else Color.RED, true).apply { setPadding(0, dp(18), 0, 0) })
                addView(text("Direct phone → xBloom remains experimental. BrewTap never auto-starts the brewer.", 13f, muted).apply { setPadding(0, dp(10), 0, 0) })
            })
        }
        nav(body, "Brew")
        setContentView(scroll)
    }

    private fun showSettings() {
        val (scroll, body) = root("Settings", "BrewTap 1.3.1")
        body.addView(card().apply {
            addView(text("APP", 11f, copper, true))
            addView(text("Modern Premium Utility", 23f, ink, true).apply { setPadding(0, dp(8), 0, dp(6)) })
            addView(text("Smart Hot + Iced recipe generation, editable coffee profiles and xBloom preflight.", 14f, muted))
        })
        nav(body, "Settings")
        setContentView(scroll)
    }
}
