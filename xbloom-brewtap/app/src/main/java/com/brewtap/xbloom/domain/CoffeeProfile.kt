package com.brewtap.xbloom.domain

import java.util.UUID

enum class CoffeeSource { PHOTO, MANUAL }
enum class RoastLevel { LIGHT, MEDIUM_LIGHT, MEDIUM, MEDIUM_DARK, DARK, UNKNOWN }

data class CoffeeProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val roaster: String = "",
    val country: String = "",
    val region: String = "",
    val farm: String = "",
    val variety: String = "",
    val process: String = "",
    val roastLevel: RoastLevel = RoastLevel.UNKNOWN,
    val altitudeM: Int? = null,
    val tastingNotes: List<String> = emptyList(),
    val bagBrewNotes: String = "",
    val source: CoffeeSource = CoffeeSource.MANUAL,
) {
    fun isGeneratable(): Boolean = name.isNotBlank() && (
        roastLevel != RoastLevel.UNKNOWN ||
            process.isNotBlank() ||
            country.isNotBlank() ||
            tastingNotes.isNotEmpty()
        )

    fun searchableText(): String = listOf(
        name, roaster, country, region, farm, variety, process,
        tastingNotes.joinToString(" "), bagBrewNotes
    ).joinToString(" ").lowercase()
}
