package com.brewtap.xbloom

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity(), XBloomBleClient.Callback {
    private val recipe = RecipeGenerator.edisonEthiopia()
    private lateinit var ble: XBloomBleClient
    private lateinit var status: TextView
    private lateinit var tapButton: Button
    private lateinit var content: LinearLayout

    private val ivory = Color.rgb(246, 242, 233)
    private val ink = Color.rgb(23, 21, 18)
    private val muted = Color.rgb(116, 111, 103)
    private val espresso = Color.rgb(57, 43, 36)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = XBloomBleClient(this)
        setContentView(buildShell())
        showHome()
    }

    private fun buildShell(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ivory)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 42, 42, 24)
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 20)
        }
        listOf("HOME", "RECIPES", "TAP", "SETTINGS").forEach { label ->
            nav.addView(Button(this).apply {
                text = label
                textSize = if (label == "TAP") 14f else 11f
                setTextColor(if (label == "TAP") Color.WHITE else ink)
                background = rounded(if (label == "TAP") espresso else Color.TRANSPARENT, 48f)
                setOnClickListener {
                    when (label) {
                        "HOME" -> showHome()
                        "RECIPES" -> showRecipes()
                        "TAP" -> armTap()
                        else -> showSettings()
                    }
                }
            }, LinearLayout.LayoutParams(0, 58, 1f).apply {
                marginStart = 5
                marginEnd = 5
            })
        }
        root.addView(nav)
        return root
    }

    private fun title(t: String, size: Float = 30f) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(ink)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun body(t: String, size: Float = 15f) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(muted)
        setLineSpacing(4f, 1f)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun clear() {
        ble.close()
        content.removeAllViews()
    }

    private fun showHome() {
        clear()
        content.addView(title("BrewTap", 34f))
        content.addView(body("Smart recipes for xBloom", 16f).apply { setPadding(0, 4, 0, 28) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            background = rounded(Color.rgb(255, 253, 248), 32f)
        }
        card.addView(body("CURRENT COFFEE", 12f))
        card.addView(title("EDISON Ethiopia", 26f).apply { setPadding(0, 6, 0, 2) })
        card.addView(body("Medium / Light  ·  Berry  ·  Citrus  ·  Floral", 14f).apply { setPadding(0, 0, 0, 22) })
        card.addView(title("18 g     270 ml", 25f))
        card.addView(body("93°C     Grind 55 @ 80 RPM", 16f).apply { setPadding(0, 5, 0, 20) })
        card.addView(body("Bloom 50 ml / 40s\n70 ml / 10s  ·  75 ml / 10s  ·  75 ml\nSpiral pour  ·  3.0–3.2 ml/s", 15f))

        tapButton = Button(this).apply {
            text = "TAP TO xBLOOM"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(espresso, 40f)
            setOnClickListener { armTap() }
        }
        card.addView(tapButton, LinearLayout.LayoutParams(-1, 66).apply { topMargin = 26 })
        content.addView(card, LinearLayout.LayoutParams(-1, -2))

        status = body(
            "Tap the button, then place your phone against the xBloom NFC area. BrewTap detects close-range Bluetooth signal and sends the selected recipe.",
            14f,
        ).apply { setPadding(6, 24, 6, 0) }
        content.addView(status)
    }

    private fun showRecipes() {
        clear()
        content.addView(title("Recipes"))
        content.addView(body("Your adaptive recipes", 16f).apply { setPadding(0, 4, 0, 24) })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = rounded(Color.WHITE, 28f)
        }
        card.addView(title("EDISON Ethiopia", 22f))
        card.addView(body("18g · 270ml · 93°C · Grind 55 · Spiral", 15f).apply { setPadding(0, 8, 0, 14) })
        card.addView(Button(this).apply {
            text = "USE & TAP"
            setOnClickListener {
                showHome()
                armTap()
            }
        })
        content.addView(card)
        status = body("")
        tapButton = Button(this)
    }

    private fun showSettings() {
        clear()
        content.addView(title("Settings"))
        content.addView(
            body(
                "BrewTap 1.1.1 experimental\n\nTap detection: close-range Bluetooth LE\nRecipe transport: direct xBloom BLE\nBrew start: physical xBloom Start button\n\nImportant: disconnect the official xBloom app before sending a recipe because the machine may allow only one BLE central connection at a time.",
                15f,
            ).apply { setPadding(0, 16, 0, 0) },
        )
        status = body("")
        tapButton = Button(this)
    }

    private fun ensureBlePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            if (needed.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissions(needed, 77)
                return false
            }
        }
        return true
    }

    private fun armTap() {
        if (!::status.isInitialized) showHome()
        if (!ensureBlePermission()) {
            status.text = "Allow Nearby devices, then tap TAP TO xBLOOM again."
            return
        }
        tapButton.isEnabled = false
        status.text = "READY TO TAP\nPlace the phone against the xBloom NFC area…"
        ble.sendRecipeWhenNear(recipe, this)
    }

    override fun onState(message: String) {
        runOnUiThread { status.text = message }
    }

    override fun onLoaded(deviceName: String) {
        runOnUiThread {
            status.text = "✓ RECIPE SENT TO $deviceName\n\nCheck the recipe on xBloom, then press Start on the machine to brew."
            tapButton.isEnabled = true
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            status.text = "Could not send recipe.\n$message"
            tapButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        ble.close()
        super.onDestroy()
    }
}
