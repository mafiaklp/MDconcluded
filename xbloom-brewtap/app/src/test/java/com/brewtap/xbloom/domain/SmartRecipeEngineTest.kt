package com.brewtap.xbloom.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecipeEngineTest {
    private val floralWashed = CoffeeProfile(
        name = "Ethiopia Guji",
        process = "washed",
        roastLevel = RoastLevel.LIGHT,
        altitudeM = 2100,
        tastingNotes = listOf("jasmine", "bergamot", "peach"),
    )

    @Test fun coffeeProfileMinimumInfoIsEnforced() {
        assertTrue(!CoffeeProfile(name = "Test").isGeneratable())
        assertTrue(CoffeeProfile(name = "Test", roastLevel = RoastLevel.LIGHT).isGeneratable())
    }

    @Test fun defaultDoseIs15gButExplicitDoseIsPreserved() {
        assertEquals(15, RecipeIntent(BrewMode.HOT).doseGrams)
        val custom = SmartRecipeEngine.generate(floralWashed, RecipeIntent(BrewMode.HOT, doseGrams = 18))
        assertEquals(18, custom.recipe.doseGrams)
    }

    @Test(expected = IllegalArgumentException::class)
    fun icedSplitRequiresMassBalance() { IcedSplit(180, 80, 270) }

    @Test fun washedLightCoffeeGetsClarityBiasedHotRecipe() {
        val g = SmartRecipeEngine.generate(floralWashed, RecipeIntent(BrewMode.HOT, doseGrams = 18))
        assertEquals(18, g.recipe.doseGrams)
        assertTrue(g.recipe.temperatureC >= 92)
        assertTrue(g.expectedCup.clarity >= 7)
        assertTrue(g.expectedCup.floral >= 7)
        assertEquals(g.recipe.totalWaterMl, g.recipe.pours.sumOf { it.volumeMl })
    }

    @Test fun icedRecipeBalancesHotWaterAndIceAndDiffersFromHot() {
        val hot = SmartRecipeEngine.generate(floralWashed, RecipeIntent(BrewMode.HOT, doseGrams = 18))
        val iced = SmartRecipeEngine.generate(floralWashed, RecipeIntent(BrewMode.ICED, doseGrams = 18))
        val split = requireNotNull(iced.icedSplit)
        assertEquals(split.targetBeverageWaterMl, split.brewWaterMl + split.iceGrams)
        assertEquals(split.brewWaterMl, iced.recipe.pours.sumOf { it.volumeMl })
        assertTrue(split.brewWaterMl < split.targetBeverageWaterMl)
        assertNotEquals(hot.recipe.totalWaterMl, iced.recipe.totalWaterMl)
        assertNotEquals(hot.recipe.grindSize, iced.recipe.grindSize)
    }

    @Test fun expectedCupScoresAreBoundedAndDescribed() {
        val g = SmartRecipeEngine.generate(floralWashed, RecipeIntent(BrewMode.HOT))
        val scores = listOf(g.expectedCup.acidity, g.expectedCup.sweetness, g.expectedCup.body,
            g.expectedCup.clarity, g.expectedCup.floral, g.expectedCup.fruit, g.expectedCup.bitternessRisk)
        assertTrue(scores.all { it in 0..10 })
        assertTrue(g.expectedCup.summary.isNotBlank())
    }
}
