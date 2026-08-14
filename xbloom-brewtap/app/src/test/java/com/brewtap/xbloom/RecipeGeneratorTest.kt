package com.brewtap.xbloom

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeGeneratorTest {
    @Test fun edisonPresetMatchesExpectedRecipe() {
        val recipe = RecipeGenerator.edisonEthiopia()
        assertEquals(18, recipe.doseGrams)
        assertEquals(270, recipe.totalWaterMl)
        assertEquals(55, recipe.grindSize)
        assertEquals(93, recipe.temperatureC)
        assertEquals(80, recipe.grinderRpm)
        assertEquals(listOf(50, 70, 75, 75), recipe.pours.map { it.volumeMl })
        assertEquals(15, recipe.machineRatio)
    }
}
