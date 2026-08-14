package com.brewtap.xbloom

import com.brewtap.xbloom.domain.IcedSplit

object XBloomRecipeValidator {
    fun validate(recipe: BrewRecipe, icedSplit: IcedSplit? = null): List<String> {
        val errors = mutableListOf<String>()
        if (recipe.doseGrams !in 10..25) errors += "Dose must be 10–25 g"
        if (recipe.grindSize !in 41..80) errors += "Grind must be 41–80"
        if (recipe.grinderRpm !in 60..120) errors += "RPM must be 60–120"
        if (recipe.pours.size !in 2..5) errors += "Recipe must have 2–5 pours"
        if (recipe.pours.sumOf { it.volumeMl } != recipe.totalWaterMl) errors += "Pour water must equal brew water"
        recipe.pours.forEachIndexed { i, p ->
            if (p.volumeMl !in 1..255) errors += "Pour ${i + 1}: volume out of range"
            if (p.temperatureC !in 85..96) errors += "Pour ${i + 1}: temperature must be 85–96°C"
            if (p.flowRateTenthsMlPerSec !in 20..45) errors += "Pour ${i + 1}: flow must be 2.0–4.5 ml/s"
            if (p.pauseSeconds !in 0..360) errors += "Pour ${i + 1}: pause must be 0–360 s"
            if (p.agitation !in 0..3) errors += "Pour ${i + 1}: agitation out of range"
        }
        if (icedSplit != null) {
            if (icedSplit.brewWaterMl != recipe.totalWaterMl) errors += "Iced brew-water split must equal recipe water"
            if (icedSplit.brewWaterMl + icedSplit.iceGrams != icedSplit.targetBeverageWaterMl) errors += "Iced water + ice mass balance is invalid"
        }
        runCatching { recipe.machineRatio }.onFailure { errors += it.message ?: "xBloom ratio must be an integer" }
        return errors
    }
}
