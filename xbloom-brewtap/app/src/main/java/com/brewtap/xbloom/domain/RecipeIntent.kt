package com.brewtap.xbloom.domain

enum class BrewMode { HOT, ICED }

enum class TasteGoal {
    RECOMMENDED,
    BRIGHTER,
    SWEETER,
    MORE_FLORAL,
    JUICIER,
    MORE_BODY,
    CLEANER,
    LESS_BITTER,
}

data class RecipeIntent(
    val mode: BrewMode,
    val tasteGoal: TasteGoal = TasteGoal.RECOMMENDED,
    val doseGrams: Int = 18,
) {
    init { require(doseGrams in 10..25) { "dose must be 10–25 g" } }
}

data class IcedSplit(
    val brewWaterMl: Int,
    val iceGrams: Int,
    val targetBeverageWaterMl: Int,
) {
    init {
        require(brewWaterMl > 0)
        require(iceGrams > 0)
        require(brewWaterMl + iceGrams == targetBeverageWaterMl) {
            "brew water + ice must equal target beverage water"
        }
    }
}
