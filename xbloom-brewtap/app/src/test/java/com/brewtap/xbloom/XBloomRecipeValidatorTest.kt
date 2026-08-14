package com.brewtap.xbloom

import com.brewtap.xbloom.domain.*
import org.junit.Assert.assertTrue
import org.junit.Test

class XBloomRecipeValidatorTest {
    @Test fun generatedHotAndIcedRecipesPassPreflight() {
        val p = CoffeeProfile(
            name = "Ethiopia Guji",
            process = "washed",
            roastLevel = RoastLevel.LIGHT,
            altitudeM = 2100,
            tastingNotes = listOf("jasmine", "bergamot", "peach"),
        )
        val hot = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.HOT, doseGrams = 18))
        val iced = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.ICED, doseGrams = 18))
        assertTrue(XBloomRecipeValidator.validate(hot.recipe).isEmpty())
        assertTrue(XBloomRecipeValidator.validate(iced.recipe, iced.icedSplit).isEmpty())
    }
}
