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
    val cupType: Int = 0,
) {
    val displayedRatio: Double get() = totalWaterMl.toDouble() / doseGrams.toDouble()
    val machineRatio: Int get() {
        require(doseGrams > 0) { "dose must be positive" }
        val ratio = totalWaterMl.toDouble() / doseGrams.toDouble()
        require(kotlin.math.abs(ratio - kotlin.math.round(ratio)) < 0.001) { "xBloom card ratio must be an integer" }
        return kotlin.math.round(ratio).toInt()
    }
}
