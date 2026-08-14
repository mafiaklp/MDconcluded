package com.brewtap.xbloom

enum class PourPattern(val code: Int) {
    CENTERED(0), CIRCULAR(1), SPIRAL(2)
}

data class BrewPour(
    val volumeMl: Int,
    val temperatureC: Int,
    val flowRateTenthsMlPerSec: Int,
    val pattern: PourPattern,
    val agitation: Int = 0,
    val pauseSeconds: Int = 0,
)

data class BrewRecipe(
    val name: String,
    val doseGrams: Int,
    val totalWaterMl: Int,
    val grindSize: Int,
    val grinderRpm: Int,
    val temperatureC: Int,
    val pours: List<BrewPour>,
    val cupType: Int = 2,
) {
    val displayedRatio: Double get() = totalWaterMl.toDouble() / doseGrams.toDouble()
    val machineRatio: Int get() = kotlin.math.round(totalWaterMl / 15.0).toInt()
}
