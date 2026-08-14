package com.brewtap.xbloom

import com.brewtap.xbloom.domain.*
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecipeAcceptanceTest {
    @Test fun floralWashedCoffeeGeneratesDistinctHotIcedAndEncodes() {
        val profile = CoffeeProfile(
            name = "Competition Ethiopia",
            country = "Ethiopia",
            process = "washed",
            roastLevel = RoastLevel.LIGHT,
            altitudeM = 2100,
            tastingNotes = listOf("jasmine", "bergamot", "peach"),
        )
        val hot = SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.HOT, doseGrams = 18))
        val iced = SmartRecipeEngine.generate(profile, RecipeIntent(BrewMode.ICED, doseGrams = 18))
        assertNotEquals(hot.recipe.totalWaterMl, iced.recipe.totalWaterMl)
        assertNotEquals(hot.recipe.grindSize, iced.recipe.grindSize)
        assertTrue(XBloomRecipeValidator.validate(hot.recipe).isEmpty())
        assertTrue(XBloomRecipeValidator.validate(iced.recipe, iced.icedSplit).isEmpty())
        val sweet = RecipeTuner.tune(hot, TasteGoal.SWEETER)
        val payload = XBloomRecipeEncoder.encodePayload(sweet.recipe, "BT00001".encodeToByteArray())
        assertTrue(payload.isNotEmpty())
    }

    @Test fun mediumNaturalUsesLowerTemperatureThanFloralWashedBaseline() {
        val washed = SmartRecipeEngine.generate(
            CoffeeProfile(name = "Washed", process = "washed", roastLevel = RoastLevel.LIGHT, tastingNotes = listOf("jasmine")),
            RecipeIntent(BrewMode.HOT),
        )
        val natural = SmartRecipeEngine.generate(
            CoffeeProfile(name = "Natural", process = "natural", roastLevel = RoastLevel.MEDIUM, tastingNotes = listOf("cherry", "chocolate")),
            RecipeIntent(BrewMode.HOT),
        )
        assertTrue(natural.recipe.temperatureC < washed.recipe.temperatureC)
        assertTrue(natural.recipe.pours.sumOf { it.agitation } <= 1)
    }
}
