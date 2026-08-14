package com.brewtap.xbloom.domain

import com.brewtap.xbloom.BrewPour
import com.brewtap.xbloom.BrewRecipe
import com.brewtap.xbloom.PourPattern
import kotlin.math.roundToInt

object SmartRecipeEngine {
    fun generate(profile: CoffeeProfile, intent: RecipeIntent): GeneratedRecipe {
        require(profile.isGeneratable()) { "coffee profile needs roast, process, origin, or tasting notes" }

        val text = profile.searchableText()
        val washed = text.contains("washed")
        val natural = text.contains("natural")
        val anaerobic = text.contains("anaerobic") || text.contains("ferment") || text.contains("thermal shock")
        val floral = hasAny(text, "jasmine", "floral", "bergamot", "orange blossom", "tea")
        val fruit = hasAny(text, "berry", "peach", "citrus", "stone fruit", "mango", "tropical", "currant", "cherry")
        val sweet = hasAny(text, "honey", "caramel", "toffee", "sugar", "chocolate", "sweet")
        val highAltitude = (profile.altitudeM ?: 0) >= 1800

        val baseTemp = when (profile.roastLevel) {
            RoastLevel.LIGHT -> if (washed || highAltitude) 94 else 93
            RoastLevel.MEDIUM_LIGHT -> 92
            RoastLevel.MEDIUM -> 90
            RoastLevel.MEDIUM_DARK -> 88
            RoastLevel.DARK -> 86
            RoastLevel.UNKNOWN -> if (washed) 93 else 91
        }.let { if (anaerobic) it - 1 else it }

        val baseGrind = when (profile.roastLevel) {
            RoastLevel.LIGHT -> if (washed) 54 else 57
            RoastLevel.MEDIUM_LIGHT -> 58
            RoastLevel.MEDIUM -> 61
            RoastLevel.MEDIUM_DARK -> 64
            RoastLevel.DARK -> 67
            RoastLevel.UNKNOWN -> 59
        }.coerceIn(41, 80)

        val ratio = when (profile.roastLevel) {
            RoastLevel.LIGHT -> if (washed) 16.0 else 15.5
            RoastLevel.MEDIUM_LIGHT -> 15.5
            RoastLevel.MEDIUM -> 15.0
            RoastLevel.MEDIUM_DARK -> 14.5
            RoastLevel.DARK -> 14.0
            RoastLevel.UNKNOWN -> 15.0
        }

        val dose = intent.doseGrams
        val targetBeverage = (dose * ratio).roundToInt()
        val icedSplit = if (intent.mode == BrewMode.ICED) {
            val iceFraction = when {
                floral || fruit -> 0.34
                profile.roastLevel >= RoastLevel.MEDIUM -> 0.30
                else -> 0.32
            }
            val ice = (targetBeverage * iceFraction).roundToInt()
            IcedSplit(targetBeverage - ice, ice, targetBeverage)
        } else null

        val brewWater = icedSplit?.brewWaterMl ?: targetBeverage
        val temp = (baseTemp + if (intent.mode == BrewMode.ICED) 1 else 0).coerceIn(85, 96)
        val grind = (baseGrind + if (intent.mode == BrewMode.ICED) -2 else 0).coerceIn(41, 80)
        val rpm = when {
            profile.roastLevel == RoastLevel.LIGHT && highAltitude -> 80
            natural || anaerobic -> 90
            profile.roastLevel == RoastLevel.DARK -> 110
            else -> 100
        }

        val pourCount = if (intent.mode == BrewMode.ICED) 3 else if (washed || floral) 4 else 3
        val pours = makePours(
            total = brewWater,
            count = pourCount,
            temp = temp,
            dose = dose,
            controlled = natural || anaerobic,
            clarityBias = washed || floral,
        )

        val expected = predictCup(profile, floral, fruit, sweet, washed, natural, anaerobic, intent.mode)
        val rationale = buildString {
            append(if (intent.mode == BrewMode.HOT) "Hot recipe" else "Iced concentrate")
            append(" designed for ")
            append(when {
                washed && floral -> "clarity and aromatic lift"
                natural || anaerobic -> "fruit definition and sweetness with controlled agitation"
                profile.roastLevel == RoastLevel.DARK -> "sweetness and body while limiting harsh extraction"
                else -> "balanced sweetness, clarity, and body"
            })
            if (highAltitude) append("; dense high-altitude coffee receives extra extraction support")
            if (icedSplit != null) append("; ${icedSplit.brewWaterMl} g brew water + ${icedSplit.iceGrams} g ice")
            append(".")
        }

        return GeneratedRecipe(
            profile = profile,
            mode = intent.mode,
            recipe = BrewRecipe(
                name = "${profile.name} — ${intent.mode.name.lowercase().replaceFirstChar { it.uppercase() }} Recommended",
                doseGrams = dose,
                totalWaterMl = brewWater,
                grindSize = grind,
                grinderRpm = rpm,
                temperatureC = temp,
                pours = pours,
                cupType = 0,
            ),
            expectedCup = expected,
            rationale = rationale,
            icedSplit = icedSplit,
        )
    }

