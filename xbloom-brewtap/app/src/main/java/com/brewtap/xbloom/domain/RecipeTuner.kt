package com.brewtap.xbloom.domain

import com.brewtap.xbloom.BrewPour

object RecipeTuner {
    fun tune(current: GeneratedRecipe, goal: TasteGoal): GeneratedRecipe {
        if (goal == TasteGoal.RECOMMENDED) return current
        val r = current.recipe
        var grind = r.grindSize
        var tempDelta = 0
        var flowDelta = 0
        var agitationDelta = 0
        var pauseDelta = 0
        var expected = current.expectedCup
        val rationale: String

        when (goal) {
            TasteGoal.BRIGHTER -> {
                grind += 1; flowDelta += 2; pauseDelta -= 2
                expected = expected.copy(acidity = up(expected.acidity), body = down(expected.body))
                rationale = "Brighter: slightly coarser grind and quicker later pours to lift acidity without pushing every extraction lever."
            }
            TasteGoal.SWEETER -> {
                grind -= 1; flowDelta -= 1; pauseDelta += 2
                expected = expected.copy(sweetness = up(expected.sweetness), bitternessRisk = down(expected.bitternessRisk))
                rationale = "Sweeter: slightly finer grind with slower middle pours for more even extraction and sweetness."
            }
            TasteGoal.MORE_FLORAL -> {
                grind += 1; agitationDelta -= 1; flowDelta += 1
                expected = expected.copy(floral = up(expected.floral), clarity = up(expected.clarity), body = down(expected.body))
                rationale = "More floral: lower agitation and a slightly cleaner, faster finish to protect volatile aromatics."
            }
            TasteGoal.JUICIER -> {
                grind -= 1; agitationDelta += 1; flowDelta += 1
                expected = expected.copy(fruit = up(expected.fruit), acidity = up(expected.acidity), body = up(expected.body))
                rationale = "Juicier: modestly finer grind and controlled extra agitation through the middle of the brew."
            }
            TasteGoal.MORE_BODY -> {
                grind -= 1; agitationDelta += 1; flowDelta -= 1
                expected = expected.copy(body = up(expected.body), clarity = down(expected.clarity))
                rationale = "More body: slightly finer grind, slower flow, and modest agitation to increase texture."
            }
            TasteGoal.CLEANER -> {
                grind += 1; agitationDelta -= 1; flowDelta += 2
                expected = expected.copy(clarity = up(expected.clarity), body = down(expected.body), bitternessRisk = down(expected.bitternessRisk))
                rationale = "Cleaner: coarser grind, lower agitation, and faster late pours to reduce muddiness and finish cleaner."
            }
            TasteGoal.LESS_BITTER -> {
                grind += 2; tempDelta -= 1; agitationDelta -= 1; pauseDelta -= 2
                expected = expected.copy(bitternessRisk = down2(expected.bitternessRisk), sweetness = up(expected.sweetness))
                rationale = "Less bitter: coarser grind, lower temperature, and lower agitation to reduce late-stage extraction pressure."
            }
            TasteGoal.RECOMMENDED -> return current
        }

        val newPours = r.pours.mapIndexed { index, p ->
            p.copy(
                temperatureC = (p.temperatureC + tempDelta).coerceIn(85, 96),
                flowRateTenthsMlPerSec = (p.flowRateTenthsMlPerSec + if (index == 0) 0 else flowDelta).coerceIn(20, 45),
                agitation = (p.agitation + if (index <= 1) agitationDelta else 0).coerceIn(0, 3),
                pauseSeconds = (p.pauseSeconds + if (index < r.pours.lastIndex) pauseDelta else 0).coerceIn(0, 360),
            )
        }

        val newRecipe = r.copy(
            grindSize = grind.coerceIn(41, 80),
            temperatureC = (r.temperatureC + tempDelta).coerceIn(85, 96),
            pours = newPours,
            name = r.name.substringBefore(" •") + " • ${goal.name.lowercase().replace('_', ' ')}",
        )
        return current.copy(recipe = newRecipe, expectedCup = expected, rationale = rationale)
    }

    fun applyFeedback(current: GeneratedRecipe, feedback: BrewFeedback): GeneratedRecipe = when (feedback.type) {
        BrewFeedbackType.TOO_SOUR -> supportExtraction(current, "Too sour: slightly finer grind and longer first pause to increase extraction evenly.")
        BrewFeedbackType.TOO_BITTER -> tune(current, TasteGoal.LESS_BITTER)
        BrewFeedbackType.TOO_WEAK -> tune(current, TasteGoal.MORE_BODY)
        BrewFeedbackType.TOO_STRONG -> tune(current, TasteGoal.CLEANER)
        BrewFeedbackType.TOO_DRY -> tune(current, TasteGoal.LESS_BITTER).let {
            it.copy(rationale = "Too dry/astringent: coarser grind and lower agitation to reduce harsh late extraction.")
        }
        BrewFeedbackType.MUTED -> tune(current, TasteGoal.BRIGHTER).let {
            it.copy(rationale = "Muted/flat: faster, brighter presentation with slightly less late contact time.")
        }
    }

    private fun supportExtraction(current: GeneratedRecipe, rationale: String): GeneratedRecipe {
        val r = current.recipe
        val pours = r.pours.mapIndexed { i, p ->
            p.copy(pauseSeconds = if (i == 0) (p.pauseSeconds + 4).coerceAtMost(360) else p.pauseSeconds)
        }
        return current.copy(
            recipe = r.copy(grindSize = (r.grindSize - 1).coerceAtLeast(41), pours = pours),
            expectedCup = current.expectedCup.copy(sweetness = up(current.expectedCup.sweetness)),
            rationale = rationale,
        )
    }

    private fun up(v: Int) = (v + 1).coerceAtMost(10)
    private fun down(v: Int) = (v - 1).coerceAtLeast(0)
    private fun down2(v: Int) = (v - 2).coerceAtLeast(0)
}
