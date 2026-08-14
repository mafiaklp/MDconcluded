package com.brewtap.xbloom.domain

import com.brewtap.xbloom.BrewRecipe

data class GeneratedRecipe(
    val profile: CoffeeProfile,
    val mode: BrewMode,
    val recipe: BrewRecipe,
    val expectedCup: ExpectedCup,
    val rationale: String,
    val icedSplit: IcedSplit? = null,
) {
    init {
        if (mode == BrewMode.ICED) requireNotNull(icedSplit)
        if (mode == BrewMode.HOT) require(icedSplit == null)
    }
}