    private fun makePours(total: Int, count: Int, temp: Int, dose: Int, controlled: Boolean, clarityBias: Boolean): List<BrewPour> {
        val bloom = minOf(total, (dose * if (clarityBias) 2.5 else 2.2).roundToInt())
        val remaining = total - bloom
        val afterCount = count - 1
        val base = if (afterCount > 0) remaining / afterCount else 0
        val volumes = MutableList(count) { if (it == 0) bloom else base }
        if (count > 1) volumes[count - 1] += total - volumes.sum()

        return volumes.mapIndexed { i, volume ->
            BrewPour(
                volumeMl = volume,
                temperatureC = (temp - if (i >= 2) 1 else 0).coerceAtLeast(85),
                flowRateTenthsMlPerSec = when {
                    i == 0 -> if (clarityBias) 28 else 30
                    controlled -> 30
                    else -> 34
                },
                pattern = if (i == 0 && controlled) PourPattern.CENTERED else PourPattern.SPIRAL,
                agitation = when {
                    i == 0 && !controlled -> 1
                    else -> 0
                },
                pauseSeconds = when {
                    i == 0 -> if (clarityBias) 35 else 30
                    i < count - 1 -> if (controlled) 12 else 8
                    else -> 0
                },
            )
        }
    }

    private fun predictCup(
        profile: CoffeeProfile,
        floralNote: Boolean,
        fruitNote: Boolean,
        sweetNote: Boolean,
        washed: Boolean,
        natural: Boolean,
        anaerobic: Boolean,
        mode: BrewMode,
    ): ExpectedCup {
        var acidity = when (profile.roastLevel) {
            RoastLevel.LIGHT -> 8
            RoastLevel.MEDIUM_LIGHT -> 7
            RoastLevel.MEDIUM -> 5
            RoastLevel.MEDIUM_DARK -> 4
            RoastLevel.DARK -> 3
            RoastLevel.UNKNOWN -> 6
        }
        var sweetness = if (sweetNote || natural) 8 else 7
        var body = if (natural || anaerobic) 7 else 5
        var clarity = if (washed) 8 else 6
        var floral = if (floralNote) 8 else if (washed) 6 else 4
        var fruit = if (fruitNote || natural) 8 else 5
        var bitterness = when (profile.roastLevel) {
            RoastLevel.DARK -> 6
            RoastLevel.MEDIUM_DARK -> 5
            RoastLevel.MEDIUM -> 4
            else -> 2
        }
        if (anaerobic) { fruit += 1; clarity -= 1; body += 1 }
        if (mode == BrewMode.ICED) { acidity -= 1; sweetness += 1; body += 1; floral -= 1 }

        fun c(v: Int) = v.coerceIn(0, 10)
        val scores = mapOf(
            "floral" to c(floral), "fruit" to c(fruit), "sweetness" to c(sweetness),
            "clarity" to c(clarity), "acidity" to c(acidity), "body" to c(body)
        )
        val top = scores.entries.sortedByDescending { it.value }.take(2).map { it.key }
        val bodyText = when { c(body) >= 8 -> "full body"; c(body) >= 6 -> "silky medium body"; else -> "light clean body" }
        val finish = if (c(clarity) >= 7) "clean finish" else "sweet lingering finish"
        val summary = "${top.joinToString(" and ") { it.replaceFirstChar(Char::uppercase) }} forward, $bodyText, $finish."

        return ExpectedCup(c(acidity), c(sweetness), c(body), c(clarity), c(floral), c(fruit), c(bitterness), summary)
    }

    private fun hasAny(text: String, vararg tokens: String): Boolean = tokens.any { text.contains(it) }
}
