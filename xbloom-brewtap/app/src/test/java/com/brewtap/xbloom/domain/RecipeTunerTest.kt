package com.brewtap.xbloom.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTunerTest {
    private fun fixture() = SmartRecipeEngine.generate(
        CoffeeProfile(
            name = "Ethiopia Natural",
            process = "natural",
            roastLevel = RoastLevel.LIGHT,
            tastingNotes = listOf("berry", "floral", "honey"),
        ),
        RecipeIntent(BrewMode.HOT, doseGrams = 18),
    )

    @Test fun lessBitterReducesExtractionPressure() {
        val base = fixture()
        val tuned = RecipeTuner.tune(base, TasteGoal.LESS_BITTER)
        val lowerTemp = tuned.recipe.temperatureC <= base.recipe.temperatureC
        val coarser = tuned.recipe.grindSize >= base.recipe.grindSize
        val lowerAgitation = tuned.recipe.pours.sumOf { it.agitation } <= base.recipe.pours.sumOf { it.agitation }
        assertTrue(lowerTemp || coarser || lowerAgitation)
        assertTrue(tuned.expectedCup.bitternessRisk <= base.expectedCup.bitternessRisk)
    }

    @Test fun brighterDoesNotBlindlyIncreaseEveryExtractionLever() {
        val base = fixture()
        val tuned = RecipeTuner.tune(base, TasteGoal.BRIGHTER)
        assertTrue(tuned.rationale.contains("bright", ignoreCase = true))
        assertTrue(tuned.recipe.grindSize >= base.recipe.grindSize)
    }

    @Test fun tooSourAddsControlledExtractionSupport() {
        val base = fixture()
        val tuned = RecipeTuner.applyFeedback(base, BrewFeedback(BrewFeedbackType.TOO_SOUR))
        assertTrue(tuned.recipe.grindSize <= base.recipe.grindSize)
        assertTrue(tuned.recipe.pours.first().pauseSeconds >= base.recipe.pours.first().pauseSeconds)
    }
}
