package com.brewtap.xbloom

object RecipeGenerator {
    fun edisonEthiopia(): BrewRecipe = BrewRecipe(
        name = "EDISON Ethiopia — Bright & Juicy",
        doseGrams = 18,
        totalWaterMl = 270,
        grindSize = 55,
        grinderRpm = 80,
        temperatureC = 93,
        pours = listOf(
            BrewPour(50, 93, 30, PourPattern.SPIRAL, pauseSeconds = 40),
            BrewPour(70, 93, 32, PourPattern.SPIRAL, pauseSeconds = 10),
            BrewPour(75, 93, 32, PourPattern.SPIRAL, pauseSeconds = 10),
            BrewPour(75, 93, 32, PourPattern.SPIRAL, pauseSeconds = 0),
        )
    )
}
